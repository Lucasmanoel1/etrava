"""Móveis e objetos dos interiores (V6): mesa, mesa longa, balcão, bancada, cama, baú, mó, ervas
penduradas e lareira, com o que fica em cima deles (velas, canecas, pão, ferramentas).

Mesmo contrato dos objetos de fora (`construcoes/objetos.py`): cada forma é `f(el, fase)`, monta o
móvel centrado na origem dentro da pegada do `content/props.yaml` (x = leste; Y do Blender = -y do
jogo; a frente do cômodo, sul, é -Y) e devolve os pontos em metros do jogo ({"luz0": [x, y, z]}).
`fase` (0..1) só mexe no fogo (chama de vela e lareira em laço).
"""
import math
import random

import bmesh
from mathutils import Vector, noise

from construcoes.casa import _bm_caixa as cx, _diagonal as diag, _objeto_de as ob
from construcoes.objetos import _bola, _cil, _m
from nucleo import materiais


def _mm():
    """Os materiais dos objetos de fora e os de dentro de casa."""
    M = dict(_m())
    mat = materiais.material
    M.update({
        "cera": mat("mov_cera", "linho", k=0.1, oa=0.4, luz_min=0.35, tons=[3, 5], linhas=False),
        "latao": mat("mov_latao", "latao", k=0.1, oa=0.6),
        "barro": mat("obj_barro", "ferrugem", k=0.1, oa=0.6),
        "pao": mat("mov_pao", "palha", k=0.3, ruido=22.0, oa=0.6),
        "bebida": mat("mov_bebida", "madeira_escura", k=0.0, oa=0.0, linhas=False, contorno=False),
        "carvao": mat("obj_carvao", "quase_preto", k=0.4, ruido=14.0, oa=0.5, linhas=False),
        "brasa": mat("mov_brasa", "ferrugem", k=0.5, ruido=18.0, emissivo=True, contorno=False, linhas=False,
                     luz_min=0.4, oa=0.0, rampa_brilho="ferrugem"),
        # chama da lareira em duas partes: o pé claro (fogo, tons altos) e as pontas laranja/ferrugem,
        # que brilham pouco; uma chama só de tom claro somava ao brilho e estourava no jogo
        "fogo_pe": mat("mov_fogo_pe", "fogo", k=0.0, emissivo=True, contorno=False, linhas=False, luz_min=0.45,
                       oa=0.0, tons=[1, 3], rampa_brilho="ferrugem"),
        "fogo_ponta": mat("mov_fogo_ponta", "fogo", k=0.0, emissivo=True, contorno=False, linhas=False, luz_min=0.2,
                          oa=0.0, tons=[0, 1], rampa_brilho="quase_preto"),
        "pedra_clara": mat("mov_pedra_clara", "pedra_clara", k=0.8, textura="pedra.png", escala=1.2, oa=0.9),
        "reboco": mat("mov_reboco", "reboco", k=0.5, textura="reboco.png", escala=1.0, oa=0.9, linhas=False),
        "erva": mat("erva", "folha_samambaia", k=0.3, ruido=12.0, oa=0.6, linhas=False),
        "erva_seca": mat("erva_seca", "grama_seca", k=0.3, ruido=12.0, oa=0.6, linhas=False),
        "flor_violeta": mat("mov_flor_violeta", "flor_violeta", k=0.3, ruido=16.0, oa=0.5, linhas=False),
        "flor_amarela": mat("mov_flor_amarela", "flor_amarela", k=0.3, ruido=16.0, oa=0.5, linhas=False),
        "tampo": mat("mov_tampo", "madeira_clara", k=0.7, textura="tabuas.png", escala=0.9, oa=0.85, linhas=False),
        "farinha": mat("mov_farinha", "linho", k=0.2, ruido=20.0, oa=0.5, luz_min=0.3, tons=[3, 5], linhas=False),
    })
    return M


# ---------------------------------------------------------------- peças

def _caixa_redonda(bm, c, meio, m=5.0, cortes=4):
    """Caixa de cantos arredondados (superelipsoide): centro c, meias-medidas `meio`."""
    antes = set(bm.verts)
    res = bmesh.ops.create_cube(bm, size=2.0)
    vs = res["verts"]
    bmesh.ops.subdivide_edges(bm, edges=list({e for v in vs for e in v.link_edges}), cuts=cortes,
                              use_grid_fill=True)
    for v in [v for v in bm.verts if v not in antes]:
        p = v.co
        n = (abs(p.x) ** m + abs(p.y) ** m + abs(p.z) ** m) ** (1.0 / m)
        q = p / n if n > 0 else p
        v.co = Vector((c[0] + q.x * meio[0], c[1] + q.y * meio[1], c[2] + q.z * meio[2]))


def _cil_deitado(bm, c, r, comp, eixo="x", seg=12, bojo=0.0):
    res = bmesh.ops.create_cone(bm, cap_ends=True, segments=seg, radius1=r, radius2=r, depth=comp)
    h = comp / 2
    for v in res["verts"]:
        b = 1.0 + bojo * math.cos(v.co.z / h * math.pi / 2)
        x, y, z = v.co.x * b, v.co.y * b, v.co.z
        v.co = Vector((c[0] + z, c[1] + y, c[2] + x)) if eixo == "x" else Vector((c[0] + x, c[1] + z, c[2] + y))


