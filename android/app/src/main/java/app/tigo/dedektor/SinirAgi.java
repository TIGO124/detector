package app.tigo.dedektor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Küçük, tam bağlı sinir ağı (MLP). Telefonda hem çalışır hem eğitilir; bilgisayarda
 * (egitim/ortak.py, PyTorch) eğitilen modellerle aynı JSON biçimini kullanır.
 * Gizli katmanlar ReLU; çıkış ya softmax (sınıflandırma) ya da birim uzunluğa
 * ölçeklenmiş vektördür (gömme: kişi tanımada yüz imzası).
 */
final class SinirAgi {

    /** Eğitim sırasında ilerleme (0–1). */
    interface Ilerleme {
        void bildir(float oran);
    }

    final String[] etiketler;
    /** true: çıkış olasılıklar (softmax); false: birim uzunlukta gömme vektörü. */
    final boolean siniflandirici;
    final float[] ortalama;
    final float[] sapma;
    /** [katman][çıkış][giriş] */
    final float[][][] agirlik;
    /** [katman][çıkış] */
    final float[][] esik;
    /**
     * Her sınıfın eğitim örneklerinin (normalize) merkezi ve yarıçapı. Ağ hiç görmediği bir girdiyi
     * de bir sınıfa "emin" biçimde atar; merkezden çok uzak girdi tanınmamış sayılır. Yoksa null.
     */
    float[][] merkezler;
    float[] yaricaplar;

    SinirAgi(String[] etiketler, boolean siniflandirici, float[] ortalama, float[] sapma, float[][][] agirlik, float[][] esik) {
        this.etiketler = etiketler;
        this.siniflandirici = siniflandirici;
        this.ortalama = ortalama;
        this.sapma = sapma;
        this.agirlik = agirlik;
        this.esik = esik;
    }

    /** Girdi bu sınıfın eğitim örneklerine yeterince yakın mı (yarıçapın 1.5 katı içinde). */
    boolean taniniyor(float[] x, int sinif) {
        if (merkezler == null) return true;
        double d = 0;
        for (int i = 0; i < x.length; i++) {
            double f = (x[i] - ortalama[i]) / sapma[i] - merkezler[sinif][i];
            d += f * f;
        }
        return Math.sqrt(d) <= 1.5 * yaricaplar[sinif];
    }

    int girdiBoyutu() {
        return ortalama.length;
    }

    /** Girdiyi normalize edip ağdan geçirir. */
    float[] calistir(float[] x) {
        float[] a = new float[x.length];
        for (int i = 0; i < x.length; i++) a[i] = (x[i] - ortalama[i]) / sapma[i];
        for (int k = 0; k < agirlik.length; k++) {
            float[] z = katman(a, k);
            if (k < agirlik.length - 1) for (int i = 0; i < z.length; i++) z[i] = Math.max(0f, z[i]);
            a = z;
        }
        return siniflandirici ? softmax(a) : birimVektor(a);
    }

    private float[] katman(float[] a, int k) {
        float[][] w = agirlik[k];
        float[] z = new float[w.length];
        for (int o = 0; o < w.length; o++) {
            float t = esik[k][o];
            float[] satir = w[o];
            for (int i = 0; i < satir.length; i++) t += satir[i] * a[i];
            z[o] = t;
        }
        return z;
    }

    static float[] softmax(float[] z) {
        float enBuyuk = Float.NEGATIVE_INFINITY;
        for (float v : z) enBuyuk = Math.max(enBuyuk, v);
        float toplam = 0;
        float[] p = new float[z.length];
        for (int i = 0; i < z.length; i++) {
            p[i] = (float) Math.exp(z[i] - enBuyuk);
            toplam += p[i];
        }
        for (int i = 0; i < p.length; i++) p[i] /= toplam;
        return p;
    }

    static float[] birimVektor(float[] v) {
        double n = 0;
        for (float d : v) n += d * d;
        float olcek = n > 0 ? (float) (1 / Math.sqrt(n)) : 0f;
        float[] u = new float[v.length];
        for (int i = 0; i < v.length; i++) u[i] = v[i] * olcek;
        return u;
    }

    // ---------- Telefonda eğitim ----------

    /** Eğitim sonucu: model ve ayrılmış doğrulama örneklerindeki doğruluk. */
    static final class Sonuc {
        final SinirAgi model;
        final float dogruluk;

