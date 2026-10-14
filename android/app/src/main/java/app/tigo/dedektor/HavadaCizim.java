package app.tigo.dedektor;

import java.util.ArrayList;
import java.util.List;

/**
 * İşaret parmağıyla havada çizim. Yalnızca işaret parmağı açıkken parmak ucu çizgiye eklenir;
 * el başka şekle geçince kalem kalkar; yumruk bir süre tutulunca her şey silinir. Her çizgi
 * çizildiği andaki kalem rengini ve kalınlığını taşır; son çizgi geri alınabilir.
 * Noktalar görüntüye göre normalize (0–1) tutulur.
 */
final class HavadaCizim {

    static final long SILME_SURESI_MS = 700;
    /** Titremeyi azaltmak için bundan yakın noktalar eklenmez. */
    private static final float EN_KISA_ADIM = 0.004f;
    /** Bundan fazla nokta birikirse en eski çizgiler silinir. */
    static final int EN_COK_NOKTA = 3000;

    /** Çizim için anlık kopya: renk, kalınlık (dp) ve noktalar. */
    static final class Cizgi {
        final int renk;
        final float kalinlik;
        final float[][] noktalar;

        Cizgi(int renk, float kalinlik, float[][] noktalar) {
            this.renk = renk;
            this.kalinlik = kalinlik;
            this.noktalar = noktalar;
        }
    }

    private static final class Etkin {
        final int renk;
        final float kalinlik;
        final List<float[]> noktalar = new ArrayList<>();

        Etkin(int renk, float kalinlik) {
            this.renk = renk;
            this.kalinlik = kalinlik;
        }
    }

    private final List<Etkin> cizgiler = new ArrayList<>();
    private Etkin etkin;
    private float[] yumusak;
    private long yumrukBasi = -1;
    private int noktaSayisi;
    private int renk = 0xFF00E676;
    private float kalinlik = 8f;

    /** Yeni çizgilerin rengi ve kalınlığı (dp); çizilmiş çizgiler değişmez. */
    synchronized void kalem(int renk, float kalinlik) {
        this.renk = renk;
        this.kalinlik = kalinlik;
        etkin = null;
    }

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
            etkin = new Etkin(renk, kalinlik);
            cizgiler.add(etkin);
            yumusak = new float[] {x, y};
        } else {
            // Parmak ucunun titremesini yumuşat.
            yumusak = new float[] {yumusak[0] * 0.5f + x * 0.5f, yumusak[1] * 0.5f + y * 0.5f};
        }
        List<float[]> n = etkin.noktalar;
        float[] son = n.isEmpty() ? null : n.get(n.size() - 1);
        if (son == null || Math.hypot(son[0] - yumusak[0], son[1] - yumusak[1]) > EN_KISA_ADIM) {
            n.add(yumusak);
            noktaSayisi++;
            while (noktaSayisi > EN_COK_NOKTA && cizgiler.size() > 1) noktaSayisi -= cizgiler.remove(0).noktalar.size();
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

    /** Son çizgiyi siler; silinecek çizgi yoksa false. */
    synchronized boolean geriAl() {
        if (cizgiler.isEmpty()) return false;
        noktaSayisi -= cizgiler.remove(cizgiler.size() - 1).noktalar.size();
        etkin = null;
        return true;
    }

    /** Çizim için anlık kopya. */
    synchronized List<Cizgi> cizgiler() {
        List<Cizgi> kopya = new ArrayList<>(cizgiler.size());
        for (Etkin c : cizgiler) kopya.add(new Cizgi(c.renk, c.kalinlik, c.noktalar.toArray(new float[0][])));
        return kopya;
    }
}
