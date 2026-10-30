"""풀숲 위에서 본 비교 (지금 ↔ 시안)"""
import numpy as np, json, sys
from PIL import Image, ImageDraw, ImageFont
src = open('../terrain/vox_iso.py').read(); exec(src[src.index('COLS = ['):src.index("F = '/usr")])
COLS[:0] = [('birch_leaves', (118, 158, 74)), ('spruce_leaves', (52, 92, 58)), ('azalea_leaves', (92, 140, 56)), ('oak_leaves', (72, 128, 52)), ('dark_oak_leaves', (58, 104, 42)),
            ('_log', (100, 78, 50)), ('large_fern', (70, 120, 60)), ('fern', (70, 120, 60)), ('tall_grass', (100, 155, 62)), ('spruce', (100, 74, 46)), ('dark_oak', (70, 50, 30)),
            ('oak_', (150, 120, 75)), ('bricks', (150, 80, 60)), ('hay', (200, 170, 50)), ('carpet', (160, 40, 40)), ('barrel', (120, 90, 55)), ('campfire', (200, 120, 40))]
PL = ('grass', 'fern', 'dandelion', 'poppy', 'cornflower', 'oxeye_daisy', 'azure_bluet', 'allium', 'red_tulip', 'white_tulip', 'pink_tulip')
def topimg(Vf, Pf, box, sc):
    x0, z0, x1, z1 = box
    sub = np.array(Vf[:, z0:z1, x0:x1]); bn = [p.split(':')[1].split('[')[0] for p in Pf]
    sk = np.array([b in ('air', 'barrier') or b in PL for b in bn])
    occ = ~sk[sub]; top = sub.shape[0] - 1 - np.argmax(occ[::-1], 0)
    ids = sub[top, np.arange(z1 - z0)[:, None], np.arange(x1 - x0)[None]]
    cols = np.array([col(p) for p in Pf], float)
    arr = cols[ids]; gx, gz = np.gradient(top.astype(float)); arr *= np.clip(1 + 0.25 * (-gx - 1.2 * gz) / 1.5, .6, 1.3)[..., None]
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).resize(((x1 - x0) * sc, (z1 - z0) * sc), Image.NEAREST)
box = (18, 25, 215, 155); sc = 5
a = topimg(np.load('V_final_v1.npy', mmap_mode='r'), json.load(open('P_final_v1.json')), box, sc)
b = topimg(np.load('V_final.npy', mmap_mode='r'), json.load(open('P_final.json')), box, sc)
F = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 30, index=1)
Fs = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 40, index=1)
d = ImageDraw.Draw(b)
for c in json.load(open('../terrain/clearings.json')):
    x, z = c['center']; X, Y = (x - box[0]) * sc, (z - box[1]) * sc; t = c['name'][0]
    d.ellipse([X - 30, Y - 30, X + 30, Y + 30], outline=(255, 240, 120), width=5); d.text((X - 13, Y - 26), t, font=Fs, fill=(255, 240, 120), stroke_width=3, stroke_fill=(30, 30, 30))
W_, H_ = a.width, a.height
out = Image.new('RGB', (W_ * 2 + 20, H_ + 60), (30, 32, 36)); out.paste(a, (0, 60)); out.paste(b, (W_ + 20, 60))
D = ImageDraw.Draw(out)
D.text((14, 10), '지금 (빽빽)', font=F, fill=(255, 255, 255)); D.text((W_ + 34, 10), sys.argv[2] if len(sys.argv) > 2 else '시안', font=F, fill=(255, 240, 120))
out.save(sys.argv[1]); print(out.size)
