import './style.css';
import { ciz } from './ciz';
import { Dedektor } from './dedektor';
import { guncellemeAlaniniKur } from './guncellemeAlani';

const video = document.querySelector<HTMLVideoElement>('#video')!;
const tuval = document.querySelector<HTMLCanvasElement>('#tuval')!;
const ctx = tuval.getContext('2d')!;
const giris = document.querySelector<HTMLElement>('#giris')!;
const baslat = document.querySelector<HTMLButtonElement>('#baslat')!;
const mesaj = document.querySelector<HTMLElement>('#mesaj')!;
const ust = document.querySelector<HTMLElement>('#ust')!;
const yuzSayisi = document.querySelector<HTMLElement>('#yuz-sayisi')!;
const elSayisi = document.querySelector<HTMLElement>('#el-sayisi')!;
const fps = document.querySelector<HTMLElement>('#fps')!;
const cevir = document.querySelector<HTMLButtonElement>('#cevir')!;

let dedektor: Dedektor | null = null;
let yon: 'user' | 'environment' = 'user';
let aynali = true;
let akis: MediaStream | null = null;
let sonKareZamani = -1;
let fpsOrt = 0;
let oncekiAn = 0;

function hataMesaji(hata: unknown): string {
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    return 'Kamera bu bağlantıda kullanılamıyor. Uygulamayı APK olarak ya da HTTPS üzerinden açın.';
  }
  const ad = hata instanceof DOMException ? hata.name : '';
  if (ad === 'NotAllowedError') return 'Kamera izni verilmedi. Ayarlardan kamera iznini açıp tekrar deneyin.';
  if (ad === 'NotFoundError' || ad === 'OverconstrainedError') return 'Kamera bulunamadı.';
  if (ad === 'NotReadableError') return 'Kamera başka bir uygulama tarafından kullanılıyor.';
  return `Başlatılamadı: ${hata instanceof Error ? hata.message : String(hata)}`;
}

async function kamerayiAc() {
  akis?.getTracks().forEach((t) => t.stop());
  akis = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: { facingMode: yon, width: { ideal: 1280 }, height: { ideal: 720 } },
  });
  video.srcObject = akis;
  await video.play();
  // Masaüstü kameraları facingMode bildirmez; onlar da kullanıcıya bakar.
  const gercekYon = akis.getVideoTracks()[0]?.getSettings().facingMode;
  aynali = (gercekYon ?? yon) !== 'environment';
  video.classList.toggle('aynali', aynali);
  sonKareZamani = -1;
}

function kare() {
  try {
    if (dedektor && video.readyState >= 2 && video.currentTime !== sonKareZamani) {
      sonKareZamani = video.currentTime;
      if (tuval.width !== video.videoWidth || tuval.height !== video.videoHeight) {
        tuval.width = video.videoWidth;
        tuval.height = video.videoHeight;
      }
      const sonuc = dedektor.tespit(video);
      ciz(ctx, sonuc, aynali);
      yuzSayisi.textContent = String(sonuc.yuz.faceLandmarks.length);
      elSayisi.textContent = String(sonuc.el.landmarks.length);

      const an = performance.now();
      if (oncekiAn) fpsOrt = fpsOrt * 0.9 + (1000 / (an - oncekiAn)) * 0.1;
      oncekiAn = an;
      fps.textContent = `${Math.round(fpsOrt)} FPS · ${dedektor.hizlandirici}`;
    }
  } catch (hata) {
    console.error(hata);
  } finally {
    sonrakiKare();
  }
}

function sonrakiKare() {
  if ('requestVideoFrameCallback' in video) video.requestVideoFrameCallback(kare);
  else requestAnimationFrame(kare);
}

baslat.addEventListener('click', async () => {
  baslat.disabled = true;
  mesaj.textContent = 'Modeller yükleniyor…';
  try {
    dedektor ??= await Dedektor.olustur();
    mesaj.textContent = 'Kamera açılıyor…';
    await kamerayiAc();
    giris.hidden = true;
    ust.hidden = false;
    cevir.hidden = false;
    sonrakiKare();
  } catch (hata) {
    console.error(hata);
    mesaj.textContent = hataMesaji(hata);
    baslat.textContent = 'Tekrar dene';
    baslat.disabled = false;
  }
});

cevir.addEventListener('click', async () => {
  cevir.disabled = true;
  const onceki = yon;
  yon = yon === 'user' ? 'environment' : 'user';
  try {
    await kamerayiAc();
  } catch (hata) {
    console.error(hata);
    yon = onceki;
    await kamerayiAc().catch(console.error);
  } finally {
    cevir.disabled = false;
  }
});

guncellemeAlaniniKur();