        Sonuc(SinirAgi model, float dogruluk) {
            this.model = model;
            this.dogruluk = dogruluk;
        }
    }

    /**
     * Sınıflandırıcı eğitir: Adam, çapraz entropi, küçük gürültüyle veri çoğaltma.
     * Örneklerin %15'i doğruluğu ölçmek için ayrılır (en az 2 örneği olan sınıflardan).
     */
    static Sonuc egit(List<float[]> x, int[] y, String[] etiketler, int[] gizli, int tur, long tohum, Ilerleme ilerleme) {
        int n = x.size();
        int boyut = x.get(0).length;
        Random r = new Random(tohum);

        // Normalizasyon: her öznitelik sıfır ortalama, birim sapma.
        float[] ort = new float[boyut];
        float[] sap = new float[boyut];
        for (float[] v : x) for (int i = 0; i < boyut; i++) ort[i] += v[i] / n;
        for (float[] v : x) for (int i = 0; i < boyut; i++) sap[i] += (v[i] - ort[i]) * (v[i] - ort[i]) / n;
        for (int i = 0; i < boyut; i++) sap[i] = (float) Math.max(Math.sqrt(sap[i]), 1e-4);
        float[][] xn = new float[n][boyut];
        for (int s = 0; s < n; s++) for (int i = 0; i < boyut; i++) xn[s][i] = (x.get(s)[i] - ort[i]) / sap[i];

        // Doğrulama ayırma
        List<Integer> egitimIdx = new ArrayList<>();
        List<Integer> dogrulamaIdx = new ArrayList<>();
        for (int s = 0; s < n; s++) (r.nextFloat() < 0.15f ? dogrulamaIdx : egitimIdx).add(s);
        if (egitimIdx.isEmpty()) {
            egitimIdx.addAll(dogrulamaIdx);
            dogrulamaIdx.clear();
        }

        // Katmanlar (He başlatma)
        int[] boyutlar = new int[gizli.length + 2];
        boyutlar[0] = boyut;
        System.arraycopy(gizli, 0, boyutlar, 1, gizli.length);
        boyutlar[boyutlar.length - 1] = etiketler.length;
        int L = boyutlar.length - 1;
        float[][][] w = new float[L][][];
        float[][] b = new float[L][];
        for (int k = 0; k < L; k++) {
            w[k] = new float[boyutlar[k + 1]][boyutlar[k]];
            b[k] = new float[boyutlar[k + 1]];
            float olcek = (float) Math.sqrt(2.0 / boyutlar[k]);
            for (float[] satir : w[k]) for (int i = 0; i < satir.length; i++) satir[i] = (float) r.nextGaussian() * olcek;
        }
        Adam adam = new Adam(w, b);

        int topluk = 32;
        int[] sira = new int[egitimIdx.size()];
        for (int i = 0; i < sira.length; i++) sira[i] = egitimIdx.get(i);
        for (int tur0 = 0; tur0 < tur; tur0++) {
            for (int i = sira.length - 1; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int t = sira[i];
                sira[i] = sira[j];
                sira[j] = t;
            }
            for (int bas = 0; bas < sira.length; bas += topluk) {
                int son = Math.min(sira.length, bas + topluk);
                adam.sifirla();
                for (int s = bas; s < son; s++) {
                    float[] girdi = xn[sira[s]].clone();
                    for (int i = 0; i < girdi.length; i++) girdi[i] += (float) r.nextGaussian() * 0.05f;
                    geriYay(w, b, girdi, y[sira[s]], adam);
                }
                adam.adim(son - bas, 3e-3f, 1e-4f);
            }
            if (ilerleme != null) ilerleme.bildir((tur0 + 1) / (float) tur);
        }

        SinirAgi model = new SinirAgi(etiketler, true, ort, sap, w, b);
        model.sinirlariHesapla(xn, y);
        float dogruluk = 1f;
        if (!dogrulamaIdx.isEmpty()) {
            int dogru = 0;
            for (int s : dogrulamaIdx) if (enBuyukIndeks(model.calistir(x.get(s))) == y[s]) dogru++;
            dogruluk = dogru / (float) dogrulamaIdx.size();
        }
        return new Sonuc(model, dogruluk);
    }

