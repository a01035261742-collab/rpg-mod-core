import numpy as np
from collections import deque
H=np.load('H2.npy'); D,W=H.shape; OFF=20
def reach(src):
    seen=np.zeros((D,W),bool); q=deque([src]); seen[src[1],src[0]]=True
    while q:
        x,z=q.popleft()
        for dx,dz in((1,0),(-1,0),(0,1),(0,-1)):
            nx,nz=x+dx,z+dz
            if 0<=nx<W and 0<=nz<D and not seen[nz,nx] and H[nz,nx]-H[z,x]<=1: seen[nz,nx]=True; q.append((nx,nz))
    return seen
s=reach((62+OFF,92+OFF))
for nm,(x,z) in {'농장':(138,103),'초원':(166,48),'풀숲3':(78,20),'풀숲4':(24,30),'아랫마을':(66,150),'출구':(22,-15)}.items():
    print(nm, s[z+OFF,x+OFF])
