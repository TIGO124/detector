package app.tigo.dedektor;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Sesli okuma (Türkçe metin okuma), titreşim, kısa sesler ve uyku alarmı. Ayarlardaki "Sesli
 * geri bildirim" ve "Titreşim" anahtarlarına uyar. Metin okuma motoru ilk gerektiğinde açılır;
 * telefonda Türkçe ses yoksa bir kez bildirilir. Her iş parçacığından çağrılabilir.
 */
final class Geribildirim implements TextToSpeech.OnInitListener {

    private static final String ETIKET = "Dedektor";
    /** Aynı söz bu süre içinde tekrar okunmaz (ör. her karede aynı kişi). */
    private static final long TEKRAR_MS = 4000;

    private final Context baglam;
    private final Ayarlar ayarlar;
    /** Kullanıcıya kısa bilgi (ör. "Türkçe ses yok"); arayüz iş parçacığına kendisi geçer. */
    private final Consumer<String> bildir;
    private TextToSpeech tts;
    /** null: hazır değil; true: Türkçe hazır; false: kullanılamıyor. */
    private Boolean turkce;
    private String sonSoz;
    private long sonSozZamani;
    private ToneGenerator ton;
    private ToneGenerator alarmTonu;
    private boolean alarmda;
    /** Ekran kapandı: geç gelen model sonuçları ses kaynağını yeniden açmasın. */
    private boolean kapandi;

    Geribildirim(Context baglam, Ayarlar ayarlar, Consumer<String> bildir) {
        this.baglam = baglam.getApplicationContext();
        this.ayarlar = ayarlar;
        this.bildir = bildir;
    }

    /** Metni Türkçe okur (sesli geri bildirim açıksa). Sıraya girmez: yeni söz eskisini keser. */
    synchronized void soyle(String metin) {
        if (kapandi || !ayarlar.sesli || metin == null || metin.isEmpty()) return;
        long simdi = SystemClock.uptimeMillis();
        if (metin.equals(sonSoz) && simdi - sonSozZamani < TEKRAR_MS) return;
        sonSoz = metin;
        sonSozZamani = simdi;
        if (tts == null) {
            tts = new TextToSpeech(baglam, this);
            return;
        }
        if (Boolean.TRUE.equals(turkce)) tts.speak(metin, TextToSpeech.QUEUE_FLUSH, null, "dedektor");
    }

    @Override
    public synchronized void onInit(int durum) {
        if (tts == null) return;
        if (durum != TextToSpeech.SUCCESS) {
            turkce = false;
            bildir.accept("Metin okuma motoru açılamadı.");
            return;
        }
        int sonuc = tts.setLanguage(new Locale("tr", "TR"));
        turkce = sonuc != TextToSpeech.LANG_MISSING_DATA && sonuc != TextToSpeech.LANG_NOT_SUPPORTED;
        Log.i(ETIKET, "Metin okuma: Türkçe " + (turkce ? "hazır" : "yok (" + sonuc + ")"));
        if (!turkce) {
            bildir.accept("Telefonda Türkçe ses yok. Ayarlar › Metin okuma bölümünden Türkçe sesi indirebilirsin.");
        } else if (sonSoz != null) {
            tts.speak(sonSoz, TextToSpeech.QUEUE_FLUSH, null, "dedektor");
        }
    }

    /** Kısa titreşim (titreşim açıksa). */
    void titret(int ms) {
        if (!ayarlar.titresim) return;
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) baglam.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm == null ? null : vm.getDefaultVibrator();
            } else {
                v = (Vibrator) baglam.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        } catch (RuntimeException e) {
            Log.w(ETIKET, "Titreşim olmadı", e);
        }
    }

    /** Kısa ses (ToneGenerator tonu). */
    synchronized void ses(int tur, int ms) {
        if (kapandi) return;
        try {
            if (ton == null) ton = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            ton.startTone(tur, ms);
        } catch (RuntimeException e) {
            Log.w(ETIKET, "Ses çalınamadı", e);
        }
    }

    /** Uyku alarmı: açılınca durdurulana kadar çalar. */
    synchronized void alarm(boolean ac) {
        if (ac == alarmda || (ac && kapandi)) return;
        alarmda = ac;
        try {
            if (ac) {
                if (alarmTonu == null) alarmTonu = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                alarmTonu.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK);
            } else if (alarmTonu != null) {
                alarmTonu.stopTone();
            }
        } catch (RuntimeException e) {
            Log.w(ETIKET, "Alarm çalınamadı", e);
        }
    }

    synchronized void kapat() {
        alarm(false);
        kapandi = true;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (ton != null) ton.release();
        if (alarmTonu != null) alarmTonu.release();
        ton = null;
        alarmTonu = null;
    }
}
