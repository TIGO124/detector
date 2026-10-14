package app.tigo.dedektor;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Egzersiz tekrarlarını sayar: duruş modeli her karede bir duruş verir (ayakta, squat, ...);
 * tekrar, "alt" duruştan "üst" duruşa geçişte tamamlanır. Tek karelik yanlış duruş saymaz:
 * duruş son karelerin çoğunluğuyla belirlenir. Plank tekrar değil süredir: plank duruşunda
 * geçen saniyeler sayılır. Hedef (tekrar ya da saniye) verilirse bir kez "tamamlandı" der.
 */
final class EgzersizSayaci {

    enum Tur {
        SQUAT("Squat", "Squat", "Ayakta"),
        SINAV("Şınav", "Şınav aşağı", "Şınav yukarı"),
        ZIPLAMA("Zıplama", "Kollar açık", "Ayakta"),
        /** Gövde yere paralel tutulur (modelde şınav duruşları); sayı saniyedir. */
        PLANK("Plank", "Şınav aşağı", "Şınav yukarı");

        final String ad;
        final String alt;
        final String ust;

        Tur(String ad, String alt, String ust) {
            this.ad = ad;
            this.alt = alt;
            this.ust = ust;
        }

        boolean sureli() {
            return this == PLANK;
        }
    }

    static final float ESIK = 0.6f;
    private static final int GECMIS = 5;
    /** Kareler arası bundan uzun boşluk (ör. kişi kadrajdan çıktı) plank süresine eklenmez. */
    private static final long EN_UZUN_ADIM_MS = 1000;

    private final ArrayDeque<String> gecmis = new ArrayDeque<>();
    private Tur tur = Tur.SQUAT;
    private String durum;
    private boolean altta;
    private int sayi;
    private long plankMs;
    private long sonZaman = -1;
    private int hedef;
    private boolean hedefTamam;

    synchronized void sec(Tur yeni) {
        tur = yeni;
        sifirla();
    }

    synchronized void sifirla() {
        sayi = 0;
        plankMs = 0;
        sonZaman = -1;
        altta = false;
        durum = null;
        hedefTamam = false;
        gecmis.clear();
    }

    /** Hedef: tekrar ya da plankta saniye (0 = yok). */
    synchronized void hedef(int yeni) {
        hedef = Math.max(0, yeni);
        hedefTamam = hedef > 0 && sayi >= hedef;
    }

    synchronized int hedef() {
        return hedef;
    }

    synchronized Tur tur() {
        return tur;
    }

    /** Tekrar sayısı; plankta tutulan saniye. */
    synchronized int sayi() {
        return sayi;
    }

    synchronized String durum() {
        return durum;
    }

    /** Plank duruşu şu an tutuluyor mu. */
    synchronized boolean tutuyor() {
        return tur.sureli() && durum != null && (durum.equals(tur.alt) || durum.equals(tur.ust));
    }

    /** Eski kullanım (zamansız): plank süresi sayılmaz. */
    boolean guncelle(String olasiDurum, float olasilik) {
        return guncelle(olasiDurum, olasilik, -1);
    }

    /**
     * Yeni kare: en olası duruş ve olasılığı (vücut görünmüyorsa null), kare zamanı (ms).
     * Sayı arttıysa (tekrar ya da plankta yeni bir saniye) true.
     */
    synchronized boolean guncelle(String olasiDurum, float olasilik, long zaman) {
        gecmis.addLast(olasiDurum != null && olasilik >= ESIK ? olasiDurum : "");
        while (gecmis.size() > GECMIS) gecmis.removeFirst();
        Map<String, Integer> sayac = new HashMap<>();
        for (String d : gecmis) sayac.merge(d, 1, Integer::sum);
        // Çoğunluk yoksa eski duruş kalır; çoğunluk "görünmüyor" ise duruş bilinmez (plank durur).
        for (Map.Entry<String, Integer> e : sayac.entrySet()) {
            if (e.getValue() * 2 > GECMIS) durum = e.getKey().isEmpty() ? null : e.getKey();
        }
        if (tur.sureli()) return plank(zaman);
        if (tur.alt.equals(durum)) {
            altta = true;
        } else if (tur.ust.equals(durum) && altta) {
            altta = false;
            sayi++;
            return true;
        }
        return false;
    }

    private boolean plank(long zaman) {
        long onceki = sonZaman;
        sonZaman = zaman;
        if (zaman < 0 || onceki < 0 || !tutuyor()) return false;
        plankMs += Math.min(EN_UZUN_ADIM_MS, Math.max(0, zaman - onceki));
        int saniye = (int) (plankMs / 1000);
        if (saniye == sayi) return false;
        sayi = saniye;
        return true;
    }

    /** Hedefe yeni ulaşıldıysa true (bir kez). */
    synchronized boolean hedefeUlasti() {
        if (hedef <= 0 || hedefTamam || sayi < hedef) return false;
        hedefTamam = true;
        return true;
    }
}
