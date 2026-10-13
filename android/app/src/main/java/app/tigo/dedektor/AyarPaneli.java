package app.tigo.dedektor;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.LinearLayout;
import androidx.appcompat.widget.SwitchCompat;
import java.util.function.Consumer;

/** Ayarlar yaprağı: çizgi rengi, gösterim anahtarları, arka plan ve hızlandırıcı (CPU/GPU). */
final class AyarPaneli {

    private static final String[] ARKA_PLAN_ADLARI = {"Kapalı", "Bulanık", "Değiştir"};
    private static final String[] RENK_ADLARI = {"Yeşil", "Mavi", "Kırmızı", "Sarı", "Beyaz"};
    /** Yaprakta seçili parçanın rengi ve yazısı. */
    private static final int PARCA_SECILI = 0xFFEEF2F7;
    private static final int PARCA_SECILI_YAZI = 0xFF070B14;

    private final Activity etkinlik;
    private final Ayarlar ayarlar;
    private final CizimView cizim;
    private final View panel;
    private final LinearLayout renkler;
    private final LinearLayout arkaPlanlar;
    private final LinearLayout hizlandiricilar;
    private final SwitchCompat[] anahtarlar;
    private final float dp;
    /** Renk değişince ekranın geri kalanı (düğmeler, sayaçlar) yeniden çizilsin. */
    private final Runnable renkDegisti;
    private final Runnable vucutDegisti;
    private final Consumer<Boolean> arkaPlanDegisti;
    private final Consumer<Boolean> gpuSecildi;

    AyarPaneli(Activity etkinlik, Ayarlar ayarlar, CizimView cizim, Runnable renkDegisti,
               Runnable vucutDegisti, Consumer<Boolean> arkaPlanDegisti, Consumer<Boolean> gpuSecildi) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.cizim = cizim;
        this.renkDegisti = renkDegisti;
        this.vucutDegisti = vucutDegisti;
        this.arkaPlanDegisti = arkaPlanDegisti;
        this.gpuSecildi = gpuSecildi;
        panel = etkinlik.findViewById(R.id.ayar_paneli);
        renkler = etkinlik.findViewById(R.id.renkler);
        arkaPlanlar = etkinlik.findViewById(R.id.arka_planlar);
        hizlandiricilar = etkinlik.findViewById(R.id.hizlandiricilar);
        anahtarlar = new SwitchCompat[] {
            etkinlik.findViewById(R.id.ayar_yuz_agi), etkinlik.findViewById(R.id.ayar_etiketler), etkinlik.findViewById(R.id.ayar_vucut),
        };
        dp = etkinlik.getResources().getDisplayMetrics().density;
        kur();
    }

    View panel() {
        return panel;
    }

    boolean acik() {
        return panel.getVisibility() == View.VISIBLE;
    }

    void ac(boolean ac) {
        if (ac) hizlandiriciyiGoster();
        panel.setVisibility(ac ? View.VISIBLE : View.GONE);
    }

    private void kur() {
        anahtarlar[0].setChecked(ayarlar.yuzAgi);
        anahtarlar[0].setOnCheckedChangeListener((b, acik) -> {
            ayarlar.yuzAgi = acik;
            ayarlar.kaydet();
            cizim.invalidate();
        });
        anahtarlar[1].setChecked(ayarlar.etiketler);
        anahtarlar[1].setOnCheckedChangeListener((b, acik) -> {
            ayarlar.etiketler = acik;
            ayarlar.kaydet();
            cizim.invalidate();
        });
        anahtarlar[2].setChecked(ayarlar.vucut);
        anahtarlar[2].setOnCheckedChangeListener((b, acik) -> {
            ayarlar.vucut = acik;
            ayarlar.kaydet();
            vucutDegisti.run();
        });
        etkinlik.findViewById(R.id.ayar_kapat).setOnClickListener(v -> ac(false));
        etkinlik.findViewById(R.id.ayar_tamam).setOnClickListener(v -> ac(false));
        renkleriGoster();
        arkaPlanlariGoster();
        hizlandiriciyiGoster();
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
                arkaPlanlariGoster();
                hizlandiriciyiGoster();
                renkDegisti.run();
                cizim.invalidate();
            });
            renkler.addView(nokta, lp);
        }
        int vurgu = ayarlar.renk;
        etkinlik.findViewById(R.id.ayar_tamam).setBackgroundTintList(ColorStateList.valueOf(vurgu));
        for (SwitchCompat a : anahtarlar) {
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

    private void arkaPlanlariGoster() {
        Ayarlar.ArkaPlan[] turler = Ayarlar.ArkaPlan.values();
        Cip.parcali(arkaPlanlar, ARKA_PLAN_ADLARI, ayarlar.arkaPlan.ordinal(), PARCA_SECILI, PARCA_SECILI_YAZI, i -> {
            Ayarlar.ArkaPlan tur = turler[i];
            ayarlar.arkaPlan = tur;
            ayarlar.kaydet();
            arkaPlanlariGoster();
            boolean acik = tur != Ayarlar.ArkaPlan.KAPALI;
            if (!acik) cizim.arkaPlaniTemizle();
            arkaPlanDegisti.accept(acik);
        });
    }

    /** FPS hapına dokunarak da değişebilir; yaprak her açılışta güncel hâli gösterir. */
    void hizlandiriciyiGoster() {
        Cip.parcali(hizlandiricilar, new String[] {"CPU", "GPU"}, ayarlar.gpu ? 1 : 0, PARCA_SECILI, PARCA_SECILI_YAZI, i -> {
            if ((i == 1) != ayarlar.gpu) gpuSecildi.accept(i == 1);
        });
    }
}
