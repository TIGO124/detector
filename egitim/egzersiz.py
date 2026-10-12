"""Egzersiz duruş modeli: yapay (rastgele) 3B iskeletler -> vücut öznitelikleri (13) -> küçük MLP.

Kullanım:  egitim/.venv/Scripts/python egitim/egzersiz.py
Çıktı:     android/app/src/main/assets/yz/egzersiz.json

Etiketli egzersiz videosu olmadığı için duruşlar eklem açılarından üretilir: beden ölçüleri,
açılar, kamera yönü ve ölçüm gürültüsü rastgele. Koordinatlar MediaPipe vücut "dünya"
noktalarıyla aynı düzende (metre, kalça ortası başlangıç, y aşağı). Tekrar sayımı telefonda
duruşlar arası geçişlerden yapılır (EgzersizSayaci.java).
"""
import os
import sys

import numpy as np

KOK = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, KOK)
from ozellik import vucut  # noqa: E402

CIKTI = os.path.join(KOK, "..", "android", "app", "src", "main", "assets", "yz", "egzersiz.json")
ETIKETLER = ["Ayakta", "Kollar açık", "Squat", "Şınav yukarı", "Şınav aşağı", "Diğer"]
YUKARI = np.array([0.0, -1.0, 0.0])


def _birim(v):
    return v / np.linalg.norm(v)


def _dondur(v, eksen, aci_derece):
    """v'yi eksen etrafında döndürür (Rodrigues)."""
    k = _birim(eksen)
    a = np.radians(aci_derece)
    return v * np.cos(a) + np.cross(k, v) * np.sin(a) + k * np.dot(k, v) * (1 - np.cos(a))


def iskelet(r, govde_yonu, on_yonu, kalca, diz, omuz, omuz_yan, dirsek, bacak_acik):
    """Eklem açılarından 33 noktalı iskelet (yalnızca kullanılan noktalar dolu).

    govde_yonu: kalçadan omuza birim vektör; on_yonu: göğsün baktığı yön.
    kalca/diz/dirsek: eklem açısı (180 = düz); omuz: kolun gövdeyle açısı (0 = yanda aşağı);
    omuz_yan: kolun yana (1) ya da öne (0) kalkması; bacak_acik: bacakların yana açılma açısı.
    """
    u = _birim(govde_yonu)
    f = _birim(on_yonu - np.dot(on_yonu, u) * u)
    yan = np.cross(u, f)
    govde = r.uniform(0.45, 0.6)
    omuz_g, kalca_g = r.uniform(0.32, 0.42), r.uniform(0.24, 0.32)
    ust_kol, on_kol = r.uniform(0.26, 0.33), r.uniform(0.23, 0.29)
    uyluk, baldir = r.uniform(0.38, 0.48), r.uniform(0.36, 0.45)
    p = np.zeros((33, 3))

    def sagital(aci):
        """Gövde düzleminde aşağıdan (-u) öne (f) doğru 'aci' derece dönmüş birim yön."""
        a = np.radians(aci)
        return np.cos(a) * -u + np.sin(a) * f

    for taraf, isaret in ((0, -1), (1, 1)):
        # MediaPipe: 11/13/15/23/25/27 sol, 12/14/16/24/26/28 sağ
        o, d, b, k, dz, ay = (11, 13, 15, 23, 25, 27) if taraf == 0 else (12, 14, 16, 24, 26, 28)
        disari = isaret * yan
        p[k] = disari * kalca_g / 2
        p[o] = u * govde + disari * omuz_g / 2
        # Bacak: kalça bükülmesi öne, yana açılma; diz bükülmesi baldırı geriye döndürür
        bukulme = 180 - kalca[taraf]
        acik = np.radians(bacak_acik)
        uy = _birim(np.cos(acik) * sagital(bukulme) + np.sin(acik) * disari)
        p[dz] = p[k] + uy * uyluk
        bal = _birim(np.cos(acik) * sagital(bukulme - (180 - diz[taraf])) + np.sin(acik) * disari)
        p[ay] = p[dz] + bal * baldir
        # Kol: aşağıdan öne (omuz_yan=0) ya da yana (1) kalkar; dirsek eli başa doğru büker
        kalkis = _birim((1 - omuz_yan) * f + omuz_yan * disari)
        t = np.radians(omuz[taraf])
        kol = np.cos(t) * -u + np.sin(t) * kalkis
        p[d] = p[o] + kol * ust_kol
        dik = u - np.dot(u, kol) * kol
        dik = _birim(dik) if np.linalg.norm(dik) > 1e-6 else f
        e = np.radians(180 - dirsek[taraf])
        p[b] = p[d] + (np.cos(e) * kol + np.sin(e) * dik) * on_kol
    return p


