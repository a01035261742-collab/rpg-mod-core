import numpy as np, sys
src = open(sys.argv[1]).read().split("def save(")[0].replace("print(", "(lambda *a, **k: None)(")
exec(src)
w = V == I['water']; air = V == 0
leak = np.zeros_like(w)
for ax, sh in ((1, 1), (1, -1), (2, 1), (2, -1)):
    leak |= w & np.roll(air, sh, ax)
below = w & np.roll(air, 1, 0)
leak[:, :, [0, -1]] = False; leak[:, [0, -1], :] = False
print(sys.argv[1], '물 블록', int(w.sum()), '옆이 빈 칸(흘러나감)', int(leak.sum()), '아래가 빈 칸', int(below.sum()))
zz = np.argwhere(leak)[:5]; print(zz)
