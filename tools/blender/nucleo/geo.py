"""Malhas simples por bmesh (sem bpy.ops: roda igual em segundo plano)."""
import bmesh
import bpy

from . import camera


def objeto(nome, bm, material=None, pai=None, suave=False):
    me = bpy.data.meshes.new(nome)
    bm.to_mesh(me)
    bm.free()
    if suave:
        for p in me.polygons:
            p.use_smooth = True
    if material is not None:
        me.materials.append(material)
    ob = bpy.data.objects.new(nome, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.parent = pai if pai is not None else camera.raiz()
    return ob


def vazio(nome, pai=None, local=(0, 0, 0)):
    ob = bpy.data.objects.new(nome, None)
    bpy.context.scene.collection.objects.link(ob)
    ob.parent = pai if pai is not None else camera.raiz()
    ob.location = local
    return ob


def caixa(nome, tam, centro=(0, 0, 0), material=None, pai=None):
    """Caixa de tamanho (x, y, z) com a base em centro.z."""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co.x = v.co.x * tam[0] + centro[0]
        v.co.y = v.co.y * tam[1] + centro[1]
        v.co.z = (v.co.z + 0.5) * tam[2] + centro[2]
    return objeto(nome, bm, material, pai)


def subdividir(ob, niveis=2):
    m = ob.modifiers.new("sub", "SUBSURF")
    m.levels = niveis
    m.render_levels = niveis
    return ob
