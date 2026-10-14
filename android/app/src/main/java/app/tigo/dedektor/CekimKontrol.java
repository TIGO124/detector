package app.tigo.dedektor;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Locale;

/**
 * Deklanşör: dokununca fotoğraf (zamanlayıcı açıksa geri sayımla), basılı tutunca efektli
 * video (bırakınca ya da 10 saniyede biter). Zafer işareti ve gülümseme de çekebilir. Son
 * çekim sol alttaki yuvarlakta: dokununca açılır, basılı tutunca paylaşılır.
 */
final class CekimKontrol {

    /** Gülümseme puanı bu eşiği geçmeli. */
    private static final float GULUMSEME_ESIGI = 0.6f;

    private final AppCompatActivity etkinlik;
    private final Ayarlar ayarlar;
    private final Fotograf fotograf;
    private final KlipKaydedici klip;
    private final Geribildirim geribildirim;
    private final TextView geriSayim;
    private final TextView kayitSuresi;
    private final ImageView kucukResim;
    private final ImageButton deklansor;
    /** Ayarlar ve Öğret yaprakları: açıkken kendiliğinden çekilmez. */
    private final View[] yapraklar;
    private final SharedPreferences tercihler;
    private final Handler ana = new Handler(Looper.getMainLooper());
    private final Tetik zafer = new Tetik(1000, 4000);
    private final Tetik gulumseme = new Tetik(800, 4000);
    private final Runnable sayac = this::kayitSayaci;
    private final Runnable geriSayimAdimi = this::geriSayimAdimi;
    /** Geri sayımda kalan saniye (0: sayılmıyor). Arayüz iş parçacığında. */
    private int kalan;
    private volatile boolean mesgul;
    private Uri sonUri;
    private String sonTur;

