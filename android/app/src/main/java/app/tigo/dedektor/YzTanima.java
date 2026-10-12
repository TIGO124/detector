package app.tigo.dedektor;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONException;

/**
 * Yapay zekâ modu: kendi el hareketlerin, TİD harfleri, duygu, kişi ve egzersiz tanıma.
 * Hareket ve TİD modelleri "Öğret" ile kaydedilen örneklerden telefonda (ya da bilgisayarda)
 * eğitilir; duygu ve egzersiz modelleri bilgisayarda eğitilip APK'ya konmuştur. Sonuçlar
 * MediaPipe iş parçacıklarından gelir; ekranda gösterilecek olanlar burada tutulur.
 */
final class YzTanima {

    /** Bir tanımanın gösterilmesi için gereken olasılık. */
    static final float ESIK = 0.7f;
    static final long KAYIT_MS = 3000;
    private static final int GECMIS = 5;
    /** Harf bu kadar kare sabit kalınca yazıya eklenir. */
    private static final int TID_SABIT = 8;
    /** Bu kadar kare harf görülmezse aynı harf yeniden eklenebilir. */
    private static final int TID_BOS = 5;
    private static final int TID_EN_UZUN = 60;
    private static final long KISI_ARALIGI_MS = 400;
    private static final long KISI_KAYIT_ARALIGI_MS = 250;
    private static final String ETIKET = "Dedektor";

    final YzVeri hareketVerisi;
    final YzVeri tidVerisi;
    final YzVeri kisiVerisi;
    final EgzersizSayaci sayac = new EgzersizSayaci();
    /** Egzersizde bir tekrar tamamlanınca çağrılır (vücut modelinin iş parçacığından). */
    volatile Runnable tekrarOldu;

    private final Context baglam;
    private final File klasor;
    private final SinirAgi duyguModeli;
    private final SinirAgi egzersizModeli;
    private volatile SinirAgi hareketModeli;
    private volatile SinirAgi tidModeli;
    private KisiTanima kisi;
    private boolean kisiDenendi;

    private volatile Ayarlar.Yz kayitTuru;
    private volatile String kayitEtiketi;
    private volatile long kayitBitisi;
    private final AtomicInteger kayitSayisi = new AtomicInteger();

    private final Map<String, ArrayDeque<String>> elGecmisi = new HashMap<>();
    private final ArrayDeque<TidKaresi> tidKareleri = new ArrayDeque<>();
    private final StringBuilder tidMetni = new StringBuilder();
    private String tidAday = "";
    private int tidSabit;
    private String tidSonEklenen = "";
    private float[] duyguOrtalamasi;
    private final ArrayDeque<String> kisiGecmisi = new ArrayDeque<>();
    private volatile String kisiEtiketi;
    private volatile long sonKisi;
    private volatile boolean vucutGorunuyor;

    private static final class TidKaresi {
        final long zaman;
        final float[] oz;
        final float[] bilek;

        TidKaresi(long zaman, float[] oz, float[] bilek) {
            this.zaman = zaman;
            this.oz = oz;
            this.bilek = bilek;
        }
    }

    YzTanima(Context baglam) {
        this.baglam = baglam.getApplicationContext();
        klasor = new File(baglam.getFilesDir(), "yz");
        if (!klasor.isDirectory() && !klasor.mkdirs()) Log.w(ETIKET, "YZ klasörü oluşturulamadı");
        hareketVerisi = new YzVeri(new File(klasor, "hareket.veri"), YzOzellik.EL_BOYUTU);
        tidVerisi = new YzVeri(new File(klasor, "tid.veri"), YzOzellik.PENCERE_BOYUTU);
        kisiVerisi = new YzVeri(kisiDosyasi(), KisiTanima.IMZA_BOYUTU);
        duyguModeli = hazirModel("yz/duygu.json");
        egzersizModeli = hazirModel("yz/egzersiz.json");
        hareketModeli = kayitliModel(Ayarlar.Yz.HAREKET);
        tidModeli = kayitliModel(Ayarlar.Yz.TID);
    }

    // ---------- El: kendi hareketlerin ve TİD ----------

    /** El modelinin sonucu; tanınan hareket ya da harf her elin yzEtiketi'ne yazılır. */
    synchronized void el(List<ElTakibi.ElDurumu> eller, Ayarlar.Yz tur, long zaman) {
        if (tur == Ayarlar.Yz.HAREKET) hareket(eller);
        else if (tur == Ayarlar.Yz.TID) tid(eller, zaman);
    }

