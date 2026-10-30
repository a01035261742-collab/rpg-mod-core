"""2번 마을 골목길 (큰길에 더해): 광장 북쪽 골목 (서·동으로 갈라짐) · 연못 북쪽 골목 · 남동 골목. 결과: R2_MATs.npy, r2_streets.json"""
import sys, json, numpy as np
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
sys.path.insert(0, T)
import pathing
from scipy.ndimage import gaussian_filter
H = np.load(T + 'R2_H.npy'); walk = np.load(T + 'R2_walk.npy'); MAT = np.load(T + 'R2_MAT.npy', allow_pickle=True).copy()
D, W = H.shape
water = np.isin(MAT, ['water', 'bog'])
noise = gaussian_filter(np.random.default_rng(17).random((D, W)), 4); noise = (noise - noise.min()) / np.ptp(noise)
cost = pathing.make_cost(H, walk & ~np.isin(MAT, ['stone', 'sand', 'gravel']), water, noise, water_cost=np.inf, center=8)
cost = np.where(MAT == 'path', 0.7, cost)
ST = [[(186, 238), (186, 229), (160, 227), (136, 236)],     # 광장 북 → 서쪽 골목
      [(186, 229), (214, 224), (240, 221), (256, 240)],     # 북 → 동쪽 (큰길에 붙음)
      [(173, 250), (156, 244), (138, 247)],                 # 광장 서 → 연못 북쪽
      [(194, 262), (210, 282), (236, 286)]]                 # 광장 남동 → 대장간·초소 쪽
polys = []
for pts in ST:
    cells = []
    for a, b in zip(pts, pts[1:]):
        r = pathing.route(cost, a, b); cells += r if not cells else r[1:]
    poly = pathing.smooth(cells, step=4, it=3)
    polys.append([list(map(float, p)) for p in poly])
    m = pathing.stamp((D, W), poly, 1.25) & walk & ~water & (MAT != 'plaza')
    MAT[m] = 'path'
np.save(T + 'R2_MATs.npy', MAT); json.dump(polys, open(T + 'r2_streets.json', 'w'))
print('골목', len(polys), '줄 · 길 칸', int((MAT == 'path').sum()))
