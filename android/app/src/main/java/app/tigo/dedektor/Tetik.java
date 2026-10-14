package app.tigo.dedektor;

/**
 * Bir koşul belli süre kesintisiz sürünce bir kez tetikler (ör. zafer işareti 1 saniye
 * tutulunca çek). Sonra bekleme süresi boyunca ve koşul bir kez bitene kadar yeniden
 * tetiklemez: gülümsemeyi sürdürmek art arda fotoğraf çektirmez. Zaman ms.
 */
final class Tetik {

    private final long tutma;
    private final long bekleme;
    private long basladi = -1;
    private long sonTetik = Long.MIN_VALUE / 2;
    /** Son tetiklemeden beri koşul en az bir kez bitti mi. */
    private boolean birakildi = true;

    Tetik(long tutmaMs, long beklemeMs) {
        tutma = tutmaMs;
        bekleme = beklemeMs;
    }

    synchronized boolean guncelle(boolean kosul, long zaman) {
        if (!kosul) {
            basladi = -1;
            birakildi = true;
            return false;
        }
        if (!birakildi || zaman - sonTetik < bekleme) {
            basladi = -1;
            return false;
        }
        if (basladi < 0) basladi = zaman;
        if (zaman - basladi < tutma) return false;
        sonTetik = zaman;
        basladi = -1;
        birakildi = false;
        return true;
    }

    /** Koşulun tutulma oranı (0–1); tutulmuyorsa 0. */
    synchronized float ilerleme(long zaman) {
        if (basladi < 0 || tutma <= 0) return 0f;
        return Math.min(1f, (zaman - basladi) / (float) tutma);
    }

    /** Bekleme şimdiden başlar (ör. fotoğraf çekildi); koşul bir kez bitmeden yeniden tetiklemez. */
    synchronized void beklet(long zaman) {
        sonTetik = zaman;
        basladi = -1;
        birakildi = false;
    }
}
