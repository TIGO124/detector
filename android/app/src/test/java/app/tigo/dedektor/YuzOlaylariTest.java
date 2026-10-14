package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YuzOlaylariTest {

    private static final int KARE_MS = 33;

    @Test
    public void basSallamakHayir() {
        YuzOlaylari o = new YuzOlaylari();
        YuzOlaylari.Bas ilk = null;
        long ilkZaman = -1;
        for (long t = 0; t < 2000; t += KARE_MS) {
            float yaw = 0.12f * (float) Math.sin(2 * Math.PI * 2.0 * t / 1000.0);
            YuzOlaylari.Bas b = o.guncelle(0.1f, yaw, 0.5f, t);
            if (b != null && ilk == null) {
                ilk = b;
                ilkZaman = t;
            }
        }
        assertEquals(YuzOlaylari.Bas.HAYIR, ilk);
        assertTrue("zaman=" + ilkZaman, ilkZaman < 1200);
        assertEquals(null, o.basEtiketi(ilkZaman + YuzOlaylari.ETIKET_MS));
    }

    @Test
    public void basOnaylamakEvet() {
        YuzOlaylari o = new YuzOlaylari();
        YuzOlaylari.Bas ilk = null;
        for (long t = 0; t < 2000 && ilk == null; t += KARE_MS) {
            float pitch = 0.5f + 0.08f * (float) Math.sin(2 * Math.PI * 2.0 * t / 1000.0);
            ilk = o.guncelle(0.1f, 0f, pitch, t);
            if (ilk != null) assertEquals("Evet", o.basEtiketi(t));
        }
        assertEquals(YuzOlaylari.Bas.EVET, ilk);
    }

    @Test
    public void tekDonusSayilmaz() {
        YuzOlaylari o = new YuzOlaylari();
        long t = 0;
        // Ortaya bak, yavaşça yana dön, bir süre öyle kal, geri dön.
        for (; t < 1000; t += KARE_MS) assertNull(o.guncelle(0.1f, 0f, 0.5f, t));
        for (int i = 0; i <= 15; i++, t += KARE_MS) assertNull(o.guncelle(0.1f, 0.2f * i / 15, 0.5f, t));
        for (long son = t + 2500; t < son; t += KARE_MS) assertNull(o.guncelle(0.1f, 0.2f, 0.5f, t));
        for (int i = 15; i >= 0; i--, t += KARE_MS) assertNull(o.guncelle(0.1f, 0.2f * i / 15, 0.5f, t));
        for (long son = t + 2000; t < son; t += KARE_MS) assertNull(o.guncelle(0.1f, 0f, 0.5f, t));
    }

    @Test
    public void titremeSayilmaz() {
        YuzOlaylari o = new YuzOlaylari();
        // Eşiğin altında küçük oynamalar (ör. konuşurken).
        for (long t = 0; t < 4000; t += KARE_MS) {
            float yaw = 0.03f * (float) Math.sin(2 * Math.PI * 3.0 * t / 1000.0);
            float pitch = 0.5f + 0.02f * (float) Math.sin(2 * Math.PI * 2.5 * t / 1000.0);
            assertNull(o.guncelle(0.1f, yaw, pitch, t));
        }
    }

    @Test
    public void gozlerUzunKapaliKalincaUyku() {
        YuzOlaylari o = new YuzOlaylari();
        long t = 0;
        for (; t < 1000; t += KARE_MS) o.guncelle(0.1f, 0f, 0.5f, t);
        assertFalse(o.uyku());
        long kapandi = t;
        for (; t < kapandi + YuzOlaylari.UYKU_MS - 100; t += KARE_MS) o.guncelle(0.8f, 0f, 0.5f, t);
        assertFalse("kısa kapanma (göz kırpma) uyku değil", o.uyku());
        for (; t < kapandi + YuzOlaylari.UYKU_MS + 100; t += KARE_MS) o.guncelle(0.8f, 0f, 0.5f, t);
        assertTrue(o.uyku());
        // Eşikler arası (yarı açık) uyku sürer; gözler açılınca biter.
        o.guncelle(0.5f, 0f, 0.5f, t += KARE_MS);
        assertTrue(o.uyku());
        o.guncelle(0.2f, 0f, 0.5f, t + KARE_MS);
        assertFalse(o.uyku());
    }

    @Test
    public void yuzKaybolunca() {
        YuzOlaylari o = new YuzOlaylari();
        long t = 0;
        for (; t < 2000; t += KARE_MS) o.guncelle(0.9f, 0f, 0.5f, t);
        assertTrue(o.uyku());
        o.yuzYok();
        assertFalse(o.uyku());
    }

    @Test
    public void tetikTutunca() {
        Tetik k = new Tetik(1000, 4000);
        assertFalse(k.guncelle(true, 0));
        assertFalse(k.guncelle(true, 500));
        assertEquals(0.5f, k.ilerleme(500), 1e-6f);
        // Kesinti süreyi baştan başlatır.
        assertFalse(k.guncelle(false, 600));
        assertFalse(k.guncelle(true, 700));
        assertFalse(k.guncelle(true, 1600));
        assertTrue(k.guncelle(true, 1700));
        // Koşul sürdükçe (bekleme bitse de) yeniden tetiklemez.
        assertFalse(k.guncelle(true, 3000));
        assertFalse(k.guncelle(true, 9000));
        // Bırakılıp yeniden tutulunca tetikler.
        assertFalse(k.guncelle(false, 9100));
        assertFalse(k.guncelle(true, 9200));
        assertTrue(k.guncelle(true, 10200));
    }

    @Test
    public void tetikBeklemedeBirakilsaBile() {
        Tetik k = new Tetik(0, 4000);
        assertTrue(k.guncelle(true, 0));
        assertFalse(k.guncelle(false, 100));
        // Bırakıldı ama bekleme sürüyor.
        assertFalse(k.guncelle(true, 3999));
        assertTrue(k.guncelle(true, 4000));
    }

    @Test
    public void tetikBekletilince() {
        Tetik k = new Tetik(0, 4000);
        k.beklet(1000);
        assertFalse(k.guncelle(true, 2000));
        // Elle çekimden sonra da koşul bir kez bitmeli.
        assertFalse(k.guncelle(true, 5000));
        assertFalse(k.guncelle(false, 5100));
        assertTrue(k.guncelle(true, 5200));
    }
}
