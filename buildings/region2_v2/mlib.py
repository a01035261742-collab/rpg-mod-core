"""버섯 마을 부품 (A 스타일 위에 버섯 테마) — 앞 = 남쪽(+z)
참고: 메이플 헤네시스(버섯 모양 집), 마크 버섯 집 빌드(버섯 기둥 벽 + 빨간/갈색 갓 지붕 + 흰 점),
Gribun 버섯 마을 제안(가운데 거대 버섯·버섯 우물·버섯 밭). 건물엔 식물 없음 (작은 버섯·꽃은 식생 단계에서)."""
import math
from alib import *
from abld import cone, lean_to, dormer
COL[:0] = [('red_mushroom_block', (196, 48, 44)), ('brown_mushroom_block', (150, 108, 76)), ('mushroom_stem', (214, 206, 190)),
           ('shroomlight', (244, 160, 76)), ('white_concrete', (236, 236, 236)), ('orange_concrete', (226, 110, 30)),
           ('orange_terracotta', (161, 83, 37)), ('smooth_sandstone', (222, 210, 160)), ('amethyst_cluster', (190, 140, 230)),
           ('lodestone', (150, 150, 155)), ('crying_obsidian', (60, 20, 90)), ('bone_block', (226, 222, 200)), ('cauldron', (60, 60, 64)),
           ('mud_brick', (138, 104, 78)), ('stripped_birch', (200, 184, 140)), ('birch', (200, 184, 140)), ('white_wool', (232, 232, 232)),
           ('light_gray_wool', (150, 150, 150)), ('red_wool', (165, 45, 40)), ('brown_wool', (115, 75, 45)), ('hay', (200, 165, 50)),
           ('soul_campfire', (90, 200, 210)), ('grindstone', (130, 130, 130)), ('smithing', (60, 50, 50)), ('fletching', (180, 160, 110)),
           ('dead_bush', (130, 100, 60)), ('moss_block', (90, 120, 50)), ('moss_carpet', (90, 120, 50)), ('mangrove', (110, 50, 45)),
           ('cobweb', (230, 230, 235)), ('purple_wool', (115, 60, 150)), ('magenta', (170, 70, 160)), ('purple_terracotta', (118, 70, 86)),
           ('purpur', (170, 125, 170)), ('end_rod', (240, 240, 230)), ('candle', (230, 220, 190))]

STEMW = [('mushroom_stem', 58), ('white_terracotta', 24), ('calcite', 12), ('smooth_sandstone', 6)]
CAPS = {'red': ([('red_mushroom_block', 1)], 'white_concrete'),
        'brown': ([('brown_mushroom_block', 1)], 'mushroom_stem'),
        'orange': ([('orange_concrete', 70), ('orange_terracotta', 30)], 'white_concrete'),
        'purple': ([('purple_terracotta', 60), ('magenta_terracotta', 40)], 'white_concrete')}

