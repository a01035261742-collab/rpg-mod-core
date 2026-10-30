"""2지역 마을 부품: 독일식 반목조 (길 쪽 박공 · 층마다 튀어나옴 · 가새 무늬 · 돌출 창 · 모서리 탑 · 아케이드)
1번 맵(A: 흰 벽·검은 기둥·갈색 지붕)과 다르게: 크림/흰/분홍 회벽 + 가문비 기둥 + 붉은 기와(맹그로브)·회색 슬레이트 지붕, 가파른 지붕(2:1).
버섯은 살짝 (mlib.overgrow 약하게)."""
from mlib import *
from abld import cone, dormer, lean_to

FR, BM = 'spruce_log', 'stripped_spruce_log'
CREAM = [('smooth_sandstone', 45), ('white_terracotta', 35), ('calcite', 20)]
WHITE = [('calcite', 60), ('white_terracotta', 25), ('smooth_sandstone', 15)]
PINK = [('white_terracotta', 75), ('calcite', 25)]
BASE2 = [('cobblestone', 25), ('stone_bricks', 25), ('mossy_cobblestone', 15), ('cobbled_deepslate', 12), ('andesite', 23)]
ROOF = {'red': ('mangrove', 'mangrove_planks'), 'slate': ('deepslate_tile', 'deepslate_tiles'), 'brown': ('dark_oak', 'dark_oak_planks')}
COL[:0] = [('mangrove', (118, 52, 46)), ('deepslate_tile', (56, 56, 64)), ('cobbled_deepslate', (78, 78, 84)), ('bell', (230, 190, 60)),
           ('diorite', (200, 200, 200)), ('white_concrete', (236, 236, 236))]

def base_story(s, x0, z0, x1, z1, y0=1, h=4, mats=BASE2):
    for y in range(y0, y0 + h):
        for f, cl in sides_of(x0, z0, x1, z1).items():
            for (x, z) in cl: s.set(x, y, z, s.pick(STONE_LOW if y == y0 else mats))
    for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        for y in range(y0, y0 + h): s.set(x, y, z, 'stone_bricks' if (y - y0) % 2 else 'polished_andesite')

def fw_story(s, x0, z0, x1, z1, y0, h=4, infill=CREAM, post=3, pattern='K', wins=(), win_h=2, sides='SNEW'):
    """가새 무늬 반목조 층: 아래 들보, 기둥(post 칸마다), 칸 사이 가새 (K = 한쪽 대각 번갈아, X = 엇갈림, '' = 없음)"""
    win_cells = {c for (f, c) in wins}
    for f, cl in sides_of(x0, z0, x1, z1).items():
        if f not in sides: continue
        ax = 'x' if f in 'SN' else 'z'
        posts = [i for i in range(len(cl)) if i % post == 0 or i == len(cl) - 1]
        for i, (x, z) in enumerate(cl):
            s.set(x, y0, z, LOG(BM, ax))
            for y in range(y0 + 1, y0 + h): s.set(x, y, z, LOG(FR) if i in posts else s.pick(infill))
        for bi, (a, b) in enumerate(zip(posts, posts[1:])):
            n = b - a - 1
            if n < 3 or any(cl[k] in win_cells for k in range(a + 1, b)): continue
            rows = h - 1
            for j in range(rows):
                t = j / max(1, rows - 1)
                k1 = a + 1 + int(round(t * (n - 1)))
                k2 = a + 1 + int(round((1 - t) * (n - 1)))
                if pattern == 'X' or (pattern == 'K' and bi % 2 == 0): x, z = cl[k1]; s.set(x, y0 + 1 + j, z, LOG(FR))
                if pattern == 'X' or (pattern == 'K' and bi % 2 == 1): x, z = cl[k2]; s.set(x, y0 + 1 + j, z, LOG(FR))
    for (f, (x, z)) in wins: window(s, x, y0 + 1, z, f, h=min(win_h, h - 2), shutters=False, wood='spruce', lintel='spruce')

