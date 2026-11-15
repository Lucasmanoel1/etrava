"""Objetos de fora (V5): cerca, poço, lampião, banca, tenda, carroça, barril, caixote, feno, sacos,
espantalho, lápides, marco, placa, estaca, bigorna, forja, entulho, buraco, terra remexida, laje.

Cada forma é uma função `f(el, fase)` que monta o objeto centrado na origem, dentro da pegada do
`content/props.yaml` (x = leste; Y do Blender = -y do jogo), e devolve os pontos em metros do
jogo ({"luz0": [x, y, z], "fumaca0": …}) ou None. `fase` (0..1) só importa para o fogo.
"""
import math
import random

import bmesh
from mathutils import Vector, noise

from construcoes.casa import _bm_caixa as cx, _diagonal as diag, _objeto_de as ob
from natureza import decoracao
from nucleo import materiais


def _m():
    M = materiais.material
    return {
        "pedra": M("pedra", "pedra", k=0.9, textura="pedra.png", escala=0.9, oa=0.9),
        "madeira": M("obj_madeira", "madeira", k=0.5, textura="casca.png", escala=1.2, oa=0.8),
        "escura": M("obj_madeira_escura", "madeira_escura", k=0.4, textura="casca.png", escala=1.2, oa=0.7),
        "clara": M("obj_madeira_clara", "madeira_clara", k=0.4, textura="casca.png", escala=1.5, oa=0.7),
        "tabuado": M("obj_tabuado", "madeira", k=0.8, textura="tabuas.png", escala=0.9, oa=0.85, linhas=False),
        "ferro": M("obj_ferro", "ferro", k=0.1, oa=0.6),
        "linho": M("obj_linho", "linho", k=0.35, textura="linho.png", escala=1.2, oa=0.6, luz_min=0.15, tons=[1, 5]),
        "palha": M("obj_palha", "palha", k=1.1, textura="colmo.png", escala=0.8, oa=0.9, linhas=False),
        "terra": M("obj_terra", "terra", k=0.8, textura="terra.png", escala=0.8, oa=0.8, linhas=False, var_objeto=0.1),
        "breu": M("obj_breu", "quase_preto", k=0.0, oa=0.0, linhas=False, contorno=False),
        "vidro": M("obj_vidro", "vidro", k=0.1, emissivo=True, contorno=False, linhas=False, rampa_brilho="fogo"),
        "fogo": M("obj_fogo", "fogo", k=0.0, emissivo=True, contorno=False, linhas=False, luz_min=0.45, oa=0.0,
                  rampa_brilho="fogo"),
        "couro": M("obj_couro", "couro", k=0.3, textura="couro.png", escala=1.5, oa=0.7),
        "agua": M("obj_agua", "agua", k=0.0, oa=0.3, linhas=False),
    }


def _cil(bm, x, y, z0, z1, r, r2=None, seg=10, bojo=0.0):
    res = bmesh.ops.create_cone(bm, cap_ends=True, segments=seg, radius1=r, radius2=r if r2 is None else r2,
                                depth=z1 - z0)
    h = (z1 - z0) / 2
    for v in res["verts"]:
        b = 1.0 + bojo * math.cos(v.co.z / h * math.pi / 2)
        v.co = Vector((x + v.co.x * b, y + v.co.y * b, v.co.z + h + z0))


def _bola(bm, c, r, s=(1, 1, 1), sub=2, ruido=0.0, sem=0.0, chao=None):
    res = bmesh.ops.create_icosphere(bm, subdivisions=sub, radius=r)
    for v in res["verts"]:
        f = 1.0 + (ruido * noise.noise(v.co.normalized() * 1.7 + Vector((sem, sem * 0.7, 0))) if ruido else 0.0)
        v.co = Vector((c[0] + v.co.x * f * s[0], c[1] + v.co.y * f * s[1], c[2] + v.co.z * f * s[2]))
        if chao is not None:
            v.co.z = max(v.co.z, chao)