    private void hareket(List<ElTakibi.ElDurumu> eller) {
        SinirAgi m = hareketModeli;
        boolean kayit = kayitta(Ayarlar.Yz.HAREKET);
        for (ElTakibi.ElDurumu el : eller) {
            if (el.dunya == null) continue;
            float[] x = YzOzellik.el(el.dunya, sol(el));
            if (kayit) ornekEkle(hareketVerisi, x);
            if (m == null) continue;
            ArrayDeque<String> g = elGecmisi.computeIfAbsent(el.taraf, k -> new ArrayDeque<>());
            g.addLast(etiket(m, x));
            while (g.size() > GECMIS) g.removeFirst();
            el.yzEtiketi = cogunluk(g);
        }
        if (eller.isEmpty()) elGecmisi.clear();
    }

    private void tid(List<ElTakibi.ElDurumu> eller, long zaman) {
        ElTakibi.ElDurumu sol = null;
        ElTakibi.ElDurumu sag = null;
        for (ElTakibi.ElDurumu el : eller) {
            if (el.dunya == null) continue;
            if (sol(el)) {
                if (sol == null) sol = el;
            } else if (sag == null) {
                sag = el;
            }
        }
        float[] bilek = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        float[] solBilek = null;
        float[] sagBilek = null;
        if (sol != null) solBilek = new float[] {bilek[0] = sol.noktalar.get(0).x(), bilek[1] = sol.noktalar.get(0).y()};
        if (sag != null) sagBilek = new float[] {bilek[2] = sag.noktalar.get(0).x(), bilek[3] = sag.noktalar.get(0).y()};
        float[] oz = YzOzellik.ikiEl(sol == null ? null : sol.dunya, solBilek, sag == null ? null : sag.dunya, sagBilek);
        tidKareleri.addLast(new TidKaresi(zaman, oz, bilek));
        while (!tidKareleri.isEmpty() && zaman - tidKareleri.peekFirst().zaman > 2 * YzOzellik.PENCERE_MS) tidKareleri.removeFirst();
        if (sol == null && sag == null) {
            tidSonucu("");
            return;
        }
        float[] x = pencere(zaman);
        if (x == null) return;
        if (kayitta(Ayarlar.Yz.TID)) ornekEkle(tidVerisi, x);
        SinirAgi m = tidModeli;
        String harf = m == null ? "" : etiket(m, x);
        tidSonucu(harf);
        if (!harf.isEmpty()) for (ElTakibi.ElDurumu el : eller) el.yzEtiketi = harf;
    }

    /** Son PENCERE_MS'den eşit aralıklı PENCERE kare; yeterince kare yoksa null. */
    private float[] pencere(long zaman) {
        TidKaresi ilk = tidKareleri.peekFirst();
        if (ilk == null || zaman - ilk.zaman < YzOzellik.PENCERE_MS * 2 / 3) return null;
        float[][] oz = new float[YzOzellik.PENCERE][];
        float[][] bilek = new float[YzOzellik.PENCERE][];
        for (int k = 0; k < YzOzellik.PENCERE; k++) {
            long hedef = zaman - YzOzellik.PENCERE_MS + k * YzOzellik.PENCERE_MS / (YzOzellik.PENCERE - 1);
            TidKaresi en = ilk;
            for (TidKaresi t : tidKareleri) if (Math.abs(t.zaman - hedef) < Math.abs(en.zaman - hedef)) en = t;
            oz[k] = en.oz;
            bilek[k] = en.bilek;
        }
        return YzOzellik.pencere(oz, bilek);
    }

    /** Harf bir süre sabit kalınca yazıya eklenir; aynı harfi tekrar yazmak için el kısa süre indirilir. */
    private void tidSonucu(String harf) {
        if (harf.equals(tidAday)) tidSabit++;
        else {
            tidAday = harf;
            tidSabit = 1;
        }
        if (harf.isEmpty()) {
            if (tidSabit >= TID_BOS) tidSonEklenen = "";
        } else if (tidSabit == TID_SABIT && !harf.equals(tidSonEklenen)) {
            tidMetni.append(harf.length() > 1 ? harf + " " : harf);
            if (tidMetni.length() > TID_EN_UZUN) tidMetni.delete(0, tidMetni.length() - TID_EN_UZUN);
            tidSonEklenen = harf;
        }
    }

