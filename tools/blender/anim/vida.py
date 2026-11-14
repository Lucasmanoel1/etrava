"""Animações de vida comum do V2 (SPEC-visual §6.2): sentar/sentado, trabalhar (bancada, martelo,
chão), conversar e dormir. Mesmo esqueleto e mesma convenção de sinal de anim/ciclos.py.

O assento fica a 46 cm (banco, cadeira e mesa da taverna); o personagem senta onde está, com o
pé no chão, então a âncora do sprite continua sendo o chão sob ele.
"""
import math

from anim.ciclos import INCLINA, _coluna, _ik_perna, _pe, _suave, _zera, _zera_peito, balanco

ASSENTO = 0.46


def _bracos_soltos(c, r=0.0):
    for lado, s in (("e", 1), ("d", -1)):
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-5)
        balanco(c.j["braco_" + lado], math.radians(-3 + 2 * r))
        balanco(c.j["antebraco_" + lado], math.radians(16 + 4 * r))


def _pernas_em_pe(c):
    for lado, s in (("e", 1), ("d", -1)):
        c.j["coxa_" + lado].rotation_euler.x = s * math.radians(-3)
        c.j["pe_" + lado].rotation_euler.z = s * math.radians(8)
        canela = _ik_perna(c, lado, 0.0, c.tornozelo)
        _pe(c, lado, canela)


def _sentado(c, u, r):
    """Pose sentada com fração u (0 = de pé, 1 = sentado) e respiração r."""
    k = c.k
    alto = c.quadril_h - 0.012 * k
    baixo = ASSENTO * k + 0.06 * k
    c.j["quadril"].location.z = alto + (baixo - alto) * u
    c.j["quadril"].location.x = -0.10 * k * u           # o quadril vai para trás, sobre o assento
    _coluna(c, 2.0 + 6.0 * u, 0.0, 0.0, respira=1.5 * r)
    balanco(c.j["pescoco"], math.radians(-4 * u + 2 * r - 1))
    for lado, s in (("e", 1), ("d", -1)):
        # pés à frente do quadril, um pouco abertos; o joelho dobra pelo IK
        canela = _ik_perna(c, lado, 0.16 * k * u, c.tornozelo)
        _pe(c, lado, canela)
        c.j["coxa_" + lado].rotation_euler.x = s * math.radians(-3 - 4 * u)
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-5)
        # mãos descansam nas coxas
        balanco(c.j["braco_" + lado], math.radians(-3 + 28 * u + 2 * r))
        balanco(c.j["antebraco_" + lado], math.radians(16 + 44 * u))


def sentar(c, t, p):
    """De pé → sentado em 3 quadros (o último já quase sentado; `sentado` continua dali)."""
    _zera(c)
    _zera_peito(c)
    _sentado(c, _suave(min(1.0, t * 1.5)), 0.0)


def sentado(c, t, p):
    _zera(c)
    _zera_peito(c)
    r = 0.5 - 0.5 * math.cos(2 * math.pi * t)
    _sentado(c, 1.0, r)
    c.j["cabeca"].rotation_euler.z = math.radians(5) * math.sin(2 * math.pi * t + 0.7)


def trabalhar_bancada(c, t, p):
    """Em pé diante de uma bancada (balcão, banca, mesa de ervas): inclinado, as duas mãos à frente
    na altura da cintura, mexendo em vez alternada; a cabeça olha o trabalho."""
    _zera(c)
    _zera_peito(c)
    f = 2 * math.pi * t
    c.j["quadril"].location.z = c.quadril_h - 0.015 * c.k
    _coluna(c, 12.0, 2.0 * math.sin(f), -5.0 * math.sin(f))
    balanco(c.j["pescoco"], math.radians(10))
    for lado, s, fs in (("e", 1, 0.0), ("d", -1, 0.5)):
        u = math.sin(2 * math.pi * (t + fs))
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-10)
        balanco(c.j["braco_" + lado], math.radians(32 + 8 * u))
        balanco(c.j["antebraco_" + lado], math.radians(48 + 10 * max(0.0, u)))
    _pernas_em_pe(c)


def trabalhar_martelo(c, t, p):
    """Martelo na mão direita: ergue devagar (60% do ciclo), desce rápido, bate, descansa um pouco.
    A esquerda segura a peça na bigorna."""
    _zera(c)
    _zera_peito(c)
    if t < 0.6:
        g = _suave(t / 0.6)                 # ergue
    elif t < 0.72:
        g = 1.0 - ((t - 0.6) / 0.12) ** 2   # desce acelerando
    else:
        g = 0.0                             # bateu
    bate = 1.0 if 0.72 <= t < 0.80 else 0.0
    c.j["quadril"].location.z = c.quadril_h - 0.02 * c.k - 0.01 * c.k * bate
    _coluna(c, 9.0 + 4.0 * (1 - g), 4.0 * g, -12.0 * g)
    balanco(c.j["pescoco"], math.radians(8))
    c.j["braco_d"].rotation_euler.x = math.radians(5)
    balanco(c.j["braco_d"], math.radians(35 + 85 * g))
    balanco(c.j["antebraco_d"], math.radians(25 + 70 * g))
    c.j["braco_e"].rotation_euler.x = math.radians(-8)
    balanco(c.j["braco_e"], math.radians(38))
    balanco(c.j["antebraco_e"], math.radians(45))
    _pernas_em_pe(c)


