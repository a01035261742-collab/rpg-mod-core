"""3D 미리보기 (비스듬히, 남동쪽 위에서)"""
import numpy as np, sys
from PIL import Image, ImageDraw, ImageFont
H = np.load('H2.npy'); MAT = np.load('MAT2.npy', allow_pickle=True)
D, W = H.shape
COL = {'grass': (98, 156, 64), 'stone': (122, 122, 126), 'andesite': (146, 146, 142), 'coarse': (120, 92, 66), 'sand': (214, 200, 150),
       'path': (178, 148, 98), 'plaza': (172, 172, 178), 'farm': (110, 76, 50), 'water': (60, 110, 205)}
s = 3.2; k = 2.4      # 가로 크기, 높이 배율
base = H.min() - 2
ox = D * s * 1.0 + 20; oy = 120
img = Image.new('RGB', (int((W + D) * s) + 40, int((W + D) * s * 0.5 + (H.max() - base) * k) + 200), (150, 190, 230))
d = ImageDraw.Draw(img)
def P(x, z, h): return (ox + (x - z) * s, oy + (x + z) * s * 0.5 - (h - base) * k + (H.max() - base) * k)
def dim(c, f): return tuple(int(min(255, v * f)) for v in c)
for sm in range(W + D - 1):
    for x in range(max(0, sm - D + 1), min(W, sm + 1)):
        z = sm - x
        h = H[z, x]; c = COL[MAT[z, x]]
        if MAT[z, x] == 'water': h = max(h, 8)
        t = [P(x, z, h + 1), P(x + 1, z, h + 1), P(x + 1, z + 1, h + 1), P(x, z + 1, h + 1)]
        # 옆면 (남쪽·동쪽으로 보이는 면): 앞 칸보다 높은 만큼
        hs = H[z + 1, x] if z + 1 < D else base
        if h > hs: d.polygon([P(x, z + 1, h + 1), P(x + 1, z + 1, h + 1), P(x + 1, z + 1, hs + 1), P(x, z + 1, hs + 1)], fill=dim(COL['stone'] if h - hs > 1 else c, 0.62))
        he = H[z, x + 1] if x + 1 < W else base
        if h > he: d.polygon([P(x + 1, z, h + 1), P(x + 1, z + 1, h + 1), P(x + 1, z + 1, he + 1), P(x + 1, z, he + 1)], fill=dim(COL['stone'] if h - he > 1 else c, 0.78))
        d.polygon(t, fill=dim(c, 0.9 + 0.012 * (h - 10)))
F = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
d.text((16, 12), '시작 마을 지역 — 3D 미리보기 (북서쪽이 위, 높이 ×2.4 과장)', font=ImageFont.truetype(F, 24, index=1), fill=(20, 30, 50))
img.save(sys.argv[1])
