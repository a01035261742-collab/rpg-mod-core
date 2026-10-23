"""아이템 목록 · 몹 목록 (프로젝트 문서용 마크다운) 을 저장소 데이터에서 만든다.
사용: python3 tools/gen_lists.py <아이템 목록.md 출력> <몹 목록.md 출력>
데이터: rpg_balance/equipment·crafts·drops·shops·mobs·quests.json, lang/ko_kr.json, ModItems·EntityClient·Jobs.java
빌드할 때마다 다시 돌려서 프로젝트 문서 "아이템 목록" · "몹 목록" 을 갱신한다."""
import json, re, sys, collections

D = 'src/main/resources/data/rpgcore/rpg_balance'
A = 'src/main/resources/assets/rpgcore'
J = 'src/main/java/rpg/core'
ver = re.search(r'^mod_version=(.+)$', open('gradle.properties').read(), re.M).group(1).strip()

def load(n): return json.load(open(f'{D}/{n}.json'))
eq, crafts, drops, shops, mobs, quests = (load(n) for n in ('equipment', 'crafts', 'drops', 'shops', 'mobs', 'quests'))
ko = json.load(open(f'{A}/lang/ko_kr.json'))

def short(i): return i.split(':', 1)[1] if i.startswith('rpgcore:') else i
VAN = {'minecraft:iron_ingot': '철 주괴', 'minecraft:copper_ingot': '구리 주괴', 'minecraft:gold_ingot': '금 주괴', 'minecraft:diamond': '다이아몬드',
       'minecraft:leather': '가죽', 'minecraft:blue_dye': '청색 염료', 'minecraft:glass_bottle': '유리병', 'minecraft:lapis_lazuli': '청금석'}
def name(i):
    if i in VAN: return VAN[i]
    s = short(i)
    return ko.get(f'item.rpgcore.{s}', s)

SLOT = {'weapon': '무기', 'hat': '모자', 'top': '상의', 'bottom': '하의', 'shoes': '신발', 'gloves': '장갑', 'cape': '망토', 'belt': '벨트',
        'earring': '귀걸이', 'ring': '반지', 'pendant': '펜던트', 'medal': '훈장'}
JOB = {0: '공용', 1: '전사', 2: '궁수', 3: '마법사', 4: '도적'}
STAT = {'str': '힘', 'dex': '민첩', 'int': '지력', 'luk': '운', 'vit': '체력스탯', 'atk': '공격', 'matk': '마력', 'hp': 'HP', 'mp': 'MP',
        'def': '방어', 'allPct': '올스탯%'}
def stats(st): return ' · '.join(f'{STAT.get(k, k)} {v:g}' for k, v in st.items()) or '-'

# ── 얻는 곳 ──
src = collections.defaultdict(list)
for sid, s in shops['shops'].items():
    for it, price in s['items']:
        src[short(it)].append(f'상점 {s["name"]}({sid}) {price:,} G')
station = {k: v['name'] for k, v in crafts['stations'].items()}
recipe_of = {}
for r in crafts['recipes']:
    res = short(r['result'])
    mats = ' + '.join(f'{name(m)} {n}' for m, n in r['mats'])
    g = f' + {r["gold"]:,} G' if r['gold'] else ''
    cnt = f' → {r["count"]}개' if r.get('count', 1) > 1 else ''
    recipe_of[res] = f'{mats}{g}{cnt}'
    src[res].append(f'제작 {station.get(r["station"], r["station"])}')
mobname = {k: v.get('name', k) for k, v in mobs.items() if not k.startswith('_')}
mat_from = {}
for mk, md in drops['mobs'].items():
    for it, ch in md.get('items', []):
        mat_from[short(it)] = (mobname.get(mk, mk), ch)
        src[short(it)].append(f'드롭 {mobname.get(mk, mk)} {ch:g}%')
    for it, ch in md.get('uniques', []):
        src[short(it)].append(f'드롭 {mobname.get(mk, mk)} {ch:g}% (엘리트 ×{mobs["_elite"]["gear"]:g})')
