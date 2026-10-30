"""2지역 버섯 마을 + 사냥터 구조물 (A 스타일 디테일 + 버섯 테마). 앞 = 남쪽(+z). 건물엔 식물 없음."""
from mlib import *

# ───────── 마을 ─────────
def kael_gate():
    """경비대 초소 (카엘): 네모 돌탑 + 반목조 망루방 + 갈색 갓 지붕, 옆에 무기 걸이·모닥불"""
    s = Bld('m2/kael_gate', 101)
    foundation(s, 0, 0, 6, 6)
    stone_story(s, 0, 0, 6, 6, 1, 7)
    door(s, 3, 6, 'S'); window(s, 3, 4, 0, 'N', h=1, shutters=False); window(s, 0, 4, 3, 'W', h=1, shutters=False); window(s, 6, 4, 3, 'E', h=1, shutters=False)
    jetty(s, 0, 0, 6, 6, 7)
    wins = [('S', (3, 7)), ('N', (3, -1)), ('E', (7, 3)), ('W', (-1, 3))]
    timber_story(s, -1, -1, 7, 7, 8, h=4, post=2, wins=wins, infill=STEMW)
    top, cells = cap(s, 3, 3, 6, 12, 4, 'brown', chimney_at=None)
    seal(s, [p for cl in sides_of(-1, -1, 7, 7).values() for p in cl], 11, cells)
    under_cap(s, 3, 3, 6, 12, glow=True)
    banner(s, 1, 6, 6, 'blue'); banner(s, 5, 6, 6, 'blue')
    for yy in range(1, 12): s.set(5, yy, 1, 'ladder[facing=west,waterlogged=false]')
    for yy in range(1, 8): s.set(1, yy, 1, s.get(1, yy, 1) or 'air')
    # 무기 걸이 (울타리 + 숫돌) · 통 · 모닥불 · 벤치
    for x in (8, 10): s.set(x, 1, 2, 'spruce_fence'); s.set(x, 2, 2, 'spruce_fence')
    s.set(9, 2, 2, 'spruce_fence'); s.set(9, 1, 2, 'grindstone[face=floor,facing=north]')
    barrels(s, [(8, 1, 4), (8, 1, 5), (8, 2, 5)])
    s.set(-3, 1, 5, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    s.set(-4, 1, 5, ST('spruce', 'east')); s.set(-2, 1, 5, ST('spruce', 'west'))
    lamp_post(s, 1, 9); lamp_post(s, 5, 9)
    return finish(s)

def lab():
    """버섯 연구소 (모리스): 3층 둥근 탑 (갈색 큰 갓) + 2층 발코니 + 옆 둥근 실험실 (빨간 갓) + 관측 망원경"""
    s = Bld('m2/lab', 102)
    body, h, top = mush_house(s, 0, 0, 5, floors=3, kind='brown', capR=7.5, capH=5, nwin=6, fkind='lab', chim=(-4, 2), extra_h=1)
    balcony(s, 0, 0, 5, 5, 'S', w=2)
    floor_at(s, 0, 0, 5, 5); floor_at(s, 0, 0, 5, 9)
    # 옆 실험실 (동쪽, 이어진 통로)
    b2, h2, t2 = mush_house(s, 10, 2, 3, floors=1, kind='red', capR=5, capH=3, nwin=3, fkind='alch', door=False)
    for z in range(0, 4):
        for y in range(1, 4):
            for x in (5, 6, 7): s.set(x, y, z, 'air' if 0 < z < 3 and y < 3 else s.pick(STEMW))
        for x in (5, 6, 7): s.set(x, 0, z, 'spruce_planks')
    for x in (5, 6, 7): s.set(x, 4, 0, SL('dark_oak')); s.set(x, 4, 3, SL('dark_oak')); s.set(x, 4, 1, 'dark_oak_planks'); s.set(x, 4, 2, 'dark_oak_planks')
    rdoor(s, 10, 5, 1)
    # 옥상 망원경 (갓 위)
    s.set(0, top + 1, 0, 'spruce_fence'); s.set(0, top + 2, 0, 'spruce_fence'); s.set(1, top + 2, 0, 'lightning_rod[facing=east,powered=false,waterlogged=false]')
    s.set(-1, top + 1, 0, ST('spruce', 'east'))
    hanging_sign(s, -3, 4, 4, 'S')
    # 연구 표본 상자·책상 (앞마당)
    barrels(s, [(-6, 1, 6), (-6, 1, 7)]); s.set(-5, 1, 7, 'crafting_table'); s.set(-4, 1, 7, 'lectern[facing=south,has_book=true,powered=false]')
    return finish(s)

def alch2():
    """연금술 공방 (네라): 보라 갓 버섯 집 + 바깥 가마솥·양조대 작업대 + 차양"""
    s = Bld('m2/alchemy', 103)
    body, h, top = mush_house(s, 0, 0, 4, floors=1, kind='purple', capR=6.5, capH=5, nwin=5, fkind='alch', chim=(-3, -2), extra_h=2)
    # 서쪽 작업 차양 (반목조 기둥 + 반블록 지붕)
    for z in range(-2, 3):
        s.set(-7, 1, z, 'cauldron' if z in (-2, 2) else 'spruce_planks' if z == 0 else 'air')
    s.set(-7, 2, 0, 'brewing_stand'); s.set(-7, 1, -1, 'barrel[facing=up,open=false]'); s.set(-7, 1, 1, 'barrel[facing=up,open=false]')
    for z in (-3, 3):
        for y in range(1, 5): s.set(-8, y, z, LOG('stripped_dark_oak_log'))
    for z in range(-3, 4):
        s.set(-8, 5, z, SL('dark_oak')); s.set(-7, 5, z, SL('dark_oak')); s.set(-6, 5, z, SL('dark_oak'))
    s.set(-6, 4, 0, 'chain[axis=y]') if False else None
    s.set(-7, 4, 0, 'lantern[hanging=true,waterlogged=false]')
    for (x, z) in ((3, 5), (-2, 5)): s.set(x, 1, z, 'amethyst_block')
    hanging_sign(s, 2, 4, 4, 'S')
    return finish(s)

def gear2():
    """장비 상점: 네모 반목조 2층 + 빨간 갓 지붕 + 앞 진열 (모루·숫돌) + 간판"""
    s = Bld('m2/gear2', 104)
    y, top = rect_cap_house(s, 10, 7, floors=2, kind='red', over=2.5, chim=(1, 2))
    door(s, 5, 7, 'S'); window(s, 2, 2, 7, 'S'); window(s, 8, 2, 7, 'S'); window(s, 0, 2, 3, 'W'); window(s, 10, 2, 3, 'E')
    for x in range(3, 8): s.set(x, 4, 8, SL('dark_oak', 'top'))
    s.set(2, 1, 9, 'anvil[facing=east]'); s.set(8, 1, 9, 'grindstone[face=floor,facing=north]'); s.set(9, 1, 9, 'barrel[facing=up,open=false]')
    hanging_sign(s, 3, 4, 7, 'S'); banner(s, 1, 4, 7, 'light_gray'); banner(s, 9, 4, 7, 'light_gray')
    furnish(s, 0, 0, 10, 7, 1, 'shop')
    return finish(s)

def general2():
    """잡화점: 주황 갓 버섯 집 + 앞 차양 (빨강·흰 양털 줄무늬) + 통·상자"""
    s = Bld('m2/general2', 105)
    body, h, top = mush_house(s, 0, 0, 4, floors=1, kind='orange', capR=6.5, capH=5, nwin=4, fkind='shop', extra_h=2)
    for x in range(-3, 4):
        s.set(x, 4, 6, 'red_wool' if x % 2 else 'white_wool'); s.set(x, 3, 7, 'red_wool' if x % 2 else 'white_wool')
    for x in (-3, 3):
        for y in range(1, 3): s.set(x, y, 7, 'spruce_fence')
    barrels(s, [(-2, 1, 7), (2, 1, 7)]); s.set(-1, 1, 7, SL('spruce', 'top')); s.set(1, 1, 7, SL('spruce', 'top'))
    crates(s, 5, 3); crates(s, -6, 2)
    hanging_sign(s, -2, 4, 4, 'S')
    return finish(s)

def smithy2():
    """대장간: 돌 둥근 몸통 + 큰 갈색 갓 + 열린 작업장 (모루·용광로·숫돌·용암 가마솥·물통)"""
    s = Bld('m2/smithy2', 106)
    body = drum(s, 0, 0, 4, 1, 5, mats=STONE, band=None)
    edge = body[1]
    rdoor(s, 0, max(z for (x, z) in edge if x == 0), 1)
    wins_around(s, 0, 0, edge, 2, n=4, start=math.pi / 2 + math.pi / 4)
    # 동쪽 작업장 (기둥 + 낮은 담)
    for z in range(-3, 4):
        s.set(5, 0, z, 'stone_bricks'); s.set(6, 0, z, 'stone_bricks'); s.set(7, 0, z, 'stone_bricks')
    for (x, z) in ((8, -3), (8, 3)):
        for y in range(1, 5): s.set(x, y, z, LOG('stripped_dark_oak_log'))
    s.set(6, 1, -2, 'blast_furnace[facing=east,lit=true]'); s.set(6, 1, 0, 'anvil[facing=north]'); s.set(6, 1, 2, 'lava_cauldron')
    s.set(7, 1, 2, 'water_cauldron[level=3]'); s.set(7, 1, -2, 'grindstone[face=floor,facing=east]'); s.set(5, 1, 3, 'smithing_table')
    top, cells = cap(s, 1.5, 0, 8, 6, 5, 'brown', rx=8, rz=6.5, chimney_at=(-2, -2))
    seal(s, edge, 5, cells)
    for (x, z) in ((8, -3), (8, 3)):
        for yy in range(5, cells.get((x, z), (6, 0, 6))[2]): s.set(x, yy, z, LOG('stripped_dark_oak_log'))
    under_cap(s, 1.5, 0, 8, 6, rx=8, rz=6.5, body=body)
    woodpile(s, -5, 4); barrels(s, [(-5, 1, -3)])
    hanging_sign(s, 2, 4, 3, 'S')
    furnish_round(s, 0, 0, 4, 1, 'smith')
    return finish(s)

def jeweler2():
    """세공소: 작은 빨간 갓 버섯 집 + 자수정 장식 + 진열대"""
    s = Bld('m2/jeweler2', 107)
    body, h, top = mush_house(s, 0, 0, 3, floors=1, kind='red', capR=5.5, capH=4, nwin=4, fkind='gem', extra_h=2)
    for (x, z) in ((-2, 4), (2, 4)): s.set(x, 1, z, 'amethyst_block'); s.set(x, 2, z, 'amethyst_cluster[facing=up,waterlogged=false]')
    s.set(4, 1, 2, SL('spruce', 'top')); s.set(4, 1, 3, SL('spruce', 'top')); s.set(4, 2, 2, 'lantern[hanging=false,waterlogged=false]')
    hanging_sign(s, -1, 4, 3, 'S')
    return finish(s)

def inn2():
    """버섯 여관: 큰 2층 버섯 (빨간 갓) + 작은 2층 버섯 (주황 갓) + 사이 반목조 이음 + 간판·통"""
    s = Bld('m2/inn2', 108)
    b1, h1, t1 = mush_house(s, 0, 0, 5, floors=2, kind='red', capR=7.5, capH=6, nwin=6, fkind='inn', chim=(-4, -3))
    balcony(s, 0, 0, 5, 5, 'S', w=2)
    b2, h2, t2 = mush_house(s, 11, -1, 4, floors=2, kind='orange', capR=6, capH=4, nwin=5, fkind='inn', door=False, extra_h=2)
    for x in range(5, 8):
        for z in range(-2, 2):
            s.set(x, 0, z, 'spruce_planks')
            for y in range(1, 5): s.set(x, y, z, 'air' if -2 < z < 1 and y < 4 else (LOG('dark_oak_log') if x in (5, 7) and z in (-2, 1) else s.pick(STEMW)))
            s.set(x, 5, z, 'dark_oak_planks')
    for x in range(5, 8): s.set(x, 6, -2, ST('dark_oak', 'south')); s.set(x, 6, 1, ST('dark_oak', 'north')); s.set(x, 6, -1, SL('dark_oak')); s.set(x, 6, 0, SL('dark_oak'))
    rdoor(s, 11, 3, 1)
    barrels(s, [(-6, 1, 3), (-6, 1, 4), (-6, 2, 4)]); bench(s, 2, 8); bench(s, -3, 8)
    hanging_sign(s, -3, 4, 5, 'S')
    return finish(s)

def plaza_mush():
    """광장: 가운데 거대 버섯 (3×3 기둥, 빨간 갓) + 둥근 물웅덩이 + 게시판 + 벤치 + 등"""
    s = Bld('m2/plaza_mush', 109); c = 7
    for x in range(0, 15):
        for z in range(0, 15):
            d = math.hypot(x - c, z - c)
            if d <= 7.4: s.set(x, 0, z, 'polished_andesite' if (x + z) % 3 else 'stone_bricks')
            if 4.6 <= d <= 5.5: s.set(x, 1, z, 'mossy_stone_bricks' if (x * 3 + z) % 5 == 0 else 'stone_bricks'); s.set(x, 2, z, SL('smooth_stone'))
            elif 1.8 < d < 4.6: s.set(x, 0, z, 'stone_bricks'); s.set(x, 1, z, 'water')
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            for y in range(0, 12): s.set(x, y, z, 'mushroom_stem')
    for (dx, dz) in ((-2, 0), (2, 0), (0, -2), (0, 2)):   # 밑동 볼록
        s.set(c + dx, 1, c + dz, 'mushroom_stem'); s.set(c + dx, 2, c + dz, 'mushroom_stem')
    top, cells = cap(s, c, c, 9, 12, 5, 'red')
    under_cap(s, c, c, 9, 12, lamps=6)
    for (dx, dz) in ((2, 2), (-2, 2), (2, -2), (-2, -2)): s.set(c + dx, 11, c + dz, 'shroomlight')
    # 게시판 (북쪽)
    for x in (5, 9):
        for y in range(1, 4): s.set(x, y, -2, LOG('dark_oak_log'))
    for x in range(5, 10): s.set(x, 4, -2, ST('spruce', 'south')); s.set(x, 4, -3, ST('spruce', 'north'))
    for x in range(6, 9): s.set(x, 2, -2, 'spruce_planks'); s.set(x, 3, -2, 'spruce_planks'); s.set(x, 2, -1, 'spruce_wall_sign[facing=south,waterlogged=false]')
    for (x, z, f) in ((0, 7, 'east'), (14, 7, 'west'), (7, 15, 'north')):
        for i in (-1, 0, 1):
            if f == 'north': s.set(x + i, 1, z, ST('spruce', f))
            else: s.set(x, 1, z + i, ST('spruce', f))
    for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)): s.set(x, 1, z, 'stone_brick_wall'); s.set(x, 2, z, 'stone_brick_wall'); s.set(x, 3, z, 'lantern[hanging=false,waterlogged=false]')
    return finish(s)

