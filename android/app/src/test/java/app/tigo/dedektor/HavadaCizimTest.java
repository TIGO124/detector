package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HavadaCizimTest {

    @Test
    public void kalemInipKalkar() {
        HavadaCizim c = new HavadaCizim();
        c.guncelle(0.1f, 0.1f, true, false, 0);
        c.guncelle(0.2f, 0.1f, true, false, 33);
        c.guncelle(0.3f, 0.1f, false, false, 66);
        c.guncelle(0.5f, 0.5f, true, false, 99);
        assertEquals(2, c.cizgiler().size());
        assertEquals(2, c.cizgiler().get(0).noktalar.length);
    }

    @Test
    public void titremeEklenmez() {
        HavadaCizim c = new HavadaCizim();
        c.guncelle(0.5f, 0.5f, true, false, 0);
        c.guncelle(0.501f, 0.5f, true, false, 33);
        assertEquals(1, c.cizgiler().get(0).noktalar.length);
    }

    @Test
    public void uzunCizimdeEskiCizgilerSilinir() {
        HavadaCizim c = new HavadaCizim();
        long t = 0;
        for (int cizgi = 0; cizgi < 10; cizgi++) {
            for (int i = 0; i < 500; i++) c.guncelle((i % 100) / 100f, cizgi / 10f + (i / 100) * 0.01f, true, false, t++);
            c.guncelle(0, 0, false, false, t++);
        }
        int toplam = 0;
        for (HavadaCizim.Cizgi cizgi : c.cizgiler()) toplam += cizgi.noktalar.length;
        assertTrue("toplam=" + toplam, toplam <= HavadaCizim.EN_COK_NOKTA);
        assertTrue(c.cizgiler().size() < 10);
    }

    @Test
    public void yumrukTutulunca() {
        HavadaCizim c = new HavadaCizim();
        c.guncelle(0.1f, 0.1f, true, false, 0);
        c.guncelle(0.1f, 0.1f, false, true, 100);
        c.guncelle(0.1f, 0.1f, false, true, 100 + HavadaCizim.SILME_SURESI_MS - 1);
        assertEquals(1, c.cizgiler().size());
        c.guncelle(0.1f, 0.1f, false, true, 100 + HavadaCizim.SILME_SURESI_MS);
        assertEquals(0, c.cizgiler().size());
    }

    @Test
    public void herCizgiKendiKaleminiTasir() {
        HavadaCizim c = new HavadaCizim();
        c.kalem(0xFFFF0000, 4f);
        c.guncelle(0.1f, 0.1f, true, false, 0);
        // Kalem değişince süren çizgi biter, yenisi yeni renkle başlar.
        c.kalem(0xFF0000FF, 14f);
        c.guncelle(0.3f, 0.3f, true, false, 33);
        assertEquals(2, c.cizgiler().size());
        assertEquals(0xFFFF0000, c.cizgiler().get(0).renk);
        assertEquals(4f, c.cizgiler().get(0).kalinlik, 0f);
        assertEquals(0xFF0000FF, c.cizgiler().get(1).renk);
        assertEquals(14f, c.cizgiler().get(1).kalinlik, 0f);
    }

    @Test
    public void geriAlSonCizgiyiSiler() {
        HavadaCizim c = new HavadaCizim();
        assertFalse(c.geriAl());
        c.guncelle(0.1f, 0.1f, true, false, 0);
        c.guncelle(0.2f, 0.2f, false, false, 33);
        c.guncelle(0.5f, 0.5f, true, false, 66);
        assertTrue(c.geriAl());
        assertEquals(1, c.cizgiler().size());
        assertEquals(0.1f, c.cizgiler().get(0).noktalar[0][0], 1e-6f);
        // Geri alınan çizgiye devam edilmez: parmak hâlâ çiziyorsa yeni çizgi başlar.
        c.guncelle(0.6f, 0.6f, true, false, 99);
        assertEquals(2, c.cizgiler().size());
    }
}