for qk, q in quests['quests'].items():
    for it, n in q.get('reward', {}).get('items', []):
        src[short(it)].append(f'퀘스트 "{q["name"]}" ×{n}')
for job, w in re.findall(r'case (\d) -> "(\w+_10)"', open(f'{J}/job/Jobs.java').read()):
    src[w].append('1차 전직 보상')
for k, v in drops.get('enhance', {}).get('items', {}).items():
    src[k].append(f'드롭 모든 몹 {drops["enhance"]["chance"]:g}% (그중 {v}%)')
# 보스 보상 (mobs.json boss.reward)
for mk, mv in mobs.items():
    if mk.startswith('_') or not isinstance(mv, dict) or 'boss' not in mv: continue
    rw = mv['boss'].get('reward', {})
    bn = mv.get('name', mk)
    if rw.get('sure'): src[rw['sure']].append(f'보스 {bn} 확정')
    if rw.get('ring'): src[rw['ring']].append(f'보스 {bn} {rw.get("ringChance", 0):g}%')
    if rw.get('first'): src[rw['first']].append(f'보스 {bn} 첫 처치')
    if rw.get('fragments'): src['scroll_fragment'].append(f'보스 {bn} ×{rw["fragments"]}')
    if rw.get('scrollChance'): src['scroll'].append(f'보스 {bn} {rw["scrollChance"]:g}%')
sell = {short(k): v for k, v in shops.get('sell', {}).items() if not k.startswith('_')}
used_in = collections.defaultdict(list)
for r in crafts['recipes']:
    for m, n in r['mats']:
        used_in[short(m)].append(name(r['result']))

def where(i): return '<br>'.join(dict.fromkeys(src.get(i, []))) or '(없음)'

out = [f'# 아이템 목록 (모드 {ver})', '',
       '저장소 데이터에서 자동으로 만든 목록 (`tools/gen_lists.py`). 빌드할 때마다 다시 만듦. 수치는 임시값 (밸런스 정리 때 한 번에).', '',
       '- 추가 옵션: 상점 장비 0줄 · 제작·드롭·보상 장비 4줄 (종류·수치 무작위)',
       f'- 몹 전용 장비 엘리트 배수 ×{mobs["_elite"]["gear"]:g} · 몹 재료 엘리트는 두 번 굴림', '']
items = eq['items']
def tier(k): return items[k]['level']
groups = [('무기', lambda k, v: v['slot'] == 'weapon'),
          ('공용 방어구 (상점 일반 장비)', lambda k, v: v['slot'] in ('hat', 'top', 'bottom', 'shoes') and v['job'] == 0),
          ('직업 방어구 (대장장이 제작)', lambda k, v: v['slot'] in ('hat', 'top', 'bottom', 'shoes') and v['job'] != 0),
          ('장신구 (세공사 제작)', lambda k, v: v['slot'] in ('gloves', 'cape', 'belt', 'earring', 'ring', 'pendant') and not any('드롭' in x for x in src.get(k, []))),
          ('몹 전용 장신구 (드롭)', lambda k, v: v['slot'] in ('gloves', 'cape', 'belt', 'earring', 'ring', 'pendant') and any('드롭' in x for x in src.get(k, []))),
          ('훈장', lambda k, v: v['slot'] == 'medal')]
seen = set()
ROWS_I = []
def num(st, *ks): return sum(st.get(k, 0) for k in ks)
for title, f in groups:
    ks = [k for k, v in items.items() if k not in seen and f(k, v)]
    ks.sort(key=lambda k: (items[k]['job'], tier(k), list(SLOT).index(items[k]['slot'])))
    seen.update(ks)
    out += [f'## {title} ({len(ks)}개)', '', '| 이름 | id | 부위 | 직업 | Lv | 기본 능력치 | 얻는 곳 | 제작 재료 | 판매가 |', '|---|---|---|---|---|---|---|---|---|']
    for k in ks:
        v = items[k]
        out.append(f'| {name(k)} | `{k}` | {SLOT[v["slot"]]} | {JOB[v["job"]]} | {v["level"]} | {stats(v["stats"])} | {where(k)} | {recipe_of.get(k, "-")} | {sell.get(k, "-")} |')
        ROWS_I.append({'name': name(k), 'id': k, 'cat': title.split(' (')[0], 'slot': SLOT[v['slot']], 'job': JOB[v['job']], 'lv': v['level'],
                       'stats': stats(v['stats']), 'atk': num(v['stats'], 'atk', 'matk'), 'def': num(v['stats'], 'def'),
                       'src': list(dict.fromkeys(src.get(k, []))), 'recipe': recipe_of.get(k, ''), 'sell': sell.get(k)})
    out.append('')

