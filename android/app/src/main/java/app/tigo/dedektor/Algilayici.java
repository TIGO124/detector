package app.tigo.dedektor;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import androidx.camera.core.ImageProxy;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer;
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * MediaPipe modellerini kurar ve kameradan gelen kareleri boştaki modellere dağıtır. Her model
 * kendi iş parçacığında çalışır; bir öncekini bitirmeyen model yeni kare almaz. Vücut, arka
 * plan ve nesne modelleri yalnızca açıkken çalışır. Kurma/kapatma ve kare verme analiz iş parçacığından,
 * sonuçlar MediaPipe'ın iş parçacıklarından gelir.
 */
final class Algilayici {

    interface Dinleyici {
        /** girdi: kamera karesi; yalnızca bu çağrı sırasında geçerli (sonra havuza döner). */
        void yuz(FaceLandmarkerResult sonuc, MPImage girdi);

        void el(GestureRecognizerResult sonuc, int genislik, int yukseklik);

        void vucut(PoseLandmarkerResult sonuc, int genislik, int yukseklik);

        void arkaPlan(ImageSegmenterResult sonuc, MPImage girdi);

        /** girdi: kamera karesi; yalnızca bu çağrı sırasında geçerli. */
        void nesne(ObjectDetectorResult sonuc, MPImage girdi);

        void bildir(String mesaj);

        /**
         * GPU ile kurulan modeller çalışırken bozuldu (kurulum başarılı olsa da bazı telefonlarda
         * GPU sonuç vermiyor). Her GPU kurulumunda en çok bir kez, model iş parçacığından çağrılır.
         */
        void gpuCalismadi();
    }

    /** Modeller web dosyalarının yanında, APK'nın assets/public klasöründe durur. */
    private static final String MODELLER = "public/mediapipe/models/";
    private static final String ETIKET = "Dedektor";
    /** Bu kadar süredir sonuç vermeyen model takılmış sayılır ve yeniden kare alır. */
    private static final long TAKILMA_MS = 2000;
    /**
     * GPU'da ilk çalıştırma gölgelendirici derlerken zayıf telefonlarda saniyeler sürebilir:
     * hiç sonuç vermeden bu kadar takılırsa GPU çalışmıyor sayılır (gerçek hata hemen sayılır).
     */
    private static final long GPU_SABIR_MS = 8000;
    private static final int HAVUZ_BOYUTU = 4;
    /** Pil tasarrufunda kareler arası en az bu kadar (saniyede en çok 8 kare). */
    private static final long TASARRUF_MS = 125;

    /** Birden çok modele verilen kamera karesi; hepsi bitirince bitmap havuza döner. */
    private final class Kare {
        final Bitmap bitmap;
        final boolean havuzdan;
        final AtomicInteger kullanan = new AtomicInteger();

        Kare(Bitmap bitmap, boolean havuzdan) {
            this.bitmap = bitmap;
            this.havuzdan = havuzdan;
        }

        void birak() {
            if (kullanan.decrementAndGet() == 0 && havuzdan) havuzaKoy(bitmap);
        }
    }

    /** Tek bir modelin durumu. */
    static final class Gorev {
        final AtomicBoolean mesgul = new AtomicBoolean();
        /** İki kare arasında en az bu kadar beklenir (ağır, sık çalışması gerekmeyen modeller). */
        final long enAzAralik;
        final FpsSayaci fps = new FpsSayaci();
        volatile BiConsumer<MPImage, Long> gonder;
        volatile Runnable kapatici;
        /** Model yanıt vermeyince (takılınca) çağrılır. */
        volatile Runnable takildi;
        /** Bu kurulumdan beri en az bir sonuç verdi mi; ne zaman kuruldu. */
        volatile boolean sonucVerdi;
        volatile long acilis;
        /** Her kurulumda artar: kapatılmış eski modelden geç gelen sonuç yenisini bozmasın. */
        volatile int nesil;
        private volatile long gonderim;
        private volatile Kare kare;

        Gorev() {
            this(0);
        }

        Gorev(long enAzAralik) {
            this.enAzAralik = enAzAralik;
        }

        boolean acik() {
            return gonder != null;
        }

        /** Kurulan modeli bağlar: kareler gonder'e verilir, kapatırken kapatici çalışır. */
        void bagla(BiConsumer<MPImage, Long> gonder, Runnable kapatici) {
            sonucVerdi = false;
            acilis = SystemClock.uptimeMillis();
            this.kapatici = kapatici;
            this.gonder = gonder;
        }

