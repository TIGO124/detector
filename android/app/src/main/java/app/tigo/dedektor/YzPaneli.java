package app.tigo.dedektor;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

/**
 * Yapay zekâ modu (Claude Design, "Dedektör Arayüzü" › YZ ekranları): alt mod hapları ve
 * sonuç kartı. Kart her bölümde aynı yerde: küçük etiket, büyük değer, sağda işlem; egzersizde
 * altında tür seçimi, TİD'de yazı düzenleme. Öğretme işleri OgretPaneli'nde.
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
    /** Alt mod değişince (ipucu, modeller) çağrılır. */
    private final Runnable altModDegisti;
    private final View panel;
    private final TextView etiket;
    private final TextView buyuk;
    private final LinearLayout islemler;
    private final LinearLayout secim;
    final OgretPaneli ogret;
    private ToneGenerator ses;
    private String sonAnahtar;

    YzPaneli(AppCompatActivity etkinlik, Ayarlar ayarlar, YzTanima yz, TextView geriSayim, Runnable altModDegisti) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.yz = yz;
        this.altModDegisti = altModDegisti;
        panel = etkinlik.findViewById(R.id.yz_paneli);
        etiket = etkinlik.findViewById(R.id.yz_etiket);
        buyuk = etkinlik.findViewById(R.id.yz_buyuk);
        islemler = etkinlik.findViewById(R.id.yz_islemler);
        secim = etkinlik.findViewById(R.id.yz_secim);
        ogret = new OgretPaneli(etkinlik, ayarlar, yz, geriSayim, altModDegisti);
        yz.tekrarOldu = () -> etkinlik.runOnUiThread(this::tekrar);
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

    /** Yapay zekâ modundaysa o alt modun kartını kurar. */
    void goster(boolean yzModu) {
        panel.setVisibility(yzModu ? View.VISIBLE : View.GONE);
        if (!yzModu) {
            ogret.kapat();
            return;
        }
        islemler.removeAllViews();
        secim.removeAllViews();
        secim.setBackground(null);
        secim.setVisibility(View.GONE);
        switch (ayarlar.yz) {
            case HAREKET:
                islemler.addView(Cip.tonlu(etkinlik, "Öğret", R.drawable.ic_arti, ayarlar.renk, v -> ogret.ac(Ayarlar.Yz.HAREKET)));
                break;
            case TID:
                islemler.addView(Cip.tonlu(etkinlik, "Öğret", R.drawable.ic_arti, ayarlar.renk, v -> ogret.ac(Ayarlar.Yz.TID)));
                secim.setVisibility(View.VISIBLE);
                secim.addView(Cip.ikincil(etkinlik, "Boşluk", R.drawable.ic_bosluk, v -> tidDuzenle("bosluk")));
                secim.addView(Cip.ikincil(etkinlik, "Sil", R.drawable.ic_sil, v -> tidDuzenle("sil")));
                secim.addView(Cip.ikincil(etkinlik, "Temizle", R.drawable.ic_temizle, v -> tidDuzenle("temizle")));
                break;
            case KISI:
                islemler.addView(Cip.tonlu(etkinlik, "Kişi ekle", R.drawable.ic_arti, ayarlar.renk, v -> ogret.ac(Ayarlar.Yz.KISI)));
                break;
            case NESNE:
                islemler.addView(Cip.tonlu(etkinlik, "Öğret", R.drawable.ic_arti, ayarlar.renk, v -> ogret.ac(Ayarlar.Yz.NESNE)));
                break;
            case EGZERSIZ:
                islemler.addView(Cip.ikonDugme(etkinlik, R.drawable.ic_sifirla, "Sayacı sıfırla", v -> {
                    yz.sayac.sifirla();
                    guncelle();
                }));
                secim.setVisibility(View.VISIBLE);
                float dp = etkinlik.getResources().getDisplayMetrics().density;
                secim.setBackgroundResource(R.drawable.parca_zemin);
                secim.getLayoutParams().height = Math.round(40 * dp);
                int ic = Math.round(3 * dp);
                secim.setPadding(ic, ic, ic, ic);
                EgzersizSayaci.Tur[] turler = EgzersizSayaci.Tur.values();
                String[] adlar = new String[turler.length];
                for (int i = 0; i < turler.length; i++) adlar[i] = turler[i].ad;
                Cip.parcali(secim, adlar, yz.sayac.tur().ordinal(), ayarlar.renk, Cip.VURGU_USTU, i -> {
                    yz.sayac.sec(turler[i]);
                    goster(true);
                });
                break;
            default:
                break;
        }
        if (ayarlar.yz != Ayarlar.Yz.EGZERSIZ) {
            secim.getLayoutParams().height = LinearLayout.LayoutParams.WRAP_CONTENT;
            secim.setPadding(0, 0, 0, 0);
        }
        sonAnahtar = null;
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

    /** Kartın etiketi ve değeri (saniyede birkaç kez). */
    void guncelle() {
        if (ayarlar.mod != Ayarlar.Mod.YZ) return;
        CharSequence ust;
        CharSequence deger;
        // Sonuç yoksa yer tutucu küçük ve soluk yazılır (büyük yazı sonuca ayrılır).
        String yerTutucu = null;
        switch (ayarlar.yz) {
            case HAREKET:
                ust = "HAREKET";
                deger = yz.hareketYazisi();
                yerTutucu = yz.modelVar(Ayarlar.Yz.HAREKET) ? "Öğrettiğin bir hareketi yap" : "Henüz öğretilmedi";
                break;
            case TID:
                ust = "TİD YAZISI";
                deger = yz.tidMetni();
                yerTutucu = yz.modelVar(Ayarlar.Yz.TID) ? "Harfi göster" : "Henüz öğretilmedi";
                break;
            case DUYGU:
                ust = "DUYGU";
                deger = yz.duyguYazisi();
                yerTutucu = "Yüz görünmüyor";
                break;
            case KISI:
                ust = "KİŞİ";
                deger = yz.kisiYazisi();
                yerTutucu = "Yüz görünmüyor";
                break;
            case EGZERSIZ:
                ust = egzersizEtiketi();
                deger = sayac(yz.sayac.sayi());
                break;
            case NESNE:
                String n = yz.nesneYazisi();
                if (n == null) {
                    ust = "NESNE";
                    deger = null;
                    yerTutucu = "Çerçevede nesne yok";
                } else if (n.startsWith("Elinde: ")) {
                    ust = "ELİNDE";
                    deger = n.substring(8);
                } else if (n.startsWith("Kaydediliyor: ")) {
                    ust = "ÖĞRETİLİYOR";
                    deger = n.substring(14);
                } else {
                    ust = "NESNE";
                    deger = n;
                }
                break;
            default:
                return;
        }
        boolean yer = deger == null || deger.length() == 0;
        if (yer) deger = yerTutucu;
        String anahtar = ust + "|" + deger;
        if (anahtar.equals(sonAnahtar)) return;
        sonAnahtar = anahtar;
        etiket.setText(ust);
        buyuk.setText(deger);
        buyuk.setTextColor(yer ? Cip.SOLUK : Cip.YAZI);
        buyuk.setTextSize(yer ? 18 : ayarlar.yz == Ayarlar.Yz.EGZERSIZ ? 52 : ayarlar.yz == Ayarlar.Yz.TID ? 26 : 30);
    }

    /** "SQUAT · AŞAĞIDA": türün adı ve o anki duruş (vurgu renginde). */
    private CharSequence egzersizEtiketi() {
        SpannableStringBuilder s = new SpannableStringBuilder(yz.sayac.tur().ad.toUpperCase(TR) + " · ");
        int bas = s.length();
        s.append(yz.egzersizDurumu().toUpperCase(TR));
        s.setSpan(new ForegroundColorSpan(ayarlar.renk), bas, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** Büyük sayı vurgu renginde, yanında küçük "tekrar". */
    private CharSequence sayac(int sayi) {
        SpannableStringBuilder s = new SpannableStringBuilder(String.valueOf(sayi));
        s.setSpan(new ForegroundColorSpan(ayarlar.renk), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int bas = s.length();
        s.append("  tekrar");
        s.setSpan(new AbsoluteSizeSpan(14, true), bas, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(Cip.SOLUK), bas, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private void tidDuzenle(String islem) {
        yz.tidDuzenle(islem);
        guncelle();
    }

    /** Egzersizde bir tekrar: kısa ses ve sayaç. */
    private void tekrar() {
        guncelle();
        buyuk.animate().cancel();
        buyuk.setScaleX(1.2f);
        buyuk.setScaleY(1.2f);
        buyuk.setPivotX(0);
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
}
