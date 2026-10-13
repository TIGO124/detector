package app.tigo.dedektor;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

    private final AppCompatActivity etkinlik;
    private final Ayarlar ayarlar;
    private final YzTanima yz;
    /** Model ya da kayıtlar değişince (ipuçları güncellensin). */
    private final Runnable degisti;
    private final View panel;
    private final TextView baslik;
    private final TextView aciklama;
    private final EditText etiketKutusu;
    private final TextView kaydet;
    private final TextView durum;
    private final ProgressBar ilerleme;
    private final TextView listeBasligi;
    private final LinearLayout etiketler;
    private final LinearLayout dugmeler;
    /** Ekranın ortasındaki büyük yazı: geri sayım. */
    private final TextView buyukYazi;
    private final Handler ana = new Handler(Looper.getMainLooper());
    private final ExecutorService is = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String[]> modelSecici;
    private Ayarlar.Yz tur;
    private boolean mesgul;

    OgretPaneli(AppCompatActivity etkinlik, Ayarlar ayarlar, YzTanima yz, TextView buyukYazi, Runnable degisti) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.yz = yz;
        this.buyukYazi = buyukYazi;
        this.degisti = degisti;
        panel = etkinlik.findViewById(R.id.ogret_paneli);
        baslik = etkinlik.findViewById(R.id.ogret_baslik);
        aciklama = etkinlik.findViewById(R.id.ogret_aciklama);
        etiketKutusu = etkinlik.findViewById(R.id.ogret_etiket);
        kaydet = etkinlik.findViewById(R.id.ogret_kaydet);
        durum = etkinlik.findViewById(R.id.ogret_durum);
        ilerleme = etkinlik.findViewById(R.id.ogret_ilerleme);
        listeBasligi = etkinlik.findViewById(R.id.ogret_liste_basligi);
        etiketler = etkinlik.findViewById(R.id.ogret_etiketler);
        dugmeler = etkinlik.findViewById(R.id.ogret_dugmeler);
        kaydet.setOnClickListener(v -> kaydiBaslat());
        modelSecici = etkinlik.registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::modelYukle);
    }

    View panel() {
        return panel;
    }

    boolean acik() {
        return panel.getVisibility() == View.VISIBLE;
    }

    void kapat() {
        klavyeyiKapat();
        panel.setVisibility(View.GONE);
    }

    void ac(Ayarlar.Yz tur) {
        // Aynı bölümde ad kalır (bir hareket birkaç kez kaydedilir); başka bölümde boş başlar.
        if (tur != this.tur) etiketKutusu.setText(null);
        this.tur = tur;
        switch (tur) {
            case KISI:
                yazilar("Kişi ekle", "Adı yaz ve Kaydet'e bas. Geri sayımdan sonra 3 saniye kameraya bak, başını hafifçe sağa sola çevir.",
                    "Ad (ör. Ayşe)", "Kayıtlı kişiler (silmek için dokun)");
                break;
            case NESNE:
                yazilar("Nesne öğret", "Nesnenin adını yaz ve Kaydet'e bas. 3 saniye nesneyi ortadaki çerçevede farklı açılardan göster. "
                        + "Yanlış tanırsa çerçeve boşken \"" + NesneTanima.BOS + "\" adıyla da kaydet.",
                    "Ad (ör. Anahtarım)", "Öğretilen nesneler (silmek için dokun)");
                break;
            case TID:
                yazilar("Öğret · TİD harfleri", "Harfin adını yaz (ör. A) ve Kaydet'e bas. Harfi 3 saniye işaretle; hareketli harflerde hareketi tekrarla. Her harfi 2-3 kez kaydet, sonra Eğit.",
                    "Harf (ör. A)", "Öğrettiklerin (silmek için dokun)");
                break;
            default:
                yazilar("Öğret · El hareketleri", "Hareketin adını yaz (ör. Selam) ve Kaydet'e bas. Hareketi 3 saniye farklı açılardan göster. En az iki hareket kaydet, sonra Eğit.",
                    "Ad (ör. Selam)", "Öğrettiklerin (silmek için dokun)");
                break;
        }
        renkleriAyarla();
        durum.setText(null);
        ilerleme.setVisibility(View.GONE);
        dugmeleriGoster();
        etiketleriGoster();
        panel.setVisibility(View.VISIBLE);
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
        kaydet.setBackgroundTintList(ColorStateList.valueOf(ayarlar.renk));
    }

    private void dugmeleriGoster() {
        dugmeler.removeAllViews();
        if (!egitimsiz()) {
            dugmeler.addView(cip("Eğit", true, v -> egit()));
            dugmeler.addView(cip("Bilgisayara gönder", false, v -> disaAktar()));
            dugmeler.addView(cip("Model yükle", false, v -> {
                if (!mesgul) modelSecici.launch(new String[] {"application/json", "application/octet-stream", "text/plain"});
            }));
        }
        dugmeler.addView(cip("Kapat", false, v -> kapat()));
    }

    private void etiketleriGoster() {
        etiketler.removeAllViews();
        Map<String, Integer> sayilar = yz.veri(tur).sayilar();
        if (sayilar.isEmpty()) {
            etiketler.addView(cip("Henüz yok", false, null));
            return;
        }
        for (Map.Entry<String, Integer> e : sayilar.entrySet()) {
            String ad = e.getKey();
            etiketler.addView(cip(ad + " · " + e.getValue(), false, v -> silmeyiSor(ad)));
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
        for (int i = 0; i < GERI_SAYIM; i++) {
            String s = String.valueOf(GERI_SAYIM - i);
            ana.postDelayed(() -> buyukYaz(s), i * 1000L);
        }
        ana.postDelayed(() -> {
            buyukYaz("●");
            yz.kayitBaslat(tur, ad);
            kayitIzle();
        }, GERI_SAYIM * 1000L);
        durum.setText("Hazırlan…");
    }

    private void kayitIzle() {
        if (yz.kayitta(tur)) {
            durum.setText("Kaydediliyor… " + yz.kayitSayisi() + " örnek");
            ana.postDelayed(this::kayitIzle, 200);
            return;
        }
        buyukYazi.setVisibility(View.GONE);
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

    private void buyukYaz(String s) {
        buyukYazi.setText(s);
        buyukYazi.setVisibility(View.VISIBLE);
    }

    // ---------- Eğitim, dışa ve içe aktarma ----------

    private void egit() {
        if (mesgul) return;
        mesgul = true;
        ilerleme.setProgress(0);
        ilerleme.setVisibility(View.VISIBLE);
        durum.setText("Eğitiliyor…");
        Ayarlar.Yz t = tur;
        is.execute(() -> {
            String sonuc;
            try {
                SinirAgi.Sonuc s = yz.egit(t, oran -> ana.post(() -> ilerleme.setProgress(Math.round(oran * 100))));
                sonuc = "Eğitildi · doğruluk %" + Math.round(100 * s.dogruluk);
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

    private TextView cip(String metin, boolean secili, View.OnClickListener tik) {
        return Cip.olustur(etkinlik, metin, secili, ayarlar.renk, tik);
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
