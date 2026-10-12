package app.tigo.dedektor;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Uygulamaya özel eklenti: uygulama içi APK güncellemesi (ApkGuncellemePlugin)
        registerPlugin(ApkGuncellemePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
