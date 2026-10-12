package app.tigo.dedektor;

import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Landmark;
import java.util.List;

/**
 * Yapay zekâ modellerinin girdileri (öznitelikler). Bilgisayarda eğitilen modeller için
 * aynı hesap egitim/ozellik.py'de birebir vardır; ikisi birlikte değiştirilmelidir.
 */
final class YzOzellik {

    private YzOzellik() {}

    // ---------- El (kendi hareketlerin) ----------

    static final int EL_BOYUTU = 63;

    /**
     * Tek elin 21 noktası (3B "dünya" noktaları): bilek başlangıç, ölçek bilekten orta parmak
     * köküne uzaklık. Sol el aynalanır: hareket hangi elle yapılırsa yapılsın aynı görünür.
     */
    static float[] el(float[][] dunya, boolean solEl) {
        float[] o = new float[EL_BOYUTU];
        float olcek = uzaklik(dunya[0], dunya[9]);
        if (olcek <= 0) olcek = 1;
        for (int i = 0; i < 21; i++) {
            float x = (dunya[i][0] - dunya[0][0]) / olcek;
            o[3 * i] = solEl ? -x : x;
            o[3 * i + 1] = (dunya[i][1] - dunya[0][1]) / olcek;
            o[3 * i + 2] = (dunya[i][2] - dunya[0][2]) / olcek;
        }
        return o;
    }

    // ---------- İki el (TİD harfleri) ----------

    /** Bir karede: sol el 63 + sağ el 63 + iki varlık bayrağı + sağ bileğin sol bileğe göre konumu (2). */
    static final int IKI_EL_BOYUTU = 2 * EL_BOYUTU + 4;
    /** Pencere: son ~0.6 sn'den eşit aralıklı kare sayısı (hareketli harfler için). */
    static final int PENCERE = 6;
    static final long PENCERE_MS = 600;

    /**
     * İki elin bir karedeki öznitelikleri. Eller aynalanmaz: TİD'de hangi elin ne yaptığı önemli.
     * solBilek/sagBilek: görüntüdeki (normalize) bilek konumu; elin yoksa null.
     */
    static float[] ikiEl(float[][] solDunya, float[] solBilek, float[][] sagDunya, float[] sagBilek) {
        float[] o = new float[IKI_EL_BOYUTU];
        if (solDunya != null) System.arraycopy(el(solDunya, false), 0, o, 0, EL_BOYUTU);
        if (sagDunya != null) System.arraycopy(el(sagDunya, false), 0, o, EL_BOYUTU, EL_BOYUTU);
        o[2 * EL_BOYUTU] = solDunya != null ? 1f : 0f;
        o[2 * EL_BOYUTU + 1] = sagDunya != null ? 1f : 0f;
        if (solBilek != null && sagBilek != null) {
            o[2 * EL_BOYUTU + 2] = (sagBilek[0] - solBilek[0]) * 4f;
            o[2 * EL_BOYUTU + 3] = (sagBilek[1] - solBilek[1]) * 4f;
        }
        return o;
    }

    /** Penceredeki bir kare: iki el + her bileğin son kareye göre kayması (2 + 2). */
    static final int KARE_BOYUTU = IKI_EL_BOYUTU + 4;
    static final int PENCERE_BOYUTU = PENCERE * KARE_BOYUTU;

    /**
     * Hareketli harfler için kısa zaman penceresi: PENCERE kare (eskiden yeniye) yan yana. Elin
     * şekli kadar nereye gittiği de önemli; bu yüzden her bileğin son kareye göre kayması eklenir.
     * kareler: ikiEl öznitelikleri. bilekler: {solX, solY, sağX, sağY}, eli olmayan için NaN.
     */
    static float[] pencere(float[][] kareler, float[][] bilekler) {
        float[] o = new float[PENCERE_BOYUTU];
        float[] son = bilekler[PENCERE - 1];
        for (int k = 0; k < PENCERE; k++) {
            int b = k * KARE_BOYUTU;
            System.arraycopy(kareler[k], 0, o, b, IKI_EL_BOYUTU);
            for (int j = 0; j < 4; j++) {
                float d = bilekler[k][j] - son[j];
                o[b + IKI_EL_BOYUTU + j] = Float.isNaN(d) ? 0f : d * 4f;
            }
        }
        return o;
    }

