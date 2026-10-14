package app.tigo.dedektor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import java.util.Random;

/**
 * Kutlama: ortada büyük yazı ve ekrana yağan renkli kâğıtlar (hedef tamamlandı, yeni rekor).
 * Çizim katmanı olarak CizimView'e eklenir, süresi bitince kendini kaldırır.
 */
final class Kutlama implements CizimView.Katman {

    private static final long SURE_MS = 2600;
    private static final int ADET = 70;
    private static final int[] RENKLER = {0xFFFF5252, 0xFFFFC83D, 0xFF29B6F6, 0xFF00E676, 0xFFAB47BC, 0xFFFFFFFF};

    private final CizimView cizim;
    private final TextView yazi;
    private final Paint boya = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rastgele = new Random();
    private final float[] x = new float[ADET];
    private final float[] hiz = new float[ADET];
    private final float[] salinim = new float[ADET];
    private final int[] renk = new int[ADET];
    private long baslangic;
    private final Runnable bitir = this::bitir;

    Kutlama(CizimView cizim, TextView yazi) {
        this.cizim = cizim;
        this.yazi = yazi;
    }

    /** Arayüz iş parçacığından. konfeti: kâğıtlar da yağsın (yoksa yalnızca yazı). */
    void baslat(String mesaj, boolean konfeti) {
        for (int i = 0; i < ADET; i++) {
            x[i] = rastgele.nextFloat();
            hiz[i] = 0.25f + rastgele.nextFloat() * 0.45f;
            salinim[i] = rastgele.nextFloat() * 6.28f;
            renk[i] = RENKLER[rastgele.nextInt(RENKLER.length)];
        }
        baslangic = SystemClock.uptimeMillis();
        yazi.setText(mesaj);
        yazi.setVisibility(View.VISIBLE);
        yazi.setScaleX(0.6f);
        yazi.setScaleY(0.6f);
        yazi.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
        yazi.removeCallbacks(bitir);
        yazi.postDelayed(bitir, SURE_MS);
        if (konfeti) cizim.katmanEkle(this);
        else cizim.katmanCikar(this);
    }

    void bitir() {
        yazi.removeCallbacks(bitir);
        if (baslangic != 0) yazi.setVisibility(View.GONE);
        baslangic = 0;
        cizim.katmanCikar(this);
    }

    @Override
    public void ciz(Canvas tuval, CizimView v) {
        long gecen = SystemClock.uptimeMillis() - baslangic;
        if (baslangic == 0 || gecen > SURE_MS) return;
        float t = gecen / 1000f;
        float w = v.getWidth();
        float h = v.getHeight();
        float boyut = w * 0.018f;
        for (int i = 0; i < ADET; i++) {
            float py = (-0.1f + hiz[i] * t) * h;
            if (py > h) continue;
            float px = (x[i] + 0.03f * (float) Math.sin(salinim[i] + t * 4)) * w;
            boya.setColor(renk[i]);
            tuval.save();
            tuval.rotate((salinim[i] + t * 3) * 57f, px, py);
            tuval.drawRect(px - boyut, py - boyut / 2, px + boyut, py + boyut / 2, boya);
            tuval.restore();
        }
        v.postInvalidateOnAnimation();
    }
}
