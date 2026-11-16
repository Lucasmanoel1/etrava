"""Casca dos cômodos (V6, SPEC-visual §7.4): paredes de fundo inteiras, paredes da frente cortadas
a ~0,4 m com acabamento, cantos e pontas — um conjunto por casa, no material do exterior dela (V5).

Cada peça é um módulo de 1 m (ou 0,25 m, nos cantos) centrado na origem, do tamanho da pegada do
`content/props.yaml` (parede: 1,0 × 0,25 m). Referencial local de cada peça:
  u   ao longo da parede, de -0,5 a 0,5 (eixo x: para leste; eixo y: para o sul)
  n   através dela, de -0,125 a 0,125; n > 0 é sempre o lado que a câmera vê
      (o lado de dentro nas paredes de fundo; o lado de fora nas da frente)
  z   para cima
No jogo: eixo x → (x, y) = (u, n); eixo y → (x, y) = (n, u). No Blender Y = -y.

Estilos (`estilo` na ficha):
  enxaimel  reboco entre esteios, frechal no alto, baldrame embaixo e travessa no peitoril
            (`lambril`: forro de tábua até essa altura, como na taverna)
  pedra     fiadas de pedra com argamassa recuada, padrão periódico de 1 m (as peças emendam)
A parede da frente é sempre o embasamento de pedra que se vê de fora, cortado a `corte` m, com
viga por cima (enxaimel) ou pedra de topo (pedra).
"""
import math
import random

import bmesh
from mathutils import Vector

from construcoes.casa import _bm_caixa, _diagonal
from nucleo import geo, materiais

E = 0.125          # meia espessura da parede
FACE = 0.09        # plano do reboco (os madeiramentos ficam à frente dele, em E)


# ---------------------------------------------------------------- referencial

def _P(eixo):
    """Ponto local (u, n, z) → Vector do Blender."""
    if eixo == "x":
        return lambda u, n, z: Vector((u, -n, z))
    return lambda u, n, z: Vector((n, -u, z))


def _caixa(bm, eixo, u0, u1, n0, n1, z0, z1):
    if u1 - u0 < 1e-4 or n1 - n0 < 1e-4 or z1 - z0 < 1e-4:
        return
    if eixo == "x":
        _bm_caixa(bm, u0, u1, -n1, -n0, z0, z1)
    else:
        _bm_caixa(bm, n0, n1, -u1, -u0, z0, z1)


def _menos(ret, vao):
    """Retângulo (u0, u1, z0, z1) menos o vão: até quatro pedaços."""
    u0, u1, z0, z1 = ret
    a0, a1, b0, b1 = vao
    if a1 <= u0 or a0 >= u1 or b1 <= z0 or b0 >= z1:
        return [ret]
    out = []
    if a0 > u0:
        out.append((u0, a0, z0, z1))
    if a1 < u1:
        out.append((a1, u1, z0, z1))
    ma, mb = max(a0, u0), min(a1, u1)
    if b0 > z0:
        out.append((ma, mb, z0, b0))
    if b1 < z1:
        out.append((ma, mb, b1, z1))
    return out


def _recortar(rets, vaos):
    for v in vaos:
        rets = [r for ret in rets for r in _menos(ret, v)]
    return rets


# ---------------------------------------------------------------- materiais

