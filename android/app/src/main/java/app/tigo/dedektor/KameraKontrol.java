package app.tigo.dedektor;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.util.Log;
import android.util.Size;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Kamera: önizleme ve analiz kullanımlarını bağlar, ön/arka kamera (seçim saklanır),
 * yakınlaştırma (iki parmak, çift dokunuş 1×/2×) ve fener (arka kamerada, flaşı varsa).
 */
final class KameraKontrol {

    private static final String ETIKET = "Dedektor";

    private final AppCompatActivity etkinlik;
    private final Ayarlar ayarlar;
    private final PreviewView onizleme;
    private final CizimView cizim;
    private final ExecutorService analiz;
    private final ImageAnalysis.Analyzer analizci;
    /** Kamera değişti (ön/arka): çizimler temizlensin. */
    private final Runnable degisti;
    private final ImageButton fener;
    private final TextView yakinlik;
    private final View[] fpsGorunumleri;
    private final Runnable yakinligiGizle;
    private ProcessCameraProvider saglayici;
    private Camera kamera;
    private boolean fenerAcik;
    /** İstenen yakınlık (kameranın bildirdiği değer hareket sırasında geride kalır). */
    private float oran = 1f;

    KameraKontrol(AppCompatActivity etkinlik, Ayarlar ayarlar, CizimView cizim, ExecutorService analiz,
                  ImageAnalysis.Analyzer analizci, Runnable degisti) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.cizim = cizim;
        this.analiz = analiz;
        this.analizci = analizci;
        this.degisti = degisti;
        onizleme = etkinlik.findViewById(R.id.onizleme);
        fener = etkinlik.findViewById(R.id.fener);
        yakinlik = etkinlik.findViewById(R.id.yakinlik);
        fpsGorunumleri = new View[] {etkinlik.findViewById(R.id.fps_ayrac), etkinlik.findViewById(R.id.fps)};
        yakinligiGizle = () -> yakinlik.setVisibility(View.GONE);
        fener.setOnClickListener(v -> feneriAc(!fenerAcik));
        dokunmaBagla();
    }

    void baslat() {
        ListenableFuture<ProcessCameraProvider> gelecek = ProcessCameraProvider.getInstance(etkinlik);
        gelecek.addListener(() -> {
            try {
                saglayici = gelecek.get();
                bagla();
            } catch (Exception e) {
                Log.e(ETIKET, "Kamera açılamadı", e);
                toast("Kamera açılamadı.");
            }
        }, ContextCompat.getMainExecutor(etkinlik));
    }

    void cevir() {
        ayarlar.onKamera = !ayarlar.onKamera;
        ayarlar.kaydet();
        bagla();
    }

    void ayir() {
        if (saglayici != null) saglayici.unbindAll();
    }

    private void bagla() {
        if (saglayici == null) return;
        // Önizleme ve analiz aynı en-boy oranında (4:3): çizim önizlemeyle birebir örtüşür.
        AspectRatioStrategy enBoy = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY;
        Preview onizlemeKullanimi = new Preview.Builder()
            .setResolutionSelector(new ResolutionSelector.Builder().setAspectRatioStrategy(enBoy).build())
            .build();
        onizlemeKullanimi.setSurfaceProvider(onizleme.getSurfaceProvider());

        // Modeller 256 pikselden küçük girdi kullanır; 640x480 yeterli ve hızlı.
        ImageAnalysis analizKullanimi = new ImageAnalysis.Builder()
            .setResolutionSelector(new ResolutionSelector.Builder()
                .setAspectRatioStrategy(enBoy)
                .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setOutputImageRotationEnabled(true)
            .build();
        analizKullanimi.setAnalyzer(analiz, analizci);

        boolean on = ayarlar.onKamera;
        CameraSelector secim = on ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
        fenerAcik = false;
        oran = 1f;
        try {
            saglayici.unbindAll();
            kamera = saglayici.bindToLifecycle(etkinlik, secim, onizlemeKullanimi, analizKullanimi);
            cizim.setAynali(on);
        } catch (Exception e) {
            Log.e(ETIKET, "Kamera bağlanamadı", e);
            kamera = null;
            toast(on ? "Ön kamera açılamadı." : "Arka kamera açılamadı.");
        }
        degisti.run();
        feneriGoster();
    }

    // ---------- Fener ----------

    private void feneriGoster() {
        boolean var = kamera != null && !ayarlar.onKamera && kamera.getCameraInfo().hasFlashUnit();
        fener.setVisibility(var ? View.VISIBLE : View.GONE);
        fener.setImageTintList(ColorStateList.valueOf(fenerAcik ? ayarlar.renk : Cip.YAZI));
        // Dar ekranda üst çubuk sığsın: fener görünürken FPS gizlenir.
        boolean dar = etkinlik.getResources().getConfiguration().screenWidthDp < 380;
        for (View v : fpsGorunumleri) v.setVisibility(var && dar ? View.GONE : View.VISIBLE);
    }

    private void feneriAc(boolean ac) {
        if (kamera == null) return;
        fenerAcik = ac;
        kamera.getCameraControl().enableTorch(ac);
        fener.setContentDescription(ac ? "Feneri kapat" : "Feneri aç");
        feneriGoster();
    }

    // ---------- Yakınlaştırma ----------

    @SuppressLint("ClickableViewAccessibility")
    private void dokunmaBagla() {
        ScaleGestureDetector iki = new ScaleGestureDetector(etkinlik, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                yakinlastir(oran * d.getScaleFactor());
                return true;
            }
        });
        GestureDetector cift = new GestureDetector(etkinlik, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                yakinlastir(oran > 1.05f ? 1f : 2f);
                return true;
            }
        });
        cizim.setOnTouchListener((v, e) -> {
            iki.onTouchEvent(e);
            if (!iki.isInProgress()) cift.onTouchEvent(e);
            return true;
        });
    }

    private void yakinlastir(float istenen) {
        if (kamera == null) return;
        ZoomState z = kamera.getCameraInfo().getZoomState().getValue();
        float en = z == null ? 1f : z.getMinZoomRatio();
        float cok = z == null ? 1f : z.getMaxZoomRatio();
        float r = Math.max(en, Math.min(cok, istenen));
        oran = r;
        kamera.getCameraControl().setZoomRatio(r);
        yakinlik.setText(String.format(Locale.US, "%.1f×", r));
        yakinlik.setVisibility(View.VISIBLE);
        yakinlik.removeCallbacks(yakinligiGizle);
        yakinlik.postDelayed(yakinligiGizle, 1200);
    }

    private void toast(String mesaj) {
        Toast.makeText(etkinlik, mesaj, Toast.LENGTH_LONG).show();
    }
}
