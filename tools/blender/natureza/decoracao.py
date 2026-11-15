"""Decoração do chão (SPEC-visual §7.2): tufos de grama, flores e pedrinhas.

A 2,2 cm por pixel de arte, uma folha de grama precisa de ~2,5 cm de largura para sobreviver
ao voto do pixelizador; os tufos são poucas folhas largas, que é como pixel art desenha grama.
"""
import math
import random

import bmesh
from mathutils import Vector, noise

from nucleo import geo, materiais


def _folha(bm, base, altura, largura, inclina, direcao, n=3, serra=0.0, arco=0.0):
    """Folha de grama: tira afinando, de base até a ponta, inclinada para `direcao`.
    `serra` alterna a largura a cada segmento (fronde de samambaia); `arco` faz a ponta cair."""
    d = Vector((math.cos(direcao), math.sin(direcao), 0))
    lado = Vector((-d.y, d.x, 0)) * (largura / 2)
    pontos = []
    for i in range(n + 1):
        f = i / n
        c = base + d * (inclina * altura * f * f) + Vector((0, 0, altura * f - arco * altura * f ** 3))
        w = (1.0 - f * 0.85) * (1.0 + (serra if i % 2 else -serra * 0.5))
        pontos.append((bm.verts.new(c - lado * w), bm.verts.new(c + lado * w)))
    folhas = []
    for i in range(n):
        a, b = pontos[i]
        c, e = pontos[i + 1]
        folhas.append(bm.faces.new((a, b, e, c)))
    return folhas, pontos[-1]


class Tufo:
    """Moita de lâminas com laço de vento: grama, capim alto (com espiga), juncos, samambaia."""

    def __init__(self, nome, semente, folhas=14, altura=(0.22, 0.46), raio=0.13, rampa="grama", largura=0.04,
                 inc=(0.15, 0.6), segmentos=3, serra=0.0, arco=0.0, espiga=None, vento=0.25):
        rng = random.Random(semente)
        self.mat = materiais.material("grama_" + rampa, rampa, k=0.0, oa=0.8, luz_min=0.2, linhas=False, contorno=False)
        self.mat_espiga = None
        if espiga:
            self.mat_espiga = materiais.material("espiga_" + espiga["rampa"], espiga["rampa"], k=0.0, oa=0.5,
                                                 luz_min=0.2, linhas=False, contorno=False)
        self.espiga = espiga
        self.larg, self.seg, self.serra, self.arco, self.amp = largura, segmentos, serra, arco, vento
        self.lam = []
        for i in range(folhas):
            a = rng.uniform(0, 2 * math.pi)
            r = raio * math.sqrt(rng.random())
            self.lam.append(dict(base=Vector((math.cos(a) * r, math.sin(a) * r, 0)), h=rng.uniform(*altura),
                                 dire=a + rng.uniform(-0.6, 0.6), inc=rng.uniform(*inc), fase=rng.random(),
                                 cabeca=espiga is not None and rng.random() < espiga.get("fracao", 0.5)))
        self.nome = nome
        self.obs = []

    def montar(self, t):
        import bpy
        for ob in self.obs:
            bpy.data.objects.remove(ob, do_unlink=True)
        self.obs = []
        bm = bmesh.new()
        topos = []
        for l in self.lam:
            vento = self.amp * math.sin(2 * math.pi * (t + l["fase"] * 0.3))
            _, topo = _folha(bm, l["base"], l["h"], self.larg, l["inc"] + vento, l["dire"] * 0.3 + 0.785 * (1 - 0.3),
                             n=self.seg, serra=self.serra, arco=self.arco)
            if l["cabeca"]:
                topos.append((topo[0].co + topo[1].co) / 2)
        self.obs.append(geo.objeto(self.nome, bm, self.mat))
        if topos:
            bm = bmesh.new()
            comp = self.espiga.get("comprimento", 0.07)
            for c in topos:
                g = bmesh.ops.create_icosphere(bm, subdivisions=1, radius=1.0)
                for v in g["verts"]:
                    v.co = Vector((v.co.x * comp * 0.35, v.co.y * comp * 0.35, v.co.z * comp * 0.5)) + c
            self.obs.append(geo.objeto(self.nome + "_espiga", bm, self.mat_espiga, suave=True))
        return self.obs


