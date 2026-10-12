"""Kişi tanıma modelini telefona çevirir: kimlik_agirlik.pt -> assets/yz/kimlik.tflite + kimlik.json.

litert-torch yalnızca Linux'ta çalıştığı için WSL'de çalıştırılır:
    wsl -d Ubuntu -- ~/yz-donustur/bin/python /mnt/c/.../egitim/kimlik_donustur.py

Telefondaki girdi: (1, 112, 112, 3) RGB, 0-255 (hizalı yüz). Kanal sırası çevirme, ImageNet
normalizasyonu ve birim uzunluğa ölçekleme modelin içindedir; Java yalnızca pikselleri kopyalar.
Dönüşümden sonra TFLite çıktısı PyTorch ile karşılaştırılır.
"""
import json
import os
import sys

import numpy as np
import torch
from torch import nn

KOK = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, KOK)
import kimlik  # noqa: E402

YZ = os.path.join(KOK, "..", "android", "app", "src", "main", "assets", "yz")


class Telefon(nn.Module):
    def __init__(self, govde):
        super().__init__()
        self.govde = govde
        self.register_buffer("ort", torch.tensor(kimlik.ORTALAMA).view(1, 3, 1, 1) * 255)
        self.register_buffer("sap", torch.tensor(kimlik.SAPMA).view(1, 3, 1, 1) * 255)

    def forward(self, x):
        x = x.permute(0, 3, 1, 2)
        v = self.govde((x - self.ort) / self.sap)
        return v / torch.sqrt((v * v).sum(dim=1, keepdim=True) + 1e-12)


def main(agirlik=kimlik.AGIRLIK, klasor=YZ):
    import litert_torch
    from ai_edge_litert.interpreter import Interpreter

    govde = kimlik.model_kur(onceden_egitilmis=False)
    govde.load_state_dict(torch.load(agirlik, map_location="cpu", weights_only=True))
    model = Telefon(govde).eval()

    ornek = (torch.rand(1, 112, 112, 3) * 255,)
    tfl = litert_torch.convert(model, ornek)
    os.makedirs(klasor, exist_ok=True)
    yol = os.path.join(klasor, "kimlik.tflite")
    tfl.export(yol)

    # Doğrulama: LFW'den gerçek hizalı yüzlerle PyTorch ve TFLite aynı imzayı vermeli.
    yuzler = np.load(os.path.join(kimlik.VERI, "lfw", "hizali.npz"))["x"][:16].astype(np.float32)
    yorum = Interpreter(model_path=yol)
    yorum.allocate_tensors()
    gir, cik = yorum.get_input_details()[0], yorum.get_output_details()[0]
    en_dusuk = 1.0
    for y in yuzler:
        yorum.set_tensor(gir["index"], y[None])
        yorum.invoke()
        a = yorum.get_tensor(cik["index"])[0]
        with torch.no_grad():
            b = model(torch.from_numpy(y[None])).numpy()[0]
        en_dusuk = min(en_dusuk, float(np.dot(a, b)))
    print(f"girdi {gir['shape'].tolist()} {gir['dtype'].__name__}, çıktı {cik['shape'].tolist()}; "
          f"PyTorch ile en düşük benzerlik {en_dusuk:.5f}")
    assert en_dusuk > 0.999, "TFLite çıktısı PyTorch'tan farklı"

    with open(kimlik.OLCUM, encoding="utf-8") as f:
        olcum = json.load(f)
    with open(os.path.join(klasor, "kimlik.json"), "w", encoding="utf-8") as f:
        json.dump({"esik": olcum["esik"], "lfw_test": olcum["lfw_test"], "boyut": 112, "imza": 128,
                   "kalip": np.round(kimlik.hizala.KALIP, 3).tolist()}, f)
    print("yazıldı:", os.path.normpath(yol), f"{os.path.getsize(yol) / 1e6:.1f} MB")


if __name__ == "__main__":
    # Deneme için: kimlik_donustur.py [ağırlık.pt] [çıktı klasörü]
    main(*sys.argv[1:3])
