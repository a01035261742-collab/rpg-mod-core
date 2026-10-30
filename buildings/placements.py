"""NPC · 퀘스트 지점 · 사냥터(몹) · 보스 · 마을 위치 계산 → placements.json + 지도 2장 (번호 표시)
좌표 = 붙이기 기준점(1번 맵 분수 가운데에 선 발 위치)에서 떨어진 칸 (동 +X, 위 +Y, 남 +Z)"""
import sys, json, math, numpy as np
VD = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/village/'
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
sys.path.insert(0, VD); sys.path.insert(0, T); sys.path.insert(0, '/home/claude/rpg-mod-core/buildings')
from scipy.ndimage import distance_transform_edt, label
from schem import _rotf
from PIL import Image, ImageDraw, ImageFont

DV = {'S': (0, 1), 'N': (0, -1), 'E': (1, 0), 'W': (-1, 0)}
LOOK = {'S': 180, 'N': 0, 'E': 90, 'W': -90}      # 문이 이쪽을 볼 때 플레이어 시선 (건물 쪽) → NPC 는 반대로 길 쪽을 봄
STEPS = {'S': 0, 'W': 1, 'N': 2, 'E': 3}
PASS = ('air', 'cave_air', 'barrier', 'grass', 'fern', 'tall_grass', 'large_fern', 'dandelion', 'poppy', 'cornflower', 'oxeye_daisy', 'azure_bluet',
        'allium', 'red_tulip', 'white_tulip', 'pink_tulip', 'red_mushroom', 'brown_mushroom', 'lily_pad', 'snow')

def door_front(fn, r):
    s = fn(); n = STEPS[r['face']]; pts = {}
    for (x, y, z), st in s.b.items():
        for _ in range(n): x, z = -z, x
        pts[(x, y, z)] = _rotf(st, n)
    mx = min(p[0] for p in pts); mz = min(p[2] for p in pts)
    foot = {(x - mx, z - mz) for (x, y, z), st in pts.items() if st != 'minecraft:air'}
    dl = [(x - mx, z - mz) for (x, y, z), st in pts.items() if '_door[' in st and 'half=lower' in st and y == 1]
    fx, fz = DV[r['face']]
    if not dl: return None
    dx, dz = max(dl, key=lambda p: p[0] * fx + p[1] * fz)
    k = 1
    while (dx + fx * k, dz + fz * k) in foot: k += 1
    return (r['x0'] + dx + fx * (k + 1), r['z0'] + dz + fz * (k + 1))    # 문 앞 2칸 (디딤돌 지나서)

def feet(V, PB, x, z, H):
    y = int(H[z, x])
    while y + 2 < V.shape[0] and not (PB[V[y + 1, z, x]] and PB[V[y + 2, z, x]] and not PB[V[y, z, x]]): y += 1
    return y + 1

def clear_spot(V, PB, H, x, z, toward=None, rmax=5):
    """나무·버섯·건물 안이 아닌 가장 가까운 빈 땅 (땅 = 지형 높이 ±1, 머리 위 2칸 비어 있음)"""
    best = None
    for r in range(0, rmax + 1):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) != r: continue
                X, Z = x + dx, z + dz
                if not (0 <= X < H.shape[1] and 0 <= Z < H.shape[0]): continue
                f = feet(V, PB, X, Z, H)
                if f - (int(H[Z, X]) + 1) > 1: continue          # 나무·건물 위로 올라간 자리 X
                if not all(PB[V[f + k, Z, X]] for k in range(0, 3)): continue
                d = math.hypot(dx, dz) + (0 if toward is None else 0.3 * math.hypot(X - toward[0], Z - toward[1]))
                if best is None or d < best[0]: best = (d, X, Z, f)
        if best: return best[1:]
    return (x, z, feet(V, PB, x, z, H))