def _vela(nome, x, y, z, fase, M, alta=0.16, prato="latao"):
    """Vela num castiçal baixo; devolve o ponto da luz em metros do jogo."""
    if prato:
        ob(nome + "_castical", lambda bm: (_cil(bm, x, y, z, z + 0.025, 0.075, 0.07, seg=10),
                                           _cil(bm, x, y, z + 0.025, z + 0.06, 0.03, 0.035, seg=8)),
           M[prato], suave=True)
        z += 0.06
    ob(nome + "_cera", lambda bm: (_cil(bm, x, y, z, z + alta, 0.034, 0.032, seg=8),
                                   _bola(bm, (x + 0.03, y - 0.025, z + alta * 0.55), 0.016, (1, 1, 2.2), sub=1)),
       M["cera"], suave=True)
    a = 2 * math.pi * fase
    h = 0.085 + 0.022 * math.sin(a) + 0.01 * math.sin(3 * a + 1.0)
    lx = 0.012 * math.sin(2 * a + 0.5)
    topo = z + alta

    def chama(bm):
        res = bmesh.ops.create_cone(bm, cap_ends=True, segments=7, radius1=0.03, radius2=0.002, depth=h)
        for v in res["verts"]:
            t = (v.co.z + h / 2) / h
            v.co = Vector((x + v.co.x + lx * t, y + v.co.y - lx * 0.5 * t, topo + 0.012 + v.co.z + h / 2))
        _bola(bm, (x, y, topo + 0.025), 0.03, (1, 1, 1.1), sub=1)
    ob(nome + "_chama", chama, M["fogo"], suave=True)
    return [round(x, 3), round(-y, 3), round(topo + 0.05, 3)]


def _caneca(nome, x, y, z, M, cheia=True, ferro=False):
    mat = M["ferro"] if ferro else M["barro"]
    ob(nome, lambda bm: _cil(bm, x, y, z, z + 0.16, 0.068, 0.074, seg=10), mat, suave=True)
    ob(nome + "_asa", lambda bm: cx(bm, x + 0.07, x + 0.11, y - 0.015, y + 0.015, z + 0.035, z + 0.13), mat)
    if cheia:
        ob(nome + "_bebida", lambda bm: _cil(bm, x, y, z + 0.16, z + 0.162, 0.06, seg=10), M["bebida"])


def _prato_pao(nome, x, y, z, M):
    ob(nome + "_prato", lambda bm: _cil(bm, x, y, z, z + 0.025, 0.12, 0.16, seg=12), M["escura"], suave=True)
    ob(nome + "_pao", lambda bm: _bola(bm, (x - 0.02, y, z + 0.07), 0.12, (1.25, 0.85, 0.6), ruido=0.08, sem=3),
       M["pao"], suave=True)


def _jarra(nome, x, y, z, M, alta=0.26):
    ob(nome, lambda bm: (_cil(bm, x, y, z, z + alta, 0.085, 0.05, seg=10, bojo=0.45),
                         _cil(bm, x, y, z + alta - 0.01, z + alta + 0.03, 0.045, 0.06, seg=10)), M["barro"], suave=True)
    ob(nome + "_asa", lambda bm: diag(bm, Vector((x - 0.06, y, z + alta - 0.03)), Vector((x - 0.11, y, z + 0.08)),
                                      0.025), M["barro"])


def _tigela(nome, x, y, z, M, mat="clara", conteudo="pao"):
    ob(nome, lambda bm: _cil(bm, x, y, z, z + 0.08, 0.065, 0.13, seg=12), M[mat], suave=True)
    if conteudo:
        ob(nome + "_dentro", lambda bm: _cil(bm, x, y, z + 0.06, z + 0.065, 0.105, seg=12), M[conteudo])


def _tampo(nome, x0, x1, y0, y1, z0, z1, M, tabuas=3):
    """Tampo de tábuas ao comprido (ao longo de x), com a fresta entre elas e as pontas um pouco
    desencontradas: a mesa de casa de vila, não de marceneiro."""
    rng = random.Random(len(nome) * 7 + int(x1 * 10))
    w = (y1 - y0) / tabuas

    def pecas(bm):
        for i in range(tabuas):
            d = rng.uniform(-0.015, 0.015)
            cx(bm, x0 + d, x1 + rng.uniform(-0.015, 0.01), y0 + i * w + 0.008, y0 + (i + 1) * w - 0.008,
               z0, z1 - rng.uniform(0.0, 0.008))
    ob(nome, pecas, M["tampo"])
    ob(nome + "_fresta", lambda bm: cx(bm, x0 + 0.03, x1 - 0.03, y0 + 0.02, y1 - 0.02, z0, z1 - 0.012), M["breu"])


# ---------------------------------------------------------------- mesas

