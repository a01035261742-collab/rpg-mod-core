"""두 번째 마을 초안 (레퍼런스: 사진1 버섯 탑, 사진2 Shrumble Ville) — 식물 없음"""
import os, random, math
import schem
from schem import S, autoconnect, render, view
from lib2 import box, roof, door, path
from lib4 import stall
from sheet import sheet

schem.COL[:0] = [('orange_terracotta', (165, 85, 40)), ('light_gray_terracotta', (135, 105, 95)), ('gray_terracotta', (58, 42, 36)),
                 ('brown_terracotta', (78, 52, 36)), ('red_sandstone', (180, 95, 35)), ('smooth_sandstone', (222, 210, 160)),
                 ('sandstone', (215, 200, 150)), ('stripped_spruce', (115, 90, 55)), ('cut_sandstone', (218, 205, 155)), ('green_terracotta', (76, 83, 42)), ('lime_terracotta', (103, 117, 52)), ('white_terracotta', (210, 178, 161))]

def ring_fill(s, cx, cz, y, r_out, mat, r_in=-1, noise=None, rr=None):
    R = int(r_out) + 2
    for x in range(int(cx) - R, int(cx) + R + 1):
        for z in range(int(cz) - R, int(cz) + R + 1):
            d = math.hypot(x - cx, z - cz)
            ro = r_out + (noise(x, z) if noise else 0)
            if r_in < d <= ro:
                s.set(x, y, z, mat(x, z, d) if callable(mat) else mat)

def noise_fn(seed, amp):
    r = random.Random(seed); ph = [r.random() * 6.28 for _ in range(3)]
    def f(x, z, cx=0, cz=0):
        a = math.atan2(z, x)
        return amp * (math.sin(3 * a + ph[0]) * 0.5 + math.sin(5 * a + ph[1]) * 0.35 + math.sin(7 * a + ph[2]) * 0.15)
    return f

