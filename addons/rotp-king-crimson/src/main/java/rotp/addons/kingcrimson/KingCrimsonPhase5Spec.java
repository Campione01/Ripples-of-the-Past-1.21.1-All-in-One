package rotp.addons.kingcrimson;

public final class KingCrimsonPhase5Spec {
    public static final float TIME_SKIP_STAMINA = 400.0F;
    public static final int TIME_SKIP_COOLDOWN_TICKS = 60;
    public static final int TIME_SKIP_IMPLIED_TICKS = 200;
    public static final int TIME_SKIP_INVULNERABLE_TICKS = 10;
    public static final double TIME_SKIP_MAX_DISTANCE = 32.0D;
    public static final double TIME_SKIP_STEP = 0.5D;
    public static final double TIME_SKIP_PRESENTATION_RADIUS = 192.0D;

    public static final float TIME_ERASE_ACTIVATION_STAMINA = 250.0F;
    public static final float TIME_ERASE_STAMINA_PER_TICK = 0.5F;
    // Owner 2026-11-07: the longest erase is a setting in seconds (KingCrimsonConfig timeEraseMaxSeconds), reached at
    // the top Resolve level; a level below takes its share of it, in tenths, the shares of Epitaph's foresight.
    public static final int TIME_ERASE_MAX_SECONDS = 15;
    public static final int TIME_ERASE_UNLOCK_RESOLVE_LEVEL = 3;
    private static final int[] TIME_ERASE_SHARE_TENTHS = {1, 1, 3, 6, 10};
    // Owner 2026-11-07, the passive: a hit above 10% of max health is escaped by a 2 s erase (300 stamina, 30 s
    // cooldown); a killing hit, or any hit at 10% health or below, by a 4 s one for free, once in 10 minutes.
    public static final double REFLEX_ERASE_THRESHOLD = 0.1D;
    public static final float REFLEX_ERASE_STAMINA = 300.0F;
    public static final double REFLEX_ERASE_SECONDS = 2.0D;
    public static final int REFLEX_ERASE_COOLDOWN_SECONDS = 30;
    public static final double LAST_CHANCE_ERASE_HEALTH_SHARE = 0.1D;
    public static final double LAST_CHANCE_ERASE_SECONDS = 4.0D;
    public static final int LAST_CHANCE_ERASE_COOLDOWN_SECONDS = 600;
    // Owner 2026-11-07, Blood Throw: 2 health and 50 stamina, 10 s; blood over a player's screen for 2 s, half
    // blindness for 5 s on a creature, which has no screen.
    public static final float BLOOD_THROW_HEALTH_COST = 2.0F;
    public static final float BLOOD_THROW_STAMINA = 50.0F;
    public static final int BLOOD_THROW_COOLDOWN_SECONDS = 10;
    public static final int BLOOD_THROW_MOB_BLINDNESS_TICKS = 100;
    public static final double BLOOD_THROW_RANGE = 32.0D;
    /** Blocks a drop of thrown blood flies in a tick. */
    public static final double BLOOD_THROW_SPEED = 1.6D;
    // Owner 2026-11-07, the step: modelled on The World's time stop blink (0.8 of the cost, a sixth of the
    // cooldown), one Resolve level under Time Erase, and modest: 64 blocks at most.
    public static final int TIME_ERASE_STEP_UNLOCK_RESOLVE_LEVEL = 2;
    public static final int TIME_ERASE_STEP_MAX_DISTANCE = 64;
    public static final float TIME_ERASE_STEP_COST_SHARE = 0.8F;
    public static final float TIME_ERASE_STEP_COOLDOWN_SHARE = 1.0F / 6.0F;
    // Owner 2026-11-07: "所有受影響的非玩家生物ai會短暫停頓1.5s", after a full erase at the top Resolve level.
    public static final double TIME_ERASE_AMNESIA_SECONDS = 1.5D;
    // Owner 2026-11-07, the shifted image: a little ("一點點") further for every move of another player.
    public static final double TIME_ERASE_SHIFT_PER_MOVE = 0.3D;
    // Owner 2026-11-11: with several players online so many of the creatures nearest to each player are shifted as
    // players are, drawn at random among twice as many within this range.
    public static final int TIME_ERASE_SHIFT_CREATURES = 8;
    public static final double TIME_ERASE_SHIFT_CREATURE_RANGE = 32.0D;
    // Owner 2026-11-11, the erase of a creature: its blow ends it, three seconds inside at the earliest ("非玩家生物發動時刪
    // 最少在裏面等三秒才能結束").
    public static final int CREATURE_ERASE_MIN_TICKS = 60;
    /** The fuse of a creeper: for so long its fake body, which never goes off, must want to. */
    public static final int CREATURE_ERASE_FUSE_TICKS = 30;
    /** A step longer than this in one tick was a teleport, a pearl or a leap: it shifts nothing. */
    public static final double TIME_ERASE_SHIFT_MAX_STEP = 2.0D;
    /** Owner: a drop of more than three blocks along the way stops the image at the edge. */
    public static final int TIME_ERASE_SHIFT_MAX_DROP = 3;
    // 1.16 KCConfig.timeErasePresentEntityDelay (60) timed only the stationary "present" afterimage and the
    // rewind it marked at erase end; the port keeps neither (owner decision 2026-09-23, manga logic).
    public static final int TIME_ERASE_MOVING_AFTERIMAGE_DELAY_TICKS = 10;
    public static final double TIME_ERASE_AFTERIMAGE_RADIUS = 192.0D;
    public static final double TIME_ERASE_PRESENTATION_RADIUS = 192.0D;
    public static final double TIME_ERASE_DECOY_TARGET_RADIUS = 16.0D;
    public static final int TIME_ERASE_MINIMUM_COOLDOWN_TICKS = 120;
    // The owner, 2026-11-12: twice the run time was too long. An erase waits as long as it ran, at least the minimum.
    public static final float TIME_ERASE_COOLDOWN_MULTIPLIER = 1.0F;
    // 1.16 KCConfig defaults; only timeEraseCooldown ever reached the player (KingCrimsonTimeErase :245-252)
    public static final boolean TIME_ERASE_DYNAMIC_COOLDOWN = true;
    public static final int TIME_ERASE_FIXED_COOLDOWN_TICKS = 200;
    public static final double TIME_ERASE_MOVEMENT_MULTIPLIER = 1.0D;
    public static final double TIME_ERASE_MAX_FALL_SPEED = -0.15D;
    public static final int TIME_ERASE_TRANSITION_MILLIS = 375;
    public static final int TIME_ERASE_TRANSITION_FRAME_MILLIS = 23;
    public static final int TIME_ERASE_SKY_PORTAL_LAYERS = 13;
    public static final long TIME_ERASE_SKY_RANDOM_SEED = 31100L;

    public static final String TIME_SKIP_POST_CHAIN_PATH =
            "shaders/post/time_skip.json";

    private KingCrimsonPhase5Spec() {}

    /** A Resolve level's share of a cap in ticks, rounded up to a tick. */
    public static int timeEraseTicks(int capTicks, int resolveLevel) {
        int level = Math.max(0, Math.min(resolveLevel, TIME_ERASE_SHARE_TENTHS.length - 1));
        return (int) ((Math.max(capTicks, 0) * (long) TIME_ERASE_SHARE_TENTHS[level] + 9L) / 10L);
    }
}
