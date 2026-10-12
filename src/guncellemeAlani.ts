import { guncellemeDestekleniyor, guncellemeyiKur, UYGULAMA_SURUMU, yeniSurumuBul, type SurumBilgisi } from './guncelleme';

/** Giriş ekranındaki güncelleme alanı: sürümü gösterir, açılışta ve istendiğinde yeni sürüme bakar. */
export function guncellemeAlaniniKur() {
  const surum = document.querySelector<HTMLElement>('#surum')!;
  const denetle = document.querySelector<HTMLButtonElement>('#denetle')!;
  const yeni = document.querySelector<HTMLElement>('#yeni-surum')!;
  const baslik = document.querySelector<HTMLElement>('#yeni-baslik')!;
  const notlar = document.querySelector<HTMLUListElement>('#yeni-notlar')!;
  const guncelle = document.querySelector<HTMLButtonElement>('#guncelle')!;
  const durum = document.querySelector<HTMLElement>('#guncelleme-durum')!;

  surum.textContent = `Sürüm ${UYGULAMA_SURUMU}`;
  // Web sürümü her açılışta en yenisini yükler; APK güncellemesi yalnızca Android uygulamasında.
  if (!guncellemeDestekleniyor()) return;
  denetle.hidden = false;
  let bulunan: SurumBilgisi | null = null;

  async function bak() {
    denetle.disabled = true;
    durum.textContent = 'Güncellemeler denetleniyor…';
    try {
      bulunan = await yeniSurumuBul();
      durum.textContent = bulunan ? '' : 'Uygulama güncel.';
      if (bulunan) {
        baslik.textContent = `Yeni sürüm: ${bulunan.surum}`;
        notlar.replaceChildren(
          ...bulunan.notlar.map((n) => {
            const li = document.createElement('li');
            li.textContent = n;
            return li;
          }),
        );
        yeni.hidden = false;
        denetle.hidden = true;
      }
    } catch (hata) {
      console.warn(hata);
      durum.textContent = 'Güncellemeler denetlenemedi. İnternet bağlantını kontrol et.';
    } finally {
      denetle.disabled = false;
    }
  }

  denetle.addEventListener('click', () => void bak());

  guncelle.addEventListener('click', async () => {
    if (!bulunan) return;
    guncelle.disabled = true;
    durum.textContent = 'İndiriliyor…';
    try {
      const sonuc = await guncellemeyiKur(bulunan.surum, (yuzde) => {
        durum.textContent = yuzde === null ? 'İndiriliyor…' : `İndiriliyor… %${yuzde}`;
      });
      durum.textContent =
        sonuc === 'kuruluyor'
          ? 'Kurulum ekranı açıldı. "Güncelle"ye dokun.'
          : 'Kurulum izni verilmedi. Açılan ekranda "Bu kaynaktan izin ver"i açıp yeniden dene.';
    } catch (hata) {
      durum.textContent = hata instanceof Error ? hata.message : String(hata);
    } finally {
      guncelle.disabled = false;
    }
  });

  void bak();
}
