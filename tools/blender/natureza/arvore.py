"""Árvores por espécie (V1: carvalho e bétula; V4: as outras).

Copa como o pixel artista desenha (D-098): poucas massas grandes de folha, cada uma com a borda
recortada em cachos (esferas pequenas, 4 a 6 px na tela), lisas, sem textura — a luz em faixas
dá a cada cacho o seu realce no alto à esquerda e a sombra embaixo. Nada de ruído por pixel
(era o que dava cara de "folha por folha"). O vento balança a copa inteira e cada massa um
pouco, com amplitude de um pixel ou dois.
"""
import math
import random

import bmesh
from mathutils import Vector

from nucleo import geo, materiais

ESPECIES = {
    # massas: quantas massas grandes de folha; cacho: raio dos cachos que fazem a borda recortada
    "carvalho": dict(rampa_folha="folha_carvalho", rampa_casca="casca_carvalho", tronco_r=0.22, tronco_h=0.50,
                     mestres=4, abertura=0.95, envelope=(0.5, 0.5, 0.30), centro=0.68, massa=(0.62, 0.85),
                     massas=9, anel=(0.48, 0.66), cacho=(0.13, 0.20), cachos=26, alongar=0.85, cair=0.0, galho_r=0.08, k_casca=0.35,
                     textura_casca="casca.png", ruido_casca=9.0),
    "betula": dict(rampa_folha="folha_betula", rampa_casca="casca_betula", tronco_r=0.10, tronco_h=0.55,
                   mestres=4, abertura=0.45, envelope=(0.42, 0.42, 0.40), centro=0.64, massa=(0.40, 0.52),
                   massas=9, anel=(0.40, 0.55), cacho=(0.10, 0.16), cachos=20, alongar=1.25, cair=0.12, galho_r=0.035, k_casca=0.9,
                   textura_casca=None, ruido_casca=14.0),
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


def _bola(bm, centro, raio, achatar=0.35):
    res = bmesh.ops.create_icosphere(bm, subdivisions=2, radius=raio)
    for v in res["verts"]:
        if v.co.z < -raio * achatar:          # achatada embaixo: a sombra fica no fundo
            v.co.z = -raio * achatar + (v.co.z + raio * achatar) * 0.4
        v.co += centro


def _massa(nome, centro, raio, rng, material, e):
    """Uma massa de folha: um miolo e cachos na casca de cima e de fora (borda recortada)."""
    bm = bmesh.new()
    _bola(bm, Vector((0, 0, 0)), raio * 0.86)
    n = int(e["cachos"] * (raio / e["massa"][1]) ** 2) + 6
    for _ in range(n):
        d = Vector((rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-0.35, 1))).normalized()
        r = rng.uniform(*e["cacho"])
        _bola(bm, d * raio * rng.uniform(0.80, 0.92), r, achatar=0.2)
    for v in bm.verts:
        v.co.z *= e["alongar"]
    ob = geo.objeto(nome, bm, material, suave=True)
    ob.location = centro
    return ob


def construir(especie, altura, copa, semente):
    """Monta a árvore; devolve a lista de (massa, posição base, fase, amplitude) para o vento."""
    e = ESPECIES[especie]
    rng = random.Random(semente)
    casca = materiais.material("casca_" + especie, e["rampa_casca"], k=e["k_casca"], textura=e["textura_casca"],
                               escala=1.2, ruido=e["ruido_casca"], oa=0.5, sem_sombra=0.8)
    # folha lisa: nada de textura nem ruído; a forma (cachos) faz o desenho
    folha = materiais.material("folha_" + especie, e["rampa_folha"], k=0.0, oa=0.55, linhas=True)
    centro_z = altura * e["centro"]
    rx, ry, rz = copa * e["envelope"][0], copa * e["envelope"][1], altura * e["envelope"][2]

    # massas: uma no alto, as outras num anel irregular em volta e abaixo (determinístico)
    pontos = [(Vector((0, 0, centro_z + rz * 0.45)), e["massa"][1])]
    giro = rng.uniform(0, 6.28)
    nm = e["massas"] - 1
    for i in range(nm):
        a = giro + i * 2 * math.pi / nm + rng.uniform(-0.25, 0.25)
        anel = rng.uniform(*e["anel"])
        z = (0.25 if i % 2 else -0.35) + rng.uniform(-0.12, 0.12)
        q = Vector((math.cos(a) * rx * anel, math.sin(a) * ry * anel, centro_z + z * rz))
        pontos.append((q, rng.uniform(*e["massa"])))
    # tronco e galhos-mestres
    bm = bmesh.new()
    topo = Vector((0, 0, altura * e["tronco_h"]))
    inclina = Vector((rng.uniform(-0.15, 0.15), rng.uniform(-0.15, 0.15), 0)) * altura * 0.05
    # tronco afinando, com a base um pouco mais larga (sem "pé" separado)
    _tubo(bm, Vector((0, 0, -0.05)), topo + inclina, e["tronco_r"] * 1.3, e["tronco_r"] * 0.75, seg=10)
    mestres = []
    for i in range(e["mestres"]):
        a = i * 2 * math.pi / e["mestres"] + rng.uniform(-0.4, 0.4)
        sobe = altura * rng.uniform(0.18, 0.30)
        fim = topo + inclina + Vector((math.cos(a) * copa * 0.28 * e["abertura"], math.sin(a) * copa * 0.28 * e["abertura"], sobe))
        fim.z = min(fim.z, centro_z + rz * 0.15)   # galho nunca fura a copa
        _tubo(bm, topo + inclina - Vector((0, 0, 0.3)), fim, e["tronco_r"] * 0.62, e["tronco_r"] * 0.35, seg=7)
        mestres.append(fim)
    if especie == "betula":  # o tronco da bétula segue até o alto da copa
        alto = centro_z + rz * 0.35
        _tubo(bm, topo + inclina, Vector((inclina.x * 1.5, inclina.y * 1.5, alto)), e["tronco_r"] * 0.8,
              e["tronco_r"] * 0.3, seg=7)
        mestres.append(Vector((inclina.x * 1.5, inclina.y * 1.5, alto)))
    for q, r in pontos:
        base = min(mestres, key=lambda m: (m - q).length)
        _tubo(bm, base, q, e["galho_r"], e["galho_r"] * 0.5, seg=5)
    # sem subdivisão: ela arredonda as pontas dos tubos (tronco em ponta, base virando bola)
    geo.objeto("tronco", bm, casca, suave=True)

    massas = []
    pontos.sort(key=lambda t: (t[0].z, t[0].x, t[0].y))
    for i, (q, r) in enumerate(pontos):
        q2 = q + Vector((0, 0, -e["cair"] * r))
        ob = _massa("tufo%03d" % i, q2, r, rng, folha, e)
        amp = 0.02 + 0.03 * max(0.0, (q.z - altura * 0.35) / altura)
        massas.append((ob, q2.copy(), rng.uniform(0, 0.25), amp))
    return massas


def vento(massas, t):
    """Posição das massas no instante t ∈ [0, 1) do laço de vento: a copa balança junta, cada massa
    um pouco atrasada (fase pequena), para não cintilar."""
    dire = Vector((0.7, 0.7, 0.0))
    for ob, base, fase, amp in massas:
        s = math.sin(2 * math.pi * (t + fase))
        ob.location = base + dire * (amp * s)
