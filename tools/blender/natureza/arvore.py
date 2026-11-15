"""Árvores do ETRAVA, versão 2 (D-100): estrutura orgânica no Blender, folhagem pintada no acabamento.

O Blender constrói o que é forma:
- tronco que cresce em passos curtos, com desvio, afunilamento e colo largo;
- raízes de apoio que mergulham no chão;
- galhos por colonização do espaço: pontos espalhados no volume da copa (com a silhueta da
  espécie e lobos irregulares) puxam os galhos, que se ramificam sozinhos; o raio de cada galho
  sai do modelo do cano (r² do pai = soma dos r² dos filhos);
- tufos de folha nas pontas — massas irregulares (esfera deformada por ruído, base achatada),
  cada uma com material próprio para o acabamento saber qual tufo é qual.

A aparência da folha NÃO sai daqui: o pintor de folhagem (Java, `Folhagem`) carimba cachos
desenhados em pixel (pincéis em `tools/art/pinceis/folhas.pxo`, editáveis no Pixelorama) usando a
luz e a oclusão deste render. O Blender diz onde há folha e quanta luz chega; o pincel diz como a
folha é desenhada.

Variantes (YAML): altura, copa (largura), semente e o caráter — idade, inclinacao/rumo,
forquilha (dois líderes), densidade, assimetria, seca (sem folha), frutos, toco.
"""
import math
import random

import bmesh
import bpy
from mathutils import Vector, kdtree, noise

from nucleo import camera, geo, materiais

