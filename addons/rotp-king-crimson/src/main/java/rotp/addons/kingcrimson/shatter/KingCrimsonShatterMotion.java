package rotp.addons.kingcrimson.shatter;

import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** The entity shader subtracts this view-space pivot delta from its current position. */
public final class KingCrimsonShatterMotion {
    private KingCrimsonShatterMotion() {}

    public static Vector3f pivotDelta(
            boolean hasPreviousFrame,
            Matrix4fc view, Matrix4fc pose,
            Matrix4fc previousView, Matrix4fc previousPose,
            Vector3f result, Vector3f previous) {
        if (!hasPreviousFrame) {
            return result.zero();
        }
        previousPose.getTranslation(previous).mulPosition(previousView);
        return pose.getTranslation(result).mulPosition(view).sub(previous);
    }
}
