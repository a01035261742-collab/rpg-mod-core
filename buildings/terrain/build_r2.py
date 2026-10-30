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
     'minecraft:stone_bricks', 'minecraft:cobblestone', 'minecraft:cobbled_deepslate', 'minecraft:packed_mud', 'minecraft:gravel', 'minecraft:sand', 'minecraft:smooth_basalt', 'minecraft:clay', 'minecraft:cracked_stone_bricks', 'minecraft:deepslate_bricks', 'minecraft:cracked_deepslate_bricks', 'minecraft:mossy_stone_bricks']
import arena_deco, blockmix
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
import blockmix
blockmix.strata(V, H, np.isin(MAT, ['stone', 'andesite']), I, blockmix.LIGHT, 21)
blockmix.strata(V, H, np.isin(MAT, ['tuff', 'deepslate']), I, blockmix.DARK, 22)
blockmix.lakebed(V, H, MAT == 'water', I, [('gravel', .4), ('clay', .35), ('sand', .25)], 23)
wm = np.isin(MAT, ['water', 'bog'])
V[wm[None] & (Y > Hc) & (Y <= WLV[None])] = I['water']


# 길: 가운데 줄 + 양옆 줄 (구역별) · 마을 근처는 돌 포장 · 경기장 입구 앞은 옛 벽돌 포장
ZN = np.load('R2_zone.npy'); MM0 = json.load(open('R2_meta.json'))
PLZ = [(int(round((112 + 12) * 1.5)), int(round((152 + 16) * 1.5)))]
d_v = blockmix.zone_dist((D, W), PLZ) + (blockmix.blob((D, W), 4, 41) - 0.5) * 8
d_a = {k: blockmix.zone_dist((D, W), [tuple(v)]) for k, v in MM0['arenas'].items()}
WILD = {'grass': ('dirt_path', 'coarse_dirt', 'gravel'), 'arena1': ('dirt_path', 'coarse_dirt', 'gravel'), 'g5': ('dirt_path', 'coarse_dirt', 'gravel'),
        'g6': ('coarse_dirt', 'podzol', 'packed_mud'), 'g7': ('coarse_dirt', 'podzol', 'packed_mud'), 'arena2': ('coarse_dirt', 'podzol', 'packed_mud'),
        'g8': ('packed_mud', 'mud', 'gravel')}
def pick(z, x):
    if d_a['boss1'][z, x] < 54: return ('stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks')
    if d_a['boss2'][z, x] < 54: return ('deepslate_bricks', 'cracked_deepslate_bricks', 'cobbled_deepslate')
    if d_v[z, x] < 62: return ('cobblestone', 'andesite', 'gravel')
    return WILD.get(ZN[z, x], WILD['grass'])
okm = WALK & np.isin(MAT, ['path', 'grass', 'podzol', 'mycelium', 'coarse', 'mud', 'deepdirt'])
print('길', blockmix.natural_paths(V, H, MAT, I, json.load(open('R2_polys.json')), pick, okm))
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
