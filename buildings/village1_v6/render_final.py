"""최종 블록(V_final)에서 잘라 3D 미리보기"""
import sys, json, numpy as np
sys.path.insert(0, '/home/claude/rpg-mod-core/buildings')
import schem
from schem import S, render2, COL
from PIL import Image, ImageDraw, ImageFont
COL[:0] = [('birch_leaves', (118, 158, 74)), ('spruce_leaves', (52, 92, 58)), ('dark_oak_leaves', (58, 104, 42)), ('flowering_azalea', (120, 140, 90)),
           ('azalea_leaves', (92, 140, 56)), ('oak_leaves', (72, 128, 52)), ('birch_log', (216, 212, 200)), ('spruce_log', (72, 52, 32)), ('oak_log', (110, 84, 52)),
           ('large_fern', (70, 120, 60)), ('tall_grass', (100, 155, 62)), ('sugar_cane', (140, 190, 95)), ('lily_pad', (55, 125, 45)), ('azure_bluet', (225, 230, 240)),
           ('oxeye_daisy', (240, 240, 225)), ('cornflower', (80, 110, 210)), ('allium', (170, 100, 200)), ('red_tulip', (210, 50, 40)), ('white_tulip', (235, 235, 235)),
           ('pink_tulip', (235, 160, 190)), ('sand', (218, 206, 150)), ('clay', (160, 166, 178)), ('white_terracotta', (214, 196, 178)), ('calcite', (226, 226, 220))]
_ob = schem._boxes
def _nb(st):
    n = st.split(':')[1].split('[')[0]
    if n in ('grass', 'fern', 'tall_grass', 'large_fern') or any(k in n for k in ('dandelion', 'poppy', 'cornflower', 'daisy', 'bluet', 'allium', 'tulip')):
        flower = n not in ('grass', 'fern', 'tall_grass', 'large_fern')
        h = 0.95 if n in ('tall_grass', 'large_fern') else 0.6
        return [(0.38, 0.62, 0, 0.45, 0.38, 0.62), (0.3, 0.7, 0.45, 0.7, 0.3, 0.7)] if flower else [(0.25, 0.75, 0, h, 0.25, 0.75)]
    if n == 'sugar_cane': return [(0.3, 0.7, 0, 1, 0.3, 0.7)]
    if n == 'lily_pad': return [(0.05, 0.95, 0, 0.06, 0.05, 0.95)]
    return _ob(st)
schem._boxes = _nb
V = np.load('V_final.npy', mmap_mode='r'); P = json.load(open('P_final.json'))
BN = [p.split(':')[1].split('[')[0] for p in P]
skip = np.array([b in ('air', 'barrier', 'cave_air') for b in BN])
PLANT = {'grass', 'fern', 'tall_grass', 'large_fern', 'sugar_cane', 'lily_pad', 'dandelion', 'poppy', 'cornflower', 'oxeye_daisy', 'azure_bluet', 'allium', 'red_tulip', 'white_tulip', 'pink_tulip'}
ground_like = np.array([not skip[i] and b not in PLANT and 'leaves' not in b and not b.endswith('_log') for i, b in enumerate(BN)])
F = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 28, index=1)

def shot(name, box, sc, title):
    x0, z0, x1, z1 = box
    sub = np.array(V[:, z0:z1, x0:x1]); occ = ~skip[sub]
    Hh = sub.shape[0]
    topn = Hh - 1 - np.argmax(occ[::-1], axis=0)
    s = S('shot')
    # 땅 높이(가장 위 '흙·돌·잔디' 블록) 근처부터 위쪽 전부
    for z in range(z1 - z0):
        for x in range(x1 - x0):
            col_ = sub[:, z, x]
            g = np.nonzero(ground_like[col_])[0]
            gy = g.max() if len(g) else 0
            nb = []
            for dz, dx in ((1, 0), (0, 1)):
                if z + dz < z1 - z0 and x + dx < x1 - x0:
                    gg = np.nonzero(ground_like[sub[:, z + dz, x + dx]])[0]; nb.append(gg.max() if len(gg) else 0)
            low = min([gy] + nb) - 1
            for y in range(max(0, low), topn[z, x] + 1):
                b = col_[y]
                if not skip[b]: s.b[(x0 + x, y, z0 + z)] = P[b]
    im = render2(s, f'{name}.png', scale=sc, bg=(150, 190, 230))
    c = Image.new('RGB', (im.width, im.height + 50), (150, 190, 230)); c.paste(im, (0, 50))
    ImageDraw.Draw(c).text((16, 8), title, font=F, fill=(20, 30, 50)); c.save(f'{name}.png'); print(name, c.size, len(s.b))

if __name__ == '__main__':
    for a in sys.argv[1:]:
        name, x0, z0, x1, z1, sc, *t = a.split(':')
        shot(name, (int(x0), int(z0), int(x1), int(z1)), int(sc), ' '.join(t))
