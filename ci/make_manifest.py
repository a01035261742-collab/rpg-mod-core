"""manifest.json 생성 (런처 자동 업데이트용). 사용: python3 ci/make_manifest.py <jar 경로>
출력 형식 (schema 1):
  pack, version, minecraft, loader{type,version}, java,
  files[]: path(게임 폴더 기준, mods/ 등), url, sha256, size, optional"""
import hashlib, json, os, sys, urllib.request, urllib.parse

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
          'sha256': sha256(data), 'size': len(data), 'optional': False}]

pack = json.load(open('pack/mods.json')) if os.path.exists('pack/mods.json') else {}
extra = pack.get('mods', [])
forge = pack.get('forge') or forge
for m in extra:
    slug = m['modrinth']
    try:
        q = urllib.parse.urlencode({'loaders': '["forge"]', 'game_versions': f'["{mc}"]'})
        vers = json.loads(get(f'https://api.modrinth.com/v2/project/{slug}/version?{q}'))
        pick = next((v for v in vers if not m.get('version') or m['version'] in v['version_number']), None)
        if not pick: raise RuntimeError(f'{mc} Forge 판 {m.get("version") or "최신"} 없음')
        f = next((x for x in pick['files'] if x.get('primary')), pick['files'][0])
        blob = get(f['url'])
        files.append({'path': f'mods/{f["filename"]}', 'url': f['url'], 'sha256': sha256(blob),
                      'size': len(blob), 'optional': bool(m.get('optional'))})
        print(f'[manifest] {slug} → {f["filename"]}', file=sys.stderr)
    except Exception as e:
        print(f'::warning title=manifest::{slug} 건너뜀 ({e})', file=sys.stderr)

print(json.dumps({'schema': 1, 'pack': 'rpg-mod', 'version': ver, 'minecraft': mc,
                  'loader': {'type': 'forge', 'version': forge}, 'java': 17, 'files': files},
                 ensure_ascii=False, indent=2))
