"""A 스타일 (목조 반목조) 건물 부품 — 블록 상태 포함, 앞 = 남쪽(+z)
참고: 처마 내밀기·창 깊이(안쪽 유리판 + 위 계단 + 덧문 다락문)·튀어나온 기둥·지붕 색 그라데이션·돌 풍화(아래 이끼)·굴뚝 위 모닥불"""
import sys, random
sys.path.insert(0, '/home/claude/rpg-mod-core/buildings')
from schem import S, autoconnect, render2, view2, COL
COL[:0] = [('white_terracotta', (214, 196, 178)), ('calcite', (226, 226, 220)), ('stripped_dark_oak', (96, 74, 48)), ('dark_oak', (66, 45, 26)),
           ('spruce', (112, 82, 50)), ('cracked_stone', (118, 118, 118)), ('smooth_stone', (160, 160, 160)), ('hay', (200, 165, 50)),
           ('target', (225, 205, 190)), ('polished_deepslate', (72, 72, 76)), ('deepslate_brick', (70, 70, 75)), ('iron_bars', (150, 150, 155)),
           ('anvil', (68, 68, 72)), ('blast_furnace', (100, 100, 104)), ('red_banner', (165, 45, 40)), ('green_banner', (80, 130, 50)),
           ('blue_banner', (55, 75, 165)), ('purple_banner', (115, 60, 150)), ('black_banner', (35, 35, 40)), ('yellow_banner', (220, 185, 60)),
           ('amethyst', (155, 110, 200)), ('copper', (190, 110, 80)), ('bell', (230, 190, 60)), ('coal_block', (25, 25, 28)), ('soul_lantern', (110, 220, 230)), ('brown_carpet', (115, 75, 45)), ('enchanting', (60, 30, 80)), ('pressure', (150, 115, 70)), ('white_wool', (232, 232, 232)), ('yellow_wool', (225, 190, 60)), ('blue_wool', (55, 75, 165)), ('red_wool', (165, 45, 40)), ('wall_sign', (160, 120, 70)), ('gold_block', (240, 200, 70)), ('composter', (110, 80, 50)), ('furnace', (110, 110, 110)), ('coarse_dirt', (110, 80, 55))]

def ST(m, f, half='bottom'): return f'{m}_stairs[facing={f},half={half},shape=straight,waterlogged=false]'
def SL(m, t='bottom'): return f'{m}_slab[type={t},waterlogged=false]'
def LOG(m, ax='y'): return f'{m}[axis={ax}]'
def TD(m, f, open_=True, half='bottom'): return f'{m}_trapdoor[facing={f},half={half},open={"true" if open_ else "false"},powered=false,waterlogged=false]'
OUT = {'S': (0, 1), 'N': (0, -1), 'E': (1, 0), 'W': (-1, 0)}
FACE = {'S': 'south', 'N': 'north', 'E': 'east', 'W': 'west'}
OPP = {'S': 'N', 'N': 'S', 'E': 'W', 'W': 'E'}

class Bld(S):
    def __init__(s, name, seed=1):
        super().__init__(name); s.r = random.Random(seed)
    def pick(s, lst):
        tot = sum(w for _, w in lst); v = s.r.random() * tot
        for m, w in lst:
            v -= w
            if v <= 0: return m
        return lst[-1][0]

STONE = [('cobblestone', 30), ('stone_bricks', 40), ('andesite', 14), ('cracked_stone_bricks', 8), ('mossy_stone_bricks', 8)]
STONE_LOW = [('mossy_cobblestone', 35), ('cobblestone', 35), ('mossy_stone_bricks', 30)]
INFILL = [('white_terracotta', 55), ('calcite', 45)]

def sides_of(x0, z0, x1, z1):
    """(면, 그 면의 칸들) — 면 바깥 방향"""
    return {'S': [(x, z1) for x in range(x0, x1 + 1)], 'N': [(x, z0) for x in range(x0, x1 + 1)],
            'E': [(x1, z) for z in range(z0, z1 + 1)], 'W': [(x0, z) for z in range(z0, z1 + 1)]}