def steep_gable(s, x0, z0, x1, z1, y, axis='z', roof='red', over=1, rise=2, infill=CREAM, gwin=True):
    """가파른 박공 (한 줄에 2칸씩 오름): 계단 + 아래 꽉 찬 블록, 처마 내밀기, 박공벽 반목조, 꼭대기 반블록.
    axis = 용마루 방향 ('z' 면 박공이 남·북 = 길 쪽)"""
    st, full = ROOF[roof]
    if axis == 'x': L0, L1, a0, a1 = x0 - over, x1 + over, z0 - over, z1 + over
    else: L0, L1, a0, a1 = z0 - over, z1 + over, x0 - over, x1 + over
    n = (a1 - a0 + 1) // 2; odd = (a1 - a0 + 1) % 2 == 1
    top = y
    for k in range(n):
        for side in (0, 1):
            aa = a0 + k if side == 0 else a1 - k
            f = {('x', 0): 'south', ('x', 1): 'north', ('z', 0): 'east', ('z', 1): 'west'}[(axis, side)]
            for L in range(L0, L1 + 1):
                for r in range(rise):
                    yy = y + k * rise + r; top = max(top, yy)
                    pos = (L, yy, aa) if axis == 'x' else (aa, yy, L)
                    m = ST(st, f) if r == rise - 1 else full
                    if k == 0 and r == 0 and L not in (L0, L1): m = ST(st, f)   # 처마 끝은 계단
                    s.set(*pos, m)
                if k == 0:   # 처마 밑 받침
                    pb = (L, y - 1, aa) if axis == 'x' else (aa, y - 1, L)
                    if s.get(*pb) is None: s.set(*pb, ST('spruce', f, 'top'))
    if odd:
        aa = a0 + n
        for L in range(L0, L1 + 1):
            for r in range(rise):
                pos = (L, y + n * rise + r - rise, aa) if axis == 'x' else (aa, y + n * rise + r - rise, L)
                s.set(*pos, full)
            pos = (L, y + n * rise, aa) if axis == 'x' else (aa, y + n * rise, L)
            s.set(*pos, SL(st)); top = max(top, y + n * rise)
    # 박공벽: 가운데 기둥 + 가장자리 따라 원목 + 회벽 + 작은 창
    mid = (a0 + a1) / 2
    for Lw in ((x0, x1) if axis == 'x' else (z0, z1)):
        for aa in range(a0 + 1, a1):
            krow = min(aa - a0, a1 - aa)
            hmax = y + krow * rise - (0 if aa == a0 + n and odd else 1)
            for yy in range(y, hmax + (1 if odd and aa == a0 + n else 0)):
                pos = (Lw, yy, aa) if axis == 'x' else (aa, yy, Lw)
                if s.get(*pos) and ('stairs' in s.get(*pos) or 'planks' in s.get(*pos) or 'tiles' in s.get(*pos)): continue
                edge_ = yy >= hmax - 1
                s.set(*pos, LOG(FR) if abs(aa - mid) < 0.6 or edge_ or yy == y else s.pick(infill))
        if gwin:
            ww = y + 2
            for dd in (-1, 1):
                aa = int(round(mid + dd * 1.5)) if (a1 - a0) >= 8 else None
                if aa is None: continue
                pos = (Lw, ww, aa) if axis == 'x' else (aa, ww, Lw)
                s.set(*pos, 'glass_pane')
            if (a1 - a0) < 8:
                pos = (Lw, ww, int(mid)) if axis == 'x' else (int(mid), ww, Lw)
                s.set(*pos, 'glass_pane'); pos2 = (Lw, ww + 1, int(mid)) if axis == 'x' else (int(mid), ww + 1, Lw); s.set(*pos2, 'glass_pane')
    return top

def hip_roof(s, x0, z0, x1, z1, y, roof='slate', over=1):
    """우진각 지붕 (네 면 모두 경사): 테두리 고리마다 1칸씩 오름, 모서리는 반블록"""
    st, full = ROOF[roof]
    X0, Z0, X1, Z1 = x0 - over, z0 - over, x1 + over, z1 + over
    k = 0; top = y
    while X0 + k <= X1 - k and Z0 + k <= Z1 - k:
        a, b, c, d = X0 + k, Z0 + k, X1 - k, Z1 - k
        yy = y + k; top = yy
        if a == c or b == d:
            for x in range(a, c + 1):
                for z in range(b, d + 1): s.set(x, yy, z, SL(st))
            break
        for x in range(a, c + 1):
            for z in range(b, d + 1):
                on = x in (a, c) or z in (b, d)
                if not on:
                    if k == 0 or s.get(x, yy, z) is None: s.set(x, yy, z, full) if k > 0 and False else None
                    continue
                corner = x in (a, c) and z in (b, d)
                if corner: s.set(x, yy, z, SL(st))
                else:
                    f = 'south' if z == b else 'north' if z == d else 'east' if x == a else 'west'
                    s.set(x, yy, z, ST(st, f))
                if k > 0 and s.get(x, yy - 1, z) is None: s.set(x, yy - 1, z, full)
                if k == 0 and not corner:
                    f = 'south' if z == b else 'north' if z == d else 'east' if x == a else 'west'
                    if s.get(x, yy - 1, z) is None: s.set(x, yy - 1, z, ST('spruce', f, 'top'))
        k += 1
    return top

