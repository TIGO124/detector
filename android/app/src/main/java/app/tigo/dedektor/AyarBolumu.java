package app.tigo.dedektor;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import java.util.ArrayList;
import java.util.List;

/**
 * Ayarlar yaprağının koddan kurulan parçaları (Tasarım dili: bölüm etiketi, liste içinde
 * anahtarlı satırlar, parçalı seçim, düğme sırası). Kurulan anahtarlar vurgu rengi değişince
 * yeniden boyansın diye tutulur.
 */
final class AyarBolumu {

    private final Context baglam;
    private final LinearLayout kap;
    private final float dp;
    final List<SwitchCompat> anahtarlar = new ArrayList<>();

    AyarBolumu(LinearLayout kap) {
        this.kap = kap;
        this.baglam = kap.getContext();
        this.dp = baglam.getResources().getDisplayMetrics().density;
    }

    private int px(float d) {
        return Math.round(d * dp);
    }

    /** Büyük harfli, soluk bölüm etiketi. */
    TextView baslik(String metin) {
        TextView t = new TextView(baglam);
        t.setText(metin);
        t.setTypeface(Cip.font(baglam, R.font.manrope_cok_kalin));
        t.setLetterSpacing(0.1f);
        t.setTextColor(Cip.SOLUK);
        t.setTextSize(11);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(18);
        lp.bottomMargin = px(10);
        kap.addView(t, lp);
        return t;
    }

    /** Satırların dizildiği yuvarlak köşeli liste; aralarında ince çizgi. */
    LinearLayout liste() {
        LinearLayout l = new LinearLayout(baglam);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(R.drawable.liste_zemin);
        l.setDividerDrawable(baglam.getDrawable(R.drawable.ayrac));
        l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        kap.addView(l, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return l;
    }

    /** Başlık, açıklama ve sağda anahtar; satırın tamamına dokunmak da anahtarı çevirir. */
    SwitchCompat anahtar(LinearLayout liste, String baslik, String aciklama, boolean acik, CompoundButton.OnCheckedChangeListener degisti) {
        LinearLayout satir = new LinearLayout(baglam);
        satir.setOrientation(LinearLayout.HORIZONTAL);
        satir.setGravity(Gravity.CENTER_VERTICAL);
        satir.setMinimumHeight(px(64));
        satir.setPadding(px(16), px(8), px(12), px(8));

        LinearLayout yazilar = new LinearLayout(baglam);
        yazilar.setOrientation(LinearLayout.VERTICAL);
        TextView b = new TextView(baglam);
        b.setText(baslik);
        b.setTypeface(Cip.font(baglam, R.font.manrope_kalin));
        b.setTextColor(Cip.YAZI);
        b.setTextSize(15);
        yazilar.addView(b);
        if (aciklama != null) {
            TextView a = new TextView(baglam);
            a.setText(aciklama);
            a.setTypeface(Cip.font(baglam, R.font.manrope_orta));
            a.setTextColor(Cip.SOLUK);
            a.setTextSize(13);
            yazilar.addView(a);
        }
        satir.addView(yazilar, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        SwitchCompat s = new SwitchCompat(baglam);
        s.setThumbResource(R.drawable.anahtar_top);
        s.setTrackResource(R.drawable.anahtar_iz);
        s.setShowText(false);
        s.setChecked(acik);
        s.setContentDescription(baslik);
        s.setOnCheckedChangeListener(degisti);
        satir.addView(s, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, px(44)));
        satir.setOnClickListener(v -> s.toggle());
        anahtarlar.add(s);
        liste.addView(satir, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return s;
    }

    /** Parçalı seçimin kabı (seçenekleri Cip.parcali doldurur). */
    LinearLayout parcaliKap() {
        LinearLayout l = new LinearLayout(baglam);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setBackgroundResource(R.drawable.parca_zemin);
        int ic = px(3);
        l.setPadding(ic, ic, ic, ic);
        kap.addView(l, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(44)));
        return l;
    }

    /** Küçük açıklama satırı. */
    TextView not(String metin) {
        TextView t = new TextView(baglam);
        t.setText(metin);
        t.setTypeface(Cip.font(baglam, R.font.manrope_orta));
        t.setTextColor(Cip.SOLUK);
        t.setTextSize(13);
        t.setPadding(px(4), 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(8);
        kap.addView(t, lp);
        return t;
    }

    /** Eşit genişlikte ikincil düğmeler için yatay sıra. */
    LinearLayout dugmeSirasi(View... dugmeler) {
        LinearLayout l = new LinearLayout(baglam);
        l.setOrientation(LinearLayout.HORIZONTAL);
        for (View d : dugmeler) l.addView(d);
        kap.addView(l, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return l;
    }
}
