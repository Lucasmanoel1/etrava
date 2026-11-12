"""Decoração do chão (SPEC-visual §7.2): tufos de grama, flores e pedrinhas.

A 2,2 cm por pixel de arte, uma folha de grama precisa de ~2,5 cm de largura para sobreviver
ao voto do pixelizador; os tufos são poucas folhas largas, que é como pixel art desenha grama.
"""
import math
import random

import bmesh
from mathutils import Vector

from nucleo import geo, materiais


def _folha(bm, base, altura, largura, inclina, direcao):
    """Folha de grama: tira afinando, de base até a ponta, inclinada para `direcao`."""
    d = Vector((math.cos(direcao), math.sin(direcao), 0))
    lado = Vector((-d.y, d.x, 0)) * (largura / 2)
    pontos = []
    n = 3
    for i in range(n + 1):
        f = i / n
        c = base + d * (inclina * altura * f * f) + Vector((0, 0, altura * f))
        w = 1.0 - f * 0.85
        pontos.append((bm.verts.new(c - lado * w), bm.verts.new(c + lado * w)))
    folhas = []
    for i in range(n):
        a, b = pontos[i]
        c, e = pontos[i + 1]
        folhas.append(bm.faces.new((a, b, e, c)))
    return folhas


class Tufo:
    def __init__(self, nome, semente, folhas=14, altura=(0.22, 0.46), raio=0.13, rampa="grama"):
        rng = random.Random(semente)
        self.mat = materiais.material("grama_" + rampa, rampa, k=0.0, oa=0.8, luz_min=0.2, linhas=False, contorno=False)
        self.lam = []
        for i in range(folhas):
            a = rng.uniform(0, 2 * math.pi)
            r = raio * math.sqrt(rng.random())
            self.lam.append(dict(base=Vector((math.cos(a) * r, math.sin(a) * r, 0)), h=rng.uniform(*altura),
                                 dire=a + rng.uniform(-0.6, 0.6), inc=rng.uniform(0.15, 0.6), fase=rng.random()))
        self.nome = nome
        self.ob = None

    def montar(self, t):
        import bpy
        if self.ob is not None:
            bpy.data.objects.remove(self.ob, do_unlink=True)
        bm = bmesh.new()
        for l in self.lam:
            vento = 0.25 * math.sin(2 * math.pi * (t + l["fase"] * 0.3))
            _folha(bm, l["base"], l["h"], 0.04, l["inc"] + vento, l["dire"] * 0.3 + 0.785 * (1 - 0.3))
        self.ob = geo.objeto(self.nome, bm, self.mat)
        return self.ob


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
