import { describe, expect, it } from 'vitest';
import { surumBilgisiDogrula, surumKarsilastir, surumKodu, UYGULAMA_SURUMU, yeniSurumuBul } from '../src/guncelleme';

const json = (veri: unknown, status = 200) => async () => new Response(JSON.stringify(veri), { status });

describe('sürüm hesapları', () => {
  it('sürümleri sayı olarak karşılaştırır', () => {
    expect(surumKarsilastir('1.2.10', '1.2.9')).toBe(1);
    expect(surumKarsilastir('1.0.0', '1.0.0')).toBe(0);
    expect(surumKarsilastir('0.9.9', '1.0.0')).toBe(-1);
  });

  it('Android versionCode ile aynı kodu üretir', () => {
    expect(surumKodu('1.2.3')).toBe(10203);
    expect(surumKodu('2.0.0')).toBe(20000);
  });
});

describe('surumBilgisiDogrula', () => {
  it('geçerli bilgiyi kabul eder, notları süzer', () => {
    expect(surumBilgisiDogrula({ surum: '1.0.1', notlar: ['Yeni', 5] })).toEqual({ surum: '1.0.1', notlar: ['Yeni'] });
  });

  it('bozuk bilgiyi reddeder', () => {
    expect(surumBilgisiDogrula(null)).toBeNull();
    expect(surumBilgisiDogrula({ surum: '1.0' })).toBeNull();
    expect(surumBilgisiDogrula({ surum: '1.0.0/../x' })).toBeNull();
  });
});

describe('yeniSurumuBul', () => {
  it('yeni sürüm varsa bilgisini döndürür', async () => {
    const [a, b, c] = UYGULAMA_SURUMU.split('.').map(Number);
    const sonraki = `${a}.${b}.${c + 1}`;
    await expect(yeniSurumuBul(json({ surum: sonraki, notlar: ['Not'] }))).resolves.toEqual({ surum: sonraki, notlar: ['Not'] });
  });

  it('aynı sürümde null döndürür', async () => {
    await expect(yeniSurumuBul(json({ surum: UYGULAMA_SURUMU }))).resolves.toBeNull();
  });

  it('GitHub API sınıra takılırsa raw adresine düşer', async () => {
    const sonraki = UYGULAMA_SURUMU.replace(/\d+$/, (s) => String(Number(s) + 1));
    const adresler: string[] = [];
    const getir = async (adres: string) => {
      adresler.push(adres);
      return adres.includes('api.github.com')
        ? new Response('{"message":"API rate limit exceeded"}', { status: 403 })
        : new Response(JSON.stringify({ surum: sonraki }), { status: 200 });
    };
    await expect(yeniSurumuBul(getir)).resolves.toEqual({ surum: sonraki, notlar: [] });
    expect(adresler).toHaveLength(2);
    expect(adresler[1]).toContain('raw.githubusercontent.com');
  });

  it('sunucu hatasında hata fırlatır', async () => {
    await expect(yeniSurumuBul(json({}, 404))).rejects.toThrow('404');
  });
});