def _roda(bm, c, r, larg=0.07, seg=14):
    """Roda de carroça: aro e cubo, com o eixo ao longo de Y."""
    for rr, ll in ((r, larg), (r * 0.22, larg * 2.2)):
        res = bmesh.ops.create_cone(bm, cap_ends=True, segments=seg, radius1=rr, radius2=rr, depth=ll)
        for v in res["verts"]:
            v.co = Vector((c[0] + v.co.x, c[1] + v.co.z, c[2] + v.co.y))


# ---------------------------------------------------------------- cercas e marcos

def cerca(el, fase):
    M = _m()
    rng = random.Random(el.get("semente", 1))
    ao_x = el.get("eixo", "x") == "x"

    def T(u, n, z):
        return Vector((u, n, z)) if ao_x else Vector((n, u, z))

    def pecas(bm):
        a, b = T(-0.055, -0.055, 0), T(0.055, 0.055, 1.0 + rng.uniform(-0.05, 0.03))
        cx(bm, min(a.x, b.x), max(a.x, b.x), min(a.y, b.y), max(a.y, b.y), 0.0, b.z)
        for z in (0.42, 0.78):
            dz = rng.uniform(-0.03, 0.03)
            diag(bm, T(-0.5, -0.07, z + dz), T(0.5, -0.07, z - dz), 0.075)
    ob("cerca", pecas, M["madeira"])


def marco(el, fase):
    M = _m()
    ob("marco", lambda bm: _cil(bm, 0, 0, 0.0, 0.62, 0.19, 0.13, seg=6), M["pedra"])
    ob("marco_topo", lambda bm: _bola(bm, (0, 0, 0.6), 0.14, (1, 1, 0.6), sub=1), M["pedra"], suave=True)


def estaca(el, fase):
    M = _m()
    ob("estaca", lambda bm: diag(bm, Vector((0, 0, 0)), Vector((0.05, 0.02, 1.15)), 0.06), M["clara"])
    fita = materiais.material("obj_fita", "tecido_vermelho", k=0.0, oa=0.4, linhas=False)
    ob("estaca_fita", lambda bm: cx(bm, 0.0, 0.2, -0.02, 0.03, 0.95, 1.08), fita)


def placa(el, fase):
    M = _m()
    ob("placa_poste", lambda bm: cx(bm, -0.055, 0.055, -0.055, 0.055, 0.0, 2.15), M["escura"])

    def tabuas(bm):
        cx(bm, -0.1, 0.75, -0.085, -0.055, 1.72, 1.98)
        cx(bm, -0.7, 0.1, -0.085, -0.055, 1.38, 1.62)
    ob("placa_tabuas", tabuas, M["clara"])


def lapide(el, fase):
    M = _m()
    rng = random.Random(el.get("semente", 1))
    inc = rng.uniform(-0.06, 0.06)
    if el.get("cruz"):
        def cruz(bm):
            diag(bm, Vector((0, 0, 0)), Vector((inc, 0, 1.0)), 0.16)
            diag(bm, Vector((-0.26 + inc * 0.7, 0, 0.68)), Vector((0.26 + inc * 0.7, 0, 0.7)), 0.14)
        ob("lapide", cruz, M["pedra"])
    else:
        def laje(bm):
            res = bmesh.ops.create_cube(bm, size=1.0)
            bmesh.ops.subdivide_edges(bm, edges=bm.edges[:], cuts=3, use_grid_fill=True)
            for v in bm.verts:
                u, z = v.co.x, v.co.z + 0.5
                topo = 0.9 - 0.9 * (abs(u) * 2) ** 3 * 0.28      # alto arredondado
                v.co = Vector((u * 0.52 + inc * z, v.co.y * 0.14, z * topo))
        ob("lapide", laje, M["pedra"])
    ob("lapide_terra", lambda bm: _bola(bm, (0, -0.1, 0.0), 0.3, (1.1, 0.6, 0.25), sub=1, ruido=0.3, chao=0.0),
       M["terra"], suave=True)


# ---------------------------------------------------------------- carga e depósito

def barril(el, fase):
    M = _m()
    ob("barril", lambda bm: _cil(bm, 0, 0, 0.0, 1.0, 0.29, seg=14, bojo=0.16), M["tabuado"], suave=True)

    def aros(bm):
        for z in (0.16, 0.8):
            _cil(bm, 0, 0, z, z + 0.06, 0.315, seg=14)
    ob("barril_aros", aros, M["ferro"], suave=True)
    ob("barril_tampa", lambda bm: _cil(bm, 0, 0, 0.94, 0.97, 0.25, seg=14), M["escura"])


