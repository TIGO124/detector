package app.tigo.dedektor;

import com.google.mediapipe.tasks.components.containers.Landmark;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

/**
 * El noktalarından (MediaPipe: 21 nokta) açık parmaklar ve Türkçe adlar. Hesap 3B yapılır:
 * MediaPipe'ın "dünya" noktaları (metre) kullanılınca kameraya doğru uzanan parmak da kısa
 * görünmez; parmak ekleminden bükülme açısına bakılır.
 */
final class ElAnalizi {

    static final int BILEK = 0;
    static final int ISARET_UCU = 8;
    /** Dört parmak için {kök, orta eklem, uç eklem, uç}. */
    private static final int[][] PARMAKLAR = {{5, 6, 7, 8}, {9, 10, 11, 12}, {13, 14, 15, 16}, {17, 18, 19, 20}};
    private static final int BASPARMAK_KOK = 1;
    private static final int BASPARMAK_EKLEM = 2;
    private static final int BASPARMAK_UC_EKLEM = 3;
    private static final int BASPARMAK_UCU = 4;
    private static final int SERCE_KOKU = 17;

    /**
     * Toplam bükülme (kök + orta + uç eklem) bundan azsa parmak açıktır. Ölçülen: rahat açık
     * parmak 27–103°, avuca kıvrık parmak 163–241°; sınır ikisinin ortasında.
     */
    static final float PARMAK_BUKULME_SINIRI = 130f;
    /** Başparmak bundan az bükülmüşse ve ucu serçe kökünden eklemden daha uzaksa açıktır. */
    static final float BASPARMAK_BUKULME_SINIRI = 75f;

    private ElAnalizi() {}

    static float[][] noktalar(List<NormalizedLandmark> n) {
        float[][] p = new float[n.size()][];
        for (int i = 0; i < n.size(); i++) p[i] = new float[] {n.get(i).x(), n.get(i).y(), n.get(i).z()};
        return p;
    }

    static float[][] dunyaNoktalari(List<Landmark> n) {
        float[][] p = new float[n.size()][];
        for (int i = 0; i < n.size(); i++) p[i] = new float[] {n.get(i).x(), n.get(i).y(), n.get(i).z()};
        return p;
    }

    /** Açık parmaklar: [başparmak, işaret, orta, yüzük, serçe]. */
    static boolean[] acikParmaklar(float[][] p) {
        boolean[] acik = new boolean[5];
        float basparmakBukulme = aci(fark(p[BASPARMAK_EKLEM], p[BASPARMAK_KOK]), fark(p[BASPARMAK_UC_EKLEM], p[BASPARMAK_EKLEM]))
            + aci(fark(p[BASPARMAK_UC_EKLEM], p[BASPARMAK_EKLEM]), fark(p[BASPARMAK_UCU], p[BASPARMAK_UC_EKLEM]));
        // Kıvrık başparmağın ucu avucun içine, serçe parmağa doğru gelir.
        acik[0] = uzaklik(p[BASPARMAK_UCU], p[SERCE_KOKU]) > uzaklik(p[BASPARMAK_UC_EKLEM], p[SERCE_KOKU]) * 1.05f
            && basparmakBukulme < BASPARMAK_BUKULME_SINIRI;
        for (int i = 0; i < PARMAKLAR.length; i++) {
            int[] f = PARMAKLAR[i];
            float bukulme = aci(fark(p[f[0]], p[BILEK]), fark(p[f[1]], p[f[0]]))
                + aci(fark(p[f[1]], p[f[0]]), fark(p[f[2]], p[f[1]]))
                + aci(fark(p[f[2]], p[f[1]]), fark(p[f[3]], p[f[2]]));
            acik[i + 1] = bukulme < PARMAK_BUKULME_SINIRI;
        }
        return acik;
    }

    static int parmakSayisi(boolean[] acik) {
        int sayi = 0;
        for (boolean a : acik) if (a) sayi++;
        return sayi;
    }

    /** Yalnızca işaret parmağı açık (başparmak serbest): havada çizim için kalem aşağıda. */
    static boolean isaretEdiyor(boolean[] acik) {
        return acik[1] && !acik[2] && !acik[3] && !acik[4];
    }

    /** MediaPipe el hareketi adını Türkçeye çevirir; tanınmayan hareket için null. */
    static String hareketAdi(String kategori) {
        if (kategori == null) return null;
        switch (kategori) {
            case "Closed_Fist": return "Yumruk";
            case "Open_Palm": return "Açık el";
            case "Pointing_Up": return "İşaret";
            case "Thumb_Down": return "Başparmak aşağı";
            case "Thumb_Up": return "Başparmak yukarı";
            case "Victory": return "Zafer";
            case "ILoveYou": return "Seni seviyorum";
            default: return null;
        }
    }

    /** MediaPipe etiketi kişinin gerçek elini gösterir (modele aynalanmamış kare verilir). */
    static String elEtiketi(String kategori) {
        if ("Right".equals(kategori)) return "Sağ el";
        if ("Left".equals(kategori)) return "Sol el";
        return "El";
    }

    private static float[] fark(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float uzaklik(float[] a, float[] b) {
        float[] d = fark(a, b);
        return (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
    }

    /** İki vektör arasındaki açı (derece). */
    private static float aci(float[] u, float[] v) {
        double nu = Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
        double nv = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (nu == 0 || nv == 0) return 0f;
        double cos = (u[0] * v[0] + u[1] * v[1] + u[2] * v[2]) / (nu * nv);
        return (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }
}