    synchronized String tidMetni() {
        return tidMetni.toString();
    }

    synchronized void tidDuzenle(String islem) {
        if ("bosluk".equals(islem)) tidMetni.append(' ');
        else if ("sil".equals(islem) && tidMetni.length() > 0) tidMetni.setLength(tidMetni.length() - 1);
        else if ("temizle".equals(islem)) tidMetni.setLength(0);
    }

    // ---------- Yüz: duygu ve kişi ----------

    /**
     * Yüz modelinin sonucu; yüzün altında gösterilecek yazı (yoksa null). Resim yalnızca kişi
     * tanımada ve bu çağrı sırasında okunur.
     */
    String yuz(List<NormalizedLandmark> noktalar, List<Category> puanlar, Bitmap resim, Ayarlar.Yz tur) {
        if (noktalar == null) {
            synchronized (this) {
                duyguOrtalamasi = null;
                kisiGecmisi.clear();
            }
            kisiEtiketi = null;
            return null;
        }
        if (tur == Ayarlar.Yz.DUYGU) return duygu(puanlar);
        if (tur == Ayarlar.Yz.KISI) return kisi(noktalar, resim);
        return null;
    }

    private synchronized String duygu(List<Category> puanlar) {
        if (duyguModeli == null || puanlar == null) return null;
        float[] p = duyguModeli.calistir(YzOzellik.yuz(puanlar));
        if (duyguOrtalamasi == null) duyguOrtalamasi = p;
        else for (int i = 0; i < p.length; i++) duyguOrtalamasi[i] = duyguOrtalamasi[i] * 0.7f + p[i] * 0.3f;
        int i = SinirAgi.enBuyukIndeks(duyguOrtalamasi);
        return duyguModeli.etiketler[i] + " %" + Math.round(100 * duyguOrtalamasi[i]);
    }

    private String kisi(List<NormalizedLandmark> noktalar, Bitmap resim) {
        KisiTanima k = kisiModeli();
        if (k == null) return "Kişi modeli açılamadı";
        long simdi = SystemClock.uptimeMillis();
        long aralik = kayitta(Ayarlar.Yz.KISI) ? KISI_KAYIT_ARALIGI_MS : KISI_ARALIGI_MS;
        if (resim != null && simdi - sonKisi >= aralik && k.imzaCikar(noktalar, resim, this::kisiImzasi)) sonKisi = simdi;
        if (kayitta(Ayarlar.Yz.KISI)) return "Kaydediliyor: " + kayitEtiketi;
        return kisiEtiketi;
    }

    private void kisiImzasi(float[] imza) {
        if (kayitta(Ayarlar.Yz.KISI)) {
            ornekEkle(kisiVerisi, imza);
            return;
        }
        KisiTanima k = kisiModeli();
        String ad = k == null ? null : k.tani(imza, kisiVerisi.hepsi());
        synchronized (this) {
            kisiGecmisi.addLast(ad == null ? "" : ad);
            while (kisiGecmisi.size() > 3) kisiGecmisi.removeFirst();
            String c = cogunluk(kisiGecmisi);
            kisiEtiketi = c != null ? c : kisiVerisi.toplam() == 0 ? "Kayıtlı kişi yok" : "Tanınmadı";
        }
    }

    /** Model ilk kullanımda açılır (LiteRT yüklemesi kısa sürer ama gereksizse hiç yapılmasın). */
    private synchronized KisiTanima kisiModeli() {
        if (!kisiDenendi) {
            kisiDenendi = true;
            kisi = KisiTanima.olustur(baglam);
        }
        return kisi;
    }

    // ---------- Vücut: egzersiz ----------

