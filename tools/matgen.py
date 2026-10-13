"""몹 재료 32개: 아이콘 + 이름 + 드롭·판매가·레시피 생성. python3 tools/matgen.py"""
import json, os, collections, random
from PIL import Image, ImageDraw
R = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
A = os.path.join(R, 'assets', 'rpgcore'); D = os.path.join(R, 'data', 'rpgcore')
# (mob, mid level, common id, 한글, 영어, 모양, 색) , (rare ...)
MATS = [
 ('pig', 3, ('pig_hide', '돼지 가죽', 'Pig Hide', 'hide', (225, 160, 150)), ('golden_hoof', '황금 발굽', 'Golden Hoof', 'hoof', (240, 200, 60))),
 ('cow', 3, ('cow_hide', '소가죽', 'Cow Hide', 'hide', (110, 80, 60)), ('cow_horn', '소의 뿔', 'Cow Horn', 'horn', (230, 220, 190))),
 ('slime', 8, ('slime_goo', '슬라임 점액', 'Slime Goo', 'goo', (110, 200, 90)), ('slime_core', '슬라임 핵', 'Slime Core', 'gem', (60, 170, 60))),
 ('rat', 8, ('rat_tail', '쥐 꼬리', 'Rat Tail', 'tail', (215, 150, 150)), ('rat_tooth', '날카로운 앞니', 'Sharp Incisor', 'fang', (240, 230, 200))),
 ('wild_dog', 13, ('dog_fur', '들개 털', 'Wild Dog Fur', 'fur', (170, 160, 150)), ('dog_fang', '들개 송곳니', 'Wild Dog Fang', 'fang', (235, 230, 215))),
 ('bee', 13, ('bee_sting', '벌침', 'Bee Sting', 'sting', (60, 50, 40)), ('royal_jelly', '로열 젤리', 'Royal Jelly', 'goo', (250, 220, 120))),
 ('boar', 18, ('boar_bristle', '거친 갈기', 'Boar Bristle', 'fur', (130, 90, 60)), ('boar_tusk', '멧돼지 엄니', 'Boar Tusk', 'horn', (240, 235, 210))),
 ('bear', 18, ('bear_pelt', '곰 털가죽', 'Bear Pelt', 'hide', (115, 80, 50)), ('bear_paw', '곰 발바닥', 'Bear Paw', 'paw', (95, 65, 40))),
 ('mush_spirit', 23, ('spore_dust', '포자 가루', 'Spore Dust', 'dust', (200, 180, 150)), ('spirit_cap', '정령의 갓', 'Spirit Cap', 'cap', (200, 50, 45))),
 ('mush_kid', 23, ('tiny_cap', '작은 버섯 갓', 'Tiny Cap', 'cap', (220, 70, 60)), ('glow_spore', '반짝이는 포자', 'Glowing Spore', 'gem', (250, 240, 140))),
 ('mush_soldier', 28, ('toxic_cap', '독버섯 조각', 'Toxic Cap Piece', 'cap', (130, 60, 160)), ('soldier_badge', '병정의 휘장', 'Soldier Badge', 'badge', (200, 170, 70))),
 ('mush_fairy', 28, ('fairy_wing', '요정 날개', 'Fairy Wing', 'wing', (190, 240, 255)), ('fairy_dust', '요정 가루', 'Fairy Dust', 'dust', (255, 170, 220))),
 ('spore_spider', 33, ('spore_silk', '포자 거미줄', 'Spore Silk', 'web', (200, 220, 180)), ('spore_gland', '포자 독샘', 'Spore Gland', 'sac', (130, 180, 60))),
 ('spore_witch', 33, ('witch_herb', '마녀의 약초', 'Witch Herb', 'herb', (90, 160, 80)), ('odd_potion', '수상한 물약', 'Odd Potion', 'bottle', (150, 80, 200))),
 ('dark_spider', 38, ('dark_silk', '어둠 거미줄', 'Dark Silk', 'web', (90, 80, 110)), ('dark_eye', '어둠 거미 눈', 'Dark Spider Eye', 'eye', (200, 30, 40))),
 ('mush_beast', 38, ('beast_shell', '짐승의 버섯 껍질', 'Beast Shell', 'cap', (150, 60, 50)), ('beast_core', '거대 버섯 심', 'Giant Mushroom Core', 'gem', (230, 120, 60))),
]
OLD = ['rotten_chunk', 'zombie_heart', 'bone_shard', 'glowing_skull', 'web_bundle', 'venom_sac']

