"""Blockbench MCP(tools/bb_export.js) 결과를 이 세션 기록에서 찾아 assets 로 저장.
사용: python3 tools/bb_import.py <id> <세션 jsonl 경로>"""
import json, base64, os, sys
mid, path = sys.argv[1], sys.argv[2]
key = 'geometry.' + mid
found = None
def walk(o):
    global found
    if isinstance(o, str):
        if o.startswith('{"geo":{"format_version"') and key in o: found = o
    elif isinstance(o, dict):
        for v in o.values(): walk(v)
    elif isinstance(o, list):
        for v in o: walk(v)
for line in open(path):
    if key in line: walk(json.loads(line))
d = json.loads(found)
base = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/rpgcore')
for sub in ('geo/entity', 'animations/entity', 'textures/entity'): os.makedirs(os.path.join(base, sub), exist_ok=True)
json.dump(d['geo'], open(f'{base}/geo/entity/{mid}.geo.json', 'w'), indent=1)
json.dump(d['anim'], open(f'{base}/animations/entity/{mid}.animation.json', 'w'), indent=1)
open(f'{base}/textures/entity/{mid}.png', 'wb').write(base64.b64decode(d['tex'].split(',', 1)[1]))
print(mid, list(d['anim']['animations']), len(d['geo']['minecraft:geometry'][0]['bones']), 'bones')
