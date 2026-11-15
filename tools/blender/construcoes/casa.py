"""Kit de construção (V1: a oficina; V5: as 9 construções e as variantes).

A pegada fica centrada na origem: x em [-w/2, w/2] (leste), y do jogo em [-d/2, d/2] (sul). No
Blender, Y = -y: a face sul do jogo é a face -Y. Faces visíveis: sul (-Y, iluminada) e leste
(+X, na sombra). As medidas saem do YAML e batem com a pegada do mapa (VT-07): a pegada é a
colisão; o corpo da casa pode ser menor (`corpo`) e o resto vira alpendre ou puxado.

Parâmetros (YAML), além dos do V1 (largura, profundidade, parede, pedra, telhado, beiral,
espessura_colmo, chamine, aberturas):
  corpo        [w, d, dx, dy]: caixa das paredes dentro da pegada (padrão: a pegada inteira)
  forma        duas_aguas | quatro_aguas | meia_agua
  cumeeira     x | y (direção da cumeeira; na meia-água, o lado alto: N, S, E ou W em `alto`)
  cobertura    colmo | tabuinha | lousa
  paredes      enxaimel | pedra | tabua
  reboco       rampa do reboco (reboco, la_crua, linho…)
  desgaste     0..1: irregularidade do telhado e tom mais escuro
  alpendre     {face: S|E, prof, de, ate, altura}: cobertura sobre esteios
  puxado       {face: E|W|N, larg, prof, altura}: anexo de meia-água
  placa        {face, pos, altura}: tabuleta pendurada num braço
  ervas        {face, de, ate}: maços pendurados sob o beiral
  pas          {face: S, raio, quadros}: pás do moinho (girando)
  chamines     lista de {x, y, acima} (`chamine` continua valendo)
"""
import math
import random

import bmesh
from mathutils import Vector

from nucleo import geo, materiais


def _bm_caixa(bm, x0, x1, y0, y1, z0, z1):
    res = bmesh.ops.create_cube(bm, size=1.0)
    for v in res["verts"]:
        v.co.x = x0 + (v.co.x + 0.5) * (x1 - x0)
        v.co.y = y0 + (v.co.y + 0.5) * (y1 - y0)
        v.co.z = z0 + (v.co.z + 0.5) * (z1 - z0)
    return res["verts"]


def _objeto_de(nome, construir, material, sub=0, suave=False):
    bm = bmesh.new()
    construir(bm)
    ob = geo.objeto(nome, bm, material, suave=suave)
    if sub:
        geo.subdividir(ob, sub)
    return ob


class Telhado:
    """Telhado como campo de alturas sobre o retângulo do corpo (com beiral). z(x, y) no mundo do
    Blender (y = -y do jogo)."""

    def __init__(self, forma, cumeeira, x0, x1, y0, y1, parede, alto, alto_lado=None):
        self.forma, self.cum = forma, cumeeira
        self.x0, self.x1, self.y0, self.y1 = x0, x1, y0, y1
        self.cx, self.cy = (x0 + x1) / 2, (y0 + y1) / 2
        self.hx, self.hy = (x1 - x0) / 2, (y1 - y0) / 2
        self.parede, self.alto = parede, alto
        self.lado = alto_lado
        if forma == "meia_agua":
            span = 2 * (self.hy if alto_lado in ("N", "S") else self.hx)
        elif forma == "quatro_aguas":
            span = min(self.hx, self.hy)
        else:
            span = self.hy if cumeeira == "x" else self.hx
        self.inc = alto / span

    def z(self, x, y):
        dx, dy = x - self.cx, y - self.cy
        if self.forma == "meia_agua":
            # lado alto: N = +Y do Blender; S = -Y; E = +X; W = -X
            t = {"N": (self.hy - dy), "S": (self.hy + dy), "E": (self.hx - dx), "W": (self.hx + dx)}[self.lado]
            return self.parede + self.alto - self.inc * t
        if self.forma == "quatro_aguas":
            if self.hx >= self.hy:
                d = max(abs(dy), abs(dx) - (self.hx - self.hy))
            else:
                d = max(abs(dx), abs(dy) - (self.hy - self.hx))
            return self.parede + self.alto - self.inc * d
        d = abs(dy) if self.cum == "x" else abs(dx)
        return self.parede + self.alto - self.inc * d


