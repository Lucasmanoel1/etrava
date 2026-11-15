"""O lobo (V3): quadrúpede magro de inverno (referência em arte-bruta/referencias/v3/lobo*.png).

Esqueleto de vazios, como o humano: raiz → quadril (anca) → tronco → peito (cernelha) →
pescoço → cabeça → mandíbula; pernas de três segmentos (a de trás em zigue-zague: coxa, canela,
jarrete) e pata; cauda em dois elos. Peças rígidas arredondadas presas às juntas; o pelo é
material com ruído, não geometria.

Medidas em metros (corpo de 1,0): cernelha a ~0,70 m, do focinho à anca ~1,05 m. Tudo um pouco
mais grosso que o real (a correção vertical achata e o fino some no voto).

Convenção de giro (a mesma do humano): em Y, ângulo positivo leva +X para baixo; num membro que
pende, ângulo negativo leva o membro para a frente.
"""
import math

import bmesh
import bpy
from mathutils import Vector

from kit_humano.corpo import _bola, _caixa, _pai, _tubo, ponto
from nucleo import camera, geo, materiais

# repouso das pernas (rad): a de trás em zigue-zague, a da frente quase reta
REPOUSO = {
    "coxa": -0.55, "canela": 1.15, "jarrete": -0.60, "pata_t": 0.05,
    "braco": 0.18, "antebraco": -0.16, "pata_f": -0.02,
}
COMP = {"coxa": 0.24, "canela": 0.24, "jarrete": 0.15, "braco": 0.26, "antebraco": 0.25}


class Lobo:
    def __init__(self, p):
        self.k = p.get("tamanho", 1.0)
        self.j = {}
        self.altura_base = 0.0      # ajustada no montar: a pata pousa em z = 0 no repouso


def _junta(c, nome, pai, local):
    v = geo.vazio(nome, pai=pai, local=tuple(x * c.k for x in local))
    v.rotation_mode = "XYZ"
    c.j[nome] = v
    return v


def esqueleto(c):
    r = _junta(c, "raiz", camera.raiz(), (0, 0, 0))   # a raiz da câmera, pelo objeto (o nome é o mesmo)
    q = _junta(c, "quadril", r, (-0.45, 0, 0.60))  # a raiz (posição do jogo) fica no meio do corpo
    t = _junta(c, "tronco", q, (0, 0, 0))
    pt = _junta(c, "peito", t, (0.58, 0, 0.04))
    pc = _junta(c, "pescoco", pt, (0.10, 0, 0.06))
    cb = _junta(c, "cabeca", pc, (0.17, 0, 0.12))
    _junta(c, "mandibula", cb, (0.02, 0, -0.035))
    c1 = _junta(c, "cauda1", q, (-0.12, 0, 0.03))
    _junta(c, "cauda2", c1, (-0.16, 0, 0))
    for lado, s in (("e", 1), ("d", -1)):
        cx = _junta(c, "coxa_" + lado, q, (0.0, s * 0.075, -0.02))
        cn = _junta(c, "canela_" + lado, cx, (0, 0, -COMP["coxa"]))
        jr = _junta(c, "jarrete_" + lado, cn, (0, 0, -COMP["canela"]))
        _junta(c, "pata_t_" + lado, jr, (0, 0, -COMP["jarrete"]))
        om = _junta(c, "braco_" + lado, pt, (0.02, s * 0.075, -0.10))
        ab = _junta(c, "antebraco_" + lado, om, (0, 0, -COMP["braco"]))
        _junta(c, "pata_f_" + lado, ab, (0, 0, -COMP["antebraco"]))
    return c


def _lathe(nome, aneis, material, junta_, segs=14, sub=1):
    """Corpo deitado ao longo de X: aneis = [(x, meia_largura_y, meia_altura_z, dz)]."""
    bm = bmesh.new()
    voltas = []
    for x, ly, lz, dz in aneis:
        voltas.append([bm.verts.new((x, ly * math.sin(2 * math.pi * i / segs), dz + lz * math.cos(2 * math.pi * i / segs)))
                       for i in range(segs)])
    for a in range(len(voltas) - 1):
        for i in range(segs):
            j = (i + 1) % segs
            bm.faces.new((voltas[a][i], voltas[a][j], voltas[a + 1][j], voltas[a + 1][i]))
    bm.faces.new(list(reversed(voltas[0])))
    bm.faces.new(voltas[-1])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    ob = geo.objeto(nome, bm, material, suave=True)
    _pai(ob, junta_)
    if sub:
        geo.subdividir(ob, sub)
    return ob


