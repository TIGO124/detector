package app.tigo.dedektor;

import android.content.SharedPreferences;

/** Kamera ekranı ayarları; telefonda saklanır. Alanlar birden çok iş parçacığından okunur. */
final class Ayarlar {

    enum Mod { NORMAL, CIZIM, EFEKT, OYUN, YZ }

    /** Yapay zekâ modunun alt modları. */
    enum Yz { HAREKET, TID, DUYGU, KISI, EGZERSIZ, NESNE }

    enum ArkaPlan { KAPALI, BULANIK, DEGISTIR }

    enum Efekt { GOZLUK, BIYIK, SAPKA, HEPSI }

    static final int[] RENKLER = {0xFF00E676, 0xFF29B6F6, 0xFFFF5252, 0xFFFFEB3B, 0xFFFFFFFF};

    volatile int renk = RENKLER[0];
    volatile boolean yuzAgi = true;
    volatile boolean etiketler = true;
    volatile boolean vucut = false;
    volatile ArkaPlan arkaPlan = ArkaPlan.KAPALI;
    volatile Mod mod = Mod.NORMAL;
    volatile Efekt efekt = Efekt.GOZLUK;
    volatile Yz yz = Yz.HAREKET;
    volatile boolean gpu = false;

    private final SharedPreferences tercihler;

    Ayarlar(SharedPreferences tercihler) {
        this.tercihler = tercihler;
        renk = tercihler.getInt("renk", renk);
        yuzAgi = tercihler.getBoolean("yuzAgi", yuzAgi);
        etiketler = tercihler.getBoolean("etiketler", etiketler);
        vucut = tercihler.getBoolean("vucut", vucut);
        arkaPlan = sec(ArkaPlan.values(), tercihler.getString("arkaPlan", null), arkaPlan);
        mod = sec(Mod.values(), tercihler.getString("mod", null), mod);
        efekt = sec(Efekt.values(), tercihler.getString("efekt", null), efekt);
        yz = sec(Yz.values(), tercihler.getString("yz", null), yz);
        gpu = "GPU".equals(tercihler.getString("hizlandirici", "CPU"));
    }

    void kaydet() {
        tercihler.edit()
            .putInt("renk", renk)
            .putBoolean("yuzAgi", yuzAgi)
            .putBoolean("etiketler", etiketler)
            .putBoolean("vucut", vucut)
            .putString("arkaPlan", arkaPlan.name())
            .putString("mod", mod.name())
            .putString("efekt", efekt.name())
            .putString("yz", yz.name())
            .putString("hizlandirici", gpu ? "GPU" : "CPU")
            .apply();
    }

    /** Vücut modeli açık mı: ayarlardan ya da egzersiz sayarken. */
    boolean vucutGerekli() {
        return vucut || (mod == Mod.YZ && yz == Yz.EGZERSIZ);
    }

    /** Nesne algılayıcı yalnızca "Nesne" bölümünde çalışır. */
    boolean nesneGerekli() {
        return mod == Mod.YZ && yz == Yz.NESNE;
    }

    private static <E extends Enum<E>> E sec(E[] degerler, String ad, E varsayilan) {
        for (E e : degerler) if (e.name().equals(ad)) return e;
        return varsayilan;
    }
}
