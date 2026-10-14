"""v3 디테일: 버팀목·꽃상자·아치창·바지보드·이끼 지붕·피라미드 지붕·간판·우물·손수레·샹들리에·가구"""
from lib2 import *

def braces(s, x0, x1, z0, z1, y_top, xs, zs, frame='dark_oak', sides=('n', 's', 'e', 'w'), skip=()):
    """창 없는 패널 위 모서리에 거꾸로 계단 = 까치발 버팀목 (바깥 평면)"""
    def ok(x, y, z): return s.get(x, y, z) is None
    for i in range(len(xs) - 1):
        a, b = xs[i], xs[i + 1]
        if b - a < 3: continue
        for side, z, f1, f2 in (('n', z0, 'east', 'west'), ('s', z1, 'east', 'west')):
            if side not in sides: continue
            if ok(a + 1, y_top - 1, z): s.set(a + 1, y_top - 1, z, f'{frame}_stairs[facing={f2},half=top,shape=straight]')
            if ok(b - 1, y_top - 1, z): s.set(b - 1, y_top - 1, z, f'{frame}_stairs[facing={f1},half=top,shape=straight]')
    for i in range(len(zs) - 1):
        a, b = zs[i], zs[i + 1]
        if b - a < 3: continue
        for side, x in (('w', x0), ('e', x1)):
            if side not in sides: continue
            if ok(x, y_top - 1, a + 1): s.set(x, y_top - 1, a + 1, f'{frame}_stairs[facing=north,half=top,shape=straight]')
            if ok(x, y_top - 1, b - 1): s.set(x, y_top - 1, b - 1, f'{frame}_stairs[facing=south,half=top,shape=straight]')

def flower_box(s, r, x, y, z):
    s.set(x, y, z, r.choice(['flowering_azalea_leaves[persistent=true,distance=1,waterlogged=false]', 'azalea_leaves[persistent=true,distance=1,waterlogged=false]']))

def mossy_roof(s, r, chance=0.12, moss='mossy_cobblestone'):
    for k, v in list(s.b.items()):
        if 'deepslate_tile_stairs' in v and r.random() < chance:
            s.b[k] = v.replace('deepslate_tile_stairs', f'{moss}_stairs')
        elif 'deepslate_tile_slab' in v and r.random() < chance:
            s.b[k] = v.replace('deepslate_tile_slab', f'{moss}_slab')

def barge_x(s, x0, x1, z0, z1, y, over=1, frame='dark_oak'):
    """박공 끝(x0-over, x1+over 바깥) 장식 들보: 경사를 따라 거꾸로 계단"""
    a, b, yy = z0 - over, z1 + over, y
    while a < b:
        for x in (x0 - over - 1, x1 + over + 1):
            s.set(x, yy, a, f'{frame}_stairs[facing=south,half=bottom,shape=straight]')
            s.set(x, yy, b, f'{frame}_stairs[facing=north,half=bottom,shape=straight]')
        a += 1; b -= 1; yy += 1
    if a == b:
        for x in (x0 - over - 1, x1 + over + 1): s.set(x, yy, a, f'{frame}_slab[type=bottom]')

