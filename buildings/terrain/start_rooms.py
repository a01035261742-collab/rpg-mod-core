"""시작 마을 지역 v2: 공터(구역) + 자연스러운 바위 언덕 벽 + 좁은 통로. 식물 없음."""
import numpy as np, sys
from scipy.ndimage import distance_transform_edt, gaussian_filter
from PIL import Image, ImageDraw, ImageFont

OFF = 20                              # 바깥 여백 (두꺼운 벽)
W, D = 200 + 2 * OFF, 170 + 2 * OFF   # 동서 × 남북
X0, Z0 = np.meshgrid(np.arange(W), np.arange(D))   # 배열 칸
X, Z = X0 - OFF, Z0 - OFF                          # 설계 좌표 (여백 = 음수·200 넘음)

def vnoise(scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((D // scale + 3, W // scale + 3))
    fx, fz = X0 / scale, Z0 / scale
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
    'farm':    ((138, 103), (32, 18), BASE, 0),
    'meadow':  ((166, 48), (24, 26), BASE + 1, 2.0),
    'forest':  ((56, 26), (44, 20), BASE + 4, 1.5),
}
# 통로: 점들, 폭
PASS = [
    ([(84, 96), (114, 100)], 7),                  # 마을 → 농장
    ([(148, 92), (154, 76), (158, 58)], 7),       # 농장 → 초원
    ([(146, 40), (126, 36), (106, 34)], 7),       # 초원 → 풀숲 (오르막)
    ([(36, 108), (24, 122)], 5),                  # 마을 → 연못
    ([(24, 10), (22, -OFF)], 6),                     # 풀숲 서쪽 → 북쪽 (다음 지역)
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
    'forest':  [(50, 12, 102, 38), (60, 6, 86, 14), (94, 24, 108, 42), (34, 18, 54, 34), (8, 6, 40, 46), (16, 40, 34, 54)],
}
for name, bs in BOXES.items():
    for (x0, z0, x1, z1) in bs:
        walk |= (WX >= x0) & (WX <= x1) & (WZ >= z0) & (WZ <= z1)
for pts, w in PASS:
    d = np.full((D, W), 999.0)
    for a, b in zip(pts, pts[1:]): d = np.minimum(d, seg(X, Z, a, b))
    walk |= d + edge * 3 < w / 2
# 공터 안 바위 언덕 (튀어나온 곳)
ROCKS = [(34, 80, 4), (104, 74, 3), (158, 34, 4), (180, 60, 3), (96, 22, 3), (166, 92, 2)]
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
for name, ((cx, cz), (rx, rz), h, rough) in ROOMS.items():   # 마을·농장·연못은 완전 평평
    if rough == 0:
        inner = np.sqrt(((X - cx) / rx) ** 2 + ((Z - cz) / rz) ** 2) < 0.92
        base = np.where(inner, h, base)
# 벽: 바깥으로 갈수록 오름 (처음 몇 칸은 완만한 둔덕 → 바위 비탈 → 꼭대기 들쭉날쭉)
dout = distance_transform_edt(~walk)
rise = 3 * sstep(0, 3, dout) + 25 * sstep(1.5, 10, dout) + (fbm(77) - 0.3) * 8 * sstep(6, 20, dout)
H = base + rise + rock
# 마을↔풀숲 사이 (예전 통로 자리): 높은 산등성이
H += 12 * np.exp(-(((X - 70) / 24) ** 2 + ((Z - 54) / 8) ** 2)) * (~walk)
# 마을 북서쪽 언덕 단 (+2, 큰 집 자리)
terr = (WX >= 34) & (WX <= 56) & (WZ >= 68) & (WZ <= 82) & walk
base = np.where(terr, base + 2, base)
H = np.where(terr, H + 2, H)
H = gaussian_filter(H, 0.8)
# 낮은 안장 없애기: 벽 안쪽 몇 칸만 지나면 최소 +13 (얇은 벽도 못 넘을 높이)
H = np.maximum(H, base + np.minimum(13, 2.2 * dout + 1) * (dout >= 1))
H = np.where(walk, base + rock, H)
H = np.round(H).astype(int)

# 연못 + 폭포 (연못 남쪽 절벽)
pond_c = ROOMS['pond'][0]
pd = np.sqrt(((X - pond_c[0]) / 8) ** 2 + ((Z - pond_c[1] - 3) / 6) ** 2)
WATER = BASE - 2
# 강: 서쪽 절벽 폭포 → 연못 → 마을 남쪽 → 농장 남쪽 → 동쪽 협곡으로 빠짐
RIVER = [(pond_c[0], pond_c[1] + 3), (32, 128), (50, 120), (72, 121), (94, 124), (114, 123), (134, 123), (152, 125), (172, 132), (199 + OFF, 134)]
sd = np.full((D, W), 999.0)
for a, b in zip(RIVER, RIVER[1:]): sd = np.minimum(sd, seg(X, Z, a, b))
rw = 2.6 + fbm(51) * 1.6                       # 강폭 반 (5~8칸)
river = sd < rw
water = (pd < 1) | river
# 강가: 물 양옆 5칸은 낮고 평평한 둑, 벽을 지나는 곳은 협곡 (양옆 바위 비탈)
bankw = sd - rw
wallz = gaussian_filter((dout >= 3).astype(float), 2) > 0.5     # 벽 지대: 강이 좁은 협곡으로만 지나감 (둑 길 없음)
valley = np.where(wallz, sstep(1.5, 0.3, bankw), sstep(9, 2, bankw))
H = np.where(H > BASE - 1, H * (1 - valley) + (BASE - 1) * valley, H).round().astype(int)
H = np.where(water, WATER - 1 - ((sd < rw * 0.5) | (pd < 0.6)), H)
walk = walk | ((bankw < 4) & ~water & ~wallz)
fall_x, fall_z = None, pond_c[1] + 3
for x in range(pond_c[0] - 6, -OFF - 1, -1):          # 연못 서쪽으로 가다가 처음 높아지는 곳 = 폭포
    if H[fall_z + OFF, x + OFF] >= BASE + 8: fall_x = x; break
BRIDGES = []

# 길
road = np.zeros((D, W), bool)
def path(pts, width):
    global road
    d = np.full((D, W), 999.0)
    for a, b in zip(pts, pts[1:]): d = np.minimum(d, seg(X, Z, a, b))
    hit = (d < width) & water
    if hit.any(): zz, xx = np.nonzero(hit); BRIDGES.append((int(xx.mean()) - OFF, int(zz.mean()) - OFF))
    road |= (d < width) & walk & ~water
VC = ROOMS['village'][0]
path([VC, (84, 97), (106, 101), (140, 104)], 1.8)
path([(140, 104), (150, 88), (156, 72), (164, 50), (146, 40), (126, 36), (106, 34), (76, 26), (46, 26), (24, 14), (22, -OFF)], 1.4)
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
farm = (np.abs(X - fc[0] - 4) < 24) & (np.abs(Z - fc[1] - 2) < 9) & ~road   # 밭 약 48 × 18
MAT[farm] = 'farm'
MAT[water] = 'water'
np.save('H2.npy', H); np.save('MAT2.npy', MAT); np.save('walk2.npy', walk)
print('H', H.min(), H.max(), 'fall', fall_x, fall_z)
# 공터 사이 벽 가장 낮은 곳 (마을↔풀숲, 마을↔초원, 농장↔강 등): 경계 위 최소 높이
from scipy.ndimage import label
lab_, n_ = label(walk)
print('구역 수', n_)
for x0, z0, x1, z1, nm in [(60, 44, 100, 66, '마을-풀숲'), (100, 60, 130, 92, '마을-초원'), (108, 40, 140, 60, '풀숲-초원')]:
    sub = H[z0 + OFF:z1 + OFF, x0 + OFF:x1 + OFF]
    cols = [sub[:, i].max() for i in range(sub.shape[1])]
    print(nm, '벽 최저', min(cols) - BASE)

# ───────── 미리보기 ─────────
F = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
fb = ImageFont.truetype(F, 22, index=1); fs = ImageFont.truetype(F, 14, index=1); fx = ImageFont.truetype(F, 12, index=1)
S = 4
img = Image.new('RGB', (W * S + 280, D * S + 70), (20, 22, 28))
COL = {'grass': (98, 156, 64), 'stone': (122, 122, 126), 'andesite': (146, 146, 142), 'coarse': (120, 92, 66), 'sand': (214, 200, 150),
       'path': (178, 148, 98), 'plaza': (172, 172, 178), 'farm': (110, 76, 50), 'water': (60, 110, 205)}
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
if fall_x is not None: d.rectangle(((fall_x + OFF) * S, 60 + (fall_z + OFF) * S - 4, (pond_c[0] - 6 + OFF) * S, 60 + (fall_z + OFF) * S + 4), fill=(110, 160, 235))

d.text((12, 10), '시작 마을 지역 v13 — 마을·폭포 왼쪽으로 · 농장 넓힘 · 240 × 210칸', font=fb, fill=(240, 230, 200))
d.text((12, 38), f'공터 높이 {BASE} 기준 · 벽은 바깥으로 갈수록 완만하게 올라감 (최대 +{int(H.max() - BASE)}칸) · 식물 없음 · 방벽은 직접', font=fs, fill=(170, 170, 180))
def lab(x, z, t, col=(255, 255, 255)):
    d.text(((x + OFF) * S, 60 + (z + OFF) * S), t, font=fs, fill=col, anchor='mm', stroke_width=3, stroke_fill=(15, 15, 20))
lab(VC[0], VC[1] - 15, '시작 마을 (윗마을)', (255, 230, 120)); lab(66, 156, '아랫마을', (255, 230, 120)); lab(66, 146, '작은 광장', (60, 60, 70)); lab(VC[0], VC[1], '광장', (60, 60, 70))
lab(138, 88, '농장 (사냥터 1)', (230, 255, 160)); lab(166, 48, '외곽 초원 (사냥터 2)', (230, 255, 160))
lab(78, 20, '풀숲 언덕 (사냥터 3)', (230, 255, 160)); lab(24, 30, '깊은 풀숲 (사냥터 4)', (230, 255, 160)); lab(14, 120, '연못 · 폭포', (170, 210, 255)); lab(120, 132, '강', (170, 210, 255))
for (bx, bz) in BRIDGES:
    d.rectangle(((bx + OFF) * S - 10, 60 + (bz + OFF) * S - 16, (bx + OFF) * S + 10, 60 + (bz + OFF) * S + 16), outline=(255, 240, 120), width=3)
    lab(bx + 8, bz, '다리', (255, 240, 120))
lab(40, -12, '↑ 출구 (숲의 군주 · 다음 지역)', (255, 220, 160))
for i, (nx, nz) in enumerate([(62, 92), (160, 112), (166, 48), (78, 20), (24, 30)]): lab(nx + 14, nz + 8, ['시작', '①', '②', '③', '④'][i], (255, 120, 120))
lx = W * S + 16
d.text((lx, 70), '재질', font=fb, fill=(240, 240, 240))
for i, (k, t) in enumerate([('grass', '잔디'), ('path', '흙길'), ('plaza', '광장 (돌)'), ('farm', '밭 자리 (흙)'), ('water', '물'), ('sand', '모래'),
                            ('coarse', '거친 흙 (비탈)'), ('andesite', '안산암'), ('stone', '돌')]):
    y = 110 + i * 30
    d.rectangle((lx, y, lx + 22, y + 22), fill=COL[k]); d.text((lx + 32, y + 2), t, font=fs, fill=(220, 220, 230))
y = 400
for t in ['점선 = 높이 3칸마다', '밝을수록 높음', '', '통로 폭 5~7칸', '윗마을 68×54 + 아랫마을 62×34', '', '벽 위에 방벽은 직접 설치']:
    d.text((lx, y), t, font=fs, fill=(180, 180, 195)); y += 22
d.line((12, D * S + 64, 12 + 20 * S, D * S + 64), fill=(255, 255, 255), width=3); d.text((12 + 22 * S, D * S + 56), '20칸', font=fx, fill=(255, 255, 255))
img.save(sys.argv[1] if len(sys.argv) > 1 else 'rooms_preview.png')
