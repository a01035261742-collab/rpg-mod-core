"""시작 마을 + 농장 v5: 형태 1·3·6·10·14 배정, 식물 없음"""
import os, random
from schem import S, autoconnect, render, view
from lib4 import *
from candidates2 import steep_dormer
from sheet import sheet

built = []
def new(name, seed): s = S(name); s.r = random.Random(seed); return s
def done(s, label):
    for k in [k for k, v in s.b.items() if any(t in v for t in ('poppy', 'dandelion', 'azure', 'oxeye', 'cornflower', 'allium', 'minecraft:grass[', 'fern', 'rose_bush', 'peony', 'lilac', 'leaves', 'vine', 'lily_of', 'orchid', 'potted_'))]:
        del s.b[k]
    autoconnect(s); built.append((s, label))

def lower(s, r, x0, z0, w, d, ground, door_x=None, wins=True):
    """돌 1층 + 정면 문(아치) + 깊은 창"""
    x1, z1 = x0 + w - 1, z0 + d - 1
    stone_box(s, r, x0, z0, w, d, 1, ground)
    dx = door_x if door_x is not None else (x0 + x1) // 2
    if wins:
        for x in range(x0 + 2, x1 - 1, 3):
            if abs(x - dx) >= 2: deep_window(s, x, 2, z1, 'south')
        for z in range(z0 + 2, z1 - 1, 3): deep_window(s, x0, 2, z, 'west'); deep_window(s, x1, 2, z, 'east')
    door(s, dx, 1, z1)
    s.set(dx - 1, 3, z1 + 1, 'stone_brick_stairs[facing=east,half=top,shape=straight]'); s.set(dx + 1, 3, z1 + 1, 'stone_brick_stairs[facing=west,half=top,shape=straight]')
    s.set(dx, 0, z1 + 1, 'stone_brick_stairs[facing=north,half=bottom,shape=straight]')
    return x1, z1, dx

def upper(s, r, x0, z0, w, d, y, h=4, jetty=1, frame='dark_oak', sides=('s', 'n', 'e', 'w'), step=4):
    ux0, uz0 = x0 - jetty, z0 - jetty
    jetty_floor(s, ux0, uz0, ux0 + w - 1 + 2 * jetty, uz0 + d - 1 + 2 * jetty, y - 1, frame=frame)
    box(s, r, ux0, uz0, w + 2 * jetty, d + 2 * jetty, y, h, frame=frame, infill=INF4, found=False, post_step=step, win_sides=sides)
    return ux0, ux0 + w - 1 + 2 * jetty, uz0, uz0 + d - 1 + 2 * jetty

GRAY = dict(full='deepslate_tiles', stair='deepslate_tile', alt=('cobbled_deepslate',))
BROWN = dict(full='spruce_planks', stair='spruce', alt=('dark_oak_planks',), moss=0.0)
DARK = dict(full='dark_oak_planks', stair='dark_oak', alt=('spruce_planks',), moss=0.03)
MOSS = dict(full='mossy_cobblestone', stair='mossy_cobblestone', alt=('cobblestone',), moss=0.0)

# 잡화점: 10 옆 박공 + 지붕창 2, 갈색, 차양 + 가판대
def general_store():
    s = new('start/general_store', 51); r = s.r
    lower(s, r, 0, 0, 11, 8, 4)
    a, b, c, d = upper(s, r, 0, 0, 11, 8, 6, sides=('s', 'n'), step=5)
    top = steep_roof(s, r, a, b, c, d, 10, axis='x', gable=INF4, timber_gable='dark_oak', **BROWN)
    for cx in (2, 8): steep_dormer(s, r, cx, d + 1, 12, full='spruce_planks', stair='spruce')
    for x in range(-1, 12): s.set(x, 4, 9, 'red_wool' if x % 2 == 0 else 'white_wool') if s.get(x, 4, 9) is None else None
    stall(s, -6, 9, 'red'); stall(s, 13, 9, 'blue')
    for x, bb in ((1, 'barrel[facing=up,open=false]'), (2, 'barrel[facing=up,open=false]'), (8, 'pumpkin'), (9, 'barrel[facing=up,open=false]')): s.set(x, 1, 9, bb)
    s.set(11, 4, 4, 'dark_oak_fence'); s.set(12, 4, 4, 'dark_oak_fence'); s.set(12, 3, 4, 'dark_oak_hanging_sign[attached=false,rotation=4,waterlogged=false]')
    for x in range(2, 9, 2): s.set(x, 1, 1, 'barrel[facing=south,open=false]'); s.set(x + 1, 1, 1, 'chest[facing=south,type=single]')
    chandelier(s, 5, 3, 4, 0) if False else s.set(5, 4, 4, 'lantern[hanging=true]')
    path(s, r, -6, 16, 10, 12)
    done(s, '잡화점 — 10 옆 박공+지붕창 (갈색)')

