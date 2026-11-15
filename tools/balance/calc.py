"""
밸런스 계산기 (0.76 코드 기준).  python3 tools/balance/calc.py [--md]

실제 json(player·mobs·equipment·skills·passives·quests) + Combat.java 공식으로 계산.
- 스공 = (주스탯×4 + 부스탯) × 공격력 × (1+공%) / 100 × (1+데미지%)       (Combat.statAttack)
- 한 번 피해 = 스공 × 평균(숙련도~1) × (1 + 크리확률×(크리피해-1)) × (1 - 몹 방어율)   (Combat.roll, defMul)
- 몹 방어율 = defRate (없으면 min(10, Lv/5)), 보스는 내 레벨이 낮으면 1레벨당 penaltyPerLevel%
- 받는 피해 = 몹 공격 × (1 - 방어/(방어+defBase+몹Lv×defPerMobLevel))       (Combat.mobToPlayer)
- 최대 HP = (hpBase + 직업HP×Lv + 체력×hpPerVit + 장비HP) × (1+HP%)       (PlayerData.recalc)

캐릭터 (모두 그 레벨에서 제작 가능한 가장 높은 티어 장비 한 벌 = 직업 방어구 4 + 무기 + 장갑·망토·벨트·귀걸이·반지 4·펜던트, 세트 효과 포함. 훈장 없음):
- 맨몸 : 강화 없음, 패시브 트리 없음
- 트리 : + 패시브 트리 점수의 60%를 딜 노드에 (요구 칸은 무시하고 효율 순, 특수 노드 뺌)
- 강화 : + 트리 + ★5 (STARS, 상한이 낮으면 상한) + 주문서 60%짜리를 횟수의 60% 성공 (잠재·불꽃·버프 없음)
스탯 포인트는 전부 주스탯. 스킬 포인트(레벨당 3)는 1차 주력 → 1차 마스터리 → 2차 공격 스킬 → 2차 익스퍼트 순.

딜 (초당 %): 공격 스킬마다 '1번 쓰는 데 걸리는 시간'(CAST, 아래 표)과 쿨타임으로, 시간 1초를 %/시간 좋은 순으로 채움 (나머지는 평타).
평타 초당 %: 전사 112 · 궁수 70 · 마법사 84 · 도적 126 (Epic Fight 모션 × basicAttackPct, 보고서 13-2 값).
버프 스킬(광폭화·엘리멘탈 앰프·블레스 등)·도트·표식은 뺌 → 이론 최솟값 쪽.
"""
import json, math, os, sys

B = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src/main/resources/data/rpgcore/rpg_balance/')
J = lambda f: json.load(open(B + f))
P = J('player.json'); M = J('mobs.json'); EQ = J('equipment.json'); S = J('skills.json'); PS = J('passives.json')['nodes']
IT = EQ['items']; SETS = EQ['sets']; EN = EQ['enhance']

JOB = {1: ('전사', 'warrior', 'warrior_sword', 'str', 'dex', 'atk'), 2: ('궁수', 'archer', 'archer_bow', 'dex', 'str', 'atk'),
       3: ('마법사', 'mage', 'mage_staff', 'int', 'luk', 'matk'), 4: ('도적', 'thief', 'thief_dagger', 'luk', 'dex', 'atk')}
BRANCH = {(1, 1): '가디언', (1, 2): '버서커', (2, 1): '스나이퍼', (2, 2): '헌터', (3, 1): '아크메이지', (3, 2): '클레릭', (4, 1): '어쌔신', (4, 2): '트릭스터'}
BASIC = {1: 112, 2: 70, 3: 84, 4: 126}
MAIN1 = {1: 'combo_slash', 2: 'double_shot', 3: 'magic_claw', 4: 'double_stab'}
MASTERY1 = {1: 'greatsword_mastery', 2: 'bow_mastery', 3: 'staff_mastery', 4: 'dagger_mastery'}
EXPERT = {1: 'greatsword_expert', 2: 'bow_expert', 3: 'staff_expert', 4: 'dagger_expert'}
# 1번 쓰는 데 걸리는 시간 (초, 모션·조준). 없으면 0.5
CAST = {'power_strike': 0.8, 'double_shot': 0.5, 'magic_claw': 0.5, 'double_stab': 0.5, 'retreat_shot': 0.6,
        'blood_spin': 1.0, 'combo_slash': 0.4, 'leap_slam': 1.0, 'sniping': 1.5, 'mark_shot': 0.5, 'explosive_shot': 0.6,
        'trap': 0.5, 'fireball': 0.6, 'chain_lightning': 0.6, 'holy_arrow': 0.5, 'flurry': 0.8, 'knife_throw': 0.5,
        'poison_mist': 0.6, 'shadow_clone': 0.5}
