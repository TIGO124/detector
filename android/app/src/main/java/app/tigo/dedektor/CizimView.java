package app.tigo.dedektor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import com.google.mediapipe.tasks.components.containers.Connection;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Kamera önizlemesinin üstüne çizer: arka plan katmanı, vücut iskeleti, yüz ağı ya da yüz
 * efektleri, havada çizim, eller ve etiketler. Önizleme gibi "fillCenter" ölçeklenir; ön
 * kamerada önizleme aynalı olduğu için çizim de aynalanır.
 */
public class CizimView extends View {

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
    /** Vücut iskeleti; yüz noktaları (0–10) yüz ağıyla çakışmasın diye atlanır. */
    private static final int[] VUCUT = vucutBaglantilari();
    private static final int CENE = 152;

    private final Paint agKalemi = kalem();
    private final Paint hatKalemi = kalem();
    private final Paint elKalemi = kalem();
    private final Paint cizimKalemi = kalem();
    private final Paint eklemKalemi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint etiketZemini = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint etiketYazisi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint filtre = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF kutu = new RectF();
    private final Path yol = new Path();
    private final YuzEfektleri efektler = new YuzEfektleri();
    private final float dp;

    private Ayarlar ayarlar;
    private HavadaCizim havadaCizim;
    private volatile FaceLandmarkerResult yuz;
    private volatile List<ElTakibi.ElDurumu> eller = Collections.emptyList();
    private volatile List<String> ifadeler = Collections.emptyList();
    private volatile PoseLandmarkerResult vucut;
    private volatile Bitmap arkaPlan;
    private volatile int resimGenisligi = 1;
    private volatile int resimYuksekligi = 1;
    private volatile boolean aynali = true;

    private float olcek, kaymaX, kaymaY;
    private float[] cizgiler = new float[0];
    private int sonRenk;

