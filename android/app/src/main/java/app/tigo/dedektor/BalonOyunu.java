package app.tigo.dedektor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Balon patlatma: 30 saniye boyunca alttan balonlar yükselir, işaret parmağı ucu değen balon
 * patlar. Konumlar ekrana oranla (0–1): x genişliğe, y yüksekliğe göre; çarpışma ekran
 * oranıyla düzeltilmiş uzaklıkla (yuvarlak kalsın diye) hesaplanır.
 */
final class BalonOyunu {

    static final long SURE_MS = 30_000;
    /** Balon yarıçapı (ekran genişliğine oranla). */
    static final float YARICAP = 0.075f;
    /** Patlayan balon bu kadar süre patlama olarak çizilir. */
    static final long PATLAMA_MS = 350;
    static final int[] RENKLER = {0xFFFF5252, 0xFFFFC83D, 0xFF29B6F6, 0xFF00E676, 0xFFAB47BC, 0xFFFF7043};

    static final class Balon {
        float x;
        float y;
        final float hiz;
        final int renk;
        long patladi = -1;

        Balon(float x, float y, float hiz, int renk) {
            this.x = x;
            this.y = y;
            this.hiz = hiz;
            this.renk = renk;
        }

        Balon kopya() {
            Balon b = new Balon(x, y, hiz, renk);
            b.patladi = patladi;
            return b;
        }
    }

    private final Random rastgele;
    private final List<Balon> balonlar = new ArrayList<>();
    private long baslangic;
    private long sonAdim;
    private long sonrakiBalon;
    private int puan;
    private boolean oynaniyor;

    BalonOyunu(Random rastgele) {
        this.rastgele = rastgele;
    }

    synchronized void basla(long zaman) {
        balonlar.clear();
        puan = 0;
        baslangic = zaman;
        sonAdim = zaman;
        sonrakiBalon = zaman;
        oynaniyor = true;
    }

    synchronized void durdur() {
        oynaniyor = false;
        balonlar.clear();
    }

    /**
     * Zamanı ilerletir: balonlar yükselir, yenileri çıkar, parmak uçlarına (ekran oranında
     * {x, y}) değenler patlar. oran: ekran yüksekliği / genişliği. Patlayan balon sayısı;
     * süre bittiyse -1.
     */
    synchronized int adim(long zaman, List<float[]> parmaklar, float oran) {
        if (!oynaniyor) return 0;
        if (zaman - baslangic >= SURE_MS) {
            oynaniyor = false;
            balonlar.clear();
            return -1;
        }
        float dt = Math.max(0, Math.min(200, zaman - sonAdim)) / 1000f;
        sonAdim = zaman;
        while (zaman >= sonrakiBalon) {
            float x = YARICAP * 1.5f + rastgele.nextFloat() * (1 - YARICAP * 3);
            float hiz = 0.17f + rastgele.nextFloat() * 0.13f;
            balonlar.add(new Balon(x, 1.1f, hiz, RENKLER[rastgele.nextInt(RENKLER.length)]));
            // Zaman ilerledikçe balonlar sıklaşır.
            long aralik = 650 - Math.min(250, (zaman - baslangic) / 100);
            sonrakiBalon += aralik / 2 + rastgele.nextInt((int) aralik);
        }
        int patlayan = 0;
        for (Iterator<Balon> i = balonlar.iterator(); i.hasNext(); ) {
            Balon b = i.next();
            if (b.patladi >= 0) {
                if (zaman - b.patladi > PATLAMA_MS) i.remove();
                continue;
            }
            b.y -= b.hiz * dt;
            if (b.y < -0.15f) {
                i.remove();
                continue;
            }
            for (float[] p : parmaklar) {
                if (Math.hypot(p[0] - b.x, (p[1] - b.y) * oran) < YARICAP * 1.25f) {
                    b.patladi = zaman;
                    puan++;
                    patlayan++;
                    break;
                }
            }
        }
        return patlayan;
    }

    synchronized boolean oynaniyor() {
        return oynaniyor;
    }

    synchronized int puan() {
        return puan;
    }

    synchronized long kalanMs(long zaman) {
        return oynaniyor ? Math.max(0, SURE_MS - (zaman - baslangic)) : 0;
    }

    /** Çizim için anlık kopya. */
    synchronized List<Balon> balonlar() {
        List<Balon> k = new ArrayList<>(balonlar.size());
        for (Balon b : balonlar) k.add(b.kopya());
        return k;
    }
}
