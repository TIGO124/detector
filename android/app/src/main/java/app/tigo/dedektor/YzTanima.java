package app.tigo.dedektor;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult;
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
 * Yapay zekâ modu: kendi el hareketlerin, TİD harfleri, duygu, kişi, egzersiz ve elindeki nesne.
 * Hareket ve TİD modelleri "Öğret" ile kaydedilen örneklerden telefonda (ya da bilgisayarda)
 * eğitilir; duygu ve egzersiz modelleri bilgisayarda eğitilip APK'ya konmuştur. Sonuçlar
 * MediaPipe iş parçacıklarından gelir; ekranda gösterilecek olanlar burada tutulur.
 */
final class YzTanima {

    /** Bir tanımanın gösterilmesi için gereken olasılık. */
    static final float ESIK = 0.7f;
    static final long KAYIT_MS = 3000;
    private static final int GECMIS = 5;
    private static final long KISI_ARALIGI_MS = 400;
    private static final long NESNE_ARALIGI_MS = 300;
    private static final long KAYIT_ARALIGI_MS = 250;
    private static final String ETIKET = "Dedektor";

    final YzVeri hareketVerisi;
    final YzVeri tidVerisi;
    final YzVeri kisiVerisi;
    final YzVeri nesneVerisi;
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
    private final TidYazici tid = new TidYazici();
    private final NesneTanima nesne;
    private float[] duyguOrtalamasi;
    private final ArrayDeque<String> kisiGecmisi = new ArrayDeque<>();
    private volatile String kisiEtiketi;
    private volatile String hareketYazisi;
    private volatile String duyguYazisi;
    private volatile long sonKisi;
    private volatile boolean vucutGorunuyor;
    private final ArrayDeque<String> nesneGecmisi = new ArrayDeque<>();
    private final ArrayDeque<String> ozelGecmisi = new ArrayDeque<>();
    private volatile String nesneYazisi;
    private volatile String ozelNesne;
    private volatile long sonNesneImzasi;