def _telhado(nome, t, beiral, esp, rng, material, ondula=0.04, fiadas=0.0, cursos=5, n=(40, 32), suave=True):
    """Telhado com espessura: superfície de cima (com ondulação e fiadas), de baixo e a borda."""
    bm = bmesh.new()
    nx, ny = n
    xa, xb = t.x0 - beiral, t.x1 + beiral
    ya, yb = t.y0 - beiral, t.y1 + beiral
    topo_z = t.parede + t.alto
    cima, baixo = [], []
    for j in range(ny + 1):
        y = ya + (yb - ya) * j / ny
        lc, lb = [], []
        for i in range(nx + 1):
            x = xa + (xb - xa) * i / nx
            z = t.z(x, y)
            f = (z - (t.parede - beiral * t.inc)) / max(1e-3, (topo_z - (t.parede - beiral * t.inc)))
            on = ondula * math.sin(x * 2.3 + y * 1.7) + rng.uniform(-ondula, ondula) * 0.5
            fi = fiadas * (1.0 - ((f * cursos) % 1.0)) if 0.02 < f < 0.97 else 0.0
            lc.append(bm.verts.new((x, y, z + esp + on + fi)))
            lb.append(bm.verts.new((x, y, z)))
        cima.append(lc)
        baixo.append(lb)
    for j in range(ny):
        for i in range(nx):
            bm.faces.new([cima[j][i], cima[j][i + 1], cima[j + 1][i + 1], cima[j + 1][i]])
            bm.faces.new([baixo[j][i], baixo[j + 1][i], baixo[j + 1][i + 1], baixo[j][i + 1]])
    for i in range(nx):
        bm.faces.new([baixo[0][i], baixo[0][i + 1], cima[0][i + 1], cima[0][i]])
        bm.faces.new([cima[ny][i], cima[ny][i + 1], baixo[ny][i + 1], baixo[ny][i]])
    for j in range(ny):
        bm.faces.new([cima[j][0], cima[j + 1][0], baixo[j + 1][0], baixo[j][0]])
        bm.faces.new([baixo[j][nx], baixo[j + 1][nx], cima[j + 1][nx], cima[j][nx]])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    ob = geo.objeto(nome, bm, material, suave=suave)
    if suave:
        m = ob.modifiers.new("sub", "SUBSURF")
        m.levels = m.render_levels = 1
    return ob


def _empenas(bm, t, x0, x1, y0, y1, parede, recuo=0.12, passos=24):
    """Fecha o vão entre o alto das paredes e o telhado, em cada uma das quatro faces."""
    lados = [((x0, y0), (x1, y0), (0, 1)), ((x1, y0), (x1, y1), (-1, 0)),
             ((x1, y1), (x0, y1), (0, -1)), ((x0, y1), (x0, y0), (1, 0))]
    for (ax, ay), (bx, by), (nx, ny) in lados:
        pts = []
        for k in range(passos + 1):
            f = k / passos
            x, y = ax + (bx - ax) * f, ay + (by - ay) * f
            pts.append((x, y, t.z(x, y) - 0.02))
        if max(p[2] for p in pts) < parede + 0.05:
            continue
        frente = [bm.verts.new((x, y, max(parede, z))) for x, y, z in pts]
        base = [bm.verts.new((x, y, parede)) for x, y, _ in pts]
        tras = [bm.verts.new((x + nx * recuo, y + ny * recuo, max(parede, z))) for x, y, z in pts]
        trasb = [bm.verts.new((x + nx * recuo, y + ny * recuo, parede)) for x, y, _ in pts]
        for k in range(passos):
            if frente[k].co.z - parede < 1e-3 and frente[k + 1].co.z - parede < 1e-3:
                continue
            bm.faces.new([base[k], base[k + 1], frente[k + 1], frente[k]])
            bm.faces.new([trasb[k + 1], trasb[k], tras[k], tras[k + 1]])
            bm.faces.new([frente[k], frente[k + 1], tras[k + 1], tras[k]])


def _materiais(p):
    desg = p.get("desgaste", 0.0)
    m = {}
    m["pedra"] = materiais.material("pedra", "pedra", k=0.9, textura="pedra.png", escala=0.55, oa=0.9, pontilhar=False)
    m["reboco"] = materiais.material("reboco_" + p.get("reboco", "reboco"), p.get("reboco", "reboco"), k=0.45 + desg * 0.3,
                                     textura="reboco.png", escala=0.45, oa=0.8, pontilhar=True, linhas=False)
    m["viga"] = materiais.material("viga", "madeira_escura", k=0.4, textura="casca.png", escala=1.0, oa=0.7)
    m["tabua"] = materiais.material("tabua", "madeira", k=0.5, textura="casca.png", escala=0.8, oa=0.8)
    m["tabuado"] = materiais.material("tabuado", p.get("rampa_tabuado", "madeira"), k=0.8, textura="tabuas.png",
                                      escala=0.375, oa=0.85, linhas=False)
    m["vidro"] = materiais.material("vidro", "vidro", k=0.1, emissivo=True, contorno=False, linhas=False,
                                    rampa_brilho="fogo")
    m["amarra"] = materiais.material("amarra", "madeira_escura", k=0.1, oa=0.6, linhas=False)
    cob = p.get("cobertura", "colmo")
    if cob == "colmo":
        tons = [0, 3] if desg > 0.5 else [0, 4]
        m["telhado"] = materiais.material("colmo" + ("_velho" if desg > 0.5 else ""), "colmo", k=1.2 + desg * 0.4,
                                          textura="colmo.png", escala=0.42, oa=1.0, linhas=False, tons=tons)
        m["cume"] = materiais.material("cumeeira", "colmo", k=0.8, textura="colmo.png", escala=0.6, oa=1.0, tons=[0, 4],
                                       linhas=False)
    elif cob == "tabuinha":
        m["telhado"] = materiais.material("tabuinha", "madeira_clara" if desg < 0.5 else "madeira", k=1.1,
                                          textura="tabuinha.png", escala=0.75, oa=1.0, linhas=False)
        m["cume"] = m["viga"]
    else:
        m["telhado"] = materiais.material("lousa", "ardosia", k=1.1, textura="lousa.png", escala=0.75, oa=1.0,
                                          linhas=False)
        m["cume"] = materiais.material("cume_lousa", "ardosia", k=0.3, oa=0.8)
    return m


