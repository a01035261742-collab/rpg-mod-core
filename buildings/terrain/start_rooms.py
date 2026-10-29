"""시작 마을 지역 v2: 공터(구역) + 자연스러운 바위 언덕 벽 + 좁은 통로. 식물 없음."""
import numpy as np, sys
from scipy.ndimage import distance_transform_edt, gaussian_filter
from PIL import Image, ImageDraw, ImageFont

OFFX, OFFZ = 25, 45                   # 바깥 여백 (설계 단위)
OFF = OFFX
SC = 1.5                              # 가로 배율 (설계 1칸 = 실제 1.5칸)
HS = 0.7                              # 벽 높이 배율
W, D = int(325 * SC), int(235 * SC)
X0, Z0 = np.meshgrid(np.arange(W), np.arange(D))   # 배열 칸 (실제)
X, Z = X0 / SC - OFFX, Z0 / SC - OFFZ              # 설계 좌표
def G(x, z): return int(round((x + OFFX) * SC)), int(round((z + OFFZ) * SC))   # 설계 → 배열
VC0 = (62, 92)
K = 1.3
def T(p): return (VC0[0] + (p[0] - VC0[0]) * K, VC0[1] + (p[1] - VC0[1]) * K)   # 사냥터: 마을 기준 1.3배 바깥으로
def TB(b): a = T(b[:2]); c = T(b[2:]); return (a[0], a[1], c[0], c[1])

