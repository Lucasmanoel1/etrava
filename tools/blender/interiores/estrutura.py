"""A estrutura enterrada (V6): paredes, chão, fibras, entulho, laje e a luz do buraco.

Um lugar que não é deste mundo rural: blocos lisos e frios, todos do mesmo tamanho, em junta
corrida (uma junta em cima da outra, coisa que pedreiro nenhum da região faz), juntas finas e
retas demais. O que é da região entrou depois: a terra que caiu pelo buraco, a escada, a viga.
As fibras atravessam o chão e sobem: parecem raízes, mas são pedra. Nada aqui se explica — os
sulcos da laje são abstratos (riscos iguais, um arco aberto, pontos em grade com um faltando);
nenhuma figura, nenhuma letra, nenhum mecanismo, nada que brilhe sozinho (D-127, lore: nada de
V2/V3 no Capítulo I).

Convenção (como em construcoes/objetos.py): x = leste; Y do Blender = -y do jogo; a peça fica
centrada na origem, dentro da pegada do `content/props.yaml`. Cada forma é `f(el, fase)` e
devolve pontos em metros do jogo, ou None.

Paredes: o cliente (ParedesEstrutura.java) põe uma peça em cada borda entre chão e rocha.
Fundo (norte, oeste): parede inteira. Frente (sul, leste): cortada a ~0,45 m, com o topo do corte
à mostra. Cantos: pilar de 0,25 × 0,25 m.
"""
import math
import random

import bmesh
from mathutils import Vector, noise

from nucleo import geo, materiais

ALTURA = 2.8          # parede do fundo
ALTURA_CORTE = 0.45   # parede da frente, cortada
ESPESSURA = 0.25
JUNTA = 0.045         # largura da junta (aparece como 1 px de arte)
FIADAS = (0.0, 0.16, 0.86, 1.56, 2.26, ALTURA)   # rodapé + quatro fiadas iguais


def _m():
    M = materiais.material
    return {
        # o material de fora deste mundo: liso (quase sem textura), frio (ardósia), contorno limpo
        "fria": M("estr_fria", "ardosia", k=0.05, ruido=2.5, oa=0.85, tons=[1, 4]),
        "corte": M("estr_corte", "ardosia", k=0.15, ruido=9.0, oa=0.5, tons=[2, 4], linhas=False),
        "junta": M("estr_junta", "quase_preto", k=0.0, oa=0.0, linhas=False, contorno=False),
        "fibra": M("estr_fibra", "casca_salgueiro", k=0.5, ruido=7.0, ruido_eixos=(3.0, 3.0, 12.0), oa=0.9),
        "terra": M("estr_terra", "terra", k=0.8, textura="terra.png", escala=0.8, oa=0.8, linhas=False,
                   var_objeto=0.1),
        "madeira": M("estr_madeira", "madeira", k=0.5, textura="casca.png", escala=1.2, oa=0.8),
        "raiz": M("estr_raiz", "madeira_escura", k=0.3, textura="casca.png", escala=2.0, oa=0.5, linhas=False),
        "grama": M("estr_grama", "grama_seca", k=0.4, ruido=20.0, oa=0.6, linhas=False),
    }


def _feixe():
    """A luz que entra pelo buraco: pontilhada (um terço dos pixels), acende no escuro."""
    mat = materiais.material("estr_feixe", "ardosia", k=0.0, oa=0.0, emissivo=True, contorno=False, linhas=False,
                             luz_min=0.9, tons=[2, 3], rampa_brilho="noite")
    for info in materiais._MATS:
        if info["nome"] == mat.name:
            info["opacidade"] = 0.13
    return mat


def _sem_sombra(ob):
    """Luz não faz sombra nem escurece o que está perto (oclusão)."""
    ob.visible_shadow = False
    return ob


def _poeira():
    return materiais.material("estr_poeira", "ardosia", k=0.0, oa=0.0, emissivo=True, contorno=False, linhas=False,
                              luz_min=1.0, tons=[4, 4], rampa_brilho="flor_branca")


# ---------------------------------------------------------------- malhas

