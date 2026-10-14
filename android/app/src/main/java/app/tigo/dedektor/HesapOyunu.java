package app.tigo.dedektor;

import java.util.Random;

/**
 * Parmak hesabı: 10 soru (toplama, çıkarma; cevap 0–10). Cevap kadar parmak gösterilir ve
 * 1 saniye tutulur; iki el birlikte sayılır. 0 için yumruk gösterilir (el görünmeli).
 */
final class HesapOyunu {

    static final int SORU_SAYISI = 10;
    static final long TUTMA_MS = 1000;

    /** guncelle sonucu. */
    static final int YOK = 0;
    static final int DOGRU = 1;
    static final int BITTI = 2;

    private final Random rastgele;
    private int a;
    private int b;
    private boolean toplama;
    private int cevap = -1;
    private int soruNo;
    private long baslangic;
    private long bitis;
    private int gosterilen = -1;
    private long tutmaBasi;
    private boolean oynaniyor;

    HesapOyunu(Random rastgele) {
        this.rastgele = rastgele;
    }

    synchronized void basla(long zaman) {
        soruNo = 0;
        cevap = -1;
        baslangic = zaman;
        oynaniyor = true;
        yeniSoru(zaman);
    }

    synchronized void durdur() {
        oynaniyor = false;
    }

    /** Art arda aynı cevap gelmesin: eldeki parmaklar hemen yeni soruyu cevaplamasın. */
    private void yeniSoru(long zaman) {
        soruNo++;
        int onceki = cevap;
        do {
            toplama = rastgele.nextBoolean();
            if (toplama) {
                a = rastgele.nextInt(6);
                b = rastgele.nextInt(11 - a);
                cevap = a + b;
            } else {
                a = 1 + rastgele.nextInt(10);
                b = rastgele.nextInt(a + 1);
                cevap = a - b;
            }
        } while (cevap == onceki);
        gosterilen = -1;
        tutmaBasi = zaman;
    }

    /**
     * Yeni el karesi: görünen ellerin açık parmak toplamı; el yoksa elVar false.
     * Sonuç: YOK, DOGRU (yeni soruya geçildi) ya da BITTI.
     */
    synchronized int guncelle(int parmak, boolean elVar, long zaman) {
        if (!oynaniyor) return YOK;
        int deger = elVar ? parmak : -1;
        if (deger != gosterilen) {
            gosterilen = deger;
            tutmaBasi = zaman;
            return YOK;
        }
        if (deger != cevap || zaman - tutmaBasi < TUTMA_MS) return YOK;
        if (soruNo >= SORU_SAYISI) {
            oynaniyor = false;
            bitis = zaman;
            return BITTI;
        }
        yeniSoru(zaman);
        return DOGRU;
    }

    synchronized boolean oynaniyor() {
        return oynaniyor;
    }

    synchronized String soru() {
        return a + (toplama ? " + " : " − ") + b;
    }

    synchronized int cevap() {
        return cevap;
    }

    synchronized int soruNo() {
        return soruNo;
    }

    /** Gösterilen parmak sayısı (-1: el yok). */
    synchronized int gosterilen() {
        return gosterilen;
    }

    /** Doğru cevabın tutulma oranı (0–1). */
    synchronized float ilerleme(long zaman) {
        if (!oynaniyor || gosterilen != cevap) return 0f;
        return Math.min(1f, (zaman - tutmaBasi) / (float) TUTMA_MS);
    }

    synchronized long sureMs(long zaman) {
        return (oynaniyor ? zaman : bitis) - baslangic;
    }
}
