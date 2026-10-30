"""2지역 최종: 지형(build_r2) + 골목·문 앞 길 + 건물 14 + 사냥터 구조물 7 + 식생(입구 숲 → 안쪽 버섯) → region2_1..6, 자가 검토"""
import sys, os, json, gzip, numpy as np
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
VD = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/village/'
sys.path.insert(0, T); os.chdir(T)
src = open('build_r2.py').read().split("def save(")[0].replace("R2_MAT.npy", "R2_MATv.npy").replace("print(", "(lambda *a, **k: None)(")
exec(src)
blockmix.natural_paths(V, H, MAT, I, json.load(open('r2_streets.json')), pick, okm, seed=52, wscale=0.8, keep_old=True)
blockmix.natural_paths(V, H, MAT, I, json.load(open('r2_door_polys.json')), pick, okm, seed=53, wscale=0.6, keep_old=True)
os.chdir(VD); sys.path.insert(0, VD)
import importlib.util as _iu
def _load(nm):
    sp = _iu.spec_from_file_location(nm, VD + nm + '.py'); m = _iu.module_from_spec(sp); sp.loader.exec_module(m); return m
r2_place = _load('r2_place'); r2_veg = _load('r2_veg')
from schem import _rotf, _tag
from mlib import mush_faces

Pn = [('minecraft:' + p.split(':', 1)[1]) for p in P]
PAL = {st: i for i, st in enumerate(Pn)}
def idx(st):
    st = st if st.startswith('minecraft:') else 'minecraft:' + st
    if st not in PAL: PAL[st] = len(PAL); Pn.append(st)
    return PAL[st]
TOPN = 80
V2 = np.zeros((TOPN, D, W), np.uint16); V2[:V.shape[0]] = V
AIR = PAL['minecraft:air']; BAR = PAL['minecraft:barrier']
# ── 건물 (자리 평평하게: 바닥 아래 흙 채우고 위 지형 비움)
STEPS = {'S': 0, 'W': 1, 'N': 2, 'E': 3}
LAY = json.load(open(VD + 'r2_layout.json')); fns = {fn.__name__: fn for fn, *_ in r2_place.PLAN}
Hf = H.copy(); body = np.zeros((D, W), bool)
from alib import declutter
from bake import bake, floating
BPOS = []
VILLAGE_FN = {fn.__name__ for fn, *_ in r2_place.PLAN[:17]}
FLAT2 = {'plaza3', 'well3', 'waystone2', 'stalls3', 'spore_camp', 'spirit_altar', 'soldier_camp', 'deep_post', 'witch_hut', 'beast_nest'}   # 땅에 놓이는 것
for r in LAY:
    s = fns[r['fn']]()
    if r['fn'] in VILLAGE_FN: declutter(s)          # 마을 건물 안팎 소품 치움 (사냥터 구조물은 이야기 소품이라 남김)
    n = STEPS[r['face']]; pts = {}
    for (x, y, z), st in s.b.items():
        for _ in range(n): x, z = -z, x
        pts[(x, y, z)] = _rotf(st, n)
    bake(pts, r, r['fn'] not in FLAT2, V2, H, Hf, body, idx, AIR, BAR, W, D, TOPN, BPOS)
# ── 식생 (빈 칸에만)
VEG = r2_veg.build(); nveg = 0; VPOS = []
for (x, y, z), st in VEG.items():
    if 0 <= x < W and 0 <= z < D and 0 <= y < TOPN and V2[y, z, x] == AIR and not body[z, x]:
        V2[y, z, x] = idx(st); nveg += 1
        if 'mushroom_block' in st or 'stem' in st or 'leaves' in st or 'shroomlight' in st: VPOS.append((y, z, x))
from bake import floating as _fl
_fv = _fl(V2, AIR, BAR, VPOS, Pn, fix_small=300); print('떠 있는 버섯·잎 작은 조각 지움, 남은 큰 조각', len(_fv))
# ── 큰 버섯 블록 면 상태 (같은 블록끼리 붙은 면 숨김, 갓 밑면 = 주름)
MB = {i: st for i, st in enumerate(Pn) if st.split('[')[0] in ('minecraft:red_mushroom_block', 'minecraft:brown_mushroom_block', 'minecraft:mushroom_stem')}
mask = np.isin(V2, list(MB))
coords = [((int(x), int(y), int(z)), Pn[V2[y, z, x]].split('[')[0]) for y, z, x in np.argwhere(mask)]
def _get(x, y, z):
    if 0 <= x < W and 0 <= z < D and 0 <= y < TOPN: return Pn[V2[y, z, x]]
    return None
def _set(x, y, z, st): V2[y, z, x] = idx(st)
mush_faces(_get, _set, coords)
print('건물', len(LAY), '· 식생 블록', nveg, '· 버섯 블록', len(coords), '· 팔레트', len(Pn))

# ── 자가 검토: 걸어서 다니기 (입구 → 마을·문·사냥터·보스 입구·출구)
SOLID_SKIP = ('air', 'grass', 'fern', 'flower', 'tulip', 'daisy', 'bluet', 'allium', 'poppy', 'dandelion', 'cornflower', 'carpet', 'mushroom', 'barrier', 'water', 'path', 'pressure', 'lily')
base_names = [st.split(':')[1].split('[')[0] for st in Pn]
solid_ids = np.array([(not any(k in b for k in SOLID_SKIP)) or b in ('grass_block', 'dirt_path', 'red_mushroom_block', 'brown_mushroom_block', 'mushroom_stem') for b in base_names])
from collections import deque
surf = Hf.copy()
zi, xi = np.mgrid[0:D, 0:W]
blocked = solid_ids[V2[np.clip(surf + 1, 0, TOPN - 1), zi, xi]] | solid_ids[V2[np.clip(surf + 2, 0, TOPN - 1), zi, xi]]
barr = (V2[np.clip(surf + 1, 0, TOPN - 1), zi, xi] == BAR)
wm2 = np.isin(MAT, ['water', 'bog'])
def bfs(starts):
    seen = np.zeros((D, W), bool); q = deque(starts)
    for x, z in starts: seen[z, x] = True
    while q:
        x, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if 0 <= nx < W and 0 <= nz < D and not seen[nz, nx] and not blocked[nz, nx] and not barr[nz, nx] and (surf[nz, nx] - surf[z, x] <= 1 or wm2[nz, nx]):
                seen[nz, nx] = True; q.append((nx, nz))
    return seen
