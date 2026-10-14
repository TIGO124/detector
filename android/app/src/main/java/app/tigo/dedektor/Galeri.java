package app.tigo.dedektor;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Fotoğrafı (Pictures/Dedektor, JPEG) ve videoyu (Movies/Dedektor, MP4) galeriye kaydeder. */
final class Galeri {

    /** Galeriye yazılacak içerik. */
    private interface Yazici {
        void yaz(OutputStream cikis) throws Exception;
    }

    private Galeri() {}

    static Uri kaydet(Context baglam, Bitmap foto) throws Exception {
        return ekle(baglam, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_PICTURES, "jpg", "image/jpeg", cikis -> {
            if (!foto.compress(Bitmap.CompressFormat.JPEG, 92, cikis)) throw new IllegalStateException("JPEG yazılamadı");
        });
    }

    static Uri videoKaydet(Context baglam, File mp4) throws Exception {
        return ekle(baglam, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MOVIES, "mp4", "video/mp4", cikis -> {
            try (InputStream giris = new FileInputStream(mp4)) {
                byte[] tampon = new byte[64 * 1024];
                for (int n; (n = giris.read(tampon)) > 0; ) cikis.write(tampon, 0, n);
            }
        });
    }

    private static Uri ekle(Context baglam, Uri tablo, String klasor, String uzanti, String tur, Yazici yazici) throws Exception {
        ContentResolver cr = baglam.getContentResolver();
        ContentValues d = new ContentValues();
        String ad = "Dedektor_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "." + uzanti;
        d.put(MediaStore.MediaColumns.DISPLAY_NAME, ad);
        d.put(MediaStore.MediaColumns.MIME_TYPE, tur);
        if (Build.VERSION.SDK_INT >= 29) {
            d.put(MediaStore.MediaColumns.RELATIVE_PATH, klasor + "/Dedektor");
            d.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }
        Uri uri = cr.insert(tablo, d);
        if (uri == null) throw new IllegalStateException("Galeriye yazılamadı");
        try (OutputStream cikis = cr.openOutputStream(uri)) {
            if (cikis == null) throw new IllegalStateException("Galeriye yazılamadı");
            yazici.yaz(cikis);
        } catch (Exception e) {
            // Yarım kalan kayıt galeride boş dosya olarak kalmasın.
            cr.delete(uri, null, null);
            throw e;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            d.clear();
            d.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, d, null, null);
        }
        return uri;
    }
}
