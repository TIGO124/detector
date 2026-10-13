package app.tigo.dedektor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Elindeki nesne. Hazır algılayıcı (EfficientDet-Lite0) günlük 80 nesneyi bulur; bir elin
 * noktaları nesnenin kutusunun içindeyse nesne "elde" sayılır. Kendi nesnelerin ise ekranın
 * ortasındaki çerçeveden çıkarılan görüntü imzasıyla (MobileNetV3) en yakın kayda bakılarak
 * tanınır: nesneyi kavrayan el çoğu zaman algılanamadığı için burada elin yeri kullanılmaz.
 */
final class NesneTanima {

    /**
     * Çerçeve: görüntünün kısa kenarının bu oranında, ortada bir kare. Uzun ekranlarda (20:9)
     * 3:4 kamera görüntüsünün yanları kırpılır ve görünen genişlik kısa kenarın ~%60'ıdır;
     * çerçeve bunun içinde kalmalı.
     */
    static final float CERCEVE = 0.5f;
    /** Öğretilen nesneye bundan az benzeyen görüntü tanınmaz (ölçüm: aynı nesne ~0.8, başka ~0.15). */
    static final float ESIK = 0.6f;
    /** Bu adla öğretilen kayıt "nesne yok" demektir (boş çerçeve, arka plan). */
    static final String BOS = "Boş";
    static final int IMZA_BOYUTU = 1024;
    /** İmza modeli değişirse kayıtlar uyumsuz olur; dosya adı buna bağlıdır. */
    static final String IMZA_SURUMU = "mnv3k1";
    static final String ALGILAYICI = "efficientdet_lite0.tflite";
    private static final String IMZA_MODELI = "public/mediapipe/models/mobilenet_v3_small_embedder.tflite";
    private static final int GIRDI = 224;
    /** El noktalarından en az bu kadarı kutudaysa nesne eldedir (21 noktadan). */
    private static final int ELDE_NOKTA = 4;
    private static final String ETIKET = "Dedektor";

    /** Ekranda gösterilen nesne: kutu normalize (0–1) {sol, üst, sağ, alt}. */
    static final class Nesne {
        final String ad;
        final float puan;
        final float[] kutu;
        final boolean elde;

        Nesne(String ad, float puan, float[] kutu, boolean elde) {
            this.ad = ad;
            this.puan = puan;
            this.kutu = kutu;
            this.elde = elde;
        }
    }

    private final Context baglam;
    private final ExecutorService is = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mesgul = new AtomicBoolean();
    private final Bitmap kirpim = Bitmap.createBitmap(GIRDI, GIRDI, Bitmap.Config.ARGB_8888);
    private final Canvas tuval = new Canvas(kirpim);
    private final Paint filtre = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect kaynak = new Rect();
    private final Rect hedef = new Rect(0, 0, GIRDI, GIRDI);
    private ImageEmbedder gomucu;
    private boolean denendi;

    NesneTanima(Context baglam) {
        this.baglam = baglam.getApplicationContext();
    }

    // ---------- Hazır nesneler ----------

    /** Algılayıcı sonucu: Türkçe ad, normalize kutu ve elde mi. g, y: görüntü boyutu. */
    static List<Nesne> nesneler(ObjectDetectorResult s, int g, int y, List<ElTakibi.ElDurumu> eller) {
        if (s.detections().isEmpty()) return Collections.emptyList();
        List<Nesne> liste = new ArrayList<>();
        for (Detection d : s.detections()) {
            if (d.categories().isEmpty()) continue;
            Category c = d.categories().get(0);
            RectF b = d.boundingBox();
            float[] kutu = {b.left / g, b.top / y, b.right / g, b.bottom / y};
            boolean elde = false;
            for (ElTakibi.ElDurumu el : eller) elde |= elde(kutu, el.noktalar);
            liste.add(new Nesne(ad(c.categoryName()), c.score(), kutu, elde));
        }
        return liste;
    }

    /** El noktalarından en az ELDE_NOKTA tanesi kutunun (her yandan %10 büyütülmüş) içinde mi. */
    static boolean elde(float[] kutu, List<NormalizedLandmark> el) {
        float payX = 0.1f * (kutu[2] - kutu[0]);
        float payY = 0.1f * (kutu[3] - kutu[1]);
        int icinde = 0;
        for (NormalizedLandmark p : el) {
            if (p.x() >= kutu[0] - payX && p.x() <= kutu[2] + payX && p.y() >= kutu[1] - payY && p.y() <= kutu[3] + payY) icinde++;
        }
        return icinde >= ELDE_NOKTA;
    }

    /**
     * Büyük yazıda gösterilecek nesne: eldeki (en yüksek puanlı), yoksa merkezi çerçevenin
     * içinde olan; hiçbiri yoksa null. cerceve: normalize çerçeve kutusu.
     */
    static Nesne anaNesne(List<Nesne> liste, float[] cerceve) {
        Nesne elde = null;
        Nesne ortada = null;
        for (Nesne n : liste) {
            if (n.elde && (elde == null || n.puan > elde.puan)) elde = n;
            float cx = (n.kutu[0] + n.kutu[2]) / 2;
            float cy = (n.kutu[1] + n.kutu[3]) / 2;
            boolean icinde = cx >= cerceve[0] && cx <= cerceve[2] && cy >= cerceve[1] && cy <= cerceve[3];
            if (icinde && (ortada == null || n.puan > ortada.puan)) ortada = n;
        }
        return elde != null ? elde : ortada;
    }

