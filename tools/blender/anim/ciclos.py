"""Ciclos de animação por nome (SPEC-visual §6.2), parametrizados e iguais para todo corpo.

Cada ciclo é uma função pose(corpo, t, params) com t em [0, 1): gira as juntas por nome. O
andar e o correr plantam o pé de apoio: em espaço do sprite o pé de apoio recua exatamente
`passada` metros por ciclo; o cliente avança o quadro pela distância percorrida (não pelo
relógio), então o pé não desliza (VT-15).
"""
import math

ADIANTE = -1.0  # girar a junta em Y por -a leva o membro a para a frente (+X)


def _zera(c):
    for v in c.j.values():
        v.rotation_euler = (0.0, 0.0, 0.0)
    c.j["quadril"].location.z = c.quadril_h
    c.j["quadril"].location.x = 0.0


def balanco(junta, angulo):
    """Leva o membro `angulo` radianos para a frente (ou para trás, se negativo)."""
    r = junta.rotation_euler
    junta.rotation_euler = (r.x, ADIANTE * angulo, r.z)


def _ik_perna(c, lado, pe_x, pe_z):
    """IK de dois ossos no plano sagital: põe o tornozelo em (pe_x, pe_z), joelho para a frente.
    Coordenadas relativas ao chão sob a raiz; o quadril já está posicionado."""
    q = c.j["quadril"].location
    dx, dz = pe_x - q.x, pe_z - q.z
    l1, l2 = c.coxa, c.canela
    d = min(math.hypot(dx, dz), (l1 + l2) * 0.9995)
    d = max(d, abs(l1 - l2) + 1e-3)
    phi = math.atan2(dx, -dz)                                   # ângulo da reta quadril→pé (+ = à frente)
    alfa = math.acos(max(-1, min(1, (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d))))
    joelho = math.pi - math.acos(max(-1, min(1, (l1 * l1 + l2 * l2 - d * d) / (2 * l1 * l2))))
    coxa = phi + alfa
    balanco(c.j["coxa_" + lado], coxa)
    balanco(c.j["canela_" + lado], -joelho)
    return coxa - joelho                                        # ângulo absoluto da canela


def _pe(c, lado, canela_abs, inclinacao=0.0):
    """Mantém o pé com a sola no chão (mais `inclinacao` para a frente = ponta para baixo)."""
    balanco(c.j["pe_" + lado], -canela_abs - inclinacao)


def _suave(u):
    return u * u * (3 - 2 * u)


def parado(c, t, p):
    """Respira: ombros e cabeça sobem um pixel no meio do ciclo; braços balançam de leve."""
    _zera(c)
    r = 0.5 - 0.5 * math.cos(2 * math.pi * t)
    c.j["quadril"].location.z = c.quadril_h - 0.006 * (1 - r)
    c.j["tronco"].rotation_euler.y = math.radians(-1.5 * r) + (math.radians(6) if c.curvado else 0)
    balanco(c.j["pescoco"], math.radians(2 * r - 1))
    for lado, s in (("e", 1), ("d", -1)):
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-5)   # braço um pouco aberto
        balanco(c.j["braco_" + lado], math.radians(2 + 2 * r))
        balanco(c.j["antebraco_" + lado], math.radians(8 + 3 * r))
        c.j["coxa_" + lado].rotation_euler.x = s * math.radians(-2.5)
        c.j["pe_" + lado].rotation_euler.x = s * math.radians(2.5)
        _ik_perna(c, lado, 0.0, c.tornozelo)


def _passo(c, t, passada, apoio, altura_pe, quadril_med, quadril_amp, chute=0.0):
    """Trajetória dos pés e altura do quadril para um ciclo de locomoção.

    apoio: fração do ciclo com o pé no chão (andar 0,6; correr 0,38).
    """
    d = passada * apoio / 2                                     # o pé de apoio vai de +d a -d
    resultado = {}
    for lado, fase in (("e", 0.0), ("d", 0.5)):
        u = (t + fase) % 1.0
        if u < apoio:                                           # apoio: recua com o chão
            x = d - passada * u
            z = c.tornozelo
            incl = 0.0
        else:                                                   # balanço: volta pelo ar
            v = (u - apoio) / (1 - apoio)
            x = -d + 2 * d * _suave(v) - chute * math.sin(math.pi * v) * (1 - v)
            z = c.tornozelo + altura_pe * math.sin(math.pi * v) ** 0.8
            incl = math.radians(25) * math.sin(math.pi * v) * (1 - v) * 2
        resultado[lado] = (x, z, incl)
    # quadril mais alto quando um pé passa sob o corpo (duas vezes por ciclo)
    c.j["quadril"].location.z = quadril_med + quadril_amp * math.cos(4 * math.pi * (t - apoio / 2))
    return resultado


def andar(c, t, p):
    _zera(c)
    passada = p.get("passada", 1.3) * c.k
    pes = _passo(c, t, passada, 0.62, 0.09 * c.k, c.quadril_h - 0.035 * c.k, 0.018 * c.k)
    c.j["tronco"].rotation_euler.y = math.radians(-3 - (6 if c.curvado else 0))
    c.j["tronco"].rotation_euler.z = math.radians(4) * math.sin(2 * math.pi * t)
    for lado, s, fase in (("e", 1, 0.0), ("d", -1, 0.5)):
        x, z, incl = pes[lado]
        canela = _ik_perna(c, lado, x, z)
        _pe(c, lado, canela, incl)
        # braço oposto à perna: o braço esquerdo vai à frente quando a perna direita vai
        a = math.radians(18) * math.cos(2 * math.pi * (t + fase + 0.5))
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-4)
        balanco(c.j["braco_" + lado], a)
        balanco(c.j["antebraco_" + lado], math.radians(14) + max(0.0, a) * 0.6)


def correr(c, t, p):
    _zera(c)
    passada = p.get("passada", 2.4) * c.k
    pes = _passo(c, t, passada, 0.38, 0.22 * c.k, c.quadril_h - 0.07 * c.k, 0.03 * c.k, chute=0.30 * c.k)
    c.j["tronco"].rotation_euler.y = math.radians(-10)
    c.j["tronco"].rotation_euler.z = math.radians(7) * math.sin(2 * math.pi * t)
    balanco(c.j["pescoco"], math.radians(-6))
    for lado, s, fase in (("e", 1, 0.0), ("d", -1, 0.5)):
        x, z, incl = pes[lado]
        canela = _ik_perna(c, lado, x, z)
        _pe(c, lado, canela, incl)
        a = math.radians(38) * math.cos(2 * math.pi * (t + fase + 0.5))
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-6)
        balanco(c.j["braco_" + lado], a)
        balanco(c.j["antebraco_" + lado], math.radians(80))


CICLOS = {"parado": parado, "andar": andar, "correr": correr}
