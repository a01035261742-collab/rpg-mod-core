"""나머지 18개 (A 스타일, 같은 디테일 수준)"""
from abld import *

def house_body(s, X1, Z1, floors=2, axis='x', jet='SN', roofm=('dark_oak', 'spruce', 'spruce'), full='spruce_planks', door_x=None, post=4, infill=INFILL):
    foundation(s, 0, 0, X1, Z1)
    stone_story(s, 0, 0, X1, Z1, 1, 4)
    dx = door_x if door_x is not None else X1 // 2
    door(s, dx, Z1, 'S')
    for x in range(2, X1 - 1, 4):
        if abs(x - dx) > 1: window(s, x, 2, Z1, 'S')
    for z in range(2, Z1 - 1, 4): window(s, 0, 2, z, 'W'); window(s, X1, 2, z, 'E')
    x0, z0, x1, z1 = 0, 0, X1, Z1
    y = 5
    for f in range(floors - 1):
        jetty(s, x0, z0, x1, z1, y - 1, sides=jet)
        x0 -= 'W' in jet; x1 += 'E' in jet; z0 -= 'N' in jet; z1 += 'S' in jet
        wins = [('S', (x, z1)) for x in range(x0 + 2, x1 - 1, post)] + [('N', (x, z0)) for x in range(x0 + 2, x1 - 1, post)] \
             + [('E', (x1, z)) for z in range(z0 + 2, z1 - 1, post)] + [('W', (x0, z)) for z in range(z0 + 2, z1 - 1, post)]
        timber_story(s, x0, z0, x1, z1, y, h=4, post=post, wins=wins, infill=infill)
        y += 4
    top = gable_roof(s, x0, z0, x1, z1, y, axis=axis, mats=roofm, full=full, gable=infill)
    return (x0, z0, x1, z1, y, top)

# ── 광장 분수 + 게시판 ──
def fountain():
    s = Bld('start/plaza_fountain', 21); c = 6
    for x in range(0, 13):
        for z in range(0, 13):
            d = math.hypot(x - c, z - c)
            if d <= 6.4: s.set(x, 0, z, 'polished_andesite' if (x + z) % 2 else 'stone_bricks')
            if 4.6 <= d <= 5.5: s.set(x, 1, z, 'stone_bricks'); s.set(x, 2, z, SL('smooth_stone'))
            elif d < 4.6: s.set(x, 0, z, 'stone_bricks'); s.set(x, 1, z, 'water')
    for y in range(1, 5): s.set(c, y, c, 'chiseled_stone_bricks' if y in (1, 4) else 'stone_bricks')
    for (dx, dz, f) in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')):
        s.set(c + dx, 4, c + dz, ST('stone_brick', f, 'top')); s.set(c + dx, 1, c + dz, 'water')
    s.set(c, 5, c, 'water')
    for (dx, dz) in ((2, 2), (-2, 2), (2, -2), (-2, -2)): s.set(c + dx, 1, c + dz, 'water')
    # 게시판 (북쪽)
    for x in (4, 8):
        for y in range(1, 4): s.set(x, y, -2, LOG('dark_oak_log'))
    for x in range(4, 9): s.set(x, 4, -2, ST('spruce', 'south')); s.set(x, 4, -3, ST('spruce', 'north'))
    for x in range(5, 8):
        s.set(x, 2, -2, 'spruce_planks'); s.set(x, 3, -2, 'spruce_planks'); s.set(x, 2, -1, 'spruce_wall_sign[facing=south,waterlogged=false]')
    s.set(5, 3, -1, 'white_wall_banner[facing=south]') if False else None
    for (x, z, f) in ((0, 6, 'east'), (12, 6, 'west'), (6, 13, 'north')):
        if f == 'north':
            for i in (-1, 0, 1): s.set(x + i, 1, z, ST('spruce', f))
        else:
            for i in (-1, 0, 1): s.set(x, 1, z + i, ST('spruce', f))
    for (x, z) in ((1, 1), (11, 1), (1, 11), (11, 11)): lamp_post(s, x, z) if False else (s.set(x, 1, z, 'stone_brick_wall'), s.set(x, 2, z, 'stone_brick_wall'), s.set(x, 3, z, 'lantern[hanging=false,waterlogged=false]'))
    return finish(s)

