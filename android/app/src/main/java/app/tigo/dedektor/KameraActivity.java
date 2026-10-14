package app.tigo.dedektor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.mediapipe.framework.image.BitmapExtractor;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kamera ve algılama ekranı. Modlar: Normal (algılama), Çizim (havada çizim), Efekt (yüz
 * efektleri), Oyun (dört oyun), YZ (öğretilen hareketler, TİD, duygu, kişi, egzersiz, nesne).
 * Modeller Algilayici'da native çalışır; kamera KameraKontrol'de, çekim CekimKontrol'de,
 * paneller kendi sınıflarında. Bu sınıf onları bağlar ve model sonuçlarını dağıtır.
 */
public class KameraActivity extends AppCompatActivity implements Algilayici.Dinleyici, AyarPaneli.Dinleyici {

    private static final String[] MOD_ADLARI = {"Normal", "Çizim", "Efekt", "Oyun", "YZ"};

    private CizimView cizim;
    private TextView yuzSayisi;
    private TextView elSayisi;
    private TextView fpsYazisi;
    private TextView ipucu;
    private TextView uykuUyarisi;
    private LinearLayout modlar;

    private Ayarlar ayarlar;
    private Algilayici algilayici;
    private YzTanima yz;
    private Geribildirim geribildirim;
    private SonucKarti kart;
    private Kutlama kutlama;
    private AyarPaneli ayarPaneli;
    private YzPaneli yzPaneli;
    private OyunPaneli oyunPaneli;
    private ModPaneli modPaneli;
    private KameraKontrol kamera;
    private CekimKontrol cekim;
    private YzYedek yedek;
    private final ArkaPlan arkaPlanIsleyici = new ArkaPlan();
    private final HavadaCizim havadaCizim = new HavadaCizim();
    private final ElTakibi elTakibi = new ElTakibi();
    private final IfadeAnalizi ifadeAnalizi = new IfadeAnalizi();
    private final YuzOlaylari yuzOlaylari = new YuzOlaylari();
    private final ExecutorService analiz = Executors.newSingleThreadExecutor();
    private volatile int sonYuzSayisi;
    private volatile int sonElSayisi;
    private volatile long sonDurumYazimi;
    private volatile List<ElTakibi.ElDurumu> sonEller = Collections.emptyList();
    private volatile boolean uykuGosteriliyor;

    private final ActivityResultLauncher<String> kameraIzni = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        verildi -> {
            if (verildi) kamera.baslat();
            else {
                // "Bir daha sorma" seçildiyse istek hiç sorulmadan reddedilir: nereden açılacağını söyle.
                boolean kalici = !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA);
                toast(kalici ? "Kamera izni kapalı. Telefonun Ayarlar › Uygulamalar › Dedektör › İzinler bölümünden açabilirsin."
                    : "Kamera izni verilmedi.");
                finish();
            }
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        // Egzersiz ve oyunlarda ekrana dokunulmaz: ekran kararmasın.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_kamera);

        cizim = findViewById(R.id.cizim);
        yuzSayisi = findViewById(R.id.yuz_sayisi);
        elSayisi = findViewById(R.id.el_sayisi);
        fpsYazisi = findViewById(R.id.fps);
        ipucu = findViewById(R.id.ipucu);
        uykuUyarisi = findViewById(R.id.uyku_uyarisi);
        modlar = findViewById(R.id.modlar);

