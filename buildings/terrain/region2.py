"""두 번째 지역: 숲길 → (옆) 숲의 군주 경기장 · 두 번째 마을 · 버섯 군락 5~8 · (옆) 수호자 경기장 → 출구. 식물 없음."""
import numpy as np, sys, json
from scipy.ndimage import distance_transform_edt, gaussian_filter, maximum_filter
from PIL import Image, ImageDraw, ImageFont

SC, HS, BASE = 1.5, 0.7, 10
OX, OZ = 12, 16                        # 여백 (설계 좌표가 음수인 곳)
DW, DD = 330 + OX, 250 + OZ            # 설계 크기 (동서 × 남북)
W, D = int(DW * SC), int(DD * SC)
X0, Z0 = np.meshgrid(np.arange(W), np.arange(D))
X, Z = X0 / SC - OX, Z0 / SC - OZ
def G(x, z): return int(round((x + OX) * SC)), int(round((z + OZ) * SC))
def vnoise(scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((int(DD // scale) + 3, int(DW // scale) + 3))
    fx, fz = (X + OX) / scale, (Z + OZ) / scale
    x0, z0 = fx.astype(int), fz.astype(int); tx, tz = fx - x0, fz - z0
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a, b, c, d = g[z0, x0], g[z0, x0 + 1], g[z0 + 1, x0], g[z0 + 1, x0 + 1]
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz
def fbm(seed): return vnoise(24, seed) * 0.55 + vnoise(12, seed + 1) * 0.3 + vnoise(6, seed + 2) * 0.15
def sstep(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def seg(px, pz, a, b):
    ax, az = a; bx, bz = b; vx, vz = bx - ax, bz - az; L = vx * vx + vz * vz
    t = np.clip(((px - ax) * vx + (pz - az) * vz) / L, 0, 1)
    return np.sqrt((px - ax - t * vx) ** 2 + (pz - az - t * vz) ** 2)
edge = fbm(5) - 0.5
WX = X + (fbm(301) - 0.5) * 20 + (vnoise(5, 303) - 0.5) * 5
WZ = Z + (fbm(302) - 0.5) * 20 + (vnoise(5, 304) - 0.5) * 5

ENTRY = (96, 250)          # 남쪽 입구 (시작 마을 북쪽 출구와 이어짐) = 붙이는 기준점
EXIT = (64, -OZ)          # 북쪽 출구 (Lv41~ 다음 지역)
# 구역: 중심, 반지름, 바닥 높이, 울퉁불퉁, 땅 종류, 상자들
ROOMS = {
    'gate':    ((100, 214), (24, 14), BASE, 1.0, 'grass',  [(80, 204, 122, 226), (90, 198, 112, 208)]),
    'village': ((112, 152), (44, 30), BASE + 2, 0, 'grass', [(70, 132, 154, 176), (84, 122, 140, 134), (150, 140, 162, 164)]),
    'g5':      ((206, 160), (32, 24), BASE + 2, 1.5, 'g5',   [(178, 140, 236, 182), (190, 132, 222, 142), (232, 150, 246, 172)]),
    'g6':      ((262, 96), (34, 28), BASE + 5, 2.0, 'g6',    [(232, 72, 294, 122), (244, 64, 280, 74), (226, 104, 240, 120)]),
    'g7':      ((176, 54), (38, 22), BASE + 1, 1.5, 'g7',    [(140, 38, 214, 72), (152, 30, 196, 40), (204, 52, 222, 70)]),
    'g8':      ((72, 52), (38, 26), BASE - 3, 1.5, 'g8',     [(36, 34, 108, 74), (48, 28, 94, 36), (30, 60, 46, 76)]),
}
ARENAS = {   # 보스 경기장 (둥글고 평평, 반지름 = 설계 칸): 중심, 반지름, 높이, 땅
    'boss1': ((24, 126), 27, BASE, 'arena1'),      # 숲의 군주 (실제 반지름 36 + 여유)
    'boss2': ((290, 20), 27, BASE + 4, 'arena2'),  # 거대버섯 수호자
}
PASS = [   # 통로
    ([ENTRY, (98, 224)], 8),
    ([(84, 214), (62, 232), (34, 236), (12, 220), (6, 192), (12, 164), (20, 150)], 7),   # 숲길 → 숲의 군주 (멀리 돌아가는 골짜기)
    ([(104, 200), (108, 176)], 8),          # 숲길 → 마을
    ([(152, 152), (180, 158)], 8),          # 마을 → 5
    ([(232, 142), (250, 120)], 7),          # 5 → 6
    ([(234, 80), (212, 66)], 7),            # 6 → 7
    ([(292, 108), (316, 104), (326, 82), (320, 58), (306, 44)], 7),   # 6 → 수호자 (멀리 돌아가는 골짜기)
    ([(146, 54), (94, 54)], 7),             # 7 → 8
    ([(66, 44), EXIT], 7),                  # 8 → 출구
]
walk = np.zeros((D, W), bool)
for nm, (c, r, h, rough, gnd, boxes) in ROOMS.items():
    for (x0, z0, x1, z1) in boxes: walk |= (WX >= x0) & (WX <= x1) & (WZ >= z0) & (WZ <= z1)
for nm, (c, r, h, gnd) in ARENAS.items():
    walk |= np.sqrt((X - c[0]) ** 2 + (Z - c[1]) ** 2) + edge * 2 < r
passm = np.zeros((D, W), bool)
for pts, w in PASS:
    d = np.full((D, W), 999.0)
    for a, b in zip(pts, pts[1:]): d = np.minimum(d, seg(X, Z, a, b))
    walk |= (d + edge * 3 < w / 2) | (d < w / 2 - 1.5)   # 가운데는 노이즈 없이 꼭 뚫림
    passm |= d < w / 2 + 4
np.save('R2_pass.npy', passm)
ROCKS = [(124, 140, 3), (196, 170, 4), (270, 108, 4), (164, 46, 3), (60, 60, 4), (92, 44, 3)]
rock = np.zeros((D, W))
for (rx, rz, rr) in ROCKS:
    dd = np.sqrt((X - rx) ** 2 + (Z - rz) ** 2) + edge * 3
    rock = np.maximum(rock, sstep(rr + 1.5, 0, dd) * rr * 1.5)

# 바닥 높이 (구역마다, 통로는 자연스러운 경사) + 어느 구역 땅인지
wsum = np.zeros((D, W)); hsum = np.zeros((D, W)); near = np.full((D, W), 1e9); zone = np.full((D, W), '', dtype=object)
for nm, (c, r, h, rough, gnd, boxes) in ROOMS.items():
    dist = np.sqrt(((X - c[0]) / r[0]) ** 2 + ((Z - c[1]) / r[1]) ** 2)
    wt = 1 / (dist ** 4 + 0.05); hh = h + (fbm(hash(nm) % 97) - 0.5) * 2 * rough
    wsum += wt; hsum += wt * hh
    upd = dist < near; zone[upd] = gnd; near = np.where(upd, dist, near)
for nm, (c, r, h, gnd) in ARENAS.items():
    dist = np.sqrt((X - c[0]) ** 2 + (Z - c[1]) ** 2) / r
    wt = 1 / (dist ** 4 + 0.05); wsum += wt; hsum += wt * h
    upd = dist < near; zone[upd] = gnd; near = np.where(upd, dist, near)
base = hsum / wsum
for nm, (c, r, h, rough, gnd, boxes) in ROOMS.items():
    if rough == 0: base = np.where(np.sqrt(((X - c[0]) / r[0]) ** 2 + ((Z - c[1]) / r[1]) ** 2) < 0.92, h, base)
for nm, (c, r, h, gnd) in ARENAS.items():   # 경기장은 완전 평평
    base = np.where(np.sqrt((X - c[0]) ** 2 + (Z - c[1]) ** 2) < r - 1, h, base)
    rock = np.where(np.sqrt((X - c[0]) ** 2 + (Z - c[1]) ** 2) < r + 2, 0, rock)

dout = distance_transform_edt(~walk) / SC
rise = (3 * sstep(0, 3, dout) + 25 * sstep(1.5, 10, dout) + (fbm(77) - 0.3) * 8 * sstep(6, 20, dout)) * HS
H = base + rise + rock
H = gaussian_filter(H, 0.8)
H = np.maximum(H, base + np.minimum(round(13 * HS), 2.2 * HS * dout + 1) * (dout >= 1))
H = np.where(walk, base + rock, H)
H = np.round(H).astype(int)

# 물: 마을 연못 + 8 어둠 경계의 검은 늪 웅덩이
WATER = np.zeros((D, W), bool); WLV = np.zeros((D, W), int)
pond = np.sqrt(((X - 92) / 9) ** 2 + ((Z - 162) / 6) ** 2) + edge * 0.4 < 1
WATER |= pond; WLV[pond] = BASE + 1
H = np.where(pond, BASE, H)
bog = np.zeros((D, W), bool)
for (bx, bz, br) in [(58, 48, 6), (84, 62, 5), (44, 64, 4)]:
    bog |= np.sqrt((X - bx) ** 2 + (Z - bz) ** 2) + edge * 4 < br
bog &= walk
WATER |= bog; WLV[bog] = BASE - 3
H = np.where(bog, BASE - 4, H)

# 길: 구불구불
PX = X + (fbm(501) - 0.5) * 7; PZ = Z + (fbm(502) - 0.5) * 7
near_walk = distance_transform_edt(~walk) / SC < 2.5
road = np.zeros((D, W), bool)
def spline(pts, n=10):
    P = [pts[0]] + list(pts) + [pts[-1]]; out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = (np.array(P[j], float) for j in (i - 1, i, i + 1, i + 2))
        for t in np.linspace(0, 1, n, endpoint=False):
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    out.append(np.array(pts[-1], float)); return [tuple(p) for p in out]
import pathing
BRIDGES = []
_pcost = None
def path(pts, width):
    """실제 지형 위 길 찾기 (공터 가운데·완만한 곳으로), 폭 일정, 둥글게 다듬음"""
    global road, _pcost
    if _pcost is None: _pcost = pathing.make_cost(H, walk, WATER, fbm(701))
    cells = []
    for a, b in zip(pts, pts[1:]):
        r_ = pathing.route(_pcost, G(*a), G(*b)); cells += r_ if not cells else r_[1:]
    poly = pathing.smooth(cells)
    m = pathing.stamp(road.shape, poly, 1.0 + width * 0.6)
    hit = m & WATER
    if hit.any(): zz, xx = np.nonzero(hit); BRIDGES.append((xx.mean() / SC - OFFX, zz.mean() / SC - OFFZ) if 'OFFX' in globals() else (xx.mean() / SC - OX, zz.mean() / SC - OZ))
    road |= m & ~WATER
path([ENTRY, (98, 214), (104, 196), (110, 176), (112, 152), (150, 152), (182, 158), (206, 160), (234, 140), (250, 118), (262, 96), (234, 80), (210, 64), (176, 54), (140, 54), (108, 54), (72, 52), (64, 30), EXIT], 1.6)
path([(98, 214), (84, 214), (62, 232), (34, 236), (12, 220), (6, 192), (12, 164), (24, 126)], 1.1)      # 숲의 군주 경기장
path([(262, 96), (292, 108), (316, 104), (326, 82), (320, 58), (306, 44), (290, 20)], 1.1)      # 수호자 경기장
plaza = np.sqrt((X - 112) ** 2 + (Z - 152) ** 2) < 8

# 재질
gx, gz = np.gradient(H.astype(float)); slope = np.sqrt(gx ** 2 + gz ** 2)
MAT = np.full((D, W), 'grass', dtype=object)
fl = fbm(611)
inroom = dout < 4   # 구역 땅 종류는 공터·비탈 아래쪽만 (바깥 고원은 잔디)
zone_full = zone.copy()
zone = np.where(inroom, zone, '')
MAT[zone == 'g5'] = np.where(fl[zone == 'g5'] > 0.55, 'podzol', 'grass')
MAT[zone == 'g6'] = np.where(fl[zone == 'g6'] > 0.45, 'mycelium', 'podzol')
MAT[zone == 'g7'] = np.where(fl[zone == 'g7'] > 0.35, 'mycelium', 'coarse')
MAT[zone == 'g8'] = np.where(fl[zone == 'g8'] > 0.5, 'mud', 'deepdirt')
MAT[zone == 'arena1'] = 'grass'
MAT[zone == 'arena2'] = 'mycelium'
dark = np.isin(zone_full, ['g7', 'g8', 'arena2']) & (dout < 16)
rockn = fbm(91)
steep = slope >= 1.0
MAT[steep & ~dark & (rockn > 0.5)] = 'andesite'; MAT[steep & ~dark & (rockn <= 0.5)] = 'stone'
MAT[steep & dark & (rockn > 0.5)] = 'tuff'; MAT[steep & dark & (rockn <= 0.5)] = 'deepslate'
MAT[walk & (rock >= 1.5)] = np.where(dark[walk & (rock >= 1.5)], 'deepslate', 'stone')
MAT[road] = 'path'; MAT[plaza] = 'plaza'; MAT[WATER] = np.where(bog[WATER], 'bog', 'water')
# 경기장 테두리 (원 표시용 돌 고리, 반지름 = 실제 경기장)
for nm, (c, r, h, gnd) in ARENAS.items():
    rr = np.sqrt((X - c[0]) ** 2 + (Z - c[1]) ** 2)
    MAT[(np.abs(rr - 24) < 0.5) & walk & ~road] = 'ring'

np.save('R2_H.npy', H); np.save('R2_MAT.npy', MAT); np.save('R2_walk.npy', walk); np.save('R2_WLV.npy', WLV)
json.dump({'SC': SC, 'entry': G(*ENTRY), 'exit': G(*EXIT), 'arenas': {k: G(*v[0]) for k, v in ARENAS.items()}}, open('R2_meta.json', 'w'))
print('H', H.min(), H.max(), 'size', W, D)

# ───────── 미리보기 ─────────
F = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
fb = ImageFont.truetype(F, 22, index=1); fs = ImageFont.truetype(F, 14, index=1); fx = ImageFont.truetype(F, 12, index=1)
S = 3
COL = {'grass': (98, 156, 64), 'stone': (122, 122, 126), 'andesite': (146, 146, 142), 'coarse': (120, 92, 66), 'podzol': (112, 82, 40),
       'mycelium': (118, 100, 120), 'mud': (60, 56, 60), 'deepdirt': (82, 66, 52), 'tuff': (100, 104, 92), 'deepslate': (66, 66, 72),
       'path': (178, 148, 98), 'plaza': (172, 172, 178), 'water': (60, 110, 205), 'bog': (52, 70, 60), 'ring': (200, 200, 200)}
arr = np.zeros((D, W, 3))
for k, c in COL.items(): arr[MAT == k] = c
lum = 0.82 + 0.018 * (H - BASE); shade = 1 + 0.30 * (-gx - 1.2 * gz) / 1.56
arr = np.clip(arr * np.where(np.isin(MAT, ['water', 'bog']), 1, lum * shade)[..., None], 0, 255).astype(np.uint8)
img = Image.new('RGB', (W * S + 290, D * S + 70), (20, 22, 28))
img.paste(Image.fromarray(arr).resize((W * S, D * S), Image.NEAREST), (0, 60))
d = ImageDraw.Draw(img)
def lab(x, z, t, col=(255, 255, 255)):
    gx_, gz_ = G(x, z); d.text((gx_ * S, 60 + gz_ * S), t, font=fs, fill=col, anchor='mm', stroke_width=3, stroke_fill=(15, 15, 20))
d.text((12, 10), f'두 번째 지역 시안 — 숲의 군주 · 두 번째 마을 · 버섯 군락 5~8 · 수호자 · {W} × {D}칸', font=fb, fill=(240, 230, 200))
d.text((12, 38), '아래 = 시작 마을에서 들어오는 길 · 보스 경기장은 옆길 (지나가다 안 걸림) · 식물 없음 (버섯·나무는 직접)', font=fs, fill=(170, 170, 180))
lab(100, 220, '숲길 (입구)', (255, 220, 160)); lab(96, 244, '↓ 시작 마을', (255, 220, 160))
lab(24, 112, '숲의 군주 경기장 (Lv20)', (255, 140, 140)); lab(112, 140, '두 번째 마을', (255, 230, 120)); lab(112, 152, '광장', (60, 60, 70))
lab(206, 150, '⑤ 버섯 군락 입구 (21~25)', (230, 255, 160)); lab(262, 86, '⑥ 버섯 군락 안쪽 (26~30)', (230, 255, 160))
lab(176, 46, '⑦ 버섯 군락 깊은 곳 (31~35)', (230, 255, 160)); lab(72, 44, '⑧ 어둠 경계 (36~40)', (230, 255, 160))
lab(290, 6, '수호자 경기장 (Lv35)', (255, 140, 140)); lab(64, 6, '↑ 다음 지역 (Lv41~)', (255, 220, 160)); lab(92, 170, '연못', (170, 210, 255)); lab(58, 56, '검은 늪', (170, 210, 200))
lx = W * S + 16
d.text((lx, 70), '재질', font=fb, fill=(240, 240, 240))
for i, (k, t) in enumerate([('grass', '잔디'), ('podzol', '포드졸'), ('mycelium', '균사체'), ('coarse', '거친 흙'), ('mud', '진흙'),
                            ('deepdirt', '어두운 흙'), ('path', '흙길'), ('water', '물'), ('bog', '늪 (얕은 물)'), ('ring', '경기장 원 (반지름 36)'),
                            ('stone', '돌'), ('andesite', '안산암'), ('tuff', '응회암'), ('deepslate', '심층암')]):
    y = 110 + i * 28
    d.rectangle((lx, y, lx + 22, y + 22), fill=COL[k]); d.text((lx + 32, y + 2), t, font=fs, fill=(220, 220, 230))
d.line((12, D * S + 64, 12 + 30 * S, D * S + 64), fill=(255, 255, 255), width=3); d.text((12 + 32 * S, D * S + 56), '30칸', font=fx, fill=(255, 255, 255))
img.save(sys.argv[1] if len(sys.argv) > 1 else 'r2.png')
