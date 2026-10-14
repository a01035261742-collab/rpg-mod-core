"""두 번째 마을 초안 v2 — 디테일·각 보강 (레퍼런스: 사진1 버섯 탑, 사진2 Shrumble Ville). 식물 없음"""
import os, random, math
import schem
from schem import S, autoconnect, render2, view2
from lib2 import box, roof, door, path
from sheet import sheet

schem.COL[:0] = [('orange_terracotta', (165, 85, 40)), ('light_gray_terracotta', (135, 105, 95)), ('gray_terracotta', (58, 42, 36)),
                 ('brown_terracotta', (78, 52, 36)), ('red_sandstone', (180, 95, 35)), ('smooth_sandstone', (222, 210, 160)),
                 ('sandstone', (215, 200, 150)), ('stripped_spruce', (115, 90, 55)), ('cut_sandstone', (218, 205, 155)),
                 ('green_terracotta', (76, 83, 42)), ('lime_terracotta', (103, 117, 52)), ('white_terracotta', (210, 178, 161)),
                 ('mossy_cobblestone_stairs', (95, 115, 85)), ('brown_wool', (115, 75, 45)), ('red_wall_banner', (170, 40, 40)),
                 ('stone_brick', (130, 130, 130)), ('dark_oak_wood', (60, 45, 30)), ('spruce_wood', (60, 45, 30)), ('stripped_dark_oak', (95, 70, 45)), ('bricks', (150, 75, 60)),
                 ('iron_bars', (110, 110, 115))]

OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
DIRS = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}

def ring_fill(s, cx, cz, y, r_out, mat, r_in=-1, noise=None):
    R = int(r_out) + 3
    for x in range(int(cx) - R, int(cx) + R + 1):
        for z in range(int(cz) - R, int(cz) + R + 1):
            d = math.hypot(x - cx, z - cz)
            ro = r_out + (noise(x - cx, z - cz) if noise else 0)
            if r_in < d <= ro: s.set(x, y, z, mat(x, z, d) if callable(mat) else mat)

def noise_fn(seed, amp):
    r = random.Random(seed); ph = [r.random() * 6.28 for _ in range(3)]
    def f(x, z):
        a = math.atan2(z, x)
        return amp * (math.sin(3 * a + ph[0]) * 0.5 + math.sin(5 * a + ph[1]) * 0.35 + math.sin(7 * a + ph[2]) * 0.15)
    return f

def stair_edges(s, pred, stair, ys=None):
    """테라스 가장자리 위에 계단 얹기 → 경사가 각지게 이어짐"""
    for (x, y, z), st in list(s.b.items()):
        if not pred(st) or (ys and y not in ys): continue
        if s.get(x, y + 1, z) is not None: continue
        for f, (dx, dz) in DIRS.items():
            n = s.get(x + dx, y + 1, z + dz)
            if n and pred(n):
                s.set(x, y + 1, z, f'{stair}_stairs[facing={f},half=bottom,shape=straight]'); break

def brackets(s, pts, wood='spruce'):
    """돌출부 아래 받침: 거꾸로 계단 (벽 쪽을 향함)"""
    for x, y, z, f in pts: s.set(x, y, z, f'{wood}_stairs[facing={f},half=top,shape=straight]')

def cap(s, cx, cz, y0, radii, mat, lip=None, gill='mushroom_stem', nz=None, lean=None):
    """계단식 갓: 층마다 깔끔한 테라스, 아랫면 주름 + 처진 챙"""
    for k, rad in enumerate(radii):
        nxt = radii[k + 1] if k + 1 < len(radii) else -1
        c = cx + (lean(k) if lean else 0)
        ring_fill(s, c, cz, y0 + k, rad, lambda x, z, d, k=k: mat(x, y0 + k, z, d, k), r_in=(min(nxt - 0.3, rad - 1.7) if k else -1), noise=nz)
    # 아랫면: 흰 주름 (방사형 줄무늬)
    ring_fill(s, cx, cz, y0 - 1, radii[0] - 1.0, lambda x, z, d: gill if int((math.atan2(z - cz, x - cx) + 3.15) * 4) % 2 else 'brown_mushroom_block', r_in=1.6, noise=nz)
    # 처진 챙
    ring_fill(s, cx, cz, y0 - 1, radii[0] + 0.3, lip or (lambda x, z, d: mat(x, y0, z, d, 0)), r_in=radii[0] - 1.0, noise=nz)