def waystone():
    """이동의 돌 (마을 텔레포트 자리): 둥근 단 + 가운데 자석석 + 자수정 + 네 귀퉁이 작은 버섯 기둥"""
    s = Bld('m2/waystone', 110); c = 4
    for x in range(0, 9):
        for z in range(0, 9):
            d = math.hypot(x - c, z - c)
            if d <= 4.4: s.set(x, 0, z, 'polished_deepslate' if d > 3.2 else 'deepslate_tiles' if d > 1.5 else 'chiseled_deepslate')
            if 3.3 < d <= 4.4: s.set(x, 1, z, SL('polished_deepslate'))
    s.set(c, 1, c, 'lodestone'); s.set(c, 2, c, 'amethyst_block'); s.set(c, 3, c, 'amethyst_cluster[facing=up,waterlogged=false]')
    for (x, z) in ((1, 1), (7, 1), (1, 7), (7, 7)):
        small_mush(s, x, z, h=3, R=1, kind='red' if (x + z) % 4 else 'brown')
        s.set(x, 1, z, 'polished_deepslate')
    for (x, z) in ((c, 0), (c, 8), (0, c), (8, c)): s.set(x, 1, z, 'air')
    return finish(s)

def mh1():
    """주민 집 1: 1층 + 빨간 갓, 뒤에 작은 갈색 버섯 창고"""
    s = Bld('m2/house1', 111)
    mush_house(s, 0, 0, 4, floors=1, kind='red', capR=6.5, capH=5, nwin=4, extra_h=2, chim=(-2, -3))
    small_mush(s, 5, -5, h=3, R=3, kind='brown', stem2=True)
    woodpile(s, -6, 3, 3); s.set(-5, 1, 5, 'composter[level=0]') if False else None
    return finish(s)

