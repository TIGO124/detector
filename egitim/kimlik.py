"""Kişi tanıma: hizalanmış yüz görüntüsünden 128 boyutlu "yüz imzası" üreten CNN (ekran kartı).

Kullanım:  egitim/.venv/Scripts/python egitim/kimlik.py
Çıktı:     egitim/veri/kimlik_agirlik.pt (+ ölçümler) -> kimlik_donustur.py (WSL) -> assets/yz/kimlik.tflite

Eğitim verisi: DigiFace-1M (Microsoft, tamamen sentetik yüzler; gerçek kişi yok). Beş parça:
10.000 kişi x 72 fotoğraf. Model: ImageNet ile ön eğitilmiş MobileNetV3-küçük + ArcFace kaybı.
Sentetikten gerçeğe geçiş için ekran kartında renk, bulanıklık, gri ton ve kaydırma çoğaltması;
omurga yavaş öğrenir (aşağıda neden).

Ölçüm: LFW (gerçek kişiler), telefonla aynı MediaPipe hizalamasıyla (hizala.py). Tur ve eşik,
LFW'nin test dışı kişilerinden üretilen doğrulama çiftleriyle seçilir; resmî test çiftleri
(hiç görülmemiş kişiler) yalnızca en sonda bir kez ölçülür.
"""
import io
import json
import os
import sys
import time
import zipfile
from concurrent.futures import ProcessPoolExecutor

import numpy as np

KOK = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, KOK)
import hizala  # noqa: E402

VERI = os.path.join(KOK, "veri")
MODEL_YUZ = os.path.join(KOK, "..", "public", "mediapipe", "models", "face_landmarker.task")
AGIRLIK = os.path.join(VERI, "kimlik_agirlik.pt")
OLCUM = os.path.join(VERI, "kimlik_olcum.json")
ORTALAMA = [0.485, 0.456, 0.406]
SAPMA = [0.229, 0.224, 0.225]


# ---------- DigiFace ----------

def _coz(bayt):
    from PIL import Image
    r = Image.open(io.BytesIO(bayt)).convert("RGB")
    if r.size != (112, 112):
        r = r.resize((112, 112), Image.BILINEAR)
    return np.asarray(r, np.uint8)


def digiface():
    """Zip'lerdeki yüzler -> (N,112,112,3) uint8 bellek eşlemli dosya + kişi numaraları."""
    resim_yolu = os.path.join(VERI, "digiface", "resimler.u8")
    etiket_yolu = os.path.join(VERI, "digiface", "kisiler.npy")
    if os.path.exists(etiket_yolu):
        y = np.load(etiket_yolu)
        return np.memmap(resim_yolu, np.uint8, "r", shape=(len(y), 112, 112, 3)), y
    adlar, kisiler, zipler = [], [], []
    for zip_adi in sorted(f for f in os.listdir(os.path.join(VERI, "digiface")) if f.endswith(".zip")):
        z = zipfile.ZipFile(os.path.join(VERI, "digiface", zip_adi))
        for ad in z.namelist():
            if ad.lower().endswith((".png", ".jpg")):
                adlar.append(ad)
                kisiler.append(ad.replace("\\", "/").split("/")[-2])
                zipler.append(zip_adi)
    sinif = {k: i for i, k in enumerate(sorted(set(kisiler), key=lambda s: (len(s), s)))}
    y = np.asarray([sinif[k] for k in kisiler], np.int32)
    x = np.memmap(resim_yolu, np.uint8, "w+", shape=(len(y), 112, 112, 3))
    print(f"DigiFace: {len(y)} yüz, {len(sinif)} kişi; açılıyor...")
    t = time.time()
    with ProcessPoolExecutor(max_workers=os.cpu_count() - 2) as havuz:
        bas = 0
        for zip_adi in sorted(set(zipler)):
            z = zipfile.ZipFile(os.path.join(VERI, "digiface", zip_adi))
            secili = [i for i in range(len(adlar)) if zipler[i] == zip_adi]
            for k in range(0, len(secili), 4096):
                parca = secili[k:k + 4096]
                for i, r in zip(parca, havuz.map(_coz, [z.read(adlar[i]) for i in parca], chunksize=64)):
                    x[i] = r
                bas += len(parca)
            print(f"  {zip_adi}: {bas} yüz, {time.time() - t:.0f} sn")
    x.flush()
    np.save(etiket_yolu, y)
    return np.memmap(resim_yolu, np.uint8, "r", shape=(len(y), 112, 112, 3)), y


