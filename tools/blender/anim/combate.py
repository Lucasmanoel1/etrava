"""Animações de combate do V3 (SPEC-visual §6.2): golpes por classe de arma, dano, cair, caído e
levantar. Mesmo esqueleto e mesma convenção de sinal de anim/ciclos.py.

Tempo do golpe (D-078, cliente): t de 0 a 0,5 é o preparo, que no inimigo é o aviso que dá para
ler e esquivar; o impacto cai em t = 0,5 (quadro 3 de 6); o resto é a volta à guarda. O golpe do
jogador sai na hora, então o cliente começa do quadro 1, já armando.
"""
import math

from anim.ciclos import INCLINA, _coluna, _ik_perna, _pe, _suave, _zera, _zera_peito, balanco


def _fase(t):
    """(armar, bater): armar sobe até 1 no fim do preparo; bater vai de 0 a 1 do impacto até a
    volta. Antes do impacto bater = 0; depois, armar desce junto com a volta."""
    if t < 0.5:
        return _suave(t / 0.5), 0.0
    if t < 0.62:
        return 1.0 - (t - 0.5) / 0.12, 1.0                     # o golpe desce de uma vez
    v = (t - 0.62) / 0.38
    return 0.0, 1.0 - _suave(v)                                 # volta à guarda


def _guarda(c, avanco=0.0, baixa=0.0):
    """Pernas em guarda: pé esquerdo à frente, direito atrás; o quadril avança `avanco` (m) no
    bote e desce `baixa` (m)."""
    k = c.k
    c.j["quadril"].location.x = avanco * k
    c.j["quadril"].location.z = c.quadril_h - (0.035 + baixa) * k
    for lado, s, x in (("e", 1, 0.14), ("d", -1, -0.16)):
        c.j["coxa_" + lado].rotation_euler.x = s * math.radians(-4)
        canela = _ik_perna(c, lado, x * k, c.tornozelo)
        _pe(c, lado, canela)
        c.j["pe_" + lado].rotation_euler.z = s * math.radians(12)


def golpe_corte(c, t, p):
    """Corte (faca, machadinha, punho): o braço direito arma por cima do ombro, o tronco gira para
    trás; no impacto o braço cruza o corpo de cima para baixo e o peso vai para o pé da frente."""
    _zera(c)
    _zera_peito(c)
    a, b = _fase(t)
    _guarda(c, avanco=0.06 * b - 0.02 * a, baixa=0.02 * b)
    _coluna(c, 6.0 + 10.0 * b - 4.0 * a, -10.0 * a + 14.0 * b, 28.0 * a - 32.0 * b)
    balanco(c.j["pescoco"], math.radians(4 * b))
    c.j["braco_d"].rotation_euler.x = math.radians(35 * a - 12 * b)
    balanco(c.j["braco_d"], math.radians(25 + 95 * a + 30 * b))
    balanco(c.j["antebraco_d"], math.radians(20 + 75 * a - 10 * b))
    c.j["braco_e"].rotation_euler.x = math.radians(-10)
    balanco(c.j["braco_e"], math.radians(30 - 25 * b))
    balanco(c.j["antebraco_e"], math.radians(60))


def golpe_estocada(c, t, p):
    """Estocada (faca, lança curta): o cotovelo recua com a ponta junto à cintura; no impacto o
    braço estica reto para a frente e o corpo vai junto num passo longo."""
    _zera(c)
    _zera_peito(c)
    a, b = _fase(t)
    _guarda(c, avanco=0.13 * b - 0.04 * a, baixa=0.04 * b)
    _coluna(c, 4.0 + 14.0 * b - 3.0 * a, -6.0 * a + 8.0 * b, 22.0 * a - 22.0 * b)
    balanco(c.j["pescoco"], math.radians(-4 * b))
    c.j["braco_d"].rotation_euler.x = math.radians(8)
    balanco(c.j["braco_d"], math.radians(15 - 45 * a + 80 * b))
    balanco(c.j["antebraco_d"], math.radians(30 + 75 * a - 25 * b))
    c.j["braco_e"].rotation_euler.x = math.radians(-14)
    balanco(c.j["braco_e"], math.radians(20 - 30 * b))
    balanco(c.j["antebraco_e"], math.radians(50 - 20 * b))