def construir(p):
    """Monta a construção e devolve os pontos em metros do jogo: {"luzN": [x, y, z], "fumacaN": …}."""
    rng = random.Random(p.get("semente", 3))
    w, d = p["largura"], p["profundidade"]
    corpo = p.get("corpo", [w, d, 0.0, 0.0])
    cw, cd, cdx, cdy = corpo[0], corpo[1], corpo[2] if len(corpo) > 2 else 0.0, corpo[3] if len(corpo) > 3 else 0.0
    # corpo no Blender (y invertido): centro (cdx, -cdy)
    bx0, bx1 = cdx - cw / 2, cdx + cw / 2
    by0, by1 = -cdy - cd / 2, -cdy + cd / 2
    parede, pedra_h = p.get("parede", 2.7), p.get("pedra", 0.9)
    alto, beiral = p.get("telhado", 2.3), p.get("beiral", 0.4)
    esp = p.get("espessura_colmo", 0.38)
    forma, cum = p.get("forma", "duas_aguas"), p.get("cumeeira", "x")
    cob = p.get("cobertura", "colmo")
    paredes = p.get("paredes", "enxaimel")
    desg = p.get("desgaste", 0.0)
    M = _materiais(p)
    mundo = {}

    # --- base de pedra e paredes
    r = 0.06
    if paredes == "pedra":
        _objeto_de("paredes", lambda bm: _bm_caixa(bm, bx0, bx1, by0, by1, 0.0, parede), M["pedra"])
        m_emp = M["pedra"]
    else:
        _objeto_de("base", lambda bm: _bm_caixa(bm, bx0, bx1, by0, by1, 0.0, pedra_h), M["pedra"])
        m_par = M["reboco"] if paredes == "enxaimel" else M["tabuado"]
        _objeto_de("paredes", lambda bm: _bm_caixa(bm, bx0 + r, bx1 - r, by0 + r, by1 - r, pedra_h, parede), m_par)
        m_emp = m_par

    t = Telhado(forma, cum, bx0, bx1, by0, by1, parede, alto, p.get("alto"))
    _objeto_de("empenas", lambda bm: _empenas(bm, t, bx0 + r, bx1 - r, by0 + r, by1 - r, parede), m_emp)

    if paredes == "enxaimel":
        _objeto_de("vigas", lambda bm: _vigas(bm, bx0, bx1, by0, by1, r, pedra_h, parede, t), M["viga"])
    elif paredes == "tabua":
        def cantos(bm):  # cunhais e frechal de viga, para a tábua não ficar solta
            for x in (bx0 + r, bx1 - r - 0.14):
                _bm_caixa(bm, x, x + 0.14, by0 + r - 0.04, by0 + r + 0.1, pedra_h, parede)
            _bm_caixa(bm, bx1 - r - 0.1, bx1 - r + 0.04, by1 - r - 0.14, by1 - r, pedra_h, parede)
            _bm_caixa(bm, bx0, bx1, by0 + r - 0.05, by0 + r + 0.08, parede - 0.14, parede)
            _bm_caixa(bm, bx1 - r - 0.08, bx1 - r + 0.05, by0, by1, parede - 0.14, parede)
        _objeto_de("vigas", cantos, M["viga"])

    # --- aberturas: porta e janelas (faces S e E do corpo); cada janela é um ponto de luz
    k = 0
    for a in p.get("aberturas", []):
        _abertura(a, bx0, bx1, by0, by1, r, 0.035, pedra_h if paredes != "pedra" else 0.0, M, paredes)
        if not a.get("porta") and not a.get("apagada"):
            lx, ly, lz = _ponto_janela(a, bx0, bx1, by0, by1)
            mundo["luz%d" % k] = [round(lx, 3), round(-ly, 3), round(lz, 3)]
            k += 1

    # --- telhado
    if cob == "colmo":
        _telhado("telhado", t, beiral, esp, rng, M["telhado"], ondula=0.04 + desg * 0.05, fiadas=0.09, cursos=5)
    else:
        _telhado("telhado", t, beiral, 0.10, rng, M["telhado"], ondula=0.005 + desg * 0.02, fiadas=0.03,
                 cursos=int(alto / 0.22), n=(60, 48), suave=False)
    _cumeeira(t, beiral, esp if cob == "colmo" else 0.10, cob, M, w)

    # --- chaminés
    chs = list(p.get("chamines", []))
    if p.get("chamine"):
        chs.append(p["chamine"])
    for i, ch in enumerate(chs):
        cx, cy = ch.get("x", -cw * 0.3), -ch.get("y", 0.0)
        topo = t.z(cx, cy) + esp + ch.get("acima", 0.9)
        _objeto_de("chamine%d" % i, lambda bm, cx=cx, cy=cy, topo=topo: (
            _bm_caixa(bm, cx - 0.34, cx + 0.34, cy - 0.34, cy + 0.34, parede - 0.5, topo),
            _bm_caixa(bm, cx - 0.40, cx + 0.40, cy - 0.40, cy + 0.40, topo - 0.12, topo)), M["pedra"])
        geo.vazio("ponto.fumaca%d" % i, local=(cx, cy, topo + 0.1))
        mundo["fumaca%d" % i] = [round(cx, 3), round(-cy, 3), round(topo + 0.15, 3)]

    # --- soleira na frente de cada porta
    for a in p.get("aberturas", []):
        if a.get("porta") and a["face"] == "S":
            x = bx0 + a["pos"]
            _objeto_de("soleira%s" % a["pos"], lambda bm, x=x: _bm_caixa(bm, x - 0.65, x + 0.65, by0 - 0.45, by0 + 0.02, 0.0, 0.10),
                       M["pedra"], sub=1, suave=True)

    if p.get("alpendre"):
        _alpendre(p["alpendre"], bx0, bx1, by0, by1, parede, M, rng, cob)
    if p.get("puxado"):
        _puxado(p["puxado"], bx0, bx1, by0, by1, parede, pedra_h, M, rng, cob, paredes)
    if p.get("placa"):
        _placa(p["placa"], bx0, bx1, by0, by1, M)
    if p.get("ervas"):
        _ervas(p["ervas"], bx0, bx1, by0, by1, parede, rng)
    if p.get("extras"):
        _extras(p["extras"], M, rng)
    return mundo


