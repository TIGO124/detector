package app.tigo.dedektor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Öğret" ile kaydedilen örnekler (etiket + öznitelik vektörü). Telefonda ikili dosyada durur;
 * aynı dosya bilgisayara aktarılıp ekran kartıyla da eğitilebilir (egitim/kisisel.py okur).
 * Biçim: "YZV1", boyut, örnek sayısı; her örnek: etiket (UTF), boyut kadar float.
 */
final class YzVeri {

    static final int ETIKET_BASINA_EN_COK = 400;
    private static final int SIHIR = 0x595A5631; // "YZV1"

    final int boyut;
    private final File dosya;
    private final Map<String, List<float[]>> ornekler = new LinkedHashMap<>();

    YzVeri(File dosya, int boyut) {
        this.dosya = dosya;
        this.boyut = boyut;
        try {
            yukle();
        } catch (IOException e) {
            ornekler.clear();
        }
    }

    synchronized void ekle(String etiket, float[] x) {
        if (x.length != boyut) return;
        List<float[]> liste = ornekler.computeIfAbsent(etiket, k -> new ArrayList<>());
        liste.add(x);
        // Etiket başına sınır: en eski örnekler çıkar, dosya büyümez.
        while (liste.size() > ETIKET_BASINA_EN_COK) liste.remove(0);
    }

    synchronized Map<String, Integer> sayilar() {
        Map<String, Integer> s = new LinkedHashMap<>();
        for (Map.Entry<String, List<float[]>> e : ornekler.entrySet()) s.put(e.getKey(), e.getValue().size());
        return s;
    }

    synchronized int toplam() {
        int n = 0;
        for (List<float[]> l : ornekler.values()) n += l.size();
        return n;
    }

    /** Bütün örnekler (kopya): etiket -> vektörler. */
    synchronized Map<String, List<float[]>> hepsi() {
        Map<String, List<float[]>> k = new LinkedHashMap<>();
        for (Map.Entry<String, List<float[]>> e : ornekler.entrySet()) k.put(e.getKey(), new ArrayList<>(e.getValue()));
        return k;
    }

    synchronized void sil(String etiket) {
        ornekler.remove(etiket);
    }

    synchronized void hepsiniSil() {
        ornekler.clear();
    }

    /** Eğitim için: etiket listesi, örnekler ve her örneğin etiket sırası. */
    synchronized Egitim egitimVerisi() {
        Egitim e = new Egitim();
        e.etiketler = ornekler.keySet().toArray(new String[0]);
        for (int i = 0; i < e.etiketler.length; i++) {
            for (float[] x : ornekler.get(e.etiketler[i])) {
                e.x.add(x);
                e.ySirasi.add(i);
            }
        }
        return e;
    }

    static final class Egitim {
        String[] etiketler;
        final List<float[]> x = new ArrayList<>();
        final List<Integer> ySirasi = new ArrayList<>();

        int[] y() {
            int[] y = new int[ySirasi.size()];
            for (int i = 0; i < y.length; i++) y[i] = ySirasi.get(i);
            return y;
        }
    }

    synchronized void kaydet() throws IOException {
        File gecici = new File(dosya.getPath() + ".tmp");
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(gecici)))) {
            o.writeInt(SIHIR);
            o.writeInt(boyut);
            o.writeInt(toplam());
            for (Map.Entry<String, List<float[]>> e : ornekler.entrySet()) {
                for (float[] x : e.getValue()) {
                    o.writeUTF(e.getKey());
                    for (float v : x) o.writeFloat(v);
                }
            }
        }
        if (dosya.exists() && !dosya.delete()) throw new IOException("Eski veri silinemedi");
        if (!gecici.renameTo(dosya)) throw new IOException("Veri kaydedilemedi");
    }

    private void yukle() throws IOException {
        if (!dosya.exists()) return;
        try (DataInputStream g = new DataInputStream(new BufferedInputStream(new FileInputStream(dosya)))) {
            if (g.readInt() != SIHIR || g.readInt() != boyut) return;
            int n = g.readInt();
            for (int s = 0; s < n; s++) {
                String etiket = g.readUTF();
                float[] x = new float[boyut];
                for (int i = 0; i < boyut; i++) x[i] = g.readFloat();
                ornekler.computeIfAbsent(etiket, k -> new ArrayList<>()).add(x);
            }
        }
    }

    File dosya() {
        return dosya;
    }
}