def _caixa(bm, x0, x1, y0, y1, z0, z1):
    res = bmesh.ops.create_cube(bm, size=1.0)
    for v in res["verts"]:
        v.co = Vector((x0 + (v.co.x + 0.5) * (x1 - x0), y0 + (v.co.y + 0.5) * (y1 - y0),
                       z0 + (v.co.z + 0.5) * (z1 - z0)))
    return res["verts"]


def _ob(nome, construir, material, suave=False):
    bm = bmesh.new()
    construir(bm)
    return geo.objeto(nome, bm, material, suave=suave)


def _tubo(bm, pts, raios, seg=7, achatar=1.0):
    """Tubo ao longo de uma polilinha, com raio por ponto (fibras, raízes)."""
    aneis = []
    for i, p in enumerate(pts):
        t = (pts[min(i + 1, len(pts) - 1)] - pts[max(i - 1, 0)]).normalized()
        ref = Vector((0, 0, 1)) if abs(t.z) < 0.9 else Vector((1, 0, 0))
        a = t.cross(ref).normalized()
        b = t.cross(a).normalized()
        anel = []
        for k in range(seg):
            ang = 2 * math.pi * k / seg
            anel.append(bm.verts.new(p + (a * math.cos(ang) + b * math.sin(ang) * achatar) * raios[i]))
        aneis.append(anel)
    for i in range(len(aneis) - 1):
        for k in range(seg):
            bm.faces.new((aneis[i][k], aneis[i][(k + 1) % seg], aneis[i + 1][(k + 1) % seg], aneis[i + 1][k]))
    bm.faces.new(list(reversed(aneis[0])))
    bm.faces.new(aneis[-1])


def _bola(bm, c, r, s=(1, 1, 1), sub=2, ruido=0.0, sem=0.0, chao=None):
    res = bmesh.ops.create_icosphere(bm, subdivisions=sub, radius=r)
    for v in res["verts"]:
        f = 1.0 + (ruido * noise.noise(v.co.normalized() * 1.7 + Vector((sem, sem * 0.7, 0))) if ruido else 0.0)
        v.co = Vector((c[0] + v.co.x * f * s[0], c[1] + v.co.y * f * s[1], c[2] + v.co.z * f * s[2]))
        if chao is not None:
            v.co.z = max(v.co.z, chao)


def _bloco_partido(bm, x0, x1, y0, y1, z0, z1, canto):
    """Bloco com um canto de cima arrancado (a quebra é a única coisa irregular nele)."""
    vs = _caixa(bm, x0, x1, y0, y1, z0, z1)
    cx, cz = (x1 if canto > 0 else x0), z1
    for v in vs:
        if abs(v.co.x - cx) < 1e-6 and abs(v.co.z - cz) < 1e-6:
            v.co.x -= canto * (x1 - x0) * 0.22
            v.co.z -= (z1 - z0) * 0.35


# ---------------------------------------------------------------- paredes

def _sistema(el):
    """P(u, n, z): u ao longo da parede (-0,5..0,5), n da face de fora (-E/2) para a de dentro
    (+E/2), z para cima. `visivel`: o lado de n que a câmera vê (+1 = o de dentro, no fundo)."""
    lado = el.get("lado", "norte")
    if lado == "norte":      # parede ao norte do chão: o chão fica ao sul (−Y do Blender)
        return lambda u, n, z: Vector((u, -n, z))
    if lado == "oeste":      # o chão fica a leste (+X)
        return lambda u, n, z: Vector((n, -u, z))
    if lado == "sul":        # parede da frente: a câmera vê a face de fora (−Y)
        return lambda u, n, z: Vector((u, n, z))
    return lambda u, n, z: Vector((-n, -u, z))   # "leste": face de fora em +X


def _caixa_s(bm, P, u0, u1, n0, n1, z0, z1):
    a, b = P(u0, n0, z0), P(u1, n1, z1)
    return _caixa(bm, min(a.x, b.x), max(a.x, b.x), min(a.y, b.y), max(a.y, b.y), z0, z1)


