"""1번 맵 3D 시안: 지형(schem 블록) + 배치된 건물 → 보이는 블록만 모아서 계단·반블록 모양까지 그림"""
import sys, json, os, numpy as np
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
sys.path.insert(0, T)
os.chdir(T)
exec(open('build_schem.py').read().split("def save(")[0].replace("print(", "(lambda *a, **k: None)("))
os.chdir('/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/village')
from layout import PLAN, res as LAY
import schem
from schem import S, render2, _rotf, COL
import veg
VEG = veg.build()
COL[:0] = [('birch_leaves', (118, 158, 74)), ('spruce_leaves', (52, 92, 58)), ('dark_oak_leaves', (58, 104, 42)), ('flowering_azalea', (120, 140, 90)),
           ('azalea_leaves', (92, 140, 56)), ('oak_leaves', (72, 128, 52)), ('birch_log', (216, 212, 200)), ('spruce_log', (72, 52, 32)), ('oak_log', (110, 84, 52)),
           ('large_fern', (70, 120, 60)), ('tall_grass', (100, 155, 62)), ('sugar_cane', (140, 190, 95)), ('lily_pad', (55, 125, 45)), ('azure_bluet', (225, 230, 240)),
           ('oxeye_daisy', (240, 240, 225)), ('cornflower', (80, 110, 210)), ('allium', (170, 100, 200)), ('red_tulip', (210, 50, 40)), ('white_tulip', (235, 235, 235)), ('pink_tulip', (235, 160, 190))]
_ob = schem._boxes
PLANTS = ('minecraft:grass[', 'minecraft:grass', 'fern', 'tall_grass', 'dandelion', 'poppy', 'cornflower', 'daisy', 'bluet', 'allium', 'tulip', 'sugar_cane', 'lily_pad')
def _nb(st):
    if st == 'minecraft:grass' or any(p in st for p in PLANTS[2:]):
        if 'lily_pad' in st: return [(0.05, 0.95, 0, 0.06, 0.05, 0.95)]
        if 'sugar_cane' in st: return [(0.3, 0.7, 0, 1, 0.3, 0.7)]
        h = 0.95 if ('tall' in st or 'large' in st) else 0.6
        flower = not any(k in st for k in ('grass', 'fern'))
        return [(0.38, 0.62, 0, 0.45, 0.38, 0.62), (0.3, 0.7, 0.45, 0.7, 0.3, 0.7)] if flower else [(0.25, 0.75, 0, h, 0.25, 0.75)]
    return _ob(st)
schem._boxes = _nb
COL[:0] = [('sand', (218, 206, 150)), ('clay', (160, 166, 178))]

TERR = {}   # (x,y,z) -> state (지형)
Pn = ['minecraft:' + p.split(':')[1] if ':' in p else p for p in P]
STEPS = {'S': 0, 'W': 1, 'N': 2, 'E': 3}
BLD = {}
for (fn, name, *_), r in zip(PLAN, LAY):
    s = fn(); n = STEPS[r['face']]
    pts = {}
    for (x, y, z), st in s.b.items():
        for _ in range(n): x, z = -z, x
        pts[(x, y, z)] = _rotf(st, n)
    mx = min(p[0] for p in pts); mz = min(p[2] for p in pts)
    fl = r['floor']
    xs, zs = set(), set()
    for (x, y, z), st in pts.items():
        X, Z = r['x0'] + x - mx, r['z0'] + z - mz
        BLD[(X, fl + y, Z)] = (st, name); xs.add(X); zs.add(Z)
    # 바닥 맞추기: 건물 자리 지형을 바닥 높이로
    for (x, y, z) in list(BLD):
        pass
    r['_cells'] = {(X, Z) for (X, _, Z) in [(r['x0'] + x - mx, 0, r['z0'] + z - mz) for (x, y, z) in pts if y == 0]}

def scene(x0, z0, x1, z1):
    s = S('scene')
    Hl = H.copy()
    for r in LAY:
        for (X, Z) in r['_cells']:
            if 0 <= X < W and 0 <= Z < D: Hl[Z, X] = r['floor'] - 1
    for z in range(z0, z1):
        for x in range(x0, x1):
            h = int(Hl[z, x]); top = h
            low = min(h, int(Hl[min(z + 1, D - 1), x]), int(Hl[z, min(x + 1, W - 1)])) - 1
            for y in range(max(0, low), min(V.shape[0], top + 7)):
                if y > top and (V[y, z, x] == 0 or 'water' in Pn[V[y, z, x]]): continue
                b = V[y, z, x] if y < V.shape[0] else 0
                st = Pn[b] if b else None
                if Hl[z, x] != H[z, x] and y == top: st = 'minecraft:dirt_path' if False else 'minecraft:grass_block'
                if st and 'barrier' not in st: s.b[(x, y, z)] = st
            if MAT[z, x] == 'water':
                for y in range(h + 1, int(WLV2[z, x]) + 1): s.b[(x, y, z)] = 'minecraft:water'
    for (x, y, z), st in VEG.items():
        if x0 <= x < x1 and z0 <= z < z1 and (x, y, z) not in s.b: s.b[(x, y, z)] = st
    inside = {r['name'] for r in LAY if x0 <= r['x0'] + r['w'] / 2 < x1 and z0 <= r['z0'] + r['l'] / 2 < z1}
    for (x, y, z), (st, nm) in BLD.items():
        if nm in inside:
            if st == 'minecraft:air': s.b.pop((x, y, z), None)
            else: s.b[(x, y, z)] = st
    return s

from PIL import Image, ImageDraw, ImageFont
F = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 30, index=1)
for name, box, sc, title in (('veg_north', (70, 160, 200, 255), 7, '북쪽 마을 + 나무·풀'), ('veg_south', (40, 240, 200, 330), 7, '남쪽 마을·연못 + 나무·풀'),
                             ('veg_forest', (40, 30, 300, 165), 4, '풀숲·초원 사냥터 + 고원 나무'), ('veg_farm', (225, 170, 440, 300), 5, '농장·풍차 언덕 + 나무·풀')):
    s = scene(*box)
    im = render2(s, f'{name}.png', scale=sc, bg=(150, 190, 230))
    c = Image.new('RGB', (im.width, im.height + 50), (150, 190, 230)); c.paste(im, (0, 50))
    ImageDraw.Draw(c).text((16, 8), title + ' — 3D 시안', font=F, fill=(20, 30, 50)); c.save(f'{name}.png')
    print(name, c.size, len(s.b))
