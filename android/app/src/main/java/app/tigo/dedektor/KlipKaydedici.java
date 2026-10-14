package app.tigo.dedektor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Efektli video: kamera karesi ve üstündeki çizimler (yüz ağı, efektler, oyunlar) MP4'e yazılır;
 * en çok 10 saniye, sessiz. Kare yüz modelinin sonucunda kopyalanır; çizim arayüz iş
 * parçacığında kodlayıcının yüzeyine yapılır; kodlanan veri ayrı iş parçacığında dosyaya
 * yazılır, kayıt bitince galeriye (Movies/Dedektor) taşınır.
 */
final class KlipKaydedici {

    /** Kayıt kaydedildi (uri) ya da olmadı (hata); arayüz iş parçacığında. */
    interface Dinleyici {
        void bitti(Uri uri, Bitmap kucuk, String hata);
    }

    static final long EN_UZUN_MS = 10_000;
    private static final String ETIKET = "Dedektor";
    private static final String TUR = MediaFormat.MIMETYPE_VIDEO_AVC;
    /** Kodlayıcıların çoğunun kaldırdığı en büyük boyut (dik video). */
    private static final int EN_DAR = 720;
    private static final int EN_UZUN = 1280;

    private final Context baglam;
    private final CizimView cizim;
    private final Dinleyici dinleyici;
    private final Handler ana = new Handler(Looper.getMainLooper());
    /** Arayüz iş parçacığında çizilmeyi bekleyen kare var. */
    private final AtomicBoolean karede = new AtomicBoolean();
    private final Paint filtre = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect hedef = new Rect();
    private final Runnable bitirici = this::bitir;
    private volatile boolean kayitta;
    private volatile int yazilan;
    /** Yüz modelinden arayüze geçen kare (tek; karede bayrağıyla korunur). */
    private Bitmap tampon;
    private MediaCodec kodlayici;
    private Surface yuzey;
    private MediaMuxer muxer;
    private Thread bosaltici;
    private File dosya;
    private int genislik;
    private int yukseklik;
    private long baslangic;

    KlipKaydedici(Context baglam, CizimView cizim, Dinleyici dinleyici) {
        this.baglam = baglam.getApplicationContext();
        this.cizim = cizim;
        this.dinleyici = dinleyici;
    }

    boolean kayitta() {
        return kayitta;
    }

    long sureMs() {
        return kayitta ? SystemClock.uptimeMillis() - baslangic : 0;
    }

    /** Arayüz iş parçacığından. Kodlayıcı ilk karede (boyut belli olunca) kurulur. */
    void baslat() {
        if (kayitta) return;
        yazilan = 0;
        baslangic = SystemClock.uptimeMillis();
        kayitta = true;
        ana.postDelayed(bitirici, EN_UZUN_MS);
    }

    /** Kamera karesi (yüz modelinin iş parçacığında); kare yalnızca bu çağrı sırasında geçerli. */
    void kare(Bitmap kare) {
        if (!kayitta || !karede.compareAndSet(false, true)) return;
        Bitmap t = tampon;
        if (t == null || t.getWidth() != kare.getWidth() || t.getHeight() != kare.getHeight()) {
            t = Bitmap.createBitmap(kare.getWidth(), kare.getHeight(), Bitmap.Config.ARGB_8888);
            tampon = t;
        }
        new Canvas(t).drawBitmap(kare, 0, 0, null);
        Bitmap kopya = t;
        ana.post(() -> {
            try {
                ciz(kopya);
            } finally {
                karede.set(false);
            }
        });
    }

    private void ciz(Bitmap kare) {
        if (!kayitta) return;
        try {
            if (kodlayici == null) kur(kare.getWidth(), kare.getHeight());
            Canvas c = yuzey.lockHardwareCanvas();
            try {
                kareyiCiz(c, kare);
            } finally {
                yuzey.unlockCanvasAndPost(c);
            }
        } catch (IOException | RuntimeException e) {
            Log.e(ETIKET, "Video karesi yazılamadı", e);
            bitir();
        }
    }

    /** Kamera karesi (ön kamerada aynalı, önizleme gibi) ve üstüne çizimler. */
    private void kareyiCiz(Canvas c, Bitmap kare) {
        c.drawColor(Color.BLACK);
        hedef.set(0, 0, genislik, yukseklik);
        c.save();
        if (cizim.aynali()) c.scale(-1f, 1f, genislik / 2f, 0);
        c.drawBitmap(kare, null, hedef, filtre);
        c.restore();
        cizim.kareyeCiz(c, genislik, yukseklik);
    }

