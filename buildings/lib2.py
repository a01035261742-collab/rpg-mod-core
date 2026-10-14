"""디테일 건축 도구 v2: 튀어나온 뼈대 · 덧창 · 2톤 지붕(처마 두께) · 재료 섞기 · 돌출 2층 · 지붕창 · 현관 차양"""
import random
from schem import S, autoconnect

FOUND = ['stone_bricks'] * 5 + ['cracked_stone_bricks', 'mossy_stone_bricks', 'cobblestone', 'andesite', 'mossy_cobblestone']
def mix(r, lst): return r.choice(lst)

def door(s, x, y, z, wood='spruce', facing='north', hinge='left'):
    s.set(x, y, z, f'{wood}_door[facing={facing},half=lower,hinge={hinge},open=false,powered=false]')
    s.set(x, y + 1, z, f'{wood}_door[facing={facing},half=upper,hinge={hinge},open=false,powered=false]')

class Wall:
    """한 면: 바깥 평면(뼈대) + 안으로 한 칸 들어간 벽 패널"""

def box(s, r, x0, z0, w, d, y0, h, frame='dark_oak', infill=('calcite',), floor='spruce_planks', post_step=4,
        windows=True, found=True, shutters='spruce', recess=True, win_sides=('n', 's', 'w', 'e')):
    """하프팀버 층 하나. 바깥 테두리 = 뼈대(기둥·들보), 패널은 한 칸 안쪽. 정면 = 남쪽(z1)"""
    x1, z1 = x0 + w - 1, z0 + d - 1
    ins = 1 if recess else 0
    if found:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1): s.set(x, y0 - 1, z, mix(r, FOUND))
        for x in range(x0 + 1 + ins, x1 - ins):
            for z in range(z0 + 1 + ins, z1 - ins): s.set(x, y0 - 1, z, floor)
    xs = sorted(set([x0, x1] + list(range(x0, x1, post_step))))
    zs = sorted(set([z0, z1] + list(range(z0, z1, post_step))))
    top = y0 + h - 1
    P = f'stripped_{frame}_log[axis=y]'
    for y in range(y0, top + 1):
        for x in range(x0 + 1, x1):
            s.set(x, y, z0 + ins, mix(r, infill)); s.set(x, y, z1 - ins, mix(r, infill))
            if recess: s.set(x, y, z0, None); s.set(x, y, z1, None)
        for z in range(z0 + 1, z1):
            s.set(x0 + ins, y, z, mix(r, infill)); s.set(x1 - ins, y, z, mix(r, infill))
            if recess: s.set(x0, y, z, None); s.set(x1, y, z, None)
        for x in xs: s.set(x, y, z0, P); s.set(x, y, z1, P)
        for z in zs: s.set(x0, y, z, P); s.set(x1, y, z, P)
    for x in range(x0, x1 + 1):
        s.set(x, top, z0, f'{frame}_log[axis=x]'); s.set(x, top, z1, f'{frame}_log[axis=x]')
        if found and x not in xs: s.set(x, y0, z0, mix(r, FOUND)); s.set(x, y0, z1, mix(r, FOUND))
    for z in range(z0, z1 + 1):
        s.set(x0, top, z, f'{frame}_log[axis=z]'); s.set(x1, top, z, f'{frame}_log[axis=z]')
        if found and z not in zs: s.set(x0, y0, z, mix(r, FOUND)); s.set(x1, y0, z, mix(r, FOUND))
    s.fill(x0 + 1 + ins, y0, z0 + 1 + ins, x1 - 1 - ins, top, z1 - 1 - ins, 'air')
    if windows:
        wy = y0 + 1 if h >= 4 else y0
        def shut(x, z, face): 
            for yy in (wy, wy + 1):
                if s.get(x, yy, z) is None: s.set(x, yy, z, f'{shutters}_trapdoor[facing={face},half=bottom,open=true]')
        for i in range(len(xs) - 1):
            a, b = xs[i], xs[i + 1]
            if b - a < 4: continue
            cx = (a + b) // 2
            for side, zin, zout, face in (('n', z0 + ins, z0, 'north'), ('s', z1 - ins, z1, 'south')):
                if side not in win_sides: continue
                s.set(cx, wy, zin, 'glass_pane'); s.set(cx, wy + 1, zin, 'glass_pane')
                if recess:
                    s.set(cx, wy - 1, zout, f'{shutters}_trapdoor[facing={face},half=top,open=false]')
                    if b - a - 1 >= 4: shut(cx - 1, zout, face); shut(cx + 1, zout, face)
        for i in range(len(zs) - 1):
            a, b = zs[i], zs[i + 1]
            if b - a < 4: continue
            cz = (a + b) // 2
            for side, xin, xout, face in (('w', x0 + ins, x0, 'west'), ('e', x1 - ins, x1, 'east')):
                if side not in win_sides: continue
                s.set(xin, wy, cz, 'glass_pane'); s.set(xin, wy + 1, cz, 'glass_pane')
                if recess:
                    s.set(xout, wy - 1, cz, f'{shutters}_trapdoor[facing={face},half=top,open=false]')
                    if b - a - 1 >= 4: shut(xout, cz - 1, face); shut(xout, cz + 1, face)
    return x1, z1, xs, zs

