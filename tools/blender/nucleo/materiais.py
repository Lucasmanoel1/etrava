"""Materiais do pipeline: cada material do jogo vira um índice e os parâmetros que o pixelizador
precisa (rampa, quanto de textura, pontilhado, brilho). A cor nunca sai daqui — sai da rampa
da paleta (SPEC-visual §5.3). Os nós de cada material são refeitos para cada passe:

passe "a"  (emissão pura): R = índice/255, G = profundidade (0 perto, 1 longe), B = 1 se emissivo
passe "b"  (luz do sol + emissão): R = luz direta com sombra, G = oclusão ambiente, B = detalhe
"""
import os

import bpy

from . import camera

_MATS = []          # dicionários na ordem de registro; índice = posição + 1
INDICE_SOMBRA = 255  # chão que só recebe sombra (objetos estáticos)
_PASTA_TEXTURAS = None


def pasta_texturas(p):
    global _PASTA_TEXTURAS
    _PASTA_TEXTURAS = p


def registrados():
    return list(_MATS)


def material(nome, rampa, k=0.25, textura=None, escala=1.0, ruido=None, var_objeto=0.0,
             pontilhar=False, emissivo=False, contorno=True, luz_min=0.0, oa=1.0, tons=None, linhas=True,
             rampa_brilho=None, escala2=None, peso2=0.0):
    """Registra (ou devolve) o material `nome`.

    k          quanto a textura mexe no valor (0 = liso; casca e colmo ~0,5)
    textura    PNG em tools/blender/texturas (luminância); projeção em caixa, `escala` repetições/m
    ruido      escala de um ruído procedural, quando não há textura
    var_objeto variação de valor por objeto (folhas, pedras): cada objeto um pouco diferente
    luz_min    piso de luz (tecidos claros não afundam no preto do lado da sombra)
    oa         peso da oclusão ambiente
    tons       limita a rampa aos tons [i, j] (o pixelizador usa só esses)
    """
    for m in _MATS:
        if m["nome"] == nome:
            return bpy.data.materials[nome]
    if len(_MATS) >= 250:
        raise RuntimeError("materiais demais num trabalho")
    info = {"nome": nome, "indice": len(_MATS) + 1, "rampa": rampa, "k": k, "textura": textura,
            "escala": escala, "ruido": ruido, "var_objeto": var_objeto, "pontilhar": pontilhar,
            "emissivo": emissivo, "contorno": contorno, "luz_min": luz_min, "oa": oa, "tons": tons,
            "linhas": linhas, "rampa_brilho": rampa_brilho, "escala2": escala2, "peso2": peso2}
    _MATS.append(info)
    mat = bpy.data.materials.new(nome)
    mat["etrava"] = info["indice"]
    return mat


def sombra_chao():
    mat = bpy.data.materials.get("_sombra") or bpy.data.materials.new("_sombra")
    mat["etrava"] = INDICE_SOMBRA
    return mat


def _limpar(mat):
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    return nt, nt.nodes.new("ShaderNodeOutputMaterial")


def _rgb(nt, r, g, b):
    """Monta uma cor (r, g, b), cada canal um valor ou um soquete de saída."""
    comb = nt.nodes.new("ShaderNodeCombineColor")
    for i, v in enumerate((r, g, b)):
        if isinstance(v, (int, float)):
            comb.inputs[i].default_value = float(v)
        else:
            nt.links.new(v, comb.inputs[i])
    return comb.outputs[0]


def _emissao(nt, cor):
    em = nt.nodes.new("ShaderNodeEmission")
    em.inputs["Strength"].default_value = 1.0
    nt.links.new(cor, em.inputs["Color"])
    return em


def _profundidade(nt):
    cd = nt.nodes.new("ShaderNodeCameraData")
    mr = nt.nodes.new("ShaderNodeMapRange")
    mr.inputs["From Min"].default_value = camera.DISTANCIA - 20.0
    mr.inputs["From Max"].default_value = camera.DISTANCIA + 20.0
    nt.links.new(cd.outputs["View Z Depth"], mr.inputs["Value"])
    return mr.outputs["Result"]


