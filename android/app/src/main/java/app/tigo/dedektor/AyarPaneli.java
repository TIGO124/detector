package app.tigo.dedektor;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;

/**
 * Ayarlar yaprağı: çizgi rengi; gösterim, çekim ve yardımcı anahtarları; arka plan;
 * hızlandırıcı (CPU/GPU); yapay zekâ verilerini yedekleme; hakkında ve sıfırlama.
 * Bölümler koddan kurulur (AyarBolumu).
 */
final class AyarPaneli {

    /** Ayar değişince kamera ekranının yapacakları. */
    interface Dinleyici {
        void renkDegisti();

        void vucutDegisti();

        void arkaPlanDegisti(boolean acik);

        void hizlandiriciSecildi(boolean gpu);

        /** Izgara, pil tasarrufu, sesli geri bildirim gibi anlık uygulanan ayarlar. */
        void ayarDegisti();

        void yedekle();

        void geriYukle();

        void hakkinda();

        void sifirla();
    }

    private static final String[] ARKA_PLAN_ADLARI = {"Kapalı", "Bulanık", "Değiştir"};
    private static final String[] ZAMANLAYICI_ADLARI = {"Kapalı", "3 sn", "10 sn"};
    private static final String[] RENK_ADLARI = {"Yeşil", "Mavi", "Kırmızı", "Sarı", "Beyaz"};
    /** Yaprakta seçili parçanın rengi ve yazısı. */
    private static final int PARCA_SECILI = 0xFFEEF2F7;
    private static final int PARCA_SECILI_YAZI = 0xFF070B14;

    private final Activity etkinlik;
    private final Ayarlar ayarlar;
    private final Dinleyici dinleyici;
    /** Kaydırma kabı (görünürlük) ve içindeki yaprak (zemin, alt boşluk). */
    private final ScrollView kap;
    private final View panel;
    private final LinearLayout renkler;
    private final float dp;
    private LinearLayout arkaPlanlar;
    private LinearLayout hizlandiricilar;
    private LinearLayout zamanlayicilar;
    private AyarBolumu bolum;

