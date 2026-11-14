"""Kit humano do ETRAVA (SPEC-visual §6.1): esqueleto padrão + corpo paramétrico + roupa.

O esqueleto são vazios com nomes fixos; cada peça do corpo é uma malha rígida filha de uma
junta (a 48 px não se vê dobra de pele, e peças rígidas não têm surpresa de peso). Qualquer
animação de anim/ciclos.py serve em qualquer corpo, porque só gira juntas por nome.

Em repouso o personagem olha para +X (leste do jogo), com a esquerda em +Y. Os membros pendem
ao longo de -Z da junta; girar em Y para negativo leva o membro para a frente (ver `balanco`).
"""
import math

import bmesh
import bpy
from mathutils import Matrix, Vector

from nucleo import camera, geo, materiais

JUNTAS = ("raiz", "quadril", "tronco", "peito", "pescoco", "cabeca",
          "ombro_e", "braco_e", "antebraco_e", "mao_e",
          "ombro_d", "braco_d", "antebraco_d", "mao_d",
          "coxa_e", "canela_e", "pe_e", "coxa_d", "canela_d", "pe_d")


class Corpo:
    """Medidas (em metros) e juntas de um corpo montado."""

    def __init__(self, p):
        self.altura = p.get("altura", 1.70)
        k = self.altura / 1.70
        largo = {"magro": 0.93, "medio": 1.0, "largo": 1.18}[p.get("corpo", "medio")]
        self.largo = largo
        self.k = k
        self.tornozelo = 0.08 * k
        self.canela = 0.36 * k
        self.coxa = 0.38 * k
        self.quadril_h = self.tornozelo + self.canela + self.coxa      # junta do quadril
        self.tronco = 0.48 * k
        self.pescoco = 0.06 * k
        self.cabeca = 0.29 * k * p.get("cabeca", 1.0)
        self.braco = 0.27 * k
        self.antebraco = 0.24 * k
        f = p.get("sexo", "m") == "f"
        self.sexo = "f" if f else "m"
        # V2: ombro mais estreito e quadril mais largo no corpo feminino; barriga; braços grossos
        self.ombros = 0.185 * k * largo * (0.88 if f else 1.0)          # meia largura
        self.quadris = 0.085 * k * largo * (1.06 if f else 1.0)         # meio afastamento das pernas
        self.largo_quadril = largo * (1.10 if f else 1.0)
        self.barriga = p.get("barriga", 0.0)
        self.bracos = p.get("bracos", 1.0)
        self.curvado = p.get("postura", "ereta") == "curvada"
        self.curvatura = 1.0 if self.curvado else 0.0      # a ficha (jeito.curvatura) pode mudar
        self.rosto = p.get("rosto", 16.0)        # graus de queixo para cima (ver anim.ciclos)
        self.j = {}
        self.pele = {"camisa": [], "calca": [], "botas_e": [], "botas_d": [], "alca": [], "avental": []}
        self.organico = p.get("organico", True)


def _pai(ob, junta):
    ob.parent = junta
    ob.matrix_parent_inverse = Matrix.Identity(4)
    return ob


def junta(corpo, nome, pai, local):
    v = geo.vazio(nome, pai=pai, local=local)
    v.rotation_mode = "XYZ"
    corpo.j[nome] = v
    return v


def esqueleto(c):
    r = junta(c, "raiz", camera.raiz(), (0, 0, 0))
    q = junta(c, "quadril", r, (0, 0, c.quadril_h))
    t = junta(c, "tronco", q, (0, 0, 0.05 * c.k))
    # peito (D-102, do rig do diretor): a coluna dobra em dois — a contrarrotação dos ombros e a
    # respiração ficam no peito, a inclinação no tronco
    pt = junta(c, "peito", t, (0, 0, c.tronco * 0.52))
    pe = junta(c, "pescoco", pt, (0, 0, c.tronco * 0.48))
    junta(c, "cabeca", pe, (0, 0, c.pescoco))
    for lado, s in (("e", 1), ("d", -1)):
        o = junta(c, "ombro_" + lado, pt, (0, s * c.ombros, c.tronco * 0.48 - 0.05 * c.k))
        b = junta(c, "braco_" + lado, o, (0, 0, 0))
        a = junta(c, "antebraco_" + lado, b, (0, 0, -c.braco))
        junta(c, "mao_" + lado, a, (0, 0, -c.antebraco))
        cx = junta(c, "coxa_" + lado, q, (0, s * c.quadris, 0))
        cn = junta(c, "canela_" + lado, cx, (0, 0, -c.coxa))
        junta(c, "pe_" + lado, cn, (0, 0, -c.canela))
    return c