        /** Model boştaysa (ya da takılmışsa) kareyi almak için ayırır. */
        boolean ayir(long simdi) {
            if (!acik()) return false;
            if (mesgul.get() && simdi - gonderim > TAKILMA_MS) {
                Log.w(ETIKET, "Model yanıt vermedi, yeniden kare veriliyor");
                nesil++;
                kare = null;
                mesgul.set(false);
                Runnable t = takildi;
                if (t != null) t.run();
            }
            if (!mesgul.get() && simdi - gonderim < enAzAralik) return false;
            if (!mesgul.compareAndSet(false, true)) return false;
            gonderim = simdi;
            return true;
        }

        void bitti(int n, boolean basarili) {
            if (n != nesil) return;
            Kare k = kare;
            kare = null;
            if (k != null) k.birak();
            if (basarili) {
                fps.say();
                sonucVerdi = true;
            }
            mesgul.set(false);
        }

        void kapat() {
            Runnable k = kapatici;
            gonder = null;
            kapatici = null;
            nesil++;
            kare = null;
            // Çalışırken bozulmuş bir model (ör. GPU hatası) kapatılırken de hata fırlatır;
            // yakalanmazsa uygulama çöker.
            if (k != null) {
                try {
                    k.run();
                } catch (RuntimeException e) {
                    Log.w(ETIKET, "Model kapatılırken hata", e);
                }
            }
            mesgul.set(false);
            fps.sifirla();
        }
    }

    final Gorev yuz = new Gorev();
    final Gorev el = new Gorev();
    final Gorev vucut = new Gorev();
    final Gorev arkaPlan = new Gorev();
    /** Nesne algılayıcı: saniyede en çok ~6 kez (zayıf telefonda diğer modelleri boğmasın). */
    final Gorev nesne = new Gorev(150);

    private final Context baglam;
    private final Dinleyici dinleyici;
    private final ArrayDeque<Bitmap> havuz = new ArrayDeque<>();
    private volatile Delegate hizlandirici = Delegate.CPU;
    private final AtomicBoolean gpuBildirildi = new AtomicBoolean();
    /** Pil tasarrufu: modellere daha seyrek kare verilir. */
    volatile boolean tasarruf;
    private long sonKare;

    Algilayici(Context baglam, Dinleyici dinleyici) {
        this.baglam = baglam;
        this.dinleyici = dinleyici;
        for (Gorev g : new Gorev[] {yuz, el, vucut, nesne}) g.takildi = () -> gpuSorunu(g, false);
    }

    /**
     * GPU'daki bir model hata verdi (hata) ya da takıldı: kullanıcıya bildirilir, CPU'ya dönülür.
     * Takılma ancak model kurulduğundan beri hiç sonuç vermeden GPU_SABIR_MS geçince sayılır.
     */
    private void gpuSorunu(Gorev g, boolean hata) {
        if (hizlandirici != Delegate.GPU) return;
        if (!hata && (g.sonucVerdi || SystemClock.uptimeMillis() - g.acilis < GPU_SABIR_MS)) return;
        if (gpuBildirildi.compareAndSet(false, true)) dinleyici.gpuCalismadi();
    }

    /** Yüz ve el modellerini verilen hızlandırıcıyla kurar; GPU olmazsa CPU'ya düşer. Sonuç: GPU kullanılıyor mu. */
    boolean temelModelleriKur(boolean gpu) {
        yuz.kapat();
        el.kapat();
        hizlandirici = gpu ? Delegate.GPU : Delegate.CPU;
        gpuBildirildi.set(false);
        try {
            yuzuKur();
            eliKur();
        } catch (RuntimeException e) {
            Log.e(ETIKET, "Modeller kurulamadı (" + hizlandirici + ")", e);
            yuz.kapat();
            el.kapat();
            if (!gpu) {
                dinleyici.bildir("Modeller yüklenemedi: " + e.getMessage());
                return false;
            }
            dinleyici.bildir("Bu telefonda GPU kullanılamıyor, CPU kullanılıyor.");
            return temelModelleriKur(false);
        }
        // Açık olan isteğe bağlı modeller de aynı hızlandırıcıya geçsin.
        if (vucut.acik()) {
            vucut.kapat();
            vucutAyarla(true);
        }
        if (nesne.acik()) {
            nesne.kapat();
            nesneAyarla(true);
        }
        return gpu;
    }

