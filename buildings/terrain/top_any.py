import numpy as np, sys, os
from PIL import Image
exec(open(sys.argv[1]).read().split("def save(")[0].replace("print(", "(lambda *a, **k: None)("))
src = open('vox_iso.py').read(); exec(src[src.index('COLS = ['):src.index("F = '/usr")])
Vb = V.copy(); Vb[Vb == I['barrier']] = 0
top = (Vb.shape[0] - 1 - np.argmax(Vb[::-1] > 0, axis=0))
cols = np.array([col(n) for n in P], float)
arr = cols[Vb[top, np.arange(D)[:, None], np.arange(W)[None]]]
gx, gz = np.gradient(top.astype(float)); arr *= np.clip(1 + 0.25 * (-gx - 1.2 * gz) / 1.5, .6, 1.3)[..., None]
im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
for spec in sys.argv[2:]:
    nm, x0, z0, w, h = spec.split(':'); x0, z0, w, h = map(int, (x0, z0, w, h))
    im.crop((x0, z0, x0 + w, z0 + h)).resize((w * 6, h * 6), Image.NEAREST).save(nm)