def mesa(el, fase):
    """Mesa de quatro pés com travessa; em cima, o que a variante pede (sempre uma vela)."""
    M = _mm()
    _tampo("mesa_tampo", -0.55, 0.55, -0.45, 0.45, 0.73, 0.79, M)

    def estrutura(bm):
        for sx in (-1, 1):
            for sy in (-1, 1):
                cx(bm, sx * 0.46 - 0.04, sx * 0.46 + 0.04, sy * 0.36 - 0.04, sy * 0.36 + 0.04, 0.0, 0.73)
        cx(bm, -0.44, 0.44, -0.37, -0.33, 0.6, 0.73)      # saia da frente
        cx(bm, -0.44, 0.44, 0.33, 0.37, 0.6, 0.73)
        cx(bm, 0.44, 0.48, -0.34, 0.34, 0.6, 0.73)
        cx(bm, -0.48, -0.44, -0.34, 0.34, 0.6, 0.73)
        cx(bm, -0.46, 0.46, -0.025, 0.025, 0.12, 0.17)    # travessa baixa
        for sx in (-1, 1):
            cx(bm, sx * 0.46 - 0.025, sx * 0.46 + 0.025, -0.36, 0.36, 0.12, 0.17)
    ob("mesa_estrutura", estrutura, M["madeira"])
    z = 0.79
    v = el.get("variante", "a")
    if v == "a":
        luz = _vela("mesa_vela", 0.28, 0.17, z, fase, M)
        _caneca("mesa_caneca", -0.22, -0.18, z, M)
        _prato_pao("mesa_ceia", -0.18, 0.17, z, M)
    elif v == "b":
        ob("mesa_toalha", lambda bm: (cx(bm, -0.57, 0.57, -0.2, 0.08, z, z + 0.008),
                                      cx(bm, -0.575, -0.565, -0.2, 0.08, z - 0.16, z + 0.008),
                                      cx(bm, 0.565, 0.575, -0.2, 0.08, z - 0.16, z + 0.008)), M["linho"])
        luz = _vela("mesa_vela", -0.3, 0.2, z, fase, M, alta=0.1, prato="barro")
        _jarra("mesa_jarra", 0.27, 0.16, z, M)
        _tigela("mesa_tigela", 0.02, -0.08, z + 0.008, M, mat="escura")
        ob("mesa_colher", lambda bm: diag(bm, Vector((-0.18, -0.2, z + 0.02)), Vector((-0.06, -0.12, z + 0.02)), 0.025),
           M["clara"])
    else:
        luz = _vela("mesa_vela", 0.02, 0.22, z, fase, M, alta=0.12)
        _caneca("mesa_caneca", -0.26, -0.12, z, M)
        _caneca("mesa_caneca2", 0.27, -0.2, z, M, ferro=True)
        ob("mesa_dados", lambda bm: (cx(bm, -0.02, 0.03, -0.17, -0.12, z, z + 0.05),
                                     cx(bm, 0.06, 0.11, -0.1, -0.05, z, z + 0.05)), M["linho"])
        ob("mesa_moedas", lambda bm: (_cil(bm, -0.3, 0.17, z, z + 0.02, 0.035, seg=8),
                                      _cil(bm, -0.24, 0.22, z, z + 0.035, 0.035, seg=8)), M["latao"], suave=True)
    return {"luz0": luz}


def mesa_longa(el, fase):
    """Mesa de cavaletes, comprida (taverna): tampo grosso, dois cavaletes e a travessa."""
    M = _mm()
    _tampo("ml_tampo", -1.0, 1.0, -0.45, 0.45, 0.72, 0.79, M, tabuas=3)

    def cavaletes(bm):
        for x in (-0.72, 0.72):
            cx(bm, x - 0.045, x + 0.045, -0.3, 0.3, 0.1, 0.72)      # tábua de pé
            cx(bm, x - 0.06, x + 0.06, -0.4, 0.4, 0.0, 0.1)         # pé
            cx(bm, x - 0.05, x + 0.05, -0.38, 0.38, 0.64, 0.72)     # travessa de cima
        cx(bm, -0.78, 0.78, -0.03, 0.03, 0.36, 0.44)                # travessa ao comprido
        for x in (-0.82, 0.82):                                      # cunhas da travessa
            cx(bm, x - 0.03, x + 0.03, -0.05, 0.05, 0.34, 0.47)
    ob("ml_cavaletes", cavaletes, M["madeira"])
    z = 0.79
    luz = _vela("ml_vela", 0.0, 0.12, z, fase, M)
    _caneca("ml_caneca0", -0.75, -0.2, z, M)
    _caneca("ml_caneca1", -0.35, 0.22, z, M, cheia=False)
    _caneca("ml_caneca2", 0.62, -0.15, z, M, ferro=True)
    _tigela("ml_tigela0", -0.42, -0.14, z, M, mat="escura")
    _tigela("ml_tigela1", 0.35, 0.2, z, M, mat="barro", conteudo="pao")
    _jarra("ml_jarra", 0.82, 0.18, z, M)
    _prato_pao("ml_ceia", 0.18, -0.18, z, M)
    return {"luz0": luz}


def balcao(el, fase):
    """Balcão da taverna: frente de tábua com pilares, tampo largo, canecas, jarra, pano, uma vela
    e um barrilete deitado na ponta, com torneira."""
    M = _mm()

    def corpo(bm):
        cx(bm, -1.38, 1.38, -0.33, 0.3, 0.06, 1.0)
    ob("balcao_corpo", corpo, M["tabuado"])

    def molduras(bm):
        cx(bm, -1.42, 1.42, -0.37, 0.33, 0.0, 0.08)                 # rodapé
        for x in (-1.36, -0.46, 0.46, 1.36):                        # pilares da frente
            cx(bm, x - 0.055, x + 0.055, -0.38, -0.3, 0.0, 1.0)
        cx(bm, -1.4, 1.4, -0.37, -0.31, 0.86, 0.93)                 # friso alto
        cx(bm, 1.36, 1.42, -0.3, 0.3, 0.0, 1.0)                     # lado leste
    ob("balcao_moldura", molduras, M["escura"])
    _tampo("balcao_tampo", -1.45, 1.45, -0.42, 0.4, 1.0, 1.07, M, tabuas=2)
    z = 1.07
    _caneca("balcao_caneca0", -1.05, -0.18, z, M)
    _caneca("balcao_caneca1", -0.86, 0.02, z, M, cheia=False)
    _caneca("balcao_caneca2", 0.35, -0.2, z, M, ferro=True)
    ob("balcao_pano", lambda bm: _caixa_redonda(bm, (-0.35, 0.08, z + 0.03), (0.18, 0.12, 0.03), m=6.0, cortes=2),
       M["linho"], suave=True)
    _jarra("balcao_jarra", 0.72, 0.15, z, M)
    luz = _vela("balcao_vela", -0.05, 0.18, z, fase, M)
    # barrilete deitado no berço, com a torneira virada para o salão
    c = Vector((1.12, 0.06, z + 0.2))
    ob("balcao_berco", lambda bm: (cx(bm, c.x - 0.2, c.x - 0.15, -0.14, 0.26, z, z + 0.1),
                                   cx(bm, c.x + 0.15, c.x + 0.2, -0.14, 0.26, z, z + 0.1)), M["escura"])
    ob("balcao_barrilete", lambda bm: _cil_deitado(bm, c, 0.17, 0.46, eixo="y", seg=14, bojo=0.14), M["tabuado"],
       suave=True)
    ob("balcao_aros", lambda bm: (_cil_deitado(bm, (c.x, c.y - 0.15, c.z), 0.183, 0.04, eixo="y", seg=14),
                                  _cil_deitado(bm, (c.x, c.y + 0.15, c.z), 0.183, 0.04, eixo="y", seg=14)),
       M["ferro"], suave=True)
    ob("balcao_tampa", lambda bm: _cil_deitado(bm, (c.x, c.y - 0.235, c.z), 0.14, 0.012, eixo="y", seg=14),
       M["escura"])
    ob("balcao_torneira", lambda bm: (cx(bm, c.x - 0.02, c.x + 0.02, c.y - 0.31, c.y - 0.23, c.z - 0.08, c.z - 0.045),
                                      cx(bm, c.x - 0.015, c.x + 0.015, c.y - 0.31, c.y - 0.28, c.z - 0.13, c.z - 0.06)),
       M["latao"])
    return {"luz0": luz}