def flores(nome, semente, rampa, n=4):
    """Pequeno tufo com cabeças de flor."""
    rng = random.Random(semente)
    m = materiais.material("flor_" + rampa, rampa, k=0.0, oa=0.5, linhas=False, contorno=False)
    hastes = materiais.material("haste", "grama", k=0.0, oa=0.8, linhas=False, tons=[1, 4], contorno=False)
    bm = bmesh.new()
    for i in range(6):
        a = rng.uniform(0, 2 * math.pi)
        _folha(bm, Vector((math.cos(a) * 0.05, math.sin(a) * 0.05, 0)), rng.uniform(0.10, 0.18), 0.03, 0.4, a)
    geo.objeto(nome + "_hastes", bm, hastes)
    for i in range(n):
        a = rng.uniform(0, 2 * math.pi)
        r = rng.uniform(0.0, 0.09)
        h = rng.uniform(0.14, 0.26)
        bm = bmesh.new()
        bmesh.ops.create_icosphere(bm, subdivisions=1, radius=rng.uniform(0.028, 0.04))
        for v in bm.verts:
            v.co += Vector((math.cos(a) * r, math.sin(a) * r, h))
        geo.objeto("%s_%d" % (nome, i), bm, m, suave=True)


def pedrinhas(nome, semente, n=4):
    rng = random.Random(semente)
    m = materiais.material("pedrinha", "pedra", k=0.0, oa=0.6, var_objeto=0.12)
    for i in range(n):
        bm = bmesh.new()
        r = rng.uniform(0.035, 0.07)
        bmesh.ops.create_icosphere(bm, subdivisions=1, radius=r)
        a = rng.uniform(0, 2 * math.pi)
        d = rng.uniform(0, 0.12)
        for v in bm.verts:
            v.co.z *= 0.55
            v.co += Vector((math.cos(a) * d, math.sin(a) * d, r * 0.25))
        geo.objeto("%s_%d" % (nome, i), bm, m, suave=True)


def cogumelos(nome, semente, n=4):
    """Touceira de cogumelos: pé claro e chapéu achatado, de tamanhos diferentes."""
    rng = random.Random(semente)
    pe = materiais.material("cogumelo_pe", "linho", k=0.0, oa=0.6, tons=[2, 5])
    chapeu = materiais.material("cogumelo_chapeu", "cogumelo", k=0.0, oa=0.5, var_objeto=0.15)
    for i in range(n):
        a = rng.uniform(0, 2 * math.pi)
        d = rng.uniform(0, 0.08)
        h = rng.uniform(0.07, 0.13) * (1.0 if i == 0 else 0.75)
        r = rng.uniform(0.06, 0.085) * (1.0 if i == 0 else 0.7)
        base = Vector((math.cos(a) * d, math.sin(a) * d, 0))
        bm = bmesh.new()
        bmesh.ops.create_cone(bm, cap_ends=True, segments=8, radius1=r * 0.35, radius2=r * 0.28, depth=h)
        for v in bm.verts:
            v.co += base + Vector((0, 0, h / 2))
        geo.objeto("%s_pe%d" % (nome, i), bm, pe, suave=True)
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=10, v_segments=6, radius=r)
        for v in bm.verts:
            v.co.z = max(v.co.z, -r * 0.1) * 0.6
            v.co += base + Vector((0, 0, h))
        geo.objeto("%s_ch%d" % (nome, i), bm, chapeu, suave=True)