def foundation(s, x0, z0, x1, z1, floor='spruce_planks', skirt=True):
    s.fill(x0, 0, z0, x1, 0, z1, floor)
    for x in range(x0, x1 + 1):
        for z in (z0, z1): s.set(x, 0, z, 'stone_bricks')
    for z in range(z0, z1 + 1):
        for x in (x0, x1): s.set(x, 0, z, 'stone_bricks')
    if skirt:   # 바깥 한 줄 계단 띠 (기단)
        for f, cells in sides_of(x0, z0, x1, z1).items():
            dx, dz = OUT[f]
            for (x, z) in cells:
                if s.get(x + dx, 0, z + dz) is None: s.set(x + dx, 0, z + dz, ST('stone_brick', FACE[OPP[f]]))

def stone_story(s, x0, z0, x1, z1, y0, h, quoin=True):
    for y in range(y0, y0 + h):
        for f, cells in sides_of(x0, z0, x1, z1).items():
            for (x, z) in cells:
                s.set(x, y, z, s.pick(STONE_LOW if y == y0 else STONE))
    if quoin:   # 모서리 돌 (번갈아 매끈한 안산암)
        for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
            for y in range(y0, y0 + h): s.set(x, y, z, 'polished_andesite' if (y - y0) % 2 == 0 else 'stone_bricks')

def window(s, x, y, z, f, h=2, wood='spruce', shutters=True, lintel='spruce', sill=True):
    """창: 벽면에 유리판 + 위 계단 처마 + 아래 받침 + 양옆 덧문"""
    dx, dz = OUT[f]
    for k in range(h):
        s.set(x, y + k, z, 'glass_pane')            # 유리판은 벽면에 (안으로 들이지 않음)
    s.set(x + dx, y + h, z + dz, ST(lintel, FACE[OPP[f]], 'top'))            # 창 위 작은 처마
    if sill: s.set(x + dx, y - 1, z + dz, ST(wood, FACE[OPP[f]], 'top'))
    if shutters:
        ox, oz = (1, 0) if f in 'SN' else (0, 1)
        for sgn in (-1, 1):
            for k in range(h): s.set(x + dx + ox * sgn, y + k, z + dz + oz * sgn, TD(wood, FACE[f]))

def timber_story(s, x0, z0, x1, z1, y0, h=4, post=4, wins=(), brace=True, frame='dark_oak_log', infill=INFILL, beam='stripped_dark_oak_log'):
    """반목조 층: y0 = 아래 들보, y0+1..y0+h-1 = 회벽 + 기둥 (post 칸마다), 창은 wins 의 (면, 칸 위치)"""
    for f, cells in sides_of(x0, z0, x1, z1).items():
        ax = 'x' if f in 'SN' else 'z'
        for i, (x, z) in enumerate(cells):
            s.set(x, y0, z, LOG(beam, ax))
            for y in range(y0 + 1, y0 + h): s.set(x, y, z, s.pick(infill))
            if i % post == 0 or i == len(cells) - 1:
                for y in range(y0 + 1, y0 + h): s.set(x, y, z, LOG(frame, 'y'))
    for f, cells in sides_of(x0, z0, x1, z1).items():   # 가새 (창 없는 칸: 대각 계단)
        if not brace: continue
        for i in range(0, len(cells) - post, post):
            seg = cells[i + 1:i + post]
            if any((f, c) in wins for c in seg) or len(seg) < 2: continue
            if s.r.random() < 0.4:
                a, b = seg[0], seg[-1]
                ff = FACE[f]
                lr = {'S': ('west', 'east'), 'N': ('east', 'west'), 'E': ('south', 'north'), 'W': ('north', 'south')}[f]
                s.set(*(a[0], y0 + 1, a[1]), ST('dark_oak', lr[1] if False else lr[0]))
                s.set(*(b[0], y0 + 1, b[1]), ST('dark_oak', lr[1]))
    for (f, (x, z)) in wins: window(s, x, y0 + 1, z, f, h=min(2, h - 2), shutters=False)

