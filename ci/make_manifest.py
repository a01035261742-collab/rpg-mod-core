"""manifest.json 생성 (런처 자동 업데이트용). 사용: python3 ci/make_manifest.py <jar 경로>
출력 형식 (schema 1):
  pack, version, minecraft, loader{type,version}, java,
  files[]: path(게임 폴더 기준, mods/ 등), url, sha256, size, optional, side(both|client|server)"""
import hashlib, json, os, re, sys, urllib.request, urllib.parse

REPO = os.environ.get('GITHUB_REPOSITORY', 'a01035261742-collab/rpg-mod-core')
props = dict(l.strip().split('=', 1) for l in open('gradle.properties') if '=' in l and not l.startswith('#'))
ver, mc, forge = props['mod_version'], props['minecraft_version'], props['forge_version']
jar = sys.argv[1]

def sha256(data): return hashlib.sha256(data).hexdigest()
def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': f'{REPO} manifest builder'})
    with urllib.request.urlopen(req, timeout=60) as r: return r.read()

data = open(jar, 'rb').read()
name = os.path.basename(jar)
files = [{'path': f'mods/{name}', 'url': f'https://github.com/{REPO}/releases/download/v{ver}/{name}',
          'sha256': sha256(data), 'size': len(data), 'optional': False, 'side': 'both'}]

pack = json.load(open('pack/mods.json')) if os.path.exists('pack/mods.json') else {}
extra = pack.get('mods', [])
forge = pack.get('forge') or forge
CF_KEY = os.environ.get('CURSEFORGE_API_KEY', '')
def cf_get(path):
    # CurseForge API 는 키 필요. 키 없으면 멈춤 (빠진 모드가 있는 목록을 올리지 않게)
    if not CF_KEY: sys.exit('CURSEFORGE_API_KEY가 없어요. 저장소 Secrets에 넣어 주세요.')
    req = urllib.request.Request(f'https://api.curseforge.com/v1{path}', headers={'x-api-key': CF_KEY, 'Accept': 'application/json'})
    with urllib.request.urlopen(req, timeout=60) as r: return json.loads(r.read())['data']
SAFE_JAR = re.compile(r'^[A-Za-z0-9._+()\-\[\] ]{1,150}\.jar$')
SAFE_ZIP = re.compile(r'^[A-Za-z0-9._+()\-\[\] ]{1,150}\.zip$')

# CurseForge 모드 (런처 0.1.0-beta.15 이상 필요 — 친구들 런처 업데이트 확인 후에만 mods.json 에 넣을 것). 파일 번호 고정.
for m in extra:
    if 'curseforge' not in m: continue
    mod = cf_get(f"/mods/{m['curseforge']}")
    if mod.get('allowModDistribution') is False: sys.exit(f"{mod['name']}: 제작자가 다른 런처에서 받기를 막아 두었어요.")
    f = cf_get(f"/mods/{m['curseforge']}/files/{m['file']}")
    if not f.get('downloadUrl'): sys.exit(f"{mod['name']}: 다운로드 주소가 없어요 (배포가 막힌 파일).")
    gv = f.get('gameVersions', [])
    if mc not in gv or 'Forge' not in gv: sys.exit(f"{mod['name']}: 이 파일은 {mc} Forge용이 아니에요.")
    if not SAFE_JAR.match(f['fileName']): sys.exit(f"{mod['name']}: 런처가 받지 않는 파일 이름 ({f['fileName']})")
    blob = get(f['downloadUrl'])
    files.append({'path': f"mods/{f['fileName']}", 'url': f['downloadUrl'], 'sha256': sha256(blob), 'size': len(blob),
                  'optional': bool(m.get('optional')), 'side': m.get('side', 'both')})
    print(f"[manifest] CurseForge {mod['name']} → {f['fileName']}", file=sys.stderr)

for m in extra:
    if 'modrinth' not in m: continue
    slug = m['modrinth']
    try:
        q = urllib.parse.urlencode({'loaders': '["forge"]', 'game_versions': f'["{mc}"]'})
        vers = json.loads(get(f'https://api.modrinth.com/v2/project/{slug}/version?{q}'))
        pick = next((v for v in vers if not m.get('version') or m['version'] in v['version_number']), None)
        if not pick: raise RuntimeError(f'{mc} Forge 판 {m.get("version") or "최신"} 없음')
        f = next((x for x in pick['files'] if x.get('primary')), pick['files'][0])
        blob = get(f['url'])
        files.append({'path': f'mods/{f["filename"]}', 'url': f['url'], 'sha256': sha256(blob),
                      'size': len(blob), 'optional': bool(m.get('optional')), 'side': m.get('side', 'both')})
        print(f'[manifest] {slug} → {f["filename"]}', file=sys.stderr)
    except Exception as e:
        print(f'::warning title=manifest::{slug} 건너뜀 ({e})', file=sys.stderr)

# 리소스팩·셰이더팩 (런처 0.1.0-beta.20 이상 필요 — 친구들 런처 업데이트 확인 후에만 mods.json 에 넣을 것). Modrinth 버전 ID 고정.
for folder in ('resourcepacks', 'shaderpacks'):
    for rp in pack.get(folder, []):
        v = json.loads(get(f"https://api.modrinth.com/v2/version/{rp['version']}"))
        f = next((x for x in v['files'] if x.get('primary')), v['files'][0])
        nm = rp.get('name') or f['filename']
        if not SAFE_ZIP.match(nm): sys.exit(f'{folder}/{nm}: 런처가 받지 않는 파일 이름이에요. mods.json 에 "name" 을 적어 주세요.')
        blob = get(f['url'])
        files.append({'path': f'{folder}/{nm}', 'url': f['url'], 'sha256': sha256(blob), 'size': len(blob), 'optional': False, 'side': 'client'})
        print(f'[manifest] {folder}/{nm}', file=sys.stderr)

# 같이 깔 설정 파일 (pack/mods.json "files"): 커밋 기준 raw 주소
sha_ref = os.environ.get('GITHUB_SHA', 'main')
for c in pack.get('files', []):
    try:
        blob = open(c['src'], 'rb').read()
        files.append({'path': c['path'], 'url': f'https://raw.githubusercontent.com/{REPO}/{sha_ref}/{c["src"]}', 'sha256': sha256(blob),
                      'size': len(blob), 'optional': bool(c.get('optional')), 'side': c.get('side', 'both')})
        print(f'[manifest] 설정 {c["path"]}', file=sys.stderr)
    except Exception as e:
        print(f'::warning title=manifest::설정 {c.get("src")} 건너뜀 ({e})', file=sys.stderr)

print(json.dumps({'schema': 1, 'pack': 'rpg-mod', 'version': ver, 'minecraft': mc,
                  'loader': {'type': 'forge', 'version': forge}, 'java': 17, 'files': files},
                 ensure_ascii=False, indent=2))