def ring_cells(cx, cz, R, rx=None, rz=None):
    """둥근 벽 칸 (안쪽 칸, 바깥 테두리 칸) — 타원 가능"""
    rx = rx or R; rz = rz or R
    inside = {(x, z) for x in range(int(cx - rx - 2), int(cx + rx + 3)) for z in range(int(cz - rz - 2), int(cz + rz + 3))
              if ((x - cx) / (rx + 0.35)) ** 2 + ((z - cz) / (rz + 0.35)) ** 2 <= 1}
    edge = {(x, z) for (x, z) in inside if any((x + dx, z + dz) not in inside for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    return inside, edge

def card(cx, cz, x, z):
    """칸이 가운데에서 어느 쪽인지 (S/N/E/W)"""
    a = math.atan2(z - cz, x - cx)
    return 'E' if -math.pi / 4 <= a < math.pi / 4 else 'S' if math.pi / 4 <= a < 3 * math.pi / 4 else 'N' if -3 * math.pi / 4 <= a < -math.pi / 4 else 'W'

def drum(s, cx, cz, R, y0, h, rx=None, rz=None, mats=STEMW, low=True, band=None):
    """둥근 몸통: 바닥(돌 테두리+나무 마루) + 둥근 벽. band = 나무 띠 높이 목록"""
    ins, edge = ring_cells(cx, cz, R, rx, rz)
    for (x, z) in ins: s.set(x, 0, z, 'stone_bricks' if (x, z) in edge else 'spruce_planks')
    for (x, z) in edge:
        for y in range(y0, y0 + h):
            if y == y0 and low: s.set(x, y, z, s.pick(STONE_LOW))
            elif band and y in band: s.set(x, y, z, LOG('stripped_dark_oak_log', 'x' if card(cx, cz, x, z) in 'SN' else 'z'))
            else: s.set(x, y, z, s.pick(mats))
    # 버섯 밑동처럼 아래가 살짝 넓음 (바깥 한 줄 계단)
    for (x, z) in edge:
        f = card(cx, cz, x, z); dx, dz = OUT[f]
        if (x + dx, z + dz) not in ins and s.get(x + dx, 0, z + dz) is None: s.set(x + dx, 0, z + dz, ST('stone_brick', FACE[OPP[f]]))
    return ins, edge

def floor_at(s, cx, cz, R, y, rx=None, rz=None):
    ins, edge = ring_cells(cx, cz, R, rx, rz)
    for (x, z) in ins:
        if (x, z) not in edge: s.set(x, y, z, 'spruce_planks')

def rwin(s, cx, cz, x, z, y, h=2, frame='dark_oak'):
    """둥근 벽 창: 벽칸 유리판 + 바깥 위 처마 계단 + 아래 받침"""
    f = card(cx, cz, x, z); dx, dz = OUT[f]
    for k in range(h): s.set(x, y + k, z, 'glass_pane')
    s.set(x + dx, y + h, z + dz, ST(frame, FACE[OPP[f]], 'top'))
    s.set(x + dx, y - 1, z + dz, ST(frame, FACE[OPP[f]], 'top'))

def wins_around(s, cx, cz, edge, y, n=4, skip=('S',), h=2, start=0.0):
    """벽 둘레에 창 n개 (각도 고르게), 문 쪽(skip 면 가운데)은 건너뜀"""
    out = []
    def adiff(p, a): return abs(((math.atan2(p[1] - cz, p[0] - cx) - a + math.pi) % (2 * math.pi)) - math.pi)
    for i in range(n):
        a = start + i * 2 * math.pi / n
        c = min(edge, key=lambda p: adiff(p, a))
        if card(cx, cz, *c) in skip and abs(c[0] - cx) <= 1.5: continue
        rwin(s, cx, cz, c[0], c[1], y, h); out.append(c)
    return out

def rdoor(s, x, z, y=1, wood='spruce', f='S'):
    """둥근 벽 문: 양옆 원목 기둥 + 위 아치 + 문 앞 디딤돌 + 매단 등"""
    dx, dz = OUT[f]; ox, oz = (1, 0) if f in 'SN' else (0, 1)
    s.set(x, y, z, f'{wood}_door[facing={FACE[f]},half=lower,hinge=left,open=false,powered=false]')
    s.set(x, y + 1, z, f'{wood}_door[facing={FACE[f]},half=upper,hinge=left,open=false,powered=false]')
    for sg in (-1, 1):
        for k in range(0, 3): s.set(x + ox * sg, y + k, z + oz * sg, LOG('dark_oak_log'))
    s.set(x, y + 2, z, LOG('stripped_dark_oak_log', 'x' if f in 'SN' else 'z'))
    s.set(x + dx, y + 2, z + dz, SL('dark_oak', 'top'))
    for sg in (-1, 1): s.set(x + dx + ox * sg, y + 2, z + dz + oz * sg, ST('dark_oak', FACE[OPP[f]], 'top'))
    s.set(x + dx, 0, z + dz, SL('stone_brick')); s.set(x + 2 * dx, 0, z + 2 * dz, SL('stone_brick')) if s.get(x + 2 * dx, 0, z + 2 * dz) is None else None
    s.set(x + dx + ox * 2, y + 1, z + dz + oz * 2, 'lantern[hanging=false,waterlogged=false]') if s.get(x + dx + ox * 2, y + 1, z + dz + oz * 2) is None else None

def cap(s, cx, cz, R, y, H, kind='red', rx=None, rz=None, lip=True, spots=True, thick=2, chimney_at=None, seed_off=0):
    """버섯 갓 지붕 (둥근 돔, 타원 가능): 가장자리는 한 칸 아래로 말림, 빨간 갓엔 흰 점.
    y = 갓 밑면 높이, H = 돔 높이. 돌려주는 값: 갓 꼭대기 y"""
    rx = rx or R; rz = rz or R
    mats, spot = CAPS[kind]
    cells = {}
    for x in range(int(cx - rx - 1), int(cx + rx + 2)):
        for z in range(int(cz - rz - 1), int(cz + rz + 2)):
            d = math.sqrt(((x - cx) / (rx + 0.4)) ** 2 + ((z - cz) / (rz + 0.4)) ** 2)
            if d > 1: continue
            t = y + int(round(H * max(0.0, 1 - d ** 2) ** 0.75))
            cells[(x, z)] = (t, d)
    top = y
    for (x, z), (t, d) in list(cells.items()):
        b0 = max(y, t - (1 if d > 0.78 else thick) + 1)
        if lip and d > 0.9: b0 = y - 1          # 말린 가장자리
        for yy in range(b0, t + 1): s.set(x, yy, z, s.pick(mats))
        cells[(x, z)] = (t, d, b0)
        top = max(top, t)
    # 계단으로 경사 부드럽게: 옆보다 높이가 1 낮은 바깥 칸 위에 계단 얹지 않고, 둥근 느낌은 높이차 1칸씩이라 그대로
    if spots and spot:
        import random
        r = random.Random(int(cx * 7 + cz * 13 + R + seed_off))
        surf = [(x, z) for (x, z), (t, d, b0) in cells.items() if d < 0.93]
        centers = []
        for _ in range(len(surf) // 9):
            c = r.choice(surf)
            if all(math.hypot(c[0] - a, c[1] - b) > 3.2 for a, b in centers): centers.append(c)
        for (a, b) in centers:
            rr = r.choice((0.8, 1.0, 1.5)) if kind != 'brown' else 0.5
            if kind == 'brown' and r.random() < 0.5: continue
            for (x, z), (t, d, b0) in cells.items():
                if math.hypot(x - a, z - b) <= rr: s.set(x, t, z, spot)
    if chimney_at:
        x, z = chimney_at
        t = cells.get((x, z), (y, 0, y))[0]
        for yy in range(t - 1, t + 3): s.set(x, yy, z, 'bricks' if s.r.random() > 0.2 else 'stone_bricks')
        s.set(x, t + 3, z, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    return top, cells

def under_cap(s, cx, cz, R, y, rx=None, rz=None, body=None, lamps=4, glow=True):
    """갓 밑: 버섯빛 + 사슬 등 (가장자리 안쪽), 몸통 위 천장 마루"""
    rx = rx or R; rz = rz or R
    for i in range(lamps):
        a = math.pi / 4 + i * 2 * math.pi / lamps
        x = int(round(cx + math.cos(a) * (rx - 1.2))); z = int(round(cz + math.sin(a) * (rz - 1.2)))
        if body and (x, z) in body[0]: continue
        if s.get(x, y - 1, z) is None:
            s.set(x, y - 1, z, 'chain[axis=y]'); s.set(x, y - 2, z, 'lantern[hanging=true,waterlogged=false]')
    if glow:
        for i in range(lamps):
            a = i * 2 * math.pi / lamps
            x = int(round(cx + math.cos(a) * (rx - 0.6))); z = int(round(cz + math.sin(a) * (rz - 0.6)))
            if s.get(x, y, z) and 'mushroom' in s.get(x, y, z): s.set(x, y, z, 'shroomlight')


def stem_to_cap(s, xs_zs, y_from, cells):
    """줄기를 갓 밑면까지 이어 붙임 (갓 가운데가 둥글게 비어 있어서 생기는 틈 메움)"""
    for (x, z) in xs_zs:
        c = cells.get((x, z))
        if not c: continue
        for yy in range(y_from, c[2]): 
            if s.get(x, yy, z) is None: s.set(x, yy, z, 'mushroom_stem')

def small_mush(s, x, z, h=3, R=2, kind='red', y0=1, stem2=False):
    """작은 버섯 (곁채·장식): 1×1 또는 2×2 기둥 + 갓"""
    for yy in range(y0, y0 + h):
        s.set(x, yy, z, 'mushroom_stem')
        if stem2:
            for (a, b) in ((1, 0), (0, 1), (1, 1)): s.set(x + a, yy, z + b, 'mushroom_stem')
    c = (x + 0.5, z + 0.5) if stem2 else (x, z)
    res = cap(s, c[0], c[1], R, y0 + h, max(1, R // 2 + 1), kind, lip=R >= 3, spots=R >= 3)
    stem_to_cap(s, [(x, z)] + ([(x + 1, z), (x, z + 1), (x + 1, z + 1)] if stem2 else []), y0 + h, res[1])
    return res

def furnish_round(s, cx, cz, R, y=1, kind='home'):
    return   # 소품 없음 (사용자가 직접 꾸밈)
    ins, edge = ring_cells(cx, cz, R)
    inner = [p for p in ins if p not in edge]
    near_wall = [p for p in inner if any((p[0] + dx, p[1] + dz) in edge for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    items = {'home': ['bookshelf', 'barrel[facing=up,open=false]', 'chest[facing=south,type=single,waterlogged=false]', 'crafting_table', 'furnace[facing=south,lit=false]'],
             'shop': ['barrel[facing=up,open=false]', 'chest[facing=south,type=single,waterlogged=false]', 'barrel[facing=up,open=false]', 'bookshelf'],
             'lab': ['bookshelf', 'bookshelf', 'brewing_stand', 'lectern[facing=south,has_book=true,powered=false]', 'cartography_table'],
             'alch': ['brewing_stand', 'cauldron', 'bookshelf', 'barrel[facing=up,open=false]', 'brewing_stand'],
             'smith': ['anvil[facing=north]', 'smithing_table', 'blast_furnace[facing=south,lit=true]', 'grindstone[face=floor,facing=north]'],
             'inn': ['barrel[facing=up,open=false]', 'barrel[facing=up,open=false]', 'brewing_stand', 'chest[facing=south,type=single,waterlogged=false]'],
             'gem': ['chest[facing=south,type=single,waterlogged=false]', 'amethyst_block', 'bookshelf', 'smithing_table']}[kind]
    near_wall.sort(key=lambda p: math.atan2(p[1] - cz, p[0] - cx))
    i = 0
    for k, (x, z) in enumerate(near_wall):
        if card(cx, cz, x, z) == 'S' and abs(x - cx) <= 1: continue
        if k % 2 == 0 and s.get(x, y, z) is None: s.set(x, y, z, items[i % len(items)]); i += 1
    for x in range(int(cx) - 1, int(cx) + 2):
        for z in range(int(cz) - 1, int(cz) + 2):
            if s.get(x, y, z) is None: s.set(x, y, z, 'brown_carpet' if kind in ('shop', 'smith') else 'red_carpet')
    s.set(int(cx), y, int(cz), 'spruce_fence'); s.set(int(cx), y + 1, int(cz), 'spruce_pressure_plate[powered=false]')

def balcony(s, cx, cz, R, y, f='S', w=2, rail='dark_oak_fence'):
    """앞쪽 작은 반원 발코니 (뒤집힌 계단 받침 + 울타리)"""
    dx, dz = OUT[f]; ox, oz = (1, 0) if f in 'SN' else (0, 1)
    bx, bz = int(round(cx + dx * (R + 1))), int(round(cz + dz * (R + 1)))
    for i in range(-w, w + 1):
        x, z = bx + ox * i, bz + oz * i
        s.set(x, y, z, 'spruce_planks'); s.set(x, y - 1, z, ST('dark_oak', FACE[OPP[f]], 'top'))
        s.set(x + dx, y, z + dz, ST('dark_oak', FACE[OPP[f]], 'top')); s.set(x + dx, y + 1, z + dz, rail)
    for sg in (-1, 1):
        x, z = bx + ox * sg * (w + 1), bz + oz * sg * (w + 1)
        s.set(x, y + 1, z, rail); s.set(x + dx, y + 1, z + dz, rail)
    # 발코니로 나가는 문 (벽칸)
    wx, wz = int(round(cx + dx * R)), int(round(cz + dz * R))
    s.set(wx, y + 1, wz, f'spruce_door[facing={FACE[f]},half=lower,hinge=left,open=false,powered=false]')
    s.set(wx, y + 2, wz, f'spruce_door[facing={FACE[f]},half=upper,hinge=left,open=false,powered=false]')

def ladder_in(s, cx, cz, R, y0, y1):
    """안쪽 북쪽 벽에 사다리 + 위층 바닥 구멍"""
    x, z = int(cx), int(cz - R + 1)
    for yy in range(y0, y1 + 1): s.set(x, yy, z, 'ladder[facing=south,waterlogged=false]')
    s.set(x, y1, z, 'ladder[facing=south,waterlogged=false]')

def mush_faces(get, setf, coords):
    """큰 버섯 블록 면 상태: 같은 블록과 붙은 면 = false (안 보임), 갓 밑면 = false (주름) — 배치 끝난 뒤 실제 좌표로"""
    dirs = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
    out = {}
    for (x, y, z), base in coords:
        props = []
        for dn, (dx, dy, dz) in dirs.items():
            nb = get(x + dx, y + dy, z + dz)
            same = nb is not None and nb.split('[')[0] == base
            val = not same
            if dn == 'down' and base != 'minecraft:mushroom_stem': val = False
            props.append(f'{dn}={"true" if val else "false"}')
        out[(x, y, z)] = f'{base}[{",".join(sorted(props))}]'
    for k, v in out.items(): setf(*k, v)

def seal(s, edge, wall_top, cells, mats=STEMW):
    """벽 위 ~ 갓 밑 사이 틈 메우기 (기둥이 갓 속으로 들어가는 모양)"""
    for (x, z) in edge:
        if (x, z) in cells:
            for yy in range(wall_top + 1, cells[(x, z)][2]): s.set(x, yy, z, s.pick(mats))

def mush_house(s, cx, cz, R, floors=1, kind='red', capR=None, capH=None, rx=None, rz=None, chim=None, nwin=5, fkind='home', band=True, door=True, extra_h=0):
    """둥근 버섯 집: 몸통(층마다 4칸, 층 사이 나무 띠) + 창 + 문 + 갓 + 갓 밑 등 + 안 꾸미기. 돌려줌: (몸통, 벽 꼭대기 y, 갓 꼭대기 y)"""
    fh = 4; h = floors * fh + extra_h
    bands = tuple(1 + fh * i for i in range(1, floors)) if band else None
    body = drum(s, cx, cz, R, 1, h, rx, rz, band=bands)
    for i in range(1, floors): floor_at(s, cx, cz, R, 1 + fh * i, rx, rz)
    edge = body[1]
    if door:
        dz = max(z for (x, z) in edge if x == int(cx))
        rdoor(s, int(cx), dz, 1)
    wins_around(s, cx, cz, edge, 2, n=nwin, start=math.pi / 2)
    for i in range(1, floors): wins_around(s, cx, cz, edge, 2 + fh * i, n=nwin, start=math.pi / 2 + math.pi / nwin, skip=())
    capR = capR or R + 2.5; capH = capH or max(4, int(round(capR * 0.72)))
    crx = (rx + capR - R) if rx else None; crz = (rz + capR - R) if rz else None
    top, cells = cap(s, cx, cz, capR, h + 1, capH, kind, rx=crx, rz=crz, chimney_at=chim)
    seal(s, edge, h, cells)
    under_cap(s, cx, cz, capR, h + 1, rx=crx, rz=crz, body=body)
    furnish_round(s, cx, cz, R if not rx else min(rx, rz), 1, fkind)
    if floors > 1: ladder_in(s, cx, cz, R if not rz else rz, 1, 1 + fh * (floors - 1))
    return body, h, top

def rect_cap_house(s, X1, Z1, floors=1, kind='red', over=2.5, capH=None, timber=True, chim=None):
    """네모 반목조 몸통 + 버섯 갓 지붕 (타원)"""
    foundation(s, 0, 0, X1, Z1)
    stone_story(s, 0, 0, X1, Z1, 1, 4)
    y = 5
    for f in range(floors - 1):
        wins = [('S', (x, Z1)) for x in range(2, X1 - 1, 4)] + [('N', (x, 0)) for x in range(2, X1 - 1, 4)] + [('E', (X1, z)) for z in range(2, Z1 - 1, 4)] + [('W', (0, z)) for z in range(2, Z1 - 1, 4)]
        timber_story(s, 0, 0, X1, Z1, y, h=4, post=4, wins=wins, infill=STEMW) if timber else stone_story(s, 0, 0, X1, Z1, y, 4)
        y += 4
    cx, cz = X1 / 2, Z1 / 2
    rx, rz = X1 / 2 + over, Z1 / 2 + over
    capH = capH or max(4, int(round(min(rx, rz) * 0.8)))
    top, cells = cap(s, cx, cz, max(rx, rz), y, capH, kind, rx=rx, rz=rz, chimney_at=chim)
    edge = [p for f_, cl in sides_of(0, 0, X1, Z1).items() for p in cl]
    seal(s, edge, y - 1, cells)
    under_cap(s, cx, cz, max(rx, rz), y, rx=rx, rz=rz, glow=True)
    return y, top

# ───────── 반목조 건물에 버섯이 자라남 ─────────
WALLY = ('terracotta', 'calcite', 'mushroom_stem', 'stone_brick', 'cobblestone', 'andesite', '_log', 'planks', 'bricks')

def shelf(s, x, y, z, f, size, kind='brown'):
    """벽에 붙은 선반 버섯 (벽에서 튀어나온 반달 판)"""
    dx, dz = OUT[f]; tx, tz = (1, 0) if f in 'SN' else (0, 1)
    m = 'brown_mushroom_block' if kind == 'brown' else 'red_mushroom_block'
    for i in range(-(size // 2), size - size // 2):
        p = (x + dx + tx * i, y, z + dz + tz * i)
        if s.get(*p) is None: s.set(*p, m)
    if size >= 3 and s.get(x + 2 * dx, y, z + 2 * dz) is None: s.set(x + 2 * dx, y, z + 2 * dz, m)

def giant(s, x, z, h, R, kind='red', y0=1, lean=(0, 0), stem2=False, H=None):
    """큰 버섯 (곁에 자라서 지붕 위로 갓이 덮음): 줄기는 위로 갈수록 lean 쪽으로 기울어짐"""
    lx, lz = lean
    for k in range(h):
        t = k / max(1, h - 1)
        xx, zz = int(round(x + lx * t * t)), int(round(z + lz * t * t))
        s.set(xx, y0 + k, zz, 'mushroom_stem')
        if stem2:
            for (a, b) in ((1, 0), (0, 1), (1, 1)): s.set(xx + a, y0 + k, zz + b, 'mushroom_stem')
    cx, cz = x + lx + (0.5 if stem2 else 0), z + lz + (0.5 if stem2 else 0)
    top, cells = cap(s, cx, cz, R, y0 + h, H or max(2, int(R * 0.6)), kind, lip=R >= 3, spots=True)
    xx, zz = int(round(x + lx)), int(round(z + lz))
    stem_to_cap(s, [(xx, zz)] + ([(xx + 1, zz), (xx, zz + 1), (xx + 1, zz + 1)] if stem2 else []), y0 + h, cells)
    for (a, b, c) in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
        q = (int(round(cx)) + a * int(R * 0.6), y0 + h - 1, int(round(cz)) + c * int(R * 0.6))
        if s.get(*q) is None and R >= 4: s.set(*q, 'shroomlight') if False else None
    return top

def overgrow(s, amount=1.0, seed=0, kinds=(('brown', 3), ('red', 1)), roof_sprouts=2, base_bumps=True):
    """다 지은 반목조 건물에 버섯이 자라게: 벽 선반 버섯 · 지붕에서 솟은 작은 버섯 · 밑동 혹 버섯"""
    import random
    r = random.Random(seed)
    def kpick(): return s.pick(list(kinds))
    cand = []
    for (x, y, z), st in list(s.b.items()):
        if y < 2 or st == 'minecraft:air': continue
        n = st.split(':')[1].split('[')[0]
        if 'stairs' in n or 'slab' in n or not any(k in n for k in WALLY): continue
        for f, (dx, dz) in OUT.items():
            if all(s.get(x + dx, y + k, z + dz) is None for k in (-1, 0, 1)) and s.get(x + 2 * dx, y, z + 2 * dz) is None:
                cand.append((x, y, z, f))
    r.shuffle(cand); used = []
    n_shelf = max(2, int(len(cand) * 0.015 * amount))
    for (x, y, z, f) in cand:
        if len(used) >= n_shelf: break
        if any(abs(x - a) + abs(z - b) + abs(y - c) < 6 for a, c, b in used): continue
        k = 'brown' if r.random() < 0.85 else 'red'; size = r.choice((1, 2, 3, 3))
        shelf(s, x, y, z, f, size, k)
        if size >= 2 and r.random() < 0.6: shelf(s, x + (1 if f in 'SN' else 0), y - 2, z + (0 if f in 'SN' else 1), f, size - 1, k)
        used.append((x, y, z))
    # 지붕에서 솟은 작은 버섯: 지붕 겉면(계단·반블록) 중 위가 빈 칸
    ys = [p[1] for p in s.b]; ymax = max(ys)
    roof = [(x, y, z) for (x, y, z), st in s.b.items() if y > ymax * 0.45 and ('stairs' in st or 'slab' in st) and 'stone' not in st and 'brick' not in st and s.get(x, y + 1, z) is None
            and all(s.get(x + a, y + b, z + c) is None for a in (-2, 0, 2) for c in (-2, 0, 2) for b in (2, 3))]
    r.shuffle(roof); got = []
    for (x, y, z) in roof:
        if len(got) >= roof_sprouts: break
        if any(abs(x - a) + abs(z - b) < 7 for a, b in got): continue
        h = r.randint(1, 2); R = r.choice((2, 2.5))
        for k in range(1, h + 1): s.set(x, y + k, z, 'mushroom_stem')
        _, cl = cap(s, x, z, R, y + h + 1, 1 if R < 2 else 2, kpick(), lip=False, spots=R >= 2)
        stem_to_cap(s, [(x, z)], y + h + 1, cl)
        got.append((x, z))
    if base_bumps:   # 밑동 모서리에 혹 버섯 (땅 바로 위 벽에 1~2칸)
        low = [(x, y, z, f) for (x, y, z, f) in cand if 2 <= y <= 3]
        r.shuffle(low)
        for (x, y, z, f) in low[:max(1, int(2 * amount))]: shelf(s, x, y, z, f, r.choice((1, 2)), 'brown')
