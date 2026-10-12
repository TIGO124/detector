"""Duygu modeli: FER-2013 yüzleri -> MediaPipe yüz ifade puanları (52) -> küçük MLP (ekran kartı).

Kullanım:  egitim/.venv/Scripts/python egitim/duygu.py
Çıktı:     android/app/src/main/assets/yz/duygu.json  (uygulama bunu hazır model olarak kullanır)

Veri: Hugging Face "Aaryan333/fer2013_train_publicTest_privateTest" (parquet) -> egitim/veri/fer/.
FER-2013 yüzleri 48x48 gri; MediaPipe için büyütülür. Telefon da aynı 52 puanı üretir.
"""
import io
import os
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np

KOK = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, KOK)
VERI = os.path.join(KOK, "veri", "fer")
MODEL = os.path.join(KOK, "..", "public", "mediapipe", "models", "face_landmarker.task")
CIKTI = os.path.join(KOK, "..", "android", "app", "src", "main", "assets", "yz", "duygu.json")
# FER-2013 sırası: 0 kızgın, 1 iğrenmiş, 2 korkmuş, 3 mutlu, 4 üzgün, 5 şaşkın, 6 nötr
ETIKETLER = ["Kızgın", "İğrenmiş", "Korkmuş", "Mutlu", "Üzgün", "Şaşkın", "Nötr"]

_isaretci = None


def _baslat():
    global _isaretci
    import mediapipe as mp
    from mediapipe.tasks.python import vision
    from mediapipe.tasks.python.core.base_options import BaseOptions
    _isaretci = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=MODEL),
        output_face_blendshapes=True,
        num_faces=1,
        min_face_detection_confidence=0.3,
        min_face_presence_confidence=0.3,
    ))


def _puanlar(resim_bayt):
    """Bir yüz resmi -> 52 ifade puanı ya da yüz bulunamazsa None."""
    import mediapipe as mp
    from PIL import Image
    resim = Image.open(io.BytesIO(resim_bayt)).convert("RGB").resize((192, 192), Image.BICUBIC)
    sonuc = _isaretci.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.asarray(resim)))
    if not sonuc.face_blendshapes:
        return None
    o = np.zeros(52, np.float32)
    for c in sonuc.face_blendshapes[0]:
        o[c.index] = c.score
    return o


def _bolum(ad):
    """Parquet bölümünü okur, özniteliklere çevirir; sonucu önbelleğe yazar."""
    onbellek = os.path.join(VERI, ad + "_puanlar.npz")
    if os.path.exists(onbellek):
        d = np.load(onbellek)
        return d["x"], d["y"]
    import pyarrow.parquet as pq
    tablo = pq.read_table(os.path.join(VERI, ad + ".parquet")).to_pydict()
    resimler = [r["bytes"] for r in tablo["image"]]
    etiketler = tablo["label"]
    with ProcessPoolExecutor(max_workers=os.cpu_count() - 2, initializer=_baslat) as havuz:
        puanlar = list(havuz.map(_puanlar, resimler, chunksize=64))
    secili = [i for i, p in enumerate(puanlar) if p is not None]
    x = np.stack([puanlar[i] for i in secili])
    y = np.asarray([etiketler[i] for i in secili], np.int64)
    print(f"{ad}: {len(resimler)} resim, yüz bulunan {len(secili)} (%{100 * len(secili) / len(resimler):.0f})")
    np.savez(onbellek, x=x, y=y)
    return x, y


def main():
    from ortak import CIHAZ, json_yaz, siniflandirici_egit, tahmin
    x, y = _bolum("train")
    xd, yd = _bolum("publicTest")
    xt, yt = _bolum("privateTest")
    print("cihaz:", CIHAZ)

    # Ayar: gizli katman boyu publicTest ile seçilir, sonuç privateTest ile raporlanır.
    en_iyi = None
    for gizli in ([64], [128, 64], [256, 128]):
        model, ort, sap = siniflandirici_egit(x, y, ETIKETLER, gizli, tur=80, gurultu=0.03)
        d = float((tahmin(model, ort, sap, xd) == yd).mean())
        print(f"gizli {gizli}: publicTest doğruluk %{100 * d:.1f}")
        if en_iyi is None or d > en_iyi[0]:
            en_iyi = (d, gizli, model, ort, sap)
    _, gizli, model, ort, sap = en_iyi
    t = tahmin(model, ort, sap, xt)
    print(f"seçilen {gizli}: privateTest doğruluk %{100 * float((t == yt).mean()):.1f}")
    for i, ad in enumerate(ETIKETLER):
        m = yt == i
        print(f"  {ad:9s} %{100 * float((t[m] == i).mean()):.0f}  ({int(m.sum())} örnek)")

    os.makedirs(os.path.dirname(CIKTI), exist_ok=True)
    json_yaz(CIKTI, model, ort, sap, ETIKETLER)
    print("yazıldı:", os.path.normpath(CIKTI), os.path.getsize(CIKTI), "bayt")


if __name__ == "__main__":
    main()
