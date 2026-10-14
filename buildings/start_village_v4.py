"""시작 마을 + 농장 v4 (저택 스타일: 돌 1층 · 돌출 목조 2층 · 가파른 지붕)"""
import os, random
from schem import S, autoconnect, render, view
from lib4 import *
from sheet import sheet

built = []
def new(name, seed): s = S(name); s.r = random.Random(seed); return s
def done(s, label, anchor=None): autoconnect(s); built.append((s, label, anchor))

def flowerbed(s, r, x0, x1, z):
    for x in range(x0, x1 + 1):
        s.set(x, 0, z, 'grass_block'); s.set(x, 1, z, r.choice(['poppy', 'dandelion', 'azure_bluet', 'oxeye_daisy', 'cornflower', 'allium']))

# 1. 약초 가게: 낮은 돌 1층 + 이끼 지붕 + 약초밭
def herb_shop():
    s = new('start/herb_shop', 31); r = s.r
    h = townhouse(s, r, 0, 0, 7, 8, ground=4, upper=3, axis='z', full='mossy_cobblestone', stair='mossy_cobblestone', eave='oak',
                  frame='oak', gable=INF4, timber_gable='oak', post_step=3)
    hanging_sign(s, 6, 3, 7, arm_dir='east') if False else hanging_sign(s, 1, 3, 8, arm_dir='south') if False else None
    s.set(5, 3, 8, 'oak_fence'); s.set(5, 2, 8, 'oak_hanging_sign[attached=false,rotation=0,waterlogged=false]')
    for z in (1, 3, 5):
        for y in (1, 2, 3, 4):
            if r.random() < 0.7 and s.get(-1, y, z) is None: s.set(-1, y, z, 'vine[east=true,north=false,south=false,up=false,west=false]')
    s.set(7, 4, 3, 'bee_nest[facing=east,honey_level=3]')
    # 약초밭 (동쪽, 울타리)
    for x in range(8, 13):
        for z in range(1, 8):
            edge = x in (8, 12) or z in (1, 7)
            if edge: s.set(x, 1, z, 'oak_fence'); s.set(x, 0, z, 'dirt_path')
            else: s.set(x, 0, z, 'farmland[moisture=7]' if z % 2 else 'grass_block'); s.set(x, 1, z, r.choice(['sweet_berry_bush[age=3]', 'allium', 'blue_orchid', 'fern', 'lily_of_the_valley']) if z % 2 == 0 else r.choice(['beetroots[age=3]', 'carrots[age=7]']))
    s.set(10, 1, 7, 'oak_fence_gate[facing=south,open=false,in_wall=false,powered=false]')
    s.set(10, 1, 4, 'composter[level=7]') ; s.set(10, 0, 4, 'dirt')
    s.set(1, 1, 1, 'brewing_stand'); s.set(2, 1, 1, 'cauldron'); s.set(5, 1, 1, 'barrel[facing=up,open=false]')
    for x in (1, 5): s.set(x, 1, 4, 'potted_red_mushroom' if x == 1 else 'potted_allium')
    s.set(3, 4, 4, 'lantern[hanging=true]')
    path(s, r, 2, 4, 9, 11); ground_skirt(s, r, -1, 13, -1, 8, ring=1)
    done(s, '약초 가게 (엘린)')

# 2. 잡화점: 2층 돌출 + 줄무늬 차양 + 가판대 2개
def general_store():
    s = new('start/general_store', 32); r = s.r
    h = townhouse(s, r, 0, 0, 9, 8, ground=4, upper=4, axis='z', full='spruce_planks', stair='spruce', eave='dark_oak', post_step=4)
    for x in range(-1, 10):
        c = 'red' if x % 2 == 0 else 'white'
        s.set(x, 4, 9, f'{c}_wool') if s.get(x, 4, 9) is None else None
        s.set(x, 3, 10, f'{c}_carpet') if False else None
    stall(s, -6, 9, 'red'); stall(s, 11, 9, 'blue')
    for x, b in ((1, 'barrel[facing=up,open=false]'), (2, 'barrel[facing=up,open=false]'), (6, 'barrel[facing=up,open=false]'), (7, 'pumpkin')):
        s.set(x, 1, 9, b)
    s.set(1, 2, 9, 'potted_dandelion'); s.set(7, 2, 9, 'carved_pumpkin[facing=south]') if False else None
    s.set(9, 4, 4, 'dark_oak_fence'); s.set(10, 4, 4, 'dark_oak_fence'); s.set(10, 3, 4, 'dark_oak_hanging_sign[attached=false,rotation=4,waterlogged=false]')
    for x in range(2, 8, 2): s.set(x, 1, 1, 'barrel[facing=south,open=false]'); s.set(x + 1, 1, 1, 'chest[facing=south,type=single]')
    s.set(4, 4, 4, 'lantern[hanging=true]'); chandelier(s, 4, 9, 4, 1)
    path(s, r, -6, 14, 10, 12); ground_skirt(s, r, -6, 14, -2, 9, ring=1)
    done(s, '잡화점 + 가판대')

