"""Ciclos de animação por nome (SPEC-visual §6.2), parametrizados e iguais para todo corpo.

Cada ciclo é uma função pose(corpo, t, params) com t em [0, 1): gira as juntas por nome. O
andar e o correr plantam o pé de apoio: em espaço do sprite o pé de apoio recua exatamente
`passada` metros por ciclo; o cliente avança o quadro pela distância percorrida (não pelo
relógio), então o pé não desliza (VT-15).
"""
import math

ADIANTE = -1.0  # girar a junta em Y por -a leva o membro a para a frente (+X)
# Atenção ao sinal (D-102): nas juntas que apontam para CIMA (tronco, peito, pescoço, cabeça),
# rotação Y positiva inclina para a FRENTE; nos membros que pendem (braço, perna), positiva
# leva para TRÁS. O andar e o correr do V1 inclinavam o tronco para trás por isso.
INCLINA = 1.0


def _zera(c):
    for v in c.j.values():
        v.rotation_mode = "XYZ"       # dormir troca a ordem no quadril
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


def _planta(c, lado, x, z, y=None, inclinacao=0.0, pe_x=0.0):
    """Põe o tornozelo em (x, y, z) no espaço da raiz (o do sprite), seja qual for a pose do quadril.

    O _ik_perna resolve só no plano da passada e a partir do centro do quadril; quando o quadril
    desliza para o lado, gira e rola (o peso que passa de um pé ao outro), o pé de apoio ia junto
    e escorregava até 2 px (VT-15, D-108). Aqui o alvo é levado para o espaço do quadril, a coxa
    abre para o lado (X) e anula o giro do quadril (Z), e a perna dobra no próprio plano (Y).
    y: afastamento lateral; padrão = o da articulação da coxa. pe_x: rolagem extra do pé."""
    from mathutils import Matrix, Vector
    q, cx = c.j["quadril"], c.j["coxa_" + lado]
    if y is None:
        y = cx.location.y
    mq = Matrix.Translation(q.location) @ q.rotation_euler.to_matrix().to_4x4()
    v = (mq.inverted() @ Vector((x, y, z))) - cx.location
    gama = -q.rotation_euler.z                                  # o pé aponta para a frente do sprite
    w = Matrix.Rotation(-gama, 3, "Z") @ v
    theta = math.atan2(w.y, -w.z)                               # abertura lateral da perna
    dx, dz = w.x, -math.hypot(w.y, w.z)
    l1, l2 = c.coxa, c.canela
    d = min(math.hypot(dx, dz), (l1 + l2) * 0.9995)
    d = max(d, abs(l1 - l2) + 1e-3)
    phi = math.atan2(dx, -dz)
    alfa = math.acos(max(-1, min(1, (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d))))
    joelho = math.pi - math.acos(max(-1, min(1, (l1 * l1 + l2 * l2 - d * d) / (2 * l1 * l2))))
    coxa = phi + alfa
    # YXZ: R = Rz·Rx·Ry — a perna dobra para a frente (Y), abre para o lado (X), gira com o quadril (Z)
    cx.rotation_mode = "YXZ"
    cx.rotation_euler = (theta, ADIANTE * coxa, gama)
    balanco(c.j["canela_" + lado], -joelho)
    pe = c.j["pe_" + lado]
    pe.rotation_euler.x = pe_x - theta                          # sola de novo paralela ao chão
    _pe(c, lado, coxa - joelho, inclinacao)