def _materiais(p):
    M = materiais.material
    m = {
        "reboco": M("reboco", p.get("reboco", "reboco"), k=p.get("k_reboco", 0.45), textura="reboco.png", escala=0.45,
                    oa=0.8, pontilhar=True, linhas=False, tons=p.get("tons_reboco")),
        "viga": M("viga", p.get("madeira", "madeira_escura"), k=0.4, textura="casca.png", escala=1.0, oa=0.7),
        "tabua": M("tabua", "madeira", k=0.5, ruido=9.0, ruido_eixos=(2.0, 2.0, 16.0), oa=0.8),
        "tabua_b": M("tabua_b", "madeira_clara", k=0.5, ruido=9.0, ruido_eixos=(2.0, 2.0, 16.0), oa=0.8, tons=[0, 3]),
        "pedra": M("pedra", "pedra", k=0.7, textura="pedra.png", escala=0.9, oa=0.9, tons=[1, 5]),
        "pedra_b": M("pedra_b", "pedra", k=0.7, textura="pedra.png", escala=1.3, oa=0.9, tons=[0, 4]),
        "pedra_c": M("pedra_c", "pedra_clara", k=0.6, textura="pedra.png", escala=1.1, oa=0.9, tons=[0, 4]),
        "argamassa": M("argamassa", "pedra", k=0.2, oa=1.0, linhas=False, tons=[0, 2]),
        "vidro": M("vidro", "vidro", k=0.0, oa=0.4, linhas=False, tons=[2, 3], luz_min=0.3),
        "ferro": M("ferro", "ferro", k=0.1, oa=0.6),
        "barro": M("barro", "ferrugem", k=0.2, oa=0.6, tons=[0, 3]),
        "linho": M("linho", "linho", k=0.3, textura="linho.png", escala=1.5, oa=0.6, tons=[1, 4]),
        "couro": M("couro", "couro", k=0.3, textura="couro.png", escala=1.5, oa=0.7),
    }
    return m


# ---------------------------------------------------------------- fiadas de pedra

def _fiadas(semente, z0, z1, alt=(0.2, 0.32), comp=(0.28, 0.55), juntas_na_borda=False):
    """Fiadas de pedra num módulo de 1 m: [(za, zb, [(ua, ub), …])]. Sem `juntas_na_borda`, o
    padrão é periódico em u (a pedra que passa da borda continua no módulo vizinho)."""
    rng = random.Random(semente)
    out = []
    z = z0
    while z < z1 - 0.05:
        h = rng.uniform(*alt)
        if z1 - (z + h) < alt[0] * 0.6:
            h = z1 - z
        zb = min(z1, z + h)
        juntas = []
        u = rng.uniform(0.0, 1.0) if not juntas_na_borda else 0.0
        inicio = u
        while u < inicio + 1.0 - comp[0] * 0.7:
            juntas.append(u)
            u += rng.uniform(*comp)
        pedras = []
        for i, a in enumerate(juntas):
            b = juntas[i + 1] if i + 1 < len(juntas) else inicio + 1.0
            pedras.append((a, b))
        out.append((z, zb, pedras))
        z = zb
    return out


def _pedras(bm_por_mat, eixo, fiadas, rng, u_lim=(-0.5, 0.5), vaos=(), n0=-E, n1=E, saliencia=0.018,
            junta=0.024, mats=("pedra", "pedra_b", "pedra_c"), pesos=(0.55, 0.3, 0.15)):
    """Assenta as pedras das fiadas (u em [0, 1) deslocado para [-0,5, 0,5), com volta)."""
    for za, zb, pedras in fiadas:
        for a, b in pedras:
            mat = rng.choices(mats, pesos)[0]
            sai = rng.uniform(0.0, saliencia)
            dz = rng.uniform(0.0, 0.006)
            trechos = []
            for k in (-1, 0, 1):          # volta: o trecho que cai no módulo vizinho volta deste lado
                ua, ub = a - 0.5 + k, b - 0.5 + k
                ua, ub = max(ua, u_lim[0]), min(ub, u_lim[1])
                if ub - ua > 1e-3:
                    # junta só onde a pedra acaba de verdade (não na borda do módulo)
                    ja = junta / 2 if abs(ua - (a - 0.5 + k)) < 1e-6 else 0.0
                    jb = junta / 2 if abs(ub - (b - 0.5 + k)) < 1e-6 else 0.0
                    trechos.append((ua + ja, ub - jb))
            for ua, ub in trechos:
                for r in _recortar([(ua, ub, za + junta / 2 + dz, zb - junta / 2)], vaos):
                    _caixa(bm_por_mat[mat], eixo, r[0], r[1], n0, n1 + sai, r[2], r[3])