# 3. 장비점: 돌 2층 대장간 + 화덕 별채 (경사 지붕)
def gear_shop():
    s = new('start/gear_shop', 33); r = s.r
    h = townhouse(s, r, 0, 0, 9, 8, ground=5, upper=4, axis='x', full='deepslate_tiles', stair='deepslate_tile', eave='dark_oak', infill=INF4, post_step=4)
    # 화덕 작업장 (동쪽): 돌 기둥 + 한쪽 경사 지붕
    for x in range(10, 15):
        for z in range(1, 7): s.set(x, 0, z, r.choice(['cobblestone', 'stone_bricks', 'gravel', 'andesite']))
    for (x, z) in ((14, 1), (14, 6)):
        for y in range(1, 5): s.set(x, y, z, r.choice(['stone_bricks', 'cobblestone', 'polished_andesite']))
    for x in range(10, 16):
        yy = 6 - (x - 10) // 2
        for z in range(0, 8): s.set(x, yy, z, 'deepslate_tile_slab[type=bottom]' if (x - 10) % 2 else 'deepslate_tile_slab[type=top]')
    for y in range(1, 12):
        for x in (12, 13): s.set(x, y, 1, r.choice(['bricks', 'bricks', 'stone_bricks']) if y > 3 else r.choice(['stone_bricks', 'cobblestone']))
    s.set(12, 12, 1, 'campfire[lit=true,facing=south,signal_fire=false,waterlogged=false]'); s.set(13, 12, 1, 'brick_slab[type=bottom]')
    s.set(12, 1, 2, 'blast_furnace[facing=south,lit=true]'); s.set(13, 1, 2, 'lava_cauldron')
    s.set(11, 1, 4, 'anvil[facing=east]'); s.set(13, 1, 4, 'grindstone[face=floor,facing=south]'); s.set(11, 1, 6, 'smithing_table'); s.set(13, 1, 6, 'barrel[facing=up,open=false]')
    s.set(12, 4, 4, 'lantern[hanging=true]')
    for z in (3, 4, 5): s.set(15, 1, z, 'spruce_fence'); s.set(15, 2, z, 'spruce_fence')
    s.set(1, 1, 1, 'chest[facing=south,type=single]'); s.set(7, 1, 1, 'anvil[facing=south]'); s.set(4, 5, 4, 'lantern[hanging=true]')
    hanging_sign(s, 0, 3, 8, arm_dir='south') if False else (s.set(1, 4, 9, 'dark_oak_fence'), s.set(1, 3, 9, 'dark_oak_hanging_sign[attached=false,rotation=0,waterlogged=false]'))
    path(s, r, 3, 5, 9, 11); ground_skirt(s, r, -1, 15, -2, 9, ring=1)
    done(s, '장비점 + 대장간')

# 4. 전직 교관 4곳: 같은 틀 + 계열 색 박공·깃발 + 훈련장 소품
def trainer(name, color, seed, label, yard):
    s = new('start/trainer_' + name, seed); r = s.r
    h = townhouse(s, r, 0, 0, 9, 9, ground=4, upper=4, axis='z', full='deepslate_tiles', stair='deepslate_tile', eave='dark_oak',
                  gable=(f'{color}_terracotta',) * 2 + INF4[:2], post_step=4)
    for x in (3, 5): s.set(x, 3, 9, f'{color}_wall_banner[facing=south]') if s.get(x, 3, 9) is None else None
    for x in (2, 6): s.set(x, 4, 10, 'lantern[hanging=true]')
    for x in range(2, 7):
        for z in range(2, 7): s.set(x, 1, z, f'{color}_carpet')
    s.set(4, 4, 4, 'lantern[hanging=true]')
    # 훈련장 (서쪽 앞마당, 울타리)
    for x in range(-7, -1):
        for z in range(3, 10):
            s.set(x, 0, z, r.choice(['coarse_dirt', 'dirt_path', 'gravel', 'coarse_dirt']))
            if x in (-7,) or z in (3, 9): s.set(x, 1, z, 'spruce_fence')
    for spot, b in yard: s.set(*spot, b)
    path(s, r, 3, 5, 10, 12); ground_skirt(s, r, -7, 9, -2, 10, ring=1)
    done(s, label)

