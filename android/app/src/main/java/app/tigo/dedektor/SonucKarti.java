package app.tigo.dedektor;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Alttaki sonuç kartı (Claude Design, "Dedektör Arayüzü" › YZ ekranları): küçük etiket, büyük
 * değer, altında isteğe bağlı küçük yazı; sağda işlemler; en altta seçim satırı. Yapay zekâ
 * modu ve oyunlar aynı kartı kullanır (bir anda biri).
 */
final class SonucKarti {

    final View panel;
    final TextView etiket;
    final TextView buyuk;
    final TextView alt;
    final LinearLayout islemler;
    final LinearLayout secim;
    private final float dp;
    private String sonAnahtar;

    SonucKarti(Activity etkinlik) {
        panel = etkinlik.findViewById(R.id.yz_paneli);
        etiket = etkinlik.findViewById(R.id.yz_etiket);
        buyuk = etkinlik.findViewById(R.id.yz_buyuk);
        alt = etkinlik.findViewById(R.id.yz_alt);
        islemler = etkinlik.findViewById(R.id.yz_islemler);
        secim = etkinlik.findViewById(R.id.yz_secim);
        dp = etkinlik.getResources().getDisplayMetrics().density;
    }

    void goster(boolean goster) {
        panel.setVisibility(goster ? View.VISIBLE : View.GONE);
    }

    boolean gorunuyor() {
        return panel.getVisibility() == View.VISIBLE;
    }

    /** İşlemleri, seçimi ve küçük yazıyı kaldırır. */
    void temizle() {
        islemler.removeAllViews();
        secim.removeAllViews();
        secim.setBackground(null);
        secim.setPadding(0, 0, 0, 0);
        secim.getLayoutParams().height = LinearLayout.LayoutParams.WRAP_CONTENT;
        secim.setVisibility(View.GONE);
        alt.setVisibility(View.GONE);
        sonAnahtar = null;
    }

    /** Seçim satırı düğme dizisi olarak. */
    LinearLayout dugmeSatiri() {
        secim.setVisibility(View.VISIBLE);
        return secim;
    }

    /** Seçim satırı parçalı seçim olarak (zeminli, 40 dp). */
    LinearLayout parcaliSatir() {
        secim.setVisibility(View.VISIBLE);
        secim.setBackgroundResource(R.drawable.parca_zemin);
        secim.getLayoutParams().height = Math.round(40 * dp);
        int ic = Math.round(3 * dp);
        secim.setPadding(ic, ic, ic, ic);
        return secim;
    }

    /**
     * Etiket ve değer; değer boşsa yer tutucu küçük ve soluk yazılır. boyut: değerin yazı
     * boyutu (sp). Değişmediyse görünümlere dokunmaz (saniyede birkaç kez çağrılır).
     */
    void yaz(CharSequence ust, CharSequence deger, String yerTutucu, int boyut) {
        boolean yer = deger == null || deger.length() == 0;
        if (yer) deger = yerTutucu;
        String anahtar = ust + "|" + deger + "|" + boyut;
        if (anahtar.equals(sonAnahtar)) return;
        sonAnahtar = anahtar;
        etiket.setText(ust);
        buyuk.setText(deger);
        buyuk.setTextColor(yer ? Cip.SOLUK : Cip.YAZI);
        buyuk.setTextSize(yer ? 18 : boyut);
    }

    /** Değerin altındaki küçük yazı (null: gizli). */
    void altYazi(CharSequence metin) {
        boolean bos = metin == null || metin.length() == 0;
        if (!bos && !metin.toString().equals(alt.getText().toString())) alt.setText(metin);
        alt.setVisibility(bos ? View.GONE : View.VISIBLE);
    }

    /** Değer kısa bir an büyür (tekrar, puan). */
    void zipla() {
        buyuk.animate().cancel();
        buyuk.setScaleX(1.2f);
        buyuk.setScaleY(1.2f);
        buyuk.setPivotX(0);
        buyuk.animate().scaleX(1f).scaleY(1f).setDuration(250).start();
    }
}
