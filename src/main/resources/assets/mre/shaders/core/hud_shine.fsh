#version 150








uniform vec2 size;       
uniform vec2 location;   
uniform vec4 radius;     
uniform float Time;      

out vec4 fragColor;

const float SWEEP = 2.2;   
const float PAUSE = 1.1;   
uniform float beamWidth;
uniform float brightness;
const float EDGEW = 2.6;
const float EDGEK = 1.6;

float roundedBoxSDF(vec2 center, vec2 halfSize, vec4 radius) {
    radius.xy = (center.x > 0.0) ? radius.xy : radius.zw;
    radius.x  = (center.y > 0.0) ? radius.x  : radius.y;
    vec2 q = abs(center) - halfSize + radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius.x;
}

void main() {
    float full = SWEEP + PAUSE;
    float t = mod(Time, full);
    if (t > SWEEP) {
        fragColor = vec4(0.0);
        return;
    }
    float progress = t / SWEEP;

    
    
    
    
    
    float phase = mod(floor(Time / full), 4.0);

    
    vec2 uv = (gl_FragCoord.xy - location) / size;

    
    float fx = (phase == 1.0 || phase == 3.0) ? (1.0 - uv.x) : uv.x;
    float fy = (phase == 2.0 || phase == 3.0) ? (1.0 - uv.y) : uv.y;
    float proj = (fx + fy) * 0.5;

    float center = (1.0 + beamWidth) - progress * (1.0 + 2.0 * beamWidth);
    float tBeam = (proj - center) / beamWidth;

    float profile = 0.0;
    if (abs(tBeam) < 1.0) {
        float c = cos(tBeam * 1.5707963);   
        profile = c * c;
    }
    if (profile <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    
    float dist = roundedBoxSDF(gl_FragCoord.xy - location - size * 0.5, size * 0.5, radius);
    float inside = 1.0 - smoothstep(-1.0, 1.0, dist);

    float a = brightness * profile * inside;
    float edge = exp(-(dist * dist) / (EDGEW * EDGEW));
    a = a + brightness * EDGEK * profile * edge;
    a = min(a, 1.0);
    fragColor = vec4(1.0, 1.0, 1.0, a);
}