    private void kur(int g, int y) throws IOException {
        float o = Math.min(1f, Math.min(EN_UZUN / (float) Math.max(g, y), EN_DAR / (float) Math.min(g, y)));
        genislik = Math.max(16, Math.round(g * o) & ~15);
        yukseklik = Math.max(16, Math.round(y * o) & ~15);
        MediaFormat f = MediaFormat.createVideoFormat(TUR, genislik, yukseklik);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        f.setInteger(MediaFormat.KEY_BIT_RATE, 3_000_000);
        f.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        kodlayici = MediaCodec.createEncoderByType(TUR);
        kodlayici.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        yuzey = kodlayici.createInputSurface();
        kodlayici.start();
        dosya = new File(baglam.getCacheDir(), "klip.mp4");
        muxer = new MediaMuxer(dosya.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        MediaCodec k = kodlayici;
        MediaMuxer m = muxer;
        bosaltici = new Thread(() -> bosalt(k, m), "klip");
        bosaltici.start();
    }

    /** Kodlanan veriyi dosyaya yazar; akış sonu gelince (ya da kodlayıcı kapanınca) biter. */
    private void bosalt(MediaCodec k, MediaMuxer m) {
        MediaCodec.BufferInfo bilgi = new MediaCodec.BufferInfo();
        int iz = -1;
        try {
            while (true) {
                int i = k.dequeueOutputBuffer(bilgi, 20_000);
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    iz = m.addTrack(k.getOutputFormat());
                    m.start();
                } else if (i >= 0) {
                    ByteBuffer veri = k.getOutputBuffer(i);
                    boolean ayar = (bilgi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (!ayar && bilgi.size > 0 && iz >= 0 && veri != null) {
                        veri.position(bilgi.offset);
                        veri.limit(bilgi.offset + bilgi.size);
                        m.writeSampleData(iz, veri, bilgi);
                        yazilan++;
                    }
                    k.releaseOutputBuffer(i, false);
                    if ((bilgi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
                }
            }
        } catch (IllegalStateException e) {
            Log.w(ETIKET, "Video kodlayıcısı kapandı", e);
        }
    }

    /** Kaydı bitirir (arayüz iş parçacığından); kaydetme arka planda, sonuç dinleyiciye. */
    void bitir() {
        if (!kayitta) return;
        kayitta = false;
        ana.removeCallbacks(bitirici);
        Bitmap kucuk = kucukResim();
        MediaCodec k = kodlayici;
        Surface s = yuzey;
        MediaMuxer m = muxer;
        Thread t = bosaltici;
        File f = dosya;
        kodlayici = null;
        yuzey = null;
        muxer = null;
        bosaltici = null;
        new Thread(() -> kapat(k, s, m, t, f, kucuk), "klip-kaydet").start();
    }

    /** Son kareden çizimli küçük resim (son çekim düğmesi için). */
    private Bitmap kucukResim() {
        Bitmap kare = tampon;
        if (kare == null || genislik == 0) return null;
        try {
            float o = 160f / Math.min(genislik, yukseklik);
            Bitmap b = Bitmap.createBitmap(Math.round(genislik * o), Math.round(yukseklik * o), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.scale(o, o);
            kareyiCiz(c, kare);
            return b;
        } catch (RuntimeException e) {
            Log.w(ETIKET, "Video küçük resmi yapılamadı", e);
            return null;
        }
    }

    private void kapat(MediaCodec k, Surface s, MediaMuxer m, Thread t, File f, Bitmap kucuk) {
        Uri uri = null;
        String hata = null;
        try {
            if (k == null) throw new IllegalStateException("Kare gelmedi");
            k.signalEndOfInputStream();
            if (t != null) t.join(3000);
            if (yazilan == 0) throw new IllegalStateException("Kare yazılmadı");
            m.stop();
            m.release();
            m = null;
            uri = Galeri.videoKaydet(baglam, f);
        } catch (Exception e) {
            Log.e(ETIKET, "Video kaydedilemedi", e);
            hata = yazilan == 0 ? "Video kaydedilemedi: görüntü gelmedi." : "Video kaydedilemedi.";
        } finally {
            birak(k, s, m);
            if (f != null && f.exists() && !f.delete()) Log.w(ETIKET, "Geçici video silinemedi");
        }
        Uri sonuc = uri;
        String h = hata;
        ana.post(() -> dinleyici.bitti(sonuc, kucuk, h));
    }

    private static void birak(MediaCodec k, Surface s, MediaMuxer m) {
        try {
            if (k != null) {
                k.stop();
                k.release();
            }
        } catch (RuntimeException e) {
            Log.w(ETIKET, "Kodlayıcı kapatılamadı", e);
        }
        if (s != null) s.release();
        try {
            if (m != null) m.release();
        } catch (RuntimeException e) {
            Log.w(ETIKET, "Video dosyası kapatılamadı", e);
        }
    }
}
