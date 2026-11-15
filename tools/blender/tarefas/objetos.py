"""Objetos de fora (V5): cada elemento vira uma folha, com sombra no chão e, se tiver vidro ou
fogo, a imagem de brilho. `quadros` > 1 só para o fogo (a fase do laço vai para a forma).
Os pontos em metros do jogo (luz, fumaça) vão em `mundo`, por folha."""
import bpy

from construcoes import objetos
from nucleo import camera, materiais, render


def _limpar():
    for ob in [o for o in bpy.data.objects
               if o.type in ("MESH", "CURVE") and not o.name.startswith("chao_sombra")]:
        bpy.data.objects.remove(ob, do_unlink=True)


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t["quadro"]
    ancora = t["ancora"]
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    render.chao_de_sombra(20.0)
    bpy.context.scene.cycles.samples = t.get("amostras", 4)
    folhas, mundo = [], {}
    for el in t["elementos"]:
        nq = el.get("quadros", 1)
        quadros = []
        for f in range(nq):
            _limpar()
            pts = objetos.FORMAS[el["forma"]](el, f / nq)
            if pts:
                mundo[el["nome"]] = pts
            arq = "%s_%d" % (el["nome"], f)
            render.quadro(t["saida"], arq, L, A, distancia_oa=t.get("distancia_oa", 0.4), pontos=[],
                          amostras_oa=t.get("amostras", 4))
            quadros.append({"anim": "parado", "dir": 0, "f": f, "arquivo": arq, "pontos": {}})
        folhas.append({"nome": el["nome"], "quadros": quadros,
                       "animacoes": [{"nome": "parado", "direcoes": 1, "quadros": nq, "fps": el.get("fps", 8)}]})
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss, "folhas": folhas,
                                      "mundo": mundo})
