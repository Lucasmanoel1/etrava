"""Construção: uma folha por variante, com sombra no chão e a imagem de brilho das janelas.

`variantes` (opcional): lista de {nome, ...parâmetros que mudam}; cada uma vira a folha
`<id>_<nome>` (o cliente escolhe pela posição). Sem a lista, a folha é `<id>`.
`pas` (moinho): as pás giram; a folha tem `pas.quadros` quadros (um quarto de volta, que emenda).
Os pontos em metros do jogo (luz das janelas, fumaça) vão em `mundo`, por folha.
"""
import math

import bpy

from construcoes import casa
from nucleo import camera, materiais, render


def _limpar():
    for ob in [o for o in bpy.data.objects
               if (o.type == "MESH" and not o.name.startswith("chao_sombra")) or o.name.startswith("ponto.")]:
        bpy.data.objects.remove(ob, do_unlink=True)


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t["quadro"]
    ancora = t["ancora"]
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    render.chao_de_sombra(60.0)
    bpy.context.scene.cycles.samples = t.get("amostras", 4)
    variantes = t.get("variantes") or [{}]
    folhas, mundo = [], {}
    for v in variantes:
        p = dict(t)
        p.update(v)
        nome = t["id"] + ("_" + v["nome"] if v.get("nome") else "")
        _limpar()
        mundo[nome] = casa.construir(p)
        nq = p["pas"].get("quadros", 16) if p.get("pas") else 1
        fps = p["pas"].get("fps", 8) if p.get("pas") else 1
        quadros = []
        for f in range(nq):
            if p.get("pas"):
                casa.pas(p, 0.35 + (math.pi / 2) * f / nq)
            arq = "%s_%d" % (nome, f)
            pts = render.quadro(t["saida"], arq, L, A, distancia_oa=t.get("distancia_oa", 1.0),
                                amostras_oa=t.get("amostras", 4))
            quadros.append({"anim": "parado", "dir": 0, "f": f, "arquivo": arq, "pontos": pts})
        folhas.append({"nome": nome, "animacoes": [{"nome": "parado", "direcoes": 1, "quadros": nq, "fps": fps}],
                       "quadros": quadros})
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss, "folhas": folhas,
                                      "mundo": mundo})
