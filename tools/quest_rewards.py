"""퀘스트 초안: 기존 44개 보상 다시 계산 + 새 퀘스트 24개 (대사 포함) → draft.json
보상 규칙 (R2): 경험치 = max(필요경험치(L) × 18%, 40 + 20L) × 배수 (10단위) · 골드 = 160 × L × 배수 (10단위)
선택 보상 (메이플식): choose = 그중 1개 고름
"""
import json, sys
SRC = '/home/claude/rpg-mod-core/src/main/resources/data/rpgcore/rpg_balance/quests.json'
Q = json.load(open(SRC))['quests']
need = lambda L: 22.5 * L * L + 45 * L
r10 = lambda v: int(round(v / 10.0)) * 10

W = {'talk': 0.3, 'deliver': 0.6, 'task': 0.6, 'fetch': 0.8, 'collect': 0.9, 'kill': 1.0, 'rare': 1.5, 'elite': 1.5, 'boss': 2.0}
TNAME = {'talk': '대화', 'deliver': '배달', 'task': '제작·강화', 'fetch': '가져오기', 'collect': '수집', 'kill': '처치', 'rare': '희귀 재료', 'elite': '엘리트', 'boss': '보스'}
RARE = {'dog_fang', 'glow_spore', 'soldier_badge', 'odd_potion', 'golden_hoof', 'slime_core', 'royal_jelly', 'bear_paw', 'spirit_cap', 'fairy_dust', 'dark_eye'}

def typ(v):
    g = v.get('goals', [])
    if v.get('_type'): return v['_type']
    if not g: return 'talk'
    if any(x['type'] == 'kill' and x.get('elite') for x in g): return 'elite'
    if any(x['type'] == 'kill' and x.get('mob') in ('forest_lord', 'mush_guardian') for x in g): return 'boss'
    if any(x['type'] == 'kill' for x in g): return 'kill'
    if any(x['type'] in ('craft', 'enhance') for x in g): return 'task'
    it = g[0].get('item', '')
    if v.get('fetch'): return 'fetch'
    if it in RARE: return 'rare'
    if it.startswith('quest_'): return 'deliver'
    return 'collect'

def potions(L):
    if L <= 20: return 'red_potion', 'blue_potion'
    if L <= 35: return 'orange_potion', 'deep_blue_potion'
    return 'white_potion', 'mana_elixir'

def sgrade(L):   # 주문서 등급: 0=100% 1=60%
    return 0 if L < 15 else 1

def acc_tier(L):
    return 10 if L < 20 else 20 if L < 30 else 30 if L < 38 else 40

def W_CH(g): return [[f'scroll#watk#{g}', 1], [f'scroll#wmatk#{g}', 1]]
def S_CH(g): return [[f'scroll#{s}#{g}', 1] for s in ('str', 'dex', 'int', 'luk')]
def A_CH(t): return [[f'{p}_{t}', 1] for p in ('earring', 'ring', 'pendant', 'belt')]

CHAIN_END = {'q10_elite', 'q15_fang', 'q20_letter', 'q26_glow', 'q30_badge', 'q37_oddpotion', 'q44_alpha'}

