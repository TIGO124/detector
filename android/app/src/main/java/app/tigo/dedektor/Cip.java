package app.tigo.dedektor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import java.util.function.IntConsumer;

/**
 * Kamera ekranının küçük parçaları (Claude Design, "Dedektör Arayüzü" › Tasarım dili): cam
 * haplar, mod sekmeleri, tonlu ve ikincil düğmeler, parçalı seçim, öğretilenlerin hapları.
 * Seçili olan vurgu renginde dolu; vurgu rengi ayarlardan seçilir.
 */
final class Cip {

    static final int CAM = 0xB80A0F1C;
    static final int CAM_CIZGI = 0x14FFFFFF;
    static final int LISTE = 0xFF151E31;
    static final int CIZGI_ACIK = 0xFF2A3448;
    static final int YAZI = 0xFFEEF2F7;
    static final int YAZI_IKINCIL = 0xFFDCE3EC;
    static final int YAZI_CAM = 0xFFC6CFDB;
    static final int SOLUK = 0xFF9AA6B8;
    static final int VURGU_USTU = 0xFF04140B;

    private static final SparseArray<Typeface> FONTLAR = new SparseArray<>();

    private Cip() {}

    static Typeface font(Context baglam, int kaynak) {
        Typeface t = FONTLAR.get(kaynak);
        if (t == null) {
            t = ResourcesCompat.getFont(baglam, kaynak);
            FONTLAR.put(kaynak, t);
        }
        return t;
    }

    static int saydam(int renk, int alfa) {
        return (renk & 0x00FFFFFF) | (alfa << 24);
    }

    static GradientDrawable hap(int dolgu, int cizgi, float dp) {
        GradientDrawable z = new GradientDrawable();
        z.setCornerRadius(999 * dp);
        z.setColor(dolgu);
        if (cizgi != 0) z.setStroke(Math.max(1, Math.round(dp)), cizgi);
        return z;
    }

    private static float dp(Context baglam) {
        return baglam.getResources().getDisplayMetrics().density;
    }

    private static Drawable ikon(Context baglam, int kaynak, int renk, int boyutDp) {
        Drawable d = ContextCompat.getDrawable(baglam, kaynak).mutate();
        d.setTint(renk);
        int b = Math.round(boyutDp * dp(baglam));
        d.setBounds(0, 0, b, b);
        return d;
    }

