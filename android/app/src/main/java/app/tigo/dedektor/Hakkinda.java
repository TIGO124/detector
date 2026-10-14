package app.tigo.dedektor;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.io.IOException;
import java.io.InputStream;

/** Hakkında: sürüm, gizlilik, açık kaynak bileşenler ve yazı tiplerinin lisans metinleri. */
final class Hakkinda {

    private static final String METIN =
        "Yüz, el, vücut ve nesne algılama tamamen telefonda çalışır; kamera görüntüsü, fotoğraflar ve "
        + "öğrettiğin yapay zekâ verileri hiçbir yere gönderilmez. İnternet yalnızca yeni sürüm "
        + "denetimi ve indirmesi için kullanılır.\n\n"
        + "Açık kaynak bileşenler:\n"
        + "• MediaPipe Tasks (Google) — Apache 2.0\n"
        + "• LiteRT (Google) — Apache 2.0\n"
        + "• AndroidX, CameraX — Apache 2.0\n"
        + "• Capacitor (Ionic) — MIT\n\n"
        + "Yazı tipleri: Manrope ve Space Grotesk — SIL Open Font License 1.1.";

    private static final String[] LISANSLAR = {"lisanslar/OFL-Manrope.txt", "lisanslar/OFL-SpaceGrotesk.txt"};

    private Hakkinda() {}

    static void goster(Context baglam) {
        String surum = "";
        try {
            surum = " " + baglam.getPackageManager().getPackageInfo(baglam.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w("Dedektor", "Sürüm okunamadı", e);
        }
        new AlertDialog.Builder(baglam)
            .setTitle("Dedektör" + surum)
            .setMessage(METIN)
            .setNeutralButton("Lisans metinleri", (d, w) -> lisanslar(baglam))
            .setPositiveButton("Tamam", null)
            .show();
    }

    private static void lisanslar(Context baglam) {
        StringBuilder s = new StringBuilder();
        for (String yol : LISANSLAR) {
            try (InputStream g = baglam.getAssets().open(yol)) {
                if (s.length() > 0) s.append("\n\n────────\n\n");
                s.append(YzDosyalari.metin(g).trim());
            } catch (IOException e) {
                Log.w("Dedektor", "Lisans okunamadı: " + yol, e);
            }
        }
        float dp = baglam.getResources().getDisplayMetrics().density;
        TextView t = new TextView(baglam);
        t.setText(s.length() == 0 ? "Lisans metinleri bulunamadı." : s);
        t.setTextSize(12);
        t.setTextColor(Cip.YAZI_CAM);
        t.setTextIsSelectable(true);
        int ic = Math.round(20 * dp);
        t.setPadding(ic, Math.round(8 * dp), ic, 0);
        ScrollView kaydirma = new ScrollView(baglam);
        kaydirma.addView(t);
        new AlertDialog.Builder(baglam)
            .setTitle("Lisanslar")
            .setView(kaydirma)
            .setPositiveButton("Tamam", null)
            .show();
    }
}
