"""배치 v3: 문은 가까운 큰길/광장을 봄, 큰길에서 3~6칸 띄움(건물마다 다르게), 건물끼리 3칸 이상, 길에 옆구리 안 붙음"""
import sys, json, math, random, numpy as np
sys.path.insert(0, '../terrain')
from scipy.ndimage import distance_transform_edt, binary_dilation
from abld import *
from abld2 import *
from schem import _rotf
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
H = np.load(T + 'H2.npy'); walk = np.load(T + 'walk2.npy'); MAT = np.load(T + 'MAT2.npy', allow_pickle=True)
D, W = H.shape
MAT = MAT.copy(); MAT[np.isin(MAT, ['farm', 'farm2'])] = 'grass'
road = np.isin(MAT, ['path']); plaza = MAT == 'plaza'
target_all = road | plaza
bad = np.isin(MAT, ['water', 'sand', 'stone', 'andesite']) | ~walk
STEPS = {'S': 0, 'W': 1, 'N': 2, 'E': 3}; DV = {'S': (0, 1), 'N': (0, -1), 'E': (1, 0), 'W': (-1, 0)}

def shapes(fn):
    s = fn(); out = {}
    for f, n in STEPS.items():
        pts = {}
        for (x, y, z), st in s.b.items():
            for _ in range(n): x, z = -z, x
            pts[(x, y, z)] = _rotf(st, n)
        mx = min(p[0] for p in pts); mz = min(p[2] for p in pts)
        Mx = max(p[0] for p in pts); Mz = max(p[2] for p in pts)
        w, l = Mx - mx + 1, Mz - mz + 1
        body = np.zeros((l, w), bool)
        for (x, y, z), st in pts.items():
            if y >= 1 and st != 'minecraft:air': body[z - mz, x - mx] = True
        dl = [(x - mx, z - mz) for (x, y, z), st in pts.items() if '_door[' in st and 'half=lower' in st and y == 1]
        fx, fz = DV[f]
        if dl: dx, dz = max(dl, key=lambda p: p[0] * fx + p[1] * fz)
        else: dx, dz = (w // 2 if f in 'SN' else (w - 1 if f == 'E' else 0)), (l // 2 if f in 'EW' else (l - 1 if f == 'S' else 0))
        # 문 앞: 몸체 밖으로 나갈 때까지
        k = 1
        while 0 <= dz + fz * k < l and 0 <= dx + fx * k < w and body[dz + fz * k, dx + fx * k]: k += 1
        out[f] = dict(w=w, l=l, body=body, door=(dx + fx * k, dz + fz * k))
    return out

dist_t, idx_t = distance_transform_edt(~target_all, return_indices=True)
dist_p, idx_p = distance_transform_edt(~plaza, return_indices=True)
rng = random.Random(7)

def place_one(name, sh, at, kind, occ, setback, faces='SNEW', R=60, force=None):
    best = None
    ax, az = at
    for f in faces:
        S_ = sh[f]; w, l = S_['w'], S_['l']; ddx, ddz = S_['door']; fx, fz = DV[f]
        for cz in range(az - R, az + R + 1):
            for cx in range(ax - R, ax + R + 1):
                x0, z0 = cx - w // 2, cz - l // 2
                if x0 < 2 or z0 < 2 or x0 + w >= W - 2 or z0 + l >= D - 2: continue
                if force is None:
                    sl = (slice(z0 - 1, z0 + l + 1), slice(x0 - 1, x0 + w + 1))
                    if bad[sl].any() or occ[sl].any() or target_all[sl].any(): continue
                    hh = H[z0:z0 + l, x0:x0 + w]
                    if hh.max() - hh.min() > 1: continue
                px, pz = x0 + ddx, z0 + ddz
                if not (0 <= px < W and 0 <= pz < D): continue
                if kind == 'plaza':
                    d = dist_p[pz, px]; tz, tx = idx_p[:, pz, px]
                else:
                    d = dist_t[pz, px]; tz, tx = idx_t[:, pz, px]
                vx, vz = tx - px, tz - pz; n = math.hypot(vx, vz) or 1
                ang = math.degrees(math.acos(max(-1, min(1, (vx * fx + vz * fz) / n)))) if d > 0.5 else 0
                if ang > 50: continue
                score = abs(d - setback) * 4 + ang * 0.08 + math.hypot(cx - ax, cz - az) * 0.12
                if best is None or score < best[0]: best = (score, f, x0, z0, d, ang)
    return best

PLAN = [  # (함수, 이름, 원하는 자리, 종류, 띄우기, 방향 제한)
    (fountain, '광장 분수 + 게시판', (130, 206), 'force', 0, 'S'),
    (harold, '촌장 해럴드의 집', (130, 176), 'plaza', 6, 'SNEW'),
    (general_store, '잡화점', (160, 200), 'plaza', 5, 'SNEW'),
    (gear_shop, '장비 상점', (158, 178), 'plaza', 6, 'SNEW'),
    (stalls, '시장 노점', (110, 210), 'plaza', 3, 'SNEW'),
    (inn, '여관', (150, 262), 'road', 4, 'SNEW', 40),
    (well, '우물', (112, 226), 'road', 3, 'SNEW'),
    (house1, '주민 집 1', (92, 206), 'road', 5, 'SNEW'),
    (house2, '주민 집 2', (96, 180), 'road', 6, 'SNEW', 30),
    (mage, '마법사 탑 (세라)', (68, 236), 'road', 5, 'SNEW'),
    (fishing_hut, '낚시 오두막 (모리)', (60, 252), 'force', 0, 'S'),
    (thief, '도적 은신처 (카이)', (178, 308), 'road', 7, 'SNEW', 22),
    (smithy, '대장간', (112, 296), 'plaza', 4, 'SNEW'),
    (jeweler, '세공소', (152, 272), 'road', 4, 'SNEW'),
    (alchemy, '연금술 공방', (160, 306), 'road', 5, 'SNEW'),
    (herb_shop, '약초 가게 (엘린)', (96, 282), 'road', 4, 'SNEW'),
    (house3, '주민 집 3', (172, 296), 'road', 5, 'SNEW'),
        (warrior, '전사 수련장 (바르크)', (270, 272), 'road', 10, 'SNEW', 20),
    (archer, '궁수 수련장', (420, 262), 'road', 5, 'SNEW'),
    (farmhouse, '브람 농가', (262, 210), 'road', 5, 'SNEW'),
    (barn, '헛간', (292, 226), 'road', 5, 'SNEW'),
    (granary, '곡물 창고', (250, 238), 'road', 4, 'SNEW'),
    (windmill, '풍차', (400, 208), 'force', 0, 'S'),
    (guard_post, '경비 초소 (루크)', (322, 152), 'road', 3, 'SNEW'),
    (hunter_hut, '사냥꾼 오두막 (로완)', (214, 100), 'road', 5, 'SNEW'),
]
occ = np.zeros((D, W), bool); res = []
for fn, name, at, kind, setback, faces, *rr in PLAN:
    sh = shapes(fn)
    if kind == 'force':
        f = faces[0]; w, l = sh[f]['w'], sh[f]['l']; x0, z0 = at[0] - w // 2, at[1] - l // 2; b = (0, f, x0, z0, 0, 0)
    else:
        b = place_one(name, sh, at, kind, occ, setback, faces, R=rr[0] if rr else 60)
    if not b: print('자리 없음', name); continue
    _, f, x0, z0, d, ang = b; w, l = sh[f]['w'], sh[f]['l']
    occ[max(0, z0 - 2):z0 + l + 2, max(0, x0 - 2):x0 + w + 2] = True
    fl = int(np.median(H[z0:z0 + l, x0:x0 + w]))
    res.append(dict(name=name, x0=int(x0), z0=int(z0), w=int(w), l=int(l), face=f, kind='new', floor=fl))
    print(f'{name:14} 문{f} 큰길/광장까지 {d:4.1f}칸 각도 {ang:3.0f}°  ({x0},{z0})')
json.dump(res, open('layout1.json', 'w'), ensure_ascii=False, indent=1)
