package app.tigo.dedektor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** Öznitelikler, bilgisayardaki eğitimle (egitim/ozellik.py) birebir aynı olmalı. */
public class YzOzellikTest {

    @Test
    public void elPythonIleAyni() throws Exception {
        JSONArray ornekler = SinirAgiTest.fikstur().getJSONArray("el");
        for (int i = 0; i < ornekler.length(); i++) {
            JSONObject o = ornekler.getJSONObject(i);
            float[] java = YzOzellik.el(SinirAgiTest.noktalar(o.getJSONArray("noktalar")), o.getBoolean("sol"));
            assertArrayEquals(SinirAgiTest.dizi(o.getJSONArray("beklenen")), java, 1e-5f);
        }
    }

    @Test
    public void vucutPythonIleAyni() throws Exception {
        JSONArray ornekler = SinirAgiTest.fikstur().getJSONArray("vucut");
        for (int i = 0; i < ornekler.length(); i++) {
            JSONObject o = ornekler.getJSONObject(i);
            float[] java = YzOzellik.vucut(SinirAgiTest.noktalar(o.getJSONArray("noktalar")));
            assertArrayEquals(SinirAgiTest.dizi(o.getJSONArray("beklenen")), java, 1e-4f);
        }
    }

    /** Kişi tanımada yüz hizalama (KisiTanima) egitim/hizala.py ile aynı dönüşümü bulmalı. */
    @Test
    public void hizalamaPythonIleAyni() throws Exception {
        JSONObject o = SinirAgiTest.fikstur().getJSONArray("hizalama").getJSONObject(0);
        List<NormalizedLandmark> yuz = new ArrayList<>();
        for (float[] n : SinirAgiTest.noktalar(o.getJSONArray("noktalar"))) yuz.add(NormalizedLandmark.create(n[0], n[1], n[2]));
        float[] bes = KisiTanima.besNokta(yuz, o.getInt("g"), o.getInt("y"));
        assertArrayEquals(SinirAgiTest.dizi(o.getJSONArray("bes")), bes, 1e-3f);
        assertArrayEquals(SinirAgiTest.dizi(o.getJSONArray("beklenen")), KisiTanima.benzerlik(bes, SinirAgiTest.dizi(o.getJSONArray("kalip"))), 1e-3f);
    }

    /** Bilinen bir benzerlik dönüşümü tam olarak geri bulunur. */
    @Test
    public void benzerlikDonusumunuBulur() {
        float a = 0.8f, b = 0.3f, tx = 5f, ty = -7f;
        float[] kaynak = {10, 20, 50, 22, 30, 40, 15, 60, 45, 61};
        float[] hedef = new float[10];
        for (int i = 0; i < 5; i++) {
            hedef[2 * i] = a * kaynak[2 * i] - b * kaynak[2 * i + 1] + tx;
            hedef[2 * i + 1] = b * kaynak[2 * i] + a * kaynak[2 * i + 1] + ty;
        }
        assertArrayEquals(new float[] {a, -b, tx, b, a, ty}, KisiTanima.benzerlik(kaynak, hedef), 1e-4f);
    }

    /** Pencere: kareler sırayla yan yana, bileklerin son kareye göre kayması (x4), eksik el 0. */
    @Test
    public void pencere() {
        float[][] kareler = new float[YzOzellik.PENCERE][YzOzellik.IKI_EL_BOYUTU];
        float[][] bilekler = new float[YzOzellik.PENCERE][];
        for (int k = 0; k < YzOzellik.PENCERE; k++) {
            kareler[k][0] = k;
            bilekler[k] = new float[] {0.1f * k, 0.5f, Float.NaN, Float.NaN};
        }
        float[] p = YzOzellik.pencere(kareler, bilekler);
        assertEquals(YzOzellik.PENCERE_BOYUTU, p.length);
        int k = YzOzellik.KARE_BOYUTU;
        assertEquals(2f, p[2 * k], 0f);
        int kayma = YzOzellik.IKI_EL_BOYUTU;
        assertEquals(-0.5f * 4, p[kayma], 1e-5f);
        assertEquals(0f, p[kayma + 1], 0f);
        assertEquals(0f, p[kayma + 2], 0f);
        assertEquals(0f, p[(YzOzellik.PENCERE - 1) * k + kayma], 0f);
    }
}
