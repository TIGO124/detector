package app.tigo.dedektor;

import java.util.ArrayList;
import java.util.List;

/**
 * İşaret parmağıyla havada çizim. Yalnızca işaret parmağı açıkken parmak ucu çizgiye eklenir;
 * el başka şekle geçince kalem kalkar; yumruk bir süre tutulunca her şey silinir.
 * Noktalar görüntüye göre normalize (0–1) tutulur.
 */
final class HavadaCizim {

    static final long SILME_SURESI_MS = 700;
    /** Titremeyi azaltmak için bundan yakın noktalar eklenmez. */
    private static final float EN_KISA_ADIM = 0.004f;
    /** Bundan fazla nokta birikirse en eski çizgiler silinir. */
    static final int EN_COK_NOKTA = 3000;

    private final List<List<float[]>> cizgiler = new ArrayList<>();
    private List<float[]> etkin;
    private float[] yumusak;
    private long yumrukBasi = -1;
    private int noktaSayisi;

    synchronized void guncelle(float x, float y, boolean ciziyor, boolean yumruk, long zamanMs) {
        if (yumruk) {
            etkin = null;
            if (yumrukBasi < 0) yumrukBasi = zamanMs;
            else if (zamanMs - yumrukBasi >= SILME_SURESI_MS) temizle();
            return;
        }
        yumrukBasi = -1;
        if (!ciziyor) {
            etkin = null;
            return;
        }
        if (etkin == null) {
            etkin = new ArrayList<>();
            cizgiler.add(etkin);
            yumusak = new float[] {x, y};
        } else {
            // Parmak ucunun titremesini yumuşat.
            yumusak = new float[] {yumusak[0] * 0.5f + x * 0.5f, yumusak[1] * 0.5f + y * 0.5f};
        }
        float[] son = etkin.isEmpty() ? null : etkin.get(etkin.size() - 1);
        if (son == null || Math.hypot(son[0] - yumusak[0], son[1] - yumusak[1]) > EN_KISA_ADIM) {
            etkin.add(yumusak);
            noktaSayisi++;
            while (noktaSayisi > EN_COK_NOKTA && cizgiler.size() > 1) noktaSayisi -= cizgiler.remove(0).size();
        }
    }

    /** El görünmüyorsa kalem kalkar. */
    synchronized void elYok() {
        etkin = null;
        yumrukBasi = -1;
    }

    synchronized void temizle() {
        cizgiler.clear();
        noktaSayisi = 0;
        etkin = null;
    }

    /** Çizim için anlık kopya. */
    synchronized List<float[][]> cizgiler() {
        List<float[][]> kopya = new ArrayList<>(cizgiler.size());
        for (List<float[]> c : cizgiler) kopya.add(c.toArray(new float[0][]));
        return kopya;
    }
}
