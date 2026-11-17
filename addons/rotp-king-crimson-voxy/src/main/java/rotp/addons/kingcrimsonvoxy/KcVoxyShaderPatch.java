package rotp.addons.kingcrimsonvoxy;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text patches on Voxy's shaders, applied where Voxy has flattened a shader's imports. Every anchor must occur exactly
 * once in all the shaders before any of them is patched, so that a Voxy update turns the whole effect off and never half of it.
 * A patched shader behaves as the original while kcActive, kcCull and kcLit are 0. It must not read quad bits itself or touch
 * setupQuad, the point line or SceneUniform: voxy_fluid_side_rim_compat audits those texts.
 */
public final class KcVoxyShaderPatch {
    public static final int UBO_BINDING = 14;
    public static final int POSE_BINDING = 13;
    public static final int WORD_BINDING = 14;

    static final String VERT = "voxy:lod/gl46/quads3.vert";
    static final String FRAG = "voxy:lod/gl46/quads.frag";
    static final String CMDGEN = "voxy:lod/gl46/cmdgen.comp";
    static final String TRAVERSAL = "voxy:lod/hierarchical/traversal_dev.comp";

    /** kcOrigin.w: which parts of the look run (all in play; the debug command switches them to film before and after). */
    static final int LOOK_LIGHT = 1;
    static final int LOOK_SPLIT = 2;
    static final int LOOK_TEAR = 4;
    static final int LOOK_UNDERSIDE = 8;
    static final int LOOK_MOTION = 16;
    static final int LOOK_WATER = 32;
    /** CPU only: far plates move farther the farther they are, so that they move alike on screen. */
    static final int LOOK_REACH = 64;
    /** Loose pieces of the far ground float up (KcVoxyPieces). */
    static final int LOOK_PIECES = 128;
    static final int LOOK_ALL = 255;

    private static final String SCENE = """
            layout(binding = 14, std140) uniform KcFarScene {
                uint kcActive;
                uint kcCull;
                uint kcLit;
                uint kcPlates;
                ivec4 kcOrigin;    // xyz the erase's origin, w the look switches and (from bit 8) the last LOD level that splits
                ivec4 kcFootprint; // min x, min z relative to the origin, side, first word
                ivec4 kcMap;       // half extent in blocks, texels per side, blocks per texel as a shift, outer sectors
                vec4 kcPlanes[4];
                ivec4 kcPieces;    // loose pieces: first word of their squares, squares per side (0: none), first plate word, first pose
            };
            const int KC_LOOK_LIGHT = 1;
            const int KC_LOOK_SPLIT = 2;
            const int KC_LOOK_TEAR = 4;
            const int KC_LOOK_UNDERSIDE = 8;
            const int KC_LOOK_MOTION = 16;
            const int KC_LOOK_WATER = 32;
            const int KC_LOOK_PIECES = 128;

            bool kcSplits(uint level) {
                return (kcOrigin.w & KC_LOOK_SPLIT) != 0 && int(level) <= ((kcOrigin.w >> 8) & 15);
            }
            """;

