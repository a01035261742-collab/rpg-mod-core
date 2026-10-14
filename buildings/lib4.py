"""v4: 레퍼런스 기법 — 가파른 지붕(2:1), 돌 1층 + 목조 2층, 대각 버팀목, 창틀, 층마다 박공 돌출"""
from lib3 import *

def stone_box(s, r, x0, z0, w, d, y0, h, mats=None, floor='spruce_planks', quoin='polished_andesite'):
    """돌 1층: 모서리는 다듬은 돌 번갈아 쌓기(quoin), 벽은 돌 섞기, 창은 깊게(안쪽)"""
    mats = mats or ('stone_bricks',) * 4 + ('cobblestone', 'mossy_stone_bricks', 'cracked_stone_bricks', 'andesite', 'mossy_cobblestone', 'tuff')
    x1, z1 = x0 + w - 1, z0 + d - 1
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1): s.set(x, y0 - 1, z, r.choice(mats) if x in (x0, x1) or z in (z0, z1) else floor)
    for y in range(y0, y0 + h):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                corner = x in (x0, x1) and z in (z0, z1)
                if not edge: s.set(x, y, z, 'air'); continue
                if corner: s.set(x, y, z, quoin if (y - y0) % 2 == 0 else 'stone_bricks')
                else: s.set(x, y, z, r.choice(mats))
    return x1, z1

def deep_window(s, x, y, z, out_dir, hgt=2, frame='spruce'):
    """돌벽 깊은 창: 유리는 벽 안쪽 면, 바깥 위·아래는 계단으로 테두리"""
    dx, dz = {'south': (0, 1), 'north': (0, -1), 'east': (1, 0), 'west': (-1, 0)}[out_dir]
    for k in range(hgt): s.set(x, y + k, z, 'glass_pane')
    opp = {'south': 'north', 'north': 'south', 'east': 'west', 'west': 'east'}[out_dir]
    s.set(x + dx, y - 1, z + dz, f'stone_brick_stairs[facing={opp},half=bottom,shape=straight]')
    s.set(x + dx, y + hgt, z + dz, f'stone_brick_stairs[facing={opp},half=top,shape=straight]')

def steep_roof_x(s, r, x0, x1, z0, z1, y, full='deepslate_tiles', stair='deepslate_tile', eave='dark_oak',
                 gable=('spruce_planks',), over=1, alt=('cobbled_deepslate',), moss=0.08):
    """가파른 박공 (높이 2 : 폭 1), 용마루 x방향. 처마 밑 거꾸로 계단. 반환 꼭대기 y"""
    a, b, yy, first = z0 - over, z1 + over, y, True
    def mat(base):
        if r.random() < moss: return 'mossy_cobblestone'
        if alt and r.random() < 0.12: return r.choice(alt)
        return base
    while a < b:
        for x in range(x0 - over, x1 + over + 1):
            if first:
                s.set(x, yy, a, f'{eave}_stairs[facing=south,half=bottom,shape=straight]'); s.set(x, yy, b, f'{eave}_stairs[facing=north,half=bottom,shape=straight]')
                s.set(x, yy - 1, a, f'{eave}_stairs[facing=north,half=top,shape=straight]') if s.get(x, yy - 1, a) is None else None
                s.set(x, yy - 1, b, f'{eave}_stairs[facing=south,half=top,shape=straight]') if s.get(x, yy - 1, b) is None else None
            else:
                fa, fb = mat(full), mat(full)
                s.set(x, yy, a, fa if fa != 'mossy_cobblestone' else 'mossy_cobblestone'); s.set(x, yy, b, fb)
                ma, mb = mat(stair), mat(stair)
                s.set(x, yy + 1, a, f'{ma}_stairs[facing=south,half=bottom,shape=straight]'); s.set(x, yy + 1, b, f'{mb}_stairs[facing=north,half=bottom,shape=straight]')
        lo, hi = max(a + 1, z0), min(b - 1, z1)
        for yyy in (yy, yy + 1):
            for z in range(lo, hi + 1):
                s.set(x0, yyy, z, r.choice(gable)); s.set(x1, yyy, z, r.choice(gable))
                for x in range(x0 + 1, x1): s.set(x, yyy, z, 'air') if s.get(x, yyy, z) is None else None
        yy += 1 if first else 2
        a += 1; b -= 1; first = False
    for x in range(x0 - over, x1 + over + 1):   # 용마루
        if a == b: s.set(x, yy, a, f'{full}' if False else f'{stair}_slab[type=bottom]'); 
        else: s.set(x, yy, a, f'{stair}_slab[type=bottom]'); s.set(x, yy, b, f'{stair}_slab[type=bottom]')
    if a == b:
        s.set(x0, yy - 1, a, r.choice(gable)); s.set(x1, yy - 1, a, r.choice(gable))
    return yy

