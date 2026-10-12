package app.tigo.dedektor;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
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
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kamera ve algılama ekranı. Yüz ve el modelleri MediaPipe'ın Android kütüphanesiyle native
 * çalışır: çok çekirdekli işlemci (ya da GPU) kullanır, web sürümünden kat kat hızlıdır.
 * Her model bir önceki kareyi bitirince yeni kare alır; arada gelen kareler atlanır.
 */
public class KameraActivity extends AppCompatActivity {

    private static final String ETIKET = "Dedektor";
    /** Modeller web dosyalarının yanında, APK'nın assets/public klasöründe durur. */
    private static final String MODELLER = "public/mediapipe/models/";
    private static final String TERCIH_HIZLANDIRICI = "hizlandirici";

    private PreviewView onizleme;
    private CizimView cizim;
    private TextView yuzSayisi;
    private TextView elSayisi;
    private TextView fpsYazisi;

    private final ExecutorService analiz = Executors.newSingleThreadExecutor();
    private final AtomicBoolean yuzMesgul = new AtomicBoolean(false);
    private final AtomicBoolean elMesgul = new AtomicBoolean(false);
    private final FpsSayaci yuzFps = new FpsSayaci();
    private final FpsSayaci elFps = new FpsSayaci();

    private volatile FaceLandmarker yuz;
    private volatile HandLandmarker el;
    private volatile Delegate hizlandirici = Delegate.CPU;
    private volatile int sonYuzSayisi;
    private volatile int sonElSayisi;
    private ProcessCameraProvider saglayici;
    private boolean onKamera = true;
    private SharedPreferences tercihler;

