package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class TidYaziciTest {

    private static void tekrar(TidYazici t, String harf, int kez) {
        for (int i = 0; i < kez; i++) t.sonuc(harf);
    }

    @Test
    public void sabitHarfBirKezEklenir() {
        TidYazici t = new TidYazici();
        tekrar(t, "A", TidYazici.SABIT - 1);
        assertEquals("", t.metin());
        tekrar(t, "A", 30);
        assertEquals("A", t.metin());
        // Aynı harf tekrar yazılmaz; el indirilip yeniden gösterilince yazılır.
        tekrar(t, "", TidYazici.BOS);
        tekrar(t, "A", TidYazici.SABIT);
        assertEquals("AA", t.metin());
        // Kelime olarak öğretilen etiketin arkasına boşluk gelir.
        tekrar(t, "Merhaba", TidYazici.SABIT);
        assertEquals("AAMerhaba ", t.metin());
        t.duzenle("sil");
        t.duzenle("bosluk");
        assertEquals("AAMerhaba ", t.metin());
        t.duzenle("temizle");
        assertEquals("", t.metin());
    }

    @Test
    public void titreyenTanimaEklenmez() {
        TidYazici t = new TidYazici();
        for (int i = 0; i < 40; i++) t.sonuc(i % 2 == 0 ? "A" : "B");
        assertEquals("", t.metin());
    }

    @Test
    public void pencereYeterliKareBirikinceHazir() {
        TidYazici t = new TidYazici();
        List<NormalizedLandmark> noktalar = new ArrayList<>();
        float[][] dunya = new float[21][3];
        for (int i = 0; i < 21; i++) {
            noktalar.add(NormalizedLandmark.create(0.5f, 0.5f, 0f));
            dunya[i] = new float[] {0.01f * i, 0.02f * i, 0f};
        }
        ElTakibi.ElDurumu el = new ElTakibi.ElDurumu(noktalar, "Sağ el", null, 5, false);
        el.dunya = dunya;
        List<ElTakibi.ElDurumu> eller = Collections.singletonList(el);
        assertNull(t.kare(eller, 0));
        assertNull(t.kare(eller, 200));
        float[] x = t.kare(eller, 400);
        assertNotNull(x);
        assertEquals(YzOzellik.PENCERE_BOYUTU, x.length);
        // El yoksa pencere yok.
        assertNull(t.kare(Collections.emptyList(), 450));
    }
}