    // Which plate holds a column: the plan's map near, analytic outer plates by angle and radius beyond it.
    private static final String PLATE_AT = """
            layout(binding = 14, std430) readonly restrict buffer KcWords { uint kcWords[]; };

            uint kcPlateAt(ivec2 w) {
                ivec2 t = (w + kcMap.x) >> kcMap.z;
                if (all(greaterThanEqual(t, ivec2(0))) && all(lessThan(t, ivec2(kcMap.y)))) {
                    int i = t.y * kcMap.y + t.x;
                    uint id = (kcWords[i >> 1] >> (uint(i & 1) * 16u)) & 0xFFFFu;
                    if (id != 0xFFFFu) return id;
                }
                float r = length(vec2(w));
                float a = atan(float(w.y), float(w.x)) / 6.2831853 + 0.5;
                int sector = min(int(a * float(kcMap.w)), kcMap.w - 1);
                int ring = r < 4096.0 ? 0 : r < 8192.0 ? 1 : 2;
                return kcPlates + uint(ring * kcMap.w + sector);
            }

            // The loose pieces of a 128-block square of the world (KcVoxyPieces): x the first piece's index and (high half) the
            // count, y and z four cells of 16 bits.
            uvec3 kcPieceSquare(ivec2 square) {
                if (kcPieces.y == 0 || (kcOrigin.w & KC_LOOK_PIECES) == 0) return uvec3(0u);
                ivec2 t = square - (kcOrigin.xz >> 7) + (kcPieces.y >> 1);
                if (any(lessThan(t, ivec2(0))) || any(greaterThanEqual(t, ivec2(kcPieces.y)))) return uvec3(0u);
                int i = kcPieces.x + (t.y * kcPieces.y + t.x) * 4;
                return uvec3(kcWords[i], kcWords[i + 1], kcWords[i + 2]);
            }

            // Cell j of a square: its corner relative to the erase's origin, and its side.
            ivec3 kcPieceCell(ivec2 square, uvec3 list, uint j) {
                uint c = ((j < 2u ? list.y : list.z) >> ((j & 1u) * 16u)) & 0xFFFFu;
                return ivec3((square << 7) + ivec2(int(c & 127u), int((c >> 7) & 127u)) - kcOrigin.xz, 2 << int(c >> 14));
            }

            // The pieces inside a section's footprint, four at most: a section of LOD level 2 or finer lies in one square.
            uint kcSectionPieces(ivec3 lodPos, uint level, out uint ids[4], out ivec3 cells[4]) {
                uint n = 0u;
                if (kcActive == 0u || level > 2u) return 0u;
                ivec2 corner = (lodPos.xz << level) << 5;
                ivec2 square = corner >> 7;
                uvec3 list = kcPieceSquare(square);
                ivec2 from = corner - kcOrigin.xz;
                int side = 32 << level;
                for (uint j = 0u; j < (list.x >> 16); j++) {
                    ivec3 cell = kcPieceCell(square, list, j);
                    if (all(greaterThanEqual(cell.xy, from)) && all(lessThan(cell.xy, from + side))) {
                        ids[n] = (list.x & 0xFFFFu) + j;
                        cells[n] = cell;
                        n++;
                    }
                }
                return n;
            }

            """;

