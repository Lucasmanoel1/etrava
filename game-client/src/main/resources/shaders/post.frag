// Pós-processamento unificado (RF-028): saturação, contraste e vinheta.
#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform float u_saturation;
uniform float u_contrast;
uniform float u_vignette;

void main() {
    vec3 c = texture2D(u_texture, v_texCoords).rgb;
    float luma = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(luma), c, u_saturation);
    c = (c - 0.5) * u_contrast + 0.5;
    vec2 d = (v_texCoords - 0.5) * vec2(1.0, 0.82);
    float edge = smoothstep(0.30, 0.78, length(d));
    c *= 1.0 - u_vignette * edge;
    gl_FragColor = vec4(c, 1.0);
}
