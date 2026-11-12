"""Luz fixa e render dos passes de um quadro (SPEC-visual §4.3 e §5.1)."""
import json
import math
import os

import bpy
from mathutils import Vector

from . import camera, materiais

# Luz do alto à esquerda da tela, um pouco vinda do observador (STYLE §1.6). No Blender:
# esquerda da tela no chão = (-1, -1); o observador fica em (+1, -1). A face sudoeste (-Y) fica
# iluminada e a sudeste (+X) na sombra, como na arte do M8; a sombra projetada cai à direita.
_HORIZ = Vector((-0.55, -0.85, 0.0)).normalized()
ELEVACAO = math.radians(58)
DIRECAO_LUZ = Vector((_HORIZ.x * math.cos(ELEVACAO), _HORIZ.y * math.cos(ELEVACAO), math.sin(ELEVACAO)))


def montar_luz():
    luz = bpy.data.lights.new("sol", "SUN")
    # calibrado: com esta energia o passe de luz vale exatamente N·L num difuso branco
    # (medido no chão: 0,3654 × N·L com energia 1)
    luz.energy = 1.0 / 0.3654
    luz.angle = 0.0
    try:
        luz.cycles.cast_shadow = True
    except AttributeError:
        pass
    ob = bpy.data.objects.new("sol", luz)
    bpy.context.scene.collection.objects.link(ob)
    # o sol aponta para -Z local; gira -Z até -DIRECAO_LUZ
    ob.rotation_euler = (-DIRECAO_LUZ).to_track_quat("-Z", "Y").to_euler()
    return ob


def chao_de_sombra(tamanho=30.0):
    """Plano em z=0 que só existe para receber a sombra projetada (objetos parados)."""
    import bmesh
    me = bpy.data.meshes.new("chao_sombra")
    bm = bmesh.new()
    bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=tamanho / 2)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(materiais.sombra_chao())
    ob = bpy.data.objects.new("chao_sombra", me)
    ob.parent = camera.raiz()
    bpy.context.scene.collection.objects.link(ob)
    return ob


def _render(caminho):
    s = bpy.context.scene
    s.render.filepath = caminho
    bpy.ops.render.render(write_still=True)


def quadro(pasta, nome, largura, altura, distancia_oa=0.6, pontos=None, amostras_oa=8):
    """Renderiza os passes a e b de um quadro e devolve os pontos marcados em px de arte.

    pontos: objetos (vazios) cujo nome começa com "ponto." — mão, cabeça, olhos, luz, fumaça.
    Cada ponto vai com x, y e `visivel` (para olhos: virado para a câmera).
    """
    os.makedirs(pasta, exist_ok=True)
    bpy.context.view_layer.update()
    materiais.aplicar_passe("a", distancia_oa, amostras_oa)
    _render(os.path.join(pasta, nome + "_a.png"))
    materiais.aplicar_passe("b", distancia_oa, amostras_oa)
    _render(os.path.join(pasta, nome + "_b.png"))
    saida = {}
    vista = camera.direcao_vista()
    for ob in (pontos if pontos is not None else [o for o in bpy.data.objects if o.name.startswith("ponto.")]):
        x, y = camera.para_tela(ob.matrix_world.translation, largura, altura)
        frente = ob.matrix_world.to_3x3() @ Vector((1, 0, 0))
        visivel = frente.length == 0 or frente.normalized().dot(-vista) > 0.15
        saida[ob.name[len("ponto."):]] = {"x": round(x, 3), "y": round(y, 3), "visivel": bool(visivel)}
    return saida


def escrever_json(pasta, dados):
    dados = dict(dados)
    dados["materiais"] = materiais.registrados()
    dados["indice_sombra"] = materiais.INDICE_SOMBRA
    dados["blender"] = bpy.app.version_string
    with open(os.path.join(pasta, "render.json"), "w", encoding="utf-8") as f:
        json.dump(dados, f, ensure_ascii=False, indent=1, sort_keys=True)
