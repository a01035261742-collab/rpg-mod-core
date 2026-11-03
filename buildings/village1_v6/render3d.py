"""1번 맵 3D 시안: 지형(schem 블록) + 배치된 건물 → 보이는 블록만 모아서 계단·반블록 모양까지 그림"""
import sys, json, os, numpy as np
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
sys.path.insert(0, T)
os.chdir(T)
exec(open('build_schem.py').read().split("def save(")[0].replace("print(", "(lambda *a, **k: None)("))
os.chdir('/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/village')
from layout import PLAN, res as LAY
from schem import S, render2, _rotf, COL
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
    inside = {r['name'] for r in LAY if x0 <= r['x0'] + r['w'] / 2 < x1 and z0 <= r['z0'] + r['l'] / 2 < z1}
    for (x, y, z), (st, nm) in BLD.items():
        if nm in inside:
            if st == 'minecraft:air': s.b.pop((x, y, z), None)
            else: s.b[(x, y, z)] = st
    return s

from PIL import Image, ImageDraw, ImageFont
F = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 30, index=1)
for name, box, sc, title in (('v3d_north', (70, 160, 200, 255), 7, '북쪽 마을 (광장)'), ('v3d_south', (70, 240, 200, 330), 7, '다리 건너 남쪽 마을 (공방 거리)'),
                             ('v3d_farm', (225, 170, 440, 300), 5, '농장 · 풍차 언덕 · 전사·궁수 수련장')):
    s = scene(*box)
    im = render2(s, f'{name}.png', scale=sc, bg=(150, 190, 230))
    c = Image.new('RGB', (im.width, im.height + 50), (150, 190, 230)); c.paste(im, (0, 50))
    ImageDraw.Draw(c).text((16, 8), title + ' — 3D 시안', font=F, fill=(20, 30, 50)); c.save(f'{name}.png')
    print(name, c.size, len(s.b))
