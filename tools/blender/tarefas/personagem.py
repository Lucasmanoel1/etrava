"""Personagem: monta o corpo (kit_humano), anima por ciclo e fotografa em 8 direções."""
import math

import bpy

from anim import ciclos, combate, vida
from kit_humano import roupa as rp
from kit_humano import corpo, pele
from nucleo import camera, materiais, render

TODOS = dict(ciclos.CICLOS)
TODOS.update({n: ciclos._rosto_para_a_camera(f) for n, f in vida.CICLOS.items()})
# V3: golpes e dano com o rosto erguido como os outros; no chão, o pescoço já vira o rosto para cima
TODOS.update({n: (f if n in ("cair", "caido", "levantar") else ciclos._rosto_para_a_camera(f))
              for n, f in combate.CICLOS.items()})

# quadros e qps de cada animação de vida comum (SPEC-visual §6.2)
VIDA = {"sentar": (3, 6), "sentado": (4, 3), "trabalhar_bancada": (8, 7), "trabalhar_martelo": (8, 8),
        "trabalhar_chao": (8, 6), "conversar": (6, 5), "dormir": (4, 2)}


# o que a pessoa leva na mão em qualquer animação (o resto, só trabalhando)
SEMPRE_NA_MAO = {"bengala", "cajado_trouxa", "lanca", "machadinha"}


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
    if papel == "saqueador":
        # V3: um golpe só (o da machadinha), dano e a queda
        for n in ("golpe_corte", "dano", "cair", "caido"):
            q, fps = combate.QUADROS[n]
            lista.append({"nome": n, "quadros": q, "fps": fps})
    if papel == "comum":
        nomes = ["sentar", "sentado"]
        trabalho = t.get("ficha", {}).get("trabalho", "nenhum")
        if trabalho in VIDA:
            nomes.append(trabalho)
        nomes.append("conversar")
        if t.get("ficha", {}).get("dorme"):       # só quem dorme à vista (as outras agendas: hidden)
            nomes.append("dormir")
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
    # a passada da ficha é para o corpo de 1,70 m; o ciclo a escala pelo corpo (passada × k), e o
    # cliente tem de avançar o mesmo, senão o pé desliza (VT-15, D-108)
    anims = [{"nome": a["nome"], "direcoes": direcoes, "quadros": a["quadros"], "fps": a.get("fps", 8),
              "passada": round(a["passada"] * c.k, 4) if a.get("passada") else None} for a in lista]

    def fotografar(prefixo):
        quadros = []
        for a in lista:
            ciclo = TODOS[a["nome"]]
            # a ferramenta do ofício só aparece trabalhando; o que ajuda a andar ou é o papel da
            # pessoa (bengala, cajado, lança da guarda, arma do saqueador) fica sempre (D-113)
            for qual, obs in getattr(c, "ferramentas", []):
                ver = qual in SEMPRE_NA_MAO or a["nome"].startswith("trabalhar")
                for ob in obs:
                    ob.hide_render = not ver
            for d in range(direcoes):
                for f in range(a["quadros"]):
                    ciclo(c, f / a["quadros"], a)
                    # direção 0 = leste do jogo; o ângulo do jogo cresce para o sul (y do jogo = -Y)
                    c.j["raiz"].rotation_euler = (0.0, 0.0, -d * 2 * math.pi / direcoes)
                    pele.atualizar(c)      # a malha contínua segue as juntas (D-102)
                    nome = "%s%s_%d_%d" % (prefixo, a["nome"], d, f)
                    pts = render.quadro(t["saida"], nome, L, A, distancia_oa=t.get("distancia_oa", 0.25))
                    quadros.append({"anim": a["nome"], "dir": d, "f": f, "arquivo": nome, "pontos": pts})
        return quadros

    # so_camadas (V3): a ficha só existe para as camadas (armas do jogador); o corpo entra como
    # holdout e não sai folha dele. Assim as camadas se dividem em fichas que rodam em paralelo.
    folhas = [] if t.get("so_camadas") else [{"nome": t["id"], "animacoes": anims, "quadros": fotografar("")}]
    # camadas (SPEC-visual §6.4): chapéu, capuz... O corpo vira holdout (recorta o que ele esconde)
    # e a peça é fotografada sozinha na mesma grade; o jogo a desenha por cima do corpo.
    camadas = t.get("camadas", [])
    if camadas:
        corpo_objs = [ob for ob in bpy.data.objects if ob.type == "MESH"]
        for ob in corpo_objs:
            ob.is_holdout = True
        for cam in camadas:
            antes = set(bpy.data.objects)
            if cam.get("ferramenta"):                      # V3: a arma ou a lamparina na mão
                rp.ferramenta(c, cam.get("mao", "d"), cam["ferramenta"])
            for r in cam.get("roupa", []):
                fn, rampa = rp.PECAS[r["peca"]]
                mat = materiais.material("camada_%s_%s" % (cam["nome"], r["peca"]), r.get("rampa", rampa),
                                         k=r.get("k", 0.12), oa=0.7, luz_min=r.get("luz_min", 0.0))
                fn(c, r, mat)
            novos = [ob for ob in bpy.data.objects if ob not in antes]
            folhas.append({"nome": "%s_%s" % (t["id"], cam["nome"]), "animacoes": anims,
                           "quadros": fotografar(cam["nome"] + "__")})
            for ob in novos:
                bpy.data.objects.remove(ob, do_unlink=True)
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss, "folhas": folhas})
