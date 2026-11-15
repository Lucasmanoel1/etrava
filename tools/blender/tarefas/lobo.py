"""Lobo (V3): monta o quadrúpede (criaturas/lobo.py), anima pelos ciclos dele e fotografa em 8
direções, como a tarefa do personagem."""
import math

from criaturas import lobo
from nucleo import camera, materiais, render


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t.get("quadro", [96, 72])
    ancora = t.get("ancora", [48, 58])
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    c = lobo.montar(t)
    direcoes = t.get("direcoes", 8)
    lista = t["animacoes"]
    anims = [{"nome": a["nome"], "direcoes": direcoes, "quadros": a["quadros"], "fps": a.get("fps", 8),
              "passada": round(a["passada"] * c.k, 4) if a.get("passada") else None} for a in lista]
    quadros = []
    for a in lista:
        ciclo = lobo.CICLOS[a["nome"]]
        for d in range(direcoes):
            for f in range(a["quadros"]):
                ciclo(c, f / a["quadros"], a)
                c.j["raiz"].rotation_euler = (0.0, 0.0, -d * 2 * math.pi / direcoes)
                nome = "%s_%d_%d" % (a["nome"], d, f)
                pts = render.quadro(t["saida"], nome, L, A, distancia_oa=t.get("distancia_oa", 0.25))
                quadros.append({"anim": a["nome"], "dir": d, "f": f, "arquivo": nome, "pontos": pts})
    folhas = [{"nome": t["id"], "animacoes": anims, "quadros": quadros}]
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss, "folhas": folhas})
