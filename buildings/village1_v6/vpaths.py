"""건물 문 → 가까운 길까지 오솔길 + 농장 밭 자리 잔디로. 결과: MAT2v.npy, polys2v.json, door_polys.json"""
import sys, json, heapq, numpy as np
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
sys.path.insert(0, T)
import pathing
from scipy.ndimage import gaussian_filter, binary_dilation
from layout import PLAN, res as LAY
from schem import _rotf
H = np.load(T + 'H2.npy'); walk = np.load(T + 'walk2.npy'); MAT = np.load(T + 'MAT2.npy', allow_pickle=True)
D, W = H.shape
MAT = MAT.copy()
MAT[np.isin(MAT, ['farm', 'farm2'])] = 'grass'          # 밭은 사용자가 직접
STEPS = {'S': 0, 'W': 1, 'N': 2, 'E': 3}; DIRV = {'S': (0, 1), 'N': (0, -1), 'E': (1, 0), 'W': (-1, 0)}
foot = np.zeros((D, W), bool); doors = []
for (fn, name, *_), r in zip(PLAN, LAY):
    s = fn(); n = STEPS[r['face']]; pts = {}
    for (x, y, z), st in s.b.items():
        for _ in range(n): x, z = -z, x
        pts[(x, y, z)] = _rotf(st, n)
    mx = min(p[0] for p in pts); mz = min(p[2] for p in pts)
    cells = {(r['x0'] + x - mx, r['z0'] + z - mz) for (x, y, z), st in pts.items() if st != 'minecraft:air' and y >= 1}
    for (X, Z) in cells:
        if 0 <= X < W and 0 <= Z < D: foot[Z, X] = True
    dl = [(x, z) for (x, y, z), st in pts.items() if '_door[' in st and 'half=lower' in st and y == 1]
    fx, fz = DIRV[r['face']]
    if dl:   # 정면 쪽 문 하나
        x, z = max(dl, key=lambda p: p[0] * fx + p[1] * fz)
        doors.append((name, (r['x0'] + x - mx + fx * 1, r['z0'] + z - mz + fz * 1), (fx, fz)))
    elif name.startswith(('시장', '우물')):
        cx, cz = r['x0'] + r['w'] // 2, r['z0'] + r['l'] // 2
        doors.append((name, (cx + fx * (r['w'] // 2 + 1), cz + fz * (r['l'] // 2 + 1)), (fx, fz)))
block = binary_dilation(foot, iterations=1)
water = MAT == 'water'
noise = gaussian_filter(np.random.default_rng(9).random((D, W)), 4); noise = (noise - noise.min()) / np.ptp(noise)
cost = pathing.make_cost(H, walk, water, noise, water_cost=np.inf, center=6)
cost = np.where(block, np.inf, cost)
road = np.isin(MAT, ['path', 'plaza'])
cost = np.where(road, 0.6, cost)
def to_road(a):
    INF = float('inf'); dist = {a: 0.0}; prev = {}; pq = [(0.0, a)]
    N8 = [(1, 0, 1), (-1, 0, 1), (0, 1, 1), (0, -1, 1), (1, 1, 1.414), (1, -1, 1.414), (-1, 1, 1.414), (-1, -1, 1.414)]
    while pq:
        g, p = heapq.heappop(pq)
        if g > dist.get(p, INF): continue
        x, z = p
        if road[z, x] and p != a: break
        for dx, dz, m in N8:
            nx, nz = x + dx, z + dz
            if not (0 <= nx < W and 0 <= nz < D): continue
            c = cost[nz, nx]
            if not np.isfinite(c): continue
            ng = g + c * m
            if ng < dist.get((nx, nz), INF): dist[(nx, nz)] = ng; prev[(nx, nz)] = p; heapq.heappush(pq, (ng, (nx, nz)))
    if p == a: return [a]
    out = [p]
    while out[-1] != a: out.append(prev[out[-1]])
    return out[::-1]
polys = []
for name, (x, z), (fx, fz) in doors:
    lead = []
    for k in range(14):    # 문 앞 마당·소품 칸은 곧장 앞으로 지나감
        if 0 <= x < W and 0 <= z < D and np.isfinite(cost[z, x]): break
        lead.append((x, z)); x += fx; z += fz
    if not (0 <= x < W and 0 <= z < D) or not np.isfinite(cost[z, x]): print('막힘', name); continue
    cells = lead + to_road((x, z))
    if len(cells) < 3: continue
    poly = pathing.smooth(cells, step=3, it=2)
    polys.append([list(map(float, p)) for p in poly])
    m = pathing.stamp((D, W), poly, 1.1) & ~foot & ~water
    m |= np.isin(np.arange(D)[:, None] * 0, [1])   # (자리 표시)
    MAT[m] = 'path'
    print(name, len(cells), '칸')
np.save(T + 'MAT2v.npy', MAT)
json.dump(json.load(open(T + 'polys2.json')), open(T + 'polys2v.json', 'w'))
json.dump(polys, open(T + 'door_polys.json', 'w'))
np.save(T + 'foot1.npy', foot)