def mh2():
    """주민 집 2: 2층 + 갈색 갓 + 발코니"""
    s = Bld('m2/house2', 112)
    mush_house(s, 0, 0, 4, floors=2, kind='brown', capR=6.5, capH=5, nwin=5, chim=(3, -2))
    balcony(s, 0, 0, 4, 5, 'S', w=1)
    barrels(s, [(5, 1, 3)])
    return finish(s)

def mh3():
    """주민 집 3: 옆으로 긴 타원 몸통 + 주황 갓 + 굴뚝"""
    s = Bld('m2/house3', 113)
    mush_house(s, 0, 0, 4, floors=1, kind='orange', rx=6, rz=4, capR=6.5, capH=5, nwin=6, extra_h=2, chim=(-4, -1))
    bench(s, 3, 7); s.set(-4, 1, 6, 'barrel[facing=up,open=false]')
    return finish(s)

def mh4():
    """주민 집 4: 쌍둥이 버섯 (빨강 + 갈색, 키 다름), 사이 낮은 이음"""
    s = Bld('m2/house4', 114)
    mush_house(s, 0, 0, 3, floors=1, kind='red', capR=5.5, capH=4, nwin=3, extra_h=2)
    mush_house(s, 7, -2, 3, floors=2, kind='brown', capR=5, capH=4, nwin=4, door=False)
    for x in (3, 4):
        for z in (-1, 0):
            s.set(x, 0, z, 'spruce_planks'); s.set(x, 1, z, 'air'); s.set(x, 2, z, 'air'); s.set(x, 3, z, 'dark_oak_planks')
        s.set(x, 1, -2, s.pick(STEMW)); s.set(x, 2, -2, s.pick(STEMW)); s.set(x, 1, 1, s.pick(STEMW)); s.set(x, 2, 1, 'glass_pane')
    return finish(s)