    /** Vücut modelinin sonucu: duruşu sınıflandırır ve tekrarları sayar. */
    void vucut(PoseLandmarkerResult s) {
        if (egzersizModeli == null || s.worldLandmarks().isEmpty() || s.landmarks().isEmpty()) {
            sayac.guncelle(null, 0f);
            vucutGorunuyor = false;
            return;
        }
        // Bacaklar görünmüyorsa duruş güvenilir değil (ör. yalnızca üst beden kadrajda).
        List<NormalizedLandmark> n = s.landmarks().get(0);
        vucutGorunuyor = n.size() > 28 && Math.max(gorunurluk(n.get(27)), gorunurluk(n.get(28))) >= 0.5f;
        if (!vucutGorunuyor) {
            sayac.guncelle(null, 0f);
            return;
        }
        float[] p = egzersizModeli.calistir(YzOzellik.vucut(YzOzellik.noktalar(s.worldLandmarks().get(0))));
        int i = SinirAgi.enBuyukIndeks(p);
        Runnable r = tekrarOldu;
        if (sayac.guncelle(egzersizModeli.etiketler[i], p[i]) && r != null) r.run();
    }

    /** Egzersiz ekranındaki küçük yazı: o anki duruş ya da neden sayılamadığı. */
    String egzersizDurumu() {
        if (!vucutGorunuyor) return "Bütün vücudun görünmüyor";
        String d = sayac.durum();
        return d == null ? "Duruş belirsiz" : d;
    }

    private static float gorunurluk(NormalizedLandmark l) {
        return l.visibility().orElse(1f);
    }

    // ---------- Öğret: kayıt, eğitim, modeller ----------

    YzVeri veri(Ayarlar.Yz tur) {
        switch (tur) {
            case HAREKET: return hareketVerisi;
            case TID: return tidVerisi;
            case KISI: return kisiVerisi;
            default: return null;
        }
    }

    void kayitBaslat(Ayarlar.Yz tur, String etiket) {
        kayitSayisi.set(0);
        kayitEtiketi = etiket;
        kayitBitisi = SystemClock.uptimeMillis() + KAYIT_MS;
        kayitTuru = tur;
    }

    /** Kaydı bitirir ve örnekleri dosyaya yazar; kaç örnek alındığını verir. Arka planda çağrılmalı. */
    int kayitBitir() throws IOException {
        Ayarlar.Yz tur = kayitTuru;
        kayitTuru = null;
        if (tur != null) veri(tur).kaydet();
        return kayitSayisi.get();
    }

    boolean kayitta(Ayarlar.Yz tur) {
        return kayitTuru == tur && SystemClock.uptimeMillis() < kayitBitisi;
    }

    int kayitSayisi() {
        return kayitSayisi.get();
    }

    private void ornekEkle(YzVeri v, float[] x) {
        String e = kayitEtiketi;
        if (e == null) return;
        v.ekle(e, x);
        kayitSayisi.incrementAndGet();
    }

    /** Öğretilen örneklerden modeli telefonda eğitir ve kaydeder. Arka planda çağrılmalı. */
    SinirAgi.Sonuc egit(Ayarlar.Yz tur, SinirAgi.Ilerleme ilerleme) throws IOException, JSONException {
        YzVeri.Egitim e = veri(tur).egitimVerisi();
        if (e.etiketler.length < 2) throw new IllegalStateException("En az iki farklı şey öğretmelisin.");
        int[] gizli = tur == Ayarlar.Yz.TID ? new int[] {64} : new int[] {32};
        SinirAgi.Sonuc s = SinirAgi.egit(e.x, e.y(), e.etiketler, gizli, 60, System.nanoTime(), ilerleme);
        modeliKaydet(tur, s.model);
        return s;
    }

    /** Bilgisayarda eğitilmiş modeli (JSON) yükler; girdi boyutu bu türe uymuyorsa hata verir. */
    void modeliYukle(Ayarlar.Yz tur, String json) throws IOException, JSONException {
        SinirAgi m = SinirAgi.json(json);
        int beklenen = veri(tur).boyut;
        if (m.girdiBoyutu() != beklenen || !m.siniflandirici) {
            throw new IllegalArgumentException("Bu model bu bölüm için değil (girdi " + m.girdiBoyutu() + ", beklenen " + beklenen + ").");
        }
        modeliKaydet(tur, m);
    }

    void modeliSil(Ayarlar.Yz tur) {
        if (!modelDosyasi(tur).delete()) Log.w(ETIKET, "Model dosyası silinemedi");
        model(tur, null);
    }

    boolean modelVar(Ayarlar.Yz tur) {
        return tur == Ayarlar.Yz.HAREKET ? hareketModeli != null : tur == Ayarlar.Yz.TID && tidModeli != null;
    }

    /** Bilgisayarda eğitmek için örnek dosyası (egitim/kisisel.py okur). Arka planda çağrılmalı. */
    File disaAktar(Ayarlar.Yz tur) throws IOException {
        YzVeri v = veri(tur);
        v.kaydet();
        return v.dosya();
    }