def _vigas(bm, bx0, bx1, by0, by1, r, pedra_h, parede, t):
    sai, vg = 0.035, 0.13
    w, d = bx1 - bx0, by1 - by0
    ys = by0 + r - sai
    xs = bx1 - r + sai
    for z in (pedra_h, (pedra_h + parede) / 2 + 0.05, parede - vg):
        _bm_caixa(bm, bx0, bx1, ys - 0.02, ys + vg * 0.6, z, z + vg)
        _bm_caixa(bm, xs - vg * 0.6, xs + 0.02, by0, by1, z, z + vg)
    n_s = max(3, int(round(w / 1.15)))
    for i in range(n_s + 1):
        x = bx0 + r + (w - 2 * r - vg) * i / n_s
        _bm_caixa(bm, x, x + vg, ys - 0.02, ys + vg * 0.6, pedra_h, parede)
    n_e = max(3, int(round(d / 1.15)))
    for i in range(n_e + 1):
        y = by0 + r + (d - 2 * r - vg) * i / n_e
        _bm_caixa(bm, xs - vg * 0.6, xs + 0.02, y, y + vg, pedra_h, parede)
    z0, z1 = pedra_h + vg, (pedra_h + parede) / 2 + 0.05
    for (xa, xb) in ((bx0 + r + vg, bx0 + r + (w - 2 * r) / n_s), (bx1 - r - (w - 2 * r) / n_s, bx1 - r - vg)):
        _diagonal(bm, Vector((xa, ys + 0.02, z0)), Vector((xb, ys + 0.02, z1)), vg * 0.8)
    for (ya, yb) in ((by0 + r + vg, by0 + r + (d - 2 * r) / n_e), (by1 - r - (d - 2 * r) / n_e, by1 - r - vg)):
        _diagonal(bm, Vector((xs - 0.02, ya, z1)), Vector((xs - 0.02, yb, z0)), vg * 0.8)
    # empena visível (leste, se a cumeeira corre em x; sul, se corre em y): pendural e escoras
    cy = (by0 + by1) / 2
    cx = (bx0 + bx1) / 2
    if t.forma == "duas_aguas" and t.cum == "x":
        topo = t.z(xs, cy)
        if topo - parede > 0.6:
            _bm_caixa(bm, xs - vg * 0.6 - 0.12, xs + 0.02 - 0.12, cy - vg / 2, cy + vg / 2, parede, topo - 0.3)
            _diagonal(bm, Vector((xs - 0.13, cy - d * 0.27, parede + 0.05)), Vector((xs - 0.13, cy - 0.05, parede + (topo - parede) * 0.55)), vg * 0.8)
            _diagonal(bm, Vector((xs - 0.13, cy + d * 0.27, parede + 0.05)), Vector((xs - 0.13, cy + 0.05, parede + (topo - parede) * 0.55)), vg * 0.8)
    elif t.forma == "duas_aguas" and t.cum == "y":
        topo = t.z(cx, ys)
        if topo - parede > 0.6:
            yv = ys + 0.12
            _bm_caixa(bm, cx - vg / 2, cx + vg / 2, yv - vg * 0.6, yv + 0.02, parede, topo - 0.3)
            _bm_caixa(bm, bx0 + 0.2, bx1 - 0.2, yv - vg * 0.6, yv + 0.02, parede, parede + vg)
            _diagonal(bm, Vector((cx - w * 0.27, yv - 0.02, parede + 0.05)), Vector((cx - 0.05, yv - 0.02, parede + (topo - parede) * 0.55)), vg * 0.8)
            _diagonal(bm, Vector((cx + w * 0.27, yv - 0.02, parede + 0.05)), Vector((cx + 0.05, yv - 0.02, parede + (topo - parede) * 0.55)), vg * 0.8)