def golpe_pesado(c, t, p):
    """Golpe pesado (martelo, cajado, ou o golpe forte): as duas mãos erguem acima da cabeça e o
    corpo inclina para trás; no impacto tudo desce junto, o tronco dobra e os joelhos cedem."""
    _zera(c)
    _zera_peito(c)
    a, b = _fase(t)
    _guarda(c, avanco=0.07 * b, baixa=0.07 * b - 0.01 * a)
    _coluna(c, -8.0 * a + 26.0 * b, 0.0, 6.0 * a - 6.0 * b)
    balanco(c.j["pescoco"], math.radians(-6 * a + 6 * b))
    for lado, s in (("e", 1), ("d", -1)):
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-6 + 4 * a)
        balanco(c.j["braco_" + lado], math.radians(20 + 140 * a + 20 * b))
        balanco(c.j["antebraco_" + lado], math.radians(35 + 30 * a - 25 * b))


def dano(c, t, p):
    """Recuo ao levar o golpe: o tronco joga para trás, a cabeça vai junto, os braços sobem um
    pouco e o quadril recua; volta no terceiro quadro."""
    _zera(c)
    _zera_peito(c)
    g = math.sin(math.pi * min(1.0, t / 0.67))
    _guarda(c, avanco=-0.05 * g, baixa=0.02 * g)
    _coluna(c, 2.0 - 16.0 * g, 6.0 * g, -10.0 * g)
    balanco(c.j["pescoco"], math.radians(-14 * g))
    for lado, s in (("e", 1), ("d", -1)):
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-10 - 15 * g)
        balanco(c.j["braco_" + lado], math.radians(10 + 30 * g))
        balanco(c.j["antebraco_" + lado], math.radians(30 + 40 * g))


def _no_chao(c, u):
    """De pé (u = 0) a deitado de costas (u = 1): primeiro os joelhos cedem, depois o corpo tomba
    para trás. Deitado, o corpo fica centrado sobre a âncora, com os joelhos dobrados e os braços
    abertos, para caber no quadro em toda direção."""
    k = c.k
    joelho = _suave(min(1.0, u / 0.45))
    v = _suave(max(0.0, (u - 0.25) / 0.75))
    q = c.j["quadril"]
    q.rotation_euler.y = INCLINA * math.radians(-88 * v)
    q.location.x = (-0.10 * joelho * (1 - v) + 0.14 * v) * k
    q.location.z = (c.quadril_h - 0.28 * k * joelho) * (1 - v) + 0.13 * k * v
    _coluna(c, 6.0 * joelho * (1 - v) + 10.0 * v, 0.0, 0.0)
    balanco(c.j["pescoco"], math.radians(22 * v))                 # o rosto ainda vira para cima
    for lado, s in (("e", 1), ("d", -1)):
        if v < 0.05:
            canela = _ik_perna(c, lado, 0.05 * k * joelho, c.tornozelo)
            _pe(c, lado, canela)
        else:
            balanco(c.j["coxa_" + lado], math.radians(40 * joelho + 45 * v))
            balanco(c.j["canela_" + lado], math.radians(-70 * joelho - 30 * v))
            balanco(c.j["pe_" + lado], math.radians(20 * v))
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-8 - 50 * v)
        balanco(c.j["braco_" + lado], math.radians(25 * joelho * (1 - v) + 15 * v))
        balanco(c.j["antebraco_" + lado], math.radians(30 + 20 * v))


def cair(c, t, p):
    _zera(c)
    _zera_peito(c)
    _no_chao(c, min(1.0, t / (5.0 / 6.0)))                       # o último quadro já está no chão


def caido(c, t, p):
    _zera(c)
    _zera_peito(c)
    _no_chao(c, 1.0)


def levantar(c, t, p):
    _zera(c)
    _zera_peito(c)
    _no_chao(c, max(0.0, 1.0 - t / 0.8))                          # o último quadro já está de pé


CICLOS = {"golpe_corte": golpe_corte, "golpe_estocada": golpe_estocada, "golpe_pesado": golpe_pesado,
          "dano": dano, "cair": cair, "caido": caido, "levantar": levantar}
# quadros e qps (SPEC-visual §6.2); os golpes andam pela fase do golpe no cliente, não pelo relógio
QUADROS = {"golpe_corte": (6, 12), "golpe_estocada": (6, 12), "golpe_pesado": (6, 10), "dano": (3, 10),
           "cair": (6, 10), "caido": (1, 1), "levantar": (5, 8)}
