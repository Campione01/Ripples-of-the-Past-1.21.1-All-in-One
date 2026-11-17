#version 330 core

// One instance for each edge or face. An edge is a line in screen space between its two ends, as vanilla's rendertype_lines, with room left
// for a soft edge in the fragment; everything comes from one buffer that does not move with the camera, so the part behind the camera is
// cut off here. A face is a rectangle along the axes between two opposite corners.
in vec3 Position;
// The other end or corner, and for an edge the height of the highest block it touches (+ 0.5 on a floor line), for a face its kind.
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ViewSize;
uniform vec3 Origin;
uniform vec3 Feet;
uniform float FeetLevel;
uniform float ViewScale;
uniform vec3 Reach;
uniform int Mode;

out vec3 world;
out float height;
out float floorLine;
out float kind;
out float widthPixels;
out float acrossPixels;

const float NEAR = 0.12;
const float UPPER_WIDTH = 1.7;
const float UNDER_WIDTH = 1.0;
const float EDGE_ROOM = 2.0;
// Near lines lead: this much of its width is left to a line far off.
const float FAR_WIDTH = 0.75;
// Thinner than a pixel a line only gets dimmer, and its distance is not what it says then.
const float THINNEST = 0.9;

// How far out of the reach: a far wall keeps its top, a block far overhead goes.
float reachOf(vec3 at) {
    vec3 away = at - Feet;
    return max(length(away.xz) / Reach.x, abs(away.y) / (away.y > 0.0 ? Reach.y : Reach.z));
}

void main() {
    vec3 a = Position;
    vec3 b = Color.xyz;
    kind = Color.w;
    floorLine = 0.0;
    widthPixels = 0.0;
    acrossPixels = 0.0;
    if (Mode != 0) {
        vec2 corner = vec2(gl_VertexID & 1, gl_VertexID >> 1);
        world = a.x == b.x ? vec3(a.x, mix(a.y, b.y, corner.x), mix(a.z, b.z, corner.y))
                : a.y == b.y ? vec3(mix(a.x, b.x, corner.x), a.y, mix(a.z, b.z, corner.y))
                : vec3(mix(a.x, b.x, corner.x), mix(a.y, b.y, corner.y), a.z);
        height = world.y;
        gl_Position = ProjMat * ModelViewMat * vec4(world + Origin, 1.0);
        return;
    }
    vec4 viewA = ModelViewMat * vec4(a + Origin, 1.0);
    vec4 viewB = ModelViewMat * vec4(b + Origin, 1.0);
    float frontA = -viewA.z - NEAR;
    float frontB = -viewB.z - NEAR;
    float tA = frontA < 0.0 ? frontA / (frontA - frontB) : 0.0;
    float tB = frontB < 0.0 ? 1.0 - frontB / (frontB - frontA) : 1.0;
    vec4 clipA = ProjMat * mix(viewA, viewB, tA);
    vec4 clipB = ProjMat * mix(viewA, viewB, tB);
    vec2 screen = (clipB.xy / clipB.w - clipA.xy / clipA.w) * ViewSize;
    vec2 direction = dot(screen, screen) > 1.0e-6 ? normalize(screen) : vec2(1.0, 0.0);
    bool atB = gl_VertexID >= 2;
    float side = (gl_VertexID & 1) == 0 ? 1.0 : -1.0;
    world = mix(a, b, atB ? tB : tA);
    height = a.x == b.x && a.z == b.z ? world.y : floor(Color.w);
    floorLine = fract(Color.w) > 0.25 ? 1.0 : 0.0;
    float recede = smoothstep(0.06, 0.7, reachOf(world));
    widthPixels = max(THINNEST, (height >= FeetLevel ? UPPER_WIDTH : UNDER_WIDTH) * ViewScale * mix(1.0, FAR_WIDTH, recede));
    float wide = (UPPER_WIDTH * ViewScale) + EDGE_ROOM;
    vec2 offset = vec2(-direction.y, direction.x) * wide / ViewSize;
    vec4 here = atB ? clipB : clipA;
    gl_Position = vec4((here.xy / here.w + offset * side) * here.w, here.z, here.w);
    acrossPixels = side * wide * 0.5;
    if (frontA < 0.0 && frontB < 0.0) gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
}