def _cumeeira(t, beiral, esp, cob, M, w):
    """Rolo de colmo amarrado (colmo) ou tábua/peça de cumeeira (tabuinha, lousa)."""
    if t.forma == "meia_agua":
        return
    if t.forma == "quatro_aguas":
        meia = abs(t.hx - t.hy)
        ao_x = t.hx >= t.hy
    else:
        ao_x = t.cum == "x"
        meia = (t.hx if ao_x else t.hy) + beiral - 0.05
    if meia < 0.05:
        return
    z = t.parede + t.alto
    if cob == "colmo":
        def rolo(bm):
            res = bmesh.ops.create_cone(bm, cap_ends=True, segments=10, radius1=0.30, radius2=0.30, depth=2 * meia)
            for v in res["verts"]:
                if ao_x:
                    v.co = Vector((t.cx + v.co.z, t.cy + v.co.y * 1.15, v.co.x * 0.75 + z + esp * 0.55))
                else:
                    v.co = Vector((t.cx + v.co.y * 1.15, t.cy + v.co.z, v.co.x * 0.75 + z + esp * 0.55))
        _objeto_de("cumeeira", rolo, M["cume"], sub=1, suave=True)

        def amarras(bm):
            n = max(2, int(2 * meia / 1.3))
            for i in range(n + 1):
                s = -meia + 0.3 + (2 * meia - 0.6) * i / n
                if ao_x:
                    _bm_caixa(bm, t.cx + s - 0.025, t.cx + s + 0.025, t.cy - 0.36, t.cy + 0.36, z + esp * 0.55 + 0.12, z + esp * 0.55 + 0.18)
                else:
                    _bm_caixa(bm, t.cx - 0.36, t.cx + 0.36, t.cy + s - 0.025, t.cy + s + 0.025, z + esp * 0.55 + 0.12, z + esp * 0.55 + 0.18)
        _objeto_de("amarras", amarras, M["amarra"])
    else:
        def tabua(bm):
            if ao_x:
                _bm_caixa(bm, t.cx - meia, t.cx + meia, t.cy - 0.12, t.cy + 0.12, z + esp - 0.02, z + esp + 0.08)
            else:
                _bm_caixa(bm, t.cx - 0.12, t.cx + 0.12, t.cy - meia, t.cy + meia, z + esp - 0.02, z + esp + 0.08)
        _objeto_de("cumeeira", tabua, M["cume"])


def _diagonal(bm, a, b, larg):
    d = b - a
    comp = d.length
    res = bmesh.ops.create_cube(bm, size=1.0)
    rot = d.normalized().to_track_quat("Z", "Y").to_matrix().to_4x4()
    for v in res["verts"]:
        v.co = Vector((v.co.x * larg, v.co.y * larg * 0.6, (v.co.z + 0.5) * comp))
        v.co = (rot @ v.co.to_4d()).to_3d() + a


def _sistema(face, pos, bx0, bx1, by0, by1, r):
    """Sistema local de uma face: u ao longo dela, n para fora, z para cima."""
    if face == "S":
        return lambda u, n, z: Vector((bx0 + pos + u, by0 + r - n, z))
    # "E": pos medido do canto sul para o norte
    return lambda u, n, z: Vector((bx1 - r + n, by0 + pos + u, z))


def _ponto_janela(a, bx0, bx1, by0, by1):
    porta = a.get("porta", False)
    alt = a.get("altura", 1.95 if porta else 0.72)
    z0 = a.get("peitoril", 1.25)
    P = _sistema(a["face"], a["pos"], bx0, bx1, by0, by1, 0.0)
    v = P(0, 0.6, z0 + alt / 2)
    return v.x, v.y, v.z


def _abertura(a, bx0, bx1, by0, by1, r, sai, pedra_h, M, paredes):
    """Porta ou janela numa face: moldura de viga, folha recuada (tábua ou vidro) e postigos."""
    face, pos = a["face"], a["pos"]
    porta = a.get("porta", False)
    larg = a.get("largura", 1.0 if porta else 0.62)
    alt = a.get("altura", 1.95 if porta else 0.72)
    z0 = 0.0 if porta else a.get("peitoril", 1.25)
    if paredes == "pedra":   # parede de pedra não recua sobre a base: a abertura fica no plano dela
        r = 0.0
    P = _sistema(face, pos, bx0, bx1, by0, by1, r)

    def caixa_local(bm, u0, u1, n0, n1, za, zb):
        res = bmesh.ops.create_cube(bm, size=1.0)
        for v in res["verts"]:
            u = u0 + (v.co.x + 0.5) * (u1 - u0)
            n = n0 + (v.co.y + 0.5) * (n1 - n0)
            z = za + (v.co.z + 0.5) * (zb - za)
            v.co = P(u, n, z)

    hl = larg / 2
    f = 0.11
    base = 0 if porta else max(z0 - f, pedra_h)

    def moldura(bm):
        caixa_local(bm, -hl - f, -hl, -0.02, sai + 0.05, base, z0 + alt + f)
        caixa_local(bm, hl, hl + f, -0.02, sai + 0.05, base, z0 + alt + f)
        caixa_local(bm, -hl - f, hl + f, -0.02, sai + 0.06, z0 + alt, z0 + alt + f * 1.3)
        if not porta:
            caixa_local(bm, -hl - f * 1.3, hl + f * 1.3, -0.02, sai + 0.09, z0 - f, z0)
    _objeto_de("moldura_%s_%s" % (face, pos), moldura, M["viga"])
    if porta:
        _objeto_de("porta_%s_%s" % (face, pos), lambda bm: caixa_local(bm, -hl, hl, 0.0, 0.03, z0, z0 + alt), M["tabua"])

        def ferragem(bm):
            for z in (0.45, alt - 0.5):
                caixa_local(bm, -hl + 0.05, hl - 0.15, 0.03, 0.045, z, z + 0.07)
            if larg > 1.6:   # porta dupla (celeiro): a junta do meio
                caixa_local(bm, -0.03, 0.03, 0.03, 0.05, z0, z0 + alt)
        _objeto_de("ferragem_%s_%s" % (face, pos), ferragem, M["viga"])
    else:
        _objeto_de("vidro_%s_%s" % (face, pos), lambda bm: caixa_local(bm, -hl, hl, 0.0, 0.012, z0, z0 + alt),
                   M["vidro"])

        def caixilho(bm):
            caixa_local(bm, -0.025, 0.025, 0.012, 0.04, z0, z0 + alt)
            caixa_local(bm, -hl, hl, 0.012, 0.04, z0 + alt * 0.5 - 0.025, z0 + alt * 0.5 + 0.025)
        _objeto_de("caixilho_%s_%s" % (face, pos), caixilho, M["viga"])
        if a.get("postigos", True):
            def postigos(bm):
                if a.get("fechada"):
                    caixa_local(bm, -hl, hl, 0.02, 0.06, z0, z0 + alt)
                else:
                    caixa_local(bm, -hl - f - larg * 0.5, -hl - f, sai, sai + 0.05, z0, z0 + alt)
                    caixa_local(bm, hl + f, hl + f + larg * 0.5, sai, sai + 0.05, z0, z0 + alt)
            _objeto_de("postigos_%s_%s" % (face, pos), postigos, M["tabua"])