def roof(s, r, x0, x1, z0, z1, y, mats=('dark_oak',), eave='spruce', gable_fill=('spruce_planks',), over=1, axis='x', ridge=None):
    """박공 지붕: 재료 섞기, 처마 첫 줄은 eave 재료, 처마 밑 거꾸로 계단(두께), 꼭대기 ridge"""
    yy = y
    if axis == 'x':
        a, b = z0 - over, z1 + over
        first = True
        while a <= b:
            for x in range(x0 - over, x1 + over + 1):
                m = eave if first else (mats[0] if r.random() < 0.8 or len(mats) == 1 else r.choice(mats[1:]))
                if a == b: s.set(x, yy, a, f'{ridge or m}_slab[type=bottom]')
                else:
                    s.set(x, yy, a, f'{m}_stairs[facing=south,half=bottom,shape=straight]')
                    s.set(x, yy, b, f'{m}_stairs[facing=north,half=bottom,shape=straight]')
                    if first:   # 처마 두께
                        s.set(x, yy - 1, a, f'{eave}_stairs[facing=north,half=top,shape=straight]') if s.get(x, yy - 1, a) is None else None
                        s.set(x, yy - 1, b, f'{eave}_stairs[facing=south,half=top,shape=straight]') if s.get(x, yy - 1, b) is None else None
                    if b - a == 1 and ridge:
                        s.set(x, yy + 1, a, f'{ridge}_slab[type=bottom]') if False else None
            for z in range(max(a + 1, z0), min(b - 1, z1) + 1):
                s.set(x0, yy, z, r.choice(gable_fill)); s.set(x1, yy, z, r.choice(gable_fill))
            if not first:
                for x in (x0 - over, x1 + over):   # 박공 끝 장식 들보
                    pass
            a += 1; b -= 1; yy += 1; first = False
        if ridge and a - 1 < b + 1:
            pass
    else:
        a, b = x0 - over, x1 + over
        first = True
        while a <= b:
            for z in range(z0 - over, z1 + over + 1):
                m = eave if first else (mats[0] if r.random() < 0.8 or len(mats) == 1 else r.choice(mats[1:]))
                if a == b: s.set(a, yy, z, f'{ridge or m}_slab[type=bottom]')
                else:
                    s.set(a, yy, z, f'{m}_stairs[facing=east,half=bottom,shape=straight]')
                    s.set(b, yy, z, f'{m}_stairs[facing=west,half=bottom,shape=straight]')
                    if first:
                        if s.get(a, yy - 1, z) is None: s.set(a, yy - 1, z, f'{eave}_stairs[facing=west,half=top,shape=straight]')
                        if s.get(b, yy - 1, z) is None: s.set(b, yy - 1, z, f'{eave}_stairs[facing=east,half=top,shape=straight]')
            for x in range(max(a + 1, x0), min(b - 1, x1) + 1):
                s.set(x, yy, z0, r.choice(gable_fill)); s.set(x, yy, z1, r.choice(gable_fill))
            a += 1; b -= 1; yy += 1; first = False
    return yy

