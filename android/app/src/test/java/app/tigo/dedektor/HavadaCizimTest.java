package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;

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
        assertEquals(2, c.cizgiler().get(0).length);
    }

    @Test
    public void titremeEklenmez() {
        HavadaCizim c = new HavadaCizim();
        c.guncelle(0.5f, 0.5f, true, false, 0);
        c.guncelle(0.501f, 0.5f, true, false, 33);
        assertEquals(1, c.cizgiler().get(0).length);
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
}
