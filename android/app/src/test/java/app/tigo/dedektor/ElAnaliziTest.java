package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ElAnaliziTest {

    /** Dik duran, avucu kameraya bakan açık el (görüntü koordinatı: y aşağı). */
    private static float[][] acikEl() {
        return new float[][] {
            {0.50f, 0.90f},
            {0.42f, 0.85f}, {0.36f, 0.80f}, {0.32f, 0.75f}, {0.28f, 0.70f},
            {0.44f, 0.65f}, {0.43f, 0.55f}, {0.43f, 0.48f}, {0.43f, 0.42f},
            {0.50f, 0.64f}, {0.50f, 0.53f}, {0.50f, 0.46f}, {0.50f, 0.40f},
            {0.56f, 0.65f}, {0.57f, 0.55f}, {0.57f, 0.49f}, {0.57f, 0.44f},
            {0.62f, 0.68f}, {0.63f, 0.60f}, {0.63f, 0.55f}, {0.63f, 0.51f},
        };
    }

    /** Parmağı avuca kıvırır: uç, kök ekleme yakın düşer. */
    private static void kivir(float[][] el, int kok, int orta, int uc) {
        el[orta] = new float[] {el[kok][0], el[kok][1] - 0.06f};
        el[orta + 1] = new float[] {el[kok][0] + 0.01f, el[kok][1] - 0.03f};
        el[uc] = new float[] {el[kok][0] + 0.01f, el[kok][1] + 0.03f};
    }

    private static void basparmagiKivir(float[][] el) {
        el[3] = new float[] {0.40f, 0.76f};
        el[4] = new float[] {0.52f, 0.72f};
    }

    private static List<NormalizedLandmark> noktalar(float[][] el) {
        List<NormalizedLandmark> n = new ArrayList<>();
        for (float[] p : el) n.add(NormalizedLandmark.create(p[0], p[1], 0f));
        return n;
    }

    /** Eli bilek çevresinde 90 derece döndürür. */
    private static float[][] dondur(float[][] el) {
        float[][] d = new float[el.length][];
        for (int i = 0; i < el.length; i++) {
            float dx = el[i][0] - el[0][0];
            float dy = el[i][1] - el[0][1];
            d[i] = new float[] {el[0][0] - dy, el[0][1] + dx};
        }
        return d;
    }

    @Test
    public void acikElBesParmak() {
        assertEquals(5, ElAnalizi.parmakSayisi(noktalar(acikEl())));
        assertFalse(ElAnalizi.isaretEdiyor(noktalar(acikEl())));
    }

    @Test
    public void yumrukSifirParmak() {
        float[][] el = acikEl();
        basparmagiKivir(el);
        kivir(el, 5, 6, 8);
        kivir(el, 9, 10, 12);
        kivir(el, 13, 14, 16);
        kivir(el, 17, 18, 20);
        assertEquals(0, ElAnalizi.parmakSayisi(noktalar(el)));
    }

    @Test
    public void zaferVeIsaret() {
        float[][] el = acikEl();
        basparmagiKivir(el);
        kivir(el, 13, 14, 16);
        kivir(el, 17, 18, 20);
        assertEquals(2, ElAnalizi.parmakSayisi(noktalar(el)));
        assertFalse(ElAnalizi.isaretEdiyor(noktalar(el)));

        kivir(el, 9, 10, 12);
        assertEquals(1, ElAnalizi.parmakSayisi(noktalar(el)));
        assertTrue(ElAnalizi.isaretEdiyor(noktalar(el)));
    }

    @Test
    public void yonDegisinceSayiDegismez() {
        float[][] el = acikEl();
        kivir(el, 17, 18, 20);
        assertEquals(4, ElAnalizi.parmakSayisi(noktalar(el)));
        assertEquals(4, ElAnalizi.parmakSayisi(noktalar(dondur(el))));
    }

    @Test
    public void adlar() {
        assertEquals("Zafer", ElAnalizi.hareketAdi("Victory"));
        assertEquals("Başparmak yukarı", ElAnalizi.hareketAdi("Thumb_Up"));
        assertNull(ElAnalizi.hareketAdi("None"));
        assertEquals("Sağ el", ElAnalizi.elEtiketi("Right"));
        assertEquals("Sol el", ElAnalizi.elEtiketi("Left"));
    }
}
