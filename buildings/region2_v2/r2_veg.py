"""2지역 식생 (입구에서 안쪽으로 갈수록 버섯):
숲길·숲의 군주 골짜기 = 1번 맵 같은 숲 / 마을 = 나무 듬성 + 작은 버섯 몇 개 / 5 = 나무 + 중간 버섯 / 6·7 = 큰 버섯 숲 / 8 = 어두운 숲 (짙은 참나무 + 갈색 버섯).
사냥터는 벽 쪽 빽빽, 가운데 듬성. 작은 버섯 풀은 균사체·포드졸 위에만 (밝은 곳에서도 안 떨어짐)."""
import math, random, json, numpy as np
from scipy.ndimage import distance_transform_edt, gaussian_filter
import veg
from veg import oak, birch, spruce, bush, blob, LV, LG
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'

def dark_oak(r):
    out = {}; h = r.randint(6, 9)
    for y in range(h):
        for (a, b) in ((0, 0), (1, 0), (0, 1), (1, 1)): out[(a, y, b)] = LG('dark_oak')
    for (dx, dz, ax) in ((-1, 0, 'x'), (2, 1, 'x'), (0, -1, 'z'), (1, 2, 'z')):
        if r.random() < 0.7: out[(dx, 0, dz)] = LG('dark_oak', ax)
    lv = [LV('dark_oak')] * 5 + [LV('oak')]
    blob(out, 0.5, h + 0.5, 0.5, 4.2, 2.2, 4.2, r, lv)
    for _ in range(3):
        a = r.random() * 6.28
        blob(out, 0.5 + math.cos(a) * 2.5, h - 1, 0.5 + math.sin(a) * 2.5, 2.4, 1.4, 2.4, r, lv)
    return out

def gmush(r, kind='red', h=8, R=5, glow=False):
    """큰 버섯: 줄기 (R≥5 면 2×2, 살짝 기울기) + 갓 (빨강 = 둥근 돔 + 흰 점 없음(바닐라처럼), 갈색 = 납작한 판)"""
    out = {}; big = R >= 5
    lx, lz = r.choice((-1, 0, 1)), r.choice((-1, 0, 1))
    for k in range(h):
        t = k / max(1, h - 1)
        xx, zz = int(round(lx * t * t)), int(round(lz * t * t))
        out[(xx, k, zz)] = 'minecraft:mushroom_stem'
        if big:
            for (a, b) in ((1, 0), (0, 1), (1, 1)): out[(xx + a, k, zz + b)] = 'minecraft:mushroom_stem'
    cx, cz = lx + (0.5 if big else 0), lz + (0.5 if big else 0)
    m = 'minecraft:red_mushroom_block' if kind == 'red' else 'minecraft:brown_mushroom_block'
    H = max(1, int(R * (0.6 if kind == 'red' else 0.25)))
    centers = []
    for x in range(int(cx - R - 1), int(cx + R + 2)):
        for z in range(int(cz - R - 1), int(cz + R + 2)):
            d = math.sqrt(((x - cx) / (R + 0.4)) ** 2 + ((z - cz) / (R + 0.4)) ** 2)
            if d > 1: continue
            t = h + int(round(H * max(0.0, 1 - d ** 2) ** 0.75))
            b0 = max(h, t - (1 if d > 0.7 else 1))
            if kind == 'red' and d > 0.88: b0 = h - 1
            for yy in range(b0, t + 1): out[(x, yy, z)] = m
            centers.append((x, z, b0))
            if glow and d < 0.75 and r.random() < 0.05 and (x, h - 1, z) not in out: out[(x, h - 1, z)] = 'minecraft:shroomlight'
    # 줄기를 갓 밑면까지 이어 붙임 (갓 가운데 밑이 비어 공중에 뜨지 않게)
    stem_cols = {(x, z) for (x, y, z), st in out.items() if st == 'minecraft:mushroom_stem'}
    top_stem = {}
    for (x, y, z), st in out.items():
        if st == 'minecraft:mushroom_stem': top_stem[(x, z)] = max(top_stem.get((x, z), -1), y)
    for (x, z, b0) in centers:
        if (x, z) in top_stem:
            for yy in range(top_stem[(x, z)] + 1, b0): out[(x, yy, z)] = 'minecraft:mushroom_stem'
    return out

