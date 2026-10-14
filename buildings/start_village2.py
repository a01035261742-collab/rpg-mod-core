"""시작 마을 + 농장 v2 (디테일 보강)"""
import sys, os, random
from schem import S, autoconnect, render
from lib2 import *

OUT = sys.argv[1] if len(sys.argv) > 1 else 'out'
built = []
INF = ('calcite',) * 7 + ('diorite', 'white_terracotta')
def new(name, seed):
    s = S(name); s.r = random.Random(seed); return s
def finish(s, anchor=None):
    autoconnect(s); built.append((s, anchor))

def flowers(s, r, x0, x1, z, y=1):
    for x in range(x0, x1 + 1):
        s.set(x, y - 1, z, 'grass_block')
        s.set(x, y, z, r.choice(['poppy', 'dandelion', 'azure_bluet', 'oxeye_daisy', 'cornflower', 'allium']))

# ───────── 1. 촌장 집: 돌출 2층 + 돌기와 + 지붕창 + 바깥 굴뚝 ─────────
def harold_house():
    s = new('start/harold_house', 1); r = s.r
    box(s, r, 0, 1, 11, 9, 1, 4, infill=INF, post_step=5)                       # 1층 (z 1~9)
    for x in range(0, 11):                                          # 2층 바닥 들보 (돌출)
        for z in range(0, 11): s.set(x, 4, z, 'spruce_planks')
        s.set(x, 4, 0, 'dark_oak_log[axis=x]'); s.set(x, 4, 10, 'dark_oak_log[axis=x]')
    for z in range(0, 11): s.set(0, 4, z, 'dark_oak_log[axis=z]'); s.set(10, 4, z, 'dark_oak_log[axis=z]')
    for x in (0, 4, 8, 10):                                         # 돌출 받침 (까치발)
        s.set(x, 3, 0, 'dark_oak_stairs[facing=south,half=top,shape=straight]')
        s.set(x, 3, 10, 'dark_oak_stairs[facing=north,half=top,shape=straight]')
    box(s, r, 0, 0, 11, 11, 5, 4, infill=INF, found=False, post_step=5)         # 2층 (z 0~10)
    for x in range(1, 10):
        for z in range(1, 10): s.set(x, 4, z, 'spruce_planks')
    top = roof(s, r, 0, 10, 0, 10, 9, mats=('deepslate_tile',) * 3 + ('cobbled_deepslate',), eave='dark_oak', gable_fill=('spruce_planks', 'spruce_planks', 'stripped_spruce_log[axis=y]'))
    dormer(s, 5, 10, 10, mat='dark_oak', fill='calcite')
    # 정면 문 (1층 패널 z=8)
    door(s, 5, 1, 8); s.set(5, 1, 9, 'air'); s.set(5, 2, 9, 'air'); s.set(5, 0, 9, 'stone_bricks')
    s.set(5, 0, 10, 'stone_brick_stairs[facing=north,half=bottom,shape=straight]')
    s.set(3, 3, 10, 'lantern[hanging=true]'); s.set(7, 3, 10, 'lantern[hanging=true]')
    flowers(s, r, 1, 3, 10); flowers(s, r, 7, 9, 10)
    # 바깥 굴뚝 (동쪽)
    for y in range(0, top + 2):
        for z in (4, 5): s.set(11, y, z, r.choice(['stone_bricks', 'stone_bricks', 'cobblestone', 'mossy_stone_bricks']))
    s.set(11, top + 2, 4, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]'); s.set(11, top + 2, 5, 'stone_brick_slab[type=bottom]')
    path(s, r, 4, 6, 11, 13)
    # 안
    s.set(9, 1, 3, 'furnace[facing=west,lit=false]')
    for x in (7, 8): s.set(x, 1, 2, 'bookshelf'); s.set(x, 2, 2, 'bookshelf'); s.set(x, 3, 2, 'bookshelf')
    s.set(3, 1, 4, 'dark_oak_fence'); s.set(3, 2, 4, 'dark_oak_pressure_plate'); s.set(4, 1, 4, 'dark_oak_stairs[facing=west,half=bottom,shape=straight]')
    s.set(2, 1, 3, 'barrel[facing=up,open=false]'); s.set(2, 1, 7, 'chest[facing=east,type=single]')
    s.set(5, 3, 5, 'lantern[hanging=true]'); s.set(5, 8, 5, 'lantern[hanging=true]')
    for y in range(1, 5): s.set(8, y, 7, 'ladder[facing=north]')
    s.set(8, 4, 7, 'air')
    s.set(2, 5, 2, 'red_bed[facing=south,part=head,occupied=false]'); s.set(2, 5, 3, 'red_bed[facing=south,part=foot,occupied=false]')
    s.set(4, 5, 2, 'red_carpet'); s.set(4, 5, 3, 'red_carpet')
    finish(s)

