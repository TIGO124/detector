package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.mediapipe.tasks.components.containers.Category;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class IfadeAnaliziTest {

    private static List<Category> puanlar(Object... adVePuan) {
        List<Category> l = new ArrayList<>();
        for (int i = 0; i < adVePuan.length; i += 2) {
            l.add(Category.create((Float) adVePuan[i + 1], i / 2, (String) adVePuan[i], ""));
        }
        return l;
    }

    @Test
    public void notrYuzIfadesiz() {
        assertTrue(IfadeAnalizi.ifadeler(puanlar("mouthSmileLeft", 0.1f, "jawOpen", 0.05f)).isEmpty());
    }

    @Test
    public void gulumsemeVeAgiz() {
        List<String> s = IfadeAnalizi.ifadeler(puanlar("mouthSmileLeft", 0.8f, "mouthSmileRight", 0.7f, "jawOpen", 0.5f));
        assertEquals(Arrays.asList("Gülümsüyor", "Ağız açık"), s);
    }

    @Test
    public void gozler() {
        assertEquals(Arrays.asList("Gözler kapalı"), IfadeAnalizi.ifadeler(puanlar("eyeBlinkLeft", 0.9f, "eyeBlinkRight", 0.8f)));
        assertEquals(Arrays.asList("Göz kırpıyor"), IfadeAnalizi.ifadeler(puanlar("eyeBlinkLeft", 0.9f, "eyeBlinkRight", 0.1f)));
        // Kısık gözler kırpma sayılmaz.
        assertTrue(IfadeAnalizi.ifadeler(puanlar("eyeBlinkLeft", 0.55f, "eyeBlinkRight", 0.45f)).isEmpty());
    }

    @Test
    public void kaslar() {
        assertEquals(Arrays.asList("Kaşlar kalkık"), IfadeAnalizi.ifadeler(puanlar("browInnerUp", 0.7f)));
        assertEquals(Arrays.asList("Kaşlar çatık"), IfadeAnalizi.ifadeler(puanlar("browDownLeft", 0.6f, "browDownRight", 0.6f)));
    }
}
