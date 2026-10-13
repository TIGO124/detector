package app.tigo.dedektor;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Öğret" paneli: bir ad yazılır, kısa geri sayımdan sonra 3 saniye örnek kaydedilir (el
 * hareketi, TİD harfi ya da yüz). Hareket ve TİD için "Eğit" modeli telefonda eğitir; örnekler
 * bilgisayara gönderilip ekran kartıyla da eğitilebilir (egitim/kisisel.py), çıkan model geri
 * yüklenir. Kişi ve nesne eklemede eğitim gerekmez: imzalar doğrudan saklanır.
 */
final class OgretPaneli {

    private static final String ETIKET = "Dedektor";
    private static final int GERI_SAYIM = 3;
    private static final int KAYIT = 0xFFFF4D5E;
    private static final int KAYIT_ACIK = 0xFFFF8A95;

    private final AppCompatActivity etkinlik;
    private final Ayarlar ayarlar;
    private final YzTanima yz;
    /** Model ya da kayıtlar değişince (ipuçları güncellensin). */
    private final Runnable degisti;
    /** Kaydırma kabı (görünürlük) ve içindeki yaprak (zemin, alt boşluk). */
    private final ScrollView kap;
    private final View panel;
    private final TextView baslik;
    private final TextView aciklama;
    private final EditText etiketKutusu;
    private final TextView kaydet;
    private final TextView durum;
    private final ProgressBar ilerleme;
    private final TextView listeBasligi;
    private final LinearLayout etiketler;
    /** Eğit, bilgisayara gönder, model yükle (kişi ve nesnede yok). */
    private final View egitimDugmeleri;
    private final TextView egitDugmesi;
    /** Kameranın üstündeki yuvarlak sayaç: geri sayım, sonra kaydın kalan saniyesi. */
    private final TextView geriSayim;
    private final Handler ana = new Handler(Looper.getMainLooper());
    private final ExecutorService is = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String[]> modelSecici;
    private Ayarlar.Yz tur;
    private boolean mesgul;
    /** Kaydet'e basıldı, kayıt henüz başlamadı (3-2-1). */
    private boolean geriSayiyor;