def max_rect(m):
    """가장 큰 직사각형 (히스토그램)"""
    h = np.zeros(m.shape[1], int); best = (0, None)
    for i in range(m.shape[0]):
        h = np.where(m[i], h + 1, 0); st = []
        for j in range(m.shape[1] + 1):
            cur = h[j] if j < m.shape[1] else 0
            start = j
            while st and st[-1][1] >= cur:
                s0, hh = st.pop(); area = hh * (j - s0)
                if area > best[0] and hh >= 6 and (j - s0) >= 6: best = (area, (i - hh + 1, s0, i, j - 1))
                start = s0
            st.append((start, cur))
    return best[1]

def load(map_):
    if map_ == 1:
        V = np.load(VD + 'V_final.npy', mmap_mode='r'); P = json.load(open(VD + 'P_final.json')); H = np.load(T + 'H2.npy')
        off = (-130, -11, -206)
    else:
        V = np.load(VD + 'V_r2.npy', mmap_mode='r'); P = json.load(open(VD + 'P_r2.json')); H = np.load(T + 'R2_H.npy')
        off = (-239, -8, -605)
    PB = np.array([p.split(':')[1].split('[')[0] in PASS for p in P])
    return np.array(V), P, PB, H, off

OUT = []
def add(map_, kind, label, cmd, x, z, yaw=None, V=None, PB=None, H=None, off=None, note=''):
    x, z, y = clear_spot(V, PB, H, x, z)
    OUT.append(dict(map=map_, kind=kind, label=label, cmd=cmd, cell=[int(x), int(z)], off=[int(x + off[0]), int(y + off[1]), int(z + off[2])],
                    yaw=yaw, note=note))

def zone_add(map_, no, name, m, V, PB, H, off, note='', zid=None):
    r = max_rect(m)
    if r is None: print('사냥터 자리 못 찾음', name); return
    z0, x0, z1, x1 = r
    z0 += 1; x0 += 1; z1 -= 1; x1 -= 1
    cx_, cz_ = (x0 + x1) / 2, (z0 + z1) / 2
    x0, z0, y1 = clear_spot(V, PB, H, x0, z0, toward=(cx_, cz_))
    x1, z1, y2 = clear_spot(V, PB, H, x1, z1, toward=(cx_, cz_))
    OUT.append(dict(map=map_, kind='zone', label=f'사냥터 {no} — {name}', cmd=f'/rpg zone ground {no} {zid}', ground=no,
                    c1=[int(x0 + off[0]), int(y1 + off[1]), int(z0 + off[2])], c2=[int(x1 + off[0]), int(y2 + off[1]), int(z1 + off[2])],
                    cell1=[int(x0), int(z0)], cell2=[int(x1), int(z1)], size=[int(x1 - x0 + 1), int(z1 - z0 + 1)], note=note))

# ───────── 1번 맵 ─────────
import layout as L1
V, P, PB, H, off = load(1)
LAY = json.load(open(VD + 'layout1.json')); FN = {p[1]: p[0] for p in L1.PLAN}
byname = {r['name']: r for r in LAY}
def bld(name, kind, label, cmd, note=''):
    r = byname[name]; fx = door_front(FN[name], r)
    if fx is None: print('문 없음', name); return
    add(1, kind, label, cmd, fx[0], fx[1], LOOK[r['face']], V, PB, H, off, note or f'{name} 문 앞')
