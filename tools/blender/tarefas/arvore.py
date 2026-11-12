"""Árvore: uma ou mais variantes (tamanho, semente), cada uma com o laço de vento."""
import bpy

from natureza import arvore
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
    v = t.get("vento", {"quadros": 8, "fps": 5})
    folhas = []
    for var in t["variantes"]:
        for ob in [o for o in bpy.data.objects if o.name.startswith(("tronco", "tufo"))]:
            bpy.data.objects.remove(ob, do_unlink=True)
        tufos = arvore.construir(t["especie"], var["altura"], var["copa"], var["semente"])
        quadros = []
        for f in range(v["quadros"]):
            arvore.vento(tufos, f / v["quadros"])
            nome = "%s_%d" % (var["nome"], f)
            pts = render.quadro(t["saida"], nome, L, A, distancia_oa=t.get("distancia_oa", 1.2), pontos=[],
                                amostras_oa=t.get("amostras", 4))
            quadros.append({"anim": "vento", "dir": 0, "f": f, "arquivo": nome, "pontos": pts})
        folhas.append({"nome": var["nome"], "quadros": quadros,
                       "animacoes": [{"nome": "vento", "direcoes": 1, "quadros": v["quadros"], "fps": v["fps"]}]})
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss, "folhas": folhas})