class _Malhas:
    """Uma bmesh por material; vira um objeto por material no fim."""

    def __init__(self, M, prefixo):
        self.M, self.prefixo, self.bms = M, prefixo, {}

    def __getitem__(self, mat):
        if mat not in self.bms:
            self.bms[mat] = bmesh.new()
        return self.bms[mat]

    def fechar(self):
        for mat, bm in self.bms.items():
            if len(bm.verts) == 0:
                bm.free()
                continue
            geo.objeto("%s_%s" % (self.prefixo, mat), bm, self.M[mat])


# ---------------------------------------------------------------- vãos e peças de parede

def _vaos(tipo, p):
    """Vãos da peça: (u0, u1, z0, z1)."""
    if tipo == "janela":
        j = p.get("janela", {})
        hl = j.get("largura", 0.58) / 2
        return [(-hl, hl, j.get("peitoril", 1.0), j.get("peitoril", 1.0) + j.get("altura", 0.8))]
    if tipo == "porta":
        hl = p.get("porta_largura", 0.82) / 2
        return [(-hl, hl, 0.0, p.get("porta_altura", 1.95))]
    return []


def _abertura(mm, eixo, tipo, p, rng):
    """Moldura, folha da porta ou vidraça, e soleira."""
    P = _P(eixo)
    if tipo == "janela":
        u0, u1, z0, z1 = _vaos(tipo, p)[0]
        f = 0.07
        _caixa(mm["viga"], eixo, u0 - f, u0, -E, E + 0.01, z0 - 0.02, z1 + f)
        _caixa(mm["viga"], eixo, u1, u1 + f, -E, E + 0.01, z0 - 0.02, z1 + f)
        _caixa(mm["viga"], eixo, u0 - f, u1 + f, -E, E + 0.015, z1, z1 + f * 1.4)
        # peitoril de tábua que avança sobre o cômodo
        _caixa(mm["viga"], eixo, u0 - f * 1.4, u1 + f * 1.4, -E, E + 0.07, z0 - 0.06, z0)
        _caixa(mm["vidro"], eixo, u0, u1, -0.04, -0.02, z0, z1)
        uc, zc = (u0 + u1) / 2, (z0 + z1) / 2
        _caixa(mm["viga"], eixo, uc - 0.022, uc + 0.022, -0.04, 0.0, z0, z1)
        _caixa(mm["viga"], eixo, u0, u1, -0.04, 0.0, zc - 0.022, zc + 0.022)
        # derrame do vão (o lado de dentro da parede, até o vidro)
        if p.get("estilo") != "pedra":
            for ua, ub in ((u0, u0 + 0.012), (u1 - 0.012, u1)):
                _caixa(mm["reboco"], eixo, ua, ub, -0.02, FACE, z0, z1)
    elif tipo == "porta":
        u0, u1, z0, z1 = _vaos(tipo, p)[0]
        f = 0.08
        _caixa(mm["viga"], eixo, u0 - f, u0, -E, E + 0.012, 0.0, z1 + f)
        _caixa(mm["viga"], eixo, u1, u1 + f, -E, E + 0.012, 0.0, z1 + f)
        _caixa(mm["viga"], eixo, u0 - f * 1.4, u1 + f * 1.4, -E, E + 0.02, z1, z1 + f * 1.6)
        # folha de tábuas em pé, recuada no vão
        n_t = 4
        for i in range(n_t):
            a = u0 + (u1 - u0) * i / n_t
            b = u0 + (u1 - u0) * (i + 1) / n_t
            mat = "tabua" if i % 2 == 0 else "tabua_b"
            _caixa(mm[mat], eixo, a + 0.006, b - 0.006, -0.05, -0.01 + rng.uniform(0, 0.006), 0.0, z1)
        _caixa(mm["tabua"], eixo, u0, u1, -0.06, -0.04, 0.0, z1)          # fundo das frestas
        for z in (0.32, z1 - 0.42):                                          # travessas e ferragem
            _caixa(mm["viga"], eixo, u0 + 0.04, u1 - 0.04, -0.01, 0.015, z, z + 0.1)
            _caixa(mm["ferro"], eixo, u0 + 0.02, u0 + 0.5 * (u1 - u0), 0.015, 0.03, z + 0.025, z + 0.075)
        _caixa(mm["ferro"], eixo, u1 - 0.14, u1 - 0.08, 0.0, 0.05, 0.95, 1.05)  # tranca
        # soleira de pedra no chão do cômodo, diante da porta
        _caixa(mm["pedra_c"], eixo, u0 - 0.06, u1 + 0.06, -E, E + 0.16, 0.0, 0.045)
    return P


