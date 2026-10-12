package app.tigo.dedektor;

/** Taş-kağıt-makas kuralları; oyuncunun seçimi el hareketinden okunur. */
final class TasKagitMakas {

    enum Secim {
        TAS("Taş"),
        KAGIT("Kağıt"),
        MAKAS("Makas");

        final String ad;

        Secim(String ad) {
            this.ad = ad;
        }
    }

    private TasKagitMakas() {}

    /** Yumruk taş, açık el kağıt, zafer işareti makas. Hareket tanınmazsa parmak sayısına bakılır. */
    static Secim hareketten(String hareket, int parmakSayisi) {
        if ("Closed_Fist".equals(hareket)) return Secim.TAS;
        if ("Open_Palm".equals(hareket)) return Secim.KAGIT;
        if ("Victory".equals(hareket)) return Secim.MAKAS;
        if (parmakSayisi == 0) return Secim.TAS;
        if (parmakSayisi == 5) return Secim.KAGIT;
        if (parmakSayisi == 2) return Secim.MAKAS;
        return null;
    }

    /** 1: oyuncu kazandı, 0: berabere, -1: telefon kazandı. */
    static int sonuc(Secim oyuncu, Secim telefon) {
        if (oyuncu == telefon) return 0;
        boolean kazandi = (oyuncu == Secim.TAS && telefon == Secim.MAKAS)
            || (oyuncu == Secim.KAGIT && telefon == Secim.TAS)
            || (oyuncu == Secim.MAKAS && telefon == Secim.KAGIT);
        return kazandi ? 1 : -1;
    }
}
