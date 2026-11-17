package rotp.addons.kingcrimson.client.shatter;

import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * What is left of King Crimson's own far terrain, which the far-terrain add-on replaced (R772.1): the one check that add-on's
 * FarReaderGateMixin hooks. Its gate checks that this class and method exist and turns itself off when they do not, so the class
 * stays until the add-on drops that mixin. King Crimson reads no far terrain itself any more.
 */
@OnlyIn(Dist.CLIENT)
public final class KingCrimsonShatterVoxyFarReader {
    private KingCrimsonShatterVoxyFarReader() {}

    /** Never: King Crimson has no far reader of its own. */
    public static boolean available(ClientLevel level) {
        return false;
    }
}
