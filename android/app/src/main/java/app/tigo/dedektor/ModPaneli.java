package app.tigo.dedektor;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Mod sekmelerinin üstündeki haplar: çizimde kalem rengi, kalınlık, geri al ve temizle;
 * efektte efekt seçimi; oyun ve yapay zekâ modunda kendi panellerinin seçimleri.
 */
final class ModPaneli {

    private static final String[] EFEKT_ADLARI = {"Gözlük", "Bıyık", "Şapka", "Kedi", "Taç", "Palyaço", "Piksel", "Hepsi"};
    private static final String[] KALINLIK_ADLARI = {"İnce", "Orta", "Kalın"};

    private final AppCompatActivity etkinlik;
    private final Ayarlar ayarlar;
    private final CizimView cizim;
    private final HavadaCizim havadaCizim;
    private final YzPaneli yzPaneli;
    private final OyunPaneli oyunPaneli;
    private final LinearLayout panel;
    private final float dp;

    ModPaneli(AppCompatActivity etkinlik, Ayarlar ayarlar, CizimView cizim, HavadaCizim havadaCizim,
              YzPaneli yzPaneli, OyunPaneli oyunPaneli) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.cizim = cizim;
        this.havadaCizim = havadaCizim;
        this.yzPaneli = yzPaneli;
        this.oyunPaneli = oyunPaneli;
        panel = etkinlik.findViewById(R.id.mod_paneli);
        dp = etkinlik.getResources().getDisplayMetrics().density;
        kalemiAyarla();
    }

    void goster() {
        panel.removeAllViews();
        switch (ayarlar.mod) {
            case CIZIM:
                cizimHaplari();
                break;
            case EFEKT:
                Ayarlar.Efekt[] efektler = Ayarlar.Efekt.values();
                for (int i = 0; i < efektler.length; i++) {
                    Ayarlar.Efekt e = efektler[i];
                    panel.addView(Cip.olustur(etkinlik, EFEKT_ADLARI[i], ayarlar.efekt == e, ayarlar.renk, v -> {
                        ayarlar.efekt = e;
                        ayarlar.kaydet();
                        goster();
                        cizim.invalidate();
                    }));
                }
                break;
            case OYUN:
                oyunPaneli.secimler(panel);
                break;
            case YZ:
                yzPaneli.altModlar(panel);
                break;
            default:
                break;
        }
        ((View) panel.getParent()).setVisibility(panel.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void cizimHaplari() {
        TextView renk = Cip.olustur(etkinlik, "Renk", false, ayarlar.renk, v -> {
            ayarlar.cizimRengi = (ayarlar.cizimRengi + 1) % Ayarlar.RENKLER.length;
            kalemiDegistir();
        });
        GradientDrawable nokta = new GradientDrawable();
        nokta.setShape(GradientDrawable.OVAL);
        nokta.setColor(ayarlar.cizimRengiDegeri());
        nokta.setStroke(Math.max(1, Math.round(dp)), 0x80FFFFFF);
        int b = Math.round(16 * dp);
        nokta.setBounds(0, 0, b, b);
        renk.setCompoundDrawablesRelative(nokta, null, null, null);
        renk.setCompoundDrawablePadding(Math.round(7 * dp));
        renk.setPadding(Math.round(12 * dp), 0, Math.round(16 * dp), 0);
        renk.setContentDescription("Kalem rengini değiştir");
        panel.addView(renk);
        TextView kalinlik = Cip.olustur(etkinlik, KALINLIK_ADLARI[ayarlar.cizimKalinligi], false, ayarlar.renk, v -> {
            ayarlar.cizimKalinligi = (ayarlar.cizimKalinligi + 1) % Ayarlar.KALINLIKLAR.length;
            kalemiDegistir();
        });
        kalinlik.setContentDescription("Kalem kalınlığı: " + KALINLIK_ADLARI[ayarlar.cizimKalinligi]);
        panel.addView(kalinlik);
        panel.addView(Cip.olustur(etkinlik, "Geri al", R.drawable.ic_geri_al, false, ayarlar.renk, v -> {
            havadaCizim.geriAl();
            cizim.invalidate();
        }));
        panel.addView(Cip.olustur(etkinlik, "Temizle", R.drawable.ic_silgi, false, ayarlar.renk, v -> {
            havadaCizim.temizle();
            cizim.invalidate();
        }));
    }

    private void kalemiDegistir() {
        ayarlar.kaydet();
        kalemiAyarla();
        goster();
    }

    private void kalemiAyarla() {
        havadaCizim.kalem(ayarlar.cizimRengiDegeri(), Ayarlar.KALINLIKLAR[ayarlar.cizimKalinligi]);
    }
}