HITS1 = {'combo_slash': 1}           # 누를 때마다 1타
SKIP = {'taunt', 'iron_wall', 'berserk', 'focus', 'hunt_instinct', 'elemental_amp', 'heal', 'bless', 'shadow_blade', 'plunder', 'meso_guard',
        'iron_body', 'charge_rush', 'booster', 'teleport', 'magic_guard', 'evade_leap', 'haste', 'sprint', 'first_aid'}
TIERS = [1, 10, 20, 30, 40, 50, 60]
STARS = 5   # 강화 캐릭터 ★ (상한이 낮으면 상한)


def sv(k, lv, key='value'):
    s = S[k]; v = s.get(key, 0) + s.get(key + 'PerLevel', 0) * (lv - 1)
    st = s.get('step') if key == 'value' else s.get('step2')
    return round(v / st) * st if st else v


def need(L): return round(P['expBase'] * L ** P['expPower'] + P['expLinear'] * L)


def tier_of(L): return max(t for t in TIERS if t <= L)


def max_star(lv):
    m = 0
    for a, b in EN['maxStars']:
        if lv >= a: m = b
    return m


def add(d, k, v): d[k] = d.get(k, 0) + v


def gear(job, L, enh):
    """장비 합계 (eq 맵) — 무기·방어구·장신구 + 세트 + 강화"""
    name, pre, wpn, main, sub, ak = JOB[job]
    t = tier_of(L); eq = {}
    pieces = [(f'{wpn}_{t}', 'weapon')]
    for s in ('hat', 'top', 'bottom', 'shoes'):
        pieces.append((f'{pre}_{s}_{t}' if t > 1 else f'{s}_1', 'armor'))
    if t >= 10:
        for s in ('gloves', 'cape', 'belt', 'earring', 'pendant'): pieces.append((f'{s}_{t}', 'acc' if s in ('earring', 'pendant') else 'armor'))
        for _ in range(4): pieces.append((f'ring_{t}', 'acc'))
    have = set()
    for key, kind in pieces:
        it = IT.get(key)
        if not it: continue
        have.add(key)
        for k, v in it['stats'].items(): add(eq, k, v)
        if not enh: continue
        lv = it['level']; n = min(STARS, max_star(lv))
        if kind == 'weapon':
            a = it['stats'].get(ak, 0); base = a
            for i in range(n): a += a * EN['weaponPct'] / 100 + EN['weaponFlat']
            add(eq, ak, a - base); add(eq, main, 2 * n)
            ups = EN['upgrades']['weapon']; sc = dict((p, (x, y)) for p, x, y in EN['weaponScroll'])[60]
            add(eq, ak, ups * 0.6 * sc[0]); add(eq, main, ups * 0.6 * sc[1])
        else:
            st = sum(EN['starStat'][:n])
            for k in ('str', 'dex', 'int', 'luk'): add(eq, k, st)
            if 'def' in it['stats']: add(eq, 'def', it['stats']['def'] * EN['defPct'] / 100 * n)
            if it['slot'] == 'gloves':
                g = sum(1 for s in EN['gloveAtkStars'] if s <= n); add(eq, 'atk', g); add(eq, 'matk', g)
            ups = EN['upgrades']['accessory' if kind == 'acc' else 'armor']; sc = dict(EN['statScroll'])[60]
            add(eq, main, ups * 0.6 * sc)
    for sid, s in SETS.items():
        if not isinstance(s, dict): continue
        c = sum(1 for x in s['items'] if x in have)
        for need_n, bon in s.get('bonus', {}).items():
            if c >= int(need_n):
                for k, v in bon.items(): add(eq, k, v)
    return eq


def tree(job, L, branch, share=0.6):
    """패시브 트리: 딜 노드 효율 순 (요구 칸 무시)"""
    pts = int(max(0, L - 9) * P['passivePointsPerLevel'] * share)
    w = {'dmgPct': 1.0, 'atkPct': 1.0, 'critDmg': 0.45, 'critChance': 0.55, JOB[job][3]: 0.18, 'bossPct': 0.3}
    nodes = []
    for n in PS:
        if n.get('maxRank', 0) <= 0 or n.get('special'): continue
        if n.get('job', 0) not in (0, job) or (n.get('job', 0) == 0 and any(x.get('job') == job for x in PS)): continue
        if n.get('branch', 0) not in (0, branch): continue
        if n.get('stat') in w: nodes.append((w[n['stat']] * n['perRank'], n))
    nodes.sort(key=lambda x: -x[0]); b = {}
    for _, n in nodes:
        r = min(n['maxRank'], pts)
        if r <= 0: break
        add(b, n['stat'], n['perRank'] * r); pts -= r
    return b


