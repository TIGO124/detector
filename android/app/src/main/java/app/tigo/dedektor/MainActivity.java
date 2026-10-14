package app.tigo.dedektor;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    /**
     * Uygulama zaten açıkken gelen başlatıcı açılışı (ör. kamera ekranının üstüne) ikinci bir
     * başlangıç ekranı yığmasın: bu kopya köprü kurulmadan kapanır, açık ekran olduğu gibi görünür.
     */
    private boolean kopya;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Uygulamaya özel eklenti: uygulama içi APK güncellemesi (ApkGuncellemePlugin)
        registerPlugin(ApkGuncellemePlugin.class);
        // Native kamera ve algılama ekranı (KameraActivity)
        registerPlugin(KameraPlugin.class);
        Intent niyet = getIntent();
        kopya = !isTaskRoot() && niyet != null && Intent.ACTION_MAIN.equals(niyet.getAction())
            && niyet.hasCategory(Intent.CATEGORY_LAUNCHER);
        super.onCreate(savedInstanceState);
        if (kopya) finish();
    }

    @Override
    protected void load() {
        if (!kopya) super.load();
    }

    @Override
    public void onDestroy() {
        if (kopya) {
            // Pencereye hiç bağlanmayan WebView'i Capacitor yok etmez (bunu pencereden ayrılırken
            // yapar): yok edilmezse ekranla birlikte bellekte kalır.
            View w = findViewById(com.getcapacitor.android.R.id.webview);
            if (w instanceof WebView) {
                ((ViewGroup) w.getParent()).removeView(w);
                ((WebView) w).destroy();
            }
        }
        super.onDestroy();
    }
}