def trabalhar_chao(c, t, p):
    """Curvado sobre o chão (enxada, colheita, cova, fardo): joelhos dobrados, tronco a ~35°, os
    braços puxam para trás e voltam."""
    _zera(c)
    _zera_peito(c)
    f = 2 * math.pi * t
    u = math.sin(f)
    k = c.k
    c.j["quadril"].location.z = c.quadril_h - 0.07 * k - 0.015 * k * u
    c.j["quadril"].location.x = -0.05 * k
    _coluna(c, 30.0 + 6.0 * u, 3.0 * u, -6.0 * u)
    balanco(c.j["pescoco"], math.radians(-12))      # a cabeça não acompanha toda a curva
    for lado, s, fs in (("e", 1, 0.0), ("d", -1, 0.15)):
        v = math.sin(f + fs * 2 * math.pi)
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-6)
        balanco(c.j["braco_" + lado], math.radians(40 + 22 * v))
        balanco(c.j["antebraco_" + lado], math.radians(30 + 15 * v))
    for lado, s, x in (("e", 1, 0.10), ("d", -1, -0.10)):     # um pé à frente: base firme
        c.j["coxa_" + lado].rotation_euler.x = s * math.radians(-4)
        canela = _ik_perna(c, lado, x * k, c.tornozelo)
        _pe(c, lado, canela)


def conversar(c, t, p):
    """Em pé, conversando: a mão direita gesticula (abre, aponta, volta), a cabeça acena, o peso
    passa de um pé ao outro devagar."""
    _zera(c)
    _zera_peito(c)
    f = 2 * math.pi * t
    w = math.sin(f)
    c.j["quadril"].location.z = c.quadril_h - 0.014 * c.k
    c.j["quadril"].location.y = 0.008 * c.k * w
    _coluna(c, 3.0, 0.0, 6.0 * math.sin(f + 0.8), lado_peito=-2.0 * w)
    balanco(c.j["pescoco"], math.radians(2 + 4 * math.sin(2 * f)))      # acena
    c.j["cabeca"].rotation_euler.z = math.radians(6) * math.sin(f + 1.2)
    g = 0.5 - 0.5 * math.cos(f)
    c.j["braco_d"].rotation_euler.x = math.radians(8 + 14 * g)
    balanco(c.j["braco_d"], math.radians(12 + 26 * g))
    balanco(c.j["antebraco_d"], math.radians(55 + 30 * math.sin(f * 2 + 0.4) * g))
    c.j["mao_d"].rotation_euler.x = math.radians(25 * g)
    c.j["braco_e"].rotation_euler.x = math.radians(-6)
    balanco(c.j["braco_e"], math.radians(-2))
    balanco(c.j["antebraco_e"], math.radians(22 + 6 * g))
    _pernas_em_pe(c)


def dormir(c, t, p):
    """Deitado de costas no chão (ou na cama, que o mapa põe embaixo): o corpo inteiro gira no
    quadril; só o peito respira."""
    _zera(c)
    _zera_peito(c)
    r = 0.5 - 0.5 * math.cos(2 * math.pi * t)
    k = c.k
    c.j["quadril"].location.z = 0.11 * k
    c.j["quadril"].location.x = 0.0
    c.j["quadril"].rotation_euler.y = INCLINA * math.radians(-90)    # de costas: a cabeça para trás (-X)
    c.j["peito"].rotation_euler.y = INCLINA * math.radians(-2 * r)
    balanco(c.j["pescoco"], math.radians(-10))
    for lado, s in (("e", 1), ("d", -1)):
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-8)
        balanco(c.j["antebraco_" + lado], math.radians(10))
        c.j["coxa_" + lado].rotation_euler.x = s * math.radians(-4)
        balanco(c.j["canela_" + lado], math.radians(-6))
        balanco(c.j["pe_" + lado], math.radians(-60))       # pés para cima


CICLOS = {"sentar": sentar, "sentado": sentado, "trabalhar_bancada": trabalhar_bancada,
          "trabalhar_martelo": trabalhar_martelo, "trabalhar_chao": trabalhar_chao, "conversar": conversar,
          "dormir": dormir}
