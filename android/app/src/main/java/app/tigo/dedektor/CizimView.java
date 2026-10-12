package app.tigo.dedektor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Connection;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Kamera önizlemesinin üstüne yüz ağını ve elleri yeşil çizgilerle çizer. Önizleme gibi
 * "fillCenter" ölçeklenir; ön kamerada önizleme aynalı olduğu için çizim de aynalanır.
 */
public class CizimView extends View {

    private static final int YESIL = 0xFF00E676;
    private static final int YESIL_SOLUK = 0x5900E676;
    private static final int YESIL_ACIK = 0xFFB9F6CA;
    private static final int KOYU = 0xFF0B1120;

    private static final int[] AG = diziyeCevir(FaceLandmarker.FACE_LANDMARKS_TESSELATION);
    private static final int[] YUZ_HATLARI = diziyeCevir(
        FaceLandmarker.FACE_LANDMARKS_FACE_OVAL,
        FaceLandmarker.FACE_LANDMARKS_LIPS,
        FaceLandmarker.FACE_LANDMARKS_LEFT_EYE,
        FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE,
        FaceLandmarker.FACE_LANDMARKS_LEFT_EYE_BROW,
        FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE_BROW,
        FaceLandmarker.FACE_LANDMARKS_LEFT_IRIS,
        FaceLandmarker.FACE_LANDMARKS_RIGHT_IRIS
    );
    private static final int[] EL = diziyeCevir(HandLandmarker.HAND_CONNECTIONS);

    private final Paint agKalemi = kalem(YESIL_SOLUK);
    private final Paint hatKalemi = kalem(YESIL);
    private final Paint elKalemi = kalem(YESIL);
    private final Paint eklemKalemi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint etiketZemini = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint etiketYazisi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF etiketKutusu = new RectF();
    private final float dp;

    private volatile FaceLandmarkerResult yuz;
    private volatile HandLandmarkerResult el;
    private volatile int resimGenisligi = 1;
    private volatile int resimYuksekligi = 1;
    private volatile boolean aynali = true;

    private float olcek, kaymaX, kaymaY;
    private float[] cizgiler = new float[0];

