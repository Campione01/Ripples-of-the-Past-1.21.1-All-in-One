package rotp.addons.kingcrimson.shatter;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class KingCrimsonShatterProjection {
    // Reserved for the shell; depth exactly 1 is the pack's sky.
    public static final float VOID_DEPTH = Math.nextDown(1.0F);
    // Leave room for float matrix evaluation and depth quantization below the reserved D24 value.
    private static final float PLATE_DEPTH_LIMIT = VOID_DEPTH - 4.0F * Math.ulp(VOID_DEPTH);

    private KingCrimsonShatterProjection() {}

    public static float farPlane(Matrix4fc projection) {
        // At the far plane, clip-space Z equals W for a view-space point at Z = -far.
        return (projection.m32() - projection.m33()) / (projection.m22() - projection.m23());
    }

    public static float depthAtDistance(Matrix4fc projection, float distance) {
        float clipZ = projection.m32() - distance * projection.m22();
        float clipW = projection.m33() - distance * projection.m23();
        return (clipZ / clipW + 1.0F) * 0.5F;
    }

    /**
     * Keeps native depth ordering against near plates and actors. Returns the original projection only when
     * the full positive view-space bound fits ahead of the shell, or {@code null} to omit the far batch.
     */
    public static Matrix4f forFarPlates(Matrix4f projection, float seam, float maximumDepth) {
        if (projection == null || !projection.isFinite() || !Float.isFinite(seam) || seam < 128.0F
                || !Float.isFinite(maximumDepth) || maximumDepth < seam
                || projection.m23() != -1.0F || projection.m33() != 0.0F
                || projection.m03() != 0.0F || projection.m13() != 0.0F
                || projection.m02() != 0.0F || projection.m12() != 0.0F
                || projection.m22() >= -1.0F || projection.m32() >= 0.0F) {
            return null;
        }
        double xyDeterminant = (double) projection.m00() * projection.m11() - (double) projection.m01() * projection.m10();
        double seamZ = ndcZ(projection, seam);
        if (xyDeterminant == 0.0D || !(seamZ > -1.0D && seamZ < 1.0D)) return null;

        double targetZ = 2.0D * PLATE_DEPTH_LIMIT - 1.0D;
        return ndcZ(projection, maximumDepth) <= targetZ && depthAtDistance(projection, maximumDepth) < VOID_DEPTH
                ? projection : null;
    }

    private static double ndcZ(Matrix4fc projection, float distance) {
        return -projection.m22() + (double) projection.m32() / distance;
    }
}
