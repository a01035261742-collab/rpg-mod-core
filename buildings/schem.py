"""작은 건축 도구: 블록 찍기 → WorldEdit .schem (Sponge v2) + 미리보기 PNG"""
import gzip, struct, math
from PIL import Image, ImageDraw

# ── NBT 쓰기 ──
def _s(b, s):
    e = s.encode('utf-8'); b += struct.pack('>H', len(e)) + e
def _tag(b, t, name, v):
    b.append(t); _s(b, name); _pay(b, t, v)
def _pay(b, t, v):
    if t == 1: b += struct.pack('>b', v)
    elif t == 2: b += struct.pack('>h', v)
    elif t == 3: b += struct.pack('>i', v)
    elif t == 7: b += struct.pack('>i', len(v)) + bytes(v)
    elif t == 8: _s(b, v)
    elif t == 9:
        et, items = v; b.append(et); b += struct.pack('>i', len(items))
        for it in items: _pay(b, et, it)
    elif t == 10:
        for k, (tt, vv) in v.items(): _tag(b, tt, k, vv)
        b.append(0)
    elif t == 11: b += struct.pack('>i', len(v)) + b''.join(struct.pack('>i', x) for x in v)

def varint(n):
    out = bytearray()
    while True:
        x = n & 0x7F; n >>= 7
        if n: out.append(x | 0x80)
        else: out.append(x); return out

