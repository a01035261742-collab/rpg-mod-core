"""후보 2차: 새 형태 + 1·3·6 변형"""
import os, random
from schem import S, autoconnect, render, view
from lib4 import *
from sheet import sheet
from candidates import base

C = []
def crow_gable(s, r, x0, x1, z, ybase, ytop):
    """계단식 돌 박공 (박공벽을 지붕보다 위로 계단 모양으로)"""
    w = x1 - x0
    for y in range(ybase, ytop + 1):
        k = (y - ybase) // 2
        a, b = x0 + k, x1 - k
        if a > b: break
        for x in range(a, b + 1): s.set(x, y, z, r.choice(['stone_bricks', 'stone_bricks', 'polished_andesite', 'mossy_stone_bricks']))
        if (y - ybase) % 2 == 1:
            s.set(a, y + 1, z, 'stone_brick_slab[type=bottom]'); s.set(b, y + 1, z, 'stone_brick_slab[type=bottom]')

def steep_dormer(s, r, cx, z_front, y, full=full, stair='deepslate_tile'):
    """가파른 지붕용 지붕창 (폭 3, 앞으로 튀어나옴)"""
    for dy in range(3):
        for z in range(z_front - 3, z_front + 1):
            s.set(cx - 1, y + dy, z, 'stripped_dark_oak_log[axis=y]' if z == z_front else r.choice(INF4)); s.set(cx + 1, y + dy, z, 'stripped_dark_oak_log[axis=y]' if z == z_front else r.choice(INF4))
            s.set(cx, y + dy, z, 'air')
        s.set(cx, y + dy, z_front, 'glass_pane' if dy < 2 else 'stripped_dark_oak_log[axis=x]')
    for z in range(z_front - 3, z_front + 2):
        s.set(cx - 2, y + 3, z, f'{stair}_stairs[facing=east,half=bottom,shape=straight]'); s.set(cx + 2, y + 3, z, f'{stair}_stairs[facing=west,half=bottom,shape=straight]')
        s.set(cx - 1, y + 3, z, full); s.set(cx + 1, y + 3, z, full); s.set(cx, y + 3, z, full)
        s.set(cx - 1, y + 4, z, f'{stair}_stairs[facing=east,half=bottom,shape=straight]'); s.set(cx + 1, y + 4, z, f'{stair}_stairs[facing=west,half=bottom,shape=straight]'); s.set(cx, y + 4, z, f'{stair}_slab[type=bottom]')

# 9. 계단식 돌 박공 (플랑드르풍)
s, r, (a, b, c, d), y = base(9); top = steep_roof(s, r, a, b, c, d, y, axis='z', gable=INF4, timber_gable='dark_oak', over=0)
crow_gable(s, r, a, b, d + 1, y, top + 1); C.append((s, '9. 계단식 돌 박공'))
# 10. 옆 박공 + 지붕창 줄
s, r, (a, b, c, d), y = base(10); steep_roof(s, r, a - 1, b + 1, c, d, y, axis='x', gable=INF4, timber_gable='dark_oak')
for cx in (1, 7): steep_dormer(s, r, cx, d + 1, y + 2)
C.append((s, '10. 옆 박공 + 지붕창 2개'))
# 11. 1층 회랑(기둥) + 정면 박공
s, r, (a, b, c, d), y = base(11)
for x in range(0, 9):
    for yy in (1, 2, 3): s.set(x, yy, 7, 'air') if x not in (0, 4, 8) else s.set(x, yy, 7, 'stone_brick_wall')
    s.set(x, 3, 7, 'stone_brick_stairs[facing=south,half=top,shape=straight]') if x not in (0, 4, 8) else None
    for yy in (1, 2, 3): s.set(x, yy, 5, r.choice(INF4)) if x not in (4,) else None