ESPECIES = {
    # copa: perfil do raio da copa pela altura (larga, oval, redonda, ogiva); base: onde a copa
    # começa (fração da altura); lobos: quanto o contorno da copa é irregular; luz/peso: para
    # onde as pontas crescem; tufo: raio dos tufos (m); pontas: atratores por m³; partida: de
    # que fração do tronco os galhos podem brotar
    "carvalho": dict(rampa_folha="folha_carvalho", rampa_casca="casca_carvalho", pincel="carvalho",
                     tronco_r=0.24, fuste=0.40, sinuoso=0.30, raizes=5, copa="larga", base=0.44, lobos=0.38,
                     luz=0.10, peso=0.05, tufo=(0.50, 0.75), achatar=0.75, pontas=9.0, partida=0.70, espaco=1.5,
                     rampa_folha2="folha_carvalho_parda", mistura=0.18, galho_min=0.025,
                     ruido_casca=(10.0, 10.0, 2.2)),
    "betula": dict(rampa_folha="folha_betula", rampa_casca="casca_betula", pincel="betula",
                   tronco_r=0.11, fuste=0.30, sinuoso=0.14, raizes=3, copa="oval", base=0.34, lobos=0.30,
                   luz=0.05, peso=0.35, tufo=(0.30, 0.44), achatar=1.30, pontas=12.0, partida=0.55, espaco=1.55,
                   galho_min=0.018, k_casca=0.9, contraste_casca=0.10,
                   ruido_casca=(4.0, 4.0, 16.0)),
    "macieira": dict(rampa_folha="folha_macieira", rampa_casca="casca_fruteira", pincel="macieira",
                     tronco_r=0.15, fuste=0.30, sinuoso=0.45, raizes=3, copa="redonda", base=0.34, lobos=0.30,
                     luz=0.15, peso=0.10, tufo=(0.45, 0.65), achatar=0.80, pontas=12.0, partida=0.75, espaco=1.0,
                     ruido_casca=(9.0, 9.0, 3.0)),
    "pereira": dict(rampa_folha="folha_pereira", rampa_casca="casca_fruteira", pincel="pereira",
                    tronco_r=0.14, fuste=0.24, sinuoso=0.25, raizes=3, copa="ogiva", base=0.28, lobos=0.25,
                    luz=0.35, peso=0.0, tufo=(0.40, 0.56), achatar=1.0, pontas=12.0, partida=0.6, espaco=1.0,
                    ruido_casca=(9.0, 9.0, 3.0)),
    # ---- V4
    # castanheiro: tronco grosso e curto de casca em sulcos, copa alta e redonda, cachos grandes
    "castanheiro": dict(rampa_folha="folha_castanheiro", rampa_casca="casca_castanheiro", pincel="castanheiro",
                        tronco_r=0.28, fuste=0.34, sinuoso=0.22, raizes=5, copa="redonda", base=0.36, lobos=0.30,
                        luz=0.12, peso=0.04, tufo=(0.55, 0.80), achatar=0.85, pontas=9.0, partida=0.65, espaco=1.4,
                        galho_min=0.028, ruido_casca=(12.0, 12.0, 2.5)),
    # freixo: alto e aberto, galhos que sobem, folha miúda e clara, casca cinza
    "freixo": dict(rampa_folha="folha_freixo", rampa_casca="casca_freixo", pincel="freixo",
                   tronco_r=0.18, fuste=0.45, sinuoso=0.15, raizes=4, copa="oval", base=0.42, lobos=0.36,
                   luz=0.30, peso=0.0, tufo=(0.34, 0.50), achatar=0.95, pontas=8.0, partida=0.60, espaco=1.75,
                   galho_min=0.020, ruido_casca=(9.0, 9.0, 3.0)),
    # pinheiro-silvestre: fuste reto e comprido, galhos só no alto, copa em almofadas deitadas
    "pinheiro": dict(rampa_folha="folha_conifera", rampa_casca="casca_pinheiro", pincel="pinheiro",
                     tronco_r=0.17, fuste=0.66, sinuoso=0.10, raizes=3, copa="guarda", base=0.60, lobos=0.42,
                     luz=0.0, peso=0.30, tufo=(0.42, 0.66), achatar=0.48, pontas=7.0, partida=0.80, espaco=1.25,
                     galho_min=0.022, ruido_casca=(7.0, 7.0, 4.0)),
    # salgueiro: tronco largo e torto, galhos que caem; a folha pende em cortina
    "salgueiro": dict(rampa_folha="folha_salgueiro", rampa_casca="casca_salgueiro", pincel="salgueiro",
                      tronco_r=0.26, fuste=0.36, sinuoso=0.40, raizes=4, copa="redonda", base=0.44, lobos=0.34,
                      luz=0.10, peso=0.85, tufo=(0.34, 0.48), achatar=1.0, pontas=8.0, partida=0.70, espaco=1.35,
                      pendente=1.0, galho_min=0.025, ruido_casca=(13.0, 13.0, 2.0)),
    # ---- arbustos (sem fuste: os galhos saem do pé)
    "zimbro": dict(rente=4, rampa_folha="folha_zimbro", rampa_casca="casca_salgueiro", pincel="zimbro",
                   tronco_r=0.05, fuste=0.12, sinuoso=0.3, raizes=0, copa="ogiva", base=0.05, lobos=0.30,
                   luz=0.45, peso=0.0, tufo=(0.20, 0.28), achatar=1.1, pontas=40.0, partida=0.2, espaco=1.0,
                   galho_min=0.5, ruido_casca=(9.0, 9.0, 3.0)),
    "arbusto": dict(rente=4, rampa_folha="folha_macieira", rampa_casca="casca_fruteira", pincel="arbusto",
                    tronco_r=0.05, fuste=0.10, sinuoso=0.4, raizes=0, copa="redonda", base=0.08, lobos=0.35,
                    luz=0.20, peso=0.10, tufo=(0.22, 0.32), achatar=0.85, pontas=30.0, partida=0.2, espaco=1.1,
                    galho_min=0.5, ruido_casca=(9.0, 9.0, 3.0)),
    "amora": dict(rente=3, rampa_folha="folha_pereira", rampa_casca="casca_fruteira", pincel="amora",
                  tronco_r=0.03, fuste=0.08, sinuoso=0.5, raizes=0, copa="larga", base=0.02, lobos=0.45,
                  luz=0.0, peso=0.25, tufo=(0.18, 0.26), achatar=0.7, pontas=40.0, partida=0.2, espaco=1.0,
                  galho_min=0.5, ruido_casca=(9.0, 9.0, 3.0)),
}

TUFOS_MAX = 64


