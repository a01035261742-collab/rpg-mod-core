"""모드 조회 (일회용): 1순위 Modrinth, 없으면 2순위 CurseForge. 1.20.1 Forge/Fabric 버전, 서버 필요 여부, 라이선스, 필수 의존 모드를 찍는다.
CurseForge 는 키가 있어야 조회된다 (CURSEFORGE_API_KEY). 아무것도 설치·수정하지 않고 읽기만 한다."""
import json, os, urllib.request, urllib.parse

MODS = [  # (이름, Modrinth 슬러그, CurseForge 슬러그)
 ('Mobtimizations','mobtimizations','mobtimizations'),('CullLessLeaves','culllessleaves','culllessleaves-reforged'),
 ('Fast Paintings','fast-paintings','fast-paintings'),('AllTheLeaks','alltheleaks','alltheleaks'),
 ('fix GPU memory leak','fix-gpu-memory-leak','fix-gpu-memory-leak'),('GPUTape','gputape','gputape'),
 ('Epic Fight FPS Optimizer','epic-fight-fps-optimizer','epic-fight-fps-optimizer'),
 ('Structure Layout Optimizer','structure-layout-optimizer','structure-layout-optimizer'),
 ('Chunky','chunky','chunky-pregenerator-forge'),('Fast Async World Save','fast-async-world-save','fast-async-world-save-forge-fabric'),
 ('Chunk Sending','chunk-sending','chunk-sending-forge-fabric'),('Async Logger','asynclogger','asynclogger'),
 ('Chloride','chloride','chloride'),('DespawnTweaker','despawntweaker','despawntweaker'),
 ('Immersive Optimization','immersive-optimization','immersive-optimization'),('Radium Reforged','radium-reforged','radium-reforged'),
 ('ServerCore','servercore','servercore'),('Fast NBT','fast-nbt','fast-nbt'),('Smooth Chunk Save','smooth-chunk-save','smooth-chunk-save'),
 ('Enhanced Block Entities','ebe','enhanced-block-entities'),('Icterine','icterine','icterine'),('Flerovium','flerovium','flerovium'),
 ('Quick Pack','quick-pack','quick-pack'),('Get It Together, Drops!','get-it-together-drops','get-it-together-drops'),
 ('Client Crafting','client-crafting','client-crafting'),("Feelix's Soulrend Optimizations",'feelixs-soulrend-optimizations','feelixs-soulrend-optimizations'),
 ('FastEvent','fastevent','fast-event'),('Particle Core','particle-core','particle-core'),
 ('ThreadTweak Reforged','threadtweak-reforged','threadtweak-reforged'),('Cupboard','cupboard','cupboard'),
 ('CoroUtil','coroutil','coroutil'),('Moonlight Lib','moonlight','selene'),('Resourceful Config','resourceful-config','resourceful-config'),
 ('Fzzy Config','fzzy-config','fzzy-config'),('Forgified Fabric API','forgified-fabric-api','forgified-fabric-api'),
 ('Forge Config API Port','forge-config-api-port','forge-config-api-port'),
]
KEY = os.environ.get('CURSEFORGE_API_KEY', '')
UA = {'User-Agent': 'a01035261742-collab/rpg-mod-core lookup'}

def jget(url, headers=None):
    req = urllib.request.Request(url, headers={**UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=60) as r: return json.loads(r.read())
def mr(path): return jget('https://api.modrinth.com/v2' + path)
def cf(path): return jget('https://api.curseforge.com/v1' + path, {'x-api-key': KEY, 'Accept': 'application/json'})
q = lambda o: urllib.parse.quote(json.dumps(o))

def modrinth(name, slug):
    try: p = mr('/project/' + slug)
    except Exception: return None
    row = {'출처': 'Modrinth', 'slug': p['slug'], '클라': p['client_side'], '서버': p['server_side'], '라이선스': p.get('license', {}).get('id')}
    for loader in ('forge', 'fabric'):
        v = mr(f"/project/{p['id']}/version?loaders={q([loader])}&game_versions={q(['1.20.1'])}")
        row[loader] = v[0]['version_number'] if v else None
        if v:
            deps = []
            for d in v[0]['dependencies']:
                if d['dependency_type'] == 'required' and d.get('project_id'):
                    try: deps.append(mr('/project/' + d['project_id'])['slug'])
                    except Exception: deps.append(d['project_id'])
            row[loader + '_필수'] = deps
    return row if (row['forge'] or row['fabric']) else {**row, '메모': '1.20.1 Forge/Fabric 버전이 Modrinth에 없음'}

def curseforge(name, slug):
    if not KEY: return {'출처': 'CurseForge', '오류': '키 없음'}
    res = cf('/mods/search?gameId=432&slug=' + urllib.parse.quote(slug))['data']
    if not res: return {'출처': 'CurseForge', '메모': '찾지 못함'}
    m = res[0]
    row = {'출처': 'CurseForge', 'slug': m['slug'], '이름': m['name'], '다른런처허용': m.get('allowModDistribution'), 'id': m['id']}
    for label, loader in (('forge', 1), ('fabric', 4)):
        idx = [i for i in m.get('latestFilesIndexes', []) if i['gameVersion'] == '1.20.1' and i['modLoader'] == loader]
        if not idx: row[label] = None; continue
        f = cf(f"/mods/{m['id']}/files/{idx[0]['fileId']}")['data']
        row[label] = f['fileName'] + f" (파일 {f['id']})"
        deps = []
        for d in f.get('dependencies', []):
            if d['relationType'] == 3:
                try: deps.append(cf(f"/mods/{d['modId']}")['data']['slug'])
                except Exception: deps.append(str(d['modId']))
        row[label + '_필수'] = deps
        row[label + '_다운로드주소'] = bool(f.get('downloadUrl'))
    return row

for name, ms, cs in MODS:
    try:
        row = modrinth(name, ms)
        if not row or row.get('메모'): row = {**(row or {}), **{'fallback': curseforge(name, cs)}}
    except Exception as e:
        row = {'오류': str(e)[:100]}
    print(json.dumps({'이름': name, **row}, ensure_ascii=False))
