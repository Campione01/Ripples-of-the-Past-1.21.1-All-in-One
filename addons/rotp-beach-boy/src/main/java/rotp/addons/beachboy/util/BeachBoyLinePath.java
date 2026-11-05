package rotp.addons.beachboy.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 1.16 BeachBoyLinePath. */
public final class BeachBoyLinePath {
    private BeachBoyLinePath() {}

    public static double polylineLength(List<Vec3> points) {
        double length = 0.0;
        for (int i = 1; i < points.size(); i++) {
            length += points.get(i - 1).distanceTo(points.get(i));
        }
        return length;
    }

    public static List<Vec3> sagCurve(Vec3 start, Vec3 end, double sag) {
        Vec3 delta = end.subtract(start);
        double span = delta.length();
        if (span < 1.0E-4) {
            List<Vec3> points = new ArrayList<>(2);
            points.add(start);
            points.add(end);
            return points;
        }
        int samples = Mth.clamp((int) Math.max(span * 2.0, 4.0), 4, 24);
        List<Vec3> points = new ArrayList<>(samples + 1);
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            points.add(new Vec3(start.x + delta.x * t, start.y + delta.y * t - 4.0 * sag * t * (1.0 - t),
                    start.z + delta.z * t));
        }
        return points;
    }

    /** A path line follows its wrap points; a free line sags a little between rod tip and hook. */
    public static List<Vec3> buildPath(List<Vec3> anchors, boolean pathMode) {
        if (anchors == null || anchors.size() < 2) {
            return anchors == null ? Collections.emptyList() : new ArrayList<>(anchors);
        }
        if (pathMode) {
            return new ArrayList<>(anchors);
        }
        Vec3 start = anchors.get(0);
        Vec3 end = anchors.get(anchors.size() - 1);
        double sag = Math.min(0.12, start.distanceTo(end) * 0.01);
        return sagCurve(start, end, sag);
    }
}
