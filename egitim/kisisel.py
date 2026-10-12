"""Telefonda "Öğret" ile kaydedilen örnekleri bilgisayarın ekran kartında eğitir.

Telefonda: YZ > Hareket (ya da TİD) > Öğret > "Bilgisayara gönder" -> hareket.veri / tid.veri
Kullanım:  egitim/.venv/Scripts/python egitim/kisisel.py hareket.veri [--gizli 64 32] [--tur 150]
Çıktı:     hareket.json (aynı klasörde) -> telefonda Öğret > "Model yükle"

Telefondaki eğitimden farkı: daha büyük ağ, daha çok tur ve doğrulama için ayrılan örneklerle
gerçek bir ölçüm. Model biçimi telefondakiyle aynıdır (SinirAgi.json).
"""
import argparse
import os
import struct
import sys

import numpy as np

KOK = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, KOK)
import ortak  # noqa: E402

SIHIR = 0x595A5631  # "YZV1"


def oku(yol):
    """YzVeri.java dosyası (Java DataOutputStream: büyük sonlu). Döndürür: x, y, etiketler."""
    with open(yol, "rb") as f:
        veri = f.read()
    sihir, boyut, n = struct.unpack_from(">iii", veri, 0)
    if sihir != SIHIR:
        raise SystemExit(f"{yol}: Dedektör örnek dosyası değil")
    k = 12
    x = np.empty((n, boyut), np.float32)
    adlar = []
    for s in range(n):
        uzunluk = struct.unpack_from(">H", veri, k)[0]
        # Java "değiştirilmiş UTF-8": Türkçe harfler için standart UTF-8 ile aynı.
        adlar.append(veri[k + 2:k + 2 + uzunluk].decode("utf-8"))
        k += 2 + uzunluk
        x[s] = np.frombuffer(veri, ">f4", boyut, k)
        k += 4 * boyut
    etiketler = list(dict.fromkeys(adlar))
    y = np.asarray([etiketler.index(a) for a in adlar], np.int64)
    return x, y, etiketler


def sinirlar(xn, y, k):
    """SinirAgi.java ile aynı: sınıf merkezi ve örneklerin %95'ini kapsayan yarıçap (en az 0.25·√boyut)."""
    merkezler, yaricaplar = [], []
    for c in range(k):
        xc = xn[y == c]
        m = xc.mean(0)
        d = np.sort(np.linalg.norm(xc - m, axis=1))
        r95 = float(d[min(len(d) - 1, int(len(d) * 0.95))]) if len(d) else 0.0
        merkezler.append(np.round(m, 6).tolist())
        yaricaplar.append(round(max(r95, 0.25 * float(np.sqrt(xn.shape[1]))), 6))
    return merkezler, yaricaplar


def main():
    a = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    a.add_argument("dosya")
    a.add_argument("--gizli", type=int, nargs="+", default=[64, 32])
    a.add_argument("--tur", type=int, default=150)
    g = a.parse_args()

    x, y, etiketler = oku(g.dosya)
    print(f"{len(x)} örnek, {len(etiketler)} etiket: " + ", ".join(f"{e} ({(y == i).sum()})" for i, e in enumerate(etiketler)))
    if len(etiketler) < 2:
        raise SystemExit("En az iki farklı etiket gerekli.")

    # Ölçüm: her etiketin %20'si ayrılır, kalanla eğitilir.
    r = np.random.default_rng(0)
    test = np.zeros(len(y), bool)
    for c in range(len(etiketler)):
        idx = np.flatnonzero(y == c)
        if len(idx) >= 5:
            test[r.choice(idx, max(1, len(idx) // 5), replace=False)] = True
    model, ort, sap = ortak.siniflandirici_egit(x[~test], y[~test], etiketler, g.gizli, g.tur)
    if test.any():
        dogru = (ortak.tahmin(model, ort, sap, x[test]) == y[test]).mean()
        print(f"ayrılan örneklerde doğruluk: %{100 * dogru:.1f}")

    # Son model bütün örneklerle.
    model, ort, sap = ortak.siniflandirici_egit(x, y, etiketler, g.gizli, g.tur)
    merkezler, yaricaplar = sinirlar((x - ort) / sap, y, len(etiketler))
    cikti = os.path.splitext(g.dosya)[0] + ".json"
    ortak.json_yaz(cikti, model, ort, sap, etiketler, ek={"merkezler": merkezler, "yaricaplar": yaricaplar})
    print("yazıldı:", cikti, "-> telefonda Öğret > Model yükle")


if __name__ == "__main__":
    main()
