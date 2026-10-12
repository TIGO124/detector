package app.tigo.dedektor;

import com.google.mediapipe.tasks.components.containers.Category;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Yüz ifadeleri: MediaPipe yüz modelinin 52 yüz hareketi (blendshape) puanından okunur.
 * Puanlar kareler boyunca yumuşatılır ve her ifadenin açılma eşiği kapanma eşiğinden
 * yüksektir: tek karelik sıçrama ifade sayılmaz, sınırdaki ifade yanıp sönmez.
 */
final class IfadeAnalizi {

    /** Yeni ölçümün ağırlığı. */
    private static final float YUMUSATMA = 0.4f;
    private static final String[] SIRA = {"Gülümsüyor", "Gözler kapalı", "Göz kırpıyor", "Ağız açık", "Kaşlar kalkık", "Kaşlar çatık"};

    private final Map<String, Float> puanlar = new HashMap<>();
    private final Set<String> aktif = new HashSet<>();

    /** Yeni kareyi işler, o anki ifadeleri döndürür. Tek iş parçacığından çağrılmalı. */
    List<String> guncelle(List<Category> olcum) {
        for (Category c : olcum) {
            Float eski = puanlar.get(c.categoryName());
            puanlar.put(c.categoryName(), (eski == null ? 0f : eski) * (1 - YUMUSATMA) + c.score() * YUMUSATMA);
        }
        esik("Gülümsüyor", ortalama("mouthSmileLeft", "mouthSmileRight"), 0.5f, 0.35f);

        float sol = puan("eyeBlinkLeft");
        float sag = puan("eyeBlinkRight");
        esik("Gözler kapalı", Math.min(sol, sag), 0.5f, 0.35f);
        // Tek göz kapalı: iki göz arasındaki fark büyük. Kısık gözler kırpma sayılmaz.
        float kirpma = Math.max(sol, sag) > 0.45f && !aktif.contains("Gözler kapalı") ? Math.abs(sol - sag) : 0f;
        esik("Göz kırpıyor", kirpma, 0.35f, 0.2f);

        esik("Ağız açık", puan("jawOpen"), 0.35f, 0.25f);
        esik("Kaşlar kalkık", puan("browInnerUp"), 0.5f, 0.35f);
        esik("Kaşlar çatık", aktif.contains("Kaşlar kalkık") ? 0f : ortalama("browDownLeft", "browDownRight"), 0.5f, 0.35f);
        // Not: "mouthPucker" düz, kapalı dudakta da yüksek çıkıyor (0.9); bu yüzden kullanılmıyor.

        List<String> sonuc = new ArrayList<>();
        for (String ad : SIRA) if (aktif.contains(ad)) sonuc.add(ad);
        return sonuc;
    }

    /** Yüz kaybolunca eski puanlar yeni yüze taşınmasın. */
    void sifirla() {
        puanlar.clear();
        aktif.clear();
    }

    private void esik(String ad, float deger, float acma, float kapama) {
        if (aktif.contains(ad)) {
            if (deger < kapama) aktif.remove(ad);
        } else if (deger > acma) {
            aktif.add(ad);
        }
    }

    private float puan(String ad) {
        Float d = puanlar.get(ad);
        return d == null ? 0f : d;
    }

    private float ortalama(String a, String b) {
        return (puan(a) + puan(b)) / 2f;
    }
}
