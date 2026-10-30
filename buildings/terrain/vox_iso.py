"""schem 블록(V)으로 경기장 확대 3D 미리보기"""
import numpy as np, gzip, sys, json, os
from PIL import Image, ImageDraw, ImageFont
exec(open(os.environ.get('BUILD', 'build_r2.py')).read().split("def save(")[0].replace("print(", "(lambda *a, **k: None)("))
COLS = [('cracked_deepslate_bricks', (62, 62, 68)), ('deepslate_bricks', (80, 80, 88)), ('smooth_basalt', (72, 70, 78)), ('clay', (160, 166, 178)), ('sand', (218, 206, 150)), ('packed_mud', (142, 106, 80)), ('deepslate_brick_slab', (76, 76, 82)), ('deepslate_brick_wall', (70, 70, 76)), ('slab', (150, 150, 150)), ('brick_wall', (128, 128, 128)), ('soul_lantern', (110, 220, 230)), ('lantern', (255, 200, 90)), ('chain', (60, 60, 70)), ('sculk', (16, 40, 52)), ('calcite', (220, 222, 218)), ('calcite', (220, 222, 218)), ('smooth_stone', (160, 160, 160)), ('mud_bricks', (138, 104, 78)), ('mossy', (96, 120, 86)), ('cracked_deepslate', (60, 60, 66)), ('chiseled_deepslate', (50, 50, 56)), ('deepslate_tiles', (58, 58, 64)),
        ('deepslate_bricks', (72, 72, 78)), ('polished_deepslate', (80, 80, 86)), ('cobbled_deepslate', (78, 78, 82)), ('polished_basalt', (98, 98, 104)), ('polished_tuff', (112, 116, 104)),
        ('chiseled_stone', (150, 150, 150)), ('cracked_stone', (128, 128, 128)), ('stone_bricks', (138, 138, 138)), ('polished_andesite', (176, 176, 180)),
        ('grass', (98, 156, 64)), ('mycelium', (118, 100, 120)), ('podzol', (112, 82, 40)), ('coarse', (120, 92, 66)), ('dirt_path', (178, 148, 98)), ('tuff', (100, 104, 92)),
        ('deepslate', (66, 66, 72)), ('andesite', (146, 146, 142)), ('cobble', (120, 120, 120)), ('gravel', (136, 128, 124)), ('mud', (60, 56, 60)), ('rooted', (82, 66, 52)),
        ('water', (60, 110, 205)), ('stone', (122, 122, 126)), ('dirt', (120, 90, 60))]
def col(n):
    n = n.split('[')[0]
    for k, c in COLS:
        if k in n: return c
    return (255, 0, 255)
F = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
MM = json.load(open('R2_meta.json')); imgs = []
import os
AR = MM['arenas'] if not os.environ.get('CENTERS') else {f'c{i}': tuple(map(int, c.split(','))) for i, c in enumerate(os.environ['CENTERS'].split(';'))}
for nm, (cx, cz) in AR.items():
    if len(sys.argv) > 3:   # 입구 쪽 가장자리 확대
        XX, ZZ = np.meshgrid(np.arange(W), np.arange(D)); rr_ = np.hypot(XX - cx, ZZ - cz)
        nr = (MAT == 'path') & (rr_ > 40) & (rr_ < 50); a_ = np.arctan2((ZZ - cz)[nr].mean(), (XX - cx)[nr].mean())
        cx, cz = int(cx + 33 * np.cos(a_)), int(cz + 33 * np.sin(a_))
    R = int(sys.argv[1]) if len(sys.argv) > 1 else 54; x0, x1, z0, z1 = max(0, cx - R), min(W, cx + R), max(0, cz - R), min(D, cz + R)
    sub = V[:, z0:z1, x0:x1].copy(); sub[sub == I['barrier']] = 0
    Hh, L, Wd = sub.shape
    top = np.where(sub.any(0), Hh - 1 - np.argmax(sub[::-1] > 0, axis=0), 0)
    s = 7.0 * 54 / R; k = s; base = top.min() - 1
    img = Image.new('RGB', (int((Wd + L) * s) + 40, int((Wd + L) * s * 0.5 + (top.max() - base) * k) + 80), (150, 190, 230)); d = ImageDraw.Draw(img)
    ox = L * s + 20; oy = 60
    def Pp(x, z, y): return (ox + (x - z) * s, oy + (x + z) * s * 0.5 - (y - base) * k + (top.max() - base) * k)
    def dim(c, f): return tuple(int(min(255, v * f)) for v in c)
    for sm in range(Wd + L - 1):
        for x in range(max(0, sm - L + 1), min(Wd, sm + 1)):
            z = sm - x
            for y in range(max(base, int(top[z, x]) - 12), int(top[z, x]) + 1):
                b = sub[y, z, x]
                if not b: continue
                c = col(P[b])
                if y + 1 < Hh and sub[y + 1, z, x] == 0 or y == top[z, x]: d.polygon([Pp(x, z, y + 1), Pp(x + 1, z, y + 1), Pp(x + 1, z + 1, y + 1), Pp(x, z + 1, y + 1)], fill=c)
                if z + 1 >= L or sub[y, z + 1, x] == 0: d.polygon([Pp(x, z + 1, y + 1), Pp(x + 1, z + 1, y + 1), Pp(x + 1, z + 1, y), Pp(x, z + 1, y)], fill=dim(c, .62))
                if x + 1 >= Wd or sub[y, z, x + 1] == 0: d.polygon([Pp(x + 1, z, y + 1), Pp(x + 1, z + 1, y + 1), Pp(x + 1, z + 1, y), Pp(x + 1, z, y)], fill=dim(c, .78))
    d.text((14, 10), {'boss1': '숲의 군주 경기장', 'boss2': '거대버섯 수호자 경기장'}.get(nm, ''), font=ImageFont.truetype(F, 30, index=1), fill=(20, 30, 50))
    imgs.append(img)
Wt = sum(i.width for i in imgs); sheet = Image.new('RGB', (Wt, max(i.height for i in imgs)), (150, 190, 230)); x = 0
for i in imgs: sheet.paste(i, (x, 0)); x += i.width
sheet.save(sys.argv[2] if len(sys.argv) > 2 else 'arena_deco.png'); print(sheet.size)