def dummy(x, z):
    return [((x, 1, z), 'oak_fence'), ((x, 2, z), 'hay_block[axis=y]'), ((x, 3, z), 'carved_pumpkin[facing=south]'), ((x - 1, 2, z), 'oak_fence'), ((x + 1, 2, z), 'oak_fence')]

# 5. 브람 농가: 돌 하단 + 목조, 긴 차양, 장작, 굴뚝
def bram_farmhouse():
    s = new('farm/bram_farmhouse', 35); r = s.r
    h = townhouse(s, r, 0, 0, 9, 7, ground=4, upper=3, jetty=0, axis='x', full='spruce_planks', stair='spruce', eave='dark_oak',
                  infill=('white_terracotta', 'white_terracotta', 'calcite'), frame='spruce', timber_gable='spruce', door_x=3, post_step=4)
    for x in range(1, 9):   # 앞 차양
        s.set(x, 4, 8, 'spruce_stairs[facing=north,half=bottom,shape=straight]'); s.set(x, 4, 9, 'spruce_stairs[facing=north,half=bottom,shape=straight]') if False else None
        s.set(x, 0, 8, 'spruce_planks')
    for x in (1, 8): s.set(x, 1, 8, 'spruce_fence'); s.set(x, 2, 8, 'spruce_fence'); s.set(x, 3, 8, 'spruce_fence')
    for x in (6, 7): s.set(x, 1, 8, 'spruce_stairs[facing=north,half=bottom,shape=straight]')
    for y in range(0, h['top'] + 1): s.set(-1, y, 3, r.choice(['cobblestone', 'cobblestone', 'mossy_cobblestone']))
    s.set(-1, h['top'] + 1, 3, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    woodpile(s, 10, 2); woodpile(s, 10, 3)
    for z in (1, 4): s.set(13, 1, z, 'spruce_fence'); s.set(13, 2, z, 'spruce_fence')
    for z in range(1, 5): s.set(10, 3, z, 'spruce_slab[type=bottom]'); s.set(11, 3, z, 'spruce_slab[type=bottom]'); s.set(12, 3, z, 'spruce_slab[type=bottom]'); s.set(13, 3, z, 'spruce_stairs[facing=west,half=bottom,shape=straight]')
    s.set(9, 1, 9, 'hay_block[axis=y]'); s.set(10, 1, 9, 'hay_block[axis=x]'); s.set(10, 2, 9, 'hay_block[axis=y]')
    s.set(0, 1, 9, 'composter[level=4]'); cart(s, 11, 7)
    s.set(1, 1, 1, 'crafting_table'); s.set(2, 1, 1, 'smoker[facing=south,lit=false]'); s.set(7, 1, 1, 'white_bed[facing=east,part=head,occupied=false]'); s.set(6, 1, 1, 'white_bed[facing=east,part=foot,occupied=false]')
    s.set(4, 4, 3, 'lantern[hanging=true]')
    path(s, r, 2, 4, 9, 11); ground_skirt(s, r, -2, 14, -1, 9, ring=1)
    done(s, '브람 농가')

# 6. 헛간: 돌 하단 2칸 + 판자벽 + 매우 가파른 지붕 + 큰 문·다락 문
def barn():
    s = new('farm/barn', 36); r = s.r
    stone_box(s, r, 0, 0, 11, 10, 1, 2)
    box(s, r, 0, 0, 11, 10, 3, 4, frame='dark_oak', infill=('spruce_planks', 'spruce_planks', 'stripped_spruce_log[axis=y]'), found=False, windows=False, post_step=5)
    for x in range(1, 10):
        for z in range(1, 9): s.set(x, 0, z, r.choice(['coarse_dirt', 'coarse_dirt', 'hay_block[axis=y]' if False else 'coarse_dirt']))
    top = steep_roof(s, r, 0, 10, 0, 9, 7, axis='z', full='dark_oak_planks', stair='dark_oak', eave='spruce', gable=('spruce_planks', 'stripped_spruce_log[axis=y]'), timber_gable='dark_oak', moss=0.05, alt=('spruce_planks',))
    for x in range(4, 7):
        for y in range(1, 6): s.set(x, y, 9, 'air'); s.set(x, y, 8, 'air')
    for y in range(1, 6): s.set(3, y, 9, 'stripped_dark_oak_log[axis=y]'); s.set(7, y, 9, 'stripped_dark_oak_log[axis=y]')
    for x in range(3, 8): s.set(x, 6, 9, 'dark_oak_log[axis=x]')
    for (x, y) in ((5, 8), (5, 9)): s.set(x, y, 9, 'air'); s.set(x, y, 10, None)
    s.set(5, 10, 10, 'dark_oak_fence'); s.set(5, 11, 10, 'dark_oak_log[axis=z]'); s.set(5, 10, 11, 'chain[axis=y]') if False else None
    for x in range(1, 10):
        for z in range(1, 4): s.set(x, 7, z, 'spruce_planks')
    for x, z in ((1, 1), (2, 1), (3, 1), (1, 2), (8, 1), (9, 1), (9, 2), (1, 7)): s.set(x, 1, z, 'hay_block[axis=y]')
    for x, z in ((2, 8), (3, 8)): s.set(x, 8, z, 'hay_block[axis=x]') if False else None
    for x in range(2, 8): s.set(x, 8, 2, 'hay_block[axis=x]') if x % 2 else None
    for z in range(4, 8): s.set(8, 1, z, 'spruce_fence'); s.set(8, 2, z, 'spruce_fence')
    s.set(5, 6, 5, 'lantern[hanging=true]')
    path(s, r, 3, 7, 10, 12); ground_skirt(s, r, -1, 11, -1, 10, ring=1)
    done(s, '헛간')

herb_shop(); general_store(); gear_shop()
trainer('warrior', 'red', 41, '전사 교관 (훈련 허수아비)', dummy(-5, 6) + dummy(-3, 6) + [((2, 1, 1), 'anvil[facing=south]'), ((6, 1, 1), 'grindstone[face=floor,facing=south]')])
trainer('archer', 'green', 42, '궁수 교관 (과녁장)', [((-6, 1, 4), 'target'), ((-6, 2, 4), 'target'), ((-6, 1, 6), 'target'), ((-6, 1, 8), 'target'), ((-6, 2, 8), 'target'), ((2, 1, 1), 'fletching_table'), ((-2, 1, 8), 'hay_block[axis=y]')])
trainer('mage', 'blue', 43, '마법사 교관 (자수정 정원)', [((-5, 1, 5), 'amethyst_block'), ((-5, 2, 5), 'amethyst_cluster[facing=up,waterlogged=false]'), ((-3, 1, 7), 'amethyst_block'), ((-3, 2, 7), 'large_amethyst_bud[facing=up,waterlogged=false]'), ((2, 1, 1), 'enchanting_table'), ((6, 1, 1), 'bookshelf'), ((6, 2, 1), 'bookshelf'), ((7, 1, 1), 'bookshelf')])
trainer('thief', 'purple', 44, '도적 교관 (밀수품 창고)', [((-5, 1, 5), 'barrel[facing=up,open=false]'), ((-4, 1, 5), 'barrel[facing=up,open=false]'), ((-5, 2, 5), 'barrel[facing=up,open=false]'), ((-3, 1, 8), 'cobweb'), ((-6, 1, 7), 'chest[facing=east,type=single]'), ((2, 1, 1), 'barrel[facing=up,open=false]'), ((6, 1, 1), 'chest[facing=south,type=single]')])
bram_farmhouse(); barn()

os.makedirs('out4', exist_ok=True)
items = []
for s, label, anchor in built:
    p = os.path.join('out4', s.name.replace('/', '_'))
    W, H, L, n = s.save(p + '.schem', anchor=anchor)
    render(view(s, 0), p + '.png', scale=10)
    items.append((p + '.png', label))
    print(s.name, f'{W}x{H}x{L}', n)
print(sheet(items, 'start_village_preview_v4.png', cols=3))
