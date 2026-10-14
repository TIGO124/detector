package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public class YzYedekTest {

    private static File klasor() throws IOException {
        File kok = Files.createTempDirectory("yzyedek").toFile();
        File k = new File(kok, "yz");
        assertTrue(k.mkdirs());
        return k;
    }

    private static void yaz(File f, String metin) throws IOException {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(metin.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String oku(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static byte[] zip(String... adVeIcerik) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            for (int i = 0; i < adVeIcerik.length; i += 2) {
                z.putNextEntry(new ZipEntry(adVeIcerik[i]));
                z.write(adVeIcerik[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return b.toByteArray();
    }

    @Test
    public void adlar() {
        assertTrue(YzYedek.gecerliAd("hareket.json"));
        assertTrue(YzYedek.gecerliAd("kisiler_1a2b.veri"));
        assertFalse(YzYedek.gecerliAd("../hareket.json"));
        assertFalse(YzYedek.gecerliAd("a/b.veri"));
        assertFalse(YzYedek.gecerliAd("model.json.tmp"));
        assertFalse(YzYedek.gecerliAd("resim.png"));
        assertFalse(YzYedek.gecerliAd(""));
        assertFalse(YzYedek.gecerliAd(null));
    }

    @Test
    public void yedekVeGeriYukleme() throws IOException {
        File kaynak = klasor();
        yaz(new File(kaynak, "hareket.json"), "{\"a\":1}");
        yaz(new File(kaynak, "tid.veri"), "veri");
        yaz(new File(kaynak, "hareket.json.tmp"), "yarım");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        assertEquals(2, YzYedek.yedekle(kaynak, b));

        File hedef = klasor();
        yaz(new File(hedef, "tid.veri"), "eski");
        yaz(new File(hedef, "kisiler_x.veri"), "kalır");
        assertEquals(2, YzYedek.geriYukle(new ByteArrayInputStream(b.toByteArray()), hedef));
        assertEquals("{\"a\":1}", oku(new File(hedef, "hareket.json")));
        assertEquals("veri", oku(new File(hedef, "tid.veri")));
        assertEquals("kalır", oku(new File(hedef, "kisiler_x.veri")));
        assertFalse(new File(hedef, "hareket.json.tmp").exists());
        assertFalse(new File(hedef.getParentFile(), "yz_geri").exists());
    }

    @Test
    public void tehlikeliAdReddedilir() throws IOException {
        File hedef = klasor();
        yaz(new File(hedef, "tid.veri"), "eski");
        byte[] kotu = zip("tid.veri", "yeni", "../disari.veri", "x");
        try {
            YzYedek.geriYukle(new ByteArrayInputStream(kotu), hedef);
            fail();
        } catch (IOException e) {
            assertEquals("Bu dosya bir Dedektör yedeği değil.", e.getMessage());
        }
        // Hiçbir şey değişmedi, dışarı dosya yazılmadı.
        assertEquals("eski", oku(new File(hedef, "tid.veri")));
        assertFalse(new File(hedef.getParentFile(), "disari.veri").exists());
    }

    @Test
    public void zipOlmayanVeBosReddedilir() throws IOException {
        File hedef = klasor();
        try {
            YzYedek.geriYukle(new ByteArrayInputStream("zip değil".getBytes(StandardCharsets.UTF_8)), hedef);
            fail();
        } catch (IOException e) {
            assertEquals("Bu dosya bir Dedektör yedeği değil.", e.getMessage());
        }
        try {
            YzYedek.geriYukle(new ByteArrayInputStream(zip()), hedef);
            fail();
        } catch (IOException e) {
            assertEquals("Bu dosya bir Dedektör yedeği değil.", e.getMessage());
        }
    }

    @Test
    public void cokDosyaReddedilir() throws IOException {
        String[] girdiler = new String[(YzYedek.EN_COK_DOSYA + 1) * 2];
        for (int i = 0; i <= YzYedek.EN_COK_DOSYA; i++) {
            girdiler[2 * i] = "d" + i + ".veri";
            girdiler[2 * i + 1] = "x";
        }
        File hedef = klasor();
        try {
            YzYedek.geriYukle(new ByteArrayInputStream(zip(girdiler)), hedef);
            fail();
        } catch (IOException e) {
            assertEquals("Yedekte çok fazla dosya var.", e.getMessage());
        }
        String[] icerik = hedef.list();
        assertEquals(0, icerik == null ? 0 : icerik.length);
    }
}