def _alpendre(a, bx0, bx1, by0, by1, parede, M, rng, cob):
    """Cobertura de meia-água sobre esteios, encostada numa face (S ou E)."""
    face = a.get("face", "S")
    prof = a.get("prof", 1.4)
    h = a.get("altura", parede - 0.2)
    if face == "S":
        x0, x1 = bx0 + a.get("de", 0.0), bx0 + a.get("ate", bx1 - bx0)
        y0, y1 = by0 - prof, by0
        t = Telhado("meia_agua", None, x0, x1, y0, y1, h - 0.55, 0.55, "N")
        esteios = [(x0 + 0.15, y0 + 0.15), (x1 - 0.15, y0 + 0.15)]
        n = int((x1 - x0) / 2.4)
        esteios += [(x0 + (x1 - x0) * i / (n + 1), y0 + 0.15) for i in range(1, n + 1)]
    else:
        y0, y1 = by0 + a.get("de", 0.0), by0 + a.get("ate", by1 - by0)
        x0, x1 = bx1, bx1 + prof
        t = Telhado("meia_agua", None, x0, x1, y0, y1, h - 0.55, 0.55, "W")
        esteios = [(x1 - 0.15, y0 + 0.15), (x1 - 0.15, y1 - 0.15)]
    _telhado("alpendre_telhado", t, 0.2, 0.08 if cob != "colmo" else 0.22, rng, M["telhado"],
             ondula=0.01, fiadas=0.0, n=(24, 8), suave=cob == "colmo")

    def postes(bm):
        for x, y in esteios:
            _bm_caixa(bm, x - 0.08, x + 0.08, y - 0.08, y + 0.08, 0.0, t.z(x, y))
        if face == "S":
            _bm_caixa(bm, x0, x1, y0 + 0.07, y0 + 0.23, t.z(x0, y0 + 0.15) - 0.16, t.z(x0, y0 + 0.15))
        else:
            _bm_caixa(bm, x1 - 0.23, x1 - 0.07, y0, y1, t.z(x1 - 0.15, y0) - 0.16, t.z(x1 - 0.15, y0))
    _objeto_de("alpendre_esteios", postes, M["viga"])
    # chão de tábuas do alpendre
    _objeto_de("alpendre_piso", lambda bm: _bm_caixa(bm, x0, x1, y0, y1, 0.0, 0.12), M["tabuado"])


def _puxado(a, bx0, bx1, by0, by1, parede, pedra_h, M, rng, cob, paredes):
    """Anexo baixo de meia-água encostado numa face lateral (E, W) ou atrás (N)."""
    face = a.get("face", "E")
    prof, h = a.get("prof", 1.6), a.get("altura", parede - 0.6)
    larg = a.get("larg", (by1 - by0) * 0.7)
    if face in ("E", "W"):
        y0 = by0 + a.get("de", (by1 - by0 - larg) / 2)
        y1 = y0 + larg
        x0, x1 = (bx1, bx1 + prof) if face == "E" else (bx0 - prof, bx0)
        lado = "W" if face == "E" else "E"
    else:
        x0 = bx0 + a.get("de", (bx1 - bx0 - larg) / 2)
        x1 = x0 + larg
        y0, y1 = by1, by1 + prof
        lado = "S"
    m_par = M["tabuado"] if a.get("tabua", True) else M["reboco"]
    _objeto_de("puxado_base", lambda bm: _bm_caixa(bm, x0, x1, y0, y1, 0.0, 0.3), M["pedra"])
    _objeto_de("puxado_paredes", lambda bm: _bm_caixa(bm, x0 + 0.04, x1 - 0.04, y0 + 0.04, y1 - 0.04, 0.3, h), m_par)
    t = Telhado("meia_agua", None, x0, x1, y0, y1, h, 0.5, lado)
    _objeto_de("puxado_empenas", lambda bm: _empenas(bm, t, x0 + 0.04, x1 - 0.04, y0 + 0.04, y1 - 0.04, h), m_par)
    _telhado("puxado_telhado", t, 0.22, 0.08 if cob != "colmo" else 0.25, rng, M["telhado"], ondula=0.01, fiadas=0.0,
             n=(20, 10), suave=cob == "colmo")
    if a.get("porta"):
        def portinha(bm):
            if face in ("E", "W"):
                x = x1 - 0.02 if face == "E" else x0 - 0.02
                _bm_caixa(bm, x, x + 0.06, (y0 + y1) / 2 - 0.4, (y0 + y1) / 2 + 0.4, 0.3, min(h - 0.2, 1.9))
            else:
                _bm_caixa(bm, (x0 + x1) / 2 - 0.4, (x0 + x1) / 2 + 0.4, y0 - 0.04, y0 + 0.02, 0.3, min(h - 0.2, 1.9))
        _objeto_de("puxado_porta", portinha, M["tabua"])


