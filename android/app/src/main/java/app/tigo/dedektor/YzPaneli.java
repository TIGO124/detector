package app.tigo.dedektor;

import android.media.ToneGenerator;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

/**
 * Yapay zekâ modu (Claude Design, "Dedektör Arayüzü" › YZ ekranları): alt mod hapları ve
 * sonuç kartı. Kart her bölümde aynı yerde: küçük etiket, büyük değer, sağda işlem; egzersizde
 * altında tür seçimi, hedef ve geçmiş; TİD'de yazı düzenleme. Öğretme işleri OgretPaneli'nde.
 */
final class YzPaneli {

    private static final String[] ADLAR = {"Hareket", "TİD", "Duygu", "Kişi", "Egzersiz", "Nesne"};
    private static final int[] SIMGELER = {
        R.drawable.ic_el, R.drawable.ic_tid, R.drawable.ic_yuz, R.drawable.ic_kisi, R.drawable.ic_egzersiz, R.drawable.ic_nesne,
    };
    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    private final AppCompatActivity etkinlik;
    private final Ayarlar ayarlar;
    private final YzTanima yz;
    private final SonucKarti kart;
    private final Geribildirim geribildirim;
    private final Kutlama kutlama;
    /** Alt mod değişince (ipucu, modeller) çağrılır. */
    private final Runnable altModDegisti;
    final OgretPaneli ogret;

