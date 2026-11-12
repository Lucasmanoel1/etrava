"""Árvores por espécie (V1: carvalho e bétula; V4: as outras).

A copa é feita de tufos (esferas irregulares de folha) espalhados num envelope por amostragem
com distância mínima; cada tufo recebe um galho que sai do tronco ou de um galho-mestre. A
32 px por metro de largura, o que se lê é a massa dos tufos, os vãos entre eles e um pedaço de
galho aqui e ali — é isso que o modelo prioriza. O vento mexe cada tufo com fase própria.
"""
import math
import random

import bmesh
from mathutils import Vector

from nucleo import geo, materiais

ESPECIES = {
    "carvalho": dict(rampa_folha="folha_carvalho", rampa_casca="casca_carvalho", tronco_r=0.24, tronco_h=0.48,
                     mestres=4, abertura=0.95, envelope=(0.5, 0.5, 0.30), centro=0.68, tufo=(0.36, 0.56),
                     dist=0.80, alongar=1.0, cair=0.0, galho_r=0.09, k_casca=0.8, textura_casca="casca.png", vaos=4,
                     ruido_casca=9.0),
    "betula": dict(rampa_folha="folha_betula", rampa_casca="casca_betula", tronco_r=0.12, tronco_h=0.82,
                   mestres=5, abertura=0.55, envelope=(0.5, 0.5, 0.36), centro=0.64, tufo=(0.22, 0.36),
                   dist=0.85, alongar=1.45, cair=0.18, galho_r=0.035, k_casca=1.4, textura_casca=None, vaos=5,
                   ruido_casca=14.0),
}


def _tubo(bm, a, b, ra, rb, seg=7):
    """Tubo de a até b (cone com tampas) dentro de um bmesh."""
    d = b - a
    comp = d.length
    if comp < 1e-4:
        return
    res = bmesh.ops.create_cone(bm, cap_ends=True, segments=seg, radius1=ra, radius2=rb, depth=comp)
    rot = d.normalized().to_track_quat("Z", "Y").to_matrix().to_4x4()
    for v in res["verts"]:
        co = rot @ (v.co + Vector((0, 0, comp / 2)))
        v.co = co + a


def _tufo(nome, centro, raio, rng, material, alongar):
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=2, radius=raio)
    fase = [rng.uniform(0, 6.28) for _ in range(3)]
    for v in bm.verts:
        n = v.co.normalized()
        calombo = 0.22 * math.sin(4.1 * n.x + fase[0]) * math.sin(3.7 * n.y + fase[1]) + 0.16 * math.sin(5.3 * n.z + fase[2])
        v.co = v.co * (1.0 + calombo + rng.uniform(-0.16, 0.16))
        v.co.z *= alongar
        if v.co.z < -raio * 0.35:          # tufo achatado embaixo: a sombra fica no fundo
            v.co.z = -raio * 0.35 + (v.co.z + raio * 0.35) * 0.4
    ob = geo.objeto(nome, bm, material, suave=True)
    ob.location = centro
    return ob