    private static final String VERT_BLOCK = SCENE + PLATE_AT + """
            // Rows of the pose, then the tear (share, grain), then the rows of this frame's pose minus the last one.
            struct KcPose { vec4 a; vec4 b; vec4 c; vec4 d; vec4 pa; vec4 pb; vec4 pc; };
            layout(binding = 13, std430) readonly restrict buffer KcPoses { KcPose kcPose[]; };
            layout(location = 8) out vec2 kcRestV;
            layout(location = 9) out flat uint kcPlateV;
            layout(location = 10) out flat vec2 kcTearV;
            layout(location = 11) out vec3 kcDeltaV;
            layout(location = 12) out flat uint kcSplitV;
            layout(location = 13) out flat uint kcPieceV;
            layout(location = 14) out flat ivec3 kcPieceCellV;
            layout(location = 15) out flat uint kcCutV;
            const uint KC_NONE = 0xFFFFu;
            uint kcId = KC_NONE;

            // A quad whose corners lie in one plate lies in it whole: plates are convex. Otherwise instance k draws the k-th plate
            // met at the corners, the centre and, on a long quad, the middles of its sides; the fragment test cuts it to that plate.
            uint kcPick(ivec2 lo, ivec2 span, uint k, out bool split) {
                ivec2 hi = lo + max(span - 1, ivec2(0));
                uint ids[9];
                ids[0] = kcPlateAt(lo);
                ids[1] = kcPlateAt(ivec2(hi.x, lo.y));
                ids[2] = kcPlateAt(ivec2(lo.x, hi.y));
                ids[3] = kcPlateAt(hi);
                split = ids[0] != ids[1] || ids[0] != ids[2] || ids[0] != ids[3];
                if (!split) return k == 0u ? ids[0] : KC_NONE;
                int n = 4;
                ids[n++] = kcPlateAt(lo + span / 2);
                if (max(span.x, span.y) >= 32) {
                    ids[n++] = kcPlateAt(ivec2(lo.x + span.x / 2, lo.y));
                    ids[n++] = kcPlateAt(ivec2(lo.x + span.x / 2, hi.y));
                    ids[n++] = kcPlateAt(ivec2(lo.x, lo.y + span.y / 2));
                    ids[n++] = kcPlateAt(ivec2(hi.x, lo.y + span.y / 2));
                }
                uint found = 0u;
                for (int i = 0; i < n; i++) {
                    bool seen = false;
                    for (int j = 0; j < i; j++) {
                        seen = seen || ids[j] == ids[i];
                    }
                    if (!seen) {
                        if (found == k) return ids[i];
                        found++;
                    }
                }
                return KC_NONE;
            }

            void kcSetup(const in Quad q, uvec2 s) {
                kcRestV = vec2(0.0);
                kcPlateV = 0u;
                kcTearV = vec2(0.0);
                kcDeltaV = vec3(0.0);
                kcSplitV = 0u;
                kcPieceV = 0u;
                kcPieceCellV = ivec3(0);
                kcCutV = 0u;
                if (kcActive == 0u) return;
                // What has no sky above it is the inside of the ground: a plate is a shell and must not show its caves.
                if ((extractLightId(q) & 0x0Fu) == 0u) return;
                uint level = getLoDLevel(s);
                uint axis = extractFace(q) >> 1;
                ivec3 cell = ((getLoDPosition(s) << level) << 5) + (ivec3(extractPos(q)) << level);
                ivec2 size = extractSize(q) << level;
                ivec2 along = axis == 0u ? size : axis == 1u ? ivec2(size.x, 0) : ivec2(0, size.y);
                ivec2 lo = cell.xz - kcOrigin.xz;
            #ifdef TRANSLUCENT
                kcId = gl_InstanceID == 0 ? kcPlateAt(lo + along / 2) : KC_NONE;
            #else
                // The section's loose pieces draw first, one instance each, cut to their cells; the plates' instances leave
                // those cells out (kcCutV).
                uint kcPieceIds[4];
                ivec3 kcPieceCells[4];
                uint pieces = kcSectionPieces(getLoDPosition(s), level, kcPieceIds, kcPieceCells);
                uint k = uint(gl_InstanceID);
                if (k < pieces) {
                    ivec3 piece = kcPieceCells[k];
                    ivec2 hi = lo + max(along, ivec2(1));
                    if (any(greaterThanEqual(lo, piece.xy + piece.z)) || any(lessThanEqual(hi, piece.xy))) return;
                    kcId = uint(kcPieces.w) + kcPieceIds[k];
                    kcPlateV = kcWords[kcPieces.z + int(kcPieceIds[k])];
                    kcPieceV = 1u;
                    kcPieceCellV = piece;
                    kcTearV = kcPose[kcId].d.xy;
                    return;
                }
                k -= pieces;
                kcCutV = pieces;
                if (kcSplits(level)) {
                    bool split;
                    kcId = kcPick(lo, along, k, split);
                    kcSplitV = split ? 1u : 0u;
                } else {
                    kcId = k == 0u ? kcPlateAt(lo + along / 2) : KC_NONE;
                }
            #endif
                if (kcId == KC_NONE) return;
                kcPlateV = kcId;
                kcTearV = kcPose[kcId].d.xy;
            }

            vec3 kcMove(vec3 p) {
                if (kcActive == 0u) return p;
                if (kcId == KC_NONE) return vec3(1.0e7);
                vec3 offset = vec3((baseSectionPos << 5) - kcOrigin.xyz);
                vec4 rest = vec4(p + offset, 1.0);
                kcRestV = rest.xz;
                KcPose pose = kcPose[kcId];
                if ((kcOrigin.w & KC_LOOK_MOTION) != 0) {
                    kcDeltaV = vec3(dot(pose.pa, rest), dot(pose.pb, rest), dot(pose.pc, rest));
                }
                return vec3(dot(pose.a, rest), dot(pose.b, rest), dot(pose.c, rest)) - offset;
            }

            """;

