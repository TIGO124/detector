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
 * kamera önizlemesinden görünür, arkası bulanık ya da renk geçişli olur. Katman yarım
 * çözünürlükte hazırlanır (ekranda yumuşak büyütülür); bitmap'ler her karede yeniden ayrılmaz.
 * Ayırma modelinin sonuç iş parçacığında çalışır.
 */
final class ArkaPlan {

    private static final int TAMPON = 3;
    /** Maske ve katman bu oranda küçültülür: 4 kat az piksel, kenarlar da yumuşar. */
    private static final int KUCULTME = 2;

    private final Paint filtre = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint maskeKalemi = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Bitmap[] sonuclar = new Bitmap[TAMPON];
    private final Canvas[] tuvaller = new Canvas[TAMPON];
    private int sira;
    private int[] pikseller;
    private Bitmap maske;
    private Bitmap orta;
    private Bitmap kucuk;
    private Canvas ortaTuvali;
    private Canvas kucukTuvali;
    private Bitmap gradyan;
    private Rect tum;
    private Rect ortaAlan;
    private Rect kucukAlan;

    ArkaPlan() {
        maskeKalemi.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    /** Arka plan katmanı ya da maske yoksa null. */
    Bitmap katman(ImageSegmenterResult sonuc, MPImage girdi, Ayarlar.ArkaPlan tur) {
        List<MPImage> maskeler = sonuc.confidenceMasks().orElse(null);
        if (maskeler == null || maskeler.isEmpty()) return null;
        // Tek maske varsa kişi olasılığıdır; iki maske varsa ikincisi (arka plan, kişi).
        MPImage kisi = maskeler.get(maskeler.size() - 1);
        int mg = kisi.getWidth();
        int my = kisi.getHeight();
        int g = Math.max(1, mg / KUCULTME);
        int y = Math.max(1, my / KUCULTME);
        hazirla(g, y);

        FloatBuffer olasilik = ByteBufferExtractor.extract(kisi).order(ByteOrder.nativeOrder()).asFloatBuffer();
        int i = 0;
        for (int sy = 0; sy < y; sy++) {
            int satir = sy * KUCULTME * mg;
            for (int sx = 0; sx < g; sx++) {
                float p = Math.min(1f, Math.max(0f, olasilik.get(satir + sx * KUCULTME)));
                pikseller[i++] = ((int) ((1f - p) * 255f)) << 24;
            }
        }
        maske.setPixels(pikseller, 0, g, 0, 0, g, y);

        // Arayüz bir önceki katmanı çizerken üzerine yazılmasın diye sırayla üç tampon.
        sira = (sira + 1) % TAMPON;
        Bitmap hedef = sonuclar[sira];
        Canvas c = tuvaller[sira];
        hedef.eraseColor(0);
        if (tur == Ayarlar.ArkaPlan.BULANIK) {
            // İki adımda küçültüp yumuşak büyütmek ucuz ve titremeyen bir bulanıklık verir.
            ortaTuvali.drawBitmap(BitmapExtractor.extract(girdi), null, ortaAlan, filtre);
            kucukTuvali.drawBitmap(orta, null, kucukAlan, filtre);
            c.drawBitmap(kucuk, null, tum, filtre);
        } else {
            c.drawBitmap(gradyan, 0, 0, null);
        }
        c.drawBitmap(maske, 0, 0, maskeKalemi);
        return hedef;
    }

    private void hazirla(int g, int y) {
        if (pikseller != null && pikseller.length == g * y) return;
        pikseller = new int[g * y];
        maske = Bitmap.createBitmap(g, y, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < TAMPON; i++) {
            sonuclar[i] = Bitmap.createBitmap(g, y, Bitmap.Config.ARGB_8888);
            tuvaller[i] = new Canvas(sonuclar[i]);
        }
        orta = Bitmap.createBitmap(Math.max(1, g / 2), Math.max(1, y / 2), Bitmap.Config.ARGB_8888);
        kucuk = Bitmap.createBitmap(Math.max(1, g / 8), Math.max(1, y / 8), Bitmap.Config.ARGB_8888);
        ortaTuvali = new Canvas(orta);
        kucukTuvali = new Canvas(kucuk);
        tum = new Rect(0, 0, g, y);
        ortaAlan = new Rect(0, 0, orta.getWidth(), orta.getHeight());
        kucukAlan = new Rect(0, 0, kucuk.getWidth(), kucuk.getHeight());
        gradyan = Bitmap.createBitmap(g, y, Bitmap.Config.ARGB_8888);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, g, y, new int[] {0xFF0F2027, 0xFF203A43, 0xFF2C5364}, null, Shader.TileMode.CLAMP));
        new Canvas(gradyan).drawRect(0, 0, g, y, p);
    }
}
