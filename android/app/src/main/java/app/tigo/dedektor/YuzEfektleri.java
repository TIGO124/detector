package app.tigo.dedektor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

/**
 * Yüze oturan efektler: gözlük, bıyık, şapka, kedi, taç, palyaço ve yüzü pikselleştirme. Yüz
 * noktalarının ekran konumuna göre ölçeklenir ve başla birlikte döner. Yerel koordinatta x
 * sağa, y aşağıdır; döndürme göz çizgisine göre.
 */
final class YuzEfektleri {

    /** Yüz noktasının (0–477) ekran konumu. */
    interface Noktalar {
        float x(int i);

        float y(int i);
    }

    // MediaPipe yüz ağı nokta numaraları
    private static final int GOZ_1 = 468;
    private static final int GOZ_2 = 473;
    private static final int BURUN_UCU = 1;
    private static final int BURUN_ALTI = 2;
    private static final int UST_DUDAK = 0;
    private static final int AGIZ_SOL = 61;
    private static final int AGIZ_SAG = 291;
    private static final int ALIN = 10;
    private static final int YANAK_SOL = 234;
    private static final int YANAK_SAG = 454;
    private static final int ELMACIK_1 = 205;
    private static final int ELMACIK_2 = 425;
    /** Yüz çevresi, sırayla (pikselleştirmede kırpma yolu). */
    private static final int[] YUZ_CEVRESI = {
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
        152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109,
    };
    /** Pikselleştirilmiş yüzün genişliği (kare sayısı). */
    private static final int PIKSEL = 12;
    private static final int[] SAC_RENKLERI = {0xFFFF7043, 0xFF42A5F5, 0xFFAB47BC};

    private final Paint dolgu = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cizgi = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Pikseller büyütülürken yumuşatılmasın. */
    private final Paint keskin = new Paint();
    private final Path yol = new Path();
    private final RectF kutu = new RectF();

    YuzEfektleri() {
        // Android 9'dan beri resimler varsayılan olarak yumuşatılır: pikseller bulanıklaşmasın.
        keskin.setFilterBitmap(false);
        cizgi.setStyle(Paint.Style.STROKE);
        cizgi.setStrokeCap(Paint.Cap.ROUND);
        cizgi.setStrokeJoin(Paint.Join.ROUND);
    }

    void ciz(Canvas c, Noktalar n, Ayarlar.Efekt efekt, int renk) {
        float ax = n.x(GOZ_1), ay = n.y(GOZ_1), bx = n.x(GOZ_2), by = n.y(GOZ_2);
        // Gözler ekranda soldan sağa sıralansın: aynalı görüntüde de "yukarı" doğru kalır.
        if (bx < ax) {
            float t = ax; ax = bx; bx = t;
            t = ay; ay = by; by = t;
        }
        float d = (float) Math.hypot(bx - ax, by - ay);
        if (d < 4f) return;
        float aci = (float) Math.toDegrees(Math.atan2(by - ay, bx - ax));
        float f = (float) Math.hypot(n.x(YANAK_SAG) - n.x(YANAK_SOL), n.y(YANAK_SAG) - n.y(YANAK_SOL));
        boolean hepsi = efekt == Ayarlar.Efekt.HEPSI;
        if (hepsi || efekt == Ayarlar.Efekt.SAPKA) sapka(c, n, aci, f, renk);
        if (hepsi || efekt == Ayarlar.Efekt.BIYIK) biyik(c, n, aci);
        if (hepsi || efekt == Ayarlar.Efekt.GOZLUK) gozluk(c, (ax + bx) / 2, (ay + by) / 2, d, aci, renk);
        if (efekt == Ayarlar.Efekt.KEDI) kedi(c, n, aci, f);
        else if (efekt == Ayarlar.Efekt.TAC) tac(c, n, aci, f);
        else if (efekt == Ayarlar.Efekt.PALYACO) palyaco(c, n, aci, f);
    }

    /** Yerel koordinata geç: başlangıç noktası (x, y), göz çizgisi açısı. */
    private static void yerel(Canvas c, float x, float y, float aci) {
        c.save();
        c.rotate(aci, x, y);
        c.translate(x, y);
    }

    private void gozluk(Canvas c, float mx, float my, float d, float aci, int renk) {
        yerel(c, mx, my, aci);
        float w = 0.9f * d;
        float h = 0.62f * d;
        dolgu.setColor(0xD8101418);
        cizgi.setColor(renk);
        cizgi.setStrokeWidth(0.07f * d);
        for (int yon = -1; yon <= 1; yon += 2) {
            kutu.set(yon * d / 2 - w / 2, -h / 2, yon * d / 2 + w / 2, h / 2);
            c.drawRoundRect(kutu, 0.28f * d, 0.28f * d, dolgu);
            c.drawRoundRect(kutu, 0.28f * d, 0.28f * d, cizgi);
            // Sap: camın dış kenarından kulağa doğru
            c.drawLine(yon * (d / 2 + w / 2), -h / 4, yon * (d / 2 + w / 2 + 0.45f * d), -h / 3, cizgi);
        }
        // Köprü
        c.drawLine(-d / 2 + w / 2, -h / 6, d / 2 - w / 2, -h / 6, cizgi);
        c.restore();
    }