def _placa(a, bx0, bx1, by0, by1, M):
    """Tabuleta pendurada num braço de ferro que sai da parede (taverna, Posto)."""
    face = a.get("face", "S")
    pos, z = a.get("pos", 1.0), a.get("altura", 2.4)
    m_ferro = materiais.material("ferro", "ferro", k=0.1, oa=0.6)
    m_placa = materiais.material("placa_" + a.get("rampa", "madeira_clara"), a.get("rampa", "madeira_clara"), k=0.4,
                                 textura="casca.png", escala=1.5, oa=0.6)
    m_pint = materiais.material("placa_pintura", a.get("pintura", "latao"), k=0.0, oa=0.4, linhas=False)
    if face == "S":
        o, dvec = Vector((bx0 + pos, by0, z)), Vector((0, -1, 0))
    else:
        o, dvec = Vector((bx1, by0 + pos, z)), Vector((1, 0, 0))
    lado = Vector((-dvec.y, dvec.x, 0))
    comp = 0.95

    def braco(bm):
        _diagonal(bm, o, o + dvec * comp, 0.05)
        _diagonal(bm, o - Vector((0, 0, 0.45)), o + dvec * comp * 0.6, 0.04)
    _objeto_de("placa_braco", braco, m_ferro)
    c = o + dvec * comp * 0.55 - Vector((0, 0, 0.42))

    def tabua(bm):
        res = bmesh.ops.create_cube(bm, size=1.0)
        for v in res["verts"]:
            v.co = c + dvec * v.co.x * 0.62 + lado * v.co.y * 0.05 + Vector((0, 0, v.co.z * 0.5))
    _objeto_de("placa_tabua", tabua, m_placa)

    def pintura(bm):  # um sinal simples (um disco: a caneca ou a roda), nas duas faces
        for s in (-1, 1):
            res = bmesh.ops.create_circle(bm, cap_ends=True, segments=12, radius=0.14)
            for v in res["verts"]:
                v.co = c + dvec * v.co.x + Vector((0, 0, v.co.y)) + lado * s * 0.03
    _objeto_de("placa_pintura", pintura, m_pint)


def _ervas(a, bx0, bx1, by0, by1, parede, rng):
    """Maços de ervas pendurados de cabeça para baixo sob o beiral (curandeira)."""
    m_erva = materiais.material("erva", "folha_samambaia", k=0.3, ruido=12.0, oa=0.6, linhas=False)
    m_seca = materiais.material("erva_seca", "grama_seca", k=0.3, ruido=12.0, oa=0.6, linhas=False)
    face = a.get("face", "S")
    de, ate = a.get("de", 0.4), a.get("ate", (bx1 - bx0) - 0.4)
    n = a.get("n", 7)
    for i in range(n):
        s = de + (ate - de) * (i + rng.uniform(-0.2, 0.2)) / max(1, n - 1)
        if face == "S":
            base = Vector((bx0 + s, by0 - 0.22, parede - 0.15))
        else:
            base = Vector((bx1 + 0.22, by0 + s, parede - 0.15))
        comp = rng.uniform(0.28, 0.42)

        def maco(bm, base=base, comp=comp):
            res = bmesh.ops.create_cone(bm, cap_ends=True, segments=7, radius1=0.02, radius2=0.09, depth=comp)
            for v in res["verts"]:
                v.co = base + Vector((v.co.x, v.co.y, -(v.co.z + comp / 2) - 0.08))
        _objeto_de("erva%d" % i, maco, m_erva if i % 3 else m_seca, suave=True)


