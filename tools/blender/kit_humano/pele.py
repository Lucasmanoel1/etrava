"""Pele contínua do kit humano (D-102, a partir do rig orgânico do diretor).

As peças rígidas de camisa, calça, botas e alça (tubos presos às juntas) viram, em repouso, uma
malha só por grupo: une, refaz em voxels (o joelho e a virilha deixam de ser duas bolas
encostadas) e prende a um esqueleto (armature). Os ossos não têm hierarquia: a cada quadro, cada
osso copia o movimento da sua junta (as juntas vazias continuam mandando na pose; os ciclos de
anim/ciclos.py não mudam). Braços, mãos, cabeça e acessórios continuam rígidos, como no rig do
diretor.
"""
import math

import bmesh
import bpy
from mathutils import Matrix, Vector

from nucleo import camera, geo

# osso: (junta que ele segue, junta da ponta ou deslocamento local da ponta)
OSSOS = {
    "pelvis": ("quadril", (0, 0, 0.12)),
    "lower_spine": ("tronco", "peito"),
    "chest": ("peito", "pescoco"),
    "thigh_e": ("coxa_e", "canela_e"), "shin_e": ("canela_e", "pe_e"), "foot_e": ("pe_e", (0.15, 0, 0)),
    "thigh_d": ("coxa_d", "canela_d"), "shin_d": ("canela_d", "pe_d"), "foot_d": ("pe_d", (0.15, 0, 0)),
}
GRUPOS = {
    "camisa": ("pelvis", "lower_spine", "chest", "thigh_e", "thigh_d"),
    "calca": ("pelvis", "lower_spine", "thigh_e", "shin_e", "thigh_d", "shin_d"),
    "botas_e": ("shin_e", "foot_e"),
    "botas_d": ("shin_d", "foot_d"),
    "alca": ("pelvis", "lower_spine", "chest"),
    "avental": ("pelvis", "lower_spine", "chest"),   # sem as pernas: o avental não se rasga no passo
}
VOXEL = 0.011


def _no_espaco_da_raiz(ob):
    return camera.raiz().matrix_world.inverted() @ ob.matrix_world


def _segmento(c, osso):
    junta, ponta = OSSOS[osso]
    m = _no_espaco_da_raiz(c.j[junta])
    a = m.translation.copy()
    if isinstance(ponta, str):
        b = _no_espaco_da_raiz(c.j[ponta]).translation.copy()
    else:
        b = m @ Vector(ponta)
    return a, b


def _dist_segmento(p, a, b):
    ab = b - a
    t = max(0.0, min(1.0, (p - a).dot(ab) / max(1e-9, ab.length_squared)))
    return (p - (a + ab * t)).length


def _unir(pecas):
    """Malha avaliada (com subdivisão) de cada peça, no espaço da raiz, numa malha só."""
    dg = bpy.context.evaluated_depsgraph_get()
    verts, faces = [], []
    for ob in pecas:
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        m = _no_espaco_da_raiz(ob)
        base = len(verts)
        verts.extend([tuple(m @ v.co) for v in me.vertices])
        faces.extend([tuple(base + i for i in p.vertices) for p in me.polygons])
        ev.to_mesh_clear()
    me = bpy.data.meshes.new("uniao")
    me.from_pydata(verts, [], faces)
    me.update()
    return me


def _refazer_em_voxels(nome, me, material):
    tmp = bpy.data.objects.new(nome + "_tmp", me)
    bpy.context.scene.collection.objects.link(tmp)
    tmp.parent = camera.raiz()
    rm = tmp.modifiers.new("voxel", "REMESH")
    rm.mode = "VOXEL"
    rm.voxel_size = VOXEL
    rm.adaptivity = 0.0
    sm = tmp.modifiers.new("liso", "SMOOTH")
    sm.factor = 0.5
    sm.iterations = 2
    dg = bpy.context.evaluated_depsgraph_get()
    novo = bpy.data.meshes.new_from_object(tmp.evaluated_get(dg))
    bpy.data.objects.remove(tmp, do_unlink=True)
    bpy.data.meshes.remove(me)
    for p in novo.polygons:
        p.use_smooth = True
    novo.materials.clear()
    novo.materials.append(material)
    ob = bpy.data.objects.new(nome, novo)
    bpy.context.scene.collection.objects.link(ob)
    ob.parent = camera.raiz()
    return ob