mat_ids = [m[0] for m in re.findall(r'\{"(\w+)", "([cr])"\}', open(f'{J}/item/ModItems.java').read())]
kinds = dict(re.findall(r'\{"(\w+)", "([cr])"\}', open(f'{J}/item/ModItems.java').read()))
out += [f'## 몹 재료 ({len(mat_ids)}개)', '', '| 이름 | id | 등급 | 드롭 | 판매가 | 쓰는 곳 | 만들기 |', '|---|---|---|---|---|---|---|']
for k in mat_ids:
    mob, ch = mat_from.get(k, ('-', 0))
    uses = ', '.join(dict.fromkeys(used_in.get(k, []))) or '-'
    out.append(f'| {name(k)} | `{k}` | {"일반" if kinds[k] == "c" else "희귀"} | {mob} {ch:g}% | {sell.get(k, "-")} | {uses} | {recipe_of.get(k, "-")} |')
    ROWS_I.append({'name': name(k), 'id': k, 'cat': '몹 재료', 'slot': '일반 재료' if kinds[k] == 'c' else '희귀 재료', 'job': '-', 'lv': None,
                   'stats': '쓰는 곳: ' + uses, 'atk': 0, 'def': 0, 'src': list(dict.fromkeys(src.get(k, []))), 'recipe': recipe_of.get(k, ''), 'sell': sell.get(k)})
out.append('')
other = ['red_potion', 'orange_potion', 'white_potion', 'blue_potion', 'deep_blue_potion', 'mana_elixir', 'elixir', 'power_elixir', 'stat_reset_scroll', 'scroll', 'star_fragment', 'scroll_fragment', 'gold_pouch']
out += ['## 소모품 · 강화 재료 · 기타', '', '| 이름 | id | 얻는 곳 | 만들기 | 판매가 |', '|---|---|---|---|---|']
for k in other:
    out.append(f'| {name(k)} | `{k}` | {where(k)} | {recipe_of.get(k, "-")} | {sell.get(k, "-")} |')
    ROWS_I.append({'name': name(k), 'id': k, 'cat': '소모품·기타', 'slot': '-', 'job': '-', 'lv': None, 'stats': '', 'atk': 0, 'def': 0,
                   'src': list(dict.fromkeys(src.get(k, []))), 'recipe': recipe_of.get(k, ''), 'sell': sell.get(k)})
out.append('')
qitems = re.findall(r'"(quest_\w+)"', open(f'{J}/item/ModItems.java').read())
out += ['## 퀘스트 물건', '', ', '.join(f'{name(k)} (`{k}`)' for k in dict.fromkeys(qitems)), '']
out += ['## 바닐라 재료 (제작에 씀)', '', ', '.join(f'{v} (`{k}`)' for k, v in VAN.items() if k in used_in or short(k) in used_in), '']
open(sys.argv[1], 'w').write('\n'.join(out) + '\n')

# ── 몹 목록 ──
ec = open(f'{J}/client/entity/EntityClient.java').read()
geo = set(re.findall(r'RpgGeoRenderer\(ctx, "(\w+)"', ec))
ground_of = {}
for gk, g in mobs['_grounds'].items():
    for mk in g['mobs']: ground_of[mk] = f'{gk}. {g["name"]} (Lv{g["min"]}~{g["max"]})'
