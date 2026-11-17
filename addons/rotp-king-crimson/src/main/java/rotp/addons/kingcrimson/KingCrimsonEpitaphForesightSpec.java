package rotp.addons.kingcrimson;

import java.util.List;

/**
 * Epitaph's foresight (the owner's design of R772.1, EV1105AC sections 1 to 4): how far a user sees ahead at each
 * Resolve level and how long the phantoms take to show it, as data; what a prediction is held to; the numbers of the
 * chase.
 */
public final class KingCrimsonEpitaphForesightSpec {
    /** The longest foresight, at the top Resolve level, in seconds; a setting. */
    public static final int MAX_FORESIGHT_SECONDS = 5;
    /** Tenths of the longest foresight a user sees at each Resolve level (index = level): 0.1, 0.1, 0.3, 0.6, 1.0. */
    private static final int[] FORESIGHT_TENTHS = {1, 1, 3, 6, 10};
    /** The phantom phase at each Resolve level, in ticks: 0.5 s, 0.5 s, 1 s, 2 s, 3 s. Not scaled by the setting. */
    private static final int[] PHASE_TICKS = {10, 10, 20, 40, 60};
    /** With one player online, the bodies live a quarter of a tick per server tick during the phantom phase. */
    public static final double SOLO_BODY_RATE = 0.25D;
    /** With several players online nobody slows: the phantoms live the whole foresight in this share of the phase. */
    public static final double MULTIPLAYER_PHASE_SCALE = 0.25D;
    /** Creatures hunting the user are predicted within this many blocks of him, and the crosshair reaches as far. */
    public static final double TARGET_RADIUS = 50.0D;
    public static final String DENIED_ENTITIES = "minecraft:ender_dragon,minecraft:command_block_minecart";
    /**
     * What a phantom may press, by the name the ability has in its moveset (EPITAPH-ARCH 2.4): the punch, the barrage, the
     * heavy punch and the guard of any Stand, and the chop and the impale of King Crimson. Never on it: Epitaph, Time
     * Erase and its step, Time Skip, a stop of time, a rewind, an acceleration, Bites the Dust, or anything else that
     * reaches a whole level.
     */
    public static final List<String> PHANTOM_ABILITIES =
            List.of("punch", "barrage", "heavy_punch", "guard", "kingcrimson_chop", "kingcrimson_impale");
    /**
     * Pairs of a Stand type and an ability of the table that the audit before delivery found to reach past a room
     * (EPITAPH-ARCH, ruling R13): written as the id of the type, a slash, and the name of the ability in its moveset, as
     * in jojo_ripples:star_platinum/barrage. A phantom of that type neither presses that ability nor takes it over.
     */
    public static final List<String> PHANTOM_ABILITY_EXCLUSIONS = List.of();
    /**
     * The three caps. Measured on the game test server with this add-on and the core alone: a tick of one phantom
     * costs about 25 microseconds, so a room of 24 creatures and what they shoot lives a tick in about half a
     * millisecond and the hundred ticks of the longest foresight in about 60; the press costs about 2 ms, most of it
     * the copy of the player. With several players online the room must live 7 ticks a server tick, which 24 creatures
     * do in half the budget. Measured again in stage G with creatures that bear a Stand, each of which is two phantoms: 24
     * of them and the user with his Stand are 50 phantoms, the press costs about 10 ms and a tick of the room about 1.4 ms
     * (29 microseconds a phantom), the hundred ticks about 140; 7 ticks a server tick are about 10 ms, just over the
     * budget, so that with 24 Stand bearers and several players online the room falls behind the phantom phase and the
     * window or the phase stretches, as section 6 has it. The walk itself is cheaper than the creatures it carries: the
     * server tick went from 2.4 ms with the 24 bearers thinking to 0.9 ms with them on their tracks.
     */
    /**
     * What a press costs when the entity under the crosshair is a player, and how long Epitaph then cools down (the owner,
     * 2026-11-11: 25 seconds, and a cost balanced against the whole of a use). The press is not all a use takes: the walked
     * user pays what his phantom did in its fight, action by action. Measured over the 100 ticks of the longest foresight,
     * against the phantom of a player with a Stand: 177 to 421 stamina, 283 on average, while a base bar of 1000 fills by
     * 150 with the Stand out. With this press a use takes 327 to 571 of that bar in all, 433 on average: about 40 to 50
     * percent, and under 60 in the dearest run. In the 25 seconds of the cooldown the bar fills by 750 with the Stand out
     * and by 1500 with it away.
     */
    public static final int PLAYER_STAMINA = 300;
    public static final int PLAYER_COOLDOWN_SECONDS = 25;
    public static final int SIM_BUDGET_MILLIS = 8;
    public static final int MAX_TARGETS = 24;
    public static final int MAX_CHILDREN = 64;
    /** The chase: a body off its track comes back by this much a tick at least, by this share of its distance at most. */
    public static final double CHASE_RETURN_MIN = 0.15D;
    public static final double CHASE_RETURN_SHARE = 0.35D;
    /** Further off its track than this, a body takes the short cut in time. */
    public static final double CHASE_MAX_OFFSET = 6.0D;

    private KingCrimsonEpitaphForesightSpec() {}

    /** How far ahead a user at this Resolve level foresees, in ticks, for a longest foresight in seconds. */
    public static int foresightTicks(int maxSeconds, int resolveLevel) {
        int level = Math.max(0, Math.min(resolveLevel, FORESIGHT_TENTHS.length - 1));
        return Math.max(1, (maxSeconds * 20 * FORESIGHT_TENTHS[level] + 9) / 10);
    }

    /** The phantom phase at this Resolve level, in ticks, as single player has it. */
    public static int phaseTicks(int resolveLevel) {
        return PHASE_TICKS[Math.max(0, Math.min(resolveLevel, PHASE_TICKS.length - 1))];
    }

    /** The phantom phase with several players online: the share of the table's that the setting gives. */
    public static int multiplayerPhaseTicks(int resolveLevel, double scale) {
        return Math.max(1, (int) Math.round(phaseTicks(resolveLevel) * scale));
    }
}
