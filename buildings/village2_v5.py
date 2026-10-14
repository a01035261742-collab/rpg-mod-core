"""두 번째 마을 v5 — 상점 2곳 + 경비 초소를 새로 (디테일 갓 공통 생성기). 식물 없음"""
import os, random, math
import schem
from schem import S, autoconnect, render2, view2
from sheet import sheet
from village2_draft3 import lab3, alchemy3, put, lamp, sign_post, crates, brazier, top
from village2_lib_v2 import ring_fill, brackets, DIRS
from lib2 import door, roof, box
from lib3 import cart

schem.COL[:0] = [('birch_wood', (215, 210, 195)), ('smooth_quartz', (235, 230, 222)), ('decorated_pot', (150, 85, 60)),
                 ('red_nether_brick', (75, 15, 18)), ('tuff', (108, 109, 102)), ('red_terracotta', (143, 61, 46))]

LAB_PROFILE = [12.0, 12.8, 13.1, 12.9, 12.3, 11.3, 9.9, 7.9, 5.3, 2.3]

def dcap(s, cx, cz, y0, R, kind, seed, H=None, drip=(1, 3), spots=8, glow=0, gill=('mushroom_stem', 'white_terracotta'), chimney=None):
    """디테일 갓: 볼록한 어깨 + 물결 테두리 + 색 그라데이션 + 흘러내림 + 오목한 주름 아랫면 + 점"""
    r = random.Random(seed)
    SMALL = [1.0, 0.95, 0.82, 0.62, 0.36]
    H = len(SMALL)
    prof = [f * R for f in SMALL]
    ph = [r.random() * 6.28 for _ in range(3)]
    amp = min(0.45, R * 0.06)
    def nz(a): return amp * (math.sin(3 * a + ph[0]) + 0.6 * math.sin(5 * a + ph[1]) + 0.3 * math.sin(9 * a + ph[2])) / 1.9
    def under(d):
        return y0 if d > 0.80 * R else y0 + 1 if (d > 0.55 * R or R < 6) else y0 + 2
    cells = {}
    Ri = int(R) + 3
    for x in range(-Ri, Ri + 1):
        for z in range(-Ri, Ri + 1):
            d = math.hypot(x, z); a = math.atan2(z, x); n = nz(a)
            ty = None
            for k, rad in enumerate(prof):
                if rad + n >= d: ty = y0 + k
            if ty is None: continue
            uy = under(d)
            for y in range(uy, ty + 1): cells[(cx + x, y, cz + z)] = (d, a, y == ty, y == uy)
    def body(y, d, is_top):
        k = y - y0; t = k / (H - 1)
        if kind == 'red':
            if is_top and t > 0.75: return r.choices(['red_mushroom_block', 'red_terracotta', 'orange_terracotta'], [80, 12, 8])[0]
            q = r.random(); dark = 0.14 if k <= 1 else 0.03
            return 'red_nether_bricks' if q < dark else 'red_terracotta' if q < dark + 0.12 else 'red_mushroom_block'
        if kind == 'brown':
            if is_top and t > 0.6: return r.choices(['brown_mushroom_block', 'terracotta', 'stripped_spruce_wood' if False else 'packed_mud'], [70, 18, 12])[0]
            q = r.random(); dark = 0.18 if k <= 1 else 0.06
            return 'brown_terracotta' if q < dark else 'packed_mud' if q < dark + 0.1 else 'brown_mushroom_block'
    for (x, y, z), (d, a, is_top, is_bot) in cells.items():
        if is_bot and d < 0.86 * R and d > 1.2:
            st = gill[0] if int((a + 3.1416) * max(10, int(R * 2.2)) / 6.2832) % 2 else gill[1]
        else: st = body(y, d, is_top)
        s.set(x, y, z, st)
    # 점
    surf = [p for p, v in cells.items() if not v[3] and s.get(p[0], p[1] + 1, p[2]) is None and v[0] > 1.5]
    r.shuffle(surf)
    spot = 'mushroom_stem' if kind == 'red' else 'shroomlight'
    for (x, y, z) in surf[:spots]:
        s.set(x, y, z, spot)
        if kind == 'red' and r.random() < 0.45:
            q = r.choice([(x + 1, y, z), (x, y, z + 1), (x - 1, y, z), (x, y, z - 1)])
            if q in cells and s.get(q[0], q[1] + 1, q[2]) is None: s.set(*q, spot)
    for (x, y, z) in surf[spots:spots + glow]: s.set(x, y, z, 'shroomlight')
    # 흘러내림
    n = int(R * 9)
    for i in range(n):
        a = i / n * 6.2832; rad = prof[0] + nz(a) - 0.3
        x, z = cx + round(math.cos(a) * rad), cz + round(math.sin(a) * rad)
        if (x, y0, z) not in cells: continue
        L = r.randint(0, drip[1]) if r.random() < 0.75 else 0
        L = max(L, drip[0] if r.random() < 0.5 else 0)
        for k in range(1, L + 1):
            if s.get(x, y0 - k, z) is not None: break
            tip = k == L and L > 1
            if kind == 'red': s.set(x, y0 - k, z, 'red_nether_bricks' if tip else 'red_mushroom_block')
            else: s.set(x, y0 - k, z, 'brown_terracotta' if tip else 'brown_mushroom_block')
    # 아랫면 빛
    gl = [p for p, v in cells.items() if v[3] and 0.35 * R < v[0] < 0.75 * R]; r.shuffle(gl)
    for p in gl[:max(2, int(R / 2))]: s.set(*p, 'shroomlight')
    if chimney:
        hx, hz = chimney
        ty = max(y for (x, y, z) in s.b if x == cx + hx and z == cz + hz)
        for y in range(y0 - 2, ty + 3): s.set(cx + hx, y, cz + hz, r.choice(['cobblestone', 'stone_bricks', 'mossy_cobblestone']))
        s.set(cx + hx, ty + 3, cz + hz, 'cobblestone_wall'); s.set(cx + hx, ty + 4, cz + hz, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    return prof

def mini_mush(s, x, y, z, kind, size=1, h=2, lean=(0, 0)):
    m = 'red_mushroom_block' if kind == 'red' else 'brown_mushroom_block'
    for k in range(1, h + 1):
        if k == h and lean != (0, 0): x += lean[0]; z += lean[1]
        s.set(x, y + k - 1, z, 'mushroom_stem')
    yc = y + h
    for dx in range(-size, size + 1):
        for dz in range(-size, size + 1):
            if abs(dx) + abs(dz) <= size + (1 if size > 1 else 0): s.set(x + dx, yc, z + dz, m)
    if size >= 1:
        s.set(x, yc + 1, z, m)
        for dx, dz in ((size + 1, 0), (-size - 1, 0), (0, size + 1), (0, -size - 1)): s.set(x + dx, yc - 1, z + dz, m) if size >= 2 else None
        if kind == 'red': s.set(x + 1, yc, z - 1 if size else z, 'mushroom_stem')

def stem(s, r, y0, y1, prof, mats, cx=0, cz=0, hollow=True):
    """줄기: 높이별 반지름 프로필 (아래 넓고 위로 갈수록 갓에 붙으며 다시 넓어짐)"""
    for y in range(y0, y1 + 1):
        rad = prof(y)
        ring_fill(s, cx, cz, y, rad, lambda x, z, d: r.choices(mats[0], mats[1])[0], r_in=(rad - 1.15 if hollow else -1))

def root_flare(s, r, cx, cz, R, n, mat, stair):
    for i in range(n):
        a = i / n * 6.2832 + r.random() * 0.5
        for t, h in ((R + 0.3, 2), (R + 1.1, 1)):
            x, z = cx + round(math.cos(a) * t), cz + round(math.sin(a) * t)
            for y in range(1, h + 1): s.set(x, y, z, mat)
            dx, dz = cx - x, cz - z
            f = ('east' if dx > 0 else 'west') if abs(dx) >= abs(dz) else ('south' if dz > 0 else 'north')
            if s.get(x, h + 1, z) is None: s.set(x, h + 1, z, f'{stair}[facing={f},half=bottom,shape=straight]')

def plinth(s, r, cx, cz, R):
    ring_fill(s, cx, cz, 0, R, lambda x, z, d: r.choice(['cobblestone', 'stone_bricks', 'mossy_cobblestone', 'andesite', 'gravel', 'coarse_dirt']))

# ═════════ C1. 상점 (빨강 갓, 잡화점) ═════════
def shop_red():
    s = S('v2/shop_red'); r = random.Random(501)
    plinth(s, r, 0, 1, 5.2)
    prof = lambda y: {1: 3.4, 2: 3.0, 3: 2.75}.get(y, 2.6 if y < 9 else 3.0)
    stem(s, r, 1, 10, prof, (['mushroom_stem', 'calcite', 'birch_wood', 'smooth_quartz'], [70, 14, 8, 8]))
    root_flare(s, r, 0, 0, 3.2, 5, 'mushroom_stem', 'smooth_quartz_stairs')
    # 정문 (남) + 아치
    for y in (1, 2, 3):
        for z in (1, 2, 3): s.set(0, y, z, None)
    door(s, 0, 1, 3, wood='spruce')
    s.set(-1, 3, 3, 'spruce_stairs[facing=east,half=top,shape=straight]'); s.set(1, 3, 3, 'spruce_stairs[facing=west,half=top,shape=straight]')
    s.set(0, 4, 3, 'spruce_slab[type=bottom]')
    for x in (-1, 1): s.set(x, 1, 4, 'spruce_fence'); s.set(x, 2, 4, 'lantern[hanging=false]') if x < 0 else s.set(x, 2, 4, 'spruce_fence')
    # 줄기 창 (2층 남·북·서) + 덧문 + 작은 발코니 (동)
    for (x, z, f) in ((0, 3, 'south'), (0, -3, 'north'), (-3, 0, 'west')):
        dx, dz = DIRS[f]
        for y in (6, 7): s.set(x, y, z, 'glass_pane')
        s.set(x, 5, z + dz if dz else z, f'spruce_trapdoor[facing={f},half=top,open=false]') if dz else s.set(x + dx, 5, z, f'spruce_trapdoor[facing={f},half=top,open=false]')
        side = [(x - 1, z + dz), (x + 1, z + dz)] if dz else [(x + dx, z - 1), (x + dx, z + 1)]
        for (sx, sz) in side:
            for y in (6, 7): s.set(sx, y, sz, f'spruce_trapdoor[facing={f},half=bottom,open=true]')
    for z in (-1, 0, 1):
        s.set(3, 6, z, 'spruce_slab[type=top]'); s.set(4, 6, z, 'spruce_slab[type=top]'); s.set(4, 7, z, 'spruce_fence')
        s.set(3, 5, z, 'spruce_stairs[facing=west,half=top,shape=straight]')
    s.set(3, 7, -1, 'spruce_fence'); s.set(3, 7, 1, 'spruce_fence')
    s.set(2, 7, 0, 'spruce_door[facing=east,half=lower,hinge=left,open=false,powered=false]'); s.set(2, 8, 0, 'spruce_door[facing=east,half=upper,hinge=left,open=false,powered=false]')
    # 진열 별채 (동남): 하프팀버 3×3 + 진열창 + 박공
    box(s, r, 2, 2, 4, 4, 1, 3, frame='spruce', infill=('calcite',), post_step=3, found=False, windows=False, recess=False)
    for y in (1, 2): s.set(3, y, 5, 'glass_pane'); s.set(4, y, 5, 'glass_pane')
    s.set(3, 0, 6, 'spruce_trapdoor[facing=south,half=top,open=false]'); s.set(4, 0, 6, 'spruce_trapdoor[facing=south,half=top,open=false]')
    for x in (3, 4): s.set(x, 1, 6, 'spruce_trapdoor[facing=south,half=top,open=false]')
    for y in (1, 2): s.set(5, y, 3, 'glass_pane')
    roof(s, r, 2, 5, 2, 5, 4, mats=('dark_oak',), eave='spruce', gable_fill=('calcite', 'stripped_spruce_log[axis=y]'), axis='z', over=1)
    s.set(6, 3, 5, 'spruce_fence'); s.set(7, 3, 5, 'spruce_fence'); s.set(7, 2, 5, 'spruce_hanging_sign[attached=false,rotation=0,waterlogged=false]')
    # 줄무늬 차양 + 진열대 (서남)
    for x in range(-4, 0):
        c = 'red_wool' if x % 2 == 0 else 'white_wool'
        s.set(x, 4, 3, c); s.set(x, 3, 4, c); s.set(x, 3, 5, f'{"red" if x % 2 == 0 else "white"}_carpet') if False else None
        s.set(x, 2, 5, 'spruce_slab[type=top]') if False else None
    for x in (-4, -1): s.set(x, 1, 5, 'spruce_fence'); s.set(x, 2, 5, 'spruce_fence')
    for x in range(-4, 0):
        s.set(x, 3, 5, 'red_carpet' if False else ('red_wool' if x % 2 == 0 else 'white_wool'))
    for x in (-3, -2): s.set(x, 1, 5, 'spruce_slab[type=top]')
    s.set(-3, 2, 5, 'decorated_pot[facing=south,waterlogged=false]'); s.set(-2, 2, 5, 'barrel[facing=up,open=true]')
    s.set(-2, 2, 4, 'lantern[hanging=true]') if False else None
    # 마당 소품: 상자·자루·손수레·기둥 랜턴·작은 버섯
    crates(s, r, [(-4, -2, 2), (-5, -1, 1), (-4, -3, 1)])
    for (x, z) in ((-5, 1), (-5, 2)): put(s, x, z, 'brown_wool')
    put(s, -5, 1, 'white_wool')
    cart(s, 5, 0, 'east') if False else None
    lamp(s, 2, 7, 'spruce', 2)
    mini_mush(s, -3, 1, -4, 'red', 0, 1); mini_mush(s, 4, 1, -3, 'red', 1, 2)
    # 갓
    dcap(s, 0, 0, 11, 6.2, 'red', 51, drip=(0, 2), spots=9, chimney=(2, -2))
    mini_mush(s, -4, top(s, -4, 2) + 1, 2, 'red', 1, 2, lean=(-1, 0)) if False else None
    # 갓 밑 사슬 랜턴
    for (x, z, L) in ((-4, -2, 2), (4, 2, 1), (-3, 3, 2)):
        for k in range(L): s.set(x, 10 - k, z, 'chain')
        s.set(x, 10 - L, z, 'lantern[hanging=true]')
    autoconnect(s); return s

# ═════════ C2. 상점 (갈색 갓, 창고 + 2층 발코니) ═════════
def shop_brown():
    s = S('v2/shop_brown'); r = random.Random(602)
    plinth(s, r, 0, 1, 5.5)
    prof = lambda y: {1: 3.6, 2: 3.2, 3: 2.9}.get(y, 2.8 if y < 10 else 3.2)
    stem(s, r, 1, 11, prof, (['mushroom_stem', 'calcite', 'birch_wood', 'smooth_quartz'], [66, 16, 10, 8]))
    root_flare(s, r, 0, 0, 3.3, 6, 'mushroom_stem', 'smooth_quartz_stairs')
    for y in (1, 2, 3):
        for z in (1, 2, 3): s.set(0, y, z, None)
    door(s, 0, 1, 3, wood='dark_oak')
    s.set(-1, 3, 3, 'dark_oak_stairs[facing=east,half=top,shape=straight]'); s.set(1, 3, 3, 'dark_oak_stairs[facing=west,half=top,shape=straight]')
    # 문 앞 차양 (계단 지붕 + 기둥)
    for x in (-2, 2):
        for y in (1, 2, 3): s.set(x, y, 5, 'dark_oak_fence')
    for x in range(-2, 3): s.set(x, 4, 5, 'dark_oak_stairs[facing=north,half=bottom,shape=straight]'); s.set(x, 4, 4, 'dark_oak_planks')
    for x in range(-1, 2): s.set(x, 5, 4, 'dark_oak_stairs[facing=north,half=bottom,shape=straight]')
    s.set(0, 3, 5, 'lantern[hanging=true]')
    # 2층 둘레 발코니 (북·서) + 바깥 계단 (서쪽)
    for x in range(-5, 4):
        for z in range(-5, 2):
            d = math.hypot(x, z)
            if 3.0 < d <= 4.8 and (x < 0 or z < -1):
                s.set(x, 6, z, 'spruce_slab[type=top]')
                if d > 4.0: s.set(x, 7, z, 'spruce_fence')
    for (x, z, f) in ((-4, -2, 'east'), (-2, -4, 'south'), (0, -4, 'south'), (-4, 0, 'east')): s.set(x, 5, z, f'spruce_stairs[facing={f},half=top,shape=straight]')
    for i, (x, z) in enumerate(((-6, 4), (-6, 3), (-6, 2), (-6, 1), (-6, 0))):
        s.set(x, i + 1, z, 'spruce_stairs[facing=north,half=bottom,shape=straight]')
        if i: s.set(x, i, z, 'spruce_stairs[facing=south,half=top,shape=straight]')
        s.set(x - 1, i + 2, z, 'spruce_fence') if i % 2 == 0 else None
    s.set(-6, 6, -1, 'spruce_slab[type=top]'); s.set(-5, 6, 0, 'spruce_slab[type=top]'); s.set(-6, 6, 0, 'spruce_slab[type=top]')
    for y in (7, 8): s.set(-3, y, 0, 'glass_pane') if False else None
    s.set(-2, 7, -2, 'spruce_door[facing=northwest]') if False else None
    for (x, z, f) in ((0, -3, 'north'), (-3, 0, 'west'), (0, 3, 'south'), (3, 0, 'east')):
        dx, dz = DIRS[f]
        for y in (7, 8): s.set(x, y, z, 'glass_pane')
        side = [(x - 1, z + dz), (x + 1, z + dz)] if dz else [(x + dx, z - 1), (x + dx, z + 1)]
        for (sx, sz) in side:
            for y in (7, 8): s.set(sx, y, sz, f'spruce_trapdoor[facing={f},half=bottom,open=true]') if s.get(sx, y, sz) is None else None
    # 동쪽 창고: 돌 1층 + 작은 갈색 버섯 지붕
    for y in range(0, 4):
        for x in range(4, 9):
            for z in range(-2, 3):
                edge = x in (4, 8) or z in (-2, 2)
                if y == 0: s.set(x, y, z, r.choice(['cobblestone', 'stone_bricks', 'mossy_cobblestone']))
                elif edge: s.set(x, y, z, 'stripped_spruce_log[axis=y]' if (x in (4, 8) and z in (-2, 2)) else r.choice(['spruce_planks', 'spruce_planks', 'mossy_cobblestone' if y == 1 else 'spruce_planks']))
    for x in range(5, 8):
        for y in (1, 2): s.set(x, y, 2, None)
    s.set(6, 1, 2, 'spruce_door[facing=north,half=lower,hinge=left,open=true,powered=false]'); s.set(6, 2, 2, 'spruce_door[facing=north,half=upper,hinge=left,open=true,powered=false]')
    s.set(5, 1, 2, 'spruce_planks'); s.set(7, 1, 2, 'spruce_planks'); s.set(5, 2, 2, 'spruce_planks'); s.set(7, 2, 2, 'spruce_planks')
    s.set(8, 2, 0, 'iron_bars')
    roof(s, r, 4, 8, -2, 2, 4, mats=('spruce', 'dark_oak'), eave='dark_oak', gable_fill=('spruce_planks',), axis='z', over=1)
    # 마당: 손수레·상자·장작·간판·랜턴·작은 버섯
    cart(s, 3, 6, 'east')
    crates(s, r, [(9, -3, 2), (9, -2, 1), (8, -4, 1)])
    for i, z in enumerate((3, 4)): put(s, 9, z, 'spruce_log[axis=x]', 'oak_log[axis=x]' if i == 0 else 'spruce_slab[type=bottom]')
    sign_post(s, -3, 6, 'east')
    lamp(s, 4, 4, 'dark_oak', 2)
    mini_mush(s, -4, 1, -4, 'brown', 1, 2); mini_mush(s, 2, 1, -5, 'brown', 0, 1)
    dcap(s, 0, 0, 12, 6.4, 'brown', 62, drip=(0, 2), spots=6, glow=0, chimney=(-2, -3))
    mini_mush(s, 2, top(s, 2, 1) + 1, 1, 'brown', 1, 2)
    for (x, z, L) in ((-5, -2, 2), (4, -3, 3), (2, 5, 1)):
        for k in range(L): s.set(x, 11 - k, z, 'chain')
        s.set(x, 11 - L, z, 'lantern[hanging=true]')
    autoconnect(s); return s

# ═════════ D. 경비 초소 입구 (카엘) ═════════
def gate():
    s = S('v2/guard_gate'); r = random.Random(744)
    STONE = ['stone_bricks', 'stone_bricks', 'cobblestone', 'mossy_stone_bricks', 'andesite', 'cracked_stone_bricks']
    INF = ['calcite'] * 6 + ['white_terracotta']
    for tx in (-9, 4):
        x0, x1, z0, z1 = tx, tx + 4, 0, 4
        cxm = x0 + 2
        # 돌 기단 + 모서리 버팀벽 (계단)
        for y in range(0, 4):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1) or y == 0: s.set(x, y, z, r.choice(STONE))
        for (x, z, fx, fz) in ((x0, z0, -1, -1), (x1, z0, 1, -1), (x0, z1, -1, 1), (x1, z1, 1, 1)):
            for (bx, bz, f) in ((x + fx, z, 'east' if fx < 0 else 'west'), (x, z + fz, 'south' if fz < 0 else 'north')):
                s.set(bx, 0, bz, r.choice(STONE)); s.set(bx, 1, bz, r.choice(STONE))
                s.set(bx, 2, bz, f'stone_brick_stairs[facing={f},half=bottom,shape=straight]')
        for x in range(x0, x1 + 1): s.set(x, 3, z1 + 1, 'stone_brick_stairs[facing=north,half=top,shape=straight]') if x not in (x0, x1) else None
        # 1층 하프팀버 (y4~6)
        for y in range(4, 7):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1):
                        corner = x in (x0, x1) and z in (z0, z1)
                        s.set(x, y, z, 'spruce_log[axis=y]' if corner else ('stripped_spruce_log[axis=y]' if (x == cxm or z == 2) else r.choice(INF)))
        for x in range(x0, x1 + 1):
            for z in (z0, z1): s.set(x, 4, z, 'dark_oak_log[axis=x]') if x not in (x0, x1) else None
        # 2층 돌출 (y7 바닥, y8~10 벽) + 받침 + 대각 버팀
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                edge = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
                s.set(x, 7, z, ('dark_oak_log[axis=x]' if z in (z0 - 1, z1 + 1) else 'dark_oak_log[axis=z]') if edge else 'spruce_planks')
                if edge:
                    corner = x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1)
                    for y in (8, 9, 10):
                        mid = x == cxm or z == 2
                        s.set(x, y, z, 'dark_oak_log[axis=y]' if corner else ('stripped_dark_oak_log[axis=y]' if mid and y != 9 else r.choice(INF)))
                    s.set(x, 11, z, 'dark_oak_log[axis=x]' if z in (z0 - 1, z1 + 1) else 'dark_oak_log[axis=z]')
        brackets(s, [(x, 6, z1 + 1, 'north') for x in (x0, cxm, x1)] + [(x, 6, z0 - 1, 'south') for x in (x0, cxm, x1)] +
                 [(x0 - 1, 6, z, 'east') for z in (z0, 2, z1)] + [(x1 + 1, 6, z, 'west') for z in (z0, 2, z1)], 'dark_oak')
        # 대각 버팀 (정면 패널 안쪽에 계단으로 X)
        for (x, y, f) in ((x0, 8, 'east'), (x0 + 1, 9, 'east'), (x1, 8, 'west'), (x1 - 1, 9, 'west')):
            pass
        # 창: 1층 좁은 활창, 2층 덧문 창 (정면·옆)
        for y in (5, 6): s.set(cxm, y, z1, 'iron_bars')
        s.set(cxm, 4, z1 + 1, 'stone_brick_stairs[facing=north,half=top,shape=straight]') if False else None
        for (wx, wz, f) in ((cxm, z1 + 1, 'south'), ((x0 - 1) if tx < 0 else (x1 + 1), 2, 'west' if tx < 0 else 'east')):
            dx, dz = DIRS[f]
            for y in (8, 9): s.set(wx, y, wz, 'glass_pane')
            side = [(wx - 1, wz + dz), (wx + 1, wz + dz)] if dz else [(wx + dx, wz - 1), (wx + dx, wz + 1)]
            for (sx, sz) in side:
                for y in (8, 9): s.set(sx, y, sz, f'spruce_trapdoor[facing={f},half=bottom,open=true]')
            s.set(wx + dx, 7, wz + dz, f'spruce_trapdoor[facing={f},half=top,open=false]')
        # 깃발 + 벽 랜턴
        s.set(cxm - 1, 6, z1 + 1, 'red_wall_banner[facing=south]'); s.set(cxm + 1, 6, z1 + 1, 'red_wall_banner[facing=south]')
        for x in (x0, x1): s.set(x, 5, z1 + 1, 'spruce_fence'); s.set(x, 4, z1 + 1, 'lantern[hanging=true]')
        # 갓 받침 (짧은 줄기) + 디테일 갓
        ring_fill(s, cxm, 2, 12, 2.2, 'mushroom_stem'); ring_fill(s, cxm, 2, 13, 2.6, 'mushroom_stem')
        dcap(s, cxm, 2, 14, 4.8, 'red', 800 + tx, drip=(1, 2), spots=8)
        for (x, z, L) in ((cxm - 3, -1, 2), (cxm + 3, 5, 1)):
            for k in range(L): s.set(x, 13 - k, z, 'chain')
            s.set(x, 13 - L, z, 'lantern[hanging=true]')
        put(s, cxm, 2, 'lightning_rod[facing=up,powered=false,waterlogged=false]', roof=True)
    # 성문: 돌 문틀 + 열린 나무 문짝 + 쇠창살 끝
    for z in (0, 4):
        for y in range(1, 7): s.set(-4, y, z, r.choice(STONE)); s.set(3, y, z, r.choice(STONE))
    for x in range(-3, 3):
        s.set(x, 7, 0, 'dark_oak_log[axis=x]'); s.set(x, 7, 4, 'dark_oak_log[axis=x]')
    for z in (0, 4):
        s.set(-3, 6, z, 'dark_oak_stairs[facing=west,half=top,shape=straight]'); s.set(2, 6, z, 'dark_oak_stairs[facing=east,half=top,shape=straight]')
    for x in range(-2, 2): s.set(x, 6, 2, 'iron_bars')
    for y in range(1, 6):
        s.set(-3, y, 5, 'spruce_trapdoor[facing=east,half=bottom,open=true]'); s.set(-3, y, 6, 'spruce_trapdoor[facing=east,half=bottom,open=true]')
        s.set(2, y, 5, 'spruce_trapdoor[facing=west,half=bottom,open=true]'); s.set(2, y, 6, 'spruce_trapdoor[facing=west,half=bottom,open=true]')
    # 통로: 바닥 + 난간 + 버섯 갓 지붕 (작은 빨강)
    for x in range(-4, 4):
        for z in range(0, 5): s.set(x, 8, z, 'spruce_planks' if 0 < z < 4 else 'dark_oak_log[axis=x]')
        s.set(x, 9, 0, 'spruce_fence'); s.set(x, 9, 4, 'spruce_fence')
    for x in (-4, 3):
        for z in (0, 4):
            for y in (9, 10): s.set(x, y, z, 'spruce_fence')
    roof(s, r, -3, 2, 0, 4, 10, mats=('dark_oak', 'spruce'), eave='spruce', gable_fill=('calcite', 'stripped_dark_oak_log[axis=y]'), axis='x', over=1)
    s.set(-1, 10, 2, 'chain'); s.set(-1, 9, 2, 'lantern[hanging=true]') if False else None
    for x in (-4, 3): s.set(x, 7, 5, 'spruce_fence'); s.set(x, 6, 5, 'lantern[hanging=true]')
    s.set(-1, 7, 5, 'spruce_hanging_sign[attached=true,rotation=0,waterlogged=false]') if False else None
    s.set(-1, 6, 5, 'red_wall_banner[facing=south]'); s.set(0, 6, 5, 'red_wall_banner[facing=south]') if False else None
    # 방책: 끝이 뾰족한 통나무 (높이 4·5·6), 가로 띠, 돌 기초, 안쪽 발판
    for xs in (range(-16, -10), range(10, 16)):
        for i, x in enumerate(xs):
            s.set(x, 0, 2, r.choice(STONE)); s.set(x, 0, 1, r.choice(STONE)); s.set(x, 0, 3, r.choice(STONE))
            h = (5, 4, 6, 4, 5, 4)[i]
            for y in range(1, h + 1): s.set(x, y, 2, 'spruce_log[axis=y]')
            s.set(x, h + 1, 2, 'spruce_fence')
            s.set(x, 2, 3, 'dark_oak_log[axis=x]'); s.set(x, 4, 3, 'dark_oak_log[axis=x]') if h >= 4 else None
            s.set(x, 3, 1, 'spruce_slab[type=top]')
    # 길·화로·무기 거치대·과녁·상자·게시판
    for z in range(-3, 11):
        for x in range(-3, 3):
            if s.get(x, 0, z) is None: s.set(x, 0, z, r.choice(['gravel', 'coarse_dirt', 'cobblestone', 'dirt_path', 'dirt_path']))
    brazier(s, -5, 8); brazier(s, 4, 8)
    for x in (5, 6, 7): s.set(x, 1, 7, 'spruce_fence')
    s.set(5, 2, 7, 'grindstone[face=floor,facing=north]'); s.set(7, 2, 7, 'spruce_slab[type=bottom]'); s.set(6, 2, 7, 'spruce_fence')
    s.set(-9, 1, 7, 'hay_block[axis=y]'); s.set(-9, 2, 7, 'target[power=0]'); s.set(-10, 1, 7, 'hay_block[axis=x]')
    crates(s, r, [(-7, 7, 2), (-6, 7, 1), (-7, 8, 1)])
    for x in (9, 11):
        for y in (1, 2): s.set(x, y, 7, 'spruce_fence')
    for x in (9, 10, 11): s.set(x, 3, 7, 'spruce_planks'); s.set(x, 4, 7, 'spruce_stairs[facing=south,half=bottom,shape=straight]')
    s.set(10, 3, 8, 'white_wall_banner[facing=south]') if False else None
    mini_mush(s, -12, 1, 5, 'red', 1, 2); mini_mush(s, 13, 1, 5, 'red', 0, 1)
    autoconnect(s); return s

if __name__ == '__main__':
    os.makedirs('v2d5', exist_ok=True)
    items = []
    for s, label in ((shop_red(), 'C1. 상점 (빨강 갓)'), (shop_brown(), 'C2. 상점 (갈색 갓 + 창고)'), (gate(), 'D. 경비 초소 입구 (카엘)')):
        p = 'v2d5/' + s.name.split('/')[1]
        s.save(p + '.schem')
        x0, y0, z0, x1, y1, z1 = s.bounds(); dim = f' {x1 - x0 + 1}×{z1 - z0 + 1}×{y1 - y0 + 1}'
        render2(view2(s, 0), p + '.png', scale=22); render2(view2(s, 2), p + '_back.png', scale=22)
        items.append((p + '.png', label + dim)); items.append((p + '_back.png', label + ' (뒤)'))
        print(s.name, dim)
    print(sheet(items[:4], 'village2_v5_shops.png', cols=2))
    print(sheet(items[4:], 'village2_v5_gate.png', cols=2))
