#version 150

in vec2 texCoord;
in vec4 vertexColor;

uniform float Time;
uniform float Opacity;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord * 1.5;
    float t = Time * 1.5;

    float v = 0.0;
    v += sin(uv.x + t);
    v += sin((uv.y + t) * 0.5);
    v += sin((uv.x + uv.y + t) * 0.5);
    vec2 c = uv + vec2(sin(t * 0.33), cos(t * 0.5)) * 0.75;
    v += sin(sqrt(c.x * c.x + c.y * c.y + 1.0) + t);
    v *= 0.25;

    float intensity = 0.5 + 0.5 * sin(v * 3.14159265);
    float swirl = 0.5 + 0.5 * cos(v * 6.2831853 + t);

    vec3 tint = vertexColor.rgb;
    vec3 col = mix(tint * 0.45, mix(tint, vec3(1.0), 0.55), intensity);
    col += swirl * tint * 0.25;

    float fill = clamp(0.35 + intensity * 0.8, 0.0, 1.0);
    float alpha = clamp(Opacity * fill, 0.0, 1.0);

    fragColor = vec4(col, alpha);
}
