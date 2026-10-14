"""촌장 저택 v3 샘플: 본채(돌출 2층) + 서쪽 돌탑 + 동쪽 부엌 별채(L자) + 지붕·벽·소품·내부"""
import sys, os, random
from schem import S, autoconnect, render
from lib3 import *

INF = ('calcite',) * 7 + ('diorite', 'white_terracotta')
STONE = ('stone_bricks',) * 4 + ('cobblestone', 'mossy_stone_bricks', 'cracked_stone_bricks', 'andesite', 'mossy_cobblestone')

def build():
    s = S('start/harold_manor'); r = random.Random(21); s.r = r
    # ── 본채: x0~10, 1층 z1~9 / 2층 z0~10 ──
    _, _, xs1, zs1 = box(s, r, 0, 1, 11, 9, 1, 4, infill=INF, post_step=5, win_sides=('s', 'n'))
    for x in range(0, 11):
        for z in range(0, 11): s.set(x, 4, z, 'spruce_planks')
        s.set(x, 4, 0, 'dark_oak_log[axis=x]'); s.set(x, 4, 10, 'dark_oak_log[axis=x]')
    for z in range(0, 11): s.set(0, 4, z, 'dark_oak_log[axis=z]'); s.set(10, 4, z, 'dark_oak_log[axis=z]')
    for x in (0, 5, 10):
        s.set(x, 3, 0, 'dark_oak_stairs[facing=south,half=top,shape=straight]'); s.set(x, 3, 10, 'dark_oak_stairs[facing=north,half=top,shape=straight]')
    _, _, xs2, zs2 = box(s, r, 0, 0, 11, 11, 5, 4, infill=INF, found=False, post_step=5)
    for x in range(1, 10):
        for z in range(1, 10): s.set(x, 4, z, 'spruce_planks')
    braces(s, 0, 10, 0, 10, 8, xs2, zs2, sides=('e', 'w'))
    # 1층 정면: 2x2 큰 창 + 꽃상자 (패널 x1~4, x6~9)
    for cx in (2, 7):
        for x in (cx, cx + 1):
            for y in (2, 3): s.set(x, y, 8, 'glass_pane')
            flower_box(s, r, x, 1, 9)
            s.set(x, 3, 9, None); s.set(x, 2, 9, None)
    top = roof(s, r, 0, 10, 0, 10, 9, mats=('deepslate_tile',) * 4 + ('cobbled_deepslate',), eave='dark_oak', gable_fill=('spruce_planks', 'stripped_spruce_log[axis=y]'))
    barge_x(s, 0, 10, 0, 10, 9)
    dormer(s, 5, 10, 10, mat='dark_oak', fill='calcite')
    door(s, 5, 1, 8); s.set(5, 1, 9, 'air'); s.set(5, 2, 9, 'air'); s.set(5, 0, 9, 'stone_bricks')
    s.set(5, 0, 10, 'stone_brick_stairs[facing=north,half=bottom,shape=straight]')
    s.set(4, 3, 10, 'lantern[hanging=true]'); s.set(6, 3, 10, 'lantern[hanging=true]')
    hanging_sign(s, 10, 3, 6, arm_dir='east') if False else None

    # ── 서쪽 돌탑: x-5~-1, z3~7, 3층 + 피라미드 지붕 ──
    tx0, tx1, tz0, tz1 = -7, -3, 3, 7
    for y in range(0, 13):
        for x in range(tx0, tx1 + 1):
            for z in range(tz0, tz1 + 1):
                edge = x in (tx0, tx1) or z in (tz0, tz1)
                corner = x in (tx0, tx1) and z in (tz0, tz1)
                if y == 0: s.set(x, y, z, r.choice(STONE))
                elif edge:
                    if y >= 9: s.set(x, y, z, 'stripped_dark_oak_log[axis=y]' if corner else r.choice(INF))
                    else: s.set(x, y, z, 'polished_andesite' if corner and y % 4 == 0 else r.choice(STONE))
                else: s.set(x, y, z, 'air')
    for y in (4, 8):                                       # 층 바닥 + 띠
        for x in range(tx0 + 1, tx1):
            for z in range(tz0 + 1, tz1): s.set(x, y, z, 'spruce_planks')
        for x in range(tx0 - 1, tx1 + 2):
            for z, f in ((tz0 - 1, 'north'), (tz1 + 1, 'south')):
                s.set(x, y, z, f'stone_brick_stairs[facing={"south" if f == "north" else "north"},half=top,shape=straight]') if y == 8 else None
        if y == 8:
            for z in range(tz0, tz1 + 1):
                s.set(tx0 - 1, y, z, 'stone_brick_stairs[facing=east,half=top,shape=straight]')
    for x in range(tx0, tx1 + 1):                          # 3층 들보
        s.set(x, 12, tz0, 'dark_oak_log[axis=x]'); s.set(x, 12, tz1, 'dark_oak_log[axis=x]')
    for z in range(tz0, tz1 + 1):
        s.set(tx0, 12, z, 'dark_oak_log[axis=z]'); s.set(tx1, 12, z, 'dark_oak_log[axis=z]')
    # 아치형 좁은 창 (서·남·북), 3층은 덧창 창
    for y0 in (2, 6):
        for (x, z) in ((tx0, 5), (-5, tz1), (-5, tz0)):
            s.set(x, y0, z, 'glass_pane'); s.set(x, y0 + 1, z, 'glass_pane')
    for (x, z, f) in ((tx0, 5, 'west'), (-5, tz1, 'south'), (-5, tz0, 'north')):
        s.set(x, 10, z, 'glass_pane'); s.set(x, 11, z, 'glass_pane')
    tt = pyramid(s, tx0, tx1, tz0, tz1, 13, mat='deepslate_tile', over=1)
    for y in range(1, 13): s.set(-4, y, 4, 'ladder[facing=south]')
    # 탑 ↔ 본채 연결 복도 (x -2~-1, 돌벽 + 슬랩 지붕)
    for x in (-2, -1):
        s.set(x, 0, 5, r.choice(STONE)); s.set(x, 0, 4, r.choice(STONE)); s.set(x, 0, 6, r.choice(STONE))
        for y in (1, 2, 3):
            s.set(x, y, 4, r.choice(STONE)); s.set(x, y, 6, r.choice(STONE)); s.set(x, y, 5, 'air' if y < 3 else r.choice(STONE))
        for z in (3, 4, 5, 6, 7): s.set(x, 4, z, 'deepslate_tile_slab[type=bottom]')
    s.set(-2, 2, 4, 'glass_pane'); s.set(-2, 2, 6, 'glass_pane')
    for y in (1, 2):
        for x in (-3, 0, 1): s.set(x, y, 5, 'air')
    s.set(-1, 3, 5, 'stone_brick_stairs[facing=east,half=top,shape=straight]') if False else None

    # ── 동쪽 부엌 별채 (L자): x11~17, z4~10, 1층 + 낮은 지붕 ──
    _, _, xs3, zs3 = box(s, r, 11, 4, 7, 7, 1, 4, infill=INF, post_step=3, win_sides=('s', 'e'), shutters='spruce')
    for z in range(5, 10): s.set(11, 1, z, 'air') if 5 <= z <= 9 else None
    for y in (1, 2):
        s.set(10, y, 7, 'air'); s.set(9, y, 7, 'air'); s.set(11, y, 7, 'air'); s.set(12, y, 7, 'air')
    roof(s, r, 11, 17, 4, 10, 5, mats=('deepslate_tile',) * 4 + ('cobbled_deepslate',), eave='spruce', gable_fill=('spruce_planks',), over=1, axis='x')
    # 부엌 굴뚝 + 벽난로
    for y in range(0, 12):
        for x in (15, 16): s.set(x, y, 4, r.choice(['bricks', 'bricks', 'stone_bricks']) if y > 5 else r.choice(STONE))
    s.set(15, 12, 4, 'campfire[lit=true,facing=south,signal_fire=false,waterlogged=false]'); s.set(16, 12, 4, 'brick_slab[type=bottom]')
    s.set(15, 1, 5, 'campfire[lit=true,facing=south,signal_fire=false,waterlogged=false]') ; s.set(16, 1, 5, 'smoker[facing=south,lit=true]')
    s.set(14, 1, 5, 'stone_brick_stairs[facing=east,half=bottom,shape=straight]') if False else s.set(14, 1, 5, 'barrel[facing=up,open=false]')
    s.set(14, 2, 5, 'stone_brick_slab[type=bottom]')
    s.set(13, 1, 9, 'crafting_table'); s.set(14, 1, 9, 'barrel[facing=up,open=false]'); s.set(16, 1, 9, 'water_cauldron[level=3]')
    s.set(15, 3, 7, 'lantern[hanging=true]')
    hanging_sign(s, 17, 3, 9, arm_dir='east')

    # ── 1층 내부: 긴 식탁, 책장, 샹들리에, 카펫 길 ──
    table(s, 3, 4, 6, 4, chairs=True)
    for x in range(2, 9): s.set(x, 1, 7, 'red_carpet') if s.get(x, 1, 7) in (None, 'minecraft:air') else None
    for z in range(3, 8): s.set(5, 1, z, 'red_carpet') if s.get(5, 1, z) in (None, 'minecraft:air') else None
    for x in (2, 3, 7, 8):
        s.set(x, 1, 2, 'bookshelf'); s.set(x, 2, 2, 'chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]' if x in (3, 7) else 'bookshelf')
    s.set(5, 1, 2, 'lectern[facing=south,has_book=true,powered=false]')
    chandelier(s, 5, 3, 5, length=0) if False else s.set(5, 3, 5, 'lantern[hanging=true]')
    for y in range(1, 5): s.set(8, y, 6, 'ladder[facing=west]') if False else None
    for y in range(1, 5): s.set(9, y, 3, 'ladder[facing=west]')
    s.set(9, 4, 3, 'air')
    # 2층: 침실
    s.set(2, 5, 2, 'red_bed[facing=south,part=head,occupied=false]'); s.set(2, 5, 3, 'red_bed[facing=south,part=foot,occupied=false]')
    s.set(3, 5, 2, 'barrel[facing=south,open=false]'); s.set(1, 5, 5, 'chest[facing=east,type=single]')
    for x in range(3, 8):
        for z in range(4, 8): s.set(x, 5, z, 'brown_carpet')
    s.set(7, 5, 2, 'cartography_table'); s.set(8, 5, 2, 'bookshelf')
    chandelier(s, 5, 8, 5, length=1)
    # 탑 내부
    s.set(-6, 5, 6, 'lectern[facing=east,has_book=true,powered=false]'); s.set(-6, 5, 4, 'bookshelf'); s.set(-6, 6, 4, 'bookshelf')
    s.set(-6, 9, 6, 'chest[facing=east,type=single]'); s.set(-5, 11, 5, 'lantern[hanging=true]'); s.set(-5, 7, 5, 'lantern[hanging=true]')
    s.set(-6, 1, 6, 'barrel[facing=up,open=false]'); s.set(-6, 1, 4, 'barrel[facing=up,open=false]')

    # ── 바깥: 길, 우물, 손수레, 장작, 정원 울타리, 덤불, 바닥 ──
    path(s, r, 4, 6, 11, 16)
    well(s, r, 12, 14)
    cart(s, 15, 12)
    woodpile(s, 18, 7)
    for x in range(-4, 4):                                   # 정원 울타리 (왼쪽 앞)
        s.set(x, 1, 15, 'spruce_fence')
    for z in range(11, 15): s.set(-4, 1, z, 'spruce_fence')
    s.set(3, 1, 15, 'spruce_fence_gate[facing=south,open=false,in_wall=false,powered=false]') if False else None
    for x in range(-3, 3):
        for z in range(12, 15):
            if (x + z) % 3 == 0: s.set(x, 0, z, 'farmland[moisture=7]'); s.set(x, 1, z, r.choice(['carrots[age=7]', 'potatoes[age=7]', 'beetroots[age=3]']))
            else: s.set(x, 0, z, 'grass_block'); s.set(x, 1, z, r.choice(['rose_bush[half=lower]', 'peony[half=lower]', 'poppy', 'allium', 'cornflower', 'grass']))
    for (x, z) in ((-3, 12), (0, 13)):
        if 'bush[half=lower]' in (s.get(x, 1, z) or '') or 'peony' in (s.get(x, 1, z) or ''):
            s.set(x, 2, z, s.get(x, 1, z).replace('lower', 'upper'))
    for (x, z) in [(x, z) for x in range(-3, 3) for z in range(12, 15)]:
        v = s.get(x, 1, z) or ''
        if 'half=lower' in v: s.set(x, 2, z, v.replace('lower', 'upper'))
    for (x, z) in ((-8, 2), (-8, 8), (11, 11), (-1, 9), (18, 3)): bush(s, r, x, 1, z, r.choice(['oak', 'azalea'])); s.set(x, 0, z, 'grass_block')
    lamp_post(s, 7, 1, 13); s.set(7, 0, 13, 'stone_bricks')
    ground_skirt(s, r, -8, 18, -1, 11, ring=2)
    autoconnect(s)
    return s

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else 'out'
    os.makedirs(out, exist_ok=True)
    s = build()
    print(s.save(os.path.join(out, 'start_harold_manor.schem')))
    render(s, os.path.join(out, 'start_harold_manor.png'), scale=12)