    /** Tek örnek için ileri ve geri yayılım; türevler Adam'ın toplamlarına eklenir. */
    private static void geriYay(float[][][] w, float[][] b, float[] girdi, int hedef, Adam adam) {
        int L = w.length;
        float[][] akt = new float[L + 1][];
        akt[0] = girdi;
        for (int k = 0; k < L; k++) {
            float[] z = new float[w[k].length];
            for (int o = 0; o < z.length; o++) {
                float t = b[k][o];
                float[] satir = w[k][o];
                for (int i = 0; i < satir.length; i++) t += satir[i] * akt[k][i];
                z[o] = k < L - 1 ? Math.max(0f, t) : t;
            }
            akt[k + 1] = z;
        }
        float[] delta = softmax(akt[L]);
        delta[hedef] -= 1f;
        for (int k = L - 1; k >= 0; k--) {
            float[] onceki = akt[k];
            float[] yeniDelta = k > 0 ? new float[onceki.length] : null;
            for (int o = 0; o < delta.length; o++) {
                float d = delta[o];
                if (d == 0f) continue;
                adam.gb[k][o] += d;
                float[] satir = w[k][o];
                float[] gs = adam.gw[k][o];
                for (int i = 0; i < satir.length; i++) {
                    gs[i] += d * onceki[i];
                    if (yeniDelta != null) yeniDelta[i] += d * satir[i];
                }
            }
            if (yeniDelta != null) for (int i = 0; i < yeniDelta.length; i++) if (onceki[i] <= 0f) yeniDelta[i] = 0f;
            delta = yeniDelta;
        }
    }

    /** Adam eniyileyicisi (türev toplamları + birinci/ikinci moment). */
    private static final class Adam {
        final float[][][] w;
        final float[][] b;
        final float[][][] gw;
        final float[][] gb;
        final float[][][] mw;
        final float[][][] vw;
        final float[][] mb;
        final float[][] vb;
        int t;

        Adam(float[][][] w, float[][] b) {
            this.w = w;
            this.b = b;
            gw = bos(w);
            mw = bos(w);
            vw = bos(w);
            gb = bos(b);
            mb = bos(b);
            vb = bos(b);
        }

        private static float[][][] bos(float[][][] a) {
            float[][][] k = new float[a.length][][];
            for (int i = 0; i < a.length; i++) k[i] = bos(a[i]);
            return k;
        }

        private static float[][] bos(float[][] a) {
            float[][] k = new float[a.length][];
            for (int i = 0; i < a.length; i++) k[i] = new float[a[i].length];
            return k;
        }

        void sifirla() {
            for (float[][] k : gw) for (float[] s : k) java.util.Arrays.fill(s, 0f);
            for (float[] s : gb) java.util.Arrays.fill(s, 0f);
        }

        void adim(int ornek, float oran, float azalma) {
            t++;
            float b1 = 0.9f, b2 = 0.999f;
            float d1 = 1 - (float) Math.pow(b1, t), d2 = 1 - (float) Math.pow(b2, t);
            for (int k = 0; k < w.length; k++) {
                for (int o = 0; o < w[k].length; o++) {
                    for (int i = 0; i < w[k][o].length; i++) {
                        float g = gw[k][o][i] / ornek + azalma * w[k][o][i];
                        mw[k][o][i] = b1 * mw[k][o][i] + (1 - b1) * g;
                        vw[k][o][i] = b2 * vw[k][o][i] + (1 - b2) * g * g;
                        w[k][o][i] -= oran * (mw[k][o][i] / d1) / ((float) Math.sqrt(vw[k][o][i] / d2) + 1e-8f);
                    }
                    float g = gb[k][o] / ornek;
                    mb[k][o] = b1 * mb[k][o] + (1 - b1) * g;
                    vb[k][o] = b2 * vb[k][o] + (1 - b2) * g * g;
                    b[k][o] -= oran * (mb[k][o] / d1) / ((float) Math.sqrt(vb[k][o] / d2) + 1e-8f);
                }
            }
        }
    }