    // Before Voxy's own corner setup: an instance with no plate of its own, or a quad under the ground, stops there.
    private static final String VERT_EARLY = """
            kcSetup(quadData[uint(gl_VertexID)>>2], pos);
            #ifndef USE_NV_JANK
                if (kcActive != 0u && kcId == KC_NONE) {
                    gl_Position = vec4(0.0);
                    return;
                }
            #endif
                """;

    private static final String FRAG_BLOCK = SCENE + PLATE_AT + """
            // A shader pack's Voxy program, appended after this text, may use these while ROTP_KC_FAR is defined.
            #define ROTP_KC_FAR 1
            layout(location = 8) in vec2 kcRestV;
            layout(location = 9) in flat uint kcPlateV;
            layout(location = 10) in flat vec2 kcTearV;
            layout(location = 11) in vec3 kcDeltaV;
            layout(location = 12) in flat uint kcSplitV;
            layout(location = 13) in flat uint kcPieceV;
            layout(location = 14) in flat ivec3 kcPieceCellV;
            layout(location = 15) in flat uint kcCutV;

            // True once the shader pack's own far look is joined to this patch (KcVoxyShaderPatch.join): the pack then lights the
            // plates and gets the block's own id, not the stand-in that lights them without it.
            const bool kcPackJoined = false;
            float kcEvenLevel = 0.0;
            float kcFaceRadiance = 0.0;

            /** What the pack's far look reads: x 0, or 1 plus the face's mean radiance R when lit as the near plates; y water shown
                as they show it; z a far plate; w the near plates' level for the face (KingCrimsonShatterPlateLight), which a pack
                that lights the albedo once multiplies by R, and an older pack takes as it is. */
            vec4 kcFarState() {
                if (kcActive == 0u) return vec4(0.0);
                return vec4((kcOrigin.w & KC_LOOK_LIGHT) != 0 ? 1.0 + kcFaceRadiance : 0.0, (kcOrigin.w & KC_LOOK_WATER) != 0 ? 1.0 : 0.0,
                        1.0, kcEvenLevel);
            }

            /** How far this point of a far plate moved since the last frame, in blocks of the world. */
            vec3 kcFarMotion() {
                return kcActive != 0u ? kcDeltaV : vec3(0.0);
            }

            bool kcOwnedByNear(ivec2 w) {
                ivec2 t = w - kcFootprint.xy;
                if (any(lessThan(t, ivec2(0))) || t.x >= kcFootprint.z || t.y >= kcFootprint.z) return false;
                int i = t.y * kcFootprint.z + t.x;
                return ((kcWords[kcFootprint.w + (i >> 5)] >> uint(i & 31)) & 1u) != 0u;
            }

            float kcTearHash(ivec2 w, uint plate) {
                uvec2 u = uvec2(w);
                uint h = (u.x * 0x8da6b343u) ^ (u.y * 0xd8163841u) ^ (plate * 0xcb1ab31fu);
                h ^= h >> 16u;
                h *= 0x7feb352du;
                h ^= h >> 15u;
                return float(h & 0xFFFFu) / 65535.0;
            }

            // Smooth value noise over the plate, with a little of each cell's own hash: holes that grow as blots, not as tiles.
            float kcTearNoise(vec2 p, uint plate) {
                vec2 i = floor(p);
                vec2 f = p - i;
                f = f * f * (3.0 - 2.0 * f);
                ivec2 c = ivec2(i);
                float n = mix(mix(kcTearHash(c, plate), kcTearHash(c + ivec2(1, 0), plate), f.x),
                        mix(kcTearHash(c + ivec2(0, 1), plate), kcTearHash(c + ivec2(1, 1), plate), f.x), f.y);
                return clamp((n - 0.5) * 1.7 + 0.5, 0.0, 1.0);
            }

            """;

