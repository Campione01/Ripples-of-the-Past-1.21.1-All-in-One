package rotp.addons.kingcrimson.client.shatter;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Where the far ring's coarse cells read the ground from. */
@OnlyIn(Dist.CLIENT)
interface KingCrimsonShatterFarSource {
    /** Fills the cell for the column at x, z; false when nothing is known there. */
    boolean sample(int x, int z, Cell cell);

    @OnlyIn(Dist.CLIENT)
    final class Cell {
        int top;
        int color;
        int sideColor;
        /** The real light on the top, packed as the lightmap takes it. */
        int light;
        /** Whether the top is the ground rather than a tree or water. */
        boolean natural;
    }
}
