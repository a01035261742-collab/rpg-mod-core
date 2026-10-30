"""보스 경기장 (따로, TP로 입장): 둥근 평지 + 둘레 바위 그릇 + 투명 방벽. 기준점 = 경기장 가운데 바닥 위."""
import numpy as np, sys, gzip, os
sys.path.insert(0, '/home/claude/rpg-mod-core/buildings')
from schem import _tag
from scipy.ndimage import gaussian_filter
from PIL import Image, ImageDraw, ImageFont

def vnoise(N, scale, seed):
    r = np.random.default_rng(seed); g = r.random((N // scale + 3, N // scale + 3))
    X, Z = np.meshgrid(np.arange(N), np.arange(N)); fx, fz = X / scale, Z / scale
    x0, z0 = fx.astype(int), fz.astype(int); tx, tz = fx - x0, fz - z0
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a, b, c, d = g[z0, x0], g[z0, x0 + 1], g[z0 + 1, x0], g[z0 + 1, x0 + 1]
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz

THEMES = {
    'forest': dict(floor=[('grass_block[snowy=false]', 0.8), ('coarse_dirt', 0.12), ('podzol[snowy=false]', 0.08)], wall=['stone', 'andesite', 'cobblestone'],
                   top='grass_block[snowy=false]', name='숲의 군주', R=36),
    'mush':   dict(floor=[('mycelium[snowy=false]', 0.75), ('podzol[snowy=false]', 0.15), ('coarse_dirt', 0.10)], wall=['deepslate', 'tuff', 'cobbled_deepslate'],
                   top='mycelium[snowy=false]', name='거대버섯 수호자', R=36),
    'queen':  dict(floor=[('mud', 0.5), ('packed_mud', 0.3), ('coarse_dirt', 0.2)], wall=['deepslate', 'blackstone', 'tuff'],
                   top='mud', name='어둠거미여왕 (레이드)', R=28),
}

def build(key, out):
    t = THEMES[key]; R = t['R']; FLAT = R + 4; RIM = 12; N = 2 * (FLAT + RIM + 4) + 1
    c = N // 2
    X, Z = np.meshgrid(np.arange(N), np.arange(N))
    rr = np.sqrt((X - c) ** 2 + (Z - c) ** 2)
    edge = (vnoise(N, 10, 5) - 0.5) * 6 + (vnoise(N, 4, 6) - 0.5) * 2
    BASE = 6
    rise = np.clip(rr + edge - FLAT, 0, None)
    H = BASE + np.minimum(16, rise * 1.2 + (vnoise(N, 8, 7) - 0.5) * 4 * (rise > 3))
    H = gaussian_filter(H, 0.8)
    H = np.where(rr < FLAT, BASE, np.round(H)).astype(int)
    TOP = int(H.max()) + 6
    pal = ['minecraft:air', 'minecraft:barrier', 'minecraft:stone', 'minecraft:dirt', 'minecraft:polished_andesite', 'minecraft:stone_bricks']
    for n, _ in t['floor']: pal.append('minecraft:' + n)
    for n in t['wall']: pal.append('minecraft:' + n)
    pal.append('minecraft:' + t['top'])
    pal = list(dict.fromkeys(pal)); I = {n: i for i, n in enumerate(pal)}
    V = np.zeros((TOP + 1, N, N), np.uint8)
    rng = np.random.default_rng(11)
    Y = np.arange(TOP + 1)[:, None, None]
    V[Y <= H[None]] = I['minecraft:stone']
    V[(Y < H[None]) & (Y >= H[None] - 2)] = I['minecraft:dirt']
    # 바닥
    floor = rr < FLAT
    probs = np.cumsum([p for _, p in t['floor']]); rv = rng.random((N, N))
    for z, x in zip(*np.nonzero(floor)):
        k = int(np.searchsorted(probs, rv[z, x])); V[H[z, x], z, x] = I['minecraft:' + t['floor'][min(k, len(t['floor']) - 1)][0]]
    # 벽 (비탈 = 바위, 꼭대기 평평 = 풀/균사체)
    gx, gz = np.gradient(H.astype(float)); slope = np.sqrt(gx ** 2 + gz ** 2)
    for z, x in zip(*np.nonzero(~floor)):
        h = H[z, x]
        if slope[z, x] >= 0.9: V[max(0, h - 3):h + 1, z, x] = I['minecraft:' + t['wall'][rng.integers(len(t['wall']))]]
        else: V[h, z, x] = I['minecraft:' + t['top']]
    # 경기장 원 표시 (반지름 R, 돌 테두리 한 줄) + 가운데 표시 + 들어오는 자리 (남쪽 끝 돌 단상)
    ring = (np.abs(rr - R) < 0.5) & floor
    V[H[ring] if False else BASE, ring] = I['minecraft:polished_andesite']
    V[BASE, c, c] = I['minecraft:stone_bricks']
    arr = (np.abs(X - c) <= 2) & (np.abs(Z - (c + R - 3)) <= 2)
    V[BASE, arr] = I['minecraft:stone_bricks']
    # 투명 방벽: 평지 바깥 2칸 고리, 땅 위 4칸
    bar = (rr >= FLAT + 1) & (rr < FLAT + 2.6)
    for z, x in zip(*np.nonzero(bar)): V[H[z, x] + 1:H[z, x] + 5, z, x] = I['minecraft:barrier']
    # 저장 (기준점 = 가운데 바닥 위 발 위치)
    Hh, L, Wd = V.shape
    root = {'Version': (3, 2), 'DataVersion': (3, 3465), 'Width': (2, Wd), 'Height': (2, Hh), 'Length': (2, L),
            'PaletteMax': (3, len(pal)), 'Palette': (10, {n: (3, i) for i, n in enumerate(pal)}),
            'BlockData': (7, V.astype(np.uint8).tobytes()), 'Offset': (11, [0, 0, 0]),
            'Metadata': (10, {'WEOffsetX': (3, -c), 'WEOffsetY': (3, -(BASE + 1)), 'WEOffsetZ': (3, -c)})}
    b = bytearray(); _tag(b, 10, 'Schematic', root)
    open(out, 'wb').write(gzip.compress(bytes(b)))
    print(key, N, Hh, os.path.getsize(out), '들어오는 자리 = 가운데에서 남쪽', R - 3, '칸')
    return H, V, pal, c, R, FLAT

if __name__ == '__main__':
    os.makedirs('arena_out', exist_ok=True)
    F = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
    imgs = []
    for key in ('forest', 'mush', 'queen'):
        H, V, pal, c, R, FLAT = build(key, f'arena_out/arena_{key}.schem')
        # 3D 미리보기
        N = H.shape[0]; s = 4.0; k = 3.0; base = H.min() - 2
        img = Image.new('RGB', (int(2 * N * s) + 40, int(N * s + (H.max() - base) * k) + 120), (150, 190, 230)); d = ImageDraw.Draw(img)
        ox = N * s + 20; oy = 70
        def P(x, z, h): return (ox + (x - z) * s, oy + (x + z) * s * 0.5 - (h - base) * k + (H.max() - base) * k)
        def colof(name):
            for kk, cc in [('barrier', None), ('grass', (98, 156, 64)), ('mycelium', (118, 100, 120)), ('podzol', (112, 82, 40)), ('coarse', (120, 92, 66)),
                           ('packed_mud', (140, 105, 80)), ('mud', (60, 56, 60)), ('deepslate', (66, 66, 72)), ('tuff', (100, 104, 92)), ('blackstone', (40, 36, 44)),
                           ('andesite', (146, 146, 142)), ('cobble', (120, 120, 120)), ('stone_brick', (130, 130, 130)), ('stone', (122, 122, 126)), ('dirt', (120, 90, 60))]:
                if kk in name: return cc
            return (200, 0, 200)
        for sm in range(2 * N - 1):
            for x in range(max(0, sm - N + 1), min(N, sm + 1)):
                z = sm - x; h = H[z, x]
                col = colof(pal[V[h, z, x]]) or (122, 122, 126)
                hs = H[z + 1, x] if z + 1 < N else base; he = H[z, x + 1] if x + 1 < N else base
                if h > hs: d.polygon([P(x, z + 1, h + 1), P(x + 1, z + 1, h + 1), P(x + 1, z + 1, hs + 1), P(x, z + 1, hs + 1)], fill=tuple(int(v * 0.62) for v in col))
                if h > he: d.polygon([P(x + 1, z, h + 1), P(x + 1, z + 1, h + 1), P(x + 1, z + 1, he + 1), P(x + 1, z, he + 1)], fill=tuple(int(v * 0.78) for v in col))
                d.polygon([P(x, z, h + 1), P(x + 1, z, h + 1), P(x + 1, z + 1, h + 1), P(x, z + 1, h + 1)], fill=col)
        d.text((14, 10), f'{THEMES[key]["name"]} 경기장 — 반지름 {R} (+4 여유) · {N}×{N}칸', font=ImageFont.truetype(F, 22, index=1), fill=(20, 30, 50))
        imgs.append(img)
    Wt = sum(i.width for i in imgs); Ht = max(i.height for i in imgs)
    sheet = Image.new('RGB', (Wt, Ht), (150, 190, 230)); x = 0
    for i in imgs: sheet.paste(i, (x, 0)); x += i.width
    sheet.save('arenas.png')
