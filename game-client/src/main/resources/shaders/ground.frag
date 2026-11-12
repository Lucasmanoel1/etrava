// Chão da Fase V: a máscara (u_texture) recorta o tile; a cor vem da textura do terreno
// (u_terrain), repetível e alinhada ao mundo: 1 px de arte = 2 px de mundo (D-086).
#ifdef GL_ES
precision mediump float;
#endif
varying vec4 v_color;
varying vec2 v_texCoords;
varying vec2 v_world;
uniform sampler2D u_texture;
uniform sampler2D u_terrain;
uniform vec2 u_size;

void main() {
    vec4 m = texture2D(u_texture, v_texCoords);
    if (m.a < 0.5) discard;
    vec2 art = floor(vec2(v_world.x, -v_world.y) * 0.5);
    vec4 c = texture2D(u_terrain, (art + 0.5) / u_size);
    gl_FragColor = vec4(c.rgb * m.r * v_color.rgb, 1.0);
}
