package rotp.addons.kingcrimson.shatter;

/** How the erased space is drawn under what the player runs. */
public enum KingCrimsonShatterPackRoute {
    /** No shader pack: the sky renderer draws the universe. */
    NO_SHADERS,
    /** The pack declares the marker in its id map and draws the universe itself. */
    DECLARED,
    /** Any other pack: the mod paints the universe over the pack's final image. */
    UNDECLARED;

    /** The pack's name for the void and the id it must give it; no such entity exists, the line in entity.properties is the declaration. */
    public static final String MARKER_NAMESPACE = "rotp_kingcrimson";
    public static final String MARKER_PATH = "time_erase_void";
    public static final int MARKER_ID = 10099;

    /**
     * Whether the world comes back from under the universe instead of all at once (owner, 2026-11-12: "修：結束前加約半秒淡出"). Such a
     * pack has adapted its exposure to the dark erase and would show the returned world overexposed; a pack that declares the void
     * keeps its own end.
     */
    public boolean fadesTheReturn() {
        return this == UNDECLARED;
    }

    public static KingCrimsonShatterPackRoute choose(boolean packInUse, int declaredId) {
        if (!packInUse) return NO_SHADERS;
        return declaredId == MARKER_ID ? DECLARED : UNDECLARED;
    }
}
