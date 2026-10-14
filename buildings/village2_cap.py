"""연구소 갓 v4 (사진2 Shrumble Ville 갓 재현): 볼록한 어깨, 빨강→주황 그라데이션, 고드름 같은 흘러내림, 오목한 회색 주름, 흰 점, 작은 버섯들"""
import math, random
import schem

schem.COL[:0] = [('red_nether_brick', (75, 15, 18)), ('tuff', (108, 109, 102)), ('red_terracotta', (143, 61, 46)),
                 ('smooth_red_sandstone', (181, 98, 31)), ('polished_andesite', (132, 134, 133))]

OUTER = {22: 12.0, 23: 12.8, 24: 13.1, 25: 12.9, 26: 12.3, 27: 11.3, 28: 9.9, 29: 7.9, 30: 5.3, 31: 2.3}

def clear_old(s):
    for (x, y, z) in list(s.b):
        d = math.hypot(x, z)
        if y >= 24 or (y >= 17 and d > 6.5): del s.b[(x, y, z)]

def under_y(d):
    return 22 if d > 10.3 else 23 if d > 7.3 else 24

def top_y(d, nz):
    t = None
    for y in sorted(OUTER):
        if OUTER[y] + nz >= d: t = y
    return t

def build(s, seed=4):
    r = random.Random(seed)
    ph = [r.random() * 6.28 for _ in range(4)]
    def nz(a): return 0.35 * math.sin(3 * a + ph[0]) + 0.25 * math.sin(7 * a + ph[1]) + 0.15 * math.sin(11 * a + ph[2])
    cells = {}
    for x in range(-15, 16):
        for z in range(-15, 16):
            d = math.hypot(x, z); a = math.atan2(z, x); n = nz(a)
            ty = top_y(d, n)
            if ty is None: continue
            uy = under_y(d)
            if d <= 4.6: uy = 24
            for y in range(uy, ty + 1):
                if d < 4.4 and y < 24: continue
                cells[(x, y, z)] = (d, a, y == ty, y == uy)
    for (x, y, z), (d, a, is_top, is_bot) in cells.items():
        if is_bot and d > 4.6 and d < OUTER[22] - 0.6:
            # 오목한 아랫면 주름 (방사형 줄무늬)
            st = 'tuff' if int((a + 3.1416) * 26 / 6.2832) % 2 else r.choice(['light_gray_terracotta', 'polished_andesite'])
        elif y >= 29 or (is_top and d < 8.6):
            st = r.choices(['orange_terracotta', 'smooth_red_sandstone', 'terracotta', 'red_mushroom_block'], [70, 16, 6, 8])[0]
        elif y >= 27 or (is_top and d < 10.2):
            st = r.choice(['orange_terracotta', 'red_mushroom_block', 'red_mushroom_block', 'smooth_red_sandstone'])
        else:
            dark = 0.12 if y <= 23 else 0.03
            q = r.random()
            st = 'red_nether_bricks' if q < dark else 'red_terracotta' if q < dark + 0.12 else 'red_mushroom_block'
        s.set(x, y, z, st)
    # 흰 점 (빨간 띠에, 2~3칸 덩어리)
    band = [p for p, v in cells.items() if 24 <= p[1] <= 26 and not v[3] and v[0] > OUTER[p[1]] - 1.2]
    r.shuffle(band)
    for (x, y, z) in band[:14]:
        s.set(x, y, z, 'mushroom_stem')
        if r.random() < 0.6:
            q = r.choice([(x, y + 1, z), (x + 1, y, z), (x, y, z + 1), (x - 1, y, z)])
            if q in cells and s.get(*q) and 'red' in s.get(*q): s.set(*q, 'mushroom_stem')
    # 고드름 흘러내림 (빽빽, 길이 1~5, 끝은 어두운 빨강)
    for i in range(110):
        a = i / 110 * 6.2832
        rad = OUTER[22] + nz(a) - 0.3
        x, z = round(math.cos(a) * rad), round(math.sin(a) * rad)
        if (x, 22, z) not in cells: continue
        L = r.choices([0, 1, 2, 3, 4, 5], [10, 30, 28, 18, 9, 5])[0]
        for k in range(1, L + 1):
            if s.get(x, 22 - k, z) is not None: break
            s.set(x, 22 - k, z, 'red_nether_bricks' if k == L and L > 1 else 'red_mushroom_block' if r.random() > 0.2 else 'red_terracotta')
    # 주름 사이 빛 + 사슬 랜턴
    gills = [p for p, v in cells.items() if v[3] and 5.5 < v[0] < 10.5]
    r.shuffle(gills)
    for p in gills[:9]: s.set(*p, 'shroomlight')
    for i in range(7):
        a = i / 7 * 6.2832 + 0.4; rad = 6.5 + (i % 3) * 1.4
        x, z = round(math.cos(a) * rad), round(math.sin(a) * rad)
        uy = under_y(math.hypot(x, z)); L = 2 + (i * 5) % 4
        for k in range(1, L + 1): s.set(x, uy - k, z, 'chain')
        s.set(x, uy - L - 1, z, 'lantern[hanging=true]')
    # 갓 위 작은 버섯 5개 (크기·색·기울기 다르게, 흰 점)
    def topmost(x, z):
        return max(y for (xx, y, zz) in s.b if xx == x and zz == z)
    for (mx, mz, h, size, m, lean) in ((-5, -3, 4, 2, 'red_mushroom_block', (1, 0)), (4, -6, 3, 1, 'brown_mushroom_block', (0, 0)),
                                       (7, 3, 2, 1, 'red_mushroom_block', (0, 1)), (-2, 6, 2, 0, 'brown_mushroom_block', (0, 0)),
                                       (1, -1, 3, 1, 'brown_mushroom_block', (-1, 0))):
        y0 = topmost(mx, mz); x, z = mx, mz
        for k in range(1, h + 1):
            if k == h - 1 and lean != (0, 0): x += lean[0]; z += lean[1]
            s.set(x, y0 + k, z, 'mushroom_stem')
        yc = y0 + h + 1
        if size == 0:
            s.set(x, yc, z, m)
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)): s.set(x + dx, yc - 1, z + dz, m) if False else None
            continue
        R = size + 0.6
        for dx in range(-size - 1, size + 2):
            for dz in range(-size - 1, size + 2):
                dd = math.hypot(dx, dz)
                if dd <= R + 0.5: s.set(x + dx, yc, z + dz, m)
                if dd <= R - 0.6: s.set(x + dx, yc + 1, z + dz, m)
                if size >= 2 and dd <= 1.0: s.set(x + dx, yc + 2, z + dz, m)
        for dx, dz in ((size + 1, 0), (-size - 1, 0), (0, size + 1), (0, -size - 1)):
            s.set(x + dx, yc - 1, z + dz, m)   # 처진 가장자리
        if m.startswith('red'):
            for (dx, dz) in ((1, -1), (-1, 1)) if size >= 1 else ():
                s.set(x + dx, yc + 1, z + dz, 'mushroom_stem')
    # 몸통 선반버섯 3개 (갈색, 반원 2단)
    for (a, y) in ((0.7, 15), (2.8, 18), (4.6, 16)):
        cx, cz = math.cos(a) * 4.6, math.sin(a) * 4.6
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                x, z = round(cx + dx), round(cz + dz)
                d0 = math.hypot(x - cx, z - cz); dt = math.hypot(x, z)
                if dt > 4.6 and d0 <= 2.3 and s.get(x, y, z) is None: s.set(x, y, z, 'brown_mushroom_block')
                if dt > 4.6 and d0 <= 1.4 and s.get(x, y + 1, z) is None: s.set(x, y + 1, z, 'brown_mushroom_block')
