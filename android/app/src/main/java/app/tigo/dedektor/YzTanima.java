package app.tigo.dedektor;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult;
import java.io.File;
import java.io.IOException;
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
 * Dosyalar YzDosyalari'nda, egzersiz sayımı EgzersizTakibi'nde.
 */
final class YzTanima {

    /** Sesli okunacak olaylar: tanınan kişi, eklenen harf, yeni hareket ya da nesne. */
    interface Olaylar {
        void soyle(String metin);
    }

    /** Bir tanımanın gösterilmesi için gereken olasılık. */
    static final float ESIK = 0.7f;
    static final long KAYIT_MS = 3000;
    private static final int GECMIS = 5;
    private static final long KISI_ARALIGI_MS = 400;
    private static final long NESNE_ARALIGI_MS = 300;
    private static final long KAYIT_ARALIGI_MS = 250;

    final YzDosyalari dosyalar;
    final YzVeri hareketVerisi;
    final YzVeri tidVerisi;
    final YzVeri kisiVerisi;
    final YzVeri nesneVerisi;
    final EgzersizTakibi egzersiz;
    volatile Olaylar olaylar;

    private final Context baglam;
    private final SinirAgi duyguModeli;
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
    private final ArrayDeque<String> nesneGecmisi = new ArrayDeque<>();
    private final ArrayDeque<String> ozelGecmisi = new ArrayDeque<>();
    private volatile String nesneYazisi;
    private volatile String ozelNesne;
    private volatile long sonNesneImzasi;
    /** En son okunan (aynısı tekrar okunmasın; kaybolunca sıfırlanır). */
    private String okunanHareket;
    private String okunanKisi;
    private String okunanNesne;

    YzTanima(Context baglam, EgzersizGecmisi gecmis) {
        this.baglam = baglam.getApplicationContext();
        dosyalar = new YzDosyalari(baglam);
        File klasor = dosyalar.klasor;
        hareketVerisi = new YzVeri(new File(klasor, "hareket.veri"), YzOzellik.EL_BOYUTU);
        tidVerisi = new YzVeri(new File(klasor, "tid.veri"), YzOzellik.PENCERE_BOYUTU);
        kisiVerisi = new YzVeri(dosyalar.surumluDosya("kisiler", dosyalar.kisiSurumu()), KisiTanima.IMZA_BOYUTU);
        nesneVerisi = new YzVeri(dosyalar.surumluDosya("nesneler", NesneTanima.IMZA_SURUMU), NesneTanima.IMZA_BOYUTU);
        nesne = new NesneTanima(baglam);
        duyguModeli = dosyalar.hazirModel("yz/duygu.json");
        egzersiz = new EgzersizTakibi(dosyalar.hazirModel("yz/egzersiz.json"), gecmis);
        hareketModeli = dosyalar.kayitliModel(Ayarlar.Yz.HAREKET);
        tidModeli = dosyalar.kayitliModel(Ayarlar.Yz.TID);
    }

    private void soyle(String metin) {
        Olaylar o = olaylar;
        if (o != null && metin != null) o.soyle(metin);
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
        if (ilk != null && !ilk.equals(okunanHareket) && !kayit) soyle(ilk);
        okunanHareket = ilk;
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
        if (tid.sonuc(harf)) soyle(harf);
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
                okunanKisi = null;
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
        String selam = null;
        synchronized (this) {
            kisiGecmisi.addLast(ad == null ? "" : ad);
            while (kisiGecmisi.size() > 3) kisiGecmisi.removeFirst();
            String c = cogunluk(kisiGecmisi);
            kisiEtiketi = c != null ? c : kisiVerisi.toplam() == 0 ? "Kayıtlı kişi yok" : "Tanınmadı";
            if (c != null && !c.equals(okunanKisi)) selam = "Merhaba " + c;
            if (c != null) okunanKisi = c;
        }
        soyle(selam);
    }

    /** Model ilk kullanımda açılır (LiteRT yüklemesi kısa sürer ama gereksizse hiç yapılmasın). */
    private synchronized KisiTanima kisiModeli() {
        if (!kisiDenendi) {
            kisiDenendi = true;
            kisi = KisiTanima.olustur(baglam);
        }
        return kisi;
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
        String okunacak = null;
        synchronized (this) {
            nesneGecmisi.addLast(ana == null ? "" : ana.elde ? "Elinde: " + ana.ad : ana.ad);
            while (nesneGecmisi.size() > GECMIS) nesneGecmisi.removeFirst();
            nesneYazisi = cogunluk(nesneGecmisi);
            if (nesneVerisi.toplam() == 0) ozelNesne = null;
            String yeni = kayit ? null : nesneYazisi();
            if (yeni != null && !yeni.equals(okunanNesne)) okunacak = yeni.replace("Elinde: ", "Elinde ");
            okunanNesne = yeni;
        }
        soyle(okunacak);
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
        dosyalar.modeliKaydet(tur, s.model);
        model(tur, s.model);
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
        dosyalar.modeliKaydet(tur, m);
        model(tur, m);
    }

    void modeliSil(Ayarlar.Yz tur) {
        dosyalar.modeliSil(tur);
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

    private synchronized void model(Ayarlar.Yz tur, SinirAgi m) {
        if (tur == Ayarlar.Yz.HAREKET) hareketModeli = m;
        else if (tur == Ayarlar.Yz.TID) tidModeli = m;
        elGecmisi.clear();
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