# ───────── A. 버섯 연구소 (모리스) — 사진2 ─────────
def lab():
    s = S('v2/mush_lab'); r = random.Random(11)
    nz = noise_fn(1, 1.0)
    # 뿌리 (지름 15까지)
    for y, rad in ((0, 7.5), (1, 6.2), (2, 5.4), (3, 5.0)):
        ring_fill(s, 0, 0, y, rad, lambda x, z, d: r.choice(['dark_oak_log[axis=y]', 'spruce_log[axis=y]', 'brown_terracotta']),
                  noise=lambda x, z: nz(x, z) * (1.4 if y < 2 else 0.4))
    # 몸통 (지름 9, 소용돌이 무늬)
    BARK = ['dark_oak_log[axis=y]', 'spruce_log[axis=y]', 'brown_terracotta', 'stripped_spruce_log[axis=y]']
    for y in range(4, 22):
        def bark(x, z, d, y=y):
            a = math.atan2(z, x)
            return BARK[int((a * 2 + y * 0.45) % 4)]
        ring_fill(s, 0, 0, y, 4.5, bark, r_in=3.3)
    # 감싸는 집: 1층 (y1~4) + 돌출 2층 (y5~8)
    box(s, r, -8, -2, 17, 11, 1, 4, frame='spruce', infill=('calcite',) * 6 + ('white_terracotta',), post_step=4, win_sides=('s', 'w', 'e'))
    for x in range(-9, 10):
        for z in range(-3, 10): s.set(x, 5, z, 'spruce_planks') if s.get(x, 5, z) is None else None
    box(s, r, -9, -3, 19, 13, 6, 4, frame='spruce', infill=('calcite',) * 6 + ('white_terracotta',), post_step=4, found=False, win_sides=('s', 'w', 'e'))
    # 몸통이 집 위로 뚫고 지나가게: 집 내부 몸통 복원
    for y in range(1, 10):
        ring_fill(s, 0, 0, y, 4.5, lambda x, z, d, y=y: BARK[int((math.atan2(z, x) * 2 + y * 0.45) % 4)], r_in=3.3)
    door(s, 0, 1, 9, wood='spruce', facing='north')
    # 2층 둘레 발코니 (y5 바닥 1칸 밖 + 난간)
    for x in range(-10, 11):
        for z in range(-4, 11):
            if (x in (-10, 10) or z in (-4, 10)):
                s.set(x, 5, z, 'spruce_slab[type=top]'); s.set(x, 6, z, 'spruce_fence')
    s.set(0, 6, 10, None); s.set(-10, 6, 6, None)
    # 1층 지붕 대신: 2층 위 녹색 띠 (이끼 대체 = 초록 테라코타), 아래로 흘러내림
    gz = noise_fn(3, 1.0)
    ring_fill(s, 0, 3, 10, 11, lambda x, z, d: 'green_terracotta' if r.random() > 0.15 else 'moss_block' if False else 'lime_terracotta',
              r_in=4.4, noise=lambda x, z: gz(x, z - 3))
    ring_fill(s, 0, 3, 11, 8.5, 'green_terracotta', r_in=4.4, noise=lambda x, z: gz(x, z - 3) * 0.6)
    for (x, y, z), st in list(s.b.items()):
        if y == 10 and 'terracotta' in st and r.random() < 0.25 and s.get(x, 9, z) is None:
            s.set(x, 9, z, 'green_terracotta')
    # 위쪽 몸통 창
    for y in (13, 14): s.set(0, y, 4, 'glass_pane'); s.set(-1, y, 4, 'spruce_trapdoor[facing=south,half=bottom,open=true]'); s.set(1, y, 4, 'spruce_trapdoor[facing=south,half=bottom,open=true]')
    s.set(0, 12, 5, 'spruce_trapdoor[facing=south,half=top,open=false]')
    # 동쪽 날개 (빨간 지붕)
    box(s, r, 9, 1, 6, 7, 1, 4, frame='spruce', infill=('calcite',), post_step=5, win_sides=('e', 's'))
    roof(s, r, 9, 14, 1, 7, 5, mats=('red_sandstone',), eave='spruce', gable_fill=('calcite',), axis='z')
    # 바깥 나선 계단 (서쪽, 1층 → 2층 발코니)
    steps = [(-11, 1, 8, 'east'), (-12, 2, 7, 'north'), (-12, 3, 6, 'north'), (-12, 4, 5, 'north'), (-11, 5, 5, 'east')]
    for x, y, z, f in steps:
        s.set(x, y, z, f'spruce_stairs[facing={f},half=bottom,shape=straight]')
        for yy in range(0, y): s.set(x, yy, z, 'stripped_spruce_log[axis=y]') if yy == y - 1 else s.set(x, yy, z, 'spruce_fence')
    s.set(-11, 0, 9, 'spruce_slab[type=bottom]')
    # 갓 (지름 23): 아랫면 회색 주름, 가장자리 빨강 흘러내림, 윗면 주황
    base = 22
    cn = noise_fn(5, 0.8)
    ring_fill(s, 0, 0, base, 11.5, lambda x, z, d: 'light_gray_terracotta' if int(math.atan2(z, x) * 9) % 2 else 'gray_terracotta', r_in=4.4)
    for k, rad in enumerate((11.9, 11.5, 10.6, 9.3, 7.6, 5.5, 3.0)):
        y = base + 1 + k
        def capm(x, z, d, k=k, rad=rad):
            if k >= 3 and d < rad - 2.5: return 'orange_terracotta' if r.random() > 0.12 else 'red_mushroom_block'
            if k >= 4: return 'orange_terracotta' if r.random() > 0.4 else 'red_mushroom_block'
            return 'red_mushroom_block'
        ring_fill(s, 0, 0, y, rad, capm, r_in=(rad - 2.2 if k < 6 else -1), noise=lambda x, z: cn(x, z))
    # 가장자리 흘러내림 (1~3칸)
    for a in range(0, 72):
        ang = a / 72 * 6.283
        x = round(math.cos(ang) * 11.3); z = round(math.sin(ang) * 11.3)
        n = r.choice([0, 0, 0, 1, 1, 2, 3])
        s.set(x, base, z, 'red_mushroom_block')
        for k in range(1, n + 1): s.set(x, base - k, z, 'red_mushroom_block')
    # 갓 위 작은 버섯 3개
    for (mx, mz) in ((-4, -3), (3, -5), (5, 2)):
        top = max(y for (x, y, z) in s.b if x == mx and z == mz)
        s.set(mx, top + 1, mz, 'mushroom_stem'); s.set(mx, top + 2, mz, 'mushroom_stem')
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1): s.set(mx + dx, top + 3, mz + dz, 'brown_mushroom_block')
    # 조명 8
    for (x, y, z) in ((-5, 4, 10), (5, 4, 10), (-10, 4, 0), (10, 9, -4), (-10, 9, 10), (10, 9, 10), (-11, 6, 8), (12, 4, 8), (0, 19, 6), (6, 21, 0)):
        s.set(x, y, z, 'lantern[hanging=true]')
    path(s, r, -1, 1, 11, 14)
    autoconnect(s); return s