def _perfil(forma, t):
    """Raio relativo da copa na altura relativa t (0 = base da copa, 1 = topo)."""
    t = max(0.0, min(1.0, t))
    if forma == "larga":     # carvalho: cheia do meio para baixo, topo baixo e arredondado
        return math.sin(math.pi * min(1.0, 0.15 + 0.85 * t)) ** 0.55
    if forma == "oval":
        return math.sin(math.pi * t) ** 0.8
    if forma == "guarda":    # pinheiro: cresce até o alto e termina chata (guarda-chuva)
        return min(1.0, 1.2 * t ** 0.4) * max(0.0, 1.0 - t ** 5) ** 0.5
    if forma == "ogiva":     # pereira: mais larga embaixo, ponta no alto
        return (math.sin(math.pi * t) ** 0.7) * (1.0 - 0.45 * t)
    return math.sin(math.pi * t) ** 0.6   # redonda


def _perpendicular(d, rng):
    """Uma direção perpendicular a d, girada ao acaso em volta dele."""
    a = Vector((0, 0, 1)) if abs(d.z) < 0.9 else Vector((1, 0, 0))
    u = d.cross(a).normalized()
    v = d.cross(u).normalized()
    t = rng.uniform(0, 2 * math.pi)
    return (u * math.cos(t) + v * math.sin(t)).normalized()


