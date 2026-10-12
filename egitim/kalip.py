"""Hizalama kalıbını eğitim verisine uydurur.

DigiFace yüzleri kendi (sentetik, kusursuz) noktalarıyla hizalanmıştır; telefonda ise noktalar
MediaPipe'tan gelir (iris merkezi, burun ucu, ağız köşesi) ve biraz farklı yerlere düşer. Bu
betik hizalı DigiFace yüzlerinde MediaPipe'ın 5 noktasını ölçer; ortalamaları yeni kalıptır.
Telefonda ve LFW ölçümünde yüzler bu kalıba hizalanınca eğitimdeki yüzlere birebir benzer.

Kullanım:  egitim/.venv/Scripts/python egitim/kalip.py
Çıktı:     egitim/veri/kalip.json  (hizala.py ve kimlik_donustur.py okur)
"""
import json
import os
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np

KOK = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, KOK)
import hizala  # noqa: E402
import kimlik  # noqa: E402

CIKTI = os.path.join(kimlik.VERI, "kalip.json")
ORNEK = 3000


def _olc(resim):
    from PIL import Image
    import mediapipe as mp
    # 112 piksel MediaPipe için küçük: büyütülür (normalize noktalar değişmez).
    buyuk = np.asarray(Image.fromarray(resim).resize((224, 224), Image.BICUBIC))
    sonuc = kimlik._isaretci.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=buyuk))
    if not sonuc.face_landmarks:
        return None
    noktalar = [(n.x, n.y, n.z) for n in sonuc.face_landmarks[0]]
    return hizala.bes_nokta(noktalar, hizala.BOYUT, hizala.BOYUT)


def main():
    x, y = kimlik.digiface()
    secili = np.random.default_rng(0).choice(len(y), ORNEK, replace=False)
    with ProcessPoolExecutor(max_workers=6, initializer=kimlik._baslat) as havuz:
        olcumler = [o for o in havuz.map(_olc, [np.array(x[i]) for i in secili], chunksize=32) if o is not None]
    o = np.stack(olcumler)
    ort = np.median(o, axis=0)
    print(f"{len(o)}/{ORNEK} yüzde ölçüldü")
    for ad, k, m, s in zip(["sol göz", "sağ göz", "burun", "sol ağız", "sağ ağız"], hizala.ARCFACE, ort, o.std(0)):
        print(f"  {ad}: kalıp ({k[0]:.1f}, {k[1]:.1f}) -> MediaPipe ({m[0]:.1f}, {m[1]:.1f}) ± ({s[0]:.1f}, {s[1]:.1f})")
    with open(CIKTI, "w", encoding="utf-8") as f:
        json.dump({"kalip": np.round(ort, 3).tolist(), "olculen": len(o)}, f)
    print("yazıldı:", CIKTI)


if __name__ == "__main__":
    main()