# ---------------------------------------------------------------- trabalho

def bancada(el, fase):
    """Bancada da oficina: tampo grosso, prateleira de baixo com tábuas, painel de ferramentas
    atrás, torno na ponta e o trabalho do dia em cima (plaina, aparas, martelo)."""
    M = _mm()
    ob("bancada_tampo", lambda bm: cx(bm, -1.0, 1.0, -0.5, 0.38, 0.78, 0.9), M["tabuado"])

    def pernas(bm):
        for x in (-0.88, 0.88):
            for y in (-0.42, 0.3):
                cx(bm, x - 0.06, x + 0.06, y - 0.06, y + 0.06, 0.0, 0.78)
            cx(bm, x - 0.04, x + 0.04, -0.42, 0.3, 0.14, 0.22)
        cx(bm, -0.9, 0.9, -0.44, 0.32, 0.22, 0.26)                  # prateleira
    ob("bancada_pernas", pernas, M["madeira"])
    ob("bancada_estoque", lambda bm: (cx(bm, -0.8, 0.55, -0.3, -0.12, 0.26, 0.31),
                                      cx(bm, -0.7, 0.7, -0.08, 0.12, 0.26, 0.3),
                                      cx(bm, -0.6, 0.4, -0.25, -0.05, 0.31, 0.35)), M["clara"])
    # painel de ferramentas na beira de trás
    ob("bancada_painel", lambda bm: (cx(bm, -0.95, 0.95, 0.4, 0.46, 0.9, 1.62),
                                     cx(bm, -1.0, -0.92, 0.38, 0.5, 0.0, 1.66),
                                     cx(bm, 0.92, 1.0, 0.38, 0.5, 0.0, 1.66)), M["escura"])
    yf = 0.39

    def ferros(bm):
        # serrote (lâmina), tenaz, formões, lâmina do martelo pendurado
        vs = [bm.verts.new((-0.78, yf, 1.42)), bm.verts.new((-0.38, yf, 1.38)), bm.verts.new((-0.38, yf, 1.24)),
              bm.verts.new((-0.78, yf, 1.32))]
        bm.faces.new(vs)
        bmesh.ops.solidify(bm, geom=bm.faces[:], thickness=0.012)
        diag(bm, Vector((-0.2, yf, 1.5)), Vector((-0.12, yf, 1.02)), 0.025)
        diag(bm, Vector((-0.14, yf, 1.5)), Vector((-0.2, yf, 1.02)), 0.025)
        for k in range(3):
            cx(bm, 0.08 + k * 0.09, 0.1 + k * 0.09, yf - 0.01, yf, 1.18 - k * 0.03, 1.3)
        cx(bm, 0.52, 0.68, yf - 0.05, yf, 1.42, 1.48)
        cx(bm, 0.72, 0.9, yf - 0.02, yf, 1.06, 1.36)                 # esquadro
        cx(bm, 0.72, 0.76, yf - 0.02, yf, 1.06, 1.46)
    ob("bancada_ferros", ferros, M["ferro"])

    def cabos(bm):
        cx(bm, -0.36, -0.22, yf - 0.03, yf, 1.24, 1.36)              # cabo do serrote
        for k in range(3):
            cx(bm, 0.075 + k * 0.09, 0.105 + k * 0.09, yf - 0.03, yf, 1.3, 1.42 - k * 0.03)
        cx(bm, 0.58, 0.62, yf - 0.04, yf, 1.12, 1.42)                # cabo do martelo
        for x in (-0.6, -0.16, 0.18, 0.6):                           # pinos
            cx(bm, x - 0.012, x + 0.012, yf - 0.05, yf, 1.5, 1.52)
    ob("bancada_cabos", cabos, M["clara"])
    # torno de bancada na ponta leste
    ob("bancada_torno", lambda bm: (cx(bm, 0.72, 0.95, -0.54, -0.42, 0.62, 0.98),
                                    cx(bm, 0.8, 0.86, -0.75, -0.54, 0.78, 0.82),
                                    cx(bm, 0.7, 0.97, -0.76, -0.73, 0.79, 0.81)), M["escura"])
    ob("bancada_fuso", lambda bm: _cil_deitado(bm, (0.83, -0.6, 0.8), 0.022, 0.28, eixo="y", seg=8), M["ferro"],
       suave=True)
    # o trabalho: uma tábua presa, a plaina, aparas, o martelo deitado
    ob("bancada_peca", lambda bm: cx(bm, 0.05, 0.9, -0.32, -0.14, 0.9, 0.94), M["clara"])
    ob("bancada_plaina", lambda bm: _caixa_redonda(bm, (-0.25, -0.2, 0.95), (0.16, 0.05, 0.05), m=4.0, cortes=2),
       M["madeira"], suave=True)
    ob("bancada_plaina_ferro", lambda bm: cx(bm, -0.27, -0.23, -0.24, -0.16, 0.98, 1.03), M["ferro"])
    rng = random.Random(7)

    def aparas(bm):
        for k in range(9):
            x, y = rng.uniform(-0.65, 0.0), rng.uniform(-0.4, 0.1)
            _bola(bm, (x, y, 0.905), rng.uniform(0.03, 0.05), (1.4, 1, 0.5), sub=1, ruido=0.3, sem=k)
    ob("bancada_aparas", aparas, M["palha"], suave=True)
    ob("bancada_martelo", lambda bm: diag(bm, Vector((-0.75, -0.3, 0.92)), Vector((-0.5, -0.05, 0.92)), 0.035),
       M["clara"])
    ob("bancada_martelo_ferro", lambda bm: cx(bm, -0.55, -0.43, -0.12, 0.02, 0.9, 0.97), M["ferro"])


