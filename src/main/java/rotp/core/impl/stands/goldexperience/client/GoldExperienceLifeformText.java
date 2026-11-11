package rotp.core.impl.stands.goldexperience.client;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The words and numbers of the lifeform menu's rows. */
public final class GoldExperienceLifeformText {
    static final String TEXT = "jojo_ripples.lifeforms.";

    private GoldExperienceLifeformText() {}

    /** Game ticks as m:ss, or h:mm:ss from an hour on. */
    public static String age(long ticks) {
        long seconds = Math.max(ticks, 0) / 20;
        long hours = seconds / 3600;
        long minutes = seconds / 60 % 60;
        long secs = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    public static String coordinates(Vec3 pos) {
        return Mth.floor(pos.x) + ", " + Mth.floor(pos.y) + ", " + Mth.floor(pos.z);
    }

    /** The three vanilla dimensions by their own words, any other by its id made readable. */
    public static Component dimensionName(@Nullable ResourceLocation dimension) {
        if (dimension == null) {
            return Component.translatable(TEXT + "unknown_place");
        }
        String path = dimension.getPath();
        String readable = path.isEmpty() ? dimension.toString()
                : Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
        return Component.translatableWithFallback(TEXT + "dimension." + dimension.getNamespace() + "." + path, readable);
    }

    /**
     * Where a place lies seen from a player, in degrees: 0 straight ahead, positive to his right, as a screen arrow
     * turns clockwise.
     */
    public static float relativeYaw(double fromX, double fromZ, float playerYaw, double toX, double toZ) {
        float towards = (float) (Mth.atan2(toZ - fromZ, toX - fromX) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.wrapDegrees(towards - playerYaw);
    }
}
