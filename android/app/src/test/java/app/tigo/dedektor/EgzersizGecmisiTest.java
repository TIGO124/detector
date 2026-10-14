package app.tigo.dedektor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class EgzersizGecmisiTest {

    private static final class Bellek implements EgzersizGecmisi.Depo {
        final Map<String, Integer> m = new HashMap<>();

        @Override
        public int oku(String anahtar) {
            Integer d = m.get(anahtar);
            return d == null ? 0 : d;
        }

        @Override
        public void yaz(String anahtar, int deger) {
            m.put(anahtar, deger);
        }

        @Override
        public Set<String> anahtarlar() {
            return new HashSet<>(m.keySet());
        }

        @Override
        public void sil(String anahtar) {
            m.remove(anahtar);
        }
    }

    @Test
    public void gunlukVeHaftalik() {
        EgzersizGecmisi g = new EgzersizGecmisi(new Bellek());
        long bugun = 20000;
        g.ekle(bugun, EgzersizSayaci.Tur.SQUAT, 5);
        g.ekle(bugun, EgzersizSayaci.Tur.SQUAT, 3);
        g.ekle(bugun - 2, EgzersizSayaci.Tur.SQUAT, 10);
        g.ekle(bugun - 7, EgzersizSayaci.Tur.SQUAT, 100);
        g.ekle(bugun, EgzersizSayaci.Tur.SINAV, 4);
        assertEquals(8, g.gunluk(bugun, EgzersizSayaci.Tur.SQUAT));
        assertArrayEquals(new int[] {0, 0, 0, 0, 10, 0, 8}, g.haftalik(bugun, EgzersizSayaci.Tur.SQUAT));
        // 7 gün önceki pencerenin dışında
        assertEquals(18, g.haftalikToplam(bugun, EgzersizSayaci.Tur.SQUAT));
        assertEquals(4, g.gunluk(bugun, EgzersizSayaci.Tur.SINAV));
    }

    @Test
    public void sifirVeEksiEklenmez() {
        Bellek b = new Bellek();
        EgzersizGecmisi g = new EgzersizGecmisi(b);
        g.ekle(1, EgzersizSayaci.Tur.PLANK, 0);
        g.ekle(1, EgzersizSayaci.Tur.PLANK, -3);
        assertTrue(b.m.isEmpty());
    }

    @Test
    public void eskiVeBozukKayitlarSilinir() {
        Bellek b = new Bellek();
        EgzersizGecmisi g = new EgzersizGecmisi(b);
        g.ekle(1000, EgzersizSayaci.Tur.SQUAT, 1);
        g.ekle(1059, EgzersizSayaci.Tur.SQUAT, 1);
        b.m.put("bozuk", 3);
        g.temizle(1100);
        assertFalse(b.m.containsKey("1000_SQUAT"));
        assertTrue(b.m.containsKey("1059_SQUAT"));
        assertFalse(b.m.containsKey("bozuk"));
    }
}
