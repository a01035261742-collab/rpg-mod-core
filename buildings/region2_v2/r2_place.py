"""2지역 배치 (1번 맵 배치 v3 규칙 그대로): 문은 가까운 큰길/광장을 봄, 큰길에서 띄움, 건물끼리 띄움, 길이 몸통 안 지나감.
사냥터 구조물은 구역 안 목표 자리 근처 (땅 높이차 2까지 허용, 바닥 맞춤). 결과: r2_layout.json"""
import sys, json, math, numpy as np
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
sys.path.insert(0, T)
from scipy.ndimage import distance_transform_edt
from mbld import *
from vbld2 import *
from schem import _rotf
H = np.load(T + 'R2_H.npy'); walk = np.load(T + 'R2_walk.npy'); MAT = np.load(T + 'R2_MATs.npy', allow_pickle=True)
D, W = H.shape
road = MAT == 'path'; plaza = MAT == 'plaza'; target_all = road | plaza
bad = np.isin(MAT, ['water', 'bog', 'sand', 'gravel', 'stone', 'andesite', 'tuff', 'deepslate', 'ring']) | ~walk
d_bad = distance_transform_edt(~bad)
STEPS = {'S': 0, 'W': 1, 'N': 2, 'E': 3}; DV = {'S': (0, 1), 'N': (0, -1), 'E': (1, 0), 'W': (-1, 0)}

