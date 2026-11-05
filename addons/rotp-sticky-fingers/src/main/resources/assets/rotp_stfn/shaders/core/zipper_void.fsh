#version 150

#moj_import <matrix.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform float GameTime;
uniform int EndPortalLayers;

in vec4 texProj0;

// 1.16 StickyFingersZipperBlockRenderer.renderCube: Random(31101), red
// 0.75, green 0.35, blue 0.9 (+ nextFloat * 0.05), times 0.15 for the end
// sky layer and 2 / (18 - j) for void layer j.
const vec3[] COLORS = vec3[](
    vec3(0.113208, 0.058358, 0.139853),
    vec3(0.092960, 0.043535, 0.107344),
    vec3(0.093903, 0.046055, 0.118596),
    vec3(0.102851, 0.050141, 0.124150),
    vec3(0.109194, 0.052816, 0.130826),
    vec3(0.116924, 0.056905, 0.142271),
    vec3(0.131780, 0.065459, 0.150990),
    vec3(0.140667, 0.064672, 0.170846),
    vec3(0.150090, 0.071753, 0.184352),
    vec3(0.172695, 0.086232, 0.202390),
    vec3(0.195927, 0.091583, 0.234301),
    vec3(0.215421, 0.106213, 0.258947),
    vec3(0.265800, 0.127774, 0.310860),
    vec3(0.318779, 0.149618, 0.367437),
    vec3(0.398417, 0.197430, 0.461676)
);

const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

mat4 end_portal_layer(float layer) {
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 17.0 / layer,
        0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (GameTime * 1.5),
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );

    mat2 rotate = mat2_rotate_z(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0));

    mat2 scale = mat2((4.5 - layer / 4.0) * 2.0);

    return mat4(scale * rotate) * translate * SCALE_TRANSLATE;
}

out vec4 fragColor;

// Layer 0 is the end sky through portal layer 1, void layer j goes through
// portal layer j + 1, as the 1.16 RENDER_TYPES list.
void main() {
    vec3 color = textureProj(Sampler0, texProj0 * end_portal_layer(1.0)).rgb * COLORS[0];
    for (int i = 1; i < EndPortalLayers; i++) {
        color += textureProj(Sampler1, texProj0 * end_portal_layer(float(i + 1))).rgb * COLORS[i];
    }
    fragColor = vec4(color, 1.0);
}