    private static LinearLayout.LayoutParams sarili(Context baglam, int yukseklikDp, int yanBoslukDp) {
        float dp = dp(baglam);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Math.round(yukseklikDp * dp));
        lp.setMarginStart(Math.round(yanBoslukDp * dp));
        lp.setMarginEnd(Math.round(yanBoslukDp * dp));
        return lp;
    }

    /** Kamera üstü hap: cam zemin; seçili olan vurgu renginde dolu. */
    static TextView olustur(Context baglam, String metin, boolean secili, int renk, View.OnClickListener tik) {
        return olustur(baglam, metin, 0, secili, renk, tik);
    }

    static TextView olustur(Context baglam, String metin, int ikon, boolean secili, int renk, View.OnClickListener tik) {
        float dp = dp(baglam);
        TextView t = new TextView(baglam);
        t.setText(metin);
        t.setTextSize(14);
        t.setTypeface(font(baglam, secili ? R.font.manrope_cok_kalin : R.font.manrope_kalin));
        int yazi = secili ? VURGU_USTU : YAZI_IKINCIL;
        t.setTextColor(yazi);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Math.round((ikon != 0 ? 12 : 16) * dp), 0, Math.round(16 * dp), 0);
        if (ikon != 0) {
            t.setCompoundDrawablesRelative(ikon(baglam, ikon, yazi, 18), null, null, null);
            t.setCompoundDrawablePadding(Math.round(7 * dp));
        }
        t.setBackground(secili ? hap(renk, renk, dp) : hap(CAM, CAM_CIZGI, dp));
        t.setLayoutParams(sarili(baglam, 44, 4));
        if (tik != null) t.setOnClickListener(tik);
        else t.setClickable(false);
        return t;
    }

    /** Alttaki mod sekmesi: yalnızca yazı; seçili olan vurgu renginde ve altında bir nokta. */
    static TextView sekme(Context baglam, String metin, boolean secili, int renk, View.OnClickListener tik) {
        float dp = dp(baglam);
        TextView t = new TextView(baglam);
        t.setText(metin);
        t.setTextSize(15);
        t.setTypeface(font(baglam, secili ? R.font.manrope_cok_kalin : R.font.manrope_kalin));
        t.setTextColor(secili ? renk : SOLUK);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Math.round(11 * dp), 0, Math.round(11 * dp), 0);
        GradientDrawable nokta = new GradientDrawable();
        nokta.setShape(GradientDrawable.OVAL);
        nokta.setColor(secili ? renk : 0);
        int b = Math.round(5 * dp);
        nokta.setSize(b, b);
        nokta.setBounds(0, 0, b, b);
        t.setCompoundDrawables(null, null, null, nokta);
        t.setCompoundDrawablePadding(Math.round(4 * dp));
        t.setLayoutParams(sarili(baglam, 48, 0));
        t.setOnClickListener(tik);
        return t;
    }

    /** Vurgu renginin açık tonunda düğme (ör. "Öğret"): kart içindeki ana işlem. */
    static TextView tonlu(Context baglam, String metin, int ikon, int renk, View.OnClickListener tik) {
        float dp = dp(baglam);
        TextView t = new TextView(baglam);
        t.setText(metin);
        t.setTextSize(14);
        t.setTypeface(font(baglam, R.font.manrope_cok_kalin));
        t.setTextColor(renk);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Math.round(12 * dp), 0, Math.round(16 * dp), 0);
        if (ikon != 0) {
            t.setCompoundDrawablesRelative(ikon(baglam, ikon, renk, 18), null, null, null);
            t.setCompoundDrawablePadding(Math.round(6 * dp));
        }
        t.setBackground(hap(saydam(renk, 0x24), saydam(renk, 0x66), dp));
        t.setLayoutParams(sarili(baglam, 44, 0));
        t.setOnClickListener(tik);
        return t;
    }

    /** Kart içindeki yuvarlak simge düğmesi (ör. sayacı sıfırla). */
    static ImageButton ikonDugme(Context baglam, int ikon, String aciklama, View.OnClickListener tik) {
        float dp = dp(baglam);
        ImageButton b = new ImageButton(baglam);
        b.setImageDrawable(ikon(baglam, ikon, YAZI_IKINCIL, 20));
        b.setBackground(hap(0x0AFFFFFF, 0x1AFFFFFF, dp));
        b.setContentDescription(aciklama);
        b.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(44 * dp), Math.round(44 * dp));
        lp.setMarginStart(Math.round(6 * dp));
        b.setLayoutParams(lp);
        b.setOnClickListener(tik);
        return b;
    }

    /** Çerçeveli ikincil düğme; satırda eşit genişlikte dizilir. */
    static TextView ikincil(Context baglam, String metin, int ikon, View.OnClickListener tik) {
        float dp = dp(baglam);
        TextView t = new TextView(baglam);
        t.setText(metin);
        t.setTextSize(14);
        t.setTypeface(font(baglam, R.font.manrope_kalin));
        t.setTextColor(YAZI_IKINCIL);
        t.setGravity(Gravity.CENTER);
        if (ikon != 0) {
            t.setCompoundDrawablesRelative(ikon(baglam, ikon, YAZI_IKINCIL, 17), null, null, null);
            t.setCompoundDrawablePadding(Math.round(6 * dp));
        }
        t.setBackground(hap(0, CIZGI_ACIK, dp));
        ortala(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.round(40 * dp), 1f);
        lp.setMarginStart(Math.round(3 * dp));
        lp.setMarginEnd(Math.round(3 * dp));
        t.setLayoutParams(lp);
        t.setOnClickListener(tik);
        return t;
    }

    /**
     * Parçalı seçim: kap (zemini çizilmiş, yatay) içine eşit genişlikte seçenekler. Seçili olan
     * dolgu renginde (yaprakta açık renk, kamera kartında vurgu rengi).
     */
    static void parcali(LinearLayout kap, String[] adlar, int secili, int dolgu, int dolguYazisi, IntConsumer sec) {
        Context baglam = kap.getContext();
        float dp = dp(baglam);
        kap.removeAllViews();
        for (int i = 0; i < adlar.length; i++) {
            final int sira = i;
            boolean bu = i == secili;
            TextView t = new TextView(baglam);
            t.setText(adlar[i]);
            t.setTextSize(14);
            t.setTypeface(font(baglam, bu ? R.font.manrope_cok_kalin : R.font.manrope_kalin));
            t.setTextColor(bu ? dolguYazisi : YAZI_CAM);
            t.setGravity(Gravity.CENTER);
            t.setBackground(bu ? hap(dolgu, 0, dp) : null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.setMarginStart(Math.round(3 * dp));
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> sec.accept(sira));
            kap.addView(t);
        }
    }

    /** Öğretilenler listesinde bir kayıt: ad, örnek sayısı (vurgu renginde) ve silme işareti. */
    static TextView kayit(Context baglam, String ad, int sayi, int renk, View.OnClickListener tik) {
        float dp = dp(baglam);
        TextView t = new TextView(baglam);
        SpannableStringBuilder s = new SpannableStringBuilder(ad);
        if (sayi >= 0) {
            s.append("  ");
            int bas = s.length();
            s.append(String.valueOf(sayi));
            s.setSpan(new ForegroundColorSpan(renk), bas, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                s.setSpan(new TypefaceSpan(font(baglam, R.font.space_grotesk_kalin)), bas, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        t.setText(s);
        t.setTextSize(14);
        t.setTypeface(font(baglam, R.font.manrope_kalin));
        t.setTextColor(YAZI);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Math.round(14 * dp), 0, Math.round((tik != null ? 10 : 14) * dp), 0);
        if (tik != null) {
            t.setCompoundDrawablesRelative(null, null, ikon(baglam, R.drawable.ic_kapat, SOLUK, 16), null);
            t.setCompoundDrawablePadding(Math.round(8 * dp));
        }
        t.setBackground(hap(LISTE, CIZGI_ACIK, dp));
        LinearLayout.LayoutParams lp = sarili(baglam, 40, 0);
        lp.setMarginEnd(Math.round(8 * dp));
        t.setLayoutParams(lp);
        if (tik != null) {
            t.setContentDescription(ad + ", " + sayi + " örnek, sil");
            t.setOnClickListener(tik);
        }
        return t;
    }

    /**
     * Geniş düğmede simge ile yazıyı birlikte ortalar: TextView'in baş simgesi normalde kenara
     * yapışır, yazı ortada kalır. İçerik genişliği kadar boşluk iki yana eşit dağıtılır.
     */
    static void ortala(TextView t) {
        t.addOnLayoutChangeListener((v, sol, ust, sag, alt, eskiSol, eskiUst, eskiSag, eskiAlt) -> {
            Drawable d = t.getCompoundDrawablesRelative()[0];
            int icerik = Math.round(t.getPaint().measureText(t.getText().toString()))
                + (d == null ? 0 : d.getBounds().width() + t.getCompoundDrawablePadding());
            int bosluk = Math.max(0, (sag - sol - icerik) / 2);
            if (t.getPaddingStart() != bosluk || t.getPaddingEnd() != bosluk) {
                t.post(() -> t.setPaddingRelative(bosluk, t.getPaddingTop(), bosluk, t.getPaddingBottom()));
            }
        });
    }

    /** Simgeli metin düğmesinin simgesini (ör. XML'deki 24 dp'lik) küçültür ve renklendirir. */
    static void simge(TextView t, int ikon, int renk, int boyutDp) {
        t.setCompoundDrawablesRelative(ikon == 0 ? null : ikon(t.getContext(), ikon, renk, boyutDp), null, null, null);
    }

    static ColorStateList durumRengi(int secili, int normal) {
        return new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}}, new int[] {secili, normal});
    }
}