    YzPaneli(AppCompatActivity etkinlik, Ayarlar ayarlar, YzTanima yz, SonucKarti kart, TextView geriSayim,
             Geribildirim geribildirim, Kutlama kutlama, Runnable altModDegisti) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.yz = yz;
        this.kart = kart;
        this.geribildirim = geribildirim;
        this.kutlama = kutlama;
        this.altModDegisti = altModDegisti;
        ogret = new OgretPaneli(etkinlik, ayarlar, yz, geriSayim, altModDegisti);
        yz.egzersiz.dinleyici = (sayi, hedef) -> etkinlik.runOnUiThread(() -> artti(sayi, hedef));
    }

    private EgzersizSayaci sayac() {
        return yz.egzersiz.sayac;
    }

    /** Mod panelinde alt mod hapları (simgeli); seçili olan görünür yere kaydırılır. */
    void altModlar(LinearLayout modPaneli) {
        Ayarlar.Yz[] tum = Ayarlar.Yz.values();
        TextView secili = null;
        for (int i = 0; i < tum.length; i++) {
            Ayarlar.Yz t = tum[i];
            TextView c = Cip.olustur(etkinlik, ADLAR[i], SIMGELER[i], ayarlar.yz == t, ayarlar.renk, v -> {
                if (ayarlar.yz == t) return;
                ogret.kapat();
                ayarlar.yz = t;
                ayarlar.kaydet();
                altModDegisti.run();
            });
            if (ayarlar.yz == t) secili = c;
            modPaneli.addView(c);
        }
        TextView hedef = secili;
        if (hedef != null && modPaneli.getParent() instanceof HorizontalScrollView) {
            HorizontalScrollView kaydirma = (HorizontalScrollView) modPaneli.getParent();
            kaydirma.post(() -> kaydirma.smoothScrollTo(Math.max(0, hedef.getLeft() - (kaydirma.getWidth() - hedef.getWidth()) / 2), 0));
        }
    }

    /** Yapay zekâ modundaysa o alt modun kartını kurar (kartın görünürlüğü etkinlikte). */
    void goster(boolean yzModu) {
        if (!yzModu) {
            ogret.kapat();
            return;
        }
        kart.temizle();
        switch (ayarlar.yz) {
            case HAREKET:
                kart.islemler.addView(Cip.tonlu(etkinlik, "Öğret", R.drawable.ic_arti, ayarlar.renk, v -> ogret.ac(Ayarlar.Yz.HAREKET)));
                break;
            case TID:
                kart.islemler.addView(Cip.tonlu(etkinlik, "Öğret", R.drawable.ic_arti, ayarlar.renk, v -> ogret.ac(Ayarlar.Yz.TID)));
                LinearLayout s = kart.dugmeSatiri();
                s.addView(Cip.ikincil(etkinlik, "Boşluk", R.drawable.ic_bosluk, v -> tidDuzenle("bosluk")));
                s.addView(Cip.ikincil(etkinlik, "Sil", R.drawable.ic_sil, v -> tidDuzenle("sil")));
                s.addView(Cip.ikincil(etkinlik, "Temizle", R.drawable.ic_temizle, v -> tidDuzenle("temizle")));
                break;
            case KISI:
                kart.islemler.addView(Cip.tonlu(etkinlik, "Kişi ekle", R.drawable.ic_arti, ayarlar.renk, v -> ogret.ac(Ayarlar.Yz.KISI)));
                break;
            case NESNE:
                kart.islemler.addView(Cip.tonlu(etkinlik, "Öğret", R.drawable.ic_arti, ayarlar.renk, v -> ogret.ac(Ayarlar.Yz.NESNE)));
                break;
            case EGZERSIZ:
                egzersizKarti();
                break;
            default:
                break;
        }
        guncelle();
    }

    /** Egzersiz: hedef, geçmiş ve sıfırlama düğmeleri; altta tür seçimi. */
    private void egzersizKarti() {
        EgzersizSayaci s = sayac();
        s.hedef(EgzersizTakibi.hedefDegeri(s.tur(), ayarlar.egzersizHedefi));
        kart.islemler.addView(Cip.ikonDugme(etkinlik, R.drawable.ic_hedef, "Hedefi değiştir", v -> {
            ayarlar.egzersizHedefi = (ayarlar.egzersizHedefi + 1) % EgzersizTakibi.TEKRAR_HEDEFLERI.length;
            ayarlar.kaydet();
            s.hedef(EgzersizTakibi.hedefDegeri(s.tur(), ayarlar.egzersizHedefi));
            guncelle();
        }));
        kart.islemler.addView(Cip.ikonDugme(etkinlik, R.drawable.ic_takvim, "Geçmiş",
            v -> GecmisPenceresi.goster(etkinlik, yz.egzersiz.gecmis, s.tur(), ayarlar.renk)));
        kart.islemler.addView(Cip.ikonDugme(etkinlik, R.drawable.ic_sifirla, "Sayacı sıfırla", v -> {
            s.sifirla();
            guncelle();
        }));
        EgzersizSayaci.Tur[] turler = EgzersizSayaci.Tur.values();
        String[] adlar = new String[turler.length];
        for (int i = 0; i < turler.length; i++) adlar[i] = turler[i].ad;
        Cip.parcali(kart.parcaliSatir(), adlar, s.tur().ordinal(), ayarlar.renk, Cip.VURGU_USTU, i -> {
            s.sec(turler[i]);
            altModDegisti.run();
        });
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
                return sayac().tur().sureli() ? "Telefonu yana koy, plank duruşunda bekle"
                    : "Telefonu sabitle, bütün vücudun görünsün";
            case NESNE:
                return yz.nesneVerisi.toplam() == 0 ? "Nesneyi ortadaki çerçeveye tut"
                    : "Öğrettiğin nesneler çerçevede tanınır";
            default:
                return null;
        }
    }

    /** Kartın etiketi ve değeri (saniyede birkaç kez). */
    void guncelle() {
        if (ayarlar.mod != Ayarlar.Mod.YZ) return;
        switch (ayarlar.yz) {
            case HAREKET:
                kart.yaz("HAREKET", yz.hareketYazisi(),
                    yz.modelVar(Ayarlar.Yz.HAREKET) ? "Öğrettiğin bir hareketi yap" : "Henüz öğretilmedi", 30);
                break;
            case TID:
                kart.yaz("TİD YAZISI", yz.tidMetni(), yz.modelVar(Ayarlar.Yz.TID) ? "Harfi göster" : "Henüz öğretilmedi", 26);
                break;
            case DUYGU:
                kart.yaz("DUYGU", yz.duyguYazisi(), "Yüz görünmüyor", 30);
                break;
            case KISI:
                kart.yaz("KİŞİ", yz.kisiYazisi(), "Yüz görünmüyor", 30);
                break;
            case EGZERSIZ:
                kart.yaz(egzersizEtiketi(), sayi(), null, 52);
                kart.altYazi(egzersizOzeti());
                break;
            case NESNE:
                String n = yz.nesneYazisi();
                if (n == null) kart.yaz("NESNE", null, "Çerçevede nesne yok", 30);
                else if (n.startsWith("Elinde: ")) kart.yaz("ELİNDE", n.substring(8), null, 30);
                else if (n.startsWith("Kaydediliyor: ")) kart.yaz("ÖĞRETİLİYOR", n.substring(14), null, 30);
                else kart.yaz("NESNE", n, null, 30);
                break;
            default:
                break;
        }
    }

    /** "SQUAT · AŞAĞIDA": türün adı ve o anki duruş (vurgu renginde). */
    private CharSequence egzersizEtiketi() {
        SpannableStringBuilder s = new SpannableStringBuilder(sayac().tur().ad.toUpperCase(TR) + " · ");
        int bas = s.length();
        s.append(yz.egzersiz.durum().toUpperCase(TR));
        s.setSpan(new ForegroundColorSpan(ayarlar.renk), bas, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** Büyük sayı vurgu renginde; yanında küçük "tekrar" ya da "sn" ve hedef. */
    private CharSequence sayi() {
        EgzersizSayaci s = sayac();
        EgzersizSayaci.Tur tur = s.tur();
        String buyuk = tur.sureli() && s.sayi() >= 60 ? GecmisPenceresi.deger(tur, s.sayi()) : String.valueOf(s.sayi());
        SpannableStringBuilder b = new SpannableStringBuilder(buyuk);
        b.setSpan(new ForegroundColorSpan(ayarlar.renk), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int bas = b.length();
        int hedef = s.hedef();
        String birim = tur.sureli() ? (s.sayi() >= 60 ? "" : " sn") : " tekrar";
        b.append(hedef > 0 ? "  / " + GecmisPenceresi.deger(tur, hedef) + (tur.sureli() ? "" : birim) : "  " + birim.trim());
        b.setSpan(new AbsoluteSizeSpan(14, true), bas, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new ForegroundColorSpan(Cip.SOLUK), bas, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    /** "Bugün 12 · 7 gün 40" */
    private String egzersizOzeti() {
        EgzersizSayaci.Tur tur = sayac().tur();
        EgzersizGecmisi g = yz.egzersiz.gecmis;
        long bugun = EgzersizGecmisi.bugun();
        return "Bugün " + GecmisPenceresi.deger(tur, g.gunluk(bugun, tur)) + " · 7 gün " + GecmisPenceresi.deger(tur, g.haftalikToplam(bugun, tur));
    }

    private void tidDuzenle(String islem) {
        yz.tidDuzenle(islem);
        guncelle();
    }

    /** Egzersizde sayı arttı: tekrarda ses, titreşim ve sesli sayı; plankta her 10 saniyede. */
    private void artti(int sayi, boolean hedef) {
        if (ayarlar.mod != Ayarlar.Mod.YZ || ayarlar.yz != Ayarlar.Yz.EGZERSIZ) return;
        guncelle();
        boolean sureli = sayac().tur().sureli();
        if (!sureli || sayi % 10 == 0) {
            kart.zipla();
            geribildirim.ses(ToneGenerator.TONE_PROP_BEEP, 120);
            geribildirim.titret(40);
            if (!hedef) geribildirim.soyle(sureli ? sayi + " saniye" : String.valueOf(sayi));
        }
        if (hedef) {
            kutlama.baslat("Hedef tamam!", true);
            geribildirim.titret(300);
            geribildirim.soyle("Hedef tamam, tebrikler");
        }
    }

    boolean geri() {
        if (!ogret.acik()) return false;
        ogret.kapat();
        return true;
    }

    void kapat() {
        yz.egzersiz.dinleyici = null;
        ogret.kapatIsleri();
    }
}