# ───────── 2. 약초 가게: 오크 뼈대 + 이끼 돌기와 + 덩굴·벌집 ─────────
def herb_shop():
    s = new('start/herb_shop', 2); r = s.r
    box(s, r, 0, 0, 9, 8, 1, 4, frame='oak', infill=INF, shutters='oak', post_step=4)
    top = roof(s, r, 0, 8, 0, 7, 5, mats=('mossy_cobblestone', 'mossy_cobblestone', 'cobblestone', 'mossy_stone_brick'), eave='oak', gable_fill=('oak_planks',), axis='z')
    door(s, 4, 1, 6, wood='oak'); s.set(4, 1, 7, 'air'); s.set(4, 2, 7, 'air')
    porch(s, 3, 5, 7, 1, h=3, mat='oak', post='oak_fence', depth=2)
    s.set(3, 3, 9, 'lantern[hanging=true]') if False else None
    s.set(4, 3, 8, 'lantern[hanging=true]')
    for z in range(0, 8): bush(s, r, -1, 1, z, 'azalea' if z % 3 else 'oak') if z in (0, 1, 6, 7) else None
    for x in (1, 2, 6, 7): s.set(x, 1, 8, 'potted_' + r.choice(['fern', 'red_mushroom', 'allium', 'blue_orchid', 'poppy'])); s.set(x, 0, 8, 'moss_block')
    s.set(8, 3, 3, 'bee_nest[facing=east,honey_level=3]') if False else s.set(9, 3, 3, 'bee_nest[facing=east,honey_level=3]')
    for y in (2, 3, 4):
        for z in (2, 5): s.set(9, y, z, 'vine[east=false,north=false,south=false,up=false,west=true]')
    path(s, r, 3, 5, 9, 11)
    s.set(2, 1, 2, 'brewing_stand'); s.set(3, 1, 2, 'barrel[facing=up,open=false]'); s.set(6, 1, 2, 'cauldron'); s.set(6, 1, 4, 'composter[level=6]')
    s.set(2, 1, 4, 'potted_red_mushroom'); s.set(4, 4, 3, 'lantern[hanging=true]')
    finish(s)

