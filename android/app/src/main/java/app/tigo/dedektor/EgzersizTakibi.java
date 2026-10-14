package app.tigo.dedektor;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.util.List;

/**
 * Egzersiz: vücut modelinin sonucundan duruşu sınıflandırır (bilgisayarda eğitilip APK'ya
 * konan model), tekrarları ya da plank süresini sayar; hedefi ve günlük geçmişi tutar.
 */
final class EgzersizTakibi {

    /** Sayı arttı (tekrar ya da plankta bir saniye). Vücut modelinin iş parçacığından çağrılır. */
    interface Dinleyici {
        /** hedef: hedefe şimdi ulaşıldı (bir kez). */
        void artti(int sayi, boolean hedef);
    }

    /** Hedef seçenekleri (Ayarlar.egzersizHedefi bu dizilerin sırasıdır; 0 = yok). */
    static final int[] TEKRAR_HEDEFLERI = {0, 10, 20, 30};
    static final int[] PLANK_HEDEFLERI = {0, 30, 60, 90};

    final EgzersizSayaci sayac = new EgzersizSayaci();
    final EgzersizGecmisi gecmis;
    volatile Dinleyici dinleyici;
    private final SinirAgi model;
    private volatile boolean gorunuyor;

    EgzersizTakibi(SinirAgi model, EgzersizGecmisi gecmis) {
        this.model = model;
        this.gecmis = gecmis;
    }

    static int hedefDegeri(EgzersizSayaci.Tur tur, int sira) {
        int[] h = tur.sureli() ? PLANK_HEDEFLERI : TEKRAR_HEDEFLERI;
        return h[Math.max(0, Math.min(sira, h.length - 1))];
    }

    /** Vücut modelinin sonucu: duruşu sınıflandırır ve sayar. */
    void vucut(PoseLandmarkerResult s, long zaman) {
        if (model == null || s.worldLandmarks().isEmpty() || s.landmarks().isEmpty()) {
            gorunuyor = false;
            sayac.guncelle(null, 0f, zaman);
            return;
        }
        // Bacaklar görünmüyorsa duruş güvenilir değil (ör. yalnızca üst beden kadrajda).
        List<NormalizedLandmark> n = s.landmarks().get(0);
        gorunuyor = n.size() > 28 && Math.max(gorunurluk(n.get(27)), gorunurluk(n.get(28))) >= 0.5f;
        if (!gorunuyor) {
            sayac.guncelle(null, 0f, zaman);
            return;
        }
        float[] p = model.calistir(YzOzellik.vucut(YzOzellik.noktalar(s.worldLandmarks().get(0))));
        int i = SinirAgi.enBuyukIndeks(p);
        if (!sayac.guncelle(model.etiketler[i], p[i], zaman)) return;
        gecmis.ekle(EgzersizGecmisi.bugun(), sayac.tur(), 1);
        Dinleyici d = dinleyici;
        boolean hedef = sayac.hedefeUlasti();
        if (d != null) d.artti(sayac.sayi(), hedef);
    }

    /** Kartın küçük yazısı: o anki duruş ya da neden sayılamadığı. */
    String durum() {
        if (model == null) return "Egzersiz modeli açılamadı";
        if (!gorunuyor) return "Bütün vücudun görünmüyor";
        if (sayac.tutuyor()) return "Tutuyorsun";
        String d = sayac.durum();
        return d == null ? "Duruş belirsiz" : d;
    }

    private static float gorunurluk(NormalizedLandmark l) {
        return l.visibility().orElse(1f);
    }
}
