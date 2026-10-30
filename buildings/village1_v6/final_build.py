"""1번 맵 최종: 지형 + 건물 25개 + 나무·풀 → 6조각 schem, 건물 schem 25개 따로, 자가 검토"""
import sys, os, json, gzip, numpy as np
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
VD = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/village/'
sys.path.insert(0, T); os.chdir(T)
exec(open('build_schem.py').read().split("def save(")[0].replace("print(", "(lambda *a, **k: None)("))
os.chdir(VD); sys.path.insert(0, VD)
from layout import PLAN, res as LAY
from schem import _rotf, _tag
import importlib.util as _iu
_sp = _iu.spec_from_file_location('veg', VD + 'veg.py'); veg = _iu.module_from_spec(_sp); _sp.loader.exec_module(veg)   # 마을 폴더 veg.py 확실히

Pn = [('minecraft:' + p.split(':', 1)[1]) for p in P]
PAL = {st: i for i, st in enumerate(Pn)}
def idx(st):
    if st not in PAL: PAL[st] = len(PAL); Pn.append(st)
    return PAL[st]
TOPN = 80
V2 = np.zeros((TOPN, D, W), np.uint16); V2[:V.shape[0]] = V
AIR = PAL['minecraft:air']
# 투명 방벽 (build_schem 의 save 뒤쪽 코드와 같음)
from scipy.ndimage import maximum_filter as _mf
_wallz = gaussian_filter((distance_transform_edt(~WALK) >= 4.5).astype(float), 3) > 0.5
_allowed = WALK | ((MAT == 'water') & ~_wallz)
_dA = distance_transform_edt(~_allowed)
_crest = (_dA >= _mf(_dA, 5) - 0.5) & (_dA > 3) & (_dA <= 11)
RING = ((_dA > 9) & (_dA <= 11)) | _crest
BAR = idx('minecraft:barrier')
for z, x in zip(*np.nonzero(RING)):
    h = int(H[z, x]); top = max(h, WATER) if MAT[z, x] == 'water' else h
    V2[h + 1:top + 5, z, x] = BAR
print('방벽 칸', int(RING.sum()))
# ── 건물 넣기 (자리 평평하게: 바닥 아래 흙 채우고 위 지형 비움)
STEPS = {'S': 0, 'W': 1, 'N': 2, 'E': 3}
Hf = H.copy(); body = np.zeros((D, W), bool); placed = []
GROUND = ('dirt_path', 'coarse_dirt', 'gravel', 'cobblestone', 'mossy_cobblestone', 'polished_andesite', 'andesite', 'spruce_planks', 'oak_planks',
          'polished_deepslate', 'deepslate_tiles', 'chiseled_deepslate', 'stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks')
from alib import declutter
def bake(s, r):
    """건물 넣기: 바닥(y=0)이 땅 위 한 칸 (안 파묻힘). 위에 아무것도 없는 바닥 깔기(광장 포장·마당)는 땅 높이 그대로"""
    n = STEPS[r['face']]; pts = {}
    for (x, y, z), st in s.b.items():
        for _ in range(n): x, z = -z, x
        pts[(x, y, z)] = _rotf(st, n)
    mx = min(p[0] for p in pts); mz = min(p[2] for p in pts); fl = r['floor']
    cols = {}
    for (x, y, z), st in pts.items(): cols.setdefault((r['x0'] + x - mx, r['z0'] + z - mz), []).append((y, st))
    for (X, Z), lst in cols.items():
        if not (0 <= X < W and 0 <= Z < D): continue
        flat = all(y <= 0 for y, _ in lst) and all(st.split(':')[1].split('[')[0] in GROUND for y, st in lst if y == 0)
        b0 = fl if flat else fl + 1
        if min(y for y, _ in lst) <= 0:
            top = int(H[Z, X])
            for y in range(b0 + (0 if flat else 1), max(top, b0) + 1):
                if y != b0 or flat is False:
                    if 'barrier' not in Pn[V2[y, Z, X]]: V2[y, Z, X] = AIR
            for y in range(top + 1, b0): V2[y, Z, X] = idx('minecraft:dirt')
            Hf[Z, X] = b0
        for y, st in lst:
            yy = b0 + y
            if 0 <= yy < TOPN: V2[yy, Z, X] = idx(st if st.startswith('minecraft:') else 'minecraft:' + st)
            if y >= 1 and st != 'minecraft:air': body[Z, X] = True
for (fn, name, *_), r in zip(PLAN, LAY):
    bake(declutter(fn()), r)
    placed.append(name)
# ── 나무·풀 (빈 칸에만)
VEG = veg.build()
nveg = 0
for (x, y, z), st in VEG.items():
    if 0 <= x < W and 0 <= z < D and 0 <= y < TOPN and V2[y, z, x] == AIR and not body[z, x]:
        V2[y, z, x] = idx(st); nveg += 1
print('건물', len(placed), '· 식생 블록', nveg, '· 팔레트', len(Pn))

# ── 자가 검토 1: 걸어서 다닐 수 있는지 (나무 줄기·덤불·건물 = 막힘, 한 칸 턱까지)
SOLID_SKIP = ('air', 'grass', 'fern', 'flower', 'tulip', 'daisy', 'bluet', 'allium', 'poppy', 'dandelion', 'cornflower', 'carpet', 'lily_pad', 'sugar_cane', 'barrier', 'water', 'path', 'pressure')
solid_ids = np.array([not any(k in st.split(':')[1].split('[')[0] for k in SOLID_SKIP) or st.split(':')[1].split('[')[0] in ('grass_block', 'dirt_path') for st in Pn])
from collections import deque
surf = Hf.copy()
blocked = np.zeros((D, W), bool)
for z in range(D):
    for x in range(W):
        h = surf[z, x]
        if h + 2 < TOPN and (solid_ids[V2[h + 1, z, x]] or solid_ids[V2[h + 2, z, x]]): blocked[z, x] = True