def gable_beam(s, x, ybot, ytop, zc, frame='dark_oak'):
    """박공벽 가운데 세로 들보"""
    for y in range(ybot, ytop): s.set(x, y, zc, f'stripped_{frame}_log[axis=y]') if s.get(x, y, zc) and 'stairs' not in s.get(x, y, zc) and 'slab' not in s.get(x, y, zc) else None

def dormer(s, cx, z_front, y, mat='dark_oak', fill='calcite', frame='dark_oak'):
    """앞(남쪽) 경사에 작은 지붕창: 폭 3"""
    for dy in range(0, 2):
        s.set(cx - 1, y + dy, z_front, f'stripped_{frame}_log[axis=y]'); s.set(cx + 1, y + dy, z_front, f'stripped_{frame}_log[axis=y]')
        s.set(cx, y + dy, z_front, 'glass_pane')
        for z in range(z_front - 3, z_front): s.set(cx - 1, y + dy, z, fill); s.set(cx + 1, y + dy, z, fill); s.set(cx, y + dy, z, 'air')
    for z in range(z_front - 3, z_front + 2):
        s.set(cx - 2, y + 2, z, f'{mat}_stairs[facing=east,half=bottom,shape=straight]')
        s.set(cx + 2, y + 2, z, f'{mat}_stairs[facing=west,half=bottom,shape=straight]')
        s.set(cx - 1, y + 2, z, f'{mat}_planks'); s.set(cx + 1, y + 2, z, f'{mat}_planks')
        s.set(cx, y + 2, z, f'{mat}_planks')
        s.set(cx - 1, y + 3, z, f'{mat}_stairs[facing=east,half=bottom,shape=straight]')
        s.set(cx + 1, y + 3, z, f'{mat}_stairs[facing=west,half=bottom,shape=straight]')
        s.set(cx, y + 3, z, f'{mat}_slab[type=bottom]')
    s.set(cx, y + 2, z_front, f'stripped_{frame}_log[axis=x]')

def porch(s, x0, x1, z, y, h=3, mat='spruce', post='spruce_fence', depth=2):
    """정면 차양: 기둥 두 개 + 작은 경사 지붕"""
    zf = z + depth
    for x in (x0, x1):
        for yy in range(y, y + h): s.set(x, yy, zf, post)
    for x in range(x0 - 1, x1 + 2):
        for dz in range(1, depth + 2):
            s.set(x, y + h, z + dz, f'{mat}_stairs[facing=north,half=bottom,shape=straight]' if dz == depth + 1 else f'{mat}_slab[type=top]')
    for x in range(x0, x1 + 1):
        for dz in range(1, depth + 1): s.set(x, y - 1, z + dz, 'spruce_planks' if mat != 'stone' else 'stone_bricks')

def path(s, r, x0, x1, z0, z1, y=0):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s.set(x, y, z, r.choice(['dirt_path'] * 4 + ['coarse_dirt', 'gravel', 'packed_mud']))

def lamp_post(s, x, y, z, wood='dark_oak'):
    s.set(x, y, z, 'cobblestone_wall'); s.set(x, y + 1, z, f'{wood}_fence'); s.set(x, y + 2, z, f'{wood}_fence')
    s.set(x, y + 3, z, f'stripped_{wood}_log[axis=y]'); s.set(x, y + 2, z + 1, 'lantern[hanging=true]') if False else None
    s.set(x + 1, y + 3, z, f'{wood}_fence'); s.set(x + 1, y + 2, z, 'lantern[hanging=true]')

def bush(s, r, x, y, z, kind='oak'):
    s.set(x, y, z, f'{kind}_leaves[persistent=true,distance=1,waterlogged=false]')
    if r.random() < 0.5: s.set(x, y + 1, z, f'{kind}_leaves[persistent=true,distance=1,waterlogged=false]')
