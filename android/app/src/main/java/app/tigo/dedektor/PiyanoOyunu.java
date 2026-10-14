package app.tigo.dedektor;

import java.util.ArrayList;
import java.util.List;

/**
 * Havada piyano: ekranın üst bölümünde yan yana yedi tuş (Do–Si). Parmak ucu bir tuşa girince
 * nota bir kez çalar; aynı tuşu yeniden çalmak için parmak tuştan çıkmalı. Konumlar ekrana
 * oranla (0–1) ve ekranda göründüğü gibi (aynalanmış): Do soldadır.
 */
final class PiyanoOyunu {

    static final int TUS = 7;
    static final String[] NOTALAR = {"Do", "Re", "Mi", "Fa", "Sol", "La", "Si"};
    /** Tuşların dikey aralığı (ekran yüksekliğine oranla). */
    static final float UST = 0.17f;
    static final float ALT = 0.42f;

    private boolean[] basili = new boolean[TUS];
    private int sonNota = -1;

    /** Ekran oranındaki noktanın tuşu; tuşların dışındaysa -1. */
    static int tus(float x, float y) {
        if (y < UST || y > ALT || x < 0 || x >= 1) return -1;
        return Math.min(TUS - 1, (int) (x * TUS));
    }

    /** Parmak uçları (ekran oranında {x, y}); yeni basılan tuşlar. */
    synchronized List<Integer> guncelle(List<float[]> parmaklar) {
        boolean[] simdi = new boolean[TUS];
        for (float[] p : parmaklar) {
            int t = tus(p[0], p[1]);
            if (t >= 0) simdi[t] = true;
        }
        List<Integer> yeni = new ArrayList<>();
        for (int i = 0; i < TUS; i++) {
            if (simdi[i] && !basili[i]) {
                yeni.add(i);
                sonNota = i;
            }
        }
        basili = simdi;
        return yeni;
    }

    synchronized boolean basili(int tus) {
        return basili[tus];
    }

    /** Son çalınan nota (-1: henüz yok). */
    synchronized int sonNota() {
        return sonNota;
    }

    synchronized void sifirla() {
        basili = new boolean[TUS];
        sonNota = -1;
    }
}
