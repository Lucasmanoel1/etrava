"""Câmera isométrica do ETRAVA (SPEC-visual §4).

Coordenadas: o jogo usa x para leste, y para sul, z para cima; no Blender, X = x, Y = -y, Z = z.
Tela de arte (densidade dobrada, D-086): um tile de 1 m é um losango de 64×32 px e 1 m de
altura mede 28 px. Ortográfica, elevação 30°, azimute 45° (a câmera fica a sudeste).

Na projeção real 1 m de altura mediria 45,25·cos30° = 39,19 px; o jogo usa 28. Por isso tudo o
que se fotografa é filho do vazio `raiz`, com escala Z = 28/39,19 (correção vertical).

A origem do objeto cai exatamente no CANTO superior esquerdo do pixel da âncora (ax, ay), que é
o ponto que o cliente põe sobre a posição do objeto no mundo (SceneRenderer: left = sx - ax).
"""
import math

import bpy
from mathutils import Vector

PX_POR_M = 32.0 * math.sqrt(2.0)          # 45,25 px de arte por metro na horizontal da tela
Z_PX = 28.0                                # px de arte por metro de altura no jogo
CORRECAO_Z = Z_PX / (PX_POR_M * math.cos(math.radians(30)))
DISTANCIA = 60.0


def raiz():
    """Vazio com a correção vertical; tudo o que aparece no render fica embaixo dele."""
    r = bpy.data.objects.get("raiz")
    if r is None:
        r = bpy.data.objects.new("raiz", None)
        bpy.context.scene.collection.objects.link(r)
        r.scale = (1.0, 1.0, CORRECAO_Z)
    return r


def montar(largura, altura, ancora, ss):
    """Cria a câmera para um quadro de `largura`×`altura` px de arte, superamostrado `ss` vezes."""
    s = bpy.context.scene
    s.render.resolution_x = int(largura * ss)
    s.render.resolution_y = int(altura * ss)
    s.render.pixel_aspect_x = 1.0
    s.render.pixel_aspect_y = 1.0
    cam_data = bpy.data.cameras.new("camera")
    cam_data.type = "ORTHO"
    cam_data.sensor_fit = "HORIZONTAL"
    cam_data.ortho_scale = largura / PX_POR_M
    cam_data.clip_start = 1.0
    cam_data.clip_end = DISTANCIA * 2
    # desloca o quadro para a origem cair no canto do pixel da âncora; o deslocamento é
    # medido em frações da maior dimensão do quadro
    maior = float(max(largura, altura))
    cam_data.shift_x = (largura / 2.0 - ancora[0]) / maior
    cam_data.shift_y = (ancora[1] - altura / 2.0) / maior
    cam = bpy.data.objects.new("camera", cam_data)
    s.collection.objects.link(cam)
    cam.rotation_euler = (math.radians(60), 0.0, math.radians(45))
    olhar = cam.rotation_euler.to_matrix() @ Vector((0, 0, -1))
    cam.location = -olhar * DISTANCIA
    s.camera = cam
    return cam


def direcao_vista():
    """Vetor unitário do olho para a cena (mundo do Blender)."""
    cam = bpy.context.scene.camera
    return (cam.matrix_world.to_3x3() @ Vector((0, 0, -1))).normalized()


def para_tela(co_mundo, largura, altura):
    """Ponto do mundo → (x, y) em px de arte, a partir do canto superior esquerdo do quadro."""
    from bpy_extras.object_utils import world_to_camera_view
    s = bpy.context.scene
    v = world_to_camera_view(s, s.camera, co_mundo)
    return (v.x * largura, (1.0 - v.y) * altura)
