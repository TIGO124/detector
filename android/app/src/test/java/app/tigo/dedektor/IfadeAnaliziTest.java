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

    /** Aynı ölçümü birkaç kare verir, son sonucu döndürür. */
    private static List<String> tekrarla(IfadeAnalizi a, int kare, List<Category> olcum) {
        List<String> sonuc = null;
        for (int i = 0; i < kare; i++) sonuc = a.guncelle(olcum);
        return sonuc;
    }

    @Test
    public void notrYuzIfadesiz() {
        assertTrue(tekrarla(new IfadeAnalizi(), 5, puanlar("mouthSmileLeft", 0.1f, "jawOpen", 0.05f)).isEmpty());
    }

    @Test
    public void tekKarelikSicramaSayilmaz() {
        IfadeAnalizi a = new IfadeAnalizi();
        assertTrue(a.guncelle(puanlar("mouthSmileLeft", 0.9f, "mouthSmileRight", 0.9f)).isEmpty());
        assertTrue(a.guncelle(puanlar("mouthSmileLeft", 0.1f, "mouthSmileRight", 0.1f)).isEmpty());
    }

    @Test
    public void gulumsemeVeAgiz() {
        List<String> s = tekrarla(new IfadeAnalizi(), 5, puanlar("mouthSmileLeft", 0.8f, "mouthSmileRight", 0.7f, "jawOpen", 0.6f));
        assertEquals(Arrays.asList("Gülümsüyor", "Ağız açık"), s);
    }

    @Test
    public void sinirdakiIfadeYanipSonmez() {
        IfadeAnalizi a = new IfadeAnalizi();
        tekrarla(a, 5, puanlar("mouthSmileLeft", 0.8f, "mouthSmileRight", 0.8f));
        // Açılma eşiğinin (0.5) altına düştü ama kapanma eşiğinin (0.35) üstünde: sürer.
        assertEquals(Arrays.asList("Gülümsüyor"), tekrarla(a, 10, puanlar("mouthSmileLeft", 0.42f, "mouthSmileRight", 0.42f)));
        assertTrue(tekrarla(a, 10, puanlar("mouthSmileLeft", 0.1f, "mouthSmileRight", 0.1f)).isEmpty());
    }

    @Test
    public void gozler() {
        assertEquals(Arrays.asList("Gözler kapalı"), tekrarla(new IfadeAnalizi(), 5, puanlar("eyeBlinkLeft", 0.9f, "eyeBlinkRight", 0.8f)));
        assertEquals(Arrays.asList("Göz kırpıyor"), tekrarla(new IfadeAnalizi(), 5, puanlar("eyeBlinkLeft", 0.9f, "eyeBlinkRight", 0.1f)));
        // Kısık gözler kırpma sayılmaz.
        assertTrue(tekrarla(new IfadeAnalizi(), 5, puanlar("eyeBlinkLeft", 0.45f, "eyeBlinkRight", 0.35f)).isEmpty());
    }

    @Test
    public void kaslar() {
        assertEquals(Arrays.asList("Kaşlar kalkık"), tekrarla(new IfadeAnalizi(), 5, puanlar("browInnerUp", 0.7f)));
        assertEquals(Arrays.asList("Kaşlar çatık"), tekrarla(new IfadeAnalizi(), 5, puanlar("browDownLeft", 0.6f, "browDownRight", 0.6f)));
    }

    @Test
    public void sifirlaninca() {
        IfadeAnalizi a = new IfadeAnalizi();
        tekrarla(a, 5, puanlar("jawOpen", 0.8f));
        a.sifirla();
        assertTrue(a.guncelle(puanlar("jawOpen", 0.1f)).isEmpty());
    }
}
