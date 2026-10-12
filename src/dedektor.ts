import {
  FaceDetector,
  FaceLandmarker,
  FilesetResolver,
  HandLandmarker,
  type FaceLandmarkerResult,
  type HandLandmarkerResult,
} from '@mediapipe/tasks-vision';

const temel = `${import.meta.env.BASE_URL}mediapipe`;

export interface Sonuc {
  yuz: FaceLandmarkerResult;
  el: HandLandmarkerResult;
}

type Hizlandirici = 'GPU' | 'CPU';
type Wasm = Awaited<ReturnType<typeof FilesetResolver.forVisionTasks>>;

const yuzOlustur = (wasm: Wasm, delegate: Hizlandirici) =>
  FaceLandmarker.createFromOptions(wasm, {
    baseOptions: { modelAssetPath: `${temel}/models/face_landmarker.task`, delegate },
    runningMode: 'VIDEO',
    numFaces: 2,
    minFaceDetectionConfidence: 0.6,
    minFacePresenceConfidence: 0.5,
    minTrackingConfidence: 0.5,
  });

/** GPU doğrulaması için hafif, CPU'da çalışan yüz bulucu. */
const kontrolOlustur = (wasm: Wasm) =>
  FaceDetector.createFromOptions(wasm, {
    baseOptions: { modelAssetPath: `${temel}/models/blaze_face_short_range.tflite`, delegate: 'CPU' },
    runningMode: 'IMAGE',
    minDetectionConfidence: 0.6,
  });

const elOlustur = (wasm: Wasm, delegate: Hizlandirici) =>
  HandLandmarker.createFromOptions(wasm, {
    baseOptions: { modelAssetPath: `${temel}/models/hand_landmarker.task`, delegate },
    runningMode: 'VIDEO',
    numHands: 2,
    minHandDetectionConfidence: 0.5,
    minHandPresenceConfidence: 0.5,
    minTrackingConfidence: 0.5,
  });

/** GPU sonucunu CPU ile kıyaslamak için tutulan sayaçlar. */
interface Dogrulama {
  kontrol: FaceDetector;
  kare: number;
  uyum: number;
  uyumsuz: number;
}

/** Yüz ve el modellerini birlikte çalıştırır. */
export class Dedektor {
  private sonZaman = 0;
  private gecisVar = false;

  private constructor(
    private readonly wasm: Wasm,
    private yuz: FaceLandmarker,
    private el: HandLandmarker,
    public hizlandirici: Hizlandirici,
    private dogrulama: Dogrulama | null,
  ) {}

  static async olustur(): Promise<Dedektor> {
    const wasm = await FilesetResolver.forVisionTasks(`${temel}/wasm`);
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

  /** Bir video karesinde yüz ve elleri bulur. */
  tespit(kare: HTMLVideoElement): Sonuc {
    // MediaPipe VIDEO modunda zaman damgası kesin artan olmalı.
    this.sonZaman = Math.max(this.sonZaman + 1, performance.now());
    const sonuc = {
      yuz: this.yuz.detectForVideo(kare, this.sonZaman),
      el: this.el.detectForVideo(kare, this.sonZaman),
    };
    this.dogrula(kare, sonuc);
    return sonuc;
  }

  /**
   * Bazı cihazlarda (ör. emülatör) GPU hata vermeden boş sonuç döndürür.
   * Her 10 karede bir yüzü CPU ile de arar; CPU yüz bulup GPU 3 kez bulamazsa
   * CPU'ya geçer, 3 kez ikisi de bulursa GPU'yu doğrulanmış sayar.
   */
  private dogrula(kare: HTMLVideoElement, sonuc: Sonuc) {
    const d = this.dogrulama;
    if (!d || ++d.kare % 10 !== 0) return;
    if (d.kontrol.detect(kare).detections.length === 0) return;
    if (sonuc.yuz.faceLandmarks.length > 0) d.uyum++;
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
