package app.tigo.dedektor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Kamera önizlemesinin üstüne çizer: arka plan katmanı, vücut iskeleti, yüz ağı ya da yüz
 * efektleri, havada çizim, eller ve etiketler. Önizleme gibi "fillCenter" ölçeklenir; ön
 * kamerada önizleme aynalı olduğu için çizim de aynalanır.
 */
public class CizimView extends View {

    /** Oyunların ve kutlamanın çizimi: en üste, arayüz iş parçacığında çizilir. */
    interface Katman {
        void ciz(Canvas tuval, CizimView v);
    }

    /** Vurgu renginin üstündeki yazı (Tasarım dili: "vurgu üstü"). */
    private static final int KOYU = 0xFF04140B;

    private static final int CENE = 152;

    private final Paint agKalemi = kalem();
    private final Paint hatKalemi = kalem();
    private final Paint elKalemi = kalem();
    private final Paint cizimKalemi = kalem();
    private final Paint nesneKalemi = kalem();
    private final Paint cerceveKalemi = kalem();
    private final Paint eklemKalemi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint etiketZemini = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint etiketYazisi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint filtre = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint izgaraKalemi = new Paint();
    private final RectF kutu = new RectF();
    private final Path yol = new Path();
    private final YuzEfektleri efektler = new YuzEfektleri();
    /** Efektler için yüz noktalarının ekran konumu; her karede yeni nesne üretilmesin diye tek örnek. */
    private final EkranNoktalari yuzNoktalari = new EkranNoktalari();

    private final class EkranNoktalari implements YuzEfektleri.Noktalar {
        List<NormalizedLandmark> liste;

        @Override
        public float x(int i) {
            return CizimView.this.x(liste.get(i).x());
        }

        @Override
        public float y(int i) {
            return CizimView.this.y(liste.get(i).y());
        }
    }
    private final float dp;

    private Ayarlar ayarlar;
    private HavadaCizim havadaCizim;
    private volatile FaceLandmarkerResult yuz;
    private volatile List<ElTakibi.ElDurumu> eller = Collections.emptyList();
    private volatile List<String> ifadeler = Collections.emptyList();
    private volatile PoseLandmarkerResult vucut;
    private volatile Bitmap arkaPlan;
    private volatile List<NesneTanima.Nesne> nesneler = Collections.emptyList();
    private volatile int resimGenisligi = 1;
    private volatile int resimYuksekligi = 1;
    private volatile boolean aynali = true;
    /** Pikselleştirme efekti: yüzün küçük resmi ve normalize kutusu. */
    private volatile Bitmap piksel;
    private volatile float[] pikselKutusu;
    private final List<Katman> katmanlar = new CopyOnWriteArrayList<>();
    /** Fotoğraf ya da video için çiziliyor: ızgara çizilmez. Arayüz iş parçacığında. */
    private boolean cekimIcin;

    private float olcek, kaymaX, kaymaY;
    private float[] cizgiler = new float[0];
    private int sonRenk;

