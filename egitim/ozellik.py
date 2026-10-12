"""Yapay zekâ öznitelikleri. Android'deki YzOzellik.java ile birebir aynı hesap.

İkisi birlikte değiştirilmelidir; tests/ozellik_ornekleri.json ikisinin aynı sonucu
verdiğini doğrular (Java: YzOzellikTest).
"""
import numpy as np

EL_BOYUTU = 63
YUZ_BOYUTU = 52
VUCUT_BOYUTU = 13


def _uzaklik(a, b):
    return float(np.linalg.norm(np.asarray(a, np.float32) - np.asarray(b, np.float32)))


def vektor_acisi(u, v):
    u = np.asarray(u, np.float64)
    v = np.asarray(v, np.float64)
    nu, nv = np.linalg.norm(u), np.linalg.norm(v)
    if nu == 0 or nv == 0:
        return 0.0
    cos = float(np.dot(u, v) / (nu * nv))
    return float(np.arccos(max(-1.0, min(1.0, cos))) / np.pi)


def aci(a, b, c):
    """b köşesindeki açı, 0–1 (180° = 1)."""
    return vektor_acisi(np.subtract(a, b), np.subtract(c, b))


def el(dunya, sol_el):
    p = np.asarray(dunya, np.float32)
    olcek = _uzaklik(p[0], p[9]) or 1.0
    o = (p - p[0]) / olcek
    if sol_el:
        o[:, 0] = -o[:, 0]
    return o.reshape(-1)


def yuz(puanlar_52):
    return np.asarray(puanlar_52, np.float32)


def vucut(p):
    """MediaPipe vücut "dünya" noktaları (33 nokta, metre, y aşağı) -> 13 öznitelik."""
    p = np.asarray(p, np.float32)
    o = np.zeros(VUCUT_BOYUTU, np.float32)
    o[0] = aci(p[11], p[13], p[15])
    o[1] = aci(p[12], p[14], p[16])
    o[2] = aci(p[13], p[11], p[23])
    o[3] = aci(p[14], p[12], p[24])
    o[4] = aci(p[11], p[23], p[25])
    o[5] = aci(p[12], p[24], p[26])
    o[6] = aci(p[23], p[25], p[27])
    o[7] = aci(p[24], p[26], p[28])
    omuz = (p[11] + p[12]) / 2
    kalca = (p[23] + p[24]) / 2
    govde = omuz - kalca
    govde_boyu = max(float(np.linalg.norm(govde)), 1e-4)
    o[8] = vektor_acisi(govde, [0, -1, 0])
    o[9] = (omuz[1] - p[15][1]) / govde_boyu
    o[10] = (omuz[1] - p[16][1]) / govde_boyu
    kalca_genisligi = max(_uzaklik(p[23], p[24]), 1e-4)
    omuz_genisligi = max(_uzaklik(p[11], p[12]), 1e-4)
    o[11] = _uzaklik(p[27], p[28]) / kalca_genisligi
    o[12] = _uzaklik(p[15], p[16]) / omuz_genisligi
    return o