def mo(el, fase):
    """Mó do moinho: base de tábua, o tonel que cobre as pedras, a pedra de cima aparecendo,
    a moega com o grão, a bica de farinha e um saco aberto embaixo."""
    M = _mm()
    ob("mo_base", lambda bm: cx(bm, -0.85, 0.85, -0.85, 0.85, 0.0, 0.22), M["tabuado"])
    ob("mo_base_borda", lambda bm: (cx(bm, -0.87, 0.87, -0.87, -0.8, 0.0, 0.24),
                                    cx(bm, 0.8, 0.87, -0.87, 0.87, 0.0, 0.24)), M["escura"])
    ob("mo_tonel", lambda bm: _cil(bm, 0.0, 0.05, 0.22, 0.6, 0.6, 0.6, seg=16), M["tabuado"], suave=True)
    ob("mo_aros", lambda bm: (_cil(bm, 0.0, 0.05, 0.27, 0.31, 0.615, seg=16),
                              _cil(bm, 0.0, 0.05, 0.52, 0.56, 0.615, seg=16)), M["ferro"], suave=True)
    ob("mo_pedra", lambda bm: _cil(bm, 0.0, 0.05, 0.6, 0.7, 0.54, 0.52, seg=16), M["pedra_clara"], suave=True)
    ob("mo_olho", lambda bm: _cil(bm, 0.0, 0.05, 0.7, 0.705, 0.09, seg=10), M["breu"])
    ob("mo_po", lambda bm: (_cil(bm, 0.25, -0.62, 0.22, 0.235, 0.22, seg=10),
                            _cil(bm, 0.0, 0.05, 0.6, 0.605, 0.6, seg=16)), M["farinha"])

    def moega(bm):
        res = bmesh.ops.create_cube(bm, size=1.0)
        for v in res["verts"]:
            t = v.co.z + 0.5
            s = 0.16 + 0.36 * t
            v.co = Vector((v.co.x * s, 0.05 + v.co.y * s, 0.86 + 0.44 * t))
        bm.normal_update()
        bmesh.ops.delete(bm, geom=[f for f in bm.faces if f.normal.z > 0.9], context="FACES")
        bmesh.ops.solidify(bm, geom=bm.faces[:], thickness=0.03)
    ob("mo_moega", moega, M["tabuado"])
    ob("mo_grao", lambda bm: _bola(bm, (0.0, 0.05, 1.2), 0.24, (1, 1, 0.25), sub=2, ruido=0.2), M["palha"], suave=True)

    def cavalete(bm):
        for sx in (-1, 1):
            diag(bm, Vector((sx * 0.48, 0.05, 0.7)), Vector((sx * 0.22, 0.05, 1.12)), 0.05)
        cx(bm, -0.3, 0.3, 0.01, 0.09, 1.08, 1.13)
        cx(bm, -0.03, 0.03, 0.02, 0.08, 0.7, 0.93)                  # sapata
    ob("mo_cavalete", cavalete, M["escura"])
    # bica de farinha na frente, para o saco
    ob("mo_bica", lambda bm: diag(bm, Vector((0.2, -0.46, 0.58)), Vector((0.26, -0.64, 0.52)), 0.09), M["clara"])
    ob("mo_saco", lambda bm: _bola(bm, (0.27, -0.64, 0.34), 0.16, (1.1, 1.0, 0.9), ruido=0.12, sem=5, chao=0.22),
       M["linho"], suave=True)
    ob("mo_saco_boca", lambda bm: _cil(bm, 0.27, -0.64, 0.44, 0.5, 0.08, 0.11, seg=8), M["linho"], suave=True)
    # pá de farinha encostada
    ob("mo_pa", lambda bm: (diag(bm, Vector((-0.75, -0.7, 0.22)), Vector((-0.55, -0.35, 0.95)), 0.035),
                            cx(bm, -0.83, -0.63, -0.8, -0.66, 0.22, 0.24)), M["clara"])


# ---------------------------------------------------------------- quarto

