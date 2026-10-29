"""필드 보스 경기장 안 꾸미기 (식물 없음). 바닥은 평평 그대로 = 싸움 방해 없음.
- 바닥: 땅 얼룩 + 가운데 옛 의식 원판(바닥과 같은 높이) + 방사선 돌길(군데군데 끊김)
- 둘레(전투 원 바깥, 실제 36~40칸): 부서진 기둥 + 쓰러진 기둥 + 기둥 위 랜턴
- 입구: 길이 끝나는 곳에 돌 아치 문"""
import numpy as np

THEME = {
    'boss1': dict(floor=[('grass_block[snowy=false]', .70), ('coarse_dirt', .15), ('podzol[snowy=false]', .10), ('mossy_cobblestone', .05)],
                  disc=['stone_bricks', 'mossy_stone_bricks', 'cracked_stone_bricks'], rune='chiseled_stone_bricks', spoke=['stone_bricks', 'mossy_stone_bricks', 'cobblestone'],
                  pillar=['mossy_stone_bricks', 'stone_bricks', 'cracked_stone_bricks', 'mossy_cobblestone'], ring='polished_andesite', mid='polished_andesite'),
    'boss2': dict(floor=[('mycelium[snowy=false]', .65), ('podzol[snowy=false]', .15), ('coarse_dirt', .12), ('tuff', .08)],
                  disc=['deepslate_tiles', 'cracked_deepslate_tiles', 'polished_deepslate'], rune='chiseled_deepslate', spoke=['deepslate_bricks', 'cracked_deepslate_bricks', 'cobbled_deepslate'],
                  pillar=['deepslate_bricks', 'cracked_deepslate_bricks', 'cobbled_deepslate', 'polished_tuff'], ring='polished_deepslate', mid='polished_basalt[axis=y]'),
}
EXTRA = ['minecraft:lantern[hanging=false,waterlogged=false]']

def blocks():
    out = set(EXTRA)
    for t in THEME.values():
        for n, _ in t['floor']: out.add('minecraft:' + n)
        for k in ('disc', 'spoke', 'pillar'): out |= {'minecraft:' + n for n in t[k]}
        for k in ('rune', 'ring', 'mid'): out.add('minecraft:' + t[k])
    return sorted(out)

def deco(V, H, I, road_mask, arenas, radii, seed=5):
    """I = 블록이름(minecraft: 포함) → 팔레트 번호"""
    D, W = H.shape; X, Z = np.meshgrid(np.arange(W), np.arange(D))
    for nm, (cx, cz) in arenas.items():
        t = THEME[nm]; R = radii[nm]; rng = np.random.default_rng(seed + len(nm) * 7 + cx)
        g = lambda n: I['minecraft:' + n]
        dx, dz = X - cx, Z - cz; rr = np.hypot(dx, dz); ang = np.arctan2(dz, dx)
        h = int(H[cz, cx])
        # 길이 들어오는 방향 (경기장 바로 밖 길 칸들의 평균 방향)
        near = road_mask & (rr > R) & (rr < R + 10)
        ea = float(np.arctan2(dz[near].mean(), dx[near].mean())) if near.any() else np.pi / 2
        def adiff(a, b): return np.abs((a - b + np.pi) % (2 * np.pi) - np.pi)
        inside = rr < R - 0.5
        # 1) 바닥 얼룩 (노이즈 덩어리)
        from scipy.ndimage import gaussian_filter
        n = gaussian_filter(rng.random((D, W)), 3); n = (n - n.min()) / (np.ptp(n) + 1e-9)
        cum = np.cumsum([p for _, p in t['floor']]); cum /= cum[-1]
        rv = (n * 0.75 + rng.random((D, W)) * 0.25)
        for z, x in zip(*np.nonzero(inside)):
            k = int(np.searchsorted(cum, rv[z, x])); V[h, z, x] = g(t['floor'][min(k, len(cum) - 1)][0])
        # 2) 방사선 돌길 8줄 (r 8~34, 군데군데 끊김)
        for i in range(8):
            a = i * np.pi / 4 + np.pi / 8
            line = (adiff(ang, a) * rr < 1.0) & (rr > 8) & (rr < 34)
            for z, x in zip(*np.nonzero(line)):
                if rng.random() < 0.8: V[h, z, x] = g(t['spoke'][rng.integers(len(t['spoke']))])
        # 3) 가운데 의식 원판 r7 + 룬 고리 r5 + 십자
        disc = rr < 7.5
        for z, x in zip(*np.nonzero(disc)): V[h, z, x] = g(t['disc'][rng.choice(3, p=[.6, .25, .15])])
        V[h, (np.abs(rr - 5) < 0.55)] = g(t['rune'])
        V[h, (np.abs(rr - 7.5) < 0.55)] = g(t['ring'])
        V[h, disc & ((np.abs(dx) < 0.6) | (np.abs(dz) < 0.6)) & (rr < 4.5)] = g(t['mid'])
        V[h, cz, cx] = g(t['rune'])
        # 4) 전투 원 표시 (r 36) — 원래 링 위치 그대로, 테마 돌
        V[h, (np.abs(rr - 36) < 0.5) & inside] = g(t['ring'])
        # 5) 부서진 기둥 10개 (r 38.5, 입구 방향 ±25° 비움) + 쓰러진 기둥
        n_p = 10
        for i in range(n_p):
            a = ea + np.pi / n_p + i * 2 * np.pi / n_p
            if adiff(a, ea) < np.radians(25): continue
            px, pz = int(round(cx + 38.5 * np.cos(a))), int(round(cz + 38.5 * np.sin(a)))
            tall = int(rng.choice([2, 3, 5, 6, 7]))
            fp = [(px + ox, pz + oz) for ox in (0, 1) for oz in (0, 1)]
            for j, (x, z) in enumerate(fp):
                hh = tall - (rng.integers(0, 3) if tall > 3 else 0)   # 윗부분 깨진 느낌
                for y in range(h + 1, h + 1 + hh): V[y, z, x] = g(t['pillar'][rng.integers(len(t['pillar']))])
            if tall >= 6: V[h + 1 + tall, pz, px] = I[EXTRA[0]]   # 기둥 위 랜턴
            if tall == 2 and rng.random() < 0.9:   # 짧은 기둥 옆엔 쓰러진 토막 (바깥쪽 따라 눕힘)
                ta = a + np.pi / 2
                for s in range(2, 6):
                    x, z = int(round(px + s * np.cos(ta))), int(round(pz + s * np.sin(ta)))
                    if 37 < np.hypot(x - cx, z - cz) < R - 0.5: V[h + 1, z, x] = g(t['pillar'][rng.integers(len(t['pillar']))])
        # 6) 입구 아치: 길 양쪽 기둥(높이 7) + 위 가로대 (통로 높이 6칸)
        ux, uz = np.cos(ea), np.sin(ea); vx, vz = -uz, ux
        bx, bz = cx + (R - 1.5) * ux, cz + (R - 1.5) * uz
        for side in (-1, 1):
            for w in (0, 1):
                x, z = int(round(bx + side * (5 + w) * vx)), int(round(bz + side * (5 + w) * vz))
                for y in range(h + 1, h + 8): V[y, z, x] = g(t['pillar'][1])
            x, z = int(round(bx + side * 5 * vx)), int(round(bz + side * 5 * vz))
            V[h + 8, z, x] = I[EXTRA[0]]
        for s in np.linspace(-6, 6, 25):
            x, z = int(round(bx + s * vx)), int(round(bz + s * vz))
            V[h + 7, z, x] = g(t['pillar'][1])