# ---------- LFW (MediaPipe ile hizalanmış, telefondaki gibi) ----------

_isaretci = None


def _baslat():
    global _isaretci
    from mediapipe.tasks.python import vision
    from mediapipe.tasks.python.core.base_options import BaseOptions
    _isaretci = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=MODEL_YUZ), num_faces=1))


def _resim(bayt):
    from PIL import Image
    return np.asarray(Image.open(io.BytesIO(bayt)).convert("RGB"))


def _lfw_noktalar(bayt):
    """Görüntüdeki 5 yüz noktası (MediaPipe, telefondaki gibi); yüz bulunamazsa NaN."""
    import mediapipe as mp
    r = _resim(bayt)
    s = _isaretci.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=r))
    if not s.face_landmarks:
        return np.full((5, 2), np.nan, np.float32)
    return hizala.bes_nokta([[l.x, l.y, l.z] for l in s.face_landmarks[0]], r.shape[1], r.shape[0])


def _lfw_hizala(ikili):
    bayt, bes = ikili
    return None if np.isnan(bes).any() else hizala.hizala_bes(_resim(bayt), bes)


def _kisi(yol):
    ad = os.path.basename(yol)
    return ad[: ad.rfind("_")]


def lfw():
    """LFW, telefondaki gibi hizalanmış. MediaPipe noktaları bir kez bulunup saklanır; hizalama
    (kalıp değişebilir) her seferinde yeniden yapılır. Son hali hizali.npz'ye de yazılır."""
    import pyarrow.parquet as pq
    t = pq.read_table(os.path.join(VERI, "lfw", "data.parquet")).to_pydict()
    c = pq.read_table(os.path.join(VERI, "lfw", "pairs_test.parquet")).to_pydict()
    gruplar = {"t": [r["bytes"] for r in t["image"]], "c0": [r["bytes"] for r in c["img_0"]],
               "c1": [r["bytes"] for r in c["img_1"]]}
    nokta_yolu = os.path.join(VERI, "lfw", "noktalar.npz")
    if os.path.exists(nokta_yolu):
        noktalar = dict(np.load(nokta_yolu))
    else:
        with ProcessPoolExecutor(max_workers=os.cpu_count() - 2, initializer=_baslat) as havuz:
            noktalar = {k: np.stack(list(havuz.map(_lfw_noktalar, v, chunksize=32))) for k, v in gruplar.items()}
        np.savez(nokta_yolu, **noktalar)
    with ProcessPoolExecutor(max_workers=os.cpu_count() - 2) as havuz:
        yuzler, h0, h1 = (list(havuz.map(_lfw_hizala, zip(gruplar[k], noktalar[k]), chunksize=64))
                          for k in ("t", "c0", "c1"))
    test_kisileri = {_kisi(r["path"]) for r in c["img_0"]} | {_kisi(r["path"]) for r in c["img_1"]}
    secili = [i for i, h in enumerate(yuzler) if h is not None and _kisi(t["image"][i]["path"]) not in test_kisileri]
    x = np.stack([yuzler[i] for i in secili])
    kisi = np.asarray([_kisi(t["image"][i]["path"]) for i in secili])
    tamam = [i for i in range(len(h0)) if h0[i] is not None and h1[i] is not None]
    c0, c1 = np.stack([h0[i] for i in tamam]), np.stack([h1[i] for i in tamam])
    ayni = np.asarray([c["pair"][i] for i in tamam], np.int64)
    print(f"LFW: doğrulama için {len(x)} yüz; test çifti {len(tamam)}/{len(h0)}")
    np.savez(os.path.join(VERI, "lfw", "hizali.npz"), x=x, kisi=kisi, c0=c0, c1=c1, ayni=ayni)
    return x, kisi, c0, c1, ayni