    /** Sınıf merkezleri ve yarıçapları (örneklerin %95'ini kapsayan uzaklık). */
    private void sinirlariHesapla(float[][] xn, int[] y) {
        int k = etiketler.length;
        int boyut = ortalama.length;
        merkezler = new float[k][boyut];
        yaricaplar = new float[k];
        int[] adet = new int[k];
        for (int s = 0; s < xn.length; s++) {
            adet[y[s]]++;
            for (int i = 0; i < boyut; i++) merkezler[y[s]][i] += xn[s][i];
        }
        for (int c = 0; c < k; c++) for (int i = 0; i < boyut; i++) merkezler[c][i] /= Math.max(1, adet[c]);
        for (int c = 0; c < k; c++) {
            float[] uzakliklar = new float[adet[c]];
            int j = 0;
            for (int s = 0; s < xn.length; s++) {
                if (y[s] != c) continue;
                double d = 0;
                for (int i = 0; i < boyut; i++) d += (xn[s][i] - merkezler[c][i]) * (xn[s][i] - merkezler[c][i]);
                uzakliklar[j++] = (float) Math.sqrt(d);
            }
            java.util.Arrays.sort(uzakliklar);
            float r95 = uzakliklar.length == 0 ? 0f : uzakliklar[Math.min(uzakliklar.length - 1, (int) (uzakliklar.length * 0.95f))];
            // Çok az ya da çok benzer örnekte yarıçap sıfıra düşmesin.
            yaricaplar[c] = Math.max(r95, 0.25f * (float) Math.sqrt(boyut));
        }
    }

    static int enBuyukIndeks(float[] p) {
        int en = 0;
        for (int i = 1; i < p.length; i++) if (p[i] > p[en]) en = i;
        return en;
    }

    // ---------- JSON (bilgisayarla ortak biçim) ----------

    String json() throws JSONException {
        JSONObject j = new JSONObject();
        JSONArray et = new JSONArray();
        for (String e : etiketler) et.put(e);
        j.put("etiketler", et);
        j.put("cikis", siniflandirici ? "softmax" : "l2");
        j.put("ortalama", dizi(ortalama));
        j.put("sapma", dizi(sapma));
        JSONArray katmanlar = new JSONArray();
        for (int k = 0; k < agirlik.length; k++) {
            JSONObject kat = new JSONObject();
            JSONArray w = new JSONArray();
            for (float[] satir : agirlik[k]) w.put(dizi(satir));
            kat.put("agirlik", w);
            kat.put("esik", dizi(esik[k]));
            katmanlar.put(kat);
        }
        j.put("katmanlar", katmanlar);
        if (merkezler != null) {
            JSONArray m = new JSONArray();
            for (float[] satir : merkezler) m.put(dizi(satir));
            j.put("merkezler", m);
            j.put("yaricaplar", dizi(yaricaplar));
        }
        return j.toString();
    }

    static SinirAgi json(String metin) throws JSONException {
        JSONObject j = new JSONObject(metin);
        JSONArray et = j.getJSONArray("etiketler");
        String[] etiketler = new String[et.length()];
        for (int i = 0; i < etiketler.length; i++) etiketler[i] = et.getString(i);
        JSONArray katmanlar = j.getJSONArray("katmanlar");
        float[][][] w = new float[katmanlar.length()][][];
        float[][] b = new float[katmanlar.length()][];
        for (int k = 0; k < w.length; k++) {
            JSONObject kat = katmanlar.getJSONObject(k);
            JSONArray satirlar = kat.getJSONArray("agirlik");
            w[k] = new float[satirlar.length()][];
            for (int o = 0; o < w[k].length; o++) w[k][o] = dizi(satirlar.getJSONArray(o));
            b[k] = dizi(kat.getJSONArray("esik"));
        }
        SinirAgi ag = new SinirAgi(etiketler, !"l2".equals(j.optString("cikis", "softmax")),
            dizi(j.getJSONArray("ortalama")), dizi(j.getJSONArray("sapma")), w, b);
        if (j.has("merkezler")) {
            JSONArray m = j.getJSONArray("merkezler");
            ag.merkezler = new float[m.length()][];
            for (int i = 0; i < m.length(); i++) ag.merkezler[i] = dizi(m.getJSONArray(i));
            ag.yaricaplar = dizi(j.getJSONArray("yaricaplar"));
        }
        return ag;
    }

    private static JSONArray dizi(float[] d) throws JSONException {
        JSONArray a = new JSONArray();
        for (float v : d) a.put((double) v);
        return a;
    }

    private static float[] dizi(JSONArray a) throws JSONException {
        float[] d = new float[a.length()];
        for (int i = 0; i < d.length; i++) d[i] = (float) a.getDouble(i);
        return d;
    }
}