def caixote(el, fase):
    M = _m()
    ob("caixote", lambda bm: cx(bm, -0.42, 0.42, -0.42, 0.42, 0.0, 0.8), M["tabuado"])

    def ripas(bm):
        for sx in (-1, 1):
            for sy in (-1, 1):
                cx(bm, sx * 0.44 - 0.05, sx * 0.44 + 0.05, sy * 0.44 - 0.05, sy * 0.44 + 0.05, 0.0, 0.83)
        cx(bm, -0.45, 0.45, -0.46, -0.42, 0.74, 0.83)
        cx(bm, 0.42, 0.46, -0.45, 0.45, 0.74, 0.83)
        cx(bm, -0.45, 0.45, -0.46, -0.42, 0.0, 0.09)
        cx(bm, 0.42, 0.46, -0.45, 0.45, 0.0, 0.09)
    ob("caixote_ripas", ripas, M["escura"])


def sacos(el, fase):
    M = _m()
    for i, (c, s) in enumerate((((-0.2, 0.05, 0.22), (1.2, 0.9, 0.75)), ((0.25, -0.1, 0.2), (1.0, 1.1, 0.7)),
                                ((0.02, 0.0, 0.52), (1.1, 0.85, 0.6)))):
        ob("saco%d" % i, lambda bm, c=c, s=s, i=i: _bola(bm, c, 0.3, s, ruido=0.12, sem=i * 3.1, chao=0.0),
           M["linho"], suave=True)
    ob("saco_boca", lambda bm: _cil(bm, 0.32, 0.0, 0.6, 0.74, 0.05, 0.09, seg=6), M["linho"])


def feno(el, fase):
    M = _m()
    ob("feno", lambda bm: _bola(bm, (0, 0, 0.42), 0.76, (1, 1, 1.1), sub=3, ruido=0.16, sem=el.get("semente", 2),
                                chao=0.0), M["palha"], suave=True)
    ob("feno_vara", lambda bm: diag(bm, Vector((0.05, 0, 1.0)), Vector((0.1, 0.02, 1.5)), 0.05), M["escura"])


def carroca(el, fase):
    M = _m()
    ob("carroca_leito", lambda bm: cx(bm, -1.1, 0.55, -0.48, 0.48, 0.55, 0.64), M["tabuado"])

    def guardas(bm):
        cx(bm, -1.1, 0.55, -0.5, -0.45, 0.64, 0.98)
        cx(bm, -1.1, 0.55, 0.45, 0.5, 0.64, 0.98)
        cx(bm, -1.12, -1.07, -0.5, 0.5, 0.64, 0.98)
        cx(bm, 0.5, 0.55, -0.5, 0.5, 0.64, 0.86)
    ob("carroca_guardas", guardas, M["tabuado"])

    def rodas(bm):
        _roda(bm, (-0.35, -0.57, 0.46), 0.46)
        _roda(bm, (-0.35, 0.57, 0.46), 0.46)
        cx(bm, -0.39, -0.31, -0.57, 0.57, 0.42, 0.5)
    ob("carroca_rodas", rodas, M["escura"])

    def varais(bm):
        for s in (-1, 1):
            diag(bm, Vector((0.5, s * 0.36, 0.58)), Vector((1.18, s * 0.36, 0.06)), 0.06)
        for k in range(4):   # raios, só o desenho de fora
            a = k * math.pi / 4
            d = Vector((math.cos(a), 0, math.sin(a))) * 0.42
            diag(bm, Vector((-0.35, -0.615, 0.46)) - d, Vector((-0.35, -0.615, 0.46)) + d, 0.035)
    ob("carroca_varais", varais, M["clara"])
    ob("carroca_carga", lambda bm: _bola(bm, (-0.5, 0.0, 0.8), 0.32, (1.5, 1.0, 0.7), ruido=0.12, chao=0.64),
       M["linho"], suave=True)


# ---------------------------------------------------------------- praça e Posto

