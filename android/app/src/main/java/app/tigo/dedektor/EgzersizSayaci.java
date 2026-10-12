package app.tigo.dedektor;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Egzersiz tekrarlarını sayar: duruş modeli her karede bir duruş verir (ayakta, squat, ...);
 * tekrar, "alt" duruştan "üst" duruşa geçişte tamamlanır. Tek karelik yanlış duruş saymaz:
 * duruş son karelerin çoğunluğuyla belirlenir.
 */
final class EgzersizSayaci {

    enum Tur {
        SQUAT("Squat", "Squat", "Ayakta"),
        SINAV("Şınav", "Şınav aşağı", "Şınav yukarı"),
        ZIPLAMA("Zıplama", "Kollar açık", "Ayakta");

        final String ad;
        final String alt;
        final String ust;

        Tur(String ad, String alt, String ust) {
            this.ad = ad;
            this.alt = alt;
            this.ust = ust;
        }
    }

    static final float ESIK = 0.6f;
    private static final int GECMIS = 5;

    private final ArrayDeque<String> gecmis = new ArrayDeque<>();
    private Tur tur = Tur.SQUAT;
    private String durum;
    private boolean altta;
    private int sayi;

    synchronized void sec(Tur yeni) {
        tur = yeni;
        sifirla();
    }

    synchronized void sifirla() {
        sayi = 0;
        altta = false;
        durum = null;
        gecmis.clear();
    }

    synchronized Tur tur() {
        return tur;
    }

    synchronized int sayi() {
        return sayi;
    }

    synchronized String durum() {
        return durum;
    }

    /** Yeni kare: en olası duruş ve olasılığı (vücut görünmüyorsa null). Tekrar tamamlandıysa true. */
    synchronized boolean guncelle(String olasiDurum, float olasilik) {
        gecmis.addLast(olasiDurum != null && olasilik >= ESIK ? olasiDurum : "");
        while (gecmis.size() > GECMIS) gecmis.removeFirst();
        Map<String, Integer> sayac = new HashMap<>();
        for (String d : gecmis) if (!d.isEmpty()) sayac.merge(d, 1, Integer::sum);
        for (Map.Entry<String, Integer> e : sayac.entrySet()) {
            if (e.getValue() * 2 > GECMIS) durum = e.getKey();
        }
        if (tur.alt.equals(durum)) {
            altta = true;
        } else if (tur.ust.equals(durum) && altta) {
            altta = false;
            sayi++;
            return true;
        }
        return false;
    }
}