def _extras(lista, M, rng):
    """Peças soltas encostadas na construção: barris, caixotes, lenha, ferramentas, vasos."""
    m_aro = materiais.material("aro", "ferro", k=0.1, oa=0.6)
    for i, e in enumerate(lista):
        x, y = e["x"], -e["y"]
        tipo = e["tipo"]
        if tipo == "barril":
            def barril(bm, x=x, y=y):
                res = bmesh.ops.create_cone(bm, cap_ends=True, segments=12, radius1=0.30, radius2=0.30, depth=0.85)
                for v in res["verts"]:
                    bojo = 1.0 + 0.12 * math.cos(v.co.z / 0.425 * math.pi / 2)
                    v.co = Vector((x + v.co.x * bojo, y + v.co.y * bojo, v.co.z + 0.425))
            _objeto_de("extra_barril%d" % i, barril, M["tabua"], suave=True)
        elif tipo == "caixote":
            _objeto_de("extra_caixote%d" % i, lambda bm, x=x, y=y: _bm_caixa(bm, x - 0.3, x + 0.3, y - 0.3, y + 0.3, 0, 0.5),
                       M["tabuado"])
        elif tipo == "lenha":
            def lenha(bm, x=x, y=y):
                for fila in range(4):
                    for k in range(5 - fila):
                        cx = x - 0.5 + 0.22 * k + 0.11 * fila
                        res = bmesh.ops.create_cone(bm, cap_ends=True, segments=7, radius1=0.1, radius2=0.1, depth=0.7)
                        for v in res["verts"]:
                            v.co = Vector((cx + v.co.x, y + v.co.z, 0.1 + fila * 0.18 + v.co.y))
            _objeto_de("extra_lenha%d" % i, lenha, M["tabua"], suave=True)
        elif tipo == "ferramentas":   # cabos encostados na parede (pá, forcado, enxada)
            def ferr(bm, x=x, y=y):
                for k in range(3):
                    a = Vector((x + k * 0.25, y - 0.35, 0.0))
                    b = Vector((x + k * 0.25 + 0.05, y + 0.02, 1.45 - k * 0.12))
                    _diagonal(bm, a, b, 0.05)
            _objeto_de("extra_ferr%d" % i, ferr, M["viga"])
            def laminas(bm, x=x, y=y):
                for k in range(3):
                    _bm_caixa(bm, x + k * 0.25 - 0.1, x + k * 0.25 + 0.1, y - 0.42, y - 0.36, 0.0, 0.25)
            _objeto_de("extra_lamina%d" % i, laminas, m_aro)
        elif tipo == "vaso":
            m_barro = materiais.material("barro", "ferrugem", k=0.2, oa=0.6)
            def vaso(bm, x=x, y=y):
                res = bmesh.ops.create_cone(bm, cap_ends=True, segments=10, radius1=0.14, radius2=0.2, depth=0.3)
                for v in res["verts"]:
                    v.co = Vector((x + v.co.x, y + v.co.y, v.co.z + 0.15))
            _objeto_de("extra_vaso%d" % i, vaso, m_barro, suave=True)
            m_planta = materiais.material("planta_vaso", "folha_samambaia", k=0.3, ruido=10.0, oa=0.6, linhas=False)
            def planta(bm, x=x, y=y):
                res = bmesh.ops.create_icosphere(bm, subdivisions=2, radius=0.22)
                for v in res["verts"]:
                    v.co = Vector((x + v.co.x, y + v.co.y, 0.38 + v.co.z * 0.8))
            _objeto_de("extra_planta%d" % i, planta, m_planta, suave=True)


def pas(p, angulo):
    """Pás do moinho: quatro braços de treliça com pano, girando em torno do eixo que sai da
    empena. Objeto separado (o laço gira só ele)."""
    import bpy
    for ob in [o for o in bpy.data.objects if o.name.startswith("pas")]:
        bpy.data.objects.remove(ob, do_unlink=True)
    a = p["pas"]
    raio = a.get("raio", 3.2)
    centro = Vector((a.get("x", 0.0), -a.get("y", 0.0), a.get("z", 4.6)))
    eixo = Vector((0, -1, 0)) if a.get("face", "S") == "S" else Vector((1, 0, 0))
    lado = Vector((1, 0, 0)) if a.get("face", "S") == "S" else Vector((0, 1, 0))
    up = Vector((0, 0, 1))
    m_ma = materiais.material("pas_madeira", "madeira", k=0.3, textura="casca.png", escala=1.2, oa=0.6)
    m_pano = materiais.material("pas_pano", "linho", k=0.3, textura="linho.png", escala=1.0, oa=0.5, tons=[1, 4])
    c = centro + eixo * 0.25

    def braco_dir(k):
        ang = angulo + k * math.pi / 2
        return (lado * math.cos(ang) + up * math.sin(ang)).normalized()

    def bracos(bm):
        _bm_caixa(bm, c.x - 0.12, c.x + 0.12, c.y - 0.12, c.y + 0.12, c.z - 0.12, c.z + 0.12)
        for k in range(4):
            dvec = braco_dir(k)
            _diagonal(bm, c, c + dvec * raio, 0.10)
            perp = eixo.cross(dvec).normalized()
            for s in range(5):   # travessas da treliça
                f = 0.3 + 0.7 * s / 4
                q = c + dvec * raio * f
                _diagonal(bm, q - perp * 0.05, q + perp * 0.55, 0.035)
            _diagonal(bm, c + dvec * raio * 0.3 + perp * 0.55, c + dvec * raio + perp * 0.55, 0.035)
    _objeto_de("pas_bracos", bracos, m_ma)

    def pano(bm):
        for k in range(4):
            dvec = braco_dir(k)
            perp = eixo.cross(dvec).normalized()
            p0 = c + dvec * raio * 0.32 + eixo * 0.03
            p1 = c + dvec * raio * 0.98 + eixo * 0.03
            vs = [bm.verts.new(p0), bm.verts.new(p1), bm.verts.new(p1 + perp * 0.5), bm.verts.new(p0 + perp * 0.5)]
            bm.faces.new(vs)
    _objeto_de("pas_pano", pano, m_pano)
