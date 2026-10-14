import sys, os
from PIL import Image, ImageDraw, ImageFont
def sheet(items, out, cols=4):
    ims=[Image.open(p) for p,_ in items]
    font=ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc',16)
    cw=max(i.width for i in ims); ch=max(i.height for i in ims)+24
    rows=(len(ims)+cols-1)//cols
    s=Image.new('RGB',(cols*cw,rows*ch),(34,36,44)); d=ImageDraw.Draw(s)
    for k,((p,label),im) in enumerate(zip(items,ims)):
        x=(k%cols)*cw; y=(k//cols)*ch
        s.paste(im,(x+(cw-im.width)//2,y+24)); d.text((x+6,y+2),label,fill=(255,220,140),font=font)
    s.save(out); return s.size