def _orelha(nome, material, junta_, centro, k):
    """Orelha em pirâmide (3 px a 48 px): base larga, ponta para cima e um pouco para trás."""
    bm = bmesh.new()
    cx, cy, cz = centro
    base = [bm.verts.new((cx + 0.03 * k * math.cos(a), cy + 0.022 * k * math.sin(a), cz))
            for a in (0, 2.1, 4.2)]
    ponta = bm.verts.new((cx - 0.02 * k, cy, cz + 0.085 * k))
    for i in range(3):
        bm.faces.new((base[i], base[(i + 1) % 3], ponta))
    bm.faces.new(list(reversed(base)))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    ob = geo.objeto(nome, bm, material, suave=False)
    _pai(ob, junta_)
    return ob


def montar(p):
    c = esqueleto(Lobo(p))
    k = c.k
    ruido = p.get("ruido", 55.0)
    pelo = materiais.material("pelo", "pelo_lobo", k=0.35, ruido=ruido, oa=0.7)
    claro = materiais.material("pelo_claro", "pelo_lobo_claro", k=0.3, ruido=ruido, oa=0.7, luz_min=0.15)
    dorso = materiais.material("pelo_dorso", "pelo_lobo_dorso", k=0.35, ruido=ruido, oa=0.7)
    focinho = materiais.material("focinho", "quase_preto", k=0.0, oa=0.5)
    j = c.j
    # tronco: anca, cintura recolhida (barriga sobe), costelas, peito fundo
    _lathe("tronco", [(-0.15 * k, 0.05 * k, 0.05 * k, 0.03 * k),
                      (-0.07 * k, 0.095 * k, 0.105 * k, 0.0),
                      (0.12 * k, 0.090 * k, 0.095 * k, 0.025 * k),
                      (0.32 * k, 0.100 * k, 0.140 * k, -0.015 * k),
                      (0.50 * k, 0.110 * k, 0.170 * k, -0.035 * k),
                      (0.66 * k, 0.100 * k, 0.140 * k, -0.005 * k),
                      (0.74 * k, 0.050 * k, 0.070 * k, 0.040 * k)], pelo, j["tronco"])
    # dorso mais escuro (a "sela" da referência) e barriga clara
    _lathe("sela", [(-0.10 * k, 0.03 * k, 0.03 * k, 0.085 * k), (0.10 * k, 0.05 * k, 0.035 * k, 0.10 * k),
                    (0.40 * k, 0.06 * k, 0.045 * k, 0.125 * k), (0.62 * k, 0.05 * k, 0.04 * k, 0.12 * k)],
           dorso, j["tronco"], segs=12)
    _lathe("barriga", [(0.20 * k, 0.06 * k, 0.03 * k, -0.08 * k), (0.48 * k, 0.08 * k, 0.04 * k, -0.18 * k),
                       (0.64 * k, 0.06 * k, 0.03 * k, -0.13 * k)], claro, j["tronco"], segs=12)
    # colar de pelo no pescoço e nos ombros (a silhueta do lobo de inverno)
    _bola("colar", 0.17 * k, pelo, j["peito"], centro=(0.07 * k, 0, 0.03 * k), escala=(0.9, 1.0, 1.1), sub=1)
    _bola("colar_frente", 0.11 * k, claro, j["peito"], centro=(0.16 * k, 0, -0.05 * k), escala=(0.7, 0.9, 1.0), sub=1)
    _bola("colar_dorso", 0.13 * k, dorso, j["peito"], centro=(0.02 * k, 0, 0.11 * k), escala=(1.0, 0.85, 0.6), sub=1)
    # pescoço: sobe para a frente, da cernelha até a nuca (eixo reto, seções que sobem)
    _lathe("pescoco", [(-0.04 * k, 0.085 * k, 0.10 * k, -0.02 * k), (0.08 * k, 0.075 * k, 0.085 * k, 0.05 * k),
                       (0.17 * k, 0.065 * k, 0.070 * k, 0.12 * k)], pelo, j["pescoco"], segs=12)
    # cabeça: crânio, focinho longo e fino, nariz, mandíbula, orelhas em pé
    cab = j["cabeca"]
    _bola("cranio", 0.100 * k, pelo, cab, centro=(0, 0, 0), escala=(1.1, 0.95, 0.85), sub=1)
    _bola("bochecha", 0.07 * k, claro, cab, centro=(0.01 * k, 0, -0.035 * k), escala=(1.1, 1.0, 0.6), sub=1)
    _lathe("focinho", [(0.04 * k, 0.052 * k, 0.050 * k, -0.01 * k), (0.13 * k, 0.040 * k, 0.036 * k, -0.02 * k),
                       (0.21 * k, 0.028 * k, 0.026 * k, -0.025 * k)], pelo, cab, segs=10)
    _bola("nariz", 0.026 * k, focinho, cab, centro=(0.215 * k, 0, -0.02 * k), sub=0, segs=8)
    _lathe("mandibula", [(0.0, 0.035 * k, 0.018 * k, 0.0), (0.15 * k, 0.020 * k, 0.012 * k, 0.0)],
           claro, j["mandibula"], segs=8, sub=0)
    for lado, s in (("e", 1), ("d", -1)):
        _orelha("orelha_" + lado, pelo, cab, (-0.01 * k, s * 0.05 * k, 0.05 * k), k)
        ponto("olho_" + lado, cab, (0.07 * k, s * 0.040 * k, 0.025 * k), frente=(1, s * 0.4, 0))
    # pernas: grossas no alto, finas embaixo; patas claras
    for lado in ("e", "d"):
        _tubo("coxa_" + lado, COMP["coxa"] + 0.05 * k, 0.075 * k, 0.045 * k, pelo, j["coxa_" + lado], z0=0.05 * k, sub=1)
        _tubo("canela_" + lado, COMP["canela"], 0.050 * k, 0.038 * k, pelo, j["canela_" + lado], sub=1)
        _tubo("jarrete_" + lado, COMP["jarrete"], 0.036 * k, 0.030 * k, claro, j["jarrete_" + lado], sub=1)
        _bola("pata_t_" + lado, 0.035 * k, claro, j["pata_t_" + lado], centro=(0.02 * k, 0, -0.012 * k),
              escala=(1.4, 0.9, 0.6), sub=1)
        _tubo("braco_" + lado, COMP["braco"] + 0.04 * k, 0.060 * k, 0.038 * k, pelo, j["braco_" + lado], z0=0.04 * k, sub=1)
        _tubo("antebraco_" + lado, COMP["antebraco"], 0.040 * k, 0.032 * k, claro, j["antebraco_" + lado], sub=1)
        _bola("pata_f_" + lado, 0.035 * k, claro, j["pata_f_" + lado], centro=(0.025 * k, 0, -0.012 * k),
              escala=(1.4, 0.9, 0.6), sub=1)
        ponto("pe_t_" + lado, j["pata_t_" + lado], (0.02 * k, 0, -0.035 * k))
        ponto("pe_f_" + lado, j["pata_f_" + lado], (0.025 * k, 0, -0.035 * k))
    # cauda: cheia, mais escura na ponta, pendendo
    _bola("cauda1", 0.07 * k, pelo, j["cauda1"], centro=(-0.08 * k, 0, 0), escala=(1.6, 0.8, 0.8), sub=1)
    _bola("cauda2", 0.075 * k, dorso, j["cauda2"], centro=(-0.10 * k, 0, 0), escala=(1.7, 0.85, 0.85), sub=1)
    # assenta no chão: no repouso, a pata mais baixa toca z = 0
    pose_repouso(c)
    bpy.context.view_layer.update()
    pes = [o for o in bpy.data.objects if o.name.startswith("ponto.pe_")]
    baixo = min(o.matrix_world.translation.z for o in pes)
    raiz_z = j["raiz"].matrix_world.translation.z
    j["quadril"].location.z -= (baixo - raiz_z)
    c.altura_base = j["quadril"].location.z
    return c