def _prateleira(mm, eixo, p, rng):
    """Duas tábuas em mãos-francesas, com potes, tigelas, um maço e uma caixinha."""
    P = _P(eixo)
    for z in p.get("prateleiras", [1.15, 1.6]):
        _caixa(mm["tabua"], eixo, -0.42, 0.42, FACE, E + 0.27, z - 0.035, z)
        for u in (-0.32, 0.32):
            _caixa(mm["viga"], eixo, u - 0.025, u + 0.025, FACE, E + 0.04, z - 0.2, z - 0.035)
            _diagonal(mm["viga"], P(u, E + 0.02, z - 0.19), P(u, E + 0.22, z - 0.045), 0.035)
        u = -0.36
        while u < 0.33:
            tipo = rng.choice(["pote", "pote", "tigela", "maco", "caixa", "vazio"])
            if tipo == "pote":
                r = rng.uniform(0.045, 0.07)
                h = rng.uniform(0.1, 0.2)
                _cil(mm["barro" if rng.random() < 0.7 else "pedra_c"], eixo, u + r, E + 0.13, z, z + h, r, r * 0.75)
                u += 2 * r + 0.03
            elif tipo == "tigela":
                _cil(mm["tabua_b"], eixo, u + 0.08, E + 0.14, z, z + 0.05, 0.05, 0.08)
                u += 0.19
            elif tipo == "maco":
                _caixa(mm["linho"], eixo, u, u + 0.14, E + 0.05, E + 0.21, z, z + 0.08)
                u += 0.17
            elif tipo == "caixa":
                _caixa(mm["couro" if rng.random() < 0.5 else "tabua_b"], eixo, u, u + 0.13, E + 0.04, E + 0.2, z, z + 0.1)
                u += 0.16
            else:
                u += 0.1


def _cil(bm, eixo, u, n, z0, z1, r0, r1=None, seg=8):
    P = _P(eixo)
    res = bmesh.ops.create_cone(bm, cap_ends=True, segments=seg, radius1=r0, radius2=r0 if r1 is None else r1,
                                depth=z1 - z0)
    c = P(u, n, 0)
    for v in res["verts"]:
        v.co = Vector((c.x + v.co.x, c.y + v.co.y, v.co.z + (z1 - z0) / 2 + z0))


# ---------------------------------------------------------------- paredes de fundo

