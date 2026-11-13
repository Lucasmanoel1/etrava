"""Árvore: uma ou mais variantes (espécie, tamanho, caráter), cada uma com o laço de vento.

Cada quadro leva a posição na tela de cada tufo (`tufoNNN`): o pintor de folhagem carimba os
cachos de um tufo presos a ele, e o desenho anda junto com o vento em vez de cintilar.
"""
import bpy

from natureza import arvore
from nucleo import camera, materiais, render

PREFIXOS = ("tronco", "tufo", "fruto", "corte", "ponto.")


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t["quadro"]
    ancora = t["ancora"]
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    render.chao_de_sombra(40.0)
    bpy.context.scene.cycles.samples = t.get("amostras", 4)
    v = t.get("vento", {"quadros": 8, "fps": 5})
    folhas = []
    for var in t["variantes"]:
        for ob in [o for o in bpy.data.objects if o.name.startswith(PREFIXOS)]:
            bpy.data.objects.remove(ob, do_unlink=True)
        especie = var.get("especie", t.get("especie"))
        tufos = arvore.construir(especie, var)
        quadros = []
        for f in range(v["quadros"]):
            arvore.vento(tufos, f / v["quadros"])
            nome = "%s_%d" % (var["nome"], f)
            pts = render.quadro(t["saida"], nome, L, A, distancia_oa=t.get("distancia_oa", 1.2),
                                pontos=[tf[4] for tf in tufos], amostras_oa=t.get("amostras", 4))
            quadros.append({"anim": "vento", "dir": 0, "f": f, "arquivo": nome, "pontos": pts})
        folhas.append({"nome": var["nome"], "quadros": quadros,
                       "animacoes": [{"nome": "vento", "direcoes": 1, "quadros": v["quadros"], "fps": v["fps"]}]})
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss, "folhas": folhas})
