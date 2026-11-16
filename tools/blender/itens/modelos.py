"""Modelos dos 43 itens (V6, SPEC-visual §2.2 `itens/`): uma função por item, com o mesmo nome do
id em content/items.yaml. Cada função monta o item em metros, perto da origem, sob o vazio
`raiz` (a tarefa tira a correção vertical do mundo, enquadra e normaliza o tamanho).

Eixos do Blender: X para a direita da tela, Y para longe da câmera, Z para cima. A câmera do ícone
olha de -Y, de cima (`elev` na ficha); a luz é a do mundo (alto, à esquerda e da frente).

As ferramentas e armas que o aprendiz já leva na mão (kit_humano/roupa.py, `ferramenta`) têm aqui
as mesmas peças, materiais e proporções; só a espessura dos cabos é afinada para o ícone (a 48 px
de personagem o cabo precisava ser grosso; aqui o item ocupa o quadro inteiro).

Desenho em papel (escrita, engrenagem, carimbo) é pintura por face numa grade fina: a tinta é um
material de rampa escura, sem relevo e sem contorno.
"""
import math
import random

import bmesh
from mathutils import Matrix, Vector, noise

from nucleo import geo, materiais

Z = (0.0, 0.0, 1.0)


# ------------------------------------------------------------------ materiais

def mat(nome, rampa, **kw):
    kw.setdefault("k", 0.12)
    kw.setdefault("oa", 0.5)
    return materiais.material("it_" + nome, rampa, **kw)


def tinta(rampa="tecido_marrom", nome="tinta"):
    """Tinta, carvão, carimbo: cor chapada sobre o papel."""
    return mat(nome, rampa, k=0.0, oa=0.0, contorno=False, linhas=False, luz_min=0.0)


# ------------------------------------------------------------------ malhas

def ob(nome, construir, mats, suave=False, sub=0):
    bm = bmesh.new()
    construir(bm)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    o = geo.objeto(nome, bm, None, suave=suave)
    for m in (mats if isinstance(mats, (list, tuple)) else [mats]):
        o.data.materials.append(m)
    if sub:
        geo.subdividir(o, sub)
    return o


def _frame(a, b, cima=Z):
    """Matriz que leva o eixo Z local (0..L) ao segmento a→b; X local fica na horizontal."""
    a = Vector(a)
    d = Vector(b) - a
    L = d.length
    z = d / L
    x = Vector(cima).cross(z)
    if x.length < 1e-6:
        x = Vector((0.0, 1.0, 0.0)).cross(z)
    x.normalize()
    y = z.cross(x)
    return Matrix(((x.x, y.x, z.x, a.x), (x.y, y.y, z.y, a.y), (x.z, y.z, z.z, a.z), (0, 0, 0, 1))), L


def _aplicar(verts, M):
    for v in verts:
        v.co = M @ v.co


def tubo(bm, a, b, r1, r2=None, seg=10, cima=Z):
    M, L = _frame(a, b, cima)
    res = bmesh.ops.create_cone(bm, cap_ends=True, segments=seg, radius1=r1, radius2=r1 if r2 is None else r2,
                                depth=L)
    for v in res["verts"]:
        v.co.z += L / 2
    _aplicar(res["verts"], M)
    return res["verts"]


def barra(bm, a, b, larg, alt, cima=Z):
    """Caixa de a até b; `larg` na horizontal perpendicular, `alt` na direção de `cima`."""
    M, L = _frame(a, b, cima)
    res = bmesh.ops.create_cube(bm, size=1.0)
    for v in res["verts"]:
        v.co = Vector((v.co.x * larg, v.co.y * alt, (v.co.z + 0.5) * L))
    _aplicar(res["verts"], M)
    return res["verts"]


def caixa(bm, c, tam, giro=0.0):
    res = bmesh.ops.create_cube(bm, size=1.0)
    R = Matrix.Rotation(math.radians(giro), 4, "Z")
    for v in res["verts"]:
        v.co = (R @ Vector((v.co.x * tam[0], v.co.y * tam[1], v.co.z * tam[2]))) + Vector(c)
    return res["verts"]


def bola(bm, c, r, s=(1, 1, 1), sub=2, rot=None, forma=None):
    """Icosfera; `forma(v)` deforma o ponto da esfera unitária antes da escala."""
    res = bmesh.ops.create_icosphere(bm, subdivisions=sub, radius=1.0)
    R = Matrix.Identity(3) if rot is None else rot
    for v in res["verts"]:
        p = v.co.copy()
        if forma is not None:
            p = Vector(forma(p))
        v.co = R @ Vector((p.x * r * s[0], p.y * r * s[1], p.z * r * s[2])) + Vector(c)
    return res["verts"]


def quadrado(p, e=0.35):
    """Esfera → superelipsoide (mais quadrada com `e` pequeno)."""
    return Vector(tuple(math.copysign(abs(t) ** e, t) for t in p))


def torno(bm, perfil, seg=16, c=(0, 0, 0)):
    """Sólido de revolução em Z: `perfil` = [(raio, z), ...] de baixo para cima, fechado nas pontas."""
    aneis = []
    for r, z in perfil:
        aneis.append([bm.verts.new((c[0] + r * math.cos(2 * math.pi * k / seg),
                                    c[1] + r * math.sin(2 * math.pi * k / seg), c[2] + z)) for k in range(seg)])
    for a, b in zip(aneis, aneis[1:]):
        for k in range(seg):
            k1 = (k + 1) % seg
            bm.faces.new((a[k], a[k1], b[k1], b[k]))
    bm.faces.new(list(reversed(aneis[0])))
    bm.faces.new(aneis[-1])