    AyarPaneli(Activity etkinlik, Ayarlar ayarlar, Dinleyici dinleyici) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.dinleyici = dinleyici;
        kap = etkinlik.findViewById(R.id.ayar_kaydirma);
        panel = etkinlik.findViewById(R.id.ayar_paneli);
        renkler = etkinlik.findViewById(R.id.renkler);
        dp = etkinlik.getResources().getDisplayMetrics().density;
        kur();
    }

    View panel() {
        return panel;
    }

    boolean acik() {
        return kap.getVisibility() == View.VISIBLE;
    }

    void ac(boolean ac) {
        if (ac) {
            hizlandiriciyiGoster();
            kap.scrollTo(0, 0);
        }
        kap.setVisibility(ac ? View.VISIBLE : View.GONE);
    }

    private void kur() {
        LinearLayout bolumler = etkinlik.findViewById(R.id.ayar_bolumleri);
        bolumler.removeAllViews();
        bolum = new AyarBolumu(bolumler);

        bolum.baslik("GÖSTERİM");
        LinearLayout gosterim = bolum.liste();
        bolum.anahtar(gosterim, "Yüz ağı", "Yüzün üstünde ince çizgiler", ayarlar.yuzAgi, (b, a) -> {
            ayarlar.yuzAgi = a;
            kaydet();
        });
        bolum.anahtar(gosterim, "Etiketler", "Hareket, parmak sayısı ve ifade yazıları", ayarlar.etiketler, (b, a) -> {
            ayarlar.etiketler = a;
            kaydet();
        });
        bolum.anahtar(gosterim, "Vücut iskeleti", "Omuzdan ayağa eklemler", ayarlar.vucut, (b, a) -> {
            ayarlar.vucut = a;
            ayarlar.kaydet();
            dinleyici.vucutDegisti();
        });
        bolum.anahtar(gosterim, "Izgara çizgileri", "Kadrajı üçe bölen çizgiler", ayarlar.izgara, (b, a) -> {
            ayarlar.izgara = a;
            kaydet();
        });

        bolum.baslik("ÇEKİM");
        zamanlayicilar = bolum.parcaliKap();
        bolum.not("Zamanlayıcı: deklanşörden sonra bekler. Video için deklanşöre basılı tut.");
        LinearLayout cekim = bolum.liste();
        ((LinearLayout.LayoutParams) cekim.getLayoutParams()).topMargin = Math.round(12 * dp);
        bolum.anahtar(cekim, "Zafer işaretiyle çek", "✌ işaretini 1 saniye tut: 3 saniye sonra çeker", ayarlar.hareketleCek, (b, a) -> {
            ayarlar.hareketleCek = a;
            kaydet();
        });
        bolum.anahtar(cekim, "Gülümseyince çek", "Gülümseme algılanınca fotoğraf çeker", ayarlar.gulumseyinceCek, (b, a) -> {
            ayarlar.gulumseyinceCek = a;
            kaydet();
        });

        bolum.baslik("YARDIMCILAR");
        LinearLayout yardim = bolum.liste();
        bolum.anahtar(yardim, "Uyku uyarısı", "Gözler 1,5 saniye kapalı kalınca alarm çalar", ayarlar.uykuUyarisi, (b, a) -> {
            ayarlar.uykuUyarisi = a;
            kaydet();
        });
        bolum.anahtar(yardim, "Sesli geri bildirim", "Tanınan kişi, harf, tekrar ve nesneleri Türkçe okur", ayarlar.sesli, (b, a) -> {
            ayarlar.sesli = a;
            kaydet();
        });
        bolum.anahtar(yardim, "Titreşim", "Çekim, tekrar ve oyunlarda kısa titreşim", ayarlar.titresim, (b, a) -> {
            ayarlar.titresim = a;
            kaydet();
        });
        bolum.anahtar(yardim, "Pil tasarrufu", "Algılama saniyede en çok 8 kez; telefon daha az ısınır", ayarlar.pilTasarrufu, (b, a) -> {
            ayarlar.pilTasarrufu = a;
            kaydet();
        });

        bolum.baslik("ARKA PLAN");
        arkaPlanlar = bolum.parcaliKap();

        bolum.baslik("HIZLANDIRICI");
        hizlandiricilar = bolum.parcaliKap();
        bolum.not("Bazı telefonlarda GPU daha akıcı çalışır. Çalışmazsa CPU'ya kendiliğinden döner.");

        bolum.baslik("YAPAY ZEKÂ VERİLERİ");
        bolum.dugmeSirasi(
            Cip.ikincil(etkinlik, "Yedekle", R.drawable.ic_gonder, v -> dinleyici.yedekle()),
            Cip.ikincil(etkinlik, "Geri yükle", R.drawable.ic_yukle, v -> dinleyici.geriYukle()));
        bolum.not("Öğrettiğin hareketler, harfler, kişiler ve nesneler tek dosyada.");

        etkinlik.findViewById(R.id.ayar_kapat).setOnClickListener(v -> ac(false));
        etkinlik.findViewById(R.id.ayar_tamam).setOnClickListener(v -> ac(false));
        TextView hakkinda = etkinlik.findViewById(R.id.ayar_hakkinda);
        TextView sifirla = etkinlik.findViewById(R.id.ayar_sifirla);
        Cip.simge(hakkinda, R.drawable.ic_bilgi, Cip.YAZI_IKINCIL, 17);
        Cip.simge(sifirla, R.drawable.ic_sifirla, Cip.YAZI_IKINCIL, 17);
        Cip.ortala(hakkinda);
        Cip.ortala(sifirla);
        hakkinda.setOnClickListener(v -> dinleyici.hakkinda());
        sifirla.setOnClickListener(v -> dinleyici.sifirla());
        renkleriGoster();
        parcalilariGoster();
    }

    private void kaydet() {
        ayarlar.kaydet();
        dinleyici.ayarDegisti();
    }

    private void renkleriGoster() {
        renkler.removeAllViews();
        for (int i = 0; i < Ayarlar.RENKLER.length; i++) {
            int renk = Ayarlar.RENKLER[i];
            View nokta = new View(etkinlik);
            nokta.setBackground(renkNoktasi(renk, renk == ayarlar.renk));
            nokta.setContentDescription(RENK_ADLARI[i]);
            nokta.setSelected(renk == ayarlar.renk);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(48 * dp), Math.round(48 * dp));
            lp.setMarginEnd(Math.round(8 * dp));
            nokta.setOnClickListener(v -> {
                ayarlar.renk = renk;
                ayarlar.kaydet();
                renkleriGoster();
                parcalilariGoster();
                dinleyici.renkDegisti();
            });
            renkler.addView(nokta, lp);
        }
        int vurgu = ayarlar.renk;
        etkinlik.findViewById(R.id.ayar_tamam).setBackgroundTintList(ColorStateList.valueOf(vurgu));
        for (SwitchCompat a : bolum.anahtarlar) {
            a.setTrackTintList(Cip.durumRengi(vurgu, Cip.CIZGI_ACIK));
            a.setThumbTintList(Cip.durumRengi(Cip.VURGU_USTU, Cip.SOLUK));
        }
    }

    /** 40 dp renk dairesi; seçiliyse çevresinde 3 dp boşlukla beyaz halka. */
    private Drawable renkNoktasi(int renk, boolean secili) {
        GradientDrawable ic = new GradientDrawable();
        ic.setShape(GradientDrawable.OVAL);
        ic.setColor(renk);
        GradientDrawable halka = new GradientDrawable();
        halka.setShape(GradientDrawable.OVAL);
        halka.setColor(0);
        halka.setStroke(Math.round(2 * dp), secili ? 0xFFFFFFFF : 0);
        LayerDrawable d = new LayerDrawable(new Drawable[] {halka, ic});
        int bosluk = Math.round(4 * dp);
        d.setLayerInset(1, bosluk, bosluk, bosluk, bosluk);
        return d;
    }

    private void parcalilariGoster() {
        arkaPlanlariGoster();
        zamanlayicilariGoster();
        hizlandiriciyiGoster();
    }

    private void arkaPlanlariGoster() {
        Ayarlar.ArkaPlan[] turler = Ayarlar.ArkaPlan.values();
        Cip.parcali(arkaPlanlar, ARKA_PLAN_ADLARI, ayarlar.arkaPlan.ordinal(), PARCA_SECILI, PARCA_SECILI_YAZI, i -> {
            ayarlar.arkaPlan = turler[i];
            ayarlar.kaydet();
            arkaPlanlariGoster();
            dinleyici.arkaPlanDegisti(turler[i] != Ayarlar.ArkaPlan.KAPALI);
        });
    }

    private void zamanlayicilariGoster() {
        int secili = 0;
        for (int i = 0; i < Ayarlar.ZAMANLAYICI.length; i++) if (Ayarlar.ZAMANLAYICI[i] == ayarlar.zamanlayici) secili = i;
        Cip.parcali(zamanlayicilar, ZAMANLAYICI_ADLARI, secili, PARCA_SECILI, PARCA_SECILI_YAZI, i -> {
            ayarlar.zamanlayici = Ayarlar.ZAMANLAYICI[i];
            ayarlar.kaydet();
            zamanlayicilariGoster();
        });
    }

    /** FPS hapı ya da kendiliğinden geri dönüş değiştirebilir; yaprak her açılışta güncel hâli gösterir. */
    void hizlandiriciyiGoster() {
        Cip.parcali(hizlandiricilar, new String[] {"CPU", "GPU"}, ayarlar.gpu ? 1 : 0, PARCA_SECILI, PARCA_SECILI_YAZI, i -> {
            if ((i == 1) != ayarlar.gpu) dinleyici.hizlandiriciSecildi(i == 1);
        });
    }
}
