package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class OyunlarTest {

    // ---------- Balon ----------

    @Test
    public void balonlarYukselirVeParmaklaPatlar() {
        BalonOyunu o = new BalonOyunu(new Random(1));
        o.basla(0);
        List<float[]> yok = Collections.emptyList();
        for (long t = 0; t <= 3000; t += 33) o.adim(t, yok, 2f);
        List<BalonOyunu.Balon> b = o.balonlar();
        assertFalse(b.isEmpty());
        BalonOyunu.Balon hedef = b.get(0);
        assertTrue("yükseldi: y=" + hedef.y, hedef.y < 1.1f);
        int patlayan = o.adim(3033, Collections.singletonList(new float[] {hedef.x, hedef.y}), 2f);
        assertTrue(patlayan >= 1);
        assertEquals(patlayan, o.puan());
    }

    @Test
    public void balonUzaktaysaPatlamaz() {
        BalonOyunu o = new BalonOyunu(new Random(2));
        o.basla(0);
        o.adim(0, Collections.emptyList(), 2f);
        BalonOyunu.Balon b = o.balonlar().get(0);
        // Yatayda yarıçaptan uzak; dikeyde ekran oranıyla ölçülür.
        float[] uzak = {b.x + BalonOyunu.YARICAP * 3, b.y};
        assertEquals(0, o.adim(10, Collections.singletonList(uzak), 2f));
        float[] dikeyUzak = {b.x, b.y + BalonOyunu.YARICAP * 1.3f};
        assertEquals(0, o.adim(20, Collections.singletonList(dikeyUzak), 2f));
    }

    @Test
    public void balonSuresiBiter() {
        BalonOyunu o = new BalonOyunu(new Random(3));
        o.basla(1000);
        assertEquals(BalonOyunu.SURE_MS, o.kalanMs(1000));
        assertEquals(-1, o.adim(1000 + BalonOyunu.SURE_MS, Collections.emptyList(), 2f));
        assertFalse(o.oynaniyor());
        assertTrue(o.balonlar().isEmpty());
    }

    // ---------- Hesap ----------

    @Test
    public void hesapDogruCevapTutulunca() {
        HesapOyunu o = new HesapOyunu(new Random(4));
        o.basla(0);
        int cevap = o.cevap();
        assertTrue(cevap >= 0 && cevap <= 10);
        assertEquals(1, o.soruNo());
        assertEquals(HesapOyunu.YOK, o.guncelle(cevap, true, 0));
        assertEquals(HesapOyunu.YOK, o.guncelle(cevap, true, HesapOyunu.TUTMA_MS - 1));
        assertEquals(HesapOyunu.DOGRU, o.guncelle(cevap, true, HesapOyunu.TUTMA_MS));
        assertEquals(2, o.soruNo());
        // Aynı cevap art arda gelmez.
        assertTrue(o.cevap() != cevap);
    }

    @Test
    public void hesapYanlisVeElsizSayilmaz() {
        HesapOyunu o = new HesapOyunu(new Random(5));
        o.basla(0);
        int yanlis = (o.cevap() + 1) % 11;
        for (long t = 0; t < 3000; t += 100) assertEquals(HesapOyunu.YOK, o.guncelle(yanlis, true, t));
        // El yokken 0 gösterilmiş sayılmaz.
        for (long t = 3000; t < 6000; t += 100) assertEquals(HesapOyunu.YOK, o.guncelle(0, false, t));
        assertEquals(1, o.soruNo());
    }

    @Test
    public void hesapOnSorudaBiter() {
        HesapOyunu o = new HesapOyunu(new Random(6));
        o.basla(0);
        long t = 0;
        int son = HesapOyunu.YOK;
        for (int i = 0; i < HesapOyunu.SORU_SAYISI; i++) {
            int c = o.cevap();
            o.guncelle(c, true, t);
            t += HesapOyunu.TUTMA_MS;
            son = o.guncelle(c, true, t);
            t += 100;
        }
        assertEquals(HesapOyunu.BITTI, son);
        assertFalse(o.oynaniyor());
        assertEquals(HesapOyunu.SORU_SAYISI * HesapOyunu.TUTMA_MS + (HesapOyunu.SORU_SAYISI - 1) * 100, o.sureMs(99_999));
    }

    @Test
    public void hesapSorulariGecerli() {
        HesapOyunu o = new HesapOyunu(new Random(7));
        for (int i = 0; i < 200; i++) {
            o.basla(0);
            String[] s = o.soru().split(" ");
            int a = Integer.parseInt(s[0]);
            int b = Integer.parseInt(s[2]);
            int c = s[1].equals("+") ? a + b : a - b;
            assertEquals(c, o.cevap());
            assertTrue(c >= 0 && c <= 10);
        }
    }

    // ---------- Piyano ----------

    @Test
    public void piyanoTuslari() {
        assertEquals(0, PiyanoOyunu.tus(0.01f, 0.3f));
        assertEquals(6, PiyanoOyunu.tus(0.99f, 0.3f));
        assertEquals(3, PiyanoOyunu.tus(0.5f, 0.3f));
        assertEquals(-1, PiyanoOyunu.tus(0.5f, 0.6f));
        assertEquals(-1, PiyanoOyunu.tus(0.5f, 0.05f));
    }

    @Test
    public void piyanoBirKezCalar() {
        PiyanoOyunu p = new PiyanoOyunu();
        List<float[]> parmak = new ArrayList<>(Collections.singletonList(new float[] {0.5f, 0.3f}));
        assertEquals(Collections.singletonList(3), p.guncelle(parmak));
        // Parmak tuşta kaldıkça yeniden çalmaz.
        assertTrue(p.guncelle(parmak).isEmpty());
        assertTrue(p.basili(3));
        // Çıkıp yeniden girince çalar.
        assertTrue(p.guncelle(Collections.singletonList(new float[] {0.5f, 0.8f})).isEmpty());
        assertEquals(Collections.singletonList(3), p.guncelle(parmak));
        // İki parmak iki tuş.
        List<float[]> iki = Arrays.asList(new float[] {0.05f, 0.3f}, new float[] {0.95f, 0.3f});
        assertEquals(Arrays.asList(0, 6), p.guncelle(iki));
        assertEquals(6, p.sonNota());
    }
}
