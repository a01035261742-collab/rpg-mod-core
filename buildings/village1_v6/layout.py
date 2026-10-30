"""1번 맵 배치 시안: 실제 건물 크기로 자리 찾기 + 위에서 본 건물 그림을 지도에 얹기"""
import sys, json, numpy as np
sys.path.insert(0, '../terrain')
from abld import *
from abld2 import *
from schem import col
from PIL import Image, ImageDraw, ImageFont
T = '/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/terrain/'
from place import place
H = np.load(T + 'H2.npy'); walk = np.load(T + 'walk2.npy'); MAT = np.load(T + 'MAT2.npy', allow_pickle=True)
P1 = (130, 206)
PLAN = [  # (함수, 이름, 원하는 자리, 문 방향(None=광장 쪽), 강제)
    (fountain, '광장 분수 + 게시판', (130, 206), 'S', True),
    (harold, '촌장 해럴드의 집', (128, 180), None, False),
    (inn, '여관', (98, 192), None, False),
    (general_store, '잡화점', (160, 194), None, False),
    (gear_shop, '장비 상점', (162, 222), None, False),
    (well, '우물', (112, 220), None, False),
    (stalls, '시장 노점', (146, 216), None, False),
    (house1, '주민 집 1', (96, 224), None, False),
    (house2, '주민 집 2', (86, 232), None, False),
    (mage, '마법사 탑 (세라)', (80, 236), None, False),
    (smithy, '대장간', (112, 285), None, False),
    (jeweler, '세공소', (160, 282), None, False),
    (alchemy, '연금술 공방', (140, 300), None, False),
    (herb_shop, '약초 가게 (엘린)', (95, 300), None, False),
    (house3, '주민 집 3', (168, 300), None, False),
    (thief, '도적 은신처 (카이)', (180, 315), None, False),
    (fishing_hut, '낚시 오두막 (모리)', (60, 252), 'S', True),
    (warrior, '전사 수련장 (바르크)', (262, 268), None, False),
    (archer, '궁수 수련장', (420, 262), 'W', False),
    (farmhouse, '브람 농가', (262, 212), None, False),
    (barn, '헛간', (290, 226), None, False),
    (granary, '곡물 창고', (250, 236), None, False),
    (windmill, '풍차', (400, 208), 'S', True),
    (guard_post, '경비 초소 (루크)', (322, 152), None, False),
    (hunter_hut, '사냥꾼 오두막 (로완)', (214, 100), None, False),
]
blds = []; items = []
for fn, name, at, face, force in PLAN:
    s = fn(); x0, y0, z0, x1, y1, z1 = s.bounds()
    blds.append(s)
    items.append(dict(name=name, size=(x1 - x0 + 1, z1 - z0 + 1), at=at, face=face, force=force, kind='new'))
res = place(H, walk, MAT, items, P1, roadpad=1)
json.dump(res, open('layout1.json', 'w'), ensure_ascii=False, indent=1)

def topview(s, px):
    x0, y0, z0, x1, y1, z1 = s.bounds()
    W, L = x1 - x0 + 1, z1 - z0 + 1
    im = Image.new('RGBA', (W * px, L * px), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    tops = {}
    for (x, y, z), st in s.b.items():
        if st == 'minecraft:air': continue
        if (x, z) not in tops or y > tops[(x, z)][0]: tops[(x, z)] = (y, st)
    for (x, z), (y, st) in tops.items():
        c = col(st); k = 0.75 + 0.25 * (y - y0) / max(1, y1 - y0)
        c = tuple(int(min(255, v * k)) for v in c)
        d.rectangle([(x - x0) * px, (z - z0) * px, (x - x0 + 1) * px - 1, (z - z0 + 1) * px - 1], fill=c + (255,))
    return im

S_ = 3; OFF = (40, 70); K = 2
base = Image.open(T + 'map1_new.png').convert('RGB')
crop = (40, 60, 440, 330)
im = base.crop((OFF[0] + crop[0] * S_, OFF[1] + crop[1] * S_, OFF[0] + crop[2] * S_, OFF[1] + crop[3] * S_))
im = im.resize((im.width * K, im.height * K), Image.NEAREST)
d = ImageDraw.Draw(im)
F = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 22, index=1)
FS = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 15, index=1)
rot = {'S': 0, 'E': 90, 'N': 180, 'W': 270}
names = {r['name']: r for r in res}
for (fn, name, *_), s in zip(PLAN, blds):
    r = names.get(name)
    if not r: continue
    tv = topview(s, S_ * K).rotate(rot[r['face']], expand=True)
    X = (r['x0'] - crop[0]) * S_ * K; Z = (r['z0'] - crop[1]) * S_ * K
    shadow = Image.new('RGBA', tv.size, (0, 0, 0, 0)); ImageDraw.Draw(shadow).rectangle([0, 0, tv.width, tv.height], fill=(0, 0, 0, 70))
    im.paste(shadow, (int(X) + 4, int(Z) + 4), tv); im.paste(tv, (int(X), int(Z)), tv)
for i, r in enumerate(res):
    cx = (r['x0'] + r['w'] / 2 - crop[0]) * S_ * K; cz = (r['z0'] + r['l'] / 2 - crop[1]) * S_ * K
    d.ellipse([cx - 14, cz - 14, cx + 14, cz + 14], fill=(255, 235, 120), outline=(40, 30, 10), width=2)
    d.text((cx, cz), str(i + 1), font=FS, fill=(30, 20, 10), anchor='mm')
# 범례
rows = (len(res) + 2) // 3; leg = Image.new('RGB', (im.width, 60 + rows * 30), (20, 22, 28)); dl = ImageDraw.Draw(leg)
dl.text((14, 12), '1번 맵 건물 배치 시안 — 실제 건물을 위에서 본 모습 (번호 = 아래 목록)', font=F, fill=(240, 230, 200))
for i, r in enumerate(res):
    cx = 14 + (i % 3) * (im.width // 3); cy = 52 + (i // 3) * 30
    dl.text((cx, cy), f"{i + 1}. {r['name']}  (문 {'남북동서'['SNEW'.index(r['face'])]})", font=FS, fill=(230, 230, 230))
out = Image.new('RGB', (im.width, im.height + leg.height)); out.paste(leg, (0, 0)); out.paste(im, (0, leg.height))
out.save('layout1.png'); print(out.size, len(res), '/', len(PLAN))
