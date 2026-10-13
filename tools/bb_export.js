// Blockbench(MCP execute_script)에서 실행: 열린 프로젝트를 GeckoLib용으로 뽑기
// 결과 geo → assets/rpgcore/geo/entity/<id>.geo.json
//      anim → assets/rpgcore/animations/entity/<id>.animation.json
//      tex  → assets/rpgcore/textures/entity/<id>.png (data URL)
// 애니메이션 이름 규칙: walk · idle · attack / 그룹: head 는 시선 회전
const geo = Codecs.bedrock.compile({raw: true});
const anim = Animator.buildFile(null, true);
const tex = Texture.all[0].getDataURL();
return JSON.stringify({geo, anim, tex});