def oriel(s, x, y, z, f='S', w=3, h=3, roof='red'):
    """돌출 창 (위층 벽에서 1칸 튀어나온 유리 상자 + 받침 + 작은 지붕)"""
    dx, dz = OUT[f]; tx, tz = (1, 0) if f in 'SN' else (0, 1)
    st, full = ROOF[roof]
    half = w // 2
    for i in range(-half, half + 1):
        px, pz = x + tx * i + dx, z + tz * i + dz
        s.set(px, y - 1, pz, ST('spruce', FACE[OPP[f]], 'top'))
        for k in range(h): s.set(px, y + k, pz, 'glass_pane' if abs(i) < half else LOG(FR))
        s.set(px, y + h, pz, ST(st, FACE[OPP[f]]))
        s.set(x + tx * i, y + h, z + tz * i, full) if s.get(x + tx * i, y + h, z + tz * i) is None else None
        for k in range(h): s.set(x + tx * i, y + k, z + tz * i, 'air')
    s.set(x + dx * 2, y - 2, z + dz * 2, None) if False else None

def turret(s, cx, cz, R, y0, y1, roof='slate', mats=None, timber=True):
    """원통 탑 (돌 또는 반목조) + 원뿔 지붕"""
    ins, edge = ring_cells(cx, cz, R)
    edge = sorted(edge, key=lambda p: math.atan2(p[1] - cz, p[0] - cx))
    for y in range(y0, y1):
        for i, (x, z) in enumerate(edge):
            if timber and y > y0 + 3: m = LOG(FR) if i % 3 == 0 else s.pick(CREAM)
            else: m = s.pick(mats or BASE2)
            s.set(x, y, z, m)
    for (x, z) in ins:
        if (x, z) not in edge: s.set(x, y0 - 1 if y0 > 1 else 0, z, 'spruce_planks')
    st, full = ROOF[roof]
    for (x, z) in edge:
        f = card(cx, cz, x, z); dx, dz = OUT[f]
        if s.get(x + dx, y1 - 1, z + dz) is None: s.set(x + dx, y1 - 1, z + dz, ST('spruce', FACE[OPP[f]], 'top'))
    for i, (x, z) in enumerate(edge):
        if i % 4 == 2: s.set(x, y1 - 3, z, 'glass_pane'); s.set(x, y1 - 2, z, 'glass_pane')
    return cone(s, int(cx), int(cz), int(R) + 1, y1, mats=(st, st, st), full=full, step=0.4)

def arcade(s, x0, x1, z, f='S', h=3):
    """아래층 앞 아케이드: 돌 아치 (기둥 + 위 뒤집힌 계단)"""
    for x in range(x0, x1 + 1):
        if (x - x0) % 3 == 0:
            for y in range(1, h + 1): s.set(x, y, z, 'stone_bricks' if y < h else 'chiseled_stone_bricks')
        else:
            for y in range(1, h): s.set(x, y, z, 'air')
            lr = 'east' if (x - x0) % 3 == 1 else 'west'
            s.set(x, h, z, ST('stone_brick', lr, 'top'))

def front_door(s, x, z, f='S', wood='spruce', y=1):
    """반목조 문: 문 + 위 가로대 + 양옆 작은 등 + 디딤 반블록"""
    dx, dz = OUT[f]; ox, oz = (1, 0) if f in 'SN' else (0, 1)
    s.set(x, y, z, f'{wood}_door[facing={FACE[f]},half=lower,hinge=left,open=false,powered=false]')
    s.set(x, y + 1, z, f'{wood}_door[facing={FACE[f]},half=upper,hinge=left,open=false,powered=false]')
    s.set(x, y + 2, z, LOG(BM, 'x' if f in 'SN' else 'z'))
    s.set(x + dx, y + 2, z + dz, ST('spruce', FACE[OPP[f]], 'top'))
    for sg in (-1, 1):
        p = (x + dx + ox * sg, y + 1, z + dz + oz * sg)
        if s.get(*p) is None: s.set(*p, 'lantern[hanging=false,waterlogged=false]') if sg == 1 else None
    if s.get(x + dx, 0, z + dz) is None: s.set(x + dx, 0, z + dz, SL('stone_brick'))

def shop_sign(s, x, y, z, f='S'):
    hanging_sign(s, x, y, z, f, wood='spruce')
