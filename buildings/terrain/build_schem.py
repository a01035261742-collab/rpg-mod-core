"""H2/MAT2 → .schem 4조각 (광장 가운데에 서서 //paste 하면 제자리). 식물 없음."""
import numpy as np, gzip, sys, os
sys.path.insert(0, '/home/claude/rpg-mod-core/buildings')
from schem import _tag

import json
from scipy.ndimage import distance_transform_edt, gaussian_filter
H = np.load('H2.npy'); MAT = np.load('MAT2v.npy' if os.path.exists('MAT2v.npy') else 'MAT2.npy', allow_pickle=True); WALK = np.load('walk2.npy')
M = json.load(open('meta.json'))
D, W = H.shape
BASE, WATER = 10, 8
PLAZA = tuple(M['plaza'])               # 광장 가운데 (배열 칸)
rng = np.random.default_rng(3)
TOP = int(H.max()) + 3

P = ['minecraft:air', 'minecraft:barrier', 'minecraft:stone', 'minecraft:dirt', 'minecraft:grass_block[snowy=false]', 'minecraft:dirt_path',
     'minecraft:coarse_dirt', 'minecraft:andesite', 'minecraft:gravel', 'minecraft:sand', 'minecraft:water[level=0]',
     'minecraft:polished_andesite', 'minecraft:stone_bricks', 'minecraft:cracked_stone_bricks', 'minecraft:mossy_stone_bricks',
     'minecraft:stone_brick_wall[east=none,north=low,south=low,up=true,waterlogged=false,west=none]', 'minecraft:cobblestone',
     'minecraft:stone_brick_slab[type=bottom,waterlogged=false]', 'minecraft:tuff', 'minecraft:clay']
I = {n.split(':')[1].split('[')[0]: i for i, n in enumerate(P)}
V = np.zeros((TOP + 1, D, W), np.uint8)   # y, z, x
Y = np.arange(TOP + 1)[:, None, None]
Hc = H[None]
surf = {'grass': 'grass_block', 'path': 'dirt_path', 'plaza': 'polished_andesite', 'farm': 'dirt', 'stone': 'stone',
        'andesite': 'andesite', 'coarse': 'coarse_dirt', 'sand': 'sand', 'water': 'gravel'}
sub = {'grass': 'dirt', 'path': 'dirt', 'plaza': 'stone', 'farm': 'dirt', 'stone': 'stone', 'andesite': 'andesite',
       'coarse': 'dirt', 'sand': 'sand', 'water': 'gravel'}
V[(Y < Hc)] = I['stone']
for k in surf:
    m = (MAT == k)[None]
    V[m & (Y < Hc) & (Y >= Hc - 3)] = I[sub[k]]
    V[m & (Y == Hc)] = I[surf[k]]
# 광장: 무늬
pl = MAT == 'plaza'
zz, xx = np.nonzero(pl)
for z, x in zip(zz, xx):
    r = rng.random()
    V[H[z, x], z, x] = I['stone_bricks'] if (x + z) % 4 == 0 else I['cracked_stone_bricks'] if r < 0.08 else I['polished_andesite']
# 바위 비탈: 지층 (점박이 없이 띠·덩어리)
import blockmix
rock = (MAT == 'stone') | (MAT == 'andesite')
blockmix.strata(V, H, rock, I, blockmix.LIGHT, 11)
# 물: 바닥 위 ~ 수면
WLV2 = np.load('WLV2.npy')
wm = (MAT == 'water')
blockmix.lakebed(V, H, wm, I, [('gravel', .45), ('clay', .3), ('sand', .25)], 12)
V[wm[None] & (Y > Hc) & (Y <= WLV2[None])] = I['water']

# 길: 가운데 줄 흙길 + 양옆 거친 흙 · 마을 광장 근처는 돌 포장 (안산암 + 조약돌)
d_v = blockmix.zone_dist((D, W), [PLAZA]) + (blockmix.blob((D, W), 4, 41) - 0.5) * 8
okm = WALK & np.isin(MAT, ['path', 'grass', 'coarse'])
_pal = lambda z, x: ('cobblestone', 'andesite', 'gravel') if d_v[z, x] < 58 else ('dirt_path', 'coarse_dirt', 'gravel')
blockmix.natural_paths(V, H, MAT, I, json.load(open('polys2.json')), _pal, okm)
if os.path.exists('door_polys.json'):   # 건물 문 → 길 오솔길 (좁게)
    blockmix.natural_paths(V, H, MAT, I, json.load(open('door_polys.json')), _pal, okm, seed=52, wscale=0.6, keep_old=True)
