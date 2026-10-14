package app.tigo.dedektor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import java.util.function.BiConsumer;

/**
 * Fotoğraf: önizleme ile çizimleri birleştirip galeriye kaydeder; Android 9 ve öncesinde izin
 * ister. Kaydedilince adresi ve küçük resmi bildirir (son çekim düğmesi için).
 */
final class Fotograf {

    private final AppCompatActivity etkinlik;
    private final PreviewView onizleme;
    private final CizimView cizim;
    private final View flas;
    /** Kaydedildi: galerideki adresi ve küçük resim (arayüz iş parçacığında). */
    private final BiConsumer<Uri, Bitmap> kaydedildi;
    private final ActivityResultLauncher<String> depoIzni;
    private Bitmap kaydedilecek;

    /** Etkinliğin onCreate'inde kurulmalı (izin isteği o sırada kaydedilir). */
    Fotograf(AppCompatActivity etkinlik, PreviewView onizleme, CizimView cizim, View flas, BiConsumer<Uri, Bitmap> kaydedildi) {
        this.etkinlik = etkinlik;
        this.onizleme = onizleme;
        this.cizim = cizim;
        this.flas = flas;
        this.kaydedildi = kaydedildi;
        depoIzni = etkinlik.registerForActivityResult(new ActivityResultContracts.RequestPermission(), verildi -> {
            Bitmap foto = kaydedilecek;
            kaydedilecek = null;
            if (!verildi) toast("Galeriye kaydetme izni verilmedi.");
            else if (foto != null) arkadaKaydet(foto);
            else toast("İzin verildi, yeniden dene.");
        });
    }

    /** Android 10'dan önce galeriye yazmak için izin gerekir. */
    boolean depoIzniVar() {
        return Build.VERSION.SDK_INT >= 29
            || ContextCompat.checkSelfPermission(etkinlik, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    void depoIzniIste() {
        depoIzni.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    void cek() {
        Bitmap goruntu = onizleme.getBitmap();
        if (goruntu == null || cizim.getWidth() == 0) {
            toast("Fotoğraf çekilemedi.");
            return;
        }
        Bitmap foto = Bitmap.createBitmap(goruntu.getWidth(), goruntu.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(foto);
        c.drawBitmap(goruntu, 0, 0, null);
        c.scale(foto.getWidth() / (float) cizim.getWidth(), foto.getHeight() / (float) cizim.getHeight());
        cizim.draw(c);
        flas.setAlpha(0.8f);
        flas.animate().alpha(0f).setDuration(300).start();
        if (!depoIzniVar()) {
            kaydedilecek = foto;
            depoIzniIste();
            return;
        }
        arkadaKaydet(foto);
    }

    private void arkadaKaydet(Bitmap foto) {
        new Thread(() -> {
            try {
                Uri uri = Galeri.kaydet(etkinlik, foto);
                Bitmap kucuk = kucukResim(foto);
                etkinlik.runOnUiThread(() -> {
                    toast("Fotoğraf galeriye kaydedildi.");
                    kaydedildi.accept(uri, kucuk);
                });
            } catch (Exception e) {
                Log.e("Dedektor", "Fotoğraf kaydedilemedi", e);
                etkinlik.runOnUiThread(() -> toast("Fotoğraf kaydedilemedi."));
            }
        }).start();
    }

    /** Son çekim düğmesi için küçük kopya (kısa kenar 160 piksel). */
    static Bitmap kucukResim(Bitmap b) {
        float o = 160f / Math.min(b.getWidth(), b.getHeight());
        if (o >= 1f) return b;
        return Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * o), Math.round(b.getHeight() * o), true);
    }

    private void toast(String mesaj) {
        Toast.makeText(etkinlik, mesaj, Toast.LENGTH_LONG).show();
    }
}