def _alcanca(c, alvos, folga=0.985):
    """Baixa o quadril o mínimo para que todo alvo de tornozelo caiba na perna (com folga).

    No toque do calcanhar, a perna da frente vai esticada e o quadril está no auge do balanço
    lateral: o alvo passava do comprimento da perna, o IK encurtava e o pé de apoio pousava ~4 cm
    fora do lugar (1,1–1,4 px no VT-15, D-108). alvos: [(lado, x, z)] no espaço da raiz."""
    from mathutils import Matrix, Vector
    q = c.j["quadril"]
    alcance = (c.coxa + c.canela) * folga
    for _ in range(8):
        mq = Matrix.Translation(q.location) @ q.rotation_euler.to_matrix().to_4x4()
        pior = 0.0
        for lado, x, z in alvos:
            cx = c.j["coxa_" + lado]
            quadril = mq @ cx.location                          # articulação da coxa, no espaço da raiz
            dx, dy, dz = x - quadril.x, cx.location.y - quadril.y, z - quadril.z
            horiz = math.hypot(dx, dy)
            if horiz >= alcance:
                continue
            falta = -dz - math.sqrt(alcance * alcance - horiz * horiz)
            pior = max(pior, falta)
        if pior <= 1e-5:
            return
        q.location.z -= pior


def _pe(c, lado, canela_abs, inclinacao=0.0):
    """Mantém o pé com a sola no chão (mais `inclinacao` para a frente = ponta para baixo)."""
    balanco(c.j["pe_" + lado], -canela_abs - inclinacao)


def _suave(u):
    return u * u * (3 - 2 * u)


def _zera_peito(c):
    if "peito" in c.j:
        c.j["peito"].rotation_euler = (0.0, 0.0, 0.0)


def _coluna(c, inclina_tronco, gira_quadril, gira_peito, lado_peito=0.0, respira=0.0):
    """Coluna em duas partes (rig do diretor): o tronco inclina (graus, + = frente), o quadril e o
    peito giram em Z em sentidos opostos (contrarrotação)."""
    curv = 6.0 * getattr(c, "curvatura", 1.0 if c.curvado else 0.0)
    c.j["quadril"].rotation_euler.z = math.radians(gira_quadril)
    c.j["tronco"].rotation_euler.y = INCLINA * math.radians(inclina_tronco + curv * 0.6)
    if "peito" in c.j:
        c.j["peito"].rotation_euler = (math.radians(lado_peito), INCLINA * math.radians(curv * 0.4 - respira),
                                       math.radians(gira_peito))
    else:
        c.j["tronco"].rotation_euler.z = math.radians(gira_peito)


def parado(c, t, p):
    """Respira (o peito sobe e abre), transfere o peso (o quadril desliza para o lado de apoio e o
    peito compensa), cabeça que olha em volta; braços soltos com o cotovelo um pouco dobrado."""
    _zera(c)
    _zera_peito(c)
    r = 0.5 - 0.5 * math.cos(2 * math.pi * t)           # respiração
    w = math.sin(2 * math.pi * t)                       # peso de um lado para o outro
    c.j["quadril"].location.z = c.quadril_h - 0.012 * c.k - 0.006 * (1 - r)
    c.j["quadril"].location.y = 0.010 * c.k * w
    c.j["quadril"].rotation_euler.x = math.radians(1.5) * w
    _coluna(c, 2.0, 0.0, 0.0, lado_peito=-2.5 * w, respira=1.5 * r)
    balanco(c.j["pescoco"], math.radians(2 * r - 1))
    c.j["cabeca"].rotation_euler.z = math.radians(4) * math.sin(2 * math.pi * t + 1.0)
    for lado, s in (("e", 1), ("d", -1)):
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-5)
        balanco(c.j["braco_" + lado], math.radians(-3 + 2 * r))
        balanco(c.j["antebraco_" + lado], math.radians(16 + 4 * r))
        c.j["pe_" + lado].rotation_euler.z = s * math.radians(10)     # pés um pouco abertos
        # pés fixos no chão enquanto o peso passa de um para o outro, um pouco mais abertos que o
        # quadril: de frente, abre-se um V entre as pernas e elas não viram uma coluna só (D-113)
        y = s * (c.quadris + 0.025 * c.k)
        _planta(c, lado, 0.0, c.tornozelo, y=y)


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
    """Andar no estilo do rig do diretor: corpo um pouco baixo e inclinado para a frente, quadril
    gira com a perna que avança e o peito gira ao contrário, peso de um pé para o outro, braço
    que balança mais para trás que para a frente com o cotovelo meio dobrado."""
    _zera(c)
    _zera_peito(c)
    passada = p.get("passada", 1.15) * c.k
    pes = _passo(c, t, passada, 0.62, 0.08 * c.k, c.quadril_h - 0.045 * c.k, 0.016 * c.k)
    fase = 2 * math.pi * t
    c.j["quadril"].location.y = 0.014 * c.k * math.cos(fase)          # peso sobre o pé de apoio
    c.j["quadril"].rotation_euler.x = math.radians(2.0) * math.cos(fase)
    _coluna(c, 4.0, 6.0 * math.sin(fase), -9.0 * math.sin(fase), lado_peito=-2.0 * math.cos(fase))
    balanco(c.j["pescoco"], math.radians(-2))
    c.j["cabeca"].rotation_euler.z = math.radians(4) * math.sin(fase)
    _alcanca(c, [(lado, pes[lado][0], pes[lado][1]) for lado in ("e", "d")])
    for lado, s, fs in (("e", 1, 0.0), ("d", -1, 0.5)):
        x, z, incl = pes[lado]
        _planta(c, lado, x, z, inclinacao=incl)
        u = math.cos(2 * math.pi * (t + fs + 0.5))
        a = math.radians(-8) + math.radians(16) * u               # de −24° (trás) a +8° (frente)
        c.j["braco_" + lado].rotation_euler.x = s * math.radians(-3)
        balanco(c.j["braco_" + lado], a)
        balanco(c.j["antebraco_" + lado], math.radians(18) + math.radians(10) * max(0.0, u))