PT = {'slam': '내려찍기(내 주변 원)', 'leap': '점프(대상 자리 원)', 'dash': '돌진(직선)', 'shot': '사격(직선)', 'zone': '장판(대상 자리 원)', 'cone': '앞 부채꼴'}
el = mobs['_elite']; st = mobs.get('_stun', {})
mo = [f'# 몹 목록 (모드 {ver})', '', '저장소 데이터에서 자동으로 만든 목록 (`tools/gen_lists.py`). 빌드할 때마다 다시 만듦. 수치는 임시값.', '',
      f'- 엘리트 {el["chance"]:g}%: HP ×{el["hp"]:g} · 공격 ×{el["atk"]:g} · 경험치·골드 ×{el["reward"]:g} · 전용 장비 ×{el["gear"]:g} · 재료 두 번 굴림 · 경직 방어 +{st.get("eliteArmor", 0):g}',
      f'- 경직: 충격 − 경직 방어 ≥ {st.get("shortMin", 0):g} 짧은 경직 {st.get("shortTicks", 0)}틱 · ≥ {st.get("longMin", 0):g} 긴 경직 {st.get("longTicks", 0)}틱 + 밀림 · {st.get("chainWindow", 0)}틱 안 {st.get("chain", 0)}번 넘으면 {st.get("immuneTicks", 0)}틱 면역', '',
      '| 몹 | key | 몸 | 사냥터 | 레벨 범위 | HP | 공격 | 경험치 | 골드 | 근접 공격 (EF 방식) | 패턴 | 경직 방어 | 재료 드롭 | 전용 장비 |',
      '|---|---|---|---|---|---|---|---|---|---|---|---|---|---|']
ROWS_M = []
order = [m for g in mobs['_grounds'].values() for m in g['mobs']]
for k in order + [k for k in mobs if not k.startswith('_') and k not in order]:
    v = mobs[k]
    lv = sorted(v.get('levels', {}).items(), key=lambda x: int(x[0]))
    def rng(f): return f'{lv[0][1][f]:g}~{lv[-1][1][f]:g}' if lv else '-'
    ent = v.get('entity', '')
    body = 'GeckoLib 모델' if short(ent) in geo or k in geo else ('전용 모델' if ent.startswith('rpgcore:') else f'바닐라 {ent.split(":")[-1]}')
    atk = '<br>'.join(f'{a["anim"]}: {a["start"]}~{a["end"]}틱 {a["range"]:g}칸 ' + (f'{a["angle"]:g}°' if a.get('angle') else f'폭 {a.get("width", 1):g}')
                      + (f' 넉백 {a["knock"]:g}' if a.get('knock') else '') for a in v.get('attacks', [])) or '바닐라 방식'
    pat = '<br>'.join(f'{PT.get(s["type"], s["type"])} 예고 {s.get("delay", 1):g}초 · {s.get("pct", 100):g}% · 쿨 {s.get("cd", 6):g}초' + (f' · {s["effect"]}' if s.get('effect') else '')
                      for s in v.get('skills', [])) or '-'
    dm = drops['mobs'].get(k, {})
    mat = ', '.join(f'{name(i)} {c:g}%' for i, c in dm.get('items', [])) or '-'
    uq = ', '.join(f'{name(i)} {c:g}%' for i, c in dm.get('uniques', [])) or '-'
    def lo(f): return lv[0][1][f] if lv else None
    def hi(f): return lv[-1][1][f] if lv else None
    ROWS_M.append({'name': v.get('name', k), 'id': k, 'body': body, 'ground': ground_of.get(k, '-'), 'lvMin': int(lv[0][0]) if lv else None,
                   'lvMax': int(lv[-1][0]) if lv else None, 'hp': [lo('hp'), hi('hp')], 'atk': [lo('atk'), hi('atk')], 'exp': [lo('exp'), hi('exp')],
                   'gold': [lo('gold'), hi('gold')], 'melee': atk.replace('<br>', ' / '), 'pattern': pat.replace('<br>', ' / '),
                   'stun': v.get('stunArmor', 0), 'mats': mat, 'unique': uq})
    mo.append(f'| {v.get("name", k)} | `{k}` | {body} | {ground_of.get(k, "-")} | {lv[0][0] if lv else "-"}~{lv[-1][0] if lv else "-"} | {rng("hp")} | {rng("atk")} | {rng("exp")} | {rng("gold")} | {atk} | {pat} | {v.get("stunArmor", 0):g} | {mat} | {uq} |')
