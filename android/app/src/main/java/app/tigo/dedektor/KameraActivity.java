package app.tigo.dedektor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapExtractor;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kamera ve algılama ekranı. Modlar: Normal (algılama), Çizim (havada çizim), Efekt (yüz
 * efektleri), Oyun (taş-kağıt-makas), YZ (öğretilen hareketler, TİD, duygu, kişi, egzersiz, nesne).
 * Modeller Algilayici'da native çalışır; bu sınıf kamerayı, düğmeleri ve fotoğraf çekmeyi yönetir.
 */
public class KameraActivity extends AppCompatActivity implements Algilayici.Dinleyici {

    private static final String ETIKET = "Dedektor";
    private static final String[] MOD_ADLARI = {"Normal", "Çizim", "Efekt", "Oyun", "YZ"};
    private static final String[] EFEKT_ADLARI = {"Gözlük", "Bıyık", "Şapka", "Hepsi"};

    private PreviewView onizleme;
    private CizimView cizim;
    private TextView yuzSayisi;
    private TextView elSayisi;
    private TextView fpsYazisi;
    private TextView ipucu;
    private LinearLayout modlar;
    private LinearLayout modPaneli;
    private AyarPaneli ayarPaneli;
    private YzPaneli yzPaneli;
    private View flas;

    private Ayarlar ayarlar;
    private Algilayici algilayici;
    private OyunAkisi oyun;
    private YzTanima yz;
    private final ArkaPlan arkaPlanIsleyici = new ArkaPlan();
    private final HavadaCizim havadaCizim = new HavadaCizim();
    private final ElTakibi elTakibi = new ElTakibi();
    private final IfadeAnalizi ifadeAnalizi = new IfadeAnalizi();
    private final ExecutorService analiz = Executors.newSingleThreadExecutor();
    private ProcessCameraProvider saglayici;
    private boolean onKamera = true;
    private volatile int sonYuzSayisi;
    private volatile int sonElSayisi;
    private volatile long sonDurumYazimi;
    private volatile List<ElTakibi.ElDurumu> sonEller = Collections.emptyList();
    private Bitmap kaydedilecek;

