"""Peças de roupa, cabelo, barba e ferramentas do V2 (SPEC-visual §6.1), sobre o corpo de corpo.py.

Cada peça é chamada por nome a partir da ficha (`roupa: [{peca: ...}]`, `ferramenta: {mao_d: ...}`).
Peças do tronco e das pernas que precisam dobrar com o corpo entram num grupo da pele contínua
(kit_humano/pele.py); chapéus, capuzes, cabelo e ferramentas ficam rígidos, presos a uma junta.
Regra de escala: a 48 px, 1 px de arte ≈ 3,6 cm; o que for menor que ~2,5 cm some no voto, então
cabos, faixas e alças têm espessura mínima de 2–3 cm.
"""
import math

import bmesh
import bpy
from mathutils import Matrix, Vector

from kit_humano.corpo import _bola, _caixa, _pai, _secao, _tubo, ponto
from nucleo import geo, materiais


# ------------------------------------------------------------------ utilidades

def _apagar(ob, fora):
    """Apaga os vértices em que fora(co) é verdadeiro (abre capuz, colete, barba por fazer)."""
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if fora(v.co)], context="VERTS")
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def _espessura(ob, e):
    so = ob.modifiers.new("esp", "SOLIDIFY")
    so.thickness = e
    so.offset = 0.0
    return ob


