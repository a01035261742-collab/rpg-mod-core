"""최종 블록(V_final)에서 잘라 3D 미리보기"""
import sys, json, numpy as np
sys.path.insert(0, '/home/claude/rpg-mod-core/buildings')
import schem
from schem import S, render2, COL
from PIL import Image, ImageDraw, ImageFont
COL[:0] = [('cobbled_deepslate', (78, 78, 84)), ('deepslate_brick', (70, 70, 76)), ('polished_deepslate', (72, 72, 76)), ('deepslate', (74, 74, 80)), ('smooth_basalt', (72, 70, 78)), ('rooted_dirt', (140, 100, 70)), ('packed_mud', (142, 106, 80)), ('tuff', (106, 108, 98)), ('red_mushroom_block', (196, 48, 44)), ('brown_mushroom_block', (150, 108, 76)), ('mushroom_stem', (214, 206, 190)), ('shroomlight', (244, 160, 76)), ('mycelium', (128, 108, 128)), ('podzol', (106, 76, 40)), ('mud', (60, 56, 60)), ('dark_oak_leaves', (50, 92, 36)), ('dark_oak_log', (66, 45, 26)), ('mangrove', (118, 52, 46)), ('deepslate_tile', (56, 56, 64)), ('red_mushroom', (200, 50, 45)), ('brown_mushroom', (150, 110, 80)), ('birch_leaves', (118, 158, 74)), ('spruce_leaves', (52, 92, 58)), ('dark_oak_leaves', (58, 104, 42)), ('flowering_azalea', (120, 140, 90)),
           ('azalea_leaves', (92, 140, 56)), ('oak_leaves', (72, 128, 52)), ('birch_log', (216, 212, 200)), ('spruce_log', (72, 52, 32)), ('oak_log', (110, 84, 52)),
           ('large_fern', (70, 120, 60)), ('tall_grass', (100, 155, 62)), ('sugar_cane', (140, 190, 95)), ('lily_pad', (55, 125, 45)), ('azure_bluet', (225, 230, 240)),
           ('oxeye_daisy', (240, 240, 225)), ('cornflower', (80, 110, 210)), ('allium', (170, 100, 200)), ('red_tulip', (210, 50, 40)), ('white_tulip', (235, 235, 235)),
           ('pink_tulip', (235, 160, 190)), ('sand', (218, 206, 150)), ('clay', (160, 166, 178)), ('white_terracotta', (214, 196, 178)), ('calcite', (226, 226, 220))]
_ob = schem._boxes
def _nb(st):
    n = st.split(':')[1].split('[')[0]
    if n in ('red_mushroom', 'brown_mushroom'): return [(0.3, 0.7, 0, 0.4, 0.3, 0.7)]
    if n in ('grass', 'fern', 'tall_grass', 'large_fern') or any(k in n for k in ('dandelion', 'poppy', 'cornflower', 'daisy', 'bluet', 'allium', 'tulip')):
        flower = n not in ('grass', 'fern', 'tall_grass', 'large_fern')
        h = 0.95 if n in ('tall_grass', 'large_fern') else 0.6
        return [(0.38, 0.62, 0, 0.45, 0.38, 0.62), (0.3, 0.7, 0.45, 0.7, 0.3, 0.7)] if flower else [(0.25, 0.75, 0, h, 0.25, 0.75)]
    if n == 'sugar_cane': return [(0.3, 0.7, 0, 1, 0.3, 0.7)]
    if n == 'lily_pad': return [(0.05, 0.95, 0, 0.06, 0.05, 0.95)]
    return _ob(st)
schem._boxes = _nb
V = np.load('V_r2.npy', mmap_mode='r'); P = json.load(open('P_r2.json')); HT = np.load('/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/R2_H.npy')
BN = [p.split(':')[1].split('[')[0] for p in P]
skip = np.array([b in ('air', 'barrier', 'cave_air') for b in BN])
PLANT = {'red_mushroom', 'brown_mushroom', 'grass', 'fern', 'tall_grass', 'large_fern', 'sugar_cane', 'lily_pad', 'dandelion', 'poppy', 'cornflower', 'oxeye_daisy', 'azure_bluet', 'allium', 'red_tulip', 'white_tulip', 'pink_tulip'}
ground_like = np.array([not skip[i] and b not in PLANT and 'leaves' not in b and not b.endswith('_log') for i, b in enumerate(BN)])
F = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 28, index=1)

def shot(name, box, sc, title):
    x0, z0, x1, z1 = box
    sub = np.array(V[:, z0:z1, x0:x1]); occ = ~skip[sub]
    Hh = sub.shape[0]
    topn = Hh - 1 - np.argmax(occ[::-1], axis=0)
    s = S('shot')
    # 땅 높이(가장 위 '흙·돌·잔디' 블록) 근처부터 위쪽 전부
    Hs = HT[z0:z1, x0:x1]
    from scipy.ndimage import minimum_filter
    lowm = minimum_filter(Hs, size=3, mode='nearest') - 1
    for z in range(z1 - z0):
        for x in range(x1 - x0):
            col_ = sub[:, z, x]
            for y in range(max(0, int(lowm[z, x])), topn[z, x] + 1):
                b = col_[y]
                if not skip[b]: s.b[(x0 + x, y, z0 + z)] = P[b]
    im = render2(s, f'{name}.png', scale=sc, bg=(150, 190, 230))
    c = Image.new('RGB', (im.width, im.height + 50), (150, 190, 230)); c.paste(im, (0, 50))
    ImageDraw.Draw(c).text((16, 8), title, font=F, fill=(20, 30, 50)); c.save(f'{name}.png'); print(name, c.size, len(s.b))

if __name__ == '__main__':
    for a in sys.argv[1:]:
        name, x0, z0, x1, z1, sc, *t = a.split(':')
        shot(name, (int(x0), int(z0), int(x1), int(z1)), int(sc), ' '.join(t))