    private static final String FRAG_MAIN = """

                if (kcActive != 0u) {
                    ivec2 kcColumn = ivec2(floor(kcRestV));
                    if (kcOwnedByNear(kcColumn)) discard;
                    if (kcSplitV != 0u && kcPlateAt(kcColumn) != kcPlateV) discard;
                    if (kcPieceV != 0u) {
                        if (any(lessThan(kcColumn, kcPieceCellV.xy)) || any(greaterThanEqual(kcColumn, kcPieceCellV.xy + kcPieceCellV.z))) discard;
                    } else if (kcCutV != 0u) {
                        ivec2 kcSquare = (kcColumn + kcOrigin.xz) >> 7;
                        uvec3 kcList = kcPieceSquare(kcSquare);
                        for (uint j = 0u; j < (kcList.x >> 16); j++) {
                            ivec3 kcCell = kcPieceCell(kcSquare, kcList, j);
                            if (all(greaterThanEqual(kcColumn, kcCell.xy)) && all(lessThan(kcColumn, kcCell.xy + kcCell.z))) discard;
                        }
                    }
                    if (kcTearV.x > 0.0) {
                        bool kcTorn = kcTearV.x >= 0.999 || ((kcOrigin.w & KC_LOOK_TEAR) != 0
                                ? kcTearNoise(kcRestV / float(2 << int(kcTearV.y)), kcPlateV) < kcTearV.x
                                : kcTearHash(kcColumn >> int(kcTearV.y), kcPlateV) < kcTearV.x);
                        if (kcTorn) discard;
                    }
                }""";

    // The back of a top face, seen when a far plate leans: what lies under grass, not the grass from below. Then the near plates'
    // light (KingCrimsonShatterPlateLight): one level for the whole block face, EVEN_LIGHT over the face's mean radiance, which
    // the atlas holds at the mip where the face is one texel.
    private static final String UNDERSIDE = """
                if (kcActive != 0u) {
            #ifndef TRANSLUCENT
                    if ((kcOrigin.w & KC_LOOK_UNDERSIDE) != 0 && face == 0u && getFace() == 1u) {
                        colour = vec4(0.525, 0.376, 0.263, colour.a);
                        tint = vec4(1.0);
                    }
            #endif
                    vec3 kcMean = textureLod(blockModelAtlas, texPos, 4.0).rgb * tint.rgb;
                    float kcRadiance = dot(pow(kcMean, vec3(2.2)), vec3(1.0 / 3.0));
                    kcEvenLevel = clamp(round(4.0 / max(kcRadiance, 1.0e-3)), 1.0, 96.0) * 0.01;
                    kcFaceRadiance = kcRadiance;
                }
                """;

    private static final String CMDGEN_BLOCK = SCENE + PLATE_AT + """
            uint kcInstances = 1u;

            // One draw instance per plate met at nine points of the section (at most four): a section in one plate draws once.
            uint kcPlateInstances(ivec3 lodPos, uint level) {
                if (kcActive == 0u || !kcSplits(level)) return 1u;
                ivec2 lo = ((lodPos.xz << level) << 5) - kcOrigin.xz;
                int mid = ((32 << level) - 1) >> 1;
                uint ids[4];
                uint n = 0u;
                for (int i = 0; i < 9; i++) {
                    uint id = kcPlateAt(lo + ivec2(i % 3, i / 3) * mid);
                    bool seen = false;
                    for (uint j = 0u; j < n; j++) {
                        seen = seen || ids[j] == id;
                    }
                    if (!seen) {
                        if (n == 4u) return 4u;
                        ids[n] = id;
                        n++;
                    }
                }
                return n;
            }

            // Then one more for each loose piece inside the section (quads3.vert draws those first).
            uint kcSectionInstances(ivec3 lodPos, uint level) {
                uint pieceIds[4];
                ivec3 pieceCells[4];
                return kcPlateInstances(lodPos, level) + kcSectionPieces(lodPos, level, pieceIds, pieceCells);
            }

            """;

