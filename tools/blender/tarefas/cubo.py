"""Teste do pipeline (V1, tarefa 1): um cubo de 1 m tem de cair exatamente no losango de um tile,
com 28 px de altura. Renderiza sem superamostragem e compara pixel a pixel com a projeção
analítica do jogo (IsoProjection com ART_SCALE = 2)."""
import os

import bpy

from nucleo import camera, geo, materiais, render

L, A, AX, AY = 64, 64, 32, 44


def _dentro(px, py):
    """O centro do pixel (px+.5, py+.5) cai na silhueta projetada do cubo?"""
    x, y = px + 0.5 - AX, py + 0.5 - AY            # relativo à âncora, y para baixo
    if abs(x) > 32:
        return None
    # topo do losango de cima (z = 1 → sobe 28 px) até o fundo do losango de baixo
    alto = -28 - (16 - abs(x) / 2.0)
    baixo = 16 - abs(x) / 2.0
    if min(abs(y - alto), abs(y - baixo), abs(abs(x) - 32)) < 0.05:
        return None                                 # centro em cima da aresta: indefinido
    return alto < y < baixo


def executar(t):
    pasta = t["saida"]
    os.makedirs(pasta, exist_ok=True)
    camera.montar(L, A, (AX, AY), 1)
    render.montar_luz()
    geo.caixa("cubo", (1, 1, 1), material=materiais.material("cubo", "pedra"))
    render.quadro(pasta, "cubo", L, A)
    img = bpy.data.images.load(os.path.join(pasta, "cubo_a.png"))
    px = list(img.pixels)
    erros = 0
    for py in range(A):
        for x in range(L):
            esperado = _dentro(x, py)
            if esperado is None:
                continue
            i = ((A - 1 - py) * L + x) * 4               # pixels do Blender: de baixo para cima
            tem = px[i + 3] > 0.5
            if tem != esperado:
                erros += 1
                if erros <= 10:
                    print("ETRAVA cubo: pixel (%d, %d) %s, esperado %s" % (x, py, tem, esperado))
    topo = img.pixels[((A - 1 - 2) * L + 32) * 4]  # (32, 2): topo do cubo, índice no R
    print("ETRAVA cubo: indice no topo %.4f (esperado %.4f)" % (topo, 1 / 255))
    if erros:
        raise RuntimeError("cubo fora do losango: %d pixels" % erros)
    print("ETRAVA cubo: ok, 64x60 px exatos")
