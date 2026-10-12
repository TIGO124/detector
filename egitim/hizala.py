"""Yüz hizalama: MediaPipe yüz noktalarından 5 nokta -> 112x112 standart (ArcFace) kalıp.

Android'deki KisiTanima.java ile aynı hesap (benzerlik dönüşümü, en küçük kareler).
"""
import json
import os

import numpy as np

# 112x112 standart (ArcFace) kalıp: sol göz, sağ göz, burun ucu, sol ve sağ ağız köşesi (görüntüye göre)
ARCFACE = np.array([
    [38.2946, 51.6963],
    [73.5318, 51.5014],
    [56.0252, 71.7366],
    [41.5493, 92.3655],
    [70.7299, 92.2041],
], np.float32)
BOYUT = 112
# Eğitim yüzlerinde MediaPipe noktalarının ortalaması (kalip.py ölçer): MediaPipe'ın burun ve göz
# noktaları ArcFace kalıbındakilerden biraz farklı yere düşer; telefonda da bu kalıp kullanılır.
_OLCULEN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "veri", "kalip.json")
KALIP = np.array(json.load(open(_OLCULEN))["kalip"], np.float32) if os.path.exists(_OLCULEN) else ARCFACE
# MediaPipe yüz ağı: iris merkezleri (468, 473), burun ucu (1), ağız köşeleri (61, 291)
GOZLER = (468, 473)
BURUN = 1
AGIZ = (61, 291)


def bes_nokta(noktalar, g, y):
    """478 normalize yüz noktası -> görüntüdeki 5 nokta (piksel); gözler ve ağız soldan sağa."""
    p = lambda i: (noktalar[i][0] * g, noktalar[i][1] * y)  # noqa: E731
    gozler = sorted([p(i) for i in GOZLER])
    agiz = sorted([p(i) for i in AGIZ])
    return np.array([gozler[0], gozler[1], p(BURUN), agiz[0], agiz[1]], np.float32)


def benzerlik(kaynak, hedef):
    """kaynak noktaları hedefe taşıyan [a -b tx; b a ty] (ölçek+dönme+öteleme), en küçük kareler."""
    n = len(kaynak)
    A = np.zeros((2 * n, 4))
    b = np.zeros(2 * n)
    for i, ((x, y), (u, v)) in enumerate(zip(kaynak, hedef)):
        A[2 * i] = [x, -y, 1, 0]
        A[2 * i + 1] = [y, x, 0, 1]
        b[2 * i], b[2 * i + 1] = u, v
    a, bb, tx, ty = np.linalg.lstsq(A, b, rcond=None)[0]
    return np.array([[a, -bb, tx], [bb, a, ty]], np.float32)


def hizala(resim_rgb, noktalar):
    """resim (H, W, 3) uint8 + 478 yüz noktası -> 112x112 hizalı yüz (uint8)."""
    y, g = resim_rgb.shape[:2]
    return hizala_bes(resim_rgb, bes_nokta(noktalar, g, y))


def hizala_bes(resim_rgb, bes):
    """resim (H, W, 3) uint8 + görüntüdeki 5 nokta (piksel) -> 112x112 hizalı yüz (uint8)."""
    from PIL import Image
    M = benzerlik(bes, KALIP)
    # PIL ters dönüşüm ister: çıktı pikseli -> kaynak pikseli
    tam = np.vstack([M, [0, 0, 1]])
    ters = np.linalg.inv(tam)
    resim = Image.fromarray(resim_rgb).transform(
        (BOYUT, BOYUT), Image.AFFINE, tuple(ters[:2].reshape(-1)), resample=Image.BILINEAR)
    return np.asarray(resim)
