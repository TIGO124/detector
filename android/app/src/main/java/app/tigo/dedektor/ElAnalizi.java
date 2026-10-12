package app.tigo.dedektor;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

/** El noktalarından (MediaPipe: 21 nokta) parmak sayısı, işaret hali ve Türkçe adlar. */
final class ElAnalizi {

    static final int BILEK = 0;
    static final int ISARET_UCU = 8;
    private static final int BASPARMAK_EKLEM = 3;
    private static final int BASPARMAK_UCU = 4;
    private static final int SERCE_KOKU = 17;
    /** Dört parmak için {orta eklem, uç}. */
    private static final int[][] PARMAKLAR = {{6, 8}, {10, 12}, {14, 16}, {18, 20}};

    private ElAnalizi() {}

    /**
     * Açık parmaklar: [başparmak, işaret, orta, yüzük, serçe]. Elin yönünden bağımsızdır:
     * parmak ucu bileğe orta eklemden daha uzaksa parmak açıktır. Başparmak için serçe
     * parmak kökü kullanılır: açık başparmağın ucu oradan eklemden daha uzaktadır.
     */
    static boolean[] acikParmaklar(List<NormalizedLandmark> n) {
        boolean[] acik = new boolean[5];
        acik[0] = uzaklik(n.get(BASPARMAK_UCU), n.get(SERCE_KOKU)) > uzaklik(n.get(BASPARMAK_EKLEM), n.get(SERCE_KOKU));
        for (int i = 0; i < PARMAKLAR.length; i++) {
            acik[i + 1] = uzaklik(n.get(BILEK), n.get(PARMAKLAR[i][1])) > uzaklik(n.get(BILEK), n.get(PARMAKLAR[i][0]));
        }
        return acik;
    }

    static int parmakSayisi(List<NormalizedLandmark> n) {
        int sayi = 0;
        for (boolean a : acikParmaklar(n)) if (a) sayi++;
        return sayi;
    }

    /** Yalnızca işaret parmağı açık (başparmak serbest): havada çizim için kalem aşağıda. */
    static boolean isaretEdiyor(List<NormalizedLandmark> n) {
        boolean[] a = acikParmaklar(n);
        return a[1] && !a[2] && !a[3] && !a[4];
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

    private static float uzaklik(NormalizedLandmark a, NormalizedLandmark b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
