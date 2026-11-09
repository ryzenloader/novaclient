#version 150

in vec2 texCoord;
in vec4 vertexColor;

uniform float Time;
uniform float Opacity;

out vec4 fragColor;

vec4 hexGrid(vec2 p) {
    vec2 r = vec2(1.0, 1.7320508);
    vec2 h = r * 0.5;
    vec2 a = mod(p, r) - h;
    vec2 b = mod(p - h, r) - h;
    vec2 gv = dot(a, a) < dot(b, b) ? a : b;
    vec2 id = p - gv;
    return vec4(gv, id);
}

void main() {
    vec2 uv = texCoord * 1.25;
    vec4 hx = hexGrid(uv);
    vec2 gv = hx.xy;
    vec2 id = hx.zw;

    float d = length(gv);
    float cell = smoothstep(0.5, 0.42, d);
    float edge = smoothstep(0.5, 0.46, d) - smoothstep(0.46, 0.4, d);

    float pulse = 0.5 + 0.5 * sin(Time * 2.2 + (id.x * 1.7 + id.y * 1.3));

    vec3 tint = vertexColor.rgb;
    vec3 base = tint * (0.35 + 0.55 * pulse);
    vec3 col = base + edge * mix(tint, vec3(1.0), 0.6) * 1.4;

    float fill = clamp(cell * (0.45 + pulse * 0.55) + edge, 0.0, 1.0);
    float alpha = clamp(Opacity * fill, 0.0, 1.0);

    fragColor = vec4(col, alpha);
}