# ------------------------------------------------------------------ pose

def _y(j, a):
    r = j.rotation_euler
    j.rotation_euler = (r.x, a, r.z)


def pose_repouso(c):
    for v in c.j.values():
        v.rotation_euler = (0.0, 0.0, 0.0)
    if getattr(c, "altura_base", 0.0):
        c.j["quadril"].location.z = c.altura_base
    c.j["quadril"].location.x = -0.45 * c.k
    c.j["cauda1"].rotation_euler.y = math.radians(-55)        # (a cauda aponta para -X; Y negativo a baixa)
    c.j["cauda2"].rotation_euler.y = math.radians(-15)
    for lado in ("e", "d"):
        for nome in ("coxa", "canela", "jarrete"):
            _y(c.j[nome + "_" + lado], REPOUSO[nome])
        _y(c.j["pata_t_" + lado], REPOUSO["pata_t"] - (REPOUSO["coxa"] + REPOUSO["canela"] + REPOUSO["jarrete"]))
        for nome in ("braco", "antebraco"):
            _y(c.j[nome + "_" + lado], REPOUSO[nome])
        _y(c.j["pata_f_" + lado], REPOUSO["pata_f"] - (REPOUSO["braco"] + REPOUSO["antebraco"]))


def perna_tras(c, lado, giro, dobra):
    """giro: a perna inteira (rad, negativo = para a frente); dobra: 0..1, o jarrete sobe."""
    _y(c.j["coxa_" + lado], REPOUSO["coxa"] + giro - 0.35 * dobra)
    _y(c.j["canela_" + lado], REPOUSO["canela"] + 0.9 * dobra)
    _y(c.j["jarrete_" + lado], REPOUSO["jarrete"] - 0.5 * dobra)
    soma = REPOUSO["coxa"] + giro - 0.35 * dobra + REPOUSO["canela"] + 0.9 * dobra + REPOUSO["jarrete"] - 0.5 * dobra
    _y(c.j["pata_t_" + lado], -soma + 0.6 * dobra)


