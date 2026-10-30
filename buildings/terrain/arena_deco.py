"""필드 보스 경기장 안 꾸미기 (식물 없음). 바닥은 평평 그대로 = 싸움 방해 없음.
- 바닥: 땅 얼룩 + 가운데 옛 의식 원판(바닥과 같은 높이) + 방사선 돌길(군데군데 끊김)
- 둘레(전투 원 바깥, 실제 36~40칸): 부서진 기둥 + 쓰러진 기둥 + 기둥 위 랜턴
- 입구: 길이 끝나는 곳에 돌 아치 문"""
import numpy as np

THEME = {
    'boss1': dict(floor=[('stone_bricks', .48), ('cracked_stone_bricks', .18), ('mossy_stone_bricks', .14), ('cobblestone', .10), ('coarse_dirt', .06), ('gravel', .04)],
                  disc=['smooth_stone', 'polished_andesite', 'cracked_stone_bricks'], rune='chiseled_stone_bricks', spoke=['polished_andesite', 'andesite', 'polished_andesite'],
                  pillar=['mossy_stone_bricks', 'stone_bricks', 'cracked_stone_bricks', 'mossy_cobblestone'], ring='polished_andesite', mid='chiseled_stone_bricks', crack=['cracked_stone_bricks', 'gravel', 'cobblestone'],
                  slab='stone_brick_slab[type=bottom,waterlogged=false]', wall='stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]',
                  lamp='lantern', worn=['cobblestone', 'mossy_cobblestone', 'coarse_dirt', 'gravel'], patch=None),
    'boss2': dict(floor=[('deepslate_tiles', .40), ('cracked_deepslate_tiles', .14), ('tuff', .20), ('mud_bricks', .16), ('cobbled_deepslate', .10)],
                  disc=['polished_deepslate', 'polished_basalt[axis=y]', 'cracked_deepslate_tiles'], rune='chiseled_deepslate', spoke=['calcite', 'polished_basalt[axis=y]', 'calcite'],
                  pillar=['deepslate_bricks', 'cracked_deepslate_bricks', 'cobbled_deepslate', 'polished_deepslate'], ring='calcite', mid='chiseled_deepslate', crack=['cracked_deepslate_tiles', 'cobbled_deepslate', 'tuff'],
                  slab='deepslate_brick_slab[type=bottom,waterlogged=false]', wall='deepslate_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]',
                  lamp='soul_lantern', worn=['cobbled_deepslate', 'tuff', 'mud_bricks', 'cracked_deepslate_bricks'], patch='sculk'),
}
EXTRA = ['minecraft:chain[axis=y,waterlogged=false]']