def dogrulama_ciftleri(x, kisi, n=1500, tohum=0):
    r = np.random.default_rng(tohum)
    kisiler, sayi = np.unique(kisi, return_counts=True)
    coklu = kisiler[sayi >= 2]
    idx = {k: np.flatnonzero(kisi == k) for k in kisiler}
    a0, a1, ay = [], [], []
    for _ in range(n):
        k = coklu[r.integers(len(coklu))]
        i, j = r.choice(idx[k], 2, replace=False)
        a0.append(i); a1.append(j); ay.append(1)
        k1, k2 = r.choice(len(kisiler), 2, replace=False)
        a0.append(r.choice(idx[kisiler[k1]])); a1.append(r.choice(idx[kisiler[k2]])); ay.append(0)
    return x[a0], x[a1], np.asarray(ay)


def en_iyi_esik(b, ayni):
    en = (0.0, 0.0)
    for e in np.unique(b):
        d = float(((b >= e) == (ayni == 1)).mean())
        if d > en[0]:
            en = (d, float(e))
    return en


# ---------- Model ----------

def model_kur(onceden_egitilmis=True):
    """MobileNetV3-küçük + ortalama havuzlama + 128 boyutlu imza."""
    from torch import nn
    from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small
    agirlik = MobileNet_V3_Small_Weights.IMAGENET1K_V1 if onceden_egitilmis else None
    govde = mobilenet_v3_small(weights=agirlik).features
    return nn.Sequential(govde, nn.AdaptiveAvgPool2d(1), nn.Flatten(), nn.Dropout(0.2),
                         nn.Linear(576, 128), nn.BatchNorm1d(128))


