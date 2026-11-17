#version 330 core

// Mode 0: one direction of the blur of an afterimage's silhouette. Mode 1: the blurred silhouette as crimson light, faint over the body.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 Step;
uniform int Mode;
in vec2 texCoord;
out vec4 fragColor;

const vec3 CRIMSON = vec3(0.86, 0.04, 0.10);
const float RIM_STRENGTH = 1.5;
const float BODY_KEEPS = 0.28;

void main() {
    if (Mode == 0) {
        float sum = texture(Sampler0, texCoord).a * 0.1964;
        sum += (texture(Sampler0, texCoord + Step * 1.41).a + texture(Sampler0, texCoord - Step * 1.41).a) * 0.2969;
        sum += (texture(Sampler0, texCoord + Step * 3.29).a + texture(Sampler0, texCoord - Step * 3.29).a) * 0.0945;
        sum += (texture(Sampler0, texCoord + Step * 5.18).a + texture(Sampler0, texCoord - Step * 5.18).a) * 0.0104;
        fragColor = vec4(vec3(1.0), sum);
        return;
    }
    float glow = clamp(texture(Sampler0, texCoord).a * RIM_STRENGTH, 0.0, 1.0);
    float body = texture(Sampler1, texCoord).a;
    float light = glow * mix(1.0, BODY_KEEPS, body);
    if (light <= 0.002) discard;
    fragColor = vec4(CRIMSON * light, 0.0);
}
