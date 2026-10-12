package app.tigo.dedektor;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Fotoğrafı galeriye (Pictures/Dedektor) JPEG olarak kaydeder. */
final class Galeri {

    private Galeri() {}

    static void kaydet(Context baglam, Bitmap foto) throws Exception {
        ContentResolver cr = baglam.getContentResolver();
        ContentValues d = new ContentValues();
        String ad = "Dedektor_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        d.put(MediaStore.Images.Media.DISPLAY_NAME, ad);
        d.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            d.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Dedektor");
            d.put(MediaStore.Images.Media.IS_PENDING, 1);
        }
        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, d);
        if (uri == null) throw new IllegalStateException("Galeriye yazılamadı");
        try (OutputStream cikis = cr.openOutputStream(uri)) {
            if (cikis == null || !foto.compress(Bitmap.CompressFormat.JPEG, 92, cikis)) throw new IllegalStateException("JPEG yazılamadı");
        }
        if (Build.VERSION.SDK_INT >= 29) {
            d.clear();
            d.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, d, null, null);
        }
    }
}
