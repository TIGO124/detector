package app.tigo.dedektor;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import androidx.appcompat.widget.SwitchCompat;
import java.util.function.Consumer;

/** Ayarlar paneli: çizgi rengi, yüz ağı, etiketler, vücut iskeleti ve arka plan. */
final class AyarPaneli {

    private static final String[] ARKA_PLAN_ADLARI = {"Kapalı", "Bulanık", "Değiştir"};

    private final Activity etkinlik;
    private final Ayarlar ayarlar;
    private final CizimView cizim;
    private final View panel;
    private final LinearLayout renkler;
    private final LinearLayout arkaPlanlar;
    private final float dp;
    /** Renk değişince ekranın geri kalanı (düğmeler, sayaçlar) yeniden çizilsin. */
    private final Runnable renkDegisti;
    private final Runnable vucutDegisti;
    private final Consumer<Boolean> arkaPlanDegisti;

    AyarPaneli(Activity etkinlik, Ayarlar ayarlar, CizimView cizim, Runnable renkDegisti,
               Runnable vucutDegisti, Consumer<Boolean> arkaPlanDegisti) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.cizim = cizim;
        this.renkDegisti = renkDegisti;
        this.vucutDegisti = vucutDegisti;
        this.arkaPlanDegisti = arkaPlanDegisti;
        panel = etkinlik.findViewById(R.id.ayar_paneli);
        renkler = etkinlik.findViewById(R.id.renkler);
        arkaPlanlar = etkinlik.findViewById(R.id.arka_planlar);
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
        panel.setVisibility(ac ? View.VISIBLE : View.GONE);
    }

    private void kur() {
        SwitchCompat yuzAgi = etkinlik.findViewById(R.id.ayar_yuz_agi);
        yuzAgi.setChecked(ayarlar.yuzAgi);
        yuzAgi.setOnCheckedChangeListener((b, acik) -> {
            ayarlar.yuzAgi = acik;
            ayarlar.kaydet();
            cizim.invalidate();
        });
        SwitchCompat etiketler = etkinlik.findViewById(R.id.ayar_etiketler);
        etiketler.setChecked(ayarlar.etiketler);
        etiketler.setOnCheckedChangeListener((b, acik) -> {
            ayarlar.etiketler = acik;
            ayarlar.kaydet();
            cizim.invalidate();
        });
        SwitchCompat vucut = etkinlik.findViewById(R.id.ayar_vucut);
        vucut.setChecked(ayarlar.vucut);
        vucut.setOnCheckedChangeListener((b, acik) -> {
            ayarlar.vucut = acik;
            ayarlar.kaydet();
            vucutDegisti.run();
        });
        etkinlik.findViewById(R.id.ayar_tamam).setOnClickListener(v -> ac(false));
        renkleriGoster();
        arkaPlanlariGoster();
    }

    private void renkleriGoster() {
        renkler.removeAllViews();
        for (int renk : Ayarlar.RENKLER) {
            View nokta = new View(etkinlik);
            GradientDrawable zemin = new GradientDrawable();
            zemin.setShape(GradientDrawable.OVAL);
            zemin.setColor(renk);
            if (renk == ayarlar.renk) zemin.setStroke((int) (3 * dp), 0xFFFFFFFF);
            nokta.setBackground(zemin);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
            lp.setMarginEnd((int) (12 * dp));
            nokta.setOnClickListener(v -> {
                ayarlar.renk = renk;
                ayarlar.kaydet();
                renkleriGoster();
                arkaPlanlariGoster();
                renkDegisti.run();
                cizim.invalidate();
            });
            renkler.addView(nokta, lp);
        }
        etkinlik.findViewById(R.id.ayar_tamam).setBackgroundTintList(ColorStateList.valueOf(ayarlar.renk));
    }

    private void arkaPlanlariGoster() {
        arkaPlanlar.removeAllViews();
        Ayarlar.ArkaPlan[] turler = Ayarlar.ArkaPlan.values();
        for (int i = 0; i < turler.length; i++) {
            Ayarlar.ArkaPlan tur = turler[i];
            arkaPlanlar.addView(Cip.olustur(etkinlik, ARKA_PLAN_ADLARI[i], ayarlar.arkaPlan == tur, ayarlar.renk, v -> {
                ayarlar.arkaPlan = tur;
                ayarlar.kaydet();
                arkaPlanlariGoster();
                boolean acik = tur != Ayarlar.ArkaPlan.KAPALI;
                if (!acik) cizim.arkaPlaniTemizle();
                arkaPlanDegisti.accept(acik);
            }));
        }
    }
}
