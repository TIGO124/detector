package app.tigo.dedektor;

import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Yüzden okunan olaylar: uyku (gözler uzun süre kapalı) ve baş hareketi (başı sallamak "Hayır",
 * başla onaylamak "Evet"). Baş açısı, kısa vadeli ortalamasına göre değerlendirilir: duruş
 * değişince kendini ayarlar; ortalamanın iki yanına kısa sürede iki kez geçmek sallama sayılır.
 * Tek iş parçacığından (yüz modeli) çağrılır.
 */
final class YuzOlaylari {

    enum Bas { EVET, HAYIR }

    static final float GOZ_KAPALI = 0.55f;
    static final float GOZ_ACIK = 0.4f;
    static final long UYKU_MS = 1500;
    static final float YAW_ESIK = 0.06f;
    static final float PITCH_ESIK = 0.04f;
    /** İki yön değişimi bu süre içinde olmalı. */
    static final long SALLAMA_MS = 1500;
    /** "Evet"/"Hayır" bu süre ekranda kalır; bu sürede yenisi algılanmaz. */
    static final long ETIKET_MS = 1500;
    /** Ortalamanın zaman sabiti. */
    private static final float TABAN_MS = 1200f;

    private static final int BURUN = 1;
    private static final int ALIN = 10;
    private static final int CENE = 152;
    private static final int YANAK_SOL = 234;
    private static final int YANAK_SAG = 454;

    /** Bir eksendeki salınım: yön değişimlerinin zamanları. */
    private static final class Eksen {
        final float esik;
        float taban = Float.NaN;
        int yon;
        final ArrayDeque<Long> donusler = new ArrayDeque<>();

        Eksen(float esik) {
            this.esik = esik;
        }

        /** Yeni açı; kısa sürede ikinci yön değişimi olduysa true. */
        boolean guncelle(float deger, float agirlik, long zaman) {
            if (Float.isNaN(taban)) taban = deger;
            float fark = deger - taban;
            taban += (deger - taban) * agirlik;
            int yeni = fark > esik ? 1 : fark < -esik ? -1 : yon;
            if (yeni != yon && yon != 0) donusler.addLast(zaman);
            yon = yeni;
            while (!donusler.isEmpty() && zaman - donusler.peekFirst() > SALLAMA_MS) donusler.removeFirst();
            return donusler.size() >= 2;
        }

        void sifirla() {
            taban = Float.NaN;
            yon = 0;
            donusler.clear();
        }
    }

    private final Eksen yaw = new Eksen(YAW_ESIK);
    private final Eksen pitch = new Eksen(PITCH_ESIK);
    private long sonZaman = -1;
    private long gozKapandi = -1;
    private boolean uyku;
    private Bas bas;
    private long basZamani;

    /**
     * Yeni kare. goz: iki gözün kapalılık puanı (küçük olanı), yaw/pitch: başın açısı (yaw()
     * ve pitch()). Yeni bir baş hareketi algılandıysa onu, yoksa null verir.
     */
    synchronized Bas guncelle(float goz, float yawDegeri, float pitchDegeri, long zaman) {
        if (goz > GOZ_KAPALI) {
            if (gozKapandi < 0) gozKapandi = zaman;
            if (zaman - gozKapandi >= UYKU_MS) uyku = true;
        } else if (goz < GOZ_ACIK) {
            gozKapandi = -1;
            uyku = false;
        }
        float dt = sonZaman < 0 ? 0 : Math.min(500, zaman - sonZaman);
        sonZaman = zaman;
        float agirlik = 1f - (float) Math.exp(-dt / TABAN_MS);
        boolean hayir = yaw.guncelle(yawDegeri, agirlik, zaman);
        boolean evet = pitch.guncelle(pitchDegeri, agirlik, zaman);
        if (bas != null && zaman - basZamani < ETIKET_MS) return null;
        Bas yeni = hayir ? Bas.HAYIR : evet ? Bas.EVET : null;
        if (yeni == null) return null;
        bas = yeni;
        basZamani = zaman;
        yaw.donusler.clear();
        pitch.donusler.clear();
        return yeni;
    }

    /** Yüz görünmüyor: uyku ve baş hareketi sıfırlanır. */
    synchronized void yuzYok() {
        gozKapandi = -1;
        uyku = false;
        sonZaman = -1;
        yaw.sifirla();
        pitch.sifirla();
    }

    synchronized boolean uyku() {
        return uyku;
    }

    /** Ekranda gösterilecek baş hareketi ("Evet"/"Hayır"); süresi geçtiyse null. */
    synchronized String basEtiketi(long zaman) {
        if (bas == null || zaman - basZamani >= ETIKET_MS) return null;
        return bas == Bas.EVET ? "Evet" : "Hayır";
    }

    // ---------- MediaPipe sonucundan değerler ----------

    static float puan(List<Category> puanlar, String ad) {
        for (Category c : puanlar) if (ad.equals(c.categoryName())) return c.score();
        return 0f;
    }

    /** İki göz birlikte ne kadar kapalı (küçük olanı: tek göz kırpmak sayılmaz). */
    static float gozKapali(List<Category> puanlar) {
        return Math.min(puan(puanlar, "eyeBlinkLeft"), puan(puanlar, "eyeBlinkRight"));
    }

    static float gulumseme(List<Category> puanlar) {
        return (puan(puanlar, "mouthSmileLeft") + puan(puanlar, "mouthSmileRight")) / 2f;
    }

    /** Burnun iki yanak arasındaki yeri (-0.5 – 0.5): baş sağa-sola dönünce değişir. */
    static float yaw(List<NormalizedLandmark> n) {
        float sol = n.get(YANAK_SOL).x();
        float sag = n.get(YANAK_SAG).x();
        if (Math.abs(sag - sol) < 1e-4f) return 0f;
        return (n.get(BURUN).x() - sol) / (sag - sol) - 0.5f;
    }

    /** Burnun alın ile çene arasındaki yeri: baş öne-arkaya eğilince değişir. */
    static float pitch(List<NormalizedLandmark> n) {
        float ust = n.get(ALIN).y();
        float alt = n.get(CENE).y();
        if (Math.abs(alt - ust) < 1e-4f) return 0f;
        return (n.get(BURUN).y() - ust) / (alt - ust);
    }
}
