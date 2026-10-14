"""두 번째 마을 초안 v3 — v2 + 소품·마감 디테일 (식물 없음)"""
import os, random, math
from village2_lib_v2 import *
from schem import autoconnect, render2, view2
from sheet import sheet

def top(s, x, z):
    ys = [y for (xx, y, zz) in s.b if xx == x and zz == z and s.b[(xx, y, zz)] != 'minecraft:air']
    return max(ys) if ys else -1

def put(s, x, z, *blocks, start=0, roof=False):
    """start 높이부터 위로 첫 빈칸에 차례로 쌓기 (roof=True 면 기둥 맨 위)"""
    if roof: y = top(s, x, z) + 1
    else:
        y = start
        while s.get(x, y, z) not in (None, 'minecraft:air'): y += 1
    for b in blocks: s.set(x, y, z, b); y += 1
    return y

def sign_post(s, x, z, arm='west', wood='spruce'):
    """울타리 기둥 + 팔 + 매달린 간판"""
    y = put(s, x, z, f'{wood}_fence', f'{wood}_fence', f'{wood}_fence')
    dx, dz = DIRS[arm]
    s.set(x + dx, y - 1, z + dz, f'{wood}_fence')
    rot = 0 if arm in ('east', 'west') else 4
    s.set(x + dx, y - 2, z + dz, f'{wood}_hanging_sign[attached=false,rotation={rot},waterlogged=false]')

def lamp(s, x, z, wood='spruce', h=2, start=0):
    put(s, x, z, start=start, * ([f'{wood}_fence'] * h + ['lantern[hanging=false]']))

