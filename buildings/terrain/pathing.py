"""길 찾기: 실제 지형 위에서 공터 가운데·완만한 곳을 따라가는 길 (벽에 안 묻힘, 폭 일정)."""
import heapq, numpy as np
from scipy.ndimage import distance_transform_edt

def make_cost(H, walk, water, noise, water_cost=25.0, center=0):
    clear = distance_transform_edt(walk | water)          # 벽까지 거리 (칸)
    gx, gz = np.gradient(H.astype(float)); slope = np.sqrt(gx ** 2 + gz ** 2)
    cost = 1.0 + 3.0 * np.clip(slope, 0, 3) + 1.2 * noise   # 경사·약간의 노이즈 (자연스럽게 휨)
    cost += np.where(clear < 2, 60, np.where(clear < 4, 12, np.where(clear < 6, 3, 0)))   # 벽 가까이는 피함
    cost += center / (clear + 1)                            # 가운데 쪽으로 (벽 따라 붙지 않게)
    cost = np.where(water, water_cost, cost)               # 강은 다리 놓을 곳만 건넘
    cost = np.where(walk | water, cost, np.inf)
    return cost

def route(cost, a, b):
    """a, b = (x, z) 배열 칸. 8방향 다익스트라 (A*)."""
    D, W = cost.shape
    INF = float('inf'); dist = {a: 0.0}; prev = {}
    h = lambda p: np.hypot(p[0] - b[0], p[1] - b[1])
    pq = [(h(a), 0.0, a)]
    N8 = [(1, 0, 1), (-1, 0, 1), (0, 1, 1), (0, -1, 1), (1, 1, 1.414), (1, -1, 1.414), (-1, 1, 1.414), (-1, -1, 1.414)]
    while pq:
        f, g, p = heapq.heappop(pq)
        if p == b: break
        if g > dist.get(p, INF): continue
        x, z = p
        for dx, dz, m in N8:
            nx, nz = x + dx, z + dz
            if not (0 <= nx < W and 0 <= nz < D): continue
            c = cost[nz, nx]
            if c == INF: continue
            ng = g + c * m
            q = (nx, nz)
            if ng < dist.get(q, INF):
                dist[q] = ng; prev[q] = p; heapq.heappush(pq, (ng + h(q), ng, q))
    if b not in prev and a != b: return [a, b]
    out = [b]
    while out[-1] != a: out.append(prev[out[-1]])
    return out[::-1]

def smooth(pts, step=5, it=3):
    p = [np.array(pts[i], float) for i in range(0, len(pts), step)] + [np.array(pts[-1], float)]
    for _ in range(it):   # Chaikin: 모서리 깎아 둥글게
        q = [p[0]]
        for a, b in zip(p, p[1:]): q += [0.75 * a + 0.25 * b, 0.25 * a + 0.75 * b]
        q.append(p[-1]); p = q
    return p

def stamp(mask_shape, poly, radius):
    D, W = mask_shape
    out = np.zeros(mask_shape, bool)
    r = int(np.ceil(radius)) + 1
    yy, xx = np.mgrid[-r:r + 1, -r:r + 1]; disk = xx ** 2 + yy ** 2 <= radius ** 2
    for a, b in zip(poly, poly[1:]):
        n = int(max(1, np.hypot(*(b - a)) * 2))
        for t in np.linspace(0, 1, n + 1):
            x, z = (a + (b - a) * t).round().astype(int)
            x0, x1, z0, z1 = max(0, x - r), min(W, x + r + 1), max(0, z - r), min(D, z + r + 1)
            out[z0:z1, x0:x1] |= disk[z0 - (z - r):z1 - (z - r), x0 - (x - r):x1 - (x - r)]
    return out
