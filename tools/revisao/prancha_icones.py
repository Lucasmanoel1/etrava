# Prancha dos ícones de item (V6): os 43 a 1× (sobre o papel claro e sobre fundo escuro, como na
# interface) e a 4× com o nome. Lê a ordem de content/items.yaml e as folhas de assets/arte/itens.
#   python tools/revisao/prancha_icones.py [SAIDA]   (padrão: docs/capturas/v6/prancha_icones.png)
import os
import sys

import yaml
from PIL import Image, ImageDraw

saida = sys.argv[1] if len(sys.argv) > 1 else "docs/capturas/v6/prancha_icones.png"
ids = [i["id"] for i in yaml.safe_load(open("content/items.yaml", encoding="utf-8"))]
ims = {}
for i in ids:
    p = f"assets/arte/itens/{i}.png"
    ims[i] = Image.open(p).convert("RGBA") if os.path.exists(p) else Image.new("RGBA", (32, 32), (255, 0, 255, 255))

COLS, G = 11, 4
PAPEL, ESCURO, FUNDO = (216, 204, 176, 255), (42, 36, 40, 255), (58, 54, 52, 255)
linhas = (len(ids) + COLS - 1) // COLS
# faixa 1×: ícones com 4 px de vão, sobre papel e sobre escuro
w1 = COLS * (32 + G) + G
alto4 = 32 * 4 + 14
W = max(w1 * 2 + 24, COLS * (32 * 4 + 8) + 8)
H = 8 + linhas * (32 + G) + G + 16 + linhas * (alto4 + 6) + 8
out = Image.new("RGBA", (W, H), FUNDO)
d = ImageDraw.Draw(out)
for bloco, cor in ((0, PAPEL), (1, ESCURO)):
    x0 = 8 + bloco * (w1 + 8)
    d.rectangle([x0, 8, x0 + w1 - 1, 8 + linhas * (32 + G) + G - 1], fill=cor)
    for k, i in enumerate(ids):
        out.alpha_composite(ims[i], (x0 + G + (k % COLS) * (32 + G), 8 + G + (k // COLS) * (32 + G)))
y4 = 8 + linhas * (32 + G) + G + 16
for k, i in enumerate(ids):
    x = 8 + (k % COLS) * (32 * 4 + 8)
    y = y4 + (k // COLS) * (alto4 + 6)
    d.rectangle([x, y, x + 127, y + 127], fill=PAPEL if (k // COLS) % 2 == 0 else (196, 187, 168, 255))
    out.alpha_composite(ims[i].resize((128, 128), Image.NEAREST), (x, y))
    d.text((x + 2, y + 129), i, fill=(230, 220, 200, 255))
os.makedirs(os.path.dirname(saida), exist_ok=True)
out.save(saida)
print(saida, out.size)