# ═════════ A. 버섯 연구소 (모리스) ═════════
def lab():
    s = S('v2/mush_lab'); r = random.Random(11)
    # 뿌리 6갈래 (나무 껍질 블록 + 끝은 계단으로 경사)
    for i in range(7):
        a = i / 7 * 6.283 + 0.4
        for t in range(0, 10):
            d = 4.0 + t * 0.5; h = int(4.5 - (d - 4.5) * 1.3)
            if h < 0: break
            x, z = round(math.cos(a) * d), round(math.sin(a) * d)
            for y in range(0, h + 1): s.set(x, y, z, r.choice(['dark_oak_wood', 'spruce_wood', 'dark_oak_wood']))
    for (x, y, z), st in list(s.b.items()):
        if st.endswith('_wood') and s.get(x, y + 1, z) is None and math.hypot(x, z) > 4.6:
            dx, dz = -x, -z
            f = ('east' if dx > 0 else 'west') if abs(dx) > abs(dz) else ('south' if dz > 0 else 'north')
            s.set(x, y + 1, z, f'dark_oak_stairs[facing={f},half=bottom,shape=straight]')
    # 바닥 돌 기초
    ring_fill(s, 0, 3, 0, 11.5, lambda x, z, d: r.choice(['stone_bricks', 'stone_bricks', 'cobblestone', 'mossy_stone_bricks', 'andesite']), noise=None)
    # 몸통 (지름 9) + 나선형 나무 결 (돌출 리브)
    BARK = ['spruce_log[axis=y]', 'stripped_spruce_log[axis=y]', 'brown_terracotta', 'spruce_log[axis=y]']
    for y in range(1, 24):
        ring_fill(s, 0, 0, y, 4.5, lambda x, z, d, y=y: BARK[int((math.atan2(z, x) * 2 + y * 0.45) % 4)], r_in=3.3)
        for k in range(6):
            a = k / 6 * 6.283 + y * 0.13
            s.set(round(math.cos(a) * 5.0), y, round(math.sin(a) * 5.0), 'dark_oak_wood')
    # 1층 (y1~4)
    box(s, r, -8, -2, 17, 11, 1, 4, frame='spruce', infill=('calcite',) * 6 + ('white_terracotta',), post_step=4, win_sides=('s', 'w', 'e'), found=False)
    # 2층 (y6~9) 1칸 돌출 + 받침
    for x in range(-9, 10):
        for z in range(-3, 10):
            if s.get(x, 5, z) is None or 'calcite' in (s.get(x, 5, z) or '') or 'log' in (s.get(x, 5, z) or ''): s.set(x, 5, z, 'spruce_planks')
    for x in range(-9, 10): s.set(x, 5, 9, 'stripped_dark_oak_log[axis=x]'); s.set(x, 5, -3, 'stripped_dark_oak_log[axis=x]')
    for z in range(-3, 10): s.set(-9, 5, z, 'stripped_dark_oak_log[axis=z]'); s.set(9, 5, z, 'stripped_dark_oak_log[axis=z]')
    box(s, r, -9, -3, 19, 13, 6, 4, frame='dark_oak', infill=('calcite',) * 6 + ('white_terracotta',), post_step=3, found=False, win_sides=('s', 'w', 'e'))
    bx = [(x, 4, 9, 'north') for x in (-8, -4, 0, 4, 8)] + [(-9, 4, z, 'east') for z in (-2, 2, 6)] + [(9, 4, z, 'west') for z in (-2, 2, 6)]
    brackets(s, bx, 'dark_oak')
    # 2층 대각 버팀 (정면)
    for x0 in (-9, 3):
        for i in range(3):
            s.set(x0 + i + (0 if x0 < 0 else 0), 7 + i if x0 < 0 else 9 - i, 10, None)
    # 몸통 복원 (집 안)
    for y in range(1, 11):
        ring_fill(s, 0, 0, y, 4.5, lambda x, z, d, y=y: BARK[int((math.atan2(z, x) * 2 + y * 0.45) % 4)], r_in=3.3)
    # 정문 + 차양
    for y in (1, 2, 3):
        for x in (-1, 0, 1): s.set(x, y, 8, None)
    door(s, 0, 1, 8, wood='spruce'); s.set(-1, 1, 8, 'spruce_planks'); s.set(1, 1, 8, 'spruce_planks')
    s.set(-1, 2, 8, 'spruce_planks'); s.set(1, 2, 8, 'spruce_planks'); s.set(0, 3, 8, 'spruce_planks')
    for x in (-2, 2): s.set(x, 1, 10, 'spruce_fence'); s.set(x, 2, 10, 'spruce_fence'); s.set(x, 3, 10, 'spruce_fence')
    for x in range(-2, 3):
        s.set(x, 4, 10, 'dark_oak_stairs[facing=north,half=bottom,shape=straight]'); s.set(x, 4, 9, 'dark_oak_planks')
    s.set(0, 3, 9, 'lantern[hanging=true]')
    # 2층 발코니 (y5 바깥 1칸 + 난간 + 기둥 랜턴)
    for x in range(-11, 12):
        for z in range(-5, 12):
            if x in (-11, 11) or z in (-5, 11):
                if -11 <= x <= 11 and -5 <= z <= 11:
                    s.set(x, 5, z, 'spruce_slab[type=top]')
                    s.set(x, 6, z, 'spruce_fence')
            elif x in (-10, 10) or z in (-4, 10):
                s.set(x, 5, z, 'spruce_planks')
    for (x, z) in ((-11, -5), (11, -5), (-11, 11), (11, 11), (-11, 3), (11, 3), (0, 11)):
        for y in range(0, 5): s.set(x, y, z, 'stripped_spruce_log[axis=y]')
        s.set(x, 6, z, 'spruce_fence'); s.set(x, 7, z, 'lantern[hanging=false]')
    s.set(0, 6, 11, None); s.set(0, 7, 11, None)
    # 3층 지붕: 녹색 언덕 테라스 (식물 대신 초록·연두 테라코타) + 이끼 조약돌 계단 가장자리
    G = lambda: 'green_terracotta' if r.random() > 0.2 else 'lime_terracotta'
    for y, sh in ((10, 0), (11, 2), (12, 4)):
        xa, xb, za, zb = -10 + sh, 10 - sh, -4 + sh, 10 - sh
        for x in range(xa, xb + 1):
            for z in range(za, zb + 1):
                cxx = min(max(x, xa + 2), xb - 2); czz = min(max(z, za + 2), zb - 2)
                if math.hypot(x - cxx, z - czz) > 2.3 + (r.random() * 0.4 if y == 10 else 0): continue
                if math.hypot(x, z) <= 4.4: continue
                s.set(x, y, z, G())
    stair_edges(s, lambda st: 'green_terracotta' in st or 'lime_terracotta' in st, 'mossy_cobblestone', ys=(10, 11, 12))
    for (x, y, z), st in list(s.b.items()):   # 가장자리 아래로 늘어진 흙덩이
        if y == 10 and 'terracotta' in st and (abs(x) >= 9 or z <= -3 or z >= 9) and r.random() < 0.3 and s.get(x, 9, z) is None:
            s.set(x, 9, z, 'green_terracotta')
    # 지붕창 (남쪽, 녹색 언덕 위)
    box(s, r, -2, 7, 5, 4, 11, 3, frame='dark_oak', infill=('calcite',), post_step=4, found=False, windows=False, recess=False)
    s.set(0, 12, 10, 'glass_pane'); s.set(0, 13, 10, 'glass_pane')
    s.set(-1, 12, 11, 'spruce_trapdoor[facing=south,half=bottom,open=true]'); s.set(1, 12, 11, 'spruce_trapdoor[facing=south,half=bottom,open=true]')
    s.set(-1, 13, 11, 'spruce_trapdoor[facing=south,half=bottom,open=true]'); s.set(1, 13, 11, 'spruce_trapdoor[facing=south,half=bottom,open=true]')
    roof(s, r, -2, 2, 7, 10, 14, mats=('dark_oak',), eave='dark_oak', gable_fill=('calcite',), axis='z', over=1)
    # 위 몸통 창 + 작은 발코니
    for y in (16, 17): s.set(0, y, 4, 'glass_pane'); s.set(-1, y, 5, 'spruce_trapdoor[facing=south,half=bottom,open=true]'); s.set(1, y, 5, 'spruce_trapdoor[facing=south,half=bottom,open=true]')
    for x in (-1, 0, 1):
        s.set(x, 15, 5, 'spruce_slab[type=top]'); s.set(x, 15, 6, 'spruce_slab[type=top]'); s.set(x, 16, 6, 'spruce_fence')
        s.set(x, 14, 5, 'spruce_stairs[facing=north,half=top,shape=straight]')
    s.set(0, 18, 5, 'spruce_trapdoor[facing=south,half=top,open=false]')
    # 동쪽 날개 + 벽돌 굴뚝
    box(s, r, 11, 0, 6, 8, 1, 4, frame='spruce', infill=('calcite',), post_step=5, win_sides=('e', 's'))
    roof(s, r, 11, 16, 0, 7, 5, mats=('red_sandstone', 'red_sandstone', 'granite'), eave='dark_oak', gable_fill=('calcite', 'stripped_spruce_log[axis=y]'), axis='z')
    for y in range(1, 12): s.set(17, y, 3, r.choice(['bricks', 'bricks', 'stone_bricks']))
    s.set(17, 12, 3, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    # 바깥 계단 (서쪽, 땅 → 2층 발코니), 받침 기둥 + 난간
    run = [(-13, 1, 10, 'north'), (-13, 2, 9, 'north'), (-13, 3, 8, 'north'), (-13, 4, 7, 'north'), (-13, 5, 6, 'north')]
    for x, y, z, f in run:
        s.set(x, y, z, f'spruce_stairs[facing={f},half=bottom,shape=straight]')
        s.set(x, y - 1, z, 'spruce_stairs[facing=south,half=top,shape=straight]') if y > 1 else None
        s.set(x - 1, y, z, 'spruce_fence'); s.set(x - 1, y + 1, z, 'spruce_fence') if y in (2, 4) else None
    for z in (5, 4, 3): s.set(-13, 5, z, 'spruce_slab[type=top]'); s.set(-14, 6, z, 'spruce_fence'); s.set(-12, 5, z, 'spruce_slab[type=top]')
    s.set(-14, 5, 10, 'stripped_spruce_log[axis=y]'); s.set(-14, 6, 10, 'lantern[hanging=false]')
    for y in range(0, 5): s.set(-14, y, 4, 'stripped_spruce_log[axis=y]')
    # 갓 (지름 24): 깔끔한 테라스, 가장자리 빨강 2칸, 윗면 주황 + 갈색 얼룩
    def capmat(x, y, z, d, k):
        rad = (12.0, 11.2, 9.8, 8.0, 5.8, 3.2)[k]
        if d > rad - 1.6 and k < 3: return 'red_mushroom_block'
        return 'orange_terracotta' if r.random() > 0.15 else r.choice(['brown_terracotta', 'red_mushroom_block'])
    cap(s, 0, 0, 24, (12.0, 11.2, 9.8, 8.0, 5.8, 3.2), capmat, lip=lambda x, z, d: 'red_mushroom_block', gill='light_gray_terracotta', nz=noise_fn(5, 0.35))
    # 흘러내림 (짧고 드물게)
    for a in range(0, 84):
        ang = a / 84 * 6.283
        x = round(math.cos(ang) * 11.9); z = round(math.sin(ang) * 11.9)
        n = r.choice([0, 0, 0, 0, 1, 1, 2])
        for k in range(1, n + 1): s.set(x, 23 - k, z, 'red_mushroom_block')
    # 갓 밑 사슬 랜턴 6
    for i in range(6):
        a = i / 6 * 6.283 + 0.5; x = round(math.cos(a) * 8); z = round(math.sin(a) * 8)
        L = 2 + i % 3
        for k in range(1, L + 1): s.set(x, 23 - k, z, 'chain')
        s.set(x, 22 - L, z, 'lantern[hanging=true]')
    # 갓 위 작은 버섯 3 (높이·크기 다르게)
    def top_y(mx, mz): return max(y for (x, y, z) in s.b if x == mx and z == mz)
    for (mx, mz, h, big, m) in ((-4, -3, 3, True, 'brown_mushroom_block'), (3, -6, 2, False, 'red_mushroom_block'), (6, 2, 2, False, 'brown_mushroom_block')):
        t = top_y(mx, mz)
        for k in range(1, h + 1): s.set(mx, t + k, mz, 'mushroom_stem')
        R = 2 if big else 1
        for dx in range(-R, R + 1):
            for dz in range(-R, R + 1):
                if abs(dx) + abs(dz) <= R + (1 if big else 0): s.set(mx + dx, t + h + 1, mz + dz, m)
        if big:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1): s.set(mx + dx, t + h + 2, mz + dz, m)
    path(s, r, -1, 1, 12, 15)
    autoconnect(s); return s

