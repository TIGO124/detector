package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import app.tigo.dedektor.TasKagitMakas.Secim;
import org.junit.Test;

public class TasKagitMakasTest {

    @Test
    public void kurallar() {
        assertEquals(1, TasKagitMakas.sonuc(Secim.TAS, Secim.MAKAS));
        assertEquals(1, TasKagitMakas.sonuc(Secim.KAGIT, Secim.TAS));
        assertEquals(1, TasKagitMakas.sonuc(Secim.MAKAS, Secim.KAGIT));
        assertEquals(-1, TasKagitMakas.sonuc(Secim.MAKAS, Secim.TAS));
        assertEquals(-1, TasKagitMakas.sonuc(Secim.TAS, Secim.KAGIT));
        assertEquals(0, TasKagitMakas.sonuc(Secim.KAGIT, Secim.KAGIT));
    }

    @Test
    public void cogunluk() {
        assertEquals(Secim.MAKAS, TasKagitMakas.cogunluk(java.util.Arrays.asList(Secim.KAGIT, Secim.MAKAS, null, Secim.MAKAS)));
        assertEquals(Secim.TAS, TasKagitMakas.cogunluk(java.util.Arrays.asList(null, Secim.TAS)));
        assertNull(TasKagitMakas.cogunluk(java.util.Arrays.asList(null, null)));
    }

    @Test
    public void hareketVeParmak() {
        assertEquals(Secim.TAS, TasKagitMakas.hareketten("Closed_Fist", 3));
        assertEquals(Secim.KAGIT, TasKagitMakas.hareketten("Open_Palm", 0));
        assertEquals(Secim.MAKAS, TasKagitMakas.hareketten("Victory", 5));
        assertEquals(Secim.MAKAS, TasKagitMakas.hareketten("None", 2));
        assertEquals(Secim.TAS, TasKagitMakas.hareketten(null, 0));
        assertNull(TasKagitMakas.hareketten("Thumb_Up", 1));
    }
}