def parede(el, fase):
    """Um metro de parede: rodapé e fiadas de blocos de 1 m, junta corrida. `estrago`: 0 nada,
    1 bloco com o canto arrancado e terra embaixo, 2 uma racha fina e reta, 3 um bloco recuado."""
    M = _m()
    P = _sistema(el)
    baixa = el.get("baixa", False)
    topo = ALTURA_CORTE if baixa else ALTURA
    fiadas = [z for z in FIADAS if z < topo] + [topo]
    E = ESPESSURA / 2
    rng = random.Random(el.get("semente", 1))
    estrago = el.get("estrago", 0)
    j = JUNTA / 2
    # dentro das juntas: o escuro de trás dos blocos
    _ob("estr_junta_" + el["nome"], lambda bm: _caixa_s(bm, P, -0.5, 0.5, -E + 0.06, E - 0.06, 0.0, topo - 0.01),
        M["junta"])
    partido = rng.randrange(1, len(fiadas) - 1) if estrago == 1 and len(fiadas) > 2 else -1
    recuado = rng.randrange(1, len(fiadas) - 1) if estrago == 3 and len(fiadas) > 2 else -1

    def blocos(bm):
        for i in range(len(fiadas) - 1):
            z0, z1 = fiadas[i] + (0 if i == 0 else j), fiadas[i + 1] - (0 if fiadas[i + 1] == topo else j)
            if i == 0:    # rodapé: um pouco saliente, sem junta vertical
                _caixa_s(bm, P, -0.5, 0.5, -E, E + 0.03, z0, z1)
                continue
            dn = -0.03 if i == recuado else 0.0
            if i == partido:   # um entalhe no canto de cima do bloco: falta um pedaço, reto
                lado = 1 if rng.random() < 0.5 else -1
                u0, u1 = (-0.5 + j, 0.2) if lado > 0 else (-0.2, 0.5 - j)
                _caixa_s(bm, P, u0, u1, -E, E, z0, z1)
                v0, v1 = (0.2, 0.5 - j) if lado > 0 else (-0.5 + j, -0.2)
                _caixa_s(bm, P, v0, v1, -E, E, z0, z1 - 0.22)
            else:
                _caixa_s(bm, P, -0.5 + j, 0.5 - j, -E, E + dn, z0, z1)
    _ob("estr_blocos_" + el["nome"], blocos, M["fria"])
    if baixa:   # o corte: o topo da parede, à mostra
        _ob("estr_corte_" + el["nome"], lambda bm: _caixa_s(bm, P, -0.5, 0.5, -E, E, topo - 0.01, topo + 0.005),
            M["corte"])
    if estrago == 2:   # racha fina e reta, de uma fiada à outra
        z0 = fiadas[1] + 0.1
        n = (E + 0.004) if not el.get("lado") in ("sul", "leste") else -(E + 0.004)
        _ob("estr_racha_" + el["nome"], lambda bm: _caixa_s(bm, P, 0.08, 0.11, n - 0.004, n + 0.004, z0,
                                                            min(topo - 0.1, z0 + 1.1)), M["junta"])
    if estrago == 1 and partido > 0:   # o pó do bloco e um pouco de terra, no pé
        n = E + 0.18 if el.get("lado") in ("norte", "oeste") else -E - 0.18
        c = P(rng.uniform(-0.2, 0.2), n, 0.0)
        _ob("estr_po_" + el["nome"], lambda bm: _bola(bm, (c.x, c.y, 0.0), 0.2, (1.2, 1.2, 0.35), sub=1, ruido=0.3,
                                                      chao=0.0), M["terra"], suave=True)
        for k in range(3):
            d = P(rng.uniform(-0.3, 0.3), n + rng.uniform(-0.06, 0.12), 0.0)
            _ob("estr_lasca%d_%s" % (k, el["nome"]), lambda bm, d=d, k=k: _caixa(
                bm, d.x - 0.05, d.x + 0.05, d.y - 0.04, d.y + 0.04, 0.0, 0.05 + 0.03 * k), M["fria"])


