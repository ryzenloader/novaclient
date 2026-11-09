#version 150

in vec4 vertexColor;

uniform float Time;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy * 0.005;
    
    
    vec2 center = vec2(400.0, 300.0); 
    float dist = length(uv - center * 0.005);
    
    
    float rings = sin(dist * 20.0 - Time * 5.0) * 0.5 + 0.5;
    
    
    float mainPulse = sin(Time * 3.0) * 0.5 + 0.5;
    
    
    float energy = pow(rings, 2.0) * mainPulse;
    
    
    vec3 color1 = vec3(1.0, 0.2, 0.8); 
    vec3 color2 = vec3(0.2, 0.8, 1.0); 
    vec3 color3 = vec3(1.0, 1.0, 0.3); 
    
    vec3 finalColor = mix(color1, color2, dist * 0.1);
    finalColor = mix(finalColor, color3, energy);
    
    
    float centerGlow = 1.0 - smoothstep(0.0, 2.0, dist);
    finalColor += centerGlow * vec3(1.0, 1.0, 1.0) * mainPulse * 0.5;
    
    
    finalColor += rings * vec3(0.5, 0.5, 1.0) * 0.3;
    
    
    float alpha = 0.6 + mainPulse * 0.4;
    
    fragColor = vec4(finalColor, alpha) * vertexColor;
}
