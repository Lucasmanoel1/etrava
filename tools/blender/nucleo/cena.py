"""Cena vazia e configuração de render fixa (determinismo, VT-16)."""
import bpy


def limpar():
    """Apaga tudo o que a cena de fábrica traz e deixa o render pronto."""
    for coll in (bpy.data.objects, bpy.data.meshes, bpy.data.materials, bpy.data.lights,
                 bpy.data.cameras, bpy.data.images, bpy.data.curves, bpy.data.node_groups):
        for item in list(coll):
            coll.remove(item)
    s = bpy.context.scene
    s.render.engine = "CYCLES"
    c = s.cycles
    c.device = "CPU"
    c.samples = 8
    c.use_adaptive_sampling = False
    c.use_denoising = False
    c.seed = 7
    c.use_animated_seed = False
    # sem antisserrilhado: cada amostra cai no centro do pixel; a redução é por voto (SPEC §5.2.1)
    c.pixel_filter_type = "BOX"
    c.filter_width = 0.01
    c.max_bounces = 0
    c.diffuse_bounces = 0
    c.glossy_bounces = 0
    c.transmission_bounces = 0
    c.transparent_max_bounces = 0
    c.caustics_reflective = False
    c.caustics_refractive = False
    s.render.film_transparent = True
    s.render.use_persistent_data = False
    s.render.resolution_percentage = 100
    s.render.use_compositing = False
    s.render.use_sequencer = False
    s.render.dither_intensity = 0.0
    im = s.render.image_settings
    im.file_format = "PNG"
    im.color_mode = "RGBA"
    im.color_depth = "16"
    im.compression = 15
    # os passes são dados, não imagem: nada de curva de tela
    s.display_settings.display_device = "sRGB"
    try:
        s.view_settings.view_transform = "Raw"
    except TypeError:
        s.view_settings.view_transform = "Standard"
    s.view_settings.look = "None"
    s.view_settings.exposure = 0.0
    s.view_settings.gamma = 1.0
    s.sequencer_colorspace_settings.name = "sRGB"
    w = bpy.data.worlds.get("World") or bpy.data.worlds.new("World")
    s.world = w
    w.color = (0, 0, 0)
    if w.node_tree is not None:
        for n in w.node_tree.nodes:
            if n.bl_idname == "ShaderNodeBackground":
                n.inputs["Color"].default_value = (0, 0, 0, 1)
                n.inputs["Strength"].default_value = 0.0
    return s
