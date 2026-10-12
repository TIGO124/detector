package app.tigo.dedektor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Yüze oturan efektler: gözlük, bıyık, şapka. Yüz noktalarının ekran konumuna göre ölçeklenir
 * ve başla birlikte döner. Yerel koordinatta x sağa, y aşağıdır; döndürme göz çizgisine göre.
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
    private static final int BURUN_ALTI = 2;
    private static final int UST_DUDAK = 0;
    private static final int AGIZ_SOL = 61;
    private static final int AGIZ_SAG = 291;
    private static final int ALIN = 10;
    private static final int YANAK_SOL = 234;
    private static final int YANAK_SAG = 454;

    private final Paint dolgu = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cizgi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path yol = new Path();
    private final RectF kutu = new RectF();

    YuzEfektleri() {
        cizgi.setStyle(Paint.Style.STROKE);
        cizgi.setStrokeCap(Paint.Cap.ROUND);
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
        boolean hepsi = efekt == Ayarlar.Efekt.HEPSI;
        if (hepsi || efekt == Ayarlar.Efekt.SAPKA) sapka(c, n, aci, renk);
        if (hepsi || efekt == Ayarlar.Efekt.BIYIK) biyik(c, n, aci);
        if (hepsi || efekt == Ayarlar.Efekt.GOZLUK) gozluk(c, (ax + bx) / 2, (ay + by) / 2, d, aci, renk);
    }

    private void gozluk(Canvas c, float mx, float my, float d, float aci, int renk) {
        c.save();
        c.rotate(aci, mx, my);
        c.translate(mx, my);
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
        c.save();
        c.rotate(aci, mx, my);
        c.translate(mx, my);
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

    private void sapka(Canvas c, Noktalar n, float aci, int renk) {
        float ax = n.x(ALIN);
        float ay = n.y(ALIN);
        float f = (float) Math.hypot(n.x(YANAK_SAG) - n.x(YANAK_SOL), n.y(YANAK_SAG) - n.y(YANAK_SOL));
        c.save();
        c.rotate(aci, ax, ay);
        c.translate(ax, ay - 0.08f * f);
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
}