def canto(el, fase):
    """Pilar de canto (0,25 × 0,25 m), alto ou cortado, com as mesmas fiadas."""
    M = _m()
    baixa = el.get("baixa", False)
    topo = ALTURA_CORTE if baixa else ALTURA
    fiadas = [z for z in FIADAS if z < topo] + [topo]
    E = ESPESSURA / 2
    j = JUNTA / 2
    _ob("estr_junta_" + el["nome"], lambda bm: _caixa(bm, -E + 0.03, E - 0.03, -E + 0.03, E - 0.03, 0, topo - 0.01),
        M["junta"])

    def blocos(bm):
        for i in range(len(fiadas) - 1):
            z0, z1 = fiadas[i] + (0 if i == 0 else j), fiadas[i + 1] - (0 if fiadas[i + 1] == topo else j)
            _caixa(bm, -E, E, -E, E, z0, z1)
    _ob("estr_blocos_" + el["nome"], blocos, M["fria"])
    if baixa:
        _ob("estr_corte_" + el["nome"], lambda bm: _caixa(bm, -E, E, -E, E, topo - 0.01, topo + 0.005), M["corte"])


# ---------------------------------------------------------------- fibras

def fibra(el, fase):
    """Feixe de 3 a 5 fibras que rompe o chão e sobe, torcendo, até sumir no escuro de cima.
    Parecem raízes grossas; são pedra (cinza seco, estrias ao comprido). No pé, lajes do chão
    levantadas e partidas — o único lugar onde o liso cede."""
    M = _m()
    rng = random.Random(el.get("semente", 1))
    n = el.get("fios", 4)
    alto = el.get("altura", 2.7)
    inclina = Vector((rng.uniform(-0.35, -0.1), rng.uniform(0.1, 0.35), 0))   # para o fundo (noroeste)

    def fios(bm):
        for f in range(n):
            ang0 = 2 * math.pi * f / n + rng.uniform(-0.3, 0.3)
            r0 = rng.uniform(0.16, 0.3)
            raio = rng.uniform(0.05, 0.09)
            torce = rng.uniform(1.4, 2.2) * (1 if el.get("semente", 1) % 2 else -1)
            pts, rs = [], []
            for i in range(13):
                t = i / 12
                z = -0.05 + t * alto
                # no pé abre (pega no chão), no meio se junta e torce, em cima se espalha de novo
                rr = r0 * (1.0 - 0.45 * math.sin(min(1.0, t * 1.6) * math.pi / 2)) + 0.12 * t * t
                a = ang0 + torce * t * 2.2
                c = Vector((math.cos(a) * rr, math.sin(a) * rr, z)) + inclina * (t ** 1.6)
                c += Vector((noise.noise(Vector((t * 3, f, 1.0))) * 0.05, noise.noise(Vector((t * 3, f, 4.0))) * 0.05, 0))
                pts.append(c)
                rs.append(raio * (1.5 - 0.6 * t) * (1.3 if i == 0 else 1.0))
            _tubo(bm, pts, rs, seg=7)
        # fios finos que saem do feixe e entram no chão
        for f in range(3):
            a = rng.uniform(0, 2 * math.pi)
            base = Vector((math.cos(a) * 0.38, math.sin(a) * 0.32, -0.02))
            meio = Vector((math.cos(a) * 0.2, math.sin(a) * 0.18, 0.22))
            _tubo(bm, [base, meio, Vector((0, 0, 0.5))], [0.045, 0.04, 0.03], seg=5)
    _ob("estr_fibra_" + el["nome"], fios, M["fibra"], suave=True)

    # lajes do chão levantadas em volta do pé
    def lajes(bm):
        for k in range(4):
            a = 2 * math.pi * k / 4 + rng.uniform(0, 0.8)
            d = Vector((math.cos(a), math.sin(a), 0))
            c = d * 0.36
            lado = Vector((-d.y, d.x, 0))
            inc = rng.uniform(0.12, 0.3)
            vs = []
            for (du, dv) in ((-0.16, -0.12), (0.16, -0.12), (0.16, 0.12), (-0.16, 0.12)):
                p = c + lado * du + d * dv
                vs.append(Vector((p.x, p.y, 0.02 + max(0.0, -dv) * inc * 2.0)))
            top = [bm.verts.new(v + Vector((0, 0, 0.06))) for v in vs]
            bot = [bm.verts.new(Vector((v.x, v.y, 0.0))) for v in vs]
            bm.faces.new(top)
            bm.faces.new(list(reversed(bot)))
            for i in range(4):
                bm.faces.new((bot[i], bot[(i + 1) % 4], top[(i + 1) % 4], top[i]))
    _ob("estr_lajes_" + el["nome"], lajes, M["fria"])


