package app.tigo.dedektor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class NesneTanimaTest {

    /** Algılayıcı modelinin (efficientdet_lite0.tflite) etiket listesi, "???" boşlukları hariç. */
    private static final String[] COCO = {
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
        "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard",
        "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase",
        "scissors", "teddy bear", "hair drier", "toothbrush",
    };

    @Test
    public void butunNesnelerinTurkceAdiVar() {
        assertEquals(80, COCO.length);
        for (String ad : COCO) assertNotEquals(ad, NesneTanima.ad(ad));
        assertEquals(80, NesneTanima.adSayisi());
        assertEquals("Bardak", NesneTanima.ad("cup"));
        assertEquals("Telefon", NesneTanima.ad("cell phone"));
        // Bilinmeyen ad olduğu gibi kalır.
        assertEquals("ufo", NesneTanima.ad("ufo"));
    }

    /** Eli (21 nokta) bir kutunun içine ya da dışına yerleştirir. */
    private static List<NormalizedLandmark> el(float cx, float cy, float yaricap) {
        List<NormalizedLandmark> n = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            double a = 2 * Math.PI * i / 21;
            n.add(NormalizedLandmark.create((float) (cx + yaricap * Math.cos(a)), (float) (cy + yaricap * Math.sin(a)), 0f));
        }
        return n;
    }

    @Test
    public void eldeKurali() {
        float[] kutu = {0.4f, 0.4f, 0.6f, 0.6f};
        // Parmaklar nesneyi sarar: noktaların çoğu kutuda.
        assertTrue(NesneTanima.elde(kutu, el(0.5f, 0.5f, 0.08f)));
        // El kutunun kenarında: yalnızca birkaç nokta içeride (en az 4 gerekir).
        assertFalse(NesneTanima.elde(kutu, el(0.75f, 0.5f, 0.1f)));
        // El çok uzakta.
        assertFalse(NesneTanima.elde(kutu, el(0.1f, 0.1f, 0.05f)));
    }

    @Test
    public void anaNesneOnceEldeki() {
        float[] cerceve = {0.3f, 0.3f, 0.7f, 0.7f};
        NesneTanima.Nesne ortada = new NesneTanima.Nesne("Kitap", 0.9f, new float[] {0.4f, 0.4f, 0.6f, 0.6f}, false);
        NesneTanima.Nesne elde = new NesneTanima.Nesne("Bardak", 0.5f, new float[] {0.0f, 0.0f, 0.2f, 0.2f}, true);
        NesneTanima.Nesne kenarda = new NesneTanima.Nesne("Saat", 0.95f, new float[] {0.8f, 0.0f, 1.0f, 0.2f}, false);
        assertEquals("Bardak", NesneTanima.anaNesne(Arrays.asList(ortada, elde, kenarda), cerceve).ad);
        assertEquals("Kitap", NesneTanima.anaNesne(Arrays.asList(ortada, kenarda), cerceve).ad);
        assertNull(NesneTanima.anaNesne(Collections.singletonList(kenarda), cerceve));
        assertNull(NesneTanima.anaNesne(Collections.emptyList(), cerceve));
    }

    @Test
    public void cerceveOrtadaKare() {
        // 480x640 dikey kare: kısa kenarın yarısı = 240 piksel.
        float[] c = NesneTanima.cerceve(480, 640);
        assertEquals(240f, (c[2] - c[0]) * 480, 1e-3f);
        assertEquals(240f, (c[3] - c[1]) * 640, 1e-3f);
        assertArrayEquals(new float[] {0.5f, 0.5f}, new float[] {(c[0] + c[2]) / 2, (c[1] + c[3]) / 2}, 1e-6f);
    }
}
