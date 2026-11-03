from alib import *
import math

def dormer(s, xc, zf, y, roofm=('dark_oak', 'spruce')):
    """남쪽 지붕창: 3칸 폭, 앞은 반목조 + 창, 위에 작은 박공"""
    for z in range(zf - 2, zf + 1):
        for x in (xc - 1, xc + 1): s.set(x, y, z, LOG('dark_oak_log')); s.set(x, y + 1, z, LOG('dark_oak_log'))
        s.set(xc, y, z, 'air'); s.set(xc, y + 1, z, 'air')
    s.set(xc, y, zf, 'glass_pane'); s.set(xc, y + 1, zf, 'glass_pane')
    gable_roof(s, xc - 1, zf - 2, xc + 1, zf, y + 2, axis='z', over=1, mats=roofm, gable_frame=False)

def cone(s, cx, cz, R, y, mats=('dark_oak', 'spruce'), full='spruce_planks', tip='lightning_rod[facing=up,powered=false,waterlogged=false]', step=0.5):
    """원뿔 지붕: 층마다 반지름을 조금씩 줄이고, 테두리 칸만 계단 (빈 이웃 쪽을 등지게)
    - 테두리에서 비어 있는 이웃이 한 방향이면 그 반대를 바라보는 계단, 두 방향(모서리)이면 반블록"""
    k = 0
    while True:
        rr = R - k * step + 0.35
        if rr < 0.6: break
        disk = {(x, z) for x in range(cx - R - 1, cx + R + 2) for z in range(cz - R - 1, cz + R + 2) if math.hypot(x - cx, z - cz) <= rr}
        m = mats[0] if k == 0 else mats[min(len(mats) - 1, 1 + (k * (len(mats) - 1)) // max(1, int(R / step)))]
        for (x, z) in disk:
            out = [d for d, (dx, dz) in (('north', (0, -1)), ('south', (0, 1)), ('west', (-1, 0)), ('east', (1, 0))) if (x + dx, z + dz) not in disk]
            if not out:
                if s.get(x, y + k, z) is None and math.hypot(x - cx, z - cz) > rr - 2.2: s.set(x, y + k, z, full)
                continue
            if len(out) == 1:
                f = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}[out[0]]
                s.set(x, y + k, z, ST(m, f))
            else:
                s.set(x, y + k, z, SL(m))
        k += 1
    s.set(cx, y + k, cz, full); s.set(cx, y + k + 1, cz, 'dark_oak_fence'); s.set(cx, y + k + 2, cz, tip)
    return y + k

def lean_to(s, x0, x1, z_wall, depth, y_top, f='S', mat='spruce', posts=True):
    """벽에 기댄 한쪽 지붕 (현관·작업장 차양)"""
    dx, dz = OUT[f]
    for k in range(depth):
        for x in range(x0, x1 + 1):
            s.set(x, y_top - k // 2 if False else y_top - (k // 2), z_wall + dz * (k + 1), ST(mat, FACE[OPP[f]]) if k % 2 == 1 or depth == 1 else SL(mat, 'top'))
    if posts:
        zz = z_wall + dz * depth
        for x in (x0, x1) + tuple(range(x0 + 4, x1 - 2, 4)):
            for y in range(1, y_top - (depth - 1) // 2): s.set(x, y, zz, LOG('stripped_dark_oak_log'))

# ───────── 1. 촌장 해럴드의 집 ─────────
def harold():
    s = Bld('start/harold_house', 11)
    X1, Z1 = 14, 10
    foundation(s, 0, 0, X1, Z1)
    stone_story(s, 0, 0, X1, Z1, 1, 4)
    for x in (2, 12): window(s, x, 2, Z1, 'S')
    for x in (3, 7, 11): window(s, x, 2, 0, 'N')
    for z in (3, 7): window(s, 0, 2, z, 'W'); window(s, X1, 2, z, 'E')
    jetty(s, 0, 0, X1, Z1, 4, sides='SN')
    wins = [('S', (x, Z1 + 1)) for x in (2, 12)] + [('N', (x, -1)) for x in (2, 7, 12)] + [('E', (X1, z)) for z in (2, 8)] + [('W', (0, z)) for z in (2, 8)]
    timber_story(s, 0, -1, X1, Z1 + 1, 5, h=4, post=3, wins=wins)
    top = gable_roof(s, 0, -1, X1, Z1 + 1, 9, axis='x')
    gable_window(s, 'x', 0, -1, X1, Z1 + 1, 11)
    # 앞 날개 (십자 박공): 현관 + 위층 발코니 창
    wx0, wx1, wz0, wz1 = 5, 9, Z1 + 1, Z1 + 4
    foundation(s, wx0, wz0, wx1, wz1, skirt=False)
    stone_story(s, wx0, wz0, wx1, wz1, 1, 4)
    for x in range(wx0 + 1, wx1): s.set(x, 1, wz0, 'air'); s.set(x, 2, wz0, 'air'); s.set(x, 3, wz0, 'air')
    door(s, 7, wz1, 'S')
    window(s, wx0, 2, wz1 - 1, 'W'); window(s, wx1, 2, wz1 - 1, 'E')
    timber_story(s, wx0, wz0, wx1, wz1, 5, h=4, post=2, wins=[('S', (7, wz1))], brace=False)
    gable_roof(s, wx0, wz0, wx1, wz1 + 1, 9, axis='z', over=1)
    for x in range(wx0 - 1, wx1 + 2): s.set(x, 5, wz1 + 1, 'dark_oak_fence') if x not in (wx0 - 1, wx1 + 1) else s.set(x, 5, wz1 + 1, LOG('dark_oak_log'))
    for x in range(wx0, wx1 + 1): s.set(x, 4, wz1 + 1, SL('dark_oak', 'top'))
    for x in (wx0 - 1, wx1 + 1):
        for y in range(1, 5): s.set(x, y, wz1 + 1, LOG('dark_oak_log'))
    banner(s, 6, 4, wz1, 'red'); banner(s, 8, 4, wz1, 'red')
    dormer(s, 2, Z1 + 1, 10); dormer(s, 12, Z1 + 1, 10)
    chimney(s, -1, 4, 1, top + 2, w=1); s.set(-1, 0, 4, 'stone_bricks')
    chimney(s, X1 + 1, 6, 1, top + 1, w=1); s.set(X1 + 1, 0, 6, 'stone_bricks')
    hanging_sign(s, 9, 4, wz1, 'S') if False else None
    path_patch(s, 6, 8, wz1 + 2, wz1 + 5)
    lamp_post(s, 4, wz1 + 4); lamp_post(s, 10, wz1 + 4)
    furnish(s, 0, 0, X1, Z1, 1, 'home'); furnish(s, 0, -1, X1, Z1 + 1, 6, 'home')
    corner_lamps(s, 0, -1, X1, Z1 + 1, 7); weathervane(s, 7, top + 1, (Z1) // 2)
    scatter(s, 0, 0, X1, Z1, 8); crates(s, X1 + 2, 2); s.set(X1 + 2, 1, 1, 'hay_block[axis=y]')
    bench(s, 0, Z1 + 3); bench(s, 13, Z1 + 3)
    return finish(s)

# ───────── 2. 여관 ─────────
def inn():
    s = Bld('start/inn', 12)
    X1, Z1 = 16, 10
    foundation(s, 0, 0, X1, Z1)
    stone_story(s, 0, 0, X1, Z1, 1, 4)
    door(s, 8, Z1, 'S', lamps=False)
    for x in (3, 5, 11, 13): window(s, x, 2, Z1, 'S')
    for z in (3, 7): window(s, 0, 2, z, 'W'); window(s, X1, 2, z, 'E')
    jetty(s, 0, 0, X1, Z1, 4, sides='SNEW')
    wins1 = [('S', (x, Z1 + 1)) for x in (2, 5, 8, 11, 14)] + [('E', (X1 + 1, z)) for z in (2, 5, 8)] + [('W', (-1, z)) for z in (2, 5, 8)]
    timber_story(s, -1, -1, X1 + 1, Z1 + 1, 5, h=4, post=3, wins=wins1)
    jetty(s, -1, -1, X1 + 1, Z1 + 1, 8, sides='SN')
    wins2 = [('S', (x, Z1 + 2)) for x in (2, 5, 11, 14)]
    timber_story(s, -1, -2, X1 + 1, Z1 + 2, 9, h=4, post=3, wins=wins2)
    top = gable_roof(s, -1, -2, X1 + 1, Z1 + 2, 13, axis='x')
    gable_window(s, 'x', -1, -2, X1 + 1, Z1 + 2, 15)
    dormer(s, 4, Z1 + 3, 14); dormer(s, 12, Z1 + 3, 14)
    # 앞 테라스: 기둥 + 얕은 차양 (정면 안 가리게 2칸)
    for x in range(1, X1): s.set(x, 0, Z1 + 1, 'spruce_planks'); s.set(x, 0, Z1 + 2, 'spruce_planks'); s.set(x, 0, Z1 + 3, 'spruce_planks')
    for x in range(1, X1): s.set(x, 4, Z1 + 2, ST('spruce', 'north'))
    for x in (1, 6, 10, X1 - 1):
        for y in (1, 2, 3): s.set(x, y, Z1 + 2, 'spruce_fence')
    for x in (3, 13): s.set(x, 1, Z1 + 2, 'barrel[facing=up,open=false]'); s.set(x - 1, 1, Z1 + 2, ST('spruce', 'east')); s.set(x + 1, 1, Z1 + 2, ST('spruce', 'west'))
    s.set(8, 0, Z1 + 1, 'spruce_planks')
    banner(s, 7, 4, Z1, 'yellow') if False else None
    hanging_sign(s, 8, 7, Z1 + 1, 'S')
    s.set(8, 6, Z1 + 3, 'lantern[hanging=true,waterlogged=false]') if False else None
    chimney(s, X1 + 2, 5, 1, top + 2, w=1); s.set(X1 + 2, 0, 5, 'stone_bricks')
    for y in range(5, 12): s.set(X1 + 2, y, 4, 'bricks') if False else None
    path_patch(s, 6, 10, Z1 + 4, Z1 + 6)
    lamp_post(s, 0, Z1 + 5); lamp_post(s, X1, Z1 + 5)
    crates(s, X1 + 1, Z1 + 1); woodpile(s, -3, 3)
    furnish(s, 0, 0, X1, Z1, 1, 'inn'); furnish(s, -1, -1, X1 + 1, Z1 + 1, 6, 'home'); furnish(s, -1, -2, X1 + 1, Z1 + 2, 10, 'home')
    corner_lamps(s, -1, -1, X1 + 1, Z1 + 1, 7); weathervane(s, 3, top + 1, Z1 // 2)
    for x in (2, 14): s.set(x, 1, Z1 + 4, 'hay_block[axis=y]') if False else None
    s.set(X1 + 1, 1, Z1 + 3, 'hay_block[axis=y]'); s.set(X1 + 2, 1, Z1 + 3, 'hay_block[axis=x]')
    scatter(s, 0, 0, X1, Z1, 8)
    return finish(s)

# ───────── 3. 대장간 ─────────
def smithy():
    s = Bld('start/smithy', 13)
    # 본채: 돌 1층 + 반목조 2층 (튀어나옴), 박공은 앞을 봄
    X1, Z1 = 8, 8
    foundation(s, 0, 0, X1, Z1)
    stone_story(s, 0, 0, X1, Z1, 1, 4)
    door(s, 4, Z1, 'S')
    window(s, 2, 2, Z1, 'S'); window(s, 6, 2, Z1, 'S'); window(s, 0, 2, 4, 'W')
    jetty(s, 0, 0, X1, Z1, 4, sides='SN')
    timber_story(s, 0, -1, X1, Z1 + 1, 5, h=4, post=4, wins=[('S', (x, Z1 + 1)) for x in (2, 6)] + [('W', (0, 4))])
    top = gable_roof(s, 0, -1, X1, Z1 + 1, 9, axis='z')
    hanging_sign(s, 1, 4, Z1, 'S')
    # 옆 작업장 (동쪽): 낮은 돌벽 + 원목 기둥 + 가볍게 기울어진 가문비 지붕 (3줄) — 앞·옆 트임
    WX0, WX1, WZ0, WZ1 = 9, 15, 1, 8
    for x in range(WX0, WX1 + 1):
        for z in range(WZ0, WZ1 + 1): s.set(x, 0, z, s.pick([('smooth_stone', 45), ('stone_bricks', 35), ('cobblestone', 20)]))
    for x in range(WX0, WX1 + 1): s.set(x, 1, WZ0, 'stone_bricks'); s.set(x, 2, WZ0, 'cobblestone_wall')
    for (x, z) in ((WX1, WZ0), (WX1, WZ1), (12, WZ1), (WX1, 4)):
        for y in range(1, 5): s.set(x, y, z, LOG('stripped_dark_oak_log'))
    for z in range(WZ0, WZ1 + 1): s.set(WX1, 4, z, LOG('dark_oak_log', 'z'))
    for x in range(WX0, WX1 + 1): s.set(x, 4, WZ1, LOG('dark_oak_log', 'x'))
    # 지붕: 동쪽으로 뻗은 작은 박공 (동쪽 끝은 벽 없이 들보만 → 안이 보임)
    gable_roof(s, WX0, WZ0, WX1, WZ1, 5, axis='x', over=1, gable_frame=False, gable=[('air', 1)])
    for z in range(WZ0, WZ1 + 1):
        for y in range(5, 9):
            if s.get(WX1, y, z) == 'minecraft:air': s.set(WX1, y, z, None)
    s.set(WX1, 5, (WZ0 + WZ1) // 2, LOG('dark_oak_log', 'z')); s.set(WX1, 6, (WZ0 + WZ1) // 2, LOG('dark_oak_log', 'y'))
    for z in range(WZ0, WZ1 + 1): s.set(WX1, 5, z, LOG('dark_oak_log', 'z'))
    for y in range(1, 5): s.set(WX1, y, 4, None)
    # 화덕 + 굴뚝 (2×2 벽돌, 지붕 위로)
    for x in (10, 11):
        for z in (1, 2):
            for y in range(1, 13): s.set(x, y, z, 'bricks' if y > 2 and s.r.random() > 0.15 else 'stone_bricks')
    s.set(10, 1, 3, 'blast_furnace[facing=south,lit=true]'); s.set(11, 1, 3, 'blast_furnace[facing=south,lit=true]')
    s.set(10, 2, 3, ST('stone_brick', 'north', 'top')); s.set(11, 2, 3, ST('stone_brick', 'north', 'top'))
    for x in (10, 11): s.set(x, 13, 1, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]'); s.set(x, 13, 2, SL('stone_brick'))
    s.set(12, 1, 2, 'lava_cauldron')
    s.set(11, 1, 5, 'anvil[facing=east]'); s.set(13, 1, 5, 'anvil[facing=north]')
    s.set(14, 1, 2, 'water_cauldron[level=3]'); s.set(14, 1, 7, 'grindstone[face=floor,facing=south]'); s.set(10, 1, 7, 'smithing_table')
    s.set(12, 3, 5, 'chain[axis=y]'); s.set(12, 2, 5, 'lantern[hanging=true,waterlogged=false]')
    # 앞 무기 진열대 + 장작 + 석탄 통
    for x in range(10, 15): s.set(x, 1, WZ1 + 2, 'spruce_fence'); s.set(x, 2, WZ1 + 2, SL('spruce'))
    for x in (10, 12, 14): s.set(x, 2, WZ1 + 2, 'iron_bars') if False else None
    s.set(9, 1, WZ1 + 2, 'barrel[facing=up,open=false]'); s.set(15, 1, WZ1 + 2, 'barrel[facing=up,open=false]')
    woodpile(s, 10, -1); woodpile(s, 13, -1)
    s.set(16, 1, 0, 'coal_block'); s.set(16, 1, -1, 'barrel[facing=up,open=false]')
    path_patch(s, 3, 5, Z1 + 2, Z1 + 4); path_patch(s, 9, 15, WZ1 + 1, WZ1 + 1)
    furnish(s, 0, 0, X1, Z1, 1, 'smith'); furnish(s, 0, -1, X1, Z1 + 1, 6, 'home')
    corner_lamps(s, 0, -1, X1, Z1 + 1, 7); scatter(s, 0, 0, X1, Z1, 6)
    return finish(s)

# ───────── 4. 전사 수련장 ─────────
def warrior():
    s = Bld('start/trainer_warrior', 14)
    X1, Z1 = 12, 8
    foundation(s, 0, 0, X1, Z1)
    stone_story(s, 0, 0, X1, Z1, 1, 5)
    door(s, 6, Z1, 'S')
    for x in (2, 10): window(s, x, 2, Z1, 'S', h=3)
    for z in (2, 6): window(s, 0, 2, z, 'W'); window(s, X1, 2, z, 'E')
    for x in range(0, X1 + 1):   # 돌 처마 띠
        s.set(x, 6, Z1 + 1, ST('stone_brick', 'north', 'top')); s.set(x, 6, -1, ST('stone_brick', 'south', 'top'))
    timber_story(s, 0, 0, X1, Z1, 6, h=3, post=3, wins=[('S', (x, Z1)) for x in (3, 9)])
    top = gable_roof(s, 0, 0, X1, Z1, 9, axis='x', mats=('dark_oak', 'dark_oak', 'spruce'))
    gable_window(s, 'x', 0, 0, X1, Z1, 11)
    # 정면 탑문 (작은 망루 박공)
    for y in range(1, 9):
        for x in (4, 8): s.set(x, y, Z1 + 1, 'stone_bricks' if y % 3 else 'polished_andesite')
    for x in range(4, 9): s.set(x, 8, Z1 + 1, 'stone_bricks'); s.set(x, 9, Z1 + 1, 'stone_brick_wall' if x % 2 == 0 else 'air')
    banner(s, 4, 6, Z1 + 1, 'red'); banner(s, 8, 6, Z1 + 1, 'red')
    s.set(6, 6, Z1 + 1, 'stone_bricks'); banner(s, 6, 6, Z1 + 1, 'red')
    # 마당: 자갈 + 울타리 + 허수아비 + 무기 걸이
    for x in range(-1, 21):
        for z in range(Z1 + 2, Z1 + 11):
            s.set(x, 0, z, s.pick([('gravel', 45), ('coarse_dirt', 35), ('dirt_path', 20)]))
    for x in range(-1, 21):
        if x not in (5, 6, 7): s.set(x, 1, Z1 + 11, 'spruce_fence')
    for z in range(Z1 + 2, Z1 + 12): s.set(21, 1, z, 'spruce_fence'); s.set(-2, 1, z, 'spruce_fence')
    for (x, z) in ((14, Z1 + 4), (17, Z1 + 4), (14, Z1 + 8), (17, Z1 + 8), (1, Z1 + 6)):
        s.set(x, 1, z, 'spruce_fence'); s.set(x, 2, z, 'hay_block[axis=y]'); s.set(x, 3, z, SL('spruce'))
        s.set(x - 1, 2, z, 'spruce_fence'); s.set(x + 1, 2, z, 'spruce_fence')
    for x in range(13, 20): s.set(x, 1, 0, 'spruce_planks'); s.set(x, 2, 0, 'spruce_planks'); s.set(x, 3, 0, ST('spruce', 'north'))
    for x in range(13, 20, 2): s.set(x, 2, 1, 'iron_bars')
    barrels(s, [(19, 1, 2), (19, 1, 3)])
    furnish(s, 0, 0, X1, Z1, 1, 'shop'); weathervane(s, 2, top + 1, Z1 // 2)
    for (x, z) in ((3, Z1 + 9), (9, Z1 + 9)): s.set(x, 1, z, 'spruce_fence'); s.set(x, 2, z, SL('spruce'))   # 의자 대신 쉬는 말뚝
    s.set(-1, 1, Z1 + 3, 'water_cauldron[level=3]'); s.set(-1, 1, Z1 + 4, 'barrel[facing=up,open=false]')
    return finish(s)

# ───────── 5. 궁수 수련장 ─────────
def archer():
    s = Bld('start/trainer_archer', 15)
    X1, Z1 = 8, 8
    foundation(s, 0, 0, X1, Z1)
    stone_story(s, 0, 0, X1, Z1, 1, 4)
    door(s, 4, Z1, 'S')
    window(s, 2, 2, 0, 'N'); window(s, 6, 2, 0, 'N'); window(s, X1, 2, 4, 'E'); window(s, 0, 2, 4, 'W')
    jetty(s, 0, 0, X1, Z1, 4, sides='SN')
    timber_story(s, 0, -1, X1, Z1 + 1, 5, h=4, post=2, wins=[('S', (x, Z1 + 1)) for x in (2, 6)] + [('E', (X1, 4))], brace=False)
    jetty(s, 0, -1, X1, Z1 + 1, 8, sides='SN')
    timber_story(s, 0, -2, X1, Z1 + 2, 9, h=3, post=2, wins=[('S', (4, Z1 + 2))], brace=False)
    top = gable_roof(s, 0, -2, X1, Z1 + 2, 12, axis='z', over=1)
    banner(s, 3, 4, Z1, 'green'); banner(s, 5, 4, Z1, 'green')
    # 사격장 (동쪽): 사대(지붕 있는) + 과녁 4개 (짚 받침, 12칸 거리)
    for x in range(10, 28):
        for z in range(0, 9): s.set(x, 0, z, 'dirt_path' if z in (2, 6) and x > 13 else s.pick([('coarse_dirt', 30), ('gravel', 20), ('dirt_path', 50)]) if x <= 13 else None) if (x <= 13 or z in (2, 6)) else None
    for z in range(0, 9): s.set(10, 1, z, SL('spruce', 'bottom')); s.set(13, 1, z, 'spruce_fence') if z % 2 == 0 else None
    for (x, z) in ((10, 0), (10, 8), (13, 0), (13, 8)):
        for y in range(1, 4): s.set(x, y, z, LOG('stripped_dark_oak_log'))
    for x in range(9, 15):
        for z in range(-1, 10): s.set(x, 4, z, SL('dark_oak'))
    for z in (1, 3, 5, 7):
        s.set(26, 1, z, 'hay_block[axis=y]'); s.set(26, 2, z, 'hay_block[axis=y]'); s.set(26, 3, z, 'target'); s.set(26, 4, z, 'target')
        s.set(27, 1, z, 'spruce_fence'); s.set(27, 2, z, 'spruce_fence')
    s.set(11, 1, 1, 'fletching_table'); barrels(s, [(12, 1, 1)])
    furnish(s, 0, 0, X1, Z1, 1, 'home'); furnish(s, 0, -1, X1, Z1 + 1, 6, 'home')
    corner_lamps(s, 0, -1, X1, Z1 + 1, 7); weathervane(s, 4, top + 1, Z1 // 2); scatter(s, 0, 0, X1, Z1, 6)
    for x in (11, 12): s.set(x, 1, 8, 'hay_block[axis=y]')
    s.set(12, 2, 8, 'hay_block[axis=x]')
    return finish(s)

# ───────── 6. 마법사 탑 ─────────
def mage():
    s = Bld('start/trainer_mage', 16)
    cx, cz, R = 4, 4, 4
    for x in range(cx - R - 1, cx + R + 2):
        for z in range(cz - R - 1, cz + R + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= R + 0.4: s.set(x, 0, z, 'polished_deepslate' if d < R - 0.6 else 'stone_bricks')
            elif d <= R + 1.4: s.set(x, 0, z, ST('stone_brick', 'west' if x > cx else 'east') if abs(x - cx) >= abs(z - cz) else ST('stone_brick', 'north' if z > cz else 'south'))
    for y in range(1, 16):
        for x in range(cx - R, cx + R + 1):
            for z in range(cz - R, cz + R + 1):
                d = math.hypot(x - cx, z - cz)
                if R - 0.9 <= d <= R + 0.4:
                    m = s.pick(STONE_LOW if y <= 1 else STONE)
                    if y in (6, 11): m = 'polished_andesite'
                    s.set(x, y, z, m)
    for y in (3, 8, 13):   # 창 (네 방향, 깊게)
        s.set(cx, y, cz + R, 'glass_pane'); s.set(cx, y, cz + R - 1, 'air'); s.set(cx, y + 1, cz + R, 'glass_pane'); s.set(cx, y + 1, cz + R - 1, 'air')
        s.set(cx + R, y, cz, 'glass_pane'); s.set(cx + R - 1, y, cz, 'air'); s.set(cx + R, y + 1, cz, 'glass_pane'); s.set(cx + R - 1, y + 1, cz, 'air')
    # 꼭대기 반목조 방 (튀어나옴) + 받침
    R2 = R + 1
    for x in range(cx - R2, cx + R2 + 1):
        for z in range(cz - R2, cz + R2 + 1):
            d = math.hypot(x - cx, z - cz)
            if R2 - 0.9 <= d <= R2 + 0.4:
                s.set(x, 16, z, LOG('stripped_dark_oak_log', 'x'))
                for y in (17, 18, 19, 20): s.set(x, y, z, LOG('dark_oak_log') if (x + z) % 3 == 0 else s.pick(INFILL))
                if abs(x - cx) == R2 or abs(z - cz) == R2: s.set(x, 18, z, 'glass_pane'); s.set(x, 19, z, 'glass_pane')
            if R - 0.2 <= d <= R + 0.9 and s.get(x, 15, z) is None:
                dx, dz = cx - x, cz - z
                f = ('east' if dx > 0 else 'west') if abs(dx) >= abs(dz) else ('south' if dz > 0 else 'north')
                s.set(x, 15, z, ST('dark_oak', f, 'top'))
    cone(s, cx, cz, R2 + 1, 21, mats=('dark_oak', 'spruce', 'spruce', 'dark_oak'), step=0.4)
    s.set(cx, 1, cz + R, 'spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]'); s.set(cx, 2, cz + R, 'spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
    s.set(cx, 3, cz + R, SL('stone_brick', 'top')); s.set(cx - 1, 3, cz + R + 1, 'lantern[hanging=false,waterlogged=false]'); s.set(cx + 1, 3, cz + R + 1, 'lantern[hanging=false,waterlogged=false]')
    banner(s, cx - 1, 6, cz + R - 1 + 0, 'purple') if False else None
    s.set(cx - 2, 7, cz + R, 'purple_wall_banner[facing=south]'); s.set(cx + 2, 7, cz + R, 'purple_wall_banner[facing=south]')
    s.set(cx + R + 1, 1, cz + 2, 'amethyst_block'); s.set(cx + R + 1, 2, cz + 2, 'amethyst_cluster[facing=up,waterlogged=false]')
    s.set(cx - R - 1, 1, cz + 2, 'amethyst_block'); s.set(cx - R - 1, 1, cz + 3, 'budding_amethyst') if False else None
    s.set(cx + 2, 1, cz + R + 2, 'lectern[facing=south,has_book=true,powered=false]')
    for (x, z) in ((cx - 2, cz - 2), (cx + 2, cz - 2), (cx - 2, cz + 1)): s.set(x, 1, z, 'bookshelf')
    s.set(cx, 1, cz - 2, 'enchanting_table'); s.set(cx + 2, 1, cz + 1, 'brewing_stand'); s.set(cx, 17, cz, 'bookshelf')
    for y in range(1, 16): s.set(cx - 2, y, cz, 'ladder[facing=east,waterlogged=false]') if False else None
    for (x, z) in ((cx - R - 1, cz - 2), (cx + R + 1, cz - 2)):
        s.set(x, 0, z, 'stone_bricks'); s.set(x, 1, z, 'stone_brick_wall'); s.set(x, 2, z, 'stone_brick_wall'); s.set(x, 3, z, 'soul_lantern[hanging=false,waterlogged=false]')
    s.set(cx - R - 1, 1, cz + 3, 'amethyst_block'); s.set(cx - R - 1, 2, cz + 3, 'amethyst_cluster[facing=up,waterlogged=false]')
    return finish(s)

# ───────── 7. 도적 은신처 ─────────
DARKINF = [('light_gray_terracotta', 60), ('white_terracotta', 25), ('gray_terracotta', 15)]
def thief():
    s = Bld('start/trainer_thief', 17)
    X1, Z1 = 7, 9
    foundation(s, 0, 0, X1, Z1, floor='dark_oak_planks')
    for y in range(1, 4):
        for f, cells in sides_of(0, 0, X1, Z1).items():
            for (x, z) in cells: s.set(x, y, z, s.pick([('mossy_cobblestone', 40), ('cobblestone', 30), ('mossy_stone_bricks', 20), ('cracked_stone_bricks', 10)]))
    door(s, 3, Z1, 'S', wood='dark_oak', lamps=False)
    window(s, 6, 2, Z1, 'S', wood='dark_oak', h=1, shutters=False); s.set(6, 2, Z1, 'iron_bars'); s.set(6, 2, Z1 - 1, 'air')
    jetty(s, 0, 0, X1, Z1, 3, sides='SNEW')
    timber_story(s, -1, -1, X1 + 1, Z1 + 1, 4, h=4, post=3, wins=[('S', (x, Z1 + 1)) for x in (1, 5)] + [('E', (X1 + 1, 4))], infill=DARKINF)
    jetty(s, -1, -1, X1 + 1, Z1 + 1, 7, sides='SN')
    timber_story(s, -1, -2, X1 + 1, Z1 + 2, 8, h=3, post=3, wins=[('S', (3, Z1 + 2))], infill=DARKINF, brace=False)
    top = gable_roof(s, -1, -2, X1 + 1, Z1 + 2, 11, axis='z', over=1, mats=('dark_oak', 'dark_oak'), full='dark_oak_planks', gable=DARKINF)
    s.set(1, 3, Z1 + 1, 'black_wall_banner[facing=south]') if False else None
    s.set(5, 3, Z1 + 1, 'chain[axis=y]') if False else None
    s.set(5, 2, Z1 + 1, 'lantern[hanging=true,waterlogged=false]') if False else None
    banner(s, 1, 3, Z1, 'black'); banner(s, 5, 3, Z1, 'black')
    # 옆 뒷골목: 차양 + 통 + 사다리 + 거미줄
    for z in range(0, Z1 + 1):
        s.set(X1 + 2, 4, z, ST('dark_oak', 'west')); s.set(X1 + 3, 3, z, ST('dark_oak', 'west'))
    for z in (0, Z1): s.set(X1 + 3, 1, z, 'dark_oak_fence'); s.set(X1 + 3, 2, z, 'dark_oak_fence')
    barrels(s, [(X1 + 2, 1, 2), (X1 + 2, 1, 3), (X1 + 2, 2, 2), (X1 + 3, 1, 6)])
    for y in range(1, 4): s.set(X1 + 1, y, 7, 'ladder[facing=east,waterlogged=false]') if False else None
    s.set(X1 + 2, 3, 5, 'chain[axis=y]'); s.set(X1 + 2, 2, 5, 'lantern[hanging=true,waterlogged=false]')
    s.set(X1 + 3, 3, 1, 'cobweb'); s.set(-1, 3, 0, 'cobweb')
    furnish(s, 0, 0, X1, Z1, 1, 'shop'); furnish(s, -1, -1, X1 + 1, Z1 + 1, 5, 'home')
    weathervane(s, 3, top + 1, Z1 // 2)
    s.set(-1, 1, Z1 + 2, 'barrel[facing=up,open=false]'); s.set(-1, 1, Z1 + 3, 'chest[facing=east,type=single,waterlogged=false]')
    for x in range(X1 + 1, X1 + 5):
        for z in range(-1, Z1 + 2):
            if s.get(x, 0, z) is None: s.set(x, 0, z, s.pick([('cobblestone', 40), ('gravel', 30), ('mossy_cobblestone', 30)]))
    return finish(s)

ALL = [(harold, '촌장 해럴드의 집'), (inn, '여관'), (smithy, '대장간'), (warrior, '전사 수련장 + 마당'),
       (archer, '궁수 수련장 + 사격장'), (mage, '마법사 탑'), (thief, '도적 은신처')]

if __name__ == '__main__':
    from PIL import Image, ImageDraw, ImageFont
    F = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 26, index=1)
    tiles = []
    for fn, title in ALL:
        s = fn()
        x0, y0, z0, x1, y1, z1 = s.bounds()
        im = render2(view2(s, 0), f'out/{s.name.split("/")[1]}.png', scale=14, bg=(150, 190, 230))
        c = Image.new('RGB', (im.width, im.height + 44), (150, 190, 230)); c.paste(im, (0, 44))
        ImageDraw.Draw(c).text((12, 6), f'{title} ({x1 - x0 + 1}×{z1 - z0 + 1}, 높이 {y1 - y0 + 1})', font=F, fill=(20, 30, 50))
        tiles.append(c)
    cols = 2; W = max(t.width for t in tiles)
    rows = [tiles[i:i + cols] for i in range(0, len(tiles), cols)]
    H = sum(max(t.height for t in r) for r in rows)
    sh = Image.new('RGB', (W * cols, H), (150, 190, 230)); y = 0
    for r in rows:
        for i, t in enumerate(r): sh.paste(t, (i * W, y))
        y += max(t.height for t in r)
    sh.save('a2_detail.png'); print(sh.size)
