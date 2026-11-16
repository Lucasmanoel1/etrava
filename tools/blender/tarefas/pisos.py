"""Pisos de interior (V6): tábua, lajota, pedra e terra batida, como textura de terreno repetível
alinhada ao mundo (mesmo contrato do `chao.py`: quadro 512×256 px de arte, origem do mundo no canto
superior esquerdo, período de 8 m em x e em y).

Diferente do chão de fora, o piso é geometria: cada tábua e cada laje é uma peça, com fresta,
um pouco de desnível e de inclinação (o pixelizador acende e apaga cada uma) — a junta sai nítida,
sem textura nova (uma textura nova em tools/blender/texturas mudaria a chave de cache de tudo).
"""
import math
import random

import bmesh
import bpy
from mathutils import Matrix, Vector

from nucleo import camera, geo, materiais, render

P = 8.0   # período (m)


def _caixa_inclinada(bm, x0, x1, y0, y1, z, esp, inc_x, inc_y):
    """Caixa do jogo (x, y; Y do Blender = -y) com o topo em z, levemente inclinada."""
    res = bmesh.ops.create_cube(bm, size=1.0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    for v in res["verts"]:
        gx = cx + v.co.x * (x1 - x0)
        gy = cy + v.co.y * (y1 - y0)
        zz = z - esp * (0.5 - v.co.z)
        if v.co.z > 0:
            zz += (gx - cx) * inc_x + (gy - cy) * inc_y
        v.co = Vector((gx, -gy, zz))


def _repetir(pecas, x_lim=(-1.0, 17.0), y_lim=(-9.0, 9.0)):
    """Peças de uma célula de 8×8 m repetidas sobre a área que o quadro vê."""
    for i in range(-1, 3):
        for j in range(-2, 2):
            for p in pecas:
                x0, x1, y0, y1 = p[0] + i * P, p[1] + i * P, p[2] + j * P, p[3] + j * P
                if x1 < x_lim[0] or x0 > x_lim[1] or y1 < y_lim[0] or y0 > y_lim[1]:
                    continue
                yield (x0, x1, y0, y1) + tuple(p[4:])


def _tabuas(t, rng):
    larg = t.get("largura", 0.25)
    fresta = t.get("fresta", 0.025)
    comp = t.get("comprimento", [1.0, 2.67])
    pecas = []
    n = int(round(P / larg))
    for r in range(n):
        y0 = r * larg
        juntas, u = [], rng.uniform(0, P)
        ini = u
        while u < ini + P - comp[0] * 0.8:
            juntas.append(u)
            u += rng.uniform(*comp)
        for k, a in enumerate(juntas):
            b = juntas[k + 1] if k + 1 < len(juntas) else ini + P
            pecas.append((a + fresta / 2, b - fresta / 2, y0 + fresta / 2, y0 + larg - fresta / 2,
                          rng.randrange(len(t["materiais"])), rng.uniform(-0.004, 0.004),
                          rng.uniform(-0.002, 0.002), rng.uniform(-0.03, 0.03)))
    return pecas


def _lajes(t, rng):
    """Fiadas de lajes de altura variável, com juntas desencontradas (lajota e pedra)."""
    alt = t.get("fiada", [0.45, 0.65])
    comp = t.get("comprimento", [0.45, 0.8])
    fresta = t.get("fresta", 0.035)
    inc = t.get("inclinacao", 0.03)
    pecas = []
    # fiadas que somam exatamente P
    fiadas, y = [], 0.0
    while y < P - 1e-6:
        h = rng.uniform(*alt)
        if P - (y + h) < alt[0]:
            h = P - y
        fiadas.append((y, y + h))
        y += h
    for y0, y1 in fiadas:
        juntas, u = [], rng.uniform(0, P)
        ini = u
        while u < ini + P - comp[0] * 0.8:
            juntas.append(u)
            u += rng.uniform(*comp)
        for k, a in enumerate(juntas):
            b = juntas[k + 1] if k + 1 < len(juntas) else ini + P
            # às vezes a laje é partida em duas no sentido da fiada (pedra irregular)
            partes = [(y0, y1)]
            if t.get("partir", 0) and rng.random() < t["partir"]:
                m = rng.uniform(y0 + 0.2 * (y1 - y0), y0 + 0.8 * (y1 - y0))
                partes = [(y0, m), (m, y1)]
            for ya, yb in partes:
                pecas.append((a + fresta / 2, b - fresta / 2, ya + fresta / 2, yb - fresta / 2,
                              rng.randrange(len(t["materiais"])), rng.uniform(-0.006, 0.006),
                              rng.uniform(-inc, inc), rng.uniform(-inc, inc)))
    return pecas


def _terra(t, rng):
    """Terra batida: plano liso e algumas pedrinhas e lascas enterradas."""
    pecas = []
    for _ in range(int(t.get("pedrinhas", 120))):
        x, y = rng.uniform(0, P), rng.uniform(0, P)
        s = rng.uniform(0.04, 0.12)
        pecas.append((x, x + s * rng.uniform(0.8, 1.6), y, y + s, rng.randrange(1, len(t["materiais"])),
                      rng.uniform(-0.01, 0.0), rng.uniform(-0.1, 0.1), rng.uniform(-0.1, 0.1)))
    return pecas


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t.get("quadro", [512, 256])
    ss = t.get("ss", 4)
    camera.montar(L, A, (0, 0), ss)
    render.montar_luz()
    bpy.context.scene.cycles.samples = t.get("amostras", 4)
    folhas = []
    for piso in t["pisos"]:
        for ob in [o for o in bpy.data.objects if o.type == "MESH"]:
            bpy.data.objects.remove(ob, do_unlink=True)
        rng = random.Random(piso.get("semente", 1))
        nome = piso["nome"]
        mats = [materiais.material("%s_%d" % (nome, i), m["rampa"], k=m.get("k", 0.5), textura=m.get("textura"),
                                   escala=m.get("escala", 1.0), ruido=m.get("ruido"),
                                   ruido_eixos=m.get("ruido_eixos"), tons=m.get("tons"), pontilhar=False,
                                   contorno=False, linhas=False, oa=m.get("oa", 0.6))
                for i, m in enumerate(piso["materiais"])]
        fundo = materiais.material(nome + "_fresta", piso["fresta_rampa"], k=0.0, contorno=False, linhas=False,
                                   oa=0.3, tons=piso.get("fresta_tons"))
        bm = bmesh.new()
        bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=40.0)
        for v in bm.verts:
            v.co.z = -0.03
        geo.objeto(nome + "_fundo", bm, fundo)
        forma = piso["forma"]
        pecas = {"tabuas": _tabuas, "lajes": _lajes, "terra": _terra}[forma](piso, rng)
        if forma == "terra":   # o chão é o próprio "fundo"; as pedrinhas ficam por cima
            for o in bpy.data.objects:
                if o.name == nome + "_fundo":
                    o.data.materials[0] = mats[0]
                    for v in o.data.vertices:
                        v.co.z = 0.0
        bms = [bmesh.new() for _ in mats]
        for x0, x1, y0, y1, mi, dz, ix, iy in _repetir(pecas):
            esp = 0.03 if forma != "terra" else 0.02
            _caixa_inclinada(bms[mi], x0, x1, y0, y1, dz + (0.0 if forma != "terra" else 0.006), esp, ix, iy)
        for i, b in enumerate(bms):
            geo.objeto("%s_pecas%d" % (nome, i), b, mats[i])
        render.quadro(t["saida"], nome, L, A, distancia_oa=0.05, pontos=[], amostras_oa=2)
        folhas.append({"nome": nome, "animacoes": [{"nome": "chao", "direcoes": 1, "quadros": 1, "fps": 1}],
                       "quadros": [{"anim": "chao", "dir": 0, "f": 0, "arquivo": nome, "pontos": {}}]})
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": [0, 0], "ss": ss, "folhas": folhas})