class Arvore:
    """Esqueleto por colonização do espaço (Runions et al., 2007)."""

    def __init__(self, especie, var):
        self.e = dict(ESPECIES[especie])
        self.e.update(var.get("forma", {}))
        self.especie = especie
        self.var = var
        self.rng = random.Random(var["semente"])
        self.galhos = []      # listas de (ponto, raio) — viram tubos
        self.pontas = []      # (ponto, direção) dos galhos terminais
        self.centro = Vector((0, 0, 0))
        self.raios = (1.0, 1.0)

    def _crescer(self, origem, direcao, comp, r0, r1, sinuoso, luz, passo=0.12, semente=0.0):
        pts = [(origem.copy(), r0)]
        p, d = origem.copy(), direcao.normalized()
        n = max(3, int(comp / passo))
        for i in range(1, n + 1):
            t = i / n
            q = p * 1.7 + Vector((semente, semente * 0.7, semente * 1.3))
            desvio = Vector((noise.noise(q), noise.noise(q + Vector((5.2, 1.3, 2.8))),
                             noise.noise(q + Vector((1.7, 9.2, 3.1))) * 0.4))
            d = (d + desvio * sinuoso * 0.35 + Vector((0, 0, 1)) * luz * 0.12).normalized()
            p = p + d * (comp / n)
            pts.append((p.copy(), r0 + (r1 - r0) * t))
        return pts, d

    def _atratores(self, z0, z1, rx, ry, quantos):
        e, rng = self.e, self.rng
        sem = Vector((rng.uniform(0, 80), rng.uniform(0, 80), rng.uniform(0, 80)))
        a = rng.uniform(0, 2 * math.pi)
        lado = Vector((math.cos(a), math.sin(a), 0)) * self.var.get("assimetria", 0.2)
        pts = []
        tent = 0
        while len(pts) < quantos and tent < quantos * 40:
            tent += 1
            x, y = rng.uniform(-1.4, 1.4), rng.uniform(-1.4, 1.4)
            t = rng.uniform(0, 1)
            # lobos: o raio da copa muda com a direção e a altura (ruído) — copa assimétrica
            ang = math.atan2(y, x)
            lobo = 1.0 + e["lobos"] * noise.noise(Vector((math.cos(ang) * 1.3, math.sin(ang) * 1.3, t * 2.2)) + sem)
            if math.hypot(x, y) > _perfil(e["copa"], t) * lobo:
                continue
            pts.append(Vector((x * rx, y * ry, z0 + t * (z1 - z0))) + lado * rx * t)
        return pts

    def construir(self):
        e, var, rng = self.e, self.var, self.rng
        altura, copa = var["altura"], var["copa"]
        idade = var.get("idade", 1.0)
        seca = var.get("seca", False)
        r0 = e["tronco_r"] * (0.55 + 0.45 * idade) * var.get("grossura", 1.0)
        fuste = altura * e["fuste"] * var.get("fuste", 1.0)
        inclina = math.radians(var.get("inclinacao", 0.0))
        rumo = math.radians(var.get("rumo", rng.uniform(0, 360)))
        dir0 = Vector((math.sin(inclina) * math.cos(rumo), math.sin(inclina) * math.sin(rumo), math.cos(inclina)))
        tronco, dtop = self._crescer(Vector((0, 0, -0.10)), dir0, fuste, r0, r0 * 0.8, e["sinuoso"] * idade, 0.2,
                                     semente=rng.uniform(0, 100))
        if var.get("toco"):
            self.galhos.append([(p, r * (1.0 + 0.55 * idade * math.exp(-max(0.0, p.z) / 0.35))) for p, r in tronco])
            self._raizes(r0, idade)
            return
        topo = tronco[-1][0]
        z0 = altura * e["base"] * var.get("base", 1.0)
        rx = copa / 2
        ry = copa / 2 * rng.uniform(0.85, 1.0)
        desloc = Vector((topo.x, topo.y, 0))
        vol = math.pi * rx * ry * (altura - z0) * 0.6
        quantos = int(vol * e["pontas"] * var.get("pontas", 1.0))
        atr = [p + desloc for p in self._atratores(z0, altura, rx, ry, quantos)]
        self.centro = Vector((topo.x, topo.y, (z0 + altura) / 2))
        self.raios = (rx, (altura - z0) / 2)

        # nós: o tronco (só a parte alta brota) + o que crescer
        pos, pai = [], []
        for k, (p, r) in enumerate(tronco):
            pos.append(p.copy())
            pai.append(k - 1)
        n_tronco = len(pos)
        vivos = set(range(int(n_tronco * e["partida"]), n_tronco))
        if var.get("forquilha"):        # dois líderes desde o fuste
            for s in (-1, 1):
                d = (dtop + _perpendicular(dtop, rng) * 0.7 * s).normalized()
                ult = n_tronco - 1
                for k in range(5):
                    pos.append(pos[ult] + d * 0.2)
                    pai.append(ult)
                    ult = len(pos) - 1
                    vivos.add(ult)
        passo, influencia, morte = 0.16, max(1.0, copa * 0.35), 0.30
        trop = Vector((0, 0, e["luz"] - e["peso"] * 0.5))
        for _ in range(160):
            if not atr:
                break
            kd = kdtree.KDTree(len(vivos))
            for i in vivos:
                kd.insert(pos[i], i)
            kd.balance()
            puxa = {}
            for a in atr:
                co, i, dist = kd.find(a)
                if i is None or dist > influencia:
                    continue
                if i not in puxa:
                    puxa[i] = Vector((0, 0, 0))
                puxa[i] += (a - co).normalized()
            if not puxa:
                break
            novos = []
            for i in sorted(puxa):
                if puxa[i].length < 1e-6:
                    continue
                d = (puxa[i].normalized() + trop * 0.6).normalized()
                pos.append(pos[i] + d * passo)
                pai.append(i)
                novos.append(len(pos) - 1)
            if not novos:
                break
            vivos.update(novos)
            kd2 = kdtree.KDTree(len(novos))
            for i in novos:
                kd2.insert(pos[i], i)
            kd2.balance()
            atr = [a for a in atr if kd2.find(a)[2] > morte]
        self._esqueleto(pos, pai, n_tronco, r0, idade, seca)
        self._raizes(r0, idade)

    def _esqueleto(self, pos, pai, n_tronco, r0, idade, seca):
        """Raios pelo modelo do cano e cadeias de tubos (o filho mais grosso continua o galho)."""
        n = len(pos)
        filhos = [[] for _ in range(n)]
        for i in range(n):
            if pai[i] >= 0:
                filhos[pai[i]].append(i)
        raio = [0.0] * n
        ponta_r = 0.018
        for i in range(n - 1, -1, -1):
            raio[i] = ponta_r if not filhos[i] else sum(raio[c] ** 2.3 for c in filhos[i]) ** (1 / 2.3)
        fator = (r0 * 0.8) / max(1e-4, raio[n_tronco - 1])
        # o galho afina também com o caminho percorrido desde o tronco (sem isso, com poucas
        # pontas o galho segue grosso até o fim e acaba rombudo)
        dist = [0.0] * n
        for i in range(n_tronco, n):
            dist[i] = dist[pai[i]] + (pos[i] - pos[pai[i]]).length
        dmax = max(dist) if n > n_tronco else 1.0
        for i in range(n_tronco, n):
            afina = max(0.12, 1.0 - 0.85 * dist[i] / (dmax * 1.05))
            raio[i] = max(ponta_r, min(raio[i] * fator, r0 * 0.8 * afina))
        for k in range(n_tronco):
            t = k / max(1, n_tronco - 1)
            raio[k] = r0 * (1 - 0.2 * t) * (1.0 + 0.55 * idade * math.exp(-max(0.0, pos[k].z) / 0.35))
        rng = self.rng
        pilha = [0]
        while pilha:
            i = pilha.pop()
            pts = [(pos[pai[i]], min(raio[pai[i]], raio[i] * 1.1))] if pai[i] >= 0 else []
            while True:
                pts.append((pos[i], raio[i]))
                if not filhos[i]:
                    d = pos[i] - pos[pai[i]] if pai[i] >= 0 else Vector((0, 0, 1))
                    self.pontas.append((pos[i], d.normalized()))
                    break
                fs = sorted(filhos[i], key=lambda c: (-raio[c], c))
                pilha.extend(fs[1:])
                if seca and raio[i] < r0 * 0.22 and rng.random() < 0.06:   # galho seco quebrado
                    break
                i = fs[0]
            if len(pts) > 3:       # suaviza: sem quina a cada passo
                sm = [pts[0]]
                for k in range(1, len(pts) - 1):
                    sm.append(((pts[k - 1][0] + pts[k][0] * 2 + pts[k + 1][0]) / 4, pts[k][1]))
                sm.append(pts[-1])
                pts = sm
            # galho fino dentro da copa não aparece (vira ruído); na árvore seca, tudo aparece
            if len(pts) >= 2 and (seca or pts[0][1] >= self.var.get("galho_min", self.e.get("galho_min", 0.03))):
                self.galhos.append(pts)

    def _raizes(self, r0, idade):
        e, rng = self.e, self.rng
        if e["raizes"] <= 0:
            return
        n = e["raizes"] + (1 if idade > 1.3 else 0)
        giro = rng.uniform(0, 2 * math.pi)
        for i in range(n):
            a = giro + i * 2 * math.pi / n + rng.uniform(-0.3, 0.3)
            d = Vector((math.cos(a), math.sin(a), 0))
            ini = d * r0 * 0.5 + Vector((0, 0, 0.16 + 0.08 * idade))
            fim = d * r0 * rng.uniform(1.7, 2.5) * (0.75 + 0.25 * idade) + Vector((0, 0, -0.08))
            pts = []
            for k in range(7):
                t = k / 6
                p = ini.lerp(fim, t)
                p.z = ini.z * (1 - t) ** 2.2 + fim.z * (1 - (1 - t) ** 2.2)
                pts.append((p, r0 * 0.45 * (1 - 0.8 * t) * (0.8 + 0.3 * idade)))
            self.galhos.append(pts)

    def tufos(self):
        """Tufos nas pontas: primeiro a casca da copa (o que se vê), depois o miolo; sem dois muito
        juntos. A densidade muda o espaçamento; copa rala deixa galho à mostra."""
        var, rng = self.var, self.rng
        dens = var.get("densidade", 1.0)
        rmin, rmax = self.e["tufo"]
        escala = var.get("tufo", 1.0)
        rx, rz = self.raios

        def fora(p):
            d = p - self.centro
            return (d.x / rx) ** 2 + (d.y / rx) ** 2 + (d.z / rz) ** 2

        pontas = sorted(self.pontas, key=lambda t: (-round(fora(t[0]), 3), round(t[0].x, 4), round(t[0].y, 4)))
        escolhidos = []
        for p, d in pontas:
            r = rng.uniform(rmin, rmax) * escala
            c = p + d * r * 0.25
            minimo = r * self.e["espaco"] / max(0.3, dens)
            if any((c - q).length < minimo for q, _ in escolhidos):
                continue
            escolhidos.append((c, r))
            if len(escolhidos) >= TUFOS_MAX:
                break
        if dens < 1.0:
            escolhidos = [t for t in escolhidos if rng.random() < 0.55 + 0.45 * dens]
        # arbusto: um anel de tufos rente ao chão, senão a moita flutua sobre a sombra
        rente = self.e.get("rente", 0)
        if rente and escolhidos:
            giro = rng.uniform(0, 2 * math.pi)
            for k in range(rente):
                a = giro + k * 2 * math.pi / rente
                d = Vector((math.cos(a), math.sin(a), 0)) * rx * rng.uniform(0.35, 0.6)
                r = rng.uniform(rmin, rmax) * escala
                escolhidos.append((d + Vector((0, 0, r * 0.75)), r))
        return escolhidos


