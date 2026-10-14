"""촌장 저택 v4 — 레퍼런스 기법: 돌 1층 + 돌출 목조 2층, 정면 박공(가파른 지붕), 대각 버팀목, 깊은 창, 탑, 별채"""
import sys, os, random
from schem import S, autoconnect, render, view
from lib4 import *

INF = ('calcite',) * 8 + ('white_terracotta', 'diorite')
def build():
    s = S('start/harold_manor'); r = random.Random(7); s.r = r
    # ── 본채 1층 (돌): x0~8, z0~10, y1~4 ──
    stone_box(s, r, 0, 0, 9, 11, 1, 4)
    for x in (2, 6): deep_window(s, x, 2, 10, 'south')
    for z in (3, 7): deep_window(s, 0, 2, z, 'west'); deep_window(s, 8, 2, z, 'east')
    door(s, 4, 1, 10)
    s.set(3, 3, 11, 'stone_brick_stairs[facing=east,half=top,shape=straight]'); s.set(5, 3, 11, 'stone_brick_stairs[facing=west,half=top,shape=straight]')
    s.set(4, 3, 11, 'stone_brick_slab[type=top]')
    for x in (3, 5): s.set(x, 1, 11, 'stone_brick_wall'); s.set(x, 2, 11, 'stone_brick_wall')
    s.set(4, 0, 11, 'stone_brick_stairs[facing=north,half=bottom,shape=straight]')
    # ── 2층 들보·바닥 (돌출 1칸) y5 ──
    for x in range(-1, 10):
        for z in range(-1, 12): s.set(x, 5, z, 'spruce_planks')
    for x in range(-1, 10): s.set(x, 5, -1, 'dark_oak_log[axis=x]'); s.set(x, 5, 11, 'dark_oak_log[axis=x]')
    for z in range(-1, 12): s.set(-1, 5, z, 'dark_oak_log[axis=z]'); s.set(9, 5, z, 'dark_oak_log[axis=z]')
    for x in (0, 4, 8):
        s.set(x, 4, 11, 'dark_oak_stairs[facing=north,half=top,shape=straight]'); s.set(x, 4, -1, 'dark_oak_stairs[facing=south,half=top,shape=straight]')
    for z in (0, 5, 10):
        s.set(-1, 4, z, 'dark_oak_stairs[facing=east,half=top,shape=straight]'); s.set(9, 4, z, 'dark_oak_stairs[facing=west,half=top,shape=straight]')
    # ── 2층 목조: x-1~9, z-1~11, y6~9 ──
    x1, z1, xs, zs = box(s, r, -1, -1, 11, 13, 6, 4, infill=INF, found=False, post_step=5, win_sides=('s', 'n'))
    # 옆벽 대각 버팀목 (창 없는 면)
    for i in range(len(zs) - 1):
        diag_brace(s, zs[i], zs[i + 1], 6, 9, 0, axis='z')      # 서쪽 패널 (x=0, 안쪽 평면)
        diag_brace(s, zs[i], zs[i + 1], 6, 9, 8, axis='z')      # 동쪽 패널 (x=8)
    # ── 지붕: 용마루 z방향 → 정면에 가파른 박공 ──
    top = steep_roof(s, r, -1, 9, -1, 11, 10, axis='z', gable=INF, timber_gable='dark_oak', over=1)
    for k in (0, 1): s.set(4, 12 + k, 11, 'glass_pane'); s.set(4, 12 + k, -1, 'glass_pane')   # 박공 창
    s.set(4, 11, 12, 'spruce_trapdoor[facing=south,half=top,open=false]')
    hanging_sign(s, 8, 4, 11, arm_dir='south') if False else None
    s.set(2, 7, 12, None)
    for x in (1, 7): s.set(x, 4, 12, 'lantern[hanging=true]') if False else None
    s.set(2, 4, 12, 'chain[axis=y]') if False else None
    s.set(3, 4, 12, 'lantern[hanging=true]') if False else None
    for x in (2, 6): s.set(x, 4, 12, 'lantern[hanging=true]')
    for x in (3, 5): s.set(x, 4, 11, 'red_wall_banner[facing=south]') if False else None
    # ── 반목조 탑: 돌 1층(x-7~-3, z1~5) → 2층 돌출(x-8~-2, z0~6) → 3층 한 번 더 돌출(서·남·북) → 가파른 사각뿔 ──
    tx0, tx1, tz0, tz1 = -7, -3, 1, 5
    stone_box(s, r, tx0, tz0, 5, 5, 1, 4)
    deep_window(s, tx0, 2, 3, 'west'); deep_window(s, -5, 2, tz1, 'south'); deep_window(s, -5, 2, tz0, 'north')
    jetty_floor(s, -8, 0, -2, 6, 5)
    _, _, xs, zs = box(s, r, -8, 0, 7, 7, 6, 4, infill=INF, found=False, post_step=3, win_sides=('w', 's', 'n'))
    jetty_floor(s, -9, -1, -2, 7, 10, sides=('n', 's', 'w'))
    _, _, xs, zs = box(s, r, -9, -1, 8, 9, 11, 4, infill=INF, found=False, post_step=4, win_sides=('w', 's', 'n'))
    for i in range(len(zs) - 1): diag_brace(s, zs[i], zs[i + 1], 11, 14, -8, axis='z')
    steep_pyramid(s, r, -9, -2, -1, 7, 15, over=1)
    for y in range(1, 11): s.set(-6, y, 3, 'ladder[facing=east]')
    for x in (-2, -1):                                         # 연결 복도
        s.set(x, 0, 3, 'stone_bricks')
        for y in (1, 2, 3):
            s.set(x, y, 2, r.choice(['stone_bricks', 'cobblestone', 'andesite'])); s.set(x, y, 4, r.choice(['stone_bricks', 'cobblestone', 'andesite']))
            s.set(x, y, 3, 'air' if y < 3 else 'stone_bricks')
        for z in (1, 2, 3, 4, 5): s.set(x, 4, z, 'deepslate_tile_slab[type=bottom]')
    for y in (1, 2):
        for x in (-3, 0): s.set(x, y, 3, 'air')
    # ── 동쪽 별채 (부엌): x9~15, z4~10, 돌 1층 + 가파른 지붕(박공 동쪽) ──
    stone_box(s, r, 9, 4, 7, 7, 1, 4)
    deep_window(s, 12, 2, 10, 'south'); deep_window(s, 15, 2, 7, 'east')
    steep_roof(s, r, 9, 15, 4, 10, 5, axis='x', gable=INF, timber_gable='dark_oak', over=1)
    for y in (1, 2): s.set(8, y, 7, 'air'); s.set(9, y, 7, 'air')
    for y in range(1, 15):
        for x in (13, 14): s.set(x, y, 4, r.choice(['bricks', 'bricks', 'stone_bricks']) if y > 4 else r.choice(['stone_bricks', 'cobblestone']))
    s.set(13, 15, 4, 'campfire[lit=true,facing=south,signal_fire=false,waterlogged=false]'); s.set(14, 15, 4, 'brick_slab[type=bottom]')
    s.set(13, 1, 5, 'campfire[lit=true,facing=south,signal_fire=false,waterlogged=false]'); s.set(14, 1, 5, 'smoker[facing=south,lit=true]')
    s.set(11, 1, 9, 'crafting_table'); s.set(12, 1, 5, 'barrel[facing=up,open=false]'); s.set(14, 1, 9, 'water_cauldron[level=3]')
    s.set(12, 3, 7, 'lantern[hanging=true]')
    hanging_sign(s, 15, 3, 9, arm_dir='east')
    # ── 내부 ──
    table(s, 2, 5, 6, 5)
    for z in range(1, 10): s.set(4, 1, z, 'red_carpet') if s.get(4, 1, z) in (None, 'minecraft:air') else None
    for x in (1, 2, 6, 7): s.set(x, 1, 1, 'bookshelf'); s.set(x, 2, 1, 'bookshelf')
    s.set(4, 1, 1, 'lectern[facing=south,has_book=true,powered=false]')
    chandelier(s, 4, 4, 5, length=1)
    for y in range(1, 6): s.set(7, y, 9, 'ladder[facing=north]')
    s.set(7, 5, 9, 'air')
    s.set(1, 6, 1, 'red_bed[facing=south,part=head,occupied=false]'); s.set(1, 6, 2, 'red_bed[facing=south,part=foot,occupied=false]')
    s.set(2, 6, 1, 'barrel[facing=south,open=false]'); s.set(7, 6, 1, 'cartography_table'); s.set(6, 6, 1, 'bookshelf')
    for x in range(2, 7):
        for z in range(4, 8): s.set(x, 6, z, 'brown_carpet')
    chandelier(s, 4, 9, 5, length=1)
    s.set(-4, 6, 4, 'lectern[facing=west,has_book=true,powered=false]'); s.set(-6, 6, 2, 'bookshelf'); s.set(-7, 11, 4, 'chest[facing=east,type=single]'); s.set(-7, 11, 1, 'barrel[facing=up,open=false]')
    s.set(-5, 9, 3, 'lantern[hanging=true]'); s.set(-5, 14, 3, 'lantern[hanging=true]')
    # ── 바깥 ──
    path(s, r, 3, 5, 12, 18)
    well(s, r, 11, 15)
    cart(s, 14, 13)
    woodpile(s, 16, 6)
    for x in range(-6, 3): s.set(x, 1, 16, 'spruce_fence')
    for z in range(8, 16): s.set(-6, 1, z, 'spruce_fence')
    for x in range(-5, 2):
        for z in range(12, 16):
            if (x + z) % 3 == 0: s.set(x, 0, z, 'farmland[moisture=7]'); s.set(x, 1, z, r.choice(['carrots[age=7]', 'potatoes[age=7]', 'beetroots[age=3]']))
            else:
                s.set(x, 0, z, 'grass_block'); f = r.choice(['rose_bush', 'peony', 'lilac', None, None])
                if f: s.set(x, 1, z, f + '[half=lower]'); s.set(x, 2, z, f + '[half=upper]')
                else: s.set(x, 1, z, r.choice(['poppy', 'allium', 'cornflower', 'grass']))
    for (x, z) in ((-8, 0), (-8, 6), (10, 12), (-2, 7), (16, 3), (9, -1)): bush(s, r, x, 1, z, r.choice(['oak', 'azalea'])); s.set(x, 0, z, 'grass_block')
    for z in (1, 3, 5, 7, 9):   # 덩굴
        if r.random() < 0.6:
            for y in range(1, 4): s.set(-1, y, z, 'vine[east=true,north=false,south=false,up=false,west=false]') if s.get(-1, y, z) is None else None
    lamp_post(s, 6, 1, 14); s.set(6, 0, 14, 'stone_bricks')
    ground_skirt(s, r, -8, 16, -2, 12, ring=2)
    autoconnect(s)
    return s

if __name__ == '__main__':
    from sheet import sheet
    s = build(); os.makedirs('out', exist_ok=True)
    print(s.save('out/start_harold_manor.schem'))
    render(view(s, 0), 'out/hm_front.png', scale=12); render(view(s, 2), 'out/hm_back.png', scale=12)
    render(view(s, 1), 'out/hm_side.png', scale=12); render(view(s, 0, ymax=4), 'out/hm_f1.png', scale=12)
    print(sheet([('out/hm_front.png', '정면 (남동쪽에서)'), ('out/hm_back.png', '뒤 (북서쪽에서)'), ('out/hm_side.png', '옆 (남서쪽에서)'), ('out/hm_f1.png', '1층 내부')], 'harold_manor_preview_v4.png', cols=2))
