"""두 번째 지역 → schem 6조각. 시작 마을 광장 가운데에 서서 붙이면 시작 마을 북쪽 출구에 딱 이어짐."""
import numpy as np, gzip, sys, os, json, shutil
sys.path.insert(0, '/home/claude/rpg-mod-core/buildings')
from schem import _tag
from scipy.ndimage import distance_transform_edt, maximum_filter

H = np.load('R2_H.npy'); MAT = np.load('R2_MAT.npy', allow_pickle=True); WALK = np.load('R2_walk.npy'); WLV = np.load('R2_WLV.npy')
D, W = H.shape
# 시작 마을과 맞추기: 시작 맵 맨 위 줄 출구 (x, 바닥) ↔ 이 맵 맨 아래 줄 입구 (x, 바닥)
SH = np.load('H2.npy'); SW = np.load('walk2.npy'); SM = json.load(open('meta.json'))
sx = int(np.nonzero(SW[0])[0].mean()); sfloor = int(SH[0, SW[0]].max())
ex = int(np.nonzero(WALK[D - 1])[0].mean()); efloor = int(H[D - 1, WALK[D - 1]].max())
px, pz = SM['plaza']; PFEET = 11                      # 시작 광장 바닥 10 → 발 11
OX = (sx - px) - ex                                   # 이 맵 x=0 이 광장 기준 어디
OZ = (-1 - pz) - (D - 1)                              # 이 맵 맨 아래 줄 = 시작 맵 맨 위 줄 바로 북쪽
OY = (sfloor - PFEET) - efloor                        # 입구 바닥 = 출구 바닥
print('출구 x', sx, '바닥', sfloor, '/ 입구 x', ex, '바닥', efloor, '→ 기준 OX OY OZ', OX, OY, OZ)

TOP = int(max(H.max(), WLV.max())) + 6
P = ['minecraft:air', 'minecraft:barrier', 'minecraft:stone', 'minecraft:dirt', 'minecraft:grass_block[snowy=false]', 'minecraft:dirt_path',
     'minecraft:coarse_dirt', 'minecraft:andesite', 'minecraft:podzol[snowy=false]', 'minecraft:mycelium[snowy=false]', 'minecraft:mud',
     'minecraft:rooted_dirt', 'minecraft:tuff', 'minecraft:deepslate[axis=y]', 'minecraft:water[level=0]', 'minecraft:polished_andesite',
     'minecraft:stone_bricks', 'minecraft:cobblestone', 'minecraft:cobbled_deepslate', 'minecraft:packed_mud', 'minecraft:gravel', 'minecraft:sand']
import arena_deco
P += [n for n in arena_deco.blocks() if n not in P]
I = {n.split(':')[1].split('[')[0]: i for i, n in enumerate(P)}
IF = {n: i for i, n in enumerate(P)}
surf = {'grass': 'grass_block', 'path': 'dirt_path', 'plaza': 'polished_andesite', 'stone': 'stone', 'andesite': 'andesite', 'coarse': 'coarse_dirt',
        'podzol': 'podzol', 'mycelium': 'mycelium', 'mud': 'mud', 'deepdirt': 'rooted_dirt', 'tuff': 'tuff', 'deepslate': 'deepslate',
        'water': 'dirt', 'bog': 'mud', 'ring': 'polished_andesite', 'sand': 'sand', 'gravel': 'gravel'}
sub = {'grass': 'dirt', 'path': 'dirt', 'plaza': 'stone', 'stone': 'stone', 'andesite': 'andesite', 'coarse': 'dirt', 'podzol': 'dirt',
       'mycelium': 'dirt', 'mud': 'packed_mud', 'deepdirt': 'dirt', 'tuff': 'tuff', 'deepslate': 'deepslate', 'water': 'dirt', 'bog': 'packed_mud', 'ring': 'stone', 'sand': 'sand', 'gravel': 'gravel'}
