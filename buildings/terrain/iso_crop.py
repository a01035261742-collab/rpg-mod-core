import numpy as np, json, sys
from PIL import Image, ImageDraw, ImageFont
H = np.load('H2.npy'); MAT = np.load('MAT2.npy', allow_pickle=True); M = json.load(open('meta.json'))
SC, OX, OZ = M['SC'], M['OFFX'], M['OFFZ']
def G(x, z): return int(round((x + OX) * SC)), int(round((z + OZ) * SC))
x0, z0 = G(186, 76); x1, z1 = G(278, 152)
H = H[z0:z1, x0:x1]; MAT = MAT[z0:z1, x0:x1]; D, W = H.shape
COL = {'grass': (98, 156, 64), 'stone': (122, 122, 126), 'andesite': (146, 146, 142), 'coarse': (120, 92, 66), 'sand': (214, 200, 150),
       'path': (178, 148, 98), 'plaza': (172, 172, 178), 'farm': (222, 196, 92), 'farm2': (222, 196, 92), 'water': (60, 110, 205)}
s = 5.0; k = 4.0; base = H.min() - 2
ox = D * s + 20; oy = 80
img = Image.new('RGB', (int((W + D) * s) + 40, int((W + D) * s * 0.5 + (H.max() - base) * k) + 140), (150, 190, 230))
d = ImageDraw.Draw(img)
def P(x, z, h): return (ox + (x - z) * s, oy + (x + z) * s * 0.5 - (h - base) * k + (H.max() - base) * k)
def dim(c, f): return tuple(int(min(255, v * f)) for v in c)
rng = np.random.default_rng(1)
for sm in range(W + D - 1):
    for x in range(max(0, sm - D + 1), min(W, sm + 1)):
        z = sm - x; h = H[z, x]; c = COL[MAT[z, x]]
        crop = MAT[z, x] in ('farm', 'farm2')
        hs = H[z + 1, x] if z + 1 < D else base
        if h > hs: d.polygon([P(x, z + 1, h + 1), P(x + 1, z + 1, h + 1), P(x + 1, z + 1, hs + 1), P(x, z + 1, hs + 1)], fill=dim((120, 90, 60) if crop else c, 0.62))
        he = H[z, x + 1] if x + 1 < W else base
        if h > he: d.polygon([P(x + 1, z, h + 1), P(x + 1, z + 1, h + 1), P(x + 1, z + 1, he + 1), P(x + 1, z, he + 1)], fill=dim((120, 90, 60) if crop else c, 0.78))
        top = h + 1 + (0.8 if crop else 0)
        d.polygon([P(x, z, top), P(x + 1, z, top), P(x + 1, z + 1, top), P(x, z + 1, top)], fill=dim(c, 0.92 + 0.02 * (h - 10) + (rng.random() * 0.12 if crop else 0)))
mx, mz = G(232, 110); mx -= x0; mz -= z0
F = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
px, py = P(mx, mz, H[mz, mx] + 1)
d.rectangle((px - 14, py - 70, px + 14, py), fill=(130, 95, 60), outline=(60, 40, 25), width=2)
for a in range(4):
    import math
    t = math.radians(45 + a * 90); d.line((px, py - 62, px + 46 * math.cos(t), py - 62 + 46 * math.sin(t)), fill=(240, 235, 220), width=6)
d.text((16, 12), '풍차 언덕 밀밭 확대 (밀 심은 모습 예시 · 풍차는 자리 표시만)', font=ImageFont.truetype(F, 24, index=1), fill=(20, 30, 50))
img.save(sys.argv[1])
