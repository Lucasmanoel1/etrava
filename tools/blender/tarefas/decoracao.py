"""Decoração: cada elemento vira uma folha pequena (tufos com laço de vento)."""
import bpy

from natureza import decoracao
from nucleo import camera, materiais, render


def _limpar_objetos():
    for ob in [o for o in bpy.data.objects if o.type == "MESH" and not o.name.startswith("chao_sombra")]:
        bpy.data.objects.remove(ob, do_unlink=True)


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t.get("quadro", [40, 36])
    ancora = t.get("ancora", [20, 28])
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    folhas = []
    for el in t["elementos"]:
        _limpar_objetos()
        quadros = []
        nq = el.get("quadros", 1)
        tufo = None
        if el["forma"] == "tufo":
            tufo = decoracao.Tufo(el["nome"], el["semente"], folhas=el.get("folhas", 14), rampa=el.get("rampa", "grama"))
        elif el["forma"] == "flores":
            decoracao.flores(el["nome"], el["semente"], el["rampa"])
        elif el["forma"] == "pedrinhas":
            decoracao.pedrinhas(el["nome"], el["semente"])
        for f in range(nq):
            if tufo is not None:
                tufo.montar(f / nq)
            nome = "%s_%d" % (el["nome"], f)
            render.quadro(t["saida"], nome, L, A, distancia_oa=0.15, pontos=[], amostras_oa=4)
            quadros.append({"anim": "vento", "dir": 0, "f": f, "arquivo": nome, "pontos": {}})
        folhas.append({"nome": el["nome"], "quadros": quadros,
                       "animacoes": [{"nome": "vento", "direcoes": 1, "quadros": nq, "fps": el.get("fps", 4)}]})
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss, "folhas": folhas})
