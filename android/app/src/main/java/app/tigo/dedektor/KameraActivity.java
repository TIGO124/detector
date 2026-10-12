package app.tigo.dedektor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
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
import androidx.appcompat.widget.SwitchCompat;
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
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kamera ve algılama ekranı. Modlar: Normal (algılama), Çizim (havada çizim), Efekt (yüz
 * efektleri), Oyun (taş-kağıt-makas). Modeller Algilayici'da native çalışır; bu sınıf
 * kamerayı, düğmeleri, ayarları ve fotoğraf çekmeyi yönetir.
 */
public class KameraActivity extends AppCompatActivity implements Algilayici.Dinleyici {

    private static final String ETIKET = "Dedektor";
    private static final String[] MOD_ADLARI = {"Normal", "Çizim", "Efekt", "Oyun"};
    private static final String[] EFEKT_ADLARI = {"Gözlük", "Bıyık", "Şapka", "Hepsi"};
    private static final String[] ARKA_PLAN_ADLARI = {"Kapalı", "Bulanık", "Değiştir"};

    private PreviewView onizleme;
    private CizimView cizim;
    private TextView yuzSayisi;
    private TextView elSayisi;
    private TextView fpsYazisi;
    private TextView ipucu;
    private LinearLayout modlar;
    private LinearLayout modPaneli;
    private LinearLayout renkler;
    private LinearLayout arkaPlanlar;
    private View ayarPaneli;
    private View flas;
    private float dp;

    private Ayarlar ayarlar;
    private Algilayici algilayici;
    private OyunAkisi oyun;
    private final ArkaPlan arkaPlanIsleyici = new ArkaPlan();
    private final HavadaCizim havadaCizim = new HavadaCizim();
    private final ElTakibi elTakibi = new ElTakibi();
    private final IfadeAnalizi ifadeAnalizi = new IfadeAnalizi();
    private final ExecutorService analiz = Executors.newSingleThreadExecutor();
    private ProcessCameraProvider saglayici;
    private boolean onKamera = true;
    private volatile int sonYuzSayisi;
    private volatile int sonElSayisi;
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
        dp = getResources().getDisplayMetrics().density;

        onizleme = findViewById(R.id.onizleme);
        cizim = findViewById(R.id.cizim);
        yuzSayisi = findViewById(R.id.yuz_sayisi);
        elSayisi = findViewById(R.id.el_sayisi);
        fpsYazisi = findViewById(R.id.fps);
        ipucu = findViewById(R.id.ipucu);
        modlar = findViewById(R.id.modlar);
        modPaneli = findViewById(R.id.mod_paneli);
        renkler = findViewById(R.id.renkler);
        arkaPlanlar = findViewById(R.id.arka_planlar);
        ayarPaneli = findViewById(R.id.ayar_paneli);
        flas = findViewById(R.id.flas);

        ayarlar = new Ayarlar(getSharedPreferences("kamera", MODE_PRIVATE));
        cizim.baglan(ayarlar, havadaCizim);
        oyun = new OyunAkisi(findViewById(R.id.oyun_yazisi), this::oyuncuSecimi);
        algilayici = new Algilayici(this, this);

        kenarBosluklariniAyarla();
        dugmeleriBagla();
        ayarPaneliniKur();
        modlariGoster();
        durumuYaz();

        analiz.execute(() -> {
            ayarlar.gpu = algilayici.temelModelleriKur(ayarlar.gpu);
            if (ayarlar.vucut) algilayici.vucutAyarla(true);
            if (ayarlar.arkaPlan != Ayarlar.ArkaPlan.KAPALI) algilayici.arkaPlanAyarla(true);
            runOnUiThread(this::durumuYaz);
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) kamerayiBaslat();
        else kameraIzni.launch(Manifest.permission.CAMERA);

        // Geri: önce açık ayar panelini kapatır.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (ayarPaneli.getVisibility() == View.VISIBLE) ayarPaneli.setVisibility(View.GONE);
                else finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        oyun.durdur();
        analiz.execute(algilayici::hepsiniKapat);
        analiz.shutdown();
    }

