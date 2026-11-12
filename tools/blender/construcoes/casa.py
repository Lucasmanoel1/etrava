"""Casa de enxaimel com colmo (V1: a oficina; V5: o kit para as 9 construções).

A base fica centrada na origem: x em [-w/2, w/2] (leste), y do jogo em [-d/2, d/2] (sul). No
Blender, Y = -y: a face sul do jogo é a face -Y. Faces visíveis: sul (-Y, iluminada) e leste
(+X, na sombra). As medidas saem do YAML e batem com a pegada do mapa (VT-07).
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


def construir(p):
    rng = random.Random(p.get("semente", 3))
    w, d = p["largura"], p["profundidade"]
    hw, hd = w / 2, d / 2
    parede, pedra_h = p.get("parede", 2.7), p.get("pedra", 0.9)
    telhado_h, beiral = p.get("telhado", 2.3), p.get("beiral", 0.4)
    esp = p.get("espessura_colmo", 0.38)

    m_pedra = materiais.material("pedra", "pedra", k=0.9, textura="pedra.png", escala=0.55, oa=0.9, pontilhar=False)
    m_reboco = materiais.material("reboco", "reboco", k=0.45, textura="reboco.png", escala=0.45, oa=0.8, pontilhar=True,
                                  linhas=False)
    m_viga = materiais.material("viga", "madeira_escura", k=0.4, textura="casca.png", escala=1.0, oa=0.7)
    m_colmo = materiais.material("colmo", "colmo", k=1.2, textura="colmo.png", escala=0.42, oa=1.0, linhas=False,
                                 tons=[0, 4])
    m_cume = materiais.material("cumeeira", "colmo", k=0.8, textura="colmo.png", escala=0.6, oa=1.0, tons=[0, 4],
                                linhas=False)
    m_tabua = materiais.material("tabua", "madeira", k=0.5, textura="casca.png", escala=0.8, oa=0.8)
    m_vidro = materiais.material("vidro", "vidro", k=0.1, emissivo=True, contorno=False, linhas=False,
                                 rampa_brilho="fogo")
    m_amarra = materiais.material("amarra", "madeira_escura", k=0.1, oa=0.6, linhas=False)

    # --- base de pedra e paredes (um pouco recuadas sobre a base)
    _objeto_de("base", lambda bm: _bm_caixa(bm, -hw, hw, -hd, hd, 0.0, pedra_h), m_pedra)
    r = 0.06
    _objeto_de("paredes", lambda bm: _bm_caixa(bm, -hw + r, hw - r, -hd + r, hd - r, pedra_h, parede), m_reboco)

    # --- empena leste e oeste (triângulo de reboco acima das paredes)
    def empena(bm):
        for s in (-1, 1):
            x = s * (hw - r)
            v = [bm.verts.new((x, -hd + r, parede)), bm.verts.new((x, hd - r, parede)),
                 bm.verts.new((x, 0, parede + telhado_h - 0.15))]
            f = bm.faces.new(v if s > 0 else list(reversed(v)))
            ext = bmesh.ops.extrude_face_region(bm, geom=[f])
            for e in ext["geom"]:
                if isinstance(e, bmesh.types.BMVert):
                    e.co.x -= s * 0.12
    _objeto_de("empenas", empena, m_reboco)

    # --- enxaimel: vigas nas faces sul (-Y) e leste (+X), 3 cm para fora do reboco
    sai = 0.035
    vg = 0.13

    def vigas(bm):
        ys = -hd + r - sai                      # plano da face sul
        xs = hw - r + sai                       # plano da face leste
        # frechais: em cima da pedra, no meio e no alto
        for z in (pedra_h, (pedra_h + parede) / 2 + 0.05, parede - vg):
            _bm_caixa(bm, -hw, hw, ys - 0.02, ys + vg * 0.6, z, z + vg)
            _bm_caixa(bm, xs - vg * 0.6, xs + 0.02, -hd, hd, z, z + vg)
        # montantes
        n_s = max(3, int(round(w / 1.15)))
        for i in range(n_s + 1):
            x = -hw + r + (w - 2 * r - vg) * i / n_s
            _bm_caixa(bm, x, x + vg, ys - 0.02, ys + vg * 0.6, pedra_h, parede)
        n_e = max(3, int(round(d / 1.15)))
        for i in range(n_e + 1):
            y = -hd + r + (d - 2 * r - vg) * i / n_e
            _bm_caixa(bm, xs - vg * 0.6, xs + 0.02, y, y + vg, pedra_h, parede)
        # mãos-francesas (diagonais) perto dos cantos, no painel de baixo
        z0, z1 = pedra_h + vg, (pedra_h + parede) / 2 + 0.05
        for (xa, xb) in ((-hw + r + vg, -hw + r + (w - 2 * r) / n_s), (hw - r - (w - 2 * r) / n_s, hw - r - vg)):
            _diagonal(bm, Vector((xa, ys + 0.02, z0)), Vector((xb, ys + 0.02, z1)), vg * 0.8)
        for (ya, yb) in ((-hd + r + vg, -hd + r + (d - 2 * r) / n_e), (hd - r - (d - 2 * r) / n_e, hd - r - vg)):
            _diagonal(bm, Vector((xs - 0.02, ya, z1)), Vector((xs - 0.02, yb, z0)), vg * 0.8)
        # empena leste: pendural e duas escoras
        _bm_caixa(bm, xs - vg * 0.6 - 0.12, xs + 0.02 - 0.12, -vg / 2, vg / 2, parede, parede + telhado_h - 0.3)
        _diagonal(bm, Vector((xs - 0.13, -hd * 0.55, parede + 0.05)), Vector((xs - 0.13, -0.05, parede + telhado_h * 0.55)), vg * 0.8)
        _diagonal(bm, Vector((xs - 0.13, hd * 0.55, parede + 0.05)), Vector((xs - 0.13, 0.05, parede + telhado_h * 0.55)), vg * 0.8)
    _objeto_de("vigas", vigas, m_viga)

    # --- aberturas: porta e janelas (sul = -Y, leste = +X); o vidro brilha à noite
    for a in p.get("aberturas", []):
        _abertura(a, hw, hd, r, sai, pedra_h, m_viga, m_tabua, m_vidro)

    # --- telhado de colmo: duas águas, cumeeira ao longo de X, beiral grosso e arredondado
    cume = parede + telhado_h
    tl = _telhado(w, d, parede, cume, beiral, esp, rng)
    tl.data.materials.append(m_colmo)
    # cumeeira: rolo de colmo amarrado
    def cumeeira(bm):
        res = bmesh.ops.create_cone(bm, cap_ends=True, segments=10, radius1=0.30, radius2=0.30, depth=w + 2 * beiral - 0.1)
        for v in res["verts"]:
            v.co = Vector((v.co.z, v.co.y * 1.15, v.co.x * 0.75 + cume + esp * 0.55))
    cm = _objeto_de("cumeeira", cumeeira, m_cume, sub=1, suave=True)
    # amarras: varas atravessadas na cumeeira e duas linhas de vara ao longo dela
    def amarras(bm):
        n = int(w / 1.3)
        for i in range(n + 1):
            x = -hw - beiral + 0.3 + (w + 2 * beiral - 0.6) * i / n
            _bm_caixa(bm, x - 0.025, x + 0.025, -0.36, 0.36, cume + esp * 0.55 + 0.12, cume + esp * 0.55 + 0.18)
        for s in (-1, 1):
            _diagonal(bm, Vector((-hw - beiral + 0.2, s * 0.5, cume + esp * 0.2)),
                      Vector((hw + beiral - 0.2, s * 0.5, cume + esp * 0.2)), 0.05)
    _objeto_de("amarras", amarras, m_amarra)

    # --- chaminé de pedra
    ch = p.get("chamine")
    if ch:
        cx, cy = ch.get("x", -hw * 0.55), -ch.get("y", 0.0)
        topo = cume + ch.get("acima", 0.9)
        _objeto_de("chamine", lambda bm: (_bm_caixa(bm, cx - 0.34, cx + 0.34, cy - 0.34, cy + 0.34, parede, topo),
                                          _bm_caixa(bm, cx - 0.40, cx + 0.40, cy - 0.40, cy + 0.40, topo - 0.12, topo)), m_pedra)
        geo.vazio("ponto.fumaca", local=(cx, cy, topo + 0.1))

    # pedras de soleira na frente da porta
    for a in p.get("aberturas", []):
        if a.get("porta"):
            x = -hw + a["pos"]
            _objeto_de("soleira", lambda bm, x=x: _bm_caixa(bm, x - 0.65, x + 0.65, -hd - 0.45, -hd + 0.02, 0.0, 0.10),
                       m_pedra, sub=1, suave=True)


def _diagonal(bm, a, b, larg):
    d = b - a
    comp = d.length
    res = bmesh.ops.create_cube(bm, size=1.0)
    rot = d.normalized().to_track_quat("Z", "Y").to_matrix().to_4x4()
    for v in res["verts"]:
        v.co = Vector((v.co.x * larg, v.co.y * larg * 0.6, (v.co.z + 0.5) * comp))
        v.co = (rot @ v.co.to_4d()).to_3d() + a


def _abertura(a, hw, hd, r, sai, pedra_h, m_viga, m_tabua, m_vidro):
    """Porta ou janela numa face: moldura de viga, folha recuada (tábua ou vidro) e postigos."""
    face, pos = a["face"], a["pos"]
    porta = a.get("porta", False)
    larg = a.get("largura", 1.0 if porta else 0.62)
    alt = a.get("altura", 1.95 if porta else 0.72)
    z0 = 0.0 if porta else a.get("peitoril", 1.25)
    # sistema local: u ao longo da face, n para fora
    if face == "S":
        def P(u, n, z):
            return Vector((-hw + pos + u, -(hd - r) - n, z))
    else:  # "E": pos medido do canto sul para o norte (y do jogo cresce para o sul)
        def P(u, n, z):
            return Vector(((hw - r) + n, -(hd - pos) + u, z))

    def caixa_local(bm, u0, u1, n0, n1, za, zb):
        res = bmesh.ops.create_cube(bm, size=1.0)
        for v in res["verts"]:
            u = u0 + (v.co.x + 0.5) * (u1 - u0)
            n = n0 + (v.co.y + 0.5) * (n1 - n0)
            z = za + (v.co.z + 0.5) * (zb - za)
            v.co = P(u, n, z)

    hl = larg / 2
    f = 0.11
    def moldura(bm):
        caixa_local(bm, -hl - f, -hl, -0.02, sai + 0.05, max(z0, pedra_h if not porta else 0) if not porta else 0, z0 + alt + f)
        caixa_local(bm, hl, hl + f, -0.02, sai + 0.05, max(z0, pedra_h if not porta else 0) if not porta else 0, z0 + alt + f)
        caixa_local(bm, -hl - f, hl + f, -0.02, sai + 0.06, z0 + alt, z0 + alt + f * 1.3)
        if not porta:
            caixa_local(bm, -hl - f * 1.3, hl + f * 1.3, -0.02, sai + 0.09, z0 - f, z0)
    _objeto_de("moldura_%s_%s" % (face, pos), moldura, m_viga)
    if porta:
        def folha(bm):
            caixa_local(bm, -hl, hl, 0.0, 0.03, z0, z0 + alt)
        _objeto_de("porta_%s" % pos, folha, m_tabua)
        # ferragens: duas faixas escuras
        def ferragem(bm):
            for z in (0.45, 1.45):
                caixa_local(bm, -hl + 0.05, hl - 0.15, 0.03, 0.045, z, z + 0.07)
        _objeto_de("ferragem_%s" % pos, ferragem, m_viga)
    else:
        def vidro(bm):
            caixa_local(bm, -hl, hl, 0.0, 0.012, z0, z0 + alt)
        _objeto_de("vidro_%s_%s" % (face, pos), vidro, m_vidro)
        def caixilho(bm):  # cruz do caixilho
            caixa_local(bm, -0.025, 0.025, 0.012, 0.04, z0, z0 + alt)
            caixa_local(bm, -hl, hl, 0.012, 0.04, z0 + alt * 0.5 - 0.025, z0 + alt * 0.5 + 0.025)
        _objeto_de("caixilho_%s_%s" % (face, pos), caixilho, m_viga)
        if a.get("postigos", True):  # postigos abertos, encostados na parede
            def postigos(bm):
                caixa_local(bm, -hl - f - larg * 0.5, -hl - f, sai, sai + 0.05, z0, z0 + alt)
                caixa_local(bm, hl + f, hl + f + larg * 0.5, sai, sai + 0.05, z0, z0 + alt)
            _objeto_de("postigos_%s_%s" % (face, pos), postigos, m_tabua)


def _telhado(w, d, parede, cume, beiral, esp, rng):
    """Duas águas de colmo com espessura, beiral arredondado e irregularidade leve."""
    hw, hd = w / 2, d / 2
    inc = (cume - parede) / hd                 # subida por metro
    bm = bmesh.new()
    nx, ny, cursos = 22, 30, 5
    x0, x1 = -hw - beiral, hw + beiral
    for s in (-1, 1):                          # -1: água sul (-Y no Blender), +1: norte
        grade_cima, grade_baixo = [], []
        for j in range(ny + 1):
            f = j / ny                         # 0 no beiral, 1 na cumeeira
            yy = s * (hd + beiral) * (1 - f)
            zz = cume - inc * abs(yy)
            linha_c, linha_b = [], []
            for i in range(nx + 1):
                x = x0 + (x1 - x0) * i / nx
                ond = 0.04 * math.sin(x * 2.3 + j * 1.7) + rng.uniform(-0.02, 0.02)
                # fiadas: cada camada de colmo é mais grossa embaixo; o degrau faz uma linha de sombra
                fiada = 0.09 * (1.0 - ((f * cursos) % 1.0)) if 0 < f < 0.97 else 0.0
                linha_c.append(bm.verts.new((x, yy, zz + esp + ond + fiada)))
                linha_b.append(bm.verts.new((x, yy, zz)))
            grade_cima.append(linha_c)
            grade_baixo.append(linha_b)
        for j in range(ny):
            for i in range(nx):
                q = [grade_cima[j][i], grade_cima[j][i + 1], grade_cima[j + 1][i + 1], grade_cima[j + 1][i]]
                bm.faces.new(q if s < 0 else list(reversed(q)))
                q = [grade_baixo[j][i], grade_baixo[j + 1][i], grade_baixo[j + 1][i + 1], grade_baixo[j][i + 1]]
                bm.faces.new(q if s < 0 else list(reversed(q)))
        # borda do beiral (espessura) e das empenas
        for i in range(nx):
            q = [grade_baixo[0][i], grade_baixo[0][i + 1], grade_cima[0][i + 1], grade_cima[0][i]]
            bm.faces.new(q if s < 0 else list(reversed(q)))
        for j in range(ny):
            for i in (0, nx):
                q = [grade_baixo[j][i], grade_cima[j][i], grade_cima[j + 1][i], grade_baixo[j + 1][i]]
                bm.faces.new(q if (s < 0) == (i == 0) else list(reversed(q)))
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.001)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    ob = geo.objeto("telhado", bm, None, suave=True)
    m = ob.modifiers.new("sub", "SUBSURF")
    m.levels = m.render_levels = 1
    return ob
