"""Texturas procedurais do chão (V4): `plantio.png` (sulcos de lavoura) e `tabuas.png` (assoalho).

As outras texturas vêm da Dreamina pelo funil; estas duas são geometria pura (fileiras, tábuas),
que um gerador faz melhor e repetível. Saem em cinza 256×256, emendando nas quatro bordas, como
as do funil (o render usa só a luminância; a cor é da rampa).

Uso: python tools/blender/texturas/procedurais.py
"""
import math
import os
import random

from PIL import Image

N = 256
AQUI = os.path.dirname(os.path.abspath(__file__))


def _ruido(seed, cel):
    """Ruído de valor periódico (cel divide N), suave."""
    rng = random.Random(seed)
    g = [[rng.random() for _ in range(cel)] for _ in range(cel)]

    def f(x, y):
        fx, fy = x * cel / N, y * cel / N
        x0, y0 = int(fx) % cel, int(fy) % cel
        x1, y1 = (x0 + 1) % cel, (y0 + 1) % cel
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = g[y0][x0] + (g[y0][x1] - g[y0][x0]) * tx
        b = g[y1][x0] + (g[y1][x1] - g[y1][x0]) * tx
        return a + (b - a) * ty
    return f


def plantio():
    """Oito sulcos por textura: crista clara, vale escuro, torrões e um pouco de palha."""
    n1, n2, n3 = _ruido(11, 8), _ruido(12, 32), _ruido(13, 64)
    im = Image.new("L", (N, N))
    px = im.load()
    for y in range(N):
        for x in range(N):
            ondula = 0.06 * math.sin(2 * math.pi * x / N * 2)
            fase = (y / N * 8 + ondula) % 1.0
            sulco = 0.5 + 0.5 * math.cos(2 * math.pi * fase)          # 1 na crista, 0 no vale
            v = 0.18 + 0.55 * sulco ** 1.4
            v += 0.22 * (n2(x, y) - 0.5) + 0.18 * (n3(x, y) - 0.5) * sulco + 0.10 * (n1(x, y) - 0.5)
            px[x, y] = max(0, min(255, int(v * 255)))
    im.save(os.path.join(AQUI, "plantio.png"))


def tabuas():
    """Oito tábuas por textura, emendas desencontradas, veio comprido e um nó de vez em quando."""
    rng = random.Random(21)
    veio = _ruido(22, 64)
    larga = _ruido(23, 4)
    im = Image.new("L", (N, N))
    px = im.load()
    faixas = 8
    h = N // faixas
    emendas = [rng.randrange(N) for _ in range(faixas)]
    tons = [rng.uniform(-0.08, 0.08) for _ in range(faixas * 2)]
    nos = [(rng.randrange(N), rng.randrange(N), rng.uniform(3, 6)) for _ in range(5)]
    for y in range(N):
        f = y // h
        dy = y % h
        for x in range(N):
            parte = 0 if (x - emendas[f]) % N < N // 2 else 1
            v = 0.55 + tons[f * 2 + parte]
            # veio esticado ao longo da tábua
            v += 0.10 * (veio((x // 4) % N, (y * 6) % N) - 0.5) + 0.06 * (larga(x, y) - 0.5)
            for nx, ny, r in nos:
                d = math.hypot(((x - nx + N / 2) % N) - N / 2, (((y - ny) * 1.6 + N / 2) % N) - N / 2)
                if d < r:
                    v -= 0.25 * (1 - d / r)
            if dy == 0 or dy == h - 1:
                v = 0.10                       # fresta entre tábuas
            if (x - emendas[f]) % (N // 2) == 0:
                v = 0.12                       # emenda
            px[x, y] = max(0, min(255, int(v * 255)))
    im.save(os.path.join(AQUI, "tabuas.png"))


if __name__ == "__main__":
    plantio()
    tabuas()
    print("plantio.png e tabuas.png gerados")