rng = np.random.default_rng(3)
V = np.zeros((TOP + 1, D, W), np.uint8)
Y = np.arange(TOP + 1)[:, None, None]; Hc = H[None]
V[Y < Hc] = I['stone']
for k in surf:
    m = (MAT == k)[None]
    V[m & (Y < Hc) & (Y >= Hc - 3)] = I[sub[k]]
    V[m & (Y == Hc)] = I[surf[k]]
mix = rng.random((TOP + 1, D, W)) < 0.15
V[np.isin(MAT, ['stone', 'andesite'])[None] & (Y <= Hc) & mix] = I['cobblestone']
V[np.isin(MAT, ['tuff', 'deepslate'])[None] & (Y <= Hc) & mix] = I['cobbled_deepslate']
wm = np.isin(MAT, ['water', 'bog'])
V[wm[None] & (Y > Hc) & (Y <= WLV[None])] = I['water']


# 길 자연스럽게: 가운데는 흙길 위주 + 거친 흙·자갈 섞임, 가장자리는 흙길·거친 흙·풀이 들쭉날쭉
from scipy.ndimage import distance_transform_edt as _edt
_pm = (MAT == 'path')
_dp = _edt(_pm)
_r = np.random.default_rng(77).random(_pm.shape)
_core = _pm & (_dp > 1.5); _edge = _pm & (_dp <= 1.5)
for z, x in zip(*np.nonzero(_core)):
    V[H[z, x], z, x] = I['dirt_path'] if _r[z, x] < 0.78 else I['coarse_dirt'] if _r[z, x] < 0.92 else I['gravel']
for z, x in zip(*np.nonzero(_edge)):
    V[H[z, x], z, x] = I['dirt_path'] if _r[z, x] < 0.45 else I['coarse_dirt'] if _r[z, x] < 0.7 else I['grass_block']
MM = json.load(open('R2_meta.json'))
arena_deco.deco(V, H, IF, MAT == 'path', {k: tuple(v) for k, v in MM['arenas'].items()}, MM['arena_r'])
# 투명 방벽 (시작 마을과 같은 방식: 공터에서 9~11칸 바깥 + 얇은 벽은 능선, 땅 위 4칸)
allowed = WALK | wm
dA = distance_transform_edt(~allowed)
crest = (dA >= maximum_filter(dA, 5) - 0.5) & (dA > 3) & (dA <= 11)
ring = (((dA > 9) & (dA <= 11)) | crest) & ~np.load('R2_pass.npy')   # 통로 위엔 안 세움
ring[:, [0, -1]] |= (dA[:, [0, -1]] <= 11)   # 맵 좌우 끝 (경기장 길이 맵 끝에 붙은 곳) 막음
for z, x in zip(*np.nonzero(ring)): V[H[z, x] + 1:H[z, x] + 5, z, x] = I['barrier']
print('방벽 칸', int(ring.sum()))

def save(path, x0, z0, x1, z1):
    s = V[:, z0:z1, x0:x1]; Hh, L, Wd = s.shape
    root = {'Version': (3, 2), 'DataVersion': (3, 3465), 'Width': (2, Wd), 'Height': (2, Hh), 'Length': (2, L),
            'PaletteMax': (3, len(P)), 'Palette': (10, {n: (3, i) for i, n in enumerate(P)}),
            'BlockData': (7, s.astype(np.uint8).tobytes()), 'Offset': (11, [0, 0, 0]),
            'Metadata': (10, {'WEOffsetX': (3, OX + x0), 'WEOffsetY': (3, OY), 'WEOffsetZ': (3, OZ + z0)})}
    b = bytearray(); _tag(b, 10, 'Schematic', root)
    open(path, 'wb').write(gzip.compress(bytes(b)))
    return Wd, Hh, L, os.path.getsize(path)

shutil.rmtree('r2_out', ignore_errors=True); os.makedirs('r2_out')
xs = [0, W // 3, 2 * W // 3, W]; zs = [0, D // 2, D]; i = 0
for zi in range(2):
    for xi in range(3):
        i += 1
        print(f'region2_{i}', save(f'r2_out/region2_{i}.schem', xs[xi], zs[zi], xs[xi + 1], zs[zi + 1]))
