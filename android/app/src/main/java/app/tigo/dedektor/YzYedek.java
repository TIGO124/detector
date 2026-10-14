package app.tigo.dedektor;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Yapay zekâ verilerinin yedeği: öğretilen hareketler, harfler, kişiler, nesneler ve modeller
 * (files/yz içindeki .veri ve .json dosyaları) tek zip dosyasında paylaşılır; geri yüklerken
 * dosya adları ve boyut denetlenir, sorun varsa hiçbir şey değişmez.
 */
final class YzYedek {

    static final int EN_COK_DOSYA = 50;
    static final long EN_COK_BAYT = 64L << 20;
    private static final Pattern AD = Pattern.compile("[A-Za-z0-9_\\-]+\\.(veri|json)");
    private static final String ETIKET = "Dedektor";
    private static final String GECERSIZ = "Bu dosya bir Dedektör yedeği değil.";

    private final AppCompatActivity etkinlik;
    private final File klasor;
    /** Geri yükleme bitti (veriler yeniden okunsun). */
    private final Runnable yuklendi;
    private final ActivityResultLauncher<String[]> secici;

    /** Etkinliğin onCreate'inde kurulmalı (dosya seçici o sırada kaydedilir). */
    YzYedek(AppCompatActivity etkinlik, File klasor, Runnable yuklendi) {
        this.etkinlik = etkinlik;
        this.klasor = klasor;
        this.yuklendi = yuklendi;
        secici = etkinlik.registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) sor(uri);
        });
    }

    static boolean gecerliAd(String ad) {
        return ad != null && AD.matcher(ad).matches();
    }

    /** Klasördeki yapay zekâ dosyalarını zip olarak yazar; dosya sayısı. */
    static int yedekle(File klasor, OutputStream cikis) throws IOException {
        int n = 0;
        File[] dosyalar = klasor.listFiles();
        try (ZipOutputStream z = new ZipOutputStream(cikis)) {
            if (dosyalar != null) {
                for (File f : dosyalar) {
                    if (!f.isFile() || !gecerliAd(f.getName())) continue;
                    z.putNextEntry(new ZipEntry(f.getName()));
                    try (InputStream g = new FileInputStream(f)) {
                        kopyala(g, z, Long.MAX_VALUE);
                    }
                    z.closeEntry();
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Zip'i doğrular ve klasöre açar; açılan dosya sayısı. Önce yandaki geçici klasöre açılır:
     * yedek bozuksa ya da sınırları aşıyorsa klasöre dokunulmaz. Aynı adlı dosyalar değişir.
     */
    static int geriYukle(InputStream giris, File klasor) throws IOException {
        File gecici = new File(klasor.getParentFile(), klasor.getName() + "_geri");
        sil(gecici);
        if (!gecici.mkdirs()) throw new IOException("Geçici klasör oluşturulamadı.");
        int n = 0;
        try {
            long toplam = 0;
            try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(giris))) {
                for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                    if (e.isDirectory() || !gecerliAd(e.getName())) throw new IOException(GECERSIZ);
                    if (++n > EN_COK_DOSYA) throw new IOException("Yedekte çok fazla dosya var.");
                    try (OutputStream o = new FileOutputStream(new File(gecici, e.getName()))) {
                        toplam += kopyala(z, o, EN_COK_BAYT - toplam);
                    }
                }
            } catch (ZipException e) {
                throw new IOException(GECERSIZ, e);
            }
            if (n == 0) throw new IOException(GECERSIZ);
            if (!klasor.isDirectory() && !klasor.mkdirs()) throw new IOException("Klasör oluşturulamadı.");
            File[] yeni = gecici.listFiles();
            if (yeni != null) {
                for (File f : yeni) {
                    File hedef = new File(klasor, f.getName());
                    if (hedef.exists() && !hedef.delete()) throw new IOException("Eski dosya silinemedi: " + f.getName());
                    if (!f.renameTo(hedef)) throw new IOException("Dosya taşınamadı: " + f.getName());
                }
            }
        } finally {
            sil(gecici);
        }
        return n;
    }

    /** En çok sinir bayt kopyalar; fazlası varsa hata. */
    private static long kopyala(InputStream g, OutputStream c, long sinir) throws IOException {
        byte[] tampon = new byte[16384];
        long n = 0;
        for (int k; (k = g.read(tampon)) > 0; ) {
            n += k;
            if (n > sinir) throw new IOException("Yedek çok büyük.");
            c.write(tampon, 0, k);
        }
        return n;
    }

    private static void sil(File f) {
        File[] icerik = f.listFiles();
        if (icerik != null) for (File i : icerik) sil(i);
        if (f.exists() && !f.delete()) Log.w(ETIKET, "Silinemedi: " + f.getName());
    }

    // ---------- Arayüz ----------

    /** Yedeği önbelleğe yazar ve paylaşım penceresini açar. */
    void paylas() {
        new Thread(() -> {
            try {
                File kap = new File(etkinlik.getCacheDir(), "yedek");
                sil(kap);
                if (!kap.mkdirs()) throw new IOException("Yedek klasörü oluşturulamadı.");
                String ad = "Dedektor_yedek_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".zip";
                File zip = new File(kap, ad);
                int n;
                try (OutputStream o = new FileOutputStream(zip)) {
                    n = yedekle(klasor, o);
                }
                if (n == 0) {
                    etkinlik.runOnUiThread(() -> toast("Yedeklenecek bir şey yok: henüz bir şey öğretilmedi."));
                    return;
                }
                Uri uri = FileProvider.getUriForFile(etkinlik, etkinlik.getPackageName() + ".fileprovider", zip);
                Intent gonder = new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                etkinlik.runOnUiThread(() -> etkinlik.startActivity(Intent.createChooser(gonder, "Yedeği kaydet ya da gönder")));
            } catch (IOException | RuntimeException e) {
                Log.e(ETIKET, "Yedek alınamadı", e);
                etkinlik.runOnUiThread(() -> toast("Yedek alınamadı."));
            }
        }).start();
    }

    void sec() {
        secici.launch(new String[] {"application/zip", "application/x-zip-compressed", "application/octet-stream"});
    }

    private void sor(Uri uri) {
        new AlertDialog.Builder(etkinlik)
            .setTitle("Yedek geri yüklensin mi?")
            .setMessage("Yedekteki hareketler, harfler, kişiler ve nesneler aynı adlı verilerin yerine geçer.")
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Geri yükle", (d, w) -> yukle(uri))
            .show();
    }

    private void yukle(Uri uri) {
        new Thread(() -> {
            String mesaj;
            boolean tamam = false;
            try (InputStream g = etkinlik.getContentResolver().openInputStream(uri)) {
                if (g == null) throw new IOException("Dosya açılamadı.");
                int n = geriYukle(g, klasor);
                mesaj = "Yedek geri yüklendi (" + n + " dosya).";
                tamam = true;
            } catch (IOException | RuntimeException e) {
                Log.e(ETIKET, "Yedek geri yüklenemedi", e);
                String m = e.getMessage();
                mesaj = m != null && !m.isEmpty() && e instanceof IOException ? m : "Yedek geri yüklenemedi.";
            }
            String sonuc = mesaj;
            boolean yenile = tamam;
            etkinlik.runOnUiThread(() -> {
                toast(sonuc);
                if (yenile) yuklendi.run();
            });
        }).start();
    }

    private void toast(String mesaj) {
        Toast.makeText(etkinlik, mesaj, Toast.LENGTH_LONG).show();
    }
}
