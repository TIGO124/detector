package app.tigo.dedektor;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Yapay zekâ modunun düğmeleri: alt modlar (hareket, TİD, duygu, kişi, egzersiz, nesne), her
 * birinin işlemleri, TİD ile yazılan metin, egzersiz sayacı ve elindeki nesne. Öğretme işleri
 * OgretPaneli'nde.
 */
final class YzPaneli {

    private static final String[] ADLAR = {"Hareket", "TİD", "Duygu", "Kişi", "Egzersiz", "Nesne"};

    private final AppCompatActivity etkinlik;
    private final Ayarlar ayarlar;
    private final YzTanima yz;
    /** Alt mod değişince (ipucu, vücut modeli) çağrılır. */
    private final Runnable altModDegisti;
    private final View panel;
    private final TextView buyuk;
    private final LinearLayout islemler;
    final OgretPaneli ogret;
    private ToneGenerator ses;
    private String sonBuyuk;

    YzPaneli(AppCompatActivity etkinlik, Ayarlar ayarlar, YzTanima yz, TextView ortaYazi, Runnable altModDegisti) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.yz = yz;
        this.altModDegisti = altModDegisti;
        panel = etkinlik.findViewById(R.id.yz_paneli);
        buyuk = etkinlik.findViewById(R.id.yz_buyuk);
        islemler = etkinlik.findViewById(R.id.yz_islemler);
        buyuk.setGravity(Gravity.CENTER);
        ogret = new OgretPaneli(etkinlik, ayarlar, yz, ortaYazi, altModDegisti);
        yz.tekrarOldu = () -> etkinlik.runOnUiThread(this::tekrar);
    }

    /** Mod panelinde alt mod düğmeleri. */
    void altModlar(LinearLayout modPaneli) {
        Ayarlar.Yz[] tum = Ayarlar.Yz.values();
        for (int i = 0; i < tum.length; i++) {
            Ayarlar.Yz t = tum[i];
            modPaneli.addView(cip(ADLAR[i], ayarlar.yz == t, v -> {
                if (ayarlar.yz == t) return;
                ogret.kapat();
                ayarlar.yz = t;
                ayarlar.kaydet();
                altModDegisti.run();
            }));
        }
    }

    /** Yapay zekâ modundaysa o alt modun işlemlerini gösterir. */
    void goster(boolean yzModu) {
        panel.setVisibility(yzModu ? View.VISIBLE : View.GONE);
        if (!yzModu) {
            ogret.kapat();
            return;
        }
        islemler.removeAllViews();
        switch (ayarlar.yz) {
            case HAREKET:
                islemler.addView(cip("Öğret", true, v -> ogret.ac(Ayarlar.Yz.HAREKET)));
                break;
            case TID:
                islemler.addView(cip("Boşluk", false, v -> tidDuzenle("bosluk")));
                islemler.addView(cip("Sil", false, v -> tidDuzenle("sil")));
                islemler.addView(cip("Temizle", false, v -> tidDuzenle("temizle")));
                islemler.addView(cip("Öğret", true, v -> ogret.ac(Ayarlar.Yz.TID)));
                break;
            case KISI:
                islemler.addView(cip("Kişi ekle", true, v -> ogret.ac(Ayarlar.Yz.KISI)));
                break;
            case NESNE:
                islemler.addView(cip("Nesne öğret", true, v -> ogret.ac(Ayarlar.Yz.NESNE)));
                break;
            case EGZERSIZ:
                for (EgzersizSayaci.Tur t : EgzersizSayaci.Tur.values()) {
                    islemler.addView(cip(t.ad, yz.sayac.tur() == t, v -> {
                        yz.sayac.sec(t);
                        goster(true);
                    }));
                }
                islemler.addView(cip("Sıfırla", false, v -> {
                    yz.sayac.sifirla();
                    guncelle();
                }));
                break;
            default:
                break;
        }
        sonBuyuk = null;
        guncelle();
    }

    /** Ekranın üstündeki ipucu. */
    String ipucu() {
        switch (ayarlar.yz) {
            case HAREKET:
                return yz.modelVar(Ayarlar.Yz.HAREKET) ? "Öğrettiğin hareketler elinin üstünde yazar"
                    : "Öğret'e bas: kendi el hareketlerini öğret";
            case TID:
                return yz.modelVar(Ayarlar.Yz.TID) ? "Harfi 1 saniye tut, yazıya eklenir"
                    : "Öğret'e bas: TİD harflerini (A, B, C…) öğret";
            case DUYGU:
                return "Yüz ifaden 7 duygudan birine ayrılır";
            case KISI:
                return yz.kisiVerisi.toplam() == 0 ? "Kişi ekle: adını yaz, yüzünü 3 saniye göster"
                    : "Kayıtlı kişiler yüzlerinden tanınır";
            case EGZERSIZ:
                return "Telefonu sabitle, bütün vücudun görünsün";
            case NESNE:
                return yz.nesneVerisi.toplam() == 0 ? "Nesneyi ortadaki çerçeveye tut"
                    : "Öğrettiğin nesneler çerçevede tanınır";
            default:
                return null;
        }
    }

    /** TİD metni, egzersiz sayacı ve elindeki nesne (saniyede birkaç kez). */
    void guncelle() {
        String metin = null;
        String alt = null;
        if (ayarlar.mod == Ayarlar.Mod.YZ && ayarlar.yz == Ayarlar.Yz.TID) {
            metin = yz.tidMetni();
            if (metin.isEmpty()) metin = "…";
        } else if (ayarlar.mod == Ayarlar.Mod.YZ && ayarlar.yz == Ayarlar.Yz.EGZERSIZ) {
            metin = yz.sayac.tur().ad + " · " + yz.sayac.sayi();
            alt = yz.egzersizDurumu();
        } else if (ayarlar.mod == Ayarlar.Mod.YZ && ayarlar.yz == Ayarlar.Yz.NESNE) {
            metin = yz.nesneYazisi();
            if (metin == null) metin = "…";
        }
        String anahtar = metin + "|" + alt;
        if (anahtar.equals(sonBuyuk)) return;
        sonBuyuk = anahtar;
        buyuk.setVisibility(metin == null ? View.GONE : View.VISIBLE);
        if (metin == null || alt == null) {
            buyuk.setText(metin);
            return;
        }
        // Altta küçük yazıyla o anki duruş: sayılmıyorsa nedeni görülsün.
        SpannableString s = new SpannableString(metin + "\n" + alt);
        s.setSpan(new RelativeSizeSpan(0.5f), metin.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        buyuk.setText(s);
    }

    private void tidDuzenle(String islem) {
        yz.tidDuzenle(islem);
        guncelle();
    }

    /** Egzersizde bir tekrar: kısa ses ve sayaç. */
    private void tekrar() {
        guncelle();
        buyuk.animate().cancel();
        buyuk.setScaleX(1.25f);
        buyuk.setScaleY(1.25f);
        buyuk.animate().scaleX(1f).scaleY(1f).setDuration(250).start();
        try {
            if (ses == null) ses = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            ses.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        } catch (RuntimeException e) {
            Log.w("Dedektor", "Ses çalınamadı", e);
        }
    }

    boolean geri() {
        if (!ogret.acik()) return false;
        ogret.kapat();
        return true;
    }

    void kapat() {
        yz.tekrarOldu = null;
        ogret.kapatIsleri();
        if (ses != null) ses.release();
    }

    private TextView cip(String metin, boolean secili, View.OnClickListener tik) {
        return Cip.olustur(etkinlik, metin, secili, ayarlar.renk, tik);
    }
}
