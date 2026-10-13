package app.tigo.dedektor;

import java.util.ArrayDeque;
import java.util.List;

/**
 * TİD: iki elin kısa zaman penceresi (hareketli harfler için) ve tanınan harflerden oluşan
 * yazı. Harf bir süre sabit tutulunca eklenir; aynı harfi tekrar yazmak için el kısa süre
 * indirilir. Tek iş parçacığından (el modeli) kullanılır; yazıya erişim eşzamanlıdır.
 */
final class TidYazici {

    /** Harf bu kadar kare sabit kalınca yazıya eklenir. */
    static final int SABIT = 8;
    /** Bu kadar kare harf görülmezse aynı harf yeniden eklenebilir. */
    static final int BOS = 5;
    static final int EN_UZUN = 60;

    private static final class Kare {
        final long zaman;
        final float[] oz;
        final float[] bilek;

        Kare(long zaman, float[] oz, float[] bilek) {
            this.zaman = zaman;
            this.oz = oz;
            this.bilek = bilek;
        }
    }

    private final ArrayDeque<Kare> kareler = new ArrayDeque<>();
    private final StringBuilder metin = new StringBuilder();
    private String aday = "";
    private int sabit;
    private String sonEklenen = "";

    /**
     * Yeni el karesi. Pencere hazırsa öznitelikleri verir; el yoksa ya da yeterince kare
     * birikmediyse null (el yoksa bu, "harf yok" sonucu da sayılır).
     */
    float[] kare(List<ElTakibi.ElDurumu> eller, long zaman) {
        ElTakibi.ElDurumu sol = null;
        ElTakibi.ElDurumu sag = null;
        for (ElTakibi.ElDurumu el : eller) {
            if (el.dunya == null) continue;
            if (el.sol()) {
                if (sol == null) sol = el;
            } else if (sag == null) {
                sag = el;
            }
        }
        float[] bilek = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        float[] solBilek = null;
        float[] sagBilek = null;
        if (sol != null) solBilek = new float[] {bilek[0] = sol.noktalar.get(0).x(), bilek[1] = sol.noktalar.get(0).y()};
        if (sag != null) sagBilek = new float[] {bilek[2] = sag.noktalar.get(0).x(), bilek[3] = sag.noktalar.get(0).y()};
        float[] oz = YzOzellik.ikiEl(sol == null ? null : sol.dunya, solBilek, sag == null ? null : sag.dunya, sagBilek);
        kareler.addLast(new Kare(zaman, oz, bilek));
        while (!kareler.isEmpty() && zaman - kareler.peekFirst().zaman > 2 * YzOzellik.PENCERE_MS) kareler.removeFirst();
        if (sol == null && sag == null) {
            sonuc("");
            return null;
        }
        return pencere(zaman);
    }

    /** Son PENCERE_MS'den eşit aralıklı PENCERE kare; yeterince kare yoksa null. */
    private float[] pencere(long zaman) {
        Kare ilk = kareler.peekFirst();
        if (ilk == null || zaman - ilk.zaman < YzOzellik.PENCERE_MS * 2 / 3) return null;
        float[][] oz = new float[YzOzellik.PENCERE][];
        float[][] bilek = new float[YzOzellik.PENCERE][];
        for (int k = 0; k < YzOzellik.PENCERE; k++) {
            long hedef = zaman - YzOzellik.PENCERE_MS + k * YzOzellik.PENCERE_MS / (YzOzellik.PENCERE - 1);
            Kare en = ilk;
            for (Kare t : kareler) if (Math.abs(t.zaman - hedef) < Math.abs(en.zaman - hedef)) en = t;
            oz[k] = en.oz;
            bilek[k] = en.bilek;
        }
        return YzOzellik.pencere(oz, bilek);
    }

    /** Bir pencerenin sonucu: tanınan harf ya da boş. */
    synchronized void sonuc(String harf) {
        if (harf.equals(aday)) sabit++;
        else {
            aday = harf;
            sabit = 1;
        }
        if (harf.isEmpty()) {
            if (sabit >= BOS) sonEklenen = "";
        } else if (sabit == SABIT && !harf.equals(sonEklenen)) {
            metin.append(harf.length() > 1 ? harf + " " : harf);
            if (metin.length() > EN_UZUN) metin.delete(0, metin.length() - EN_UZUN);
            sonEklenen = harf;
        }
    }

    synchronized String metin() {
        return metin.toString();
    }

    synchronized void duzenle(String islem) {
        if ("bosluk".equals(islem)) metin.append(' ');
        else if ("sil".equals(islem) && metin.length() > 0) metin.setLength(metin.length() - 1);
        else if ("temizle".equals(islem)) metin.setLength(0);
    }
}
