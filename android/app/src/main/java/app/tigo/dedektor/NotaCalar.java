package app.tigo.dedektor;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Piyano notaları (Do–Si, 4. oktav): her nota kısa, sönümlenen bir ses dalgası olarak bir kez
 * üretilir ve hazır tutulur; çalmak yalnızca başa sarıp oynatmaktır.
 */
final class NotaCalar {

    private static final int ORNEK_HIZI = 22050;
    private static final int SURE_MS = 450;
    private static final double[] FREKANSLAR = {261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88};

    private final AudioTrack[] izler = new AudioTrack[FREKANSLAR.length];

    synchronized void cal(int nota) {
        if (nota < 0 || nota >= izler.length) return;
        try {
            AudioTrack iz = izler[nota];
            if (iz == null) {
                iz = olustur(FREKANSLAR[nota]);
                izler[nota] = iz;
            } else {
                iz.stop();
                iz.reloadStaticData();
            }
            iz.play();
        } catch (RuntimeException e) {
            Log.w("Dedektor", "Nota çalınamadı", e);
        }
    }

    private static AudioTrack olustur(double frekans) {
        int n = ORNEK_HIZI * SURE_MS / 1000;
        short[] ses = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) ORNEK_HIZI;
            // Hızlı yükselme, üstel sönme; ikinci harmonik sesi biraz yumuşatır.
            double zarf = Math.min(1.0, t / 0.005) * Math.exp(-t * 6.0);
            double dalga = Math.sin(2 * Math.PI * frekans * t) + 0.3 * Math.sin(4 * Math.PI * frekans * t);
            ses[i] = (short) (zarf * dalga / 1.3 * 0.6 * Short.MAX_VALUE);
        }
        AudioTrack iz = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(ORNEK_HIZI)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(n * 2)
            .build();
        iz.write(ses, 0, n);
        return iz;
    }

    synchronized void kapat() {
        for (int i = 0; i < izler.length; i++) {
            if (izler[i] != null) izler[i].release();
            izler[i] = null;
        }
    }
}