def sh(c, k): return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)
def outline(im, col):
    px = im.load(); o = im.copy(); op = o.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    X, Y = x + dx, y + dy
                    if 0 <= X < 16 and 0 <= Y < 16 and px[X, Y][3] > 0 and px[X, Y][:3] != col[:3]:
                        op[x, y] = col; break
    return o
def icon(shape, c, seed):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im); r = random.Random(seed)
    C = c + (255,); L = sh(c, 1.25); Dk = sh(c, 0.7)
    if shape == 'hide':
        d.polygon([(3, 3), (12, 2), (14, 7), (12, 13), (4, 14), (2, 8)], fill=C); d.line((4, 5, 7, 4), fill=L)
        for _ in range(5): d.point((r.randint(4, 12), r.randint(5, 12)), fill=Dk)
    elif shape == 'hoof':
        d.ellipse((3, 5, 13, 14), fill=C); d.rectangle((7, 5, 9, 14), fill=Dk); d.point([(5, 7), (6, 6)], fill=L)
    elif shape == 'horn':
        d.line((3, 13, 6, 9), fill=C, width=3); d.line((6, 9, 10, 5), fill=C, width=2); d.line((10, 5, 13, 3), fill=L, width=1)
    elif shape == 'goo':
        d.ellipse((3, 7, 13, 14), fill=C); d.ellipse((6, 4, 10, 9), fill=C); d.point([(5, 9), (7, 5)], fill=L)
    elif shape == 'gem':
        d.polygon([(8, 2), (13, 8), (8, 14), (3, 8)], fill=C); d.polygon([(8, 4), (11, 8), (8, 12), (5, 8)], fill=L); d.point((7, 6), fill=(255, 255, 255, 255))
    elif shape == 'tail':
        pts = [(3, 12), (5, 9), (8, 9), (10, 6), (12, 4), (13, 2)]; d.line(pts, fill=C, width=2)
    elif shape == 'fang':
        d.polygon([(6, 2), (10, 2), (9, 10), (8, 14), (7, 10)], fill=C); d.line((7, 3, 7, 9), fill=L)
    elif shape == 'fur':
        for i in range(6): d.line((3 + i * 2, 13, 5 + i * 2 + r.randint(-1, 1), 3 + r.randint(0, 3)), fill=C if i % 2 else L, width=1)
    elif shape == 'sting':
        d.polygon([(3, 13), (12, 3), (13, 4), (5, 14)], fill=C); d.point((12, 3), fill=(230, 230, 230, 255)); d.line((4, 12, 6, 10), fill=(240, 200, 60, 255))
    elif shape == 'paw':
        d.ellipse((4, 7, 12, 14), fill=C)
        for x, y in ((3, 4), (6, 2), (10, 2), (13, 4)): d.ellipse((x - 1, y, x + 1, y + 2), fill=Dk)
    elif shape == 'dust':
        for _ in range(26):
            x, y = r.randint(3, 12), r.randint(4, 13); d.point((x, y), fill=C if r.random() < 0.6 else L)
        d.ellipse((5, 9, 11, 14), fill=sh(c, 0.9))
    elif shape == 'cap':
        d.pieslice((2, 3, 14, 15), 180, 360, fill=C); d.rectangle((7, 9, 9, 14), fill=(230, 220, 200, 255))
        for x, y in ((5, 6), (9, 5), (11, 7)): d.point((x, y), fill=(245, 240, 230, 255))
    elif shape == 'badge':
        d.ellipse((3, 3, 13, 13), fill=C); d.ellipse((5, 5, 11, 11), fill=L); d.line((8, 6, 8, 10), fill=Dk)
    elif shape == 'wing':
        d.polygon([(3, 12), (6, 3), (13, 2), (10, 10)], fill=c + (170,)); d.line((3, 12, 13, 2), fill=sh(c, 0.8))
    elif shape == 'web':
        d.ellipse((3, 3, 13, 13), fill=c + (150,))
        for a in ((8, 3, 8, 13), (3, 8, 13, 8), (4, 4, 12, 12), (12, 4, 4, 12)): d.line(a, fill=L)
    elif shape == 'sac':
        d.ellipse((3, 6, 13, 14), fill=C); d.rectangle((7, 3, 9, 6), fill=Dk); d.point([(5, 8), (6, 8)], fill=L)
    elif shape == 'herb':
        d.line((8, 14, 8, 5), fill=sh(c, 0.7)); d.ellipse((3, 4, 8, 8), fill=C); d.ellipse((8, 2, 13, 6), fill=L); d.ellipse((4, 9, 8, 12), fill=C)
    elif shape == 'bottle':
        d.ellipse((4, 6, 12, 14), fill=C); d.rectangle((7, 3, 9, 6), fill=(200, 200, 210, 255)); d.point((6, 8), fill=(255, 255, 255, 255))
    elif shape == 'eye':
        d.ellipse((3, 4, 13, 12), fill=(240, 240, 240, 255)); d.ellipse((6, 5, 11, 11), fill=C); d.ellipse((8, 7, 9, 9), fill=(10, 10, 10, 255))
    return outline(im, (35, 28, 24, 255))