def mh5():
    """주민 집 5: 가는 3층 탑 집 + 빨간 갓"""
    s = Bld('m2/house5', 115)
    mush_house(s, 0, 0, 3, floors=3, kind='red', capR=5.5, capH=4, nwin=4)
    s.set(4, 1, 2, 'barrel[facing=up,open=false]')
    return finish(s)

def well2():
    """버섯 우물: 둥근 돌 우물 + 네 기둥 + 갈색 작은 갓 지붕 + 사슬 두레박"""
    s = Bld('m2/well2', 116); c = 3
    for x in range(0, 7):
        for z in range(0, 7):
            d = math.hypot(x - c, z - c)
            if d <= 3.4: s.set(x, 0, z, 'stone_bricks' if d > 1.6 else 'water')
            if 1.6 < d <= 2.6: s.set(x, 1, z, 'mossy_stone_bricks' if (x + z) % 3 == 0 else 'stone_bricks'); s.set(x, 2, z, SL('stone_brick'))
            elif d <= 1.6: s.set(x, 1, z, 'water'); s.set(x, -1, z, 'water'); s.set(x, -2, z, 'stone_bricks')
    for (x, z) in ((1, 1), (5, 1), (1, 5), (5, 5)):
        for y in range(3, 6): s.set(x, y, z, 'dark_oak_fence')
    top, cells = cap(s, c, c, 4, 6, 3, 'brown', lip=True, spots=False)
    s.set(c, 5, c, 'chain[axis=y]'); s.set(c, 4, c, 'chain[axis=y]'); s.set(c, 3, c, 'cauldron')
    return finish(s)

