"""2지역 마을 17채 — 독일식 반목조 (vlib2) + 버섯 살짝. 앞 = 남쪽(+z). 건물엔 꽃·풀 없음."""
from vlib2 import *

def floor_fill(s, x0, z0, x1, z1, y, m='spruce_planks'):
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1): s.set(x, y, z, m)

def townhouse(s, X1, Z1, floors=2, roof='red', infill=CREAM, pattern='K', jet='S', axis='z', door_x=None, post=4, wins_every=3, base_h=4):
    """기본 몸통: 돌 1층 + 반목조 위층들 (층마다 jet 쪽으로 1칸씩 튀어나옴) + 가파른 박공. 돌려줌: 마지막 사각형, 지붕 시작 y, 꼭대기"""
    foundation(s, 0, 0, X1, Z1)
    base_story(s, 0, 0, X1, Z1, 1, base_h)
    dx = door_x if door_x is not None else X1 // 2
    front_door(s, dx, Z1, 'S')
    for x in range(1, X1, wins_every):
        if abs(x - dx) > 1 and 0 < x < X1: window(s, x, 2, Z1, 'S', shutters=True, wood='spruce', lintel='spruce')
    for z in range(2, Z1 - 1, 4): window(s, 0, 2, z, 'W', shutters=False); window(s, X1, 2, z, 'E', shutters=False)
    x0, z0, x1, z1 = 0, 0, X1, Z1
    y = base_h + 1
    for k in range(floors - 1):
        jetty(s, x0, z0, x1, z1, y - 1, sides=jet)
        x0 -= 'W' in jet; x1 += 'E' in jet; z0 -= 'N' in jet; z1 += 'S' in jet
        wins = [('S', (x, z1)) for x in range(x0 + 2, x1 - 1, wins_every)] + [('N', (x, z0)) for x in range(x0 + 2, x1 - 1, 4)] \
             + [('E', (x1, z)) for z in range(z0 + 2, z1 - 1, 4)] + [('W', (x0, z)) for z in range(z0 + 2, z1 - 1, 4)]
        fw_story(s, x0, z0, x1, z1, y, h=4, infill=infill, post=post, pattern=pattern, wins=wins)
        floor_fill(s, x0, z0, x1, z1, y)
        y += 4
    top = steep_gable(s, x0, z0, x1, z1, y, axis=axis, roof=roof, infill=infill)
    return (x0, z0, x1, z1), y, top

