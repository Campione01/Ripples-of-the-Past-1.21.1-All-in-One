package rotp.addons.kingcrimson.client.shatter;

/** Placeholder presentation values for the shader-pack path only. */
public final class KingCrimsonShatterIrisConstants {
    private static final int PACK_EMISSIVE_ID_BASE = 10000;
    public static final int PLATE_EMISSIVE_ID = PACK_EMISSIVE_ID_BASE + 40;
    public static final int DETAIL_EMISSIVE_ID = PACK_EMISSIVE_ID_BASE + 100;
    // Only a pack that declares this id gets it: it draws its own cosmos on these pixels.
    public static final int VOID_EMISSIVE_ID = PACK_EMISSIVE_ID_BASE + 99;
    public static final float DETAIL_WIDTH_PER_BLOCK = 0.00525F;
    public static final float IRIS_TIME_ERASE_NIGHT = 0.5F;
    public static final float VOID_COMPOSITE_DISTANCE = 32.0F;

    static final float VOID_HALF_EDGE = 512.0F;
    static final int VOID_COLOR = 0xFF0C0000;
    static final int VOID_STAR_COLOR = 0xFFFFFFFF;
    static final int VOID_STARS_PER_FACE = 32;
    static final float VOID_STAR_HALF_WIDTH = 1.35F;

    private KingCrimsonShatterIrisConstants() {}

    /**
     * Level 0 is a quad nothing was asked of, as the far mesh's: it keeps the plate's one level. A code above the cap goes to the pack
     * as itself only if the pack says it reads such levels.
     */
    static int plateId(int code, boolean wide) {
        return rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlateLight.plateId(code, PLATE_EMISSIVE_ID, wide);
    }
}
