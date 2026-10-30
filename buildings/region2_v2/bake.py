"""건물을 지형에 넣기 (1번 맵 · 2지역 같이 씀)
- raise_=True (집·가게처럼 기초가 있는 건물): 기초 자리(바닥 + 그 위 몸통이 있는 칸이 9칸 이상 이어진 덩어리)와 그 둘레 1칸
  (계단 받침·디딤돌·창틀·덧문), 그리고 2칸 이상 위에서 시작하는 칸(튀어나온 위층·처마)만 한 칸 올림.
  마당 울타리·가로등·벤치·바닥 깔기처럼 땅에 놓이는 칸은 땅 높이 그대로.
- raise_=False (분수·우물·노점·캠프·제단·둥지·선착장처럼 땅에 놓이는 것): 전부 땅 높이 (y=0 이 땅 겉면을 바꿈)"""
import numpy as np
from scipy.ndimage import label, binary_dilation

GROUND = ('dirt_path', 'coarse_dirt', 'gravel', 'cobblestone', 'mossy_cobblestone', 'polished_andesite', 'andesite', 'spruce_planks', 'oak_planks',
          'polished_deepslate', 'deepslate_tiles', 'chiseled_deepslate', 'stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks',
          'podzol', 'rooted_dirt', 'mycelium')

def bake(pts, r, raise_, V2, H, Hf, body, idx, AIR, BAR, W, D, TOPN, placed=None):
    """pts = 회전 끝난 {(x,y,z): 블록}, r = 배치(x0,z0,floor)"""
    mx = min(p[0] for p in pts); mz = min(p[2] for p in pts); fl = r['floor']
    cols = {}
    for (x, y, z), st in pts.items(): cols.setdefault((r['x0'] + x - mx, r['z0'] + z - mz), []).append((y, st))
    lift = set()
    if raise_:
        xs = [c[0] for c in cols]; zs = [c[1] for c in cols]
        X0, Z0 = min(xs), min(zs); w, l = max(xs) - X0 + 1, max(zs) - Z0 + 1
        base = np.zeros((l + 2, w + 2), bool); high = np.zeros((l + 2, w + 2), bool)
        for (X, Z), lst in cols.items():
            ys = [y for y, st in lst if st != 'minecraft:air']
            if not ys: continue
            if min(ys) <= 0 and max(ys) >= 1: base[Z - Z0 + 1, X - X0 + 1] = True
            if min(ys) >= 2: high[Z - Z0 + 1, X - X0 + 1] = True
        lab, n = label(base)
        big = np.zeros_like(base)
        for i in range(1, n + 1):
            if (lab == i).sum() >= 9: big |= lab == i
        ring = binary_dilation(big, iterations=1) & ~big
        for a, b in zip(*np.nonzero(big | high)): lift.add((X0 + b - 1, Z0 + a - 1))
        for a, b in zip(*np.nonzero(ring)):   # 둘레 1칸: 기초 계단·디딤돌(y≤0)이나 벽에 붙은 창틀(위로 붙는 계단·반블록)만 같이 올림
            X, Z = X0 + b - 1, Z0 + a - 1
            lst = [(y, st) for y, st in cols.get((X, Z), []) if st != 'minecraft:air']
            if not lst: continue
            y0, st0 = min(lst)
            if y0 <= 0 or y0 >= 2 or 'half=top' in st0 or 'type=top' in st0: lift.add((X, Z))
    for (X, Z), lst in cols.items():
        if not (0 <= X < W and 0 <= Z < D): continue
        up = (X, Z) in lift
        top = int(H[Z, X])
        has0 = min(y for y, _ in lst) <= 0
        if has0:
            b0 = fl + 1 if up else fl
            for y in range(b0 + 1, max(top, b0) + 1):         # 위로 튀어나온 땅은 비움
                if V2[y, Z, X] != BAR: V2[y, Z, X] = AIR
            for y in range(top + 1, b0): V2[y, Z, X] = idx('minecraft:dirt')   # 낮은 땅은 흙으로 채움
            Hf[Z, X] = b0
        elif up: b0 = fl + 1                                  # 튀어나온 위층·창틀 (땅은 그대로)
        else: b0 = top                                         # 마당 소품: 실제 땅 위
        for y, st in lst:
            yy = b0 + y
            if 0 <= yy < TOPN:
                V2[yy, Z, X] = idx(st if st.startswith('minecraft:') else 'minecraft:' + st)
                if placed is not None and st != 'minecraft:air': placed.append((yy, Z, X))
            if y >= 1 and st != 'minecraft:air': body[Z, X] = True

def floating(V2, AIR, BAR, placed, names, fix_small=6):
    """건물 블록 중 땅(맨 아래층)과 이어지지 않은 덩어리 찾기 (6방향 연결). fix_small 칸 이하 조각은 지움"""
    from scipy.ndimage import label
    solid = (V2 != AIR) & (V2 != BAR)
    lab, n = label(solid, structure=np.ones((3, 3, 3), bool))   # 대각으로 붙은 계단 지붕도 이어진 것으로
    ground = set(np.unique(lab[0])) - {0}
    bad = {}
    for (y, z, x) in placed:
        c = lab[y, z, x]
        if c and c not in ground: bad.setdefault(c, (int(x), int(y), int(z), names[V2[y, z, x]]))
    left = []
    for c, info in bad.items():
        m = lab == c
        if fix_small and m.sum() <= fix_small: V2[m] = AIR
        else: left.append(info + (int(m.sum()),))
    return left