def skills(job, L, branch):
    pts = 3 * (L - 1); lv = {}
    cap1 = max(0, min(20, 3 * (L - 9))) if L >= 10 else 0
    def give(k, mx):
        nonlocal pts
        g = max(0, min(mx, pts)); lv[k] = g; pts -= g
    if L >= 10:
        give(MAIN1[job], cap1); give(MASTERY1[job], 10)
    if L >= P['jobLevel2']:
        for k, s in S.items():
            if k.startswith('_') or s.get('job') != job or s.get('tier') != 2 or s.get('branch') not in (branch, 0) or k in SKIP: continue
            if s.get('type') == 'passive': continue
            give(k, s.get('maxLevel', 20))
        give(EXPERT[job], 20)
    return {k: v for k, v in lv.items() if v > 0}


def rotation(job, L, branch, sk, targets=1):
    """초당 % (1마리 / targets마리)"""
    opts = []
    for k, lv in sk.items():
        s = S[k]
        if k in SKIP or s.get('type') == 'passive' or k in (MASTERY1[job], EXPERT[job]): continue
        hits = HITS1.get(k, s.get('hits') or 1)
        if k == 'poison_mist': hits = 9
        if k == 'chain_lightning': hits = 1          # 1마리에 1번, 다음 몹으로 튐 (최대 5마리)
        if k == 'combo_slash': hits = 1
        pct = sv(k, lv) * hits
        if k == 'combo_slash': pct = sv(k, lv) + sv(k, lv, 'value2') + sv(k, lv, 'value3')
        aoe = 5 if k == 'chain_lightning' else 1 if k in ('magic_claw', 'holy_arrow', 'sniping', 'mark_shot', 'retreat_shot') else (2 if k == 'double_shot' else targets)
        cd = max(s.get('cooldown', 0) + s.get('cooldownPerLevel', 0) * (lv - 1), 0.01)
        c = CAST.get(k, 0.5)
        if k == 'combo_slash': cd, c = 3 + 3 * 0.4, 1.2
        opts.append((pct * min(aoe, targets) / c, pct * min(aoe, targets), c, cd, k))
    opts.sort(key=lambda x: -x[0]); time = 1.0; total = 0
    for rate, pct, c, cd, k in opts:
        use = min(1 / cd, time / c)       # 1초에 몇 번
        total += use * pct; time -= use * c
        if time <= 1e-9: break
    total += time * BASIC[job] * (min(targets, 3) if job in (1, 3) else 1)
    return total


def build(job, L, branch, prof):
    name, pre, wpn, main, sub, ak = JOB[job]
    eq = gear(job, L, prof == '강화')
    b = tree(job, L, branch) if prof in ('트리', '강화') else {}
    sk = skills(job, L, branch)
    st = {k: P['startStat'] for k in ('str', 'dex', 'int', 'luk', 'vit')}
    st[main] += P['statPointsPerLevel'] * (L - 1)
    tot = lambda k: st.get(k, 0) + eq.get(k, 0) + b.get(k, 0)
    m_, s_ = tot(main), tot(sub)
    atk = eq.get(ak, 0)
    atkpct = eq.get('atkPct', 0) + b.get('atkPct', 0); dmg = eq.get('dmgPct', 0) + b.get('dmgPct', 0)
    sa = (m_ * 4 + s_) * atk * (1 + atkpct / 100) / 100 * (1 + dmg / 100)
    mast = P['masteryByJob'][job] + (sv(MASTERY1[job], sk[MASTERY1[job]]) if MASTERY1[job] in sk else 0) / 100 \
        + (sv(EXPERT[job], sk[EXPERT[job]]) if EXPERT[job] in sk else 0) / 100
    mast = min(0.95, mast)
    crit = min(100, P['critBase'] + P['critPerDex'] * tot('dex') + eq.get('critChance', 0) + b.get('critChance', 0)) / 100
    cdmg = P['critDamage'] + (eq.get('critDmg', 0) + b.get('critDmg', 0)) / 100
    per = sa * (1 + mast) / 2 * (1 + crit * (cdmg - 1))
    hp = (P['hpBase'] + P['hpPerLevelByJob'][job] * L + P['hpPerVit'] * tot('vit') + eq.get('hp', 0)) * (1 + (eq.get('hpPct', 0) + b.get('hpPct', 0)) / 100)
    df = (tot('vit') * P['defPerVit'] + eq.get('def', 0)) * (1 + eq.get('defPct', 0) / 100)
    boss = eq.get('bossPct', 0) + b.get('bossPct', 0)
    return dict(sa=sa, per=per, hp=hp, df=df, boss=boss, sk=sk, crit=crit, mast=mast)


def ground(L):
    for g, v in M['_grounds'].items():
        if v['min'] <= L <= v['max']: return v
    return None