def poco(el, fase):
    M = _m()
    ob("poco_anel", lambda bm: _cil(bm, 0, 0, 0.0, 0.78, 0.88, 0.84, seg=16), M["pedra"])
    ob("poco_boca", lambda bm: _cil(bm, 0, 0, 0.78, 0.79, 0.62, seg=16), M["breu"])

    def armacao(bm):
        for s in (-1, 1):
            cx(bm, s * 0.8 - 0.07, s * 0.8 + 0.07, -0.07, 0.07, 0.0, 2.1)
        diag(bm, Vector((-0.95, 0, 1.55)), Vector((0.95, 0, 1.55)), 0.11)
        diag(bm, Vector((0.95, 0, 1.55)), Vector((1.0, -0.22, 1.4)), 0.04)   # manivela
    ob("poco_armacao", armacao, M["escura"])

    def telhado(bm):
        for s in (-1, 1):
            vs = [bm.verts.new((-1.05, 0, 2.5)), bm.verts.new((1.05, 0, 2.5)),
                  bm.verts.new((1.05, s * 0.6, 2.05)), bm.verts.new((-1.05, s * 0.6, 2.05))]
            bm.faces.new(vs if s < 0 else list(reversed(vs)))
        bmesh.ops.solidify(bm, geom=bm.faces[:], thickness=0.06)
    tel = materiais.material("obj_tabuinha", "madeira_clara", k=1.1, textura="tabuinha.png", escala=1.2, oa=1.0, linhas=False)
    ob("poco_telhado", telhado, tel)
    ob("poco_corda", lambda bm: cx(bm, -0.012, 0.012, -0.012, 0.012, 1.12, 1.55), M["clara"])
    ob("poco_balde", lambda bm: _cil(bm, 0, 0, 0.88, 1.14, 0.13, 0.16, seg=8), M["tabuado"], suave=True)


def lampiao(el, fase):
    M = _m()
    ob("lampiao_pe", lambda bm: _cil(bm, 0, 0, 0.0, 0.22, 0.17, 0.12, seg=6), M["pedra"])

    def poste(bm):
        cx(bm, -0.05, 0.05, -0.05, 0.05, 0.0, 2.42)
        diag(bm, Vector((0, 0, 2.36)), Vector((0.26, 0.26, 2.36)), 0.05)
        diag(bm, Vector((0, 0, 2.02)), Vector((0.16, 0.16, 2.34)), 0.04)
    ob("lampiao_poste", poste, M["escura"])
    c = Vector((0.2, 0.2, 0.0))
    ob("lampiao_vidro", lambda bm: cx(bm, c.x - 0.075, c.x + 0.075, c.y - 0.075, c.y + 0.075, 1.94, 2.16), M["vidro"])

    def gaiola(bm):
        cx(bm, c.x - 0.1, c.x + 0.1, c.y - 0.1, c.y + 0.1, 1.9, 1.94)
        _cil(bm, c.x, c.y, 2.16, 2.27, 0.13, 0.03, seg=4)
        cx(bm, c.x - 0.01, c.x + 0.01, c.y - 0.01, c.y + 0.01, 2.27, 2.36)
        for sx in (-1, 1):
            for sy in (-1, 1):
                cx(bm, c.x + sx * 0.08 - 0.012, c.x + sx * 0.08 + 0.012, c.y + sy * 0.08 - 0.012,
                   c.y + sy * 0.08 + 0.012, 1.94, 2.16)
    ob("lampiao_gaiola", gaiola, M["ferro"])
    return {"luz0": [0.2, -0.2, 2.05]}


