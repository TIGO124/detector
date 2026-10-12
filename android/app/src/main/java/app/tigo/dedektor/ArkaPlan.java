package app.tigo.dedektor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import com.google.mediapipe.framework.image.BitmapExtractor;
import com.google.mediapipe.framework.image.ByteBufferExtractor;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * Kişiyi arka plandan ayırır. Sonuç, arka planı opak, kişiyi saydam bir katmandır: kişi canlı
 * kamera önizlemesinden görünür, arkası bulanık ya da renk geçişli olur. Ayırma modelinin
 * sonuç iş parçacığında çalışır; bitmap'ler her karede yeniden ayrılmaz.
 */
final class ArkaPlan {

    private static final int TAMPON = 3;
    private final Paint filtre = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint maskeKalemi = new Paint();
    private final Bitmap[] sonuclar = new Bitmap[TAMPON];
    private int sira;
    private int[] pikseller;
    private Bitmap maske;
    private Bitmap kucuk;
    private Bitmap gradyan;

    ArkaPlan() {
        maskeKalemi.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    /** Arka plan katmanı (maske boyutunda) ya da maske yoksa null. */
    Bitmap katman(ImageSegmenterResult sonuc, MPImage girdi, Ayarlar.ArkaPlan tur) {
        List<MPImage> maskeler = sonuc.confidenceMasks().orElse(null);
        if (maskeler == null || maskeler.isEmpty()) return null;
        // Tek maske varsa kişi olasılığıdır; iki maske varsa ikincisi (arka plan, kişi).
        MPImage kisi = maskeler.get(maskeler.size() - 1);
        int g = kisi.getWidth();
        int y = kisi.getHeight();
        FloatBuffer olasilik = ByteBufferExtractor.extract(kisi).order(ByteOrder.nativeOrder()).asFloatBuffer();

        if (pikseller == null || pikseller.length != g * y) {
            pikseller = new int[g * y];
            maske = Bitmap.createBitmap(g, y, Bitmap.Config.ARGB_8888);
            for (int i = 0; i < TAMPON; i++) sonuclar[i] = Bitmap.createBitmap(g, y, Bitmap.Config.ARGB_8888);
            kucuk = Bitmap.createBitmap(Math.max(1, g / 16), Math.max(1, y / 16), Bitmap.Config.ARGB_8888);
            gradyan = null;
        }
        for (int i = 0; i < g * y; i++) {
            float p = Math.min(1f, Math.max(0f, olasilik.get(i)));
            pikseller[i] = ((int) ((1f - p) * 255f)) << 24;
        }
        maske.setPixels(pikseller, 0, g, 0, 0, g, y);

        // Arayüz bir önceki katmanı çizerken üzerine yazılmasın diye sırayla üç tampon.
        Bitmap hedefBmp = sonuclar[sira = (sira + 1) % TAMPON];
        hedefBmp.eraseColor(0);
        Canvas c = new Canvas(hedefBmp);
        Rect tum = new Rect(0, 0, g, y);
        if (tur == Ayarlar.ArkaPlan.BULANIK) {
            // Küçültüp yumuşak büyütmek ucuz ve güçlü bir bulanıklık verir.
            new Canvas(kucuk).drawBitmap(BitmapExtractor.extract(girdi), null, new Rect(0, 0, kucuk.getWidth(), kucuk.getHeight()), filtre);
            c.drawBitmap(kucuk, null, tum, filtre);
        } else {
            c.drawBitmap(gradyan(g, y), 0, 0, null);
        }
        c.drawBitmap(maske, 0, 0, maskeKalemi);
        return hedefBmp;
    }

    private Bitmap gradyan(int g, int y) {
        if (gradyan == null) {
            gradyan = Bitmap.createBitmap(g, y, Bitmap.Config.ARGB_8888);
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, 0, g, y, new int[] {0xFF0F2027, 0xFF203A43, 0xFF2C5364}, null, Shader.TileMode.CLAMP));
            new Canvas(gradyan).drawRect(0, 0, g, y, p);
        }
        return gradyan;
    }
}