def mini(r):
    """작은 버섯 덩어리 (블록으로 된 1~3칸 버섯, 마을 장식)"""
    out = {}; kind = r.choice(('red', 'brown', 'brown'))
    h = r.randint(1, 2); m = 'minecraft:red_mushroom_block' if kind == 'red' else 'minecraft:brown_mushroom_block'
    for k in range(h): out[(0, k, 0)] = 'minecraft:mushroom_stem'
    out[(0, h, 0)] = m
    if r.random() < 0.6:
        for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1)): out[(a, h, b)] = m
    if r.random() < 0.5:
        a, b = r.choice(((1, 1), (-1, 1), (1, -1), (-1, -1)))
        out[(a * 2, 0, b * 2)] = 'minecraft:mushroom_stem'; out[(a * 2, 1, b * 2)] = m
    return out

FLOWERS = veg.FLOWERS

def build(seed=5):
    r = random.Random(seed)
    H = np.load(T + 'R2_H.npy'); walk = np.load(T + 'R2_walk.npy'); MAT = np.load(T + 'R2_MATv.npy', allow_pickle=True)
    ZN = np.load(T + 'R2_zone.npy'); foot = np.load(T + 'r2_foot.npy'); WLV = np.load(T + 'R2_WLV.npy')
    M = json.load(open(T + 'R2_meta.json'))
    D, W = H.shape; Z, X = np.mgrid[0:D, 0:W]
    px, pz = 186, 252
    village = ((((X - px) / 82) ** 2 + ((Z - pz) / 52) ** 2) < 1) & (ZN == 'grass') & (Z < 300)
    zone = np.where(village, 'village', np.where(ZN == 'grass', 'forest', ZN)).astype(object)
    arena = np.zeros((D, W), bool)
    for k, (ax, az) in M['arenas'].items(): arena |= np.hypot(X - ax, Z - az) < M['arena_r'][k] + 3
    soil = np.isin(MAT, ['grass', 'mycelium', 'podzol', 'mud'])
    road = np.isin(MAT, ['path', 'plaza'])
    d_road = distance_transform_edt(~road); d_foot = distance_transform_edt(~foot)
    d_wall = distance_transform_edt(walk)
    water = np.isin(MAT, ['water', 'bog']); d_water = distance_transform_edt(~water)
    gx, gz = np.gradient(H.astype(float)); flat = np.hypot(gx, gz) < 0.8
    allowed = walk | water; dA = distance_transform_edt(~allowed)
    ring = (dA > 8) & (dA <= 12)
    plateau = ~walk & ~water
    out = {}; trees = []
    # 구역마다: 간격, 나무 확률(벽가/가운데), 종류 가중치
    RULE = {
        'forest': (7, 1.0, 0.75, [('oak', 4), ('oakbig', 2), ('birch', 3), ('spruce', 3), ('bush', 2)]),
        'village': (13, 0.6, 0.45, [('oakbig', 2), ('oak', 3), ('birch', 2), ('bush', 3), ('mini', 3)]),
        'arena1': (7, 1.0, 0.0, [('oak', 4), ('oakbig', 2), ('birch', 2), ('spruce', 3)]),
        'g5': (9, 1.0, 0.35, [('oak', 3), ('birch', 2), ('bush', 2), ('m_small', 4), ('m_mid', 2), ('mini', 2)]),
        'g6': (10, 1.0, 0.3, [('m_mid', 4), ('m_big', 3), ('m_small', 2), ('oak', 1), ('mini', 1)]),
        'g7': (11, 1.0, 0.3, [('m_big', 4), ('m_huge', 2), ('m_mid', 2), ('mini', 1)]),
        'arena2': (11, 1.0, 0.0, [('m_big', 3), ('m_huge', 2), ('m_mid', 2)]),
        'g8': (9, 1.0, 0.3, [('dark', 5), ('m_mid', 2), ('m_small', 2), ('spruce', 1)]),
    }
    cand = list(zip(*np.nonzero(soil & flat & ~ring & (d_road > 3) & (d_foot > 5) & ~water & (d_water > 1.5))))
    r.shuffle(cand)
    pts = []; grid = {}
    from scipy.spatial import cKDTree
    for (z, x) in cand:
        zn = zone[z, x]
        if zn not in RULE: continue
        sp, p_edge, p_mid, kinds = RULE[zn]
        if plateau[z, x]: p_ = 0.85 if zn != 'village' else 0.7
        elif arena[z, x]: p_ = 0.0
        else: p_ = p_edge if d_wall[z, x] < 8 else p_mid
        if zn == 'village' and not plateau[z, x] and d_foot[z, x] < 9: p_ *= 0.5
        if r.random() > p_: continue
        gx_, gz_ = x // 8, z // 8
        near = [p for i in (-2, -1, 0, 1, 2) for j in (-2, -1, 0, 1, 2) for p in grid.get((gx_ + i, gz_ + j), ())]
        if any((x - a) ** 2 + (z - b) ** 2 < sp * sp for a, b in near): continue
        kind = r.choices([k for k, _ in kinds], [w for _, w in kinds])[0]
        if zn == 'village' and kind == 'mini' and x < 200: kind = r.choice(('oak', 'bush', 'birch'))   # 마을 서쪽(입구 쪽)은 버섯 거의 없음
        glow = zn in ('g7', 'g8', 'arena2')
        red_p = {'g5': 0.5, 'g6': 0.45, 'g7': 0.3, 'arena2': 0.4, 'g8': 0.1}.get(zn, 0.4)
        km = 'red' if r.random() < red_p else 'brown'
        t = {'oak': lambda: oak(r), 'oakbig': lambda: oak(r, True), 'birch': lambda: birch(r), 'spruce': lambda: spruce(r), 'bush': lambda: bush(r),
             'dark': lambda: dark_oak(r), 'mini': lambda: mini(r),
             'm_small': lambda: gmush(r, km, r.randint(4, 6), r.choice((2, 3)), glow),
             'm_mid': lambda: gmush(r, km, r.randint(6, 9), r.choice((3, 4)), glow),
             'm_big': lambda: gmush(r, km, r.randint(9, 13), r.choice((5, 6)), glow),
             'm_huge': lambda: gmush(r, km, r.randint(13, 18), r.choice((6, 7, 8)), glow)}[kind]()
        y0 = int(H[z, x]) + 1
        for (dx, dy, dz), st in t.items():
            X_, Y_, Z_ = x + dx, y0 + dy, z + dz
            if not (0 <= X_ < W and 0 <= Z_ < D): continue
            if foot[Z_, X_] or ring[Z_, X_] or road[Z_, X_] and dy < 3: continue
            if Y_ <= H[Z_, X_]:
                if ('log' in st or 'stem' in st) and dy == 0: pass
                else: continue
            out[(X_, Y_, Z_)] = st
        pts.append((x, z)); grid.setdefault((x // 8, z // 8), []).append((x, z)); trees.append((int(x), int(z), kind, zn))
    # 풀·꽃·고사리·작은 버섯
    fz = np.random.default_rng(seed)
    nz = fz.random((D, W))
    patch = gaussian_filter(fz.random((D, W)), 4); patch = (patch - patch.min()) / np.ptp(patch)
    for z, x in zip(*np.nonzero(soil & ~ring & (d_road > 1.5) & (d_foot > 2) & ~water)):
        zn = zone[z, x]; m = MAT[z, x]
        dens = {'village': 0.10, 'forest': 0.28, 'arena1': 0.12, 'g5': 0.18, 'g6': 0.14, 'g7': 0.12, 'arena2': 0.05, 'g8': 0.12}.get(zn, 0.15)
        if nz[z, x] > dens * (0.4 + patch[z, x] * 1.2): continue
        y = int(H[z, x]) + 1
        if (x, y, z) in out or (x, y + 1, z) in out: continue
        v = fz.random()
        if m == 'grass':
            if zn in ('forest', 'arena1', 'g8'): st = 'minecraft:fern' if v < 0.35 else 'minecraft:grass' if v < 0.9 else 'minecraft:large_fern'
            elif zn == 'village': st = 'minecraft:grass' if v < 0.75 else 'minecraft:' + FLOWERS[int(patch[z, x] * 8.99)]
            else: st = 'minecraft:grass' if v < 0.8 else 'minecraft:fern'
        elif m in ('mycelium', 'podzol'):
            if zn in ('village', 'forest', 'arena1'): continue
            st = ('minecraft:brown_mushroom' if v < 0.6 else 'minecraft:red_mushroom') if v < 0.75 or m == 'mycelium' else 'minecraft:fern'
            if m == 'mycelium' and v >= 0.75: continue
        else: continue
        if st.endswith(('tall_grass', 'large_fern')):
            out[(x, y, z)] = st + '[half=lower]'; out[(x, y + 1, z)] = st + '[half=upper]'
        else: out[(x, y, z)] = st
    json.dump({'trees': trees}, open('r2_veg_trees.json', 'w'))
    return out

if __name__ == '__main__':
    from collections import Counter
    o = build(); t = json.load(open('r2_veg_trees.json'))['trees']
    print(len(o), Counter((k[3], k[2]) for k in t).most_common(40))
