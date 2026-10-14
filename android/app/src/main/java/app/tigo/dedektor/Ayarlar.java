package app.tigo.dedektor;

import android.content.SharedPreferences;

/** Kamera ekranı ayarları; telefonda saklanır. Alanlar birden çok iş parçacığından okunur. */
final class Ayarlar {

    enum Mod { NORMAL, CIZIM, EFEKT, OYUN, YZ }

    /** Yapay zekâ modunun alt modları. */
    enum Yz { HAREKET, TID, DUYGU, KISI, EGZERSIZ, NESNE }

    enum ArkaPlan { KAPALI, BULANIK, DEGISTIR }

    /** Yüz efektleri; HEPSI ilk üçünün (gözlük, bıyık, şapka) birlikte çizilmesi. */
    enum Efekt { GOZLUK, BIYIK, SAPKA, KEDI, TAC, PALYACO, PIKSEL, HEPSI }

    /** Oyun modunun oyunları. */
    enum Oyun { TKM, BALON, HESAP, PIYANO }

    static final int[] RENKLER = {0xFF00E676, 0xFF29B6F6, 0xFFFF5252, 0xFFFFEB3B, 0xFFFFFFFF};
    /** Zamanlayıcı seçenekleri (saniye; 0 = kapalı). */
    static final int[] ZAMANLAYICI = {0, 3, 10};
    /** Havada çizim kalınlıkları (dp). */
    static final float[] KALINLIKLAR = {4f, 8f, 14f};

    volatile int renk = RENKLER[0];
    volatile boolean yuzAgi = true;
    volatile boolean etiketler = true;
    volatile boolean vucut = false;
    volatile boolean izgara = false;
    volatile ArkaPlan arkaPlan = ArkaPlan.KAPALI;
    volatile Mod mod = Mod.NORMAL;
    volatile Efekt efekt = Efekt.GOZLUK;
    volatile Yz yz = Yz.HAREKET;
    volatile Oyun oyun = Oyun.TKM;
    volatile boolean gpu = false;
    volatile boolean onKamera = true;
    /** Deklanşörden sonra bekleme (saniye). */
    volatile int zamanlayici = 0;
    /** Zafer işaretiyle (✌) ve gülümseyince fotoğraf. */
    volatile boolean hareketleCek = false;
    volatile boolean gulumseyinceCek = false;
    volatile boolean uykuUyarisi = false;
    volatile boolean sesli = false;
    volatile boolean titresim = true;
    volatile boolean pilTasarrufu = false;
    /** Havada çizim: RENKLER sırası ve KALINLIKLAR sırası. */
    volatile int cizimRengi = 0;
    volatile int cizimKalinligi = 1;
    /** Egzersiz hedefi (tekrar ya da plankta saniye; 0 = yok). */
    volatile int egzersizHedefi = 0;

    private final SharedPreferences tercihler;

    Ayarlar(SharedPreferences tercihler) {
        this.tercihler = tercihler;
        oku();
    }

    private void oku() {
        SharedPreferences t = tercihler;
        renk = t.getInt("renk", renk);
        yuzAgi = t.getBoolean("yuzAgi", yuzAgi);
        etiketler = t.getBoolean("etiketler", etiketler);
        vucut = t.getBoolean("vucut", vucut);
        izgara = t.getBoolean("izgara", izgara);
        arkaPlan = sec(ArkaPlan.values(), t.getString("arkaPlan", null), arkaPlan);
        mod = sec(Mod.values(), t.getString("mod", null), mod);
        efekt = sec(Efekt.values(), t.getString("efekt", null), efekt);
        yz = sec(Yz.values(), t.getString("yz", null), yz);
        oyun = sec(Oyun.values(), t.getString("oyun", null), oyun);
        gpu = "GPU".equals(t.getString("hizlandirici", "CPU"));
        onKamera = t.getBoolean("onKamera", onKamera);
        zamanlayici = t.getInt("zamanlayici", zamanlayici);
        hareketleCek = t.getBoolean("hareketleCek", hareketleCek);
        gulumseyinceCek = t.getBoolean("gulumseyinceCek", gulumseyinceCek);
        uykuUyarisi = t.getBoolean("uykuUyarisi", uykuUyarisi);
        sesli = t.getBoolean("sesli", sesli);
        titresim = t.getBoolean("titresim", titresim);
        pilTasarrufu = t.getBoolean("pilTasarrufu", pilTasarrufu);
        cizimRengi = sinirla(t.getInt("cizimRengi", cizimRengi), RENKLER.length);
        cizimKalinligi = sinirla(t.getInt("cizimKalinligi", cizimKalinligi), KALINLIKLAR.length);
        egzersizHedefi = Math.max(0, t.getInt("egzersizHedefi", egzersizHedefi));
    }