# 장비점: 14 L자, 진회색, 날개 = 대장간
def gear_shop():
    s = new('start/gear_shop', 52); r = s.r
    lower(s, r, 0, 0, 9, 8, 5, door_x=3)
    a, b, c, d = upper(s, r, 0, 0, 9, 8, 7, sides=('s', 'n', 'w'))
    steep_roof(s, r, a, b, c, d, 11, axis='x', gable=INF4, timber_gable='dark_oak', **GRAY)
    # 날개 (오른쪽 앞으로 튀어나옴): 돌 1층만, 앞은 열린 작업장
    stone_box(s, r, 6, 8, 5, 6, 1, 5)
    for x in range(7, 10):
        for y in range(1, 4): s.set(x, y, 13, 'air')
    for y in (1, 2, 3): s.set(6, y, 13, 'polished_andesite'); s.set(10, y, 13, 'polished_andesite')
    for x in range(7, 10): s.set(x, 4, 13, 'stripped_dark_oak_log[axis=x]')
    steep_roof(s, r, 6, 10, 8, 13, 6, axis='z', gable=INF4, timber_gable='dark_oak', over=1, **GRAY)
    for y in range(1, 16):
        s.set(10, y, 9, r.choice(['bricks', 'bricks', 'stone_bricks']) if y > 4 else 'stone_bricks')
    s.set(10, 16, 9, 'campfire[lit=true,facing=south,signal_fire=false,waterlogged=false]')
    for y in range(1, 9): s.set(7, y, 9, 'air') if y > 5 else None
    for y in (1, 2): s.set(6, y, 10, 'air'); s.set(5, y, 10, 'air') if False else None
    s.set(9, 1, 10, 'blast_furnace[facing=west,lit=true]'); s.set(9, 1, 11, 'lava_cauldron'); s.set(7, 1, 11, 'anvil[facing=east]'); s.set(8, 1, 12, 'grindstone[face=floor,facing=south]'); s.set(7, 1, 9, 'smithing_table')
    s.set(8, 4, 11, 'lantern[hanging=true]')
    s.set(1, 1, 1, 'chest[facing=south,type=single]'); s.set(7, 1, 1, 'anvil[facing=south]'); s.set(4, 5, 4, 'lantern[hanging=true]')
    s.set(1, 5, 9, 'dark_oak_fence'); s.set(1, 4, 9, 'dark_oak_hanging_sign[attached=false,rotation=0,waterlogged=false]')
    path(s, r, 2, 4, 9, 11)
    done(s, '장비점 — 14 L자 (진회색, 날개=대장간)')

# 약초 가게: 6 원형 탑, 1층 낮은 집 + 이끼 지붕
def herb_shop():
    s = new('start/herb_shop', 53); r = s.r
    lower(s, r, 0, 0, 9, 7, 4, door_x=3)
    steep_roof(s, r, 0, 8, 0, 6, 5, axis='x', gable=INF4, timber_gable='oak', eave='oak', **MOSS)
    turret(s, r, 8, 7, 1, 10, rad=2, timber_top=4)
    for (x, z) in ((7, 6), (8, 5)):
        for y in (1, 2): s.set(x, y, z, 'air')
    s.set(8, 9, 7, 'hay_block[axis=y]') if False else None
    for y in (3, 4): s.set(6, y, 9, 'oak_fence') if False else None
    s.set(1, 1, 1, 'brewing_stand'); s.set(2, 1, 1, 'cauldron'); s.set(6, 1, 1, 'barrel[facing=up,open=false]'); s.set(1, 1, 4, 'composter[level=6]')
    s.set(4, 4, 3, 'lantern[hanging=true]')
    s.set(0, 4, 7, 'oak_fence'); s.set(0, 3, 7, 'oak_hanging_sign[attached=false,rotation=0,waterlogged=false]')
    path(s, r, 2, 4, 8, 10)
    done(s, '약초 가게 — 6 원형 탑 (이끼 지붕)')

# 교관
def trainer_cross(color, seed, label, yard):   # 전사: 3 십자 박공, 박공 왼쪽
    s = new('start/trainer_warrior', seed); r = s.r
    lower(s, r, 0, 0, 11, 8, 4, door_x=3)
    a, b, c, d = upper(s, r, 0, 0, 11, 8, 6, sides=('s', 'n'), step=5)
    steep_roof(s, r, a, b, c, d, 10, axis='x', gable=INF4, timber_gable='dark_oak', **GRAY)
    steep_roof(s, r, 1, 5, d - 4, d, 10, axis='z', gable=(f'{color}_terracotta',) + INF4[:3], timber_gable='dark_oak', over=1, **GRAY)
    for x in (2, 4): s.set(x, 3, 8, f'{color}_wall_banner[facing=south]')
    for spot, bb in yard: s.set(*spot, bb)
    path(s, r, 2, 4, 9, 11)
    done(s, label)