def banca(el, fase):
    M = _m()
    pano = materiais.material("obj_toldo_" + el.get("pano", "tecido_vermelho"), el.get("pano", "tecido_vermelho"),
                              k=0.25, textura="linho.png", escala=1.0, oa=0.6, luz_min=0.1)

    def mesa(bm):
        cx(bm, -1.12, 1.12, -0.5, 0.42, 0.82, 0.9)
        for x in (-1.05, 1.05):
            cx(bm, x - 0.05, x + 0.05, -0.45, 0.4, 0.0, 0.82)
        cx(bm, -1.1, 1.1, -0.47, -0.43, 0.3, 0.82)
    ob("banca_mesa", mesa, M["tabuado"])

    def postes(bm):
        for x in (-1.12, 1.12):
            cx(bm, x - 0.045, x + 0.045, -0.52, -0.43, 0.0, 2.0)
            cx(bm, x - 0.045, x + 0.045, 0.43, 0.52, 0.0, 2.3)
    ob("banca_postes", postes, M["escura"])

    def toldo(bm):
        n = 8
        for i in range(n):
            x0, x1 = -1.2 + 2.4 * i / n, -1.2 + 2.4 * (i + 1) / n
            vs = [bm.verts.new((x0, -0.6, 1.98)), bm.verts.new((x1, -0.6, 1.98)),
                  bm.verts.new((x1, 0.55, 2.32)), bm.verts.new((x0, 0.55, 2.32))]
            bm.faces.new(vs)
            # barra recortada na frente
            vs = [bm.verts.new((x0, -0.6, 1.98)), bm.verts.new((x1, -0.6, 1.98)),
                  bm.verts.new(((x0 + x1) / 2, -0.61, 1.8))]
            bm.faces.new(vs)
        bmesh.ops.remove_doubles(bm, verts=bm.verts[:], dist=0.001)
        bmesh.ops.solidify(bm, geom=bm.faces[:], thickness=0.03)
    ob("banca_toldo", toldo, pano)
    # mercadoria: um caixote, dois sacos, uns potes
    ob("banca_caixa", lambda bm: cx(bm, -0.95, -0.45, -0.3, 0.2, 0.9, 1.15), M["clara"])
    ob("banca_saco", lambda bm: (_bola(bm, (0.0, -0.05, 1.03), 0.17, (1, 1, 0.9), ruido=0.1),
                                 _bola(bm, (0.35, 0.05, 1.03), 0.16, (1, 1, 0.9), ruido=0.1, sem=4)),
       M["linho"], suave=True)
    barro = materiais.material("obj_barro", "ferrugem", k=0.1, oa=0.6)
    ob("banca_potes", lambda bm: (_cil(bm, 0.75, -0.15, 0.9, 1.14, 0.09, 0.07, seg=8, bojo=0.3),
                                  _cil(bm, 0.95, 0.1, 0.9, 1.08, 0.08, 0.06, seg=8, bojo=0.3)), barro, suave=True)


def tenda(el, fase):
    M = _m()

    def pano(bm):
        n = 6
        for s in (-1, 1):
            for i in range(n):
                x0, x1 = -1.2 + 2.4 * i / n, -1.2 + 2.4 * (i + 1) / n
                for j in range(3):
                    f0, f1 = j / 3, (j + 1) / 3
                    def P(x, f):
                        cede = 0.07 * math.sin(f * math.pi) * (1 + 0.5 * math.sin(x * 3.0))
                        return (x, s * 0.95 * f, 2.05 * (1 - f) - cede)
                    vs = [bm.verts.new(P(x0, f0)), bm.verts.new(P(x1, f0)), bm.verts.new(P(x1, f1)),
                          bm.verts.new(P(x0, f1))]
                    bm.faces.new(vs if s < 0 else list(reversed(vs)))
        # fundo fechado (oeste) e a boca (leste) com as abas
        bm.faces.new([bm.verts.new((-1.2, -0.95, 0)), bm.verts.new((-1.2, 0, 2.05)), bm.verts.new((-1.2, 0.95, 0))])
        bm.faces.new([bm.verts.new((1.2, -0.95, 0)), bm.verts.new((1.2, -0.3, 0)), bm.verts.new((1.2, 0, 2.05))])
        bm.faces.new([bm.verts.new((1.2, 0.3, 0)), bm.verts.new((1.2, 0.95, 0)), bm.verts.new((1.2, 0, 2.05))])
        bmesh.ops.remove_doubles(bm, verts=bm.verts[:], dist=0.001)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    ob("tenda_pano", pano, M["linho"], suave=False)
    ob("tenda_boca", lambda bm: bm.faces.new([bm.verts.new((1.19, -0.3, 0)), bm.verts.new((1.19, 0.3, 0)),
                                             bm.verts.new((1.19, 0, 2.0))]), M["breu"])

    def paus(bm):
        for x in (-1.2, 1.2):
            cx(bm, x - 0.04, x + 0.04, -0.04, 0.04, 0.0, 2.2)
        for s in (-1, 1):   # espeques e cordas
            for x in (-1.0, 1.0):
                diag(bm, Vector((x, s * 0.95, 0.0)), Vector((x, s * 0.98, 0.16)), 0.04)
    ob("tenda_paus", paus, M["escura"])


