"""Java ile Python'un aynı hesabı yaptığını doğrulayan örnekleri üretir.

Kullanım:  egitim/.venv/Scripts/python egitim/fikstur.py
Çıktı:     android/app/src/test/resources/ozellik_ornekleri.json  (YzOzellikTest, SinirAgiTest okur)
"""
import json
import os
import sys

import numpy as np

KOK = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, KOK)
import hizala  # noqa: E402
import ozellik  # noqa: E402
from dogrula import json_calistir  # noqa: E402

CIKTI = os.path.join(KOK, "..", "android", "app", "src", "test", "resources", "ozellik_ornekleri.json")
YZ = os.path.join(KOK, "..", "android", "app", "src", "main", "assets", "yz")


def main():
    r = np.random.default_rng(42)
    ornek = {"el": [], "vucut": [], "hizalama": [], "model": []}
    for sol in (False, True):
        p = r.normal(0, 0.05, (21, 3)).astype(np.float32)
        ornek["el"].append({"noktalar": p.tolist(), "sol": sol, "beklenen": ozellik.el(p, sol).tolist()})
    for _ in range(2):
        p = r.normal(0, 0.4, (33, 3)).astype(np.float32)
        ornek["vucut"].append({"noktalar": p.tolist(), "beklenen": ozellik.vucut(p).tolist()})
    p = r.uniform(0.2, 0.8, (478, 3)).astype(np.float32)
    bes = hizala.bes_nokta(p, 480, 640)
    ornek["hizalama"].append({"noktalar": p.tolist(), "g": 480, "y": 640, "bes": bes.reshape(-1).tolist(),
                              "kalip": hizala.KALIP.reshape(-1).tolist(),
                              "beklenen": hizala.benzerlik(bes, hizala.KALIP).reshape(-1).tolist()})
    for ad in ("duygu", "egzersiz"):
        model = json.load(open(os.path.join(YZ, ad + ".json"), encoding="utf-8"))
        x = r.normal(0, 1, len(model["ortalama"])).astype(np.float32) * np.asarray(model["sapma"]) + np.asarray(model["ortalama"])
        ornek["model"].append({"dosya": ad, "girdi": x.tolist(), "beklenen": json_calistir(model, x).tolist()})
    os.makedirs(os.path.dirname(CIKTI), exist_ok=True)
    with open(CIKTI, "w", encoding="utf-8") as f:
        json.dump(ornek, f)
    print("yazıldı:", os.path.normpath(CIKTI))


if __name__ == "__main__":
    main()