def diag_brace(s, x_a, x_b, y_bot, y_top, z, frame='dark_oak', axis='x'):
    """패널 안 V자 대각 버팀목 (가운데 아래 → 양쪽 위)"""
    w = x_b - x_a - 1
    if w < 3: return
    mid = (x_a + x_b) // 2
    for i, y in enumerate(range(y_bot, y_top)):
        for xx in (mid - i, mid + i + (0 if w % 2 else 1) - (0 if w % 2 else 1)):
            if x_a < xx < x_b:
                if axis == 'x': s.set(xx, y, z, f'stripped_{frame}_log[axis=y]')
                else: s.set(z, y, xx, f'stripped_{frame}_log[axis=y]')

def steep_roof(s, r, x0, x1, z0, z1, y, axis='x', full='deepslate_tiles', stair='deepslate_tile', eave='dark_oak',
               gable=('spruce_planks',), over=1, alt=('cobbled_deepslate',), moss=0.08, timber_gable=None):
    """가파른 박공. axis='x' 용마루가 x방향(박공은 동·서), 'z' 용마루 z방향(박공은 남·북 = 정면)."""
    if axis == 'x':
        A0, A1, B0, B1 = z0, z1, x0, x1
        put = lambda a, b, yy, st: s.set(b, yy, a, st)
        fa, fb, ua, ub = 'south', 'north', 'north', 'south'
    else:
        A0, A1, B0, B1 = x0, x1, z0, z1
        put = lambda a, b, yy, st: s.set(a, yy, b, st)
        fa, fb, ua, ub = 'east', 'west', 'west', 'east'
    get = (lambda a, b, yy: s.get(b, yy, a)) if axis == 'x' else (lambda a, b, yy: s.get(a, yy, b))
    def mat(base):
        if r.random() < moss: return 'mossy_cobblestone'
        if alt and r.random() < 0.12: return r.choice(alt)
        return base
    a, b, yy, first = A0 - over, A1 + over, y, True
    gable_cells = []
    while a < b:
        for t in range(B0 - over, B1 + over + 1):
            if first:
                put(a, t, yy, f'{eave}_stairs[facing={fa},half=bottom,shape=straight]'); put(b, t, yy, f'{eave}_stairs[facing={fb},half=bottom,shape=straight]')
                if get(a, t, yy - 1) is None: put(a, t, yy - 1, f'{eave}_stairs[facing={ua},half=top,shape=straight]')
                if get(b, t, yy - 1) is None: put(b, t, yy - 1, f'{eave}_stairs[facing={ub},half=top,shape=straight]')
            else:
                m1, m2 = mat(full), mat(full)
                put(a, t, yy, m1); put(b, t, yy, m2)
                s1, s2 = mat(stair), mat(stair)
                put(a, t, yy + 1, f'{s1}_stairs[facing={fa},half=bottom,shape=straight]'); put(b, t, yy + 1, f'{s2}_stairs[facing={fb},half=bottom,shape=straight]')
        lo, hi = max(a + 1, A0), min(b - 1, A1)
        for yyy in ((yy,) if first else (yy, yy + 1)):
            for q in range(lo, hi + 1): gable_cells.append((q, yyy))
        yy += 1 if first else 2
        a += 1; b -= 1; first = False
    for t in range(B0 - over, B1 + over + 1):
        if a == b: put(a, t, yy, f'{stair}_slab[type=bottom]')
        else: put(a, t, yy, f'{stair}_slab[type=bottom]'); put(b, t, yy, f'{stair}_slab[type=bottom]')
    if a == b: gable_cells.append((a, yy - 1))
    # 박공벽: 반목조 무늬 (가운데 세로 들보 + 가로 띠)
    mid = (A0 + A1) // 2
    for q, yyy in gable_cells:
        for t in (B0, B1):
            if timber_gable and (q == mid or (yyy - y) % 4 == 2):
                put(q, t, yyy, f'stripped_{timber_gable}_log[axis=y]' if q == mid else (f'{timber_gable}_log[axis=z]' if axis == 'x' else f'{timber_gable}_log[axis=x]'))
            else:
                put(q, t, yyy, r.choice(gable))
    return yy