    private void modeliKaydet(Ayarlar.Yz tur, SinirAgi m) throws IOException, JSONException {
        File hedef = modelDosyasi(tur);
        File gecici = new File(hedef.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(gecici), StandardCharsets.UTF_8)) {
            w.write(m.json());
        }
        if (hedef.exists() && !hedef.delete()) throw new IOException("Eski model silinemedi");
        if (!gecici.renameTo(hedef)) throw new IOException("Model kaydedilemedi");
        model(tur, m);
    }

    private synchronized void model(Ayarlar.Yz tur, SinirAgi m) {
        if (tur == Ayarlar.Yz.HAREKET) hareketModeli = m;
        else if (tur == Ayarlar.Yz.TID) tidModeli = m;
        elGecmisi.clear();
    }

    private File modelDosyasi(Ayarlar.Yz tur) {
        return new File(klasor, (tur == Ayarlar.Yz.TID ? "tid" : "hareket") + ".json");
    }

    private SinirAgi kayitliModel(Ayarlar.Yz tur) {
        File f = modelDosyasi(tur);
        if (!f.exists()) return null;
        try {
            return SinirAgi.json(dosyaOku(f));
        } catch (IOException | JSONException | RuntimeException e) {
            Log.e(ETIKET, "Kayıtlı model okunamadı: " + f.getName(), e);
            return null;
        }
    }

    /** java.nio.file Android 8'den önce yok. */
    static String dosyaOku(File f) throws IOException {
        try (InputStream g = new FileInputStream(f)) {
            return metin(g);
        }
    }

    /** Akışın tamamı, UTF-8 (InputStream.readAllBytes Android 13'ten önce yok). */
    static String metin(InputStream g) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] tampon = new byte[16384];
        for (int n; (n = g.read(tampon)) > 0; ) o.write(tampon, 0, n);
        return new String(o.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Yüz imzaları yalnızca onları üreten modelle karşılaştırılabilir: kayıt dosyasının adı modele
     * bağlıdır. Model güncellenince eski kayıtlar silinir (kişiler yeniden eklenir).
     */
    private File kisiDosyasi() {
        String surum = "0";
        try {
            surum = Integer.toHexString(KisiTanima.oku(baglam, "yz/kimlik.json").hashCode());
        } catch (IOException e) {
            Log.w(ETIKET, "Kişi modeli bilgisi okunamadı", e);
        }
        String ad = "kisiler_" + surum + ".veri";
        File[] eskiler = klasor.listFiles((k, a) -> a.startsWith("kisiler") && a.endsWith(".veri") && !a.equals(ad));
        if (eskiler != null) for (File f : eskiler) if (!f.delete()) Log.w(ETIKET, "Eski kişi kaydı silinemedi");
        return new File(klasor, ad);
    }

    private SinirAgi hazirModel(String yol) {
        try {
            return SinirAgi.json(KisiTanima.oku(baglam, yol));
        } catch (IOException | JSONException e) {
            Log.e(ETIKET, "Hazır model okunamadı: " + yol, e);
            return null;
        }
    }

    // ---------- Yardımcılar ----------

    /** En olası etiket; olasılık düşükse ya da girdi öğretilenlere benzemiyorsa boş. */
    private static String etiket(SinirAgi m, float[] x) {
        float[] p = m.calistir(x);
        int i = SinirAgi.enBuyukIndeks(p);
        return p[i] >= ESIK && m.taniniyor(x, i) ? m.etiketler[i] : "";
    }

    /** Geçmişin çoğunluğu (boş olmayan); çoğunluk yoksa null. */
    private static String cogunluk(ArrayDeque<String> g) {
        Map<String, Integer> sayac = new HashMap<>();
        for (String s : g) if (!s.isEmpty()) sayac.merge(s, 1, Integer::sum);
        for (Map.Entry<String, Integer> e : sayac.entrySet()) if (e.getValue() * 2 > g.size()) return e.getKey();
        return null;
    }

    private static boolean sol(ElTakibi.ElDurumu el) {
        return "Sol el".equals(el.taraf);
    }

    void kapat() {
        KisiTanima k;
        synchronized (this) {
            k = kisi;
        }
        if (k != null) k.kapat();
    }
}