def perna_frente(c, lado, giro, dobra):
    """giro: a perna inteira; dobra: 0..1, o cotovelo e o pulso dobram (a pata sobe para trás)."""
    _y(c.j["braco_" + lado], REPOUSO["braco"] + giro - 0.25 * dobra)
    _y(c.j["antebraco_" + lado], REPOUSO["antebraco"] + 1.1 * dobra)
    soma = REPOUSO["braco"] + giro - 0.25 * dobra + REPOUSO["antebraco"] + 1.1 * dobra
    _y(c.j["pata_f_" + lado], -soma + 0.9 * dobra)


# ------------------------------------------------------------------ ciclos (SPEC-visual §6.2, lobo)

def _suave(u):
    return u * u * (3 - 2 * u)


def _passos(c, t, passada, apoio, fases):
    """Pernas de um ciclo de locomoção: na fase de apoio a pata recua com o chão (giro de -A a +A),
    no balanço volta pelo ar dobrando. fases: {perna: deslocamento no ciclo}."""
    alcance = 0.55 * c.k
    A = math.asin(min(0.95, passada * apoio / 2 / alcance))
    for perna, fase in fases.items():
        u = (t + fase) % 1.0
        if u < apoio:
            giro, dobra = -A + 2 * A * u / apoio, 0.0
        else:
            v = (u - apoio) / (1 - apoio)
            giro, dobra = A - 2 * A * _suave(v), math.sin(math.pi * v)
        lado = perna[-1]
        (perna_frente if perna.startswith("f") else perna_tras)(c, lado, giro, dobra)


def parado(c, t, p):
    pose_repouso(c)
    f = 2 * math.pi * t
    c.j["quadril"].location.z += 0.004 * c.k * math.sin(f)
    c.j["peito"].rotation_euler.y = 0.02 * math.sin(f)
    c.j["cabeca"].rotation_euler.z = 0.22 * math.sin(f + 1.0)
    c.j["cabeca"].rotation_euler.y = 0.06 * math.sin(2 * f)
    c.j["cauda1"].rotation_euler.z = 0.12 * math.sin(f)


def andar(c, t, p):
    """Trote: pares diagonais juntos (frente-esquerda com trás-direita)."""
    pose_repouso(c)
    _passos(c, t, p.get("passada", 1.2) * c.k, 0.55, {"f_e": 0.0, "t_d": 0.0, "f_d": 0.5, "t_e": 0.5})
    f = 4 * math.pi * t
    c.j["quadril"].location.z += 0.012 * c.k * math.cos(f)
    c.j["pescoco"].rotation_euler.y = 0.05 * math.cos(f)
    c.j["cauda1"].rotation_euler.z = 0.10 * math.sin(2 * math.pi * t)


def correr(c, t, p):
    """Galope rotativo: trás-esquerda, trás-direita, frente-esquerda, frente-direita; a coluna
    encolhe e estica, o corpo sobe e desce, a cauda vai para trás."""
    pose_repouso(c)
    _passos(c, t, p.get("passada", 2.2) * c.k, 0.32, {"t_e": 0.0, "t_d": 0.9, "f_e": 0.5, "f_d": 0.4})
    f = 2 * math.pi * t
    c.j["tronco"].rotation_euler.y = 0.10 * math.sin(f)
    c.j["peito"].rotation_euler.y = -0.12 * math.sin(f)
    c.j["quadril"].location.z += 0.03 * c.k * math.sin(f + 1.2) - 0.02 * c.k
    c.j["pescoco"].rotation_euler.y = 0.12 + 0.08 * math.sin(f)
    c.j["cauda1"].rotation_euler.y = math.radians(-25)