def espantalho(el, fase):
    M = _m()

    def cruz(bm):
        diag(bm, Vector((0, 0, 0)), Vector((0.03, 0, 1.75)), 0.07)
        diag(bm, Vector((-0.62, 0, 1.36)), Vector((0.62, 0, 1.42)), 0.055)
    ob("esp_cruz", cruz, M["escura"])
    trapo = materiais.material("obj_trapo", "tecido_marrom", k=0.3, textura="linho.png", escala=1.5, oa=0.7)

    def camisa(bm):
        res = bmesh.ops.create_cube(bm, size=1.0)
        bmesh.ops.subdivide_edges(bm, edges=bm.edges[:], cuts=2, use_grid_fill=True)
        for v in bm.verts:
            z = v.co.z + 0.5
            larg = 0.5 - 0.12 * z + 0.05 * math.sin(v.co.x * 14)
            v.co = Vector((v.co.x * larg + 0.02, v.co.y * 0.2, 0.78 + z * 0.68 + (0.04 * math.sin(v.co.x * 20) if z < 0.1 else 0)))
        for s in (-1, 1):
            diag(bm, Vector((s * 0.2, 0, 1.39)), Vector((s * 0.52, 0, 1.39 + s * 0.02)), 0.13)
    ob("esp_camisa", camisa, trapo)
    ob("esp_cabeca", lambda bm: _bola(bm, (0.03, -0.01, 1.66), 0.15, (1, 1, 1.1), ruido=0.08), M["linho"], suave=True)

    def chapeu(bm):
        _cil(bm, 0.03, -0.01, 1.76, 1.78, 0.3, seg=10)
        _cil(bm, 0.03, -0.01, 1.78, 1.98, 0.15, 0.06, seg=8)
        for s in (-1, 1):   # palha saindo das mangas
            _cil(bm, s * 0.6, 0, 1.3, 1.46, 0.07, 0.02, seg=5)
    ob("esp_chapeu", chapeu, M["palha"])


def banco(el, fase):
    M = _m()

    def pecas(bm):
        cx(bm, -0.92, 0.92, -0.18, 0.18, 0.42, 0.49)
        for x in (-0.7, 0.7):
            cx(bm, x - 0.04, x + 0.04, -0.16, 0.16, 0.0, 0.42)
        cx(bm, -0.7, 0.7, -0.03, 0.03, 0.18, 0.25)
    ob("banco", pecas, M["madeira"])


# ---------------------------------------------------------------- forja

def bigorna(el, fase):
    M = _m()
    ob("bigorna_cepo", lambda bm: _cil(bm, 0, 0, 0.0, 0.46, 0.23, 0.21, seg=10), M["madeira"], suave=True)
    ob("bigorna_corte", lambda bm: _cil(bm, 0, 0, 0.46, 0.465, 0.2, seg=10), M["clara"])

    def ferro(bm):
        cx(bm, -0.13, 0.13, -0.08, 0.08, 0.46, 0.52)
        cx(bm, -0.08, 0.08, -0.05, 0.05, 0.52, 0.62)
        cx(bm, -0.2, 0.16, -0.08, 0.08, 0.62, 0.74)
        res = bmesh.ops.create_cone(bm, cap_ends=True, segments=6, radius1=0.07, radius2=0.01, depth=0.2)
        for v in res["verts"]:
            v.co = Vector((0.16 + v.co.z + 0.1, v.co.y, 0.68 + v.co.x * 0.7))
    ob("bigorna_ferro", ferro, M["ferro"])


