// Silhueta: a cor vem só do vértice; da textura, só a cobertura (alfa).
// Usado na máscara de personagens (luz de presença e medição de visibilidade, V0).
#ifdef GL_ES
precision mediump float;
#endif
varying vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

void main() {
    float a = texture2D(u_texture, v_texCoords).a * v_color.a;
    gl_FragColor = vec4(v_color.rgb * a, a);
}
