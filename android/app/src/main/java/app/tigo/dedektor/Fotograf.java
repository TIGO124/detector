package app.tigo.dedektor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

/** Fotoğraf: önizleme ile çizimleri birleştirip galeriye kaydeder; Android 9 ve öncesinde izin ister. */
final class Fotograf {

    private final AppCompatActivity etkinlik;
    private final PreviewView onizleme;
    private final CizimView cizim;
    private final View flas;
    private final ActivityResultLauncher<String> depoIzni;
    private Bitmap kaydedilecek;

    /** Etkinliğin onCreate'inde kurulmalı (izin isteği o sırada kaydedilir). */
    Fotograf(AppCompatActivity etkinlik, PreviewView onizleme, CizimView cizim, View flas) {
        this.etkinlik = etkinlik;
        this.onizleme = onizleme;
        this.cizim = cizim;
        this.flas = flas;
        depoIzni = etkinlik.registerForActivityResult(new ActivityResultContracts.RequestPermission(), verildi -> {
            Bitmap foto = kaydedilecek;
            kaydedilecek = null;
            if (verildi && foto != null) arkadaKaydet(foto);
            else toast("Fotoğraf kaydetme izni verilmedi.");
        });
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
        if (Build.VERSION.SDK_INT < 29
            && ContextCompat.checkSelfPermission(etkinlik, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            kaydedilecek = foto;
            depoIzni.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        arkadaKaydet(foto);
    }

    private void arkadaKaydet(Bitmap foto) {
        new Thread(() -> {
            try {
                Galeri.kaydet(etkinlik, foto);
                etkinlik.runOnUiThread(() -> toast("Fotoğraf galeriye kaydedildi."));
            } catch (Exception e) {
                Log.e("Dedektor", "Fotoğraf kaydedilemedi", e);
                etkinlik.runOnUiThread(() -> toast("Fotoğraf kaydedilemedi."));
            }
        }).start();
    }

    private void toast(String mesaj) {
        Toast.makeText(etkinlik, mesaj, Toast.LENGTH_LONG).show();
    }
}
