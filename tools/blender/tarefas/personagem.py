"""Personagem: monta o corpo (kit_humano), anima por ciclo e fotografa em 8 direções."""
import math

from anim import ciclos
from kit_humano import corpo
from nucleo import camera, materiais, render


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t.get("quadro", [64, 80])
    ancora = t.get("ancora", [32, 72])
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    c = corpo.montar(t)
    direcoes = t.get("direcoes", 8)
    quadros = []
    for a in t["animacoes"]:
        ciclo = ciclos.CICLOS[a["nome"]]
        for d in range(direcoes):
            for f in range(a["quadros"]):
                ciclo(c, f / a["quadros"], a)
                # direção 0 = leste do jogo; o ângulo do jogo cresce para o sul (y do jogo = -Y)
                c.j["raiz"].rotation_euler = (0.0, 0.0, -d * 2 * math.pi / direcoes)
                nome = "%s_%d_%d" % (a["nome"], d, f)
                pts = render.quadro(t["saida"], nome, L, A, distancia_oa=t.get("distancia_oa", 0.25))
                quadros.append({"anim": a["nome"], "dir": d, "f": f, "arquivo": nome, "pontos": pts})
    anims = [{"nome": a["nome"], "direcoes": direcoes, "quadros": a["quadros"], "fps": a.get("fps", 8),
              "passada": a.get("passada")} for a in t["animacoes"]]
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss,
                                      "folhas": [{"nome": t["id"], "animacoes": anims, "quadros": quadros}]})