def _detalhe(nt, info):
    """Luminância do material (0..1, 0,5 = neutro) que entra como variação de valor."""
    saida = None
    if info["textura"]:
        caminho = os.path.join(_PASTA_TEXTURAS or "", info["textura"])
        img = bpy.data.images.get(info["textura"])
        if img is None and os.path.exists(caminho):
            img = bpy.data.images.load(caminho)
            img.name = info["textura"]
            img.colorspace_settings.name = "Non-Color"
        if img is not None:
            tc = nt.nodes.new("ShaderNodeTexCoord")
            mp = nt.nodes.new("ShaderNodeMapping")
            mp.inputs["Scale"].default_value = (info["escala"],) * 3
            nt.links.new(tc.outputs["Object"], mp.inputs["Vector"])
            tex = nt.nodes.new("ShaderNodeTexImage")
            tex.image = img
            tex.projection = "BOX"
            tex.projection_blend = 0.25
            tex.interpolation = "Closest"
            nt.links.new(mp.outputs["Vector"], tex.inputs["Vector"])
            bw = nt.nodes.new("ShaderNodeRGBToBW")
            nt.links.new(tex.outputs["Color"], bw.inputs["Color"])
            saida = bw.outputs["Val"]
            if info.get("escala2"):
                # segunda leitura da mesma textura, bem maior e deslocada: variação larga (manchas)
                mp2 = nt.nodes.new("ShaderNodeMapping")
                mp2.inputs["Scale"].default_value = (info["escala2"],) * 3
                mp2.inputs["Location"].default_value = (0.37, 0.61, 0.0)
                nt.links.new(tc.outputs["Object"], mp2.inputs["Vector"])
                tex2 = nt.nodes.new("ShaderNodeTexImage")
                tex2.image = img
                tex2.projection = "BOX"
                tex2.interpolation = "Linear"
                nt.links.new(mp2.outputs["Vector"], tex2.inputs["Vector"])
                bw2 = nt.nodes.new("ShaderNodeRGBToBW")
                nt.links.new(tex2.outputs["Color"], bw2.inputs["Color"])
                mix = nt.nodes.new("ShaderNodeMix")
                mix.data_type = "FLOAT"
                mix.inputs["Factor"].default_value = info["peso2"]
                nt.links.new(saida, mix.inputs["A"])
                nt.links.new(bw2.outputs["Val"], mix.inputs["B"])
                saida = mix.outputs["Result"]
    if saida is None and info["ruido"]:
        tc = nt.nodes.new("ShaderNodeTexCoord")
        nz = nt.nodes.new("ShaderNodeTexNoise")
        nz.inputs["Scale"].default_value = info["ruido"]
        nz.inputs["Detail"].default_value = 3.0
        nt.links.new(tc.outputs["Object"], nz.inputs["Vector"])
        saida = nz.outputs["Fac"]
    if info["var_objeto"] > 0:
        oi = nt.nodes.new("ShaderNodeObjectInfo")
        mr = nt.nodes.new("ShaderNodeMapRange")
        mr.inputs["To Min"].default_value = 0.5 - info["var_objeto"]
        mr.inputs["To Max"].default_value = 0.5 + info["var_objeto"]
        nt.links.new(oi.outputs["Random"], mr.inputs["Value"])
        if saida is None:
            saida = mr.outputs["Result"]
        else:
            soma = nt.nodes.new("ShaderNodeMath")
            soma.operation = "ADD"
            nt.links.new(saida, soma.inputs[0])
            nt.links.new(mr.outputs["Result"], soma.inputs[1])
            menos = nt.nodes.new("ShaderNodeMath")
            menos.operation = "SUBTRACT"
            menos.use_clamp = True
            nt.links.new(soma.outputs[0], menos.inputs[0])
            menos.inputs[1].default_value = 0.5
            saida = menos.outputs[0]
    return saida if saida is not None else 0.5


def _arvore(mat, indice, emissivo, info, passe, distancia_oa, amostras_oa=8):
    nt, out = _limpar(mat)
    if passe == "a":
        cor = _rgb(nt, indice / 255.0, _profundidade(nt), 1.0 if emissivo else 0.0)
        nt.links.new(_emissao(nt, cor).outputs[0], out.inputs["Surface"])
        return
    dif = nt.nodes.new("ShaderNodeBsdfDiffuse")
    dif.inputs["Color"].default_value = (1, 0, 0, 1)
    ao = nt.nodes.new("ShaderNodeAmbientOcclusion")
    ao.samples = amostras_oa
    ao.only_local = False
    ao.inputs["Distance"].default_value = distancia_oa
    det = _detalhe(nt, info) if info else 0.5
    cor = _rgb(nt, 0.0, ao.outputs["AO"], det)
    add = nt.nodes.new("ShaderNodeAddShader")
    nt.links.new(dif.outputs[0], add.inputs[0])
    nt.links.new(_emissao(nt, cor).outputs[0], add.inputs[1])
    nt.links.new(add.outputs[0], out.inputs["Surface"])


def aplicar_passe(passe, distancia_oa=0.6, amostras_oa=8):
    for info in _MATS:
        _arvore(bpy.data.materials[info["nome"]], info["indice"], info["emissivo"], info, passe, distancia_oa,
                amostras_oa)
    sc = bpy.data.materials.get("_sombra")
    if sc is not None:
        _arvore(sc, INDICE_SOMBRA, False, None, passe, distancia_oa, amostras_oa)
