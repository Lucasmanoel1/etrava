"""Vulto (V3): monta a criatura (criaturas/vulto.py) sobre o esqueleto humano, anima e fotografa
em 8 direções. A fumaça sobe em laço por todo ciclo; no `desfazer`, a terra troca para um
material translúcido nos últimos quadros (o corpo se desfaz em pontilhado)."""
import math

from criaturas import vulto
from nucleo import camera, materiais, render


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t.get("quadro", [80, 96])
    ancora = t.get("ancora", [40, 86])
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    c = vulto.montar(t)
    some = vulto._translucido(materiais.material("terra_desfeita", "vulto_terra", k=0.45, ruido=30.0, oa=0.8,
                                                 luz_min=0.10), 0.45)
    direcoes = t.get("direcoes", 8)
    lista = t["animacoes"]
    anims = [{"nome": a["nome"], "direcoes": direcoes, "quadros": a["quadros"], "fps": a.get("fps", 8),
              "passada": round(a["passada"] * c.k, 4) if a.get("passada") else None} for a in lista]
    quadros = []
    for a in lista:
        ciclo = vulto.CICLOS[a["nome"]]
        for d in range(direcoes):
            for f in range(a["quadros"]):
                tt = f / a["quadros"]
                ciclo(c, tt, a)
                vulto.fumaca(c, tt)
                desfeito = a["nome"] == "desfazer" and f >= a["quadros"] - 2
                for ob in c.massa:
                    ob.data.materials[0] = some if desfeito else c.mats["terra"]
                c.j["raiz"].rotation_euler = (0.0, 0.0, -d * 2 * math.pi / direcoes)
                nome = "%s_%d_%d" % (a["nome"], d, f)
                pts = render.quadro(t["saida"], nome, L, A, distancia_oa=t.get("distancia_oa", 0.25))
                quadros.append({"anim": a["nome"], "dir": d, "f": f, "arquivo": nome, "pontos": pts})
    folhas = [{"nome": t["id"], "animacoes": anims, "quadros": quadros}]
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss, "folhas": folhas})