def correr(c, t, p):
    """Corrida do diretor (anim/correr_diretor.py): as 8 poses gravadas, interpoladas se a
    animação tiver outro número de quadros. Corpo curvado inclina um pouco mais."""
    from anim.correr_diretor import POSES
    _zera(c)
    _zera_peito(c)
    n = len(POSES)
    x = (t % 1.0) * n
    i0 = int(math.floor(x)) % n
    i1 = (i0 + 1) % n
    f = x - math.floor(x)
    a, b = POSES[i0], POSES[i1]
    for junta in set(a) | set(b):
        if junta.startswith("_") or junta not in c.j:
            continue
        ra, rb = a.get(junta, [0, 0, 0]), b.get(junta, [0, 0, 0])
        rot = [math.radians(ra[k] + (rb[k] - ra[k]) * f) for k in range(3)]
        if junta == "cabeca":
            rot[1] += math.radians(16.0)     # o rig do diretor já trazia o rosto erguido; aqui é o embrulho
        c.j[junta].rotation_euler = rot
    dz = a["_quadril_dz"] + (b["_quadril_dz"] - a["_quadril_dz"]) * f
    dy = a["_quadril_dy"] + (b["_quadril_dy"] - a["_quadril_dy"]) * f
    c.j["quadril"].location.z = c.quadril_h + dz * c.k
    c.j["quadril"].location.y = dy * c.k
    if getattr(c, "curvatura", 1.0 if c.curvado else 0.0) > 0:
        c.j["tronco"].rotation_euler.y += INCLINA * math.radians(4 * getattr(c, "curvatura", 1.0))


def _rosto_para_a_camera(ciclo):
    """Truque do pixel artista: a câmera isométrica vê o alto da cabeça e o cabelo come o rosto;
    a cabeça sobe o queixo um pouco (além da pose) para o rosto ocupar mais pixels."""
    def com_rosto(c, t, p):
        ciclo(c, t, p)
        r = c.j["cabeca"].rotation_euler
        c.j["cabeca"].rotation_euler = (r.x, r.y - math.radians(c.rosto), r.z)   # Y negativo = queixo para cima
    com_rosto.__name__ = ciclo.__name__
    return com_rosto


CICLOS = {n: _rosto_para_a_camera(f) for n, f in (("parado", parado), ("andar", andar), ("correr", correr))}
