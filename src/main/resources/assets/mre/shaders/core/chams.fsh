#version 150

uniform float Time;

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    if (vertexColor.a <= 0.0) {
        discard;
    }

    float y = gl_FragCoord.y;
    float x = gl_FragCoord.x;

    float scan = 0.80 + 0.20 * sin(y * 0.65 - Time * 9.0);
    float fine = 0.90 + 0.10 * sin((x + y) * 0.18 + Time * 5.0);
    float pulse = 0.90 + 0.10 * sin(Time * 2.4);

    float sweepPos = fract(Time * 0.16);
    float yNorm = fract(y * 0.0016);
    float dist = abs(yNorm - sweepPos);
    dist = min(dist, 1.0 - dist);
    float sweep = 1.0 - smoothstep(0.0, 0.05, dist);

    float energy = scan * fine * pulse;
    vec3 col = vertexColor.rgb * energy;
    col += (vertexColor.rgb * 0.5 + vec3(0.25)) * sweep * 0.8;

    float alpha = vertexColor.a * (0.85 + 0.15 * sweep);
    fragColor = vec4(clamp(col, 0.0, 1.0), clamp(alpha, 0.0, 1.0));
}