    OgretPaneli(AppCompatActivity etkinlik, Ayarlar ayarlar, YzTanima yz, TextView geriSayim, Runnable degisti) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.yz = yz;
        this.geriSayim = geriSayim;
        this.degisti = degisti;
        kap = etkinlik.findViewById(R.id.ogret_kaydirma);
        panel = etkinlik.findViewById(R.id.ogret_paneli);
        baslik = etkinlik.findViewById(R.id.ogret_baslik);
        aciklama = etkinlik.findViewById(R.id.ogret_aciklama);
        etiketKutusu = etkinlik.findViewById(R.id.ogret_etiket);
        kaydet = etkinlik.findViewById(R.id.ogret_kaydet);
        durum = etkinlik.findViewById(R.id.ogret_durum);
        ilerleme = etkinlik.findViewById(R.id.ogret_ilerleme);
        listeBasligi = etkinlik.findViewById(R.id.ogret_liste_basligi);
        etiketler = etkinlik.findViewById(R.id.ogret_etiketler);
        egitimDugmeleri = etkinlik.findViewById(R.id.ogret_egitim);
        egitDugmesi = etkinlik.findViewById(R.id.ogret_egit);
        TextView gonder = etkinlik.findViewById(R.id.ogret_gonder);
        TextView yukle = etkinlik.findViewById(R.id.ogret_yukle);
        Cip.simge(gonder, R.drawable.ic_gonder, Cip.YAZI_IKINCIL, 17);
        Cip.simge(yukle, R.drawable.ic_yukle, Cip.YAZI_IKINCIL, 17);
        Cip.ortala(gonder);
        Cip.ortala(yukle);
        Cip.ortala(egitDugmesi);
        modelSecici = etkinlik.registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::modelYukle);
        kaydet.setOnClickListener(v -> kaydiBaslat());
        egitDugmesi.setOnClickListener(v -> egit());
        gonder.setOnClickListener(v -> disaAktar());
        yukle.setOnClickListener(v -> {
            if (!mesgul) modelSecici.launch(new String[] {"application/json", "application/octet-stream", "text/plain"});
        });
        etkinlik.findViewById(R.id.ogret_kapat).setOnClickListener(v -> kapat());
    }

    View panel() {
        return panel;
    }

    boolean acik() {
        return kap.getVisibility() == View.VISIBLE;
    }

    /**
     * Yaprak kapanırken (çarpı, geri tuşu, başka bölüme geçiş) geri sayım iptal edilir; süren
     * kayıt hemen biter ve o ana kadar alınan örnekler kaydedilir. Yoksa sayaç ekranda kalır ve
     * kayıt artık görünmeyen bölüm için başlardı.
     */
    void kapat() {
        klavyeyiKapat();
        kap.setVisibility(View.GONE);
        if (geriSayiyor) {
            ana.removeCallbacksAndMessages(null);
            geriSayiyor = false;
            mesgul = false;
            geriSayim.setVisibility(View.GONE);
            durum.setText(null);
        } else if (tur != null && yz.kayitta(tur)) {
            yz.kaydiKes();
        }
    }

    void ac(Ayarlar.Yz tur) {
        // Aynı bölümde ad kalır (bir hareket birkaç kez kaydedilir); başka bölümde boş başlar.
        if (tur != this.tur) etiketKutusu.setText(null);
        this.tur = tur;
        switch (tur) {
            case KISI:
                yazilar("Kişi ekle", "Geri sayımdan sonra 3 saniye kameraya bak, başını hafifçe sağa sola çevir.",
                    "Ad (ör. Ayşe)", "KAYITLI KİŞİLER");
                break;
            case NESNE:
                yazilar("Nesne öğret", "Nesneyi 3 saniye ortadaki çerçevede farklı açılardan göster. Yanlış tanırsa çerçeve boşken \""
                        + NesneTanima.BOS + "\" adıyla da kaydet.",
                    "Ad (ör. Anahtarım)", "ÖĞRETİLEN NESNELER");
                break;
            case TID:
                yazilar("TİD harfi öğret", "Harfi 3 saniye işaretle; hareketli harflerde hareketi tekrarla. Her harfi 2-3 kez kaydet, sonra Eğit'e bas.",
                    "Harf (ör. A)", "ÖĞRETTİKLERİN");
                break;
            default:
                yazilar("El hareketi öğret", "Hareketi 3 saniye farklı açılardan göster. En az iki hareket kaydet, sonra Eğit'e bas.",
                    "Ad (ör. Selam)", "ÖĞRETTİKLERİN");
                break;
        }
        renkleriAyarla();
        durum.setText(null);
        ilerleme.setVisibility(View.GONE);
        egitimDugmeleri.setVisibility(egitimsiz() ? View.GONE : View.VISIBLE);
        etiketleriGoster();
        kap.scrollTo(0, 0);
        kap.setVisibility(View.VISIBLE);
    }

    private void yazilar(String b, String a, String ipucu, String liste) {
        baslik.setText(b);
        aciklama.setText(a);
        etiketKutusu.setHint(ipucu);
        listeBasligi.setText(liste);
    }

    /** Kişi ve nesnede eğitim yok: kaydedilen imzalar doğrudan karşılaştırılır. */
    private boolean egitimsiz() {
        return tur == Ayarlar.Yz.KISI || tur == Ayarlar.Yz.NESNE;
    }

    private void renkleriAyarla() {
        kayitGorunumu(false);
        egitDugmesi.setBackgroundTintList(ColorStateList.valueOf(ayarlar.renk));
        Cip.simge(egitDugmesi, R.drawable.ic_parilti, Cip.VURGU_USTU, 20);
    }

    /** Kaydet düğmesi: normalde vurgu renginde; kayıt sürerken kırmızı tonlu "Kaydediliyor". */
    private void kayitGorunumu(boolean kayitta) {
        if (kayitta) {
            GradientDrawable nokta = new GradientDrawable();
            nokta.setShape(GradientDrawable.OVAL);
            nokta.setColor(KAYIT);
            int b = Math.round(10 * etkinlik.getResources().getDisplayMetrics().density);
            nokta.setBounds(0, 0, b, b);
            kaydet.setCompoundDrawablesRelative(nokta, null, null, null);
            kaydet.setText("Kaydediliyor");
            kaydet.setTextColor(KAYIT_ACIK);
            kaydet.setBackgroundTintList(ColorStateList.valueOf(Cip.saydam(KAYIT, 0x30)));
        } else {
            kaydet.setCompoundDrawablesRelative(null, null, null, null);
            kaydet.setText("Kaydet");
            kaydet.setTextColor(Cip.VURGU_USTU);
            kaydet.setBackgroundTintList(ColorStateList.valueOf(ayarlar.renk));
        }
    }

    private void etiketleriGoster() {
        etiketler.removeAllViews();
        Map<String, Integer> sayilar = yz.veri(tur).sayilar();
        if (sayilar.isEmpty()) {
            etiketler.addView(Cip.kayit(etkinlik, "Henüz yok", -1, ayarlar.renk, null));
            return;
        }
        for (Map.Entry<String, Integer> e : sayilar.entrySet()) {
            String ad = e.getKey();
            etiketler.addView(Cip.kayit(etkinlik, ad, e.getValue(), ayarlar.renk, v -> silmeyiSor(ad)));
        }
    }

    private void silmeyiSor(String ad) {
        if (mesgul) return;
        new AlertDialog.Builder(etkinlik)
            .setMessage("\"" + ad + "\" silinsin mi?")
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Sil", (d, w) -> arkada(() -> {
                YzVeri v = yz.veri(tur);
                v.sil(ad);
                v.kaydet();
                return egitimsiz() ? ad + " silindi." : ad + " silindi. Modeli güncellemek için Eğit'e bas.";
            }))
            .show();
    }

    // ---------- Kayıt ----------

    private void kaydiBaslat() {
        if (mesgul) return;
        String ad = etiketKutusu.getText().toString().trim();
        if (ad.isEmpty()) {
            durum.setText("Önce bir ad yaz.");
            return;
        }
        klavyeyiKapat();
        mesgul = true;
        geriSayiyor = true;
        for (int i = 0; i < GERI_SAYIM; i++) {
            String s = String.valueOf(GERI_SAYIM - i);
            ana.postDelayed(() -> sayacYaz(s, Cip.YAZI), i * 1000L);
        }
        ana.postDelayed(() -> {
            geriSayiyor = false;
            yz.kayitBaslat(tur, ad);
            kayitGorunumu(true);
            ilerleme.setProgressTintList(ColorStateList.valueOf(KAYIT));
            ilerleme.setProgress(0);
            ilerleme.setVisibility(View.VISIBLE);
            kayitIzle();
        }, GERI_SAYIM * 1000L);
        durum.setText("Hazırlan…");
    }

    private void kayitIzle() {
        if (yz.kayitta(tur)) {
            long kalan = yz.kayitKalanMs();
            sayacYaz(String.valueOf((kalan + 999) / 1000), KAYIT_ACIK);
            ilerleme.setProgress(Math.round(100 - 100f * kalan / YzTanima.KAYIT_MS));
            durum.setText(yz.kayitSayisi() + " örnek alındı");
            ana.postDelayed(this::kayitIzle, 100);
            return;
        }
        geriSayim.setVisibility(View.GONE);
        kayitGorunumu(false);
        is.execute(() -> {
            String sonuc;
            try {
                int n = yz.kayitBitir();
                if (n == 0) sonuc = tur == Ayarlar.Yz.KISI ? "Yüz görülmedi, tekrar dene." : tur == Ayarlar.Yz.NESNE ? "Görüntü alınamadı, tekrar dene." : "El görülmedi, tekrar dene.";
                else sonuc = n + " örnek kaydedildi." + (egitimsiz() ? "" : " Bitince Eğit'e bas.");
            } catch (Exception e) {
                Log.e(ETIKET, "Örnekler kaydedilemedi", e);
                sonuc = "Kaydedilemedi: " + e.getMessage();
            }
            String s = sonuc;
            ana.post(() -> bitti(s));
        });
    }

    private void sayacYaz(String s, int renk) {
        geriSayim.setText(s);
        geriSayim.setTextColor(renk);
        geriSayim.setVisibility(View.VISIBLE);
    }

    // ---------- Eğitim, dışa ve içe aktarma ----------

    private void egit() {
        if (mesgul) return;
        mesgul = true;
        ilerleme.setProgressTintList(ColorStateList.valueOf(ayarlar.renk));
        ilerleme.setProgress(0);
        ilerleme.setVisibility(View.VISIBLE);
        durum.setText("Eğitiliyor…");
        Ayarlar.Yz t = tur;
        is.execute(() -> {
            String sonuc;
            try {
                SinirAgi.Sonuc s = yz.egit(t, oran -> ana.post(() -> ilerleme.setProgress(Math.round(oran * 100))));
                sonuc = "Eğitildi · doğruluk %" + Math.round(100 * s.dogruluk);
                // Öğretilenler birbirine çok benziyorsa model ayıramaz: ne yapılacağını söyle.
                if (s.dogruluk < 0.7f) sonuc += ". Öğrettiklerin birbirine çok benziyor; daha farklı göster ya da her birini birkaç kez daha kaydet.";
            } catch (IllegalStateException e) {
                sonuc = e.getMessage();
            } catch (Exception e) {
                Log.e(ETIKET, "Eğitim başarısız", e);
                sonuc = "Eğitilemedi: " + e.getMessage();
            }
            String s = sonuc;
            ana.post(() -> bitti(s));
        });
    }

    private void disaAktar() {
        if (mesgul) return;
        Ayarlar.Yz t = tur;
        if (yz.veri(t).toplam() == 0) {
            durum.setText("Gönderilecek örnek yok. Önce bir şey kaydet.");
            return;
        }
        is.execute(() -> {
            try {
                File f = yz.disaAktar(t);
                Uri uri = FileProvider.getUriForFile(etkinlik, etkinlik.getPackageName() + ".fileprovider", f);
                Intent gonder = new Intent(Intent.ACTION_SEND)
                    .setType("application/octet-stream")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ana.post(() -> etkinlik.startActivity(Intent.createChooser(gonder, "Örnekleri gönder")));
            } catch (Exception e) {
                Log.e(ETIKET, "Örnekler paylaşılamadı", e);
                ana.post(() -> durum.setText("Gönderilemedi: " + e.getMessage()));
            }
        });
    }

    private void modelYukle(Uri uri) {
        if (uri == null) return;
        Ayarlar.Yz t = tur;
        mesgul = true;
        arkada(() -> {
            try (InputStream g = etkinlik.getContentResolver().openInputStream(uri)) {
                if (g == null) throw new IllegalArgumentException("Dosya açılamadı.");
                yz.modeliYukle(t, YzTanima.metin(g));
            }
            return "Model yüklendi.";
        });
    }

    private interface Is {
        String calistir() throws Exception;
    }

    /** İşi arka planda yapar, sonucu (ya da hatayı) durum yazısına yazar. */
    private void arkada(Is gorev) {
        mesgul = true;
        is.execute(() -> {
            String sonuc;
            try {
                sonuc = gorev.calistir();
            } catch (Exception e) {
                Log.e(ETIKET, "Öğret işlemi başarısız", e);
                sonuc = e.getMessage();
            }
            String s = sonuc;
            ana.post(() -> bitti(s));
        });
    }

    private void bitti(String sonuc) {
        mesgul = false;
        ilerleme.setVisibility(View.GONE);
        durum.setText(sonuc);
        etiketleriGoster();
        degisti.run();
    }

    private void klavyeyiKapat() {
        InputMethodManager imm = (InputMethodManager) etkinlik.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etiketKutusu.getWindowToken(), 0);
        etiketKutusu.clearFocus();
    }

    void kapatIsleri() {
        ana.removeCallbacksAndMessages(null);
        is.shutdown();
    }
}
