// Yüz ve el modellerini ana iş parçacığından ayrı çalıştırır: kamera görüntüsü ve dokunmalar
// model hesaplarken takılmaz. Ana iş parçacığı kare (ImageBitmap) gönderir, sonuç alır.
import { Dedektor, type Sonuc } from './dedektor';

export type IsciIstegi = { tur: 'baslat' } | { tur: 'kare'; kare: ImageBitmap };

export type IsciCevabi =
  | { tur: 'hazir' }
  | { tur: 'sonuc'; sonuc: Sonuc; hizlandirici: 'GPU' | 'CPU' }
  | { tur: 'hata'; mesaj: string };

let dedektor: Dedektor | null = null;
const gonder = (cevap: IsciCevabi) => self.postMessage(cevap);

self.onmessage = async ({ data }: MessageEvent<IsciIstegi>) => {
  if (data.tur === 'baslat') {
    try {
      dedektor ??= await Dedektor.olustur();
      gonder({ tur: 'hazir' });
    } catch (hata) {
      gonder({ tur: 'hata', mesaj: hata instanceof Error ? hata.message : String(hata) });
    }
    return;
  }
  try {
    if (!dedektor) throw new Error('Dedektör hazır değil');
    gonder({ tur: 'sonuc', sonuc: dedektor.tespit(data.kare), hizlandirici: dedektor.hizlandirici });
  } catch (hata) {
    gonder({ tur: 'hata', mesaj: hata instanceof Error ? hata.message : String(hata) });
  } finally {
    data.kare.close();
  }
};