# ───────── B. 연금술 공방 (네라) — 사진1 ─────────
def alchemy():
    s = S('v2/alchemy_tower'); r = random.Random(22)
    nz = noise_fn(7, 1.1)
    SAND = ['sandstone', 'smooth_sandstone', 'cut_sandstone', 'sandstone']
    for y, rad in ((0, 5.5), (1, 4.6), (2, 3.8), (3, 3.2)):
        ring_fill(s, 0, 0, y, rad, lambda x, z, d: r.choice(SAND), noise=lambda x, z: nz(x, z) * (1 if y < 2 else 0.4))
    def cx_at(y): return 2.0 * (y / 21) ** 2
    for y in range(3, 22):
        rad = 2.6 - 1.0 * (y / 21)
        c = cx_at(y)
        ring_fill(s, c, 0, y, rad, lambda x, z, d: 'mushroom_stem' if r.random() > 0.2 else 'calcite', r_in=rad - 1.2)
    for y in range(0, 3): s.fill(-1, y + 1, 1, 1, y + 1, 1, 'air') if False else None
    door(s, 0, 1, 3, wood='spruce'); s.set(0, 1, 2, 'air'); s.set(0, 2, 2, 'air')
    # 갓: 계단식 원뿔 (가장자리 지름 15, 높이 9), 울퉁불퉁
    cn = noise_fn(9, 0.7); lights = 0
    radii = [7.5, 6.3, 5.4, 4.8, 4.3, 3.9, 3.4, 2.9, 2.4, 1.8, 1.2, 0.6]
    for k, rad in enumerate(radii):
        y = 22 + k; c = cx_at(21) + (0.6 if k > 4 else 0) + (0.6 if k > 8 else 0)
        nxt = radii[k + 1] if k + 1 < len(radii) else -1
        ring_fill(s, c, 0, y, rad, 'red_mushroom_block', r_in=min(nxt - 0.2, rad - 1.6) if k > 0 else -1, noise=lambda x, z: cn(x, z) * (1 - k / 10))
    pos = [p for p, st in s.b.items() if 'red_mushroom' in st and p[1] >= 23]
    r.shuffle(pos)
    for p in pos[:12]: s.set(*p, 'shroomlight')
    # 처진 챙 (y21)
    ring_fill(s, cx_at(21), 0, 21, 7.9, 'red_mushroom_block', r_in=6.9, noise=lambda x, z: cn(x, z))
    for (x, y, z), st in list(s.b.items()):
        if y == 22 and 'red_mushroom' in st and s.get(x, 21, z) is None and math.hypot(x - 2, z) < 6: s.set(x, 22, z, 'shroomlight' if r.random() < 0.08 else st)
    # 갓 아래 사슬 + 랜턴 6
    for i in range(6):
        ang = i / 6 * 6.283 + 0.3; x = round(2 + math.cos(ang) * 5.5); z = round(math.sin(ang) * 5.5)
        L = r.choice([2, 3, 4])
        for k in range(1, L + 1): s.set(x, 22 - k, z, 'chain')
        s.set(x, 21 - L, z, 'lantern[hanging=true]')
    # 서쪽 오두막 (5×5, 2층, y4~12)
    for (x, z) in ((-7, -2), (-3, -2), (-7, 2), (-3, 2)):
        for y in range(1, 4): s.set(x, y, z, 'stripped_dark_oak_log[axis=y]')
    for x in range(-7, -2):
        for z in range(-2, 3): s.set(x, 3, z, 'dark_oak_planks')
    for i, (x, y) in enumerate(((-6, 2), (-5, 2), (-4, 2))): pass
    box(s, r, -7, -2, 5, 5, 4, 4, frame='dark_oak', infill=('spruce_planks',), post_step=4, found=False, recess=False, windows=False)
    box(s, r, -8, -3, 6, 7, 8, 4, frame='dark_oak', infill=('spruce_planks', 'spruce_planks', 'stripped_spruce_log[axis=x]'), post_step=5, found=False, recess=False, windows=False)
    for x in range(-8, -2):
        for z in range(-3, 4): s.set(x, 8, z, 'dark_oak_planks') if s.get(x, 8, z) in (None, 'minecraft:air') else None
    # 둥근 창 (서쪽 면): 유리 + 다락문 테두리
    for (y, z) in ((10, 0), (10, 1), (9, 0), (9, 1)): s.set(-8, y, z - 0, 'glass_pane')
    for (y, z, f) in ((11, 0, 'west'), (11, 1, 'west'), (8, 0, 'west'), (8, 1, 'west')): s.set(-9, y, z, f'dark_oak_trapdoor[facing={f},half={"bottom" if y == 11 else "top"},open=false]')
    for y in (9, 10): s.set(-9, y, -1, 'dark_oak_trapdoor[facing=north,half=bottom,open=true]'); s.set(-9, y, 2, 'dark_oak_trapdoor[facing=south,half=bottom,open=true]')
    for (y, z) in ((5, 0), (6, 0)): s.set(-7, y, z, 'glass_pane')
    door(s, -3, 4, 0, wood='dark_oak', facing='east') if False else None
    roof(s, r, -8, -3, -3, 3, 12, mats=('spruce', 'spruce', 'dark_oak'), eave='dark_oak', gable_fill=('spruce_planks',), axis='z')
    for y in range(4, 18): s.set(-6, y, -4, r.choice(['cobblestone', 'cobblestone', 'mossy_cobblestone', 'stone']))
    s.set(-6, 18, -4, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    # 발코니 2개 (줄기에서 튀어나온 반블록 판 + 난간)
    for (y, cx, cz, dx, dz) in ((15, 4, 0, 1, 0), (8, 1, 3, 0, 1)):
        for a in range(-1, 2):
            for b in range(0, 3):
                x = cx + (b * dx if dx else a); z = cz + (b * dz if dz else a)
                s.set(x, y, z, 'spruce_slab[type=top]')
                if b == 2 or abs(a) == 1: s.set(x, y + 1, z, 'spruce_fence')
        s.set(cx + (dx * 0), y - 1, cz, 'spruce_stairs[facing=west,half=top,shape=straight]' if dx else 'spruce_stairs[facing=north,half=top,shape=straight]')
    s.set(0, 3, 4, 'lantern[hanging=false]') if False else None
    path(s, r, -1, 1, 4, 8)
    autoconnect(s); return s

# ───────── C. 상점 (작은 버섯집) ─────────
def shop(name, cap, seed):
    s = S('v2/' + name); r = random.Random(seed)
    nz = noise_fn(seed, 0.8)
    for y, rad in ((0, 4.2), (1, 3.4)):
        ring_fill(s, 0, 0, y, rad, lambda x, z, d: r.choice(['sandstone', 'smooth_sandstone', 'cut_sandstone']), noise=lambda x, z: nz(x, z) * 0.8)
    for y in range(1, 9):
        ring_fill(s, 0, 0, y, 2.6, lambda x, z, d: 'mushroom_stem' if r.random() > 0.15 else 'calcite', r_in=1.4)
    for y in range(1, 9):
        for x in (-1, 0, 1):
            for z in (-1, 0, 1): s.set(x, y, z, 'air') if math.hypot(x, z) < 1.5 else None
    door(s, 0, 1, 2, wood='spruce')
    for y in (3, 4): s.set(-2, y, 0, 'glass_pane'); s.set(2, y, 0, 'glass_pane')
    radii = [4.6, 4.0, 3.2, 2.4, 1.5, 0.6]
    for k, rad in enumerate(radii):
        nxt = radii[k + 1] if k + 1 < len(radii) else -1
        ring_fill(s, 0, 0, 9 + k, rad, lambda x, z, d: cap if r.random() > 0.1 else 'mushroom_stem', r_in=min(nxt - 0.2, rad - 1.6) if k else -1, noise=lambda x, z: nz(x, z) * 0.6)
    ring_fill(s, 0, 0, 8, 5.0, cap, r_in=4.1, noise=lambda x, z: nz(x, z) * 0.6)
    pos = [p for p, st in s.b.items() if cap in st and p[1] >= 10]; r.shuffle(pos)
    for p in pos[:4]: s.set(*p, 'shroomlight')
    # 차양 + 진열대
    for x in range(-2, 3):
        s.set(x, 4, 3, 'spruce_trapdoor[facing=south,half=top,open=false]')
        s.set(x, 4, 4, 'spruce_slab[type=bottom]') if False else None
    stall(s, 3, 3, color='red' if 'red' in cap else 'brown', w=3)
    s.set(-3, 1, 3, 'barrel[facing=up,open=false]'); s.set(-4, 1, 3, 'barrel[facing=up,open=false]'); s.set(-3, 2, 3, 'barrel[facing=up,open=false]')
    s.set(0, 3, 3, 'lantern[hanging=true]')
    path(s, r, -1, 1, 3, 6)
    autoconnect(s); return s

# ───────── D. 경비 초소 입구 (카엘) ─────────
def gate():
    s = S('v2/guard_gate'); r = random.Random(44)
    for tx in (-7, 4):
        for y in range(0, 10):
            for x in range(tx, tx + 4):
                for z in range(0, 4):
                    edge = x in (tx, tx + 3) or z in (0, 3)
                    if not edge: s.set(x, y, z, 'spruce_planks' if y in (0, 5) else None); continue
                    corner = x in (tx, tx + 3) and z in (0, 3)
                    if y == 0: s.set(x, y, z, r.choice(['cobblestone', 'stone_bricks', 'mossy_cobblestone']))
                    else: s.set(x, y, z, 'spruce_log[axis=y]' if corner else ('stripped_spruce_log[axis=y]' if y in (4, 9) else 'spruce_planks'))
        for y in (2, 3, 7): s.set(tx + 1, y, 3, 'glass_pane'); s.set(tx + 2, y, 3, 'glass_pane')
        door(s, tx + 1 if tx > 0 else tx + 2, 1, 3, wood='spruce') if False else None
        # 버섯 갓 지붕 (지름 7)
        cx, cz = tx + 1.5, 1.5
        for k, rad in enumerate((3.9, 3.3, 2.4, 1.3)):
            ring_fill(s, cx, cz, 10 + k, rad, 'red_mushroom_block', r_in=rad - 1.5 if k < 3 else -1)
        ring_fill(s, cx, cz, 9, 4.2, 'red_mushroom_block', r_in=3.3)
        s.set(int(cx), 11, int(cz), 'shroomlight')
    # 문 (폭 5, 높이 6) 위 들보 + 통로
    for x in range(-3, 4):
        for z in range(0, 4):
            s.set(x, 7, z, 'dark_oak_log[axis=x]' if z in (0, 3) else 'spruce_planks')
            if z in (0, 3): s.set(x, 8, z, 'spruce_fence')
    for x in (-3, 3):
        for z in (0, 3): s.set(x, 6, z, 'spruce_stairs[facing=%s,half=top,shape=straight]' % ('west' if x < 0 else 'east'))
    for x in range(-2, 3): s.set(x, 6, 3, 'spruce_trapdoor[facing=south,half=top,open=false]') if x in (-2, 2) else None
    s.set(-3, 5, 4, 'lantern[hanging=false]') if False else None
    for x in (-4, 4): s.set(x if x < 0 else x - 1, 6, 4, 'lantern[hanging=true]') if False else None
    s.set(-4, 3, 4, 'wall_torch[facing=south]') if False else None
    for x in (-3, 3): s.set(x, 7, 4, 'spruce_trapdoor[facing=south,half=top,open=false]'); s.set(x, 6, 4, 'lantern[hanging=true]')
    for z in range(-2, 7):
        for x in range(-2, 3): s.set(x, 0, z, r.choice(['gravel', 'coarse_dirt', 'cobblestone', 'dirt_path']))
    autoconnect(s); return s

items = []
os.makedirs('v2d', exist_ok=True)
for s, label in ((lab(), 'A. 버섯 연구소 (모리스) 25×25×30'), (alchemy(), 'B. 연금술 공방 (네라) 15×15×32'),
                 (shop('shop_red', 'red_mushroom_block', 5), 'C1. 상점 (빨강 갓)'), (shop('shop_brown', 'brown_mushroom_block', 6), 'C2. 상점 (갈색 갓)'),
                 (gate(), 'D. 경비 초소 입구 (카엘)')):
    p = 'v2d/' + s.name.split('/')[1]
    s.save(p + '.schem'); render(view(s, 0), p + '.png', scale=14); items.append((p + '.png', label)); render(view(s, 2), p + '_back.png', scale=14); items.append((p + '_back.png', label + ' (뒤)'))
    x0, y0, z0, x1, y1, z1 = s.bounds(); print(s.name, x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
print(sheet(items[:4], 'village2_draft_big.png', cols=4))
print(sheet(items[4:], 'village2_draft_small.png', cols=3))