    private final ActivityResultLauncher<String> izinIste = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        verildi -> {
            if (verildi) kamerayiBaslat();
            else {
                Toast.makeText(this, "Kamera izni verilmedi.", Toast.LENGTH_LONG).show();
                finish();
            }
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
        ImageButton cevir = findViewById(R.id.cevir);
        kenarBosluklariniAyarla(findViewById(R.id.ust), cevir);

        tercihler = getSharedPreferences("kamera", MODE_PRIVATE);
        if ("GPU".equals(tercihler.getString(TERCIH_HIZLANDIRICI, "CPU"))) hizlandirici = Delegate.GPU;
        durumuYaz();

        findViewById(R.id.geri).setOnClickListener(v -> finish());
        cevir.setOnClickListener(v -> {
            onKamera = !onKamera;
            kamerayiBagla();
        });
        // FPS yazısına dokunmak CPU ile GPU arasında geçiş yapar; bazı telefonlarda GPU daha hızlıdır.
        fpsYazisi.setOnClickListener(v -> {
            hizlandirici = hizlandirici == Delegate.CPU ? Delegate.GPU : Delegate.CPU;
            analiz.execute(this::modelleriKur);
        });

        analiz.execute(this::modelleriKur);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) kamerayiBaslat();
        else izinIste.launch(Manifest.permission.CAMERA);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analiz.execute(this::modelleriKapat);
        analiz.shutdown();
    }

    /** Durum çubuğu ve gezinme çubuğu içeriğin üstüne binmesin. */
    private void kenarBosluklariniAyarla(View ust, View cevir) {
        int ustBosluk = ust.getPaddingTop();
        int altBosluk = ((ViewGroup.MarginLayoutParams) cevir.getLayoutParams()).bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.kok), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ust.setPadding(ust.getPaddingLeft(), b.top + ustBosluk, ust.getPaddingRight(), ust.getPaddingBottom());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) cevir.getLayoutParams();
            lp.bottomMargin = b.bottom + altBosluk;
            cevir.setLayoutParams(lp);
            return insets;
        });
    }

    // ---------- Modeller (analiz iş parçacığında) ----------

    private void modelleriKur() {
        modelleriKapat();
        try {
            yuz = yuzOlustur(hizlandirici);
            el = elOlustur(hizlandirici);
        } catch (RuntimeException e) {
            Log.e(ETIKET, "Modeller kurulamadı (" + hizlandirici + ")", e);
            modelleriKapat();
            if (hizlandirici == Delegate.GPU) {
                hizlandirici = Delegate.CPU;
                runOnUiThread(() -> Toast.makeText(this, "Bu telefonda GPU kullanılamıyor, CPU kullanılıyor.", Toast.LENGTH_LONG).show());
                modelleriKur();
                return;
            }
            runOnUiThread(() -> Toast.makeText(this, "Modeller yüklenemedi: " + e.getMessage(), Toast.LENGTH_LONG).show());
            return;
        }
        tercihler.edit().putString(TERCIH_HIZLANDIRICI, hizlandirici.name()).apply();
        yuzFps.sifirla();
        elFps.sifirla();
        runOnUiThread(this::durumuYaz);
    }

    private void modelleriKapat() {
        if (yuz != null) yuz.close();
        if (el != null) el.close();
        yuz = null;
        el = null;
        yuzMesgul.set(false);
        elMesgul.set(false);
    }

    private BaseOptions temelAyarlar(String model, Delegate d) {
        return BaseOptions.builder().setModelAssetPath(MODELLER + model).setDelegate(d).build();
    }

    private FaceLandmarker yuzOlustur(Delegate d) {
        return FaceLandmarker.createFromOptions(this, FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(temelAyarlar("face_landmarker.task", d))
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.6f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener((sonuc, girdi) -> {
                cizim.yuzGuncelle(sonuc, girdi.getWidth(), girdi.getHeight());
                sonYuzSayisi = sonuc.faceLandmarks().size();
                yuzFps.say();
                yuzMesgul.set(false);
                runOnUiThread(this::durumuYaz);
            })
            .setErrorListener(hata -> {
                Log.e(ETIKET, "Yüz modeli hatası", hata);
                yuzMesgul.set(false);
            })
            .build());
    }

    private HandLandmarker elOlustur(Delegate d) {
        return HandLandmarker.createFromOptions(this, HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(temelAyarlar("hand_landmarker.task", d))
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener((sonuc, girdi) -> {
                cizim.elGuncelle(sonuc, girdi.getWidth(), girdi.getHeight());
                sonElSayisi = sonuc.landmarks().size();
                elFps.say();
                elMesgul.set(false);
            })
            .setErrorListener(hata -> {
                Log.e(ETIKET, "El modeli hatası", hata);
                elMesgul.set(false);
            })
            .build());
    }

    /** Kameradan gelen her kare: boşta olan modele verilir, iki model de meşgulse atlanır. */
    private void kareyiIsle(ImageProxy kare) {
        try {
            FaceLandmarker y = yuz;
            HandLandmarker e = el;
            boolean yuzaVer = y != null && yuzMesgul.compareAndSet(false, true);
            boolean eleVer = e != null && elMesgul.compareAndSet(false, true);
            if (!yuzaVer && !eleVer) return;
            // Görüntü dik (setOutputImageRotationEnabled) ve aynalanmamış; el etiketleri doğru çıkar.
            Bitmap resim = kare.toBitmap();
            MPImage girdi = new BitmapImageBuilder(resim).build();
            long zaman = SystemClock.uptimeMillis();
            if (yuzaVer) y.detectAsync(girdi, zaman);
            if (eleVer) e.detectAsync(girdi, zaman);
        } catch (RuntimeException hata) {
            Log.e(ETIKET, "Kare işlenemedi", hata);
            yuzMesgul.set(false);
            elMesgul.set(false);
        } finally {
            kare.close();
        }
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
                Toast.makeText(this, "Kamera açılamadı.", Toast.LENGTH_LONG).show();
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
        analizKullanimi.setAnalyzer(analiz, this::kareyiIsle);

        CameraSelector secim = onKamera ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
        try {
            saglayici.unbindAll();
            saglayici.bindToLifecycle(this, secim, onizlemeKullanimi, analizKullanimi);
            cizim.setAynali(onKamera);
        } catch (Exception e) {
            Log.e(ETIKET, "Kamera bağlanamadı", e);
            Toast.makeText(this, onKamera ? "Ön kamera açılamadı." : "Arka kamera açılamadı.", Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Durum ----------

    private void durumuYaz() {
        yuzSayisi.setText(hap("Yüz", sonYuzSayisi));
        elSayisi.setText(hap("El", sonElSayisi));
        int fps = Math.round(Math.min(yuzFps.deger(), elFps.deger()));
        fpsYazisi.setText(fps + " FPS · " + hizlandirici.name());
    }

    private static CharSequence hap(String ad, int sayi) {
        SpannableStringBuilder s = new SpannableStringBuilder(ad + "  " + sayi);
        int bas = ad.length() + 2;
        s.setSpan(new ForegroundColorSpan(0xFF00E676), bas, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new StyleSpan(Typeface.BOLD), bas, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** Saniyedeki sonuç sayısının yumuşatılmış ortalaması. */
    private static final class FpsSayaci {
        private long onceki;
        private float deger;

        synchronized void say() {
            long simdi = SystemClock.uptimeMillis();
            if (onceki != 0 && simdi > onceki) {
                float anlik = 1000f / (simdi - onceki);
                deger = deger == 0 ? anlik : deger * 0.9f + anlik * 0.1f;
            }
            onceki = simdi;
        }

        synchronized float deger() {
            return deger;
        }

        synchronized void sifirla() {
            onceki = 0;
            deger = 0;
        }
    }
}