def main():
    tex = os.path.join(A, 'textures', 'item'); mdl = os.path.join(A, 'models', 'item')
    for o in OLD:
        for p in (os.path.join(tex, o + '.png'), os.path.join(mdl, o + '.json')):
            if os.path.exists(p): os.remove(p)
    names = {}
    for mob, lv, c, rr in MATS:
        for (iid, ko, en, shape, col), rare in ((c, False), (rr, True)):
            icon(shape, col, iid).save(os.path.join(tex, iid + '.png'))
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "rpgcore:item/" + iid}}, open(os.path.join(mdl, iid + '.json'), 'w'))
            names[iid] = (ko, en)
    for lang, i in (('ko_kr', 0), ('en_us', 1)):
        p = os.path.join(A, 'lang', lang + '.json'); d = json.load(open(p), object_pairs_hook=collections.OrderedDict)
        for o in OLD: d.pop('item.rpgcore.' + o, None)
        for k, v in names.items(): d['item.rpgcore.' + k] = v[i]
        ent = {'rat': ('들쥐', 'Rat'), 'brown_bear': ('산곰', 'Brown Bear'), 'mush_kid': ('꼬마 버섯', 'Mushroom Kid'),
               'mush_spirit': ('버섯 정령', 'Mushroom Spirit'), 'mush_soldier': ('독버섯 병정', 'Toxic Mushroom Soldier'),
               'mush_fairy': ('버섯 요정', 'Mushroom Fairy'), 'mush_beast': ('버섯 짐승', 'Mushroom Beast')}
        for k, v in ent.items(): d['entity.rpgcore.' + k] = v[i]
        json.dump(d, open(p, 'w'), ensure_ascii=False, indent=2)
    # 드롭표
    p = os.path.join(D, 'rpg_balance', 'drops.json'); dr = json.load(open(p), object_pairs_hook=collections.OrderedDict)
    dr['mobs'] = collections.OrderedDict((mob, {"items": [[c[0], 40], [rr[0], 3]]}) for mob, lv, c, rr in MATS)
    json.dump(dr, open(p, 'w'), ensure_ascii=False, indent=1)
    # 판매가
    p = os.path.join(D, 'rpg_balance', 'shops.json'); sp = json.load(open(p), object_pairs_hook=collections.OrderedDict)
    sell = collections.OrderedDict()
    for k, v in sp['sell'].items():
        if k not in OLD and k not in ('slime_goo', 'slime_core'): sell[k] = v
    for mob, lv, c, rr in MATS:
        sell[c[0]] = round(2 + lv * 0.25); sell[rr[0]] = round(30 + lv * 3)
    sp['sell'] = sell
    json.dump(sp, open(p, 'w'), ensure_ascii=False, indent=1)
    # 레시피: 희귀 변환 (일반 9 → 희귀 1)
    rd = os.path.join(D, 'recipes')
    for f in os.listdir(rd):
        if '_from_' in f: os.remove(os.path.join(rd, f))
    for mob, lv, c, rr in MATS:
        j = {"type": "rpgcore:gold_shaped", "category": "misc", "gold": 20 + lv, "pattern": ["XXX", "XXX", "XXX"],
             "key": {"X": {"item": "rpgcore:" + c[0]}}, "result": {"item": "rpgcore:" + rr[0], "count": 1}}
        json.dump(j, open(os.path.join(rd, rr[0] + '_from_' + c[0] + '.json'), 'w'), indent=1)
    # Java 등록용 목록
    print('MAT = {' + ', '.join('{"%s", "c"}, {"%s", "r"}' % (c[0], rr[0]) for mob, lv, c, rr in MATS) + '}')

if __name__ == '__main__':
    main()