# ───────── 3. 잡화점: 줄무늬 경사 차양 + 상자 진열 + 간판 기둥 ─────────
def general_store():
    s = new('start/general_store', 3); r = s.r
    box(s, r, 0, 0, 11, 8, 1, 5, infill=INF, post_step=5)
    roof(s, r, 0, 10, 0, 7, 6, mats=('spruce', 'spruce', 'dark_oak'), eave='dark_oak', gable_fill=('spruce_planks',))
    door(s, 5, 1, 6); s.set(5, 1, 7, 'air'); s.set(5, 2, 7, 'air')
    for x in range(-1, 12):                                   # 경사 차양 (2단)
        c = 'red' if x % 2 == 0 else 'white'
        s.set(x, 4, 8, f'{c}_wool'); s.set(x, 3, 9, f'{c}_carpet') if False else s.set(x, 3, 9, f'{c}_wool')
        s.set(x, 2, 10, f'{c}_carpet') if False else None
    for x in (-1, 11): s.set(x, 1, 9, 'spruce_fence'); s.set(x, 2, 9, 'spruce_fence')
    for x in (1, 2, 3, 7, 8, 9):                               # 진열대
        s.set(x, 1, 8, r.choice(['barrel[facing=up,open=false]', 'spruce_slab[type=top]']))
        if r.random() < 0.6: s.set(x, 2, 8, r.choice(['potted_dandelion', 'melon', 'pumpkin', 'hay_block[axis=y]', 'potted_poppy']))
    path(s, r, 0, 10, 9, 11)
    s.set(5, 0, 8, 'stone_bricks')
    for x in range(2, 9, 2): s.set(x, 1, 2, 'barrel[facing=south,open=false]'); s.set(x + 1, 1, 2, 'chest[facing=south,type=single]')
    s.set(5, 4, 4, 'lantern[hanging=true]')
    lamp_post(s, -3, 1, 10)
    s.set(-3, 0, 10, 'stone_bricks')
    finish(s)