def forja(el, fase):
    """Fornalha aberta: mesa de pedra com a brasa, chaminé atrás, fole, cocho d'água e as tenazes."""
    M = _m()

    def pedra(bm):
        cx(bm, -1.0, 0.8, -0.35, 0.95, 0.0, 0.85)       # mesa
        cx(bm, -1.0, 0.8, -0.35, -0.2, 0.85, 1.0)       # borda da frente
        cx(bm, -1.0, -0.85, -0.35, 0.95, 0.85, 1.0)
        cx(bm, 0.65, 0.8, -0.35, 0.95, 0.85, 1.0)
        cx(bm, -0.7, 0.5, 0.6, 1.2, 0.85, 2.3)          # chaminé
        cx(bm, -0.5, 0.3, 0.7, 1.15, 2.3, 3.0)
        cx(bm, -0.56, 0.36, 0.64, 1.21, 2.9, 3.0)
    ob("forja_pedra", pedra, M["pedra"])
    ob("forja_boca", lambda bm: cx(bm, -0.5, 0.3, 0.58, 0.61, 0.95, 1.7), M["breu"])
    carvao = materiais.material("obj_carvao", "quase_preto", k=0.4, ruido=14.0, oa=0.5, linhas=False)
    ob("forja_carvao", lambda bm: cx(bm, -0.85, 0.65, -0.2, 0.6, 0.85, 0.93), carvao)

    def brasa(bm):
        rng = random.Random(5)
        for k in range(7):
            x, y = rng.uniform(-0.55, 0.35), rng.uniform(-0.05, 0.45)
            a = 0.5 + 0.5 * math.sin(2 * math.pi * (fase + k / 7.0))
            _cil(bm, x, y, 0.92, 0.98 + 0.22 * a * (1.0 if k % 2 else 0.5), 0.11, 0.015, seg=5)
        cx(bm, -0.45, 0.25, 0.0, 0.4, 0.925, 0.94)
    ob("forja_brasa", brasa, M["fogo"])

    def fole(bm):
        res = bmesh.ops.create_cube(bm, size=1.0)
        for v in res["verts"]:
            u = v.co.x + 0.5                               # 0 no bico, 1 no cabo
            v.co = Vector((0.85 + u * 0.6, 0.25 + v.co.y * (0.1 + 0.4 * u), 0.95 + v.co.z * (0.06 + 0.24 * u)))
    ob("forja_fole", fole, M["couro"])
    ob("forja_fole_pe", lambda bm: (cx(bm, 1.0, 1.08, 0.2, 0.3, 0.0, 0.85), cx(bm, 1.35, 1.43, 0.2, 0.3, 0.0, 0.85)),
       M["escura"])
    ob("forja_cocho", lambda bm: cx(bm, -0.3, 0.6, -1.15, -0.65, 0.0, 0.5), M["tabuado"])
    ob("forja_agua", lambda bm: cx(bm, -0.25, 0.55, -1.1, -0.7, 0.44, 0.46), M["agua"])

    def tenazes(bm):
        diag(bm, Vector((-1.3, -0.5, 0.0)), Vector((-1.08, -0.3, 1.1)), 0.035)
        diag(bm, Vector((-1.2, -0.6, 0.0)), Vector((-1.06, -0.36, 1.2)), 0.035)
        cx(bm, -1.4, -1.1, -1.1, -0.9, 0.0, 0.5)         # malho encostado
        diag(bm, Vector((-1.25, -1.0, 0.5)), Vector((-1.12, -0.5, 1.05)), 0.04)
    ob("forja_ferros", tenazes, M["ferro"])
    return {"luz0": [-0.1, -0.2, 1.1], "fumaca0": [-0.1, -0.92, 3.1]}


# ---------------------------------------------------------------- terra e ruína

def buraco(el, fase):
    M = _m()
    rng = random.Random(el.get("semente", 3))

    def boca(bm):
        vs = []
        for k in range(14):
            a = 2 * math.pi * k / 14
            r = 0.56 + rng.uniform(-0.07, 0.07)
            vs.append(bm.verts.new((math.cos(a) * r, math.sin(a) * r, 0.02)))
        bm.faces.new(vs)
    ob("buraco_boca", boca, M["breu"])
    for k in range(11):
        a = 2 * math.pi * k / 11 + rng.uniform(-0.15, 0.15)
        r = 0.66 + rng.uniform(-0.03, 0.08)
        t = rng.uniform(0.13, 0.2)
        ob("buraco_torrao%d" % k, lambda bm, a=a, r=r, t=t, k=k: _bola(
            bm, (math.cos(a) * r, math.sin(a) * r, 0.02), t, (1.2, 1.2, 0.7), sub=1, ruido=0.35, sem=k, chao=0.0),
           M["terra"], suave=True)


