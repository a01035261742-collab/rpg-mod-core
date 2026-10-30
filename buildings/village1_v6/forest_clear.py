"""풀숲: 사냥터 공터 3곳 (거의 평평, 나무 없음) + 큰길에서 공터로 가는 오솔길.
원본 H2/MAT2v 는 *_pre_forest.npy 로 한 번만 백업해 두고 항상 거기서 다시 계산 (여러 번 돌려도 같음)."""
import sys, os, json, heapq, shutil, numpy as np
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
sys.path.insert(0, T)
import pathing
from scipy.ndimage import gaussian_filter, distance_transform_edt, binary_dilation, binary_opening, label

for f in ('H2', 'MAT2v'):
    if not os.path.exists(T + f + '_pre_forest.npy'): shutil.copy(T + f + '.npy', T + f + '_pre_forest.npy')
H = np.load(T + 'H2_pre_forest.npy'); MAT = np.load(T + 'MAT2v_pre_forest.npy', allow_pickle=True).copy()
walk = np.load(T + 'walk2.npy'); foot = np.load(T + 'foot1.npy')
D, W = H.shape
Z, X = np.mgrid[0:D, 0:W]

# 공터: 이름, 가운데, 반지름 x·z, 기울기(라디안)
CL = [('A 서쪽 공터', (56, 93), (19, 15), 0.3),
      ('B 북쪽 공터', (158, 56), (22, 11), 0.0),
      ('C 남쪽 공터', (156, 121), (24, 17), -0.15)]

rng = np.random.default_rng(21)
nz = gaussian_filter(rng.random((D, W)), 5); nz = (nz - nz.min()) / np.ptp(nz)
road = np.isin(MAT, ['path', 'plaza']); water = MAT == 'water'
d_wall = distance_transform_edt(walk)
d_foot = distance_transform_edt(~foot)
Hn = H.astype(float).copy()
clear = np.zeros((D, W), bool); info = []
for name, (cx, cz), (rx, rz), a in CL:
    u, v = X - cx, Z - cz
    u, v = u * np.cos(a) + v * np.sin(a), -u * np.sin(a) + v * np.cos(a)
    d = np.sqrt((u / rx) ** 2 + (v / rz) ** 2) + (nz - 0.5) * 0.45       # 가장자리 들쭉날쭉
    m = (d < 1.0) & walk & (d_wall > 3) & (d_foot > 4) & ~water
    m = binary_opening(m, iterations=2)
    lab, n = label(m); m = lab == (np.bincount(lab.ravel())[1:].argmax() + 1)
    tgt = int(np.round(np.median(H[m])))
    # 공터 = 높이 하나, 바깥 6칸에 걸쳐 원래 높이로 부드럽게
    dd = distance_transform_edt(~m)
    t = np.clip(1 - dd / 6.0, 0, 1); t = t * t * (3 - 2 * t)
    t = np.where(m, 1.0, t) * walk
    Hn = Hn + (tgt - Hn) * t * (~road | m)
    clear |= m
    info.append((name, (cx, cz), tgt, int(m.sum())))
Hn = np.round(Hn).astype(int)
# 걸을 수 있는 곳 안에서 이웃 높이차 1 넘으면 깎음 (안전)
for _ in range(3):
    for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nb = np.roll(Hn, (dz, dx), (0, 1)); wnb = np.roll(walk, (dz, dx), (0, 1))
        bad = walk & wnb & (Hn - nb > 1) & ~road
        Hn = np.where(bad, nb + 1, Hn)

# 오솔길: 공터 가운데 → 가장 가까운 큰길 (공터 안쪽 절반은 잘라서 공터에 스며들게)
noise = gaussian_filter(np.random.default_rng(31).random((D, W)), 4); noise = (noise - noise.min()) / np.ptp(noise)
cost = pathing.make_cost(Hn, walk, water, noise, water_cost=np.inf, center=6)
cost = np.where(binary_dilation(foot, iterations=2), np.inf, cost)
cost = np.where(clear, 1.0, cost)

def to_road(a):
    INF = float('inf'); dist = {a: 0.0}; prev = {}; pq = [(0.0, a)]
    N8 = [(1, 0, 1), (-1, 0, 1), (0, 1, 1), (0, -1, 1), (1, 1, 1.414), (1, -1, 1.414), (-1, 1, 1.414), (-1, -1, 1.414)]
    while pq:
        g, p = heapq.heappop(pq)
        if g > dist.get(p, INF): continue
        x, z = p
        if road[z, x]: break
        for dx, dz, m_ in N8:
            nx, nz_ = x + dx, z + dz
            if not (0 <= nx < W and 0 <= nz_ < D): continue
            c = cost[nz_, nx]
            if not np.isfinite(c): continue
            ng = g + c * m_
            if ng < dist.get((nx, nz_), INF): dist[(nx, nz_)] = ng; prev[(nx, nz_)] = p; heapq.heappush(pq, (ng, (nx, nz_)))
    out = [p]
    while out[-1] != a: out.append(prev[out[-1]])
    return out[::-1]

polys = []; newpath = np.zeros((D, W), bool)
for (name, (cx, cz), (rx, rz), a), (_, _, tgt, n) in zip(CL, info):
    first = to_road((cx, cz)); ex, ez = first[-1]
    # 곧은 줄 말고 살짝 휘게: 가운데→큰길 사이 중간점을 옆으로 비켜서 거쳐 감
    vx, vz = ex - cx, ez - cz; L = np.hypot(vx, vz) + 1e-9
    side = 1 if (cx + cz) % 2 else -1
    mx, mz = int(round(cx + vx * 0.55 - vz / L * 5 * side)), int(round(cz + vz * 0.55 + vx / L * 5 * side))
    if walk[mz, mx] and np.isfinite(cost[mz, mx]) and L > 14:
        cells = pathing.route(cost, (cx, cz), (mx, mz))[:-1] + to_road((mx, mz))
    else: cells = first
    # 공터 안쪽(가운데에서 반지름 45% 안)은 길 없음 → 공터 들어서면 길이 흐려짐
    keep = [p for p in cells if np.hypot((p[0] - cx) / rx, (p[1] - cz) / rz) > 0.45]
    cells = keep
    poly = pathing.smooth(cells, step=3, it=2)
    polys.append([list(map(float, p)) for p in poly])
    m = pathing.stamp((D, W), poly, 1.3) & walk & ~foot & ~water
    newpath |= m
    print(f'{name}: 가운데 {cx},{cz} 높이 {tgt} 넓이 {n}칸  오솔길 {len(cells)}칸')
MAT[newpath] = 'path'
np.save(T + 'H2.npy', Hn); np.save(T + 'MAT2v.npy', MAT)
np.save(T + 'clear1.npy', clear)
json.dump(polys, open(T + 'forest_polys.json', 'w'))
json.dump([{'name': nm, 'center': c, 'y': t, 'cells': k} for nm, c, t, k in info], open(T + 'clearings.json', 'w'), ensure_ascii=False)
print('높이 바뀐 칸', int((Hn != H).sum()), '· 공터 칸', int(clear.sum()))
