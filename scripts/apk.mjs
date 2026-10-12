// Android APK üretir:
//  1. Web uygulamasını derler ve Capacitor ile android/ klasörüne aktarır
//     (Android algılamayı native yaptığı için web'in wasm dosyaları APK'ya girmez)
//  2. Kamera iznini AndroidManifest.xml'e ekler (yoksa)
//  3. Debug APK derler, Masaüstüne kopyalar; USB ile telefon bağlıysa kurar
import { execSync } from 'node:child_process';
import { copyFileSync, existsSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const kok = join(dirname(fileURLToPath(import.meta.url)), '..');
const calistir = (komut, secenek = {}) => execSync(komut, { cwd: kok, stdio: 'inherit', ...secenek });

if (!existsSync(join(kok, 'android'))) calistir('npx cap add android');
calistir('npm run build');
// Native taraf kendi modellerini kullanır (assets/public/mediapipe/models); web'e ait olanlar APK'ya girmez.
rmSync(join(kok, 'dist', 'mediapipe', 'wasm'), { recursive: true, force: true });
for (const model of ['blaze_face_short_range.tflite', 'hand_landmarker.task']) {
  rmSync(join(kok, 'dist', 'mediapipe', 'models', model), { force: true });
}
calistir('npx cap sync android');

const manifest = join(kok, 'android', 'app', 'src', 'main', 'AndroidManifest.xml');
let xml = readFileSync(manifest, 'utf8');
if (!xml.includes('android.permission.CAMERA')) {
  xml = xml.replace(
    '</manifest>',
    '    <uses-permission android:name="android.permission.CAMERA" />\n' +
      '    <uses-feature android:name="android.hardware.camera" android:required="false" />\n' +
      '</manifest>',
  );
  writeFileSync(manifest, xml);
  console.log('Kamera izni AndroidManifest.xml dosyasına eklendi.');
}

const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || join(homedir(), 'AppData', 'Local', 'Android', 'Sdk');
const yerel = join(kok, 'android', 'local.properties');
if (!existsSync(yerel)) writeFileSync(yerel, `sdk.dir=${sdk.replaceAll('\\', '\\\\')}\n`);

// Gradle sistemdeki yeni JDK ile çalışmayabilir; varsa Android Studio'nun JDK'sını kullan.
const jbr = 'C:\\Program Files\\Android\\Android Studio\\jbr';
const env = { ...process.env, ...(existsSync(jbr) && { JAVA_HOME: jbr }) };
// Önce native birim testleri (parmak sayma, ifade, oyun...), sonra APK.
const gorevler = 'testDebugUnitTest assembleDebug --no-daemon -q';
calistir(process.platform === 'win32' ? `.\\gradlew.bat ${gorevler}` : `./gradlew ${gorevler}`, {
  cwd: join(kok, 'android'),
  env,
});

const apk = join(kok, 'android', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
const masaustu = join(homedir(), 'Desktop', 'Dedektor.apk');
copyFileSync(apk, masaustu);
console.log(`\nAPK hazır: ${masaustu}`);

const adb = join(sdk, 'platform-tools', 'adb');
try {
  const cihazlar = execSync(`"${adb}" devices`, { encoding: 'utf8' })
    .split('\n')
    .slice(1)
    .filter((satir) => /\tdevice\s*$/.test(satir));
  if (cihazlar.length === 1) {
    console.log('Bağlı telefona kuruluyor…');
    execSync(`"${adb}" install -r "${apk}"`, { stdio: 'inherit' });
  } else {
    console.log('Telefona kurmak için APK dosyasını telefona atıp açın (ya da USB hata ayıklama ile bağlayıp tekrar çalıştırın).');
  }
} catch {
  console.log('adb bulunamadı; APK dosyasını telefona atıp açın.');
}