    void vucutAyarla(boolean ac) {
        if (ac == vucut.acik()) return;
        vucut.kapat();
        if (!ac) return;
        int n = vucut.nesil;
        try {
            PoseLandmarker m = PoseLandmarker.createFromOptions(baglam, PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(temel("pose_landmarker_lite.task", hizlandirici))
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.6f)
                .setMinPosePresenceConfidence(0.6f)
                .setMinTrackingConfidence(0.6f)
                .setResultListener((sonuc, girdi) -> sonucuIsle(vucut, n, () -> dinleyici.vucut(sonuc, girdi.getWidth(), girdi.getHeight())))
                .setErrorListener(h -> hata(vucut, n, "Vücut", h))
                .build());
            vucut.bagla(m::detectAsync, m::close);
        } catch (RuntimeException e) {
            Log.e(ETIKET, "Vücut modeli kurulamadı", e);
            dinleyici.bildir("Vücut modeli yüklenemedi.");
        }
    }

    /** Elindeki nesne: günlük 80 nesneyi bulan algılayıcı (insan hariç). */
    void nesneAyarla(boolean ac) {
        if (ac == nesne.acik()) return;
        nesne.kapat();
        if (!ac) return;
        int n = nesne.nesil;
        try {
            ObjectDetector m = ObjectDetector.createFromOptions(baglam, ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(temel(NesneTanima.ALGILAYICI, hizlandirici))
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMaxResults(5)
                .setScoreThreshold(0.35f)
                .setCategoryDenylist(Collections.singletonList("person"))
                .setResultListener((sonuc, girdi) -> sonucuIsle(nesne, n, () -> dinleyici.nesne(sonuc, girdi)))
                .setErrorListener(h -> hata(nesne, n, "Nesne", h))
                .build());
            nesne.bagla(m::detectAsync, m::close);
        } catch (RuntimeException e) {
            Log.e(ETIKET, "Nesne modeli kurulamadı", e);
            dinleyici.bildir("Nesne modeli yüklenemedi.");
        }
    }

    /** Arka plan ayırma CPU'da çalışır: model küçük, maske doğrudan okunabilir. */
    void arkaPlanAyarla(boolean ac) {
        if (ac == arkaPlan.acik()) return;
        arkaPlan.kapat();
        if (!ac) return;
        int n = arkaPlan.nesil;
        try {
            ImageSegmenter m = ImageSegmenter.createFromOptions(baglam, ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(temel("selfie_segmenter.tflite", Delegate.CPU))
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setOutputConfidenceMasks(true)
                .setOutputCategoryMask(false)
                .setResultListener((sonuc, girdi) -> sonucuIsle(arkaPlan, n, () -> dinleyici.arkaPlan(sonuc, girdi)))
                .setErrorListener(h -> hata(arkaPlan, n, "Arka plan", h))
                .build());
            arkaPlan.bagla(m::segmentAsync, m::close);
        } catch (RuntimeException e) {
            Log.e(ETIKET, "Arka plan modeli kurulamadı", e);
            dinleyici.bildir("Arka plan modeli yüklenemedi.");
        }
    }

    void hepsiniKapat() {
        yuz.kapat();
        el.kapat();
        vucut.kapat();
        arkaPlan.kapat();
        nesne.kapat();
        synchronized (havuz) {
            havuz.clear();
        }
    }

    /** Kameradan gelen kare: boştaki modellere verilir, hepsi meşgulse atlanır. */
    void kareVer(ImageProxy goruntu) {
        try {
            long zaman = SystemClock.uptimeMillis();
            if (tasarruf && zaman - sonKare < TASARRUF_MS) return;
            List<Gorev> alacaklar = new ArrayList<>(5);
            for (Gorev g : new Gorev[] {yuz, el, vucut, arkaPlan, nesne}) if (g.ayir(zaman)) alacaklar.add(g);
            if (alacaklar.isEmpty()) return;
            sonKare = zaman;
            // Görüntü dik (setOutputImageRotationEnabled) ve aynalanmamış; el etiketleri doğru çıkar.
            Kare kare = kareyiKopyala(goruntu);
            kare.kullanan.set(alacaklar.size());
            MPImage girdi = new BitmapImageBuilder(kare.bitmap).build();
            for (Gorev g : alacaklar) {
                g.kare = kare;
                int n = g.nesil;
                BiConsumer<MPImage, Long> gonder = g.gonder;
                try {
                    if (gonder == null) g.bitti(n, false);
                    else gonder.accept(girdi, zaman);
                } catch (RuntimeException e) {
                    Log.e(ETIKET, "Kare modele verilemedi", e);
                    g.bitti(n, false);
                }
            }
        } finally {
            goruntu.close();
        }
    }

    /** Kameranın RGBA tamponunu havuzdaki bir bitmap'e kopyalar; her karede yeni bellek ayrılmaz. */
    private Kare kareyiKopyala(ImageProxy goruntu) {
        int g = goruntu.getWidth();
        int y = goruntu.getHeight();
        ImageProxy.PlaneProxy duzlem = goruntu.getPlanes()[0];
        if (duzlem.getPixelStride() != 4 || duzlem.getRowStride() != g * 4) {
            return new Kare(goruntu.toBitmap(), false);
        }
        Bitmap b = null;
        synchronized (havuz) {
            while (b == null && !havuz.isEmpty()) {
                Bitmap aday = havuz.pollFirst();
                if (aday.getWidth() == g && aday.getHeight() == y) b = aday;
            }
        }
        if (b == null) b = Bitmap.createBitmap(g, y, Bitmap.Config.ARGB_8888);
        ByteBuffer tampon = duzlem.getBuffer();
        tampon.rewind();
        b.copyPixelsFromBuffer(tampon);
        return new Kare(b, true);
    }

    private void havuzaKoy(Bitmap b) {
        synchronized (havuz) {
            if (havuz.size() < HAVUZ_BOYUTU) havuz.addLast(b);
        }
    }

    private void yuzuKur() {
        int n = yuz.nesil;
        FaceLandmarker m = FaceLandmarker.createFromOptions(baglam, FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(temel("face_landmarker.task", hizlandirici))
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.6f)
            .setMinFacePresenceConfidence(0.6f)
            .setMinTrackingConfidence(0.6f)
            // Gülümseme, göz kırpma gibi ifadeler için.
            .setOutputFaceBlendshapes(true)
            .setResultListener((sonuc, girdi) -> sonucuIsle(yuz, n, () -> dinleyici.yuz(sonuc, girdi)))
            .setErrorListener(h -> hata(yuz, n, "Yüz", h))
            .build());
        yuz.bagla(m::detectAsync, m::close);
    }

    private void eliKur() {
        int n = el.nesil;
        GestureRecognizer m = GestureRecognizer.createFromOptions(baglam, GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(temel("gesture_recognizer.task", hizlandirici))
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            // Yanlış el algılamasını azaltmak için varsayılandan (0.5) sıkı.
            .setMinHandDetectionConfidence(0.6f)
            .setMinHandPresenceConfidence(0.6f)
            .setMinTrackingConfidence(0.6f)
            .setResultListener((sonuc, girdi) -> sonucuIsle(el, n, () -> dinleyici.el(sonuc, girdi.getWidth(), girdi.getHeight())))
            .setErrorListener(h -> hata(el, n, "El", h))
            .build());
        el.bagla(m::recognizeAsync, m::close);
    }

    /** Sonucu işler; işlerken hata olsa da model serbest kalır (yoksa bir daha kare almazdı). */
    private static void sonucuIsle(Gorev g, int n, Runnable is) {
        if (n != g.nesil) return;
        try {
            is.run();
        } catch (RuntimeException e) {
            Log.e(ETIKET, "Sonuç işlenemedi", e);
        } finally {
            g.bitti(n, true);
        }
    }

    private BaseOptions temel(String model, Delegate d) {
        return BaseOptions.builder().setModelAssetPath(MODELLER + model).setDelegate(d).build();
    }

    private void hata(Gorev g, int n, String ad, RuntimeException h) {
        Log.e(ETIKET, ad + " modeli hatası", h);
        // Kapatılmış eski bir kurulumdan geç gelen hata yeni kurulumu etkilemesin.
        boolean guncel = n == g.nesil;
        g.bitti(n, false);
        if (guncel && g != arkaPlan) gpuSorunu(g, true);
    }

    /** Saniyedeki sonuç sayısının yumuşatılmış ortalaması. */
    static final class FpsSayaci {
        private long onceki;
        private float deger;

        synchronized void say() {
            long simdi = SystemClock.uptimeMillis();
            if (onceki != 0 && simdi > onceki) {
                float anlik = 1000f / (simdi - onceki);
                deger = deger == 0 ? anlik : deger * 0.9f + anlik * 0.1f;
            }
            onceki = simdi;
        }

        synchronized float deger() {
            return deger;
        }

        synchronized void sifirla() {
            onceki = 0;
            deger = 0;
        }
    }
}