def steep_pyramid(s, r, x0, x1, z0, z1, y, full='deepslate_tiles', stair='deepslate_tile', eave='dark_oak', over=1, moss=0.06):
    """가파른 사각뿔 지붕 + 처마 끝 들림(첫 단은 나무 계단, 그 위로 2:1)"""
    xa, xb, za, zb, yy, first = x0 - over, x1 + over, z0 - over, z1 + over, y, True
    def m(base):
        return 'mossy_cobblestone' if r.random() < moss else base
    while xb - xa >= 1 and zb - za >= 1:
        ring = [(x, za, 'south') for x in range(xa, xb + 1)] + [(x, zb, 'north') for x in range(xa, xb + 1)] + \
               [(xa, z, 'east') for z in range(za + 1, zb)] + [(xb, z, 'west') for z in range(za + 1, zb)]
        for x, z, f in ring:
            if first:
                s.set(x, yy, z, f'{eave}_stairs[facing={f},half=bottom,shape=straight]')
                opp = {'south': 'north', 'north': 'south', 'east': 'west', 'west': 'east'}[f]
                if s.get(x, yy - 1, z) is None: s.set(x, yy - 1, z, f'{eave}_stairs[facing={opp},half=top,shape=straight]')
            else:
                s.set(x, yy, z, m(full)); s.set(x, yy + 1, z, f'{m(stair)}_stairs[facing={f},half=bottom,shape=straight]')
        yy += 1 if first else 2
        xa += 1; xb -= 1; za += 1; zb -= 1; first = False
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    top = yy
    for x in range(xa, xb + 1):
        for z in range(za, zb + 1): s.set(x, yy - 1, z, full); s.set(x, yy, z, f'{stair}_slab[type=bottom]')
    s.set(cx, top, cz, 'dark_oak_fence'); s.set(cx, top + 1, cz, 'dark_oak_fence'); s.set(cx, top + 2, cz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    return top

def jetty_floor(s, x0, z0, x1, z1, y, frame='dark_oak', sides=('n', 's', 'e', 'w')):
    """층 사이 들보 테두리 + 바닥 + 까치발 (x0..x1, z0..z1 = 위층 바깥선)"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1): s.set(x, y, z, 'spruce_planks')
    for x in range(x0, x1 + 1): s.set(x, y, z0, f'{frame}_log[axis=x]'); s.set(x, y, z1, f'{frame}_log[axis=x]')
    for z in range(z0, z1 + 1): s.set(x0, y, z, f'{frame}_log[axis=z]'); s.set(x1, y, z, f'{frame}_log[axis=z]')
    if 'n' in sides:
        for x in range(x0, x1 + 1, 2): s.set(x, y - 1, z0, f'{frame}_stairs[facing=south,half=top,shape=straight]') if s.get(x, y - 1, z0) is None else None
    if 's' in sides:
        for x in range(x0, x1 + 1, 2): s.set(x, y - 1, z1, f'{frame}_stairs[facing=north,half=top,shape=straight]') if s.get(x, y - 1, z1) is None else None
    if 'w' in sides:
        for z in range(z0, z1 + 1, 2): s.set(x0, y - 1, z, f'{frame}_stairs[facing=east,half=top,shape=straight]') if s.get(x0, y - 1, z) is None else None
    if 'e' in sides:
        for z in range(z0, z1 + 1, 2): s.set(x1, y - 1, z, f'{frame}_stairs[facing=west,half=top,shape=straight]') if s.get(x1, y - 1, z) is None else None