def _cone_aberto(nome, z_topo, z_base, r_topo, r_base, material, junta_, prof=0.8, segs=16,
                 fenda=0.0, arco=None, dx=0.0, esp=0.025):
    """Superfície em cone (saia, aba de casaco, capa): de z_topo a z_base no espaço da junta.

    fenda: fração da volta aberta na frente (casaco); arco=(a0, a1) em graus, só esse trecho
    (capa: as costas e os lados). Ângulo 0 = frente (+X)."""
    bm = bmesh.new()
    aneis = 4
    voltas = []
    if arco:
        a0, a1 = math.radians(arco[0]), math.radians(arco[1])
        angs = [a0 + (a1 - a0) * i / segs for i in range(segs + 1)]
    else:
        meia = math.pi * fenda
        angs = [meia + (2 * math.pi - 2 * meia) * i / segs for i in range(segs + 1)]
        if fenda <= 0:
            angs = angs[:-1]
    for k in range(aneis + 1):
        f = k / aneis
        z = z_topo + (z_base - z_topo) * f
        r = r_topo + (r_base - r_topo) * f
        voltas.append([bm.verts.new((dx * f + r * prof * math.cos(a), r * math.sin(a), z)) for a in angs])
    fechado = not arco and fenda <= 0
    n = len(angs)
    for k in range(aneis):
        for i in range(n if fechado else n - 1):
            j = (i + 1) % n
            bm.faces.new((voltas[k][i], voltas[k][j], voltas[k + 1][j], voltas[k + 1][i]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    ob = geo.objeto(nome, bm, material, suave=True)
    _pai(ob, junta_)
    _espessura(ob, esp)
    return ob


def _grupo(c, nome, ossos):
    """Grupo novo da pele contínua (ver pele.GRUPOS)."""
    from kit_humano import pele
    pele.GRUPOS.setdefault(nome, ossos)
    c.pele.setdefault(nome, [])
    return c.pele[nome]


TRONCO = ("pelvis", "lower_spine", "chest")
SAIA = ("pelvis", "thigh_e", "thigh_d")


def _sobre_tronco(c, nome, material, folga, z0=-0.02, z1=1.0, barriga=None):
    """Camada por cima do tronco (corpete, colete, gibão, casaco): as mesmas seções, com folga."""
    T, k, L = c.tronco, c.k, c.largo
    secs = c.secoes_tronco
    aneis = []
    for z, ly, lx, dx in secs:
        zz = z / T if T else 0
        if zz < z0 - 1e-6 or zz > z1 + 1e-6:
            continue
        aneis.append((z, ly + folga * k, lx + folga * k, dx))
    if len(aneis) < 2:
        aneis = [(z0 * T, 0.15 * k * L + folga * k, 0.11 * k + folga * k, 0.0),
                 (z1 * T, 0.16 * k * L + folga * k, 0.10 * k + folga * k, 0.0)]
    return _secao(nome, aneis, material, c.j["tronco"])


# ------------------------------------------------------------------ peças

def saia(c, r, mat):
    """Saia (ou barra de vestido) da cintura ao tornozelo; abre um pouco embaixo."""
    k, L = c.k, c.largo_quadril
    longa = r.get("comprimento", "longa") == "longa"
    base = -(c.quadril_h - c.tornozelo - 0.03 * k) if longa else -c.coxa * 0.9
    ob = _cone_aberto("saia", 0.10 * k, base, 0.150 * k * L, (0.25 if longa else 0.20) * k * L, mat,
                      c.j["quadril"], prof=0.85, segs=18, esp=0.03)
    _grupo(c, "saia", SAIA).append(ob)


def corpete(c, r, mat):
    _grupo(c, "corpete", TRONCO).append(_sobre_tronco(c, "corpete", mat, 0.010, z0=0.0, z1=0.72))


def colete(c, r, mat):
    ob = _sobre_tronco(c, "colete", mat, 0.012, z0=-0.05, z1=0.92)
    T = c.tronco
    # aberto na frente: a camisa aparece numa faixa de ~3 px
    _apagar(ob, lambda co: co.x > 0.05 * c.k and abs(co.y) < 0.045 * c.k and co.z > T * 0.15)
    _espessura(ob, 0.03)      # aberto vira casca; sem espessura a pele em voxels o apaga
    _grupo(c, "colete", TRONCO).append(ob)


def gibao(c, r, mat):
    _grupo(c, "gibao", TRONCO).append(_sobre_tronco(c, "gibao", mat, 0.016, z0=-0.05, z1=0.95))
    k, L = c.k, c.largo_quadril
    aba = _cone_aberto("gibao_aba", 0.06 * k, -0.10 * k, 0.16 * k * L, 0.19 * k * L, mat, c.j["quadril"],
                       prof=0.8, segs=14, esp=0.03)
    _grupo(c, "gibao_aba", SAIA).append(aba)
    if r.get("ombreiras"):
        for lado, s in (("e", 1), ("d", -1)):
            _bola("ombreira_" + lado, 0.068 * c.k, mat, c.j["ombro_" + lado], centro=(0, 0.006 * s, -0.004),
                  escala=(1.05, 1.0, 0.75), sub=1, segs=10)
    if r.get("remendos"):
        # remendos de outra cor (saqueador): retalhos chatos sobre o gibão, um no peito e dois nas costas,
        # de ~3 px, para a roupa ler como "de várias origens"; presos ao tronco, rígidos
        rem = _mat("remendo", r["remendos"])
        T = c.tronco
        for nome, zf, lado, sy in (("remendo_peito", 0.62, 1, 0.07), ("remendo_costas", 0.45, -1, -0.06),
                                   ("remendo_costas2", 0.80, -1, 0.08)):
            z = zf * T
            s = min(c.secoes_tronco, key=lambda s: abs(s[0] - z))
            x = s[3] + lado * (s[2] + 0.016 * k)
            _caixa(nome, (0.03 * k, 0.10 * k, 0.09 * k), (x, sy * k, z), rem, c.j["tronco"], sub=0)


def casaco(c, r, mat):
    """Casaco/sobretudo: tronco por cima da camisa e aba da cintura até o joelho ou a canela."""
    k, L = c.k, c.largo_quadril
    _grupo(c, "casaco", TRONCO).append(_sobre_tronco(c, "casaco", mat, 0.018, z0=-0.05, z1=0.97))
    longo = r.get("comprimento", "joelho") == "longo"
    base = -(c.coxa + c.canela * (0.55 if longo else 0.05))
    aba = _cone_aberto("casaco_aba", 0.08 * k, base, 0.165 * k * L, (0.25 if longo else 0.22) * k * L, mat,
                       c.j["quadril"], prof=0.8, segs=18, fenda=0.0 if r.get("abotoado") and not longo else 0.06,
                       esp=0.03)
    _grupo(c, "casaco_aba", SAIA).append(aba)
    # gola
    _tubo("gola", 0.05 * k, 0.07 * k, 0.085 * k, mat, c.j["pescoco"], z0=0.03 * k, sub=1)


def capa(c, r, mat):
    """Capa presa nos ombros: costas e lados; curta (cintura), média (joelho), longa (canela)."""
    k = c.k
    comp = {"curta": 0.42, "media": 0.80, "longa": 1.12}[r.get("comprimento", "media")] * k
    topo = c.tronco * 0.48 - 0.02 * k
    ob = _cone_aberto("capa", topo, topo - comp, c.ombros + 0.04 * k, c.ombros + 0.10 * k + 0.08 * comp, mat,
                      c.j["peito"], prof=0.75, segs=16, arco=(70, 290), dx=-0.05 * k, esp=0.025)
    # na pele contínua: a barra segue o quadril e as coxas (rígida, virava tábua ao curvar)
    _grupo(c, "capa", ("chest", "lower_spine", "pelvis", "thigh_e", "thigh_d")).append(ob)
    # gola da capa (fecha na frente do pescoço)
    _tubo("capa_gola", 0.05 * k, 0.085 * k, 0.11 * k, mat, c.j["pescoco"], z0=0.0, sub=1)
    return ob


def manta(c, r, mat, nome="manta", alto=0.50, baixo=0.12, folga=0.035):
    """Xale/manta sobre os ombros: cobre o alto do peito, os ombros e cai nas costas."""
    k = c.k
    T = c.tronco
    _secao(nome, [(T * baixo - T * 0.52, 0.17 * k * c.largo + folga * k, 0.11 * k + folga * k, 0.0),
                  (T * alto - T * 0.52 - 0.02 * k, c.ombros + folga * k, 0.10 * k + folga * k, 0.0),
                  (T * 0.48 + 0.01 * k, 0.09 * k, 0.07 * k, 0.0)], mat, c.j["peito"])
    for lado, s in (("e", 1), ("d", -1)):
        _bola(nome + "_ombro_" + lado, 0.06 * k, mat, c.j["ombro_" + lado], centro=(0, 0.01 * s, 0.0),
              escala=(1.1, 1.0, 0.8), sub=1, segs=10)


def xale(c, r, mat):
    manta(c, r, mat, nome="xale", alto=0.48, baixo=0.20, folga=0.025)


def capuz(c, r, mat):
    k, hc = c.k, c.cabeca
    if r.get("caido"):
        # capuz caído: dobra de pano nas costas, logo abaixo da nuca, e gola em volta do pescoço
        _tubo("capuz_gola", 0.06 * k, 0.075 * k, 0.10 * k, mat, c.j["pescoco"], z0=0.03 * k, sub=1)
        _bola("capuz_dobra", 0.085 * k, mat, c.j["peito"], centro=(-0.10 * k, 0, c.tronco * 0.42),
              escala=(0.75, 1.35, 0.7), sub=1, segs=12)
        return
    ob = _bola("capuz", hc / 2 * 1.16, mat, c.j["cabeca"], centro=(-0.012, 0, hc * 0.54), escala=(1.0, 0.95, 1.05),
               sub=0, segs=16)
    # abertura do rosto: a frente entre o queixo e a testa
    _apagar(ob, lambda co: co.x > hc * 0.16 and hc * 0.10 < co.z < hc * 0.86 and abs(co.y) < hc * 0.40)
    geo.subdividir(ob, 1)
    _espessura(ob, 0.02)
    # ponta e caimento nos ombros
    _bola("capuz_ponta", hc * 0.16, mat, c.j["cabeca"], centro=(-hc * 0.48, 0, hc * 0.78), escala=(1.2, 0.8, 0.9),
          sub=1, segs=8)
    _tubo("capuz_gola", 0.07 * k, 0.09 * k, 0.15 * k, mat, c.j["pescoco"], z0=0.05 * k, sub=1)


def chapeu_aba(c, r, mat):
    hc, k = c.cabeca, c.k
    aba = {"larga": 0.30, "media": 0.22}[r.get("aba", "media")] * k
    _tubo("chapeu_aba", 0.025 * k, aba, aba, mat, c.j["cabeca"], prof=1.0, z0=hc * 0.80, sub=1, segmentos=16)
    _bola("chapeu_copa", hc * 0.47, mat, c.j["cabeca"], centro=(-0.01, 0, hc * 0.86), escala=(1.0, 0.95, 0.62), sub=1)


def chapeu_alto(c, r, mat):
    hc, k = c.cabeca, c.k
    _tubo("chapeu_aba", 0.030 * k, hc * 0.64, hc * 0.64, mat, c.j["cabeca"], z0=hc * 0.80, sub=1, segmentos=16)
    # copa de cartola: sem subdivisão (o subsurf arredondava o cilindro num chapéu-coco) e mais alta,
    # para a silhueta da cidade ler de longe
    _tubo("chapeu_copa", 0.26 * k, hc * 0.44, hc * 0.42, mat, c.j["cabeca"], z0=hc * 0.80 + 0.26 * k, sub=0,
          segmentos=20)


def bone(c, r, mat):
    hc = c.cabeca
    _bola("bone", hc / 2 * 1.02, mat, c.j["cabeca"], centro=(0.010, 0, hc * 0.80), escala=(1.04, 0.95, 0.40), sub=1)
    _caixa("bone_pala", (hc * 0.26, hc * 0.62, 0.025 * c.k), (hc * 0.52, 0, hc * 0.74), mat, c.j["cabeca"], sub=1)


def bandana(c, r, mat):
    hc = c.cabeca
    _bola("bandana", hc / 2 * 1.07, mat, c.j["cabeca"], centro=(-0.012, 0, hc * 0.68), escala=(0.92, 0.88, 0.66),
          sub=1)
    _bola("bandana_no", hc * 0.12, mat, c.j["cabeca"], centro=(-hc * 0.47, 0, hc * 0.55), sub=1, segs=8)


def lenco_cabeca(c, r, mat):
    hc = c.cabeca
    _bola("lenco_cabeca", hc / 2 * 1.08, mat, c.j["cabeca"], centro=(-0.02, 0, hc * 0.60), escala=(0.93, 0.9, 0.78),
          sub=1)
    _bola("lenco_no", hc * 0.13, mat, c.j["cabeca"], centro=(-hc * 0.46, 0, hc * 0.32), escala=(0.8, 1.0, 1.3),
          sub=1, segs=8)


def faixa_testa(c, r, mat):
    hc = c.cabeca
    _tubo("faixa_testa", 0.04 * c.k, hc * 0.47, hc * 0.47, mat, c.j["cabeca"], prof=0.92, z0=hc * 0.76, sub=1,
          segmentos=14)


def gorro_pequeno(c, r, mat):
    hc = c.cabeca
    _bola("gorro", hc / 2 * 0.95, mat, c.j["cabeca"], centro=(-0.01, 0, hc * 0.86), escala=(0.92, 0.88, 0.45), sub=1)


def lenco_pescoco(c, r, mat):
    k = c.k
    _tubo("lenco_pescoco", 0.045 * k, 0.062 * k, 0.07 * k, mat, c.j["pescoco"], z0=0.035 * k, sub=1)
    _bola("lenco_ponta", 0.05 * k, mat, c.j["peito"], centro=(0.105 * k, 0, c.tronco * 0.40), escala=(0.45, 1.0, 0.9),
          sub=1, segs=8)


def cachecol(c, r, mat):
    k = c.k
    _tubo("cachecol", 0.075 * k, 0.08 * k, 0.09 * k, mat, c.j["pescoco"], z0=0.05 * k, sub=1)
    _bola("cachecol_ponta", 0.045 * k, mat, c.j["peito"], centro=(0.11 * k, 0.04 * k, c.tronco * 0.28),
          escala=(0.5, 0.8, 1.6), sub=1, segs=8)


def lenco_rosto(c, r, mat):
    hc = c.cabeca
    _bola("lenco_rosto", hc * 0.40, mat, c.j["cabeca"], centro=(hc * 0.14, 0, hc * 0.28), escala=(0.95, 1.0, 0.62),
          sub=1)


def corda(c, r, mat):
    """Rolo de corda no ombro esquerdo, atravessado no peito."""
    k = c.k
    bpy.ops.mesh.primitive_torus_add(major_radius=0.14 * k, minor_radius=0.028 * k, major_segments=20,
                                     minor_segments=6)
    ob = bpy.context.active_object
    ob.name = "corda"
    ob.data.materials.append(mat)
    _pai(ob, c.j["peito"])
    ob.location = (0.0, 0.07 * k, c.tronco * 0.18)
    ob.rotation_euler = (math.radians(-35), 0.0, 0.0)
    for p in ob.data.polygons:
        p.use_smooth = True


def ervas(c, r, mat):
    k = c.k
    for i, (x, y) in enumerate(((0.12, 0.10), (0.13, -0.06), (0.06, 0.15))):
        _bola("ervas_%d" % i, 0.035 * k, mat, c.j["quadril"], centro=(x * k * c.largo_quadril, y * k * c.largo_quadril,
                                                                      0.02 * k), escala=(0.7, 0.7, 1.8), sub=1, segs=6)


# ------------------------------------------------------------------ cabelo e barba

def cabelo_extra(c, estilo, mat):
    """Volumes além da calota (corpo.py faz a calota): coque, trança, cabelo longo."""
    hc, k = c.cabeca, c.k
    if estilo == "preso":
        _bola("coque", hc * 0.17, mat, c.j["cabeca"], centro=(-hc * 0.46, 0, hc * 0.60), sub=1, segs=10)
    elif estilo == "tranca":
        for i in range(4):
            _bola("tranca_%d" % i, hc * (0.12 - 0.012 * i), mat, c.j["pescoco"],
                  centro=(-0.13 * k - 0.012 * i * k, 0, -0.02 * k - 0.075 * i * k), escala=(0.9, 0.9, 1.25), sub=1,
                  segs=8)
    elif estilo == "longo":
        _bola("cabelo_costas", hc * 0.48, mat, c.j["cabeca"], centro=(-hc * 0.30, 0, hc * 0.18),
              escala=(0.55, 0.98, 1.35), sub=1)
    elif estilo == "ralo":
        _bola("cabelo_nuca", hc * 0.36, mat, c.j["cabeca"], centro=(-hc * 0.32, 0, hc * 0.30),
              escala=(0.6, 1.05, 1.0), sub=1)


def barba(c, estilo, mat):
    hc = c.cabeca
    j = c.j["cabeca"]
    if estilo == "bigode":
        _bola("bigode", hc * 0.11, mat, j, centro=(hc * 0.42, 0, hc * 0.33), escala=(0.5, 1.6, 0.5), sub=1, segs=8)
    elif estilo == "por_fazer":
        ob = _bola("barba", hc / 2 * 1.025, mat, j, centro=(0.005, 0, hc / 2), escala=(0.9, 0.84, 1.0), sub=0, segs=16)
        _apagar(ob, lambda co: co.z > hc * 0.40 or co.x < -hc * 0.05)
        geo.subdividir(ob, 1)
    elif estilo == "curta":
        _bola("barba", hc * 0.25, mat, j, centro=(hc * 0.26, 0, hc * 0.25), escala=(0.8, 1.15, 0.85), sub=1)
    else:  # cheia (padrão) e longa
        _bola("barba", hc * 0.30, mat, j, centro=(hc * 0.24, 0, hc * 0.22), escala=(0.8, 1.15, 0.9), sub=1)
        if estilo == "longa":
            _bola("barba_ponta", hc * 0.24, mat, j, centro=(hc * 0.30, 0, -hc * 0.05), escala=(0.6, 0.9, 1.4), sub=1)


# ------------------------------------------------------------------ ferramentas

def _mat(nome, rampa, **kw):
    return materiais.material(nome, rampa, k=kw.pop("k", 0.1), oa=0.7, **kw)


def _haste(nome, mao, baixo, alto, raio, mat):
    """Haste vertical na mão (cajado, lança, cabo): de `baixo` (negativo, para o chão) a `alto`,
    medidos ao longo do antebraço em repouso (o braço pende, então é a vertical)."""
    # sem subdivisão: num cilindro longo e tampado, sem anéis no meio, o subsurf afinava as duas
    # pontas até sumir (a lança da Vessa perdia a metade de cima e a ponta flutuava)
    return _tubo(nome, alto - baixo, raio, raio, mat, mao, z0=alto, sub=0, segmentos=10)


def ferramenta(c, mao_lado, qual):
    j = c.j["mao_" + mao_lado]
    k = c.k
    # da mão até 4 cm acima do chão, com o braço pendente (o quadril desce até 4,5 cm no andar)
    chao = -(c.quadril_h + c.tronco - c.braco - c.antebraco - 0.04 * k)
    madeira = _mat("ferr_madeira", "madeira_clara")   # contrasta com calça e túnica marrons
    ferro = _mat("ferr_ferro", "ferro", k=0.05)
    if qual == "martelo_forja":
        _tubo("martelo_cabo", 0.36 * k, 0.030 * k, 0.030 * k, madeira, j, z0=0.04 * k, sub=0, segmentos=10)
        _caixa("martelo_cabeca", (0.15 * k, 0.07 * k, 0.07 * k), (0.02 * k, 0, -0.32 * k), ferro, j, sub=0)
    elif qual in ("enxada", "pa"):
        _haste(qual + "_cabo", j, chao + 0.10 * k, 0.55 * k, 0.030 * k, madeira)
        if qual == "enxada":
            _caixa("enxada_lamina", (0.10 * k, 0.20 * k, 0.03 * k), (0.06 * k, 0, chao + 0.08 * k), ferro, j, sub=0)
        else:
            _caixa("pa_lamina", (0.04 * k, 0.20 * k, 0.26 * k), (0, 0, chao + 0.02 * k), ferro, j, sub=0)
    elif qual == "foice":
        _tubo("foice_cabo", 0.22 * k, 0.030 * k, 0.030 * k, madeira, j, z0=0.03 * k, sub=0, segmentos=10)
        _bola("foice_lamina", 0.12 * k, ferro, j, centro=(0.09 * k, 0, -0.24 * k), escala=(1.0, 0.18, 0.35), sub=1)
    elif qual == "bastao_medir":
        _haste("bastao", j, chao, 1.05 * k, 0.032 * k, madeira)
        for i, z in enumerate((0.25, 0.55, 0.85)):
            _tubo("bastao_marca_%d" % i, 0.035 * k, 0.03 * k, 0.03 * k, _mat("ferr_marca", "tecido_vermelho"), j,
                  z0=z * k, sub=0, segmentos=8)
    elif qual == "bengala":
        _haste("bengala", j, chao, 0.08 * k, 0.030 * k, madeira)
        _bola("bengala_cabo", 0.035 * k, madeira, j, centro=(0.03 * k, 0, 0.09 * k), escala=(1.6, 0.7, 0.7), sub=1)
    elif qual == "lanca":
        _haste("lanca", j, chao, 1.25 * k, 0.030 * k, madeira)
        _bola("lanca_ponta", 0.05 * k, ferro, j, centro=(0, 0, 1.33 * k), escala=(0.45, 0.25, 1.8), sub=1, segs=8)
    elif qual == "cajado_trouxa":
        _haste("cajado", j, chao, 0.95 * k, 0.032 * k, madeira)
        _bola("trouxa", 0.13 * k, _mat("ferr_trouxa", "linho", luz_min=0.3), j, centro=(-0.06 * k, 0, 0.98 * k),
              escala=(1.0, 0.9, 0.85), sub=1)
    elif qual == "machadinha":
        _tubo("machado_cabo", 0.40 * k, 0.030 * k, 0.030 * k, madeira, j, z0=0.05 * k, sub=0, segmentos=10)
        _caixa("machado_lamina", (0.12 * k, 0.03 * k, 0.10 * k), (0.06 * k, 0, -0.30 * k), ferro, j, sub=0)
    elif qual == "livro":
        _caixa("livro", (0.17 * k, 0.13 * k, 0.04 * k), (0.06 * k, 0, -0.06 * k), _mat("ferr_livro", "couro"), j, sub=0)
    elif qual == "pasta":
        _caixa("pasta", (0.05 * k, 0.32 * k, 0.24 * k), (0, 0, -0.10 * k), _mat("ferr_pasta", "couro"), j, sub=0)
    elif qual == "carta":
        _caixa("carta", (0.03 * k, 0.12 * k, 0.09 * k), (0.03 * k, 0, -0.06 * k),
               _mat("ferr_carta", "linho", luz_min=0.4), j, sub=0)
    elif qual == "cesto":
        _tubo("cesto", 0.14 * k, 0.15 * k, 0.12 * k, _mat("ferr_cesto", "palha", k=0.3), j, z0=-0.06 * k, sub=1,
              segmentos=12)
    elif qual == "bandeja":
        _tubo("bandeja", 0.03 * k, 0.17 * k, 0.17 * k, madeira, j, prof=1.0, z0=-0.05 * k, sub=1, segmentos=14)
    # --- V3: o que o jogador leva na mão (camadas, SPEC-visual §6.4). Tudo um pouco maior que o
    # real, para ler a 48 px: a lâmina da faca tem 3 px, a ponta da lança uns 4.
    elif qual == "faca":
        _tubo("faca_cabo", 0.09 * k, 0.026 * k, 0.026 * k, madeira, j, z0=0.03 * k, sub=0, segmentos=8)
        _caixa("faca_guarda", (0.03 * k, 0.07 * k, 0.02 * k), (0, 0, -0.065 * k), ferro, j, sub=0)
        _bola("faca_lamina", 0.10 * k, ferro, j, centro=(0.0, 0, -0.16 * k), escala=(0.32, 0.16, 1.0), sub=1, segs=8)
    elif qual == "martelo":
        _tubo("martelo_cabo", 0.30 * k, 0.026 * k, 0.026 * k, madeira, j, z0=0.04 * k, sub=0, segmentos=10)
        _caixa("martelo_cabeca", (0.11 * k, 0.055 * k, 0.055 * k), (0.015 * k, 0, -0.27 * k), ferro, j, sub=0)
    elif qual == "cajado":
        _haste("cajado", j, chao, 1.0 * k, 0.034 * k, madeira)
        _bola("cajado_no", 0.045 * k, madeira, j, centro=(0, 0, 1.0 * k), escala=(1.0, 1.0, 1.2), sub=1, segs=8)
    elif qual == "lanca_curta":
        _haste("lanca_curta", j, chao + 0.25 * k, 0.75 * k, 0.030 * k, madeira)
        _bola("lanca_curta_ponta", 0.05 * k, ferro, j, centro=(0, 0, 0.83 * k), escala=(0.45, 0.25, 1.8),
              sub=1, segs=8)
    elif qual == "lamparina":
        # pendurada pela alça, abaixo da mão; o vidro brilha (imagem de brilho) e o ponto `luz`
        # diz ao cliente onde fica a luz que segue a mão
        lata = _mat("ferr_lamparina", "ferro", k=0.05)
        vidro = materiais.material("lamparina_vidro", "vidro", k=0.1, emissivo=True, contorno=False,
                                   linhas=False, rampa_brilho="fogo")
        _tubo("lamparina_alca", 0.06 * k, 0.012 * k, 0.012 * k, lata, j, z0=-0.02 * k, sub=0, segmentos=6)
        _tubo("lamparina_tampa", 0.035 * k, 0.03 * k, 0.055 * k, lata, j, z0=-0.08 * k, sub=0, segmentos=10)
        _tubo("lamparina_vidro", 0.09 * k, 0.048 * k, 0.048 * k, vidro, j, z0=-0.115 * k, sub=0, segmentos=10)
        _tubo("lamparina_base", 0.03 * k, 0.055 * k, 0.055 * k, lata, j, z0=-0.205 * k, sub=0, segmentos=10)
        ponto("luz", j, (0, 0, -0.16 * k))
    elif qual == "caneca":
        _tubo("caneca", 0.11 * k, 0.045 * k, 0.045 * k, _mat("ferr_caneca", "ferro", k=0.05), j, z0=-0.02 * k, sub=1,
              segmentos=10)


PECAS = {
    "saia": (saia, "tecido_marrom"), "corpete": (corpete, "tecido_marrom"), "colete": (colete, "tecido_marrom"),
    "gibao": (gibao, "couro"), "casaco": (casaco, "tecido_cinza"), "capa": (capa, "tecido_marrom"),
    "manta": (manta, "tecido_vermelho"), "xale": (xale, "tecido_vermelho"), "capuz": (capuz, "tecido_marrom"),
    "chapeu_aba": (chapeu_aba, "palha"), "chapeu_alto": (chapeu_alto, "quase_preto"), "bone": (bone, "tecido_azul"),
    "bandana": (bandana, "tecido_vermelho"), "lenco_cabeca": (lenco_cabeca, "linho"),
    "faixa_testa": (faixa_testa, "la_crua"), "lenco_pescoco": (lenco_pescoco, "tecido_vermelho"),
    "cachecol": (cachecol, "la_crua"), "lenco_rosto": (lenco_rosto, "quase_preto"), "corda": (corda, "palha"),
    "ervas": (ervas, "folha_conifera"),
}