def stalls2():
    """시장 노점 3칸: 판매대 + 가운데 기둥 + 버섯 갓 파라솔 (빨강·주황·갈색)"""
    s = Bld('m2/stalls2', 117)
    for i, kind in enumerate(('red', 'orange', 'brown')):
        x0 = i * 7
        for x in range(x0, x0 + 4): s.set(x, 1, 0, SL('spruce', 'top')); s.set(x, 0, 0, 'spruce_planks') if False else None
        s.set(x0, 1, 0, 'barrel[facing=up,open=false]'); s.set(x0 + 3, 1, 0, 'barrel[facing=up,open=false]')
        for y in range(1, 4): s.set(x0 + 1, y, 2, 'mushroom_stem')
        cap(s, x0 + 1, 1, 3, 4, 2, kind, lip=True, spots=kind != 'brown')
        s.set(x0 + 2, 2, 0, 'lantern[hanging=false,waterlogged=false]')
        s.set(x0 + 1, 2, 0, ['cake[bites=0]', 'barrel[facing=up,open=false]', 'chest[facing=south,type=single,waterlogged=false]'][i])
    return finish(s)

# ───────── 사냥터 구조물 (퀘스트 지점) ─────────
def tent(s, x0, z0, L=5, wool='white_wool', frame='spruce'):
    """A자 천막 (z 방향 길이 L): 양털 지붕 + 앞 기둥"""
    for z in range(z0, z0 + L):
        s.set(x0, 1, z, wool); s.set(x0 + 4, 1, z, wool)
        s.set(x0 + 1, 2, z, wool); s.set(x0 + 3, 2, z, wool); s.set(x0 + 2, 3, z, LOG(f'{frame}_log', 'z'))
    for z in (z0 - 1, z0 + L):
        for y in (1, 2): s.set(x0 + 2, y, z, f'{frame}_fence')
    s.set(x0 + 2, 1, z0 + L - 1, 'brown_carpet')