def toro(bm, c, R, r, seg=18, segr=6, M=None, arco=None, listras=0):
    """Toro no plano XY local (M orienta); `arco` < 2π deixa aberto; `listras` = torção em 2 materiais."""
    M = Matrix.Identity(4) if M is None else M
    cheio = arco is None
    arco = 2 * math.pi if cheio else arco
    n = seg if cheio else seg + 1
    aneis = []
    for i in range(n):
        a = arco * i / seg
        anel = []
        for j in range(segr):
            b = 2 * math.pi * j / segr
            p = Vector(((R + r * math.cos(b)) * math.cos(a), (R + r * math.cos(b)) * math.sin(a), r * math.sin(b)))
            anel.append(bm.verts.new(M @ p + Vector(c)))
        aneis.append(anel)
    pares = list(zip(aneis, aneis[1:])) + ([(aneis[-1], aneis[0])] if cheio else [])
    for i, (A, B) in enumerate(pares):
        for j in range(segr):
            j1 = (j + 1) % segr
            f = bm.faces.new((A[j], A[j1], B[j1], B[j]))
            if listras:
                f.material_index = 1 if ((j + i) // listras) % 2 else 0
    if not cheio:
        bm.faces.new(list(reversed(aneis[0])))
        bm.faces.new(aneis[-1])


def cano(bm, pontos, raios, seg=6):
    """Tubo que segue uma linha (talos, fibras, prego torto); fechado nas pontas."""
    pontos = [Vector(p) for p in pontos]
    aneis = []
    for i, p in enumerate(pontos):
        t = (pontos[min(i + 1, len(pontos) - 1)] - pontos[max(i - 1, 0)]).normalized()
        x = Vector(Z).cross(t)
        if x.length < 1e-6:
            x = Vector((0, 1, 0)).cross(t)
        x.normalize()
        y = t.cross(x)
        r = raios[i] if isinstance(raios, (list, tuple)) else raios
        aneis.append([bm.verts.new(p + (x * math.cos(2 * math.pi * k / seg) + y * math.sin(2 * math.pi * k / seg)) * r)
                      for k in range(seg)])
    for a, b in zip(aneis, aneis[1:]):
        for k in range(seg):
            k1 = (k + 1) % seg
            bm.faces.new((a[k], a[k1], b[k1], b[k]))
    bm.faces.new(list(reversed(aneis[0])))
    bm.faces.new(aneis[-1])


def prisma(bm, pts, z0, z1):
    """Polígono (x, y) em sentido anti-horário, extrudado de z0 a z1."""
    b = [bm.verts.new((x, y, z0)) for x, y in pts]
    t = [bm.verts.new((x, y, z1)) for x, y in pts]
    fb = bm.faces.new(list(reversed(b)))
    ft = bm.faces.new(t)
    for i in range(len(pts)):
        j = (i + 1) % len(pts)
        bm.faces.new((b[i], b[j], t[j], t[i]))
    bmesh.ops.triangulate(bm, faces=[fb, ft])


def dentro(x, y, poli):
    """Ponto no polígono (raio)."""
    d = False
    n = len(poli)
    for i in range(n):
        x1, y1 = poli[i]
        x2, y2 = poli[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
            d = not d
    return d


def espelhar(meio):
    """Contorno simétrico: `meio` vai do topo (x = 0) pela direita até a base (x = 0)."""
    return meio + [(-x, y) for x, y in reversed(meio[1:-1])]


def grade(bm, x0, x1, y0, y1, passo, z=None, manter=None, cor=None):
    """Superfície em grade (pano, papel, couro). z(x, y) dá o relevo; manter(x, y) recorta;
    cor(x, y) devolve o índice do material da face (pintura)."""
    nu = max(1, int(round((x1 - x0) / passo)))
    nv = max(1, int(round((y1 - y0) / passo)))
    vs = {}

    def v(i, j):
        if (i, j) not in vs:
            x = x0 + (x1 - x0) * i / nu
            y = y0 + (y1 - y0) * j / nv
            p = z(x, y) if z else 0.0
            vs[i, j] = bm.verts.new(p if isinstance(p, Vector) else (x, y, p))
        return vs[i, j]

    for i in range(nu):
        for j in range(nv):
            cx = x0 + (x1 - x0) * (i + 0.5) / nu
            cy = y0 + (y1 - y0) * (j + 0.5) / nv
            if manter is not None and not manter(cx, cy):
                continue
            f = bm.faces.new((v(i, j), v(i + 1, j), v(i + 1, j + 1), v(i, j + 1)))
            if cor is not None:
                f.material_index = cor(cx, cy) or 0
    return vs


def pintar(bm, fn):
    for f in bm.faces:
        c = f.calc_center_median()
        k = fn(c, f)
        if k:
            f.material_index = k


def faixas(linhas_y, larg, x0, x1, rng, var=0.35, gap=0.0):
    """Linhas de escrita: lista de (y, xa, xb). O fim de cada linha varia."""
    out = []
    for y in linhas_y:
        xb = x1 - (x1 - x0) * rng.uniform(0, var)
        out.append((y, x0, xb))
    return lambda x, y: any(abs(y - ly) < larg / 2 and xa <= x <= xb for ly, xa, xb in out)


# ================================================================== comida e bebida

def pao(el):
    crosta = mat("pao", "terra", k=0.25, ruido=90, luz_min=0.12)
    miolo = mat("pao_corte", "palha", k=0.1, tons=[1, 4])

    def corpo(bm):
        bola(bm, (0, 0, 0), 1.0, sub=4,
             forma=lambda p: Vector((p.x * 0.13, p.y * 0.085, (max(p.z, -0.4) + 0.4) * 0.08)))
        # três talhos na diagonal, só em cima
        pintar(bm, lambda c, f: 1 if c.z > 0.07 and any(abs((c.x - c.y * 0.6) - k * 0.055) < 0.0095
                                                         for k in (-1, 0, 1)) else 0)
    ob("pao", corpo, [crosta, miolo], suave=True)


def sopa(el):
    tigela = mat("tigela", "madeira_clara", k=0.2, ruido=60)
    caldo = mat("caldo", "ferrugem", k=0.15, ruido=120, luz_min=0.3)
    pedaco = mat("sopa_pedaco", "palha", k=0.0, luz_min=0.3)
    colher = mat("colher", "madeira", k=0.1)
    ob("tigela", lambda bm: torno(bm, [(0.045, 0.0), (0.066, 0.01), (0.088, 0.03), (0.101, 0.056), (0.1, 0.066),
                                       (0.091, 0.063), (0.084, 0.05)], seg=24), tigela, suave=True)
    ob("caldo", lambda bm: torno(bm, [(0.086, 0.046), (0.087, 0.054)], seg=24), caldo)

    def pedacos(bm):
        for x, y in ((-0.03, 0.02), (0.02, -0.03), (0.035, 0.03), (-0.01, -0.005)):
            bola(bm, (x, y, 0.055), 0.013, (1, 0.8, 0.45), sub=1)
    ob("sopa_pedacos", pedacos, pedaco)

    def cabo(bm):
        barra(bm, (0.02, 0.01, 0.05), (0.17, 0.07, 0.14), 0.02, 0.009)
    ob("colher", cabo, colher)


def queijo(el):
    casca = mat("queijo_casca", "latao", k=0.2, ruido=50)
    massa = mat("queijo", "palha", k=0.15, ruido=140, tons=[2, 5])
    meio = math.radians(-70)
    abre = math.radians(24)
    pts = [(0.0, 0.0)]
    n = 30
    for i in range(n + 1):
        a = meio + abre + (2 * math.pi - 2 * abre) * i / n
        pts.append((0.12 * math.cos(a), 0.12 * math.sin(a)))

    def roda(bm):
        prisma(bm, pts, 0.0, 0.075)
        # as duas faces do corte (as que encostam no centro) e um filete da massa no topo do corte
        pintar(bm, lambda c, f: 1 if (abs(f.normal.z) < 0.5 and Vector((c.x, c.y)).length < 0.115) else 0)
    ob("queijo", roda, [casca, massa])


def maca(el):
    pele = mat("maca", "tecido_vermelho", k=0.15, ruido=70, luz_min=0.15)
    cabo = mat("maca_cabo", "madeira", k=0.0)
    folha = mat("maca_folha", "grama", k=0.0)

    def fruta(p):
        r2 = p.x * p.x + p.y * p.y
        z = p.z * 0.9 - 0.32 * math.exp(-r2 / 0.05) * (1 if p.z > 0 else 0.5)
        return Vector((p.x, p.y, z))
    ob("maca", lambda bm: bola(bm, (0, 0, 0.036), 0.04, sub=3, forma=fruta), pele, suave=True)
    ob("maca_cabo", lambda bm: barra(bm, (0, 0, 0.03), (0.008, 0.0, 0.062), 0.008, 0.008), cabo)
    ob("maca_folha", lambda bm: bola(bm, (0.024, -0.006, 0.058), 0.022, (1.0, 0.45, 0.12), sub=2,
                                     rot=Matrix.Rotation(math.radians(25), 3, "Y")), folha, suave=True)


def biscoito(el):
    massa = mat("biscoito", "palha", k=0.3, ruido=160, tons=[1, 5])
    furo = mat("biscoito_furo", "terra_seca", k=0.0, tons=[0, 2])
    camadas = ((0.0, 0.0, 0.0, 0), (0.01, 0.006, 0.016, 14), (-0.004, -0.002, 0.032, -9))

    def pilha_fina(bm):
        # topo subdividido para os furos caberem em faces pequenas
        for x, y, z, g in camadas:
            R = Matrix.Rotation(math.radians(g), 4, "Z")
            vs = grade(bm, -0.0375, 0.0375, -0.0375, 0.0375, 0.0042, z=lambda u, v: 0.0)
            topo = list(vs.values())
            for vv in topo:
                vv.co = R @ vv.co + Vector((x, y, z + 0.0155))
            caixa(bm, (x, y, z + 0.0077), (0.0749, 0.0749, 0.0152), giro=g)
        x, y, z, g = camadas[-1]
        R = Matrix.Rotation(math.radians(g), 4, "Z")
        furos = [(R @ Vector((i * 0.022, j * 0.022, 0))) + Vector((x, y, 0)) for i in (-1, 0, 1) for j in (-1, 0, 1)]
        pintar(bm, lambda c, f: 1 if c.z > 0.046 and abs(f.normal.z) > 0.9 and
               any((Vector((c.x, c.y, 0)) - p).length < 0.0055 for p in furos) else 0)
    ob("biscoito", pilha_fina, [massa, furo])


def carne_salgada(el):
    carne = mat("carne", "cabelo_ruivo", k=0.3, ruido=70, luz_min=0.1)
    sal = mat("carne_sal", "flor_branca", k=0.0)
    osso = mat("osso", "linho", k=0.1, tons=[1, 5])
    fio = mat("carne_fio", "colmo", k=0.0)

    def naco(bm):
        bola(bm, (0, 0, 0.032), 1.0, sub=4,
             forma=lambda p: Vector((quadrado(p, 0.55).x * 0.085, quadrado(p, 0.55).y * 0.055,
                                     quadrado(p, 0.55).z * 0.032)))
        pintar(bm, lambda c, f: 1 if f.normal.z > 0.3 and noise.noise(c * 70.0) > 0.28 else 0)
    ob("carne", naco, [carne, sal], suave=True)

    def ossinho(bm):
        tubo(bm, (0.07, 0.0, 0.03), (0.13, 0.012, 0.034), 0.011, seg=8)
        bola(bm, (0.135, 0.004, 0.034), 0.013, sub=1)
        bola(bm, (0.132, 0.022, 0.034), 0.012, sub=1)
    ob("osso", ossinho, osso, suave=True)
    ob("carne_fio", lambda bm: toro(bm, (-0.02, 0, 0.032), 0.04, 0.0045, seg=16, segr=4,
                                     M=Matrix(((0, 0, 1, 0), (0, 1.45, 0, 0), (1, 0, 0, 0), (0, 0, 0, 1)))
                                     @ Matrix.Scale(1.0, 4)), fio)


def legumes(el):
    cenoura = mat("cenoura", "ferrugem", k=0.15, ruido=90)
    nabo = mat("nabo", "linho", k=0.1, tons=[1, 5])
    nabo_topo = mat("nabo_topo", "flor_violeta", k=0.0)
    rama = mat("rama", "grama", k=0.15)

    def cenouras(bm):
        for (a, b) in (((-0.09, -0.04, 0.015), (0.06, 0.02, 0.018)), ((-0.07, 0.035, 0.016), (0.07, 0.05, 0.02))):
            a, b = Vector(a), Vector(b)
            pts = [a.lerp(b, t) for t in (0.0, 0.25, 0.5, 0.75, 0.95, 1.0)]
            cano(bm, pts, [0.003, 0.011, 0.016, 0.019, 0.02, 0.015], seg=8)
    ob("cenoura", cenouras, cenoura, suave=True)

    def nabos(bm):
        bola(bm, (0.0, -0.035, 0.032), 0.032, (1, 1, 0.95), sub=3)
        cano(bm, [(0.0, -0.035, 0.005), (-0.015, -0.05, 0.002), (-0.035, -0.058, 0.002)], [0.006, 0.003, 0.001])
        pintar(bm, lambda c, f: 1 if c.z > 0.045 else 0)
    ob("nabo", nabos, [nabo, nabo_topo], suave=True)

    def ramas(bm):
        for base, d in (((0.06, 0.02, 0.02), (1, 0.15)), ((0.07, 0.05, 0.022), (1, 0.4)),
                        ((0.0, -0.035, 0.06), (0.4, 1.0))):
            for k in (-1, 0, 1):
                ang = math.atan2(d[1], d[0]) + k * 0.45
                fim = Vector(base) + Vector((math.cos(ang), math.sin(ang), 0.5)) * 0.055
                barra(bm, base, fim, 0.009, 0.003)
    ob("rama", ramas, rama)


def cerveja(el):
    aduela = mat("caneca", "madeira_clara", k=0.35, ruido=1.0, ruido_eixos=(140, 140, 6))
    aro = mat("caneca_aro", "ferro", k=0.05)
    espuma = mat("espuma", "flor_branca", k=0.1)
    ob("caneca", lambda bm: torno(bm, [(0.042, 0.0), (0.045, 0.004), (0.046, 0.11), (0.042, 0.112)], seg=16),
       aduela)

    def aros(bm):
        for z in (0.012, 0.092):
            torno(bm, [(0.0475, z), (0.0475, z + 0.008)], seg=16)
        M = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
        toro(bm, (0.062, 0.0, 0.058), 0.03, 0.007, seg=10, segr=5, M=M @ Matrix.Rotation(-math.pi / 2, 4, "Z"),
             arco=math.pi)
    ob("caneca_aros", aros, aro)

    def topo(bm):
        bola(bm, (0, 0, 0.112), 0.046, (1, 1, 0.42), sub=3,
             forma=lambda p: Vector((p.x, p.y, p.z + 0.12 * noise.noise(p * 3.1))))
        bola(bm, (-0.034, -0.03, 0.09), 0.011, (1, 0.8, 1.6), sub=1)   # escorrendo pela borda
    ob("espuma", topo, espuma, suave=True)


def cha_ervas(el):
    barro = mat("xicara", "reboco", k=0.1)
    cha = mat("cha", "tecido_oliva", k=0.0, luz_min=0.35)
    folha = mat("cha_folha", "grama", k=0.0)
    ob("pires", lambda bm: torno(bm, [(0.035, 0.0), (0.066, 0.004), (0.078, 0.012), (0.074, 0.013), (0.05, 0.007)],
                                 seg=24), barro, suave=True)
    ob("xicara", lambda bm: torno(bm, [(0.025, 0.007), (0.032, 0.012), (0.043, 0.034), (0.047, 0.056),
                                       (0.044, 0.058), (0.040, 0.046)], seg=20), barro, suave=True)
    ob("cha", lambda bm: torno(bm, [(0.0415, 0.045), (0.0415, 0.049)], seg=20), cha)

    def asa(bm):
        M = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
        toro(bm, (0.05, 0.0, 0.034), 0.014, 0.004, seg=8, segr=4, M=M @ Matrix.Rotation(-math.pi / 2, 4, "Z"),
             arco=math.pi)
    ob("xicara_asa", asa, barro)

    def raminho(bm):
        cano(bm, [(-0.02, 0.01, 0.05), (-0.045, 0.0, 0.058), (-0.07, -0.02, 0.03), (-0.072, -0.03, 0.014)], 0.0018,
             seg=4)
        for p, g in (((-0.035, 0.012, 0.053), 20), ((-0.058, -0.008, 0.05), -30), ((-0.072, -0.03, 0.02), 60)):
            bola(bm, p, 0.011, (1, 0.5, 0.2), sub=1, rot=Matrix.Rotation(math.radians(g), 3, "Z"))
    ob("cha_folha", raminho, folha)


def unguento_sal(el):
    pote = mat("pote", "pedra", k=0.15, ruido=80)
    pasta = mat("unguento", "la_crua", k=0.2, ruido=120, tons=[1, 4])
    tampa = mat("pote_tampa", "madeira_clara", k=0.1)
    # potinho alto, de boca estreita e gargalo de corda (a tigela baixa confundia com a sopa)
    ob("pote", lambda bm: torno(bm, [(0.03, 0.0), (0.038, 0.008), (0.042, 0.04), (0.036, 0.062), (0.03, 0.068),
                                     (0.033, 0.074), (0.029, 0.077), (0.026, 0.07)], seg=20), pote, suave=True)
    ob("pote_corda", lambda bm: toro(bm, (0, 0, 0.066), 0.031, 0.0035, seg=16, segr=4),
       mat("pote_corda", "palha", k=0.0, tons=[1, 4]))

    def massa(bm):
        bola(bm, (0, 0, 0.071), 0.027, (1, 1, 0.3), sub=3)
        tubo(bm, (0.0, 0.0, 0.076), (0.004, 0.002, 0.088), 0.009, 0.002, seg=8)
    ob("unguento", massa, pasta, suave=True)

    # a espátula de madeira enfiada na pasta (a tampa solta lia como um pão ao lado)
    ob("pote_tampa", lambda bm: barra(bm, (-0.004, 0.004, 0.072), (0.04, -0.016, 0.12), 0.012, 0.004), tampa)


# ================================================================== materiais e mercadorias

def sal_medicinal(el):
    saco = mat("saquinho", "tecido_azul", k=0.3, textura="linho.png", escala=30, luz_min=0.15)
    fio = mat("saquinho_fio", "tecido_vermelho", k=0.0)
    sal = mat("sal", "pedra_clara", k=0.35, ruido=260)

    def corpo(bm):
        def forma(p):
            # pano mole: assentado (largo embaixo) e franzido para o nó, com pregas
            pesc = 1.0 - 0.72 * max(0.0, p.z - 0.1) / 0.9
            base = 1.0 + 0.18 * max(0.0, -p.z)
            prega = 1.0 + 0.07 * math.sin(math.atan2(p.y, p.x) * 7) * max(0.0, p.z)
            return Vector((p.x * pesc * base * prega, p.y * pesc * base * prega, max(p.z, -0.75)))
        bola(bm, (0, 0.02, 0.036), 0.05, (1.05, 0.95, 0.85), sub=3, forma=forma)
        # boca franzida acima do nó, abrindo em babado
        torno(bm, [(0.011, 0.078), (0.02, 0.088), (0.034, 0.1), (0.04, 0.106), (0.03, 0.104), (0.012, 0.094)],
              seg=14, c=(0, 0.02, 0))
        for v in bm.verts:
            if v.co.z > 0.09:
                v.co.z += 0.005 * math.sin(math.atan2(v.co.y - 0.02, v.co.x) * 6)
    ob("saquinho", corpo, saco, suave=True)
    def fio_(bm):
        toro(bm, (0, 0.02, 0.081), 0.0135, 0.0045, seg=12, segr=4)
        cano(bm, [(0.012, 0.012, 0.08), (0.03, 0.0, 0.07), (0.036, -0.004, 0.052)], 0.003, seg=4)
    ob("saquinho_fio", fio_, fio)

    def monte(bm):
        # montinho redondo, de pedrinhas, derramado à frente
        rng = random.Random(3)
        for _ in range(26):
            a, r = rng.uniform(0, 2 * math.pi), 0.03 * math.sqrt(rng.random())
            x, y = 0.045 + r * math.cos(a) * 1.2, -0.05 + r * math.sin(a)
            h = 0.022 * (1 - (r / 0.03) ** 2)
            caixa(bm, (x, y, h * rng.uniform(0.4, 1.0)), (0.011, 0.011, 0.011), giro=rng.uniform(0, 90))
    ob("sal", monte, sal, suave=False)


def farinha(el):
    saco = mat("saco", "terra_seca", k=0.3, textura="linho.png", escala=10)
    po = mat("farinha", "flor_branca", k=0.15, ruido=90)

    def corpo(bm):
        def forma(p):
            q = quadrado(p, 0.7)
            barriga = 1.0 + 0.12 * (1 - q.z * q.z)
            return Vector((q.x * barriga, q.y * barriga, q.z))
        bola(bm, (0, 0, 0.1), 1.0, (0.12, 0.09, 0.1), sub=4, forma=forma)
        toro(bm, (0, 0, 0.198), 0.098, 0.016, seg=24, segr=6, M=Matrix.Diagonal((1.0, 0.75, 1.0, 1.0)))
    ob("saco", corpo, saco, suave=True)

    def monte(bm):
        bola(bm, (0, 0, 0.2), 0.092, (1.0, 0.72, 0.32), sub=3)
        bola(bm, (0.12, -0.07, 0.0), 0.04, (1.2, 0.8, 0.28), sub=2, forma=lambda p: Vector((p.x, p.y, max(p.z, 0))))
    ob("farinha", monte, po, suave=True)


def ferro(el):
    metal = mat("barra_ferro", "ferro", k=0.35, ruido=60)
    escama = mat("escama", "ferrugem", k=0.0, tons=[0, 3])

    def barras2(bm):
        for c, g in (((0.0, 0.0, 0.0), 0.0), ((0.012, 0.018, 0.031), 28.0)):
            antes = set(bm.verts)
            res = bmesh.ops.create_cube(bm, size=1.0)
            novas = [e for e in bm.edges if not (set(e.verts) & antes)]
            bmesh.ops.subdivide_edges(bm, edges=novas, cuts=4, use_grid_fill=True)
            R = Matrix.Rotation(math.radians(g), 4, "Z")
            for v in [v for v in bm.verts if v not in antes]:
                x, y, z = v.co
                t = z + 0.5                      # 0 embaixo, 1 em cima: o topo é mais estreito
                v.co = R @ Vector((x * 0.2 * (1 - 0.14 * t), y * 0.05 * (1 - 0.3 * t), t * 0.03)) + Vector(c)
        pintar(bm, lambda c, f: 1 if noise.noise(c * 55.0 + Vector((3.1, 0.7, 0))) > 0.42 else 0)
    ob("barra_ferro", barras2, [metal, escama])


def pregos(el):
    metal = mat("prego", "cabelo_grisalho", k=0.05, tons=[0, 3])
    rng = random.Random(el.get("semente", 5))

    def punhado(bm):
        for i in range(5):
            ang = rng.uniform(0, math.pi)
            c = Vector((rng.uniform(-0.025, 0.025), rng.uniform(-0.02, 0.02), 0.004 + i * 0.0045))
            d = Vector((math.cos(ang), math.sin(ang), 0))
            a, b = c - d * 0.035, c + d * 0.035
            if i in (2, 5):   # dois tortos
                m = a.lerp(b, 0.6) + Vector((0, 0, 0.006))
                b = m + (b - a).normalized().lerp(Vector((-d.y, d.x, 0)), 0.6).normalized() * 0.03
                cano(bm, [a, m, b], [0.0028, 0.0028, 0.002], seg=4)
            else:
                cano(bm, [a, a.lerp(b, 0.85), b], [0.0028, 0.0026, 0.0008], seg=4)
            M, _ = _frame(a - d * 0.002, a + d * 0.002)
            res = bmesh.ops.create_cone(bm, cap_ends=True, segments=8, radius1=0.0068, radius2=0.0068, depth=0.003)
            _aplicar(res["verts"], M)
    ob("pregos", punhado, metal)


def lenha(el):
    casca = mat("lenha_casca", "casca_carvalho", k=0.45, ruido=1.0, ruido_eixos=(4, 90, 90))
    cerne = mat("lenha_cerne", "palha", k=0.2, tons=[2, 5], luz_min=0.4)
    corda = mat("lenha_corda", "palha", k=0.1, tons=[0, 3])

    def toras(bm):
        for y, z, r, dx in ((-0.036, 0.036, 0.036, 0.0), (0.036, 0.034, 0.034, 0.012), (0.0, 0.096, 0.033, -0.01)):
            tubo(bm, (-0.15 + dx, y, z), (0.15 + dx, y, z), r, seg=7)
        pintar(bm, lambda c, f: 1 if abs(f.normal.x) > 0.9 else 0)
    ob("lenha", toras, [casca, cerne])

    def amarras(bm):
        M = Matrix(((0, 0, 1, 0), (0, 1, 0, 0), (1, 0, 0, 0), (0, 0, 0, 1)))
        for x in (-0.08, 0.08):
            toro(bm, (x, 0.0, 0.058), 0.074, 0.006, seg=16, segr=4, M=M @ Matrix.Diagonal((0.95, 1.05, 1, 1)))
    ob("lenha_corda", amarras, corda)


def couro(el):
    # peça curtida enrolada: o rolo, a ponta solta deitada à frente, a espiral na boca e uma tira
    pele = mat("couro_peca", "couro", k=0.25, textura="couro.png", escala=8, tons=[1, 4])
    carnal = mat("couro_carnal", "terra_seca", k=0.1, tons=[1, 4])
    tira = mat("couro_tira", "madeira_escura", k=0.0)
    R, L = 0.045, 0.2

    def rolo(bm):
        tubo(bm, (-L / 2, 0.0, R), (L / 2, 0.0, R), R, seg=18)
        # a ponta solta, desenrolada para a frente
        grade(bm, -L / 2 + 0.004, L / 2 - 0.004, -0.13, 0.0, 0.006,
              z=lambda x, y: Vector((x + 0.01 * math.sin(y * 40) * (y < -0.09), y,
                                     0.002 + 0.004 * noise.noise(Vector((x * 20, y * 20, 2.0))))),
              manter=lambda x, y: y > -0.115 - 0.012 * math.sin(x * 30))
    ob("couro", rolo, pele, suave=False)

    def espiral(bm):
        # a boca do rolo que fica para a câmera: as voltas do lado de dentro, mais claro
        pts = []
        for i in range(60):
            t = i / 59
            a = t * 2.6 * 2 * math.pi
            r = 0.006 + (R - 0.01) * t
            pts.append((-L / 2 - 0.002, r * math.cos(a), R + r * math.sin(a)))
        cano(bm, pts, 0.0032, seg=4)
    ob("couro_carnal", espiral, carnal)

    def amarra(bm):
        M = Matrix(((0, 0, 1, 0), (0, 1, 0, 0), (1, 0, 0, 0), (0, 0, 0, 1)))
        toro(bm, (0.035, 0.0, R), R + 0.002, 0.005, seg=18, segr=4, M=M)
    ob("couro_tira", amarra, tira)


def corda(el):
    fibra = mat("corda", "colmo", k=0.1, tons=[1, 5])
    sombra = mat("corda_torcao", "colmo", k=0.0, tons=[0, 3])

    def rolo(bm):
        M = Matrix.Diagonal((1.0, 1.0, 1.0, 1.0))
        for i, (r, z) in enumerate(((0.085, 0.014), (0.079, 0.038), (0.073, 0.06), (0.066, 0.08))):
            toro(bm, (0.004 * i, 0.003 * i, z), r, 0.014, seg=40, segr=6, M=M, listras=1)
        cano(bm, [(0.06, -0.06, 0.012), (0.09, -0.09, 0.01), (0.13, -0.1, 0.008), (0.16, -0.085, 0.007)], 0.012,
             seg=6)
    ob("corda", rolo, [fibra, sombra], suave=True)


def ervas(el):
    caule = mat("ervas_caule", "grama_seca", k=0.0)
    folha = mat("ervas_folha", "tecido_oliva", k=0.15, ruido=60)
    flor = mat("ervas_flor", "flor_violeta", k=0.0)
    amarra = mat("ervas_amarra", "tecido_vermelho", k=0.0)
    rng = random.Random(9)
    pontas = []

    def caules(bm):
        for i in range(7):
            ang = math.radians(-30 + i * 10 + rng.uniform(-3, 3))
            fim = Vector((math.cos(ang), math.sin(ang), 0)) * rng.uniform(0.17, 0.21) + Vector((0, 0, 0.01))
            ini = Vector((-0.05 * math.cos(ang * 0.4), -0.05 * math.sin(ang * 0.4), 0.008))
            cano(bm, [ini, fim * 0.5 + Vector((0, 0, 0.006)), fim], 0.0026, seg=4)
            pontas.append(fim)
    ob("ervas_caule", caules, caule)

    def folhas(bm):
        for fim in pontas:
            d = fim.normalized()
            for t in (0.45, 0.65, 0.82):
                for s in (-1, 1):
                    p = fim * t + Vector((-d.y, d.x, 0)) * s * 0.008 + Vector((0, 0, 0.01))
                    g = math.atan2(d.y, d.x) + s * 0.7
                    bola(bm, p, 0.013, (1.0, 0.42, 0.25), sub=1, rot=Matrix.Rotation(g, 3, "Z"))
    ob("ervas_folha", folhas, folha, suave=True)

    def flores(bm):
        for fim in pontas[::2]:
            bola(bm, fim + Vector((0, 0, 0.006)), 0.011, (1.4, 0.8, 0.7), sub=1,
                 rot=Matrix.Rotation(math.atan2(fim.y, fim.x), 3, "Z"))
    ob("ervas_flor", flores, flor, suave=True)
    M = Matrix(((0, 0, 1, 0), (0, 1, 0, 0), (1, 0, 0, 0), (0, 0, 0, 1)))
    ob("ervas_amarra", lambda bm: toro(bm, (0.0, 0.0, 0.01), 0.016, 0.005, seg=12, segr=4, M=M), amarra)


def oleo(el):
    vidro = mat("garrafa", "agua", k=0.0, luz_min=0.1)
    oleo_ = mat("oleo", "latao", k=0.05, luz_min=0.2)
    rolha = mat("rolha", "madeira_clara", k=0.1)
    fio = mat("garrafa_fio", "linho", k=0.0, tons=[1, 4])

    def garrafa(bm):
        torno(bm, [(0.02, 0.0), (0.037, 0.004), (0.043, 0.026), (0.04, 0.05), (0.024, 0.068), (0.013, 0.078),
                   (0.012, 0.1), (0.0155, 0.103), (0.0125, 0.107)], seg=18)
        pintar(bm, lambda c, f: 1 if c.z < 0.047 else 0)
    ob("garrafa", garrafa, [vidro, oleo_], suave=True)
    ob("rolha", lambda bm: tubo(bm, (0, 0, 0.1), (0, 0, 0.122), 0.0105, 0.012, seg=10), rolha)
    ob("garrafa_fio", lambda bm: toro(bm, (0, 0, 0.088), 0.0135, 0.003, seg=12, segr=4), fio)


# ================================================================== ferramentas e armas
# (as peças de kit_humano/roupa.py `ferramenta`: cabo de madeira_clara, cabeça de ferro)

def _madeira():
    return mat("cabo", "madeira_clara", k=0.15, ruido=1.0, ruido_eixos=(4, 80, 80))


def _ferro():
    return mat("ferro", "ferro", k=0.05)


def _fio():
    return mat("fio", "cabelo_grisalho", k=0.0, tons=[1, 4])


def martelo(el):
    ob("martelo_cabo", lambda bm: tubo(bm, (-0.16, 0, 0.018), (0.15, 0, 0.018), 0.017, 0.015, seg=10), _madeira(),
       suave=True)

    def cabeca(bm):
        # 0,11 × 0,055 × 0,055 do kit; boca de bater de um lado, pena afinando do outro
        caixa(bm, (0.16, -0.012, 0.022), (0.055, 0.075, 0.05))
        tubo(bm, (0.16, -0.05, 0.022), (0.16, -0.065, 0.022), 0.03, 0.03, seg=8, cima=(1, 0, 0))
        barra(bm, (0.16, 0.025, 0.022), (0.16, 0.065, 0.022), 0.05, 0.012, cima=(1, 0, 0))
    ob("martelo_cabeca", cabeca, _ferro())


def faca(el):
    cabo = mat("faca_cabo", "madeira", k=0.1)
    ob("faca_cabo", lambda bm: tubo(bm, (-0.12, 0, 0.012), (-0.02, 0, 0.012), 0.013, 0.015, seg=8), cabo,
       suave=True)
    ob("faca_guarda", lambda bm: caixa(bm, (-0.015, 0, 0.012), (0.012, 0.048, 0.02)), _ferro())

    def lamina(bm):
        # gume em baixo (y < 0), costas retas em cima
        pts = [(-0.01, 0.012), (0.12, 0.009), (0.155, 0.0), (0.12, -0.012), (0.04, -0.019), (-0.01, -0.016)]
        prisma(bm, pts, 0.008, 0.016)
    ob("faca_lamina", lamina, _fio())
    ob("faca_dorso", lambda bm: barra(bm, (-0.01, 0.009, 0.0165), (0.12, 0.007, 0.0165), 0.006, 0.002),
       _ferro())


def machadinha(el):
    ob("machado_cabo", lambda bm: cano(bm, [(-0.24, 0.0, 0.015), (0.0, 0.006, 0.015), (0.17, 0.0, 0.015),
                                            (0.2, -0.004, 0.015)], [0.016, 0.015, 0.016, 0.017], seg=10),
       _madeira(), suave=True)

    def cabeca(bm):
        pts = [(0.15, -0.035), (0.21, -0.035), (0.215, 0.03), (0.25, 0.1), (0.24, 0.125), (0.14, 0.13),
               (0.12, 0.11), (0.155, 0.03)]
        prisma(bm, pts, 0.003, 0.03)
    ob("machado_cabeca", cabeca, _ferro())
    ob("machado_gume", lambda bm: prisma(bm, [(0.125, 0.112), (0.24, 0.108), (0.245, 0.124), (0.14, 0.131)],
                                         0.004, 0.031), _fio())


def cajado(el):
    ob("cajado", lambda bm: cano(bm, [(-0.5, 0.0, 0.03), (-0.1, 0.012, 0.03), (0.25, -0.005, 0.03),
                                      (0.44, 0.012, 0.03)], [0.03, 0.032, 0.034, 0.036], seg=10),
       _madeira(), suave=True)
    ob("cajado_no", lambda bm: bola(bm, (0.48, 0.016, 0.034), 0.058, (1.15, 1.0, 1.0), sub=3,
                                    forma=lambda p: p * (1 + 0.22 * noise.noise(p * 2.3))),
       mat("cajado_no", "madeira", k=0.2, ruido=40), suave=True)
    ob("cajado_couro", lambda bm: tubo(bm, (0.08, 0.004, 0.03), (0.22, 0.0, 0.03), 0.039, seg=10),
       mat("empunhadura", "couro", k=0.1, tons=[1, 4]), suave=True)
    ob("cajado_ponteira", lambda bm: tubo(bm, (-0.53, 0.0, 0.03), (-0.47, 0.0, 0.03), 0.031, 0.034, seg=10),
       _ferro())


def lanca_curta(el):
    ob("lanca_haste", lambda bm: tubo(bm, (-0.52, 0, 0.03), (0.26, 0, 0.03), 0.028, 0.031, seg=10), _madeira(),
       suave=True)
    ob("lanca_amarra", lambda bm: tubo(bm, (0.2, 0, 0.03), (0.27, 0, 0.03), 0.036, seg=10),
       mat("lanca_amarra", "couro", k=0.0, tons=[1, 4]))
    ob("lanca_alvado", lambda bm: tubo(bm, (0.26, 0, 0.03), (0.33, 0, 0.03), 0.031, 0.022, seg=10), _ferro())

    def ponta(bm):
        pts = [(0.31, 0.0), (0.38, -0.048), (0.55, 0.0), (0.38, 0.048)]
        prisma(bm, pts, 0.022, 0.038)
    ob("lanca_ponta", ponta, _fio())
    ob("lanca_nervura", lambda bm: barra(bm, (0.32, 0, 0.039), (0.52, 0, 0.039), 0.008, 0.003), _ferro())
    ob("lanca_conto", lambda bm: tubo(bm, (-0.55, 0, 0.03), (-0.5, 0, 0.03), 0.03, seg=10), _ferro())


def lamparina(el):
    lata = mat("lamparina", "ferro", k=0.05)
    # o vidro aceso: no ícone a chama é o que diz "lamparina"
    vidro = mat("lamparina_vidro", "fogo", k=0.0, luz_min=0.35, oa=0.0)
    ob("lamparina_base", lambda bm: torno(bm, [(0.05, 0.0), (0.056, 0.006), (0.056, 0.026), (0.048, 0.032)], seg=16),
       lata)
    ob("lamparina_vidro", lambda bm: torno(bm, [(0.042, 0.03), (0.046, 0.075), (0.042, 0.122)], seg=16), vidro,
       suave=True)

    def gaiola(bm):
        for k in range(4):
            a = math.radians(45 + 90 * k)
            x, y = 0.048 * math.cos(a), 0.048 * math.sin(a)
            barra(bm, (x, y, 0.03), (x * 0.95, y * 0.95, 0.122), 0.007, 0.007, cima=(x, y, 0))
        torno(bm, [(0.05, 0.118), (0.056, 0.124), (0.03, 0.15), (0.016, 0.158), (0.014, 0.164)], seg=16)
        M = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
        toro(bm, (0, 0, 0.162), 0.032, 0.0045, seg=14, segr=4, M=M, arco=math.pi)
    ob("lamparina_gaiola", gaiola, lata)


# ================================================================== roupa

def _pano(nome, contorno, material, passo=0.008, ondas=0.006, cor=None, mats_extra=()):
    def relevo(x, y):
        return ondas * (noise.noise(Vector((x * 7, y * 7, 0.5))) + 0.5 * math.sin(x * 25 + y * 9))

    def construir(bm):
        xs = [p[0] for p in contorno]
        ys = [p[1] for p in contorno]
        grade(bm, min(xs), max(xs), min(ys), max(ys), passo, z=relevo, manter=lambda x, y: dentro(x, y, contorno),
              cor=cor)
    return ob(nome, construir, [material, *mats_extra], suave=True)


def camisa_linho(el):
    linho = mat("camisa", "linho", k=0.2, textura="linho.png", escala=8, tons=[1, 5], luz_min=0.15)
    remendo = mat("remendo", "la_crua", k=0.1, tons=[1, 4])
    vao = mat("camisa_vao", "linho", k=0.0, tons=[0, 1])
    contorno = espelhar([(0.0, 0.25), (0.06, 0.27), (0.15, 0.25), (0.36, 0.1), (0.31, 0.02), (0.15, 0.12),
                         (0.16, -0.27), (0.0, -0.28)])

    def cor(x, y):
        if (x / 0.06) ** 2 + ((y - 0.265) / 0.035) ** 2 < 1:
            return 2   # gola
        if abs(x) < 0.008 and 0.17 < y < 0.24:
            return 2   # abertura do peito
        for s in (-1, 1):
            if abs(x - s * 0.27) < 0.03 and abs(y - 0.11 - s * 0.0) < 0.025 and abs((x * s) - 0.27) < 0.03:
                return 1   # remendos nos cotovelos
        return 0
    _pano("camisa", contorno, linho, cor=cor, mats_extra=(remendo, vao))


def gibao_couro(el):
    couro_ = mat("gibao", "couro", k=0.2, textura="couro.png", escala=6)
    costura = mat("gibao_costura", "madeira_escura", k=0.0, tons=[0, 2])
    botao = mat("botao", "latao", k=0.0)
    contorno = espelhar([(0.0, 0.24), (0.07, 0.26), (0.17, 0.25), (0.26, 0.17), (0.22, 0.1), (0.17, 0.13),
                         (0.18, -0.2), (0.12, -0.27), (0.0, -0.25)])

    def cor(x, y):
        if abs(x) < 0.006:
            return 1   # frente fechada
        if abs(abs(x) - 0.17) < 0.006 and y > 0.12:
            return 1   # costura do ombro
        if abs(x) < 0.17 and any(abs(y - k) < 0.004 for k in (-0.17, -0.09, -0.01, 0.07)):
            return 1   # acolchoado
        return 0
    _pano("gibao", contorno, couro_, cor=cor, mats_extra=(costura,))

    def botoes(bm):
        for y in (0.17, 0.11, 0.05, -0.01, -0.07, -0.13):
            bola(bm, (0.022, y, 0.012), 0.011, (1, 1, 0.6), sub=1)
    ob("botoes", botoes, botao)


def avental_couro(el):
    couro_ = mat("avental", "terra", k=0.25, textura="couro.png", escala=5)
    tira = mat("avental_tira", "couro", k=0.0, tons=[0, 3])
    contorno = espelhar([(0.0, 0.28), (0.08, 0.28), (0.09, 0.08), (0.16, 0.05), (0.2, -0.36), (0.0, -0.38)])

    def cor(x, y):
        if -0.2 < y < -0.08 and abs(x) < 0.11 and (abs(abs(x) - 0.11) < 0.008 or abs(y + 0.08) < 0.008
                                                   or abs(y + 0.2) < 0.008 or abs(x) < 0.005):
            return 1   # bolso duplo costurado
        return 0
    _pano("avental", contorno, couro_, ondas=0.004, cor=cor, mats_extra=(tira,))

    def tiras(bm):
        toro(bm, (0, 0.28, 0.008), 0.08, 0.008, seg=14, segr=4, M=Matrix.Diagonal((1.0, 0.9, 0.4, 1.0)),
             arco=math.pi)
        for s in (-1, 1):
            cano(bm, [(s * 0.15, 0.05, 0.008), (s * 0.24, 0.02, 0.006), (s * 0.3, -0.06, 0.004)], 0.008, seg=4)
    ob("avental_tiras", tiras, tira)


def chapeu_feltro(el):
    feltro = mat("feltro", "tecido_cinza", k=0.15, ruido=90, luz_min=0.1)
    fita = mat("chapeu_fita", "couro", k=0.0)

    def aba(bm):
        torno(bm, [(0.08, 0.026), (0.15, 0.016), (0.2, 0.0), (0.208, 0.006), (0.15, 0.024), (0.08, 0.034)], seg=32)
    ob("aba", aba, feltro, suave=True)

    def copa(bm):
        torno(bm, [(0.088, 0.02), (0.09, 0.08), (0.082, 0.112), (0.058, 0.126), (0.02, 0.13)], seg=24)
        for v in bm.verts:     # vinco no alto, de frente para trás
            if v.co.z > 0.1:
                v.co.z -= 0.022 * math.exp(-(v.co.x / 0.03) ** 2) * (v.co.z - 0.1) / 0.03
    ob("copa", copa, feltro, suave=True)
    ob("chapeu_fita", lambda bm: torno(bm, [(0.0915, 0.028), (0.0915, 0.048)], seg=24), fita)


def capuz_la(el):
    la = mat("capuz", "tecido_oliva", k=0.25, textura="linho.png", escala=8, luz_min=0.12)
    vao = mat("capuz_vao", "quase_preto", k=0.0, contorno=True)

    def capuz(bm):
        def forma(p):
            x, y, z = p
            if y > 0 and z > -0.2:      # bico para trás e para cima
                y += 0.5 * y * max(0.0, z + 0.2)
                z += 0.35 * max(0.0, y - 0.3) * max(0.0, z + 0.2)
            return Vector((x, y, z))
        bola(bm, (0, 0, 0.2), 0.11, (0.95, 1.05, 1.15), sub=4, forma=forma)
        # rosto: oval da frente afundado e escuro
        for v in bm.verts:
            if v.co.y < -0.02 and (v.co.x / 0.07) ** 2 + ((v.co.z - 0.185) / 0.085) ** 2 < 1:
                v.co.y += 0.05 * (1 - (v.co.x / 0.07) ** 2 - ((v.co.z - 0.185) / 0.085) ** 2)
        pintar(bm, lambda c, f: 1 if c.y < 0.0 and (c.x / 0.066) ** 2 + ((c.z - 0.185) / 0.08) ** 2 < 1 else 0)
    ob("capuz", capuz, [la, vao], suave=True)

    def capa(bm):
        torno(bm, [(0.2, 0.0), (0.18, 0.04), (0.12, 0.085), (0.08, 0.11), (0.07, 0.115)], seg=28)
        for v in bm.verts:
            v.co.z += 0.008 * math.sin(math.atan2(v.co.y, v.co.x) * 7) * (1 - v.co.z / 0.12)
    ob("capuz_capa", capa, la, suave=True)


def bolsa_cinto(el):
    couro_ = mat("bolsa", "couro", k=0.2, textura="couro.png", escala=12, tons=[1, 4])
    aba_ = mat("bolsa_aba", "couro", k=0.15, textura="couro.png", escala=12)
    cinto = mat("cinto", "madeira_escura", k=0.1)
    fivela = mat("fivela", "latao", k=0.0)

    def corpo(bm):
        bola(bm, (0, 0, 0.07), 1.0, (0.08, 0.035, 0.07), sub=4, forma=lambda p: quadrado(p, 0.5))
    ob("bolsa", corpo, couro_, suave=True)

    def aba(bm):
        pts = [(-0.084, 0.0), (0.084, 0.0), (0.084, -0.045), (0.06, -0.07), (0.0, -0.08), (-0.06, -0.07),
               (-0.084, -0.045)]
        prisma(bm, pts, 0.0, 0.007)
        R = Matrix.Translation((0, -0.04, 0.142)) @ Matrix.Rotation(math.radians(-90), 4, "X") \
            @ Matrix.Rotation(math.radians(0), 4, "Z")
        _aplicar(bm.verts, R)
        # parte de cima da aba, deitada sobre a boca
        caixa(bm, (0, 0.0, 0.142), (0.168, 0.08, 0.007))
    ob("bolsa_aba", aba, aba_)

    def presilha(bm):
        caixa(bm, (0, -0.046, 0.075), (0.03, 0.006, 0.022))
    ob("fivela", presilha, fivela)

    def tira(bm):
        caixa(bm, (0, 0.045, 0.125), (0.34, 0.012, 0.05))
    ob("cinto", tira, cinto)


def pele_lobo(el):
    pelo = mat("pele_lobo", "pelo_lobo_claro", k=0.55, ruido=1.0, ruido_eixos=(30, 120, 30), luz_min=0.1)
    dorso = mat("pele_lobo_dorso", "pelo_lobo", k=0.5, ruido=1.0, ruido_eixos=(30, 120, 30))
    meio = [(0.0, 0.31), (0.05, 0.26), (0.08, 0.2), (0.1, 0.17), (0.2, 0.22), (0.25, 0.17), (0.14, 0.09),
            (0.13, -0.05), (0.15, -0.09), (0.27, -0.13), (0.26, -0.2), (0.12, -0.18), (0.05, -0.22),
            (0.035, -0.3), (0.02, -0.42), (0.0, -0.45)]
    contorno = espelhar(meio)

    def manter(x, y):
        f = 1 + 0.08 * noise.noise(Vector((x * 30, y * 30, 2.0)))
        return dentro(x * f, y, contorno)

    def construir(bm):
        grade(bm, -0.28, 0.28, -0.46, 0.32, 0.008,
              z=lambda x, y: 0.006 * noise.noise(Vector((x * 8, y * 8, 0.1))) + 0.012 * (1 - min(1, abs(x) / 0.14)),
              manter=manter,
              cor=lambda x, y: 1 if abs(x) < 0.04 + 0.015 * math.sin(y * 30) and -0.35 < y < 0.28 else 0)
    ob("pele_lobo", construir, [pelo, dorso], suave=True)


# ================================================================== da estrutura (sem explicação)

def lasca_fibra(el):
    fibra = mat("fibra", "ardosia", k=0.15)
    fibra2 = mat("fibra_b", "agua", k=0.1)
    corte = mat("fibra_corte", "la_crua", k=0.2, ruido=200, tons=[1, 4])
    n = 6
    # uma ponta quebrada, grossa e reta; os fios torcidos juntos e, na outra ponta, abrindo em três
    # raízes que se curvam (nada de fuso: o fuso lia como peixe)
    destino = [(0.17, 0.06), (0.19, -0.01), (0.14, -0.07)]

    def fios(bm, par):
        for k in range(n):
            if k % 2 != par:
                continue
            pts, rs = [], []
            dx, dy = destino[k // 2]
            for i in range(19):
                t = i / 18
                a = 2 * math.pi * (k / n + 1.1 * t)
                r = 0.017 * (1 - 0.5 * t)
                x, y, z = -0.13 + 0.2 * t, r * math.cos(a), 0.03 + r * math.sin(a)
                s = max(0.0, (t - 0.5) / 0.5)        # abre depois do meio
                x += (dx - 0.07) * s * s
                y += dy * s * (0.6 + 0.4 * s)
                pts.append((x, y, z - 0.012 * s))
                rs.append(0.0085 * (1 - 0.75 * s))
            cano(bm, pts, rs, seg=5)
    ob("fibra", lambda bm: fios(bm, 0), fibra, suave=True)
    ob("fibra_b", lambda bm: fios(bm, 1), fibra2, suave=True)
    ob("fibra_corte", lambda bm: tubo(bm, (-0.134, 0, 0.03), (-0.128, 0, 0.03), 0.021, seg=10, cima=(0, 1, 0)),
       corte)


def placa_simbolos(el):
    pedra = mat("placa", "pedra_clara", k=0.06, ruido=40)
    risco = mat("placa_risco", "pedra", k=0.0, tons=[0, 1], contorno=False, linhas=False)
    rodas = [(-0.034, 0.026, 9), (0.034, 0.026, 9), (-0.034, -0.026, 9), (0.034, -0.026, 8)]

    W, H, E = 0.075, 0.06, 0.018
    canto = 0.014
    pts = []
    for cx, cy, a0 in ((W - canto, H - canto, 0), (-W + canto, H - canto, 90), (-W + canto, -H + canto, 180),
                       (W - canto, -H + canto, 270)):
        for i in range(5):
            a = math.radians(a0 + 90 * i / 4)
            pts.append((cx + canto * math.cos(a), cy + canto * math.sin(a)))

    def lousa(bm):
        prisma(bm, pts, 0.0, E - 0.0005)
        # a face de cima numa grade fina, para os riscos (pintura por face)
        grade(bm, -W, W, -H, H, 0.0028, z=lambda x, y: E, manter=lambda x, y: dentro(x, y, pts), cor=marca)

    def marca(x, y):
        if True:
            for x0, y0, n in rodas:
                dx, dy = x - x0, y - y0
                r = math.hypot(dx, dy)
                if 0.008 < r < 0.021:
                    a = (math.atan2(dy, dx) + 2 * math.pi) % (2 * math.pi)
                    k = a / (2 * math.pi) * 9
                    if abs(k - round(k)) < 0.22 and round(k) % 9 < n:
                        return 1
            return 0
    ob("placa", lousa, [pedra, risco], suave=False)


# ================================================================== documentos

def _papel(nome, material, x0, x1, y0, y1, z=None, manter=None, cor=None, extra=(), passo=None):
    passo = passo or min(x1 - x0, y1 - y0) / 44.0

    def construir(bm):
        grade(bm, x0, x1, y0, y1, passo, z=z, manter=manter, cor=cor)
    return ob(nome, construir, [material, *extra], suave=True)


def papeis_avel(el):
    papel = mat("registro", "palha", k=0.1, tons=[1, 5])
    escrita = tinta("tecido_marrom", "tinta")
    carimbo = tinta("tecido_azul", "carimbo_azul")
    dobra = mat("registro_dobra", "palha", k=0.0, tons=[0, 2], contorno=False, linhas=False)
    rng = random.Random(21)
    linhas = faixas([0.045, 0.03, 0.015, -0.0, -0.015, -0.03], 0.0045, -0.05, 0.05, rng)

    def relevo(x, y):     # dobrado em quatro: telhado leve no vinco vertical, vale no horizontal
        return 0.006 - 0.005 * abs(x) / 0.065 + 0.002 * abs(y) / 0.08

    def cor(x, y):
        if abs(x) < 0.0025 or abs(y) < 0.0025:
            return 2
        if (x - 0.035) ** 2 + (y + 0.05) ** 2 < 0.016 ** 2 and (x - 0.035) ** 2 + (y + 0.05) ** 2 > 0.011 ** 2:
            return 3
        if abs(x - 0.035) < 0.004 and abs(y + 0.05) < 0.004:
            return 3
        return 1 if linhas(x, y) else 0
    _papel("registro", papel, -0.065, 0.065, -0.08, 0.08, z=relevo, cor=cor, extra=(escrita, dobra, carimbo))
    # as outras folhas da dobra, aparecendo na borda de baixo e da direita
    _papel("registro_folhas", papel, -0.062, 0.068, -0.084, 0.076, z=lambda x, y: -0.002)


def carta_iria(el):
    papel = mat("carta", "linho", k=0.05, tons=[1, 5], luz_min=0.1)
    escrita = tinta("tecido_cinza", "tinta_cinza")
    rng = random.Random(7)
    ang = math.radians(58)
    linhas = faixas([-0.012 - 0.008 * i for i in range(9)], 0.004, -0.07, 0.07, rng, var=0.5)
    linhas2 = faixas([0.075 - 0.008 * i for i in range(8)], 0.004, -0.07, 0.07, rng, var=0.5)

    def relevo(x, y):
        if y <= 0:
            return Vector((x, y, 0.0))
        return Vector((x, -0.0 + y * math.cos(ang) * 0.3 - y * 0.0, y * math.sin(ang))) if False else \
            Vector((x, y * math.cos(ang), y * math.sin(ang)))

    def cor(x, y):
        if y > 0:
            return 1 if linhas2(x, y) else 0
        return 1 if linhas(x, y) else 0
    _papel("carta", papel, -0.08, 0.08, -0.1, 0.1, z=relevo, cor=cor, extra=(escrita,))


def esboco_simbolos(el):
    papel = mat("esboco", "colmo", k=0.0, tons=[2, 5])
    traco = tinta("quase_preto", "carvao")
    gx, gy, R = -0.012, 0.004, 0.045

    def relevo(x, y):   # dobrado muitas vezes: facetas
        return 0.0035 * (abs(((x + 0.1) / 0.05) % 1 - 0.5) + abs(((y + 0.08) / 0.04) % 1 - 0.5))

    def cor(x, y):
        dx, dy = x - gx, y - gy
        r = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        dente = (math.cos(a * 12) > 0.35)
        if abs(r - R) < 0.0035 or (dente and R < r < R + 0.011 and (abs(r - R - 0.011) < 0.0035 or
                                                                     abs(math.sin(a * 12)) > 0.72)):
            return 1
        if abs(r - 0.015) < 0.003 or r < 0.004:
            return 1
        if x > 0.068 and any(abs(y - k) < 0.0025 for k in (0.05, 0.035, 0.02, 0.005)) and x < 0.09:
            return 1   # marcas na margem
        if abs(y + 0.058) < 0.0035 and 0.04 < x < 0.09:
            return 1   # a palavra riscada
        return 0
    _papel("esboco", papel, -0.1, 0.1, -0.08, 0.08, z=relevo, cor=cor, extra=(traco,), passo=0.0035)


def bilhete_avel(el):
    papel = mat("bilhete", "flor_branca", k=0.0)
    escrita = tinta("tecido_marrom", "tinta")
    rng = random.Random(4)
    linhas = faixas([0.07, 0.058, 0.046, 0.025, 0.013], 0.0045, -0.03, 0.03, rng, var=0.45)

    def relevo(x, y):   # dobrado em sanfona, quatro faces
        t = ((y + 0.09) / 0.045) % 2.0
        return 0.022 * (t if t <= 1 else 2 - t)

    def cor(x, y):
        return 1 if linhas(x, y) else 0
    _papel("bilhete", papel, -0.04, 0.04, -0.09, 0.09, z=relevo, cor=cor, extra=(escrita,))


def edital_caldris(el):
    papel = mat("edital", "linho", k=0.05, tons=[2, 5], luz_min=0.15)
    escrita = tinta("quase_preto", "tinta_preta")
    selo = mat("selo", "tecido_vermelho", k=0.0)
    rng = random.Random(12)
    linhas = faixas([0.07 - 0.013 * i for i in range(7)], 0.005, -0.075, 0.075, rng, var=0.25)

    def manter(x, y):   # arrancado: canto de cima à direita rasgado
        rasgo = 0.07 + 0.012 * math.sin(x * 160) + 0.006 * math.sin(x * 420)
        return not (y - 0.13 > -(x - 0.03) * 1.1 + 0.0 and y > rasgo - (x - 0.03) * 1.1 + 0.03) and \
            math.hypot(x, y - 0.115) > 0.005

    def cor(x, y):
        if abs(y - 0.098) < 0.007 and -0.06 < x < 0.03:
            return 1   # título
        if abs(y + 0.08) < 0.003 and -0.075 < x < -0.01:
            return 1   # assinatura
        return 1 if linhas(x, y) else 0
    _papel("edital", papel, -0.09, 0.09, -0.125, 0.125, z=lambda x, y: 0.003 * noise.noise(Vector((x * 9, y * 9, 4))),
           manter=manter, cor=cor, extra=(escrita,))

    def carimbo(bm):
        torno(bm, [(0.025, 0.0), (0.027, 0.004), (0.024, 0.007)], seg=16, c=(0.045, -0.085, 0.0))
        caixa(bm, (0.032, -0.112, 0.002), (0.012, 0.03, 0.002), giro=15)
        caixa(bm, (0.056, -0.112, 0.002), (0.012, 0.03, 0.002), giro=-15)
    ob("selo", carimbo, selo, suave=False)


def tracado_agrimensora(el):
    papel = mat("tracado", "reboco", k=0.05, tons=[1, 4], luz_min=0.12)
    carvao = tinta("quase_preto", "carvao")
    escrita = tinta("tecido_cinza", "tinta_cinza")
    rr, x_rolo = 0.016, -0.08
    rng = random.Random(30)
    linhas = faixas([-0.06, -0.072], 0.004, -0.04, 0.1, rng)

    def relevo(x, y):   # borda da esquerda enrolada
        if x >= x_rolo:
            return Vector((x, y, 0.0))
        s = x_rolo - x
        a = s / rr
        return Vector((x_rolo - rr * math.sin(a), y, rr * (1 - math.cos(a))))

    def cor(x, y):
        # a linha reta a carvão, de canto a canto
        d = abs((y - (-0.05)) - (x + 0.06) * 0.85) / math.hypot(1, 0.85)
        if d < 0.004 and -0.07 < x < 0.12:
            return 1
        for k in range(5):
            mx = -0.04 + k * 0.035
            my = -0.05 + (mx + 0.06) * 0.85
            if abs(x - mx) < 0.002 and abs(y - my) < 0.012:
                return 1   # marcas de medida
        if (abs(x - 0.065) < 0.0022 and abs(y - 0.035) < 0.01) or (abs(y - 0.037) < 0.0022 and abs(x - 0.065) < 0.008):
            return 2
        return 2 if linhas(x, y) else 0
    _papel("tracado", papel, -0.13, 0.13, -0.09, 0.09, z=relevo, cor=cor, extra=(carvao, escrita), passo=0.004)


def lista_oren(el):
    papel = mat("lista", "la_crua", k=0.1, tons=[2, 4], luz_min=0.15)
    t1 = tinta("tecido_marrom", "tinta")
    t2 = tinta("quase_preto", "tinta_preta")
    rng = random.Random(15)
    ys = [0.11 - 0.019 * i for i in range(11)]
    fins = [(y, -0.035, 0.035 - rng.uniform(0, 0.03)) for y in ys]

    def relevo(x, y):   # amassado
        return 0.006 * noise.noise(Vector((x * 22, y * 22, 7.0))) + 0.003 * math.sin(y * 70)

    def cor(x, y):
        for i, (ly, xa, xb) in enumerate(fins):
            if abs(y - ly) < 0.0028 and xa <= x <= xb:
                return 1 if i % 3 != 1 else 2
        if abs(y - (ys[7] - 0.001)) < 0.002 and -0.04 < x < 0.04:
            return 2   # o nome riscado
        return 0
    _papel("lista", papel, -0.045, 0.045, -0.13, 0.13, z=relevo, cor=cor, extra=(t1, t2), passo=0.003)


def canto_carta(el):
    papel = mat("canto", "palha", k=0.1, tons=[1, 5])
    queimado = mat("queimado", "quase_preto", k=0.1)
    cinza = mat("chamuscado", "terra", k=0.0, tons=[0, 3], contorno=False)
    escrita = tinta("tecido_marrom", "tinta")

    def borda(x):      # a linha queimada, irregular
        return 0.03 - 0.6 * x + 0.012 * math.sin(x * 90) + 0.007 * math.sin(x * 230 + 1)

    def manter(x, y):
        return y < borda(x)

    def cor(x, y):
        d = borda(x) - y
        if d < 0.006:
            return 1
        if d < 0.012:
            return 2
        if any(abs(y - k) < 0.003 for k in (-0.03, -0.045, -0.06)) and x < 0.03 - 0.6 * 0 - (y + 0.0) * 0.0 and \
                y < borda(x) - 0.02:
            return 3
        return 0
    _papel("canto", papel, -0.07, 0.08, -0.08, 0.07,
           z=lambda x, y: 0.004 * noise.noise(Vector((x * 20, y * 20, 9))) + 0.03 * max(0.0, -0.01 + (y - borda(x) + 0.02)) ** 1,
           manter=manter, cor=cor, extra=(queimado, cinza, escrita), passo=0.0035)


def livro_enterros(el):
    capa = mat("livro_capa", "couro", k=0.25, textura="couro.png", escala=6)
    folhas = mat("livro_folhas", "linho", k=0.3, ruido=1.0, ruido_eixos=(4, 4, 300), tons=[1, 4])
    metal = mat("livro_fecho", "latao", k=0.0)
    friso = mat("livro_friso", "madeira_escura", k=0.0, tons=[0, 2], contorno=False, linhas=False)
    w, h, e = 0.15, 0.2, 0.045

    def capas(bm):
        caixa(bm, (0, 0, 0.003), (w, h, 0.006))
        vs = grade(bm, -w / 2, w / 2, -h / 2, h / 2, 0.005, z=lambda x, y: e - 0.001,
                   cor=lambda x, y: 1 if (abs(abs(x) - 0.055) < 0.003 and abs(y) < 0.085) or
                   (abs(abs(y) - 0.085) < 0.003 and abs(x) < 0.055) or
                   (abs(x) < 0.003 and abs(y) < 0.03) or (abs(y - 0.01) < 0.003 and abs(x) < 0.02) * 0 else 0)
        caixa(bm, (0, 0, e - 0.004), (w, h, 0.006))
        tubo(bm, (-w / 2, -h / 2, e / 2), (-w / 2, h / 2, e / 2), e / 2, seg=10)
    ob("livro_capa", capas, [capa, friso], suave=False)
    ob("livro_folhas", lambda bm: caixa(bm, (0.004, 0, e / 2), (w - 0.012, h - 0.01, e - 0.012)), folhas)

    def fecho(bm):
        caixa(bm, (w / 2 + 0.002, 0.0, e / 2), (0.006, 0.03, e + 0.004))
        caixa(bm, (w / 2 - 0.012, 0.0, e + 0.001), (0.028, 0.03, 0.004))
    ob("livro_fecho", fecho, metal)


MODELOS = {nome: f for nome, f in globals().items()
           if callable(f) and not nome.startswith("_") and getattr(f, "__module__", None) == __name__
           and f.__code__.co_varnames[:1] == ("el",)}
