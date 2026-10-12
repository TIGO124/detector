import { describe, expect, it } from 'vitest';
import { elEtiketi, xEkran } from '../src/ciz';

describe('elEtiketi', () => {
  it('MediaPipe etiketini Türkçeye çevirir', () => {
    expect(elEtiketi('Right')).toBe('Sağ el');
    expect(elEtiketi('Left')).toBe('Sol el');
  });

  it('bilinmeyen etikette genel ad verir', () => {
    expect(elEtiketi(undefined)).toBe('El');
    expect(elEtiketi('')).toBe('El');
  });
});

describe('aynalama', () => {
  it('normalize x koordinatını aynalar', () => {
    expect(xEkran(0.2, true)).toBeCloseTo(0.8);
    expect(xEkran(0.2, false)).toBe(0.2);
  });
});