def pyramid(s, x0, x1, z0, z1, y, mat='deepslate_tile', over=1, spire=True):
    xa, xb, za, zb, yy = x0 - over, x1 + over, z0 - over, z1 + over, y
    while xa <= xb and za <= zb:
        for x in range(xa, xb + 1):
            s.set(x, yy, za, f'{mat}_stairs[facing=south,half=bottom,shape=straight]')
            s.set(x, yy, zb, f'{mat}_stairs[facing=north,half=bottom,shape=straight]')
        for z in range(za + 1, zb):
            s.set(xa, yy, z, f'{mat}_stairs[facing=east,half=bottom,shape=straight]')
            s.set(xb, yy, z, f'{mat}_stairs[facing=west,half=bottom,shape=straight]')
        s.set(xa, yy, za, f'{mat}_stairs[facing=south,half=bottom,shape=outer_left]'); s.set(xb, yy, za, f'{mat}_stairs[facing=south,half=bottom,shape=outer_right]')
        s.set(xa, yy, zb, f'{mat}_stairs[facing=north,half=bottom,shape=outer_right]'); s.set(xb, yy, zb, f'{mat}_stairs[facing=north,half=bottom,shape=outer_left]')
        for x in range(xa + 1, xb):
            for z in range(za + 1, zb): pass
        if xb - xa <= 1 or zb - za <= 1: break
        xa += 1; xb -= 1; za += 1; zb -= 1; yy += 1
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    s.set(cx, yy + 1, cz, f'{mat}_slab[type=bottom]') if xa == xb else None
    if spire:
        s.set(cx, yy + 1, cz, 'dark_oak_fence'); s.set(cx, yy + 2, cz, 'dark_oak_fence'); s.set(cx, yy + 3, cz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    return yy

def hanging_sign(s, x, y, z, arm_dir='south', wood='dark_oak'):
    """벽에서 튀어나온 팔(울타리) + 매달린 간판"""
    dx, dz = {'south': (0, 1), 'north': (0, -1), 'east': (1, 0), 'west': (-1, 0)}[arm_dir]
    s.set(x + dx, y, z + dz, f'{wood}_fence')
    rot = 0 if arm_dir in ('south', 'north') else 4
    s.set(x + dx, y - 1, z + dz, f'{wood}_hanging_sign[attached=false,rotation={rot},waterlogged=false]')

def well(s, r, cx, cz):
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if (x, z) == (cx, cz): s.set(x, 0, z, 'water[level=0]'); s.set(x, -1, z, 'water[level=0]'); s.set(x, -2, z, 'mossy_cobblestone')
            else: s.set(x, 0, z, r.choice(['cobblestone', 'mossy_cobblestone', 'stone_bricks'])); s.set(x, 1, z, r.choice(['cobblestone_wall', 'mossy_cobblestone_wall']))
    for x, z in ((cx - 1, cz - 1), (cx + 1, cz + 1)):
        s.set(x, 2, z, 'spruce_fence'); s.set(x, 3, z, 'spruce_fence')
    for x in range(cx - 1, cx + 2):
        s.set(x, 4, cz - 1, 'spruce_stairs[facing=south,half=bottom,shape=straight]'); s.set(x, 4, cz + 1, 'spruce_stairs[facing=north,half=bottom,shape=straight]')
        s.set(x, 4, cz, 'spruce_slab[type=bottom]')
    s.set(cx, 3, cz, 'chain[axis=y]'); s.set(cx, 2, cz, 'chain[axis=y]') if False else None

def cart(s, x, z, facing='east'):
    """손수레: 통·건초 + 바퀴(트랩도어)"""
    s.set(x, 1, z, 'spruce_slab[type=top]'); s.set(x + 1, 1, z, 'spruce_slab[type=top]')
    s.set(x, 2, z, 'barrel[facing=up,open=false]'); s.set(x + 1, 2, z, 'hay_block[axis=x]')
    for xx in (x, x + 1):
        s.set(xx, 1, z - 1, 'dark_oak_trapdoor[facing=north,half=bottom,open=true]')
        s.set(xx, 1, z + 1, 'dark_oak_trapdoor[facing=south,half=bottom,open=true]')
    s.set(x + 2, 1, z, 'spruce_fence'); s.set(x - 1, 1, z, 'spruce_slab[type=bottom]') if False else None

def woodpile(s, x0, z, n=3, y=1):
    for i in range(n):
        s.set(x0 + i, y, z, 'oak_log[axis=z]' if i % 2 else 'spruce_log[axis=z]')
        if i < n - 1: s.set(x0 + i, y + 1, z, 'birch_log[axis=z]' if i % 2 else 'oak_log[axis=z]')
    s.set(x0 + n, y, z, 'chopping' if False else 'spruce_slab[type=bottom]')

def chandelier(s, x, y, z, length=1):
    for i in range(length): s.set(x, y - i, z, 'chain[axis=y]')
    yy = y - length
    s.set(x, yy, z, 'dark_oak_fence')
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)): s.set(x + dx, yy, z + dz, 'dark_oak_fence'); s.set(x + dx, yy - 1, z + dz, 'lantern[hanging=true]')

def table(s, x0, z0, x1, z1, y=1, wood='dark_oak', chairs=True):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1): s.set(x, y, z, f'{wood}_fence'); s.set(x, y + 1, z, f'{wood}_pressure_plate') if False else s.set(x, y + 1, z, 'white_carpet') if False else None
    # 상판: 거꾸로 슬랩은 위층이라 → 울타리 + 압력판 대신 상판으로 트랩도어
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1): s.set(x, y + 1, z, f'{wood}_pressure_plate')
    if chairs:
        for x in range(x0, x1 + 1):
            s.set(x, y, z0 - 1, f'spruce_stairs[facing=north,half=bottom,shape=straight]') if False else s.set(x, y, z0 - 1, 'spruce_stairs[facing=south,half=bottom,shape=straight]')
            s.set(x, y, z1 + 1, 'spruce_stairs[facing=north,half=bottom,shape=straight]')

def ground_skirt(s, r, x0, x1, z0, z1, y=0, ring=1):
    """건물 둘레 바닥: 풀·흙·이끼·꽃·덤불 섞기 (이미 있는 칸은 건너뜀)"""
    for x in range(x0 - ring, x1 + ring + 1):
        for z in range(z0 - ring, z1 + ring + 1):
            if x0 <= x <= x1 and z0 <= z <= z1: continue
            if s.get(x, y, z) is not None: continue
            s.set(x, y, z, r.choice(['grass_block'] * 5 + ['coarse_dirt', 'moss_block', 'rooted_dirt', 'podzol']))
            if s.get(x, y + 1, z) is None and r.random() < 0.35:
                s.set(x, y + 1, z, r.choice(['grass', 'grass', 'fern', 'poppy', 'dandelion', 'oxeye_daisy', 'lily_of_the_valley']))