def jetty(s, x0, z0, x1, z1, y, sides='SNEW'):
    """위층이 1칸 튀어나옴: 아래에 뒤집힌 계단 받침"""
    for f in sides:
        dx, dz = OUT[f]
        cells = sides_of(x0, z0, x1, z1)[f]
        for (x, z) in cells:
            s.set(x + dx, y, z + dz, ST('dark_oak', FACE[OPP[f]], 'top'))

def gable_roof(s, x0, z0, x1, z1, y, axis='x', over=1, mats=('dark_oak', 'spruce', 'spruce', 'spruce'), full='spruce_planks', gable=INFILL, gable_frame=True, ridge='dark_oak'):
    """박공 지붕 (45도 계단) + 처마 내밀기 + 색 그라데이션 + 박공벽 반목조 + 끝선 띠"""
    if axis == 'x':
        L0, L1 = x0 - over, x1 + over; a0, a1 = z0 - over, z1 + over
    else:
        L0, L1 = z0 - over, z1 + over; a0, a1 = x0 - over, x1 + over
    n = (a1 - a0) // 2; odd = (a1 - a0) % 2 == 0
    top = y
    for k in range(n + (1 if not odd else 0)):
        yy = y + k; top = yy
        m = mats[min(len(mats) - 1, k * len(mats) // max(1, n))]
        for L in range(L0, L1 + 1):
            edge = L in (L0, L1)
            mm = 'dark_oak' if edge else (m if s.r.random() > 0.06 else ('dark_oak' if m == 'spruce' else 'spruce'))
            for side, aa in ((0, a0 + k), (1, a1 - k)):
                if aa > a1 - k and side == 0: continue
                f = ('north' if side == 1 else 'south') if axis == 'x' else ('west' if side == 1 else 'east')
                # 남쪽 면 (a1 쪽) 계단은 북쪽을 봄 (높은 쪽이 안쪽)
                f = {('x', 0): 'south', ('x', 1): 'north', ('z', 0): 'east', ('z', 1): 'west'}[(axis, side)]
                f = {'south': 'south', 'north': 'north', 'east': 'east', 'west': 'west'}[f]
                pos = (L, yy, aa) if axis == 'x' else (aa, yy, L)
                s.set(*pos, ST(mm, f))
                if k == 0 and not edge:   # 처마 밑 받침 (두꺼운 처마)
                    pb = (L, yy - 1, aa) if axis == 'x' else (aa, yy - 1, L)
                    if s.get(*pb) is None: s.set(*pb, ST('dark_oak', f, 'top'))
                if k > 0:   # 지붕 두께
                    pos2 = (L, yy - 1, aa) if axis == 'x' else (aa, yy - 1, L)
                    if s.get(*pos2) is None or 'stairs' in (s.get(*pos2) or ''): s.set(*pos2, full)
        # 박공벽
        for aa in range(a0 + k + 1, a1 - k):
            for Lw in ((x0, x1) if axis == 'x' else (z0, z1)):
                pos = (Lw, yy, aa) if axis == 'x' else (aa, yy, Lw)
                mid = (a0 + a1) / 2
                if gable_frame and (abs(aa - mid) < 0.6 or k == 0): s.set(*pos, LOG('dark_oak_log', 'y') if abs(aa - mid) < 0.6 else LOG('stripped_dark_oak_log', 'z' if axis == 'x' else 'x'))
                else: s.set(*pos, s.pick(gable))
    if odd:   # 용마루 반블록
        yy = y + n
        for L in range(L0, L1 + 1):
            pos = (L, yy, a0 + n) if axis == 'x' else (a0 + n, yy, L)
            s.set(*pos, SL(ridge)); top = yy
            posb = (L, yy - 1, a0 + n) if axis == 'x' else (a0 + n, yy - 1, L)
            if s.get(*posb) is None: s.set(*posb, full)
    return top

def gable_window(s, axis, x0, z0, x1, z1, y, f=None):
    """박공벽 가운데 작은 창 (양쪽)"""
    if axis == 'x':
        zc = (z0 + z1) // 2
        for x, ff in ((x0, 'W'), (x1, 'E')): s.set(x, y, zc, 'glass_pane'); s.set(x + OUT[ff][0], y + 1, zc, ST('dark_oak', FACE[OPP[ff]], 'top'))
    else:
        xc = (x0 + x1) // 2
        for z, ff in ((z0, 'N'), (z1, 'S')): s.set(xc, y, z, 'glass_pane'); s.set(xc, y + 1, z + OUT[ff][1], ST('dark_oak', FACE[OPP[ff]], 'top'))

def door(s, x, z, f='S', wood='spruce', y=1, arch=True, lamps=True, step=True):
    dx, dz = OUT[f]
    s.set(x, y, z, f'{wood}_door[facing={FACE[f]},half=lower,hinge=left,open=false,powered=false]')
    s.set(x, y + 1, z, f'{wood}_door[facing={FACE[f]},half=upper,hinge=left,open=false,powered=false]')
    ox, oz = (1, 0) if f in 'SN' else (0, 1)
    if arch:
        s.set(x, y + 2, z, 'stone_bricks')
        s.set(x + dx - ox, y + 2, z + dz - oz, ST('stone_brick', FACE['E' if f in 'SN' else 'S'], 'top') if f in 'SN' else ST('stone_brick', 'south', 'top'))
        s.set(x + dx + ox, y + 2, z + dz + oz, ST('stone_brick', FACE['W' if f in 'SN' else 'N'], 'top') if f in 'SN' else ST('stone_brick', 'north', 'top'))
        s.set(x + dx, y + 2, z + dz, SL('stone_brick', 'top'))
    if lamps:
        for sgn in (-1, 1):
            s.set(x + dx + ox * sgn * 2, y + 2, z + dz + oz * sgn * 2, 'lantern[hanging=false,waterlogged=false]')
    if step: s.set(x + dx, 0, z + dz, SL('stone_brick'))

def chimney(s, x, z, y0, y1, w=1, cap=True):
    for y in range(y0, y1 + 1):
        for i in range(w): s.set(x + i, y, z, 'bricks' if y > y0 + 2 and s.r.random() > 0.15 else 'stone_bricks')
    if cap:
        for i in range(w):
            for (dx, dz, f) in ((0, 1, 'north'), (0, -1, 'south')):
                s.set(x + i, y1, z + dz, ST('stone_brick', f, 'top'))
        s.set(x - 1, y1, z, ST('stone_brick', 'east', 'top')); s.set(x + w, y1, z, ST('stone_brick', 'west', 'top'))
        for i in range(w): s.set(x + i, y1 + 1, z, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')

def hanging_sign(s, x, y, z, f='S', wood='dark_oak'):
    """벽에서 튀어나온 막대 + 매단 간판"""
    dx, dz = OUT[f]
    s.set(x + dx, y, z + dz, f'{wood}_fence'); s.set(x + 2 * dx, y, z + 2 * dz, f'{wood}_fence')
    rot = 0 if f in 'SN' else 4
    s.set(x + 2 * dx, y - 1, z + 2 * dz, f'{wood}_hanging_sign[attached=false,rotation={rot},waterlogged=false]')

def banner(s, x, y, z, color, f='S'):
    dx, dz = OUT[f]
    s.set(x + dx, y, z + dz, f'{color}_wall_banner[facing={FACE[f]}]')

def lamp_post(s, x, z, h=3):
    s.set(x, 0, z, 'stone_bricks')
    for y in range(1, h + 1): s.set(x, y, z, 'dark_oak_fence')
    s.set(x, h + 1, z, 'dark_oak_fence'); s.set(x + 1, h + 1, z, 'dark_oak_fence'); s.set(x + 1, h, z, 'lantern[hanging=true,waterlogged=false]')

def bench(s, x, z, f='S', n=2):
    return   # 소품 없음 (사용자가 직접 꾸밈)
    for i in range(n): s.set(x + i, 1, z, ST('spruce', FACE[OPP[f]]))

def barrels(s, pts):
    return   # 소품 없음 (사용자가 직접 꾸밈)
    for (x, y, z) in pts: s.set(x, y, z, 'barrel[facing=up,open=false]')

def crates(s, x, z):
    return   # 소품 없음 (사용자가 직접 꾸밈)
    s.set(x, 1, z, 'barrel[facing=up,open=false]'); s.set(x + 1, 1, z, 'spruce_planks'); s.set(x, 2, z, SL('spruce'))

def woodpile(s, x0, z, n=3, ax='x'):
    return   # 소품 없음 (사용자가 직접 꾸밈)
    for i in range(n):
        s.set(x0 + i, 1, z, LOG('spruce_log', 'z')); 
        if i < n - 1: s.set(x0 + i, 2, z, LOG('spruce_log', 'z'))

def path_patch(s, x0, x1, z0, z1):
    return   # 소품 없음 (사용자가 직접 꾸밈)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if s.get(x, 0, z) is None: s.set(x, 0, z, s.pick([('dirt_path', 60), ('coarse_dirt', 25), ('gravel', 15)]))

def finish(s):
    for k in [k for k, v in s.b.items() if v == 'minecraft:air']:
        pass
    autoconnect(s)
    return s


def corner_lamps(s, x0, z0, x1, z1, y):
    """위층 모서리 받침 랜턴"""
    for (x, z, dx, dz) in ((x0, z1, -1, 1), (x1, z1, 1, 1)):
        s.set(x + dx, y, z, 'dark_oak_fence') if s.get(x + dx, y, z) is None else None
        s.set(x + dx, y - 1, z, 'lantern[hanging=true,waterlogged=false]') if s.get(x + dx, y - 1, z) is None else None

def weathervane(s, x, y, z):
    s.set(x, y, z, 'dark_oak_fence'); s.set(x, y + 1, z, 'lightning_rod[facing=up,powered=false,waterlogged=false]')

def furnish(s, x0, z0, x1, z1, y=1, kind='home'):
    """안 꾸미기 (창으로 보임): 가장자리 가구 + 가운데 탁자·의자 + 매단 등"""
    return   # 소품 없음 (사용자가 직접 꾸밈)
    def free(x, yy, z): return s.get(x, yy, z) is None
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    # 바닥 깔개
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if free(x, y, z): s.set(x, y, z, 'red_carpet' if kind != 'shop' else 'brown_carpet')
    if free(cx, y, cz): s.set(cx, y, cz, 'spruce_fence'); s.set(cx, y + 1, cz, 'spruce_pressure_plate[powered=false]')
    for (dx, f) in ((-1, 'east'), (1, 'west')):
        if s.get(cx + dx, y, cz) in (None, 'minecraft:red_carpet', 'minecraft:brown_carpet'): s.set(cx + dx, y, cz, ST('spruce', f))
    wall_items = {'home': ['bookshelf', 'barrel[facing=up,open=false]', 'chest[facing=south,type=single,waterlogged=false]', 'crafting_table', 'furnace[facing=south,lit=false]'],
                  'shop': ['barrel[facing=up,open=false]', 'chest[facing=south,type=single,waterlogged=false]', 'barrel[facing=up,open=false]', 'bookshelf'],
                  'inn': ['barrel[facing=up,open=false]', 'barrel[facing=up,open=false]', 'brewing_stand', 'chest[facing=south,type=single,waterlogged=false]'],
                  'magic': ['bookshelf', 'bookshelf', 'enchanting_table', 'brewing_stand'], 'smith': ['chest[facing=south,type=single,waterlogged=false]', 'barrel[facing=up,open=false]']}[kind]
    i = 0
    for x in range(x0 + 1, x1, 2):
        if free(x, y, z0 + 1): s.set(x, y, z0 + 1, wall_items[i % len(wall_items)]); i += 1
    s.set(cx, y + 3, cz, 'chain[axis=y]') if free(cx, y + 3, cz) else None
    s.set(cx, y + 2, cz, 'lantern[hanging=true,waterlogged=false]') if free(cx, y + 2, cz) else None

def scatter(s, x0, z0, x1, z1, n=6):
    """건물 둘레 바닥: 자갈·거친 흙 조각 + 디딤돌"""
    return   # 소품 없음 (사용자가 직접 꾸밈)
    for _ in range(n):
        f = s.r.choice('SNEW'); x = s.r.randint(x0, x1); z = s.r.randint(z0, z1)
        dx, dz = OUT[f]
        x, z = {'S': (x, z1 + 2), 'N': (x, z0 - 2), 'E': (x1 + 2, z), 'W': (x0 - 2, z)}[f]
        for (ax, az) in ((0, 0), (1, 0), (0, 1)):
            if s.get(x + ax, 0, z + az) is None: s.set(x + ax, 0, z + az, s.pick([('coarse_dirt', 50), ('gravel', 30), ('cobblestone', 20)]))


PROPS = ('chest', 'barrel', 'crafting_table', 'furnace', 'blast_furnace', 'smoker', 'bookshelf', 'lectern', 'brewing_stand', 'cauldron', 'water_cauldron',
         'lava_cauldron', 'anvil', 'grindstone', 'smithing_table', 'cartography_table', 'fletching_table', 'composter', 'enchanting_table', 'cake',
         'hay_block', 'target', 'amethyst_block', 'amethyst_cluster', 'coal_block', 'carved_pumpkin', 'loom', 'stonecutter', 'iron_block')
def declutter(s):
    """안팎에 흩어진 소품(상자·작업대·통·책장 등) 치움. 남김: 벽·지붕·창·문·등·간판·깃발·사다리·굴뚝, 우물 두레박(사슬 밑 가마솥)"""
    gone = set()
    for (x, y, z), st in list(s.b.items()):
        n = st.split(':')[1].split('[')[0]
        rm = n in PROPS or n.endswith('_carpet') or n.endswith('pressure_plate')
        if n == 'cauldron' and (s.get(x, y + 1, z) or '').startswith('minecraft:chain'): rm = False
        if n in ('campfire', 'soul_campfire') and y <= 2: rm = True          # 땅 위 모닥불 (굴뚝 위 것은 남김)
        if n == 'chain' and s.get(x, y + 1, z) is None and s.get(x, y - 1, z) is None: rm = True   # 등 없이 매달린 사슬
        if rm: del s.b[(x, y, z)]; gone.add((x, y, z))
    # 받침이 없어져 공중에 뜬 울타리·벽 덩어리 (허수아비 팔 등) 치움
    def fence_like(st): return st is not None and any(k in st for k in ('_fence', '_wall[', 'iron_bars'))
    seen = set()
    for p0, st in list(s.b.items()):
        if p0 in seen or not fence_like(st): continue
        comp, q = [], [p0]; seen.add(p0)
        while q:
            p = q.pop(); comp.append(p)
            for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)):
                n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
                if n not in seen and fence_like(s.b.get(n)): seen.add(n); q.append(n)
        cs = set(comp)
        sup = any(p[1] <= 1 or (s.b.get((p[0], p[1] - 1, p[2])) not in (None, 'minecraft:air') and (p[0], p[1] - 1, p[2]) not in cs) or
                  any((p[0] + a, p[1], p[2] + b) not in cs and s.b.get((p[0] + a, p[1], p[2] + b)) not in (None, 'minecraft:air') for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                  for p in comp)
        if not sup:
            for p in comp: del s.b[p]; gone.add(p)
    for _ in range(2):   # 치운 것 위에 얹혀 있던 반블록·판자·서 있는 등, 탁자 다리(울타리)
        for (x, y, z), st in list(s.b.items()):
            n = st.split(':')[1].split('[')[0]
            if (x, y - 1, z) in gone and (n.endswith('_slab') or n.endswith('_planks') or (n == 'lantern' and 'hanging=false' in st)):
                del s.b[(x, y, z)]; gone.add((x, y, z))
            if (x, y + 1, z) in gone and n.endswith('_fence') and y <= 1 and not any((x + a, y, z + b) in s.b and 'fence' in s.b[(x + a, y, z + b)] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                del s.b[(x, y, z)]; gone.add((x, y, z))
    return s