    public CizimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        agKalemi.setStrokeWidth(Math.max(1f, 0.6f * dp));
        hatKalemi.setStrokeWidth(1.5f * dp);
        elKalemi.setStrokeWidth(3f * dp);
        cizimKalemi.setStrokeWidth(6f * dp);
        cizimKalemi.setStrokeJoin(Paint.Join.ROUND);
        etiketYazisi.setColor(KOYU);
        etiketYazisi.setTextSize(15f * dp);
        etiketYazisi.setFakeBoldText(true);
    }

    void baglan(Ayarlar ayarlar, HavadaCizim havadaCizim) {
        this.ayarlar = ayarlar;
        this.havadaCizim = havadaCizim;
    }

    private static Paint kalem() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
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

    private static int[] vucutBaglantilari() {
        List<Connection> secili = new ArrayList<>();
        for (Connection b : PoseLandmarker.POSE_LANDMARKS) if (b.start() > 10 && b.end() > 10) secili.add(b);
        return diziyeCevir(secili);
    }

    void setAynali(boolean aynali) {
        this.aynali = aynali;
        temizle();
    }

    void temizle() {
        yuz = null;
        eller = Collections.emptyList();
        ifadeler = Collections.emptyList();
        vucut = null;
        arkaPlan = null;
        postInvalidate();
    }

    private void boyut(int genislik, int yukseklik) {
        resimGenisligi = genislik;
        resimYuksekligi = yukseklik;
    }

    void yuzGuncelle(FaceLandmarkerResult s, List<String> ifadeler, int g, int y) {
        boyut(g, y);
        yuz = s;
        this.ifadeler = ifadeler;
        postInvalidate();
    }

    /** Eller, karelere göre sabitlenmiş etiketleriyle (ElTakibi). */
    void elGuncelle(List<ElTakibi.ElDurumu> eller, int g, int y) {
        boyut(g, y);
        this.eller = eller;
        postInvalidate();
    }

    void vucutGuncelle(PoseLandmarkerResult s, int g, int y) {
        boyut(g, y);
        vucut = s;
        postInvalidate();
    }

    void arkaPlanGuncelle(Bitmap katman) {
        arkaPlan = katman;
        postInvalidate();
    }

    void vucutuTemizle() {
        vucut = null;
        postInvalidate();
    }

    void arkaPlaniTemizle() {
        arkaPlan = null;
        postInvalidate();
    }

    /** Normalize x koordinatının (0–1) ekrandaki konumu. */
    float x(float nx) {
        float ekranX = nx * resimGenisligi * olcek + kaymaX;
        return aynali ? getWidth() - ekranX : ekranX;
    }

    float y(float ny) {
        return ny * resimYuksekligi * olcek + kaymaY;
    }

    @Override
    protected void onDraw(Canvas tuval) {
        super.onDraw(tuval);
        if (ayarlar == null) return;
        // Önizleme ile aynı "fillCenter" ölçeği: kısa kenar ekranı doldurur, taşan kısım kırpılır.
        olcek = Math.max(getWidth() / (float) resimGenisligi, getHeight() / (float) resimYuksekligi);
        kaymaX = (getWidth() - resimGenisligi * olcek) / 2f;
        kaymaY = (getHeight() - resimYuksekligi * olcek) / 2f;
        renkleriAyarla(ayarlar.renk);
        Ayarlar.Mod mod = ayarlar.mod;

        Bitmap katman = arkaPlan;
        if (katman != null && ayarlar.arkaPlan != Ayarlar.ArkaPlan.KAPALI) arkaPlaniCiz(tuval, katman);

        PoseLandmarkerResult v = vucut;
        if (v != null && ayarlar.vucut) {
            for (List<NormalizedLandmark> n : v.landmarks()) {
                cizgileriCiz(tuval, n, VUCUT, elKalemi);
                for (int i = 11; i < n.size(); i++) tuval.drawCircle(x(n.get(i).x()), y(n.get(i).y()), 4f * dp, eklemKalemi);
            }
        }

        FaceLandmarkerResult f = yuz;
        if (f != null) {
            for (List<NormalizedLandmark> n : f.faceLandmarks()) {
                if (mod == Ayarlar.Mod.EFEKT) {
                    efektler.ciz(tuval, new YuzEfektleri.Noktalar() {
                        @Override public float x(int i) { return CizimView.this.x(n.get(i).x()); }
                        @Override public float y(int i) { return CizimView.this.y(n.get(i).y()); }
                    }, ayarlar.efekt, ayarlar.renk);
                    continue;
                }
                if (ayarlar.yuzAgi) cizgileriCiz(tuval, n, AG, agKalemi);
                cizgileriCiz(tuval, n, YUZ_HATLARI, hatKalemi);
            }
        }

        if (mod == Ayarlar.Mod.CIZIM && havadaCizim != null) havadaCizimiCiz(tuval);

        elleriCiz(tuval, eller);

        if (f != null && ayarlar.etiketler && !f.faceLandmarks().isEmpty()) ifadeyiYaz(tuval, f, ifadeler);
    }

    private void renkleriAyarla(int renk) {
        if (renk == sonRenk) return;
        sonRenk = renk;
        hatKalemi.setColor(renk);
        elKalemi.setColor(renk);
        cizimKalemi.setColor(renk);
        etiketZemini.setColor(renk);
        agKalemi.setColor((renk & 0x00FFFFFF) | 0x59000000);
        // Eklemler renkten biraz açık
        int r = ((renk >> 16) & 0xFF) + (255 - ((renk >> 16) & 0xFF)) * 3 / 5;
        int g = ((renk >> 8) & 0xFF) + (255 - ((renk >> 8) & 0xFF)) * 3 / 5;
        int b = (renk & 0xFF) + (255 - (renk & 0xFF)) * 3 / 5;
        eklemKalemi.setColor(0xFF000000 | (r << 16) | (g << 8) | b);
    }

    private void arkaPlaniCiz(Canvas tuval, Bitmap katman) {
        tuval.save();
        if (aynali) tuval.scale(-1f, 1f, getWidth() / 2f, getHeight() / 2f);
        kutu.set(kaymaX, kaymaY, kaymaX + resimGenisligi * olcek, kaymaY + resimYuksekligi * olcek);
        tuval.drawBitmap(katman, null, kutu, filtre);
        tuval.restore();
    }

    private void havadaCizimiCiz(Canvas tuval) {
        for (float[][] cizgi : havadaCizim.cizgiler()) {
            if (cizgi.length == 0) continue;
            yol.reset();
            yol.moveTo(x(cizgi[0][0]), y(cizgi[0][1]));
            if (cizgi.length == 1) yol.lineTo(x(cizgi[0][0]) + 0.1f, y(cizgi[0][1]));
            for (int i = 1; i < cizgi.length; i++) yol.lineTo(x(cizgi[i][0]), y(cizgi[i][1]));
            tuval.drawPath(yol, cizimKalemi);
        }
    }

    private void elleriCiz(Canvas tuval, List<ElTakibi.ElDurumu> eller) {
        for (ElTakibi.ElDurumu el : eller) {
            List<NormalizedLandmark> n = el.noktalar;
            cizgileriCiz(tuval, n, EL, elKalemi);
            NormalizedLandmark enUst = n.get(0);
            for (NormalizedLandmark nokta : n) {
                tuval.drawCircle(x(nokta.x()), y(nokta.y()), 3.5f * dp, eklemKalemi);
                if (nokta.y() < enUst.y()) enUst = nokta;
            }
            if (!ayarlar.etiketler) continue;
            StringBuilder metin = new StringBuilder(el.taraf);
            String hareket = ElAnalizi.hareketAdi(el.hareketKodu);
            if (hareket != null) metin.append(" · ").append(hareket);
            metin.append(" · ").append(el.parmak);
            etiketCiz(tuval, metin.toString(), x(enUst.x()), y(enUst.y()) - 10f * dp, true);
        }
    }

    private void ifadeyiYaz(Canvas tuval, FaceLandmarkerResult f, List<String> ifadeler) {
        if (ifadeler.isEmpty()) return;
        NormalizedLandmark cene = f.faceLandmarks().get(0).get(CENE);
        etiketCiz(tuval, TextUtils.join(" · ", ifadeler), x(cene.x()), y(cene.y()) + 12f * dp, false);
    }

    private void cizgileriCiz(Canvas tuval, List<NormalizedLandmark> noktalar, int[] baglantilar, Paint kalem) {
        if (cizgiler.length < baglantilar.length * 2) cizgiler = new float[baglantilar.length * 2];
        int k = 0;
        for (int i = 0; i < baglantilar.length; i += 2) {
            if (baglantilar[i] >= noktalar.size() || baglantilar[i + 1] >= noktalar.size()) continue;
            NormalizedLandmark a = noktalar.get(baglantilar[i]);
            NormalizedLandmark b = noktalar.get(baglantilar[i + 1]);
            cizgiler[k++] = x(a.x());
            cizgiler[k++] = y(a.y());
            cizgiler[k++] = x(b.x());
            cizgiler[k++] = y(b.y());
        }
        tuval.drawLines(cizgiler, 0, k, kalem);
    }

    /** Etiketi noktanın üstüne (ya da altına) ortalar; ekran dışına taşmaz. */
    private void etiketCiz(Canvas tuval, String metin, float merkezX, float kenarY, boolean ustune) {
        float pay = 8f * dp;
        float genislik = etiketYazisi.measureText(metin) + 2 * pay;
        float yukseklik = etiketYazisi.getTextSize() + 1.5f * pay;
        float ust = ustune ? kenarY - yukseklik : kenarY;
        float sol = Math.max(0, Math.min(merkezX - genislik / 2, getWidth() - genislik));
        ust = Math.max(0, Math.min(ust, getHeight() - yukseklik));
        kutu.set(sol, ust, sol + genislik, ust + yukseklik);
        tuval.drawRoundRect(kutu, yukseklik / 3, yukseklik / 3, etiketZemini);
        Paint.FontMetrics m = etiketYazisi.getFontMetrics();
        tuval.drawText(metin, sol + pay, kutu.centerY() - (m.ascent + m.descent) / 2, etiketYazisi);
    }
}
