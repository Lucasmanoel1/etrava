"""A estrutura enterrada (V6): paredes, objetos, a luz do buraco e o chão próprio.

Com `chao: true` na ficha, fotografa o chão de lajes como uma textura de terreno repetível
(como tarefas/chao.py: quadro de 512×256 com a origem do mundo no canto). Sem ele, cada elemento
vira uma folha, com sombra no chão (como tarefas/objetos.py); `quadros` > 1 só na luz do buraco
(a poeira em laço).
"""
import bpy

from interiores import estrutura
from nucleo import camera, materiais, render


def _limpar():
    for ob in [o for o in bpy.data.objects
               if o.type in ("MESH", "CURVE") and not o.name.startswith("chao_sombra")]:
        bpy.data.objects.remove(ob, do_unlink=True)


def _chao(t):
    L, A = t.get("quadro", [512, 256])
    ss = t.get("ss", 4)
    camera.montar(L, A, (0, 0), ss)
    render.montar_luz()
    nome = t.get("folha", "estr_chao")
    estrutura.chao(t)
    render.quadro(t["saida"], nome, L, A, distancia_oa=0.1, pontos=[], amostras_oa=1)
    folhas = [{"nome": nome, "animacoes": [{"nome": "chao", "direcoes": 1, "quadros": 1, "fps": 1}],
               "quadros": [{"anim": "chao", "dir": 0, "f": 0, "arquivo": nome, "pontos": {}}]}]
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": [0, 0], "ss": ss, "folhas": folhas})


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    if t.get("chao"):
        _chao(t)
        return
    L, A = t["quadro"]
    ancora = t["ancora"]
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    # embaixo da terra não há sol: sem sombra projetada (só a ficha que pedir `sombra: true`)
    if t.get("sombra", False):
        render.chao_de_sombra(20.0)
    bpy.context.scene.cycles.samples = t.get("amostras", 4)
    folhas, mundo = [], {}
    for el in t["elementos"]:
        nq = el.get("quadros", 1)
        quadros = []
        for f in range(nq):
            _limpar()
            pts = estrutura.FORMAS[el["forma"]](el, f / nq)
            if pts:
                mundo[el["nome"]] = pts
            arq = "%s_%d" % (el["nome"], f)
            render.quadro(t["saida"], arq, L, A, distancia_oa=t.get("distancia_oa", 0.4), pontos=[],
                          amostras_oa=t.get("amostras", 4))
            quadros.append({"anim": "parado", "dir": 0, "f": f, "arquivo": arq, "pontos": {}})
        folhas.append({"nome": el["nome"], "quadros": quadros,
                       "animacoes": [{"nome": "parado", "direcoes": 1, "quadros": nq, "fps": el.get("fps", 6)}]})
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss, "folhas": folhas,
                                      "mundo": mundo})