    private static final String TRAVERSAL_BLOCK = SCENE + """
            bool kcOutside(vec3 pos, float size) {
                return !(testPlane(kcPlanes[0], pos, size) && testPlane(kcPlanes[1], pos, size)
                        && testPlane(kcPlanes[2], pos, size) && testPlane(kcPlanes[3], pos, size));
            }

            """;

    private static final String ALL_FACES = """
            if (kcCull != 0u) {
                        // Down faces stay as they are: below the camera they are the underside of the world, not of a plate.
                        msk = (msk & 1u) | (uint(((counts.y >> 16) & 0xFFFFu) != 0u) << 1)
                                | (uint((counts.z & 0xFFFFu) != 0u) << 2) | (uint(((counts.z >> 16) & 0xFFFFu) != 0u) << 3)
                                | (uint((counts.w & 0xFFFFu) != 0u) << 4) | (uint(((counts.w >> 16) & 0xFFFFu) != 0u) << 5)
                                | (uint(((counts.x >> 16) & 0xFFFFu) != 0u) << 6);
                    }
                    """;

    private record Edit(String anchor, UnaryOperator<String> replace) {
        private static Edit before(String anchor, String text) {
            return new Edit(anchor, found -> text + found);
        }

        private static Edit after(String anchor, String text) {
            return new Edit(anchor, found -> found + text);
        }

        private static Edit with(String anchor, String text) {
            return new Edit(anchor, found -> text);
        }
    }

    private static final Map<String, List<Edit>> EDITS = Map.of(
            VERT, List.of(
                    Edit.before("vec4 getQuadCornerPos(in QuadData quad, uint cornerId) {", VERT_BLOCK),
                    Edit.with("vec4 pos = MVP * vec4(point, 1.0f);", "vec4 pos = MVP * vec4(kcMove(point), 1.0f);"),
                    Edit.before("setupQuad(quad, quadData[uint(gl_VertexID)>>2], pos, (gl_VertexID&3) == 1);", VERT_EARLY)),
            FRAG, List.of(
                    Edit.before("void main() {", FRAG_BLOCK),
                    Edit.after("void main() {", FRAG_MAIN),
                    Edit.with("if (useChunkBounds) {", "if (useChunkBounds && kcActive == 0u) {"),
                    Edit.with("voxy_emitFragment(VoxyFragmentParameters(colour, tile, texPos, face, modelId, "
                                    + "getLightmapUv(interData.y), tint, model.customId));",
                            UNDERSIDE + "voxy_emitFragment(VoxyFragmentParameters(colour, tile, texPos, face, modelId, "
                                    + "kcLit != 0u ? vec2(0.0) : getLightmapUv(interData.y), tint, "
                                    + "kcLit != 0u && (!kcPackJoined || (kcOrigin.w & KC_LOOK_LIGHT) == 0) ? 8540u : model.customId));")),
            CMDGEN, List.of(
                    Edit.before("layout(binding = TRANSLUCENT_DISTANCE_BUFFER_BINDING, std430) restrict buffer "
                            + "TranslucentCommandCount {", CMDGEN_BLOCK),
                    Edit.with("cmd.instanceCount = 1;", "cmd.instanceCount = kcInstances;"),
                    Edit.after("bool renderTemporally = (dat&0x80000000u)==0;// means, if it was not visible last frame",
                            "\n    if (kcCull != 0u) { shouldRender = true; renderTemporally = false; }"),
                    Edit.after("uint drawId = listIdx;", "\n        kcInstances = kcSectionInstances(ipos, detail);"),
                    Edit.before("uint cmdCnt = bitCount(msk);", ALL_FACES)),
            TRAVERSAL, List.of(
                    Edit.before("layout(binding = SCENE_UNIFORM_BINDING, std140) uniform SceneUniform {", TRAVERSAL_BLOCK),
                    Edit.with("_frustumCulled = outsideFrustum(frustum, basePos, float(32<<node.lodLevel));",
                            "_frustumCulled = kcCull != 0u ? kcOutside(basePos, float(32<<node.lodLevel))"
                                    + " : outsideFrustum(frustum, basePos, float(32<<node.lodLevel));"),
                    Edit.after("bool isCulledByHiz() {", "\n    if (kcCull != 0u) return false;")));

