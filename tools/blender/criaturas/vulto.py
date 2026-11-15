"""O vulto (V3): o que mora na estrutura enterrada (content/enemies.yaml, Q-07). Referência em
arte-bruta/referencias/v3/vulto.png.

Não tem nome, não fala, não se explica (camada V1: "há algo sob a terra"). Nada nele confirma
fato V2/V3: sem rosto, sem olhos, sem símbolo, sem forma humana inteira. É uma massa curvada de
terra com raízes no lugar dos membros, trapos pendurados e uma fumaça escura que sobe dos ombros.

Usa o esqueleto humano (kit_humano.corpo), com braços mais longos e a curvatura no máximo, para
andar com o pé plantado (ciclos.andar) e bater com o tempo do golpe pesado (combate). A fumaça e
a borda dos trapos são translúcidas por pontilhado (opacidade no material, D-115).
"""
import math

import bpy

from anim import ciclos, combate
from kit_humano.corpo import Corpo, _bola, _caixa, _tubo, esqueleto
from nucleo import materiais


def _translucido(mat, opacidade):
    """Marca o material para o pixelizador sumir com parte dos pixels (Bayer). Fica no registro
    do material desta tarefa (materiais._MATS), sem mexer no núcleo compartilhado."""
    for info in materiais._MATS:
        if info["nome"] == mat.name:
            info["opacidade"] = opacidade
    return mat


def _irregular(ob, forca, escala, semente):
    """Superfície encaroçada (terra): deslocamento por ruído de nuvens."""
    tex = bpy.data.textures.new("ruido_" + ob.name, type="CLOUDS")
    tex.noise_scale = escala
    tex.noise_depth = 2
    m = ob.modifiers.new("terra", "DISPLACE")
    m.texture = tex
    m.strength = forca
    m.texture_coords = "LOCAL"
    m.mid_level = 0.5
    ob.location = ob.location            # (a semente entra pela posição local do ruído)
    tex.intensity = 1.0 + 0.01 * semente
    return ob


