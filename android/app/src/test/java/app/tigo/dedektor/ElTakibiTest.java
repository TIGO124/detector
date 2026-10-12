package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import app.tigo.dedektor.ElTakibi.ElDurumu;
import app.tigo.dedektor.ElTakibi.ElOlcumu;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ElTakibiTest {

    private static final boolean[] BES = {true, true, true, true, true};
    private static final boolean[] IKI = {false, true, true, false, false};
    private static final boolean[] ISARET = {false, true, false, false, false};

    private static ElOlcumu olcum(float x, float sag, String hareket, boolean[] parmaklar) {
        return new ElOlcumu(Collections.emptyList(), x, 0.5f, sag, hareket, 0.9f, parmaklar);
    }

    private static ElDurumu tek(ElTakibi t, ElOlcumu o) {
        return t.guncelle(Collections.singletonList(o)).get(0);
    }

    @Test
    public void tekKarelikParmakHatasiGorunmez() {
        ElTakibi t = new ElTakibi();
        for (int i = 0; i < 4; i++) tek(t, olcum(0.5f, 0.9f, "Open_Palm", BES));
        assertEquals(5, tek(t, olcum(0.5f, 0.9f, "Open_Palm", IKI)).parmak);
        assertEquals(5, tek(t, olcum(0.5f, 0.9f, "Open_Palm", BES)).parmak);
    }

    @Test
    public void hareketCogunluklaBelirlenir() {
        ElTakibi t = new ElTakibi();
        assertNull(tek(t, olcum(0.5f, 0.9f, "Victory", IKI)).hareketKodu);
        tek(t, olcum(0.5f, 0.9f, "Victory", IKI));
        assertEquals("Victory", tek(t, olcum(0.5f, 0.9f, "Victory", IKI)).hareketKodu);
        // Arada bir "None" ya da düşük puan hareketi bozmaz.
        assertEquals("Victory", tek(t, olcum(0.5f, 0.9f, "None", IKI)).hareketKodu);
        assertEquals("Victory", tek(t, new ElOlcumu(Collections.emptyList(), 0.5f, 0.5f, 0.9f, "Thumb_Up", 0.3f, IKI)).hareketKodu);
    }

    @Test
    public void tarafTekKaredeDegismez() {
        ElTakibi t = new ElTakibi();
        tek(t, olcum(0.5f, 0.9f, null, BES));
        tek(t, olcum(0.5f, 0.9f, null, BES));
        assertEquals("Sağ el", tek(t, olcum(0.5f, 0.1f, null, BES)).taraf);
        assertEquals("Sağ el", tek(t, olcum(0.5f, 0.9f, null, BES)).taraf);
    }

    @Test
    public void ellerSiraDegisseDeKarismaz() {
        ElTakibi t = new ElTakibi();
        for (int i = 0; i < 3; i++) {
            t.guncelle(Arrays.asList(olcum(0.2f, 0.9f, null, BES), olcum(0.8f, 0.1f, null, IKI)));
        }
        // Model elleri ters sırayla verdi: her el kendi geçmişini korur.
        List<ElDurumu> d = t.guncelle(Arrays.asList(olcum(0.8f, 0.1f, null, IKI), olcum(0.2f, 0.9f, null, BES)));
        assertEquals("Sol el", d.get(0).taraf);
        assertEquals(2, d.get(0).parmak);
        assertEquals("Sağ el", d.get(1).taraf);
        assertEquals(5, d.get(1).parmak);
    }

    @Test
    public void kaybolanElUnutulur() {
        ElTakibi t = new ElTakibi();
        for (int i = 0; i < 4; i++) tek(t, olcum(0.5f, 0.9f, null, BES));
        t.guncelle(Collections.emptyList());
        assertEquals(2, tek(t, olcum(0.5f, 0.9f, null, IKI)).parmak);
    }

    @Test
    public void isaretSonKarelerinCogunlugu() {
        ElTakibi t = new ElTakibi();
        tek(t, olcum(0.5f, 0.9f, null, ISARET));
        tek(t, olcum(0.5f, 0.9f, null, ISARET));
        assertTrue(tek(t, olcum(0.5f, 0.9f, null, BES)).isaret);
        assertFalse(tek(t, olcum(0.5f, 0.9f, null, BES)).isaret);
    }
}
