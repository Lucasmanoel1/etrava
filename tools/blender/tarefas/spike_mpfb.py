"""Spike do V1 (D-091): corpo do MPFB2 × kit próprio, mesmo pixelizador, mesma câmera, pose parada.

Não entra no jogo (o YAML não tem `manifesto`). Exige a extensão MPFB instalada numa pasta
isolada (BLENDER_USER_RESOURCES=build/blender-ext). A roupa é pintada por faixa de altura no
próprio corpo (a 48 px a espessura do pano é um pixel), com as mesmas rampas do aprendiz.
"""
import importlib
import math

import addon_utils
import bpy
from mathutils import Vector

from nucleo import camera, materiais, render


def _mpfb():
    nome = next((m.__name__ for m in addon_utils.modules() if m.__name__.endswith(".mpfb")), None)
    if nome is None:
        raise SystemExit("MPFB não instalado (BLENDER_USER_RESOURCES=build/blender-ext)")
    addon_utils.enable(nome, default_set=True)
    return importlib.import_module(nome + ".services.humanservice").HumanService


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L, A = t.get("quadro", [64, 80])
    ancora = t.get("ancora", [32, 72])
    ss = t.get("ss", 4)
    camera.montar(L, A, ancora, ss)
    render.montar_luz()
    hs = _mpfb()
    macros = t.get("macros")
    h = hs.create_human(detailed_helpers=False, macro_detail_dict=macros) if macros else hs.create_human(detailed_helpers=False)
    rig = hs.add_builtin_rig(h, "default")
    # remove a geometria auxiliar (vertex group HelperGeometry / JointCubes)
    import bmesh
    bm = bmesh.new()
    bm.from_mesh(h.data)
    deform = bm.verts.layers.deform.verify()
    tirar = {h.vertex_groups[n].index for n in ("HelperGeometry", "JointCubes") if n in h.vertex_groups}
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if any(k in tirar for k in v[deform].keys())], context="VERTS")
    # roupa por faixa de altura (z em metros, pés em 0)
    pele = materiais.material("pele", "pele_clara", k=0.0, oa=0.6, luz_min=0.45)
    camisa = materiais.material("camisa", "linho", k=0.12, oa=0.7, luz_min=0.55)
    calca = materiais.material("calca", "tecido_marrom", k=0.12, oa=0.7)
    bota = materiais.material("botas", "madeira_escura", k=0.12, oa=0.7)
    cabelo = materiais.material("cabelo", "cabelo_castanho", k=0.12, oa=0.7)
    for m in (pele, camisa, calca, bota, cabelo):
        h.data.materials.append(m)
    alt = max(v.co.z for v in bm.verts)
    escalpo = h.vertex_groups["scalp"].index if "scalp" in h.vertex_groups else -1
    for f in bm.faces:
        c = f.calc_center_median()
        z = c.z / alt
        lado = abs(c.x) / alt
        if escalpo >= 0 and all(escalpo in v[deform] for v in f.verts):
            f.material_index = 4
        elif z < 0.10:
            f.material_index = 3
        elif z < 0.56:
            f.material_index = 2
        elif z < 0.82 and not (lado > 0.20 and z < 0.70):   # manga arregaçada: antebraço de fora
            f.material_index = 1
        else:
            f.material_index = 0
    bm.to_mesh(h.data)
    bm.free()
    # braços para baixo (o corpo vem em pose A)
    bpy.context.view_layer.objects.active = rig
    bpy.context.view_layer.update()   # matrix_world do rig (o MPFB gira e escala o objeto)
    for lado, s in (("L", 1), ("R", -1)):
        pb = rig.pose.bones.get("upperarm01." + lado)
        if pb:
            # o corpo vem em pose A; girar o osso em Z local (negativo à esquerda) baixa o braço
            pb.rotation_mode = "XYZ"
            pb.rotation_euler = (0.0, 0.0, -s * math.radians(t.get("abaixar_braco", 40)))
    # raiz da câmera: o MPFB olha para -Y; o pipeline espera +X
    piv = bpy.data.objects.new("piv", None)
    bpy.context.scene.collection.objects.link(piv)
    piv.parent = camera.raiz()
    for ob in (h, rig):
        if ob.parent is None:
            ob.parent = piv
    piv.rotation_euler = (0, 0, math.radians(90))
    escala = t.get("altura", 1.68) / alt
    piv.scale = (escala, escala, escala)
    direcoes = t.get("direcoes", 8)
    quadros = []
    for d in range(direcoes):
        piv.rotation_euler = (0.0, 0.0, math.radians(90) - d * 2 * math.pi / direcoes)
        nome = "parado_%d_0" % d
        pts = render.quadro(t["saida"], nome, L, A, distancia_oa=t.get("distancia_oa", 0.25))
        quadros.append({"anim": "parado", "dir": d, "f": 0, "arquivo": nome, "pontos": pts})
    render.escrever_json(t["saida"], {"largura": L, "altura": A, "ancora": ancora, "ss": ss,
                                      "folhas": [{"nome": t["id"], "quadros": quadros,
                                                  "animacoes": [{"nome": "parado", "direcoes": direcoes, "quadros": 1, "fps": 1}]}]})