# ═════════ B. 연금술 공방 (네라) ═════════
def alchemy():
    s = S('v2/alchemy_tower'); r = random.Random(22)
    nz = noise_fn(7, 0.9)
    SAND = ['sandstone', 'smooth_sandstone', 'cut_sandstone', 'sandstone']
    for y, rad in ((0, 6.0), (1, 5.0), (2, 4.1), (3, 3.4)):
        ring_fill(s, 0, 0, y, rad, lambda x, z, d: r.choice(SAND), noise=lambda x, z: nz(x, z) * (1 if y < 2 else 0.4))
    stair_edges(s, lambda st: 'sandstone' in st and 'stairs' not in st, 'sandstone', ys=(0, 1, 2))
    def cx_at(y): return 2.0 * (max(0, y - 3) / 18) ** 2
    for y in range(3, 22):
        rad = 2.7 - 1.0 * (y / 21)
        c = cx_at(y)
        low = y < 8
        ring_fill(s, c, 0, y, rad, lambda x, z, d: ('smooth_sandstone' if low and r.random() < 0.35 else 'mushroom_stem' if r.random() > 0.18 else 'calcite'), r_in=rad - 1.2)
    # 줄기 혹 (울퉁불퉁)
    for (y, a) in ((6, 0.5), (11, 2.6), (15, 4.4), (18, 1.2)):
        c = cx_at(y); rad = 2.7 - y / 21 + 0.8
        x, z = round(c + math.cos(a) * rad), round(math.sin(a) * rad)
        s.set(x, y, z, 'mushroom_stem'); s.set(x, y + 1, z, 'mushroom_stem')
    # 문 + 아치
    for y in (1, 2): s.set(0, y, 2, None); s.set(0, y, 1, None)
    door(s, 0, 1, 2, wood='spruce')
    s.set(-1, 3, 3, 'sandstone_stairs[facing=east,half=top,shape=straight]'); s.set(1, 3, 3, 'sandstone_stairs[facing=west,half=top,shape=straight]'); s.set(0, 3, 3, 'cut_sandstone_slab[type=top]')
    for x in (-1, 1): s.set(x, 1, 3, 'sandstone_wall'); s.set(x, 2, 3, 'lantern[hanging=false]')
    # 줄기 창 (덧문)
    for (y, x, z, f) in ((9, 0, 2, 'south'), (14, 2, 2, 'south'), (18, 3, -2, 'north')):
        s.set(x, y, z, 'glass_pane'); s.set(x, y + 1, z, 'glass_pane')
        dz = 1 if f == 'south' else -1
        s.set(x - 1, y, z + dz, f'spruce_trapdoor[facing={f},half=bottom,open=true]'); s.set(x + 1, y, z + dz, f'spruce_trapdoor[facing={f},half=bottom,open=true]')
        s.set(x, y - 1, z + dz, f'spruce_trapdoor[facing={f},half=top,open=false]')
    # 갓: 날렵한 원뿔 (가장자리 지름 15, 12단)
    radii = [7.6, 7.0, 6.0, 5.3, 4.7, 4.1, 3.6, 3.0, 2.5, 1.9, 1.3, 0.7]
    cn = noise_fn(9, 0.4)
    cap(s, cx_at(21), 0, 22, radii, lambda x, y, z, d, k: 'red_mushroom_block', lip=lambda x, z, d: 'red_mushroom_block', gill='mushroom_stem', nz=cn,
        lean=lambda k: 0.12 * max(0, k - 4))
    # 빛 덩어리 8개 (2칸씩)
    surf = [p for p, st in s.b.items() if 'red_mushroom' in st and p[1] >= 23 and s.get(p[0], p[1] + 1, p[2]) is None]
    r.shuffle(surf)
    for (x, y, z) in surf[:8]:
        s.set(x, y, z, 'shroomlight')
        nb = [(x + 1, y, z), (x, y, z + 1), (x - 1, y, z), (x, y, z - 1)]
        for q in nb:
            if s.get(*q) and 'red_mushroom' in s.get(*q): s.set(*q, 'shroomlight'); break
    # 갓 밑 사슬 + 랜턴 6
    for i in range(6):
        ang = i / 6 * 6.283 + 0.3; x = round(2 + math.cos(ang) * 5.6); z = round(math.sin(ang) * 5.6)
        L = (2, 4, 3, 5, 2, 3)[i]
        for k in range(1, L + 1): s.set(x, 21 - k, z, 'chain')
        s.set(x, 20 - L, z, 'lantern[hanging=true]')
    # 서쪽 오두막: 기둥 + 대각 버팀, 1층 5×5, 2층 돌출 6×7, 가파른 박공
    for (x, z) in ((-8, -2), (-4, -2), (-8, 2), (-4, 2)):
        for y in range(1, 5): s.set(x, y, z, 'stripped_dark_oak_log[axis=y]')
    for z in (-2, 2):
        s.set(-7, 3, z, 'dark_oak_stairs[facing=west,half=top,shape=straight]'); s.set(-5, 3, z, 'dark_oak_stairs[facing=east,half=top,shape=straight]')
    for x in range(-8, -3):
        for z in range(-2, 3): s.set(x, 4, z, 'dark_oak_planks')
    box(s, r, -8, -2, 5, 5, 5, 4, frame='dark_oak', infill=('spruce_planks',), post_step=4, found=False, recess=False, windows=False)
    for x in range(-9, -2):
        for z in range(-3, 4): s.set(x, 9, z, 'dark_oak_planks') if s.get(x, 9, z) is None or 'spruce_planks' in s.get(x, 9, z) else None
    brackets(s, [(-9, 8, z, 'east') for z in (-2, 0, 2)] + [(x, 8, -3, 'south') for x in (-8, -6, -4)] + [(x, 8, 3, 'north') for x in (-8, -6, -4)], 'dark_oak')
    box(s, r, -9, -3, 7, 7, 10, 4, frame='dark_oak', infill=('spruce_planks', 'spruce_planks', 'calcite'), post_step=3, found=False, recess=True, windows=False)
    for (y, z) in ((6, 0), (7, 0)): s.set(-8, y, z, 'glass_pane')
    s.set(-9, 5, 0, 'spruce_trapdoor[facing=west,half=top,open=false]')
    # 둥근 창 (서쪽 2층): 2×2 유리 + 다락문 원형 테두리
    for (y, z) in ((11, 0), (11, -1), (12, 0), (12, -1)): s.set(-8, y, z, 'glass_pane')
    for z in (0, -1): s.set(-9, 13, z, 'dark_oak_trapdoor[facing=west,half=bottom,open=false]'); s.set(-9, 10, z, 'dark_oak_trapdoor[facing=west,half=top,open=false]')
    for y in (11, 12): s.set(-9, y, 1, 'dark_oak_trapdoor[facing=south,half=bottom,open=true]'); s.set(-9, y, -2, 'dark_oak_trapdoor[facing=north,half=bottom,open=true]')
    for (y, x) in ((11, -6), (12, -6)): s.set(x, y, 4, 'glass_pane')
    roof(s, r, -9, -3, -3, 3, 14, mats=('spruce', 'spruce', 'dark_oak'), eave='dark_oak', gable_fill=('calcite', 'stripped_dark_oak_log[axis=y]'), axis='z', over=1)
    for y in range(5, 20): s.set(-7, y, -5, r.choice(['cobblestone', 'cobblestone', 'mossy_cobblestone', 'stone_bricks']))
    s.set(-7, 20, -5, 'cobblestone_wall'); s.set(-7, 21, -5, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    s.set(-6, 6, -5, 'cobblestone_wall') if False else None
    # 오두막 ↔ 줄기 연결 다리
    for x in (-3, -2, -1): s.set(x, 9, 0, 'dark_oak_planks'); s.set(x, 10, -1, 'spruce_fence'); s.set(x, 10, 1, 'spruce_fence')
    # 발코니 2개 (받침 계단 + 난간 + 랜턴)
    for (y, cx, cz, dx, dz, f) in ((15, 4, 0, 1, 0, 'west'), (7, 1, 3, 0, 1, 'north')):
        for a in range(-1, 2):
            for b in range(0, 3):
                x = cx + (b * dx if dx else a); z = cz + (b * dz if dz else a)
                s.set(x, y, z, 'spruce_slab[type=top]')
                if b == 2 or abs(a) == 1: s.set(x, y + 1, z, 'spruce_fence')
            x = cx + (2 * dx if dx else a); z = cz + (2 * dz if dz else a)
        for b in range(0, 2):
            x = cx + b * dx; z = cz + b * dz
            s.set(x, y - 1, z, f'spruce_stairs[facing={f},half=top,shape=straight]')
        ex, ez = cx + 2 * dx + (1 if dz else 0), cz + 2 * dz + (1 if dx else 0)
        s.set(ex, y + 2, ez, 'lantern[hanging=false]')
    path(s, r, -1, 1, 5, 8)
    autoconnect(s); return s

# ═════════ C. 상점 ═════════
def shop(name, capm, seed, spots, wing):
    s = S('v2/' + name); r = random.Random(seed)
    # 돌 받침 + 계단 테두리
    ring_fill(s, 0, 0, 0, 4.4, lambda x, z, d: r.choice(['stone_bricks', 'cobblestone', 'mossy_stone_bricks', 'andesite']))
    stair_edges(s, lambda st: False, 'stone')
    for x in range(-4, 5):
        for z in range(-4, 5):
            d = math.hypot(x, z)
            if 3.4 < d <= 4.4 and s.get(x, 1, z) is None:
                dx, dz = -x, -z
                f = ('east' if dx > 0 else 'west') if abs(dx) > abs(dz) else ('south' if dz > 0 else 'north')
                s.set(x, 1, z, f'stone_brick_stairs[facing={f},half=bottom,shape=straight]')
    # 줄기 (지름 5, 빈 속)
    for y in range(1, 11):
        ring_fill(s, 0, 0, y, 2.6, lambda x, z, d, y=y: ('mushroom_stem' if r.random() > 0.15 else 'calcite') if y > 1 else 'mushroom_stem', r_in=1.5)
    for (x, z) in ((-2, 0), (2, 0), (0, -2), (0, 2)):   # 테 두른 띠
        pass
    for x in range(-3, 4):
        for z in range(-3, 4):
            if 2.5 < math.hypot(x, z) <= 3.2: s.set(x, 5, z, 'stripped_spruce_log[axis=%s]' % ('x' if abs(z) > abs(x) else 'z'))
    # 문 + 계단 차양
    door(s, 0, 1, 2, wood='spruce')
    s.set(0, 3, 2, 'spruce_planks')
    for x in (-1, 0, 1): s.set(x, 3, 3, 'spruce_stairs[facing=north,half=bottom,shape=straight]')
    for x in (-1, 1): s.set(x, 2, 3, 'spruce_stairs[facing=north,half=top,shape=straight]')
    # 창 + 덧문
    for (x, z, f) in ((-2, 0, 'west'), (2, 0, 'east')):
        s.set(x, 3, z, 'glass_pane'); s.set(x, 4, z, 'glass_pane')
        dx = -1 if f == 'west' else 1
        s.set(x + dx, 3, z - 1, f'spruce_trapdoor[facing={f},half=bottom,open=true]') if False else None
        s.set(x + dx, 2, z, f'spruce_trapdoor[facing={f},half=top,open=false]')
    for y in (7, 8): s.set(0, y, 2, 'glass_pane')
    s.set(0, 6, 3, 'spruce_trapdoor[facing=south,half=top,open=false]')
    # 갓
    radii = (4.9, 4.6, 3.9, 3.0, 2.0, 0.9)
    cap(s, 0, 0, 11, radii, lambda x, y, z, d, k: capm, lip=lambda x, z, d: capm, gill='mushroom_stem', nz=noise_fn(seed, 0.3))
    surf = [p for p, st in s.b.items() if capm in st and p[1] >= 12 and s.get(p[0], p[1] + 1, p[2]) is None]; r.shuffle(surf)
    for p in surf[:spots[1]]: s.set(*p, spots[0])
    # 가판대: 기둥 + 경사 차양 + 진열대
    for x in (2, 6):
        for y in (1, 2, 3): s.set(x, y, 5, 'spruce_fence')
    for x in range(2, 7):
        s.set(x, 4, 5, 'spruce_stairs[facing=north,half=bottom,shape=straight]')
        s.set(x, 4, 4, 'spruce_planks' if x > 2 else 'spruce_stairs[facing=north,half=bottom,shape=straight]')
        s.set(x, 5, 3, 'spruce_stairs[facing=north,half=bottom,shape=straight]') if x > 2 else None
        s.set(x, 3, 6, f'{"red" if "red" in capm else "brown"}_wool' if x % 2 == 0 else 'white_wool')
    for x in range(3, 6): s.set(x, 1, 5, 'spruce_slab[type=top]'); s.set(x, 2, 5, 'barrel[facing=up,open=true]' if x == 4 else 'spruce_trapdoor[facing=north,half=bottom,open=false]')
    s.set(4, 3, 4, 'lantern[hanging=true]')
    if wing:
        # 창고 곁채 (서쪽)
        box(s, r, -7, -2, 4, 4, 1, 3, frame='spruce', infill=('spruce_planks',), post_step=3, found=True, windows=False, recess=False)
        roof(s, r, -7, -4, -2, 1, 4, mats=('dark_oak',), eave='dark_oak', gable_fill=('spruce_planks',), axis='x')
        for (x, z) in ((-6, 2), (-5, 2), (-6, 3)): s.set(x, 1, z, 'barrel[facing=up,open=false]')
        s.set(-6, 2, 2, 'barrel[facing=up,open=false]')
    else:
        for (x, z) in ((-3, 3), (-4, 3), (-3, 4)): s.set(x, 1, z, 'barrel[facing=up,open=false]')
        s.set(-3, 2, 3, 'barrel[facing=up,open=false]')
    path(s, r, -1, 1, 4, 7)
    autoconnect(s); return s

# ═════════ D. 경비 초소 입구 (카엘) ═════════
def gate():
    s = S('v2/guard_gate'); r = random.Random(44)
    STONE = ['stone_bricks', 'stone_bricks', 'cobblestone', 'mossy_stone_bricks', 'andesite']
    for tx in (-9, 4):
        x0, x1, z0, z1 = tx, tx + 4, 0, 4
        # 돌 기단 (y0~2) + 계단 치마
        for y in range(0, 3):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1) or y == 0: s.set(x, y, z, r.choice(STONE))
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                    f = 'east' if x == x0 - 1 else 'west' if x == x1 + 1 else 'south' if z == z0 - 1 else 'north'
                    s.set(x, 0, z, f'stone_brick_stairs[facing={f},half=bottom,shape=straight]')
        # 1층 목조 (y3~5), 모서리 기둥 튀어나옴
        for y in range(3, 6):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1): s.set(x, y, z, 'spruce_planks')
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            for y in range(1, 10): s.set(x, y, z, 'spruce_log[axis=y]')
        # 2층 돌출 (y6 바닥, y7~9 벽) + 받침
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                s.set(x, 6, z, 'stripped_dark_oak_log[axis=x]' if z in (z0 - 1, z1 + 1) else 'stripped_dark_oak_log[axis=z]' if x in (x0 - 1, x1 + 1) else 'spruce_planks')
                if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                    for y in (7, 8, 9): s.set(x, y, z, 'calcite' if (x, z) not in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)) else 'dark_oak_log[axis=y]')
        brackets(s, [(x, 5, z1 + 1, 'north') for x in (x0, x0 + 2, x1)] + [(x, 5, z0 - 1, 'south') for x in (x0, x0 + 2, x1)] +
                 [(x0 - 1, 5, z, 'east') for z in (z0 + 2,)] + [(x1 + 1, 5, z, 'west') for z in (z0 + 2,)], 'dark_oak')
        for x in range(x0 - 1, x1 + 2): s.set(x, 9, z0 - 1, 'dark_oak_log[axis=x]'); s.set(x, 9, z1 + 1, 'dark_oak_log[axis=x]')
        for z in range(z0 - 1, z1 + 2): s.set(x0 - 1, 9, z, 'dark_oak_log[axis=z]'); s.set(x1 + 1, 9, z, 'dark_oak_log[axis=z]')
        # 활 쏘는 구멍 (좁은 창) + 정면 창
        cxm = x0 + 2
        s.set(cxm, 4, z1, 'iron_bars'); s.set(cxm, 3, z1, 'iron_bars')
        s.set(cxm, 7, z1 + 1, 'glass_pane'); s.set(cxm, 8, z1 + 1, 'glass_pane')
        s.set(cxm - 1, 7, z1 + 2, 'spruce_trapdoor[facing=south,half=bottom,open=true]'); s.set(cxm + 1, 7, z1 + 2, 'spruce_trapdoor[facing=south,half=bottom,open=true]')
        s.set(cxm - 1, 8, z1 + 2, 'spruce_trapdoor[facing=south,half=bottom,open=true]'); s.set(cxm + 1, 8, z1 + 2, 'spruce_trapdoor[facing=south,half=bottom,open=true]')
        side_x = x0 - 1 if tx < 0 else x1 + 1
        s.set(side_x, 7, 2, 'glass_pane'); s.set(side_x, 8, 2, 'glass_pane')
        s.set(cxm, 4, z1 + 1, 'red_wall_banner[facing=south]')
        # 버섯 갓 지붕 (지름 9)
        for y in (10, 11): ring_fill(s, cxm, 2, y, 2.0, 'spruce_log[axis=y]')
        cap(s, cxm + 0.0, 2, 13, (4.7, 4.3, 3.5, 2.5, 1.3), lambda x, y, z, d, k: 'red_mushroom_block', lip=lambda x, z, d: 'red_mushroom_block', gill='mushroom_stem', nz=noise_fn(tx + 50, 0.25))
        surf = [p for p, st in s.b.items() if 'red_mushroom' in st and p[1] >= 14 and s.get(p[0], p[1] + 1, p[2]) is None and abs(p[0] - cxm) < 6]
        r.shuffle(surf)
        for p in surf[:4]: s.set(*p, 'mushroom_stem')
        s.set(cxm, 10, 2, 'lantern[hanging=true]') if False else None
    # 문 사이: 아치 + 통로 + 작은 지붕
    for x in range(-4, 4):
        for z in range(0, 5):
            s.set(x, 7, z, 'spruce_planks' if 0 < z < 4 else 'dark_oak_log[axis=x]')
    for x in range(-4, 4):
        s.set(x, 8, 0, 'spruce_fence'); s.set(x, 8, 4, 'spruce_fence')
    for z in (0, 4):
        s.set(-3, 6, z, 'dark_oak_stairs[facing=west,half=top,shape=straight]'); s.set(2, 6, z, 'dark_oak_stairs[facing=east,half=top,shape=straight]')
        s.set(-4, 6, z, 'dark_oak_planks'); s.set(3, 6, z, 'dark_oak_planks')
    for x in range(-2, 2):
        s.set(x, 6, 2, 'iron_bars')   # 내려진 쇠창살 끝
    roof(s, r, -3, 2, 1, 3, 10, mats=('dark_oak',), eave='spruce', gable_fill=('spruce_planks',), axis='x', over=1)
    for (x, z) in ((-4, 1), (3, 1), (-4, 3), (3, 3)):
        s.set(x, 8, z, 'spruce_fence'); s.set(x, 9, z, 'spruce_fence')
    for x in (-4, 3): s.set(x, 5, 5, 'lantern[hanging=true]'); s.set(x, 6, 5, 'spruce_fence')
    for z in range(-3, 9):
        for x in range(-3, 3): s.set(x, 0, z, r.choice(['gravel', 'coarse_dirt', 'cobblestone', 'dirt_path', 'dirt_path'])) if s.get(x, 0, z) is None else None
    autoconnect(s); return s