    private final ActivityResultLauncher<String> kameraIzni = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        verildi -> {
            if (verildi) kamerayiBaslat();
            else {
                toast("Kamera izni verilmedi.");
                finish();
            }
        }
    );

    /** Android 9 ve öncesinde galeriye kaydetmek için izin gerekir. */
    private final ActivityResultLauncher<String> depoIzni = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        verildi -> {
            Bitmap foto = kaydedilecek;
            kaydedilecek = null;
            if (verildi && foto != null) arkadaKaydet(foto);
            else toast("Fotoğraf kaydetme izni verilmedi.");
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_kamera);

        onizleme = findViewById(R.id.onizleme);
        cizim = findViewById(R.id.cizim);
        yuzSayisi = findViewById(R.id.yuz_sayisi);
        elSayisi = findViewById(R.id.el_sayisi);
        fpsYazisi = findViewById(R.id.fps);
        ipucu = findViewById(R.id.ipucu);
        modlar = findViewById(R.id.modlar);
        modPaneli = findViewById(R.id.mod_paneli);
        flas = findViewById(R.id.flas);

        ayarlar = new Ayarlar(getSharedPreferences("kamera", MODE_PRIVATE));
        cizim.baglan(ayarlar, havadaCizim);
        TextView ortaYazi = findViewById(R.id.oyun_yazisi);
        oyun = new OyunAkisi(ortaYazi, this::oyuncuSecimi);
        algilayici = new Algilayici(this, this);
        yz = new YzTanima(this);
        yzPaneli = new YzPaneli(this, ayarlar, yz, ortaYazi, this::modlariGoster);
        ayarPaneli = new AyarPaneli(this, ayarlar, cizim, () -> {
            modlariGoster();
            durumuYaz();
        }, this::modelleriAyarla, acik -> analiz.execute(() -> algilayici.arkaPlanAyarla(acik)));

        kenarBosluklariniAyarla();
        dugmeleriBagla();
        modlariGoster();
        durumuYaz();

        analiz.execute(() -> {
            ayarlar.gpu = algilayici.temelModelleriKur(ayarlar.gpu);
            if (ayarlar.vucutGerekli()) algilayici.vucutAyarla(true);
            if (ayarlar.arkaPlan != Ayarlar.ArkaPlan.KAPALI) algilayici.arkaPlanAyarla(true);
            runOnUiThread(this::durumuYaz);
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) kamerayiBaslat();
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
    protected void onDestroy() {
        // Önce kamerayı ayır: kapanan iş parçacığına yeni kare gelip reddedilmesin.
        if (saglayici != null) saglayici.unbindAll();
        oyun.durdur();
        yzPaneli.kapat();
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
            onKamera = !onKamera;
            havadaCizim.temizle();
            kamerayiBagla();
        });
        findViewById(R.id.ayarlar).setOnClickListener(v -> ayarPaneli.ac(!ayarPaneli.acik()));
        findViewById(R.id.deklansor).setOnClickListener(v -> fotografCek());
        // FPS yazısına dokunmak CPU ile GPU arasında geçiş yapar; bazı telefonlarda GPU daha hızlıdır.
        fpsYazisi.setOnClickListener(v -> analiz.execute(() -> {
            ayarlar.gpu = algilayici.temelModelleriKur(!ayarlar.gpu);
            ayarlar.kaydet();
            runOnUiThread(this::durumuYaz);
        }));
    }

    // ---------- Modlar ----------

    private void modlariGoster() {
        modlar.removeAllViews();
        Ayarlar.Mod[] tum = Ayarlar.Mod.values();
        for (int i = 0; i < tum.length; i++) {
            Ayarlar.Mod m = tum[i];
            modlar.addView(cip(MOD_ADLARI[i], ayarlar.mod == m, v -> modSec(m)));
        }
        modPaneliniGoster();
        String metin = null;
        if (ayarlar.mod == Ayarlar.Mod.CIZIM) metin = "İşaret parmağınla çiz · Yumruğu tut: sil";
        else if (ayarlar.mod == Ayarlar.Mod.OYUN) metin = "Oyna'ya bas, \"Göster!\" deyince elini göster";
        else if (ayarlar.mod == Ayarlar.Mod.YZ) metin = yzPaneli.ipucu();
        ipucu.setText(metin);
        ipucu.setVisibility(metin == null ? View.GONE : View.VISIBLE);
        yzPaneli.goster(ayarlar.mod == Ayarlar.Mod.YZ);
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
        if (mod != Ayarlar.Mod.OYUN) oyun.durdur();
        ayarlar.mod = mod;
        ayarlar.kaydet();
        modlariGoster();
    }

    private void modPaneliniGoster() {
        modPaneli.removeAllViews();
        switch (ayarlar.mod) {
            case CIZIM:
                modPaneli.addView(cip("Temizle", false, v -> {
                    havadaCizim.temizle();
                    cizim.invalidate();
                }));
                break;
            case EFEKT:
                Ayarlar.Efekt[] efektler = Ayarlar.Efekt.values();
                for (int i = 0; i < efektler.length; i++) {
                    Ayarlar.Efekt e = efektler[i];
                    modPaneli.addView(cip(EFEKT_ADLARI[i], ayarlar.efekt == e, v -> {
                        ayarlar.efekt = e;
                        ayarlar.kaydet();
                        modPaneliniGoster();
                        cizim.invalidate();
                    }));
                }
                break;
            case OYUN:
                TextView skor = cip("", false, null);
                oyun.skorBagla(skor);
                modPaneli.addView(skor);
                modPaneli.addView(cip("Oyna", true, v -> oyun.oyna()));
                break;
            case YZ:
                yzPaneli.altModlar(modPaneli);
                break;
            default:
                break;
        }
        ((View) modPaneli.getParent()).setVisibility(modPaneli.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private TextView cip(String metin, boolean secili, View.OnClickListener tik) {
        return Cip.olustur(this, metin, secili, ayarlar.renk, tik);
    }

    // ---------- Model sonuçları (MediaPipe iş parçacıklarından) ----------

    @Override
    public void yuz(FaceLandmarkerResult sonuc, MPImage girdi) {
        List<List<Category>> puanlar = sonuc.faceBlendshapes().orElse(Collections.emptyList());
        List<String> ifadeler = Collections.emptyList();
        if (ayarlar.mod == Ayarlar.Mod.YZ) {
            ifadeler = yzYuz(sonuc, puanlar, girdi);
        } else if (puanlar.isEmpty()) {
            ifadeAnalizi.sifirla();
        } else {
            ifadeler = ifadeAnalizi.guncelle(puanlar.get(0));
        }
        cizim.yuzGuncelle(sonuc, ifadeler, girdi.getWidth(), girdi.getHeight());
        sonYuzSayisi = sonuc.faceLandmarks().size();
        // Sayaçlar saniyede 4 kez yeterli; her karede yazmak üst çubuğu her karede yeniden ölçtürür.
        long simdi = SystemClock.uptimeMillis();
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

    @Override
    public void el(GestureRecognizerResult sonuc, int genislik, int yukseklik) {
        List<ElTakibi.ElDurumu> eller = elTakibi.guncelle(ElTakibi.olcumler(sonuc));
        if (ayarlar.mod == Ayarlar.Mod.YZ) yz.el(eller, ayarlar.yz, SystemClock.uptimeMillis());
        cizim.elGuncelle(eller, genislik, yukseklik);
        sonEller = eller;
        sonElSayisi = eller.size();
        if (ayarlar.mod == Ayarlar.Mod.CIZIM) cizimiGuncelle(eller);
    }

    @Override
    public void vucut(PoseLandmarkerResult sonuc, int genislik, int yukseklik) {
        if (ayarlar.vucutGerekli()) cizim.vucutGuncelle(sonuc, genislik, yukseklik);
        if (ayarlar.mod == Ayarlar.Mod.YZ && ayarlar.yz == Ayarlar.Yz.EGZERSIZ) yz.vucut(sonuc);
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

    /** Oyun için: görünen ellerden ilk geçerli seçim. */
    private TasKagitMakas.Secim oyuncuSecimi() {
        for (ElTakibi.ElDurumu el : sonEller) {
            TasKagitMakas.Secim secim = TasKagitMakas.hareketten(el.hareketKodu, el.parmak);
            if (secim != null) return secim;
        }
        return null;
    }

    // ---------- Kamera ----------

    private void kamerayiBaslat() {
        ListenableFuture<ProcessCameraProvider> gelecek = ProcessCameraProvider.getInstance(this);
        gelecek.addListener(() -> {
            try {
                saglayici = gelecek.get();
                kamerayiBagla();
            } catch (Exception e) {
                Log.e(ETIKET, "Kamera açılamadı", e);
                toast("Kamera açılamadı.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void kamerayiBagla() {
        if (saglayici == null) return;
        // Önizleme ve analiz aynı en-boy oranında (4:3): çizim önizlemeyle birebir örtüşür.
        AspectRatioStrategy oran = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY;
        Preview onizlemeKullanimi = new Preview.Builder()
            .setResolutionSelector(new ResolutionSelector.Builder().setAspectRatioStrategy(oran).build())
            .build();
        onizlemeKullanimi.setSurfaceProvider(onizleme.getSurfaceProvider());

        // Modeller 256 pikselden küçük girdi kullanır; 640x480 yeterli ve hızlı.
        ImageAnalysis analizKullanimi = new ImageAnalysis.Builder()
            .setResolutionSelector(new ResolutionSelector.Builder()
                .setAspectRatioStrategy(oran)
                .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setOutputImageRotationEnabled(true)
            .build();
        analizKullanimi.setAnalyzer(analiz, algilayici::kareVer);

        CameraSelector secim = onKamera ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
        try {
            saglayici.unbindAll();
            saglayici.bindToLifecycle(this, secim, onizlemeKullanimi, analizKullanimi);
            cizim.setAynali(onKamera);
        } catch (Exception e) {
            Log.e(ETIKET, "Kamera bağlanamadı", e);
            toast(onKamera ? "Ön kamera açılamadı." : "Arka kamera açılamadı.");
        }
    }

    // ---------- Fotoğraf ----------

    /** Önizleme ile çizimleri birleştirip galeriye kaydeder. */
    private void fotografCek() {
        Bitmap goruntu = onizleme.getBitmap();
        if (goruntu == null || cizim.getWidth() == 0) {
            toast("Fotoğraf çekilemedi.");
            return;
        }
        Bitmap foto = Bitmap.createBitmap(goruntu.getWidth(), goruntu.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(foto);
        c.drawBitmap(goruntu, 0, 0, null);
        c.scale(foto.getWidth() / (float) cizim.getWidth(), foto.getHeight() / (float) cizim.getHeight());
        cizim.draw(c);
        flas.setAlpha(0.8f);
        flas.animate().alpha(0f).setDuration(300).start();
        if (Build.VERSION.SDK_INT < 29
            && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            kaydedilecek = foto;
            depoIzni.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        arkadaKaydet(foto);
    }

    private void arkadaKaydet(Bitmap foto) {
        new Thread(() -> {
            try {
                Galeri.kaydet(this, foto);
                runOnUiThread(() -> toast("Fotoğraf galeriye kaydedildi."));
            } catch (Exception e) {
                Log.e(ETIKET, "Fotoğraf kaydedilemedi", e);
                runOnUiThread(() -> toast("Fotoğraf kaydedilemedi."));
            }
        }).start();
    }

    // ---------- Durum ----------

    private void durumuYaz() {
        yuzSayisi.setText(Cip.sayac("Yüz", sonYuzSayisi, ayarlar.renk));
        elSayisi.setText(Cip.sayac("El", sonElSayisi, ayarlar.renk));
        int fps = Math.round(Math.min(algilayici.yuz.fps.deger(), algilayici.el.fps.deger()));
        fpsYazisi.setText(fps + " FPS · " + (ayarlar.gpu ? "GPU" : "CPU"));
        yzPaneli.guncelle();
    }

    private void toast(String mesaj) {
        Toast.makeText(this, mesaj, Toast.LENGTH_LONG).show();
    }
}