        ayarlar = new Ayarlar(getSharedPreferences("kamera", MODE_PRIVATE));
        geribildirim = new Geribildirim(this, ayarlar, this::bildir);
        cizim.baglan(ayarlar, havadaCizim);
        algilayici = new Algilayici(this, this);
        algilayici.tasarruf = ayarlar.pilTasarrufu;
        yz = new YzTanima(this, EgzersizGecmisi.tercihlerle(getSharedPreferences("egzersiz", MODE_PRIVATE)));
        yz.olaylar = geribildirim::soyle;
        kart = new SonucKarti(this);
        kutlama = new Kutlama(cizim, findViewById(R.id.oyun_yazisi));
        yzPaneli = new YzPaneli(this, ayarlar, yz, kart, findViewById(R.id.geri_sayim), geribildirim, kutlama, this::modlariGoster);
        oyunPaneli = new OyunPaneli(this, ayarlar, kart, cizim, geribildirim, kutlama, this::modlariGoster);
        modPaneli = new ModPaneli(this, ayarlar, cizim, havadaCizim, yzPaneli, oyunPaneli);
        ayarPaneli = new AyarPaneli(this, ayarlar, this);
        cekim = new CekimKontrol(this, ayarlar, cizim, geribildirim);
        kamera = new KameraKontrol(this, ayarlar, cizim, analiz, algilayici::kareVer, havadaCizim::temizle);
        yedek = new YzYedek(this, yz.dosyalar.klasor, this::recreate);

        kenarBosluklariniAyarla();
        dugmeleriBagla();
        modlariGoster();
        durumuYaz();