def _tufo(nome, centro, raio, achatar, rng, material, pendente=0.0):
    """Massa de folha irregular: esfera deformada por ruído em duas escalas, base achatada.
    Com `pendente` (salgueiro), a metade de baixo desce em cortina, mais estreita."""
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=3, radius=1.0)
    sem = Vector((rng.uniform(0, 50), rng.uniform(0, 50), rng.uniform(0, 50)))
    for v in bm.verts:
        n = v.co.normalized()
        f = 1.0 + 0.38 * noise.noise(n * 1.8 + sem) + 0.14 * noise.noise(n * 4.5 + sem * 1.7)
        co = n * f
        if pendente > 0 and co.z < 0:
            queda = 1.0 + pendente * (2.4 + 0.8 * noise.noise(Vector((co.x * 2.5, co.y * 2.5, 0)) + sem))
            co = Vector((co.x * (1.0 - 0.35 * pendente * -co.z), co.y * (1.0 - 0.35 * pendente * -co.z), co.z * queda))
        elif co.z < -0.45:
            co.z = -0.45 + (co.z + 0.45) * 0.6
        co.z *= achatar
        v.co = co * raio
    ob = geo.objeto(nome, bm, material, suave=True)
    ob.location = centro
    return ob


def Random_tom(semente, i):
    return random.Random(semente * 1009 + i * 31).random()