def spore_camp():
    """버섯 연구 캠프 (spore_camp): 천막 2개 + 연구 책상 (독서대·지도 탁자) + 보급 상자 + 모닥불"""
    s = Bld('m2p/spore_camp', 201)
    tent(s, 0, 0); tent(s, 7, -1, wool='light_gray_wool')
    s.set(4, 1, 7, 'cartography_table'); s.set(5, 1, 7, 'lectern[facing=south,has_book=true,powered=false]'); s.set(6, 1, 7, 'crafting_table')
    barrels(s, [(-2, 1, 2), (-2, 1, 3), (-2, 2, 3)]); s.set(-2, 1, 4, 'chest[facing=east,type=single,waterlogged=false]')
    s.set(6, 1, 10, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    s.set(4, 1, 10, LOG('spruce_log', 'z')); s.set(8, 1, 10, LOG('spruce_log', 'z'))
    lamp_post(s, 3, 5); lamp_post(s, 9, 5)
    for x in range(-1, 12):
        for z in range(-2, 12):
            if s.get(x, 0, z) is None and s.r.random() < 0.12: s.set(x, 0, z, s.pick([('coarse_dirt', 60), ('gravel', 40)]))
    return finish(s)

def spirit_altar():
    """정령의 제단 (spirit_altar): 둥근 이끼 돌단 + 기둥 6개 (위에 작은 버섯) + 가운데 책 놓인 독서대 + 버섯빛"""
    s = Bld('m2p/spirit_altar', 202); c = 6
    for x in range(0, 13):
        for z in range(0, 13):
            d = math.hypot(x - c, z - c)
            if d <= 6.2: s.set(x, 0, z, s.pick([('mossy_stone_bricks', 50), ('stone_bricks', 30), ('cracked_stone_bricks', 20)]))
            if d <= 2.4: s.set(x, 1, z, SL('mossy_stone_brick') if d > 1.2 else 'chiseled_stone_bricks')
    s.set(c, 2, c, 'lectern[facing=south,has_book=true,powered=false]')
    for i in range(6):
        a = i * math.pi / 3 + math.pi / 6
        x, z = int(round(c + math.cos(a) * 5)), int(round(c + math.sin(a) * 5))
        h = 3 + (i % 3)
        for y in range(1, h): s.set(x, y, z, 'mossy_stone_brick_wall' if y < h - 1 else 'stone_brick_wall')
        s.set(x, h, z, 'mushroom_stem'); cap(s, x, z, 1, h + 1, 1, 'red' if i % 2 else 'brown', lip=False, spots=False)
    for (dx, dz) in ((2, 0), (-2, 0), (0, 2), (0, -2)): s.set(c + dx, 1, c + dz, 'shroomlight')
    s.set(c + 1, 1, c + 1, 'amethyst_cluster[facing=up,waterlogged=false]'); s.set(c - 1, 1, c - 1, 'amethyst_cluster[facing=up,waterlogged=false]')
    return finish(s)

def soldier_camp():
    """병정 막사 (soldier_camp): 버섯 병정 천막 (짧은 기둥 + 빨간 갓) 2개 + 작전 탁자 + 허수아비 + 반원 목책"""
    s = Bld('m2p/soldier_camp', 203)
    for (cx, cz) in ((3, 3), (12, 3)):
        drum(s, cx, cz, 2, 1, 3, low=False)
        e = ring_cells(cx, cz, 2)[1]
        rdoor(s, cx, max(z for (x, z) in e if x == cx), 1)
        top, cells = cap(s, cx, cz, 4, 4, 3, 'red')
        seal(s, e, 3, cells)
    s.set(7, 1, 8, 'cartography_table'); s.set(8, 1, 8, 'spruce_planks'); s.set(8, 2, 8, 'red_banner[rotation=0]')
    s.set(6, 1, 8, ST('spruce', 'east')); s.set(9, 1, 8, ST('spruce', 'west'))
    for x in (3, 12):   # 허수아비
        s.set(x, 1, 10, 'spruce_fence'); s.set(x, 2, 10, 'hay_block[axis=y]'); s.set(x, 3, 10, 'white_wool')
    for i in range(23):
        a = math.pi + i * math.pi / 22
        x, z = int(round(7.5 + math.cos(a) * 11)), int(round(4 + math.sin(a) * 8))
        for y in range(1, 4): s.set(x, y, z, LOG('spruce_log'))
        s.set(x, 4, z, SL('spruce'))
    s.set(7, 1, 12, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    banner(s, 2, 3, 5, 'red'); banner(s, 11, 3, 5, 'red')
    return finish(s)

def deep_post():
    """깊은 곳 전초기지 (deep_post): 네모 목책 + 문 + 망대 (기둥 위 마루·난간·작은 지붕) + 상자"""
    s = Bld('m2p/deep_post', 204)
    N = 10
    for x in range(0, N + 1):
        for z in range(0, N + 1):
            if x in (0, N) or z in (0, N):
                if z == N and x in (4, 5, 6): continue
                for y in range(1, 4): s.set(x, y, z, LOG('spruce_log'))
                s.set(x, 4, z, 'spruce_fence' if (x + z) % 2 else SL('spruce'))
    for x in (3, 7):
        for y in range(1, 5): s.set(x, y, N, LOG('stripped_spruce_log'))
    for x in range(3, 8): s.set(x, 5, N, SL('spruce'))
    for (x, z) in ((1, 1), (4, 1), (1, 4), (4, 4)):
        for y in range(1, 6): s.set(x, y, z, LOG('dark_oak_log'))
    for x in range(0, 6):
        for z in range(0, 6): s.set(x, 6, z, 'spruce_planks')
    for x in range(0, 6):
        for z in range(0, 6):
            if x in (0, 5) or z in (0, 5): s.set(x, 7, z, 'spruce_fence')
    for yy in range(1, 7): s.set(2, yy, 5, 'ladder[facing=south,waterlogged=false]')
    s.set(2, 7, 5, 'air')
    for (x, z) in ((0, 0), (5, 0), (0, 5), (5, 5)): s.set(x, 8, z, 'spruce_fence')
    for x in range(-1, 7):
        for z in range(-1, 7): s.set(x, 9, z, SL('dark_oak'))
    barrels(s, [(8, 1, 1), (8, 1, 2), (8, 2, 1), (9, 1, 1)]); s.set(7, 1, 2, 'chest[facing=west,type=single,waterlogged=false]')
    s.set(7, 1, 7, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    s.set(3, 7, 3, 'lantern[hanging=false,waterlogged=false]')
    return finish(s)

def witch_hut():
    """마녀의 은신처 (witch_hideout): 기둥 위 비뚤어진 오두막 (진흙벽돌·짙은 참나무) + 보라 갓 지붕 + 가마솥·양조대·책장·거미줄"""
    s = Bld('m2p/witch_hut', 205)
    for (x, z) in ((0, 0), (7, 0), (0, 6), (7, 6)):
        for y in range(0, 4): s.set(x, y, z, LOG('dark_oak_log'))
    for x in range(0, 8):
        for z in range(0, 7): s.set(x, 4, z, 'dark_oak_planks')
    for y in range(5, 9):
        for f, cl in sides_of(0, 0, 7, 6).items():
            for (x, z) in cl: s.set(x, y, z, LOG('dark_oak_log') if (x in (0, 7) and z in (0, 6)) else s.pick([('mud_bricks', 60), ('packed_mud', 25), ('dark_oak_planks', 15)]))
    s.set(3, 5, 6, 'dark_oak_door[facing=south,half=lower,hinge=left,open=false,powered=false]'); s.set(3, 6, 6, 'dark_oak_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
    for (x, z) in ((6, 6), (0, 3), (7, 3), (3, 0)): s.set(x, 6, z, 'glass_pane')
    for z in range(7, 10): s.set(3, 4, z, 'dark_oak_planks') if z == 7 else None
    for k, z in enumerate(range(8, 12)): s.set(3, 3 - k, z, ST('dark_oak', 'north'))
    top, cells = cap(s, 3.5, 3, 6, 9, 4, 'purple', rx=6, rz=5.5, chimney_at=(1, 1))
    seal(s, [p for cl in sides_of(0, 0, 7, 6).values() for p in cl], 8, cells)
    s.set(2, 5, 2, 'cauldron'); s.set(5, 5, 2, 'brewing_stand'); s.set(1, 5, 1, 'bookshelf'); s.set(1, 6, 1, 'bookshelf'); s.set(6, 5, 1, 'bookshelf')
    s.set(5, 5, 4, 'crafting_table'); s.set(6, 8, 5, 'cobweb'); s.set(1, 8, 5, 'cobweb')
    s.set(3, 7, 3, 'soul_lantern[hanging=false,waterlogged=false]') if False else s.set(3, 8, 3, 'soul_lantern[hanging=true,waterlogged=false]')
    s.set(4, 4, 7, 'soul_lantern[hanging=false,waterlogged=false]')
    return finish(s)

def beast_nest():
    """짐승 둥지 (beast_nest): 비스듬히 쌓인 통나무 고리 + 건초·흙 바닥 + 뼈 블록 + 해골"""
    s = Bld('m2p/beast_nest', 206); c = 7
    for x in range(0, 15):
        for z in range(0, 15):
            d = math.hypot(x - c, z - c)
            if d <= 5.2: s.set(x, 0, z, s.pick([('coarse_dirt', 40), ('rooted_dirt', 30), ('podzol', 30)]))
            if d <= 3.6 and s.r.random() < 0.5: s.set(x, 1, z, 'hay_block[axis=y]' if s.r.random() < 0.6 else 'brown_carpet')
    for i in range(28):
        a = i * 2 * math.pi / 28
        rr = 5.6 + s.r.random() * 1.2
        x, z = int(round(c + math.cos(a) * rr)), int(round(c + math.sin(a) * rr))
        ax = 'x' if abs(math.sin(a)) > 0.7 else 'z'
        h = 1 + s.r.randint(1, 3)
        for y in range(1, h + 1): s.set(x, y, z, LOG(s.r.choice(['dark_oak_log', 'spruce_log', 'stripped_dark_oak_log']), ax if y > 1 else 'y'))
    for (x, z) in ((5, 5), (9, 8), (6, 10), (11, 6)): s.set(x, 1, z, 'bone_block[axis=x]')
    s.set(8, 2, 6, 'bone_block[axis=z]'); s.set(7, 1, 7, 'skeleton_skull[rotation=4]'); s.set(10, 1, 10, 'bone_block[axis=y]')
    return finish(s)

def owen_hut():
    """숲지기 오두막 (오웬): 통나무 오두막 + 갈색 갓 지붕 + 장작 + 말리는 틀"""
    s = Bld('m2p/owen_hut', 207)
    foundation(s, 0, 0, 7, 6, floor='spruce_planks')
    for yy in range(1, 4):
        for f, cells_ in sides_of(0, 0, 7, 6).items():
            for (x, z) in cells_: s.set(x, yy, z, LOG('spruce_log', 'x' if f in 'SN' else 'z'))
    for (x, z) in ((0, 0), (7, 0), (0, 6), (7, 6)):
        for yy in range(1, 5): s.set(x, yy, z, LOG('stripped_spruce_log'))
    door(s, 3, 6, 'S', lamps=False, arch=False); window(s, 6, 2, 6, 'S', h=1); window(s, 0, 2, 3, 'W', h=1); window(s, 7, 2, 3, 'E', h=1)
    top, cells = cap(s, 3.5, 3, 5.5, 4, 4, 'brown', rx=5.5, rz=4.5, chimney_at=(1, 1))
    seal(s, [p for cl in sides_of(0, 0, 7, 6).values() for p in cl], 3, cells)
    for x in (9, 12):
        for yy in (1, 2, 3): s.set(x, yy, 2, 'spruce_fence')
    for x in (10, 11): s.set(x, 3, 2, 'spruce_fence'); s.set(x, 2, 2, 'chain[axis=y]')
    woodpile(s, 1, 8); barrels(s, [(8, 1, 6)]); s.set(5, 1, 8, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    banner(s, 1, 3, 6, 'green'); furnish(s, 0, 0, 7, 6, 1, 'home')
    return finish(s)

ALLV = [(kael_gate, '경비대 초소 (카엘)'), (lab, '버섯 연구소 (모리스)'), (alch2, '연금술 공방 (네라)'), (gear2, '장비 상점'), (general2, '잡화점'),
        (smithy2, '대장간'), (jeweler2, '세공소'), (inn2, '버섯 여관'), (plaza_mush, '광장 거대 버섯 + 게시판'), (waystone, '이동의 돌 (텔레포트 자리)'),
        (mh1, '주민 집 1'), (mh2, '주민 집 2'), (mh3, '주민 집 3'), (mh4, '주민 집 4 (쌍둥이)'), (mh5, '주민 집 5 (탑)'), (well2, '버섯 우물'), (stalls2, '버섯 노점 3칸')]
ALLP = [(spore_camp, '버섯 연구 캠프 (5 입구)'), (spirit_altar, '정령의 제단'), (soldier_camp, '병정 막사 (6)'), (deep_post, '깊은 곳 전초기지 (7)'),
        (witch_hut, '마녀의 은신처'), (beast_nest, '짐승 둥지 (8 안쪽)'), (owen_hut, '숲지기 오두막 (오웬, 8 입구)')]

def gallery(lst, path, cols=3, scale=11):
    from PIL import Image, ImageDraw, ImageFont
    F = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 22, index=1)
    tiles = []
    for fn, title in lst:
        s = fn(); x0, y0, z0, x1, y1, z1 = s.bounds()
        im = render2(view2(s, 0), f'out/{s.name.split("/")[1]}.png', scale=scale, bg=(150, 190, 230))
        c = Image.new('RGB', (max(im.width, 520), im.height + 36), (150, 190, 230)); c.paste(im, ((c.width - im.width) // 2, 36))
        ImageDraw.Draw(c).text((10, 4), f'{title} ({x1 - x0 + 1}×{z1 - z0 + 1}, 높이 {y1 - y0 + 1})', font=F, fill=(20, 30, 50)); tiles.append(c)
    W = max(t.width for t in tiles)
    rows = [tiles[i:i + cols] for i in range(0, len(tiles), cols)]
    H = sum(max(t.height for t in r) for r in rows)
    sh = Image.new('RGB', (W * cols, H), (150, 190, 230)); y = 0
    for r in rows:
        for i, t in enumerate(r): sh.paste(t, (i * W + (W - t.width) // 2, y))
        y += max(t.height for t in r)
    sh.save(path); print(path, sh.size)

if __name__ == '__main__':
    import sys
    if 'v' in sys.argv[1:]: gallery(ALLV, 'm2_village.png')
    if 'p' in sys.argv[1:]: gallery(ALLP, 'm2_points.png')