barr = np.isin(V2[np.clip(Hf + 1, 0, TOPN - 1), np.arange(D)[:, None], np.arange(W)[None]], [i for i, st in enumerate(Pn) if 'barrier' in st])
wm = (MAT == 'water')
def bfs(start):
    seen = np.zeros((D, W), bool); q = deque([start]); seen[start[1], start[0]] = True
    while q:
        x, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if 0 <= nx < W and 0 <= nz < D and not seen[nz, nx] and not blocked[nz, nx] and not barr[nz, nx] and surf[nz, nx] - surf[z, x] <= 1:
                seen[nz, nx] = True; q.append((nx, nz))
    return seen
px, pz = M['plaza']
seen = bfs((px + 8, pz))
K = 1.3; Tf = lambda p: (62 + (p[0] - 62) * K, 92 + (p[1] - 92) * K); G = lambda p: (int((p[0] + 25) * 1.5), int((p[1] + 45) * 1.5))
spots = {'남쪽 마을': G((66, 146)), '연못': G((14, 132)), '농장(사냥터1)': G(Tf((138, 103))), '초원(사냥터2)': G(Tf((166, 48))), '풍차 언덕': (400, 214),
         '풀숲(사냥터3·4)': G(Tf((56, 26))), '북쪽 출구': None}
def near_ok(pt, r=4):
    x, z = pt
    return bool(seen[max(0, z - r):z + r + 1, max(0, x - r):x + r + 1].any())
rep = {k: (near_ok(v) if v else bool(seen[0].any())) for k, v in spots.items()}
print('도달:', rep)
door_ok = {}
for d in json.load(open(T + 'door_polys.json')):
    a = tuple(int(round(v)) for v in d[0]); door_ok[a] = near_ok(a, 2)
print('문 앞 도달', sum(door_ok.values()), '/', len(door_ok))
# 방벽 밖으로 새는지
from scipy.ndimage import distance_transform_edt
allowed = WALK | wm; dA = distance_transform_edt(~allowed)
print('방벽 밖 새는 칸', int((seen & (dA > 12)).sum()))
# 물 새는지
wid = [i for i, st in enumerate(Pn) if st.split('[')[0] == 'minecraft:water']
w = np.isin(V2, wid); air = V2 == AIR
leak = np.zeros_like(w)
for ax, sh in ((1, 1), (1, -1), (2, 1), (2, -1)): leak |= w & np.roll(air, sh, ax)
leak[:, :, [0, -1]] = False; leak[:, [0, -1], :] = False
print('물 옆이 빈 칸', int(leak.sum()), '(개울 계단 폭포 18칸은 일부러)')
for (y, z, x) in np.argwhere(leak):
    if not (25 <= x <= 50 and 260 <= z <= 280): print('  개울 밖 새는 물', x, y, z, MAT[z, x])
# 나무가 길 위에 있는지
on_road = 0
for (x, y, z), st in VEG.items():
    if 0 <= x < W and 0 <= z < D and MAT[z, x] in ('path', 'plaza') and y <= Hf[z, x] + 2 and 'leaves' not in st: on_road += 1
print('길 위 식생(머리 높이 아래)', on_road)
json.dump({'reach': rep, 'doors': f"{sum(door_ok.values())}/{len(door_ok)}"}, open('review.json', 'w'), ensure_ascii=False)

# ── 저장 (varint)
def save(path, x0, z0, x1, z1):
    sub = V2[:, z0:z1, x0:x1]; Hh, L, Wd = sub.shape
    v = sub.ravel().astype(np.int64)
    b0 = np.where(v < 128, v, (v & 127) | 128).astype(np.uint8); b1 = (v >> 7).astype(np.uint8)
    keep = np.stack([np.ones_like(v, bool), v >= 128], 1).ravel()
    data = np.stack([b0, b1], 1).ravel()[keep].tobytes()
    root = {'Version': (3, 2), 'DataVersion': (3, 3465), 'Width': (2, Wd), 'Height': (2, Hh), 'Length': (2, L),
            'PaletteMax': (3, len(Pn)), 'Palette': (10, {n: (3, i) for i, n in enumerate(Pn)}),
            'BlockData': (7, data), 'Offset': (11, [0, 0, 0]),
            'Metadata': (10, {'WEOffsetX': (3, x0 - PLAZA[0]), 'WEOffsetY': (3, 0 - (BASE + 1)), 'WEOffsetZ': (3, z0 - PLAZA[1])})}
    b = bytearray(); _tag(b, 10, 'Schematic', root)
    open(path, 'wb').write(gzip.compress(bytes(b))); return os.path.getsize(path)
import shutil
od = VD + 'final1'; shutil.rmtree(od, ignore_errors=True); os.makedirs(od)
xs = [0, W // 3, 2 * W // 3, W]; zs = [0, D // 2, D]; i = 0
for zi in range(2):
    for xi in range(3):
        i += 1; print(f'start_village_{i}', save(f'{od}/start_village_{i}.schem', xs[xi], zs[zi], xs[xi + 1], zs[zi + 1]))
np.save(VD + 'V_final.npy', V2); json.dump(Pn, open(VD + 'P_final.json', 'w'))
