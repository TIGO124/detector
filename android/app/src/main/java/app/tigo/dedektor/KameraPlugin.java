package app.tigo.dedektor;

import android.content.Intent;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Web'deki "Başlat" düğmesi native kamera ve algılama ekranını açar. */
@CapacitorPlugin(name = "Kamera")
public class KameraPlugin extends Plugin {

    @PluginMethod
    public void ac(PluginCall call) {
        getActivity().startActivity(new Intent(getContext(), KameraActivity.class));
        call.resolve();
    }
}