def montar(p):
    c = Corpo(p.get("corpo", {}))
    c.braco *= 1.40                      # braços que descem até o joelho
    c.antebraco *= 1.45
    c.ombros *= 1.55                     # os braços pendem por fora da massa, não dentro dela
    esqueleto(c)
    c.curvatura = p.get("jeito", {}).get("curvatura", 2.2)
    c.pele = {}
    c.organico = False
    k = c.k
    j = c.j
    terra = materiais.material("terra_viva", "vulto_terra", k=0.45, ruido=30.0, oa=0.8, luz_min=0.10)
    raiz = materiais.material("raiz", "vulto_raiz", k=0.30, ruido=60.0, oa=0.6, luz_min=0.12)
    trapo = materiais.material("trapo", "vulto_trapo", k=0.25, ruido=20.0, oa=0.6)   # sólido: pontilhado virava rede
    fumaca = _translucido(materiais.material("fumaca", "vulto_trapo", k=0.0, oa=0.0, luz_min=0.3,
                                             contorno=False, linhas=False), 0.40)
    c.mats = {"terra": terra, "trapo": trapo}
    # a massa: corcunda de terra nos ombros, tronco e quadril encaroçados, sem cabeça
    _irregular(_bola("corcunda", 0.25 * k, terra, j["peito"], centro=(-0.06 * k, 0, 0.10 * k), escala=(1.05, 1.15, 1.0), sub=2),
               0.07 * k, 0.12, 1)
    _irregular(_bola("ventre", 0.22 * k, terra, j["tronco"], centro=(0.02 * k, 0, 0.18 * k), escala=(0.85, 0.80, 1.6), sub=2),
               0.06 * k, 0.10, 2)
    _irregular(_bola("anca", 0.17 * k, terra, j["quadril"], centro=(0, 0, 0.02 * k), escala=(0.9, 1.1, 0.9), sub=2),
               0.04 * k, 0.10, 3)
    _irregular(_bola("toco", 0.11 * k, terra, j["pescoco"], centro=(0.07 * k, 0, 0.02 * k), escala=(1.1, 1.0, 0.8), sub=1),
               0.03 * k, 0.08, 4)                           # onde haveria cabeça, só um caroço
    # raízes claras que descem pela corcunda (como na referência): fios que pendem pelos lados e
    # pelas costas, rente à terra
    for i, (x, y, z, rx, ry) in enumerate(((-0.02, 0.24, 0.20, 0.25, 0.10), (-0.04, -0.24, 0.18, -0.25, 0.05),
                                           (-0.24, 0.10, 0.22, 0.10, -0.35), (-0.24, -0.08, 0.20, -0.10, -0.30))):
        fio = _tubo("raiz_dorso_%d" % i, 0.26 * k, 0.020 * k, 0.012 * k, raiz, j["peito"], sub=0, segmentos=5)
        fio.location = (x * k, y * k, z * k)
        fio.rotation_euler = (rx, ry, 0.0)
    c.massa = [ob for ob in bpy.data.objects if ob.name in ("corcunda", "ventre", "anca", "toco")]
    # membros de raiz: finos e longos; a mão abre em quatro raízes; o pé, em três no chão
    for lado, s in (("e", 1), ("d", -1)):
        _tubo("braco_" + lado, c.braco + 0.04 * k, 0.070 * k, 0.055 * k, terra, j["braco_" + lado], z0=0.04 * k, sub=1)
        _tubo("antebraco_" + lado, c.antebraco, 0.052 * k, 0.040 * k, terra, j["antebraco_" + lado], sub=1)
        _tubo("veio_" + lado, c.antebraco * 0.9, 0.018 * k, 0.014 * k, raiz, j["antebraco_" + lado], sub=0,
              segmentos=5).location = (0.034 * k, s * 0.02 * k, 0.0)       # na superfície, à frente
        for i, (ax, ay) in enumerate(((0.12, 0.18), (0.0, -0.10), (-0.08, 0.22))):
            dedo = _tubo("dedo_%s%d" % (lado, i), 0.28 * k, 0.024 * k, 0.010 * k, terra, j["mao_" + lado], sub=0,
                         segmentos=5)
            dedo.rotation_euler = (ay * s, ax, 0.0)              # dedos de raiz que pendem quase até o chão
        _tubo("coxa_" + lado, c.coxa + 0.03 * k, 0.075 * k, 0.058 * k, terra, j["coxa_" + lado], z0=0.03 * k, sub=1)
        _tubo("canela_" + lado, c.canela, 0.058 * k, 0.048 * k, terra, j["canela_" + lado], sub=1)
        _tubo("veio_perna_" + lado, c.canela * 0.8, 0.016 * k, 0.012 * k, raiz, j["canela_" + lado], sub=0,
              segmentos=5).location = (0.04 * k, -s * 0.015 * k, 0.0)
        _irregular(_bola("pe_terra_" + lado, 0.07 * k, terra, j["pe_" + lado], centro=(0.02 * k, 0, -0.04 * k),
                         escala=(1.5, 1.2, 0.6), sub=1), 0.02 * k, 0.05, 5)
    # trapos pendurados da cintura e dos ombros (borda translúcida)
    for i, (jn, x, y, z, comp, incl) in enumerate((
            ("tronco", 0.12, 0.10, 0.10, 0.42, 0.10), ("tronco", 0.10, -0.12, 0.08, 0.36, -0.12),
            ("tronco", -0.13, 0.04, 0.12, 0.48, 0.05), ("quadril", 0.08, 0.0, 0.04, 0.38, 0.0),
            ("peito", -0.20, 0.18, 0.20, 0.40, 0.25), ("peito", -0.22, -0.16, 0.18, 0.34, -0.2))):
        tr = _caixa("trapo_%d" % i, (0.012 * k, 0.09 * k, comp * k), (x * k, y * k, (z - comp / 2) * k), trapo, j[jn], sub=0)
        tr.rotation_euler = (incl, 0.0, 0.0)
    # fumaça que sobe dos ombros (três novelos que o ciclo faz subir e trocar)
    c.fumaca = []
    for i, (x, y) in enumerate(((-0.05, 0.12), (-0.12, -0.10), (0.02, -0.02))):
        f = _bola("fumaca_%d" % i, 0.10 * k, fumaca, j["peito"], centro=(0, 0, 0), escala=(0.8, 0.8, 1.4), sub=1)
        f.location = (x * k, y * k, 0.40 * k)
        c.fumaca.append((f, x * k, y * k, i / 3.0))
    return c


def fumaca(c, t):
    """Os novelos sobem 30 cm por ciclo e voltam embaixo (laço), um pouco de lado."""
    for f, x, y, fase in c.fumaca:
        u = (t + fase) % 1.0
        f.location = (x - 0.06 * c.k * u, y + 0.03 * c.k * math.sin(6.28 * u), (0.32 + 0.30 * u) * c.k)
        f.scale = (0.7 + 0.6 * u,) * 3


def desfazer(c, t, p):
    """Abatido, o vulto afunda e se desfaz: a massa desce até o chão, os braços caem, a terra fica
    translúcida no fim (o pixelizador troca de material pelos quadros, ver tarefas/vulto.py)."""
    ciclos.parado(c, 0.0, p)
    u = min(1.0, t / (5.0 / 6.0))
    k = c.k
    c.j["quadril"].location.z = c.quadril_h * (1 - 0.85 * u)
    c.j["tronco"].rotation_euler.y += math.radians(35 * u)
    for lado in ("e", "d"):
        ciclos.balanco(c.j["braco_" + lado], math.radians(-10 * u))
        ciclos.balanco(c.j["coxa_" + lado], math.radians(80 * u))
        ciclos.balanco(c.j["canela_" + lado], math.radians(-110 * u))
    c.j["quadril"].location.x = -0.10 * k * u


CICLOS = {"parado": ciclos.parado, "andar": ciclos.andar, "golpe": combate.golpe_pesado, "dano": combate.dano,
          "desfazer": desfazer}
