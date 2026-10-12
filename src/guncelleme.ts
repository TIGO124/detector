import { Capacitor, registerPlugin, type PluginListenerHandle } from '@capacitor/core';

/**
 * Uygulama içi güncelleme (yalnızca Android uygulamasında). Depodaki surum.json daha yeni bir
 * sürüm gösterirse aynı imzalı APK o sürümün etiketinden (vX.Y.Z) indirilir ve Android'in kurulum
 * ekranı açılır (android/app/src/main/java/app/tigo/dedektor/ApkGuncellemePlugin.java).
 */
declare const __SURUM__: string;
export const UYGULAMA_SURUMU = __SURUM__;
const DEPO = 'https://raw.githubusercontent.com/TIGO124/detector/';

interface ApkGuncellemeEklentisi {
  /** APK'yı indirir, doğrular ve Android kurulum ekranını açar. */
  indirVeKur(secenek: { adres: string; surumKodu: number }): Promise<{ durum: 'kuruluyor' | 'izin_verilmedi' }>;
  /** İndirme yüzdesi; boyut bilinmiyorsa -1. */
  addListener(olay: 'ilerleme', dinleyici: (veri: { yuzde: number }) => void): Promise<PluginListenerHandle>;
}

const ApkGuncelleme = registerPlugin<ApkGuncellemeEklentisi>('ApkGuncelleme');

export const guncellemeDestekleniyor = () => Capacitor.isPluginAvailable('ApkGuncelleme');

/** 1.2.10 > 1.2.9 gibi; a > b ise 1, eşitse 0, küçükse -1. */
export function surumKarsilastir(a: string, b: string): number {
  const pa = a.split('.').map(Number);
  const pb = b.split('.').map(Number);
  for (let i = 0; i < 3; i++) {
    const fark = (pa[i] || 0) - (pb[i] || 0);
    if (fark) return Math.sign(fark);
  }
  return 0;
}

/** 1.2.3 → 10203; android/app/build.gradle'daki versionCode hesabıyla aynı. */
export function surumKodu(surum: string): number {
  const [a, b, c] = surum.split('.').map(Number);
  return a * 10000 + b * 100 + c;
}

export interface SurumBilgisi {
  surum: string;
  notlar: string[];
}

export function surumBilgisiDogrula(v: unknown): SurumBilgisi | null {
  if (!v || typeof v !== 'object') return null;
  const b = v as Record<string, unknown>;
  if (typeof b.surum !== 'string' || !/^\d{1,2}\.\d{1,2}\.\d{1,2}$/.test(b.surum)) return null;
  const notlar = Array.isArray(b.notlar) ? b.notlar.filter((n): n is string => typeof n === 'string').slice(0, 10) : [];
  return { surum: b.surum, notlar };
}

type Getir = (adres: string, secenek?: RequestInit) => Promise<Response>;

/**
 * surum.json önce GitHub API'den okunur (önbellek 1 dk); API saatlik sınıra takılırsa ya da
 * ulaşılamazsa raw.githubusercontent.com'a düşülür (önbellek 5 dk).
 */
const API = 'https://api.github.com/repos/TIGO124/detector/contents/surum.json?ref=main';

async function surumJsonu(getir: Getir, signal: AbortSignal): Promise<unknown> {
  try {
    const cevap = await getir(API, { cache: 'no-store', signal, headers: { Accept: 'application/vnd.github.raw+json' } });
    if (cevap.ok) return await cevap.json();
  } catch (hata) {
    if (signal.aborted) throw hata;
  }
  const cevap = await getir(`${DEPO}main/surum.json`, { cache: 'no-store', signal });
  if (!cevap.ok) throw new Error(`Sunucu ${cevap.status} döndürdü`);
  return cevap.json();
}

/** Depoda yüklü olandan yeni bir sürüm varsa bilgisini, yoksa null döndürür. */
export async function yeniSurumuBul(getir: Getir = (a, s) => fetch(a, s)): Promise<SurumBilgisi | null> {
  const kontrol = new AbortController();
  const zaman = setTimeout(() => kontrol.abort(), 15_000);
  try {
    const bilgi = surumBilgisiDogrula(await surumJsonu(getir, kontrol.signal));
    if (!bilgi) throw new Error('Sürüm bilgisi okunamadı');
    return surumKarsilastir(bilgi.surum, UYGULAMA_SURUMU) > 0 ? bilgi : null;
  } finally {
    clearTimeout(zaman);
  }
}

/** Yeni APK'yı indirir ve Android'in kurulum ekranını açar. */
export async function guncellemeyiKur(
  surum: string,
  ilerleme: (yuzde: number | null) => void,
): Promise<'kuruluyor' | 'izin_verilmedi'> {
  const dinleyici = await ApkGuncelleme.addListener('ilerleme', ({ yuzde }) => ilerleme(yuzde >= 0 ? yuzde : null));
  try {
    const { durum } = await ApkGuncelleme.indirVeKur({
      adres: `${DEPO}v${surum}/indir/Dedektor.apk`,
      surumKodu: surumKodu(surum),
    });
    return durum;
  } finally {
    void dinleyici.remove();
  }
}