def vnoise(scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((D // scale + 3, W // scale + 3))
    fx, fz = (X + OFFX) / scale, (Z + OFFZ) / scale
    x0, z0 = fx.astype(int), fz.astype(int)
    tx, tz = fx - x0, fz - z0
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a, b, c, d = g[z0, x0], g[z0, x0 + 1], g[z0 + 1, x0], g[z0 + 1, x0 + 1]
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz
def fbm(seed): return vnoise(24, seed) * 0.55 + vnoise(12, seed + 1) * 0.3 + vnoise(6, seed + 2) * 0.15
def sstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def seg(px, pz, a, b):
    ax, az = a; bx, bz = b; vx, vz = bx - ax, bz - az; L = vx * vx + vz * vz
    t = np.clip(((px - ax) * vx + (pz - az) * vz) / L, 0, 1)
    return np.sqrt((px - ax - t * vx) ** 2 + (pz - az - t * vz) ** 2)

BASE = 10
# 구역: (이름, 중심, 반지름 x·z, 바닥 높이, 울퉁불퉁)
ROOMS = {
    'village': ((62, 92), (34, 27), BASE, 0),
    'pond':    ((14, 132), (15, 12), BASE - 1, 0),
    'village_s': ((66, 146), (34, 16), BASE, 0),   # 강 건너 아랫마을
    'farm':    (T((138, 103)), (32 * K, 18 * K), BASE, 0),
    'meadow':  (T((166, 48)), (24 * K, 26 * K), BASE + 1, 2.0),
    'farm_e':  ((232, 114), (34, 26), BASE + 1, 1.2),        # 풍차 언덕 밭
    'forest':  (T((56, 26)), (44 * K, 20 * K), BASE + 4, 1.5),
}
# 통로: 점들, 폭
PASS = [
    ([(84, 96), T((114, 100))], 7),               # 마을 → 농장
    ([T((148, 92)), T((154, 76)), T((158, 58))], 7),   # 농장 → 초원
    ([T((146, 40)), T((126, 36)), T((106, 34))], 7),   # 초원 → 풀숲 (오르막)
    ([(36, 108), (24, 122)], 5),                  # 마을 → 연못
    ([T((24, 10)), (T((22, 0))[0], -OFFZ)], 6),       # 풀숲 서쪽 → 북쪽 (다음 지역)
]
edge = fbm(5) - 0.5
# 도메인 왜곡: 둥근 모양을 비틀어서 들쭉날쭉하게
WX = X + (fbm(301) - 0.5) * 22 + (vnoise(5, 303) - 0.5) * 5
WZ = Z + (fbm(302) - 0.5) * 22 + (vnoise(5, 304) - 0.5) * 5
walk = np.zeros((D, W), bool)
# 구역마다 상자 여러 개를 겹쳐서 (모서리·튀어나온 곳·움푹 들어간 곳)
BOXES = {
    'village': [(32, 70, 90, 122), (46, 67, 78, 74), (24, 86, 40, 106), (80, 100, 90, 120)],
    'pond':    [(0, 122, 28, 146), (20, 116, 34, 128)],
    'village_s': [(36, 124, 98, 162), (28, 138, 44, 156), (50, 158, 84, 166)],
    'farm':    [(112, 88, 168, 120), (118, 82, 150, 92), (160, 98, 172, 114)],
    'meadow':  [(144, 26, 186, 66), (150, 18, 176, 28), (180, 40, 194, 58), (138, 50, 150, 64)],
    'forest':  [(50, 12, 102, 38), (54, 34, 100, 54), (64, 50, 94, 60), (60, 6, 86, 14), (94, 24, 108, 42), (34, 18, 54, 34), (8, 6, 40, 46), (16, 40, 34, 54)],
}
for name in ('farm', 'meadow', 'forest'): BOXES[name] = [TB(b) for b in BOXES[name]]
BOXES['farm_e'] = [(196, 92, 266, 140), (212, 82, 254, 96), (244, 126, 270, 148), (200, 132, 228, 146)]
MILL = (232, 110)   # 풍차 자리 (언덕 꼭대기)
for name, bs in BOXES.items():
    for (x0, z0, x1, z1) in bs:
        walk |= (WX >= x0) & (WX <= x1) & (WZ >= z0) & (WZ <= z1)
for pts, w in PASS:
    d = np.full((D, W), 999.0)
    for a, b in zip(pts, pts[1:]): d = np.minimum(d, seg(X, Z, a, b))
    walk |= d + edge * 3 < w / 2
# 칸막이 산줄기: 마을 ↔ 농장 (통로 아래부터 강까지)
DIV = [((98, 103), (101, 128))]
divd = np.full((D, W), 999.0)
for a, b in DIV: divd = np.minimum(divd, seg(X, Z, a, b) + edge * 2)
walk &= ~(divd < 5)
# 공터 안 바위 언덕 (튀어나온 곳)
ROCKS = [(34, 80, 4)] + [T(p[:2]) + (p[2],) for p in [(158, 34, 4), (180, 60, 3), (96, 22, 3), (166, 92, 2)]]
rock = np.zeros((D, W))
for (rx, rz, rr) in ROCKS:
    dd = np.sqrt((X - rx) ** 2 + (Z - rz) ** 2) + edge * 3
    rock = np.maximum(rock, sstep(rr + 1.5, 0, dd) * (rr * 1.5))
# 바닥 높이: 구역마다 → 부드럽게 섞음 (통로가 자연스러운 오르막)
wsum = np.zeros((D, W)); hsum = np.zeros((D, W))
for name, ((cx, cz), (rx, rz), h, rough) in ROOMS.items():
    dist = np.sqrt(((X - cx) / rx) ** 2 + ((Z - cz) / rz) ** 2)
    wt = 1 / (dist ** 4 + 0.05)
    hh = h + (fbm(hash(name) % 100) - 0.5) * 2 * rough
    wsum += wt; hsum += wt * hh
base = hsum / wsum
mill_d = np.sqrt((X - MILL[0]) ** 2 + (Z - MILL[1]) ** 2)
base = base + 6 * sstep(32, 5, mill_d + edge * 6)   # 풍차 언덕: 넓고 완만, 반올림하면 등고선 따라 1칸 계단
for name, ((cx, cz), (rx, rz), h, rough) in ROOMS.items():   # 마을·농장·연못은 완전 평평
    if rough == 0:
        inner = np.sqrt(((X - cx) / rx) ** 2 + ((Z - cz) / rz) ** 2) < 0.92
        base = np.where(inner, h, base)
# 벽: 바깥으로 갈수록 오름 (처음 몇 칸은 완만한 둔덕 → 바위 비탈 → 꼭대기 들쭉날쭉)
dout = distance_transform_edt(~walk) / SC
rise = (3 * sstep(0, 3, dout) + 25 * sstep(1.5, 10, dout) + (fbm(77) - 0.3) * 8 * sstep(6, 20, dout)) * HS
H = base + rise + rock
# 마을↔풀숲 사이 (예전 통로 자리): 높은 산등성이
H += 12 * HS * np.exp(-(((X - 70) / 24) ** 2 + ((Z - 60) / 6) ** 2)) * (~walk)
H += 14 * HS * sstep(6, 0, divd) * (~walk)   # 칸막이 산줄기 높이
# 마을 북서쪽 언덕 단 (+2, 큰 집 자리)
terr = (WX >= 34) & (WX <= 56) & (WZ >= 68) & (WZ <= 82) & walk
base = np.where(terr, base + 2, base)
H = np.where(terr, H + 2, H)
H = gaussian_filter(H, 0.8)
# 낮은 안장 없애기: 벽 안쪽 몇 칸만 지나면 최소 +13 (얇은 벽도 못 넘을 높이)
H = np.maximum(H, base + np.minimum(round(13 * HS), 2.2 * HS * dout + 1) * (dout >= 1))
H = np.where(walk, base + rock, H)
H = np.round(H).astype(int)

# 연못 + 폭포 (연못 남쪽 절벽)
pond_c = ROOMS['pond'][0]
pd = np.sqrt(((X - pond_c[0]) / 8) ** 2 + ((Z - pond_c[1] - 3) / 6) ** 2)
WATER = BASE - 2
# 강: 서쪽 절벽 폭포 → 연못 → 마을 남쪽 → 농장 남쪽 → 동쪽 협곡으로 빠짐
RIVER = [(pond_c[0], pond_c[1] + 3), (32, 128), (50, 120), (72, 121), (94, 124), (108, 134), (124, 146), (142, 150), (160, 146), (178, 152), (196, 162), (216, 164), (236, 158), (256, 162), (276, 170), (300, 168)]
sd = np.full((D, W), 999.0)
for a, b in zip(RIVER, RIVER[1:]): sd = np.minimum(sd, seg(X, Z, a, b))
rw = 2.6 + fbm(51) * 1.8                       # 강폭 반 (5~8칸)
river = sd < rw
water = (pd < 1) | river
# 강가: 물 양옆 5칸은 낮고 평평한 둑, 벽을 지나는 곳은 협곡 (양옆 바위 비탈)
bankw = sd - rw
wallz = gaussian_filter((dout >= 3).astype(float), 2) > 0.5     # 벽 지대: 강이 좁은 협곡으로만 지나감 (둑 길 없음)
bankn = bankw + (fbm(61) - 0.5) * 6                               # 둑 모양 들쭉날쭉
valley = np.where(wallz, sstep(6, 0.5, bankn), sstep(9, 2, bankn))   # 협곡도 계단처럼 완만하게 내려옴
H = np.where(H > BASE - 1, H * (1 - valley) + (BASE - 1) * valley, H).round().astype(int)
H = np.where(water, WATER - 1 - ((sd < rw * 0.5) | (pd < 0.6)), H)
walk = walk | ((bankw < 4) & ~water & ~wallz)
fall_x, fall_z = None, pond_c[1] + 3
_, fzg = G(0, fall_z)
fall_g = None
for xg in range(G(pond_c[0] - 6, 0)[0], 0, -1):          # 연못 서쪽으로 가다가 처음 높아지는 곳 = 폭포
    if H[fzg, xg] >= BASE + 6: fall_g = (xg, fzg); fall_x = xg / SC - OFF; break
BRIDGES = []

# 길
road = np.zeros((D, W), bool)
PX = X + (fbm(501) - 0.5) * 7 + (vnoise(4, 503) - 0.5) * 1.2   # 길 구불구불 (좌표 비틀기)
PZ = Z + (fbm(502) - 0.5) * 7 + (vnoise(4, 504) - 0.5) * 1.2
near_walk = distance_transform_edt(~walk) / SC < 2.5
def spline(pts, n=10):   # Catmull-Rom: 꺾인 곳 없이 부드럽게
    P = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = (np.array(P[j], float) for j in (i - 1, i, i + 1, i + 2))
        for t in np.linspace(0, 1, n, endpoint=False):
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    out.append(np.array(pts[-1], float))
    return [tuple(p) for p in out]
def path(pts, width):
    global road
    d = np.full((D, W), 999.0)
    sp = spline(pts)
    for a, b in zip(sp, sp[1:]):
        if a != b: d = np.minimum(d, seg(PX, PZ, a, b))
    wd = width / SC * 1.25 * (0.85 + 0.3 * fbm(505))   # 폭도 조금씩 달라짐
    hit = (d < width) & water
    if hit.any(): zz, xx = np.nonzero(hit); BRIDGES.append((xx.mean() / SC - OFFX, zz.mean() / SC - OFFZ))
    road |= (d < wd) & (walk | near_walk) & ~water
VC = ROOMS['village'][0]
path([VC, (84, 97), T((106, 101)), T((140, 104))], 1.8)
path([T((140, 104)), (196, 112), (212, 116), (224, 112), MILL], 1.3)   # 풍차 가는 길 (구불)
path([MILL, (238, 122), (246, 134), (258, 140)], 1.0)   # 언덕 내려가는 길 (남동)
path([MILL, (240, 100), (250, 92)], 1.0)                # (북동)
path([MILL, (226, 124), (216, 136)], 1.0)               # (남서)
path([T(p) for p in [(140, 104), (150, 88), (156, 72), (164, 50), (146, 40), (126, 36), (106, 34), (76, 26), (46, 26), (24, 14)]] + [(T((22, 0))[0], -OFFZ)], 1.4)
path([VC, (36, 108), (22, 124)], 1.1)
path([VC, (64, 112), (66, 132), (66, 146)], 1.6)          # 광장 → 다리 → 아랫마을
path([(40, 146), (66, 146), (92, 146)], 1.2)             # 아랫마을 큰길
plaza2 = np.sqrt((X - 66) ** 2 + (Z - 146) ** 2) < 6
plaza = np.sqrt((X - VC[0]) ** 2 + (Z - VC[1]) ** 2) < 9

gx, gz = np.gradient(H.astype(float))
slope = np.sqrt(gx ** 2 + gz ** 2)
MAT = np.full((D, W), 'grass', dtype=object)
rockn = fbm(91)
MAT[(slope >= 1.0) & (rockn > 0.5)] = 'andesite'
MAT[(slope >= 1.0) & (rockn <= 0.5)] = 'stone'
MAT[(slope >= 1.0) & (slope < 1.4) & (fbm(92) > 0.62)] = 'coarse'
MAT[(pd < 1.35) & ~water] = 'sand'
MAT[walk & (rock >= 1.5)] = np.where(fbm(93)[walk & (rock >= 1.5)] > 0.5, 'andesite', 'stone')   # 공터 안 바위
MAT[road] = 'path'
MAT[plaza | plaza2] = 'plaza'
fc = ROOMS['farm'][0]
# 밭: 풍차 언덕 비탈을 크게 덮는 밀밭 (길로만 나뉨, 가장자리 들쭉날쭉, 군데군데 풀밭)
dout2 = distance_transform_edt(walk) / SC
fieldR = 30 + (fbm(401) - 0.5) * 16
fieldmask = walk & ~road & (mill_d > 7) & (mill_d < fieldR) & (dout2 > 2.5)
# 밭뙈기마다 다르게: 큰 밭 · 작은 밭 · 풀밭 섬 · 두렁길 (보로노이로 나눈 뒤 일부는 풀로)
r_ = np.random.default_rng(21)
seeds = [(MILL[0] + r_.uniform(-34, 34), MILL[1] + r_.uniform(-28, 28)) for _ in range(16)]
dmin = np.full((D, W), 1e9); d2 = np.full((D, W), 1e9); idx = np.zeros((D, W), int)
for i, (sx, sz) in enumerate(seeds):
    dd = np.sqrt((X - sx) ** 2 + (Z - sz) ** 2) * (0.8 + 0.4 * r_.random()) + edge * 5
    upd = dd < dmin
    d2 = np.where(upd, dmin, np.minimum(d2, dd)); idx = np.where(upd, i, idx); dmin = np.where(upd, dd, dmin)
keep = r_.random(len(seeds)) < 0.75                 # 4개 중 1개 꼴로 풀밭
border = (d2 - dmin) < 1.2                          # 두렁 (풀)
farm = fieldmask & keep[idx] & ~border & (fbm(402) > 0.22)
# 농장(사냥터 1) 쪽: 작은 밭 두어 개 (자연스러운 모양)
for (cx, cz, rx, rz) in [(T((128, 100))[0], T((128, 100))[1], 14, 8), (T((150, 112))[0], T((150, 112))[1], 10, 7)]:
    farm |= walk & ~road & (np.sqrt(((X - cx) / rx) ** 2 + ((Z - cz) / rz) ** 2) + edge * 0.6 < 1)
farm2 = np.zeros((D, W), bool)
MAT[farm] = 'farm'
MAT[farm2] = 'farm2'
MAT[water] = 'water'
np.save('H2.npy', H); np.save('MAT2.npy', MAT); np.save('walk2.npy', walk)
import json
json.dump({'SC': SC, 'OFFX': OFFX, 'OFFZ': OFFZ, 'plaza': G(*ROOMS['village'][0]), 'fall': fall_g and [int(v) for v in fall_g],
           'bridge': [G(64, 112), G(66, 132)], 'pond_x': G(pond_c[0], 0)[0], 'K': K}, open('meta.json', 'w'))
print('H', H.min(), H.max(), 'fall', fall_x, fall_z)
# 공터 사이 벽 가장 낮은 곳 (마을↔풀숲, 마을↔초원, 농장↔강 등): 경계 위 최소 높이
from scipy.ndimage import label
lab_, n_ = label(walk)
print('구역 수', n_)
for x0, z0, x1, z1, nm in [(60, 44, 100, 66, '마을-풀숲'), (100, 60, 130, 92, '마을-초원')]:
    (a0, b0), (a1, b1) = G(x0, z0), G(x1, z1)
    sub = H[b0:b1, a0:a1]
    cols = [sub[:, i].max() for i in range(sub.shape[1])]
    print(nm, '벽 최저', min(cols) - BASE)

# ───────── 미리보기 ─────────
F = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
fb = ImageFont.truetype(F, 22, index=1); fs = ImageFont.truetype(F, 14, index=1); fx = ImageFont.truetype(F, 12, index=1)
S = 3
img = Image.new('RGB', (W * S + 280, D * S + 70), (20, 22, 28))
COL = {'grass': (98, 156, 64), 'stone': (122, 122, 126), 'andesite': (146, 146, 142), 'coarse': (120, 92, 66), 'sand': (214, 200, 150),
       'path': (178, 148, 98), 'plaza': (172, 172, 178), 'farm': (110, 76, 50), 'farm2': (88, 62, 42), 'water': (60, 110, 205)}
arr = np.zeros((D, W, 3))
for k, c in COL.items(): arr[MAT == k] = c
lum = 0.82 + 0.018 * (H - BASE)
shade = 1 + 0.30 * (-gx - 1.2 * gz) / 1.56
arr = arr * np.where(MAT == 'water', 1, lum * shade)[..., None]
arr = np.clip(arr, 0, 255).astype(np.uint8)
big = Image.fromarray(arr).resize((W * S, D * S), Image.NEAREST)
img.paste(big, (0, 60))
d = ImageDraw.Draw(img)
for z in range(D - 1):          # 등고선 3칸마다 (벽 쪽)
    for x in range(W - 1):
        if H[z, x] <= BASE + 4 and H[z, x + 1] <= BASE + 4 and H[z + 1, x] <= BASE + 4: continue
        for lv in range(BASE + 3, 60, 3):
            if (H[z, x] < lv) != (H[z, x + 1] < lv) or (H[z, x] < lv) != (H[z + 1, x] < lv):
                d.point((x * S + 2, 60 + z * S + 2), fill=(45, 45, 45))
if fall_g: d.rectangle((fall_g[0] * S, 60 + fall_g[1] * S - 4, G(pond_c[0] - 6, 0)[0] * S, 60 + fall_g[1] * S + 4), fill=(110, 160, 235))

d.text((12, 10), f'시작 마을 지역 v19 — 길 구불구불 · 끊김 없이 · {W} × {D}칸', font=fb, fill=(240, 230, 200))
d.text((12, 38), f'공터 높이 {BASE} 기준 · 벽은 바깥으로 갈수록 완만하게 올라감 (최대 +{int(H.max() - BASE)}칸) · 식물 없음 · 방벽은 직접', font=fs, fill=(170, 170, 180))
def lab(x, z, t, col=(255, 255, 255)):
    gx_, gz_ = G(x, z)
    d.text((gx_ * S, 60 + gz_ * S), t, font=fs, fill=col, anchor='mm', stroke_width=3, stroke_fill=(15, 15, 20))
lab(VC[0], VC[1] - 15, '시작 마을 (윗마을)', (255, 230, 120)); lab(66, 156, '아랫마을', (255, 230, 120)); lab(66, 146, '작은 광장', (60, 60, 70)); lab(VC[0], VC[1], '광장', (60, 60, 70))
lab(*T((138, 86)), '농장 (사냥터 1)', (230, 255, 160)); lab(*T((166, 48)), '외곽 초원 (사냥터 2)', (230, 255, 160)); lab(MILL[0], MILL[1] - 6, '풍차 자리 (언덕)', (255, 240, 120)); lab(250, 120, '밀밭', (230, 255, 160))
lab(*T((78, 20)), '풀숲 언덕 (사냥터 3)', (230, 255, 160)); lab(*T((24, 30)), '깊은 풀숲 (사냥터 4)', (230, 255, 160)); lab(14, 120, '연못 · 폭포', (170, 210, 255)); lab(160, 156, '강', (170, 210, 255))
for (bx, bz) in BRIDGES:
    gx_, gz_ = G(bx, bz); d.rectangle((gx_ * S - 12, 60 + gz_ * S - 20, gx_ * S + 12, 60 + gz_ * S + 20), outline=(255, 240, 120), width=3)
    lab(bx + 8, bz, '다리', (255, 240, 120))
lab(T((22, 0))[0] + 20, -38, '↑ 출구 (숲의 군주 · 다음 지역)', (255, 220, 160))
for i, (nx, nz) in enumerate([(62, 92), T((160, 112)), T((166, 48)), T((78, 20)), T((24, 30))]): lab(nx + 14, nz + 8, ['시작', '①', '②', '③', '④'][i], (255, 120, 120))
lx = W * S + 16
d.text((lx, 70), '재질', font=fb, fill=(240, 240, 240))
for i, (k, t) in enumerate([('grass', '잔디'), ('path', '흙길'), ('plaza', '광장 (돌)'), ('farm', '밭 자리 (흙)'), ('water', '물'), ('sand', '모래'),
                            ('coarse', '거친 흙 (비탈)'), ('andesite', '안산암'), ('stone', '돌')]):
    y = 110 + i * 30
    d.rectangle((lx, y, lx + 22, y + 22), fill=COL[k]); d.text((lx + 32, y + 2), t, font=fs, fill=(220, 220, 230))
y = 400
for t in ['점선 = 높이 3칸마다', '밝을수록 높음', '', '통로 폭 5~7칸', '윗마을 약 100×80 + 아랫마을 93×51', '', '벽 위에 방벽은 직접 설치']:
    d.text((lx, y), t, font=fs, fill=(180, 180, 195)); y += 22
d.line((12, D * S + 64, 12 + 30 * S, D * S + 64), fill=(255, 255, 255), width=3); d.text((12 + 32 * S, D * S + 56), '30칸', font=fx, fill=(255, 255, 255))
img.save(sys.argv[1] if len(sys.argv) > 1 else 'rooms_preview.png')
