package app.tigo.dedektor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;
import org.junit.Test;

public class YzVeriTest {

    @Test
    public void kaydedipGeriOkur() throws Exception {
        File f = File.createTempFile("yzveri", ".veri");
        f.deleteOnExit();
        YzVeri v = new YzVeri(f, 3);
        v.ekle("Selam", new float[] {1, 2, 3});
        v.ekle("Selam", new float[] {4, 5, 6});
        v.ekle("Dur", new float[] {7, 8, 9});
        v.ekle("Yanlış boyut", new float[] {1});
        v.kaydet();

        YzVeri g = new YzVeri(f, 3);
        assertEquals(2, (int) g.sayilar().get("Selam"));
        assertEquals(1, (int) g.sayilar().get("Dur"));
        assertEquals(3, g.toplam());
        YzVeri.Egitim e = g.egitimVerisi();
        assertArrayEquals(new String[] {"Selam", "Dur"}, e.etiketler);
        assertArrayEquals(new int[] {0, 0, 1}, e.y());
        assertArrayEquals(new float[] {7, 8, 9}, e.x.get(2), 0f);
    }

    @Test
    public void etiketBasinaSinir() throws Exception {
        YzVeri v = new YzVeri(File.createTempFile("yzveri", ".veri"), 1);
        for (int i = 0; i < YzVeri.ETIKET_BASINA_EN_COK + 50; i++) v.ekle("A", new float[] {i});
        assertEquals(YzVeri.ETIKET_BASINA_EN_COK, (int) v.sayilar().get("A"));
        // En eskiler çıkar.
        assertEquals(50f, v.egitimVerisi().x.get(0)[0], 0f);
    }

    @Test
    public void baskaBoyuttakiDosyaYoksayilir() throws Exception {
        File f = File.createTempFile("yzveri", ".veri");
        YzVeri v = new YzVeri(f, 2);
        v.ekle("A", new float[] {1, 2});
        v.kaydet();
        assertEquals(0, new YzVeri(f, 5).toplam());
    }
}
