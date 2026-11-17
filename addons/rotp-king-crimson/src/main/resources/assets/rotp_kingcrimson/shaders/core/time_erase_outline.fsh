#version 330 core

// What a player needs from these: can I walk there, and how far is that wall. So a nearer solid hides what is behind it, near lines lead,
// what stands in the way leads what lies underfoot, and a wall's top reads stronger than a step's. All of it by dimming: the strongest line
// is as faint as it ever was.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 ViewSize;
uniform int UseDepth;
uniform vec2 DepthExtent;
uniform vec2 ProjZ;
uniform float ShellDepth;
uniform vec3 Feet;
uniform float FeetLevel;
uniform vec3 Reach;
uniform float Fade;
uniform int Mode;
in vec3 world;
in float height;
in float floorLine;
in float kind;
in float widthPixels;
in float acrossPixels;
out vec4 fragColor;

// A line lies on the surface of the plate or wall it marks: it must not fight it.
const float BEHIND_BLOCKS = 0.3;
// The depth the void's shell moves to is a plain 32 blocks, not something that stands in the way of a line further off.
const float SHELL_TOLERANCE = 2.4e-7;
const float FADE_FROM = 0.7;
// At the far end of what leads, a line is this much of what it is at the feet, then it goes with no rim.
const float MID_LEVEL = 0.6;
const float LEAD_FROM = 0.06;
const float LEAD_TO = 0.7;
const float STEP_LEVEL = 0.8;
// The floor lines, graded by depth so that a player reads how far down a floor lies (KingCrimsonTerrainOutlineField: the same
// numbers there). Depth 1 is the floor stood on, the brightest; each floor further down dimmer and a deeper red; across, full near
// the feet and gone by FLOOR_LINE_GONE_ACROSS; never brighter than FLOOR_LINE_UNDER_EDGE of an edge's line at the same place.
const float FLOOR_LINE_LOWEST = 9.0;
const float FLOOR_LINE_STEP = 0.62;
const float FLOOR_LINE_DIMS = 0.84;
const float FLOOR_LINE_FULL_ACROSS = 6.0;
const float FLOOR_LINE_GONE_ACROSS = 24.0;
const float FLOOR_LINE_UNDER_EDGE = 0.8;
const float FLOOR_LINE_LEVEL = 0.8;
const vec3 DEEP_RGB = vec3(110.0 / 255.0, 18.0 / 255.0, 24.0 / 255.0);
const float FILL = 0.035;
const vec3 UPPER_RGB = vec3(165.0 / 255.0, 22.0 / 255.0, 30.0 / 255.0);
const float UPPER_ALPHA = 0.50;
const vec3 UNDER_RGB = vec3(190.0 / 255.0, 45.0 / 255.0, 45.0 / 255.0);
const float UNDER_ALPHA = 0.3;

float viewDistance(float depth) {
    return ProjZ.y / (depth * 2.0 - 1.0 + ProjZ.x);
}

void main() {
    if (Mode == 1) {
        fragColor = vec4(0.0);
        return;
    }
    if (UseDepth != 0) {
        ivec2 at = ivec2(gl_FragCoord.xy / ViewSize * DepthExtent);
        float scene = texelFetch(Sampler0, clamp(at, ivec2(0), ivec2(DepthExtent) - 1), 0).r;
        if (abs(scene - ShellDepth) > SHELL_TOLERANCE && viewDistance(gl_FragCoord.z) > viewDistance(scene) + BEHIND_BLOCKS) discard;
    }
    // The faces' own depth: they were pushed back a little there, so what lies on a face passes and what lies behind one does not.
    if (gl_FragCoord.z > texelFetch(Sampler1, ivec2(gl_FragCoord.xy), 0).r) discard;
    vec3 away = world - Feet;
    float reach = max(length(away.xz) / Reach.x, abs(away.y) / (away.y > 0.0 ? Reach.y : Reach.z));
    float rim = clamp((reach - FADE_FROM) / (1.0 - FADE_FROM), 0.0, 1.0);
    float level = mix(1.0, MID_LEVEL, smoothstep(LEAD_FROM, LEAD_TO, reach)) * (1.0 - rim * rim * (3.0 - 2.0 * rim));
    float rise = smoothstep(0.0, 2.0, world.y - FeetLevel);
    if (Mode == 2) {
        // A trace of a body on what stands in the way, its sides told apart.
        if (kind > 2.5 || (kind > 1.5 ? world.y <= FeetLevel + 0.01 : world.y < FeetLevel)) discard;
        float shade = kind > 1.5 ? 1.3 : kind > 0.5 ? 1.0 : 0.7;
        float alpha = FILL * shade * level * mix(STEP_LEVEL, 1.0, rise) * Fade;
        if (alpha <= 0.003) discard;
        fragColor = vec4(UPPER_RGB, alpha);
        return;
    }
    bool upper = height >= FeetLevel;
    float alpha;
    vec3 rgb = upper ? UPPER_RGB : UNDER_RGB;
    if (floorLine > 0.5) {
        float depth = FeetLevel - height;
        if (upper || depth > FLOOR_LINE_LOWEST) discard;
        float grade = depth < 1.5 ? 1.0 : FLOOR_LINE_STEP * pow(FLOOR_LINE_DIMS, depth - 2.0);
        float near = 1.0 - smoothstep(FLOOR_LINE_FULL_ACROSS, FLOOR_LINE_GONE_ACROSS, length(world.xz - Feet.xz));
        alpha = min(UNDER_ALPHA * FLOOR_LINE_LEVEL * grade * near, FLOOR_LINE_UNDER_EDGE * UNDER_ALPHA * level);
        rgb = mix(UNDER_RGB, DEEP_RGB, clamp((depth - 1.0) / (FLOOR_LINE_LOWEST - 1.0), 0.0, 1.0));
    } else if (upper) {
        alpha = UPPER_ALPHA * level * mix(STEP_LEVEL, 1.0, rise);
    } else {
        alpha = UNDER_ALPHA * level;
    }
    alpha *= Fade * clamp(widthPixels * 0.5 + 0.5 - abs(acrossPixels), 0.0, 1.0);
    if (alpha <= 0.003) discard;
    fragColor = vec4(rgb, alpha);
}