def mobstat(k):
    m = M[k]; lv = m.get('fixedLevel') or int(sorted(m['levels'], key=int)[0]); d = m['levels'][str(lv)]
    dr = m.get('defRate', min(10, lv / 5)); return lv, d, dr


def hunt(job, L, branch, prof):
    c = build(job, L, branch, prof); g = ground(L)
    if not g: return None
    rows = []
    for mk in g['mobs']:
        lv, d, dr = mobstat(mk)
        hit1 = c['per'] * (1 - dr / 100)
        s1 = MAIN1[job]; lv1 = c['sk'].get(s1, 0)
        cast = (sv(s1, lv1) * (S[s1].get('hits') or 1) / 100 * hit1) if lv1 else BASIC[job] / 100 * hit1
        dps = rotation(job, L, branch, c['sk'], 1) / 100 * hit1
        taken = d['atk'] * (1 - c['df'] / (c['df'] + P['defBase'] + lv * P['defPerMobLevel']))
        rows.append(dict(mob=M[mk]['name'], lv=lv, hp=d['hp'], casts=d['hp'] / cast, ttk=d['hp'] / dps, die=c['hp'] / max(1, taken), exp=d['exp']))
    return c, rows


BOSSES = [('forest_lord', 20, 1.0), ('mush_guardian', 40, 1.0), ('spider_queen', 45, 1.0)]


def boss_time(job, L, branch, prof, key, party=1, uptime=0.6):
    c = build(job, L, branch, prof); m = M[key]; bl = m['boss'] if 'boss' in m else {}
    blv = int(sorted(m['levels'], key=int)[0]); d = m['levels'][str(blv)]
    pen = min(bl.get('maxPenalty', 90), max(0, blv - L) * bl.get('penaltyPerLevel', 5)) / 100
    hpmul = 1 + 0.7 * (min(party, 6) - 1)
    dps = rotation(job, L, branch, c['sk'], 1) / 100 * c['per'] * (1 - m.get('defRate', 30) / 100) * (1 + c['boss'] / 100) * (1 - pen) * uptime
    return d['hp'] * hpmul / (dps * party) / 60, blv


def main():
    md = '--md' in sys.argv
    out = []
    pr = out.append
    pr('# 계산 결과 (calc.py)\n')
    pr('## 사냥: 1차 주력 몇 번 / 로테로 몇 초 / 몇 대 맞으면 쓰러짐 (맨몸 · 트리 · 강화)\n')
    pr('| Lv | 직업 | 몹 (Lv · HP) | 주력 횟수 | 로테 초 | 버티는 대수 | 1레벨 마리 |')
    pr('|---|---|---|---|---|---|---|')
    for L in (10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60):
        for job in (1, 2, 3, 4):
            br = 1
            r = [hunt(job, L, br, p) for p in ('맨몸', '트리', '강화')]
            if not r[0]: continue
            for i, row in enumerate(r[0][1]):
                cs = ' / '.join(f"{x[1][i]['casts']:.1f}" for x in r)
                tk = ' / '.join(f"{x[1][i]['ttk']:.1f}" for x in r)
                dd = ' / '.join(f"{x[1][i]['die']:.0f}" for x in r)
                pr(f"| {L} | {JOB[job][0]} | {row['mob']} ({row['lv']} · {row['hp']:,}) | {cs} | {tk} | {dd} | {need(L) / row['exp']:.0f} |")
    pr('\n## 2차 갈래 로테 (Lv45 · 55, 1마리 초당 %, 3마리 초당 %)\n')
    pr('| 갈래 | Lv45 1마리 | Lv45 3마리 | Lv55 1마리 | Lv55 3마리 |')
    pr('|---|---|---|---|---|')
    for (job, br), nm in BRANCH.items():
        vals = []
        for L in (45, 55):
            sk = build(job, L, br, '맨몸')['sk']
            vals += [rotation(job, L, br, sk, 1), rotation(job, L, br, sk, 3)]
        pr(f"| {nm} | " + ' | '.join(f'{v:,.0f}' for v in vals) + ' |')
    pr('\n## 보스 (가동률 60%, 분) — 1명 / 2명 / 4명, 강화 캐릭터 · 갈래 1\n')
    pr('| 보스 (Lv) | 레벨 | 전사 | 궁수 | 마법사 | 도적 |')
    pr('|---|---|---|---|---|---|')
    for key, at, _ in BOSSES:
        for L in (at, at + 5, at + 10):
            cells = []
            for job in (1, 2, 3, 4):
                cells.append(' / '.join(f'{boss_time(job, L, 1, "강화", key, p)[0]:.1f}' for p in (1, 2, 4)))
            pr(f"| {M[key]['name']} ({boss_time(1, L, 1, '강화', key)[1]}) | {L} | " + ' | '.join(cells) + ' |')
    txt = '\n'.join(out)
    print(txt)


if __name__ == '__main__':
    main()