def _pesos(c, ob, ossos):
    """Peso por distância ao segmento de cada osso (os dois mais perto, potência 4); a perna de um
    lado nunca puxa vértice do outro lado (a virilha une as duas pernas na malha)."""
    segs = {o: _segmento(c, o) for o in ossos}
    grupos = {o: ob.vertex_groups.new(name=o) for o in ossos}
    for v in ob.data.vertices:
        p = v.co
        cand = []
        for o in ossos:
            if o.endswith("_e") and p.y < -0.01 * c.k:
                continue
            if o.endswith("_d") and p.y > 0.01 * c.k:
                continue
            d = _dist_segmento(p, *segs[o])
            cand.append((d, o))
        cand.sort()
        cand = cand[:2]
        ws = [(1.0 / (d + 0.004) ** 4, o) for d, o in cand]
        tot = sum(w for w, _ in ws)
        for w, o in ws:
            grupos[o].add([v.index], w / tot, "REPLACE")


# pontes em repouso: tubo encostado em tubo só pela ponta arredondada vira ilha no voxel (o
# joelho virava "contas" na perna esticada); uma esfera na junta une as peças
PONTES = {
    "calca": [("coxa_e", 0.074), ("coxa_d", 0.074), ("canela_e", 0.058), ("canela_d", 0.058),
              ("pe_e", 0.046), ("pe_d", 0.046)],
    "botas_e": [("pe_e", 0.052)],
    "botas_d": [("pe_d", 0.052)],
}


def _ponte(c, junta, raio, material):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=12, v_segments=8, radius=raio * c.k)
    ob = geo.objeto("ponte_" + junta, bm, material, pai=c.j[junta], suave=True)
    ob.matrix_parent_inverse = Matrix.Identity(4)
    return ob


def montar(c):
    """Troca as peças rígidas de cada grupo pela malha contínua e cria o esqueleto que a deforma."""
    for grupo, pontes in PONTES.items():
        if c.pele.get(grupo):
            mat = c.pele[grupo][0].data.materials[0]
            for junta, raio in pontes:
                c.pele[grupo].append(_ponte(c, junta, raio, mat))
    bpy.context.view_layer.update()
    arm_data = bpy.data.armatures.new("pele")
    arm = bpy.data.objects.new("pele", arm_data)
    bpy.context.scene.collection.objects.link(arm)
    arm.parent = camera.raiz()
    bpy.context.view_layer.objects.active = arm
    bpy.ops.object.mode_set(mode="EDIT")
    for nome in OSSOS:
        a, b = _segmento(c, nome)
        eb = arm_data.edit_bones.new(nome)
        eb.head, eb.tail = a, b
    bpy.ops.object.mode_set(mode="OBJECT")
    c.pele_arm = arm
    c.pele_repouso = {nome: _no_espaco_da_raiz(c.j[OSSOS[nome][0]]).copy() for nome in OSSOS}
    c.pele_objs = []
    for grupo, pecas in c.pele.items():
        if not pecas:
            continue
        material = pecas[0].data.materials[0]
        me = _unir(pecas)
        for ob in pecas:
            bpy.data.objects.remove(ob, do_unlink=True)
        ob = _refazer_em_voxels("pele_" + grupo, me, material)
        _pesos(c, ob, GRUPOS[grupo])
        mod = ob.modifiers.new("esqueleto", "ARMATURE")
        mod.object = arm
        mod.use_vertex_groups = True
        c.pele_objs.append(ob)


def atualizar(c):
    """Põe cada osso onde a sua junta foi parar (chamar depois de posar as juntas)."""
    if not getattr(c, "pele_arm", None):
        return
    bpy.context.view_layer.update()
    arm = c.pele_arm
    for nome, (junta, _) in OSSOS.items():
        agora = _no_espaco_da_raiz(c.j[junta])
        delta = agora @ c.pele_repouso[nome].inverted()
        pb = arm.pose.bones[nome]
        pb.matrix = delta @ arm.data.bones[nome].matrix_local
    bpy.context.view_layer.update()
