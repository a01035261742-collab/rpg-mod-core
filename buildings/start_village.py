"""시작 마을 (중세 하프팀버) + 농장"""
import sys
from schem import S, autoconnect, render
from lib import *

OUT = sys.argv[1] if len(sys.argv) > 1 else 'out'
built = []

def finish(s):
    autoconnect(s); built.append(s)

# 1. 촌장 집 (2층, 11x9)
def harold_house():
    s = S('start/harold_house')
    w, d = 11, 9
    timber_box(s, 0, 0, w, d, 8, mid=4)
    top = gable(s, 0, w - 1, 0, d - 1, 9)
    # 문 + 현관 계단 + 랜턴
    door(s, 5, 1, d - 1)
    s.set(5, 0, d, 'stone_brick_stairs[facing=north,half=bottom,shape=straight]')
    for x in (4, 6): s.set(x, 3, d, 'wall_torch[facing=south]')
    # 굴뚝
    for y in range(1, top + 2): s.set(1, y, 1, 'cobblestone')
    s.set(1, top + 2, 1, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    s.set(2, 1, 1, 'furnace[facing=south,lit=false]')
    # 안: 1층 책상·책장, 2층 침대
    s.set(8, 1, 1, 'bookshelf'); s.set(9, 1, 1, 'bookshelf'); s.set(8, 2, 1, 'bookshelf'); s.set(9, 2, 1, 'bookshelf')
    s.set(8, 1, 3, 'spruce_fence'); s.set(8, 2, 3, 'spruce_pressure_plate')
    s.set(9, 1, 3, 'spruce_stairs[facing=west,half=bottom,shape=straight]')
    s.set(3, 1, 6, 'barrel[facing=up,open=false]'); s.set(2, 1, 7, 'chest[facing=east,type=single]')
    s.set(5, 3, 4, 'lantern[hanging=true]'); s.set(5, 7, 4, 'lantern[hanging=true]')
    # 2층 가는 사다리
    for y in range(1, 5): s.set(9, y, 7, 'ladder[facing=north]')
    s.set(9, 4, 7, 'air')
    s.set(2, 5, 2, 'red_bed[facing=south,part=head,occupied=false]'); s.set(2, 5, 3, 'red_bed[facing=south,part=foot,occupied=false]')
    # 앞 화단
    for x in (1, 2, 3, 7, 8, 9):
        s.set(x, 0, d, 'grass_block'); s.set(x, 1, d, ['poppy', 'dandelion', 'azure_bluet'][x % 3])
    finish(s)

# 2. 약초 가게 (7x7, 담쟁이·화분)
def herb_shop():
    s = S('start/herb_shop')
    w, d = 7, 7
    timber_box(s, 0, 0, w, d, 5, frame='spruce', infill='calcite')
    gable_z(s, 0, w - 1, 0, d - 1, 6, mat='spruce', fill='mossy_cobblestone')
    door(s, 3, 1, d - 1, wood='oak')
    for x in (1, 5):
        s.set(x, 1, d, 'potted_fern'); s.set(x, 0, d, 'stone_bricks')
    s.set(2, 1, d, 'composter[level=7]')
    for z in range(0, d): s.set(-1, 3, z, 'oak_leaves[persistent=true,distance=1,waterlogged=false]')
    for z in range(1, d - 1): s.set(w, 4, z, 'oak_leaves[persistent=true,distance=1,waterlogged=false]')
    s.set(1, 1, 1, 'brewing_stand'); s.set(2, 1, 1, 'barrel[facing=up,open=false]'); s.set(5, 1, 1, 'cauldron')
    s.set(5, 1, 3, 'flower_pot'); s.set(1, 1, 3, 'potted_red_mushroom'); s.set(1, 2, 1, 'potted_allium')
    s.set(3, 4, 3, 'lantern[hanging=true]')
    finish(s)

# 3. 잡화점 (9x7, 빨강·흰 차양)
def general_store():
    s = S('start/general_store')
    w, d = 9, 7
    timber_box(s, 0, 0, w, d, 5)
    gable(s, 0, w - 1, 0, d - 1, 6, mat='spruce')
    door(s, 4, 1, d - 1)
    for x in range(0, w):
        s.set(x, 4, d, ('red_wool' if x % 2 == 0 else 'white_wool'))
        s.set(x, 3, d + 1, ('red_carpet' if x % 2 == 0 else 'white_carpet'))
    for x in (0, w - 1): s.set(x, 1, d + 1, 'spruce_fence'); s.set(x, 2, d + 1, 'spruce_fence')
    for x in (1, 2, 6, 7): s.set(x, 1, d, 'barrel[facing=up,open=false]')
    s.set(2, 2, d, 'potted_dandelion'); s.set(6, 2, d, 'potted_poppy')
    for x in range(1, w - 1): s.set(x, 1, 1, 'spruce_slab[type=top]' if x % 2 else 'barrel[facing=south,open=false]')
    s.set(4, 4, 3, 'lantern[hanging=true]')
    finish(s)

# 4. 장비점 (9x7, 대장간 화덕)
def gear_shop():
    s = S('start/gear_shop')
    w, d = 9, 7
    timber_box(s, 0, 0, w, d, 5, infill='white_terracotta', found='cobblestone')
    gable(s, 0, w - 1, 0, d - 1, 6, mat='dark_oak', fill='cobblestone')
    door(s, 4, 1, d - 1)
    # 옆 화덕 (바깥 동쪽)
    s.fill(w, 0, 1, w + 2, 0, 4, 'cobblestone')
    for y in range(1, 4):
        s.set(w + 2, y, 1, 'bricks'); s.set(w + 2, y, 4, 'bricks')
    s.fill(w, 4, 1, w + 2, 4, 4, 'cobblestone_slab[type=bottom]')
    s.set(w + 1, 1, 2, 'blast_furnace[facing=south,lit=true]'); s.set(w + 1, 1, 3, 'anvil[facing=east]')
    s.set(w + 2, 1, 2, 'lava_cauldron'); s.set(w + 2, 1, 3, 'grindstone[face=floor,facing=south]')
    for y in range(5, 10): s.set(w + 2, y, 1, 'bricks')
    s.set(w + 2, 10, 1, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    s.set(1, 1, 1, 'smithing_table'); s.set(2, 1, 1, 'armor_stand' if False else 'chest[facing=south,type=single]')
    s.set(7, 1, 1, 'anvil[facing=south]'); s.set(4, 4, 3, 'lantern[hanging=true]')
    s.set(3, 3, d, 'wall_torch[facing=south]'); s.set(5, 3, d, 'wall_torch[facing=south]')
    finish(s)

# 5. 전직 교관 건물 (9x9, 계열 색)
def trainer(name, wool, deco):
    s = S('start/trainer_' + name)
    w, d = 9, 9
    timber_box(s, 0, 0, w, d, 6, found='stone_bricks')
    top = gable(s, 0, w - 1, 0, d - 1, 7, mat='dark_oak', fill=f'{wool}_wool')
    door(s, 4, 1, d - 1)
    s.set(4, 0, d, 'stone_brick_stairs[facing=north,half=bottom,shape=straight]')
    for x in (3, 5): s.set(x, 3, d, f'{wool}_wall_banner[facing=south]')
    s.set(4, 4, d, 'wall_torch[facing=south]')
    s.fill(1, 0, 1, w - 2, 0, d - 2, f'{wool}_carpet' if False else 'spruce_planks')
    s.fill(2, 1, 2, w - 3, 1, d - 3, f'{wool}_carpet')
    for spot, b in deco: s.set(*spot, b)
    s.set(4, 5, 4, 'lantern[hanging=true]')
    finish(s)

# 6. 광장 분수 (9x9)
def fountain():
    s = S('start/plaza_fountain')
    for x in range(-4, 5):
        for z in range(-4, 5):
            r = max(abs(x), abs(z))
            s.set(x, 0, z, 'stone_bricks' if r % 2 == 0 else 'polished_andesite')
    for x in range(-3, 4):
        for z in range(-3, 4):
            if max(abs(x), abs(z)) == 3: s.set(x, 1, z, 'stone_brick_wall')
            elif max(abs(x), abs(z)) <= 2: s.set(x, 1, z, 'water[level=0]'); s.set(x, 0, z, 'stone_bricks')
    for y in range(1, 4): s.set(0, y, 0, 'chiseled_stone_bricks' if y < 3 else 'stone_bricks')
    s.set(0, 4, 0, 'water[level=0]')
    for x, z in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        s.set(x, 1, z, 'spruce_fence'); s.set(x, 2, z, 'spruce_fence'); s.set(x, 3, z, 'lantern[hanging=false]')
    finish(s)
    return s

# 7. 브람 농가 (9x7, 1층)
def bram_farmhouse():
    s = S('farm/bram_farmhouse')
    w, d = 9, 7
    timber_box(s, 0, 0, w, d, 4, frame='spruce', infill='white_terracotta', found='cobblestone')
    gable(s, 0, w - 1, 0, d - 1, 5, mat='spruce', fill='spruce_planks')
    door(s, 3, 1, d - 1)
    s.set(3, 0, d, 'cobblestone_stairs[facing=north,half=bottom,shape=straight]')
    for x in (5, 6, 7): s.set(x, 1, d, 'hay_block[axis=y]')
    s.set(6, 2, d, 'hay_block[axis=x]')
    s.set(1, 1, d, 'composter[level=4]'); s.set(2, 3, d, 'wall_torch[facing=south]')
    s.set(1, 1, 1, 'crafting_table'); s.set(2, 1, 1, 'smoker[facing=south,lit=false]'); s.set(7, 1, 1, 'white_bed[facing=east,part=head,occupied=false]'); s.set(6, 1, 1, 'white_bed[facing=east,part=foot,occupied=false]')
    s.set(4, 3, 3, 'lantern[hanging=true]')
    finish(s)

# 8. 헛간 (11x9, 큰 문)
def barn():
    s = S('farm/barn')
    w, d = 11, 9
    timber_box(s, 0, 0, w, d, 6, frame='dark_oak', infill='spruce_planks', found='cobblestone', floor='coarse_dirt', windows=False)
    gable(s, 0, w - 1, 0, d - 1, 7, mat='dark_oak', fill='spruce_planks')
    # 큰 문 (3칸 열림)
    for x in range(4, 7):
        for y in range(1, 5): s.set(x, y, d - 1, 'air')
    for x in (3, 7):
        for y in range(1, 6): s.set(x, y, d - 1, 'stripped_dark_oak_log[axis=y]')
    for x in range(3, 8): s.set(x, 5, d - 1, 'dark_oak_log[axis=x]')
    for x, z in ((1, 1), (2, 1), (1, 2), (8, 1), (9, 1), (9, 2), (1, 7)):
        s.set(x, 1, z, 'hay_block[axis=y]')
    s.set(2, 2, 1, 'hay_block[axis=x]'); s.set(9, 2, 1, 'hay_block[axis=z]')
    for z in range(3, 7): s.set(8, 1, z, 'spruce_fence'); s.set(8, 2, z, 'spruce_fence')
    s.fill(9, 0, 3, 9, 0, 6, 'hay_block[axis=y]')
    s.set(5, 5, 4, 'lantern[hanging=true]')
    finish(s)

# 9. 울타리 밭 (13x9)
def field():
    s = S('farm/farm_field')
    w, d = 13, 9
    for x in range(w):
        for z in range(d):
            edge = x in (0, w - 1) or z in (0, d - 1)
            if edge:
                s.set(x, 0, z, 'dirt_path'); s.set(x, 1, z, 'oak_fence')
            elif x == w // 2:
                s.set(x, 0, z, 'water[level=0]')
            else:
                s.set(x, 0, z, 'farmland[moisture=7]')
                crop = 'wheat[age=7]' if x < 4 else 'carrots[age=7]' if x < w // 2 else 'potatoes[age=7]' if x < 10 else 'beetroots[age=3]'
                s.set(x, 1, z, crop)
    s.set(w // 2, 1, d - 1, 'oak_fence_gate[facing=south,open=false,in_wall=false,powered=false]')
    s.set(w // 2, 0, d - 1, 'dirt_path')
    for x in (0, w - 1):
        for z in (0, d - 1): s.set(x, 2, z, 'lantern[hanging=false]')
    s.set(w // 2, 1, 4, 'lily_pad') if False else None
    finish(s)

harold_house(); herb_shop(); general_store(); gear_shop()
trainer('warrior', 'red', [((2, 1, 1), 'anvil[facing=south]'), ((6, 1, 1), 'grindstone[face=floor,facing=south]'), ((4, 1, 1), 'smithing_table')])
trainer('archer', 'green', [((2, 1, 1), 'fletching_table'), ((6, 1, 1), 'target'), ((4, 1, 1), 'hay_block[axis=y]')])
trainer('mage', 'blue', [((2, 1, 1), 'enchanting_table'), ((6, 1, 1), 'bookshelf'), ((6, 2, 1), 'bookshelf'), ((4, 1, 1), 'brewing_stand')])
trainer('thief', 'purple', [((2, 1, 1), 'barrel[facing=up,open=false]'), ((6, 1, 1), 'chest[facing=south,type=single]'), ((4, 1, 1), 'loom[facing=south]')])
fountain(); bram_farmhouse(); barn(); field()

import os
for s in built:
    p = os.path.join(OUT, s.name.replace('/', '_'))
    W, H, L, n = s.save(p + '.schem', anchor=(0, 1, 5) if s.name == 'start/plaza_fountain' else None)
    render(s, p + '.png')
    print(s.name, f'{W}x{H}x{L}', n)