def brazier(s, x, z):
    put(s, x, z, 'cobblestone_wall', 'cobblestone_wall', 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')

def crates(s, r, pts):
    for (x, z, n) in pts: put(s, x, z, *(['barrel[facing=up,open=false]'] * n))

# ───────── A 연구소 디테일 ─────────
def lab3():
    s = lab(); r = random.Random(111)
    import village2_cap as vc
    vc.clear_old(s); vc.build(s)
    # 발코니 밑 사슬 랜턴
    for (x, z) in ((-6, 11), (6, 11), (-11, -1), (11, 7), (-11, 7), (11, -1)): s.set(x, 4, z, 'lantern[hanging=true]')
    # 1층 앞 연구 소품 (2층 돌출 밑)
    for (x, b) in ((-7, 'bookshelf'), (-6, 'bookshelf'), (-5, 'lectern[facing=south,has_book=true,powered=false]'), (-3, 'barrel[facing=up,open=true]'),
                   (3, 'cauldron'), (4, 'brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]'), (6, 'barrel[facing=up,open=false]'), (7, 'barrel[facing=up,open=false]')):
        s.set(x, 1, 9, b)
    s.set(-7, 2, 9, 'bookshelf'); s.set(7, 2, 9, 'barrel[facing=up,open=false]')
    s.set(-1, 3, 10, 'spruce_hanging_sign[attached=false,rotation=0,waterlogged=false]')
    # 몸통: 옆 창 + 사다리 (언덕 → 작은 발코니)
    for (x, z, f) in ((5, 0, 'east'), (-5, 1, 'west')):
        for y in (16, 17): s.set(x, y, z, 'glass_pane')
        dx = 1 if f == 'east' else -1
        s.set(x + dx, 15, z, f'spruce_trapdoor[facing={f},half=top,open=false]')
        s.set(x + dx, 16, z - 1, f'spruce_trapdoor[facing={f},half=bottom,open=true]') if False else None
    for y in (13, 14): s.set(0, y, 5, 'ladder[facing=south,waterlogged=false]')
    # 녹색 언덕 위: 기둥 랜턴 4 + 벤치
    for (x, z) in ((-8, 0), (8, 0), (-7, 8), (7, 8)): lamp(s, x, z, 'spruce', 1, start=10)
    for x in (4, 5): put(s, x, 7, 'spruce_stairs[facing=north,half=bottom,shape=straight]', start=10)
    put(s, 3, 7, 'spruce_trapdoor[facing=west,half=bottom,open=true]', start=10); put(s, 6, 7, 'spruce_trapdoor[facing=east,half=bottom,open=true]', start=10)
    # 갓: 빨간 테두리에 흰 점 + 꼭대기 피뢰침(관측 장치)
    put(s, 0, 0, 'lightning_rod[facing=up,powered=false,waterlogged=false]', roof=True)
    # 동쪽 날개: 문 + 간판 + 장작
    door(s, 14, 1, 7, wood='spruce'); s.set(14, 1, 8, None)
    s.set(13, 3, 8, 'spruce_fence'); s.set(13, 2, 8, 'spruce_hanging_sign[attached=false,rotation=0,waterlogged=false]')
    for i, z in enumerate((1, 2, 3, 4)): s.set(18, 1, z, 'spruce_log[axis=x]' if i % 2 else 'oak_log[axis=x]'); s.set(18, 2, z, 'birch_log[axis=x]') if i < 3 else None
    # 뿌리 끝 랜턴 + 통나무 벤치
    lamp(s, -8, -6, 'dark_oak', 1); lamp(s, 8, -7, 'dark_oak', 1)
    for x in (-4, -3, -2): s.set(x, 1, 13, 'stripped_spruce_log[axis=x]')
    autoconnect(s); return s

# ───────── B 공방 디테일 ─────────
def alchemy3():
    s = alchemy(); r = random.Random(222)
    # 입구 소품: 가마솥·양조대·통 + 간판 기둥
    put(s, -2, 4, 'cauldron'); put(s, 2, 4, 'brewing_stand[has_bottle_0=true,has_bottle_1=true,has_bottle_2=false]')
    crates(s, r, [(3, 3, 2), (4, 2, 1), (-3, 3, 1)])
    sign_post(s, 3, 6, 'west')
    # 갓 아래 둘레 통로 (y19, 난간)
    c = 2.0 * (16 / 18) ** 2
    for x in range(-4, 8):
        for z in range(-5, 6):
            d = math.hypot(x - c, z)
            if 2.6 < d <= 4.2 and s.get(x, 19, z) is None:
                s.set(x, 19, z, 'spruce_slab[type=top]')
                if d > 3.4: s.set(x, 20, z, 'spruce_fence')
    for a in range(0, 8):
        ang = a / 8 * 6.283; x = round(c + math.cos(ang) * 3.1); z = round(math.sin(ang) * 3.1)
        if s.get(x, 18, z) is None: s.set(x, 18, z, 'spruce_stairs[facing=%s,half=top,shape=straight]' % (('west' if math.cos(ang) > 0 else 'east') if abs(math.cos(ang)) > abs(math.sin(ang)) else ('north' if math.sin(ang) > 0 else 'south')))
    # 갓 아랫면 빛 (주름 사이)
    gills = [p for p, st in s.b.items() if p[1] == 21 and ('mushroom_stem' in st or 'brown_mushroom' in st)]
    r.shuffle(gills)
    for p in gills[:5]: s.set(*p, 'shroomlight')
    # 오두막: 창 아래 창턱 + 사다리 + 걸린 랜턴
    for y in range(1, 5): s.set(-6, y, -3, 'ladder[facing=north,waterlogged=false]')
    s.set(-9, 5, -1, 'spruce_trapdoor[facing=west,half=top,open=false]')
    s.set(-10, 9, 0, 'dark_oak_fence'); s.set(-10, 8, 0, 'lantern[hanging=true]')
    # 바닥 둘레 돌 디딤 + 기둥 랜턴
    for (x, z) in ((-4, 6), (5, 5), (6, -3), (-3, -6)): lamp(s, x, z, 'spruce', 1)
    autoconnect(s); return s

# ───────── C 상점 디테일 ─────────
def shop3(name, capm, seed, spots, wing):
    s = shop(name, capm, seed, spots, wing); r = random.Random(seed * 7)
    for y in (7, 8): s.set(-1, y, 3, 'spruce_trapdoor[facing=south,half=bottom,open=true]'); s.set(1, y, 3, 'spruce_trapdoor[facing=south,half=bottom,open=true]')
    sign_post(s, -2, 5, 'east')
    # 진열대 위 물건 + 저울(가마솥) + 케이크
    s.set(3, 2, 5, 'cake[bites=0]') if 'red' in capm else s.set(3, 2, 5, 'cauldron')
    s.set(5, 2, 5, 'barrel[facing=up,open=true]')
    # 갓 밑 사슬 랜턴 2
    for (x, z) in ((-3, -2), (3, -2)):
        for y in (10, 9): s.set(x, y, z, 'chain')
        s.set(x, 8, z, 'lantern[hanging=true]')
    # 벤치 + 기둥 랜턴
    for z in (-1, 0): put(s, 5, z, 'spruce_stairs[facing=west,half=bottom,shape=straight]')
    lamp(s, -4, -3, 'spruce', 2)
    if wing:
        for i, z in enumerate((-3, -4)): put(s, -7, z, 'spruce_log[axis=x]', 'oak_log[axis=x]' if i == 0 else 'spruce_slab[type=bottom]')
    autoconnect(s); return s

# ───────── D 초소 디테일 ─────────
def gate3():
    s = gate(); r = random.Random(444)
    # 양옆 통나무 방책 (높이 4·5 번갈아, 돌 기초)
    for xs, sgn in ((range(-15, -10), -1), (range(10, 15), 1)):
        for i, x in enumerate(xs):
            s.set(x, 0, 2, r.choice(['cobblestone', 'stone_bricks', 'mossy_cobblestone']))
            h = 5 if i % 2 == 0 else 4
            for y in range(1, h + 1): s.set(x, y, 2, 'spruce_log[axis=y]')
            s.set(x, h + 1, 2, 'spruce_fence')
            s.set(x, 3, 1, 'dark_oak_log[axis=x]') if i % 2 else None
    # 길 양쪽 화로
    brazier(s, -5, 7); brazier(s, 4, 7)
    # 탑 정면 벽 랜턴 (팔 + 랜턴)
    for x in (-8, -4, 4, 8):
        s.set(x, 5, 5, 'spruce_fence') if s.get(x, 5, 5) is None else None
    for x in (-8, 8): s.set(x, 4, 6, 'lantern[hanging=true]') if False else None
    # 무기 거치대 (울타리 + 숫돌) · 건초 과녁 · 상자
    for x in (5, 6, 7): s.set(x, 1, 6, 'spruce_fence')
    s.set(5, 2, 6, 'grindstone[face=floor,facing=north]'); s.set(7, 2, 6, 'spruce_slab[type=bottom]')
    s.set(-9, 1, 6, 'hay_block[axis=y]'); s.set(-9, 2, 6, 'target[power=0]')
    crates(s, r, [(-7, 6, 2), (-6, 6, 1)])
    # 공지 게시판 (울타리 기둥 + 판자 + 깃발)
    for x in (9, 11):
        for y in (1, 2): s.set(x, y, 7, 'spruce_fence')
    for x in (9, 10, 11): s.set(x, 3, 7, 'spruce_planks'); s.set(x, 4, 7, 'spruce_slab[type=bottom]')
    s.set(10, 2, 8, 'spruce_trapdoor[facing=south,half=top,open=false]') if False else None
    # 통로 위 깃발 + 탑 꼭대기 피뢰침
    s.set(-1, 6, 5, 'red_wall_banner[facing=south]') if False else None
    for cx in (-7, 6): put(s, cx, 2, 'lightning_rod[facing=up,powered=false,waterlogged=false]', roof=True)
    autoconnect(s); return s

if __name__ == '__main__':
    items = []
    os.makedirs('v2d3', exist_ok=True)
    for s, label in ((lab3(), 'A. 버섯 연구소 (모리스)'), (alchemy3(), 'B. 연금술 공방 (네라)'),
                     (shop3('shop_red', 'red_mushroom_block', 5, ('mushroom_stem', 7), False), 'C1. 상점 (빨강 갓)'),
                     (shop3('shop_brown', 'brown_mushroom_block', 6, ('shroomlight', 4), True), 'C2. 상점 (갈색 갓 + 창고)'),
                     (gate3(), 'D. 경비 초소 입구 (카엘)')):
        p = 'v2d3/' + s.name.split('/')[1]
        s.save(p + '.schem')
        x0, y0, z0, x1, y1, z1 = s.bounds(); dim = f' {x1 - x0 + 1}×{z1 - z0 + 1}×{y1 - y0 + 1}'
        render2(view2(s, 0), p + '.png', scale=18); render2(view2(s, 2), p + '_back.png', scale=18)
        items.append((p + '.png', label + dim)); items.append((p + '_back.png', label + ' (뒤)'))
        if 'lab' in p:
            from schem import S as _S
            c = _S('c'); c.b = {k: v for k, v in s.b.items() if k[1] >= 14}
            render2(view2(c, 1), 'v2d3/lab_cap_side.png', scale=22)
            u = _S('u'); u.b = {(x, -y, z): v for (x, y, z), v in s.b.items() if y >= 16}
            render2(view2(u, 0), 'v2d3/lab_cap_under.png', scale=18)
        print(s.name, dim)
    print(sheet(items[:4], 'village2_draft3_big.png', cols=2))
    print(sheet([('v2d3/lab_cap_side.png', 'A 갓 확대 (옆)'), ('v2d3/lab_cap_under.png', 'A 갓 아랫면 (밑에서 본 모습)')], 'village2_lab_cap.png', cols=2))
    print(sheet(items[4:], 'village2_draft3_small.png', cols=3))