def blocks():
    out = set(EXTRA)
    for t in THEME.values():
        for n, _ in t['floor']: out.add('minecraft:' + n)
        for k in ('disc', 'spoke', 'pillar'): out |= {'minecraft:' + n for n in t[k]}
        for k in ('rune', 'ring', 'mid', 'slab', 'wall'): out.add('minecraft:' + t[k])
        out |= {'minecraft:' + n for n in t['crack'] + t['worn']}
        if t['patch']: out.add('minecraft:' + t['patch'])
        for hg in ('true', 'false'): out.add(f"minecraft:{t['lamp']}[hanging={hg},waterlogged=false]")
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
            if rng.random() < 0.12 * np.clip((rr[z, x] - 28) / 8, 0, 1): V[h, z, x] = g(t['worn'][rng.integers(len(t['worn']))])
        # 1-2) 금 간 줄 (구불구불 갈라짐) 14개
        for _ in range(6):
            a0 = rng.uniform(0, 2 * np.pi); r0 = rng.uniform(10, 32); x, z = cx + r0 * np.cos(a0), cz + r0 * np.sin(a0); dirn = rng.uniform(0, 2 * np.pi)
            for _s in range(int(rng.integers(5, 12))):
                xi, zi = int(round(x)), int(round(z))
                if 8 < np.hypot(xi - cx, zi - cz) < 35: V[h, zi, xi] = g(t['crack'][rng.integers(len(t['crack']))])
                dirn += rng.normal(0, 0.6); x += np.cos(dirn); z += np.sin(dirn)
        # 2) 방사선 돌길 8줄 (r 8~34, 군데군데 끊김)
        for i in range(8):
            a = i * np.pi / 4 + np.pi / 8
            line = (adiff(ang, a) * rr < 1.0) & (rr > 8) & (rr < 34)
            for z, x in zip(*np.nonzero(line)):
                if rng.random() < 0.8: V[h, z, x] = g(t['spoke'][rng.integers(len(t['spoke']))])
            ex, ez = int(round(cx + 34 * np.cos(a))), int(round(cz + 34 * np.sin(a)))
            V[h, ez - 1:ez + 1, ex - 1:ex + 1] = g(t['rune'])          # 돌길 끝 표식 2×2
        for i in range(4):                                               # 원판 바깥 네 방향 룬 (+ 모양)
            a = i * np.pi / 2; mx, mz = int(round(cx + 12 * np.cos(a))), int(round(cz + 12 * np.sin(a)))
            for ox, oz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)): V[h, mz + oz, mx + ox] = g(t['rune'] if (ox, oz) == (0, 0) else t['mid'])
        # 3) 가운데 의식 원판 r7 + 룬 고리 r5 + 십자
        disc = rr < 7.5
        for z, x in zip(*np.nonzero(disc)): V[h, z, x] = g(t['disc'][rng.choice(3, p=[.6, .25, .15])])
        V[h, (np.abs(rr - 5) < 0.55) & (np.sin(ang * 12) > -0.3)] = g(t['rune'])   # 룬 고리 (점선)
        V[h, (np.abs(rr - 2.5) < 0.5)] = g(t['ring'])
        V[h, (np.abs(rr - 7.5) < 0.55)] = g(t['ring'])
        V[h, disc & ((np.abs(dx) < 0.6) | (np.abs(dz) < 0.6)) & (rr < 4.5)] = g(t['mid'])
        V[h, cz, cx] = g(t['rune'])
        # 4) 전투 원 표시 (r 36) — 원래 링 위치 그대로, 테마 돌
        V[h, (np.abs(rr - 36) < 0.5) & inside] = g(t['ring'])
        # 4-2) 가장자리 담 (r 39.3~40.5, 높이 2로 이어짐) — 무너진 틈 4~5곳만, 입구 비움
        gaps = []
        while len(gaps) < int(rng.integers(4, 6)):
            ga = rng.uniform(0, 2 * np.pi)
            if adiff(ga, ea) > np.radians(40) and all(adiff(ga, o) > np.radians(45) for o, _ in gaps): gaps.append((ga, rng.uniform(3, 4.5) / 40))
        wallr = (rr >= 39.3) & (rr < R - 0.4) & (adiff(ang, ea) > np.radians(9))
        for z, x in zip(*np.nonzero(wallr)):
            hh = 2
            for ga, gw in gaps:
                dd = adiff(ang[z, x], ga)
                if dd < gw * 0.55: hh = 0
                elif dd < gw: hh = min(hh, 1)
            for y in range(h + 1, h + 1 + hh): V[y, z, x] = g(t['pillar'][rng.integers(len(t['pillar']))])
            if hh == 1: V[h + 2, z, x] = g(t['slab'])
        # 4-3) 잔해는 무너진 틈 앞에만 작게 모음
        for ga, gw in gaps:
            near_gap = (rr > 36.6) & (rr < 39.3) & (adiff(ang, ga) < gw * 1.3)
            for z, x in zip(*np.nonzero(near_gap)):
                if rng.random() < 0.35: V[h + 1, z, x] = g(t['slab'] if rng.random() < 0.6 else t['worn'][0])
                elif rng.random() < 0.4: V[h, z, x] = g(t['worn'][rng.integers(len(t['worn']))])
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
            for ox in (-1, 0, 1, 2):
                for oz in (-1, 0, 1, 2):
                    if ox in (-1, 2) or oz in (-1, 2):
                        if V[h + 1, pz + oz, px + ox] == 0 and rng.random() < 0.8: V[h + 1, pz + oz, px + ox] = g(t['slab'])   # 받침
            if t['patch']:
                for ox in range(-2, 4):
                    for oz in range(-2, 4):
                        x, z = px + ox, pz + oz
                        if np.hypot(ox - 0.5, oz - 0.5) < 2.6 and rng.random() < 0.55 and 36.6 < np.hypot(x - cx, z - cz) < R - 0.5: V[h, z, x] = g(t['patch'])
            if tall >= 5:
                for x, z in fp: V[h + tall, z, x] = g(t['rune'])   # 기둥 머리
            if tall >= 6: V[h + 1 + tall, pz, px] = I[f"minecraft:{t['lamp']}[hanging=false,waterlogged=false]"]   # 기둥 위 랜턴
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
            V[h + 9, z, x] = I[f"minecraft:{t['lamp']}[hanging=false,waterlogged=false]"]
        for s in np.linspace(-6, 6, 25):
            x, z = int(round(bx + s * vx)), int(round(bz + s * vz))
            V[h + 7, z, x] = g(t['pillar'][1]); V[h + 8, z, x] = g(t['slab'])
        for s_ in (-3, 0, 3):
            x, z = int(round(bx + s_ * vx)), int(round(bz + s_ * vz))
            V[h + 6, z, x] = I[EXTRA[0]]; V[h + 5, z, x] = I[f"minecraft:{t['lamp']}[hanging=true,waterlogged=false]"]
