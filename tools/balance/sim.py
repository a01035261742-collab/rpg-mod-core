import json,math
import os; B=os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src/main/resources/data/rpgcore/rpg_balance/')
P=json.load(open(B+'player.json')); M=json.load(open(B+'mobs.json')); E=json.load(open(B+'equipment.json'))['items']
Q=json.load(open(B+'quests.json'))['quests']
def need(l):
    import json as _j; _p=_j.load(open(B+"player.json")); return round(_p["expBase"]*l**_p["expPower"]+_p["expLinear"]*l)   # 실제 player.json 공식
# zone mob for level: pick mob whose area covers
zones=[(1,5,'pig'),(6,10,'slime'),(11,15,'wild_dog'),(16,20,'boar'),(21,25,'mush_spirit'),(26,30,'mush_soldier'),(31,35,'spore_spider'),(36,45,'dark_spider')]
def mob(l):
    for a,b,k in zones:
        if a<=l<=b:
            L=M[k]['levels']; ks=sorted(int(x) for x in L); lv=max([x for x in ks if x<=l] or [ks[0]]); d=L[str(lv)]; return k,lv,d
def tier(l): return max([1]+[t for t in (10,20,30,40) if t<=l])
jobs={1:('warrior_sword','str','atk'),2:('archer_bow','dex','atk'),3:('mage_staff','int','matk'),4:('thief_dagger','luk','atk')}
mainskill={1:(300,457.9,1,4),2:(90,160,2,0.6),3:(80,150,2,0.6),4:(90,160,2,0.6)} # lv1,lv20 %, hits, cd
quest_exp={}
for k,v in Q.items(): quest_exp[v['level']]=quest_exp.get(v['level'],0)+v['reward']['exp']
print('Lv job  stAtk  hitAvg  mob(lv) HP  def%  hitsToKill  kills/lv  needExp  questExp  pHP  mobHit  hitsToDie')
for job in (1,2,3,4):
  for l in (5,10,15,20,25,30,35,40,45):
    w,st,ak=jobs[job]; t=tier(l)
    wep=E[f'{w}_{t}']['stats']; watk=wep[ak]
    main=4+5*(l-1)+wep.get(st,0)
    gear_main=0
    pre={1:'warrior',2:'archer',3:'mage',4:'thief'}[job]
    for s in ('hat','top','bottom','shoes'):
        it=E.get(f'{pre}_{s}_{t}') if t>1 else E.get(f'{s}_1'); gear_main+=(it or {}).get('stats',{}).get(st,0)
    acc=sum(E.get(f'{s}_{t}',{}).get('stats',{}).get(st,0) for s in ('earring','ring','medal'))
    main+=gear_main+acc
    sub=4+acc
    mastery=P['masteryByJob'][job]+ (0.2 if l>=15 else 0.1 if l>=12 else 0)+(0.25 if l>=40 else 0.15 if l>=35 else 0)
    sa=(main*4+sub)*watk/100
    crit=0.05+0.001*(main if job in(2,4) else 4)
    avg=sa*(1+min(.95,mastery))/2*(1+crit*.5)
    if l<10: pct,hits,cd=100*0.7 if job else 100,1,0.5
    else:
        a,b,hits,cd=mainskill[job]; sl=min(20,(l-10)*3//1); pct=a+(b-a)*min(20,sl)/20
    k,ml,d=mob(l); defr=min(10,ml/5)
    hit=avg*pct/100*(1-defr/100)
    htk=math.ceil(d['hp']/(hit*hits)) if l>=10 else math.ceil(d['hp']/hit)
    kills=need(l)/d['exp']
    vit=4; php=50+10*l+vit*5+sum(E.get(f'{s}_{t}',{}).get('stats',{}).get('hp',0) for s in ('cape','belt','pendant'))
    defv=vit+sum((E.get(f'{pre}_{s}_{t}') or E.get(f'{s}_{t}') or {}).get('stats',{}).get('def',0) for s in('hat','top','bottom','shoes'))+sum(E.get(f'{s}_{t}',{}).get('stats',{}).get('def',0) for s in('gloves','cape','belt'))
    mh=d['atk']*(1-defv/(defv+50+ml*10))
    print(f"{l:2} {job}  {sa:6.0f} {hit:6.0f}  {k[:10]}({ml}) {d['hp']:5} {defr:3.0f}  {htk:3}  {kills:6.0f}  {need(l):6}  {quest_exp.get(l,0):5}  {php:4} {mh:5.1f} {php/mh:5.1f}")
  print()
