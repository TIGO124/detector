package app.tigo.dedektor;

import com.google.mediapipe.tasks.components.containers.Category;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Yüz ifadeleri: MediaPipe yüz modelinin 52 yüz hareketi (blendshape) puanından okunur. */
final class IfadeAnalizi {

    private IfadeAnalizi() {}

    static List<String> ifadeler(List<Category> puanlar) {
        Map<String, Float> p = new HashMap<>();
        for (Category c : puanlar) p.put(c.categoryName(), c.score());
        List<String> sonuc = new ArrayList<>();

        if (ortalama(p, "mouthSmileLeft", "mouthSmileRight") > 0.5f) sonuc.add("Gülümsüyor");

        float sol = puan(p, "eyeBlinkLeft");
        float sag = puan(p, "eyeBlinkRight");
        if (sol > 0.5f && sag > 0.5f) sonuc.add("Gözler kapalı");
        else if ((sol > 0.5f && sag < 0.3f) || (sag > 0.5f && sol < 0.3f)) sonuc.add("Göz kırpıyor");

        if (puan(p, "jawOpen") > 0.35f) sonuc.add("Ağız açık");

        if (puan(p, "browInnerUp") > 0.5f) sonuc.add("Kaşlar kalkık");
        else if (ortalama(p, "browDownLeft", "browDownRight") > 0.5f) sonuc.add("Kaşlar çatık");
        // Not: "mouthPucker" düz, kapalı dudakta da yüksek çıkıyor (0.9); bu yüzden kullanılmıyor.
        return sonuc;
    }

    private static float puan(Map<String, Float> p, String ad) {
        Float d = p.get(ad);
        return d == null ? 0f : d;
    }

    private static float ortalama(Map<String, Float> p, String a, String b) {
        return (puan(p, a) + puan(p, b)) / 2f;
    }
}