    /** Ortadaki kare çerçeve, normalize {sol, üst, sağ, alt}. */
    static float[] cerceve(int g, int y) {
        float k = CERCEVE * Math.min(g, y);
        float w = k / g / 2;
        float h = k / y / 2;
        return new float[] {0.5f - w, 0.5f - h, 0.5f + w, 0.5f + h};
    }

    // ---------- Öğretilen nesneler ----------

    /**
     * Çerçevedeki görüntünün imzasını arka planda çıkarır; meşgulse false. Kare yalnızca bu
     * çağrı sırasında okunur (kamera karesi hemen sonra yeniden kullanılabilir).
     */
    boolean imzaCikar(Bitmap kare, Consumer<float[]> sonuc) {
        if (!mesgul.compareAndSet(false, true)) return false;
        float[] c = cerceve(kare.getWidth(), kare.getHeight());
        kaynak.set(Math.round(c[0] * kare.getWidth()), Math.round(c[1] * kare.getHeight()),
            Math.round(c[2] * kare.getWidth()), Math.round(c[3] * kare.getHeight()));
        tuval.drawBitmap(kare, kaynak, hedef, filtre);
        is.execute(() -> {
            try {
                ImageEmbedder m = model();
                if (m != null) {
                    float[] v = m.embed(new BitmapImageBuilder(kirpim).build()).embeddingResult().embeddings().get(0).floatEmbedding();
                    if (v.length == IMZA_BOYUTU) sonuc.accept(v);
                }
            } catch (RuntimeException e) {
                Log.e(ETIKET, "Nesne imzası çıkarılamadı", e);
            } finally {
                mesgul.set(false);
            }
        });
        return true;
    }

    /** Model ilk kullanımda (imza iş parçacığında) açılır. */
    private ImageEmbedder model() {
        if (!denendi) {
            denendi = true;
            try {
                gomucu = ImageEmbedder.createFromOptions(baglam, ImageEmbedder.ImageEmbedderOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(IMZA_MODELI).build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setL2Normalize(true)
                    .build());
            } catch (RuntimeException e) {
                Log.e(ETIKET, "Nesne imza modeli açılamadı", e);
            }
        }
        return gomucu;
    }

    void kapat() {
        is.execute(() -> {
            if (gomucu != null) gomucu.close();
        });
        is.shutdown();
    }

    // ---------- Türkçe adlar ----------

    private static final Map<String, String> ADLAR = new HashMap<>();

    static {
        String[] ikili = {
            "person", "İnsan", "bicycle", "Bisiklet", "car", "Araba", "motorcycle", "Motosiklet",
            "airplane", "Uçak", "bus", "Otobüs", "train", "Tren", "truck", "Kamyon", "boat", "Tekne",
            "traffic light", "Trafik ışığı", "fire hydrant", "Yangın musluğu", "stop sign", "Dur işareti",
            "parking meter", "Parkmetre", "bench", "Bank", "bird", "Kuş", "cat", "Kedi", "dog", "Köpek",
            "horse", "At", "sheep", "Koyun", "cow", "İnek", "elephant", "Fil", "bear", "Ayı",
            "zebra", "Zebra", "giraffe", "Zürafa", "backpack", "Sırt çantası", "umbrella", "Şemsiye",
            "handbag", "El çantası", "tie", "Kravat", "suitcase", "Bavul", "frisbee", "Frizbi",
            "skis", "Kayak", "snowboard", "Snowboard", "sports ball", "Top", "kite", "Uçurtma",
            "baseball bat", "Beyzbol sopası", "baseball glove", "Beyzbol eldiveni", "skateboard", "Kaykay",
            "surfboard", "Sörf tahtası", "tennis racket", "Tenis raketi", "bottle", "Şişe",
            "wine glass", "Kadeh", "cup", "Bardak", "fork", "Çatal", "knife", "Bıçak", "spoon", "Kaşık",
            "bowl", "Kase", "banana", "Muz", "apple", "Elma", "sandwich", "Sandviç", "orange", "Portakal",
            "broccoli", "Brokoli", "carrot", "Havuç", "hot dog", "Sosisli", "pizza", "Pizza",
            "donut", "Donut", "cake", "Pasta", "chair", "Sandalye", "couch", "Kanepe",
            "potted plant", "Saksı bitkisi", "bed", "Yatak", "dining table", "Masa", "toilet", "Klozet",
            "tv", "Televizyon", "laptop", "Dizüstü bilgisayar", "mouse", "Fare", "remote", "Kumanda",
            "keyboard", "Klavye", "cell phone", "Telefon", "microwave", "Mikrodalga", "oven", "Fırın",
            "toaster", "Tost makinesi", "sink", "Lavabo", "refrigerator", "Buzdolabı", "book", "Kitap",
            "clock", "Saat", "vase", "Vazo", "scissors", "Makas", "teddy bear", "Oyuncak ayı",
            "hair drier", "Saç kurutma makinesi", "toothbrush", "Diş fırçası",
        };
        for (int i = 0; i < ikili.length; i += 2) ADLAR.put(ikili[i], ikili[i + 1]);
    }

    /** Algılayıcının İngilizce adının Türkçesi (bilinmiyorsa kendisi). */
    static String ad(String ingilizce) {
        String a = ADLAR.get(ingilizce);
        return a != null ? a : ingilizce;
    }

    static int adSayisi() {
        return ADLAR.size();
    }
}