def cama(el, fase):
    """Cama de madeira, cabeceira a oeste: colchão de palha em linho, travesseiro e a coberta
    de lã caindo dos lados, dobrada na cabeceira."""
    M = _mm()
    pano = el.get("pano", "la_crua")
    coberta = materiais.material("mov_coberta_" + pano, pano, k=0.35, textura="linho.png", escala=1.6, oa=0.6,
                                 luz_min=0.1)

    def armacao(bm):
        for y in (-0.5, 0.44):
            cx(bm, -0.97, 0.97, y, y + 0.06, 0.18, 0.34)            # laterais
        for y in (-0.5, 0.44):
            cx(bm, -1.05, -0.95, y, y + 0.06, 0.0, 0.92)            # postes da cabeceira
            cx(bm, 0.95, 1.05, y, y + 0.06, 0.0, 0.58)              # postes dos pés
        cx(bm, -1.07, -0.93, -0.52, 0.52, 0.88, 0.94)               # travessa da cabeceira
        cx(bm, 0.93, 1.07, -0.52, 0.52, 0.54, 0.6)
    ob("cama_armacao", armacao, M["madeira"])
    ob("cama_paineis", lambda bm: (cx(bm, -1.03, -0.97, -0.44, 0.44, 0.3, 0.88),
                                   cx(bm, 0.97, 1.03, -0.44, 0.44, 0.26, 0.54)), M["tabuado"])
    ob("cama_colchao", lambda bm: _caixa_redonda(bm, (0.0, -0.03, 0.42), (0.95, 0.44, 0.1), m=4.0), M["linho"],
       suave=True)
    ob("cama_travesseiro", lambda bm: _bola(bm, (-0.74, -0.03, 0.56), 0.2, (0.85, 1.55, 0.42), ruido=0.06, sem=2),
       M["linho"], suave=True)
    rng = random.Random(el.get("semente", 3))
    dobra = -0.42

    def manta(bm):
        nx, ny = 16, 14
        res = bmesh.ops.create_grid(bm, x_segments=nx, y_segments=ny, size=1.0)
        lado_x = max(abs(v.co.x) for v in res["verts"])
        lado_y = max(abs(v.co.y) for v in res["verts"])
        for v in res["verts"]:
            u = (v.co.x / lado_x + 1) / 2   # 0 na dobra, 1 nos pés
            s = v.co.y / lado_y             # -1..1 de um lado ao outro
            x = dobra + u * (0.99 - dobra)
            larg = 0.8
            y = -0.03 + s * larg
            lado = max(0.0, abs(y + 0.03) - 0.4)      # quanto passou da beira do colchão
            z = 0.535 - lado * 0.95 - 0.02 * (abs(s) ** 2)
            z = max(z, 0.2)
            if lado > 0:                               # cai por fora da lateral da cama
                y = -0.03 + math.copysign(0.43 + min(lado, 0.08) * 1.4, s)
                z += 0.025 * math.sin(x * 14.0) * min(1.0, lado * 5)   # dobras no pano que cai
            z += 0.012 * math.sin(x * 9.0 + rng.uniform(0, 0.3)) + 0.01 * noise.noise(Vector((x * 3, y * 3, 1.0)))
            v.co = Vector((x, y, z))
        bmesh.ops.solidify(bm, geom=bm.faces[:], thickness=0.03)
    ob("cama_manta", manta, coberta, suave=True)
    ob("cama_dobra", lambda bm: _caixa_redonda(bm, (dobra + 0.08, -0.03, 0.56), (0.1, 0.6, 0.03), m=3.0, cortes=3),
       coberta, suave=True)


def bau(el, fase):
    """Baú de tábua com tampa abaulada, cintas e cantoneiras de ferro, fechadura de latão."""
    M = _mm()
    ob("bau_corpo", lambda bm: cx(bm, -0.46, 0.46, -0.3, 0.3, 0.05, 0.4), M["tabuado"])
    ob("bau_pes", lambda bm: [cx(bm, sx * 0.42 - 0.05, sx * 0.42 + 0.05, sy * 0.26 - 0.05, sy * 0.26 + 0.05, 0.0, 0.06)
                              for sx in (-1, 1) for sy in (-1, 1)], M["escura"])

    def tampa(bm, r=0.31, comp=0.94, x=0.0):
        res = bmesh.ops.create_cone(bm, cap_ends=True, segments=14, radius1=r, radius2=r, depth=comp)
        for v in res["verts"]:
            yy, zz = v.co.y, max(0.0, v.co.x)
            v.co = Vector((x + v.co.z, yy, 0.4 + zz * 0.55))
        bmesh.ops.remove_doubles(bm, verts=bm.verts[:], dist=0.001)
    ob("bau_tampa", tampa, M["tabuado"], suave=True)

    def cintas(bm):
        for x in (-0.3, 0.3):
            tampa(bm, r=0.322, comp=0.06, x=x)
            cx(bm, x - 0.03, x + 0.03, -0.312, 0.312, 0.05, 0.4)
        for sx in (-1, 1):                                           # cantoneiras
            cx(bm, sx * 0.46 - 0.05, sx * 0.46 + 0.05, -0.312, -0.29, 0.05, 0.4)
            cx(bm, sx * 0.465 - 0.012, sx * 0.465 + 0.012, -0.3, 0.3, 0.33, 0.4)
        cx(bm, 0.465, 0.475, -0.08, 0.08, 0.22, 0.26)                # alça do lado
    ob("bau_cintas", cintas, M["ferro"], suave=False)
    ob("bau_fecho", lambda bm: cx(bm, -0.06, 0.06, -0.33, -0.31, 0.27, 0.42), M["latao"])
    ob("bau_buraco", lambda bm: cx(bm, -0.012, 0.012, -0.335, -0.33, 0.32, 0.36), M["breu"])