def main():
    import torch
    from torch import nn
    cihaz = torch.device("cuda")
    x, y = digiface()
    lx, lkisi, c0, c1, ayni = lfw()
    v0, v1, vayni = dogrulama_ciftleri(lx, lkisi)
    n_kisi = int(y.max()) + 1
    print(f"eğitim: {len(y)} yüz, {n_kisi} kişi; cihaz {torch.cuda.get_device_name(0)}")

    ort = torch.tensor(ORTALAMA, device=cihaz).view(1, 3, 1, 1)
    sap = torch.tensor(SAPMA, device=cihaz).view(1, 3, 1, 1)

    def hazirla(u8, coklu=False):
        t = torch.from_numpy(np.ascontiguousarray(u8)).to(cihaz, non_blocking=True).permute(0, 3, 1, 2).float() / 255
        if coklu:
            b = t.shape[0]
            # Renk: parlaklık, karşıtlık, doygunluk; bazen gri ton
            t = t * torch.empty(b, 1, 1, 1, device=cihaz).uniform_(0.6, 1.4)
            m = t.mean(dim=(1, 2, 3), keepdim=True)
            t = (t - m) * torch.empty(b, 1, 1, 1, device=cihaz).uniform_(0.6, 1.4) + m
            gri = t.mean(dim=1, keepdim=True)
            t = gri + (t - gri) * torch.empty(b, 1, 1, 1, device=cihaz).uniform_(0.3, 1.5)
            gri_sec = torch.rand(b, 1, 1, 1, device=cihaz) < 0.2
            t = torch.where(gri_sec, t.mean(dim=1, keepdim=True).expand_as(t), t)
            # Bulanıklık (düşük çözünürlük): küçültüp büyütme
            bul = torch.rand(b, device=cihaz) < 0.3
            if bul.any():
                k = nn.functional.interpolate(t[bul], scale_factor=0.5, mode="bilinear", align_corners=False)
                t[bul] = nn.functional.interpolate(k, size=(112, 112), mode="bilinear", align_corners=False)
            # Yatay çevirme ve küçük kaydırma
            cevir = torch.rand(b, device=cihaz) < 0.5
            t[cevir] = t[cevir].flip(3)
            kay = torch.randint(-4, 5, (2,)).tolist()
            t = torch.roll(t, shifts=kay, dims=(2, 3))
            t = t.clamp(0, 1)
        return (t - ort) / sap

    @torch.no_grad()
    def imza(model, u8):
        model.eval()
        cikti = []
        for i in range(0, len(u8), 512):
            with torch.autocast("cuda", dtype=torch.float16):
                cikti.append(nn.functional.normalize(model(hazirla(u8[i:i + 512])).float(), dim=1))
        return torch.cat(cikti)

    def cift_dogrulugu(model, a, b, etiket, esik=None):
        s = (imza(model, a) * imza(model, b)).sum(1).cpu().numpy()
        if esik is None:
            return en_iyi_esik(s, etiket)
        return float(((s >= esik) == (etiket == 1)).mean()), esik

    torch.manual_seed(0)
    model = model_kur().to(cihaz)
    w = nn.Parameter(torch.randn(n_kisi, 128, device=cihaz) * 0.01)
    tur_sayisi, topluk = 14, 256
    # Omurga (ImageNet ile ön eğitilmiş) 10 kat yavaş öğrenir: hızlı öğrenince genel görüntü
    # özelliklerini "unutup" sentetik yüzlere özgü ayrıntıları ezberliyor; gerçek yüzlerde (LFW)
    # imzalar birbirine benzeşip doğruluk %55'e düşüyordu. Yavaş omurgayla ilk turda %81.
    eniyi = torch.optim.AdamW([{"params": model[0].parameters()}, {"params": list(model[1:].parameters())},
                               {"params": [w]}], lr=2e-3, weight_decay=5e-4)
    adim_sayisi = tur_sayisi * (len(y) // topluk)
    zamanlayici = torch.optim.lr_scheduler.OneCycleLR(eniyi, max_lr=[2e-4, 2e-3, 2e-3], total_steps=adim_sayisi,
                                                      pct_start=0.1)
    olcek = torch.amp.GradScaler()
    s_, m_ = 32.0, 0.4
    en_iyi = (0.0, None, 0.0)
    for tur in range(tur_sayisi):
        model.train()
        t0 = time.time()
        sira = np.random.default_rng(tur).permutation(len(y))
        toplam = 0.0
        for k in range(len(y) // topluk):
            idx = np.sort(sira[k * topluk:(k + 1) * topluk])
            girdi = hazirla(x[idx], coklu=True)
            hedef = torch.from_numpy(y[idx].astype(np.int64)).to(cihaz)
            with torch.autocast("cuda", dtype=torch.float16):
                g = model(girdi)
            g = nn.functional.normalize(g.float(), dim=1)
            cos = (g @ nn.functional.normalize(w, dim=1).T).clamp(-1 + 1e-6, 1 - 1e-6)
            # ArcFace: hedef sınıfta açıya pay ekle
            hedef_cos = torch.cos(torch.acos(cos.gather(1, hedef[:, None])) + m_)
            logit = cos.scatter(1, hedef[:, None], hedef_cos) * s_
            kayip = nn.functional.cross_entropy(logit, hedef)
            eniyi.zero_grad(set_to_none=True)
            olcek.scale(kayip).backward()
            olcek.step(eniyi)
            olcek.update()
            zamanlayici.step()
            toplam += kayip.item()
        d, esik = cift_dogrulugu(model, v0, v1, vayni)
        if d > en_iyi[0]:
            en_iyi = (d, {k: v.detach().clone() for k, v in model.state_dict().items()}, esik)
        print(f"tur {tur + 1}/{tur_sayisi}: kayıp {toplam / (len(y) // topluk):.2f}, "
              f"LFW doğrulama %{100 * d:.1f}, {time.time() - t0:.0f} sn", flush=True)

    d, durum, esik = en_iyi
    model.load_state_dict(durum)
    test, _ = cift_dogrulugu(model, c0, c1, ayni, esik)
    print(f"seçilen: doğrulama %{100 * d:.1f}, eşik {esik:.3f} -> LFW resmî test çiftleri %{100 * test:.1f}")
    torch.save({k: v.cpu() for k, v in durum.items()}, AGIRLIK)
    with open(OLCUM, "w", encoding="utf-8") as f:
        json.dump({"esik": round(esik, 4), "lfw_dogrulama": round(d, 4), "lfw_test": round(test, 4),
                   "ortalama": ORTALAMA, "sapma": SAPMA, "boyut": 112}, f)
    print("yazıldı:", AGIRLIK, OLCUM)


if __name__ == "__main__":
    main()
