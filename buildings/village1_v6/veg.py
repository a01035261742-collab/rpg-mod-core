"""1번 맵 식생: 직접 모양 잡은 나무(참나무·자작·가문비·덤불) + 풀·꽃·고사리 + 물가(사탕수수·수련잎)
참고: 잎은 가지 끝에 덩어리로, 줄기는 원목+가지(원목·울타리), 밑동에 뿌리, 나무마다 크기·모양 다르게, 잎은 몇 가지 초록 섞기"""
import math, random, json, numpy as np
from scipy.ndimage import distance_transform_edt
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'

def LV(kind): return f'minecraft:{kind}_leaves[distance=1,persistent=true]'
def LG(kind, ax='y'): return f'minecraft:{kind}_log[axis={ax}]'

def blob(out, cx, cy, cz, rx, ry, rz, r, leaves):
    for x in range(int(cx - rx - 1), int(cx + rx + 2)):
        for y in range(int(cy - ry - 1), int(cy + ry + 2)):
            for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                if d <= 1.0 - (r.random() * 0.35 if d > 0.6 else 0):
                    if (x, y, z) not in out: out[(x, y, z)] = r.choice(leaves)

def line(out, a, b, block_fn):
    n = int(max(abs(b[i] - a[i]) for i in range(3))) + 1
    for k in range(n + 1):
        t = k / n
        p = tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))
        d = [b[i] - a[i] for i in range(3)]
        ax = 'y' if abs(d[1]) >= max(abs(d[0]), abs(d[2])) else ('x' if abs(d[0]) >= abs(d[2]) else 'z')
        out[p] = block_fn(ax)

def oak(r, big=False):
    out = {}; h = r.randint(5, 7) + (2 if big else 0)
    leaves = [LV('oak')] * 6 + [LV('azalea')] + [LV('dark_oak')]
    for y in range(0, h): out[(0, y, 0)] = LG('oak')
    if big:
        for (dx, dz) in ((1, 0), (0, 1), (1, 1)):
            for y in range(0, h - 2): out[(dx, y, dz)] = LG('oak')
    for (dx, dz, ax) in ((1, 0, 'x'), (-1, 0, 'x'), (0, 1, 'z'), (0, -1, 'z')):   # 뿌리
        if r.random() < 0.7: out[(dx * (2 if big else 1), 0, dz * (2 if big else 1))] = LG('oak', ax)
    nb = r.randint(3, 5)
    a0 = r.random() * 6.28
    ends = []
    for i in range(nb):
        a = a0 + i * 6.28 / nb + r.uniform(-0.4, 0.4)
        L = r.uniform(3, 5.5) + (1.5 if big else 0)
        y0 = h - r.randint(1, 3)
        e = (math.cos(a) * L, y0 + r.uniform(1.5, 3.5), math.sin(a) * L)
        line(out, (0, y0, 0), e, lambda ax: LG('oak', ax))
        ends.append(e)
    for e in ends:
        rr = r.uniform(2.2, 3.2) + (0.6 if big else 0)
        blob(out, e[0], e[1] + 0.8, e[2], rr, rr * 0.7, rr, r, leaves)
    blob(out, 0, h + 1.5, 0, 3.0 + (1 if big else 0), 2.2, 3.0 + (1 if big else 0), r, leaves)
    return out

def birch(r):
    out = {}; h = r.randint(7, 10)
    for y in range(0, h): out[(0, y, 0)] = LG('birch')
    leaves = [LV('birch')] * 5 + [LV('oak')]
    for k in range(3):
        y = h - 1 - k * 2
        blob(out, r.uniform(-0.6, 0.6), y, r.uniform(-0.6, 0.6), 2.0 - k * 0.2 + 0.4, 1.4, 2.0 - k * 0.2 + 0.4, r, leaves)
    blob(out, 0, h + 0.5, 0, 1.4, 1.5, 1.4, r, leaves)
    return out

def spruce(r):
    out = {}; h = r.randint(9, 13)
    for y in range(0, h): out[(0, y, 0)] = LG('spruce')
    lv = LV('spruce')
    y = 3
    while y < h + 1:
        rad = max(0.8, (h + 1 - y) * 0.42)
        for x in range(-4, 5):
            for z in range(-4, 5):
                if math.hypot(x, z) <= rad + 0.3 * r.random() and (x, y, z) not in out: out[(x, y, z)] = lv
        if rad > 1.5:
            for x in range(-4, 5):
                for z in range(-4, 5):
                    if math.hypot(x, z) <= rad * 0.6 and (x, y + 1, z) not in out: out[(x, y + 1, z)] = lv
        y += 2
    out[(0, h, 0)] = lv; out[(0, h + 1, 0)] = lv
    return out

