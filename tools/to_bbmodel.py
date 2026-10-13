"""생성기(mobgen.py) 몹 → Blockbench .bbmodel (Modded Entity, 박스 UV, 텍스처 포함)"""
import sys, os, json, base64, uuid, io, math
sys.path.insert(0, os.path.dirname(__file__))
import mobgen
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
for key, m in mobgen.MOBS.items():
    offs = mobgen.place_all(m); i = 0
    elements, outliner = [], []
    for name, piv, rot, cubes in m['parts']:
        px, py, pz = piv
        children = []
        for c in cubes:
            u, v = offs[i]; i += 1
            x, y, z, w, h, d = c[:6]
            jx0, jy0, jz0 = px + x, py + y, pz + z
            jx1, jy1, jz1 = jx0 + w, jy0 + h, jz0 + d
            eid = str(uuid.uuid4())
            elements.append({"name": c[6], "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
                             "allow_mirror_modeling": True,
                             "from": [-jx1, 24 - jy1, jz0], "to": [-jx0, 24 - jy0, jz1], "autouv": 0, "color": 0,
                             "origin": [-px, 24 - py, pz], "uv_offset": [u, v], "faces": {f: {"uv": [0, 0, 0, 0], "texture": 0}
                             for f in ("north", "east", "south", "west", "up", "down")}, "type": "cube", "uuid": eid})
            children.append(eid)
        outliner.append({"name": name, "origin": [-px, 24 - py, pz],
                         "rotation": [-rot[0], -rot[1], rot[2]], "color": 0, "uuid": str(uuid.uuid4()), "export": True,
                         "isOpen": True, "locked": False, "visibility": True, "autouv": 0, "children": children})
    buf = io.BytesIO(); mobgen.paint(m, key).save(buf, 'PNG')
    src = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
    bb = {"meta": {"format_version": "4.5", "model_format": "modded_entity", "box_uv": True},
          "name": key, "model_identifier": key, "modded_entity_version": "1.17", "modded_entity_flip_y": True,
          "visible_box": [1, 1, 0], "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
          "unhandled_root_fields": {},
          "resolution": {"width": m['tw'], "height": m['th']},
          "elements": elements, "outliner": outliner,
          "textures": [{"path": "", "name": key + ".png", "folder": "", "namespace": "", "id": "0", "particle": False,
                        "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop",
                        "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": False,
                        "uuid": str(uuid.uuid4()), "relative_path": "", "source": src,
                        "width": m['tw'], "height": m['th'], "uv_width": m['tw'], "uv_height": m['th']}]}
    json.dump(bb, open(os.path.join(OUT, key + '.bbmodel'), 'w'), ensure_ascii=False)
print('ok')
