# Monta uma prancha (frame 0 de cada folha) a partir de assets/arte: python tools/revisao/prancha.py SAIDA ESCALA POR_LINHA sub/nome...
import sys, json
from PIL import Image
out_path=sys.argv[1]; escala=int(sys.argv[2]); por_linha=int(sys.argv[3]); names=sys.argv[4:]
ims=[]
for n in names:
    im=Image.open(f'assets/arte/{n}.png').convert('RGBA')
    m=json.load(open(f'assets/arte/{n}.json'))
    fw,fh=m['frame']
    i=im.crop((0,0,fw,fh))
    ims.append(i.resize((fw*escala,fh*escala),Image.NEAREST))
rows=[ims[k:k+por_linha] for k in range(0,len(ims),por_linha)]
W=max(sum(i.width for i in r)+10*len(r) for r in rows); H=sum(max(i.height for i in r)+10 for r in rows)
out=Image.new('RGBA',(W,H),(70,78,52,255)); y=0
for r in rows:
    x=0;h=max(i.height for i in r)
    for i in r: out.alpha_composite(i,(x,y+h-i.height)); x+=i.width+10
    y+=h+10
out.save(out_path); print(out.size)