class S:
    def __init__(s, name):
        s.name = name; s.b = {}
    def set(s, x, y, z, st):
        if st is None: s.b.pop((x, y, z), None)
        else: s.b[(x, y, z)] = st if st.startswith('minecraft:') else 'minecraft:' + st
    def get(s, x, y, z): return s.b.get((x, y, z))
    def fill(s, x0, y0, z0, x1, y1, z1, st):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1): s.set(x, y, z, st)
    def bounds(s):
        xs = [p[0] for p in s.b]; ys = [p[1] for p in s.b]; zs = [p[2] for p in s.b]
        return min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)

    def save(s, path, anchor=None):
        """anchor = 붙여넣을 때 플레이어 발 위치 (기본: 남쪽 정면 가운데 바로 앞, 바닥 위)"""
        x0, y0, z0, x1, y1, z1 = s.bounds()
        W, H, L = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
        pal = {'minecraft:air': 0}; data = bytearray()
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    st = s.b.get((x, y, z), 'minecraft:air')
                    if st not in pal: pal[st] = len(pal)
                    data += varint(pal[st])
        if anchor is None: anchor = ((x0 + x1) // 2, 1, z1 + 1)   # 앞(남쪽) 문 앞 한 칸
        ax, ay, az = anchor
        root = {'Version': (3, 2), 'DataVersion': (3, 3465), 'Width': (2, W), 'Height': (2, H), 'Length': (2, L),
                'PaletteMax': (3, len(pal)), 'Palette': (10, {k: (3, v) for k, v in pal.items()}),
                'BlockData': (7, data), 'Offset': (11, [0, 0, 0]),
                'Metadata': (10, {'WEOffsetX': (3, x0 - ax), 'WEOffsetY': (3, y0 - ay), 'WEOffsetZ': (3, z0 - az)})}
        b = bytearray(); _tag(b, 10, 'Schematic', root)
        open(path, 'wb').write(gzip.compress(bytes(b)))
        return W, H, L, len(s.b)

# ── 미리보기 (아이소메트릭) ──
COL = [('deepslate_tile', (55, 55, 62)), ('cobbled_deepslate', (80, 80, 85)), ('trapdoor', (125, 95, 60)), ('terracotta', (150, 100, 80)), ('vine', (60, 110, 40)), ('bee_nest', (200, 160, 70)), ('azalea', (95, 135, 55)), ('lily', (50, 120, 40)), ('pumpkin', (210, 130, 40)), ('melon', (110, 150, 50)), ('diorite', (200, 200, 200)), ('gravel', (130, 125, 120)), ('packed_mud', (140, 105, 80)), ('chiseled', (125, 125, 125)), ('polished_andesite', (140, 140, 140)), ('water', (60, 110, 200)), ('lantern', (250, 200, 90)), ('glass', (170, 210, 230)), ('calcite', (225, 225, 218)),
       ('white_terracotta', (210, 190, 175)), ('dark_oak', (70, 48, 30)), ('spruce', (115, 85, 55)), ('oak', (165, 130, 80)),
       ('birch', (210, 200, 150)), ('cobble', (120, 120, 120)), ('mossy', (95, 115, 85)), ('stone_brick', (130, 130, 130)),
       ('stone', (128, 128, 128)), ('andesite', (135, 135, 135)), ('deepslate', (75, 75, 80)), ('red_wool', (170, 40, 40)),
       ('white_wool', (230, 230, 230)), ('blue', (60, 80, 170)), ('green', (70, 120, 50)), ('purple', (110, 60, 140)), ('red_terracotta', (145, 60, 45)), ('green_terracotta', (75, 85, 45)), ('blue_terracotta', (75, 60, 90)), ('purple_terracotta', (120, 70, 90)), ('red', (160, 50, 45)),
       ('hay', (200, 170, 50)), ('farmland', (100, 70, 45)), ('wheat', (190, 170, 70)), ('carrot', (220, 130, 40)), ('beetroot', (150, 40, 60)), ('bed', (180, 50, 50)), ('composter', (110, 80, 50)), ('ladder', (150, 115, 70)), ('target', (220, 210, 200)), ('fletching', (180, 160, 110)), ('enchanting', (60, 30, 80)), ('lava', (230, 110, 30)), ('potato', (170, 150, 80)),
       ('leaves', (60, 120, 50)), ('grass', (90, 150, 60)), ('dirt', (120, 85, 55)), ('path', (150, 125, 80)), ('flower', (220, 90, 120)),
       ('poppy', (200, 40, 40)), ('dandelion', (230, 210, 60)), ('mushroom_stem', (215, 210, 195)), ('brown_mushroom', (150, 110, 80)),
       ('red_mushroom', (190, 50, 45)), ('moss', (90, 120, 50)), ('anvil', (70, 70, 70)), ('barrel', (120, 90, 55)), ('bricks', (150, 75, 60)),
       ('campfire', (230, 120, 40)), ('hay', (200, 170, 50)), ('bookshelf', (130, 95, 60)), ('chest', (150, 110, 50)), ('crafting', (140, 100, 60)),
       ('cauldron', (60, 60, 65)), ('mud', (110, 85, 70)), ('bone', (225, 220, 200)), ('cobweb', (220, 220, 220)), ('soul', (90, 200, 210)),
       ('amethyst', (150, 100, 200)), ('torch', (250, 210, 100)), ('composter', (110, 80, 50)), ('flower_pot', (150, 80, 60)), ('smoker', (90, 90, 90)),
       ('blast', (90, 90, 95)), ('grindstone', (140, 140, 140)), ('smithing', (60, 60, 70)), ('loom', (160, 130, 90)), ('brewing', (120, 120, 60)),
       ('banner', (180, 50, 50)), ('red_carpet', (170, 40, 40)), ('white_carpet', (230, 230, 230)), ('carpet', (170, 60, 60)), ('podzol', (90, 65, 40)), ('coarse', (110, 80, 55)), ('rooted', (115, 85, 60)),
       ('shroomlight', (240, 170, 80)), ('nether_wart', (130, 30, 30)), ('crimson', (120, 40, 50)), ('warped', (40, 110, 110))]
def col(st):
    n = st.split(':')[1].split('[')[0]
    for k, c in COL:
        if k in n: return c
    return (180, 120, 200)

THIN = ('torch', 'lantern', 'flower', 'poppy', 'dandelion', 'carpet', 'pressure', 'button', 'wheat', 'carrot', 'potato', 'grass[', 'mushroom[', 'sign', 'banner', 'rail', 'cobweb', 'short_grass', 'fern')
def render(s, path, scale=10):
    if not s.b: return
    x0, y0, z0, x1, y1, z1 = s.bounds()
    cw, ch = scale, scale // 2
    def proj(x, y, z):
        return ((x - x0) - (z - z0)) * cw, ((x - x0) + (z - z0)) * ch - (y - y0) * scale
    pts = [proj(x, y, z) for x in (x0, x1 + 1) for y in (y0, y1 + 1) for z in (z0, z1 + 1)]
    minx = min(p[0] for p in pts) - 10; miny = min(p[1] for p in pts) - 10
    maxx = max(p[0] for p in pts) + 10; maxy = max(p[1] for p in pts) + 10
    im = Image.new('RGB', (int(maxx - minx), int(maxy - miny)), (34, 36, 44)); d = ImageDraw.Draw(im)
    order = sorted(s.b.items(), key=lambda kv: (kv[0][0] + kv[0][2], kv[0][1], kv[0][0]))
    # 보이는 면: 위(+y), 남(+z → 화면 왼쪽 아래), 동(+x → 오른쪽 아래)  ※ 카메라는 +x +z 쪽에서 봄
    for (x, y, z), st in order:
        if st == 'minecraft:air': continue
        c = col(st); thin = any(t in st for t in THIN)
        y0f, y1f = 0.0, 1.0
        if '_slab' in st: y0f, y1f = (0.5, 1.0) if 'type=top' in st else (0.0, 0.5) if 'type=bottom' in st else (0, 1)
        if 'carpet' in st: y1f = 0.08; thin = False
        if 'trapdoor' in st:
            if 'open=true' in st: thin = True
            else: y0f, y1f = (0.8, 1.0) if 'half=top' in st else (0.0, 0.2)
        if 'pressure_plate' in st: y1f = 0.08; thin = False
        a, b = y + y0f, y + y1f
        top = [proj(x, b, z), proj(x + 1, b, z), proj(x + 1, b, z + 1), proj(x, b, z + 1)]
        south = [proj(x, a, z + 1), proj(x + 1, a, z + 1), proj(x + 1, b, z + 1), proj(x, b, z + 1)]
        east = [proj(x + 1, a, z), proj(x + 1, a, z + 1), proj(x + 1, b, z + 1), proj(x + 1, b, z)]
        sh = lambda k: tuple(max(0, min(255, int(v * k))) for v in c)
        for poly, k in ((south, 0.72), (east, 0.86), (top, 1.0)):
            pp = [(px - minx, py - miny) for px, py in poly]
            if thin:
                cx = sum(p[0] for p in pp) / 4; cy = sum(p[1] for p in pp) / 4
                pp = [(cx + (px - cx) * 0.45, cy + (py - cy) * 0.45) for px, py in pp]
            d.polygon(pp, fill=sh(k), outline=sh(k * 0.8))
    im.save(path)
    return im

# ── 울타리·유리판 자동 연결 ──
NOCONNECT = ('air', 'torch', 'lantern', 'flower', 'poppy', 'dandelion', 'carpet', 'wheat', 'carrot', 'potato', 'grass', 'sign', 'banner', 'door', 'trapdoor', 'water', 'leaves', 'slab', 'stairs', 'campfire', 'mushroom[', 'pot')
def autoconnect(s):
    dirs = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0)}
    for (x, y, z), st in list(s.b.items()):
        base = st.split('[')[0]
        kind = 'fence' if base.endswith('_fence') else 'pane' if (base.endswith('glass_pane') or base.endswith('iron_bars')) else 'wall' if base.endswith('_wall') else None
        if not kind or '[' in st: continue
        props = []
        for dn, (dx, dy, dz) in dirs.items():
            n = s.b.get((x + dx, y, z + dz))
            ok = n is not None and not any(t in n for t in NOCONNECT if not (kind != 'fence' and t == 'leaves'))
            if n and kind == 'fence' and ('_fence' in n or 'gate' in n): ok = True
            if n and kind == 'pane' and ('pane' in n or 'bars' in n): ok = True
            if kind == 'wall': props.append(f'{dn}={"low" if ok else "none"}')
            else: props.append(f'{dn}={"true" if ok else "false"}')
        if kind == 'wall': props.append('up=true')
        s.b[(x, y, z)] = base + '[' + ','.join(props) + ']'