def ervas(el, fase):
    """Varal de secar ervas da curandeira: dois pés, duas varas, maços de cabeça para baixo
    (verdes, secos, flores), um cesto e o pilão no chão."""
    M = _mm()

    def armacao(bm):
        for x in (-0.56, 0.56):
            cx(bm, x - 0.04, x + 0.04, -0.035, 0.035, 0.0, 1.88)
            cx(bm, x - 0.05, x + 0.05, -0.24, 0.24, 0.0, 0.07)
            diag(bm, Vector((x, -0.2, 0.06)), Vector((x, -0.02, 0.45)), 0.04)
            diag(bm, Vector((x, 0.2, 0.06)), Vector((x, 0.02, 0.45)), 0.04)
    ob("ervas_armacao", armacao, M["madeira"])
    ob("ervas_varas", lambda bm: (diag(bm, Vector((-0.66, 0, 1.8)), Vector((0.66, 0, 1.79)), 0.045),
                                  diag(bm, Vector((-0.66, 0, 1.18)), Vector((0.66, 0, 1.19)), 0.045)), M["clara"])
    rng = random.Random(11)
    mats = ["erva", "erva_seca", "flor_violeta", "erva", "flor_amarela", "erva_seca", "erva"]
    k = 0
    for z0, n in ((1.78, 7), (1.16, 6)):
        for i in range(n):
            x = -0.5 + 1.0 * (i + 0.5 + rng.uniform(-0.15, 0.15)) / n
            comp = rng.uniform(0.3, 0.44)
            y = rng.uniform(-0.03, 0.03)

            def maco(bm, x=x, comp=comp, y=y, z0=z0, k=k):
                res = bmesh.ops.create_cone(bm, cap_ends=True, segments=7, radius1=0.025, radius2=0.085, depth=comp)
                for v in res["verts"]:
                    t = (v.co.z + comp / 2) / comp       # 0 no talo, 1 na ponta
                    f = 1.0 + 0.25 * noise.noise(Vector((v.co.x * 9, v.co.y * 9, t * 3 + k)))
                    v.co = Vector((x + v.co.x * f, y + v.co.y * f, z0 - 0.06 - t * comp))
            ob("ervas_maco%d" % k, maco, M[mats[k % len(mats)]], suave=True)
            k += 1
    ob("ervas_amarras", lambda bm: [cx(bm, -0.5 + 1.0 * (i + 0.5) / n - 0.012, -0.5 + 1.0 * (i + 0.5) / n + 0.012,
                                       -0.01, 0.01, z0 - 0.08, z0) for z0, n in ((1.78, 7), (1.16, 6))
                                    for i in range(n)], M["palha"])
    # no chão: um cesto com o que já secou, e o pilão
    ob("ervas_cesto", lambda bm: _cil(bm, 0.24, -0.05, 0.0, 0.2, 0.13, 0.18, seg=12), M["palha"], suave=True)
    ob("ervas_cesto_dentro", lambda bm: _bola(bm, (0.24, -0.05, 0.2), 0.15, (1, 1, 0.35), sub=1, ruido=0.3),
       M["erva_seca"], suave=True)
    ob("ervas_pilao", lambda bm: _cil(bm, -0.28, -0.02, 0.0, 0.16, 0.08, 0.11, seg=10), M["pedra_clara"], suave=True)
    ob("ervas_mao", lambda bm: diag(bm, Vector((-0.28, -0.02, 0.1)), Vector((-0.2, -0.08, 0.32)), 0.04), M["clara"])


# ---------------------------------------------------------------- fogo