    void kaydet() {
        tercihler.edit()
            .putInt("renk", renk)
            .putBoolean("yuzAgi", yuzAgi)
            .putBoolean("etiketler", etiketler)
            .putBoolean("vucut", vucut)
            .putBoolean("izgara", izgara)
            .putString("arkaPlan", arkaPlan.name())
            .putString("mod", mod.name())
            .putString("efekt", efekt.name())
            .putString("yz", yz.name())
            .putString("oyun", oyun.name())
            .putString("hizlandirici", gpu ? "GPU" : "CPU")
            .putBoolean("onKamera", onKamera)
            .putInt("zamanlayici", zamanlayici)
            .putBoolean("hareketleCek", hareketleCek)
            .putBoolean("gulumseyinceCek", gulumseyinceCek)
            .putBoolean("uykuUyarisi", uykuUyarisi)
            .putBoolean("sesli", sesli)
            .putBoolean("titresim", titresim)
            .putBoolean("pilTasarrufu", pilTasarrufu)
            .putInt("cizimRengi", cizimRengi)
            .putInt("cizimKalinligi", cizimKalinligi)
            .putInt("egzersizHedefi", egzersizHedefi)
            .apply();
    }

    /** Bütün ayarlar varsayılana döner (yapay zekâ verileri ayrı dosyalarda, dokunulmaz). */
    void sifirla() {
        tercihler.edit().clear().commit();
        Ayarlar v = new Ayarlar(tercihler);
        renk = v.renk;
        yuzAgi = v.yuzAgi;
        etiketler = v.etiketler;
        vucut = v.vucut;
        izgara = v.izgara;
        arkaPlan = v.arkaPlan;
        mod = v.mod;
        efekt = v.efekt;
        yz = v.yz;
        oyun = v.oyun;
        gpu = v.gpu;
        onKamera = v.onKamera;
        zamanlayici = v.zamanlayici;
        hareketleCek = v.hareketleCek;
        gulumseyinceCek = v.gulumseyinceCek;
        uykuUyarisi = v.uykuUyarisi;
        sesli = v.sesli;
        titresim = v.titresim;
        pilTasarrufu = v.pilTasarrufu;
        cizimRengi = v.cizimRengi;
        cizimKalinligi = v.cizimKalinligi;
        egzersizHedefi = v.egzersizHedefi;
    }

    /** Vücut modeli açık mı: ayarlardan ya da egzersiz sayarken. */
    boolean vucutGerekli() {
        return vucut || (mod == Mod.YZ && yz == Yz.EGZERSIZ);
    }

    /** Nesne algılayıcı yalnızca "Nesne" bölümünde çalışır. */
    boolean nesneGerekli() {
        return mod == Mod.YZ && yz == Yz.NESNE;
    }

    int cizimRengiDegeri() {
        return RENKLER[cizimRengi];
    }

    private static int sinirla(int deger, int adet) {
        return deger < 0 || deger >= adet ? 0 : deger;
    }

    private static <E extends Enum<E>> E sec(E[] degerler, String ad, E varsayilan) {
        for (E e : degerler) if (e.name().equals(ad)) return e;
        return varsayilan;
    }
}
