// Sprites indexados (D-103): a textura guarda, no alfa, o índice da cor na paleta (1 byte por
// pixel em vez de 4); a cor sai da textura da paleta (256×1). Na vista de longe (u_reduz), a
// média de 2×2 é feita já em cor — o filtro linear da textura misturaria índices.
// u_silhueta: modo máscara (como silhouette.frag), cor do vértice e só a cobertura da paleta.
#ifdef GL_ES
precision mediump float;
#endif
varying vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform sampler2D u_paleta;
uniform vec2 u_texel;
uniform float u_reduz;
uniform float u_silhueta;

vec4 cor(vec2 uv) {
    float i = texture2D(u_texture, uv).a;
    return texture2D(u_paleta, vec2((i * 255.0 + 0.5) / 256.0, 0.5));
}

void main() {
    vec4 c;
    if (u_reduz > 0.5) {
        vec2 d = u_texel * 0.5;
        c = (cor(v_texCoords + vec2(-d.x, -d.y)) + cor(v_texCoords + vec2(d.x, -d.y))
           + cor(v_texCoords + vec2(-d.x, d.y)) + cor(v_texCoords + d)) * 0.25;
    } else {
        c = cor(v_texCoords);
    }
    if (u_silhueta > 0.5) {
        float a = c.a * v_color.a;
        gl_FragColor = vec4(v_color.rgb * a, a);
    } else {
        gl_FragColor = v_color * c;
    }
}