    // What the blocks above use of Voxy's own text without patching it: each must still be declared, at least once.
    private static final Map<String, List<String>> NEEDS = Map.of(
            VERT, List.of("#define Quad ", "uint extractLightId(", "uint extractFace(", "vec3 extractPos(", "ivec2 extractSize(",
                    "uint getLoDLevel(uvec2 ", "ivec3 getLoDPosition(uvec2 ", "ivec3 baseSectionPos;",
                    "uvec2 pos = positionBuffer[gl_BaseInstance];", "void main() {"),
            FRAG, List.of("uint customId;", "vec2 getLightmapUv(uint ", "uint face = getFace();", "vec4 tint = vec4(1);",
                    "uniform sampler2D blockModelAtlas;", "vec2 texPos = "),
            CMDGEN, List.of("uvec4 counts = meta.b;", "uint msk = 0;", "bool shouldRender = ", "uint detail = extractDetail(meta);",
                    "ivec3 ipos = extractPosition(meta);"),
            TRAVERSAL, List.of("bool testPlane(vec4 plane, vec3 base, float size) {"));

    private static volatile Decision decision;
    private static final ThreadLocal<Boolean> VERIFYING = ThreadLocal.withInitial(() -> false);
    private static UnaryOperator<String> parser;
    private static volatile boolean patchedAny;

    private record Decision(boolean approved) {}

    private KcVoxyShaderPatch() {}

    static List<String> paths() {
        return List.copyOf(EDITS.keySet());
    }

