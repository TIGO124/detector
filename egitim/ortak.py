"""Ortak eğitim araçları: PyTorch MLP (ekran kartında), Android'deki SinirAgi.java ile aynı JSON."""
import json

import numpy as np
import torch
from torch import nn

CIHAZ = torch.device("cuda" if torch.cuda.is_available() else "cpu")


class Mlp(nn.Module):
    def __init__(self, girdi, gizli, cikis):
        super().__init__()
        katmanlar, onceki = [], girdi
        for g in gizli:
            katmanlar += [nn.Linear(onceki, g), nn.ReLU()]
            onceki = g
        katmanlar.append(nn.Linear(onceki, cikis))
        self.ag = nn.Sequential(*katmanlar)

    def forward(self, x):
        return self.ag(x)


def normalizasyon(x):
    ort = x.mean(axis=0)
    sap = np.maximum(x.std(axis=0), 1e-4)
    return ort.astype(np.float32), sap.astype(np.float32)


def siniflandirici_egit(x, y, etiketler, gizli, tur=60, gurultu=0.05, tohum=0):
    """x: (n, boyut) float32, y: (n,) int. Döndürür: (model, ortalama, sapma)."""
    torch.manual_seed(tohum)
    ort, sap = normalizasyon(x)
    xn = torch.tensor((x - ort) / sap, device=CIHAZ)
    yt = torch.tensor(y, dtype=torch.long, device=CIHAZ)
    model = Mlp(x.shape[1], gizli, len(etiketler)).to(CIHAZ)
    eniyi = torch.optim.Adam(model.parameters(), lr=3e-3, weight_decay=1e-4)
    kayip = nn.CrossEntropyLoss()
    n = len(xn)
    for _ in range(tur):
        sira = torch.randperm(n, device=CIHAZ)
        for bas in range(0, n, 256):
            idx = sira[bas:bas + 256]
            girdi = xn[idx] + torch.randn_like(xn[idx]) * gurultu
            eniyi.zero_grad()
            kayip(model(girdi), yt[idx]).backward()
            eniyi.step()
    return model, ort, sap


@torch.no_grad()
def tahmin(model, ort, sap, x):
    model.eval()
    z = model(torch.tensor((x - ort) / sap, device=CIHAZ))
    return z.argmax(1).cpu().numpy()


def json_yaz(yol, model, ort, sap, etiketler, cikis="softmax", ek=None):
    """SinirAgi.json() ile aynı biçim; "ek" alanları (ör. eşik) da yazılır."""
    katmanlar = []
    for m in model.ag:
        if isinstance(m, nn.Linear):
            katmanlar.append({
                "agirlik": np.round(m.weight.detach().cpu().numpy(), 6).tolist(),
                "esik": np.round(m.bias.detach().cpu().numpy(), 6).tolist(),
            })
    veri = {
        "etiketler": list(etiketler),
        "cikis": cikis,
        "ortalama": np.round(ort, 6).tolist(),
        "sapma": np.round(sap, 6).tolist(),
        "katmanlar": katmanlar,
        **(ek or {}),
    }
    with open(yol, "w", encoding="utf-8") as f:
        json.dump(veri, f, ensure_ascii=False, separators=(",", ":"))
