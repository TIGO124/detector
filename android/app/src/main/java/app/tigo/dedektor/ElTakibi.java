package app.tigo.dedektor;

import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Elleri kareler boyunca izler ve etiketleri sabitler. Parmak sayısı, el hareketi ve sağ/sol
 * son birkaç karenin oylamasıyla belirlenir; tek karelik yanlış ölçüm ekrana yansımaz.
 * Eller bilek konumundan eşleştirilir: model elleri farklı sırayla verse de karışmaz.
 */
final class ElTakibi {

    /** Bir karede bir elin ham ölçümü. */
    static final class ElOlcumu {
        final List<NormalizedLandmark> noktalar;
        final float bilekX;
        final float bilekY;
        /** Elin kişinin sağ eli olma olasılığı (0–1). */
        final float sagOlasiligi;
        final String hareket;
        final float hareketPuani;
        final boolean[] acikParmaklar;
        /** 3B "dünya" noktaları (metre); yapay zekâ öznitelikleri için. Yoksa null. */
        float[][] dunya;

        ElOlcumu(List<NormalizedLandmark> noktalar, float bilekX, float bilekY, float sagOlasiligi,
                 String hareket, float hareketPuani, boolean[] acikParmaklar) {
            this.noktalar = noktalar;
            this.bilekX = bilekX;
            this.bilekY = bilekY;
            this.sagOlasiligi = sagOlasiligi;
            this.hareket = hareket;
            this.hareketPuani = hareketPuani;
            this.acikParmaklar = acikParmaklar;
        }
    }

    /** Ekranda gösterilen, sabitlenmiş el durumu. */
    static final class ElDurumu {
        final List<NormalizedLandmark> noktalar;
        final String taraf;
        /** MediaPipe hareket kodu (ör. "Closed_Fist"); hareket sabit değilse null. */
        final String hareketKodu;
        final int parmak;
        final boolean isaret;
        float[][] dunya;
        /** Yapay zekâ modunda tanınan hareket ya da harf; yoksa null. */
        String yzEtiketi;

        ElDurumu(List<NormalizedLandmark> noktalar, String taraf, String hareketKodu, int parmak, boolean isaret) {
            this.noktalar = noktalar;
            this.taraf = taraf;
            this.hareketKodu = hareketKodu;
            this.parmak = parmak;
            this.isaret = isaret;
        }

        boolean yumruk() {
            return "Closed_Fist".equals(hareketKodu) || parmak == 0;
        }
    }

    static final int GECMIS = 6;
    static final float HAREKET_ESIGI = 0.6f;
    /** Bir karede bundan uzağa kayan bilek yeni bir el sayılır (normalize). */
    private static final float ESLESME_UZAKLIGI = 0.25f;

    private static final class Iz {
        float bilekX;
        float bilekY;
        float sagOlasiligi = -1f;
        final ArrayDeque<Integer> parmaklar = new ArrayDeque<>();
        final ArrayDeque<String> hareketler = new ArrayDeque<>();
        final ArrayDeque<Boolean> isaretler = new ArrayDeque<>();
    }

    private List<Iz> izler = new ArrayList<>();

    /** MediaPipe sonucunu ölçümlere çevirir; parmaklar 3B "dünya" noktalarından hesaplanır. */
    static List<ElOlcumu> olcumler(GestureRecognizerResult sonuc) {
        List<ElOlcumu> liste = new ArrayList<>();
        for (int i = 0; i < sonuc.landmarks().size(); i++) {
            List<NormalizedLandmark> n = sonuc.landmarks().get(i);
            float[][] noktalar = i < sonuc.worldLandmarks().size()
                ? ElAnalizi.dunyaNoktalari(sonuc.worldLandmarks().get(i))
                : ElAnalizi.noktalar(n);
            Category taraf = ilk(sonuc.handedness(), i);
            float sag = taraf == null ? 0.5f : "Right".equals(taraf.categoryName()) ? taraf.score() : 1f - taraf.score();
            Category hareket = ilk(sonuc.gestures(), i);
            ElOlcumu o = new ElOlcumu(n, n.get(0).x(), n.get(0).y(), sag,
                hareket == null ? null : hareket.categoryName(), hareket == null ? 0f : hareket.score(),
                ElAnalizi.acikParmaklar(noktalar));
            o.dunya = noktalar;
            liste.add(o);
        }
        return liste;
    }

