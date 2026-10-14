"""두 번째 마을 스타일 샘플 A·B·C (버섯 연구소 기준)"""
import os, random, math
from schem import S, autoconnect, render, view
from lib4 import *
from sheet import sheet

def step_roof_full(s, r, x0, x1, z0, z1, y, mats, over=1):
    """계단 모양 지붕 (버섯 블록처럼 계단이 없는 재료): 한 칸 안으로 들어갈 때 1칸 올라감, 2겹"""
    a, b, yy = z0 - over, z1 + over, y
    while a <= b:
        for x in range(x0 - over, x1 + over + 1):
            for z in (a, b): s.set(x, yy, z, r.choice(mats))
        for z in range(max(a + 1, z0), min(b - 1, z1) + 1):
            s.set(x0, yy, z, r.choice(INF4)); s.set(x1, yy, z, r.choice(INF4))
        a += 1; b -= 1; yy += 1
    return yy

# A. 하프팀버 + 버섯 지붕
def sample_a():
    s = S('v2/sample_a'); r = random.Random(1); s.r = r
    stone_box(s, r, 0, 0, 9, 8, 1, 4, mats=('mossy_stone_bricks', 'mossy_cobblestone', 'stone_bricks', 'cobblestone', 'moss_block'))
    door(s, 4, 1, 7); deep_window(s, 2, 2, 7, 'south'); deep_window(s, 6, 2, 7, 'south')
    jetty_floor(s, -1, -1, 9, 8, 5)
    box(s, r, -1, -1, 11, 10, 6, 4, frame='dark_oak', infill=('mushroom_stem',) * 5 + ('calcite',), found=False, post_step=5)
    step_roof_full(s, r, -1, 9, -1, 8, 10, ('brown_mushroom_block',) * 6 + ('red_mushroom_block',))
    for (x, z) in ((-2, 9), (10, 9), (-2, -2), (10, -2)): s.set(x, 9, z, 'shroomlight')
    s.set(4, 4, 8, 'shroomlight') if False else s.set(4, 4, 8, 'lantern[hanging=true]')
    path(s, r, 3, 5, 8, 10)
    autoconnect(s); return s

# B. 거대 버섯 집 (줄기 벽 + 빨간 갓 지붕)
def sample_b():
    s = S('v2/sample_b'); r = random.Random(2); s.r = r
    R = 3
    for y in range(0, 7):
        for x in range(-R, R + 1):
            for z in range(-R, R + 1):
                d = math.hypot(x, z)
                if d <= R + 0.4:
                    edge = d > R - 0.6
                    if y == 0: s.set(x, y, z, 'mossy_cobblestone' if edge else 'spruce_planks')
                    elif edge: s.set(x, y, z, 'mushroom_stem')
                    else: s.set(x, y, z, 'air')
    for y in (2, 3):
        for (x, z) in ((-R, 0), (R, 0), (0, -R)): s.set(x, y, z, 'glass_pane')
    door(s, 0, 1, R, wood='spruce')
    # 갓: 반구 (반지름 6, 높이 5)
    CR, base = 6, 7
    for y in range(base, base + 5):
        rad = CR * math.sqrt(max(0, 1 - ((y - base) / 5.0) ** 2)) + 0.3
        for x in range(-CR - 1, CR + 2):
            for z in range(-CR - 1, CR + 2):
                d = math.hypot(x, z)
                top = y == base + 4
                if (rad - 1.2 < d <= rad) or (top and d <= rad): s.set(x, y, z, 'red_mushroom_block' if r.random() > 0.12 else 'mushroom_stem')
                elif d <= rad - 1.2 and y == base: s.set(x, y, z, 'brown_mushroom_block')
    for x in range(-CR, CR + 1):
        for z in range(-CR, CR + 1):
            if math.hypot(x, z) <= CR + 0.3 and s.get(x, base, z) is None: s.set(x, base, z, 'brown_mushroom_block')
    # 아래 처마 끝 (갓 가장자리 늘어짐)
    for x in range(-CR - 1, CR + 2):
        for z in range(-CR - 1, CR + 2):
            d = math.hypot(x, z)
            if CR - 0.2 < d <= CR + 0.8: s.set(x, base - 1, z, 'red_mushroom_block')
    # 작은 곁 버섯 (갈색 갓)
    for y in range(0, 4):
        for x in (5, 6):
            for z in (2, 3): s.set(x, y, z, 'mushroom_stem' if y else 'mossy_cobblestone')
    for x in range(3, 9):
        for z in range(0, 6):
            if math.hypot(x - 5.5, z - 2.5) <= 3: s.set(x, 4, z, 'brown_mushroom_block')
    s.set(0, 5, 0, 'shroomlight'); s.set(-2, 1, 4, 'lantern[hanging=false]'); s.set(2, 1, 4, 'lantern[hanging=false]')
    path(s, r, -1, 1, 4, 7)
    autoconnect(s); return s

