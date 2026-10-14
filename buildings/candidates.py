"""지붕·형태 후보 8종 (같은 크기 9x8 기준 가게 건물)"""
import os, random
from schem import S, autoconnect, render, view
from lib4 import *
from sheet import sheet

C = []
def base(seed, upper=True, jetty=1, ground=4, upper_h=4):
    s = S('cand'); r = random.Random(seed); s.r = r
    x0, z0, w, d = 0, 0, 9, 8
    stone_box(s, r, x0, z0, w, d, 1, ground)
    for x in (2, 6): deep_window(s, x, 2, 7, 'south')
    door(s, 4, 1, 7); s.set(3, 3, 8, 'stone_brick_stairs[facing=east,half=top,shape=straight]'); s.set(5, 3, 8, 'stone_brick_stairs[facing=west,half=top,shape=straight]')
    ux0, ux1, uz0, uz1 = -jetty, 8 + jetty, -jetty, 7 + jetty
    if upper:
        jetty_floor(s, ux0, uz0, ux1, uz1, ground + 1)
        box(s, r, ux0, uz0, ux1 - ux0 + 1, uz1 - uz0 + 1, ground + 2, upper_h, infill=INF4, found=False, post_step=4, win_sides=('s', 'n', 'e', 'w'))
        return s, r, (ux0, ux1, uz0, uz1), ground + 2 + upper_h
    return s, r, (0, 8, 0, 7), ground + 1

# 1. 정면 박공 (지금 방식)
s, r, (a, b, c, d), y = base(1); steep_roof(s, r, a, b, c, d, y, axis='z', gable=INF4, timber_gable='dark_oak'); C.append((s, '1. 정면 박공 (지금)'))
# 2. 모임지붕 (네 면 경사, 가파름)
s, r, (a, b, c, d), y = base(2); hip_roof(s, r, a, b, c, d, y); C.append((s, '2. 모임지붕 (네 면)'))
# 3. 십자 박공 (옆 박공 + 정면 작은 박공)
s, r, (a, b, c, d), y = base(3); steep_roof(s, r, a, b, c, d, y, axis='x', gable=INF4, timber_gable='dark_oak')
steep_roof(s, r, 3, 5, c + 4, d, y, axis='z', gable=INF4, timber_gable='dark_oak', over=1); C.append((s, '3. 십자 박공 (T자)'))
# 4. 꺾인 지붕 (헛간형, 갈색)
s, r, (a, b, c, d), y = base(4, upper=False, ground=5); gambrel_x(s, r, a, b, c, d, y, lower=2); C.append((s, '4. 꺾인 지붕 (네덜란드풍)'))
# 5. 한쪽 긴 지붕 (솔트박스)
s, r, (a, b, c, d), y = base(5); saltbox_z(s, r, a, b, c, d, y); C.append((s, '5. 한쪽 긴 지붕'))
# 6. 모서리 원형 소탑 + 옆 박공
s, r, (a, b, c, d), y = base(6); steep_roof(s, r, a, b, c, d, y, axis='x', gable=INF4, timber_gable='dark_oak')
turret(s, r, b + 1, d + 1, 1, 11, rad=2, timber_top=4); C.append((s, '6. 모서리 원형 탑'))
# 7. 3층 좁고 높은 집 (층마다 돌출) + 모임지붕
s, r, (a, b, c, d), y = base(7)
jetty_floor(s, a - 1, c - 1, b + 1, d + 1, y)
box(s, r, a - 1, c - 1, b - a + 3, d - c + 3, y + 1, 4, infill=INF4, found=False, post_step=4)
hip_roof(s, r, a - 1, b + 1, c - 1, d + 1, y + 5); C.append((s, '7. 3층 돌출 + 모임지붕'))
# 8. 낮은 돌집 + 완만한 지붕 + 앞 차양 (높이 변화용)
s, r, (a, b, c, d), y = base(8, upper=False, ground=4)
roof(s, r, a, b, c, d, y, mats=('spruce',), eave='dark_oak', gable_fill=INF4)
for x in range(0, 9):
    for z in (8, 9): s.set(x, 4 if z == 8 else 3, z, 'spruce_stairs[facing=north,half=bottom,shape=straight]')
for x in (0, 8): s.set(x, 1, 9, 'spruce_fence'); s.set(x, 2, 9, 'spruce_fence')
C.append((s, '8. 낮은 돌집 + 완만 + 차양'))

os.makedirs('cand', exist_ok=True)
items = []
for i, (s, label) in enumerate(C):
    autoconnect(s); p = f'cand/c{i+1}.png'; render(view(s, 0), p, scale=10); items.append((p, label))
print(sheet(items, 'roof_candidates.png', cols=4))