        analiz.execute(() -> {
            ayarlar.gpu = algilayici.temelModelleriKur(ayarlar.gpu);
            if (ayarlar.vucutGerekli()) algilayici.vucutAyarla(true);
            if (ayarlar.arkaPlan != Ayarlar.ArkaPlan.KAPALI) algilayici.arkaPlanAyarla(true);
            yz.egzersiz.gecmis.temizle(EgzersizGecmisi.bugun());
            runOnUiThread(this::durumuYaz);
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) kamera.baslat();
        else kameraIzni.launch(Manifest.permission.CAMERA);

        // Geri: önce açık paneli kapatır.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (ayarPaneli.acik()) ayarPaneli.ac(false);
                else if (!yzPaneli.geri()) finish();
            }
        });
    }

    @Override
    protected void onPause() {
        // Video kaydedilir, geri sayım ve uyku alarmı durur.
        cekim.durdur();
        uykuyuGoster(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // Önce kamerayı ayır: kapanan iş parçacığına yeni kare gelip reddedilmesin.
        kamera.ayir();
        oyunPaneli.kapat();
        yzPaneli.kapat();
        kutlama.bitir();
        geribildirim.kapat();
        analiz.execute(() -> {
            algilayici.hepsiniKapat();
            yz.kapat();
        });
        analiz.shutdown();
        super.onDestroy();
    }

    /** Durum çubuğu ve gezinme çubuğu içeriğin üstüne binmesin. */
    private void kenarBosluklariniAyarla() {
        View ust = findViewById(R.id.ust);
        View alt = findViewById(R.id.alt);
        int ustBosluk = ust.getPaddingTop();
        int altBosluk = alt.getPaddingBottom();
        View[] paneller = {ayarPaneli.panel(), yzPaneli.ogret.panel()};
        int panelBosluk = paneller[0].getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.kok), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // Öğret panelinde ad yazılırken klavye paneli örtmesin.
            int klavye = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            ust.setPadding(ust.getPaddingLeft(), b.top + ustBosluk, ust.getPaddingRight(), ust.getPaddingBottom());
            alt.setPadding(alt.getPaddingLeft(), alt.getPaddingTop(), alt.getPaddingRight(), b.bottom + altBosluk);
            for (View p : paneller) p.setPadding(p.getPaddingLeft(), p.getPaddingTop(), p.getPaddingRight(), Math.max(b.bottom, klavye) + panelBosluk);
            return insets;
        });
    }

    private void dugmeleriBagla() {
        findViewById(R.id.geri).setOnClickListener(v -> finish());
        findViewById(R.id.cevir).setOnClickListener(v -> {
            cekim.durdur();
            kamera.cevir();
        });
        findViewById(R.id.ayarlar).setOnClickListener(v -> {
            boolean ac = !ayarPaneli.acik();
            if (ac) yzPaneli.geri();
            ayarPaneli.ac(ac);
        });
    }

    /** Modelleri CPU ya da GPU ile yeniden kurar; GPU olmazsa CPU'ya düşer. */
    private void hizlandiriciSec(boolean gpu) {
        analiz.execute(() -> {
            ayarlar.gpu = algilayici.temelModelleriKur(gpu);
            ayarlar.kaydet();
            runOnUiThread(() -> {
                durumuYaz();
                ayarPaneli.hizlandiriciyiGoster();
            });
        });
    }

    // ---------- Modlar ----------

    private void modlariGoster() {
        modlar.removeAllViews();
        Ayarlar.Mod[] tum = Ayarlar.Mod.values();
        for (int i = 0; i < tum.length; i++) {
            Ayarlar.Mod m = tum[i];
            modlar.addView(Cip.sekme(this, MOD_ADLARI[i], ayarlar.mod == m, ayarlar.renk, v -> modSec(m)));
        }
        modPaneli.goster();
        String metin = null;
        if (ayarlar.mod == Ayarlar.Mod.CIZIM) metin = "İşaret parmağınla çiz · Yumruğu tut: sil";
        else if (ayarlar.mod == Ayarlar.Mod.OYUN) metin = oyunPaneli.ipucu();
        else if (ayarlar.mod == Ayarlar.Mod.YZ) metin = yzPaneli.ipucu();
        ipucu.setText(metin);
        Cip.simge(ipucu, R.drawable.ic_bilgi, ayarlar.renk, 16);
        ipucu.setVisibility(metin == null ? View.GONE : View.VISIBLE);
        yzPaneli.goster(ayarlar.mod == Ayarlar.Mod.YZ);
        oyunPaneli.goster(ayarlar.mod == Ayarlar.Mod.OYUN);
        kart.goster(ayarlar.mod == Ayarlar.Mod.YZ || ayarlar.mod == Ayarlar.Mod.OYUN);
        if (ayarlar.mod != Ayarlar.Mod.EFEKT || ayarlar.efekt != Ayarlar.Efekt.PIKSEL) cizim.pikselGuncelle(null, null);
        modelleriAyarla();
        cizim.invalidate();
    }

    /** İsteğe bağlı modeller: vücut (ayarlardan ya da egzersizde) ve nesne; gerekmeyen kapatılır. */
    private void modelleriAyarla() {
        boolean vucut = ayarlar.vucutGerekli();
        boolean nesne = ayarlar.nesneGerekli();
        if (!vucut) cizim.vucutuTemizle();
        if (!nesne) cizim.nesneleriTemizle();
        analiz.execute(() -> {
            algilayici.vucutAyarla(vucut);
            algilayici.nesneAyarla(nesne);
        });
    }

    private void modSec(Ayarlar.Mod mod) {
        ayarlar.mod = mod;
        ayarlar.kaydet();
        modlariGoster();
    }

    // ---------- Model sonuçları (MediaPipe iş parçacıklarından) ----------

    @Override
    public void yuz(FaceLandmarkerResult sonuc, MPImage girdi) {
        List<List<Category>> puanlar = sonuc.faceBlendshapes().orElse(Collections.emptyList());
        long simdi = SystemClock.uptimeMillis();
        List<String> ifadeler = Collections.emptyList();
        if (ayarlar.mod == Ayarlar.Mod.YZ) {
            ifadeler = yzYuz(sonuc, puanlar, girdi);
        } else if (puanlar.isEmpty()) {
            ifadeAnalizi.sifirla();
        } else {
            ifadeler = ifadeAnalizi.guncelle(puanlar.get(0));
        }
        ifadeler = yuzOlaylari(sonuc, puanlar, ifadeler, simdi);
        if (ayarlar.mod == Ayarlar.Mod.EFEKT && ayarlar.efekt == Ayarlar.Efekt.PIKSEL) {
            float[] kutu = new float[4];
            Bitmap kucuk = sonuc.faceLandmarks().isEmpty() ? null
                : YuzEfektleri.pikselKaresi(BitmapExtractor.extract(girdi), sonuc.faceLandmarks().get(0), kutu);
            cizim.pikselGuncelle(kucuk, kutu);
        }
        cizim.yuzGuncelle(sonuc, ifadeler, girdi.getWidth(), girdi.getHeight());
        KlipKaydedici klip = cekim.klip();
        if (klip.kayitta()) klip.kare(BitmapExtractor.extract(girdi));
        sonYuzSayisi = sonuc.faceLandmarks().size();
        // Sayaçlar saniyede 4 kez yeterli; her karede yazmak üst çubuğu her karede yeniden ölçtürür.
        if (simdi - sonDurumYazimi >= 250) {
            sonDurumYazimi = simdi;
            runOnUiThread(this::durumuYaz);
        }
    }

    /** Yapay zekâ modunda yüzün altındaki yazı: duygu ya da tanınan kişi. */
    private List<String> yzYuz(FaceLandmarkerResult sonuc, List<List<Category>> puanlar, MPImage girdi) {
        boolean var = !sonuc.faceLandmarks().isEmpty();
        Bitmap resim = var && ayarlar.yz == Ayarlar.Yz.KISI ? BitmapExtractor.extract(girdi) : null;
        String yazi = yz.yuz(var ? sonuc.faceLandmarks().get(0) : null, puanlar.isEmpty() ? null : puanlar.get(0), resim, ayarlar.yz);
        return yazi == null ? Collections.emptyList() : Collections.singletonList(yazi);
    }

    /** Uyku uyarısı, gülümseyince çekim ve (Normal modda) baş hareketi etiketi. */
    private List<String> yuzOlaylari(FaceLandmarkerResult sonuc, List<List<Category>> puanlar, List<String> ifadeler, long simdi) {
        if (sonuc.faceLandmarks().isEmpty() || puanlar.isEmpty()) {
            yuzOlaylari.yuzYok();
            uykuyuGoster(false);
            cekim.yuz(0f, simdi);
            return ifadeler;
        }
        List<Category> p = puanlar.get(0);
        List<NormalizedLandmark> n = sonuc.faceLandmarks().get(0);
        YuzOlaylari.Bas bas = yuzOlaylari.guncelle(YuzOlaylari.gozKapali(p), YuzOlaylari.yaw(n), YuzOlaylari.pitch(n), simdi);
        uykuyuGoster(ayarlar.uykuUyarisi && yuzOlaylari.uyku());
        cekim.yuz(YuzOlaylari.gulumseme(p), simdi);
        if (ayarlar.mod != Ayarlar.Mod.NORMAL) return ifadeler;
        if (bas != null) geribildirim.soyle(bas == YuzOlaylari.Bas.EVET ? "Evet" : "Hayır");
        String etiket = yuzOlaylari.basEtiketi(simdi);
        if (etiket == null) return ifadeler;
        List<String> yeni = new ArrayList<>(ifadeler.size() + 1);
        yeni.add(etiket);
        yeni.addAll(ifadeler);
        return yeni;
    }

    private void uykuyuGoster(boolean uyku) {
        if (uyku == uykuGosteriliyor) return;
        uykuGosteriliyor = uyku;
        geribildirim.alarm(uyku);
        if (uyku) geribildirim.titret(800);
        runOnUiThread(() -> uykuUyarisi.setVisibility(uyku ? View.VISIBLE : View.GONE));
    }

    @Override
    public void el(GestureRecognizerResult sonuc, int genislik, int yukseklik) {
        List<ElTakibi.ElDurumu> eller = elTakibi.guncelle(ElTakibi.olcumler(sonuc));
        long simdi = SystemClock.uptimeMillis();
        if (ayarlar.mod == Ayarlar.Mod.YZ) yz.el(eller, ayarlar.yz, simdi);
        cizim.elGuncelle(eller, genislik, yukseklik);
        sonEller = eller;
        sonElSayisi = eller.size();
        if (ayarlar.mod == Ayarlar.Mod.CIZIM) cizimiGuncelle(eller);
        oyunPaneli.el(eller);
        cekim.el(eller, simdi);
    }

    @Override
    public void vucut(PoseLandmarkerResult sonuc, int genislik, int yukseklik) {
        if (ayarlar.vucutGerekli()) cizim.vucutGuncelle(sonuc, genislik, yukseklik);
        if (ayarlar.mod == Ayarlar.Mod.YZ && ayarlar.yz == Ayarlar.Yz.EGZERSIZ) yz.egzersiz.vucut(sonuc, SystemClock.uptimeMillis());
    }

    @Override
    public void arkaPlan(ImageSegmenterResult sonuc, MPImage girdi) {
        Ayarlar.ArkaPlan tur = ayarlar.arkaPlan;
        if (tur == Ayarlar.ArkaPlan.KAPALI) return;
        Bitmap katman = arkaPlanIsleyici.katman(sonuc, girdi, tur);
        if (katman != null) cizim.arkaPlanGuncelle(katman);
    }

    @Override
    public void nesne(ObjectDetectorResult sonuc, MPImage girdi) {
        if (!ayarlar.nesneGerekli()) return;
        cizim.nesneGuncelle(yz.nesne(sonuc, BitmapExtractor.extract(girdi), sonEller), girdi.getWidth(), girdi.getHeight());
    }

    @Override
    public void gpuCalismadi() {
        runOnUiThread(() -> {
            toast("GPU bu telefonda çalışmadı, CPU'ya geçildi.");
            hizlandiriciSec(false);
        });
    }

    @Override
    public void bildir(String mesaj) {
        runOnUiThread(() -> toast(mesaj));
    }

    /** İlk elin işaret parmağı ucu çizer; yumruk tutulursa çizim silinir. Durumlar sabitlenmiştir. */
    private void cizimiGuncelle(List<ElTakibi.ElDurumu> eller) {
        if (eller.isEmpty()) {
            havadaCizim.elYok();
            return;
        }
        ElTakibi.ElDurumu el = eller.get(0);
        NormalizedLandmark uc = el.noktalar.get(ElAnalizi.ISARET_UCU);
        havadaCizim.guncelle(uc.x(), uc.y(), el.isaret && !el.yumruk(), el.yumruk(), SystemClock.uptimeMillis());
    }

    // ---------- Ayarlar yaprağı ----------

    @Override
    public void renkDegisti() {
        modlariGoster();
        durumuYaz();
    }

    @Override
    public void vucutDegisti() {
        modelleriAyarla();
    }

    @Override
    public void arkaPlanDegisti(boolean acik) {
        if (!acik) cizim.arkaPlaniTemizle();
        analiz.execute(() -> algilayici.arkaPlanAyarla(acik));
    }

    @Override
    public void hizlandiriciSecildi(boolean gpu) {
        hizlandiriciSec(gpu);
    }

    @Override
    public void ayarDegisti() {
        algilayici.tasarruf = ayarlar.pilTasarrufu;
        if (!ayarlar.uykuUyarisi) uykuyuGoster(false);
        cizim.invalidate();
    }

    @Override
    public void yedekle() {
        yedek.paylas();
    }

    @Override
    public void geriYukle() {
        yedek.sec();
    }

    @Override
    public void hakkinda() {
        Hakkinda.goster(this);
    }

    @Override
    public void sifirla() {
        new AlertDialog.Builder(this)
            .setTitle("Ayarlar sıfırlansın mı?")
            .setMessage("Bütün ayarlar ilk hâline döner. Öğrettiğin yapay zekâ verileri ve egzersiz geçmişi silinmez.")
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Sıfırla", (d, w) -> {
                ayarlar.sifirla();
                recreate();
            })
            .show();
    }

    // ---------- Durum ----------

    private void durumuYaz() {
        yuzSayisi.setText(String.valueOf(sonYuzSayisi));
        yuzSayisi.setTextColor(ayarlar.renk);
        elSayisi.setText(String.valueOf(sonElSayisi));
        elSayisi.setTextColor(ayarlar.renk);
        int fps = Math.round(Math.min(algilayici.yuz.fps.deger(), algilayici.el.fps.deger()));
        fpsYazisi.setText(fps + " FPS" + (ayarlar.gpu ? " · GPU" : ""));
        yzPaneli.guncelle();
        oyunPaneli.guncelle();
    }

    private void toast(String mesaj) {
        Toast.makeText(this, mesaj, Toast.LENGTH_LONG).show();
    }
}