if os.path.exists('forest_polys.json'):   # 풀숲 큰길 → 사냥터 공터 오솔길
    blockmix.natural_paths(V, H, MAT, I, json.load(open('forest_polys.json')), _pal, okm, seed=53, wscale=0.75, keep_old=True)
# 동쪽 끝 강물: 맵 밖으로 안 흐르게 돌로 막음
edge = (MAT[:, W - 1] == 'water')
V[:WATER + 2, edge, W - 1] = I['stone']

# 폭포: 개울 맨 위 (start_rooms 에서 물길·둑·뒤 벽 만듦) — 물은 모두 고인 물, 둑보다 1칸 아래
fx = fz = M['fall'] and M['fall'][0]
# 다리: 광장 → 아랫마을 길이 강을 건너는 곳 (돌다리, 폭 5, 난간)
(bx0, bz0), (bx1, bz1) = M['bridge']
def xline(z):
    t = np.clip((z - bz0) / (bz1 - bz0), 0, 1); return bx0 + (bx1 - bx0) * t
wz = [z for z in range(bz0 - 10, bz1 + 30) if MAT[z, int(round(xline(z)))] == 'water']
if wz:
    z0, z1 = min(wz) - 2, max(wz) + 2
    for z in range(z0, z1 + 1):
        cx = int(round(xline(z)))
        for x in range(cx - 2, cx + 3):
            V[BASE, z, x] = I['stone_bricks'] if rng.random() > 0.12 else I['mossy_stone_bricks']
            V[BASE + 1:, z, x] = 0
            if abs(x - cx) == 2: V[BASE + 1, z, x] = I['stone_brick_wall']
            if (z - z0) % 4 == 2 and abs(x - cx) == 2 and MAT[z, x] == 'water':   # 기둥
                V[H[z, x] + 1:BASE, z, x] = I['stone_bricks']
        # 양 끝 경사: 강둑(9) → 다리(10) 는 반 블록
    for z in (z0 - 1, z1 + 1):
        cx = int(round(xline(z)))
        for x in range(cx - 2, cx + 3):
            if H[z, x] < BASE: V[H[z, x] + 1, z, x] = I['stone_brick_slab']
    print('다리 길이', z1 - z0 + 1)

def save(path, x0, z0, x1, z1):
    sub_ = V[:, z0:z1, x0:x1]
    Hh, L, Wd = sub_.shape
    data = sub_.astype(np.uint8).tobytes()          # 팔레트 < 128 → varint = 1바이트
    root = {'Version': (3, 2), 'DataVersion': (3, 3465), 'Width': (2, Wd), 'Height': (2, Hh), 'Length': (2, L),
            'PaletteMax': (3, len(P)), 'Palette': (10, {n: (3, i) for i, n in enumerate(P)}),
            'BlockData': (7, data), 'Offset': (11, [0, 0, 0]),
            'Metadata': (10, {'WEOffsetX': (3, x0 - PLAZA[0]), 'WEOffsetY': (3, 0 - (BASE + 1)), 'WEOffsetZ': (3, z0 - PLAZA[1])})}
    b = bytearray(); _tag(b, 10, 'Schematic', root)
    open(path, 'wb').write(gzip.compress(bytes(b)))
    return Wd, Hh, L, os.path.getsize(path)

# 투명 방벽: 다닐 수 있는 곳(공터 + 공터 안 물)에서 5~7칸 바깥, 땅 위 3칸 (강이 벽을 지나는 곳은 물 위까지 가로막음)
wallz = gaussian_filter((distance_transform_edt(~WALK) >= 4.5).astype(float), 3) > 0.5
allowed = WALK | ((MAT == 'water') & ~wallz)
dA = distance_transform_edt(~allowed)
from scipy.ndimage import maximum_filter
crest = (dA >= maximum_filter(dA, 5) - 0.5) & (dA > 3) & (dA <= 11)   # 얇은 벽: 능선 꼭대기
ring = ((dA > 9) & (dA <= 11)) | crest

zz, xx = np.nonzero(ring)
for z, x in zip(zz, xx):
    h = H[z, x]
    top = max(h, WATER) if MAT[z, x] == 'water' else h
    V[h + 1:top + 5, z, x] = I["barrier"]
print('방벽 칸', len(zz))

import shutil
shutil.rmtree('out', ignore_errors=True)
os.makedirs('out', exist_ok=True)
xs = [0, W // 3, 2 * W // 3, W]; zs = [0, D // 2, D]
names = ['북서', '북중', '북동', '남서', '남중', '남동']
i = 0
for zi in range(2):
    for xi in range(3):
        print(f'start_terrain_{i + 1} ({names[i]})', save(f'out/start_terrain_{i + 1}.schem', xs[xi], zs[zi], xs[xi + 1], zs[zi + 1]))
        i += 1
print('폭포', fx, 'TOP', TOP)
