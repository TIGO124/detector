package app.tigo.dedektor;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

/**
 * Kişi tanıma: yüz, 5 noktadan (gözler, burun ucu, ağız köşeleri) 112x112 standart kalıba
 * hizalanır ve küçük bir evrişimli ağ (bilgisayarda egitim/kimlik.py ile eğitildi) yüzü 128
 * sayılık bir imzaya çevirir. Aynı kişinin imzaları birbirine yakındır; kayıtlı kişilerle
 * benzerlik eşiği geçerse adı verilir. Hizalama egitim/hizala.py ile aynı hesaptır.
 */
final class KisiTanima {

    static final int BOYUT = 112;
    static final int IMZA_BOYUTU = 128;
    /**
     * 112x112 standart (ArcFace) kalıp: görüntüde soldaki göz, sağdaki göz, burun ucu, soldaki ve
     * sağdaki ağız köşesi. Model dosyası kendi kalıbını verir (MediaPipe noktalarına uydurulmuş).
     */
    static final float[] KALIP = {
        38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f, 41.5493f, 92.3655f, 70.7299f, 92.2041f,
    };
    /** MediaPipe yüz ağı: iris merkezleri, burun ucu, ağız köşeleri. */
    private static final int[] GOZLER = {468, 473};
    private static final int BURUN = 1;
    private static final int[] AGIZ = {61, 291};
    private static final String ETIKET = "Dedektor";

    /** Bu benzerliğin altı "tanınmadı" (bilgisayarda LFW doğrulama çiftlerinden seçildi). */
    final float esik;
    private final float[] kalip;
    private final Interpreter yorumlayici;
    private final ByteBuffer girdi = ByteBuffer.allocateDirect(BOYUT * BOYUT * 3 * 4).order(ByteOrder.nativeOrder());
    private final float[][] cikti = new float[1][IMZA_BOYUTU];
    private final int[] pikseller = new int[BOYUT * BOYUT];
    private final Bitmap hizali = Bitmap.createBitmap(BOYUT, BOYUT, Bitmap.Config.ARGB_8888);
    private final Canvas tuval = new Canvas(hizali);
    private final Paint filtre = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matris = new Matrix();
    private final ExecutorService is = Executors.newSingleThreadExecutor();
    /** Ağ çalışırken yeni yüz alınmaz (hizalı bitmap ve tamponlar tek). */
    private final AtomicBoolean mesgul = new AtomicBoolean();

    private KisiTanima(Interpreter yorumlayici, float esik, float[] kalip) {
        this.yorumlayici = yorumlayici;
        this.esik = esik;
        this.kalip = kalip;
    }

    /** Model APK'da yoksa ya da bu telefonda açılamazsa null. */
    static KisiTanima olustur(Context baglam) {
        try {
            JSONObject bilgi = new JSONObject(oku(baglam, "yz/kimlik.json"));
            Interpreter.Options secenekler = new Interpreter.Options().setNumThreads(2);
            Interpreter y = new Interpreter(modelDosyasi(baglam, "yz/kimlik.tflite"), secenekler);
            float[] kalip = KALIP;
            JSONArray k = bilgi.optJSONArray("kalip");
            if (k != null && k.length() == 5) {
                kalip = new float[10];
                for (int i = 0; i < 5; i++) {
                    kalip[2 * i] = (float) k.getJSONArray(i).getDouble(0);
                    kalip[2 * i + 1] = (float) k.getJSONArray(i).getDouble(1);
                }
            }
            return new KisiTanima(y, (float) bilgi.getDouble("esik"), kalip);
        } catch (Exception | LinkageError e) {
            Log.e(ETIKET, "Kişi tanıma modeli açılamadı", e);
            return null;
        }
    }

    /**
     * Yüzü hizalayıp imzasını arka planda hesaplar; ağ meşgulse false. Resim yalnızca bu çağrı
     * sırasında okunur (kamera karesi hemen sonra yeniden kullanılabilir).
     */
    boolean imzaCikar(List<NormalizedLandmark> yuz, Bitmap resim, Consumer<float[]> sonuc) {
        if (yuz.size() <= GOZLER[1] || !mesgul.compareAndSet(false, true)) return false;
        float[] m = benzerlik(besNokta(yuz, resim.getWidth(), resim.getHeight()), kalip);
        matris.setValues(new float[] {m[0], m[1], m[2], m[3], m[4], m[5], 0, 0, 1});
        tuval.drawColor(0xFF000000);
        tuval.drawBitmap(resim, matris, filtre);
        is.execute(() -> {
            try {
                sonuc.accept(calistir());
            } catch (RuntimeException e) {
                Log.e(ETIKET, "Yüz imzası çıkarılamadı", e);
            } finally {
                mesgul.set(false);
            }
        });
        return true;
    }