    public CizimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        agKalemi.setStrokeWidth(Math.max(1f, 0.6f * dp));
        hatKalemi.setStrokeWidth(1.5f * dp);
        elKalemi.setStrokeWidth(3f * dp);
        cizimKalemi.setStrokeJoin(Paint.Join.ROUND);
        nesneKalemi.setStrokeJoin(Paint.Join.ROUND);
        cerceveKalemi.setStrokeWidth(2f * dp);
        cerceveKalemi.setColor(0xB3FFFFFF);
        cerceveKalemi.setPathEffect(new DashPathEffect(new float[] {12f * dp, 9f * dp}, 0));
        izgaraKalemi.setColor(0x59FFFFFF);
        izgaraKalemi.setStrokeWidth(Math.max(1f, dp));
        etiketYazisi.setColor(KOYU);
        etiketYazisi.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, getResources().getDisplayMetrics()));
        etiketYazisi.setTypeface(Cip.font(context, R.font.manrope_cok_kalin));
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
        nesneler = Collections.emptyList();
        piksel = null;
        postInvalidate();
    }

    void katmanEkle(Katman k) {
        if (!katmanlar.contains(k)) katmanlar.add(k);
        postInvalidate();
    }

    void katmanCikar(Katman k) {
        katmanlar.remove(k);
        postInvalidate();
    }

    /** Pikselleştirme için yüzün küçük resmi (null: yüz yok). */
    void pikselGuncelle(Bitmap kucuk, float[] kutu) {
        pikselKutusu = kutu;
        piksel = kucuk;
    }

    boolean aynali() {
        return aynali;
    }

    /** Ekranın yüksekliği / genişliği. */
    float oran() {
        return getWidth() == 0 ? 1f : getHeight() / (float) getWidth();
    }

    /**
     * Görüntüde normalize noktanın (0–1) ekrandaki yeri, ekran boyuna oranla (0–1); ön kamerada
     * aynalanmış. Ölçeği kendisi hesapladığı için her iş parçacığından çağrılabilir.
     */
    void ekranOrani(float nx, float ny, float[] cikis) {
        int w = getWidth();
        int h = getHeight();
        int rg = resimGenisligi;
        int ry = resimYuksekligi;
        if (w == 0 || h == 0) {
            cikis[0] = nx;
            cikis[1] = ny;
            return;
        }
        float o = Math.max(w / (float) rg, h / (float) ry);
        float sx = nx * rg * o + (w - rg * o) / 2f;
        cikis[0] = (aynali ? w - sx : sx) / w;
        cikis[1] = (ny * ry * o + (h - ry * o) / 2f) / h;
    }

    /**
     * Çizimleri kamera karesi boyutunda (g×y) bir tuvale çizer (video): ekranda kırpılan kenarlar
     * dahil bütün kare görünür. Arayüz iş parçacığından.
     */
    void kareyeCiz(Canvas tuval, int g, int y) {
        if (olcek == 0) return;
        tuval.save();
        tuval.scale(g / (resimGenisligi * olcek), y / (resimYuksekligi * olcek));
        tuval.translate(-kaymaX, -kaymaY);
        cekimIcinCiz(tuval);
        tuval.restore();
    }

    /** Çizimler fotoğrafa ya da videoya: ızgara (yalnızca kadraj yardımı) dışarıda kalır. */
    void cekimIcinCiz(Canvas tuval) {
        cekimIcin = true;
        try {
            draw(tuval);
        } finally {
            cekimIcin = false;
        }
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

    void nesneleriTemizle() {
        nesneler = Collections.emptyList();
        postInvalidate();
    }

    /** Elindeki nesne bölümü: algılanan nesneler (kutular normalize). */
    void nesneGuncelle(List<NesneTanima.Nesne> liste, int g, int y) {
        boyut(g, y);
        nesneler = liste;
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
        if (ayarlar.izgara && !cekimIcin) izgaraCiz(tuval);

        PoseLandmarkerResult v = vucut;
        if (v != null && ayarlar.vucutGerekli()) {
            for (List<NormalizedLandmark> n : v.landmarks()) {
                cizgileriCiz(tuval, n, Baglantilar.VUCUT, elKalemi);
                for (int i = 11; i < n.size(); i++) tuval.drawCircle(x(n.get(i).x()), y(n.get(i).y()), 4f * dp, eklemKalemi);
            }
        }

        FaceLandmarkerResult f = yuz;
        if (f != null) {
            for (List<NormalizedLandmark> n : f.faceLandmarks()) {
                if (mod == Ayarlar.Mod.EFEKT) {
                    yuzNoktalari.liste = n;
                    if (ayarlar.efekt == Ayarlar.Efekt.PIKSEL) pikselCiz(tuval);
                    else efektler.ciz(tuval, yuzNoktalari, ayarlar.efekt, ayarlar.renk);
                    continue;
                }
                if (ayarlar.yuzAgi) cizgileriCiz(tuval, n, Baglantilar.AG, agKalemi);
                cizgileriCiz(tuval, n, Baglantilar.YUZ_HATLARI, hatKalemi);
            }
        }

        if (mod == Ayarlar.Mod.CIZIM && havadaCizim != null) havadaCizimiCiz(tuval);

        if (ayarlar.nesneGerekli()) nesneleriCiz(tuval, nesneler);

        // Yapay zekâ modunda etiketler sonucun kendisi: ayardan bağımsız gösterilir.
        boolean etiket = ayarlar.etiketler || mod == Ayarlar.Mod.YZ;
        elleriCiz(tuval, eller, etiket, mod == Ayarlar.Mod.YZ);

        if (f != null && etiket && !f.faceLandmarks().isEmpty()) ifadeyiYaz(tuval, f, ifadeler);

        for (Katman k : katmanlar) k.ciz(tuval, this);
    }

    /** Üçte bir kuralı çizgileri. */
    private void izgaraCiz(Canvas tuval) {
        float w = getWidth();
        float h = getHeight();
        for (int i = 1; i <= 2; i++) {
            tuval.drawLine(w * i / 3f, 0, w * i / 3f, h, izgaraKalemi);
            tuval.drawLine(0, h * i / 3f, w, h * i / 3f, izgaraKalemi);
        }
    }

    private void pikselCiz(Canvas tuval) {
        Bitmap p = piksel;
        float[] k = pikselKutusu;
        if (p == null || k == null) return;
        float a = x(k[0]);
        float b = x(k[2]);
        kutu.set(Math.min(a, b), y(k[1]), Math.max(a, b), y(k[3]));
        efektler.piksel(tuval, yuzNoktalari, p, kutu, aynali);
    }

    private void renkleriAyarla(int renk) {
        if (renk == sonRenk) return;
        sonRenk = renk;
        hatKalemi.setColor(renk);
        elKalemi.setColor(renk);
        nesneKalemi.setColor(renk);
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
        for (HavadaCizim.Cizgi c : havadaCizim.cizgiler()) {
            float[][] n = c.noktalar;
            if (n.length == 0) continue;
            yol.reset();
            yol.moveTo(x(n[0][0]), y(n[0][1]));
            if (n.length == 1) yol.lineTo(x(n[0][0]) + 0.1f, y(n[0][1]));
            for (int i = 1; i < n.length; i++) yol.lineTo(x(n[i][0]), y(n[i][1]));
            cizimKalemi.setColor(c.renk);
            cizimKalemi.setStrokeWidth(c.kalinlik * dp);
            tuval.drawPath(yol, cizimKalemi);
        }
    }

    /** Ortadaki çerçeve (öğretilen nesneler burada tanınır) ve algılanan nesnelerin kutuları. */
    private void nesneleriCiz(Canvas tuval, List<NesneTanima.Nesne> liste) {
        float[] c = NesneTanima.cerceve(resimGenisligi, resimYuksekligi);
        kutuCiz(tuval, c, cerceveKalemi, 22f);
        for (NesneTanima.Nesne n : liste) {
            nesneKalemi.setStrokeWidth((n.elde ? 3.5f : 2f) * dp);
            kutuCiz(tuval, n.kutu, nesneKalemi, 14f);
            String metin = (n.elde ? "Elinde · " : "") + n.ad + " %" + Math.round(100 * n.puan);
            etiketCiz(tuval, metin, (x(n.kutu[0]) + x(n.kutu[2])) / 2, y(n.kutu[1]) - 4f * dp, true);
        }
    }

    /** Normalize kutu {sol, üst, sağ, alt}; ön kamerada aynalı olduğundan kenarlar sıralanır. */
    private void kutuCiz(Canvas tuval, float[] k, Paint kalem, float koseDp) {
        float a = x(k[0]);
        float b = x(k[2]);
        kutu.set(Math.min(a, b), y(k[1]), Math.max(a, b), y(k[3]));
        tuval.drawRoundRect(kutu, koseDp * dp, koseDp * dp, kalem);
    }

    private void elleriCiz(Canvas tuval, List<ElTakibi.ElDurumu> eller, boolean etiket, boolean yz) {
        for (ElTakibi.ElDurumu el : eller) {
            List<NormalizedLandmark> n = el.noktalar;
            cizgileriCiz(tuval, n, Baglantilar.EL, elKalemi);
            NormalizedLandmark enUst = n.get(0);
            for (NormalizedLandmark nokta : n) {
                tuval.drawCircle(x(nokta.x()), y(nokta.y()), 3.5f * dp, eklemKalemi);
                if (nokta.y() < enUst.y()) enUst = nokta;
            }
            if (!etiket) continue;
            StringBuilder metin = new StringBuilder(el.taraf);
            if (yz) {
                if (el.yzEtiketi != null) metin.append(" · ").append(el.yzEtiketi);
            } else {
                String hareket = ElAnalizi.hareketAdi(el.hareketKodu);
                if (hareket != null) metin.append(" · ").append(hareket);
                metin.append(" · ").append(el.parmak);
            }
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
        float pay = 11f * dp;
        float genislik = etiketYazisi.measureText(metin) + 2 * pay;
        float yukseklik = etiketYazisi.getTextSize() + 10f * dp;
        float ust = ustune ? kenarY - yukseklik : kenarY;
        float sol = Math.max(0, Math.min(merkezX - genislik / 2, getWidth() - genislik));
        ust = Math.max(0, Math.min(ust, getHeight() - yukseklik));
        kutu.set(sol, ust, sol + genislik, ust + yukseklik);
        tuval.drawRoundRect(kutu, yukseklik / 2, yukseklik / 2, etiketZemini);
        Paint.FontMetrics m = etiketYazisi.getFontMetrics();
        tuval.drawText(metin, sol + pay, kutu.centerY() - (m.ascent + m.descent) / 2, etiketYazisi);
    }
}