def _fundo_enxaimel(mm, eixo, tipo, var, p, rng):
    H = p["altura"]
    vaos = _vaos(tipo, p)
    lambril = p.get("lambril")
    trav = p.get("travessa", 0.9)
    # reboco entre os madeiramentos (com os vãos recortados)
    for r in _recortar([(-0.5, 0.5, 0.0, H)], vaos):
        _caixa(mm["reboco"], eixo, r[0], r[1], -E, FACE, r[2], r[3])
    # baldrame (rodapé de viga), frechal no alto (cobre o topo da parede inteira) e esteio a oeste/norte
    for r in _recortar([(-0.5, 0.5, 0.0, 0.14)], vaos):
        _caixa(mm["viga"], eixo, r[0], r[1], -E, E, r[2], r[3])
    _caixa(mm["viga"], eixo, -0.5, 0.5, -E, E + 0.01, H - 0.17, H)
    _caixa(mm["viga"], eixo, -0.5, -0.37, -E, E, 0.0, H - 0.17)
    if lambril:   # forro de tábuas em pé até `lambril`, com a régua por cima
        for r in _recortar([(-0.37, 0.5, 0.14, lambril)], vaos):
            n_t = max(1, int(round((r[1] - r[0]) / 0.17)))
            for i in range(n_t):
                a = r[0] + (r[1] - r[0]) * i / n_t
                b = r[0] + (r[1] - r[0]) * (i + 1) / n_t
                _caixa(mm["tabua" if rng.random() < 0.6 else "tabua_b"], eixo, a + 0.008, b - 0.008, FACE,
                       FACE + 0.022 + rng.uniform(0, 0.006), r[2], r[3])
        for r in _recortar([(-0.37, 0.5, lambril, lambril + 0.09)], vaos):
            _caixa(mm["viga"], eixo, r[0], r[1], -E, E, r[2], r[3])
    else:         # travessa no peitoril (corre por baixo da janela)
        for r in _recortar([(-0.37, 0.5, trav - 0.1, trav)], vaos):
            _caixa(mm["viga"], eixo, r[0], r[1], -E, E, r[2], r[3])
    base = (lambril + 0.09) if lambril else trav
    # variantes: b = mão-francesa no painel de cima; c = outra mão-francesa
    if var == "b" and not vaos:
        P = _P(eixo)
        _diagonal(mm["viga"], P(-0.37, E - 0.03, base), P(0.47, E - 0.03, H - 0.17), 0.1)
    if var == "c" and not vaos:   # mão-francesa ao contrário, no painel de baixo (a de reboco caído lia como símbolo)
        P = _P(eixo)
        if lambril:
            _diagonal(mm["viga"], P(-0.37, E - 0.03, H - 0.17), P(0.47, E - 0.03, base), 0.1)
        else:
            _diagonal(mm["viga"], P(-0.37, E - 0.03, base - 0.1), P(0.47, E - 0.03, 0.14), 0.09)
    return vaos


def _fundo_pedra(mm, eixo, tipo, var, p, rng):
    H = p["altura"]
    vaos = _vaos(tipo, p)
    # argamassa recuada atrás das pedras
    for r in _recortar([(-0.5, 0.5, 0.0, H)], vaos):
        _caixa(mm["argamassa"], eixo, r[0], r[1], -E, FACE + 0.01, r[2], r[3])
    fi = _fiadas(p.get("semente", 3), 0.0, H - 0.2, alt=(0.2, 0.3), comp=(0.26, 0.5))
    _pedras(mm, eixo, fi, random.Random(p.get("semente", 3) * 31 + (0 if var == "a" else 7)), vaos=vaos,
            n1=E - 0.012)
    # viga de apoio do soalho de cima, no alto (cobre o topo)
    _caixa(mm["viga"], eixo, -0.5, 0.5, -E, E + 0.02, H - 0.2, H)
    # vergas de madeira sobre os vãos
    for u0, u1, z0, z1 in vaos:
        _caixa(mm["viga"], eixo, u0 - 0.14, u1 + 0.14, -E, E + 0.01, z1, min(H - 0.2, z1 + 0.14))
    return vaos


def _peca_fundo(mm, eixo, tipo, var, p, rng):
    if p.get("estilo") == "pedra":
        _fundo_pedra(mm, eixo, tipo, var, p, rng)
    else:
        _fundo_enxaimel(mm, eixo, tipo, var, p, rng)
    if tipo in ("janela", "porta"):
        _abertura(mm, eixo, tipo, p, rng)
    if tipo == "prateleira":
        _prateleira(mm, eixo, p, rng)


# ---------------------------------------------------------------- cortes (frente), cantos e pontas

