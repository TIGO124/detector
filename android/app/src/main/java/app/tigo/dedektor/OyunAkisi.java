package app.tigo.dedektor;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Taş-kağıt-makas turu: geri sayım, oyuncunun el hareketini okuma, sonuç ve skor. */
final class OyunAkisi {

    /** O anki el hareketinden oyuncunun seçimi; el görünmüyorsa null. */
    interface ElOkuyucu {
        TasKagitMakas.Secim secim();
    }

    private static final long ADIM_MS = 600;
    private static final String[] SAYIM = {"Taş…", "Kağıt…", "Makas…", "Göster!"};

    private final Handler isleyici = new Handler(Looper.getMainLooper());
    private final Random rastgele = new Random();
    private final List<TasKagitMakas.Secim> okumalar = new ArrayList<>();
    private final TextView yazi;
    private final ElOkuyucu okuyucu;
    private TextView skor;
    private int oyuncuPuani;
    private int telefonPuani;
    private boolean oynaniyor;

    OyunAkisi(TextView yazi, ElOkuyucu okuyucu) {
        this.yazi = yazi;
        this.okuyucu = okuyucu;
    }

    /** Skor yazısı mod paneli her kurulduğunda yeniden bağlanır. */
    void skorBagla(TextView skor) {
        this.skor = skor;
        skoruYaz();
    }

    void oyna() {
        if (oynaniyor) return;
        oynaniyor = true;
        for (int i = 0; i < SAYIM.length; i++) {
            String s = SAYIM[i];
            isleyici.postDelayed(() -> goster(s), i * ADIM_MS);
        }
        // "Göster!" dedikten sonra el dört kez okunur, çoğunluk karar verir: el hareketi
        // oturana kadarki ara kareler ya da tek bir yanlış okuma sonucu bozmaz.
        okumalar.clear();
        long goster = (SAYIM.length - 1) * ADIM_MS;
        for (long ms = 250; ms <= 700; ms += 150) isleyici.postDelayed(() -> okumalar.add(okuyucu.secim()), goster + ms);
        isleyici.postDelayed(this::bitir, goster + 750);
    }

    void durdur() {
        isleyici.removeCallbacksAndMessages(null);
        oynaniyor = false;
        yazi.setVisibility(View.GONE);
    }

    private void bitir() {
        oynaniyor = false;
        TasKagitMakas.Secim oyuncu = TasKagitMakas.cogunluk(okumalar);
        if (oyuncu == null) {
            goster("El görülmedi\nTekrar dene");
            return;
        }
        TasKagitMakas.Secim telefon = TasKagitMakas.Secim.values()[rastgele.nextInt(3)];
        int sonuc = TasKagitMakas.sonuc(oyuncu, telefon);
        if (sonuc > 0) oyuncuPuani++;
        else if (sonuc < 0) telefonPuani++;
        String karar = sonuc > 0 ? "Kazandın!" : sonuc < 0 ? "Kaybettin" : "Berabere";
        goster("Sen: " + oyuncu.ad + "\nTelefon: " + telefon.ad + "\n" + karar);
        skoruYaz();
    }

    private void goster(String metin) {
        yazi.setText(metin);
        yazi.setVisibility(View.VISIBLE);
    }

    private void skoruYaz() {
        if (skor != null) skor.setText("Sen " + oyuncuPuani + " · " + telefonPuani + " Telefon");
    }
}
