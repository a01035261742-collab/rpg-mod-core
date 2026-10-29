import numpy as np, json
from collections import deque
H=np.load('H2.npy'); D,W=H.shape; M=json.load(open('meta.json')); SC,OX,OZ,K=M['SC'],M['OFFX'],M['OFFZ'],M['K']
def G(x,z): return int(round((x+OX)*SC)), int(round((z+OZ)*SC))
def T(p): return (62+(p[0]-62)*K, 92+(p[1]-92)*K)
def reach(src):
    seen=np.zeros((D,W),bool); q=deque([src]); seen[src[1],src[0]]=True
    while q:
        x,z=q.popleft()
        for dx,dz in((1,0),(-1,0),(0,1),(0,-1)):
            nx,nz=x+dx,z+dz
            if 0<=nx<W and 0<=nz<D and not seen[nz,nx] and H[nz,nx]-H[z,x]<=1: seen[nz,nx]=True; q.append((nx,nz))
    return seen
s=reach(G(62,92))
for nm,(x,z) in {'농장':T((138,103)),'풍차':(232,110),'초원':T((166,48)),'풀숲3':T((78,20)),'풀숲4':T((24,30)),'아랫마을':(66,150),'출구':(T((22,0))[0],-40)}.items():
    gx,gz=G(x,z); print(nm, s[gz,gx])