# ------------------------------------------------------------------ peças

def _tubo(nome, comprimento, r_topo, r_base, material, junta_, prof=None, sub=2, segmentos=8, z0=0.0):
    """Tubo arredondado pendendo de z0 até z0 - comprimento. `prof` achata em X (frente-trás)."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, cap_tris=False, segments=segmentos,
                          radius1=r_base, radius2=r_topo, depth=comprimento)
    for v in bm.verts:
        v.co.z += z0 - comprimento / 2
        if prof is not None:
            v.co.x *= prof
    ob = geo.objeto(nome, bm, material, suave=True)
    _pai(ob, junta_)
    if sub:
        geo.subdividir(ob, sub)
    return ob


def _secao(nome, aneis, material, junta_, segs=12, sub=1):
    """Tronco por seções: aneis = [(z, meia_largura_y, meia_profundidade_x, deslocamento_x)], de baixo
    para cima. Dá cintura, peito e ombros (o tubo reto dava um caixote)."""
    bm = bmesh.new()
    voltas = []
    for z, ly, lx, dx in aneis:
        volta = []
        for i in range(segs):
            a = 2 * math.pi * i / segs
            volta.append(bm.verts.new((dx + lx * math.cos(a), ly * math.sin(a), z)))
        voltas.append(volta)
    for k in range(len(voltas) - 1):
        for i in range(segs):
            j = (i + 1) % segs
            bm.faces.new((voltas[k][i], voltas[k][j], voltas[k + 1][j], voltas[k + 1][i]))
    bm.faces.new(list(reversed(voltas[0])))
    bm.faces.new(voltas[-1])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    ob = geo.objeto(nome, bm, material, suave=True)
    _pai(ob, junta_)
    if sub:
        geo.subdividir(ob, sub)
    return ob


def _bola(nome, raio, material, junta_, centro=(0, 0, 0), escala=(1, 1, 1), sub=1, segs=12):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=segs, v_segments=max(6, segs // 2), radius=raio)
    for v in bm.verts:
        v.co = Vector((v.co.x * escala[0] + centro[0], v.co.y * escala[1] + centro[1], v.co.z * escala[2] + centro[2]))
    ob = geo.objeto(nome, bm, material, suave=True)
    _pai(ob, junta_)
    if sub:
        geo.subdividir(ob, sub)
    return ob


def _caixa(nome, tam, centro, material, junta_, sub=1, cortar_topo=None):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co = Vector((v.co.x * tam[0] + centro[0], v.co.y * tam[1] + centro[1], v.co.z * tam[2] + centro[2]))
    ob = geo.objeto(nome, bm, material, suave=sub > 0)
    _pai(ob, junta_)
    if sub:
        m = ob.modifiers.new("sub", "SUBSURF")
        m.levels = m.render_levels = sub
    return ob


def ponto(nome, junta_, local, frente=(1, 0, 0)):
    """Ponto marcado (mão, olho, cabeça): sai no JSON do quadro; o eixo X local é a 'frente'."""
    v = geo.vazio("ponto." + nome, pai=junta_, local=local)
    v.matrix_parent_inverse = Matrix.Identity(4)
    v.rotation_mode = "QUATERNION"
    v.rotation_quaternion = Vector(frente).to_track_quat("X", "Z")
    return v


def montar(p):
    """Monta corpo + roupa a partir da descrição do personagem (YAML)."""
    c = esqueleto(Corpo(p.get("corpo", {})))
    cores = p.get("cores", {})

    def mat(nome, rampa_padrao, **kw):
        return materiais.material(nome, cores.get(nome, rampa_padrao), **kw)

    pele = mat("pele", "pele_clara", k=0.0, oa=0.6, luz_min=0.45)
    roupa = {r["peca"]: r for r in p.get("roupa", [])}
    j = c.j
    L = c.largo

    def tem(peca):
        return peca in roupa

    def mat_peca(peca, rampa, **kw):
        r = roupa.get(peca, {})
        nome = r.get("material", peca)
        return materiais.material(nome, r.get("rampa", cores.get(nome, rampa)),
                                  k=r.get("k", kw.pop("k", 0.12)), textura=r.get("textura"),
                                  escala=r.get("escala", 6.0), luz_min=r.get("luz_min", kw.pop("luz_min", 0.0)),
                                  oa=kw.pop("oa", 0.7), tons=r.get("tons"), **kw)

    camisa = mat_peca("camisa", "linho")
    calca = mat_peca("calca", "tecido_marrom")
    bota = mat_peca("botas", "couro")

    # --- tronco e quadril
    # tronco com cintura, peito e ombros caídos (seções); um pouco curvado para a frente no alto
    T, k = c.tronco, c.k
    f = c.sexo == "f"
    Lq, Lo = c.largo_quadril, L * (0.90 if f else 1.0)
    bar = c.barriga
    c.secoes_tronco = [(-0.02, 0.140 * k * Lq, 0.100 * k + 0.02 * k * bar, 0.0),
                       (T * 0.35, (0.145 if not f else 0.130) * k * max(Lq, L) + 0.02 * k * bar,
                        0.105 * k + 0.05 * k * bar, 0.005 + 0.035 * k * bar),
                       (T * 0.68, 0.160 * k * Lo + 0.01 * k * bar, (0.116 if f else 0.104) * k + 0.02 * k * bar,
                        0.010 + 0.01 * k * bar),
                       (T * 0.88, 0.165 * k * Lo, 0.090 * k, 0.005),
                       (T * 1.00, 0.120 * k * Lo, 0.075 * k, 0.0)]
    c.pele["camisa"].append(_secao("tronco", c.secoes_tronco, camisa, j["tronco"]))
    for lado, sy in (("e", 1), ("d", -1)):     # deltoide: ombro redondo, não quina
        _bola("deltoide_" + lado, 0.046 * k, camisa, j["ombro_" + lado], centro=(0, -0.004 * sy * k, -0.018 * k),
              escala=(1.0, 0.95, 1.0), sub=1, segs=10)
    c.pele["calca"].append(_tubo("quadril", 0.17 * c.k, 0.145 * c.k * c.largo_quadril, 0.15 * c.k * c.largo_quadril,
                                 calca, j["quadril"], prof=0.7,
                                 z0=0.08 * c.k))
    _tubo("pescoco", c.pescoco + 0.03, 0.042 * c.k, 0.05 * c.k, pele, j["pescoco"], z0=c.pescoco + 0.02, sub=1)

    # --- cabeça (um pouco maior que o real, para ler a 48 px)
    hc = c.cabeca
    cab_ob = _bola("cabeca", hc / 2, pele, j["cabeca"], centro=(0.005, 0, hc / 2), escala=(0.9, 0.84, 1.0), sub=0, segs=16)
    for v in cab_ob.data.vertices:          # queixo: a metade de baixo afina e vem um pouco à frente
        f = max(0.0, (hc * 0.45 - v.co.z) / (hc * 0.45))
        v.co.y *= 1.0 - 0.32 * f
        v.co.x = v.co.x * (1.0 - 0.18 * f) + 0.012 * f * c.k
    geo.subdividir(cab_ob, 1)
    for lado, sy in (("e", 1), ("d", -1)):  # orelhas: quebram a silhueta redonda da cabeça
        _bola("orelha_" + lado, 0.016 * c.k, pele, j["cabeca"], centro=(-0.005, sy * hc * 0.39, hc * 0.46),
              escala=(0.7, 0.5, 1.2), sub=0, segs=6)
    _bola("nariz", 0.022 * c.k, pele, j["cabeca"], centro=(hc * 0.42, 0, hc * 0.42), sub=0, segs=6)
    olho_z, olho_x, olho_y = hc * 0.52, hc * 0.40, hc * 0.17
    ponto("olho_e", j["cabeca"], (olho_x, olho_y, olho_z), frente=(1, 0.25, 0))
    ponto("olho_d", j["cabeca"], (olho_x, -olho_y, olho_z), frente=(1, -0.25, 0))
    ponto("cabeca", j["cabeca"], (0, 0, hc))
    from kit_humano import roupa as rp
    estilo = roupa.get("cabelo", {}).get("estilo", "curto")
    if tem("cabelo") and estilo != "careca":
        cab = mat_peca("cabelo", "cabelo_castanho", k=0.2)
        # calota: cobre topo e nuca, deixa a testa
        if estilo in ("calvo", "ralo"):  # careca no alto: só os lados e a nuca, mais baixos
            _bola("cabelo", hc / 2 * 1.05, cab, j["cabeca"], centro=(-0.035, 0, hc * 0.40), escala=(0.84, 0.92, 0.55),
                  sub=1)
        elif estilo in ("sob_gorro", "sob_lenco", "sob_capuz"):  # só a nuca e os lados, abaixo do gorro
            _bola("cabelo", hc / 2 * 1.04, cab, j["cabeca"], centro=(-0.03, 0, hc * 0.5), escala=(0.84, 0.86, 0.7), sub=1)
        else:
            # calota com linha do cabelo: alta na testa, na altura da orelha dos lados, baixa na
            # nuca; abaixo da linha o cabelo entra na cabeça (some). Sem isso vira capacete e o
            # rosto fica com 3 px.
            cab_ob = _bola("cabelo", hc / 2 * 1.07, cab, j["cabeca"], centro=(0.002, 0, hc * 0.52),
                           escala=(0.92, 0.88, 0.98 if estilo == "curto" else 1.02), sub=0, segs=16)
            for v in cab_ob.data.vertices:
                rel = Vector((v.co.x - 0.002, v.co.y, v.co.z - hc * 0.52))
                nx = rel.x / max(1e-6, rel.length)
                linha = hc * (0.50 + 0.24 * nx)          # testa 0,74 · lados 0,50 · nuca 0,26
                if v.co.z < linha:
                    v.co = Vector((0.002, 0, hc * 0.52)) + rel * 0.80
            geo.subdividir(cab_ob, 1)
            # franja: mecha caindo na testa (a frente da cabeça não fica careca nem capacete)
            if estilo != "penteado":
                _bola("franja", hc * 0.13, cab, j["cabeca"], centro=(hc * 0.36, hc * 0.12, hc * 0.80),
                      escala=(0.45, 1.2, 0.45), sub=1, segs=8)
        rp.cabelo_extra(c, estilo, cab)
        if estilo in ("bagunçado", "baguncado"):
            for i, (x, y, z) in enumerate(((0.07, 0.04, 0.92), (0.08, -0.05, 0.90), (0.0, 0.08, 0.95),
                                            (-0.06, -0.06, 0.88), (0.05, 0.0, 1.0))):
                _bola("mecha%d" % i, 0.035 * c.k, cab, j["cabeca"], centro=(x * c.k, y * c.k, hc * z), sub=0, segs=6)
    if tem("gorro"):
        g = mat_peca("gorro", "ferrugem", k=0.2)
        if roupa["gorro"].get("tamanho") == "pequeno":
            rp.gorro_pequeno(c, roupa["gorro"], g)
    if tem("gorro") and roupa["gorro"].get("tamanho") != "pequeno":
        g = mat_peca("gorro", "ferrugem", k=0.2)
        _bola("gorro", hc / 2 * 1.06, g, j["cabeca"], centro=(-0.018, 0, hc * 0.76), escala=(0.9, 0.86, 0.56), sub=1)
        _tubo("gorro_aba", 0.045 * c.k, hc * 0.455, hc * 0.455, g, j["cabeca"], prof=0.9, z0=hc * 0.84, sub=1)
    if tem("barba"):
        b = mat_peca("barba", "cabelo_grisalho", k=0.15)
        rp.barba(c, roupa["barba"].get("estilo", "cheia"), b)
    if tem("oculos"):
        # óculos na testa: dois pontos claros sobre o gorro/testa (decalque do pixelizador)
        ponto("oculos", j["cabeca"], (hc * 0.40, 0, hc * 0.70))

    # --- braços
    manga = roupa.get("camisa", {}).get("manga", "longa")
    for lado in ("e", "d"):
        B = c.bracos
        _tubo("braco_" + lado, c.braco + 0.02, 0.064 * c.k * L * B, 0.055 * c.k * B, camisa, j["braco_" + lado], z0=0.02)
        if tem("manga_baixo"):
            mb = mat_peca("manga_baixo", "la_crua", k=0.1)
            _tubo("antebraco_" + lado, c.antebraco * 0.62, 0.056 * c.k, 0.05 * c.k, mb, j["antebraco_" + lado], z0=0.0)
            _tubo("pulso_" + lado, c.antebraco * 0.42, 0.045 * c.k, 0.04 * c.k, pele, j["antebraco_" + lado],
                  z0=-c.antebraco * 0.58)
        elif manga == "arregacada":
            _tubo("antebraco_" + lado, c.antebraco, 0.048 * c.k, 0.04 * c.k, pele, j["antebraco_" + lado])
            _tubo("punho_" + lado, 0.06 * c.k, 0.06 * c.k, 0.056 * c.k, camisa, j["antebraco_" + lado], z0=0.02, sub=1)
        else:
            _tubo("antebraco_" + lado, c.antebraco, 0.054 * c.k, 0.045 * c.k, camisa, j["antebraco_" + lado])
        _bola("mao_" + lado, 0.043 * c.k, pele, j["mao_" + lado], centro=(0.0, 0, -0.035 * c.k),
              escala=(0.9, 0.7, 1.15), sub=1, segs=8)
        ponto("mao_" + lado, j["mao_" + lado], (0, 0, -0.04 * c.k))

    # --- pernas
    for lado in ("e", "d"):
        c.pele["calca"].append(_tubo("coxa_" + lado, c.coxa + 0.03, 0.082 * c.k * L, 0.060 * c.k, calca,
                                     j["coxa_" + lado], z0=0.03))
        alto_bota = roupa.get("botas", {}).get("altura", 0.16) * c.k
        c.pele["calca"].append(_tubo("canela_" + lado, c.canela - alto_bota + 0.02, 0.058 * c.k, 0.050 * c.k, calca,
                                     j["canela_" + lado]))
        c.pele["botas_" + lado].append(_tubo("cano_" + lado, alto_bota, 0.06 * c.k, 0.054 * c.k, bota,
                                             j["canela_" + lado], z0=-(c.canela - alto_bota), sub=1))
        c.pele["botas_" + lado].append(_caixa("pe_" + lado, (0.20 * c.k, 0.09 * c.k, 0.075 * c.k),
                                              (0.04 * c.k, 0, -0.045 * c.k), bota, j["pe_" + lado], sub=1))

    # --- peças por cima
    barra = roupa.get("camisa", {}).get("barra")
    if barra:
        # a camisa cai por cima da calça até o alto da coxa, abrindo um pouco
        c.pele["camisa"].append(_tubo("barra", barra * c.k + 0.06 * c.k, 0.155 * c.k * L, 0.175 * c.k * L, camisa,
                                      j["quadril"], prof=0.76, z0=0.14 * c.k, segmentos=10))
    if tem("cinto"):
        cm = mat_peca("cinto", "couro", k=0.05)
        _tubo("cinto", 0.045 * c.k, 0.157 * c.k * L, 0.157 * c.k * L, cm, j["quadril"], prof=0.72, z0=0.11 * c.k, sub=1)
    if tem("bolsa"):
        bm_ = mat_peca("bolsa", "couro", k=0.1)
        _caixa("bolsa", (0.11 * c.k, 0.06 * c.k, 0.12 * c.k), (-0.03 * c.k, 0.15 * c.k * L, -0.02 * c.k), bm_, j["quadril"])
        # alça do ombro direito ao quadril esquerdo, cruzando o peito
        alca = mat_peca("alca", "couro", k=0.05)
        c.pele["alca"].append(_alca(alca, j["peito"], c))
    if tem("avental"):
        av = mat_peca("avental", "couro", k=0.18)
        ra = roupa["avental"]
        topo = c.quadril_h + 0.05 * c.k + (c.tronco * 0.12 if ra.get("so_cintura") else c.tronco * 0.82)
        base = c.quadril_h - {"longo": c.coxa + c.canela * 0.55, "curto": c.coxa * 0.45}.get(
            ra.get("comprimento"), c.coxa * 0.72)
        c.pele["avental"].extend(_avental(av, j["quadril"], c, topo - c.quadril_h, base - c.quadril_h,
                                          alcas=not ra.get("so_cintura")))
    # V2: peças por cima (roupa.PECAS) e ferramentas de ofício
    for peca, r in roupa.items():
        if peca in rp.PECAS:
            fn, rampa = rp.PECAS[peca]
            fn(c, r, mat_peca(peca, rampa))
    for mao in ("mao_d", "mao_e"):
        if p.get("ferramenta", {}).get(mao):
            rp.ferramenta(c, mao[-1], p["ferramenta"][mao])
    if c.organico:
        from kit_humano import pele
        pele.montar(c)
    return c


def _alca(material, tronco, c):
    """Alça diagonal da bolsa: do ombro direito ao quadril esquerdo, pela frente e pelas costas."""
    bm = bmesh.new()
    L = c.largo
    pts = [(0.0, -0.13 * L, c.tronco - 0.02), (0.108, -0.06, c.tronco * 0.62), (0.112, 0.07, c.tronco * 0.25),
           (0.09, 0.14 * L, 0.0)]
    for frente in (1, -1):
        anterior = None
        for (x, y, z) in pts:
            a = bm.verts.new((x * frente * c.k, y * c.k, z + 0.02))
            b = bm.verts.new((x * frente * c.k, y * c.k, z - 0.035))
            if anterior:
                bm.faces.new((anterior[0], anterior[1], b, a))
            anterior = (a, b)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    ob = geo.objeto("alca", bm, material)
    so = ob.modifiers.new("esp", "SOLIDIFY")
    so.thickness = 0.02
    _pai(ob, tronco)
    ob.location = (0, 0, -c.tronco * 0.52)     # a alça foi desenhada a partir do tronco
    return ob


def _avental(material, quadril, c, topo, base, alcas=True):
    """Avental de couro: placa levemente curva na frente do peito até o joelho, com alças."""
    bm = bmesh.new()
    L = c.largo
    linhas = []
    n = 5
    for i in range(n):
        f = i / (n - 1)
        z = topo + (base - topo) * f
        meia = (0.10 + 0.05 * f) * c.k * L
        x = (0.115 if z > 0 else 0.13) * c.k
        linha = [bm.verts.new((x + 0.02 * (1 - abs(s)), s * meia, z)) for s in (-1, -0.5, 0, 0.5, 1)]
        linhas.append(linha)
    for i in range(n - 1):
        for k in range(4):
            bm.faces.new((linhas[i][k], linhas[i][k + 1], linhas[i + 1][k + 1], linhas[i + 1][k]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    ob = geo.objeto("avental", bm, material)
    so = ob.modifiers.new("esp", "SOLIDIFY")
    so.thickness = 0.025
    _pai(ob, quadril)
    pecas = [ob]
    # alças do avental, por cima dos ombros
    for s in ((1, -1) if alcas else ()):
        pecas.append(_caixa("avental_alca_%d" % s, (0.20 * c.k, 0.03 * c.k, 0.025 * c.k),
               (0.0, s * 0.10 * c.k * L, topo + 0.05 * c.k), material, quadril, sub=0))
    return pecas