def construir(especie, var):
    """Monta a árvore da variante; devolve os tufos [(objeto, posição, fase, amplitude, ponto, raio)]."""
    e = dict(ESPECIES[especie])
    e.update(var.get("forma", {}))
    arv = Arvore(especie, var)
    arv.construir()
    seca = var.get("seca", False)
    rampa_casca = var.get("casca", "casca_seca" if seca else e["rampa_casca"])
    casca = materiais.material("casca_" + rampa_casca, rampa_casca, k=e.get("k_casca", 0.55), ruido=1.0,
                               ruido_eixos=e["ruido_casca"], ruido_contraste=e.get("contraste_casca"),
                               oa=0.5, sem_sombra=0.8)
    _curvas("tronco", arv.galhos, casca)
    if var.get("toco"):
        corte = materiais.material("corte", "madeira_clara", k=0.0, oa=0.3)
        topo, r = arv.galhos[0][-1]
        bm = bmesh.new()
        bmesh.ops.create_circle(bm, cap_ends=True, segments=16, radius=r * 1.02)
        for v in bm.verts:
            v.co += topo + Vector((0, 0, 0.005))
        geo.objeto("corte", bm, corte)
        return []
    if seca:
        return []
    rng = random.Random(var["semente"] * 7 + 1)
    tufos = []
    altura = var["altura"]
    lista = arv.tufos()
    # elipsoide da copa medido nos tufos de verdade (mundo do Blender: z com a correção vertical)
    if lista:
        cx = sum(c.x for c, _ in lista) / len(lista)
        cy = sum(c.y for c, _ in lista) / len(lista)
        zs = [c.z for c, _ in lista]
        cz = (min(zs) + max(zs)) / 2
        rx = max(math.hypot(c.x - cx, c.y - cy) + r for c, r in lista)
        rz = (max(zs) - min(zs)) / 2 + max(r for _, r in lista)
        nc = (cx, cy, cz * camera.CORRECAO_Z, rx, rz * camera.CORRECAO_Z, var.get("normal_copa", 0.6))
    for i, (c, r) in enumerate(lista):
        nome = "folha_%s_%02d" % (especie, i)
        # alguns tufos de outra cor (folha mais velha, parda): a copa não é de um verde só
        rampa = var.get("folha", e["rampa_folha"])
        if e.get("rampa_folha2") and Random_tom(var["semente"], i) < var.get("mistura", e.get("mistura", 0.0)):
            rampa = e["rampa_folha2"]
            nome = "folha2_%s_%02d" % (especie, i)
        mat = materiais.material(nome, rampa, k=0.0, oa=0.7,
                                 linhas=False, pincel=e["pincel"], grupo="folha_" + especie)
        materiais.ajustar(nome, normal_copa=nc)
        ob = _tufo("tufo%03d" % i, c, r, e["achatar"], rng, mat, e.get("pendente", 0.0))
        ponto = geo.vazio("ponto.tufo%03d" % i, pai=ob)
        amp = (0.03 + 0.05 * max(0.0, (c.z - altura * 0.35) / altura)) * (1.0 + 0.7 * e.get("pendente", 0.0))
        tufos.append((ob, c.copy(), rng.uniform(0, 0.2), amp, ponto, r))
    if var.get("frutos"):
        _frutos(var, tufos, rng)
    return tufos