    public CizimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        agKalemi.setStrokeWidth(Math.max(1f, 0.6f * dp));
        hatKalemi.setStrokeWidth(1.5f * dp);
        elKalemi.setStrokeWidth(3f * dp);
        eklemKalemi.setColor(YESIL_ACIK);
        etiketZemini.setColor(YESIL);
        etiketYazisi.setColor(KOYU);
        etiketYazisi.setTextSize(15f * dp);
        etiketYazisi.setFakeBoldText(true);
    }

    private static Paint kalem(int renk) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(renk);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        return p;
    }

    /** Bağlantıları [başlangıç, bitiş, başlangıç, bitiş, ...] dizisine çevirir. */
    @SafeVarargs
    private static int[] diziyeCevir(Collection<Connection>... kumeler) {
        List<Connection> hepsi = new ArrayList<>();
        for (Collection<Connection> k : kumeler) hepsi.addAll(k);
        int[] dizi = new int[hepsi.size() * 2];
        for (int i = 0; i < hepsi.size(); i++) {
            dizi[2 * i] = hepsi.get(i).start();
            dizi[2 * i + 1] = hepsi.get(i).end();
        }
        return dizi;
    }

    public void setAynali(boolean aynali) {
        this.aynali = aynali;
        temizle();
    }

    public void temizle() {
        yuz = null;
        el = null;
        postInvalidate();
    }

    public void yuzGuncelle(FaceLandmarkerResult sonuc, int genislik, int yukseklik) {
        resimGenisligi = genislik;
        resimYuksekligi = yukseklik;
        yuz = sonuc;
        postInvalidate();
    }

    public void elGuncelle(HandLandmarkerResult sonuc, int genislik, int yukseklik) {
        resimGenisligi = genislik;
        resimYuksekligi = yukseklik;
        el = sonuc;
        postInvalidate();
    }

    /** MediaPipe etiketi kişinin gerçek elini gösterir (modele aynalanmamış kare verilir). */
    static String elEtiketi(String kategori) {
        if ("Right".equals(kategori)) return "Sağ el";
        if ("Left".equals(kategori)) return "Sol el";
        return "El";
    }

    private float x(NormalizedLandmark n) {
        float ekranX = n.x() * resimGenisligi * olcek + kaymaX;
        return aynali ? getWidth() - ekranX : ekranX;
    }

    private float y(NormalizedLandmark n) {
        return n.y() * resimYuksekligi * olcek + kaymaY;
    }

    @Override
    protected void onDraw(Canvas tuval) {
        super.onDraw(tuval);
        // Önizleme ile aynı "fillCenter" ölçeği: kısa kenar ekranı doldurur, taşan kısım kırpılır.
        olcek = Math.max(getWidth() / (float) resimGenisligi, getHeight() / (float) resimYuksekligi);
        kaymaX = (getWidth() - resimGenisligi * olcek) / 2f;
        kaymaY = (getHeight() - resimYuksekligi * olcek) / 2f;

        FaceLandmarkerResult y = yuz;
        if (y != null) {
            for (List<NormalizedLandmark> noktalar : y.faceLandmarks()) {
                cizgileriCiz(tuval, noktalar, AG, agKalemi);
                cizgileriCiz(tuval, noktalar, YUZ_HATLARI, hatKalemi);
            }
        }

        HandLandmarkerResult e = el;
        if (e != null) {
            List<List<NormalizedLandmark>> eller = e.landmarks();
            for (int i = 0; i < eller.size(); i++) {
                List<NormalizedLandmark> noktalar = eller.get(i);
                cizgileriCiz(tuval, noktalar, EL, elKalemi);
                NormalizedLandmark enUst = noktalar.get(0);
                for (NormalizedLandmark n : noktalar) {
                    tuval.drawCircle(x(n), y(n), 3.5f * dp, eklemKalemi);
                    if (n.y() < enUst.y()) enUst = n;
                }
                List<Category> tur = i < e.handedness().size() ? e.handedness().get(i) : null;
                String ad = elEtiketi(tur == null || tur.isEmpty() ? null : tur.get(0).categoryName());
                etiketCiz(tuval, ad, x(enUst), y(enUst) - 10f * dp);
            }
        }
    }

    private void cizgileriCiz(Canvas tuval, List<NormalizedLandmark> noktalar, int[] baglantilar, Paint kalem) {
        if (cizgiler.length < baglantilar.length * 2) cizgiler = new float[baglantilar.length * 2];
        int k = 0;
        for (int i = 0; i < baglantilar.length; i += 2) {
            if (baglantilar[i] >= noktalar.size() || baglantilar[i + 1] >= noktalar.size()) continue;
            NormalizedLandmark a = noktalar.get(baglantilar[i]);
            NormalizedLandmark b = noktalar.get(baglantilar[i + 1]);
            cizgiler[k++] = x(a);
            cizgiler[k++] = y(a);
            cizgiler[k++] = x(b);
            cizgiler[k++] = y(b);
        }
        tuval.drawLines(cizgiler, 0, k, kalem);
    }

    /** Etiketi verilen noktanın üstüne, ekran dışına taşmadan çizer. */
    private void etiketCiz(Canvas tuval, String metin, float merkezX, float altY) {
        float pay = 8f * dp;
        float genislik = etiketYazisi.measureText(metin) + 2 * pay;
        float yukseklik = etiketYazisi.getTextSize() + 2 * pay * 0.75f;
        float sol = Math.max(0, Math.min(merkezX - genislik / 2, getWidth() - genislik));
        float ust = Math.max(0, Math.min(altY - yukseklik, getHeight() - yukseklik));
        etiketKutusu.set(sol, ust, sol + genislik, ust + yukseklik);
        tuval.drawRoundRect(etiketKutusu, yukseklik / 3, yukseklik / 3, etiketZemini);
        Paint.FontMetrics m = etiketYazisi.getFontMetrics();
        tuval.drawText(metin, sol + pay, etiketKutusu.centerY() - (m.ascent + m.descent) / 2, etiketYazisi);
    }
}