# ---------------------------------------------------------------- entulho

def entulho(el, fase):
    """Blocos da própria estrutura que caíram de cima (um inteiro, um partido), lascas e a terra
    que veio junto. Mesma pegada do V5 (1,0 × 0,8)."""
    M = _m()
    rng = random.Random(el.get("semente", 1))

    def blocos(bm):
        # um bloco inteiro, deitado e torto: as arestas continuam retas demais
        vs = _caixa(bm, -0.45, 0.15, -0.22, 0.12, 0.0, 0.3)
        giro = rng.uniform(-0.5, 0.5)
        for v in vs:
            x, y = v.co.x + 0.15, v.co.y
            v.co.x = x * math.cos(giro) - y * math.sin(giro) - 0.15
            v.co.y = x * math.sin(giro) + y * math.cos(giro)
            v.co.z += 0.05 * (v.co.x + 0.45)
        _bloco_partido(bm, 0.05, 0.42, -0.05, 0.3, 0.0, 0.42, -1 if rng.random() < 0.5 else 1)
    _ob("estr_entulho_blocos_" + el["nome"], blocos, M["fria"])

    def lascas(bm):
        for k in range(6):
            x, y = rng.uniform(-0.48, 0.48), rng.uniform(-0.36, 0.36)
            s = rng.uniform(0.04, 0.09)
            _caixa(bm, x - s, x + s, y - s * 0.8, y + s * 0.8, 0.0, s)
    _ob("estr_entulho_lascas_" + el["nome"], lascas, M["fria"])
    _ob("estr_entulho_terra_" + el["nome"], lambda bm: _bola(bm, (rng.uniform(-0.15, 0.15), 0.12, 0.0), 0.36,
                                                             (1.4, 1.0, 0.32), sub=2, ruido=0.3,
                                                             sem=el.get("semente", 1), chao=0.0),
        M["terra"], suave=True)


# ---------------------------------------------------------------- a laje

def laje(el, fase):
    """A laje: um bloco da estrutura, grande, afundado de lado no chão e inclinado para trás, com
    uma quina partida. Na face da frente, sulcos rasos que não dizem nada: um arco largo que não
    fecha e, saindo dele, cinco linhas paralelas de comprimentos diferentes, com o mesmo espaço
    entre si (a regularidade é a única coisa estranha). Nada de grade, botão, janela ou painel.
    Pegada 2,2 × 1,4, altura 1,0."""
    M = _m()
    inc = math.radians(24)
    W, D, H = 1.95, 0.4, 1.25
    y0 = 0.1
    afunda = 0.32

    def F(u, z, n=0.0):
        """Ponto na face da frente (−Y do Blender), u ao longo, z subindo pela face; afunda à direita."""
        return Vector((u, y0 - D / 2 - n + math.sin(inc) * z,
                       math.cos(inc) * z - afunda - 0.08 * (u + W / 2)))

    def bloco(bm):
        v = {}
        for a, (u, n) in enumerate(((-W / 2, 0), (W / 2, 0), (W / 2, -D), (-W / 2, -D))):
            for b, z in enumerate((0.0, H)):
                v[a, b] = bm.verts.new(F(u, z, n))
        # quina de cima à esquerda, partida
        cq = bm.verts.new(F(-W / 2 + 0.38, H, 0))
        cl = bm.verts.new(F(-W / 2, H - 0.3, 0))
        cqt = bm.verts.new(F(-W / 2 + 0.38, H, -D))
        clt = bm.verts.new(F(-W / 2, H - 0.3, -D))
        bm.faces.new((v[0, 0], v[1, 0], v[1, 1], cq, cl))            # frente
        bm.faces.new((v[2, 0], v[3, 0], clt, cqt, v[2, 1]))          # trás
        bm.faces.new((cq, v[1, 1], v[2, 1], cqt))                     # topo
        bm.faces.new((cl, cq, cqt, clt))                              # a quebra
        bm.faces.new((v[3, 0], v[2, 0], v[1, 0], v[0, 0]))            # base
        bm.faces.new((v[1, 0], v[2, 0], v[2, 1], v[1, 1]))            # lado leste
        bm.faces.new((v[3, 0], v[0, 0], cl, clt))                     # lado oeste
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    _ob("estr_laje", bloco, M["fria"])

    def sulco(bm, a, b, larg=0.06):
        d = (b - a)
        lado = d.cross(Vector((0, -1, 0))).normalized() * larg / 2
        vs = [bm.verts.new(p) for p in (a - lado, a + lado, b + lado, b - lado)]
        bm.faces.new(vs)

    def sulcos(bm):
        e = 0.006
        cu, cz, r = -0.5, 0.62, 0.3
        for k in range(11):                     # arco de 220°, aberto para a direita
            a0, a1 = math.radians(70 + 20 * k), math.radians(70 + 20 * (k + 1))
            sulco(bm, F(cu + r * math.cos(a0), cz + r * math.sin(a0), e),
                  F(cu + r * math.cos(a1), cz + r * math.sin(a1), e))
        for k, comp in enumerate((0.55, 1.05, 0.8, 1.2, 0.4)):   # linhas que saem do arco
            z = cz - 0.24 + k * 0.12
            sulco(bm, F(cu + 0.05, z, e), F(cu + 0.05 + comp, z, e))
    _ob("estr_laje_sulcos", sulcos, M["junta"])

    def lascas(bm):
        rng = random.Random(9)
        for k in range(5):
            x, y = rng.uniform(-1.05, -0.4), rng.uniform(-0.6, -0.2)
            s = rng.uniform(0.04, 0.09)
            _caixa(bm, x - s, x + s, y - s, y + s * 0.6, 0.0, s * 0.8)
    _ob("estr_laje_lascas", lascas, M["fria"])


