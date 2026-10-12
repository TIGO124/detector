package app.tigo.dedektor;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.activity.result.ActivityResult;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uygulama içi güncelleme: yeni APK uygulamanın önbelleğine indirilir, paket adı ve sürüm kodu
 * doğrulanır, sonra Android'in kurulum ekranı açılır. APK aynı anahtarla imzalandığı için Android
 * onu güncelleme olarak kurar.
 */
@CapacitorPlugin(name = "ApkGuncelleme")
public class ApkGuncellemePlugin extends Plugin {

    /** Yalnızca bu depodaki sürüm etiketlerinden indirilir (JS tarafı da aynı adresi kurar). */
    private static final String IZINLI_ONEK = "https://raw.githubusercontent.com/TIGO124/detector/";
    private static final String KLASOR = "guncelleme";
    private static final String DOSYA = "Dedektor.apk";

    private final ExecutorService arkaPlan = Executors.newSingleThreadExecutor();

    /** Kullanıcıya olduğu gibi gösterilen hata. */
    private static class GuncellemeHatasi extends Exception {
        GuncellemeHatasi(String mesaj) {
            super(mesaj);
        }
    }

    @Override
    public void load() {
        // Önceki güncellemeden kalan dosyalar: kurulduysa artık gereksiz.
        File[] eskiler = new File(getContext().getCacheDir(), KLASOR).listFiles();
        if (eskiler != null) for (File f : eskiler) f.delete();
    }

    @PluginMethod
    public void indirVeKur(PluginCall call) {
        String adres = call.getString("adres", "");
        Integer surumKodu = call.getInt("surumKodu");
        if (adres == null || !adres.startsWith(IZINLI_ONEK) || !adres.endsWith(".apk") || surumKodu == null) {
            call.reject("Geçersiz güncelleme bilgisi.");
            return;
        }
        arkaPlan.execute(() -> {
            try {
                hazirla(adres, surumKodu);
                getActivity().runOnUiThread(() -> izinVeKur(call));
            } catch (GuncellemeHatasi e) {
                call.reject(e.getMessage());
            } catch (Exception e) {
                call.reject("İndirme başarısız. İnternet bağlantını kontrol edip yeniden dene.", e);
            }
        });
    }

    /** Aynı sürüm daha önce indirildiyse onu kullanır, yoksa indirir; paket adını ve sürüm kodunu doğrular. */
    private void hazirla(String adres, int surumKodu) throws Exception {
        File apk = apkDosyasi();
        if (!(apk.exists() && surumUygun(apk, surumKodu))) {
            indir(adres, apk);
            if (!surumUygun(apk, surumKodu)) {
                apk.delete();
                throw new GuncellemeHatasi("İndirilen dosya beklenen sürüm değil.");
            }
        }
        long kurulu = surumKodu(getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0));
        if (surumKodu <= kurulu) throw new GuncellemeHatasi("Bu sürüm zaten yüklü.");
    }

    private boolean surumUygun(File apk, int beklenen) {
        PackageInfo bilgi = getContext().getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
        return bilgi != null && getContext().getPackageName().equals(bilgi.packageName) && surumKodu(bilgi) == beklenen;
    }

    @SuppressWarnings("deprecation")
    private static long surumKodu(PackageInfo p) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? p.getLongVersionCode() : p.versionCode;
    }

    private void indir(String adres, File hedef) throws Exception {
        File gecici = new File(hedef.getPath() + ".part");
        HttpURLConnection baglanti = (HttpURLConnection) new URL(adres).openConnection();
        baglanti.setConnectTimeout(15_000);
        baglanti.setReadTimeout(30_000);
        try {
            int kod = baglanti.getResponseCode();
            if (kod != HttpURLConnection.HTTP_OK) throw new GuncellemeHatasi("Güncelleme dosyası alınamadı (sunucu " + kod + " döndürdü).");
            long toplam = baglanti.getContentLengthLong();
            try (InputStream giris = new BufferedInputStream(baglanti.getInputStream()); OutputStream cikis = new FileOutputStream(gecici)) {
                byte[] tampon = new byte[64 * 1024];
                long okunan = 0;
                int sonYuzde = -2;
                for (int n; (n = giris.read(tampon)) != -1; ) {
                    cikis.write(tampon, 0, n);
                    okunan += n;
                    int yuzde = toplam > 0 ? (int) (okunan * 100 / toplam) : -1;
                    if (yuzde != sonYuzde) {
                        sonYuzde = yuzde;
                        JSObject veri = new JSObject();
                        veri.put("yuzde", yuzde);
                        notifyListeners("ilerleme", veri);
                    }
                }
            }
        } finally {
            baglanti.disconnect();
        }
        if (hedef.exists() && !hedef.delete()) throw new IOException("Eski dosya silinemedi");
        if (!gecici.renameTo(hedef)) throw new IOException("Dosya kaydedilemedi");
    }

    /** Android 8+ uygulama başına "bilinmeyen uygulamaları yükle" izni ister; yoksa ayar ekranı açılır. */
    private void izinVeKur(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getContext().getPackageManager().canRequestPackageInstalls()) {
            Intent ayar = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
            startActivityForResult(call, ayar, "izinDondu");
            return;
        }
        kur(call);
    }

    @ActivityCallback
    private void izinDondu(PluginCall call, ActivityResult sonuc) {
        if (call == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getContext().getPackageManager().canRequestPackageInstalls()) {
            JSObject r = new JSObject();
            r.put("durum", "izin_verilmedi");
            call.resolve(r);
            return;
        }
        kur(call);
    }

    private void kur(PluginCall call) {
        Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", apkDosyasi());
        Intent kurulum = new Intent(Intent.ACTION_VIEW);
        kurulum.setDataAndType(uri, "application/vnd.android.package-archive");
        kurulum.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            getActivity().startActivity(kurulum);
        } catch (ActivityNotFoundException e) {
            call.reject("Android kurulum ekranı açılamadı.");
            return;
        }
        JSObject r = new JSObject();
        r.put("durum", "kuruluyor");
        call.resolve(r);
    }

    private File apkDosyasi() {
        File klasor = new File(getContext().getCacheDir(), KLASOR);
        klasor.mkdirs();
        return new File(klasor, DOSYA);
    }
}
