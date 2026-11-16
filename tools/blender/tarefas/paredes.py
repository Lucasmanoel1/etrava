"""Casca dos cômodos (V6): uma folha por peça de parede (fundo, frente cortada, cantos e pontas).
Sem chão de sombra: as peças se encostam umas nas outras e a sombra de uma cairia sobre a vizinha.
Descrição das peças: interiores/paredes.py; fichas: interiores/paredes_<casa>.yaml."""
import bpy

from interiores import paredes
from nucleo import camera, materiais, render


def _limpar():
    for ob in [o for o in bpy.data.objects if o.type == "MESH"]:
        bpy.data.objects.remove(ob, do_unlink=True)


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t["quadro"]
    ancora = t["ancora"]
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    bpy.context.scene.cycles.samples = t.get("amostras", 4)
    folhas = []
    for peca in t["pecas"]:
        _limpar()
        paredes.construir(peca, t)
        nome = peca["nome"]
        render.quadro(t["saida"], nome, L, A, distancia_oa=t.get("distancia_oa", 0.35), pontos=[],
                      amostras_oa=t.get("amostras", 4))
        folhas.append({"nome": nome, "animacoes": [{"nome": "parado", "direcoes": 1, "quadros": 1, "fps": 1}],
                       "quadros": [{"anim": "parado", "dir": 0, "f": 0, "arquivo": nome, "pontos": {}}]})
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss, "folhas": folhas})
