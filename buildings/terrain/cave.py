"""어둠거미여왕 레이드 경기장: 닫힌 동굴 (천장 있음, 천장 숨기용 높이 16~18). 기준점 = 가운데 바닥 위."""
import numpy as np, sys, gzip, os
sys.path.insert(0, '/home/claude/rpg-mod-core/buildings')
from schem import _tag
from arena import vnoise
R, FLAT, BASE = 28, 32, 6
N = 2 * (FLAT + 10) + 1; c = N // 2
X, Z = np.meshgrid(np.arange(N), np.arange(N)); rr = np.sqrt((X - c) ** 2 + (Z - c) ** 2)
edge = (vnoise(N, 10, 5) - 0.5) * 5 + (vnoise(N, 4, 6) - 0.5) * 2
inside = rr + edge < FLAT + 3                      # 동굴 안 (공기)
floorflat = rr < FLAT
# 바닥: 가운데 평평, 가장자리 살짝 올라감
Hf = np.where(floorflat, BASE, BASE + np.clip(rr + edge - FLAT, 0, None) * 1.5).round().astype(int)
# 천장: 가운데 BASE+18, 가장자리로 내려옴 + 울퉁불퉁 (종유석 느낌)
Hc = (BASE + 18 - 6 * np.clip(rr / (FLAT + 3), 0, 1) ** 2 + (vnoise(N, 6, 9) - 0.5) * 4 - (vnoise(N, 3, 10) > 0.8) * 2).round().astype(int)
TOP = BASE + 24
pal = ['minecraft:air', 'minecraft:deepslate', 'minecraft:tuff', 'minecraft:blackstone', 'minecraft:mud', 'minecraft:packed_mud',
       'minecraft:coarse_dirt', 'minecraft:glowstone', 'minecraft:stone_bricks', 'minecraft:polished_andesite', 'minecraft:cobbled_deepslate']
I = {n.split(':')[1]: i for i, n in enumerate(pal)}
rng = np.random.default_rng(4)
V = np.zeros((TOP + 1, N, N), np.uint8)
rock = rng.choice([I['deepslate'], I['tuff'], I['blackstone'], I['cobbled_deepslate']], size=(TOP + 1, N, N), p=[0.5, 0.25, 0.1, 0.15]).astype(np.uint8)
V[:] = rock                                         # 전부 바위로 채우고
Y = np.arange(TOP + 1)[:, None, None]
air = inside[None] & (Y > Hf[None]) & (Y < Hc[None])
V[air] = 0                                           # 동굴 파기
# 바닥 재질
fl = rng.choice([I['mud'], I['packed_mud'], I['coarse_dirt']], size=(N, N), p=[0.5, 0.3, 0.2])
for z, x in zip(*np.nonzero(inside)): V[Hf[z, x], z, x] = fl[z, x]
# 원 표시 · 가운데 · 들어오는 자리 (남쪽)
ring = (np.abs(rr - R) < 0.5); V[BASE, ring] = I['polished_andesite']; V[BASE, c, c] = I['stone_bricks']
V[BASE, (np.abs(X - c) <= 2) & (np.abs(Z - (c + R - 3)) <= 2)] = I['stone_bricks']
# 천장 빛 (발광석 무리, 7칸 간격쯤)
for z in range(3, N - 3, 7):
    for x in range(3, N - 3, 7):
        zz, xx = z + rng.integers(-2, 3), x + rng.integers(-2, 3)
        if inside[zz, xx]: V[Hc[zz, xx], zz, xx] = I['glowstone']; V[Hc[zz, xx], zz, min(N - 1, xx + 1)] = I['glowstone']
Hh, L, Wd = V.shape
root = {'Version': (3, 2), 'DataVersion': (3, 3465), 'Width': (2, Wd), 'Height': (2, Hh), 'Length': (2, L),
        'PaletteMax': (3, len(pal)), 'Palette': (10, {n: (3, i) for i, n in enumerate(pal)}),
        'BlockData': (7, V.tobytes()), 'Offset': (11, [0, 0, 0]),
        'Metadata': (10, {'WEOffsetX': (3, -c), 'WEOffsetY': (3, -(BASE + 1)), 'WEOffsetZ': (3, -c)})}
b = bytearray(); _tag(b, 10, 'Schematic', root)
os.makedirs('arena_out', exist_ok=True)
open('arena_out/arena_queen.schem', 'wb').write(gzip.compress(bytes(b)))
print('cave', N, Hh, '천장 높이 (바닥 위): 가운데', int(Hc[c, c] - BASE), '가장자리', int(Hc[c, c + FLAT] - BASE))
# 단면 미리보기
from PIL import Image, ImageDraw, ImageFont
img = Image.new('RGB', (N * 8 + 40, (TOP + 1) * 8 + 80), (20, 22, 28)); d = ImageDraw.Draw(img)
cols = {0: (24, 26, 34), I['glowstone']: (250, 210, 120), I['mud']: (60, 56, 60), I['packed_mud']: (140, 105, 80), I['coarse_dirt']: (120, 92, 66),
        I['polished_andesite']: (200, 200, 200), I['stone_bricks']: (150, 150, 150)}
for y in range(TOP + 1):
    for x in range(N):
        v = int(V[y, c, x]); d.rectangle((20 + x * 8, 60 + (TOP - y) * 8, 27 + x * 8, 67 + (TOP - y) * 8), fill=cols.get(v, (70, 70, 78)))
F = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
d.text((20, 12), f'어둠거미여왕 레이드 동굴 — 가운데를 자른 단면 (천장 가운데 {int(Hc[c, c] - BASE)}칸 · 가장자리 {int(Hc[c, c + FLAT] - BASE)}칸)', font=ImageFont.truetype(F, 22, index=1), fill=(240, 230, 200))
img.save('cave_section.png')