def _topo(mm, eixo, p, u0, u1, n0=-E, n1=E):
    """Acabamento do corte: viga (enxaimel) ou pedra de topo (pedra)."""
    c = p.get("corte", 0.4)
    if p.get("estilo") == "pedra":
        rng = random.Random(p.get("semente", 3) + int((u0 + 2) * 100))
        u = u0
        while u < u1 - 1e-3:
            b = min(u1, u + rng.uniform(0.32, 0.5))
            if u1 - b < 0.15:
                b = u1
            _caixa(mm["pedra_c"], eixo, u + (0.012 if u > u0 else 0), b - (0.012 if b < u1 else 0), n0 - 0.02,
                   n1 + 0.025, c - 0.1 + rng.uniform(0, 0.008), c)
            u = b
    else:
        _caixa(mm["viga"], eixo, u0, u1, n0 - 0.01, n1 + 0.02, c - 0.1, c)


def _frente(mm, eixo, var, p, rng):
    """Parede da frente cortada: o embasamento de pedra visto de fora, com o acabamento por cima."""
    c = p.get("corte", 0.4)
    _caixa(mm["argamassa"], eixo, -0.5, 0.5, -E, E - 0.02, 0.0, c - 0.1)
    fi = _fiadas(p.get("semente", 3) * 3 + (0 if var == "a" else 11), 0.0, c - 0.1, alt=(0.13, 0.17),
                 comp=(0.22, 0.42), juntas_na_borda=True)
    _pedras(mm, eixo, fi, rng, n1=E - 0.005, saliencia=0.012)
    _topo(mm, eixo, p, -0.5, 0.5)


def _coluna(mm, p, alta, rng):
    """Canto ou ponta (0,25 × 0,25 m): esteio de canto (enxaimel) ou cunhal de pedra."""
    H = p["altura"] if alta else p.get("corte", 0.4)
    if p.get("estilo") == "pedra":
        z, i = 0.0, 0
        topo = H if alta else H - 0.1
        while z < topo - 0.02:
            h = min(topo - z, rng.uniform(0.22, 0.3))
            _caixa(mm["pedra_b" if i % 2 else "pedra"], "x", -E - 0.01 * (i % 2), E + 0.012, -E, E + 0.012 * (1 - i % 2),
                   z + 0.012, z + h - 0.012)
            z += h
            i += 1
        _caixa(mm["argamassa"], "x", -E + 0.01, E - 0.01, -E + 0.01, E - 0.01, 0.0, topo)
        if alta:
            _caixa(mm["viga"], "x", -E, E + 0.02, -E, E + 0.02, H - 0.2, H)
        else:
            _topo(mm, "x", p, -E, E)
    else:
        base = 0.3 if not alta else 0.0
        if not alta:   # canto baixo da frente: pedra e a viga do corte
            _caixa(mm["pedra"], "x", -E, E, -E, E, 0.0, H - 0.1)
            _topo(mm, "x", p, -E, E)
        else:
            _caixa(mm["viga"], "x", -E, E, -E, E, base, H)
            _caixa(mm["viga"], "x", -E - 0.01, E + 0.01, -E - 0.01, E + 0.01, H - 0.17, H)


def construir(peca, p):
    """Monta uma peça: {"tipo": lisa|janela|porta|prateleira|frente|canto|ponta|canto_frente,
    "eixo": x|y, "var": a|b|c}."""
    tipo, eixo, var = peca["tipo"], peca.get("eixo", "x"), peca.get("var", "a")
    rng = random.Random("%s/%s/%s/%s" % (p.get("semente", 3), tipo, eixo, var))
    M = _materiais(p)
    mm = _Malhas(M, "p")
    if tipo in ("lisa", "janela", "porta", "prateleira"):
        _peca_fundo(mm, eixo, tipo, var, p, rng)
    elif tipo == "frente":
        _frente(mm, eixo, var, p, rng)
    elif tipo == "canto":
        _coluna(mm, p, True, rng)
    elif tipo == "ponta":
        _coluna(mm, p, True, rng)
    elif tipo == "canto_frente":
        _coluna(mm, p, False, rng)
    else:
        raise ValueError("peça desconhecida: " + tipo)
    mm.fechar()
