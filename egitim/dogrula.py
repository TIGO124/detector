"""Hazır modelleri gerçek fotoğraflarla dener. Tahmin, uygulamaya giden JSON'dan numpy ile yapılır
(SinirAgi.java ile aynı hesap): böylece dışa aktarılan dosya da doğrulanır.

Kullanım:  egitim/.venv/Scripts/python egitim/dogrula.py egzersiz egitim/veri/egzersiz/*.jpg
"""
import json
import os
import sys

import numpy as np

KOK = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, KOK)
import ozellik  # noqa: E402

MODELLER = os.path.join(KOK, "..", "public", "mediapipe", "models")
YZ = os.path.join(KOK, "..", "android", "app", "src", "main", "assets", "yz")


def json_calistir(model, x):
    a = (np.asarray(x, np.float32) - np.asarray(model["ortalama"])) / np.asarray(model["sapma"])
    katmanlar = model["katmanlar"]
    for i, k in enumerate(katmanlar):
        a = np.asarray(k["agirlik"]) @ a + np.asarray(k["esik"])
        if i < len(katmanlar) - 1:
            a = np.maximum(a, 0)
    if model["cikis"] == "softmax":
        e = np.exp(a - a.max())
        return e / e.sum()
    return a / np.linalg.norm(a)


def egzersiz(dosyalar):
    import mediapipe as mp
    from mediapipe.tasks.python import vision
    from mediapipe.tasks.python.core.base_options import BaseOptions
    model = json.load(open(os.path.join(YZ, "egzersiz.json"), encoding="utf-8"))
    isaretci = vision.PoseLandmarker.create_from_options(vision.PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=os.path.join(MODELLER, "pose_landmarker_lite.task"))))
    for d in dosyalar:
        sonuc = isaretci.detect(mp.Image.create_from_file(d))
        if not sonuc.pose_world_landmarks:
            print(f"{os.path.basename(d):12s} vücut bulunamadı")
            continue
        p = [[l.x, l.y, l.z] for l in sonuc.pose_world_landmarks[0]]
        olas = json_calistir(model, ozellik.vucut(p))
        sira = np.argsort(-olas)[:2]
        print(f"{os.path.basename(d):12s} " + ", ".join(f"{model['etiketler'][i]} %{100 * olas[i]:.0f}" for i in sira))


if __name__ == "__main__":
    {"egzersiz": egzersiz}[sys.argv[1]](sys.argv[2:])
