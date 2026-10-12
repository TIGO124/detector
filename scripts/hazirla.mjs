// MediaPipe dosyalarını public/mediapipe altına hazırlar:
//  - wasm çalışma zamanı node_modules'tan kopyalanır
//  - yüz ağı, yüz (GPU doğrulaması için) ve el modelleri (yoksa) Google'ın
//    resmi deposundan indirilir
// Böylece uygulama (ve APK) internetsiz çalışır.
import { copyFileSync, existsSync, mkdirSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const kok = join(dirname(fileURLToPath(import.meta.url)), '..');
const hedef = join(kok, 'public', 'mediapipe');
const kaynakWasm = join(kok, 'node_modules', '@mediapipe', 'tasks-vision', 'wasm');

// Modeller worker'da çalıştığı için MediaPipe'ın ES modül sürümü kullanılır.
const wasmDosyalari = ['vision_wasm_module_internal.js', 'vision_wasm_module_internal.wasm'];

const modeller = {
  'face_landmarker.task':
    'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task',
  'blaze_face_short_range.tflite':
    'https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite',
  'hand_landmarker.task':
    'https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task',
  // Yalnızca Android (native): el hareketi, vücut iskeleti, arka plan ayırma
  'gesture_recognizer.task':
    'https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task',
  'pose_landmarker_lite.task':
    'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task',
  'selfie_segmenter.tflite':
    'https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite',
};

mkdirSync(join(hedef, 'wasm'), { recursive: true });
mkdirSync(join(hedef, 'models'), { recursive: true });

// Artık kullanılmayan eski dosyalar APK'ya girmesin.
for (const ad of readdirSync(join(hedef, 'wasm'))) {
  if (!wasmDosyalari.includes(ad)) rmSync(join(hedef, 'wasm', ad));
}

for (const ad of wasmDosyalari) {
  const kaynak = join(kaynakWasm, ad);
  if (!existsSync(kaynak)) throw new Error(`Bulunamadı: ${kaynak} (npm install çalıştırın)`);
  const varis = join(hedef, 'wasm', ad);
  if (!existsSync(varis) || statSync(varis).size !== statSync(kaynak).size) copyFileSync(kaynak, varis);
}

for (const [ad, adres] of Object.entries(modeller)) {
  const varis = join(hedef, 'models', ad);
  if (existsSync(varis) && statSync(varis).size > 0) continue;
  console.log(`İndiriliyor: ${ad}`);
  const yanit = await fetch(adres);
  if (!yanit.ok) throw new Error(`${ad} indirilemedi: HTTP ${yanit.status}`);
  writeFileSync(varis, Buffer.from(await yanit.arrayBuffer()));
}

console.log('MediaPipe dosyaları hazır.');