    // ---------- Yüz (duygu) ----------

    static final int YUZ_BOYUTU = 52;

    /** MediaPipe'ın 52 yüz hareketi puanı, model sırasıyla (Category.index). */
    static float[] yuz(List<Category> puanlar) {
        float[] o = new float[YUZ_BOYUTU];
        for (Category c : puanlar) if (c.index() >= 0 && c.index() < YUZ_BOYUTU) o[c.index()] = c.score();
        return o;
    }

    // ---------- Vücut (egzersiz) ----------

    static final int VUCUT_BOYUTU = 13;

    /**
     * Vücut duruşu: eklem açıları (dirsek, omuz, kalça, diz; 0–1 = 0–180°), gövdenin dikeyle
     * açısı, bileklerin omuza göre yüksekliği, ayak ve bilek açıklığı. Boy ve uzaklıktan bağımsız.
     * Girdi: MediaPipe vücut "dünya" noktaları (metre, y aşağı).
     */
    static float[] vucut(float[][] p) {
        float[] o = new float[VUCUT_BOYUTU];
        o[0] = aci(p[11], p[13], p[15]);
        o[1] = aci(p[12], p[14], p[16]);
        o[2] = aci(p[13], p[11], p[23]);
        o[3] = aci(p[14], p[12], p[24]);
        o[4] = aci(p[11], p[23], p[25]);
        o[5] = aci(p[12], p[24], p[26]);
        o[6] = aci(p[23], p[25], p[27]);
        o[7] = aci(p[24], p[26], p[28]);
        float[] omuz = orta(p[11], p[12]);
        float[] kalca = orta(p[23], p[24]);
        float[] govde = fark(omuz, kalca);
        float govdeBoyu = Math.max(uzunluk(govde), 1e-4f);
        o[8] = vektorAcisi(govde, new float[] {0, -1, 0});
        o[9] = (omuz[1] - p[15][1]) / govdeBoyu;
        o[10] = (omuz[1] - p[16][1]) / govdeBoyu;
        float kalcaGenisligi = Math.max(uzaklik(p[23], p[24]), 1e-4f);
        float omuzGenisligi = Math.max(uzaklik(p[11], p[12]), 1e-4f);
        o[11] = uzaklik(p[27], p[28]) / kalcaGenisligi;
        o[12] = uzaklik(p[15], p[16]) / omuzGenisligi;
        return o;
    }

    static float[][] noktalar(List<Landmark> l) {
        float[][] p = new float[l.size()][];
        for (int i = 0; i < p.length; i++) p[i] = new float[] {l.get(i).x(), l.get(i).y(), l.get(i).z()};
        return p;
    }

    // ---------- Yardımcılar ----------

    /** b köşesindeki açı, 0–1 (180° = 1). */
    static float aci(float[] a, float[] b, float[] c) {
        return vektorAcisi(fark(a, b), fark(c, b));
    }

    static float vektorAcisi(float[] u, float[] v) {
        double nu = uzunluk(u), nv = uzunluk(v);
        if (nu == 0 || nv == 0) return 0f;
        double cos = (u[0] * v[0] + u[1] * v[1] + u[2] * v[2]) / (nu * nv);
        return (float) (Math.acos(Math.max(-1, Math.min(1, cos))) / Math.PI);
    }

    private static float[] fark(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] orta(float[] a, float[] b) {
        return new float[] {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
    }

    private static float uzunluk(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    static float uzaklik(float[] a, float[] b) {
        return uzunluk(fark(a, b));
    }
}
