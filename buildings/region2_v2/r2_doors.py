"""2지역: 건물 문 → 가까운 길 오솔길, 발자국 저장. 결과: R2_MATv.npy, r2_door_polys.json, r2_foot.npy"""
import sys, json, heapq, numpy as np
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
sys.path.insert(0, T)
import pathing
from scipy.ndimage import gaussian_filter, binary_dilation
from r2_place import shapes, PLAN, H, walk, DV
MAT = np.load(T + 'R2_MATs.npy', allow_pickle=True).copy()
D, W = H.shape
L = json.load(open('r2_layout.json')); fns = {fn.__name__: fn for fn, *_ in PLAN}
foot = np.zeros((D, W), bool); doors = []
for r in L:
    sh = shapes(fns[r['fn']])[r['face']]
    zz, xx = np.nonzero(sh['body']); foot[r['z0'] + zz, r['x0'] + xx] = True
    doors.append((r['name'], tuple(r['door']), DV[r['face']]))
block = binary_dilation(foot, iterations=1)
water = np.isin(MAT, ['water', 'bog'])
noise = gaussian_filter(np.random.default_rng(9).random((D, W)), 4); noise = (noise - noise.min()) / np.ptp(noise)
cost = pathing.make_cost(H, walk, water, noise, water_cost=np.inf, center=6)
cost = np.where(block, np.inf, cost)
road = np.isin(MAT, ['path', 'plaza'])
cost = np.where(road, 0.6, cost)
def to_road(a):
    INF = float('inf'); dist = {a: 0.0}; prev = {}; pq = [(0.0, a)]; p = a
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
    out = [p]
    while out[-1] != a: out.append(prev[out[-1]])
    return out[::-1]
polys = []
for name, (x, z), (fx, fz) in doors:
    lead = []
    for k in range(14):
        if 0 <= x < W and 0 <= z < D and np.isfinite(cost[z, x]): break
        lead.append((x, z)); x += fx; z += fz
    if not np.isfinite(cost[z, x]): print('막힘', name); continue
    if road[z, x]: print(name, '문 앞이 바로 길'); continue
    cells = lead + to_road((x, z))
    if len(cells) < 3: continue
    poly = pathing.smooth(cells, step=3, it=2)
    polys.append([list(map(float, p)) for p in poly])
    m = pathing.stamp((D, W), poly, 1.1) & ~foot & ~water
    MAT[m] = 'path'
    print(name, len(cells), '칸')
np.save(T + 'R2_MATv.npy', MAT); json.dump(polys, open(T + 'r2_door_polys.json', 'w')); np.save(T + 'r2_foot.npy', foot)
