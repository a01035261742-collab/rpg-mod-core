"""새 몹 모델(Java) + 텍스처(PNG) 생성기. python3 tools/mobgen.py"""
import os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main')
JAVA = os.path.join(ROOT, 'java', 'rpg', 'core', 'client', 'entity', 'MobModels.java')
TEX = os.path.join(ROOT, 'resources', 'assets', 'rpgcore', 'textures', 'entity')

# part: (name, pivot(x,y,z), rot(x,y,z) degrees, [cubes]); cube: (x,y,z,w,h,d, palette_key)
# palette: name -> (base rgb, style)  style: plain / spots / belly / eyes / wing / armor / fur
MOBS = {}

def mob(key, tw, th, palette, parts):
    MOBS[key] = dict(tw=tw, th=th, palette=palette, parts=parts)

# 들쥐 (작은 네발, 긴 꼬리)
mob('rat', 64, 32, {
    'fur': ((118, 98, 84), 'fur'), 'face': ((118, 98, 84), 'eyes_small'), 'pink': ((214, 150, 150), 'plain'),
    'leg': ((95, 78, 66), 'plain'), 'ear': ((200, 140, 140), 'plain')}, [
    ('body', (0, 20.5, 0), (0, 0, 0), [(-2, -2, -4, 4, 4, 8, 'fur')]),
    ('head', (0, 20, -4), (0, 0, 0), [(-1.5, -1.5, -4, 3, 3, 4, 'face'), (-0.5, -0.5, -5, 1, 1, 1, 'pink'),
                                      (-2.5, -3, -2, 1, 2, 1, 'ear'), (1.5, -3, -2, 1, 2, 1, 'ear')]),
    ('leg0', (-1.5, 22.5, -2.5), (0, 0, 0), [(-0.5, 0, -0.5, 1, 1.5, 1, 'leg')]),
    ('leg1', (1.5, 22.5, -2.5), (0, 0, 0), [(-0.5, 0, -0.5, 1, 1.5, 1, 'leg')]),
    ('leg2', (-1.5, 22.5, 2.5), (0, 0, 0), [(-0.5, 0, -0.5, 1, 1.5, 1, 'leg')]),
    ('leg3', (1.5, 22.5, 2.5), (0, 0, 0), [(-0.5, 0, -0.5, 1, 1.5, 1, 'leg')]),
    ('tail', (0, 20, 4), (-15, 0, 0), [(-0.5, -0.5, 0, 1, 1, 8, 'pink')]),
])

# 꼬마 버섯 (빨간 갓, 짧은 다리)
mob('mush_kid', 64, 32, {
    'cap': ((200, 40, 40), 'spots'), 'stem': ((235, 222, 196), 'eyes'), 'leg': ((214, 200, 170), 'plain')}, [
    ('head', (0, 17, 0), (0, 0, 0), [(-4, -3, -4, 8, 3, 8, 'cap'), (-3, -4, -3, 6, 1, 6, 'cap')]),
    ('body', (0, 17, 0), (0, 0, 0), [(-2.5, 0, -2, 5, 5, 4, 'stem')]),
    ('leg_l', (1.3, 22, 0), (0, 0, 0), [(-1, 0, -1, 2, 2, 2, 'leg')]),
    ('leg_r', (-1.3, 22, 0), (0, 0, 0), [(-1, 0, -1, 2, 2, 2, 'leg')]),
])