starts = [(x, D - 1) for x in np.nonzero(WALK[D - 1])[0] if not blocked[D - 1, x]]
seen = bfs(starts)
def near_ok(pt, rr=4):
    x, z = pt; return bool(seen[max(0, z - rr):z + rr + 1, max(0, x - rr):x + rr + 1].any())
MM = json.load(open(T + 'R2_meta.json'))
G = lambda x, z: (int(round((x + 12) * 1.5)), int(round((z + 16) * 1.5)))
spots = {'광장': (186, 264), '5': G(206, 160), '6': G(262, 96), '7': G(176, 54), '8': G(72, 52),
         '숲의 군주 입구': None, '수호자 입구': None}
rep = {k: near_ok(v) for k, v in spots.items() if v}
for k, (ax, az) in MM['arenas'].items():
    rr = MM['arena_r'][k]
    ring_pts = [(int(ax + np.cos(a) * (rr + 4)), int(az + np.sin(a) * (rr + 4))) for a in np.linspace(0, 6.28, 72)]
    rep[k + ' 입구'] = any(0 <= x < W and 0 <= z < D and seen[z, x] for x, z in ring_pts)
rep['북쪽 출구'] = bool(seen[0].any())
print('도달:', rep)
door_ok = {r['name']: near_ok(tuple(r['door']), 2) for r in LAY}
print('문 앞 도달', sum(door_ok.values()), '/', len(door_ok), [k for k, v in door_ok.items() if not v])
from scipy.ndimage import distance_transform_edt as _edt
dA2 = _edt(~(WALK | wm2))
print('방벽 밖 새는 칸', int((seen & (dA2 > 12)).sum()))
wid = [i for i, st in enumerate(Pn) if st.split('[')[0] == 'minecraft:water']
w_ = np.isin(V2, wid); air = V2 == AIR
leak = np.zeros_like(w_)
for ax_, sh in ((1, 1), (1, -1), (2, 1), (2, -1)): leak |= w_ & np.roll(air, sh, ax_)
leak[:, :, [0, -1]] = False; leak[:, [0, -1], :] = False
print('물 옆이 빈 칸', int(leak.sum()), [(int(x), int(y), int(z)) for y, z, x in np.argwhere(leak)[:8]])
on_road = sum(1 for (x, y, z), st in VEG.items() if 0 <= x < W and 0 <= z < D and MAT[z, x] in ('path', 'plaza') and y <= Hf[z, x] + 2 and 'leaves' not in st and 'mushroom_block' not in st)
print('길 위 식생(머리 높이 아래)', on_road)
fl_ = floating(V2, AIR, BAR, BPOS, Pn); print('공중에 뜬 건물 조각', len(fl_), fl_[:12])
fv_ = floating(V2, AIR, BAR, VPOS, Pn, fix_small=60); print('공중에 뜬 버섯·잎 조각 (60칸 넘는 것)', len(fv_), fv_[:6])
json.dump({'reach': rep, 'doors': f"{sum(door_ok.values())}/{len(door_ok)}"}, open(VD + 'r2_review.json', 'w'), ensure_ascii=False)

def save(path, x0, z0, x1, z1):
    sub = V2[:, z0:z1, x0:x1]; Hh, L_, Wd = sub.shape
    v = sub.ravel().astype(np.int64)
    b0 = np.where(v < 128, v, (v & 127) | 128).astype(np.uint8); b1 = (v >> 7).astype(np.uint8)
    keep = np.stack([np.ones_like(v, bool), v >= 128], 1).ravel()
    data = np.stack([b0, b1], 1).ravel()[keep].tobytes()
    root = {'Version': (3, 2), 'DataVersion': (3, 3465), 'Width': (2, Wd), 'Height': (2, Hh), 'Length': (2, L_),
            'PaletteMax': (3, len(Pn)), 'Palette': (10, {n: (3, i) for i, n in enumerate(Pn)}),
            'BlockData': (7, data), 'Offset': (11, [0, 0, 0]),
            'Metadata': (10, {'WEOffsetX': (3, OX + x0), 'WEOffsetY': (3, OY), 'WEOffsetZ': (3, OZ + z0)})}
    b = bytearray(); _tag(b, 10, 'Schematic', root)
    open(path, 'wb').write(gzip.compress(bytes(b))); return os.path.getsize(path)
if '--save' in sys.argv:
    import shutil
    od = VD + 'final2'; shutil.rmtree(od, ignore_errors=True); os.makedirs(od)
    xs = [0, W // 3, 2 * W // 3, W]; zs = [0, D // 2, D]; i = 0
    for zi_ in range(2):
        for xi_ in range(3):
            i += 1; print(f'region2_{i}', save(f'{od}/region2_{i}.schem', xs[xi_], zs[zi_], xs[xi_ + 1], zs[zi_ + 1]))
np.save(VD + 'V_r2.npy', V2); json.dump(Pn, open(VD + 'P_r2.json', 'w'))
