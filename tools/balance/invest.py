import json,math
import os; B=os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src/main/resources/data/rpgcore/rpg_balance/')
E=json.load(open(B+'equipment.json')); M=json.load(open(B+'mobs.json')); P=json.load(open(B+'player.json'))
it=E['items']; en=E['enhance']
# "평균보다 약간 아래" 가정 (장비 등급별)
INV={1:dict(ws=2,as_=1,wscroll=4,sscroll=1,pot_main=0,pot_atk=0,flame=1,flines=1),
     10:dict(ws=4,as_=2,wscroll=5,sscroll=1,pot_main=3,pot_atk=3,flame=2,flines=2),
     20:dict(ws=6,as_=3,wscroll=6,sscroll=1,pot_main=6,pot_atk=3,flame=3,flines=2),
     30:dict(ws=8,as_=5,wscroll=7,sscroll=1.5,pot_main=12,pot_atk=6,flame=3,flines=2),
     40:dict(ws=9,as_=6,wscroll=8,sscroll=1.5,pot_main=15,pot_atk=6,flame=4,flines=2)}
def tier(l): return max([1]+[t for t in (10,20,30,40) if t<=l])
def weapon_atk(base,stars):
    a=base
    for s in range(stars): a+=math.floor(a*en['weaponPct']/100)+en['weaponFlat']
    return a
def starstat(n): return sum(en['starStat'][:n])
out=[]
for l in (10,15,20,25,30,35,40,45):
    t=tier(l); I=INV[t]
    base=it[f'warrior_sword_{t}']['stats']['atk']
    w0=base; w1=weapon_atk(base,I['ws'])+I['wscroll']+math.floor(base*0.08*I['flame'])
    main0=4+5*(l-1)+it[f'warrior_sword_{t}']['stats'].get('str',0)
    main0+=sum((it.get(f'warrior_{p}_{t}') or {}).get('stats',{}).get('str',0) for p in ('hat','top','bottom','shoes'))+sum(it.get(f'{p}_{t}',{}).get('stats',{}).get('str',0) for p in ('earring','ring','medal'))
    # 장비 주스탯 (방어구 4 + 장갑·망토·벨트 3 + 장신구 귀걸이·반지4·펜던트·훈장 ~7) 
    npieces=4+3+5
    flame_main=I['flines']*0.5*I['flame']*(0.5+0.025*t)*npieces  # 줄 반쯤 주스탯
    main1=main0+starstat(I['as_'])*npieces+I['sscroll']*5*4+flame_main
    main1*=1+I['pot_main']/100
    d0=w0*main0; d1=w1*main1*(1+I['pot_atk']/100)
    out.append((l,t,w0,w1,round(main0),round(main1),d1/d0))
for r in out: print(f"Lv{r[0]:2} (장비 Lv{r[1]}): 무기공 {r[2]}→{r[3]}  주스탯 {r[4]}→{r[5]}  딜 ×{r[6]:.2f}")