    /** Durum çubuğu ve gezinme çubuğu içeriğin üstüne binmesin. */
    private void kenarBosluklariniAyarla() {
        View ust = findViewById(R.id.ust);
        View alt = findViewById(R.id.alt);
        int ustBosluk = ust.getPaddingTop();
        int altBosluk = alt.getPaddingBottom();
        int panelBosluk = ayarPaneli.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.kok), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ust.setPadding(ust.getPaddingLeft(), b.top + ustBosluk, ust.getPaddingRight(), ust.getPaddingBottom());
            alt.setPadding(alt.getPaddingLeft(), alt.getPaddingTop(), alt.getPaddingRight(), b.bottom + altBosluk);
            ayarPaneli.setPadding(ayarPaneli.getPaddingLeft(), ayarPaneli.getPaddingTop(), ayarPaneli.getPaddingRight(), b.bottom + panelBosluk);
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
        findViewById(R.id.ayarlar).setOnClickListener(v ->
            ayarPaneli.setVisibility(ayarPaneli.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
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
        ipucu.setText(metin);
        ipucu.setVisibility(metin == null ? View.GONE : View.VISIBLE);
    }

    private void modSec(Ayarlar.Mod mod) {
        if (mod != Ayarlar.Mod.OYUN) oyun.durdur();
        ayarlar.mod = mod;
        ayarlar.kaydet();
        modlariGoster();
        cizim.invalidate();
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
            default:
                break;
        }
        modPaneli.setVisibility(modPaneli.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private TextView cip(String metin, boolean secili, View.OnClickListener tik) {
        return Cip.olustur(this, metin, secili, ayarlar.renk, tik);
    }

    // ---------- Ayarlar ----------

    private void ayarPaneliniKur() {
        SwitchCompat yuzAgi = findViewById(R.id.ayar_yuz_agi);
        yuzAgi.setChecked(ayarlar.yuzAgi);
        yuzAgi.setOnCheckedChangeListener((b, acik) -> {
            ayarlar.yuzAgi = acik;
            ayarlar.kaydet();
            cizim.invalidate();
        });
        SwitchCompat etiketler = findViewById(R.id.ayar_etiketler);
        etiketler.setChecked(ayarlar.etiketler);
        etiketler.setOnCheckedChangeListener((b, acik) -> {
            ayarlar.etiketler = acik;
            ayarlar.kaydet();
            cizim.invalidate();
        });
        SwitchCompat vucut = findViewById(R.id.ayar_vucut);
        vucut.setChecked(ayarlar.vucut);
        vucut.setOnCheckedChangeListener((b, acik) -> {
            ayarlar.vucut = acik;
            ayarlar.kaydet();
            if (!acik) cizim.vucutuTemizle();
            analiz.execute(() -> algilayici.vucutAyarla(acik));
        });
        findViewById(R.id.ayar_tamam).setOnClickListener(v -> ayarPaneli.setVisibility(View.GONE));
        renkleriGoster();
        arkaPlanlariGoster();
    }

    private void renkleriGoster() {
        renkler.removeAllViews();
        for (int renk : Ayarlar.RENKLER) {
            View nokta = new View(this);
            GradientDrawable zemin = new GradientDrawable();
            zemin.setShape(GradientDrawable.OVAL);
            zemin.setColor(renk);
            if (renk == ayarlar.renk) zemin.setStroke((int) (3 * dp), 0xFFFFFFFF);
            nokta.setBackground(zemin);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
            lp.setMarginEnd((int) (12 * dp));
            nokta.setOnClickListener(v -> {
                ayarlar.renk = renk;
                ayarlar.kaydet();
                renkleriGoster();
                arkaPlanlariGoster();
                modlariGoster();
                durumuYaz();
                cizim.invalidate();
            });
            renkler.addView(nokta, lp);
        }
        findViewById(R.id.ayar_tamam).setBackgroundTintList(ColorStateList.valueOf(ayarlar.renk));
    }

    private void arkaPlanlariGoster() {
        arkaPlanlar.removeAllViews();
        Ayarlar.ArkaPlan[] turler = Ayarlar.ArkaPlan.values();
        for (int i = 0; i < turler.length; i++) {
            Ayarlar.ArkaPlan tur = turler[i];
            arkaPlanlar.addView(cip(ARKA_PLAN_ADLARI[i], ayarlar.arkaPlan == tur, v -> {
                ayarlar.arkaPlan = tur;
                ayarlar.kaydet();
                arkaPlanlariGoster();
                boolean acik = tur != Ayarlar.ArkaPlan.KAPALI;
                if (!acik) cizim.arkaPlaniTemizle();
                analiz.execute(() -> algilayici.arkaPlanAyarla(acik));
            }));
        }
    }

    // ---------- Model sonuçları (MediaPipe iş parçacıklarından) ----------

    @Override
    public void yuz(FaceLandmarkerResult sonuc, int genislik, int yukseklik) {
        List<List<Category>> puanlar = sonuc.faceBlendshapes().orElse(Collections.emptyList());
        List<String> ifadeler = Collections.emptyList();
        if (puanlar.isEmpty()) ifadeAnalizi.sifirla();
        else ifadeler = ifadeAnalizi.guncelle(puanlar.get(0));
        cizim.yuzGuncelle(sonuc, ifadeler, genislik, yukseklik);
        sonYuzSayisi = sonuc.faceLandmarks().size();
        runOnUiThread(this::durumuYaz);
    }

    @Override
    public void el(GestureRecognizerResult sonuc, int genislik, int yukseklik) {
        List<ElTakibi.ElDurumu> eller = elTakibi.guncelle(ElTakibi.olcumler(sonuc));
        cizim.elGuncelle(eller, genislik, yukseklik);
        sonEller = eller;
        sonElSayisi = eller.size();
        if (ayarlar.mod == Ayarlar.Mod.CIZIM) cizimiGuncelle(eller);
    }

    @Override
    public void vucut(PoseLandmarkerResult sonuc, int genislik, int yukseklik) {
        if (ayarlar.vucut) cizim.vucutGuncelle(sonuc, genislik, yukseklik);
    }

    @Override
    public void arkaPlan(ImageSegmenterResult sonuc, MPImage girdi) {
        Ayarlar.ArkaPlan tur = ayarlar.arkaPlan;
        if (tur == Ayarlar.ArkaPlan.KAPALI) return;
        Bitmap katman = arkaPlanIsleyici.katman(sonuc, girdi, tur);
        if (katman != null) cizim.arkaPlanGuncelle(katman);
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
    }

    private void toast(String mesaj) {
        Toast.makeText(this, mesaj, Toast.LENGTH_LONG).show();
    }
}