door(s, 4, 1, 5)
steep_roof(s, r, a, b, c, d, y, axis='z', gable=INF4, timber_gable='dark_oak'); C.append((s, '11. 1층 기둥 회랑 + 박공'))
# 12. 겹박공 (큰 박공 앞에 작은 박공)
s, r, (a, b, c, d), y = base(12); steep_roof(s, r, a, b, c, d, y, axis='z', gable=INF4, timber_gable='dark_oak')
jetty_floor(s, 2, d + 1, 6, d + 3, y - 5) if False else None
for xx in range(2, 7):
    for yy in range(y - 4, y):
        for zz in (d + 1, d + 2): s.set(xx, yy, zz, r.choice(INF4) if xx in (2, 6) or zz == d + 2 else 'air')
for yy in (y - 3, y - 2): s.set(4, yy, d + 2, 'glass_pane')
steep_roof(s, r, 2, 6, d - 1, d + 2, y, axis='z', gable=INF4, timber_gable='dark_oak', over=1)
C.append((s, '12. 겹박공 (앞에 작은 박공)'))
# 13. 1 변형: 정면 박공 + 발코니 + 깊은 처마
s, r, (a, b, c, d), y = base(13)
for x in range(a, b + 1): s.set(x, y - 4, d + 1, 'spruce_slab[type=top]'); s.set(x, y - 3, d + 1, 'spruce_fence')
for x in (a, b): s.set(x, y - 5, d + 1, 'dark_oak_stairs[facing=north,half=top,shape=straight]')
door(s, 4, y - 3, d)
steep_roof(s, r, a, b, c, d, y, axis='z', gable=INF4, timber_gable='dark_oak', over=2, full='spruce_planks', stair='spruce', alt=('dark_oak_planks',))
C.append((s, '13. [1 변형] 발코니 + 깊은 처마 + 갈색'))
# 14. 3 변형: L자 (옆 박공 + 한쪽 앞으로 튀어나온 날개)
s, r, (a, b, c, d), y = base(14)
stone_box(s, r, 5, 8, 5, 5, 1, 4)
jetty_floor(s, 5, 8, 9, 12, 5); box(s, r, 5, 8, 5, 5, 6, 4, infill=INF4, found=False, post_step=4, win_sides=('s', 'e'))
steep_roof(s, r, a, b, c, d, y, axis='x', gable=INF4, timber_gable='dark_oak')
steep_roof(s, r, 5, 9, 4, 12, y, axis='z', gable=INF4, timber_gable='dark_oak', over=1)
C.append((s, '14. [3 변형] L자 날개'))
# 15. 6 변형: 정면 가운데 입구 탑 (사각 → 뾰족)
s, r, (a, b, c, d), y = base(15); steep_roof(s, r, a, b, c, d, y, axis='x', gable=INF4, timber_gable='dark_oak')
stone_box(s, r, 2, 8, 5, 3, 1, 13) if False else None
for yy in range(1, y + 6):
    for x in range(3, 6):
        for z in (8, 9, 10):
            edge = x in (3, 5) or z in (8, 10)
            s.set(x, yy, z, (r.choice(['stone_bricks', 'cobblestone', 'andesite']) if yy < 6 else r.choice(INF4)) if edge else 'air')
s.set(4, 1, 10, 'spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]'); s.set(4, 2, 10, 'spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]')
for yy in (7, 8, 12, 13): s.set(4, yy, 10, 'glass_pane')
for yy in (6, 11):
    for x in range(2, 7):
        for z in (7, 11): s.set(x, yy, z, 'dark_oak_log[axis=x]')
steep_pyramid(s, r, 3, 5, 8, 10, y + 6, over=1)
C.append((s, '15. [6 변형] 정면 입구 탑'))
# 16. 6 변형: 양쪽 원형 탑 2개 (작은 성문집)
s, r, (a, b, c, d), y = base(16); steep_roof(s, r, a, b, c, d, y, axis='x', gable=INF4, timber_gable='dark_oak')
turret(s, r, a - 1, d + 1, 1, 12, rad=2, timber_top=4); turret(s, r, b + 1, d + 1, 1, 12, rad=2, timber_top=4)
C.append((s, '16. [6 변형] 양쪽 원형 탑'))

os.makedirs('cand', exist_ok=True)
items = []
for i, (s, label) in enumerate(C):
    autoconnect(s); p = f'cand/d{i+1}.png'; render(view(s, 0), p, scale=10); items.append((p, label))
print(sheet(items, 'roof_candidates2.png', cols=4))
