"""블록 다양화 (모양은 그대로): 점박이 없이 큰 덩어리·지층으로만 섞음."""
import numpy as np
from scipy.ndimage import gaussian_filter, distance_transform_edt

def rank01(a):
    return (np.argsort(np.argsort(a.ravel())) / a.size).reshape(a.shape)

def blob(shape, sigma, seed):
    return rank01(gaussian_filter(np.random.default_rng(seed).random(shape), sigma))

# 지층: 높이(y)마다 띠, 띠 경계는 완만하게 물결 / 띠 안에도 큰 덩어리 하나 정도
LIGHT = ['stone', 'andesite', 'stone', 'tuff', 'stone', 'andesite', 'cobblestone']
DARK = ['deepslate', 'tuff', 'deepslate', 'cobbled_deepslate', 'deepslate', 'smooth_basalt', 'tuff']

def strata(V, H, rockmask, I, pal, seed, band=3):
    D, W = H.shape; TOP = V.shape[0]
    warp = (blob((D, W), 10, seed) - 0.5) * 5
    alt = blob((D, W), 5, seed + 1)
    for y in range(TOP):
        m = rockmask & (H >= y)
        if not m.any(): continue
        k = np.floor((y + warp) / band).astype(int)
        idx = (k * 3 + (alt > 0.82)) % len(pal)
        for j, n in enumerate(pal):
            sel = m & (idx == j) & (V[y] != 0)
            V[y][sel] = I[n]

def rim(V, H, rockmask, groundmask, I, name):
    """바위 비탈과 맞닿은 땅 가장자리 한 줄 (풀 → 거친 흙 대신 '흙 드러난 턱')"""
    near = distance_transform_edt(~rockmask) <= 1.0
    sel = groundmask & near
    for z, x in zip(*np.nonzero(sel)): V[H[z, x], z, x] = I[name]
    return int(sel.sum())

def lakebed(V, H, watermask, I, names, seed):
    """물 바닥: 자갈·점토·모래 큰 덩어리"""
    b = blob(H.shape, 3, seed)
    cut = np.cumsum([p for _, p in names])
    for z, x in zip(*np.nonzero(watermask)):
        k = int(np.searchsorted(cut, b[z, x] * cut[-1])); V[H[z, x], z, x] = I[names[min(k, len(names) - 1)][0]]

def paint_paths(V, H, pm, I, pick, center):
    """길: 가운데 줄(center 마스크) / 양옆 줄 두 가지 (무작위 점 없음). pick(z, x) → (가운데, 양옆) 블록 이름"""
    for z, x in zip(*np.nonzero(pm)):
        c, e = pick(z, x)
        V[H[z, x], z, x] = I[c if center[z, x] else e]

def zone_dist(shape, centers):
    """여러 점까지 최소 거리 (칸)"""
    D, W = shape; Z, X = np.mgrid[0:D, 0:W]; d = np.full(shape, 1e9)
    for cx, cz in centers: d = np.minimum(d, np.hypot(X - cx, Z - cz))
    return d

def natural_paths(V, H, MAT, I, polys, pal, allowed, seed=51):
    """자연스러운 길: 폭이 길 따라 천천히 2~5칸으로 변함, 가장자리는 2~3칸짜리 덩어리로 들쭉날쭉,
    안쪽은 길 방향으로 길쭉한 바퀴자국 얼룩 (점 하나짜리 없음). pal(z, x) → (주재료, 보조, 드문) 블록 이름"""
    from scipy.spatial import cKDTree
    D, W = H.shape
    pts, S, T = [], [], []
    for poly in polys:
        P = np.array(poly, float); s0 = 0.0
        for a, b in zip(P, P[1:]):
            L = np.hypot(*(b - a)); n = max(1, int(L * 2))
            tdir = (b - a) / (L + 1e-9)
            for k in range(n):
                pts.append(a + (b - a) * k / n); S.append(s0 + L * k / n); T.append(tdir)
            s0 += L
    pts = np.array(pts); S = np.array(S); T = np.array(T)
    tree = cKDTree(pts)
    rng = np.random.default_rng(seed)
    w1 = rank01(gaussian_filter(rng.random(4096), 12))          # 폭 (길이 방향, 완만)
    rut = rank01(gaussian_filter(rng.random((4096, 16)), (5, 0.9)))                          # 길쭉한 얼룩 (길 방향으로 늘어남)
    clump = blob((D, W), 1.3, seed + 7)                                                       # 가장자리 덩어리
    Z, X = np.mgrid[0:D, 0:W]
    cand = np.stack([X.ravel(), Z.ravel()], 1).astype(float)
    dist, idx = tree.query(cand, distance_upper_bound=4.0)
    ok = np.isfinite(dist)
    dist = dist.reshape(D, W); idx = np.where(ok, idx, 0).reshape(D, W); ok = ok.reshape(D, W)
    s = S[idx]; tv = T[idx]; rel = np.stack([X - pts[idx][..., 0], Z - pts[idx][..., 1]], -1)
    t = rel[..., 0] * (-tv[..., 1]) + rel[..., 1] * tv[..., 0]                                  # 길 가로 위치 (부호 있음)
    hw = 1.0 + 1.9 * w1[(s * 0.7).astype(int) % 4096] ** 1.3                                   # 반폭 1.0~2.9 (폭 2~6칸)
    inside = ok & allowed & (dist < hw + (clump - 0.5) * 1.4)
    core = inside & (dist < hw - 0.6)
    r = rut[(s * 1.0).astype(int) % 4096, np.clip(((t + 4) * 2).astype(int), 0, 15)]
    for z, x in zip(*np.nonzero(inside)):
        a, b, c = pal(z, x)
        if core[z, x]: n = a if r[z, x] < 0.66 else b if r[z, x] < 0.9 else c
        else: n = b if clump[z, x] < 0.55 else a
        V[H[z, x], z, x] = I[n]
    # 원래 길 칸인데 이번에 안 칠한 곳 = 가장 가까운 땅
    pm = (MAT == 'path') & ~inside
    if pm.any():
        _, (iz, ix) = distance_transform_edt(MAT == 'path', return_indices=True)
        for z, x in zip(*np.nonzero(pm)): V[H[z, x], z, x] = V[H[iz[z, x], ix[z, x]], iz[z, x], ix[z, x]]
    return int(inside.sum())
