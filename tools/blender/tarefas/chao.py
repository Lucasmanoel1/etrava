"""Chão (SPEC-visual §7.2): uma textura de terreno repetível em espaço de tela.

Fotografa um plano grande com o material do terreno, num quadro de L×A px de arte cujo canto
superior esquerdo é a origem do mundo. Com a textura repetindo a cada P metros (P divide o
período), a imagem é periódica: o cliente a desenha alinhada ao mundo, sem costura entre tiles.
Período de tela 512×256 px = vetores (8, -8) e (-8, -8) m no chão.
"""
import bmesh

from nucleo import camera, geo, materiais, render


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t.get("quadro", [512, 256])
    ss = t.get("ss", 4)
    camera.montar(L, A, (0, 0), ss)
    render.montar_luz()
    folhas = []
    for terr in t["terrenos"]:
        for ob in [o for o in list(__import__("bpy").data.objects) if o.name.startswith("chao_")]:
            __import__("bpy").data.objects.remove(ob, do_unlink=True)
        m = materiais.material("chao_" + terr["nome"], terr["rampa"], k=terr.get("k", 1.0), textura=terr["textura"],
                               escala=terr.get("escala", 0.5), escala2=terr.get("escala2", 0.125),
                               peso2=terr.get("peso2", 0.3), pontilhar=terr.get("pontilhar", True),
                               contorno=False, linhas=False, oa=0.0, tons=terr.get("tons"))
        bm = bmesh.new()
        bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=40.0)
        geo.objeto("chao_" + terr["nome"], bm, m)
        nome = terr["nome"]
        render.quadro(t["saida"], nome, L, A, distancia_oa=0.1, pontos=[], amostras_oa=1)
        folhas.append({"nome": nome, "animacoes": [{"nome": "chao", "direcoes": 1, "quadros": 1, "fps": 1}],
                       "quadros": [{"anim": "chao", "dir": 0, "f": 0, "arquivo": nome, "pontos": {}}]})
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": [0, 0], "ss": ss, "folhas": folhas})