# ---------------------------------------------------------------- a luz do buraco

def luz_buraco(el, fase):
    """Embaixo do buraco: a escada que alguém desceu, a terra e o capim que caíram, raízes
    penduradas da borda, e a luz fria que desce pontilhada, com poeira (laço de `quadros`)."""
    M = _m()
    rng = random.Random(el.get("semente", 5))
    topo = 4.2
    # o feixe: um tronco de cone, mais largo embaixo, do teto ao chão
    centro = Vector((-0.15, 0.2, 0.0))

    def feixe(bm):
        seg = 12
        baixo, cima = [], []
        for k in range(seg):
            a = 2 * math.pi * k / seg
            baixo.append(bm.verts.new(centro + Vector((math.cos(a) * 0.62, math.sin(a) * 0.5, 0.02))))
            cima.append(bm.verts.new(centro + Vector((math.cos(a) * 0.4, math.sin(a) * 0.34, topo))))
        for k in range(seg):
            a = 2 * math.pi * (k + 0.5) / seg
            if math.cos(a) - math.sin(a) > 0.3:   # metade virada para a câmera (+X, −Y): fora
                continue
            bm.faces.new((baixo[k], baixo[(k + 1) % seg], cima[(k + 1) % seg], cima[k]))
    _sem_sombra(_ob("estr_feixe", feixe, _feixe()))

    # a poeira: grãos que descem devagar dentro do feixe (fase 0..1 = uma volta do laço)
    def poeira(bm):
        r = random.Random(17)
        for k in range(14):
            a, rr = r.uniform(0, 2 * math.pi), 0.42 * math.sqrt(r.random())
            z = (r.uniform(0.3, topo - 0.3) - fase * 0.7) % (topo - 0.3) + 0.15
            d = Vector((math.cos(a + fase * 0.6) * rr, math.sin(a + fase * 0.6) * rr * 0.8, z))
            p = centro + d
            _caixa(bm, p.x - 0.022, p.x + 0.022, p.y - 0.022, p.y + 0.022, p.z - 0.03, p.z + 0.03)
    _sem_sombra(_ob("estr_poeira", poeira, _poeira()))

    # o monte de terra caída, com capim de cima
    _ob("estr_monte", lambda bm: _bola(bm, (centro.x, centro.y, 0.0), 0.75, (1.0, 0.9, 0.28), sub=3, ruido=0.28,
                                       sem=3.0, chao=0.0), M["terra"], suave=True)

    def capim(bm):
        for k in range(16):
            a, rr = rng.uniform(0, 2 * math.pi), 0.55 * math.sqrt(rng.random())
            x, y = centro.x + math.cos(a) * rr, centro.y + math.sin(a) * rr * 0.9
            z = 0.2 * (1 - (rr / 0.75) ** 2)
            dx, dy = rng.uniform(-0.06, 0.06), rng.uniform(-0.06, 0.06)
            _tubo(bm, [Vector((x, y, z - 0.02)), Vector((x + dx, y + dy, z + rng.uniform(0.1, 0.18)))],
                  [0.018, 0.006], seg=3)
    _ob("estr_capim", capim, M["grama"])

    # a escada de mão, de madeira da vila, encostada na borda do buraco (sobe para o noroeste)
    pe = Vector((0.15, -0.15, 0.0))
    cima = Vector((-0.35, 0.55, topo))
    lado = Vector((0.26, 0.18, 0.0)).normalized() * 0.24

    def escada(bm):
        for s in (-1, 1):
            a, b = pe + lado * s, cima + lado * s
            _tubo(bm, [a, b], [0.045, 0.04], seg=5)
        for k in range(1, 12):
            t = k / 12.0
            c = pe.lerp(cima, t)
            if c.z > topo - 0.2:
                break
            _tubo(bm, [c - lado * 1.05, c + lado * 1.05], [0.028, 0.028], seg=4)
    _ob("estr_escada", escada, M["madeira"])

    # raízes penduradas da borda do buraco
    def raizes(bm):
        for k in range(6):
            a = 2 * math.pi * k / 6 + rng.uniform(-0.3, 0.3)
            p0 = centro + Vector((math.cos(a) * 0.5, math.sin(a) * 0.42, topo))
            comp = rng.uniform(0.5, 1.2)
            pts, rs = [], []
            for i in range(5):
                t = i / 4
                pts.append(p0 + Vector((rng.uniform(-0.04, 0.04) - math.cos(a) * 0.08 * t,
                                        rng.uniform(-0.04, 0.04) - math.sin(a) * 0.08 * t, -comp * t)))
                rs.append(0.035 * (1 - 0.7 * t))
            _tubo(bm, pts, rs, seg=4)
    _ob("estr_raizes", raizes, M["raiz"])
    return {"luz0": [round(centro.x, 3), round(-centro.y, 3), 3.0]}


