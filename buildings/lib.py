from schem import S, autoconnect
import random

def door(s, x, y, z, wood='spruce', facing='north'):
    s.set(x, y, z, f'{wood}_door[facing={facing},half=lower,hinge=left,open=false,powered=false]')
    s.set(x, y + 1, z, f'{wood}_door[facing={facing},half=upper,hinge=left,open=false,powered=false]')

def gable(s, x0, x1, z0, z1, y, mat='dark_oak', fill='spruce_planks', over=1):
    """ridge along x. 지붕 아래 박공벽은 x0/x1 에 fill"""
    zn, zs, yy = z0 - over, z1 + over, y
    while zn <= zs:
        for x in range(x0 - over, x1 + over + 1):
            if zn == zs: s.set(x, yy, zn, f'{mat}_slab[type=bottom]')
            else:
                s.set(x, yy, zn, f'{mat}_stairs[facing=south,half=bottom,shape=straight]')
                s.set(x, yy, zs, f'{mat}_stairs[facing=north,half=bottom,shape=straight]')
        for z in range(max(zn + 1, z0), min(zs - 1, z1) + 1):
            s.set(x0, yy, z, fill); s.set(x1, yy, z, fill)
        zn += 1; zs -= 1; yy += 1
    return yy

def gable_z(s, x0, x1, z0, z1, y, mat='dark_oak', fill='spruce_planks', over=1):
    """ridge along z"""
    xw, xe, yy = x0 - over, x1 + over, y
    while xw <= xe:
        for z in range(z0 - over, z1 + over + 1):
            if xw == xe: s.set(xw, yy, z, f'{mat}_slab[type=bottom]')
            else:
                s.set(xw, yy, z, f'{mat}_stairs[facing=east,half=bottom,shape=straight]')
                s.set(xe, yy, z, f'{mat}_stairs[facing=west,half=bottom,shape=straight]')
        for x in range(max(xw + 1, x0), min(xe - 1, x1) + 1):
            s.set(x, yy, z0, fill); s.set(x, yy, z1, fill)
        xw += 1; xe -= 1; yy += 1
    return yy

def timber_box(s, x0, z0, w, d, h, frame='dark_oak', infill='calcite', found='stone_bricks', floor='spruce_planks', post_step=4, windows=True, mid=None):
    """하프팀버 벽 상자. 정면 = 남쪽(z 큰 쪽). 반환: 벽 꼭대기 y"""
    x1, z1 = x0 + w - 1, z0 + d - 1
    s.fill(x0, 0, z0, x1, 0, z1, found)
    s.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, floor)
    for y in range(1, h + 1):
        for x in range(x0, x1 + 1):
            for z in (z0, z1): s.set(x, y, z, infill)
        for z in range(z0, z1 + 1):
            for x in (x0, x1): s.set(x, y, z, infill)
    def post(x, z):
        for y in range(1, h + 1): s.set(x, y, z, f'stripped_{frame}_log[axis=y]')
    xs = sorted(set([x0, x1] + list(range(x0, x1, post_step))))
    zs = sorted(set([z0, z1] + list(range(z0, z1, post_step))))
    for x in xs: post(x, z0); post(x, z1)
    for z in zs: post(x0, z); post(x1, z)
    beams = [h] + ([mid] if mid else [])
    for by in beams:
        for x in range(x0, x1 + 1): s.set(x, by, z0, f'{frame}_log[axis=x]'); s.set(x, by, z1, f'{frame}_log[axis=x]')
        for z in range(z0, z1 + 1): s.set(x0, by, z, f'{frame}_log[axis=z]'); s.set(x1, by, z, f'{frame}_log[axis=z]')
    # 바닥 아래 돌 띠
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if not s.get(x, 1, z).startswith('minecraft:stripped'): s.set(x, 1, z, found)
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if not s.get(x, 1, z).startswith('minecraft:stripped'): s.set(x, 1, z, found)
    if windows:
        levels = [2] + ([mid + 2] if mid else [])
        for wy in levels:
            for i in range(len(xs) - 1):
                a, b = xs[i], xs[i + 1]
                if b - a >= 3:
                    cx = (a + b) // 2
                    for z in (z0, z1): s.set(cx, wy, z, 'glass_pane'); s.set(cx, wy + 1, z, 'glass_pane')
            for i in range(len(zs) - 1):
                a, b = zs[i], zs[i + 1]
                if b - a >= 3:
                    cz = (a + b) // 2
                    for x in (x0, x1): s.set(x, wy, cz, 'glass_pane'); s.set(x, wy + 1, cz, 'glass_pane')
    # 안은 비우기
    s.fill(x0 + 1, 1, z0 + 1, x1 - 1, h, z1 - 1, 'air')
    if mid:
        s.fill(x0 + 1, mid, z0 + 1, x1 - 1, mid, z1 - 1, floor)
    return h

def clear_air(s):
    for k in [k for k, v in s.b.items() if v == 'minecraft:air']: del s.b[k]