bld('촌장 해럴드의 집', 'npc', '촌장 해럴드 (퀘스트)', '/rpg npc quest harold')
bld('브람 농가', 'npc', '농부 브람 (퀘스트)', '/rpg npc quest bram')
bld('경비 초소 (루크)', 'npc', '경비병 루크 (퀘스트)', '/rpg npc quest luke')
bld('약초 가게 (엘린)', 'npc', '약초꾼 엘린 (퀘스트)', '/rpg npc quest elin')
bld('사냥꾼 오두막 (로완)', 'npc', '사냥꾼 로완 (퀘스트)', '/rpg npc quest rowan')
bld('전사 수련장 (바르크)', 'npc', '전사 교관 (전직)', '/rpg npc job 1')
bld('궁수 수련장', 'npc', '궁수 교관 (전직)', '/rpg npc job 2')
bld('마법사 탑 (세라)', 'npc', '마법사 교관 (전직)', '/rpg npc job 3')
bld('도적 은신처 (카이)', 'npc', '도적 교관 (전직)', '/rpg npc job 4')
bld('잡화점', 'npc', '잡화 상인', '/rpg npc shop general')
bld('장비 상점', 'npc', '장비 상인 (Lv1·10)', '/rpg npc shop gear')
bld('대장간', 'npc', '대장장이 (제작)', '/rpg npc craft smith')
bld('세공소', 'npc', '세공사 (제작)', '/rpg npc craft jeweler')
bld('연금술 공방', 'npc', '연금술사 (제작)', '/rpg npc craft alchemist')
bld('여관', 'npc', '창고지기', '/rpg npc storage')
r = byname['시장 노점']; add(1, 'npc', '물약 상인', '/rpg npc shop general2', r['x0'] + r['w'] // 2, r['z0'] + r['l'] + 2, 0, V, PB, H, off, '시장 노점 앞')
add(1, 'town', '마을 등록: 시작마을 (귀환 도착지)', '/rpg town add 시작마을', 130, 218, 180, V, PB, H, off, '광장 분수 남쪽')
# 퀘스트 지점
add(1, 'point', '초원 동쪽 초소 (두고 오기)', '/rpg quest point add grass_post', 368, 108, None, V, PB, H, off, '초원 동쪽 끝 언덕')
add(1, 'point', '들개 굴 (가져오기)', '/rpg quest point add dog_den', 36, 108, None, V, PB, H, off, '깊은 풀숲 서쪽 구석')
# 사냥터 (몹)
walk = np.load(T + 'walk2.npy'); MAT = np.load(T + 'MAT2v.npy', allow_pickle=True); foot = np.load(T + 'foot1.npy')
d_wall = distance_transform_edt(walk); d_foot = distance_transform_edt(~foot)
Zg, Xg = np.mgrid[0:H.shape[0], 0:H.shape[1]]
ok = walk & (d_wall > 3) & (d_foot > 4) & ~np.isin(MAT, ['water', 'plaza'])
def ell(cx, cz, rx, rz): return ((Xg - cx) / rx) ** 2 + ((Zg - cz) / rz) ** 2 < 1
zone_add(1, 1, '농장', ok & ell(278.7, 227, 50, 30), V, PB, H, off, '돼지·소 Lv1~5 (밭 자리 포함)', 'farm')
zone_add(1, 2, '초원', ok & ell(333, 120, 40, 44), V, PB, H, off, '슬라임·쥐 Lv6~10', 'meadow')
clear = np.load(T + 'clear1.npy'); cl, n = label(clear)
cent = {i: np.argwhere(cl == i).mean(0) for i in range(1, n + 1)}
A = min(cent, key=lambda i: cent[i][1]); others = sorted([i for i in cent if i != A], key=lambda i: cent[i][0])
zone_add(1, 3, '풀숲언덕', (cl == others[0]) & ok, V, PB, H, off, '들개·벌 Lv11~15 (공터 B)', 'forest_hill')
zone_add(1, 3, '풀숲아래', (cl == others[1]) & ok, V, PB, H, off, '들개·벌 Lv11~15 (공터 C, 같은 3번 두 번째 자리)', 'forest_low')
zone_add(1, 4, '깊은풀숲', (cl == A) & ok, V, PB, H, off, '멧돼지·곰 Lv16~20 (공터 A)', 'deep_forest')
M1 = (V, P, PB, H, off)

# ───────── 2지역 ─────────
import r2_place as R2P
V, P, PB, H, off = load(2)
LAY2 = json.load(open(VD + 'r2_layout.json')); FN2 = {fn.__name__: fn for fn, *_ in R2P.PLAN}
by2 = {r['fn']: r for r in LAY2}
def bld2(fn, kind, label, cmd, note=''):
    if fn not in by2: print('배치 없음', fn); return
    r = by2[fn]; fx = door_front(FN2[fn], r)
    if fx is None: fx = tuple(r['door'])
    add(2, kind, label, cmd, fx[0], fx[1], LOOK[r['face']], V, PB, H, off, note or f"{r['name']} 문 앞")
bld2('kael2', 'npc', '경비대장 카엘 (퀘스트)', '/rpg npc quest kael')
bld2('lab2', 'npc', '버섯 학자 모리스 (퀘스트)', '/rpg npc quest morris')
bld2('alch3', 'npc', '연금술사 네라 (퀘스트)', '/rpg npc quest nera')
bld2('owen_hut', 'npc', '숲지기 오웬 (퀘스트)', '/rpg npc quest owen')
bld2('gear3', 'npc', '장비 상인 (Lv20·30·40)', '/rpg npc shop gear2')
bld2('general3', 'npc', '잡화 상인', '/rpg npc shop general')
bld2('smithy3', 'npc', '대장장이 (제작)', '/rpg npc craft smith')
bld2('jewel3', 'npc', '세공사 (제작)', '/rpg npc craft jeweler')
bld2('inn3', 'npc', '창고지기', '/rpg npc storage')
r = by2['stalls3']; add(2, 'npc', '물약 상인', '/rpg npc shop general2', *tuple(r['door']), LOOK[r['face']], V, PB, H, off, '노점 앞')
add(2, 'town', '마을 등록: 두번째마을 (귀환 도착지)', '/rpg town add 두번째마을', 186, 268, 180, V, PB, H, off, '광장 분수 남쪽')
def center(fn):
    r = by2[fn]; return r['x0'] + r['w'] // 2, r['z0'] + r['l'] // 2
for fn, pid, nm in (('spore_camp', 'spore_camp', '버섯 연구 캠프 (두고 오기)'), ('spirit_altar', 'spirit_altar', '정령의 제단 (가져오기)'),
                    ('soldier_camp', 'soldier_camp', '병정 막사 (가져오기)'), ('deep_post', 'deep_post', '깊은 곳 전초기지 (두고 오기)'),
                    ('witch_hut', 'witch_hideout', '마녀의 은신처 (가져오기)'), ('beast_nest', 'beast_nest', '짐승 둥지 (가져오기)')):
    r_ = by2[fn]
    def find(names):
        sub = V[:, r_['z0']:r_['z0'] + r_['l'], r_['x0']:r_['x0'] + r_['w']]
        ids = [i for i, p in enumerate(P) if p.split(':')[1].split('[')[0] in names]
        hits = np.argwhere(np.isin(sub, ids))
        return [(int(r_['x0'] + x), int(y), int(r_['z0'] + z)) for y, z, x in hits]
    if fn == 'soldier_camp':        # 막사 탁자 옆 (탁자 밑에서 작전 지도)
        tx, ty, tz = find(('cartography_table',))[0]
        x, z, y = clear_spot(V, PB, H, tx, tz, rmax=2)
        OUT.append(dict(map=2, kind='point', label=nm, cmd=f'/rpg quest point add {pid}', cell=[x, z], off=[x + off[0], y + off[1], z + off[2]], yaw=None, note='작전 탁자 바로 옆'))
        continue
    if fn == 'witch_hut':           # 오두막 안, 책장 옆 (책장에서 주문서)
        bs = find(('bookshelf',)); fl_ = max(b[1] for b in bs) - 1 if False else min(b[1] for b in bs) - 1
        best = None
        for (bx, by, bz) in bs:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                X, Z = bx + dx, bz + dz
                if not PB[V[fl_, Z, X]] and PB[V[fl_ + 1, Z, X]] and PB[V[fl_ + 2, Z, X]]: best = (X, Z); break
            if best: break
        x, z = best; y = fl_ + 1
        OUT.append(dict(map=2, kind='point', label=nm, cmd=f'/rpg quest point add {pid}', cell=[x, z], off=[x + off[0], y + off[1], z + off[2]], yaw=None, note='오두막 안 책장 옆 (계단으로 올라가서)'))
        continue
    x, z = center(fn)
    add(2, 'point', nm, f'/rpg quest point add {pid}', x, z, None, V, PB, H, off, '구조물 가운데' if fn != 'spirit_altar' else '제단 가운데 (독서대 옆)')
MM = json.load(open(T + 'R2_meta.json'))
for k, boss, nm in (('boss1', 'forest_lord', '숲의 군주 (필드 보스 Lv20)'), ('boss2', 'mush_guardian', '거대버섯 수호자 (필드 보스 Lv40)')):
    x, z = MM['arenas'][k]; add(2, 'boss', nm, f'/rpg boss point add {boss}', x, z, None, V, PB, H, off, '경기장 한가운데 (발밑 = 중앙)')
walk = np.load(T + 'R2_walk.npy'); MAT = np.load(T + 'R2_MATv.npy', allow_pickle=True); foot = np.load(T + 'r2_foot.npy'); ZN = np.load(T + 'R2_zone.npy')
d_wall = distance_transform_edt(walk); d_foot = distance_transform_edt(~foot)
ok = walk & (d_wall > 3) & (d_foot > 5) & ~np.isin(MAT, ['water', 'bog', 'plaza'])
Zg, Xg = np.mgrid[0:H.shape[0], 0:H.shape[1]]
arena = np.zeros_like(ok)
for k, (ax, az) in MM['arenas'].items(): arena |= np.hypot(Xg - ax, Zg - az) < MM['arena_r'][k] + 6
G = lambda x, z: (int(round((x + 12) * 1.5)), int(round((z + 16) * 1.5)))
for no, zn, nm, mobs, zid in ((5, 'g5', '군락입구', '버섯 정령·꼬마 버섯 Lv21~25', 'mush_gate'), (6, 'g6', '군락안쪽', '버섯 병정·요정 Lv26~30', 'mush_inner'),
                          (7, 'g7', '깊은곳', '포자 거미·마녀 Lv31~35', 'mush_deep'), (8, 'g8', '어둠경계', '어둠 거미·버섯 짐승 Lv36~40', 'dark_edge')):
    zone_add(2, no, nm, ok & (ZN == zn) & ~arena, V, PB, H, off, mobs, zid)
M2 = (V, P, PB, H, off)
QJ = json.load(open('/home/claude/rpg-mod-core/src/main/resources/data/rpgcore/rpg_balance/quests.json'))
NPCN = {k: v['name'] for k, v in QJ['npcs'].items()}
for o in OUT:
    if o['kind'] != 'point': continue
    pid = o['cmd'].split()[-1]
    for qk, q in QJ['quests'].items():
        f = q.get('fetch') or {}
        if f.get('point') == pid: o['quest'] = f"가져오기 · {q.get('name')} ({NPCN.get(q.get('npc'), q.get('npc'))}) — 가까이 가면 자동으로 주움: “{f.get('text')}”"
        if q.get('point') == pid: o['quest'] = f"두고 오기 · {q.get('name')} ({NPCN.get(q.get('npc'), q.get('npc'))}) — 가까이 가면 바로 완료"
json.dump(OUT, open(VD + 'placements.json', 'w'), ensure_ascii=False, indent=1)
print(len(OUT), '개')
for o in OUT: print(o['map'], o['kind'], o['label'], o.get('off') or (o['c1'], o['c2'], o['size']), o.get('yaw'))

# ───────── 지도 (위에서, 번호) ─────────
src = open(T + 'vox_iso.py').read(); exec(src[src.index('COLS = ['):src.index("F = '/usr")])
COLS[:0] = [('red_mushroom_block', (196, 48, 44)), ('brown_mushroom_block', (150, 108, 76)), ('mushroom_stem', (214, 206, 190)), ('shroomlight', (244, 160, 76)),
            ('mycelium', (128, 108, 128)), ('podzol', (106, 76, 40)), ('birch_leaves', (118, 158, 74)), ('spruce_leaves', (52, 92, 58)), ('azalea_leaves', (92, 140, 56)),
            ('oak_leaves', (72, 128, 52)), ('dark_oak_leaves', (50, 92, 36)), ('_log', (100, 78, 50)), ('mangrove', (118, 52, 46)), ('deepslate_tile', (56, 56, 64)),
            ('spruce', (100, 74, 46)), ('cobbled_deepslate', (78, 78, 84)), ('smooth_sandstone', (222, 210, 160)), ('deepslate', (74, 74, 80)), ('dark_oak', (70, 50, 30)),
            ('oak_', (150, 120, 75)), ('wool', (200, 200, 200)), ('purple_terracotta', (118, 70, 86)), ('magenta', (160, 70, 140)), ('bricks', (150, 80, 60))]
F = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 21, index=1)
KC = {'npc': (255, 214, 64), 'town': (120, 220, 255), 'point': (255, 140, 60), 'boss': (255, 80, 80), 'zone': (255, 70, 70)}
def mapimg(map_, M, path, s=3):
    V, P, PB, H, off = M
    D, W = H.shape
    sk = PB.copy()
    occ = ~sk[V]; top = V.shape[0] - 1 - np.argmax(occ[::-1], 0)
    ids = V[top, np.arange(D)[:, None], np.arange(W)[None]]
    cols = np.array([col(p) for p in P], float); arr = cols[ids]
    gx, gz = np.gradient(top.astype(float)); arr *= np.clip(1 + 0.25 * (-gx - 1.2 * gz) / 1.5, .6, 1.3)[..., None]
    arr = arr * 0.78 + 30
    im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).resize((W * s, D * s), Image.NEAREST)
    d = ImageDraw.Draw(im)
    num = 0
    items = [o for o in OUT if o['map'] == map_]
    for o in items:
        num += 1; o['no'] = num
        if o['kind'] == 'zone':
            (x0, z0), (x1, z1) = o['cell1'], o['cell2']
            d.rectangle([x0 * s, z0 * s, (x1 + 1) * s, (z1 + 1) * s], outline=KC['zone'], width=4)
            cx, cz = (x0 + x1) / 2 * s, (z0 + z1) / 2 * s
            d.rectangle([cx - 17, cz - 16, cx + 17, cz + 16], fill=(214, 72, 72), outline=(255, 255, 255), width=2); d.text((cx - 7, cz - 15), f'{o["ground"]}', font=F, fill=(255, 255, 255))
            continue
        x, z = o['cell']; c = KC[o['kind']]
        d.ellipse([x * s - 15, z * s - 15, x * s + 15, z * s + 15], fill=c, outline=(20, 20, 20), width=3)
        tx = str(num); w = d.textlength(tx, font=F)
        d.text((x * s - w / 2, z * s - 15), tx, font=F, fill=(20, 20, 20))
    # 기준점 (1번 맵 분수 가운데)
    if map_ == 1:
        d.rectangle([130 * s - 7, 206 * s - 7, 130 * s + 7, 206 * s + 7], outline=(255, 255, 255), width=3)
    im.save(path, optimize=True); print(path, im.size)
mapimg(1, M1, VD + 'place_map1.png'); mapimg(2, M2, VD + 'place_map2.png')
json.dump(OUT, open(VD + 'placements.json', 'w'), ensure_ascii=False, indent=1)