def trainer_tall(color, seed, label, yard):   # 궁수: 1 정면 박공, 좁고 높게 3층
    s = new('start/trainer_archer', seed); r = s.r
    lower(s, r, 0, 0, 7, 8, 4)
    a, b, c, d = upper(s, r, 0, 0, 7, 8, 6, step=4)
    jetty_floor(s, a, c - 1, b, d + 1, 10, sides=('n', 's'))
    box(s, r, a, c - 1, b - a + 1, d - c + 3, 11, 4, infill=INF4, found=False, post_step=4, win_sides=('s', 'n'))
    steep_roof(s, r, a, b, c - 1, d + 1, 15, axis='z', gable=(f'{color}_terracotta',) + INF4[:3], timber_gable='dark_oak', **BROWN)
    for x in (2, 4): s.set(x, 3, 8, f'{color}_wall_banner[facing=south]')
    for spot, bb in yard: s.set(*spot, bb)
    path(s, r, 2, 4, 9, 11)
    done(s, label)

def trainer_tower(color, seed, label, yard):  # 마법사: 6 큰 탑 왼쪽 뒤
    s = new('start/trainer_mage', seed); r = s.r
    lower(s, r, 0, 0, 9, 8, 4)
    a, b, c, d = upper(s, r, 0, 0, 9, 8, 6, sides=('s', 'n', 'e'))
    steep_roof(s, r, a, b, c, d, 10, axis='z', gable=(f'{color}_terracotta',) + INF4[:3], timber_gable='dark_oak', **GRAY)
    turret(s, r, -2, -1, 1, 17, rad=3, timber_top=5, wall=('stone_bricks', 'stone_bricks', 'polished_andesite', 'mossy_stone_bricks', 'tuff'))
    for x in (3, 5): s.set(x, 3, 8, f'{color}_wall_banner[facing=south]')
    for spot, bb in yard: s.set(*spot, bb)
    path(s, r, 3, 5, 9, 11)
    done(s, label)

def trainer_l(color, seed, label, yard):      # 도적: 14 L자, 날개 왼쪽, 낮고 넓게, 어두운 나무
    s = new('start/trainer_thief', seed); r = s.r
    lower(s, r, 0, 0, 13, 7, 4, door_x=9)
    steep_roof(s, r, 0, 12, 0, 6, 5, axis='x', gable=INF4, timber_gable='dark_oak', eave='spruce', **DARK)
    stone_box(s, r, 0, 7, 5, 6, 1, 4)
    for y in (1, 2): s.set(2, y, 7, 'air'); s.set(2, y, 6, 'air')
    deep_window(s, 2, 2, 12, 'south')
    steep_roof(s, r, 0, 4, 4, 12, 5, axis='z', gable=(f'{color}_terracotta',) + INF4[:2], timber_gable='dark_oak', over=1, eave='spruce', **DARK)
    for x in (8, 10): s.set(x, 3, 7, f'{color}_wall_banner[facing=south]')
    for spot, bb in yard: s.set(*spot, bb)
    path(s, r, 8, 10, 8, 10)
    done(s, label)