# 버섯 정령 (큰 갓 머리, 가는 몸)
mob('mush_spirit', 64, 64, {
    'cap': ((190, 50, 45), 'spots'), 'face': ((232, 220, 195), 'eyes'), 'body': ((210, 196, 168), 'plain'),
    'limb': ((190, 175, 145), 'plain')}, [
    ('head', (0, 8, 0), (0, 0, 0), [(-3, -6, -3, 6, 6, 6, 'face'), (-6, -10, -6, 12, 4, 12, 'cap'), (-4, -11, -4, 8, 1, 8, 'cap')]),
    ('body', (0, 8, 0), (0, 0, 0), [(-3, 0, -1.5, 6, 10, 3, 'body')]),
    ('arm_r', (-4, 9, 0), (0, 0, 0), [(-1, 0, -1, 2, 9, 2, 'limb')]),
    ('arm_l', (4, 9, 0), (0, 0, 0), [(-1, 0, -1, 2, 9, 2, 'limb')]),
    ('leg_r', (-1.5, 18, 0), (0, 0, 0), [(-1, 0, -1, 2, 6, 2, 'limb')]),
    ('leg_l', (1.5, 18, 0), (0, 0, 0), [(-1, 0, -1, 2, 6, 2, 'limb')]),
])

# 독버섯 병정 (보라 독버섯 투구, 갑옷)
mob('mush_soldier', 64, 64, {
    'cap': ((120, 60, 150), 'spots'), 'face': ((205, 190, 165), 'eyes'), 'armor': ((110, 100, 90), 'armor'),
    'arm': ((190, 175, 150), 'plain'), 'leg': ((80, 70, 62), 'plain')}, [
    ('head', (0, 0, 0), (0, 0, 0), [(-4, -8, -4, 8, 8, 8, 'face'), (-6, -11, -6, 12, 4, 12, 'cap'), (-4, -12, -4, 8, 1, 8, 'cap')]),
    ('body', (0, 0, 0), (0, 0, 0), [(-4, 0, -2, 8, 12, 4, 'armor')]),
    ('arm_r', (-5, 2, 0), (0, 0, 0), [(-3, -2, -2, 4, 12, 4, 'arm')]),
    ('arm_l', (5, 2, 0), (0, 0, 0), [(-1, -2, -2, 4, 12, 4, 'arm')]),
    ('leg_r', (-1.9, 12, 0), (0, 0, 0), [(-2, 0, -2, 4, 12, 4, 'leg')]),
    ('leg_l', (1.9, 12, 0), (0, 0, 0), [(-2, 0, -2, 4, 12, 4, 'leg')]),
])

# 버섯 요정 (둥근 몸, 작은 갓, 날개)
mob('mush_fairy', 64, 32, {
    'cap': ((230, 120, 170), 'spots'), 'body': ((245, 235, 215), 'eyes'), 'wing': ((190, 240, 255), 'wing')}, [
    ('head', (0, 16, 0), (0, 0, 0), [(-3, -2, -3, 6, 2, 6, 'cap')]),
    ('body', (0, 16, 0), (0, 0, 0), [(-2, 0, -2, 4, 4, 4, 'body')]),
    ('wing_l', (1.5, 17, 1.5), (0, 0, 0), [(0, -4, 0, 6, 6, 0, 'wing')]),
    ('wing_r', (-1.5, 17, 1.5), (0, 0, 0), [(-6, -4, 0, 6, 6, 0, 'wing')]),
])

# 버섯 짐승 (큰 네발, 등에 버섯)
mob('mush_beast', 128, 128, {
    'hide': ((95, 82, 70), 'fur'), 'cap': ((180, 50, 40), 'spots'), 'face': ((85, 72, 62), 'eyes'),
    'horn': ((220, 210, 190), 'plain'), 'leg': ((75, 64, 55), 'plain')}, [
    ('body', (0, 10, 0), (0, 0, 0), [(-7, -6, -11, 14, 12, 22, 'hide'), (-9, -11, -8, 18, 5, 16, 'cap'), (-6, -13, -5, 12, 2, 10, 'cap')]),
    ('head', (0, 9, -11), (0, 0, 0), [(-4, -4, -8, 8, 8, 8, 'face'), (-6, -6, -6, 2, 4, 2, 'horn'), (4, -6, -6, 2, 4, 2, 'horn')]),
    ('leg0', (-5, 16, -8), (0, 0, 0), [(-2.5, 0, -2.5, 5, 8, 5, 'leg')]),
    ('leg1', (5, 16, -8), (0, 0, 0), [(-2.5, 0, -2.5, 5, 8, 5, 'leg')]),
    ('leg2', (-5, 16, 8), (0, 0, 0), [(-2.5, 0, -2.5, 5, 8, 5, 'leg')]),
    ('leg3', (5, 16, 8), (0, 0, 0), [(-2.5, 0, -2.5, 5, 8, 5, 'leg')]),
])

