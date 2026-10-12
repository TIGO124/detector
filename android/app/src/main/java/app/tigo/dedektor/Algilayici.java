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
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * MediaPipe modellerini kurar ve kameradan gelen kareleri boştaki modellere dağıtır. Her model
 * kendi iş parçacığında çalışır; bir öncekini bitirmeyen model yeni kare almaz. Vücut ve arka
 * plan modelleri yalnızca açıkken çalışır. Bütün metotlar analiz iş parçacığından çağrılır.
 */
final class Algilayici {

    interface Dinleyici {
        void yuz(FaceLandmarkerResult sonuc, int genislik, int yukseklik);

        void el(GestureRecognizerResult sonuc, int genislik, int yukseklik);

        void vucut(PoseLandmarkerResult sonuc, int genislik, int yukseklik);

        void arkaPlan(ImageSegmenterResult sonuc, MPImage girdi);

        void bildir(String mesaj);
    }

    /** Modeller web dosyalarının yanında, APK'nın assets/public klasöründe durur. */
    private static final String MODELLER = "public/mediapipe/models/";
    private static final String ETIKET = "Dedektor";

    /** Tek bir modelin durumu. */
    static final class Gorev {
        final AtomicBoolean mesgul = new AtomicBoolean();
        final FpsSayaci fps = new FpsSayaci();
        volatile BiConsumer<MPImage, Long> gonder;
        volatile Runnable kapatici;

        boolean acik() {
            return gonder != null;
        }

        void bitti() {
            fps.say();
            mesgul.set(false);
        }

        void kapat() {
            Runnable k = kapatici;
            gonder = null;
            kapatici = null;
            if (k != null) k.run();
            mesgul.set(false);
            fps.sifirla();
        }
    }

    final Gorev yuz = new Gorev();
    final Gorev el = new Gorev();
    final Gorev vucut = new Gorev();
    final Gorev arkaPlan = new Gorev();

    private final Context baglam;
    private final Dinleyici dinleyici;
    private Delegate hizlandirici = Delegate.CPU;

    Algilayici(Context baglam, Dinleyici dinleyici) {
        this.baglam = baglam;
        this.dinleyici = dinleyici;
    }

    /** Yüz ve el modellerini verilen hızlandırıcıyla kurar; GPU olmazsa CPU'ya düşer. Sonuç: GPU kullanılıyor mu. */
    boolean temelModelleriKur(boolean gpu) {
        yuz.kapat();
        el.kapat();
        hizlandirici = gpu ? Delegate.GPU : Delegate.CPU;
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
        if (vucut.acik()) vucutAyarla(true);
        return gpu;
    }

    void vucutAyarla(boolean ac) {
        vucut.kapat();
        if (!ac) return;
        try {
            PoseLandmarker m = PoseLandmarker.createFromOptions(baglam, PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(temel("pose_landmarker_lite.task", hizlandirici))
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setResultListener((sonuc, girdi) -> {
                    dinleyici.vucut(sonuc, girdi.getWidth(), girdi.getHeight());
                    vucut.bitti();
                })
                .setErrorListener(h -> hata(vucut, "Vücut", h))
                .build());
            vucut.kapatici = m::close;
            vucut.gonder = m::detectAsync;
        } catch (RuntimeException e) {
            Log.e(ETIKET, "Vücut modeli kurulamadı", e);
            dinleyici.bildir("Vücut modeli yüklenemedi.");
        }
    }

    /** Arka plan ayırma CPU'da çalışır: model küçük, maske doğrudan okunabilir. */
    void arkaPlanAyarla(boolean ac) {
        if (ac == arkaPlan.acik()) return;
        arkaPlan.kapat();
        if (!ac) return;
        try {
            ImageSegmenter m = ImageSegmenter.createFromOptions(baglam, ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(temel("selfie_segmenter.tflite", Delegate.CPU))
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setOutputConfidenceMasks(true)
                .setOutputCategoryMask(false)
                .setResultListener((sonuc, girdi) -> {
                    dinleyici.arkaPlan(sonuc, girdi);
                    arkaPlan.bitti();
                })
                .setErrorListener(h -> hata(arkaPlan, "Arka plan", h))
                .build());
            arkaPlan.kapatici = m::close;
            arkaPlan.gonder = m::segmentAsync;
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
    }

    /** Kameradan gelen kare: boştaki modellere verilir, hepsi meşgulse atlanır. */
    void kareVer(ImageProxy kare) {
        try {
            List<Gorev> alacaklar = new ArrayList<>(4);
            for (Gorev g : new Gorev[] {yuz, el, vucut, arkaPlan}) {
                if (g.acik() && g.mesgul.compareAndSet(false, true)) alacaklar.add(g);
            }
            if (alacaklar.isEmpty()) return;
            // Görüntü dik (setOutputImageRotationEnabled) ve aynalanmamış; el etiketleri doğru çıkar.
            Bitmap resim = kare.toBitmap();
            MPImage girdi = new BitmapImageBuilder(resim).build();
            long zaman = SystemClock.uptimeMillis();
            for (Gorev g : alacaklar) {
                BiConsumer<MPImage, Long> gonder = g.gonder;
                try {
                    if (gonder == null) g.mesgul.set(false);
                    else gonder.accept(girdi, zaman);
                } catch (RuntimeException e) {
                    Log.e(ETIKET, "Kare modele verilemedi", e);
                    g.mesgul.set(false);
                }
            }
        } finally {
            kare.close();
        }
    }

    private void yuzuKur() {
        FaceLandmarker m = FaceLandmarker.createFromOptions(baglam, FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(temel("face_landmarker.task", hizlandirici))
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.6f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            // Gülümseme, göz kırpma gibi ifadeler için.
            .setOutputFaceBlendshapes(true)
            .setResultListener((sonuc, girdi) -> {
                dinleyici.yuz(sonuc, girdi.getWidth(), girdi.getHeight());
                yuz.bitti();
            })
            .setErrorListener(h -> hata(yuz, "Yüz", h))
            .build());
        yuz.kapatici = m::close;
        yuz.gonder = m::detectAsync;
    }

    private void eliKur() {
        GestureRecognizer m = GestureRecognizer.createFromOptions(baglam, GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(temel("gesture_recognizer.task", hizlandirici))
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener((sonuc, girdi) -> {
                dinleyici.el(sonuc, girdi.getWidth(), girdi.getHeight());
                el.bitti();
            })
            .setErrorListener(h -> hata(el, "El", h))
            .build());
        el.kapatici = m::close;
        el.gonder = m::recognizeAsync;
    }

    private BaseOptions temel(String model, Delegate d) {
        return BaseOptions.builder().setModelAssetPath(MODELLER + model).setDelegate(d).build();
    }

    private static void hata(Gorev g, String ad, RuntimeException h) {
        Log.e(ETIKET, ad + " modeli hatası", h);
        g.mesgul.set(false);
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