def construir(especie, altura, copa, semente):
    """Monta a árvore; devolve a lista de (tufo, posição base, fase, amplitude) para o vento."""
    e = ESPECIES[especie]
    rng = random.Random(semente)
    casca = materiais.material("casca_" + especie, e["rampa_casca"], k=e["k_casca"], textura=e["textura_casca"],
                               escala=1.2, ruido=e["ruido_casca"], oa=0.8)
    folha = materiais.material("folha_" + especie, e["rampa_folha"], k=1.1, textura="folha.png", escala=1.0,
                               ruido=7.0, var_objeto=0.10, oa=1.0, linhas=False)
    centro_z = altura * e["centro"]
    rx, ry, rz = copa * e["envelope"][0], copa * e["envelope"][1], altura * e["envelope"][2]

    # pontos dos tufos: amostragem no envelope com distância mínima (determinística pela semente)
    pontos = []
    tentativas = 0
    lob = [rng.uniform(0, 6.28) for _ in range(3)]
    # vãos na copa (por onde aparecem galho e céu), mais do lado de fora
    vaos = []
    for _ in range(e.get("vaos", 3)):
        d = Vector((rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-0.6, 0.6))).normalized()
        vaos.append((d * rng.uniform(0.8, 1.05), rng.uniform(0.28, 0.42)))
    while tentativas < 4000:
        tentativas += 1
        p = Vector((rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-1, 1)))
        if p.length < 1e-3:
            continue
        nrm = p.normalized()
        borda = 1.0 + 0.22 * math.sin(3 * nrm.x + lob[0]) * math.cos(2 * nrm.y + lob[1]) + 0.14 * math.sin(4 * nrm.z + lob[2])
        if p.length > borda or p.length < 0.45:   # só a casca da copa: o miolo não se vê
            continue
        if any((p - c).length < cr for c, cr in vaos):
            continue
        # copa mais cheia em cima, mais rala embaixo
        if p.z < -0.3 and rng.random() < 0.5:
            continue
        q = Vector((p.x * rx, p.y * ry, p.z * rz + centro_z))
        r = rng.uniform(*e["tufo"]) * (1.0 + 0.25 * max(0.0, p.length - 0.6))
        if all((q - o).length > e["dist"] * (r + ro) for o, ro in pontos):
            pontos.append((q, r))
    # tronco e galhos-mestres
    bm = bmesh.new()
    topo = Vector((0, 0, altura * e["tronco_h"]))
    inclina = Vector((rng.uniform(-0.15, 0.15), rng.uniform(-0.15, 0.15), 0)) * altura * 0.05
    _tubo(bm, Vector((0, 0, -0.05)), topo + inclina, e["tronco_r"] * 1.25, e["tronco_r"] * 0.8, seg=9)
    # raízes: um alargamento na base
    for i in range(5):
        a = i * 2 * math.pi / 5 + rng.uniform(-0.3, 0.3)
        _tubo(bm, Vector((0, 0, 0.25)), Vector((math.cos(a) * e["tronco_r"] * 2.2, math.sin(a) * e["tronco_r"] * 2.2, -0.02)),
              e["tronco_r"] * 0.5, e["tronco_r"] * 0.15, seg=5)
    mestres = []
    for i in range(e["mestres"]):
        a = i * 2 * math.pi / e["mestres"] + rng.uniform(-0.4, 0.4)
        sobe = altura * rng.uniform(0.18, 0.30)
        fim = topo + inclina + Vector((math.cos(a) * copa * 0.28 * e["abertura"], math.sin(a) * copa * 0.28 * e["abertura"], sobe))
        _tubo(bm, topo + inclina - Vector((0, 0, 0.3)), fim, e["tronco_r"] * 0.62, e["tronco_r"] * 0.35, seg=7)
        mestres.append(fim)
    if especie == "betula":  # o tronco da bétula segue até o alto da copa
        _tubo(bm, topo + inclina, Vector((inclina.x * 1.5, inclina.y * 1.5, altura * 0.92)), e["tronco_r"] * 0.8,
              e["tronco_r"] * 0.25, seg=7)
        mestres.append(Vector((inclina.x * 1.5, inclina.y * 1.5, altura * 0.85)))
    for q, r in pontos:
        base = min(mestres, key=lambda m: (m - q).length)
        meio = (base + q) / 2 + Vector((0, 0, -e["cair"] * (q - base).length))
        _tubo(bm, base, meio, e["galho_r"], e["galho_r"] * 0.75, seg=5)
        _tubo(bm, meio, q, e["galho_r"] * 0.75, e["galho_r"] * 0.4, seg=5)
    tronco = geo.objeto("tronco", bm, casca, suave=False)
    geo.subdividir(tronco, 1)

    tufos = []
    pontos.sort(key=lambda t: (t[0].z, t[0].x, t[0].y))
    for i, (q, r) in enumerate(pontos):
        q2 = q + Vector((0, 0, -e["cair"] * r))
        ob = _tufo("tufo%03d" % i, q2, r, rng, folha, e["alongar"])
        amp = 0.035 + 0.05 * max(0.0, (q.z - altura * 0.35) / altura)
        tufos.append((ob, q2.copy(), rng.uniform(0, 1), amp))
    return tufos


def vento(tufos, t):
    """Posição dos tufos no instante t ∈ [0, 1) do laço de vento (rajada para o leste da tela)."""
    dire = Vector((0.7, 0.7, 0.0))
    for ob, base, fase, amp in tufos:
        s = math.sin(2 * math.pi * (t + fase))
        ob.location = base + dire * (amp * s) + Vector((0, 0, -0.25 * amp * abs(s)))
        ob.rotation_euler = (0.0, 0.0, 0.05 * s)
