"""changelog.json 생성 (런처 "업데이트 기록"용). 사용: python3 ci/make_changelog.py <버전> <이전 changelog.json 또는 ''> <커밋 메시지 파일>
- 이전 릴리스의 changelog.json 에 계속 쌓음 (최신이 위)
- ci/changelog.json (저장소에 직접 쓴 기록) 을 합침: 같은 버전이면 저장소 쪽 문장이 먼저
- 커밋 메시지에서 "player: ..." 로 시작하는 줄 → 이 버전의 changes
- changes 가 하나도 없는 버전은 넣지 않음
- title (런처 패치노트 큰 제목, 60자 이하 글자만): 커밋의 "title: ..." 줄 > ci/changelog.json 의 title > 이전 기록 > 이번 [release] 커밋 첫 줄
  (title 만 있고 changes 가 없는 ci/changelog.json 항목은 그 버전에 제목만 붙임)"""
import json, os, re, sys, datetime

ver, prev_path, msg_path = sys.argv[1], sys.argv[2], sys.argv[3]

def load(p):
    try:
        with open(p, encoding='utf-8') as f: return json.load(f).get('versions', [])
    except Exception: return []

def key(v):
    return tuple(int(x) if x.isdigit() else 0 for x in re.split(r'[.\-]', v))

merged = {}
def clean_title(t):
    t = re.sub(r'<[^>]*>|\*\*|__|`|https?://\S+', '', str(t or ''))
    t = re.sub(r'\[release\]', '', t, flags=re.I)
    t = re.sub(r'\s+', ' ', t).strip(' -·:')
    return t[:60]

TITLES = {}
def add(entry, front=False):
    v = str(entry.get('version', '')).lstrip('v')
    ch = [c.strip() for c in entry.get('changes', []) if str(c).strip()]
    t = clean_title(entry.get('title'))
    if v and t and (front or v not in TITLES): TITLES[v] = t
    if not v or not ch: return
    cur = merged.setdefault(v, {'version': v, 'changes': []})
    if entry.get('date') and not cur.get('date'): cur['date'] = entry['date']
    lst = ch + [c for c in cur['changes'] if c not in ch] if front else cur['changes'] + [c for c in ch if c not in cur['changes']]
    cur['changes'] = lst

for e in load(prev_path): add(e)
for e in load('ci/changelog.json'): add(e, front=True)

lines = []; title_line = ''; first = ''
if msg_path and os.path.exists(msg_path):
    for l in open(msg_path, encoding='utf-8'):
        if not first and '[release]' in l.lower(): first = l.strip()   # git log 는 최신부터 → 이번 [release] 커밋 첫 줄
        m = re.match(r'^\s*player:\s*(.+?)\s*$', l, re.I)
        if m: lines.append(m.group(1))
        m = re.match(r'^\s*title:\s*(.+?)\s*$', l, re.I)
        if m and not title_line: title_line = m.group(1)
if clean_title(title_line): TITLES[ver] = clean_title(title_line)
elif ver not in TITLES and clean_title(first): TITLES[ver] = clean_title(first)
if lines:
    add({'version': ver, 'date': datetime.date.today().isoformat(), 'changes': lines})
if ver in merged and not merged[ver].get('date'):
    merged[ver]['date'] = datetime.date.today().isoformat()

out = {'versions': sorted(merged.values(), key=lambda e: key(e['version']), reverse=True)}
for e in out['versions']:   # 키 순서: version, date, title, changes
    e2 = {'version': e['version']}
    if e.get('date'): e2['date'] = e['date']
    if TITLES.get(e['version']): e2['title'] = TITLES[e['version']]
    e2['changes'] = e['changes']
    e.clear(); e.update(e2)
print(json.dumps(out, ensure_ascii=False, indent=2))