# ───────── 4. 장비점: 돌 1층 + 하프팀버 박공 + 벽돌 대장간 ─────────
def gear_shop():
    s = new('start/gear_shop', 4); r = s.r
    box(s, r, 0, 0, 10, 8, 1, 5, frame='dark_oak', infill=('stone_bricks', 'stone_bricks', 'cobblestone', 'andesite', 'cracked_stone_bricks'))
    roof(s, r, 0, 9, 0, 7, 6, mats=('deepslate_tile', 'deepslate_tile', 'deepslate_brick'), eave='dark_oak', gable_fill=('calcite', 'calcite', 'stripped_dark_oak_log[axis=y]'))
    door(s, 4, 1, 6); s.set(4, 1, 7, 'air'); s.set(4, 2, 7, 'air')
    # 대장간 (동쪽, 지붕 있는 작업장)
    for x in range(10, 15):
        for z in range(1, 7): s.set(x, 0, z, r.choice(['cobblestone', 'stone_bricks', 'gravel']))
    for x, z in ((14, 1), (14, 6)):
        for y in range(1, 5): s.set(x, y, z, 'stripped_dark_oak_log[axis=y]')
    for x in range(10, 15):
        for z in range(1, 7): s.set(x, 5, z, 'deepslate_tile_slab[type=bottom]')
        s.set(x, 5, 0, 'dark_oak_stairs[facing=south,half=bottom,shape=straight]'); s.set(x, 5, 7, 'dark_oak_stairs[facing=north,half=bottom,shape=straight]')
    for y in range(1, 11):
        for x in (12, 13): s.set(x, y, 1, 'bricks' if y < 9 or x == 12 else 'bricks')
    s.set(12, 11, 1, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    s.set(12, 1, 2, 'blast_furnace[facing=south,lit=true]'); s.set(13, 1, 2, 'lava_cauldron')
    s.set(11, 1, 4, 'anvil[facing=east]'); s.set(13, 1, 4, 'grindstone[face=floor,facing=south]'); s.set(11, 1, 6, 'smithing_table')
    s.set(13, 1, 6, 'barrel[facing=up,open=false]'); s.set(12, 4, 4, 'lantern[hanging=true]')
    path(s, r, 3, 5, 8, 10)
    s.set(3, 3, 7, 'wall_torch[facing=south]') if False else s.set(2, 3, 8, 'lantern[hanging=false]') if False else None
    s.set(1, 1, 2, 'chest[facing=south,type=single]'); s.set(7, 1, 2, 'anvil[facing=south]'); s.set(4, 4, 4, 'lantern[hanging=true]')
    finish(s)

# ───────── 5. 전직 교관: 앞 차양 + 계열 색 박공·깃발 + 훈련 소품 ─────────
def trainer(name, color, seed, props):
    s = new('start/trainer_' + name, seed); r = s.r
    box(s, r, 0, 0, 9, 9, 1, 5, infill=INF)
    roof(s, r, 0, 8, 0, 8, 6, mats=('dark_oak', 'dark_oak', 'spruce'), eave='spruce', gable_fill=(f'{color}_terracotta',))
    door(s, 4, 1, 7); s.set(4, 1, 8, 'air'); s.set(4, 2, 8, 'air')
    porch(s, 3, 5, 8, 1, h=3, mat='dark_oak', post='dark_oak_fence', depth=2)
    for x in (2, 6): s.set(x, 3, 9, f'{color}_wall_banner[facing=south]') if False else None
    for x in (3, 5): s.set(x, 3, 7, f'{color}_wall_banner[facing=south]')
    s.set(4, 3, 9, 'lantern[hanging=true]')
    for x in range(2, 7):
        for z in range(2, 7): s.set(x, 1, z, f'{color}_carpet') if (x + z) % 2 == 0 else s.set(x, 1, z, 'spruce_planks') if False else s.set(x, 1, z, f'{color}_carpet')
    path(s, r, 3, 5, 11, 12)
    for spot, b in props: s.set(*spot, b)
    s.set(4, 5, 4, 'lantern[hanging=true]')
    finish(s)

def dummy(x, z, pumpkin='carved_pumpkin[facing=south]'):
    return [((x, 1, z), 'oak_fence'), ((x, 2, z), 'hay_block[axis=y]'), ((x, 3, z), pumpkin), ((x - 1, 2, z), 'oak_fence'), ((x + 1, 2, z), 'oak_fence')]

# ───────── 6. 광장 분수: 2단 분수 + 벤치 + 가로등 ─────────
def fountain():
    s = new('start/plaza_fountain', 6); r = s.r
    R = 6
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            d = max(abs(x), abs(z))
            s.set(x, 0, z, r.choice(['stone_bricks', 'polished_andesite', 'andesite']) if d >= 4 else 'stone_bricks')
    for x in range(-3, 4):
        for z in range(-3, 4):
            d = max(abs(x), abs(z))
            if d == 3: s.set(x, 1, z, 'stone_brick_slab[type=bottom]') if (x + z) % 2 else s.set(x, 1, z, 'stone_bricks')
            elif d <= 2: s.set(x, 1, z, 'water[level=0]')
    for y in (1, 2): s.set(0, y, 0, 'chiseled_stone_bricks')
    for x in range(-1, 2):
        for z in range(-1, 2):
            if (x, z) != (0, 0): s.set(x, 3, z, 'stone_brick_slab[type=bottom]')
    s.set(0, 3, 0, 'polished_andesite'); s.set(0, 4, 0, 'water[level=0]')
    for x, z, f in ((-5, 0, 'east'), (5, 0, 'west'), (0, -5, 'south'), (0, 5, 'north')):   # 벤치
        for k in (-1, 0, 1):
            xx, zz = (x, z + k) if x else (x + k, z)
            s.set(xx, 1, zz, f'spruce_stairs[facing={f},half=bottom,shape=straight]')
    for x, z in ((-5, -5), (5, -5), (-5, 5), (5, 5)):
        s.set(x, 1, z, 'cobblestone_wall'); s.set(x, 2, z, 'dark_oak_fence'); s.set(x, 3, z, 'dark_oak_fence'); s.set(x, 4, z, 'lantern[hanging=false]')
    for x, z in ((-6, -2), (-6, 2), (6, -2), (6, 2)): bush(s, r, x, 1, z, 'oak'); s.set(x, 0, z, 'grass_block')
    finish(s, anchor=(0, 1, 7))

# ───────── 7. 브람 농가: 차양 + 장작더미 별채 + 굴뚝 ─────────
def bram_farmhouse():
    s = new('farm/bram_farmhouse', 7); r = s.r
    box(s, r, 0, 0, 10, 7, 1, 4, frame='spruce', infill=('white_terracotta', 'white_terracotta', 'calcite'), found=True)
    top = roof(s, r, 0, 9, 0, 6, 5, mats=('spruce', 'spruce', 'dark_oak'), eave='dark_oak', gable_fill=('spruce_planks', 'stripped_spruce_log[axis=y]'))
    door(s, 3, 1, 5); s.set(3, 1, 6, 'air'); s.set(3, 2, 6, 'air')
    porch(s, 2, 7, 6, 1, h=3, mat='spruce', post='spruce_fence', depth=2)
    s.set(6, 1, 7, 'spruce_stairs[facing=west,half=bottom,shape=straight]'); s.set(7, 1, 7, 'spruce_stairs[facing=west,half=bottom,shape=straight]')
    for y in range(0, top + 1): s.set(-1, y, 3, r.choice(['cobblestone', 'cobblestone', 'mossy_cobblestone']))
    s.set(-1, top + 1, 3, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    # 장작더미 별채 (동쪽)
    for z in range(1, 6):
        for y in (1, 2): s.set(10, y, z, 'oak_log[axis=x]' if (y + z) % 2 else 'spruce_log[axis=x]')
        s.set(10, 3, z, 'spruce_slab[type=bottom]'); s.set(11, 3, z, 'spruce_stairs[facing=west,half=bottom,shape=straight]')
    s.set(11, 1, 1, 'spruce_fence'); s.set(11, 2, 1, 'spruce_fence'); s.set(11, 1, 5, 'spruce_fence'); s.set(11, 2, 5, 'spruce_fence')
    for x, b in ((8, 'hay_block[axis=y]'), (9, 'hay_block[axis=y]')): s.set(x, 1, 8, b)
    s.set(1, 1, 7, 'composter[level=4]'); s.set(1, 1, 8, 'water_cauldron[level=3]') if False else s.set(0, 1, 8, 'cauldron')
    path(s, r, 2, 4, 9, 11)
    s.set(2, 1, 2, 'crafting_table'); s.set(3, 1, 2, 'smoker[facing=south,lit=false]')
    s.set(7, 1, 2, 'white_bed[facing=east,part=head,occupied=false]'); s.set(6, 1, 2, 'white_bed[facing=east,part=foot,occupied=false]')
    s.set(5, 3, 3, 'lantern[hanging=true]')
    finish(s)

# ───────── 8. 헛간: 돌 하단 + 세로 판자 + 건초 다락 문 ─────────
def barn():
    s = new('farm/barn', 8); r = s.r
    box(s, r, 0, 0, 13, 10, 1, 6, frame='dark_oak', infill=('spruce_planks', 'spruce_planks', 'stripped_spruce_log[axis=y]'), floor='coarse_dirt', windows=False)
    top = roof(s, r, 0, 12, 0, 9, 7, mats=('dark_oak', 'dark_oak', 'spruce'), eave='spruce', gable_fill=('spruce_planks', 'stripped_spruce_log[axis=y]'), over=1, axis='z')
    for x in range(5, 8):                                     # 큰 문
        for y in range(1, 5): s.set(x, y, 8, 'air'); s.set(x, y, 9, 'air')
    for x in (4, 8):
        for y in range(1, 6): s.set(x, y, 9, 'stripped_dark_oak_log[axis=y]')
    for x in range(4, 9): s.set(x, 5, 9, 'dark_oak_log[axis=x]')
    for x in (5, 7): s.set(x, 1, 10, 'spruce_trapdoor[facing=south,half=bottom,open=true]') if False else None
    s.set(6, 8, 9, 'air'); s.set(6, 9, 9, 'air'); s.set(6, 10, 9, 'dark_oak_fence')   # 다락 문
    s.set(6, 8, 10, 'spruce_slab[type=top]'); s.set(6, 9, 9, 'air')
    for x, z in ((1, 1), (2, 1), (3, 1), (1, 2), (10, 1), (11, 1), (11, 2), (1, 7)): s.set(x, 1, z, 'hay_block[axis=y]')
    for x, z in ((2, 1), (11, 1)): s.set(x, 2, z, 'hay_block[axis=x]')
    for z in range(3, 8): s.set(10, 1, z, 'spruce_fence'); s.set(10, 2, z, 'spruce_fence')
    for x in range(1, 12):
        for z in range(1, 9): s.set(x, 6, z, 'spruce_planks') if z < 4 else None
    s.set(6, 5, 5, 'lantern[hanging=true]'); s.set(6, 4, 10, 'lantern[hanging=true]') if False else None
    path(s, r, 4, 8, 10, 12)
    finish(s)

# ───────── 9. 울타리 밭: 물길 + 허수아비 + 퇴비통 ─────────
def field():
    s = new('farm/farm_field', 9); r = s.r
    w, d = 15, 11
    for x in range(w):
        for z in range(d):
            edge = x in (0, w - 1) or z in (0, d - 1)
            if edge: s.set(x, 0, z, r.choice(['dirt_path', 'dirt_path', 'coarse_dirt'])); s.set(x, 1, z, 'oak_fence')
            elif x == w // 2: s.set(x, 0, z, 'water[level=0]')
            else:
                s.set(x, 0, z, 'farmland[moisture=7]')
                crop = 'wheat[age=7]' if x < 4 else 'carrots[age=7]' if x < w // 2 else 'potatoes[age=7]' if x < 11 else 'beetroots[age=3]'
                s.set(x, 1, z, crop)
    s.set(w // 2, 1, d - 1, 'oak_fence_gate[facing=south,open=false,in_wall=false,powered=false]'); s.set(w // 2, 0, d - 1, 'dirt_path')
    for x, z in ((0, 0), (w - 1, 0), (0, d - 1), (w - 1, d - 1)): s.set(x, 2, z, 'lantern[hanging=false]')
    for x, z in ((3, 5), (11, 5)):   # 허수아비
        s.set(x, 1, z, 'oak_fence'); s.set(x, 2, z, 'hay_block[axis=y]'); s.set(x, 3, z, 'carved_pumpkin[facing=south]')
        s.set(x - 1, 2, z, 'oak_fence'); s.set(x + 1, 2, z, 'oak_fence')
    for z in (3, 7): s.set(w // 2, 1, z, 'lily_pad')
    s.set(w, 1, 2, 'composter[level=7]'); s.set(w, 1, 3, 'composter[level=3]'); s.set(w, 0, 2, 'dirt'); s.set(w, 0, 3, 'dirt')
    finish(s)

harold_house(); herb_shop(); general_store(); gear_shop()
trainer('warrior', 'red', 11, dummy(-2, 6) + [((2, 1, 1), 'anvil[facing=south]'), ((6, 1, 1), 'grindstone[face=floor,facing=south]')])
trainer('archer', 'green', 12, [((-2, 1, 3), 'target'), ((-2, 2, 3), 'target'), ((-2, 1, 6), 'target'), ((2, 1, 1), 'fletching_table'), ((6, 1, 1), 'hay_block[axis=y]')])
trainer('mage', 'blue', 13, [((2, 1, 1), 'enchanting_table'), ((6, 1, 1), 'bookshelf'), ((6, 2, 1), 'bookshelf'), ((7, 1, 1), 'bookshelf'), ((-2, 1, 4), 'amethyst_block'), ((-2, 2, 4), 'amethyst_cluster[facing=up,waterlogged=false]')])
trainer('thief', 'purple', 14, [((2, 1, 1), 'barrel[facing=up,open=false]'), ((6, 1, 1), 'chest[facing=south,type=single]'), ((-2, 1, 5), 'barrel[facing=up,open=false]'), ((-2, 2, 5), 'cobweb')])
fountain(); bram_farmhouse(); barn(); field()

for s, anchor in built:
    p = os.path.join(OUT, s.name.replace('/', '_'))
    W, H, L, n = s.save(p + '.schem', anchor=anchor)
    render(s, p + '.png')
    print(s.name, f'{W}x{H}x{L}', n)