# C. 통나무 숲 오두막 (껍질 통나무 벽 + 이끼 지붕)
def sample_c():
    s = S('v2/sample_c'); r = random.Random(3); s.r = r
    w, d = 9, 7
    for x in range(-1, w + 1):
        for z in range(-1, d + 1): s.set(x, 0, z, r.choice(['cobblestone', 'mossy_cobblestone', 'stone']))
    for y in range(1, 6):
        for x in range(0, w):
            s.set(x, y, 0, 'spruce_log[axis=x]'); s.set(x, y, d - 1, 'spruce_log[axis=x]')
        for z in range(0, d):
            s.set(0, y, z, 'spruce_log[axis=z]'); s.set(w - 1, y, z, 'spruce_log[axis=z]')
        for (x, z) in ((-1, 0), (-1, d - 1), (w, 0), (w, d - 1)): s.set(x, y, z, 'spruce_log[axis=x]' if y % 2 else None)   # 모서리 통나무 끝 튀어나옴
        for (x, z) in ((0, -1), (w - 1, -1), (0, d), (w - 1, d)): s.set(x, y, z, 'spruce_log[axis=z]' if y % 2 == 0 else None)
    s.fill(1, 1, 1, w - 2, 5, d - 2, 'air')
    for x in (2, 6):
        for y in (2, 3): s.set(x, y, d - 1, 'glass_pane'); s.set(x, y, 0, 'glass_pane')
        s.set(x, 1, d, 'spruce_trapdoor[facing=south,half=top,open=false]')
    door(s, 4, 1, d - 1)
    # 지붕: 완만 + 이끼 섞기 + 처마 두께
    roof(s, r, 0, w - 1, 0, d - 1, 6, mats=('spruce', 'spruce', 'mossy_cobblestone'), eave='dark_oak', gable_fill=('stripped_spruce_log[axis=y]',), over=2)
    for k, v in list(s.b.items()):
        if 'stairs' in v and r.random() < 0.15 and k[1] > 6: s.b[k] = 'minecraft:moss_block'
    # 굴뚝 + 앞 마루
    for y in range(0, 12): s.set(w, y, 3, r.choice(['cobblestone', 'mossy_cobblestone', 'stone']))
    s.set(w, 12, 3, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    for x in range(0, w):
        s.set(x, 0, d + 1, 'spruce_planks'); s.set(x, 0, d + 2, 'spruce_planks')
    for x in (0, w - 1): s.set(x, 1, d + 2, 'spruce_fence'); s.set(x, 2, d + 2, 'spruce_fence'); s.set(x, 3, d + 2, 'spruce_fence')
    s.set(2, 3, d + 1, 'lantern[hanging=true]') if False else s.set(4, 4, d + 1, 'lantern[hanging=true]')
    woodpile(s, -3, 2); woodpile(s, -3, 3)
    autoconnect(s); return s

items = []
os.makedirs('v2s', exist_ok=True)
for fn, label in ((sample_a, 'A. 하프팀버 + 버섯 지붕'), (sample_b, 'B. 거대 버섯 집'), (sample_c, 'C. 통나무 숲 오두막')):
    s = fn(); p = 'v2s/' + s.name.split('/')[1]
    s.save(p + '.schem'); render(view(s, 0), p + '.png', scale=18); items.append((p + '.png', label))
print(sheet(items, 'village2_styles.png', cols=3))