    private static Category ilk(List<List<Category>> liste, int i) {
        return i < liste.size() && !liste.get(i).isEmpty() ? liste.get(i).get(0) : null;
    }

    /** Yeni kareyi işler; görünmeyen eller unutulur. Tek iş parçacığından çağrılmalı. */
    List<ElDurumu> guncelle(List<ElOlcumu> olcumler) {
        List<Iz> yeniIzler = new ArrayList<>();
        List<ElDurumu> durumlar = new ArrayList<>();
        List<Iz> bos = new ArrayList<>(izler);
        for (ElOlcumu o : olcumler) {
            Iz iz = enYakin(bos, o.bilekX, o.bilekY);
            if (iz == null) iz = new Iz();
            else bos.remove(iz);
            iz.bilekX = o.bilekX;
            iz.bilekY = o.bilekY;
            iz.sagOlasiligi = iz.sagOlasiligi < 0 ? o.sagOlasiligi : iz.sagOlasiligi * 0.7f + o.sagOlasiligi * 0.3f;
            ekle(iz.parmaklar, ElAnalizi.parmakSayisi(o.acikParmaklar));
            // ArrayDeque null almaz: tanınmayan hareket boş dize olarak saklanır.
            ekle(iz.hareketler, o.hareket != null && o.hareketPuani >= HAREKET_ESIGI && !"None".equals(o.hareket) ? o.hareket : "");
            ekle(iz.isaretler, ElAnalizi.isaretEdiyor(o.acikParmaklar));
            yeniIzler.add(iz);
            ElDurumu d = new ElDurumu(o.noktalar, iz.sagOlasiligi >= 0.5f ? "Sağ el" : "Sol el",
                cogunluk(iz.hareketler, GECMIS / 2), enCok(iz.parmaklar), sonOylar(iz.isaretler, 3));
            d.dunya = o.dunya;
            durumlar.add(d);
        }
        izler = yeniIzler;
        return Collections.unmodifiableList(durumlar);
    }

    private Iz enYakin(List<Iz> adaylar, float x, float y) {
        Iz secilen = null;
        double enKisa = ESLESME_UZAKLIGI;
        for (Iz iz : adaylar) {
            double d = Math.hypot(iz.bilekX - x, iz.bilekY - y);
            if (d < enKisa) {
                enKisa = d;
                secilen = iz;
            }
        }
        return secilen;
    }

    private static <T> void ekle(ArrayDeque<T> gecmis, T deger) {
        gecmis.addLast(deger);
        while (gecmis.size() > GECMIS) gecmis.removeFirst();
    }

    /** En sık değer; eşitlikte en yenisi. */
    private static int enCok(ArrayDeque<Integer> gecmis) {
        Map<Integer, Integer> sayac = new HashMap<>();
        int secilen = gecmis.peekLast();
        int enFazla = 0;
        for (Integer d : gecmis) sayac.merge(d, 1, Integer::sum);
        for (Integer d : gecmis) {
            int s = sayac.get(d);
            if (s >= enFazla) {
                enFazla = s;
                secilen = d;
            }
        }
        return secilen;
    }

    /** En sık hareket (boş olmayan); en az "gereken" kez görülmediyse null. */
    private static String cogunluk(ArrayDeque<String> gecmis, int gereken) {
        Map<String, Integer> sayac = new HashMap<>();
        for (String h : gecmis) if (!h.isEmpty()) sayac.merge(h, 1, Integer::sum);
        String secilen = null;
        int enFazla = 0;
        for (Map.Entry<String, Integer> e : sayac.entrySet()) {
            if (e.getValue() > enFazla) {
                enFazla = e.getValue();
                secilen = e.getKey();
            }
        }
        return enFazla >= gereken ? secilen : null;
    }

    /** Son "adet" oyun çoğunluğu (daha az kayıt varsa olanlarınki). */
    private static boolean sonOylar(ArrayDeque<Boolean> gecmis, int adet) {
        int evet = 0;
        int sayilan = 0;
        Iterator<Boolean> it = gecmis.descendingIterator();
        while (it.hasNext() && sayilan < adet) {
            if (it.next()) evet++;
            sayilan++;
        }
        return evet * 2 > sayilan;
    }
}
