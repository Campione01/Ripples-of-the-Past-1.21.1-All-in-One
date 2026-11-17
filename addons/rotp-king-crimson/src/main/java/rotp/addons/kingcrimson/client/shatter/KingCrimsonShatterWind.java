package rotp.addons.kingcrimson.client.shatter;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The first blow: crests that stand up out of the ground along the cuts, as if a blade split it. A crest is a double-sided
 * strip on its crack line, its base following the ground, tilted a little to one side so that it never goes edge-on. It
 * shows one tile of a strip whose rows are variants and whose columns are the frames of its collapse; the height sinks
 * smoothly on top of those frames, so that their steps do not read as a slideshow. A crest is fired out from the user's
 * feet: anchored there at first, then its tail lets go and it keeps running over the ground toward the horizon.
 */
@OnlyIn(Dist.CLIENT)
final class KingCrimsonShatterWind {
    private static final ResourceLocation STRIP = KingCrimsonAddon.id("textures/environment/time_erase_crests.png");
    private static final int STRIP_ROWS = 4;
    private static final int STRIP_FRAMES = 6;
    // Half a texel of a 1024 x 128 tile: a nearest lookup must not land in the next tile.
    private static final float EDGE_U = 0.5F / (1024.0F * STRIP_FRAMES);
    private static final float EDGE_V = 0.5F / (128.0F * STRIP_ROWS);

    // The look, all in one place, with the reach of each crest in the plan. Times in ticks, sizes in blocks.
    private static final float HEIGHT_MIN = 1.6F;
    private static final float HEIGHT_MAX = 3.2F;
    private static final float LENGTH_MIN = 22.0F;
    private static final float LENGTH_MAX = 46.0F;
    private static final float TILT_MIN = 8.0F;
    private static final float TILT_MAX = 22.0F;
    // A crest on a line that runs from under the camera, as they all do in first person, would be seen edge-on: it leans over.
    private static final float LEAN_WITHIN_BLOCKS = 6.0F;
    private static final float LEAN_MOST = (float) Math.sin(Math.toRadians(32.0D));
    // And on such a line it stays low close to the camera: a tip that passes the eye line draws the strip as a wide band over
    // the whole ground. Only lines this close to the camera, so in third person only those that run about at it or away.
    private static final float LOW_FULL_ASIDE_BLOCKS = 1.0F;
    private static final float LOW_ASIDE_BLOCKS = 2.5F;
    private static final float LOW_SHARE = 0.85F;
    private static final float LOW_MOST_BLOCKS = 1.7F;
    private static final float LOW_FULL_ALONG_BLOCKS = 6.0F;
    private static final float LOW_ALONG_BLOCKS = 16.0F;

    private static final double CROSS_ALONG_MIN = 6.0D;
    private static final double CROSS_ALONG_MAX = 20.0D;
    private static final double CROSS_CHANCE = 0.35D;
    private static final float CROSS_DELAY_MOST = 0.6F;
    private static final float CROSS_DELAY_PER_BLOCK = 1.0F / 9.0F;
    private static final float CROSS_HEIGHT = 0.6F;
    private static final float CROSS_LENGTH = 0.35F;
    private static final float CROSS_LIFE = 0.5F;

    private static final float HEIGHT_GROWTH_TICKS = 2.0F;
    private static final float HEIGHT_SINK = 0.35F;
    // Above 1 the whole frames last and the broken ones go quickly: most of a crest's life it is seen whole.
    private static final float FRAME_EASE = 2.4F;
    // The last of its life it goes down into the ground, so that no strand simply vanishes.
    private static final float GOES_DOWN_FROM = 0.85F;
    private static final float SHORTEST_BLOCKS = 0.5F;
    private static final float SEGMENT_BLOCKS = 1.5F;
    private static final float STEEPEST_STEP_BLOCKS = 3.0F;
    private static final float LIFT = 0.02F;
    // Taller far away, gently, or the far crests vanish.
    private static final float FAR_FROM_BLOCKS = 24.0F;
    private static final float FAR_PER_BLOCK = 0.02F;
    private static final float FAR_MOST = 1.5F;
    // Only enough that a tuft right at the line does not chop the base; none near the feet, so as not to draw over the user.
    private static final float BIAS_SHARE = 0.1F;
    private static final float BIAS_MAX_BLOCKS = 1.0F;
    private static final float BIAS_FROM_BLOCKS = 3.0F;
    private static final float BIAS_FULL_BLOCKS = 9.0F;

