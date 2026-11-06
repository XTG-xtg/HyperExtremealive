#version 300 es
precision highp float;
in vec2 uv;
uniform sampler2D image;
uniform float exposure;
out vec4 color;
void main() {
    vec3 source = texture(image, uv).rgb;
    // Keep true black black; lift dark artwork without clipping colored highlights.
    // The old curve pinned white at 1 even at 25%: attenuation must scale highlights too.
    vec3 adjusted = exposure <= 1.0 ? source * exposure
        : 1.0 - pow(max(vec3(0.0), 1.0 - source), vec3(exposure));
    color = vec4(adjusted, 1.0);
}
