package app.tigo.dedektor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.Calendar;

/** Egzersiz geçmişi penceresi: seçili türün son 7 günü çubuk grafikte, altında bütün türlerin toplamları. */
final class GecmisPenceresi {

    private static final String[] GUNLER = {"Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"};

    private GecmisPenceresi() {}

    static void goster(Context baglam, EgzersizGecmisi gecmis, EgzersizSayaci.Tur tur, int renk) {
        float dp = baglam.getResources().getDisplayMetrics().density;
        long bugun = EgzersizGecmisi.bugun();
        LinearLayout kap = new LinearLayout(baglam);
        kap.setOrientation(LinearLayout.VERTICAL);
        int ic = Math.round(20 * dp);
        kap.setPadding(ic, Math.round(8 * dp), ic, 0);

        kap.addView(yazi(baglam, tur.ad.toUpperCase(java.util.Locale.forLanguageTag("tr-TR")) + " · SON 7 GÜN", 11, Cip.SOLUK, R.font.manrope_cok_kalin));
        Grafik g = new Grafik(baglam, gecmis.haftalik(bugun, tur), gunAdlari(), renk);
        kap.addView(g, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(170 * dp)));

        for (EgzersizSayaci.Tur t : EgzersizSayaci.Tur.values()) {
            String satir = t.ad + ":  bugün " + deger(t, gecmis.gunluk(bugun, t)) + " · 7 gün " + deger(t, gecmis.haftalikToplam(bugun, t));
            TextView s = yazi(baglam, satir, 14, t == tur ? Cip.YAZI : Cip.YAZI_CAM, t == tur ? R.font.manrope_cok_kalin : R.font.manrope_kalin);
            s.setPadding(0, Math.round(6 * dp), 0, 0);
            kap.addView(s);
        }
        new AlertDialog.Builder(baglam)
            .setTitle("Egzersiz geçmişi")
            .setView(kap)
            .setPositiveButton("Tamam", null)
            .show();
    }

    /** Tekrar sayısı ya da plankta süre (dakika:saniye). */
    static String deger(EgzersizSayaci.Tur tur, int sayi) {
        if (!tur.sureli()) return String.valueOf(sayi);
        return sayi < 60 ? sayi + " sn" : sayi / 60 + ":" + String.format(java.util.Locale.US, "%02d", sayi % 60);
    }

    private static String[] gunAdlari() {
        String[] adlar = new String[EgzersizGecmisi.GUN_SAYISI];
        Calendar c = Calendar.getInstance();
        for (int i = adlar.length - 1; i >= 0; i--) {
            adlar[i] = i == adlar.length - 1 ? "Bugün" : GUNLER[c.get(Calendar.DAY_OF_WEEK) - 1];
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return adlar;
    }

    private static TextView yazi(Context baglam, String metin, int boyut, int renk, int font) {
        TextView t = new TextView(baglam);
        t.setText(metin);
        t.setTextSize(boyut);
        t.setTextColor(renk);
        t.setTypeface(Cip.font(baglam, font));
        return t;
    }

    /** Yedi günlük çubuk grafik: değer çubuğun üstünde, gün adı altında. */
    static final class Grafik extends View {
        private final int[] degerler;
        private final String[] adlar;
        private final Paint cubuk = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bos = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint yazi = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF kutu = new RectF();
        private final float dp;

        Grafik(Context baglam, int[] degerler, String[] adlar, int renk) {
            super(baglam);
            this.degerler = degerler;
            this.adlar = adlar;
            dp = baglam.getResources().getDisplayMetrics().density;
            cubuk.setColor(renk);
            bos.setColor(Cip.CIZGI_ACIK);
            yazi.setColor(Cip.YAZI_CAM);
            yazi.setTextAlign(Paint.Align.CENTER);
            yazi.setTypeface(Cip.font(baglam, R.font.manrope_kalin));
            yazi.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, baglam.getResources().getDisplayMetrics()));
            StringBuilder aciklama = new StringBuilder();
            for (int i = 0; i < degerler.length; i++) aciklama.append(adlar[i]).append(' ').append(degerler[i]).append(", ");
            setContentDescription(aciklama.toString());
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = degerler.length;
            int enCok = 1;
            for (int d : degerler) enCok = Math.max(enCok, d);
            float yaziBoyu = yazi.getTextSize() + 6 * dp;
            float ust = yaziBoyu;
            float alt = getHeight() - yaziBoyu;
            float aralik = getWidth() / (float) n;
            float genislik = aralik * 0.55f;
            for (int i = 0; i < n; i++) {
                float orta = aralik * (i + 0.5f);
                float boy = (alt - ust) * degerler[i] / enCok;
                kutu.set(orta - genislik / 2, alt - Math.max(boy, 3 * dp), orta + genislik / 2, alt);
                c.drawRoundRect(kutu, 4 * dp, 4 * dp, degerler[i] > 0 ? cubuk : bos);
                if (degerler[i] > 0) c.drawText(String.valueOf(degerler[i]), orta, kutu.top - 4 * dp, yazi);
                c.drawText(adlar[i], orta, getHeight() - 4 * dp, yazi);
            }
        }
    }
}