def _fase_golpe(t):
    if t < 0.5:
        return _suave(t / 0.5), 0.0
    if t < 0.62:
        return 1.0 - (t - 0.5) / 0.12, 1.0
    return 0.0, 1.0 - _suave((t - 0.62) / 0.38)


def bote(c, t, p):
    """Bote: no aviso (primeira metade) agacha, recua o peso e baixa a cabeça; no impacto salta para
    a frente com as patas da frente esticadas e a boca aberta; depois volta."""
    pose_repouso(c)
    a, b = _fase_golpe(t)
    k = c.k
    q = c.j["quadril"]
    q.location.z += (-0.09 * a + 0.10 * b) * k
    q.location.x += (-0.07 * a + 0.22 * b) * k
    c.j["tronco"].rotation_euler.y = 0.05 * a - 0.22 * b
    c.j["pescoco"].rotation_euler.y = 0.30 * a - 0.10 * b
    c.j["cabeca"].rotation_euler.y = -0.15 * a + 0.15 * b
    c.j["mandibula"].rotation_euler.y = 0.15 * a + 0.55 * b
    for lado in ("e", "d"):
        perna_frente(c, lado, -0.05 * a - 1.05 * b, 0.45 * a)
        perna_tras(c, lado, -0.30 * a + 0.75 * b, 0.55 * a)
    c.j["cauda1"].rotation_euler.y = math.radians(-55 + 20 * b)


def dano(c, t, p):
    pose_repouso(c)
    g = math.sin(math.pi * min(1.0, t / 0.67))
    c.j["quadril"].location.x -= 0.06 * c.k * g
    c.j["tronco"].rotation_euler.y = 0.08 * g
    c.j["cabeca"].rotation_euler.z = 0.45 * g
    c.j["cabeca"].rotation_euler.y = -0.20 * g
    c.j["cauda1"].rotation_euler.y = math.radians(-55 - 20 * g)
    for lado in ("e", "d"):
        perna_frente(c, lado, 0.15 * g, 0.2 * g)


def _no_chao(c, u):
    """De pé (0) a caído de lado (1): as pernas cedem, o corpo tomba sobre o lado."""
    pose_repouso(c)
    v = _suave(max(0.0, (u - 0.2) / 0.8))
    dobra = _suave(min(1.0, u / 0.5))
    k = c.k
    q = c.j["quadril"]
    q.rotation_euler.x = (math.pi / 2) * v
    q.location.z = c.altura_base * (1 - 0.25 * dobra) * (1 - v) + 0.13 * k * v
    q.location.y = 0.0
    c.j["pescoco"].rotation_euler.y = 0.25 * v
    c.j["cabeca"].rotation_euler.y = 0.2 * v
    c.j["cauda1"].rotation_euler.y = math.radians(-55 + 45 * v)
    for lado in ("e", "d"):
        perna_frente(c, lado, -0.2 * v, 0.5 * dobra * (1 - v) + 0.3 * v)
        perna_tras(c, lado, 0.2 * v, 0.5 * dobra * (1 - v) + 0.3 * v)


def morrer(c, t, p):
    _no_chao(c, min(1.0, t / (5.0 / 6.0)))


def morto(c, t, p):
    _no_chao(c, 1.0)


def deitado(c, t, p):
    """Deitado de barriga, patas da frente estendidas, cabeça baixa; só o peito respira."""
    pose_repouso(c)
    r = 0.5 - 0.5 * math.cos(2 * math.pi * t)
    k = c.k
    c.j["quadril"].location.z = 0.24 * k + 0.004 * k * r
    c.j["tronco"].rotation_euler.y = 0.04
    c.j["pescoco"].rotation_euler.y = 0.35
    c.j["cabeca"].rotation_euler.y = 0.10 - 0.03 * r
    for lado in ("e", "d"):
        perna_frente(c, lado, -1.35, 0.15)
        perna_tras(c, lado, -0.95, 1.0)
    c.j["cauda1"].rotation_euler.y = math.radians(-80)
    c.j["cauda1"].rotation_euler.z = 0.4


CICLOS = {"parado": parado, "andar": andar, "correr": correr, "bote": bote, "dano": dano,
          "morrer": morrer, "morto": morto, "deitado": deitado}
