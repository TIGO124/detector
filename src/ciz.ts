import { FaceLandmarker, HandLandmarker, type NormalizedLandmark } from '@mediapipe/tasks-vision';
import type { Sonuc } from './dedektor';

const YESIL = '#00e676';
const YESIL_SOLUK = 'rgba(0, 230, 118, 0.35)';
const YESIL_ACIK = '#b9f6ca';

const YUZ_HATLARI = [
  ...FaceLandmarker.FACE_LANDMARKS_CONTOURS,
  ...FaceLandmarker.FACE_LANDMARKS_LEFT_IRIS,
  ...FaceLandmarker.FACE_LANDMARKS_RIGHT_IRIS,
];

/**
 * MediaPipe el etiketini Türkçeye çevirir. Modele ham (aynalanmamış) kamera
 * karesi verildiği için etiket kişinin gerçek elini gösterir.
 */
export function elEtiketi(kategori: string | undefined): 'Sağ el' | 'Sol el' | 'El' {
  if (kategori === 'Right') return 'Sağ el';
  if (kategori === 'Left') return 'Sol el';
  return 'El';
}

/** Normalize x koordinatını (0–1) ekrandaki konuma çevirir. */
export function xEkran(x: number, aynali: boolean): number {
  return aynali ? 1 - x : x;
}

interface Alan {
  x: number;
  y: number;
  w: number;
  h: number;
}

/** object-fit: cover ile kırpılan tuvalin ekranda görünen kısmı (tuval pikseli). */
function gorunurAlan(tuval: HTMLCanvasElement): Alan {
  const { width: W, height: H, clientWidth: cw, clientHeight: ch } = tuval;
  if (!cw || !ch) return { x: 0, y: 0, w: W, h: H };
  const olcek = Math.max(cw / W, ch / H);
  const w = cw / olcek;
  const h = ch / olcek;
  return { x: (W - w) / 2, y: (H - h) / 2, w, h };
}

const sinirla = (d: number, min: number, max: number) => Math.min(Math.max(d, min), max);

function etiket(ctx: CanvasRenderingContext2D, alan: Alan, metin: string, x: number, y: number, renk: string, boyut: number) {
  ctx.font = `600 ${boyut}px system-ui, sans-serif`;
  const pay = boyut * 0.35;
  const g = ctx.measureText(metin).width + pay * 2;
  const h = boyut + pay * 2;
  const sx = sinirla(x, alan.x, alan.x + alan.w - g);
  const sy = sinirla(y - h, alan.y, alan.y + alan.h - h);
  ctx.fillStyle = renk;
  ctx.beginPath();
  ctx.roundRect(sx, sy, g, h, h / 3);
  ctx.fill();
  ctx.fillStyle = '#0b1120';
  ctx.textBaseline = 'middle';
  ctx.fillText(metin, sx + pay, sy + h / 2);
}

type Baglanti = { start: number; end: number };

/** Noktalar arasındaki bağlantıları tek yol olarak çizer. */
function baglantilar(
  ctx: CanvasRenderingContext2D,
  noktalar: NormalizedLandmark[],
  liste: Baglanti[],
  px: (n: NormalizedLandmark) => number,
) {
  const H = ctx.canvas.height;
  ctx.beginPath();
  for (const { start, end } of liste) {
    ctx.moveTo(px(noktalar[start]), noktalar[start].y * H);
    ctx.lineTo(px(noktalar[end]), noktalar[end].y * H);
  }
  ctx.stroke();
}

/** Tespit sonuçlarını yeşil çizgilerle tuvale çizer. Tuval boyutu video karesiyle aynı olmalı. */
export function ciz(ctx: CanvasRenderingContext2D, sonuc: Sonuc, aynali: boolean) {
  const { width: W, height: H } = ctx.canvas;
  const cizgi = Math.max(2, Math.min(W, H) / 180);
  const yazi = Math.max(14, Math.round(Math.min(W, H) / 26));
  const alan = gorunurAlan(ctx.canvas);
  const px = (n: { x: number }) => xEkran(n.x, aynali) * W;
  ctx.clearRect(0, 0, W, H);
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';

  for (const noktalar of sonuc.yuz.faceLandmarks) {
    // İnce yüz ağı, üstüne kalın yüz hatları (oval, kaşlar, gözler, dudaklar).
    ctx.strokeStyle = YESIL_SOLUK;
    ctx.lineWidth = Math.max(1, cizgi * 0.3);
    baglantilar(ctx, noktalar, FaceLandmarker.FACE_LANDMARKS_TESSELATION, px);
    ctx.strokeStyle = YESIL;
    ctx.lineWidth = cizgi * 0.75;
    baglantilar(ctx, noktalar, YUZ_HATLARI, px);
  }

  sonuc.el.landmarks.forEach((noktalar, i) => {
    ctx.strokeStyle = YESIL;
    ctx.lineWidth = cizgi;
    baglantilar(ctx, noktalar, HandLandmarker.HAND_CONNECTIONS, px);

    ctx.fillStyle = YESIL_ACIK;
    for (const n of noktalar) {
      ctx.beginPath();
      ctx.arc(px(n), n.y * H, cizgi * 1.2, 0, Math.PI * 2);
      ctx.fill();
    }

    // Etiketi elin en üst noktasının üstüne koy.
    const ad = elEtiketi(sonuc.el.handedness[i]?.[0]?.categoryName);
    const ust = noktalar.reduce((a, b) => (b.y < a.y ? b : a));
    etiket(ctx, alan, ad, px(ust) - yazi, ust.y * H - cizgi * 3, YESIL, yazi);
  });
}