    CekimKontrol(AppCompatActivity etkinlik, Ayarlar ayarlar, CizimView cizim, Geribildirim geribildirim) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.geribildirim = geribildirim;
        fotograf = new Fotograf(etkinlik, etkinlik.findViewById(R.id.onizleme), cizim, etkinlik.findViewById(R.id.flas),
            (uri, kucuk) -> sonCekim(uri, kucuk, "image/jpeg"));
        klip = new KlipKaydedici(etkinlik, cizim, this::klipBitti);
        geriSayim = etkinlik.findViewById(R.id.geri_sayim);
        kayitSuresi = etkinlik.findViewById(R.id.kayit_suresi);
        kucukResim = etkinlik.findViewById(R.id.kucuk_resim);
        deklansor = etkinlik.findViewById(R.id.deklansor);
        yapraklar = new View[] {etkinlik.findViewById(R.id.ayar_kaydirma), etkinlik.findViewById(R.id.ogret_kaydirma)};
        tercihler = etkinlik.getSharedPreferences("ipuclari", AppCompatActivity.MODE_PRIVATE);
        kucukResim.setClipToOutline(true);
        bagla();
    }

    KlipKaydedici klip() {
        return klip;
    }

    private void bagla() {
        deklansor.setOnClickListener(v -> {
            if (klip.kayitta()) klipBitir();
            else if (kalan > 0) geriSayimiKes();
            else if (ayarlar.zamanlayici > 0) geriSay(ayarlar.zamanlayici);
            else cek();
        });
        deklansor.setOnLongClickListener(v -> {
            klipBaslat();
            return true;
        });
        // Basılı tutarak başlayan video bırakınca biter.
        deklansor.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && klip.kayitta()
                && klip.sureMs() > 300) {
                klipBitir();
                return true;
            }
            return false;
        });
        kucukResim.setOnClickListener(v -> ac());
        kucukResim.setOnLongClickListener(v -> {
            paylas();
            return true;
        });
    }

    // ---------- Fotoğraf ----------

    private void cek() {
        fotograf.cek();
        geribildirim.titret(30);
        long simdi = SystemClock.uptimeMillis();
        zafer.beklet(simdi);
        gulumseme.beklet(simdi);
        if (!tercihler.getBoolean("video", false)) {
            tercihler.edit().putBoolean("video", true).apply();
            toast("İpucu: video için deklanşöre basılı tut.");
        }
    }

    private void geriSay(int saniye) {
        kalan = saniye;
        geriSayim.setVisibility(View.VISIBLE);
        geriSayimAdimi();
    }

    private void geriSayimAdimi() {
        if (kalan <= 0) {
            geriSayim.setVisibility(View.GONE);
            cek();
            return;
        }
        geriSayim.setText(String.valueOf(kalan));
        geribildirim.ses(ToneGenerator.TONE_PROP_BEEP, 80);
        kalan--;
        ana.postDelayed(geriSayimAdimi, 1000);
    }

    private void geriSayimiKes() {
        ana.removeCallbacks(geriSayimAdimi);
        kalan = 0;
        geriSayim.setVisibility(View.GONE);
    }

    /** El modelinin sonucu: zafer işareti 1 saniye tutulunca 3 saniyelik geri sayım. */
    void el(List<ElTakibi.ElDurumu> eller, long zaman) {
        boolean var = false;
        if (ayarlar.hareketleCek && kendiligindenCekilir()) {
            for (ElTakibi.ElDurumu el : eller) if ("Victory".equals(el.hareketKodu)) var = true;
        }
        if (zafer.guncelle(var && !mesgul, zaman)) ana.post(() -> {
            if (kalan > 0 || klip.kayitta()) return;
            geribildirim.titret(40);
            geriSay(3);
        });
    }

    /** Yüz modelinin sonucu: gülümseme 0,8 saniye sürünce çeker. */
    void yuz(float gulumsemePuani, long zaman) {
        boolean var = ayarlar.gulumseyinceCek && kendiligindenCekilir() && gulumsemePuani > GULUMSEME_ESIGI;
        if (gulumseme.guncelle(var && !mesgul, zaman)) ana.post(() -> {
            if (kalan > 0 || klip.kayitta()) return;
            cek();
        });
    }

    /**
     * Oyunlarda (zafer = makas), yapay zekâ modunda (öğretilen hareketler) ve bir yaprak
     * açıkken kendiliğinden çekilmez.
     */
    private boolean kendiligindenCekilir() {
        if (ayarlar.mod == Ayarlar.Mod.OYUN || ayarlar.mod == Ayarlar.Mod.YZ) return false;
        for (View y : yapraklar) if (y.getVisibility() == View.VISIBLE) return false;
        return true;
    }

    // ---------- Video ----------

    private void klipBaslat() {
        if (klip.kayitta()) return;
        if (!fotograf.depoIzniVar()) {
            fotograf.depoIzniIste();
            return;
        }
        geriSayimiKes();
        klip.baslat();
        mesgul = true;
        geribildirim.titret(50);
        deklansor.setBackgroundResource(R.drawable.deklansor_kayit);
        deklansor.setContentDescription("Videoyu bitir");
        kayitSuresi.setVisibility(View.VISIBLE);
        kayitSayaci();
    }

    void klipBitir() {
        klip.bitir();
        kayitArayuzunuKapat();
        geribildirim.titret(50);
    }

    private void kayitArayuzunuKapat() {
        ana.removeCallbacks(sayac);
        mesgul = false;
        deklansor.setBackgroundResource(R.drawable.deklansor);
        deklansor.setContentDescription("Fotoğraf çek. Video için basılı tut");
        kayitSuresi.setVisibility(View.GONE);
    }

    private void kayitSayaci() {
        if (!klip.kayitta()) {
            kayitArayuzunuKapat();
            return;
        }
        long s = klip.sureMs() / 1000;
        kayitSuresi.setText(String.format(Locale.US, "●  0:%02d / 0:%02d", s, KlipKaydedici.EN_UZUN_MS / 1000));
        ana.postDelayed(sayac, 250);
    }

    private void klipBitti(Uri uri, Bitmap kucuk, String hata) {
        if (hata != null) {
            toast(hata);
            return;
        }
        toast("Video galeriye kaydedildi.");
        sonCekim(uri, kucuk, "video/mp4");
    }

    // ---------- Son çekim ----------

    private void sonCekim(Uri uri, Bitmap kucuk, String tur) {
        sonUri = uri;
        sonTur = tur;
        if (kucuk != null) kucukResim.setImageBitmap(kucuk);
        kucukResim.setContentDescription(tur.startsWith("video") ? "Son video. Dokun: aç, basılı tut: paylaş" : "Son fotoğraf. Dokun: aç, basılı tut: paylaş");
        kucukResim.setVisibility(View.VISIBLE);
    }

    private void ac() {
        if (sonUri == null) return;
        try {
            etkinlik.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(sonUri, sonTur)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (ActivityNotFoundException e) {
            toast("Bunu açacak uygulama bulunamadı.");
        }
    }

    private void paylas() {
        if (sonUri == null) return;
        Intent gonder = new Intent(Intent.ACTION_SEND).setType(sonTur).putExtra(Intent.EXTRA_STREAM, sonUri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            etkinlik.startActivity(Intent.createChooser(gonder, "Paylaş"));
        } catch (ActivityNotFoundException e) {
            toast("Paylaşacak uygulama bulunamadı.");
        }
    }

    /** Ekrandan çıkılıyor: süren video kaydedilir, geri sayım durur. */
    void durdur() {
        if (klip.kayitta()) klipBitir();
        geriSayimiKes();
    }

    private void toast(String mesaj) {
        Toast.makeText(etkinlik, mesaj, Toast.LENGTH_LONG).show();
    }
}