def lareira(el, fase):
    """Lareira de pedra encostada na parede do fundo: lar alto, ombreiras, verga de madeira com
    coisas em cima, coifa de reboco, lenha queimando em laço, caldeirão ao lado e o atiçador."""
    M = _mm()

    def pedra(bm):
        cx(bm, -0.8, 0.8, -0.45, 0.45, 0.0, 0.18)                   # lar
        cx(bm, -0.8, -0.6, -0.38, 0.45, 0.18, 1.12)                 # ombreiras
        cx(bm, 0.6, 0.8, -0.38, 0.45, 0.18, 1.12)
        cx(bm, -0.6, 0.6, 0.3, 0.45, 0.18, 1.12)                    # fundo
    ob("lareira_pedra", pedra, M["pedra"])
    ob("lareira_fuligem", lambda bm: (cx(bm, -0.6, 0.6, 0.28, 0.3, 0.18, 1.12),
                                      cx(bm, -0.6, 0.6, -0.38, 0.3, 1.1, 1.12)), M["breu"])
    ob("lareira_verga", lambda bm: cx(bm, -0.86, 0.86, -0.46, 0.45, 1.12, 1.27), M["escura"])

    def coifa(bm):
        res = bmesh.ops.create_cube(bm, size=1.0)
        for v in res["verts"]:
            t = v.co.z + 0.5
            hx = 0.74 - 0.42 * t
            y0, y1 = -0.36 + 0.4 * t, 0.45
            yy = y0 if v.co.y < 0 else y1
            v.co = Vector((v.co.x * 2 * hx, yy, 1.27 + 0.6 * t))
        cx(bm, -0.3, 0.3, 0.06, 0.45, 1.85, 2.6)                    # o cano, até o teto
    ob("lareira_coifa", coifa, M["pedra"])
    ob("lareira_coifa_friso", lambda bm: cx(bm, -0.33, 0.33, 0.03, 0.45, 1.85, 1.92), M["escura"])
    ob("lareira_verga_friso", lambda bm: cx(bm, -0.78, 0.78, -0.4, 0.45, 1.27, 1.31), M["escura"])
    # em cima da verga: dois potes e um castiçal apagado
    ob("lareira_potes", lambda bm: (_cil(bm, -0.6, -0.33, 1.27, 1.43, 0.06, 0.045, seg=8, bojo=0.3),
                                    _cil(bm, 0.52, -0.33, 1.27, 1.38, 0.055, 0.05, seg=8, bojo=0.3)), M["barro"],
       suave=True)
    ob("lareira_lata", lambda bm: _cil(bm, -0.46, -0.36, 1.27, 1.37, 0.045, seg=8), M["ferro"], suave=True)
    # cinza e brasa no lar, lenha cruzada
    ob("lareira_cinza", lambda bm: _bola(bm, (-0.08, 0.0, 0.18), 0.34, (1.3, 0.85, 0.12), sub=2, ruido=0.2,
                                         chao=0.18), M["carvao"], suave=True)

    def lenha(bm):
        _cil_deitado(bm, (-0.08, -0.02, 0.24), 0.055, 0.62, eixo="x", seg=7)
        for a, dy in ((0.5, 0.1), (-0.45, -0.08)):
            res = bmesh.ops.create_cone(bm, cap_ends=True, segments=7, radius1=0.05, radius2=0.045, depth=0.5)
            for v in res["verts"]:
                p = Vector((v.co.x, v.co.y, v.co.z))
                x = p.z * math.cos(a) - p.y * math.sin(a)
                y = p.z * math.sin(a) + p.y * math.cos(a)
                v.co = Vector((-0.08 + x, dy + y, 0.33 + p.x))
    ob("lareira_lenha", lenha, M["madeira"], suave=True)

    def brasa(bm):
        rng = random.Random(9)
        for k in range(8):
            x, y = rng.uniform(-0.38, 0.22), rng.uniform(-0.2, 0.15)
            _bola(bm, (x, y, 0.2), rng.uniform(0.04, 0.07), (1.2, 1, 0.6), sub=1, ruido=0.3, sem=k)
    ob("lareira_brasa", brasa, M["brasa"], suave=True)

    rng = random.Random(5)
    linguas = []
    for k in range(9):
        x, y = rng.uniform(-0.33, 0.17), rng.uniform(-0.15, 0.12)
        a = 0.5 + 0.5 * math.sin(2 * math.pi * (fase + k / 9.0) + k)
        alto = (0.2 + 0.36 * a) * (1.0 if k % 3 else 0.6)
        ib = 0.04 * math.sin(2 * math.pi * fase * 2 + k)
        linguas.append((x, y, alto, ib))

    def chamas(bm, de, ate, encolhe):
        for x, y, alto, ib in linguas:
            res = bmesh.ops.create_cone(bm, cap_ends=True, segments=6, radius1=0.085, radius2=0.008, depth=alto)
            for v in res["verts"]:
                t = (v.co.z + alto / 2) / alto
                tt = de + (ate - de) * t                     # só um trecho da altura da língua
                r = (0.085 + (0.008 - 0.085) * tt) / max(0.001, 0.085 + (0.008 - 0.085) * t) * encolhe
                v.co = Vector((x + v.co.x * r + ib * tt, y + v.co.y * r, 0.24 + tt * alto))
    # pé claro na frente (um pouco mais grosso), pontas laranja por cima
    ob("lareira_chamas_pe", lambda bm: chamas(bm, 0.0, 0.4, 1.08), M["fogo_pe"], suave=True)
    ob("lareira_chamas", lambda bm: chamas(bm, 0.35, 1.0, 1.0), M["fogo_ponta"], suave=True)
    # caldeirão no tripé, do lado leste da boca
    ob("lareira_caldeirao", lambda bm: (_bola(bm, (0.36, -0.05, 0.36), 0.15, (1, 1, 0.85), sub=2),
                                        _cil(bm, 0.36, -0.05, 0.42, 0.47, 0.13, seg=12)), M["ferro"], suave=True)
    ob("lareira_tripe", lambda bm: [diag(bm, Vector((0.36 + 0.15 * math.cos(a), -0.05 + 0.15 * math.sin(a), 0.18)),
                                         Vector((0.36 + 0.1 * math.cos(a), -0.05 + 0.1 * math.sin(a), 0.32)), 0.025)
                                    for a in (0.5, 2.6, 4.7)], M["ferro"])
    ob("lareira_caldo", lambda bm: _cil(bm, 0.36, -0.05, 0.465, 0.47, 0.11, seg=12), M["bebida"])
    # atiçador e pá encostados na ombreira leste; um feixinho de lenha do lado oeste
    ob("lareira_aticador", lambda bm: (diag(bm, Vector((0.92, -0.42, 0.0)), Vector((0.83, -0.36, 0.95)), 0.03),
                                       diag(bm, Vector((0.98, -0.3, 0.0)), Vector((0.86, -0.3, 0.88)), 0.03)),
       M["ferro"])

    def feixe(bm):
        for x, z in ((-1.0, 0.06), (-0.88, 0.06), (-0.94, 0.17)):     # no chão, junto da ombreira
            _cil_deitado(bm, (x, 0.05, z), 0.06, 0.55, eixo="y", seg=7)
    ob("lareira_feixe", feixe, M["madeira"], suave=True)
    return {"luz0": [-0.08, 0.5, 0.55]}


FORMAS = {f.__name__: f for f in (mesa, mesa_longa, balcao, bancada, mo, cama, bau, ervas, lareira)}
