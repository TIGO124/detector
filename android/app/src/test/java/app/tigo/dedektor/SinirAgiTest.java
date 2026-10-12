package app.tigo.dedektor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class SinirAgiTest {

    /** Üç ayrık küme: telefondaki eğitim ayırmayı öğrenmeli. */
    @Test
    public void kumeleriOgrenir() {
        Random r = new Random(1);
        List<float[]> x = new ArrayList<>();
        int[] y = new int[300];
        float[][] merkez = {{0, 0, 0, 0}, {3, 3, 0, 0}, {0, 3, 3, 3}};
        for (int i = 0; i < 300; i++) {
            int s = i % 3;
            float[] v = new float[4];
            for (int j = 0; j < 4; j++) v[j] = merkez[s][j] + (float) r.nextGaussian() * 0.5f;
            x.add(v);
            y[i] = s;
        }
        SinirAgi.Sonuc sonuc = SinirAgi.egit(x, y, new String[] {"a", "b", "c"}, new int[] {16}, 30, 7, null);
        assertTrue("doğruluk " + sonuc.dogruluk, sonuc.dogruluk > 0.9f);
        assertEquals(1, SinirAgi.enBuyukIndeks(sonuc.model.calistir(new float[] {3, 3, 0, 0})));
        // Bilinen kümenin içi tanınır; hiçbir kümeye benzemeyen girdi tanınmaz.
        assertTrue(sonuc.model.taniniyor(new float[] {3, 3, 0, 0}, 1));
        float[] uzak = {20, -20, 20, -20};
        assertTrue(!sonuc.model.taniniyor(uzak, SinirAgi.enBuyukIndeks(sonuc.model.calistir(uzak))));
        // Merkezler JSON'a yazılır ve geri okunur.
        try {
            assertTrue(!SinirAgi.json(sonuc.model.json()).taniniyor(uzak, 0));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void jsonDonusumu() throws Exception {
        List<float[]> x = new ArrayList<>();
        int[] y = new int[20];
        for (int i = 0; i < 20; i++) {
            x.add(new float[] {i % 2, i});
            y[i] = i % 2;
        }
        SinirAgi a = SinirAgi.egit(x, y, new String[] {"tek", "çift"}, new int[] {4}, 5, 3, null).model;
        SinirAgi b = SinirAgi.json(a.json());
        assertArrayEquals(a.calistir(new float[] {1, 5}), b.calistir(new float[] {1, 5}), 1e-5f);
        assertArrayEquals(new String[] {"tek", "çift"}, b.etiketler);
    }

    /** Bilgisayarda eğitilen hazır modeller, Python (numpy) ile aynı sonucu vermeli. */
    @Test
    public void hazirModellerPythonIleAyni() throws Exception {
        JSONObject f = fikstur();
        JSONArray modeller = f.getJSONArray("model");
        for (int i = 0; i < modeller.length(); i++) {
            JSONObject m = modeller.getJSONObject(i);
            String yol = "src/main/assets/yz/" + m.getString("dosya") + ".json";
            SinirAgi ag = SinirAgi.json(new String(Files.readAllBytes(Paths.get(yol)), StandardCharsets.UTF_8));
            assertArrayEquals(m.getString("dosya"), dizi(m.getJSONArray("beklenen")), ag.calistir(dizi(m.getJSONArray("girdi"))), 1e-4f);
        }
    }

    static JSONObject fikstur() throws Exception {
        try (InputStream g = SinirAgiTest.class.getClassLoader().getResourceAsStream("ozellik_ornekleri.json")) {
            return new JSONObject(new String(g.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static float[] dizi(JSONArray a) throws Exception {
        float[] d = new float[a.length()];
        for (int i = 0; i < d.length; i++) d[i] = (float) a.getDouble(i);
        return d;
    }

    static float[][] noktalar(JSONArray a) throws Exception {
        float[][] p = new float[a.length()][];
        for (int i = 0; i < p.length; i++) p[i] = dizi(a.getJSONArray(i));
        return p;
    }
}