def terra_remexida(el, fase):
    M = _m()
    rng = random.Random(el.get("semente", 4))
    ob("remexida_monte", lambda bm: _bola(bm, (0, 0, 0.0), 0.62, (1.0, 1.0, 0.3), sub=2, ruido=0.25, chao=0.0),
       M["terra"], suave=True)
    for k in range(9):
        a, r = rng.uniform(0, 2 * math.pi), 0.6 * math.sqrt(rng.random())
        ob("remexida_torrao%d" % k, lambda bm, a=a, r=r, k=k: _bola(
            bm, (math.cos(a) * r, math.sin(a) * r, 0.1), rng.uniform(0.07, 0.13), (1, 1, 0.7), sub=1, ruido=0.4,
            sem=k, chao=0.0), M["terra"], suave=True)


def entulho(el, fase):
    M = _m()
    decoracao.pedregulhos("entulho", el.get("semente", 71), n=4, tamanho=(0.18, 0.26))
    ob("entulho_viga", lambda bm: diag(bm, Vector((-0.4, -0.2, 0.05)), Vector((0.35, 0.1, 0.4)), 0.12), M["escura"])


def laje(el, fase):
    """Laje de pé, inclinada para trás, com sulcos na face da frente. Os sulcos não são letras nem
    figuras que se leiam: um anel, riscos e pontos (nada aqui se explica)."""
    M = _m()
    inc = 0.22

    def bloco(bm):
        res = bmesh.ops.create_cube(bm, size=1.0)
        bmesh.ops.subdivide_edges(bm, edges=bm.edges[:], cuts=4, use_grid_fill=True)
        for v in bm.verts:
            z = v.co.z + 0.5
            quebra = 0.12 * noise.noise(Vector((v.co.x * 3.0, 0.3, 7.0))) if z > 0.95 else 0.0
            v.co = Vector((v.co.x * 2.0, v.co.y * 0.42 + 0.15 + inc * z, z * (1.0 + quebra)))
    ob("laje", bloco, M["pedra"])

    def F(u, z):
        return Vector((u, 0.15 - 0.21 + inc * z - 0.012, z))

    def sulcos(bm):
        for k in range(12):   # anel
            a0, a1 = 2 * math.pi * k / 12, 2 * math.pi * (k + 1) / 12
            diag(bm, F(-0.45 + 0.2 * math.cos(a0), 0.55 + 0.2 * math.sin(a0)),
                 F(-0.45 + 0.2 * math.cos(a1), 0.55 + 0.2 * math.sin(a1)), 0.03)
        for k in range(4):    # riscos
            diag(bm, F(0.1 + k * 0.17, 0.3), F(0.1 + k * 0.17, 0.78 - 0.08 * (k % 2)), 0.03)
        diag(bm, F(-0.8, 0.2), F(0.8, 0.2), 0.03)
        for k in range(3):    # pontos
            diag(bm, F(-0.5 + k * 0.05, 0.54), F(-0.47 + k * 0.05, 0.56), 0.035)
    sulco = materiais.material("obj_sulco", "quase_preto", k=0.0, oa=0.0, linhas=False, contorno=False)
    ob("laje_sulcos", sulcos, sulco)
    ob("laje_base", lambda bm: _bola(bm, (0, 0.1, 0.0), 0.5, (2.1, 0.8, 0.25), sub=2, ruido=0.3, chao=0.0),
       M["terra"], suave=True)


FORMAS = {f.__name__: f for f in (cerca, marco, estaca, placa, lapide, barril, caixote, sacos, feno, carroca, poco,
                                  lampiao, banca, tenda, espantalho, banco, bigorna, forja, buraco, terra_remexida,
                                  entulho, laje)}