    /** Nearest filtering and no mipmaps; translucent and full bright, depth tested, writing no depth, not culled. */
    static final RenderType RENDER_TYPE = RenderType.create(
            "rotp_kingcrimson_wind",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(STRIP, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static final int STRIDE = 12;
    private static final int WHITE = 0xFFFFFFFF;
    // A little past the farthest head, and only as far as the terrain was read: beyond that a segment has no ground.
    private static final int GROUND_PAST_FARTHEST = 4;
    private static final int GROUND_PAST_RANGE = 8;

    /** x z of its origin, direction x z, the tick it starts, reach, length, height, life, row of the strip, tilt sin cos. */
    private final float[] crests;
    private final float[] ground;
    private final int groundHalf;
    private final int groundSide;

    @FunctionalInterface
    interface GroundSource {
        float groundAt(double dx, double dz);
    }

    private KingCrimsonShatterWind(float[] crests, float[] ground, int groundHalf) {
        this.crests = crests;
        this.ground = ground;
        this.groundHalf = groundHalf;
        this.groundSide = groundHalf * 2 + 1;
    }

    static void prepare(Minecraft minecraft) {
        minecraft.getTextureManager().getTexture(STRIP);
    }

    /** Off the render thread. Ground heights are kept as the source gives them, relative to its zero. */
    static KingCrimsonShatterWind build(KingCrimsonShatterPlan plan, GroundSource source, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        List<float[]> built = new ArrayList<>();
        List<KingCrimsonShatterPlan.Cut> cuts = plan.cuts();
        for (KingCrimsonShatterPlan.Cut primary : cuts) {
            if (primary.kind == KingCrimsonShatterPlan.KIND_PRIMARY) {
                addPrimary(built, primary, cuts, random);
            }
        }
        float[] crests = new float[built.size() * STRIDE];
        for (int i = 0; i < built.size(); i++) {
            System.arraycopy(built.get(i), 0, crests, i * STRIDE, STRIDE);
        }
        int half = Math.min((int) KingCrimsonShatterPlan.CREST_FARTHEST + GROUND_PAST_FARTHEST, plan.range() + GROUND_PAST_RANGE);
        int side = half * 2 + 1;
        float[] ground = new float[side * side];
        for (int z = -half; z <= half; z++) {
            for (int x = -half; x <= half; x++) {
                ground[x + half + (z + half) * side] = source.groundAt(x, z);
            }
        }
        return new KingCrimsonShatterWind(crests, ground, half);
    }

    private static void addPrimary(
            List<float[]> built,
            KingCrimsonShatterPlan.Cut primary,
            List<KingCrimsonShatterPlan.Cut> cuts,
            SplittableRandom random) {
        float start = primary.startTick + KingCrimsonShatterPlan.WIND_LAG_TICKS;
        built.add(crest(
                primary, start, primary.crestReach,
                between(random, LENGTH_MIN, LENGTH_MAX),
                between(random, HEIGHT_MIN, HEIGHT_MAX),
                between(random, KingCrimsonShatterPlan.CREST_LIFE_MIN, KingCrimsonShatterPlan.CREST_LIFE_MAX),
                random));

        // On the partition's own lines: at most the nearest cut on each side, born as this crest's head passes it.
        boolean[] sideDone = new boolean[2];
        for (KingCrimsonShatterPlan.Cut cut : cuts) {
            if (cut.kind != KingCrimsonShatterPlan.KIND_BRANCH) {
                continue;
            }
            double along = cut.startX * primary.dirX + cut.startZ * primary.dirZ;
            double off = cut.startX * primary.dirZ - cut.startZ * primary.dirX;
            if (along < CROSS_ALONG_MIN || along > CROSS_ALONG_MAX || Math.abs(off) > 1.0E-6D) {
                continue;
            }
            int side = primary.dirX * cut.dirZ - primary.dirZ * cut.dirX > 0.0D ? 0 : 1;
            if (sideDone[side]) {
                continue;
            }
            sideDone[side] = true;
            if (random.nextDouble() >= CROSS_CHANCE) {
                continue;
            }
            float most = CROSS_DELAY_MOST + (float) along * CROSS_DELAY_PER_BLOCK;
            float share = (float) along / primary.crestReach;
            float passes = share >= 1.0F
                    ? most
                    : Math.min(most, -KingCrimsonShatterPlan.HEAD_TAU_TICKS * (float) Math.log(1.0F - share));
            built.add(crest(
                    cut, start + passes, Math.min(cut.crestReach, (float) cut.length),
                    between(random, LENGTH_MIN, LENGTH_MAX) * CROSS_LENGTH,
                    between(random, HEIGHT_MIN, HEIGHT_MAX) * CROSS_HEIGHT,
                    between(random, KingCrimsonShatterPlan.CREST_LIFE_MIN, KingCrimsonShatterPlan.CREST_LIFE_MAX) * CROSS_LIFE,
                    random));
        }
    }

    private static float[] crest(
            KingCrimsonShatterPlan.Cut cut, float start, float reach, float length, float height, float life, SplittableRandom random) {
        double tilt = Math.toRadians(between(random, TILT_MIN, TILT_MAX)) * (random.nextBoolean() ? 1.0D : -1.0D);
        return new float[] {
            (float) cut.startX, (float) cut.startZ, (float) cut.dirX, (float) cut.dirZ,
            start, reach, length, height, life, random.nextInt(STRIP_ROWS),
            (float) Math.sin(tilt), (float) Math.cos(tilt)
        };
    }

    private static float between(SplittableRandom random, float low, float high) {
        return low + (float) random.nextDouble() * (high - low);
    }

    private float groundAt(float dx, float dz) {
        int x = (int) Math.floor(dx + 0.5F) + groundHalf;
        int z = (int) Math.floor(dz + 0.5F) + groundHalf;
        if (x < 0 || z < 0 || x >= groundSide || z >= groundSide) {
            return Float.NaN;
        }
        return ground[x + z * groundSide];
    }

    /**
     * Draws what is left of the crests at a tick; returns the vertices written. The offsets take the erase's origin column,
     * at its corner, to the camera; heights come as the ground source gave them, and {@code oy} takes those to the camera.
     */
    int draw(VertexConsumer out, float tick, float ox, float oy, float oz, int light) {
        int written = 0;
        // The plan measures from the centre of the origin's column.
        float cx = ox + 0.5F;
        float cz = oz + 0.5F;
        for (int at = 0; at < crests.length; at += STRIDE) {
            float age = tick - crests[at + 4];
            float life = crests[at + 8];
            if (age <= 0.0F || age >= life) {
                continue;
            }
            float head = KingCrimsonShatterPlan.crestHead(crests[at + 5], age);
            float tail = KingCrimsonShatterPlan.crestTail(head, crests[at + 6]);
            float length = head - tail;
            if (length < SHORTEST_BLOCKS) {
                continue;
            }
            float share = age / life;
            float height = crests[at + 7]
                    * KingCrimsonShatterPlan.smooth(age / HEIGHT_GROWTH_TICKS)
                    * (1.0F - HEIGHT_SINK * KingCrimsonShatterPlan.smooth(share))
                    * (1.0F - KingCrimsonShatterPlan.smooth((share - GOES_DOWN_FROM) / (1.0F - GOES_DOWN_FROM)));
            if (height <= 0.0F) {
                continue;
            }
            float originX = crests[at];
            float originZ = crests[at + 1];
            float dirX = crests[at + 2];
            float dirZ = crests[at + 3];
            float tiltSin = crests[at + 10];
            float tiltCos = crests[at + 11];
            float aside = Math.abs((originX + cx) * dirZ - (originZ + cz) * dirX);
            float lean = LEAN_MOST * KingCrimsonShatterPlan.smooth(1.0F - aside / LEAN_WITHIN_BLOCKS);
            if (lean > Math.abs(tiltSin)) {
                tiltSin = Math.copySign(lean, tiltSin);
                tiltCos = (float) Math.sqrt(1.0F - lean * lean);
            }
            float upX = -dirZ * tiltSin * height;
            float upY = tiltCos * height;
            float upZ = dirX * tiltSin * height;
            // Where the point of the line under the camera lies on it, and how much of the crest's height stays there.
            float cameraAlong = -(originX + cx) * dirX - (originZ + cz) * dirZ;
            float low = KingCrimsonShatterPlan.smooth((LOW_ASIDE_BLOCKS - aside) / (LOW_ASIDE_BLOCKS - LOW_FULL_ASIDE_BLOCKS))
                    * (1.0F - Math.min(LOW_SHARE, LOW_MOST_BLOCKS / height));
            int frame = Math.min(STRIP_FRAMES - 1, (int) ((float) Math.pow(share, FRAME_EASE) * STRIP_FRAMES));
            int row = (int) crests[at + 9];
            // The bottom of a tile is the ground.
            float vTop = (float) row / STRIP_ROWS + EDGE_V;
            float vGround = (float) (row + 1) / STRIP_ROWS - EDGE_V;
            int steps = Math.max(1, (int) Math.ceil(length / SEGMENT_BLOCKS));
            float s0 = tail;
            float y0 = groundAt(originX + dirX * s0, originZ + dirZ * s0);
            for (int step = 1; step <= steps; step++) {
                float s1 = tail + length * step / steps;
                float y1 = groundAt(originX + dirX * s1, originZ + dirZ * s1);
                // Up a cliff the strip would stand as a white stick: the crest breaks there and goes on above it.
                if (!Float.isNaN(y0) && !Float.isNaN(y1) && Math.abs(y1 - y0) <= STEEPEST_STEP_BLOCKS) {
                    float x0 = originX + dirX * s0 + cx;
                    float z0 = originZ + dirZ * s0 + cz;
                    float x1 = originX + dirX * s1 + cx;
                    float z1 = originZ + dirZ * s1 + cz;
                    float wy0 = y0 + LIFT + oy;
                    float wy1 = y1 + LIFT + oy;
                    float u0 = stripU(frame, (s0 - tail) / length);
                    float u1 = stripU(frame, (s1 - tail) / length);
                    float far0 = farScale(x0, wy0, z0) * lowScale(low, s0 - cameraAlong);
                    float far1 = farScale(x1, wy1, z1) * lowScale(low, s1 - cameraAlong);
                    put(out, x0, wy0, z0, u0, vGround, cx, cz, light);
                    put(out, x0 + upX * far0, wy0 + upY * far0, z0 + upZ * far0, u0, vTop, cx, cz, light);
                    put(out, x1 + upX * far1, wy1 + upY * far1, z1 + upZ * far1, u1, vTop, cx, cz, light);
                    put(out, x1, wy1, z1, u1, vGround, cx, cz, light);
                    written += 4;
                }
                s0 = s1;
                y0 = y1;
            }
        }
        return written;
    }

    private static float stripU(int frame, float along) {
        float inside = Math.max(EDGE_U * STRIP_FRAMES, Math.min(1.0F - EDGE_U * STRIP_FRAMES, along));
        return (frame + inside) / STRIP_FRAMES;
    }

    // Camera-relative coordinates.
    private static float farScale(float x, float y, float z) {
        float distance = (float) Math.sqrt(x * x + y * y + z * z);
        return 1.0F + Math.min(FAR_MOST, Math.max(0.0F, distance - FAR_FROM_BLOCKS) * FAR_PER_BLOCK);
    }

    private static float lowScale(float low, float fromCamera) {
        return low <= 0.0F
                ? 1.0F
                : 1.0F - low * (1.0F - KingCrimsonShatterPlan.smooth(
                        (Math.abs(fromCamera) - LOW_FULL_ALONG_BLOCKS) / (LOW_ALONG_BLOCKS - LOW_FULL_ALONG_BLOCKS)));
    }

    // Moving along the view ray leaves the screen position alone.
    private static void put(VertexConsumer out, float x, float y, float z, float u, float v, float cx, float cz, int light) {
        float dx = x - cx;
        float dz = z - cz;
        float fromOrigin = (float) Math.sqrt(dx * dx + dz * dz);
        float gate = KingCrimsonShatterPlan.smooth((fromOrigin - BIAS_FROM_BLOCKS) / (BIAS_FULL_BLOCKS - BIAS_FROM_BLOCKS));
        float keep = 1.0F;
        if (gate > 0.0F) {
            float distance = (float) Math.sqrt(x * x + y * y + z * z);
            keep = 1.0F - Math.min(BIAS_SHARE * distance, BIAS_MAX_BLOCKS) * gate / Math.max(distance, 1.0E-3F);
        }
        out.addVertex(x * keep, y * keep, z * keep, WHITE, u, v, OverlayTexture.NO_OVERLAY, light, 0.0F, 1.0F, 0.0F);
    }
}
