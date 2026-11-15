"""Decoração: cada elemento vira uma folha pequena (moitas com laço de vento; o resto parado).

Formas: tufo (grama, capim alto, juncos, samambaia — os parâmetros da moita vêm do YAML),
flores, pedrinhas, cogumelos, folhas (no chão), pedregulhos e tronco (caído).
"""
import bpy

from natureza import decoracao
from nucleo import camera, materiais, render

MOITA = ("folhas", "altura", "raio", "rampa", "largura", "inc", "segmentos", "serra", "arco", "espiga", "vento")


def _limpar_objetos():
    for ob in [o for o in bpy.data.objects
               if o.type in ("MESH", "CURVE") and not o.name.startswith("chao_sombra")]:
        bpy.data.objects.remove(ob, do_unlink=True)


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t.get("quadro", [40, 36])
    ancora = t.get("ancora", [20, 28])
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    if t.get("sombra"):
        render.chao_de_sombra(10.0)
    folhas = []
    for el in t["elementos"]:
        _limpar_objetos()
        quadros = []
        nq = el.get("quadros", 1)
        tufo = None
        forma = el["forma"]
        if forma == "tufo":
            kw = {k: (tuple(v) if isinstance(v, list) else v) for k, v in el.items() if k in MOITA}
            tufo = decoracao.Tufo(el["nome"], el["semente"], **kw)
        elif forma == "flores":
            decoracao.flores(el["nome"], el["semente"], el["rampa"], n=el.get("n", 4))
        elif forma == "pedrinhas":
            decoracao.pedrinhas(el["nome"], el["semente"])
        elif forma == "cogumelos":
            decoracao.cogumelos(el["nome"], el["semente"], n=el.get("n", 4))
        elif forma == "folhas":
            decoracao.folhas_chao(el["nome"], el["semente"], el["rampa"], n=el.get("n", 9))
        elif forma == "pedregulhos":
            decoracao.pedregulhos(el["nome"], el["semente"], n=el.get("n", 3),
                                  tamanho=tuple(el.get("tamanho", (0.12, 0.30))))
        elif forma == "tronco":
            decoracao.tronco_caido(el["nome"], el["semente"], comprimento=el.get("comprimento", 2.2),
                                   raio=el.get("raio", 0.17))
        else:
            raise ValueError("forma de decoração desconhecida: " + forma)
        for f in range(nq):
            if tufo is not None:
                tufo.montar(f / nq)
            nome = "%s_%d" % (el["nome"], f)
            render.quadro(t["saida"], nome, L, A, distancia_oa=t.get("distancia_oa", 0.15), pontos=[], amostras_oa=4)
            quadros.append({"anim": "vento", "dir": 0, "f": f, "arquivo": nome, "pontos": {}})
        folhas.append({"nome": el["nome"], "quadros": quadros,
                       "animacoes": [{"nome": "vento", "direcoes": 1, "quadros": nq, "fps": el.get("fps", 4)}]})
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss, "folhas": folhas})