def folhas_chao(nome, semente, rampa, n=9):
    """Folhas caídas, deitadas no chão (decalque): losangos curvos, cada um num ângulo."""
    rng = random.Random(semente)
    m = materiais.material("folha_chao_" + rampa, rampa, k=0.0, oa=0.3, var_objeto=0.25, linhas=False, contorno=False)
    for i in range(n):
        a = rng.uniform(0, 2 * math.pi)
        d = 0.20 * math.sqrt(rng.random())
        c = Vector((math.cos(a) * d, math.sin(a) * d, 0.006))
        g = rng.uniform(0, math.pi)
        u = Vector((math.cos(g), math.sin(g), 0)) * rng.uniform(0.06, 0.085)
        w = Vector((-u.y, u.x, 0)) * 0.6
        bm = bmesh.new()
        vs = [bm.verts.new(c - u), bm.verts.new(c + w + Vector((0, 0, 0.008))), bm.verts.new(c + u),
              bm.verts.new(c - w + Vector((0, 0, 0.008)))]
        bm.faces.new(vs)
        geo.objeto("%s_%d" % (nome, i), bm, m)


def pedregulhos(nome, semente, n=3, tamanho=(0.12, 0.30), musgo=0.0):
    """Pedras maiores, meio enterradas, de faces quebradas (não esferas lisas)."""
    rng = random.Random(semente)
    m = materiais.material("pedregulho", "pedra", k=0.45, ruido=6.0, oa=0.7, var_objeto=0.12)
    mm = materiais.material("musgo", "folha_samambaia", k=0.2, ruido=9.0, oa=0.6, linhas=False) if musgo else None
    for i in range(n):
        r = rng.uniform(*tamanho) * (1.0 if i == 0 else 0.6)
        bm = bmesh.new()
        bmesh.ops.create_icosphere(bm, subdivisions=1, radius=r)
        sem = Vector((rng.uniform(0, 40), rng.uniform(0, 40), 0))
        for v in bm.verts:
            f = 1.0 + 0.30 * noise.noise(v.co.normalized() * 1.5 + sem)
            v.co *= f
            v.co.z = v.co.z * 0.62
        a = rng.uniform(0, 2 * math.pi)
        d = 0.0 if i == 0 else rng.uniform(0.2, 0.35)
        for v in bm.verts:
            v.co += Vector((math.cos(a) * d, math.sin(a) * d, r * 0.18))
        if mm is not None:
            for f in bm.faces:
                f.material_index = 1 if f.normal.z > 0.75 and rng.random() < musgo else 0
        ob = geo.objeto("%s_%d" % (nome, i), bm, m)
        if mm is not None:
            ob.data.materials.append(mm)


def tronco_caido(nome, semente, comprimento=2.2, raio=0.17):
    """Tronco caído, meio afundado no capim: casca nas laterais, o corte claro numa ponta e a
    outra ponta quebrada; uns galhos tocos."""
    from natureza import arvore
    rng = random.Random(semente)
    casca = materiais.material("casca_caido", "casca_carvalho", k=0.55, ruido=1.0, ruido_eixos=(2.0, 12.0, 12.0),
                               oa=0.5)
    corte = materiais.material("corte_caido", "madeira_clara", k=0.0, oa=0.3)
    ang = rng.uniform(-0.4, 0.4) + math.pi / 4
    d = Vector((math.cos(ang), math.sin(ang), 0))
    pts = []
    for k in range(9):
        f = k / 8
        p = d * (f - 0.5) * comprimento + Vector((0, 0, raio * 0.75))
        p += Vector((-d.y, d.x, 0)) * 0.06 * math.sin(f * 3.0 + semente)
        pts.append((p, raio * (1.0 - 0.25 * f)))
    galhos = [pts]
    for k in range(rng.randint(1, 3)):
        f = rng.uniform(0.3, 0.85)
        b = d * (f - 0.5) * comprimento + Vector((0, 0, raio * 0.9))
        dd = Vector((-d.y * rng.choice((-1, 1)), d.x, 0.7)).normalized()
        galhos.append([(b, raio * 0.35), (b + dd * rng.uniform(0.2, 0.35), raio * 0.18)])
    arvore._curvas(nome, galhos, casca)
    bm = bmesh.new()
    bmesh.ops.create_circle(bm, cap_ends=True, segments=14, radius=raio * 0.98)
    rot = Vector((0, 0, 1)).rotation_difference(-d)
    for v in bm.verts:
        v.co = rot @ v.co + pts[0][0] - d * 0.005
    geo.objeto(nome + "_corte", bm, corte)