    private void biyik(Canvas c, Noktalar n, float aci) {
        float mx = (n.x(BURUN_ALTI) + n.x(UST_DUDAK)) / 2;
        float my = (n.y(BURUN_ALTI) + n.y(UST_DUDAK)) / 2;
        float w = 1.3f * (float) Math.hypot(n.x(AGIZ_SAG) - n.x(AGIZ_SOL), n.y(AGIZ_SAG) - n.y(AGIZ_SOL));
        float h = 0.3f * w;
        yerel(c, mx, my, aci);
        dolgu.setColor(0xFF2B1B10);
        for (int yon = -1; yon <= 1; yon += 2) {
            yol.reset();
            yol.moveTo(0, -0.15f * h);
            yol.cubicTo(yon * 0.15f * w, -0.55f * h, yon * 0.35f * w, -0.45f * h, yon * 0.47f * w, 0.05f * h);
            yol.cubicTo(yon * 0.52f * w, 0.3f * h, yon * 0.58f * w, 0.05f * h, yon * 0.53f * w, -0.3f * h);
            yol.cubicTo(yon * 0.6f * w, 0.55f * h, yon * 0.3f * w, 0.6f * h, yon * 0.1f * w, 0.35f * h);
            yol.cubicTo(yon * 0.05f * w, 0.3f * h, yon * 0.02f * w, 0.25f * h, 0, 0.3f * h);
            yol.close();
            c.drawPath(yol, dolgu);
        }
        c.restore();
    }

    private void sapka(Canvas c, Noktalar n, float aci, float f, int renk) {
        yerel(c, n.x(ALIN), n.y(ALIN), aci);
        c.translate(0, -0.08f * f);
        dolgu.setColor(0xFF111111);
        // Gövde, kenar (siperlik) ve renkli bant
        kutu.set(-0.42f * f, -0.9f * f, 0.42f * f, 0);
        c.drawRoundRect(kutu, 0.05f * f, 0.05f * f, dolgu);
        kutu.set(-0.75f * f, -0.07f * f, 0.75f * f, 0.05f * f);
        c.drawRoundRect(kutu, 0.06f * f, 0.06f * f, dolgu);
        dolgu.setColor(renk);
        c.drawRect(-0.42f * f, -0.3f * f, 0.42f * f, -0.18f * f, dolgu);
        c.restore();
    }

    /** Kedi: başın üstünde kulaklar, pembe burun, bıyıklar. */
    private void kedi(Canvas c, Noktalar n, float aci, float f) {
        yerel(c, n.x(ALIN), n.y(ALIN), aci);
        for (int yon = -1; yon <= 1; yon += 2) {
            dolgu.setColor(0xFF4E342E);
            ucgen(c, yon * 0.48f * f, 0.02f * f, yon * 0.36f * f, -0.55f * f, yon * 0.1f * f, -0.12f * f);
            dolgu.setColor(0xFFF48FB1);
            ucgen(c, yon * 0.4f * f, -0.04f * f, yon * 0.35f * f, -0.42f * f, yon * 0.18f * f, -0.14f * f);
        }
        c.restore();
        yerel(c, n.x(BURUN_UCU), n.y(BURUN_UCU), aci);
        dolgu.setColor(0xFFF06292);
        ucgen(c, -0.07f * f, -0.03f * f, 0.07f * f, -0.03f * f, 0, 0.05f * f);
        cizgi.setColor(0xE6212121);
        cizgi.setStrokeWidth(Math.max(1.5f, 0.012f * f));
        for (int yon = -1; yon <= 1; yon += 2) {
            for (int i = -1; i <= 1; i++) {
                c.drawLine(yon * 0.12f * f, 0.06f * f + i * 0.035f * f, yon * 0.5f * f, 0.02f * f + i * 0.09f * f, cizgi);
            }
        }
        c.restore();
    }

    /** Taç: alnın üstünde beş köşeli altın taç ve taşlar. */
    private void tac(Canvas c, Noktalar n, float aci, float f) {
        yerel(c, n.x(ALIN), n.y(ALIN), aci);
        c.translate(0, -0.05f * f);
        yol.reset();
        yol.moveTo(-0.36f * f, 0);
        yol.lineTo(-0.4f * f, -0.34f * f);
        yol.lineTo(-0.19f * f, -0.15f * f);
        yol.lineTo(0, -0.42f * f);
        yol.lineTo(0.19f * f, -0.15f * f);
        yol.lineTo(0.4f * f, -0.34f * f);
        yol.lineTo(0.36f * f, 0);
        yol.close();
        dolgu.setColor(0xFFFFC83D);
        c.drawPath(yol, dolgu);
        cizgi.setColor(0xFFB8860B);
        cizgi.setStrokeWidth(Math.max(1.5f, 0.015f * f));
        c.drawPath(yol, cizgi);
        dolgu.setColor(0xFFE0A800);
        c.drawRect(-0.36f * f, -0.08f * f, 0.36f * f, 0, dolgu);
        dolgu.setColor(0xFFE53935);
        c.drawCircle(0, -0.42f * f, 0.045f * f, dolgu);
        c.drawCircle(0, -0.04f * f, 0.035f * f, dolgu);
        dolgu.setColor(0xFF1E88E5);
        c.drawCircle(-0.4f * f, -0.34f * f, 0.04f * f, dolgu);
        c.drawCircle(0.4f * f, -0.34f * f, 0.04f * f, dolgu);
        c.restore();
    }