def _ornek(r, sinif):
    a = lambda lo, hi: np.array([r.uniform(lo, hi), r.uniform(lo, hi)])  # noqa: E731
    on = np.array([0.0, 0.0, -1.0])
    # Aralıklar gerçek fotoğraflardaki ölçümlerle ayarlandı (dogrula.py): ayakta ayak açıklığı
    # ~1 kalça genişliği, kamera perspektifi gövdeye ~20° eğim katabiliyor, squat'ta gövde dik olabilir.
    if sinif == 0:  # Ayakta: dik, bacaklar düz, kollar aşağıda
        u = _dondur(YUKARI, np.array([1.0, 0, 0]), r.uniform(-20, 20))
        p = iskelet(r, u, on, a(160, 180), a(160, 180), a(0, 30), r.uniform(0, 1), a(140, 180), r.uniform(0, 5))
    elif sinif == 1:  # Kollar açık (zıplama): bacaklar ayrık, kollar yukarıda
        u = _dondur(YUKARI, np.array([1.0, 0, 0]), r.uniform(-20, 20))
        p = iskelet(r, u, on, a(160, 180), a(160, 180), a(130, 180), r.uniform(0.6, 1), a(140, 180), r.uniform(12, 30))
    elif sinif == 2:  # Squat: kalça ve diz bükük, gövde öne eğik
        u = _dondur(YUKARI, np.array([1.0, 0, 0]), -r.uniform(0, 45))
        p = iskelet(r, u, on, a(50, 115), a(45, 115), a(0, 120), r.uniform(0, 0.5), a(90, 180), r.uniform(0, 12))
    elif sinif in (3, 4):  # Şınav: gövde yatay, göğüs yere bakar; kollar düz (3) ya da bükük (4)
        yatay = _dondur(np.array([0.0, 0, -1.0]), YUKARI, r.uniform(-90, 90))
        u = _dondur(yatay, np.cross(yatay, YUKARI), r.uniform(-15, 15))
        dirsek = a(150, 180) if sinif == 3 else a(55, 110)
        omuz = a(65, 100) if sinif == 3 else a(15, 60)
        p = iskelet(r, u, np.array([0.0, 1.0, 0.0]), a(155, 180), a(155, 180), omuz, r.uniform(0, 0.3), dirsek, r.uniform(0, 10))
    else:  # Diğer: öne eğilme, sırtüstü, adım/hamle, rastgele (oturma squat'a çok benzediği için yok)
        tur = r.integers(1, 4)
        if tur == 1:  # bacaklar düz, öne eğilme
            u = _dondur(YUKARI, np.array([1.0, 0, 0]), -r.uniform(60, 100))
            p = iskelet(r, u, on, a(70, 120), a(160, 180), a(60, 120), r.uniform(0, 0.4), a(140, 180), r.uniform(0, 10))
        elif tur == 2:  # sırtüstü yatma
            yatay = _dondur(np.array([0.0, 0, -1.0]), YUKARI, r.uniform(-90, 90))
            p = iskelet(r, yatay, np.array([0.0, -1.0, 0.0]), a(150, 180), a(140, 180), a(0, 60), r.uniform(0, 1), a(120, 180), r.uniform(0, 20))
        else:  # adım/hamle: bir bacak bükük, diğeri düz; kollar rastgele
            u = _dondur(YUKARI, np.array([1.0, 0, 0]), r.uniform(-25, 25))
            kalca = np.array([r.uniform(90, 150), r.uniform(150, 180)])
            diz = np.array([r.uniform(80, 140), r.uniform(150, 180)])
            if r.random() < 0.5:
                kalca, diz = kalca[::-1].copy(), diz[::-1].copy()
            p = iskelet(r, u, on, kalca, diz, a(0, 180), r.uniform(0, 1), a(40, 180), r.uniform(10, 35))
    # Kamera: dikey eksende rastgele dönüş, hafif yatma; ölçüm gürültüsü
    p = np.array([_dondur(v, YUKARI, 0) for v in p])
    yon = r.uniform(-60, 60)
    egim = r.uniform(-8, 8)
    p = np.array([_dondur(_dondur(v, YUKARI, yon), np.array([0.0, 0, 1.0]), egim) for v in p])
    p += r.normal(0, 0.02, p.shape)
    return vucut(p)


def veri(n_sinif, tohum):
    r = np.random.default_rng(tohum)
    x, y = [], []
    for s in range(len(ETIKETLER)):
        for _ in range(n_sinif):
            x.append(_ornek(r, s))
            y.append(s)
    return np.asarray(x, np.float32), np.asarray(y, np.int64)


def main():
    from ortak import CIHAZ, json_yaz, siniflandirici_egit, tahmin
    x, y = veri(4000, 1)
    xt, yt = veri(500, 2)
    print("cihaz:", CIHAZ, "eğitim:", x.shape)
    model, ort, sap = siniflandirici_egit(x, y, ETIKETLER, [32, 16], tur=40)
    t = tahmin(model, ort, sap, xt)
    print(f"yapay test doğruluğu %{100 * float((t == yt).mean()):.1f}")
    for i, ad in enumerate(ETIKETLER):
        print(f"  {ad:13s} %{100 * float((t[yt == i] == i).mean()):.0f}")
    os.makedirs(os.path.dirname(CIKTI), exist_ok=True)
    json_yaz(CIKTI, model, ort, sap, ETIKETLER)
    print("yazıldı:", os.path.normpath(CIKTI), os.path.getsize(CIKTI), "bayt")


if __name__ == "__main__":
    main()
