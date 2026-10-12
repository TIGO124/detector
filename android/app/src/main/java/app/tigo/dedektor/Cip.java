package app.tigo.dedektor;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Kamera ekranının hap biçimli seçenekleri ve sayaç yazıları. */
final class Cip {

    private static final int KOYU = 0xFF0B1120;
    private static final int SOLUK_ZEMIN = 0xB30B1120;
    private static final int ACIK_YAZI = 0xFFE2E8F0;

    private Cip() {}

    /** Seçili olan, verilen renkte dolu ve kalın yazılı. */
    static TextView olustur(Context baglam, String metin, boolean secili, int renk, View.OnClickListener tik) {
        float dp = baglam.getResources().getDisplayMetrics().density;
        TextView t = new TextView(baglam);
        t.setText(metin);
        t.setTextSize(14);
        t.setTextColor(secili ? KOYU : ACIK_YAZI);
        t.setTypeface(null, secili ? Typeface.BOLD : Typeface.NORMAL);
        t.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (8 * dp));
        GradientDrawable zemin = new GradientDrawable();
        zemin.setCornerRadius(999 * dp);
        zemin.setColor(secili ? renk : SOLUK_ZEMIN);
        t.setBackground(zemin);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart((int) (4 * dp));
        lp.setMarginEnd((int) (4 * dp));
        t.setLayoutParams(lp);
        if (tik != null) t.setOnClickListener(tik);
        return t;
    }

    /** "Yüz  1" gibi: sayı renkli ve kalın. */
    static CharSequence sayac(String ad, int sayi, int renk) {
        SpannableStringBuilder s = new SpannableStringBuilder(ad + "  " + sayi);
        int bas = ad.length() + 2;
        s.setSpan(new ForegroundColorSpan(renk), bas, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new StyleSpan(Typeface.BOLD), bas, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }
}