    YzTanima(Context baglam) {
        this.baglam = baglam.getApplicationContext();
        klasor = new File(baglam.getFilesDir(), "yz");
        if (!klasor.isDirectory() && !klasor.mkdirs()) Log.w(ETIKET, "YZ klasörü oluşturulamadı");
        hareketVerisi = new YzVeri(new File(klasor, "hareket.veri"), YzOzellik.EL_BOYUTU);
        tidVerisi = new YzVeri(new File(klasor, "tid.veri"), YzOzellik.PENCERE_BOYUTU);
        kisiVerisi = new YzVeri(surumluDosya("kisiler", kisiSurumu()), KisiTanima.IMZA_BOYUTU);
        nesneVerisi = new YzVeri(surumluDosya("nesneler", NesneTanima.IMZA_SURUMU), NesneTanima.IMZA_BOYUTU);
        nesne = new NesneTanima(baglam);
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
            float[] x = YzOzellik.el(el.dunya, el.sol());
            if (kayit) ornekEkle(hareketVerisi, x);
            if (m == null) continue;
            ArrayDeque<String> g = elGecmisi.computeIfAbsent(el.taraf, k -> new ArrayDeque<>());
            g.addLast(etiket(m, x));
            while (g.size() > GECMIS) g.removeFirst();
            el.yzEtiketi = cogunluk(g);
        }
        if (eller.isEmpty()) elGecmisi.clear();
        String ilk = null;
        for (ElTakibi.ElDurumu el : eller) if (ilk == null) ilk = el.yzEtiketi;
        hareketYazisi = ilk;
    }

    /** Sonuç kartı için: ilk elde tanınan hareket, duygu, kişi (yoksa null). */
    String hareketYazisi() {
        return hareketYazisi;
    }

    String duyguYazisi() {
        return duyguYazisi;
    }

    String kisiYazisi() {
        if (kayitta(Ayarlar.Yz.KISI)) return "Kaydediliyor: " + kayitEtiketi;
        return kisiEtiketi;
    }

    private void tid(List<ElTakibi.ElDurumu> eller, long zaman) {
        float[] x = tid.kare(eller, zaman);
        if (x == null) return;
        if (kayitta(Ayarlar.Yz.TID)) ornekEkle(tidVerisi, x);
        SinirAgi m = tidModeli;
        String harf = m == null ? "" : etiket(m, x);
        tid.sonuc(harf);
        if (!harf.isEmpty()) for (ElTakibi.ElDurumu el : eller) el.yzEtiketi = harf;
    }

    String tidMetni() {
        return tid.metin();
    }

    void tidDuzenle(String islem) {
        tid.duzenle(islem);
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
            duyguYazisi = null;
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
        duyguYazisi = duyguModeli.etiketler[i] + " %" + Math.round(100 * duyguOrtalamasi[i]);
        return duyguYazisi;
    }

    private String kisi(List<NormalizedLandmark> noktalar, Bitmap resim) {
        KisiTanima k = kisiModeli();
        if (k == null) return "Kişi modeli açılamadı";
        long simdi = SystemClock.uptimeMillis();
        long aralik = kayitta(Ayarlar.Yz.KISI) ? KAYIT_ARALIGI_MS : KISI_ARALIGI_MS;
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

    // ---------- Elindeki nesne ----------

    /**
     * Nesne algılayıcının sonucu (kendi iş parçacığında): ekranda çizilecek nesneler. Öğretilen
     * nesne varsa (ya da kaydediliyorsa) çerçevenin imzası da arada bir çıkarılır.
     */
    List<NesneTanima.Nesne> nesne(ObjectDetectorResult s, Bitmap kare, List<ElTakibi.ElDurumu> eller) {
        int g = kare.getWidth();
        int y = kare.getHeight();
        List<NesneTanima.Nesne> liste = NesneTanima.nesneler(s, g, y, eller);
        NesneTanima.Nesne ana = NesneTanima.anaNesne(liste, NesneTanima.cerceve(g, y));
        boolean kayit = kayitta(Ayarlar.Yz.NESNE);
        long simdi = SystemClock.uptimeMillis();
        if ((kayit || nesneVerisi.toplam() > 0) && simdi - sonNesneImzasi >= (kayit ? KAYIT_ARALIGI_MS : NESNE_ARALIGI_MS)
            && nesne.imzaCikar(kare, this::nesneImzasi)) {
            sonNesneImzasi = simdi;
        }
        synchronized (this) {
            nesneGecmisi.addLast(ana == null ? "" : ana.elde ? "Elinde: " + ana.ad : ana.ad);
            while (nesneGecmisi.size() > GECMIS) nesneGecmisi.removeFirst();
            nesneYazisi = cogunluk(nesneGecmisi);
            if (nesneVerisi.toplam() == 0) ozelNesne = null;
        }
        return liste;
    }

    private void nesneImzasi(float[] imza) {
        if (kayitta(Ayarlar.Yz.NESNE)) {
            ornekEkle(nesneVerisi, imza);
            return;
        }
        KisiTanima.Eslesme e = KisiTanima.enBenzer(imza, nesneVerisi.hepsi());
        boolean tanindi = e != null && e.benzerlik >= NesneTanima.ESIK && !NesneTanima.BOS.equalsIgnoreCase(e.ad);
        synchronized (this) {
            ozelGecmisi.addLast(tanindi ? e.ad : "");
            while (ozelGecmisi.size() > 3) ozelGecmisi.removeFirst();
            ozelNesne = cogunluk(ozelGecmisi);
        }
    }

    /** Büyük yazı: öğretilen nesne, yoksa eldeki (ya da çerçevedeki) hazır nesne; yoksa null. */
    String nesneYazisi() {
        if (kayitta(Ayarlar.Yz.NESNE)) return "Kaydediliyor: " + kayitEtiketi;
        String o = ozelNesne;
        return o != null ? o : nesneYazisi;
    }

    // ---------- Öğret: kayıt, eğitim, modeller ----------

    YzVeri veri(Ayarlar.Yz tur) {
        switch (tur) {
            case HAREKET: return hareketVerisi;
            case TID: return tidVerisi;
            case KISI: return kisiVerisi;
            case NESNE: return nesneVerisi;
            default: return null;
        }
    }

    void kayitBaslat(Ayarlar.Yz tur, String etiket) {
        kayitSayisi.set(0);
        kayitEtiketi = etiket;
        kayitBitisi = SystemClock.uptimeMillis() + KAYIT_MS;
        kayitTuru = tur;
    }

    /** Süren kaydı hemen bitirir (Öğret yaprağı kapandı); kalan iş kayitBitir'de. */
    void kaydiKes() {
        kayitBitisi = SystemClock.uptimeMillis();
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

    long kayitKalanMs() {
        return Math.max(0, kayitBitisi - SystemClock.uptimeMillis());
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
        SinirAgi m;
        try {
            m = SinirAgi.json(json);
        } catch (JSONException | RuntimeException e) {
            throw new IllegalArgumentException("Bu dosya bir Dedektör modeli değil.", e);
        }
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

    private String kisiSurumu() {
        try {
            return Integer.toHexString(KisiTanima.oku(baglam, "yz/kimlik.json").hashCode());
        } catch (IOException e) {
            Log.w(ETIKET, "Kişi modeli bilgisi okunamadı", e);
            return "0";
        }
    }

    /**
     * İmzalar (yüz, nesne) yalnızca onları üreten modelle karşılaştırılabilir: kayıt dosyasının
     * adı modele bağlıdır. Model güncellenince eski kayıtlar silinir (yeniden eklenir).
     */
    private File surumluDosya(String onEk, String surum) {
        String ad = onEk + "_" + surum + ".veri";
        File[] eskiler = klasor.listFiles((k, a) -> a.startsWith(onEk) && a.endsWith(".veri") && !a.equals(ad));
        if (eskiler != null) for (File f : eskiler) if (!f.delete()) Log.w(ETIKET, "Eski kayıt silinemedi: " + f.getName());
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

    void kapat() {
        KisiTanima k;
        synchronized (this) {
            k = kisi;
        }
        if (k != null) k.kapat();
        nesne.kapat();
    }
}
