import {
  FaceDetector,
  FaceLandmarker,
  FilesetResolver,
  HandLandmarker,
  type FaceLandmarkerResult,
  type HandLandmarkerResult,
} from '@mediapipe/tasks-vision';

// Tam adres: Vite geliştirme sunucusu "/" ile başlayan import() yollarına ?import ekleyip bozuyor.
const temel = new URL(`${import.meta.env.BASE_URL}mediapipe`, self.location.href).href;

/** Çizim için gereken sonuçlar; worker'dan ana iş parçacığına bu kadarı gönderilir. */
export interface Sonuc {
  yuz: Pick<FaceLandmarkerResult, 'faceLandmarks'>;
  el: Pick<HandLandmarkerResult, 'landmarks' | 'handedness'>;
}

type Hizlandirici = 'GPU' | 'CPU';
type Wasm = Awaited<ReturnType<typeof FilesetResolver.forVisionTasks>>;

let fabrika: unknown;

/**
 * MediaPipe'ın modül yükleyicisi globalThis.ModuleFactory'yi yalnızca modül ilk import edildiğinde
 * kurar ve her görev oluşturulunca siler. Modül önbellekten geldiği için yeniden kurulmaz; bu yüzden
 * fabrika bir kez alınır ve her görevden önce geri konur (yoksa CPU'ya geçişte "ModuleFactory not set").
 */
async function fabrikaYukle() {
  const modul = await import(/* @vite-ignore */ `${temel}/wasm/vision_wasm_module_internal.js`);
  fabrika ??= modul.default;
}

function fabrikaKur() {
  (self as unknown as { ModuleFactory?: unknown }).ModuleFactory = fabrika;
}

function yuzOlustur(wasm: Wasm, delegate: Hizlandirici) {
  fabrikaKur();
  return FaceLandmarker.createFromOptions(wasm, {
    baseOptions: { modelAssetPath: `${temel}/models/face_landmarker.task`, delegate },
    runningMode: 'VIDEO',
    numFaces: 1,
    minFaceDetectionConfidence: 0.6,
    minFacePresenceConfidence: 0.5,
    minTrackingConfidence: 0.5,
  });
}

/** GPU doğrulaması için hafif, CPU'da çalışan yüz bulucu. */
function kontrolOlustur(wasm: Wasm) {
  fabrikaKur();
  return FaceDetector.createFromOptions(wasm, {
    baseOptions: { modelAssetPath: `${temel}/models/blaze_face_short_range.tflite`, delegate: 'CPU' },
    runningMode: 'IMAGE',
    minDetectionConfidence: 0.6,
  });
}

function elOlustur(wasm: Wasm, delegate: Hizlandirici) {
  fabrikaKur();
  return HandLandmarker.createFromOptions(wasm, {
    baseOptions: { modelAssetPath: `${temel}/models/hand_landmarker.task`, delegate },
    runningMode: 'VIDEO',
    numHands: 2,
    minHandDetectionConfidence: 0.5,
    minHandPresenceConfidence: 0.5,
    minTrackingConfidence: 0.5,
  });
}

/** GPU sonucunu CPU ile kıyaslamak için tutulan sayaçlar. */
interface Dogrulama {
  kontrol: FaceDetector;
  kare: number;
  uyum: number;
  uyumsuz: number;
}

/** Yüz ve el modellerini birlikte çalıştırır. Ana iş parçacığını kilitlememek için worker'da (isci.ts) çalışır. */
export class Dedektor {
  private sonZaman = 0;
  private gecisVar = false;
  private sira = 0;
  private sonuc: Sonuc = { yuz: { faceLandmarks: [] }, el: { landmarks: [], handedness: [] } };

  private constructor(
    private readonly wasm: Wasm,
    private yuz: FaceLandmarker,
    private el: HandLandmarker,
    public hizlandirici: Hizlandirici,
    private dogrulama: Dogrulama | null,
  ) {}

  static async olustur(): Promise<Dedektor> {
    // Modül sürümü: worker'da importScripts yerine import() ile yüklenir.
    const wasm = await FilesetResolver.forVisionTasks(`${temel}/wasm`, true);
    await fabrikaYukle();
    try {
      const [yuz, el, kontrol] = await Promise.all([
        yuzOlustur(wasm, 'GPU'),
        elOlustur(wasm, 'GPU'),
        kontrolOlustur(wasm),
      ]);
      return new Dedektor(wasm, yuz, el, 'GPU', { kontrol, kare: 0, uyum: 0, uyumsuz: 0 });
    } catch (hata) {
      console.warn('GPU kullanılamadı, CPU kullanılıyor:', hata);
      const [yuz, el] = await Promise.all([yuzOlustur(wasm, 'CPU'), elOlustur(wasm, 'CPU')]);
      return new Dedektor(wasm, yuz, el, 'CPU', null);
    }
  }

  /**
   * Bir video karesinde yüzü ya da elleri bulur: modeller sırayla çalışır. Her kare tek model
   * çalıştığı için çizim iki kat sık güncellenir ve her sonuç daha taze kareden gelir.
   */
  tespit(kare: ImageBitmap): Sonuc {
    // MediaPipe VIDEO modunda zaman damgası kesin artan olmalı.
    this.sonZaman = Math.max(this.sonZaman + 1, performance.now());
    if (this.sira++ % 2 === 0) {
      this.sonuc.yuz = { faceLandmarks: this.yuz.detectForVideo(kare, this.sonZaman).faceLandmarks };
      this.dogrula(kare);
    } else {
      const el = this.el.detectForVideo(kare, this.sonZaman);
      this.sonuc.el = { landmarks: el.landmarks, handedness: el.handedness };
    }
    return this.sonuc;
  }

  /**
   * Bazı cihazlarda (ör. emülatör) GPU hata vermeden boş sonuç döndürür.
   * Her 10 yüz karesinde bir yüzü CPU ile de arar; CPU yüz bulup GPU 3 kez bulamazsa
   * CPU'ya geçer, 3 kez ikisi de bulursa GPU'yu doğrulanmış sayar.
   */
  private dogrula(kare: ImageBitmap) {
    const d = this.dogrulama;
    if (!d || ++d.kare % 10 !== 0) return;
    if (d.kontrol.detect(kare).detections.length === 0) return;
    if (this.sonuc.yuz.faceLandmarks.length > 0) d.uyum++;
    else d.uyumsuz++;
    if (d.uyum >= 3) {
      d.kontrol.close();
      this.dogrulama = null;
    } else if (d.uyumsuz >= 3) {
      this.cpuyaGec().catch((hata) => console.error(hata));
    }
  }

  private async cpuyaGec() {
    if (this.gecisVar) return;
    this.gecisVar = true;
    console.warn('GPU sonuçları hatalı görünüyor, CPU kullanılıyor.');
    const [yuz, el] = await Promise.all([yuzOlustur(this.wasm, 'CPU'), elOlustur(this.wasm, 'CPU')]);
    this.yuz.close();
    this.el.close();
    this.dogrulama?.kontrol.close();
    this.yuz = yuz;
    this.el = el;
    this.hizlandirici = 'CPU';
    this.dogrulama = null;
  }
}
