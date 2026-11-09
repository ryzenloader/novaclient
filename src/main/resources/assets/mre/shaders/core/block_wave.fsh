#version 150

in vec4 vertexColor;

uniform float Time;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy * 0.01;
    
    
    float wave1 = sin(uv.x * 10.0 + Time * 3.0);
    float wave2 = cos(uv.y * 10.0 + Time * 2.5);
    float wave3 = sin((uv.x + uv.y) * 8.0 + Time * 4.0);
    
    
    float waves = (wave1 + wave2 + wave3) / 3.0;
    
    
    vec3 color1 = vec3(0.0, 0.5, 1.0); 
    vec3 color2 = vec3(0.0, 1.0, 0.8); 
    vec3 color3 = vec3(0.5, 0.0, 1.0); 
    
    float t = waves * 0.5 + 0.5;
    vec3 finalColor = mix(color1, color2, t);
    finalColor = mix(finalColor, color3, sin(Time * 1.5) * 0.3 + 0.3);
    
    
    float glow = pow(abs(waves), 3.0);
    finalColor += glow * vec3(1.0, 1.0, 1.0) * 0.5;
    
    
    float alpha = 0.7 + sin(Time * 2.0) * 0.3;
    
    fragColor = vec4(finalColor, alpha) * vertexColor;
}
