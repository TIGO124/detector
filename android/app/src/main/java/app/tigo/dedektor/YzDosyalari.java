package app.tigo.dedektor;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;

/**
 * Yapay zekâ dosyaları (files/yz): öğretilen modeller (hareket.json, tid.json), örnekler (*.veri)
 * ve APK içindeki hazır modeller. Yedekleme de bu klasörü kullanır.
 */
final class YzDosyalari {

    private static final String ETIKET = "Dedektor";

    final Context baglam;
    final File klasor;

    YzDosyalari(Context baglam) {
        this.baglam = baglam.getApplicationContext();
        klasor = new File(baglam.getFilesDir(), "yz");
        if (!klasor.isDirectory() && !klasor.mkdirs()) Log.w(ETIKET, "YZ klasörü oluşturulamadı");
    }

    File modelDosyasi(Ayarlar.Yz tur) {
        return new File(klasor, (tur == Ayarlar.Yz.TID ? "tid" : "hareket") + ".json");
    }

    SinirAgi kayitliModel(Ayarlar.Yz tur) {
        File f = modelDosyasi(tur);
        if (!f.exists()) return null;
        try {
            return SinirAgi.json(dosyaOku(f));
        } catch (IOException | JSONException | RuntimeException e) {
            Log.e(ETIKET, "Kayıtlı model okunamadı: " + f.getName(), e);
            return null;
        }
    }

    /** Önce geçici dosyaya yazar, sonra yerine koyar: yarım kalan yazım eski modeli bozmaz. */
    void modeliKaydet(Ayarlar.Yz tur, SinirAgi m) throws IOException, JSONException {
        File hedef = modelDosyasi(tur);
        File gecici = new File(hedef.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(gecici), StandardCharsets.UTF_8)) {
            w.write(m.json());
        }
        if (hedef.exists() && !hedef.delete()) throw new IOException("Eski model silinemedi");
        if (!gecici.renameTo(hedef)) throw new IOException("Model kaydedilemedi");
    }

    void modeliSil(Ayarlar.Yz tur) {
        File f = modelDosyasi(tur);
        if (f.exists() && !f.delete()) Log.w(ETIKET, "Model dosyası silinemedi");
    }

    SinirAgi hazirModel(String yol) {
        try {
            return SinirAgi.json(KisiTanima.oku(baglam, yol));
        } catch (IOException | JSONException e) {
            Log.e(ETIKET, "Hazır model okunamadı: " + yol, e);
            return null;
        }
    }

    String kisiSurumu() {
        try {
            return Integer.toHexString(KisiTanima.oku(baglam, "yz/kimlik.json").hashCode());
        } catch (IOException e) {
            Log.w(ETIKET, "Kişi modeli bilgisi okunamadı", e);
            return "0";
        }
    }

    /**
     * İmzalar (yüz, nesne) yalnızca onları üreten modelle karşılaştırılabilir: kayıt dosyasının
     * adı modele bağlıdır. Model güncellenince eski kayıtlar silinir (yeniden eklenir).
     */
    File surumluDosya(String onEk, String surum) {
        String ad = onEk + "_" + surum + ".veri";
        File[] eskiler = klasor.listFiles((k, a) -> a.startsWith(onEk) && a.endsWith(".veri") && !a.equals(ad));
        if (eskiler != null) for (File f : eskiler) if (!f.delete()) Log.w(ETIKET, "Eski kayıt silinemedi: " + f.getName());
        return new File(klasor, ad);
    }

    /** java.nio.file Android 8'den önce yok. */
    static String dosyaOku(File f) throws IOException {
        try (InputStream g = new FileInputStream(f)) {
            return metin(g);
        }
    }

    /** Akışın tamamı, UTF-8 (InputStream.readAllBytes Android 13'ten önce yok). */
    static String metin(InputStream g) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] tampon = new byte[16384];
        for (int n; (n = g.read(tampon)) > 0; ) o.write(tampon, 0, n);
        return new String(o.toByteArray(), StandardCharsets.UTF_8);
    }
}
