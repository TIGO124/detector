import './style.css';
import { Capacitor, registerPlugin } from '@capacitor/core';
import { guncellemeAlaniniKur } from './guncellemeAlani';
import { webKamerasiniKur } from './webKamera';

/** Android uygulamasında kamera ve algılama native çalışır (KameraActivity.java); web'den kat kat hızlı. */
const Kamera = registerPlugin<{ ac(): Promise<void> }>('Kamera');

if (Capacitor.isPluginAvailable('Kamera')) {
  const baslat = document.querySelector<HTMLButtonElement>('#baslat')!;
  const mesaj = document.querySelector<HTMLElement>('#mesaj')!;
  baslat.addEventListener('click', () => {
    Kamera.ac().catch((hata: unknown) => {
      mesaj.textContent = `Kamera açılamadı: ${hata instanceof Error ? hata.message : String(hata)}`;
    });
  });
} else {
  webKamerasiniKur();
}

guncellemeAlaniniKur();
