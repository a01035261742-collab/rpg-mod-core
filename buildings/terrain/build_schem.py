"""H2/MAT2 → .schem 4조각 (광장 가운데에 서서 //paste 하면 제자리). 식물 없음."""
import numpy as np, gzip, sys, os
sys.path.insert(0, '/home/claude/rpg-mod-core/buildings')
from schem import _tag

H = np.load('H2.npy'); MAT = np.load('MAT2.npy', allow_pickle=True)
D, W = H.shape
OFF, BASE, WATER = 20, 10, 8
PLAZA = (62 + OFF, 92 + OFF)            # 광장 가운데 (배열 칸)
rng = np.random.default_rng(3)
TOP = int(H.max()) + 3

P = ['minecraft:air', 'minecraft:stone', 'minecraft:dirt', 'minecraft:grass_block[snowy=false]', 'minecraft:dirt_path',
     'minecraft:coarse_dirt', 'minecraft:andesite', 'minecraft:gravel', 'minecraft:sand', 'minecraft:water[level=0]',
     'minecraft:polished_andesite', 'minecraft:stone_bricks', 'minecraft:cracked_stone_bricks', 'minecraft:mossy_stone_bricks',
     'minecraft:stone_brick_wall[east=none,north=low,south=low,up=true,waterlogged=false,west=none]', 'minecraft:cobblestone',
     'minecraft:stone_brick_slab[type=bottom,waterlogged=false]']
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
# 바위 비탈: 돌 · 안산암 섞기
rock = (MAT == 'stone') | (MAT == 'andesite')
mix = rng.random((TOP + 1, D, W)) < 0.18
V[rock[None] & (Y <= Hc) & mix & (V == I['stone'])] = I['andesite']
V[rock[None] & (Y <= Hc) & mix & (V == I['andesite']) & (rng.random((TOP + 1, D, W)) < 0.3)] = I['cobblestone']
# 물: 바닥 위 ~ 수면
wm = (MAT == 'water')[None]
V[wm & (Y > Hc) & (Y <= WATER)] = I['water']
# 동쪽 끝 강물: 맵 밖으로 안 흐르게 돌로 막음
edge = (MAT[:, W - 1] == 'water')
V[:WATER + 2, edge, W - 1] = I['stone']

# 폭포: 연못 서쪽 절벽 꼭대기에 물 원본 (양옆·뒤 막고 연못 쪽으로만 흐름)
fz = 135 + OFF
fx = None
for x in range(14 + OFF - 6, 0, -1):
    if H[fz, x] >= BASE + 8: fx = x; break
if fx:
    h = H[fz, fx]
    V[h, fz, fx] = I['water']
    for dz in (-1, 1): V[h:h + 2, fz + dz, fx] = I['stone']
    V[h:h + 2, fz, fx - 1] = I['stone']
    V[h + 1:, fz, fx] = 0

# 다리: 광장 → 아랫마을 길이 강을 건너는 곳 (돌다리, 폭 5, 난간)
def xline(z):   # 설계 좌표 (64,112)→(66,132)
    zd = z - OFF; t = np.clip((zd - 112) / 20, 0, 1); return 64 + 2 * t + OFF
wz = [z for z in range(D) if MAT[z, int(round(xline(z)))] == 'water' and 100 + OFF < z < 140 + OFF]
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
    print('다리', z0 - OFF, z1 - OFF, '길이', z1 - z0 + 1)

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

os.makedirs('out', exist_ok=True)
hx, hz = W // 2, D // 2
for i, (x0, z0, x1, z1, nm) in enumerate([(0, 0, hx, hz, '북서'), (hx, 0, W, hz, '북동'), (0, hz, hx, D, '남서'), (hx, hz, W, D, '남동')]):
    print(f'start_terrain_{i + 1} ({nm})', save(f'out/start_terrain_{i + 1}.schem', x0, z0, x1, z1))
print('폭포', fx and fx - OFF, 'TOP', TOP)