# 브람 농가: 10 옆 박공 + 지붕창 1, 1층 + 다락
def bram_farmhouse():
    s = new('farm/bram_farmhouse', 56); r = s.r
    lower(s, r, 0, 0, 9, 7, 4, door_x=3)
    steep_roof(s, r, 0, 8, 0, 6, 5, axis='x', gable=('white_terracotta', 'calcite'), timber_gable='spruce', eave='dark_oak', **BROWN)
    steep_dormer(s, r, 6, 7, 6, full='spruce_planks', stair='spruce')
    for x in range(0, 9): s.set(x, 4, 8, 'spruce_stairs[facing=north,half=bottom,shape=straight]'); s.set(x, 0, 8, 'spruce_planks')
    for x in (0, 8): s.set(x, 1, 8, 'spruce_fence'); s.set(x, 2, 8, 'spruce_fence'); s.set(x, 3, 8, 'spruce_fence')
    for y in range(0, 16): s.set(-1, y, 3, r.choice(['cobblestone', 'cobblestone', 'mossy_cobblestone']))
    s.set(-1, 16, 3, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    woodpile(s, 10, 2); woodpile(s, 10, 3)
    for z in range(1, 5): s.set(10, 3, z, 'spruce_slab[type=bottom]'); s.set(11, 3, z, 'spruce_slab[type=bottom]'); s.set(12, 3, z, 'spruce_slab[type=bottom]')
    for z in (1, 4): s.set(13, 1, z, 'spruce_fence'); s.set(13, 2, z, 'spruce_fence'); s.set(13, 3, z, 'spruce_slab[type=bottom]')
    s.set(9, 1, 9, 'hay_block[axis=y]'); s.set(10, 1, 9, 'hay_block[axis=x]'); s.set(10, 2, 9, 'hay_block[axis=y]'); cart(s, 11, 7)
    s.set(1, 1, 1, 'crafting_table'); s.set(2, 1, 1, 'smoker[facing=south,lit=false]'); s.set(7, 1, 1, 'white_bed[facing=east,part=head,occupied=false]'); s.set(6, 1, 1, 'white_bed[facing=east,part=foot,occupied=false]')
    s.set(4, 4, 3, 'lantern[hanging=true]')
    path(s, r, 2, 4, 9, 11)
    done(s, '브람 농가 — 10 옆 박공+지붕창 (갈색, 1층+다락)')

# 헛간: 1 정면 박공, 넓고 아주 높게
def barn():
    s = new('farm/barn', 57); r = s.r
    stone_box(s, r, 0, 0, 13, 11, 1, 2)
    box(s, r, 0, 0, 13, 11, 3, 5, frame='dark_oak', infill=('spruce_planks', 'spruce_planks', 'stripped_spruce_log[axis=y]'), found=False, windows=False, post_step=4)
    steep_roof(s, r, 0, 12, 0, 10, 8, axis='z', gable=('spruce_planks', 'stripped_spruce_log[axis=y]'), timber_gable='dark_oak', eave='spruce', **DARK)
    for x in range(5, 8):
        for y in range(1, 6): s.set(x, y, 10, 'air'); s.set(x, y, 9, 'air')
    for y in range(1, 7): s.set(4, y, 10, 'stripped_dark_oak_log[axis=y]'); s.set(8, y, 10, 'stripped_dark_oak_log[axis=y]')
    for x in range(4, 9): s.set(x, 6, 10, 'dark_oak_log[axis=x]')
    for y in (10, 11): s.set(6, y, 10, 'air')
    s.set(6, 13, 11, 'dark_oak_fence'); s.set(6, 13, 12, 'dark_oak_fence'); s.set(6, 12, 12, 'chain[axis=y]')
    for x in range(1, 12):
        for z in range(1, 5): s.set(x, 8, z, 'spruce_planks')
    for x, z in ((1, 1), (2, 1), (3, 1), (1, 2), (10, 1), (11, 1), (11, 2), (1, 8)): s.set(x, 1, z, 'hay_block[axis=y]')
    for x in range(2, 10, 2): s.set(x, 9, 2, 'hay_block[axis=x]')
    for z in range(5, 9): s.set(10, 1, z, 'spruce_fence'); s.set(10, 2, z, 'spruce_fence')
    s.set(6, 7, 6, 'lantern[hanging=true]')
    path(s, r, 4, 8, 11, 13)
    done(s, '헛간 — 1 정면 박공 (넓고 높게)')

def dummy(x, z): return [((x, 1, z), 'oak_fence'), ((x, 2, z), 'hay_block[axis=y]'), ((x, 3, z), 'carved_pumpkin[facing=south]'), ((x - 1, 2, z), 'oak_fence'), ((x + 1, 2, z), 'oak_fence')]

general_store(); gear_shop(); herb_shop()
trainer_cross('red', 61, '전사 교관 — 3 십자 박공 (박공 왼쪽)', dummy(13, 5) + dummy(15, 5))
trainer_tall('green', 62, '궁수 교관 — 1 정면 박공 (좁고 높게 3층)', [((10, 1, 3), 'target'), ((10, 2, 3), 'target'), ((10, 1, 6), 'target')])
trainer_tower('blue', 63, '마법사 교관 — 6 큰 탑 (왼쪽 뒤)', [((11, 1, 5), 'amethyst_block'), ((11, 2, 5), 'amethyst_cluster[facing=up,waterlogged=false]')])
trainer_l('purple', 64, '도적 교관 — 14 L자 (낮고 넓게, 어두운 나무)', [((14, 1, 4), 'barrel[facing=up,open=false]'), ((14, 2, 4), 'barrel[facing=up,open=false]'), ((15, 1, 4), 'barrel[facing=up,open=false]'), ((14, 1, 6), 'cobweb')])
bram_farmhouse(); barn()

os.makedirs('out5', exist_ok=True)
items = []
for s, label in built:
    p = os.path.join('out5', s.name.replace('/', '_'))
    W, H, L, n = s.save(p + '.schem'); render(view(s, 0), p + '.png', scale=10); items.append((p + '.png', label)); print(s.name, W, H, L, n)
print(sheet(items, 'start_village_preview_v5.png', cols=3))