    static List<String> needsOf(String path) {
        return NEEDS.get(path);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    /** The first anchor that does not fit, or null. */
    static String firstMismatch(String path, String source) {
        for (Edit edit : EDITS.get(path)) {
            int count = occurrences(source, edit.anchor());
            if (count != 1) {
                return path + ": anchor occurs " + count + " times: " + edit.anchor();
            }
        }
        for (String need : NEEDS.get(path)) {
            if (!source.contains(need)) {
                return path + ": needed text absent: " + need;
            }
        }
        return null;
    }

    /** Voxy's own text back from a source that holds one of the patches, or null if it holds none. */
    static String unpatch(String text) {
        for (List<Edit> edits : EDITS.values()) {
            String restored = unpatch(edits, text);
            if (restored != null) {
                return restored;
            }
        }
        return null;
    }

    private static String unpatch(List<Edit> edits, String text) {
        String out = text;
        for (int i = edits.size() - 1; i >= 0; i--) {
            Edit edit = edits.get(i);
            String placed = edit.replace().apply(edit.anchor());
            if (occurrences(out, placed) != 1) {
                return null;
            }
            int at = out.indexOf(placed);
            out = out.substring(0, at) + edit.anchor() + out.substring(at + placed.length());
        }
        return out;
    }

    /** A patched program did not compile: no shader is patched from now on, and the effect stays off. */
    static void reject(String why) {
        KcVoxyStatus.fail(why);
        decision = new Decision(false);
    }

    static String patch(String path, String source) {
        String text = source;
        for (Edit edit : EDITS.get(path)) {
            int at = text.indexOf(edit.anchor());
            text = text.substring(0, at) + edit.replace().apply(edit.anchor()) + text.substring(at + edit.anchor().length());
        }
        return text;
    }

    // A shader pack's far look declares its inputs with the values that mean no far plate. Iris runs the pack's preprocessor over
    // the pack's text before Voxy appends it to its own, so the pack cannot test for this patch: the add-on joins these lines
    // instead, in a program that holds the patch (after its marker), as Voxy is about to compile it.
    private static final String JOIN_MARK = "#define ROTP_KC_FAR 1";
    private static final String UNJOINED = "const bool kcPackJoined = false;";
    private static final String JOINED = "const bool kcPackJoined = true;";
    private static final List<Join> JOINS = List.of(
            new Join(Pattern.compile("vec4\\s+rotpKcFar\\s*=\\s*vec4\\s*\\(\\s*0\\.0\\s*\\)\\s*;"), "vec4 rotpKcFar = kcFarState();"),
            new Join(Pattern.compile("vec3\\s+rotpKcFarMotion\\s*=\\s*vec3\\s*\\(\\s*0\\.0\\s*\\)\\s*;"),
                    "vec3 rotpKcFarMotion = kcFarMotion();"));

    private record Join(Pattern line, String joined) {}

    /** The program's text with the shader pack's far look joined to the patch, or null if it holds no such look. */
    static String join(String source) {
        int mark = source.indexOf(JOIN_MARK);
        if (mark < 0 || occurrences(source, UNJOINED) != 1) {
            return null;
        }
        String tail = source.substring(mark);
        boolean state = false;
        for (Join join : JOINS) {
            Matcher found = join.line().matcher(tail);
            if (!found.find()) {
                continue;
            }
            int start = found.start();
            int end = found.end();
            if (found.find()) {
                return null;
            }
            tail = tail.substring(0, start) + join.joined() + tail.substring(end);
            state |= join == JOINS.get(0);
        }
        if (!state) {
            return null;
        }
        return (source.substring(0, mark) + tail).replace(UNJOINED, JOINED);
    }

    /** How to read a shader from Voxy: its own loader, which comes back here with the patch held off. */
    public static void setParser(UnaryOperator<String> reader) {
        parser = reader;
    }

    public static boolean hasParser() {
        return parser != null;
    }

    /** A patched program reads the scene block, so it must stay bound even if the rest of the effect is off. */
    public static boolean patchedAny() {
        return patchedAny;
    }

    public static boolean approved() {
        Decision current = decision;
        return current != null && current.approved();
    }

    /** Reads every patched shader once, on the first shader Voxy loads or the first tick in a world. */
    public static synchronized void verify() {
        if (decision != null || !KcVoxyStatus.applicable() || parser == null) {
            return;
        }
        if (KcVoxyStatus.failed()) {
            decision = new Decision(false);
            return;
        }
        VERIFYING.set(true);
        String mismatch = null;
        try {
            for (String path : EDITS.keySet()) {
                mismatch = firstMismatch(path, parser.apply(path));
                if (mismatch != null) {
                    break;
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            mismatch = "cannot read Voxy's shaders: " + failure;
        } finally {
            VERIFYING.set(false);
        }
        if (mismatch != null) {
            KcVoxyStatus.fail(mismatch);
        }
        decision = new Decision(mismatch == null);
    }

    /** At the RETURN of Voxy's own shader loader. */
    public static String onParse(String path, String source) {
        if (VERIFYING.get() || !EDITS.containsKey(path)) {
            return source;
        }
        verify();
        if (!approved()) {
            return source;
        }
        String late = firstMismatch(path, source);
        if (late != null) {
            KcVoxyStatus.fail(late);
            decision = new Decision(false);
            return source;
        }
        patchedAny = true;
        return patch(path, source);
    }
}