def shapes(fn):
    s = fn(); out = {}
    for f, n in STEPS.items():
        pts = {}
        for (x, y, z), st in s.b.items():
            for _ in range(n): x, z = -z, x
            pts[(x, y, z)] = _rotf(st, n)
        mx = min(p[0] for p in pts); mz = min(p[2] for p in pts); Mx = max(p[0] for p in pts); Mz = max(p[2] for p in pts)
        w, l = Mx - mx + 1, Mz - mz + 1
        body = np.zeros((l, w), bool); foot = np.zeros((l, w), bool)
        for (x, y, z), st in pts.items():
            if st != 'minecraft:air':
                foot[z - mz, x - mx] = True
                if y >= 1: body[z - mz, x - mx] = True
        dl = [(x - mx, z - mz) for (x, y, z), st in pts.items() if '_door[' in st and 'half=lower' in st and y == 1]
        fx, fz = DV[f]
        if dl: dx, dz = max(dl, key=lambda p: p[0] * fx + p[1] * fz)
        else: dx, dz = (w // 2 if f in 'SN' else (w - 1 if f == 'E' else 0)), (l // 2 if f in 'EW' else (l - 1 if f == 'S' else 0))
        k = 1
        while 0 <= dz + fz * k < l and 0 <= dx + fx * k < w and foot[dz + fz * k, dx + fx * k]: k += 1
        out[f] = dict(w=w, l=l, body=body, foot=foot, door=(dx + fx * k, dz + fz * k))
    return out

dist_t, idx_t = distance_transform_edt(~target_all, return_indices=True)
dist_p, idx_p = distance_transform_edt(~plaza, return_indices=True)

def place_one(sh, at, kind, occ, setback, faces='SNEW', R=40, dh=1, gap=2):
    best = None; ax, az = at
    for f in faces:
        S_ = sh[f]; w, l = S_['w'], S_['l']; ddx, ddz = S_['door']; fx, fz = DV[f]; fp = S_['foot']
        for cz in range(az - R, az + R + 1, 1):
            for cx in range(ax - R, ax + R + 1, 1):
                x0, z0 = cx - w // 2, cz - l // 2
                if x0 < 3 or z0 < 3 or x0 + w >= W - 3 or z0 + l >= D - 3: continue
                sl = (slice(z0 - 1, z0 + l + 1), slice(x0 - 1, x0 + w + 1))
                # 발자국(실제 칸)만 검사: 둥근 건물 모서리 빈칸은 괜찮음
                zz, xx = np.nonzero(fp)
                if (d_bad[z0 + zz, x0 + xx] < 2).any(): continue
                if occ[sl].any() or target_all[z0 + zz, x0 + xx].any(): continue
                if (dist_t[z0 + zz, x0 + xx] < 1.5).any(): continue
                hh = H[z0 + zz, x0 + xx]
                if hh.max() - hh.min() > dh: continue
                px, pz = x0 + ddx, z0 + ddz
                if not (0 <= px < W and 0 <= pz < D) or bad[pz, px]: continue
                if kind == 'plaza': d = dist_p[pz, px]; tz, tx = idx_p[:, pz, px]
                else: d = dist_t[pz, px]; tz, tx = idx_t[:, pz, px]
                vx, vz = tx - px, tz - pz; n = math.hypot(vx, vz) or 1
                ang = math.degrees(math.acos(max(-1, min(1, (vx * fx + vz * fz) / n)))) if d > 0.5 else 0
                if ang > 50: continue
                score = abs(d - setback) * 4 + ang * 0.08 + math.hypot(cx - ax, cz - az) * 0.12
                if best is None or score < best[0]: best = (score, f, x0, z0, d, ang)
    return best

PLAN = [  # (함수, 이름, 원하는 자리, 종류, 띄우기, 방향, 찾는 반경, 높이차)
    (plaza3, '광장 시계탑 분수', (186, 252), 'force', 0, 'S'),
    (kael2, '경비대 초소 (카엘)', (182, 336), 'road', 4, 'SNEW', 16, 2),
    (lab2, '버섯 연구소 (모리스)', (205, 212), 'road', 4, 'SNEW', 16),
    (gear3, '장비 상점', (210, 240), 'road', 3, 'SNEW', 12),
    (general3, '잡화점', (160, 238), 'road', 3, 'SNEW', 12),
    (alch3, '연금술 공방 (네라)', (246, 230), 'road', 4, 'SNEW', 16),
    (smithy3, '대장간', (205, 288), 'road', 4, 'SNEW', 14),
    (inn3, '여관', (228, 270), 'road', 4, 'SNEW', 16),
    (jewel3, '세공소', (252, 268), 'road', 3, 'SNEW', 14),
    (h1, '주민 집 1', (145, 222), 'road', 4, 'SNEW', 16),
    (h2, '주민 집 2', (232, 210), 'road', 4, 'SNEW', 16),
    (well3, '우물', (165, 290), 'road', 3, 'SNEW', 14),
    (waystone2, '이동의 돌', (190, 232), 'road', 3, 'SNEW', 10),
    (stalls3, '노점', (200, 264), 'plaza', 3, 'SNEW', 12),
    (h4, '주민 집 4', (250, 288), 'road', 4, 'SNEW', 14),
    (h3, '주민 집 3 (마을 어귀)', (150, 335), 'road', 5, 'SNEW', 18, 2),
    (h5, '주민 집 5 (마을 어귀)', (200, 345), 'road', 5, 'SNEW', 18, 2),
    # 사냥터 퀘스트 지점
    (spore_camp, '버섯 연구 캠프', (300, 286), 'road', 6, 'SNEW', 22, 2),
    (spirit_altar, '정령의 제단', (372, 290), 'road', 14, 'SNEW', 22, 2),
    (soldier_camp, '병정 막사', (440, 140), 'road', 8, 'SNEW', 30, 2),
    (deep_post, '깊은 곳 전초기지', (345, 108), 'road', 5, 'SNEW', 24, 2),
    (witch_hut, '마녀의 은신처', (250, 78), 'road', 10, 'SNEW', 30, 2),
    (beast_nest, '짐승 둥지', (82, 90), 'road', 14, 'SNEW', 30, 2),
    (owen_hut, '숲지기 오두막 (오웬)', (185, 122), 'road', 5, 'SNEW', 22, 2),
]
if __name__ == '__main__':
    occ = np.zeros((D, W), bool); res = []
    for fn, name, at, kind, setback, faces, *rr in PLAN:
        sh = shapes(fn)
        if kind == 'force':
            f = faces[0]; w, l = sh[f]['w'], sh[f]['l']; b = (0, f, at[0] - w // 2, at[1] - l // 2 + 3, 0, 0)
        else:
            b = place_one(sh, at, kind, occ, setback, faces, R=rr[0] if rr else 30, dh=rr[1] if len(rr) > 1 else 1)
        if not b: print('자리 없음', name); continue
        _, f, x0, z0, d, ang = b; S_ = sh[f]; w, l = S_['w'], S_['l']
        zz, xx = np.nonzero(S_['foot'])
        for a, c in zip(zz, xx): occ[max(0, z0 + a - 2):z0 + a + 3, max(0, x0 + c - 2):x0 + c + 3] = True
        fl = int(np.round(np.median(H[z0 + zz, x0 + xx])))
        res.append(dict(name=name, fn=fn.__name__, x0=int(x0), z0=int(z0), w=int(w), l=int(l), face=f, floor=fl,
                        door=[int(x0 + S_['door'][0]), int(z0 + S_['door'][1])]))
        print(f'{name:12} 문{f} 길/광장까지 {d:4.1f}칸 각도 {ang:3.0f}°  ({x0},{z0}) 바닥 {fl}')
    json.dump(res, open('r2_layout.json', 'w'), ensure_ascii=False, indent=1)
