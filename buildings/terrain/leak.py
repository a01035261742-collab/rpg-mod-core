import numpy as np, json
from collections import deque
from scipy.ndimage import distance_transform_edt, gaussian_filter
from PIL import Image
H=np.load('H2.npy').astype(float); MAT=np.load('MAT2.npy',allow_pickle=True); WALK=np.load('walk2.npy'); D,W=H.shape
M=json.load(open('meta.json')); SC,OX,OZ,K=M['SC'],M['OFFX'],M['OFFZ'],M['K']
def G(x,z): return int(round((x+OX)*SC)), int(round((z+OZ)*SC))
T=lambda p:(62+(p[0]-62)*K,92+(p[1]-92)*K)
wallz = gaussian_filter((distance_transform_edt(~WALK) >= 4.5).astype(float), 3) > 0.5
allowed = WALK | ((MAT == 'water') & ~wallz)
dA = distance_transform_edt(~allowed); from scipy.ndimage import maximum_filter
crest=(dA>=maximum_filter(dA,5)-0.5)&(dA>3)&(dA<=11)
ring=((dA>9)&(dA<=11))|crest
def bfs(Hm):
    src=G(62,92); seen=np.zeros((D,W),bool); q=deque([src]); seen[src[1],src[0]]=True
    while q:
        x,z=q.popleft()
        for dx,dz in((1,0),(-1,0),(0,1),(0,-1),(1,1),(1,-1),(-1,1),(-1,-1)):
            nx,nz=x+dx,z+dz
            if 0<=nx<W and 0<=nz<D and not seen[nz,nx] and not ring[nz,nx] and Hm[nz,nx]-Hm[z,x]<=1: seen[nz,nx]=True; q.append((nx,nz))
    return seen
s=bfs(H)
print('바깥 고원 도달 칸', int((s & (dA>12)).sum()), ' / 다닐 곳', int(s.sum()))
H2=H.copy()
for p in [(148,92),(154,76),(158,58)]:
    c=G(*T(p)); H2[c[1]-14:c[1]+14, c[0]-14:c[0]+14]=999
s2=bfs(H2)
for nm,p in {'풀숲3':T((78,20)),'초원':T((166,48))}.items():
    g=G(*p); print(nm,'통로 막았을 때도 감?', s2[g[1],g[0]])
img=np.zeros((D,W,3),np.uint8); img[allowed]=(60,140,60); img[ring]=(255,60,60); img[s & ~allowed]=(255,220,0)
Image.fromarray(img).save('ring.png')
for nm,p in {'농장':T((138,103)),'풍차':(232,110),'초원':T((166,48)),'풀숲3':T((78,20)),'풀숲4':T((24,30)),'아랫마을':(66,150),'연못':(14,128),'출구':(T((22,0))[0],-44)}.items():
    g=G(*p); print(nm, s[g[1],g[0]], end=' | ')
