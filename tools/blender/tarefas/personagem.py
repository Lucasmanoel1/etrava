"""Personagem: monta o corpo (kit_humano), anima por ciclo e fotografa em 8 direções."""
import math

from anim import ciclos, vida
from kit_humano import corpo, pele
from nucleo import camera, materiais, render

TODOS = dict(ciclos.CICLOS)
TODOS.update({n: ciclos._rosto_para_a_camera(f) for n, f in vida.CICLOS.items()})

# quadros e qps de cada animação de vida comum (SPEC-visual §6.2)
VIDA = {"sentar": (3, 6), "sentado": (4, 3), "trabalhar_bancada": (8, 7), "trabalhar_martelo": (8, 8),
        "trabalhar_chao": (8, 6), "conversar": (6, 5), "dormir": (4, 2)}


def animacoes(t):
    """A lista explícita da ficha, ou a do papel (V2): parado, andar (com o jeito do personagem),
    correr se pedir, e a vida comum: sentar, sentado, o trabalho do ofício, conversar, dormir."""
    if "animacoes" in t:
        return t["animacoes"]
    papel = t.get("manifesto", {}).get("papel", "comum")
    jeito = t.get("jeito", {})
    lista = [{"nome": "parado", "quadros": 8, "fps": 5},
             {"nome": "andar", "quadros": 8, "fps": jeito.get("fps", 8), "passada": jeito.get("passada", 1.1)}]
    extra = t.get("manifesto", {}).get("animacoes_extra", [])
    if papel == "saqueador" or "correr" in extra:
        lista.append({"nome": "correr", "quadros": 8, "fps": jeito.get("fps_correr", 12),
                      "passada": jeito.get("passada_correr", 1.8)})
    if papel == "comum":
        nomes = ["sentar", "sentado"]
        trabalho = t.get("ficha", {}).get("trabalho", "nenhum")
        if trabalho in VIDA:
            nomes.append(trabalho)
        nomes += ["conversar", "dormir"]
        for n in nomes:
            q, fps = VIDA[n]
            lista.append({"nome": n, "quadros": q, "fps": fps})
    return lista


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t.get("quadro", [64, 80])
    ancora = t.get("ancora", [32, 72])
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    c = corpo.montar(t)
    c.curvatura = t.get("jeito", {}).get("curvatura", 1.0 if c.curvado else 0.0)
    direcoes = t.get("direcoes", 8)
    lista = animacoes(t)
    quadros = []
    for a in lista:
        ciclo = TODOS[a["nome"]]
        for d in range(direcoes):
            for f in range(a["quadros"]):
                ciclo(c, f / a["quadros"], a)
                # direção 0 = leste do jogo; o ângulo do jogo cresce para o sul (y do jogo = -Y)
                c.j["raiz"].rotation_euler = (0.0, 0.0, -d * 2 * math.pi / direcoes)
                pele.atualizar(c)      # a malha contínua segue as juntas (D-102)
                nome = "%s_%d_%d" % (a["nome"], d, f)
                pts = render.quadro(t["saida"], nome, L, A, distancia_oa=t.get("distancia_oa", 0.25))
                quadros.append({"anim": a["nome"], "dir": d, "f": f, "arquivo": nome, "pontos": pts})
    anims = [{"nome": a["nome"], "direcoes": direcoes, "quadros": a["quadros"], "fps": a.get("fps", 8),
              "passada": a.get("passada")} for a in lista]
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss,
                                      "folhas": [{"nome": t["id"], "animacoes": anims, "quadros": quadros}]})