# ---------------------------------------------------------------- o chão

def chao(t, fase=0.0):
    """Lajes de 2 × 2 m, lisas, juntas finas e retas: periódico em 8 m (o quadro de chão).
    As lajes são planas (inclinadas, cruzavam o fundo das juntas); a variação de valor é um
    ruído largo e fraco, pontilhado."""
    M = materiais.material("estr_chao", "ardosia", k=0.35, ruido=1.2, oa=0.0, contorno=False, linhas=False,
                           pontilhar=True, tons=[1, 4])
    J = materiais.material("estr_chao_junta", "quase_preto", k=0.0, oa=0.0, contorno=False, linhas=False,
                           tons=[1, 2])
    L = 2.0
    meia = 20.0
    n = int(2 * meia / L)
    rng = random.Random(t.get("semente", 7))
    inclin = {(i, k): (rng.uniform(-0.035, 0.035), rng.uniform(-0.035, 0.035)) for i in range(4) for k in range(4)}

    def lajes(bm):
        for i in range(n):
            for k in range(n):
                x0, y0 = -meia + i * L, -meia + k * L
                ix, iy = int(round(x0 / L)) % 4, int(round(y0 / L)) % 4
                ax, ay = inclin[(ix, iy)]
                g = JUNTA * 0.5
                cs = [(x0 + g, y0 + g), (x0 + L - g, y0 + g), (x0 + L - g, y0 + L - g), (x0 + g, y0 + L - g)]
                cx, cy = x0 + L / 2, y0 + L / 2
                vs = [bm.verts.new((x, y, 0.02 + 0.004 * (ax + ay) / 0.035)) for (x, y) in cs]
                bm.faces.new(vs)
    _ob("estr_chao_lajes", lajes, M)
    _ob("estr_chao_fundo", lambda bm: bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=meia), J)


FORMAS = {f.__name__: f for f in (parede, canto, fibra, entulho, laje, luz_buraco)}