    /** Palyaço: kırmızı top burun, pembe yanaklar, renkli saç topları. */
    private void palyaco(Canvas c, Noktalar n, float aci, float f) {
        dolgu.setColor(0x66FF4081);
        c.drawCircle(n.x(ELMACIK_1), n.y(ELMACIK_1), 0.1f * f, dolgu);
        c.drawCircle(n.x(ELMACIK_2), n.y(ELMACIK_2), 0.1f * f, dolgu);
        yerel(c, n.x(ALIN), n.y(ALIN), aci);
        for (int yon = -1; yon <= 1; yon += 2) {
            for (int i = 0; i < SAC_RENKLERI.length; i++) {
                dolgu.setColor(SAC_RENKLERI[i]);
                c.drawCircle(yon * (0.42f + 0.1f * i) * f, (0.02f + 0.13f * i) * f, (0.15f - 0.015f * i) * f, dolgu);
            }
        }
        c.restore();
        float bx = n.x(BURUN_UCU);
        float by = n.y(BURUN_UCU);
        dolgu.setColor(0xFFE53935);
        c.drawCircle(bx, by, 0.11f * f, dolgu);
        dolgu.setColor(0x99FFFFFF);
        c.drawCircle(bx - 0.035f * f, by - 0.035f * f, 0.032f * f, dolgu);
    }

    private void ucgen(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        yol.reset();
        yol.moveTo(x1, y1);
        yol.lineTo(x2, y2);
        yol.lineTo(x3, y3);
        yol.close();
        c.drawPath(yol, dolgu);
    }

    /**
     * Pikselleştirilmiş yüz: küçük resim (pikselKaresi) yüz kutusuna büyütülür, yüz çevresine
     * kırpılır. hedef: kutunun ekrandaki yeri; aynalıysa resim de aynalanır.
     */
    void piksel(Canvas c, Noktalar n, Bitmap kucuk, RectF hedef, boolean aynali) {
        yol.reset();
        for (int i = 0; i < YUZ_CEVRESI.length; i++) {
            int k = YUZ_CEVRESI[i];
            if (i == 0) yol.moveTo(n.x(k), n.y(k));
            else yol.lineTo(n.x(k), n.y(k));
        }
        yol.close();
        c.save();
        c.clipPath(yol);
        if (aynali) c.scale(-1f, 1f, hedef.centerX(), hedef.centerY());
        c.drawBitmap(kucuk, null, hedef, keskin);
        c.restore();
    }

    /**
     * Kameradaki yüzün küçültülmüş kopyası (PIKSEL kare genişliğinde). kutu'ya yüzün normalize
     * kutusu {sol, üst, sağ, alt} yazılır. Yüz çok küçükse null. Kare yalnızca bu çağrıda okunur.
     */
    static Bitmap pikselKaresi(Bitmap kare, List<NormalizedLandmark> noktalar, float[] kutu) {
        float sol = 1, ust = 1, sag = 0, alt = 0;
        for (int k : YUZ_CEVRESI) {
            NormalizedLandmark p = noktalar.get(k);
            sol = Math.min(sol, p.x());
            sag = Math.max(sag, p.x());
            ust = Math.min(ust, p.y());
            alt = Math.max(alt, p.y());
        }
        int g = kare.getWidth();
        int y = kare.getHeight();
        Rect kaynak = new Rect(
            Math.max(0, Math.round(sol * g)), Math.max(0, Math.round(ust * y)),
            Math.min(g, Math.round(sag * g)), Math.min(y, Math.round(alt * y)));
        if (kaynak.width() < PIKSEL || kaynak.height() < PIKSEL) return null;
        int yukseklik = Math.max(1, Math.round(PIKSEL * kaynak.height() / (float) kaynak.width()));
        Bitmap kucuk = Bitmap.createBitmap(PIKSEL, yukseklik, Bitmap.Config.ARGB_8888);
        new Canvas(kucuk).drawBitmap(kare, kaynak, new Rect(0, 0, PIKSEL, yukseklik), new Paint(Paint.FILTER_BITMAP_FLAG));
        kutu[0] = kaynak.left / (float) g;
        kutu[1] = kaynak.top / (float) y;
        kutu[2] = kaynak.right / (float) g;
        kutu[3] = kaynak.bottom / (float) y;
        return kucuk;
    }
}