# ───────── 텍스처 배치 (선반 방식) ─────────
import math
def place_all(m):
    W = m['tw']; x = y = shelf = 0; out = []
    for part in m['parts']:
        for c in part[3]:
            w, h, d = c[3], c[4], c[5]
            uw = math.ceil(2 * d + 2 * w) or 1; uh = math.ceil(d + h) or 1
            if x + uw > W: x = 0; y += shelf; shelf = 0
            out.append((x, y)); x += uw; shelf = max(shelf, uh)
    assert y + shelf <= m['th'], f"texture too small"
    return out

def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)

def paint(m, key):
    im = Image.new('RGBA', (m['tw'], m['th']), (0, 0, 0, 0)); px = im.load()
    rnd = random.Random(key)
    offs = place_all(m); i = 0
    for part in m['parts']:
        for c in part[3]:
            u, v = offs[i]; i += 1
            w, h, d = int(math.ceil(c[3])), int(math.ceil(c[4])), int(math.ceil(c[5]))
            base, style = m['palette'][c[6]]
            faces = {  # (x0,y0,fw,fh)
                'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
                'left': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
                'right': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
            for fname, (x0, y0, fw, fh) in faces.items():
                for yy in range(fh):
                    for xx in range(fw):
                        X, Y = x0 + xx, y0 + yy
                        if X >= m['tw'] or Y >= m['th']: continue
                        k = 1.0 + rnd.uniform(-0.07, 0.07)
                        if fname == 'top': k *= 1.08
                        if fname == 'bottom': k *= 0.8
                        col = shade(base, k)
                        if style == 'fur' and rnd.random() < 0.15: col = shade(base, 0.82)
                        if style == 'armor' and (yy % 4 == 0): col = shade(base, 0.75)
                        if style == 'wing':
                            col = col + (150,)
                            if xx == 0 or yy == 0 or xx == fw - 1 or yy == fh - 1: col = shade(base, 0.8) + (220,)
                            px[X, Y] = col; continue
                        if style == 'spots' and fname in ('top', 'front', 'left', 'right', 'back'):
                            if (xx * 7 + yy * 13 + len(fname)) % 11 == 0 or ((xx + 1) * 5 + yy * 3) % 13 == 0:
                                col = (240, 235, 225)
                        px[X, Y] = col + (255,)
                if fname == 'front' and style in ('eyes', 'eyes_small') and fw >= 3 and fh >= 2:
                    ey = y0 + max(0, fh // 3 if style == 'eyes' else 0)
                    ex1, ex2 = x0 + fw // 4, x0 + fw - 1 - fw // 4
                    for ex in (ex1, ex2):
                        px[ex, ey] = (25, 20, 20, 255)
                        if style == 'eyes' and fh >= 6: px[ex, ey + 1] = (25, 20, 20, 255)
                    if style == 'eyes' and fh >= 6 and fw >= 6:
                        for mx in range(x0 + fw // 2 - 1, x0 + fw // 2 + 1): px[mx, ey + 3] = (70, 40, 40, 255)
    return im

def jnum(v):
    s = repr(float(v)); return s + 'F'

def gen_java():
    out = ['package rpg.core.client.entity;', '',
           'import net.minecraft.client.model.geom.PartPose;',
           'import net.minecraft.client.model.geom.builders.CubeListBuilder;',
           'import net.minecraft.client.model.geom.builders.LayerDefinition;',
           'import net.minecraft.client.model.geom.builders.MeshDefinition;',
           'import net.minecraft.client.model.geom.builders.PartDefinition;', '',
           '/** 자동 생성 (tools/mobgen.py) — 새 몹 모양. 직접 고치지 말고 생성기를 고치세요 */',
           'public final class MobModels {', '    private MobModels() {}', '']
    for key, m in MOBS.items():
        offs = place_all(m); i = 0
        fn = ''.join(p.capitalize() for p in key.split('_'))
        out.append(f'    public static LayerDefinition {fn[0].lower() + fn[1:]}() {{')
        out.append('        MeshDefinition mesh = new MeshDefinition();')
        out.append('        PartDefinition root = mesh.getRoot();')
        for name, piv, rot, cubes in m['parts']:
            b = 'CubeListBuilder.create()'
            for c in cubes:
                u, v = offs[i]; i += 1
                b += f'.texOffs({u}, {v}).addBox({jnum(c[0])}, {jnum(c[1])}, {jnum(c[2])}, {jnum(c[3])}, {jnum(c[4])}, {jnum(c[5])})'
            rx, ry, rz = (math.radians(a) for a in rot)
            out.append(f'        root.addOrReplaceChild("{name}", {b},')
            out.append(f'                PartPose.offsetAndRotation({jnum(piv[0])}, {jnum(piv[1])}, {jnum(piv[2])}, {jnum(rx)}, {jnum(ry)}, {jnum(rz)}));')
        out.append(f'        return LayerDefinition.create(mesh, {m["tw"]}, {m["th"]});')
        out.append('    }'); out.append('')
    out.append('}')
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    open(JAVA, 'w').write('\n'.join(out) + '\n')

def gen_bear():
    """산곰: 북극곰 모델 그대로, 갈색 텍스처 (128x64, 북극곰 UV 배치)"""
    m = dict(tw=128, th=64, palette={'f': ((112, 78, 50), 'fur'), 's': ((150, 115, 85), 'eyes_small'), 'e': ((90, 62, 40), 'plain')}, parts=[])
    im = Image.new('RGBA', (128, 64), (0, 0, 0, 0)); px = im.load(); rnd = random.Random('bear')
    def box(u, v, w, h, d, base, eyes=False):
        for (x0, y0, fw, fh, k) in [(u + d, v, w, d, 1.08), (u + d + w, v, w, d, 0.8), (u, v + d, d, h, 1), (u + d, v + d, w, h, 1),
                                    (u + d + w, v + d, d, h, 1), (u + 2 * d + w, v + d, w, h, 0.95)]:
            for yy in range(fh):
                for xx in range(fw):
                    c = shade(base, k * (1 + rnd.uniform(-0.08, 0.08)))
                    if rnd.random() < 0.12: c = shade(c, 0.85)
                    px[x0 + xx, y0 + yy] = c + (255,)
        if eyes:
            px[u + d + 1, v + d + 2] = (20, 15, 10, 255); px[u + d + w - 2, v + d + 2] = (20, 15, 10, 255)
    box(0, 0, 7, 7, 7, (112, 78, 50), eyes=True)
    box(0, 44, 5, 3, 3, (160, 125, 95))
    px[2 + 3, 44 + 3] = (30, 20, 15, 255)
    box(26, 0, 2, 2, 1, (90, 62, 40))
    box(0, 19, 14, 14, 11, (112, 78, 50))
    box(39, 0, 12, 12, 10, (104, 72, 46))
    box(50, 22, 4, 10, 8, (96, 66, 42))
    box(50, 40, 4, 10, 6, (96, 66, 42))
    im.save(os.path.join(TEX, 'brown_bear.png'))

if __name__ == '__main__':
    os.makedirs(TEX, exist_ok=True)
    for k, m in MOBS.items(): paint(m, k).save(os.path.join(TEX, k + '.png'))
    gen_bear()
    gen_java()
    print('ok', list(MOBS))