# ───────── 1. 경비대 초소 (카엘) ─────────
def kael2():
    s = Bld('m2/kael_gate', 501)
    foundation(s, 0, 0, 6, 6)
    base_story(s, 0, 0, 6, 6, 1, 10)
    front_door(s, 3, 6, 'S')
    for y in (4, 8):
        for (x, z, f) in ((3, 0, 'N'), (0, 3, 'W'), (6, 3, 'E')): s.set(x, y, z, 'glass_pane' if False else 'iron_bars')
    jetty(s, 0, 0, 6, 6, 10, sides='SNEW')
    fw_story(s, -1, -1, 7, 7, 11, h=4, infill=WHITE, post=2, pattern='', wins=[('S', (3, 7)), ('N', (3, -1)), ('E', (7, 3)), ('W', (-1, 3))])
    floor_fill(s, -1, -1, 7, 7, 11)
    top = hip_roof(s, -1, -1, 7, 7, 15, roof='slate', over=1)
    s.set(3, top + 1, 3, 'spruce_fence'); s.set(3, top + 2, 3, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    for yy in range(1, 12): s.set(5, yy, 1, 'ladder[facing=west,waterlogged=false]')
    # 옆 대기실 (동쪽, 작은 박공)
    foundation(s, 7, 1, 13, 6)
    base_story(s, 7, 1, 13, 6, 1, 4)
    front_door(s, 10, 6, 'S'); window(s, 12, 2, 6, 'S', shutters=False); window(s, 13, 2, 3, 'E', shutters=False)
    steep_gable(s, 7, 1, 13, 6, 5, axis='x', roof='red', infill=WHITE)
    banner(s, 1, 9, 6, 'blue'); banner(s, 5, 9, 6, 'blue')
    for x in (15, 17): s.set(x, 1, 3, 'spruce_fence'); s.set(x, 2, 3, 'spruce_fence')
    s.set(16, 2, 3, 'spruce_fence'); s.set(16, 1, 3, 'grindstone[face=floor,facing=north]')
    barrels(s, [(15, 1, 5), (16, 1, 5)]); s.set(-3, 1, 4, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    s.set(-4, 1, 4, ST('spruce', 'east')); s.set(-2, 1, 4, ST('spruce', 'west'))
    furnish(s, 7, 1, 13, 6, 1, 'home')
    return finish(s)

# ───────── 2. 버섯 연구소 (모리스) ─────────
def lab2():
    s = Bld('m2/lab', 502)
    (x0, z0, x1, z1), y, top = townhouse(s, 10, 7, floors=3, roof='slate', infill=WHITE, pattern='X', jet='S', axis='x', door_x=3)
    # 앞 박공 날개 (왼쪽, 붉은 지붕)
    oriel(s, 7, 10, z1, 'S', w=3, h=3, roof='slate')
    # 뒤 오른쪽 원통 탑 (관측대) + 망원경
    tt = turret(s, 11, -1, 2.6, 1, 18, roof='slate', timber=True)
    s.set(12, 18, -1, 'spruce_fence') if False else None
    # 동쪽 유리 온실: 버섯 키우는 곳 (바닥 균사체·포드졸, 작은 버섯 덩어리들)
    gx0, gz0, gx1, gz1 = 12, 2, 19, 8
    for x in range(gx0, gx1 + 1):
        for z in range(gz0, gz1 + 1):
            edge = x in (gx0, gx1) or z in (gz0, gz1)
            s.set(x, 0, z, 'stone_bricks' if edge else ('mycelium[snowy=false]' if (x + z) % 3 else 'podzol[snowy=false]'))
            if edge:
                s.set(x, 1, z, s.pick(STONE_LOW))
                for yy in (2, 3, 4): s.set(x, yy, z, LOG(FR) if (x in (gx0, gx1) and z in (gz0, gz1)) or (x - gx0) % 3 == 0 and z in (gz0, gz1) else 'glass')
    for z in range(gz0 - 1, gz1 + 2):
        for k, x in enumerate(range(gx0 - 1, gx1 + 2)):
            pass
    for x in range(gx0, gx1 + 1):   # 유리 박공 지붕 (용마루 x 방향)
        for k in range(4):
            for zz in (gz0 + k, gz1 - k):
                s.set(x, 5 + k, zz, 'glass' if 0 < x - gx0 < gx1 - gx0 else LOG(FR))
        s.set(x, 8, (gz0 + gz1) // 2, SL('spruce'))
    for zz in range(gz0 + 1, gz1):
        for yy in range(5, 8):
            if s.get(gx1, yy, zz) is None and yy < 5 + min(zz - gz0, gz1 - zz): s.set(gx1, yy, zz, 'glass')
    for (mx, mz, h, R, kd) in ((14, 4, 2, 1, 'red'), (17, 6, 3, 1.5, 'brown'), (15, 7, 1, 1, 'brown'), (18, 3, 2, 1, 'red')):
        small_mush(s, mx, mz, h=h, R=R, kind=kd)
    s.set(gx0, 2, 5, 'spruce_door[facing=west,half=lower,hinge=left,open=false,powered=false]'); s.set(gx0, 3, 5, 'spruce_door[facing=west,half=upper,hinge=left,open=false,powered=false]')
    s.set(gx0, 1, 5, 'spruce_planks')
    shop_sign(s, 5, 4, z1 if False else 7, 'S'); banner(s, 1, 4, 7, 'brown')
    s.set(-2, 1, 5, 'lectern[facing=east,has_book=true,powered=false]'); barrels(s, [(-2, 1, 3), (-2, 1, 2)])
    chimney(s, -1, 1, 1, top - 2); s.set(-1, 0, 1, 'stone_bricks')
    furnish(s, 0, 0, 10, 7, 1, 'magic'); furnish(s, x0, z0, x1, z1, 6, 'home')
    return finish(s)

# ───────── 3. 연금술 공방 (네라) ─────────
def alch3():
    s = Bld('m2/alchemy', 503)
    (x0, z0, x1, z1), y, top = townhouse(s, 6, 8, floors=3, roof='red', infill=PINK, pattern='K', jet='S', axis='z', door_x=3, post=3)
    oriel(s, 3, 6, z1 - 1, 'S', w=3, h=2, roof='red') if False else None
    # 굽은 굴뚝 두 개
    for yy in range(1, top + 1): s.set(-1, yy, 2, 'stone_bricks' if yy < 5 else 'bricks')
    s.set(-2, top + 1, 2, 'bricks'); s.set(-2, top + 2, 2, 'bricks'); s.set(-2, top + 3, 2, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    s.set(-1, 0, 2, 'stone_bricks')
    for yy in range(9, top + 2): s.set(7, yy, 5, 'bricks') if s.get(7, yy, 5) is None else None
    s.set(7, top + 2, 5, 'soul_campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    for yy in range(5, 9): s.set(7, yy, 5, 'bricks')
    s.set(7, 4, 5, ST('stone_brick', 'west', 'top'))
    # 서쪽 작업 차양: 가마솥·양조대
    lean_to(s, -4, -1, 0, 1, 4, f='W', mat='spruce', posts=False) if False else None
    for z in range(1, 8):
        s.set(-2, 4, z, SL('mangrove')); s.set(-3, 4, z, SL('mangrove'))
    for z in (1, 7):
        for yy in range(1, 4): s.set(-3, yy, z, LOG(BM))
    s.set(-2, 1, 2, 'cauldron'); s.set(-2, 1, 4, 'water_cauldron[level=3]'); s.set(-2, 1, 5, 'spruce_planks'); s.set(-2, 2, 5, 'brewing_stand'); s.set(-2, 1, 6, 'barrel[facing=up,open=false]')
    s.set(-3, 3, 4, 'lantern[hanging=true,waterlogged=false]')
    banner(s, 1, 4, 8, 'purple'); banner(s, 5, 4, 8, 'purple'); shop_sign(s, 5, 4, 8, 'S') if False else None
    for (x, z) in ((1, 10), (5, 10)): s.set(x, 1, z, 'amethyst_block')
    furnish(s, 0, 0, 6, 8, 1, 'magic'); furnish(s, x0, z0, x1, z1, 6, 'magic')
    overgrow(s, 0.6, seed=13, roof_sprouts=0)
    return finish(s)

# ───────── 4. 장비 상점 ─────────
def gear3():
    s = Bld('m2/gear', 504)
    X1, Z1 = 9, 10
    foundation(s, 0, 0, X1, Z1)
    base_story(s, 0, 0, X1, Z1, 1, 4)
    for x in range(1, X1): s.set(x, 0, Z1, 'polished_andesite')
    arcade(s, 0, X1, Z1, 'S', h=3)
    for x in range(1, X1):   # 안쪽 벽 (아케이드 뒤 2칸)
        for yy in range(1, 4): s.set(x, yy, Z1 - 3, s.pick(BASE2))
        s.set(x, 0, Z1 - 1, 'polished_andesite'); s.set(x, 0, Z1 - 2, 'polished_andesite')
        for yy in range(1, 4):
            for zz in (Z1 - 1, Z1 - 2): s.set(x, yy, zz, 'air')
    front_door(s, 4, Z1 - 3, 'S'); window(s, 2, 2, Z1 - 3, 'S', shutters=False); window(s, 7, 2, Z1 - 3, 'S', shutters=False)
    for x in (1, 8): s.set(x, 1, Z1 - 2, 'anvil[facing=east]' if x == 1 else 'grindstone[face=floor,facing=north]')
    s.set(2, 1, Z1 - 2, 'barrel[facing=up,open=false]'); s.set(7, 1, Z1 - 2, 'iron_block') if False else s.set(7, 1, Z1 - 2, 'smithing_table')
    fw_story(s, 0, 0, X1, Z1, 5, h=4, infill=CREAM, post=3, pattern='X', wins=[('S', (2, Z1)), ('S', (7, Z1)), ('E', (X1, 4)), ('W', (0, 4))])
    floor_fill(s, 0, 0, X1, Z1, 5)
    top = steep_gable(s, 0, 0, X1, Z1, 9, axis='z', roof='slate', infill=CREAM)
    shop_sign(s, 1, 4, Z1, 'S'); banner(s, 4, 8, Z1, 'light_gray') if False else None
    for x in (3, 6): s.set(x, 4, Z1 + 1, 'light_gray_wall_banner[facing=south]')
    chimney(s, X1 + 1, 2, 1, top - 3); s.set(X1 + 1, 0, 2, 'stone_bricks')
    furnish(s, 0, 0, X1, Z1 - 3, 1, 'shop'); furnish(s, 0, 0, X1, Z1, 6, 'home')
    return finish(s)

# ───────── 5. 잡화점 ─────────
def general3():
    s = Bld('m2/general', 505)
    (x0, z0, x1, z1), y, top = townhouse(s, 12, 6, floors=2, roof='red', infill=CREAM, pattern='K', jet='', axis='x', door_x=6, wins_every=4)
    # 가운데 앞 박공 날개
    wx0, wx1, wz0, wz1 = 4, 8, 6, 10
    foundation(s, wx0, wz0, wx1, wz1, skirt=False); base_story(s, wx0, wz0, wx1, wz1, 1, 4)
    for x in range(wx0 + 1, wx1):
        for yy in range(1, 4): s.set(x, yy, wz0, 'air')
    front_door(s, 6, wz1, 'S')
    fw_story(s, wx0, wz0, wx1, wz1, 5, h=4, infill=CREAM, post=2, pattern='', wins=[('S', (6, wz1))], sides='SEW')
    floor_fill(s, wx0, wz0 - 1, wx1, wz1, 5)
    steep_gable(s, wx0, wz0, wx1, wz1, 9, axis='z', roof='red', infill=CREAM)
    # 줄무늬 차양 + 진열
    for x in list(range(-1, 4)) + list(range(9, 14)):
        s.set(x, 4, 8, 'red_wool' if x % 2 else 'white_wool'); s.set(x, 3, 9, 'red_wool' if x % 2 else 'white_wool')
    for x in (-1, 13):
        for yy in range(1, 3): s.set(x, yy, 9, 'spruce_fence')
    barrels(s, [(0, 1, 8), (1, 1, 8), (11, 1, 8), (12, 1, 8), (12, 2, 8)]); s.set(2, 1, 8, 'chest[facing=south,type=single,waterlogged=false]'); s.set(10, 1, 8, SL('spruce', 'top'))
    shop_sign(s, 3, 4, 10, 'S') if False else shop_sign(s, 8, 4, wz1, 'S')
    chimney(s, -1, 3, 1, top + 1); s.set(-1, 0, 3, 'stone_bricks')
    furnish(s, 0, 0, 12, 6, 1, 'shop'); furnish(s, 0, 0, 12, 6, 6, 'home')
    return finish(s)

# ───────── 6. 대장간 ─────────
def smithy3():
    s = Bld('m2/smithy', 506)
    X1, Z1 = 10, 8
    foundation(s, 0, 0, X1, Z1)
    base_story(s, 0, 0, X1, Z1, 1, 5)
    front_door(s, 3, Z1, 'S'); window(s, 7, 2, Z1, 'S', shutters=False, h=2)
    for z in (3, 6): window(s, 0, 2, z, 'W', shutters=False)
    for z in range(2, 7):   # 동쪽 벽 열림 (작업장 쪽)
        for yy in range(1, 4): s.set(X1, yy, z, 'air')
    top = hip_roof(s, 0, 0, X1, Z1, 6, roof='slate', over=1)
    # 큰 굴뚝 (2×2, 뒤)
    for yy in range(1, top + 3):
        for (x, z) in ((7, 1), (8, 1), (7, 2), (8, 2)): s.set(x, yy, z, 'bricks' if yy > 6 else 'stone_bricks')
    for (x, z) in ((7, 1), (8, 1)): s.set(x, top + 3, z, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    s.set(7, 1, 3, 'blast_furnace[facing=south,lit=true]'); s.set(8, 1, 3, 'furnace[facing=south,lit=true]')
    # 동쪽 작업장 (기둥 + 슬레이트 차양)
    for z in range(1, 8):
        for x in (11, 12, 13): s.set(x, 0, z, 'stone_bricks')
        s.set(11, 5, z, SL('deepslate_tile', 'top')); s.set(12, 5, z, SL('deepslate_tile')); s.set(13, 4, z, SL('deepslate_tile', 'top'))
    for z in (1, 7):
        for yy in range(1, 5): s.set(13, yy, z, LOG(BM))
    s.set(12, 1, 3, 'anvil[facing=north]'); s.set(12, 1, 5, 'lava_cauldron'); s.set(13, 1, 5, 'water_cauldron[level=3]'); s.set(11, 1, 6, 'grindstone[face=floor,facing=east]'); s.set(13, 1, 2, 'smithing_table')
    woodpile(s, -4, 2); s.set(-3, 1, 5, 'coal_block'); s.set(-3, 1, 6, 'barrel[facing=up,open=false]')
    shop_sign(s, 5, 4, Z1, 'S')
    furnish(s, 0, 0, X1, Z1, 1, 'smith')
    overgrow(s, 0.5, seed=16, roof_sprouts=0)
    return finish(s)

# ───────── 7. 세공소 ─────────
def jewel3():
    s = Bld('m2/jeweler', 507)
    (x0, z0, x1, z1), y, top = townhouse(s, 6, 7, floors=2, roof='slate', infill=WHITE, pattern='X', jet='S', axis='z', door_x=3, post=2, wins_every=6)
    oriel(s, 3, 6, z1, 'S', w=3, h=2, roof='slate')
    for (x, z) in ((1, 9), (5, 9)): s.set(x, 1, z, 'amethyst_block'); s.set(x, 2, z, 'amethyst_cluster[facing=up,waterlogged=false]')
    shop_sign(s, 5, 4, 7, 'S')
    furnish(s, 0, 0, 6, 7, 1, 'shop')
    return finish(s)

# ───────── 8. 여관 ─────────
def inn3():
    s = Bld('m2/inn', 508)
    # 가운데 몸통 (옆 박공) + 양쪽 앞으로 나온 박공 날개
    foundation(s, 5, 0, 15, 8); base_story(s, 5, 0, 15, 8, 1, 4)
    front_door(s, 10, 8, 'S'); window(s, 7, 2, 8, 'S'); window(s, 13, 2, 8, 'S')
    fw_story(s, 5, 0, 15, 8, 5, h=4, infill=CREAM, post=4, pattern='K', wins=[('S', (7, 8)), ('S', (10, 8)), ('S', (13, 8)), ('N', (8, 0)), ('N', (12, 0))])
    fw_story(s, 5, 0, 15, 8, 9, h=4, infill=CREAM, post=4, pattern='K', wins=[('S', (8, 8)), ('S', (12, 8)), ('N', (10, 0))])
    floor_fill(s, 5, 0, 15, 8, 5); floor_fill(s, 5, 0, 15, 8, 9)
    steep_gable(s, 5, 0, 15, 8, 13, axis='x', roof='red', infill=CREAM)
    for (wx0, wx1) in ((0, 5), (15, 20)):
        foundation(s, wx0, -1, wx1, 11); base_story(s, wx0, -1, wx1, 11, 1, 4)
        cx = (wx0 + wx1) // 2
        window(s, cx, 2, 11, 'S'); window(s, wx0 if wx0 == 0 else wx1, 2, 5, 'W' if wx0 == 0 else 'E', shutters=False)
        jetty(s, wx0, -1, wx1, 11, 4, sides='S')
        fw_story(s, wx0, -1, wx1, 12, 5, h=4, infill=CREAM, post=2, pattern='', wins=[('S', (cx, 12))])
        fw_story(s, wx0, -1, wx1, 12, 9, h=4, infill=CREAM, post=2, pattern='', wins=[('S', (cx, 12))])
        floor_fill(s, wx0, -1, wx1, 12, 5); floor_fill(s, wx0, -1, wx1, 12, 9)
        steep_gable(s, wx0, -1, wx1, 12, 13, axis='z', roof='red', infill=CREAM)
    # 가운데 2층 발코니
    for x in range(6, 15):
        s.set(x, 5, 9, 'spruce_planks'); s.set(x, 4, 9, ST('spruce', 'north', 'top')); s.set(x, 6, 10, 'spruce_fence') if False else s.set(x, 6, 9, 'spruce_fence')
    s.set(10, 6, 8, 'spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]'); s.set(10, 7, 8, 'spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
    s.set(10, 6, 9, 'air')
    shop_sign(s, 7, 4, 9, 'S') if False else None
    banner(s, 2, 8, 12, 'red'); banner(s, 18, 8, 12, 'red')
    barrels(s, [(-2, 1, 9), (-2, 1, 10), (-2, 2, 10), (22, 1, 9)]); bench(s, 7, 11); bench(s, 12, 11)
    chimney(s, 10, 1, 13, 21) if False else None
    for yy in range(1, 22): s.set(10, yy, -1, 'stone_bricks' if yy < 13 else 'bricks')
    s.set(10, 22, -1, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    furnish(s, 5, 0, 15, 8, 1, 'inn'); furnish(s, 5, 0, 15, 8, 6, 'home'); furnish(s, 5, 0, 15, 8, 10, 'home')
    overgrow(s, 0.5, seed=18, roof_sprouts=1)
    return finish(s)

# ───────── 9. 광장 시계탑 분수 ─────────
def plaza3():
    s = Bld('m2/plaza', 509); c = 7
    for x in range(0, 15):
        for z in range(0, 15):
            d = math.hypot(x - c, z - c)
            if d <= 7.4: s.set(x, 0, z, 'polished_andesite' if (x + z) % 3 else 'stone_bricks')
            if 4.6 <= d <= 5.5: s.set(x, 1, z, 'mossy_stone_bricks' if (x * 3 + z) % 5 == 0 else 'stone_bricks'); s.set(x, 2, z, SL('smooth_stone'))
            elif 1.8 < d < 4.6: s.set(x, 0, z, 'stone_bricks'); s.set(x, 1, z, 'water')
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            for y in range(0, 11): s.set(x, y, z, 'chiseled_stone_bricks' if y in (1, 5, 10) else 'stone_bricks')
    for (dx, dz, f) in ((2, 0, 'west'), (-2, 0, 'east'), (0, 2, 'north'), (0, -2, 'south')):
        s.set(c + dx, 1, c + dz, ST('stone_brick', f)); s.set(c + dx, 5, c + dz, ST('stone_brick', f, 'top')); s.set(c + dx, 10, c + dz, ST('stone_brick', f, 'top'))
    for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):   # 종루: 네 기둥 + 종
        for y in range(11, 14): s.set(c + dx, y, c + dz, 'stone_brick_wall')
    for (dx, dz) in ((0, 1), (0, -1), (1, 0), (-1, 0)): s.set(c + dx, 11, c + dz, 'air')
    s.set(c, 11, c, 'stone_bricks'); s.set(c, 13, c, 'bell[attachment=ceiling,facing=north,powered=false]')
    for x in range(c - 2, c + 3):
        for z in range(c - 2, c + 3):
            on = x in (c - 2, c + 2) or z in (c - 2, c + 2)
            if on: s.set(x, 14, z, SL('deepslate_tile') if (x in (c - 2, c + 2) and z in (c - 2, c + 2)) else ST('deepslate_tile', 'south' if z == c - 2 else 'north' if z == c + 2 else 'east' if x == c - 2 else 'west'))
            else: s.set(x, 14, z, 'deepslate_tiles')
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            on = x in (c - 1, c + 1) or z in (c - 1, c + 1)
            if on: s.set(x, 15, z, SL('deepslate_tile') if (x != c and z != c) else ST('deepslate_tile', 'south' if z == c - 1 else 'north' if z == c + 1 else 'east' if x == c - 1 else 'west'))
    s.set(c, 15, c, 'deepslate_tiles'); s.set(c, 16, c, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    for (dx, dz, f) in ((2, 0, 'east'), (-2, 0, 'west'), (0, 2, 'south'), (0, -2, 'north')): s.set(c + dx, 8, c + dz, f'lantern[hanging=false,waterlogged=false]') if False else None
    for (dx, dz) in ((2, 0), (-2, 0), (0, 2), (0, -2)): s.set(c + dx, 7, c + dz, 'lantern[hanging=false,waterlogged=false]') if False else None
    # 게시판 (북쪽) · 벤치 · 등
    for x in (5, 9):
        for y in range(1, 4): s.set(x, y, -2, LOG(FR))
    for x in range(5, 10): s.set(x, 4, -2, ST('deepslate_tile', 'south')); s.set(x, 4, -3, ST('deepslate_tile', 'north'))
    for x in range(6, 9): s.set(x, 2, -2, 'spruce_planks'); s.set(x, 3, -2, 'spruce_planks'); s.set(x, 2, -1, 'spruce_wall_sign[facing=south,waterlogged=false]')
    for (x, z, f) in ((0, 7, 'east'), (14, 7, 'west'), (7, 15, 'north')):
        for i in (-1, 0, 1):
            if f == 'north': s.set(x + i, 1, z, ST('spruce', f))
            else: s.set(x, 1, z + i, ST('spruce', f))
    for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)): s.set(x, 1, z, 'stone_brick_wall'); s.set(x, 2, z, 'stone_brick_wall'); s.set(x, 3, z, 'lantern[hanging=false,waterlogged=false]')
    return finish(s)

# ───────── 10. 이동의 돌 ─────────
def waystone2():
    s = Bld('m2/waystone', 510); c = 4
    for x in range(0, 9):
        for z in range(0, 9):
            d = math.hypot(x - c, z - c)
            if d <= 4.4: s.set(x, 0, z, 'polished_deepslate' if d > 3.2 else 'deepslate_tiles' if d > 1.5 else 'chiseled_deepslate')
            if 3.3 < d <= 4.4: s.set(x, 1, z, SL('polished_deepslate'))
    s.set(c, 1, c, 'lodestone'); s.set(c, 2, c, 'amethyst_block'); s.set(c, 3, c, 'amethyst_cluster[facing=up,waterlogged=false]')
    for (x, z) in ((1, 1), (7, 1), (1, 7), (7, 7)):
        for y in range(1, 4): s.set(x, y, z, 'polished_deepslate_wall' if y < 3 else 'deepslate_brick_wall')
        s.set(x, 4, z, 'soul_lantern[hanging=false,waterlogged=false]')
    for (x, z) in ((c, 0), (c, 8), (0, c), (8, c)): s.set(x, 1, z, 'air')
    return finish(s)

# ───────── 11~15. 주민 집 ─────────
def h1():
    """길 쪽 박공 2층 (크림 · 붉은 지붕 · 돌출 창)"""
    s = Bld('m2/house1', 511)
    (x0, z0, x1, z1), y, top = townhouse(s, 6, 8, floors=2, roof='red', infill=CREAM, pattern='K', jet='S', axis='z', door_x=2, wins_every=4)
    oriel(s, 3, 6, z1, 'S', w=3, h=2, roof='red')
    chimney(s, 7, 2, 1, top - 2); s.set(7, 0, 2, 'stone_bricks')
    furnish(s, 0, 0, 6, 8, 1, 'home'); bench(s, 4, 10); s.set(-1, 1, 6, 'barrel[facing=up,open=false]')
    return finish(s)

def h2():
    """낮은 옆 박공 오두막 (분홍 회벽 · 슬레이트 · 지붕창 · 장작 헛간) + 버섯 살짝"""
    s = Bld('m2/house2', 512)
    foundation(s, 0, 0, 9, 6); base_story(s, 0, 0, 9, 6, 1, 3)
    front_door(s, 3, 6, 'S'); window(s, 6, 2, 6, 'S'); window(s, 8, 2, 6, 'S', shutters=False); window(s, 0, 2, 3, 'W'); window(s, 9, 2, 3, 'E')
    fw_story(s, 0, 0, 9, 6, 4, h=2, infill=PINK, post=3, pattern='', sides='SN')
    top = steep_gable(s, 0, 0, 9, 6, 6, axis='x', roof='slate', infill=PINK, rise=1)
    dormer(s, 6, 7, 7, roofm=('deepslate_tile', 'deepslate_tile'))
    for z in range(1, 6): s.set(10, 3, z, SL('deepslate_tile')); s.set(11, 2, z, SL('deepslate_tile', 'top'))
    for z in (1, 5):
        for yy in (1, 2): s.set(11, yy, z, LOG(BM))
    woodpile(s, 10, 3, 2) if False else None
    for z in range(2, 5): s.set(10, 1, z, LOG('spruce_log', 'z')); s.set(10, 2, z, LOG('spruce_log', 'z')) if z != 4 else None
    chimney(s, -1, 3, 1, top + 1); s.set(-1, 0, 3, 'stone_bricks')
    furnish(s, 0, 0, 9, 6, 1, 'home')
    overgrow(s, 0.8, seed=21, roof_sprouts=1, kinds=(('brown', 1),))
    return finish(s)

def h3():
    """ㄱ자 집 (흰 회벽 · 붉은 지붕 · 옆 박공 몸통 + 앞 박공 날개)"""
    s = Bld('m2/house3', 513)
    foundation(s, 0, 0, 9, 6); base_story(s, 0, 0, 9, 6, 1, 4)
    window(s, 2, 2, 6, 'S'); window(s, 0, 2, 3, 'W')
    fw_story(s, 0, 0, 9, 6, 5, h=4, infill=WHITE, post=3, pattern='X', wins=[('S', (2, 6)), ('N', (5, 0))])
    floor_fill(s, 0, 0, 9, 6, 5)
    steep_gable(s, 0, 0, 9, 6, 9, axis='x', roof='red', infill=WHITE)
    wx0, wx1, wz0, wz1 = 5, 9, 6, 11
    foundation(s, wx0, wz0, wx1, wz1, skirt=False); base_story(s, wx0, wz0, wx1, wz1, 1, 4)
    for x in range(wx0 + 1, wx1):
        for yy in range(1, 4): s.set(x, yy, wz0, 'air')
    front_door(s, 7, wz1, 'S'); window(s, wx1, 2, 9, 'E', shutters=False)
    fw_story(s, wx0, wz0, wx1, wz1, 5, h=4, infill=WHITE, post=2, pattern='', wins=[('S', (7, wz1))], sides='SEW')
    floor_fill(s, wx0, wz0 - 1, wx1, wz1, 5)
    steep_gable(s, wx0, wz0, wx1, wz1, 9, axis='z', roof='red', infill=WHITE)
    chimney(s, -1, 2, 1, 15); s.set(-1, 0, 2, 'stone_bricks')
    furnish(s, 0, 0, 9, 6, 1, 'home'); bench(s, 1, 8)
    return finish(s)

def h4():
    """좁고 높은 3층 (흰 회벽 · 슬레이트 · 층마다 튀어나옴)"""
    s = Bld('m2/house4', 514)
    (x0, z0, x1, z1), y, top = townhouse(s, 5, 7, floors=3, roof='slate', infill=WHITE, pattern='K', jet='SN', axis='z', door_x=2, post=2, wins_every=3)
    chimney(s, 6, 3, 1, top - 1); s.set(6, 0, 3, 'stone_bricks')
    furnish(s, 0, 0, 5, 7, 1, 'home'); s.set(-1, 1, 5, 'barrel[facing=up,open=false]')
    overgrow(s, 0.5, seed=24, roof_sprouts=0)
    return finish(s)

def h5():
    """우진각 오두막 + 모서리 원통 탑 (크림 · 붉은 지붕) + 버섯 살짝"""
    s = Bld('m2/house5', 515)
    foundation(s, 0, 0, 8, 7); base_story(s, 0, 0, 8, 7, 1, 4)
    front_door(s, 4, 7, 'S'); window(s, 2, 2, 7, 'S'); window(s, 6, 2, 7, 'S'); window(s, 0, 2, 4, 'W')
    fw_story(s, 0, 0, 8, 7, 5, h=3, infill=CREAM, post=2, pattern='', wins=[('S', (2, 7)), ('S', (6, 7)), ('W', (0, 4))])
    floor_fill(s, 0, 0, 8, 7, 5)
    top = hip_roof(s, 0, 0, 8, 7, 8, roof='red', over=1)
    turret(s, 9, 0, 2.2, 1, 12, roof='red', timber=True)
    furnish(s, 0, 0, 8, 7, 1, 'home'); bench(s, 1, 9)
    overgrow(s, 0.7, seed=25, roof_sprouts=1, kinds=(('red', 1), ('brown', 2)))
    return finish(s)

# ───────── 16. 우물 ─────────
def well3():
    s = Bld('m2/well', 516); c = 3
    for x in range(0, 7):
        for z in range(0, 7):
            d = math.hypot(x - c, z - c)
            if d <= 3.4: s.set(x, 0, z, 'stone_bricks' if d > 1.6 else 'water')
            if 1.6 < d <= 2.6: s.set(x, 1, z, 'mossy_stone_bricks' if (x + z) % 3 == 0 else 'stone_bricks'); s.set(x, 2, z, SL('stone_brick'))
            elif d <= 1.6: s.set(x, 1, z, 'water'); s.set(x, -1, z, 'water'); s.set(x, -2, z, 'stone_bricks')
    for (x, z) in ((1, 1), (5, 1), (1, 5), (5, 5)):
        for y in range(1, 6): s.set(x, y, z, LOG(FR))          # 기둥은 땅부터 지붕까지
    top = hip_roof(s, 1, 1, 5, 5, 6, roof='slate', over=1)
    for y in range(4, top): s.set(c, y, c, 'chain[axis=y]')    # 두레박 사슬은 지붕까지
    s.set(c, 3, c, 'cauldron')
    return finish(s)

# ───────── 17. 노점 ─────────
def stalls3():
    s = Bld('m2/stalls', 517)
    for i, col in enumerate(('red', 'blue', 'green')):
        x0 = i * 6
        for (x, z) in ((x0, 0), (x0 + 4, 0), (x0, 3), (x0 + 4, 3)):
            for y in range(1, 4 if z == 0 else 5): s.set(x, y, z, LOG(BM))
        for x in range(x0, x0 + 5):
            s.set(x, 5, 3, f'{col}_wool'); s.set(x, 4, 2, f'{col}_wool'); s.set(x, 4, 1, 'white_wool' if x % 2 else f'{col}_wool'); s.set(x, 3, 0, f'{col}_wool')
            s.set(x, 3, -1, f'{col}_carpet') if False else None
        for x in range(x0 + 1, x0 + 4): s.set(x, 1, 0, SL('spruce', 'top'))
        s.set(x0 + 1, 2, 0, ['barrel[facing=up,open=false]', 'chest[facing=south,type=single,waterlogged=false]', 'cake[bites=0]'][i])
        s.set(x0 + 3, 2, 0, 'lantern[hanging=false,waterlogged=false]')
        s.set(x0 + 2, 1, 3, 'barrel[facing=up,open=false]'); s.set(x0 + 3, 1, 3, 'barrel[facing=up,open=false]')
    return finish(s)

ALLV2 = [(kael2, '경비대 초소 (카엘)'), (lab2, '버섯 연구소 (모리스) + 버섯 온실'), (alch3, '연금술 공방 (네라)'), (gear3, '장비 상점 (아케이드)'),
         (general3, '잡화점'), (smithy3, '대장간'), (jewel3, '세공소'), (inn3, '여관'), (plaza3, '광장 시계탑 분수'), (waystone2, '이동의 돌'),
         (h1, '주민 집 1'), (h2, '주민 집 2'), (h3, '주민 집 3 (ㄱ자)'), (h4, '주민 집 4 (3층)'), (h5, '주민 집 5 (탑)'), (well3, '우물'), (stalls3, '노점 3칸')]

if __name__ == '__main__':
    from mbld import gallery
    gallery(ALLV2, 'm2_village_v2.png', cols=3, scale=11)