# ── 잡화점 ──
def general_store():
    s = Bld('start/general_store', 22)
    X1, Z1 = 12, 8
    x0, z0, x1, z1, y, top = house_body(s, X1, Z1, floors=2, axis='x', door_x=4)
    dormer(s, 9, z1 + 1, y + 1)
    for x in range(-1, X1 + 2):   # 줄무늬 차양
        s.set(x, 4, Z1 + 2, 'red_wool' if x % 2 == 0 else 'white_wool'); s.set(x, 3, Z1 + 3, ('red_carpet' if x % 2 == 0 else 'white_carpet')) if False else None
    for x in (-1, X1 + 1):
        for y_ in range(1, 4): s.set(x, y_, Z1 + 2, 'spruce_fence')
    for (x, b) in ((6, 'barrel[facing=up,open=false]'), (7, 'barrel[facing=up,open=false]'), (9, 'chest[facing=south,type=single,waterlogged=false]'), (10, 'barrel[facing=up,open=false]'), (11, 'spruce_planks')):
        s.set(x, 1, Z1 + 1, b)
    s.set(11, 2, Z1 + 1, SL('spruce')); s.set(10, 2, Z1 + 1, 'barrel[facing=up,open=false]')
    hanging_sign(s, 2, 4, Z1 + 1, 'S') if False else None
    banner(s, 2, 3, Z1, 'yellow')
    chimney(s, X1 + 1, 4, 1, top + 1); s.set(X1 + 1, 0, 4, 'stone_bricks')
    furnish(s, 0, 0, X1, Z1, 1, 'shop'); furnish(s, x0, z0, x1, z1, 6, 'home')
    corner_lamps(s, x0, z0, x1, z1, 7); weathervane(s, 2, top + 1, (z0 + z1) // 2)
    path_patch(s, 3, 5, Z1 + 3, Z1 + 4); scatter(s, 0, 0, X1, Z1, 5)
    return finish(s)

# ── 장비 상점 ──
def gear_shop():
    s = Bld('start/gear_shop', 23)
    X1, Z1 = 10, 9
    foundation(s, 0, 0, X1, Z1)
    stone_story(s, 0, 0, X1, Z1, 1, 5)
    door(s, 5, Z1, 'S')
    for x in (2, 8): window(s, x, 2, Z1, 'S', h=3)
    for z in (3, 6): window(s, 0, 2, z, 'W'); window(s, X1, 2, z, 'E')
    for x in range(0, X1 + 1): s.set(x, 6, Z1 + 1, ST('stone_brick', 'north', 'top')); s.set(x, 6, -1, ST('stone_brick', 'south', 'top'))
    timber_story(s, 0, 0, X1, Z1, 6, h=4, post=5, wins=[('S', (x, Z1)) for x in (2, 8)] + [('E', (X1, 4))])
    top = gable_roof(s, 0, 0, X1, Z1, 10, axis='z', mats=('dark_oak', 'deepslate_tile', 'deepslate_tile'), full='deepslate_tiles')
    # 정면 진열: 철창 + 방패 대신 깃발
    for x in (1, 3, 7, 9): s.set(x, 1, Z1 + 2, 'spruce_fence'); s.set(x, 2, Z1 + 2, 'iron_bars')
    for x in (1, 9): banner(s, x, 4, Z1, 'blue')
    hanging_sign(s, 5, 6, Z1, 'S') if False else None
    hanging_sign(s, 2, 5, Z1 + 1, 'S')
    s.set(X1 + 2, 1, 2, 'anvil[facing=north]'); s.set(X1 + 2, 1, 4, 'grindstone[face=floor,facing=south]'); barrels(s, [(X1 + 2, 1, 6)])
    chimney(s, -1, 6, 1, top + 1); s.set(-1, 0, 6, 'stone_bricks')
    furnish(s, 0, 0, X1, Z1, 1, 'shop'); furnish(s, 0, 0, X1, Z1, 7, 'home')
    weathervane(s, 5, top + 1, 1); path_patch(s, 4, 6, Z1 + 3, Z1 + 4); scatter(s, 0, 0, X1, Z1, 5)
    return finish(s)

# ── 우물 ──
def well():
    s = Bld('start/well', 24)
    for x in range(0, 5):
        for z in range(0, 5):
            edge = x in (0, 4) or z in (0, 4)
            s.set(x, 0, z, 'stone_bricks' if edge else 'water')
            s.set(x, -1, z, 'stone_bricks' if edge else 'water'); s.set(x, -2, z, 'stone_bricks')
            if edge: s.set(x, 1, z, s.pick(STONE))
    for (x, z) in ((0, 2), (4, 2)):
        for y in range(2, 6): s.set(x, y, z, LOG('dark_oak_log'))   # 기둥이 지붕까지 닿게
    s.set(1, 4, 2, LOG('stripped_dark_oak_log', 'x')); s.set(2, 4, 2, LOG('stripped_dark_oak_log', 'x')); s.set(3, 4, 2, LOG('stripped_dark_oak_log', 'x'))
    s.set(2, 3, 2, 'chain[axis=y]'); s.set(2, 2, 2, 'cauldron')
    gable_roof(s, 0, 1, 4, 3, 5, axis='x', over=1, mats=('dark_oak', 'spruce'), gable_frame=False, gable=[('air', 1)])
    for x in (0, 4):
        for y in (5, 6): s.set(x, y, 2, LOG('dark_oak_log'))   # 기둥이 지붕 마루까지 닿게
    for x in (0, 4):
        for z in (1, 3): 
            if s.get(x, 5, z) == 'minecraft:air': s.set(x, 5, z, None)
    for x in range(-1, 6):
        for z in range(-1, 6):
            if s.get(x, 0, z) is None: s.set(x, 0, z, s.pick([('cobblestone', 40), ('gravel', 30), ('dirt_path', 30)]))
    s.set(5, 1, 4, 'barrel[facing=up,open=false]')
    return finish(s)

# ── 시장 노점 3칸 ──
def stalls():
    s = Bld('start/market_stalls', 25)
    for i, (col, goods) in enumerate((('red', 'barrel[facing=up,open=false]'), ('blue', 'chest[facing=south,type=single,waterlogged=false]'), ('yellow', 'hay_block[axis=y]'))):
        x0 = i * 5
        for (x, z) in ((x0, 0), (x0 + 3, 0), (x0, 3), (x0 + 3, 3)):
            for y in range(1, 4 if z == 3 else 5): s.set(x, y, z, 'spruce_fence')
        for x in range(x0, x0 + 4):
            for z in range(0, 4): s.set(x, 0, z, s.pick([('spruce_planks', 60), ('dirt_path', 40)]))
            s.set(x, 5, 0, f'{col}_wool'); s.set(x, 4, 1, f'{col}_wool'); s.set(x, 4, 2, 'white_wool'); s.set(x, 3, 3, f'{col}_wool'); s.set(x, 3, 4, f'{col}_carpet')
        for x in range(x0 + 1, x0 + 3): s.set(x, 1, 3, 'spruce_planks'); s.set(x, 2, 3, SL('spruce'))
        s.set(x0 + 1, 1, 1, goods); s.set(x0 + 2, 1, 1, 'barrel[facing=up,open=false]'); s.set(x0 + 1, 3, 2, 'lantern[hanging=true,waterlogged=false]')
        s.set(x0 + 2, 3, 3, f'{col}_wall_banner[facing=south]') if False else None
    return finish(s)

# ── 주민 집 3종 ──
def house1():
    s = Bld('start/house_1', 26)
    x0, z0, x1, z1, y, top = house_body(s, 8, 6, floors=2, axis='x', jet='S', post=4)
    chimney(s, -1, 3, 1, top + 1); s.set(-1, 0, 3, 'stone_bricks')
    furnish(s, 0, 0, 8, 6, 1, 'home'); woodpile(s, 9, 1); path_patch(s, 3, 5, 8, 9); scatter(s, 0, 0, 8, 6, 4)
    s.set(9, 1, 4, 'barrel[facing=up,open=false]'); bench(s, 1, 8)
    return finish(s)

def house2():
    s = Bld('start/house_2', 27)
    x0, z0, x1, z1, y, top = house_body(s, 6, 9, floors=2, axis='z', jet='SN', post=3, roofm=('dark_oak', 'dark_oak', 'spruce'), door_x=3)
    chimney(s, 7, 6, 1, top + 1); s.set(7, 0, 6, 'stone_bricks')
    furnish(s, 0, 0, 6, 9, 1, 'home')
    for z in range(2, 8): s.set(-1, 4, z, ST('spruce', 'east')); s.set(-2, 3, z, ST('spruce', 'east'))   # 옆 차양
    for z in (2, 7): s.set(-2, 1, z, 'spruce_fence'); s.set(-2, 2, z, 'spruce_fence')
    s.set(-1, 1, 4, 'crafting_table'); s.set(-1, 1, 5, 'barrel[facing=up,open=false]')
    path_patch(s, 2, 4, 11, 12); scatter(s, 0, 0, 6, 9, 4)
    return finish(s)

def house3():
    s = Bld('start/house_3', 28)
    foundation(s, 0, 0, 9, 6)
    stone_story(s, 0, 0, 9, 6, 1, 3)
    door(s, 3, 6, 'S'); window(s, 6, 2, 6, 'S'); window(s, 8, 2, 6, 'S', shutters=False); window(s, 0, 2, 3, 'W'); window(s, 9, 2, 3, 'E')
    top = gable_roof(s, 0, 0, 9, 6, 4, axis='x', mats=('dark_oak', 'spruce', 'spruce'))
    dormer(s, 6, 7, 5)
    chimney(s, 9, 1, 1, top + 1)
    furnish(s, 0, 0, 9, 6, 1, 'home')
    for x in range(1, 9): s.set(x, 0, 8, 'spruce_planks')
    for x in (1, 8):
        for y in range(1, 4): s.set(x, y, 8, 'spruce_fence')
    for x in range(0, 10): s.set(x, 4, 8, SL('spruce')) if s.get(x, 4, 8) is None else None
    bench(s, 5, 8); path_patch(s, 2, 4, 9, 10); scatter(s, 0, 0, 9, 6, 4)
    return finish(s)

# ── 세공소 (세공사) ──
def jeweler():
    s = Bld('start/jeweler', 29)
    x0, z0, x1, z1, y, top = house_body(s, 8, 8, floors=2, axis='z', jet='SNEW', post=3, roofm=('dark_oak', 'spruce', 'dark_oak'), door_x=4)
    # 모서리 작은 탑 (둥근 돌 + 반목조 꼭대기 + 원뿔 지붕)
    tx, tz = 12, 10
    for yy in range(0, top + 1):
        for x in range(tx - 1, tx + 2):
            for z in range(tz - 1, tz + 2):
                if (x, z) == (tx, tz): continue
                s.set(x, yy, z, ('stone_bricks' if yy == 0 else s.pick(STONE)) if yy < top - 6 else (LOG('dark_oak_log') if (x - tx) * (z - tz) != 0 else s.pick(INFILL)))
    s.set(tx + 1, top - 2, tz, 'glass_pane'); s.set(tx, top - 2, tz + 1, 'glass_pane'); s.set(tx, 4, tz + 1, 'glass_pane'); s.set(tx, 1, tz - 1, 'air'); s.set(tx, 2, tz - 1, 'air')
    cone(s, tx, tz, 2, top + 1, mats=('dark_oak', 'spruce', 'dark_oak'), step=0.4)
    s.set(2, 1, z1 + 1, 'spruce_planks') if False else None
    for (x, b) in ((6, 'gold_block'), (8, 'amethyst_block')): s.set(x, 1, 10, 'spruce_planks'); s.set(x, 2, 10, 'glass')
    banner(s, 2, 3, 8, 'purple'); hanging_sign(s, 6, 4, 8, 'S') if False else None
    furnish(s, 0, 0, 8, 8, 1, 'magic'); corner_lamps(s, x0, z0, x1, z1, 7)
    path_patch(s, 3, 5, 10, 11); scatter(s, 0, 0, 8, 8, 4)
    return finish(s)

# ── 연금술 공방 ──
def alchemy():
    s = Bld('start/alchemy', 30)
    x0, z0, x1, z1, y, top = house_body(s, 10, 8, floors=2, axis='x', jet='S', post=5, roofm=('dark_oak', 'spruce', 'spruce'), door_x=3)
    # 옆 실험 마당: 가마솥·양조대 + 초록 연기 굴뚝
    for z in range(1, 8): s.set(11, 0, z, 'stone_bricks'); s.set(12, 0, z, 'stone_bricks'); s.set(13, 0, z, 'stone_bricks')
    for z in (1, 7):
        for yy in (1, 2, 3): s.set(13, yy, z, LOG('stripped_dark_oak_log'))
    for z in range(0, 9): s.set(11, 4, z, ST('spruce', 'west')); s.set(12, 4, z, SL('spruce')); s.set(13, 4, z, ST('spruce', 'east'))
    s.set(12, 1, 3, 'water_cauldron[level=3]'); s.set(12, 1, 5, 'cauldron'); s.set(11, 1, 2, 'brewing_stand'); s.set(11, 1, 6, 'brewing_stand')
    s.set(13, 1, 4, 'barrel[facing=up,open=false]')
    for yy in range(1, top + 2): s.set(-1, yy, 4, 'mossy_stone_bricks' if yy % 3 == 0 else 'stone_bricks')
    s.set(-1, top + 2, 4, 'soul_campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    banner(s, 6, 3, 8, 'green'); hanging_sign(s, 8, 4, 8, 'S')
    furnish(s, 0, 0, 10, 8, 1, 'magic'); furnish(s, x0, z0, x1, z1, 6, 'home')
    weathervane(s, 8, top + 1, 4); path_patch(s, 2, 4, 10, 11); scatter(s, 0, 0, 10, 8, 4)
    return finish(s)

# ── 약초 가게 (엘린) ──
def herb_shop():
    s = Bld('start/herb_shop', 31)
    foundation(s, 0, 0, 10, 7)
    stone_story(s, 0, 0, 10, 7, 1, 3)
    door(s, 5, 7, 'S'); window(s, 2, 2, 7, 'S'); window(s, 8, 2, 7, 'S'); window(s, 0, 2, 3, 'W'); window(s, 10, 2, 3, 'E')
    top = gable_roof(s, 0, 0, 10, 7, 4, axis='x', mats=('dark_oak', 'spruce', 'spruce'))
    dormer(s, 5, 8, 5)
    # 말리는 시렁 (짚 묶음 + 다락문) — 식물 없이
    for x in range(1, 10, 2): s.set(x, 3, 9, 'spruce_fence'); s.set(x, 2, 9, 'hay_block[axis=y]') if x % 4 == 1 else s.set(x, 2, 9, 'chain[axis=y]')
    for x in (0, 10):
        for yy in (1, 2, 3): s.set(x, yy, 9, 'spruce_fence')
    for x in range(0, 11): s.set(x, 4, 9, SL('spruce')) if s.get(x, 4, 9) is None else None
    s.set(12, 1, 2, 'composter[level=7]'); s.set(12, 1, 3, 'water_cauldron[level=3]'); s.set(12, 1, 4, 'barrel[facing=up,open=false]')
    banner(s, 3, 3, 7, 'green'); chimney(s, -1, 4, 1, top + 1)
    furnish(s, 0, 0, 10, 7, 1, 'magic'); path_patch(s, 4, 6, 10, 11); scatter(s, 0, 0, 10, 7, 4)
    return finish(s)

# ── 낚시 오두막 (모리) + 선착장 ──
def fishing_hut():
    s = Bld('start/fishing_hut', 32)
    for x in range(0, 7):
        for z in range(0, 7): s.set(x, 0, z, 'spruce_planks')
    for (x, z) in ((0, 0), (6, 0), (0, 6), (6, 6)):
        for yy in range(-3, 0): s.set(x, yy, z, LOG('stripped_spruce_log'))
    for yy in range(1, 4):
        for f, cells in sides_of(0, 0, 6, 6).items():
            for (x, z) in cells: s.set(x, yy, z, LOG('spruce_log', 'x' if f in 'SN' else 'z') if yy != 3 or True else 'spruce_planks')
    for (x, z) in ((0, 0), (6, 0), (0, 6), (6, 6)):
        for yy in range(1, 4): s.set(x, yy, z, LOG('stripped_spruce_log'))
    door(s, 3, 6, 'S', lamps=False, arch=False, step=False)
    window(s, 0, 2, 3, 'W', h=1); window(s, 6, 2, 3, 'E', h=1); window(s, 3, 2, 0, 'N', h=1)
    top = gable_roof(s, 0, 0, 6, 6, 4, axis='z', mats=('dark_oak', 'spruce'))
    for z in range(7, 15):   # 선착장
        for x in range(2, 5): s.set(x, 0, z, 'spruce_planks' if s.r.random() > 0.1 else SL('spruce'))
        if z % 3 == 0:
            for x in (2, 4):
                for yy in range(-3, 1): s.set(x, yy, z, LOG('stripped_spruce_log'))
                s.set(x, 1, z, 'spruce_fence')
    s.set(4, 2, 14, 'lantern[hanging=false,waterlogged=false]') if False else None
    s.set(4, 1, 14, 'spruce_fence'); s.set(4, 2, 14, 'lantern[hanging=false,waterlogged=false]')
    barrels(s, [(2, 1, 13), (2, 1, 12)]); s.set(3, 1, 7, 'barrel[facing=up,open=false]') if False else None
    s.set(7, 1, 2, 'barrel[facing=up,open=false]'); s.set(7, 1, 3, 'barrel[facing=up,open=false]'); s.set(7, 2, 2, 'barrel[facing=up,open=false]')
    hanging_sign(s, 5, 3, 6, 'S')
    furnish(s, 0, 0, 6, 6, 1, 'home')
    return finish(s)

# ── 브람 농가 ──
def farmhouse():
    s = Bld('farm/bram_farmhouse', 33)
    foundation(s, 0, 0, 12, 8)
    stone_story(s, 0, 0, 12, 8, 1, 3)
    door(s, 4, 8, 'S'); window(s, 2, 2, 8, 'S'); window(s, 8, 2, 8, 'S'); window(s, 10, 2, 8, 'S'); window(s, 0, 2, 4, 'W'); window(s, 12, 2, 4, 'E')
    timber_story(s, 0, 0, 12, 8, 4, h=3, post=4, wins=[('E', (12, 4)), ('W', (0, 4))], brace=True)
    top = gable_roof(s, 0, 0, 12, 8, 7, axis='x', mats=('dark_oak', 'spruce', 'spruce'))
    dormer(s, 3, 9, 8); dormer(s, 9, 9, 8)
    for x in range(-1, 14): s.set(x, 0, 10, 'spruce_planks'); s.set(x, 0, 11, 'spruce_planks')   # 긴 앞 툇마루
    for x in range(-1, 14): s.set(x, 4, 10, ST('spruce', 'north')); s.set(x, 3, 11, ST('spruce', 'north')) if False else s.set(x, 4, 11, SL('spruce'))
    for x in (-1, 5, 13):
        for yy in (1, 2, 3): s.set(x, yy, 11, 'spruce_fence')
    bench(s, 7, 10); s.set(1, 1, 10, 'barrel[facing=up,open=false]')
    chimney(s, -1, 4, 1, top + 1); s.set(-1, 0, 4, 'stone_bricks')
    woodpile(s, 14, 2); woodpile(s, 14, 4)
    s.set(14, 1, 7, 'hay_block[axis=y]'); s.set(15, 1, 7, 'hay_block[axis=x]'); s.set(14, 2, 7, 'hay_block[axis=y]')
    for (x, z) in ((15, 9), (16, 9)): s.set(x, 1, z, 'spruce_planks')   # 수레
    s.set(15, 2, 9, 'hay_block[axis=y]'); s.set(14, 1, 9, 'spruce_fence'); s.set(17, 1, 9, LOG('stripped_spruce_log', 'z'))
    furnish(s, 0, 0, 12, 8, 1, 'home'); weathervane(s, 10, top + 1, 4)
    path_patch(s, 3, 5, 12, 13); scatter(s, 0, 0, 12, 8, 6)
    return finish(s)

# ── 헛간 ──
def barn():
    s = Bld('farm/barn', 34)
    X1, Z1 = 12, 10
    foundation(s, 0, 0, X1, Z1, floor='coarse_dirt')
    for yy in range(1, 3):
        for f, cells in sides_of(0, 0, X1, Z1).items():
            for (x, z) in cells: s.set(x, yy, z, s.pick(STONE_LOW if yy == 1 else STONE))
    for yy in range(3, 8):
        for f, cells in sides_of(0, 0, X1, Z1).items():
            for i, (x, z) in enumerate(cells):
                s.set(x, yy, z, LOG('dark_oak_log') if i % 4 == 0 or i == len(cells) - 1 else ('spruce_planks' if yy % 2 else 'stripped_spruce_log[axis=' + ('x' if f in 'SN' else 'z') + ']'))
    # 큰 문 (앞)
    for x in range(4, 9):
        for yy in range(1, 6): s.set(x, yy, Z1, None if False else 'air')
    for yy in range(1, 6): s.set(3, yy, Z1 + 1, LOG('dark_oak_log')); s.set(9, yy, Z1 + 1, LOG('dark_oak_log'))
    for x in range(3, 10): s.set(x, 6, Z1 + 1, LOG('dark_oak_log', 'x'))
    for (x, z) in ((4, Z1 + 1), (8, Z1 + 1)): s.set(x, 1, z, 'spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]') if False else None
    top = gable_roof(s, 0, 0, X1, Z1, 8, axis='z', mats=('dark_oak', 'dark_oak', 'spruce', 'spruce'), gable=[('spruce_planks', 1)])
    # 건초 다락 문
    s.set(6, 10, Z1, 'air'); s.set(6, 11, Z1, 'air'); s.set(6, 12, Z1 + 1, 'dark_oak_fence'); s.set(6, 12, Z1 + 2, 'dark_oak_fence'); s.set(6, 11, Z1 + 2, 'chain[axis=y]')
    for x in range(1, X1):
        for z in range(1, 5): s.set(x, 7, z, 'spruce_planks')
    for (x, z) in ((1, 1), (2, 1), (1, 2), (10, 1), (11, 1), (11, 2), (2, 2), (5, 2), (6, 2), (5, 3)): s.set(x, 8, z, 'hay_block[axis=y]')
    for (x, z) in ((1, 8), (1, 9), (2, 9), (11, 8)): s.set(x, 1, z, 'hay_block[axis=y]')
    for z in range(5, 9): s.set(9, 1, z, 'spruce_fence'); s.set(9, 2, z, 'spruce_fence')
    s.set(6, 6, 6, 'chain[axis=y]'); s.set(6, 5, 6, 'lantern[hanging=true,waterlogged=false]')
    weathervane(s, 6, top + 1, 1); path_patch(s, 4, 8, Z1 + 2, Z1 + 4); scatter(s, 0, 0, X1, Z1, 5)
    s.set(X1 + 2, 1, 3, 'water_cauldron[level=3]'); s.set(X1 + 2, 1, 4, 'hay_block[axis=y]')
    return finish(s)

# ── 곡물 창고 (돌 받침 위 나무 창고) ──
def granary():
    s = Bld('farm/granary', 35)
    for (x, z) in ((0, 0), (6, 0), (0, 6), (6, 6), (3, 0), (3, 6), (0, 3), (6, 3)):
        s.set(x, 0, z, 'stone_bricks'); s.set(x, 1, z, 'stone_brick_wall'); s.set(x, 2, z, SL('smooth_stone'))
    for x in range(0, 7):
        for z in range(0, 7): s.set(x, 3, z, 'spruce_planks')
    for yy in range(4, 7):
        for f, cells in sides_of(0, 0, 6, 6).items():
            for i, (x, z) in enumerate(cells): s.set(x, yy, z, LOG('dark_oak_log') if i in (0, 3, 6) else 'spruce_planks')
    s.set(3, 4, 6, 'spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]'); s.set(3, 5, 6, 'spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
    for k, yy in enumerate(range(0, 3)): s.set(3, yy + 1, 8 - k, ST('spruce', 'north')) if True else None
    top = gable_roof(s, 0, 0, 6, 6, 7, axis='z', mats=('dark_oak', 'spruce'), gable=[('spruce_planks', 1)])
    s.set(8, 1, 2, 'hay_block[axis=y]'); s.set(8, 1, 3, 'hay_block[axis=x]'); s.set(8, 2, 2, 'hay_block[axis=y]')
    return finish(s)

# ── 풍차 ──
def windmill():
    """높은 풍차: 돌 1층(9칸) + 반목조 몸통(위로 갈수록 살짝 좁아짐) + 원뿔 지붕 + 큰 날개 4개"""
    s = Bld('farm/windmill', 36); cx, cz, R = 6, 6, 5
    for x in range(0, 13):
        for z in range(0, 13):
            d = math.hypot(x - cx, z - cz)
            if d <= R + 0.4: s.set(x, 0, z, 'stone_bricks')
            elif d <= R + 1.4:
                dx, dz = cx - x, cz - z
                s.set(x, 0, z, ST('stone_brick', ('east' if dx > 0 else 'west') if abs(dx) >= abs(dz) else ('south' if dz > 0 else 'north')))
    TOPY = 24
    for yy in range(1, TOPY):
        rr = R if yy <= 9 else R - (yy - 9) * 0.09
        for x in range(0, 13):
            for z in range(0, 13):
                d = math.hypot(x - cx, z - cz)
                if rr - 0.95 <= d <= rr + 0.35:
                    if yy <= 9: m = s.pick(STONE_LOW if yy == 1 else STONE)
                    elif yy in (10, 17): m = LOG('stripped_dark_oak_log', 'x')
                    else: m = LOG('dark_oak_log') if (round(math.atan2(z - cz, x - cx) / (math.pi / 4))) % 2 == 0 and abs(math.hypot(x - cx, z - cz) - rr) < 0.5 and (x == cx or z == cz or abs(x - cx) == abs(z - cz)) else s.pick(INFILL)
                    s.set(x, yy, z, m)
    for (dx, dz, f) in ((0, 1, 'S'), (1, 0, 'E'), (0, -1, 'N'), (-1, 0, 'W')):   # 층마다 창
        for yy in (4, 13, 20):
            r_ = R if yy <= 9 else int(R - (yy - 9) * 0.09 + 0.3)
            s.set(cx + dx * r_, yy, cz + dz * r_, 'glass_pane'); s.set(cx + dx * r_, yy + 1, cz + dz * r_, 'glass_pane')
    for x in range(0, 13):   # 돌층 위 받침 띠
        for z in range(0, 13):
            d = math.hypot(x - cx, z - cz)
            if R - 0.2 <= d <= R + 0.9 and s.get(x, 10, z) is None:
                dx, dz = cx - x, cz - z
                s.set(x, 10, z, ST('dark_oak', ('east' if dx > 0 else 'west') if abs(dx) >= abs(dz) else ('south' if dz > 0 else 'north'), 'top'))
    s.set(cx, 1, cz + R, 'spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]'); s.set(cx, 2, cz + R, 'spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
    s.set(cx, 3, cz + R, SL('stone_brick', 'top')); s.set(cx - 1, 3, cz + R + 1, 'lantern[hanging=false,waterlogged=false]'); s.set(cx + 1, 3, cz + R + 1, 'lantern[hanging=false,waterlogged=false]')
    top = cone(s, cx, cz, R + 1, TOPY, mats=('dark_oak', 'spruce', 'spruce', 'dark_oak'), step=0.45)
    # 날개: 정면 축 + 4개 (길이 12)
    hy = 19; hz = cz + R + 2
    for z in range(cz + R - 1, hz + 1): s.set(cx, hy, z, LOG('dark_oak_log', 'z'))
    s.set(cx, hy, hz + 1, 'dark_oak_fence')
    for (dx, dy) in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        ox, oy = (dy, -dx)
        for k in range(1, 13):
            x, yy = cx + dx * k, hy + dy * k
            s.set(x, yy, hz, 'dark_oak_fence')
            if 3 <= k <= 12:
                ax = 'x' if dx == 0 else 'y'
                for w in (1, 2, 3):
                    s.set(x + ox * w, yy + oy * w, hz, 'white_wool' if k % 3 else f'stripped_dark_oak_log[axis={ax}]')
                s.set(x + 4 * ox, yy + 4 * oy, hz, 'dark_oak_fence')
    for (x, z) in ((cx + R + 2, cz - 1), (cx + R + 2, cz)): s.set(x, 1, z, 'hay_block[axis=y]')
    s.set(cx + R + 3, 1, cz - 1, 'hay_block[axis=x]'); s.set(cx - R - 2, 1, cz, 'barrel[facing=up,open=false]'); s.set(cx - R - 2, 1, cz + 1, 'barrel[facing=up,open=false]')
    return finish(s)

# ── 경비 초소 (루크) ──
def guard_post():
    s = Bld('gate/guard_post', 37)
    foundation(s, 0, 0, 6, 6)
    stone_story(s, 0, 0, 6, 6, 1, 7)
    door(s, 3, 6, 'S'); window(s, 3, 4, 0, 'N', h=1, shutters=False); window(s, 0, 4, 3, 'W', h=1, shutters=False); window(s, 6, 4, 3, 'E', h=1, shutters=False)
    for x in range(-1, 8):   # 망루 바닥 (튀어나옴) + 받침
        for z in range(-1, 8):
            if x in (-1, 7) or z in (-1, 7): s.set(x, 7, z, ST('stone_brick', 'south' if z == -1 else 'north' if z == 7 else 'east' if x == -1 else 'west', 'top'))
            s.set(x, 8, z, 'spruce_planks')
    for x in range(-1, 8):
        for z in range(-1, 8):
            if x in (-1, 7) or z in (-1, 7): s.set(x, 9, z, 'stone_brick_wall' if (x + z) % 2 else 'stone_bricks')
    for (x, z) in ((-1, -1), (7, -1), (-1, 7), (7, 7)):
        for yy in range(9, 12): s.set(x, yy, z, LOG('dark_oak_log'))
    top = gable_roof(s, -1, -1, 7, 7, 12, axis='x', mats=('dark_oak', 'spruce'), gable=[('air', 1)], gable_frame=False)
    for x in (-1, 7):
        for z in range(-1, 8):
            for yy in range(12, 16):
                if s.get(x, yy, z) == 'minecraft:air': s.set(x, yy, z, None)
    s.set(3, 10, 7, 'lantern[hanging=false,waterlogged=false]') if False else None
    banner(s, 1, 6, 6, 'blue'); banner(s, 5, 6, 6, 'blue')
    for x in (8, 9): s.set(x, 1, 2, 'spruce_fence'); s.set(x, 2, 2, 'iron_bars')
    barrels(s, [(8, 1, 4), (8, 1, 5)]); s.set(-2, 1, 4, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]'); bench(s, -3, 6) if False else None
    s.set(3, 9, 3, 'bell[attachment=ceiling,facing=north,powered=false]') if False else None
    for yy in range(1, 8): s.set(5, yy, 1, 'ladder[facing=west,waterlogged=false]')
    return finish(s)

# ── 사냥꾼 오두막 (로완) ──
def hunter_hut():
    s = Bld('gate/hunter_hut', 38)
    foundation(s, 0, 0, 7, 6, floor='spruce_planks')
    for yy in range(1, 4):
        for f, cells in sides_of(0, 0, 7, 6).items():
            for (x, z) in cells: s.set(x, yy, z, LOG('spruce_log', 'x' if f in 'SN' else 'z'))
    for (x, z) in ((0, 0), (7, 0), (0, 6), (7, 6)):
        for yy in range(1, 5): s.set(x, yy, z, LOG('stripped_spruce_log'))
    door(s, 3, 6, 'S', lamps=False, arch=False); window(s, 6, 2, 6, 'S', h=1); window(s, 0, 2, 3, 'W', h=1); window(s, 7, 2, 3, 'E', h=1)
    top = gable_roof(s, 0, 0, 7, 6, 4, axis='x', mats=('dark_oak', 'dark_oak', 'spruce'), full='dark_oak_planks', gable=[('spruce_planks', 1)])
    chimney(s, -1, 3, 1, top + 1)
    # 말리는 틀 (울타리 + 사슬) · 과녁 · 장작 · 통
    for x in (9, 12):
        for yy in (1, 2, 3): s.set(x, yy, 2, 'spruce_fence')
    for x in (10, 11): s.set(x, 3, 2, 'spruce_fence'); s.set(x, 2, 2, 'chain[axis=y]')
    s.set(10, 1, 5, 'hay_block[axis=y]'); s.set(10, 2, 5, 'target')
    woodpile(s, 1, 8); barrels(s, [(8, 1, 6)]); s.set(5, 1, 8, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    s.set(4, 1, 8, ST('spruce', 'east')); s.set(6, 1, 8, ST('spruce', 'west'))
    banner(s, 1, 3, 6, 'green'); furnish(s, 0, 0, 7, 6, 1, 'home')
    return finish(s)

ALL2 = [(fountain, '광장 분수 + 게시판'), (general_store, '잡화점'), (gear_shop, '장비 상점'), (well, '우물'), (stalls, '시장 노점 3칸'),
        (house1, '주민 집 1'), (house2, '주민 집 2'), (house3, '주민 집 3'), (jeweler, '세공소'), (alchemy, '연금술 공방'),
        (herb_shop, '약초 가게 (엘린)'), (fishing_hut, '낚시 오두막 (모리)'), (farmhouse, '브람 농가'), (barn, '헛간'),
        (granary, '곡물 창고'), (windmill, '풍차'), (guard_post, '경비 초소 (루크)'), (hunter_hut, '사냥꾼 오두막 (로완)')]

if __name__ == '__main__':
    from PIL import Image, ImageDraw, ImageFont
    F = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 22, index=1)
    tiles = []
    for fn, title in ALL2:
        s = fn(); x0, y0, z0, x1, y1, z1 = s.bounds()
        im = render2(view2(s, 0), f'out/{s.name.split("/")[1]}.png', scale=11, bg=(150, 190, 230))
        c = Image.new('RGB', (im.width, im.height + 36), (150, 190, 230)); c.paste(im, (0, 36))
        ImageDraw.Draw(c).text((10, 4), f'{title} ({x1 - x0 + 1}×{z1 - z0 + 1}, 높이 {y1 - y0 + 1})', font=F, fill=(20, 30, 50)); tiles.append(c)
    cols = 3; W = max(t.width for t in tiles)
    rows = [tiles[i:i + cols] for i in range(0, len(tiles), cols)]
    H = sum(max(t.height for t in r) for r in rows)
    sh = Image.new('RGB', (W * cols, H), (150, 190, 230)); y = 0
    for r in rows:
        for i, t in enumerate(r): sh.paste(t, (i * W, y))
        y += max(t.height for t in r)
    sh.save('a2_rest.png'); print(sh.size)
