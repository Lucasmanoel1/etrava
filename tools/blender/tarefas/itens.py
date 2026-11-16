"""Ícones dos itens (V6): cada elemento da ficha vira uma folha de um quadro (32×32 px de arte,
fundo transparente; o contorno é do pixelizador).

Câmera própria do ícone: ortográfica, olhando de -Y com elevação `elev` (graus; padrão 40), sem a
correção vertical do mundo (o ícone mostra o objeto na proporção dele). A luz é a do mundo (alto,
à esquerda, um pouco da frente). `pose` = [rx, ry, rz] em graus gira o item (o vazio `raiz`), não
a câmera, para a luz ficar fixa em relação à tela, como no mundo (SPEC-visual §4.2).

Enquadramento automático: o item é escalado para que a maior medida na tela seja 1 unidade, e a
câmera mostra `tamanho` px (padrão 28) por unidade, centrada no item; sobram 2 px de cada lado
para o contorno. Com o tamanho normalizado, a oclusão e as linhas internas valem igual para um
prego e para um cajado.
"""
import math

import bpy
from mathutils import Vector

from itens import modelos
from nucleo import camera, materiais, render


def _limpar():
    for ob in [o for o in bpy.data.objects if o.type in ("MESH", "CURVE")]:
        bpy.data.objects.remove(ob, do_unlink=True)


def _camera(L, ss):
    s = bpy.context.scene
    s.render.resolution_x = int(L * ss)
    s.render.resolution_y = int(L * ss)
    s.render.pixel_aspect_x = 1.0
    s.render.pixel_aspect_y = 1.0
    cd = bpy.data.cameras.new("camera_icone")
    cd.type = "ORTHO"
    cd.sensor_fit = "HORIZONTAL"
    cd.clip_start = 1.0
    cd.clip_end = camera.DISTANCIA * 2
    cam = bpy.data.objects.new("camera_icone", cd)
    s.collection.objects.link(cam)
    s.camera = cam
    return cam


def _apontar(cam, elev):
    cam.rotation_euler = (math.radians(90.0 - elev), 0.0, 0.0)
    olhar = cam.rotation_euler.to_matrix() @ Vector((0, 0, -1))
    cam.location = -olhar * camera.DISTANCIA
    bpy.context.view_layer.update()


def _extensao(cam):
    """Caixa do item no plano da câmera (unidades do mundo)."""
    bpy.context.view_layer.update()
    inv = cam.matrix_world.inverted()
    x0 = y0 = 1e9
    x1 = y1 = -1e9
    for ob in bpy.data.objects:
        if ob.type != "MESH":
            continue
        mw = ob.matrix_world
        for v in ob.data.vertices:
            p = inv @ (mw @ v.co)
            x0, x1 = min(x0, p.x), max(x1, p.x)
            y0, y1 = min(y0, p.y), max(y1, p.y)
    return x0, x1, y0, y1


def executar(t):
    materiais.pasta_texturas(t["texturas"])
    L = t.get("lado", 32)
    ss = t.get("ss", 4)
    cam = _camera(L, ss)
    render.montar_luz()
    bpy.context.scene.cycles.samples = t.get("amostras", 16)
    raiz = camera.raiz()
    folhas = []
    for el in t["elementos"]:
        _limpar()
        raiz.scale = (1.0, 1.0, 1.0)
        raiz.location = (0.0, 0.0, 0.0)
        raiz.rotation_euler = tuple(math.radians(a) for a in el.get("pose", [0, 0, 0]))
        _apontar(cam, el.get("elev", 40.0))
        modelos.MODELOS[el.get("modelo", el["nome"])](el)
        x0, x1, y0, y1 = _extensao(cam)
        s = 1.0 / max(x1 - x0, y1 - y0)
        raiz.scale = (s, s, s)
        orto = L / float(el.get("tamanho", t.get("tamanho", 28)))
        cam.data.ortho_scale = orto
        cam.data.shift_x = (x0 + x1) / 2 * s / orto
        cam.data.shift_y = (y0 + y1) / 2 * s / orto
        arq = el["nome"] + "_0"
        render.quadro(t["saida"], arq, L, L, distancia_oa=t.get("distancia_oa", 0.12), pontos=[],
                      amostras_oa=t.get("amostras", 16))
        folhas.append({"nome": el["nome"], "quadros": [{"anim": "parado", "dir": 0, "f": 0, "arquivo": arq,
                                                        "pontos": {}}],
                       "animacoes": [{"nome": "parado", "direcoes": 1, "quadros": 1, "fps": 1}]})
    render.escrever_json(t["saida"], {"largura": L, "altura": L, "ancora": t.get("ancora", [16, 31]), "ss": ss,
                                      "folhas": folhas})
