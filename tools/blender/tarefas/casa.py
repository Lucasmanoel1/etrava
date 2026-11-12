"""Construção parada: um quadro, com sombra no chão e a imagem de brilho das janelas."""
import bpy

from construcoes import casa
from nucleo import camera, materiais, render


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t["quadro"]
    ancora = t["ancora"]
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    render.chao_de_sombra(40.0)
    bpy.context.scene.cycles.samples = t.get("amostras", 4)
    casa.construir(t)
    pts = render.quadro(t["saida"], t["id"], L, A, distancia_oa=t.get("distancia_oa", 1.0),
                        amostras_oa=t.get("amostras", 4))
    render.escrever_json(t["saida"], {
        "largura": L, "altura": A, "ancora": ancora, "ss": ss,
        "folhas": [{"nome": t["id"], "animacoes": [{"nome": "parado", "direcoes": 1, "quadros": 1, "fps": 1}],
                    "quadros": [{"anim": "parado", "dir": 0, "f": 0, "arquivo": t["id"], "pontos": pts}]}]})