mo.append('')
open(sys.argv[2], 'w').write('\n'.join(mo) + '\n')
# ── 스킬 (게임에 있는 것 + tools/skills_draft.json 초안) ──
ROWS_S = []
sk = load('skills')
def g(x): return f'{x:g}'
JOBN = {0: '초보자', 1: '전사', 2: '궁수', 3: '마법사', 4: '도적'}
BRN = {(1, 1): '가디언', (1, 2): '버서커', (2, 1): '스나이퍼', (2, 2): '헌터', (3, 1): '아크메이지', (3, 2): '클레릭', (4, 1): '어쌔신', (4, 2): '트릭스터'}
for k, v in sk.items():
    if k.startswith('_'): continue
    mx = v.get('maxLevel', 10)
    def val(lv, key):
        x = v.get(key, 0) + v.get(key + 'PerLevel', 0) * (lv - 1)
        st = v.get('step' if key == 'value' else 'step2', 0) if key in ('value', 'value2') else 0
        return int(x / st + 0.5) * st if st else x
    def hitsn(lv): return max(1, int(v.get('hits', 1) + v.get('hitsPerLevel', 0) * (lv - 1) + 0.5))
    def desc(lv): return v.get('desc', '').replace('{n}', str(hitsn(lv))).replace('{v}', g(round(val(lv, 'value'), 1))).replace('{v2}', g(round(val(lv, 'value2'), 1))).replace('{d}', g(v.get('duration', 0)))
    mp1, mpx = round(v.get('mp', 0)), round(v.get('mp', 0) + v.get('mpPerLevel', 0) * (mx - 1))
    t = v.get('type')
    kind = '패시브' if t == 'passive' else '차지' if t == 'charge' else ('버프' if v.get('duration', 0) > 0 and not v.get('hits') else '액티브')
    cd1 = v.get('cooldown', 0); cdx = cd1 + v.get('cooldownPerLevel', 0) * (mx - 1)
    cd = '-' if t == 'passive' else (f'{g(round(cd1, 1))}초' + (f' → {g(round(cdx, 1))}초' if abs(cdx - cd1) > 1e-6 else '')) if cd1 else '없음'
    ROWS_S.append({'job': JOBN.get(v.get('job', 0), '-'), 'branch': BRN.get((v.get('job', 0), v.get('branch', 0)), '-'),
                   'tier': '초보자' if v.get('tier', 0) == 0 else f'{v.get("tier")}차',
                   'name': v.get('name', k), 'id': k, 'kind': kind, 'max': mx, 'hits': (f'{hitsn(1)} → {hitsn(mx)}' if v.get('hitsPerLevel') else str(v.get('hits', '-'))),
                   'dmg': f"{g(round(val(1, 'value'), 1))}% → {g(round(val(mx, 'value'), 1))}%" if v.get('hits') else '-',
                   'eff': f'Lv1: {desc(1)} → Lv{mx}: {desc(mx)}', 'cd': cd,
                   'mp': '-' if t == 'passive' else f'{mp1} → {mpx}', 'status': '게임에 있음'})
try:
    for d in json.load(open('tools/skills_draft.json'))['skills']:
        ROWS_S.append({**d, 'id': ''})
except FileNotFoundError:
    pass

if len(sys.argv) > 3:   # 표 페이지: tools/lists_page.html 의 /*DATA*/ 자리에 데이터 넣기
    page = open('tools/lists_page.html').read()
    data = json.dumps({'ver': ver, 'items': ROWS_I, 'mobs': ROWS_M, 'skills': ROWS_S, 'elite': mo[4][2:], 'stun': mo[5][2:]}, ensure_ascii=False)
    open(sys.argv[3], 'w').write(page.replace('/*DATA*/null', data.replace('</', '<\\/')))
print('ok', len(items), 'items')
