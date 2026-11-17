package rotp.addons.ger.state;

/** How Return to Zero's cosmos is drawn under what the player runs. */
public enum GerRtzPackRoute {
    /** No shader pack: the sky renderer draws the cosmos. */
    NO_SHADERS,
    /** The pack declares the marker in its id map and draws the cosmos itself. */
    DECLARED,
    /** Any other pack: the mod paints the cosmos over the pack's final image. */
    UNDECLARED;

    /** The pack's name for the void and the id it must give it; no such entity exists, the line in entity.properties is the declaration. */
    public static final String MARKER_NAMESPACE = "geraddon";
    public static final String MARKER_PATH = "rtz_cosmic_void";
    public static final int MARKER_ID = 10098;

    public static GerRtzPackRoute choose(boolean packInUse, int declaredId) {
        if (!packInUse) return NO_SHADERS;
        return declaredId == MARKER_ID ? DECLARED : UNDECLARED;
    }
}