def reward(qid, v):
    L = v['level']; t = typ(v); w = W[t]
    if qid in CHAIN_END: w = max(w, 1.5)   # 구간 마지막 연계는 엘리트급
    exp = r10(max(need(L) * 0.18, 40 + 20 * L) * w)
    gold = max(100, r10(160 * L * w))
    hp, mp = potions(L)
    n = {'talk': 5, 'deliver': 10, 'task': 10, 'fetch': 10, 'collect': 10, 'kill': 15, 'rare': 15, 'elite': 20, 'boss': 30}[t]
    items = [[hp, n], [mp, max(5, n // 2)]]
    choose = []
    frag = max(3, L // 2)
    if t in ('deliver', 'fetch'): items.append(['return_scroll', 1])
    if t == 'rare': items.append(['scroll_fragment', frag]); choose = S_CH(sgrade(L))
    if t == 'elite': items.append(['scroll_fragment', frag]); choose = W_CH(sgrade(L))
    if t == 'boss': items.append(['scroll_fragment', frag * 2]); items.append(['cube', 1]); choose = A_CH(acc_tier(L))
    if qid in CHAIN_END and t not in ('boss',):
        choose = A_CH(acc_tier(L))   # 구간 마지막 연계: 장신구 고르기 (주문서 선택 대신)
    if qid == 'q44_alpha' and ['scroll#white', 1] not in items: items.append(['scroll#white', 1])
    if v.get('_extra'): items += v['_extra']
    if v.get('_choose'): choose = v['_choose']
    return {'exp': exp, 'gold': gold, 'items': items, **({'choose': choose} if choose else {})}, t

# ───────── 새 퀘스트 24개 ─────────
NEW = {
 'q45_hoof': {"name": "반짝이는 발굽", "npc": "bram", "level": 3, "summary": "황금 발굽 1개 구해 오기",
   "offer": ["돼지 중에 발굽이 금색으로 반짝이는 녀석이 있더라고.", "그 발굽을 문에 걸어 두면 풍년이 든대. 하나만 구해 줄래?"],
   "progress": ["황금 발굽은 잘 안 나와. 돼지를 꾸준히 잡아 봐."],
   "done": ["이야, 진짜 금빛이네! 올해 농사는 걱정 없겠어."],
   "goals": [{"type": "item", "item": "golden_hoof", "count": 1}]},
 'q46_farm': {"name": "농장 한 바퀴", "npc": "bram", "level": 5, "summary": "돼지 30마리 + 소 30마리 처치", "after": "q05_cows",
   "offer": ["돼지랑 소가 또 울타리를 넘었어. 이번엔 같이 몰려다녀.", "농장 한 바퀴 돌면서 30마리씩만 정리해 줘."],
   "progress": ["울타리 쪽이 제일 심해."],
   "done": ["농장이 이렇게 조용한 건 처음이야. 너 정말 든든하다!"],
   "goals": [{"type": "kill", "mob": "pig", "count": 30}, {"type": "kill", "mob": "cow", "count": 30}]},
 'q47_tails': {"name": "쥐 꼬리 증거", "npc": "luke", "level": 7, "summary": "쥐 꼬리 20개 모아 오기",
   "offer": ["들쥐를 잡았다는 증거가 있어야 위에 보고를 하거든.", "쥐 꼬리 20개만 모아 와 줘."],
   "progress": ["꼬리는 들쥐한테서 나와."],
   "done": ["좋아, 이 정도면 보고서가 두툼해지겠어."],
   "goals": [{"type": "item", "item": "rat_tail", "count": 20}]},
 'q48_core': {"name": "슬라임 핵", "npc": "elin", "level": 8, "summary": "슬라임 핵 1개 구해 오기",
   "offer": ["슬라임 몸속에 단단한 핵이 들어 있을 때가 있어요.", "그걸 갈아 넣으면 연고가 훨씬 잘 들어요. 하나만 부탁해요."],
   "progress": ["핵은 아주 가끔 나와요. 끈기가 필요해요."],
   "done": ["반짝반짝하네요! 이걸로 좋은 연고를 만들 수 있겠어요."],
   "goals": [{"type": "item", "item": "slime_core", "count": 1}]},
 'q49_enhance': {"name": "첫 강화", "npc": "harold", "level": 10, "summary": "장비 스타포스 강화 1번 해 보기", "_type": "task",
   "offer": ["전직했군요! 이제 장비를 더 강하게 만들 차례예요.", "메뉴(M)의 강화에서 스타포스를 한 번 해 봐요. 성공하든 실패하든 괜찮아요."],
   "progress": ["메뉴(M) → 강화 → 스타포스예요."],
   "done": ["해 봤군요! 별이 늘어날수록 장비가 강해져요. 이건 연습용이에요."],
   "goals": [{"type": "enhance", "count": 1, "label": "스타포스 강화 1번"}],
   "_choose": W_CH(0)},
 'q50_craft': {"name": "대장장이 견습", "npc": "harold", "level": 11, "summary": "대장장이에게 장비 1개 만들기", "_type": "task",
   "offer": ["상점 장비보다 대장장이가 만든 장비가 더 좋아요. 추가 옵션이 붙거든요.", "재료를 모아서 대장장이에게 장비를 하나 만들어 봐요."],
   "progress": ["대장장이는 마을 남쪽 대장간에 있어요. 직업 방어구는 소가죽·슬라임 점액으로 만들어요."],
   "done": ["멋진 장비네요! 앞으로도 재료를 모으면 더 좋은 걸 만들 수 있어요."],
   "goals": [{"type": "craft", "station": "smith", "count": 1, "label": "대장장이 제작 1번"}]},
 'q51_fur': {"name": "들개 털 외투", "npc": "rowan", "level": 12, "summary": "들개 털 20개 모아 오기",
   "offer": ["밤에 풀숲에서 잠복하려면 따뜻한 외투가 필요해.", "들개 털 20개면 하나 만들 수 있어."],
   "progress": ["들개 털은 들개한테서 나와."],
   "done": ["털이 촘촘하네. 이걸로 겨울도 버티겠어."],
   "goals": [{"type": "item", "item": "dog_fur", "count": 20}]},
 'q52_jelly': {"name": "로열 젤리", "npc": "elin", "level": 14, "summary": "로열 젤리 1개 구해 오기",
   "offer": ["벌들이 아주 귀한 젤리를 모아 둔대요.", "로열 젤리 하나면 아픈 사람 여럿을 살릴 수 있어요."],
   "progress": ["로열 젤리는 벌한테서 아주 가끔 나와요."],
   "done": ["향이 정말 좋아요… 소중히 쓸게요."],
   "goals": [{"type": "item", "item": "royal_jelly", "count": 1}]},
 'q53_sweep': {"name": "풀숲 대청소", "npc": "rowan", "level": 15, "summary": "들개 30마리 + 벌 30마리 처치", "after": "q16_bees",
   "offer": ["들개랑 벌이 서로 영역 싸움을 하는 바람에 풀숲이 엉망이야.", "둘 다 30마리씩 정리해 줘."],
   "progress": ["벌은 멀리서 쏘니까 가까이 붙어서 잡아."],
   "done": ["이제야 사냥할 맛이 나겠네. 수고했어!"],
   "goals": [{"type": "kill", "mob": "wild_dog", "count": 30}, {"type": "kill", "mob": "bee", "count": 30}]},
 'q54_paw': {"name": "마을 잔치 요리", "npc": "harold", "level": 18, "summary": "곰 발바닥 1개 구해 오기",
   "offer": ["곰을 쫓아낸 기념으로 잔치를 열려고 해요.", "전설의 요리 재료, 곰 발바닥을 하나 구해 줄래요?"],
   "progress": ["곰 발바닥은 곰한테서 아주 가끔 나와요."],
   "done": ["드디어 잔치를 열 수 있겠어요! 꼭 놀러 와요."],
   "goals": [{"type": "item", "item": "bear_paw", "count": 1}]},
 'q55_alpha2': {"name": "깊은 풀숲의 우두머리", "npc": "rowan", "level": 19, "summary": "엘리트 몹 1마리 처치", "after": "q17_boars",
   "offer": ["깊은 풀숲에 별(★) 붙은 녀석이 또 나타났어.", "멧돼지든 곰이든 상관없어. 우두머리 하나만 쓰러뜨려 줘."],
   "progress": ["엘리트는 체력이 다섯 배야. 물약 넉넉히 챙겨."],
   "done": ["해냈구나! 이제 너한테 사냥을 배워야겠는걸."],
   "goals": [{"type": "kill", "mob": "*", "count": 1, "elite": True, "label": "엘리트 몹"}]},
 'q56_lord': {"name": "숲의 군주", "npc": "harold", "level": 20, "summary": "필드 보스 숲의 군주 처치",
   "offer": ["초원 끝 숲에 '숲의 군주'라는 거대한 녀석이 산대요.", "혼자서는 위험해요. 동료들과 함께 쓰러뜨려 줄래요?"],
   "progress": ["숲의 군주는 초원 끝 숲 경기장에 있어요. 파티를 꼭 꾸려요."],
   "done": ["정말 숲의 군주를…! 이 마을의 영웅이에요."],
   "goals": [{"type": "kill", "mob": "forest_lord", "count": 1, "label": "숲의 군주"}]},
 'q57_herbs2': {"name": "두 마을의 약초", "npc": "nera", "level": 22, "summary": "시작마을 엘린에게 약초 상자 전하기",
   "offer": ["시작마을 엘린이랑 약초를 바꿔 쓰기로 했어.", "이 상자 좀 전해 줘. 이동의 돌을 쓰면 금방이야."],
   "progress": ["마을 이동의 돌을 우클릭하면 가 본 마을로 갈 수 있어."],
   "done": ["네라 씨가 보낸 거예요? 버섯 군락 약초는 처음 봐요! 고마워요."],
   "goals": [{"type": "item", "item": "quest_parcel", "count": 1, "label": "약초 상자 전달"}],
   "turnIn": "elin", "give": [["quest_parcel", 1]]},
 'q58_soup': {"name": "작은 버섯 갓 수프", "npc": "nera", "level": 22, "summary": "작은 버섯 갓 20개 모아 오기",
   "offer": ["경비대가 매일 버섯 군락에서 고생하잖아.", "작은 버섯 갓 20개로 따뜻한 수프를 끓여 주려고."],
   "progress": ["작은 버섯 갓은 꼬마 버섯한테서 나와."],
   "done": ["좋아, 오늘 저녁은 버섯 수프다!"],
   "goals": [{"type": "item", "item": "tiny_cap", "count": 20}]},
 'q59_cap': {"name": "정령의 갓", "npc": "morris", "level": 25, "summary": "정령의 갓 1개 구해 오기", "after": "q24_spirits",
   "offer": ["정령들 머리 위 갓에서 아주 가끔 빛이 나요!", "그 갓 하나만 있으면 정령이 어디서 왔는지 알 수 있을 거예요."],
   "progress": ["정령의 갓은 버섯 정령한테서 아주 가끔 나와요."],
   "done": ["와아, 따뜻해요! 정령들이 군락 깊은 곳에서 힘을 받고 있는 것 같아요."],
   "goals": [{"type": "item", "item": "spirit_cap", "count": 1}]},
 'q60_toxic': {"name": "독버섯 조각 분석", "npc": "morris", "level": 27, "summary": "독버섯 조각 20개 모아 오기",
   "offer": ["병정들 몸이 점점 독해지고 있어요.", "독버섯 조각 20개만 있으면 이유를 알아낼 수 있어요!"],
   "progress": ["독버섯 조각은 독버섯 병정한테서 나와요. 독 조심하세요!"],
   "done": ["역시… 누군가 일부러 포자를 퍼뜨리고 있어요."],
   "goals": [{"type": "item", "item": "toxic_cap", "count": 20}]},
 'q61_dust': {"name": "요정 가루", "npc": "nera", "level": 28, "summary": "요정 가루 1개 구해 오기",
   "offer": ["요정들이 흘리는 반짝이 가루 알지?", "그 가루 하나면 물약 효과가 두 배가 돼. 구해 줘."],
   "progress": ["요정 가루는 버섯 요정한테서 아주 가끔 나와."],
   "done": ["이거야 이거! 기대해, 엄청난 물약을 만들어 줄게."],
   "goals": [{"type": "item", "item": "fairy_dust", "count": 1}]},
 'q62_alpha3': {"name": "군락 안쪽 우두머리", "npc": "kael", "level": 30, "summary": "엘리트 몹 1마리 처치", "after": "q27_soldiers",
   "offer": ["군락 안쪽에서 별 붙은 녀석이 병정들을 지휘하고 있어.", "그 우두머리를 쓰러뜨리면 병정들도 흩어질 거야."],
   "progress": ["엘리트는 혼자 상대하기 벅차. 파티를 꾸려."],
   "done": ["병정들이 우왕좌왕하고 있어. 네 덕분이야!"],
   "goals": [{"type": "kill", "mob": "*", "count": 1, "elite": True, "label": "엘리트 몹"}]},
 'q63_silk': {"name": "포자 거미줄", "npc": "nera", "level": 32, "summary": "포자 거미줄 20개 모아 오기",
   "offer": ["포자 거미줄은 질기면서도 약효가 있어.", "20개만 모아 오면 해독 붕대를 만들 수 있어."],
   "progress": ["포자 거미줄은 포자 거미한테서 나와."],
   "done": ["끈적하지만 쓸 만해. 고마워!"],
   "goals": [{"type": "item", "item": "spore_silk", "count": 20}]},
 'q64_alpha4': {"name": "깊은 곳의 우두머리", "npc": "morris", "level": 34, "summary": "엘리트 몹 1마리 처치", "after": "q36_witches",
   "offer": ["깊은 곳에 별 붙은 마녀인지 거미인지가 나타났대요!", "그 녀석이 포자를 퍼뜨리는 것 같아요. 막아 주세요."],
   "progress": ["깊은 곳은 독이 강해요. 해독 물약을 챙기세요."],
   "done": ["포자가 한결 옅어졌어요! 연구가 한 걸음 나아갔어요."],
   "goals": [{"type": "kill", "mob": "*", "count": 1, "elite": True, "label": "엘리트 몹"}]},
 'q65_guardian': {"name": "거대버섯 수호자", "npc": "kael", "level": 35, "summary": "필드 보스 거대버섯 수호자 처치",
   "offer": ["군락 깊은 곳에 거대한 버섯 수호자가 깨어났어.", "그 녀석이 있는 한 포자는 멈추지 않아. 동료들과 함께 쓰러뜨려 줘."],
   "progress": ["수호자는 군락 깊은 곳 경기장에 있어. 패턴을 잘 봐."],
   "done": ["수호자를 쓰러뜨렸다고? 경비대 모두가 너한테 고마워할 거야."],
   "goals": [{"type": "kill", "mob": "mush_guardian", "count": 1, "label": "거대버섯 수호자"}]},
 'q66_eye': {"name": "붉은 눈", "npc": "owen", "level": 37, "summary": "어둠 거미 눈 1개 구해 오기",
   "offer": ["어둠 속에서 붉은 눈이 번쩍이는 걸 봤어.", "그 눈 하나만 가져와. 거미들이 어디서 오는지 알아봐야겠어."],
   "progress": ["어둠 거미 눈은 어둠 거미한테서 아주 가끔 나와."],
   "done": ["이 눈… 보통 거미가 아니야. 더 큰 무언가의 새끼들이야."],
   "goals": [{"type": "item", "item": "dark_eye", "count": 1}]},
 'q67_shell': {"name": "짐승 껍질 갑옷", "npc": "nera", "level": 38, "summary": "짐승의 버섯 껍질 20개 모아 오기",
   "offer": ["버섯 짐승 껍질은 칼도 잘 안 들어가.", "20개만 모아 오면 경비대 갑옷을 보강해 줄 수 있어."],
   "progress": ["짐승의 버섯 껍질은 버섯 짐승한테서 나와."],
   "done": ["단단하다! 경비대가 한숨 돌리겠어."],
   "goals": [{"type": "item", "item": "beast_shell", "count": 20}]},
 'q68_nest': {"name": "둥지의 기척", "npc": "owen", "level": 40, "summary": "어둠 거미 30마리 + 버섯 짐승 30마리 처치", "after": "q66_eye",
   "offer": ["어둠 경계 너머에서 거미 떼가 계속 몰려와.", "거미 30마리, 짐승 30마리만 줄여 줘. 그동안 둥지를 찾아볼게."],
   "progress": ["거미는 멀리서 실을 쏘니까 조심해."],
   "done": ["찾았어. 경계 너머 동굴에 '어둠거미여왕'의 둥지가 있어.", "혼자 갈 곳이 아니야. Lv45가 되면 동료들과 레이드 단말기로 가 봐."],
   "goals": [{"type": "kill", "mob": "dark_spider", "count": 30}, {"type": "kill", "mob": "mush_beast", "count": 30}]},
}

ALL = dict(Q)
for k, v in NEW.items(): ALL[k] = v
out = {}
for k, v in sorted(ALL.items(), key=lambda kv: (kv[1]['level'], kv[0])):
    rw, t = reward(k, v)
    v2 = {kk: vv for kk, vv in v.items() if not kk.startswith('_')}
    v2['reward'] = rw
    old = Q[k]['reward'] if k in Q else None
    out[k] = {'quest': v2, 'type': t, 'new': k not in Q, 'old': old}
json.dump(out, open('/tmp/claude-0/-home-claude/ef3cffc3-4ba8-552d-9a1e-cc3d26fda8a3/scratchpad/quest/draft.json', 'w'), ensure_ascii=False, indent=1)
if '-v' in sys.argv:
    for k, o in out.items():
        q = o['quest']; r = q['reward']
        print(f"{'*' if o['new'] else ' '} {k:16s} L{q['level']:<3}{TNAME[o['type']]:6s} exp {r['exp']:>6} gold {r['gold']:>6} {r['items']} choose={[c[0] for c in r.get('choose', [])]}  (전 {o['old']['exp'] if o['old'] else '-'}/{o['old']['gold'] if o['old'] else '-'})")
print(len(out), 'quests')