def bush(r):
    out = {}; leaves = [LV('oak'), LV('azalea'), LV('oak'), LV('flowering_azalea') if r.random() < 0.15 else LV('oak')]
    blob(out, 0, 0.6, 0, r.uniform(1.2, 2.2), r.uniform(0.9, 1.4), r.uniform(1.2, 2.2), r, leaves)
    out[(0, 0, 0)] = LG('oak')
    return out

FLOWERS = ['dandelion', 'poppy', 'cornflower', 'oxeye_daisy', 'azure_bluet', 'allium', 'red_tulip', 'white_tulip', 'pink_tulip']

def build(seed=3):
    r = random.Random(seed)
    H = np.load(T + 'H2.npy'); walk = np.load(T + 'walk2.npy'); MAT = np.load(T + 'MAT2v.npy', allow_pickle=True)
    foot = np.load(T + 'foot1.npy')
    WLV = np.load(T + 'WLV2.npy')
    D, W = H.shape
    Z, X = np.mgrid[0:D, 0:W]
    C = {'village': ((130.5, 205.5), (51, 40)), 'pond': ((58.5, 265.5), (22, 18)), 'village_s': ((136.5, 286.5), (51, 24)),
         'farm': ((278.7, 227), (62, 35)), 'meadow': ((333, 120), (47, 51)), 'farm_e': ((385.5, 238.5), (51, 39)), 'forest': ((118.8, 84.6), (86, 39))}
    best = np.full((D, W), 1e9); zone = np.full((D, W), 'plateau', dtype=object)
    for k, ((cx, cz), (rx, rz)) in C.items():
        d = np.sqrt(((X - cx) / rx) ** 2 + ((Z - cz) / rz) ** 2)
        u = (d < best) & walk; zone[u] = k; best = np.where(u, d, best)
    grass = MAT == 'grass'
    road = np.isin(MAT, ['path', 'plaza'])
    d_road = distance_transform_edt(~road); d_foot = distance_transform_edt(~foot)
    d_wall = distance_transform_edt(walk)       # 공터 안에서 벽까지
    water = MAT == 'water'; d_water = distance_transform_edt(~water)
    gx, gz = np.gradient(H.astype(float)); flat = np.hypot(gx, gz) < 0.8
    import os
    clear = np.load(T + 'clear1.npy') if os.path.exists(T + 'clear1.npy') else np.zeros((D, W), bool)
    d_clear = distance_transform_edt(~clear)    # 사냥터 공터까지 거리
    # 고원 위 방벽 줄은 피함
    allowed = walk | water; dA = distance_transform_edt(~allowed)
    ring = (dA > 8) & (dA <= 12)
    out = {}
    trees = []
    SP = {'village': 13, 'village_s': 13, 'pond': 9, 'farm': 14, 'farm_e': 16, 'meadow': 18, 'forest': 7, 'plateau': 9}
    cand = list(zip(*np.nonzero(grass & flat & ~ring & (d_road > 3) & (d_foot > 5) & (d_clear > 3))))
    r.shuffle(cand)
    taken = []
    from scipy.spatial import cKDTree
    pts = []
    for (z, x) in cand:
        zn = zone[z, x]
        sp = SP[zn]
        p_ = {'village': 0.5, 'village_s': 0.5, 'pond': 0.8, 'farm': 0.0, 'farm_e': 0.0, 'meadow': 0.25, 'forest': 1.0, 'plateau': 0.75}[zn]
        if zn in ('farm', 'farm_e'): p_ = 0.6 if d_wall[z, x] < 8 else 0.0      # 밭 자리는 비우고 가장자리만
        if zn == 'meadow': p_ = 0.6 if d_wall[z, x] < 10 else 0.05
        if zn == 'forest': p_ = 1.0 if d_wall[z, x] < 8 or d_clear[z, x] < 8 else 0.8   # 예전보다 살짝 적게 (공터 안은 0)
        if zn == 'forest' and d_road[z, x] < 6: p_ = 0.25
        if r.random() > p_: continue
        if any((x - a) ** 2 + (z - b) ** 2 < sp * sp for a, b in pts[-400:]) and len(pts) < 400: continue
        if pts and min((x - a) ** 2 + (z - b) ** 2 for a, b in pts) < sp * sp: continue
        pts.append((x, z))
        if zn in ('forest', 'plateau'): kind = r.choices(['oak', 'oakbig', 'birch', 'spruce', 'bush'], [4, 2, 3, 3, 2])[0]
        elif zn == 'pond': kind = r.choices(['oak', 'birch', 'bush'], [2, 3, 2])[0]
        elif zn in ('village', 'village_s'): kind = r.choices(['oakbig', 'oak', 'birch', 'bush'], [2, 3, 1, 3])[0]
        else: kind = r.choices(['oak', 'birch', 'bush'], [3, 2, 2])[0]
        t = {'oak': lambda: oak(r), 'oakbig': lambda: oak(r, True), 'birch': lambda: birch(r), 'spruce': lambda: spruce(r), 'bush': lambda: bush(r)}[kind]()
        y0 = int(H[z, x]) + 1
        for (dx, dy, dz), st in t.items():
            X_, Y_, Z_ = x + dx, y0 + dy, z + dz
            if not (0 <= X_ < W and 0 <= Z_ < D): continue
            if foot[Z_, X_] or ring[Z_, X_]: continue
            if Y_ <= H[Z_, X_]: 
                if 'log' in st and dy == 0: pass
                else: continue
            out[(X_, Y_, Z_)] = st
        trees.append((x, z, kind))
    # 풀·꽃·고사리
    fz = np.random.default_rng(seed)
    nz = fz.random((D, W))
    from scipy.ndimage import gaussian_filter
    patch = gaussian_filter(fz.random((D, W)), 4); patch = (patch - patch.min()) / np.ptp(patch)
    for z, x in zip(*np.nonzero(grass & ~ring & (d_road > 1.5) & (d_foot > 2))):
        zn = zone[z, x]
        dens = {'village': 0.10, 'village_s': 0.10, 'pond': 0.25, 'farm': 0.06, 'farm_e': 0.06, 'meadow': 0.35, 'forest': 0.25, 'plateau': 0.18}[zn]
        if clear[z, x]: dens = 0.05                     # 공터: 풀 조금
        if nz[z, x] > dens * (0.4 + patch[z, x] * 1.2): continue
        y = int(H[z, x]) + 1
        if (x, y, z) in out or (x, y + 1, z) in out: continue
        v = fz.random()
        if clear[z, x]:
            st = 'minecraft:grass' if v < 0.85 else 'minecraft:' + FLOWERS[int(patch[z, x] * 8.99)]
        elif zn == 'forest':
            st = 'minecraft:fern' if v < 0.35 else 'minecraft:grass' if v < 0.92 else 'minecraft:large_fern'
        elif zn == 'meadow':
            st = 'minecraft:tall_grass' if v < 0.35 else 'minecraft:grass' if v < 0.75 else 'minecraft:' + FLOWERS[int(patch[z, x] * 8.99)]
        else:
            st = 'minecraft:grass' if v < 0.7 else 'minecraft:tall_grass' if v < 0.8 else 'minecraft:' + FLOWERS[int(patch[z, x] * 8.99)]
        if st.endswith(('tall_grass', 'large_fern')):
            out[(x, y, z)] = st + '[half=lower]'; out[(x, y + 1, z)] = st + '[half=upper]'
        else: out[(x, y, z)] = st
    # 물가: 수련잎 · 사탕수수
    for z, x in zip(*np.nonzero(water)):
        if WLV[z, x] - H[z, x] >= 1 and zone[z, x] in ('pond',) or (fz.random() < 0.015 and WLV[z, x] - H[z, x] >= 2):
            if fz.random() < 0.06: out[(x, int(WLV[z, x]) + 1, z)] = 'minecraft:lily_pad'
    for z, x in zip(*np.nonzero(grass & (d_water <= 1.0) & (d_road > 1.5))):
        ok = any(0 <= z + dz < D and 0 <= x + dx < W and water[z + dz, x + dx] and WLV[z + dz, x + dx] >= H[z, x] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if ok and fz.random() < 0.12:
            y = int(H[z, x]) + 1; hh = fz.integers(1, 4)
            for k in range(hh): out[(x, y + k, z)] = 'minecraft:sugar_cane[age=0]'
    json.dump({'trees': [(int(a), int(b), c) for a, b, c in trees]}, open('veg_trees.json', 'w'))
    return out

if __name__ == '__main__':
    o = build(); print(len(o))