def _curvas(nome, galhos, material):
    """Todos os galhos num objeto de curva com bisel (raio por ponto): tubos lisos, sem emenda."""
    cu = bpy.data.curves.new(nome, "CURVE")
    cu.dimensions = "3D"
    cu.bevel_mode = "ROUND"
    cu.bevel_depth = 1.0
    cu.bevel_resolution = 3
    cu.resolution_u = 3
    cu.use_fill_caps = True
    for pts in galhos:
        sp = cu.splines.new("POLY")
        sp.points.add(len(pts) - 1)
        for k, (p, r) in enumerate(pts):
            sp.points[k].co = (p.x, p.y, p.z, 1.0)
            sp.points[k].radius = r
        sp.use_smooth = True
    cu.materials.append(material)
    ob = bpy.data.objects.new(nome, cu)
    bpy.context.scene.collection.objects.link(ob)
    ob.parent = camera.raiz()
    return ob


def _frutos(var, tufos, rng):
    """Frutos na casca de baixo e de fora dos tufos (presos ao tufo: balançam junto) e alguns caídos."""
    rampa = var["frutos"].get("rampa", "ferrugem")
    mat = materiais.material("fruto_" + rampa, rampa, k=0.0, oa=0.3, linhas=False)
    raio = var["frutos"].get("raio", 0.065)
    n = var["frutos"].get("quantos", 24)
    for k in range(n):
        ob, c, _, _, _, rt = tufos[rng.randrange(len(tufos))]
        d = Vector((rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-0.9, 0.3))).normalized()
        if d.x - d.y < -0.4:          # o lado de quem olha (sudeste, +X −Y) aparece mais
            d = Vector((-d.x, -d.y, d.z))
        bm = bmesh.new()
        bmesh.ops.create_icosphere(bm, subdivisions=1, radius=raio)
        fr = geo.objeto("fruto%03d" % k, bm, mat, pai=ob, suave=True)
        fr.location = d * rt * 0.95
    for k in range(var["frutos"].get("chao", 0)):
        a = rng.uniform(0, 2 * math.pi)
        dist = rng.uniform(0.5, var["copa"] * 0.45)
        bm = bmesh.new()
        bmesh.ops.create_icosphere(bm, subdivisions=1, radius=raio)
        fr = geo.objeto("fruto_chao%03d" % k, bm, mat, suave=True)
        fr.location = (math.cos(a) * dist, math.sin(a) * dist, raio * 0.6)


def vento(tufos, t):
    """Laço de vento (D-103: 96 quadros a 60 qps): a copa balança junta, cada tufo um pouco
    atrasado; um segundo harmônico (rajada) tira a cara de seno perfeito. Periódico em t ∈ [0, 1)."""
    dire = Vector((0.7, 0.7, 0.0))
    lado = Vector((-0.7, 0.7, 0.0))
    for ob, base, fase, amp, _, _ in tufos:
        u = 2 * math.pi * (t + fase)
        s = 0.78 * math.sin(u) + 0.22 * math.sin(2 * u + 1.3)
        w = 0.25 * math.sin(u + 1.9)
        ob.location = base + dire * (amp * s) + lado * (amp * w)