    /** Model girdisi: 112x112 RGB, 0–255 (normalizasyon modelin içinde). Çıktı birim uzunlukta. */
    private float[] calistir() {
        hizali.getPixels(pikseller, 0, BOYUT, 0, 0, BOYUT, BOYUT);
        girdi.rewind();
        for (int p : pikseller) {
            girdi.putFloat((p >> 16) & 0xFF);
            girdi.putFloat((p >> 8) & 0xFF);
            girdi.putFloat(p & 0xFF);
        }
        girdi.rewind();
        yorumlayici.run(girdi, cikti);
        return SinirAgi.birimVektor(cikti[0]);
    }

    /** En benzer kayıtlı kişi; kimse eşiği geçmezse null. */
    String tani(float[] imza, Map<String, List<float[]>> kisiler) {
        Eslesme e = enBenzer(imza, kisiler);
        return e != null && e.benzerlik >= esik ? e.ad : null;
    }

    static final class Eslesme {
        final String ad;
        final float benzerlik;

        Eslesme(String ad, float benzerlik) {
            this.ad = ad;
            this.benzerlik = benzerlik;
        }
    }

    /** Her kişinin imzalarından en yakını (kosinüs benzerliği) ile karşılaştırır. */
    static Eslesme enBenzer(float[] imza, Map<String, List<float[]>> kayitlar) {
        Eslesme en = null;
        for (Map.Entry<String, List<float[]>> k : kayitlar.entrySet()) {
            for (float[] v : k.getValue()) {
                float b = 0;
                for (int i = 0; i < v.length; i++) b += v[i] * imza[i];
                if (en == null || b > en.benzerlik) en = new Eslesme(k.getKey(), b);
            }
        }
        return en;
    }

    void kapat() {
        is.execute(yorumlayici::close);
        is.shutdown();
    }

    // ---------- Hizalama (egitim/hizala.py ile aynı) ----------

    /** 478 normalize yüz noktasından görüntüdeki 5 nokta (piksel): gözler ve ağız soldan sağa. */
    static float[] besNokta(List<NormalizedLandmark> yuz, int g, int y) {
        float[] o = new float[10];
        sirali(yuz, GOZLER, g, y, o, 0);
        o[4] = yuz.get(BURUN).x() * g;
        o[5] = yuz.get(BURUN).y() * y;
        sirali(yuz, AGIZ, g, y, o, 6);
        return o;
    }

    private static void sirali(List<NormalizedLandmark> yuz, int[] ikili, int g, int y, float[] o, int bas) {
        NormalizedLandmark a = yuz.get(ikili[0]);
        NormalizedLandmark b = yuz.get(ikili[1]);
        if (b.x() < a.x() || (b.x() == a.x() && b.y() < a.y())) {
            NormalizedLandmark t = a;
            a = b;
            b = t;
        }
        o[bas] = a.x() * g;
        o[bas + 1] = a.y() * y;
        o[bas + 2] = b.x() * g;
        o[bas + 3] = b.y() * y;
    }

    /**
     * Kaynak noktaları hedefe en iyi taşıyan benzerlik dönüşümü (ölçek + dönme + öteleme), en
     * küçük kareler: [a -b tx; b a ty]. Noktalar {x0, y0, x1, y1, ...}.
     */
    static float[] benzerlik(float[] kaynak, float[] hedef) {
        int n = kaynak.length / 2;
        double kx = 0, ky = 0, hx = 0, hy = 0;
        for (int i = 0; i < n; i++) {
            kx += kaynak[2 * i] / n;
            ky += kaynak[2 * i + 1] / n;
            hx += hedef[2 * i] / n;
            hy += hedef[2 * i + 1] / n;
        }
        double pay1 = 0, pay2 = 0, payda = 0;
        for (int i = 0; i < n; i++) {
            double x = kaynak[2 * i] - kx, y = kaynak[2 * i + 1] - ky;
            double u = hedef[2 * i] - hx, v = hedef[2 * i + 1] - hy;
            pay1 += x * u + y * v;
            pay2 += x * v - y * u;
            payda += x * x + y * y;
        }
        if (payda == 0) payda = 1;
        double a = pay1 / payda, b = pay2 / payda;
        double tx = hx - (a * kx - b * ky), ty = hy - (b * kx + a * ky);
        return new float[] {(float) a, (float) -b, (float) tx, (float) b, (float) a, (float) ty};
    }

    // ---------- Dosyalar ----------

    private static MappedByteBuffer modelDosyasi(Context baglam, String yol) throws IOException {
        try (AssetFileDescriptor fd = baglam.getAssets().openFd(yol);
             FileInputStream g = new FileInputStream(fd.getFileDescriptor())) {
            return g.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    /** APK içindeki metin dosyası. */
    static String oku(Context baglam, String yol) throws IOException {
        try (InputStream g = baglam.getAssets().open(yol)) {
            return YzTanima.metin(g);
        }
    }
}
