package rotp.addons.kingcrimson;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import rotp.core.init.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 1.16 KCConfig (COMMON, rotp_kingcrimson.toml): same sections, keys and ranges; the defaults are the spec constants.
 * Not kept: epitaphStaminaCostTick (1.16 never read it), timeErasePresentEntityDelay (the stationary afterimage
 * it timed is gone, owner decision 2026-09-23) and timeEraseDuration, in ticks, which timeEraseMaxSeconds replaces
 * (owner 2026-11-07: the limit of the top Resolve level, in seconds; a value left in the old key is not read).
 * Added in R772.1 with the owner's Time Erase: timeEraseShiftPlayers, timeEraseShiftPerMove,
 * timeEraseAmnesiaSeconds and timeEraseAmnesiaExempt, and the section of the passive that took the place of
 * Epitaph's dodge (reflexErase and its seven values). Added with the owner's rules of 2026-11-11:
 * timeEraseShiftCreatures.
 */
public final class KingCrimsonConfig {
    public static final String FILE_NAME = "rotp_kingcrimson.toml";
    /** 1.16 ids used the core's old "jojo" namespace. */
    private static final String LEGACY_CORE_NAMESPACE = "jojo";

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue BARRAGE_HEALTH_THRESHOLD;
    public static final ModConfigSpec.DoubleValue BARRAGE_HEALTH_THRESHOLD_DOPPIO;
    public static final ModConfigSpec.DoubleValue DESPERATE_EYEJAB_HEALTH_THRESHOLD;
    public static final ModConfigSpec.IntValue DESPERATE_EYEJAB_COOLDOWN;
    public static final ModConfigSpec.IntValue CHOP_COOLDOWN;
    public static final ModConfigSpec.IntValue IMPALE_COOLDOWN;
    public static final ModConfigSpec.IntValue IMPALE_GAPING_WOUND_LEVEL;
    public static final ModConfigSpec.IntValue PROJECTILE_THROW_COOLDOWN;
    public static final ModConfigSpec.ConfigValue<String> ALLOWED_PROJECTILES;
    public static final ModConfigSpec.IntValue EPITAPH_COOLDOWN;
    public static final ModConfigSpec.IntValue EPITAPH_STAMINA;
    public static final ModConfigSpec.IntValue EPITAPH_PLAYER_STAMINA;
    public static final ModConfigSpec.IntValue EPITAPH_PLAYER_COOLDOWN_SECONDS;
    public static final ModConfigSpec.BooleanValue EPITAPH_ENABLED;
    public static final ModConfigSpec.IntValue EPITAPH_MAX_FORESIGHT_SECONDS;
    public static final ModConfigSpec.IntValue EPITAPH_RADIUS;
    public static final ModConfigSpec.DoubleValue EPITAPH_MULTIPLAYER_PHASE_SCALE;
    public static final ModConfigSpec.BooleanValue EPITAPH_PHANTOM_SOUNDS;
    public static final ModConfigSpec.BooleanValue EPITAPH_USER_FOLLOWS_PHANTOM;
    public static final ModConfigSpec.BooleanValue EPITAPH_STEP_FREES_USER;
    public static final ModConfigSpec.BooleanValue EPITAPH_RELEASE_ON_VOID_BLOW;
    public static final ModConfigSpec.BooleanValue EPITAPH_FORCE_PLAYER_DEATH;
    public static final ModConfigSpec.BooleanValue EPITAPH_PULL_BACK;
    public static final ModConfigSpec.IntValue EPITAPH_SIM_BUDGET_MILLIS;
    public static final ModConfigSpec.IntValue EPITAPH_MAX_TARGETS;
    public static final ModConfigSpec.IntValue EPITAPH_MAX_CHILDREN;
    public static final ModConfigSpec.ConfigValue<String> EPITAPH_DENIED_ENTITIES;
    public static final ModConfigSpec.BooleanValue EPITAPH_VERIFY_LOOKUPS;
    public static final ModConfigSpec.BooleanValue EPITAPH_TRUST_TRACK;
    public static final ModConfigSpec.DoubleValue EPITAPH_CHASE_RETURN_MIN;
    public static final ModConfigSpec.DoubleValue EPITAPH_CHASE_RETURN_SHARE;
    public static final ModConfigSpec.DoubleValue EPITAPH_CHASE_MAX_OFFSET;
    public static final ModConfigSpec.IntValue TIME_ERASE_MAX_SECONDS;
    public static final ModConfigSpec.IntValue TIME_ERASE_COOLDOWN;
    public static final ModConfigSpec.BooleanValue TIME_ERASE_DYNAMIC_COOLDOWN;
    public static final ModConfigSpec.DoubleValue TIME_ERASE_DYNAMIC_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.IntValue TIME_ERASE_DYNAMIC_MINIMUM_COOLDOWN;
    public static final ModConfigSpec.DoubleValue TIME_ERASE_STAMINA_TICK;
    public static final ModConfigSpec.BooleanValue TIME_ERASE_SHIFT_PLAYERS;
    public static final ModConfigSpec.DoubleValue TIME_ERASE_SHIFT_PER_MOVE;
    public static final ModConfigSpec.IntValue TIME_ERASE_SHIFT_CREATURES;
    public static final ModConfigSpec.DoubleValue TIME_ERASE_AMNESIA_SECONDS;
    public static final ModConfigSpec.ConfigValue<String> TIME_ERASE_AMNESIA_EXEMPT;
    public static final ModConfigSpec.BooleanValue REFLEX_ERASE;
    public static final ModConfigSpec.DoubleValue REFLEX_ERASE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue REFLEX_ERASE_STAMINA;
    public static final ModConfigSpec.DoubleValue REFLEX_ERASE_SECONDS;
    public static final ModConfigSpec.IntValue REFLEX_ERASE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.DoubleValue LAST_CHANCE_ERASE_HEALTH_SHARE;
    public static final ModConfigSpec.DoubleValue LAST_CHANCE_ERASE_SECONDS;
    public static final ModConfigSpec.IntValue LAST_CHANCE_ERASE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.DoubleValue BLOOD_THROW_HEALTH_COST;
    public static final ModConfigSpec.DoubleValue BLOOD_THROW_STAMINA;
    public static final ModConfigSpec.IntValue BLOOD_THROW_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue TIME_ERASE_STEP_MAX_DISTANCE;
    public static final ModConfigSpec.IntValue TIME_SKIP_DURATION;
    public static final ModConfigSpec.IntValue TIME_SKIP_COOLDOWN;

    private static volatile ParsedProjectiles parsedProjectiles;
    private static volatile ParsedIds parsedAmnesiaExempt;
    private static volatile ParsedIds parsedEpitaphDenied;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("King Crimson Barrage Config");
        BARRAGE_HEALTH_THRESHOLD = builder
                .comment("Above this share of max health (0.0 to 1.0) a resolve 3+ user cannot use King Crimson's barrage.")
                .defineInRange("barrageHealthThreshold",
                        (double) KingCrimsonDonorGameplaySpec.BARRAGE_HEALTH_THRESHOLD, 0.0D, 1.0D);
        BARRAGE_HEALTH_THRESHOLD_DOPPIO = builder
                .comment("Above this share of max health (0.0 to 1.0) a resolve 3+ user cannot use Doppio's barrage.")
                .defineInRange("barrageHealthThresholdDoppio",
                        (double) KingCrimsonDonorGameplaySpec.DOPPIO_BARRAGE_HEALTH_THRESHOLD, 0.0D, 1.0D);
        builder.pop();

        builder.push("King Crimson Desperate Eyejab Config");
        DESPERATE_EYEJAB_HEALTH_THRESHOLD = builder
                .comment("Below this share of max health (0.0 to 1.0) Ground Punch is replaced by Desperate Eyejab.")
                .defineInRange("desperateEyejabHealthThreshold",
                        (double) KingCrimsonPhase4Spec.EYEJAB_HEALTH_THRESHOLD, 0.0D, 1.0D);
        DESPERATE_EYEJAB_COOLDOWN = builder
                .comment("Desperate Eyejab cooldown in ticks.")
                .defineInRange("desperateEyejabCooldown",
                        KingCrimsonPhase4Spec.EYEJAB_COOLDOWN_TICKS, 0, 12000);
        builder.pop();

        builder.push("King Crimson Chop Config");
        CHOP_COOLDOWN = builder
                .comment("Chop cooldown in ticks.")
                .defineInRange("chopCooldown", KingCrimsonPhase2Spec.CHOP_COOLDOWN_TICKS, 0, 12000);
        builder.pop();

        builder.push("King Crimson Impale Config");
        IMPALE_COOLDOWN = builder
                .comment("Impale cooldown in ticks.")
                .defineInRange("impaleCooldown", KingCrimsonPhase2Spec.IMPALE_COOLDOWN_TICKS, 0, 12000);
        // 1.16 allowed -1; a 1.21 effect amplifier cannot go below 0
        IMPALE_GAPING_WOUND_LEVEL = builder
                .comment("Gaping Wound amplifier applied by Impale (0 = level 1, 1 = level 2, ...).")
                .defineInRange("impaleGapingWoundLevel",
                        KingCrimsonPhase2Spec.IMPALE_GAPING_WOUND_AMPLIFIER, 0, 9);
        builder.pop();

        builder.push("King Crimson Projectile Throw Config");
        PROJECTILE_THROW_COOLDOWN = builder
                .comment("Projectile Throw cooldown in ticks (the triple throw shares it).")
                .defineInRange("projectileThrowCooldown",
                        KingCrimsonPhase2Spec.PROJECTILE_COOLDOWN_TICKS, 0, 12000);
        ALLOWED_PROJECTILES = builder
                .comment("Comma-separated item ids Projectile Throw may throw; arrows are always allowed. "
                        + "Only items King Crimson knows how to throw count. \"jojo:\" is read as the core namespace.")
                .define("allowedProjectiles", KingCrimsonPhase2Spec.ALLOWED_PROJECTILES);
        builder.pop();

        builder.push("King Crimson Epitaph Config");
        EPITAPH_COOLDOWN = builder
                .comment("Epitaph cooldown in ticks.")
                .defineInRange("epitaphCooldown", KingCrimsonPhase3Spec.EPITAPH_COOLDOWN_TICKS, 0, 12000);
        EPITAPH_STAMINA = builder
                .comment("Stamina one Epitaph costs, taken at the press.")
                .defineInRange("epitaphStamina", (int) KingCrimsonPhase3Spec.EPITAPH_ACTIVATION_STAMINA, 0, 2000);
        EPITAPH_PLAYER_STAMINA = builder
                .comment("Stamina one Epitaph costs when a player is under the crosshair, taken at the press. A rider and what he "
                        + "rides are one target at one cost. The foreseen fight is paid on top of it, action by action: with the "
                        + "default, one use takes about 40 to 50 percent of a base stamina bar in all.")
                .defineInRange("epitaphPlayerStamina", KingCrimsonEpitaphForesightSpec.PLAYER_STAMINA, 0, 2000);
        EPITAPH_PLAYER_COOLDOWN_SECONDS = builder
                .comment("Epitaph cooldown in seconds after a press with a player under the crosshair.")
                .defineInRange("epitaphPlayerCooldownSeconds", KingCrimsonEpitaphForesightSpec.PLAYER_COOLDOWN_SECONDS, 0, 600);
        EPITAPH_ENABLED = builder
                .comment("Whether Epitaph foresees at all. Off, the ability is refused.")
                .define("epitaphEnabled", true);
        EPITAPH_MAX_FORESIGHT_SECONDS = builder
                .comment("The longest foresight in seconds, reached at Resolve 4. Resolve 0 and 1 see 10% of it, "
                        + "Resolve 2 sees 30%, Resolve 3 sees 60%.")
                .defineInRange("epitaphMaxForesightSeconds", KingCrimsonEpitaphForesightSpec.MAX_FORESIGHT_SECONDS, 1, 30);
        EPITAPH_RADIUS = builder
                .comment("Blocks around the user within which creatures hunting him are foreseen; the crosshair reaches as far.")
                .defineInRange("epitaphRadius", (int) KingCrimsonEpitaphForesightSpec.TARGET_RADIUS, 8, 128);
        EPITAPH_MULTIPLAYER_PHASE_SCALE = builder
                .comment("With several players online nobody slows and the phantoms run ahead instead: they live the whole "
                        + "foresight in this share of the single player phantom phase. 0.25 is four times as fast; 1.0 is "
                        + "the single player phase as it is.")
                .defineInRange("epitaphMultiplayerPhaseScale", KingCrimsonEpitaphForesightSpec.MULTIPLAYER_PHASE_SCALE, 0.05D, 1.0D);
        EPITAPH_PHANTOM_SOUNDS = builder
                .comment("Whether the one who foresees also hears what the phantoms would sound like. Off, the foreseen "
                        + "future is silent.")
                .define("epitaphPhantomSounds", false);
        EPITAPH_USER_FOLLOWS_PHANTOM = builder
                .comment("Whether the one who foresees is walked along his own foreseen track from the press, so that "
                        + "what he is shown is what happens; Time Erase frees him. Off, he keeps control, and nothing "
                        + "that was foreseen of him or for him happens.")
                .define("epitaphUserFollowsPhantom", true);
        EPITAPH_STEP_FREES_USER = builder
                .comment("Whether the Time Erase step frees the one who foresees from his foreseen track, as an erase "
                        + "does. Off, the step is refused while he is walked.")
                .define("epitaphStepFreesUser", true);
        EPITAPH_RELEASE_ON_VOID_BLOW = builder
                .comment("After the one who foresees has erased time, a creature still walks the track his foreseen "
                        + "blows shaped, though those blows no longer land. On, it is let go at the first such blow "
                        + "and decides anew.")
                .define("epitaphReleaseOnVoidBlow", false);
        EPITAPH_FORCE_PLAYER_DEATH = builder
                .comment("Whether a player foreseen to die dies at that tick even when the real blow leaves him alive. "
                        + "Off, he takes the foreseen blow for real and what is really on him decides.")
                .define("epitaphForcePlayerDeath", false);
        EPITAPH_PULL_BACK = builder
                .comment("With several players online and a player under the crosshair: on, everyone who is foreseen plays on "
                        + "while the phantoms run ahead, and is then put back exactly as he was at the press, inventory "
                        + "included, and walked along what was foreseen. It cannot be proven safe against every mod that "
                        + "keeps items in a place of its own; each pull-back is written to the server log. Off, that "
                        + "player is walked from the press and nothing is undone.")
                .define("epitaphPullBack", false);
        EPITAPH_SIM_BUDGET_MILLIS = builder
                .comment("Milliseconds of a server tick a prediction may spend running ahead. Past it the phantoms run "
                        + "slower instead of the server.")
                .defineInRange("epitaphSimBudgetMillis", KingCrimsonEpitaphForesightSpec.SIM_BUDGET_MILLIS, 0, 50);
        EPITAPH_MAX_TARGETS = builder
                .comment("The most entities one Epitaph foresees, the nearest first; the one under the crosshair is always in.")
                .defineInRange("epitaphMaxTargets", KingCrimsonEpitaphForesightSpec.MAX_TARGETS, 1, 128);
        EPITAPH_MAX_CHILDREN = builder
                .comment("The most entities that first appear inside one prediction (arrows shot, items dropped) and are "
                        + "foreseen too. Past it they still appear when their time comes, unforeseen.")
                .defineInRange("epitaphMaxChildren", KingCrimsonEpitaphForesightSpec.MAX_CHILDREN, 0, 512);
        EPITAPH_DENIED_ENTITIES = builder
                .comment("Comma-separated entity type ids Epitaph never foresees. A mod id alone, or modid:*, leaves out "
                        + "every entity of that mod.")
                .define("epitaphDeniedEntities", KingCrimsonEpitaphForesightSpec.DENIED_ENTITIES);
        EPITAPH_VERIFY_LOOKUPS = builder
                .comment("Troubleshooting: check every entity a prediction is handed and leave out what is not its own. "
                        + "Turn it on if another mod makes foreseen creatures react to real ones.")
                .define("epitaphVerifyLookups", false);
        EPITAPH_TRUST_TRACK = builder
                .comment("Troubleshooting: carry a foreseen creature along its track through anything that was put on it, "
                        + "without the short cut past it.")
                .define("epitaphTrustTrack", false);
        EPITAPH_CHASE_RETURN_MIN = builder
                .comment("Blocks per tick, at least, by which a foreseen creature knocked off its track comes back to it.")
                .defineInRange("epitaphChaseReturnMin", KingCrimsonEpitaphForesightSpec.CHASE_RETURN_MIN, 0.01D, 2.0D);
        EPITAPH_CHASE_RETURN_SHARE = builder
                .comment("Share of its distance from its track by which a foreseen creature comes back each tick, when "
                        + "that is more than the least.")
                .defineInRange("epitaphChaseReturnShare", KingCrimsonEpitaphForesightSpec.CHASE_RETURN_SHARE, 0.0D, 1.0D);
        EPITAPH_CHASE_MAX_OFFSET = builder
                .comment("Blocks off its track past which a foreseen creature appears back on it at once.")
                .defineInRange("epitaphChaseMaxOffset", KingCrimsonEpitaphForesightSpec.CHASE_MAX_OFFSET, 1.0D, 32.0D);
        builder.pop();

        builder.push("King Crimson Time Erase Config");
        TIME_ERASE_MAX_SECONDS = builder
                .comment("The longest Time Erase in seconds, reached at Resolve 4. Resolve 3 gets 60% of it.")
                .defineInRange("timeEraseMaxSeconds", KingCrimsonPhase5Spec.TIME_ERASE_MAX_SECONDS, 1, 120);
        TIME_ERASE_COOLDOWN = builder
                .comment("Time Erase cooldown in ticks when timeEraseDynamicCooldown is off.")
                .defineInRange("timeEraseCooldown",
                        KingCrimsonPhase5Spec.TIME_ERASE_FIXED_COOLDOWN_TICKS, 0, 12000);
        TIME_ERASE_DYNAMIC_COOLDOWN = builder
                .comment("Whether the cooldown of Time Erase grows with how long the erase ran, as a time stop's does: "
                        + "the ticks it ran times timeEraseDynamicCooldownMultiplier, at least "
                        + "timeEraseDynamicMinimumCooldown. Off: always timeEraseCooldown. A third of it in Resolve.")
                .define("timeEraseDynamicCooldown", KingCrimsonPhase5Spec.TIME_ERASE_DYNAMIC_COOLDOWN);
        TIME_ERASE_DYNAMIC_COOLDOWN_MULTIPLIER = builder
                .comment("Cooldown ticks for each tick a Time Erase ran, with timeEraseDynamicCooldown on. "
                        + "A time stop waits 3.")
                .defineInRange("timeEraseDynamicCooldownMultiplier",
                        (double) KingCrimsonPhase5Spec.TIME_ERASE_COOLDOWN_MULTIPLIER, 0.0D, 10.0D);
        TIME_ERASE_DYNAMIC_MINIMUM_COOLDOWN = builder
                .comment("The shortest cooldown of Time Erase in ticks, with timeEraseDynamicCooldown on.")
                .defineInRange("timeEraseDynamicMinimumCooldown",
                        KingCrimsonPhase5Spec.TIME_ERASE_MINIMUM_COOLDOWN_TICKS, 0, 12000);
        TIME_ERASE_STAMINA_TICK = builder
                .comment("Stamina Time Erase costs per tick. Its user regenerates no stamina while it runs, and an "
                        + "erase that ends early gives back the unused share of its start.")
                .defineInRange("timeEraseStaminaTick",
                        (double) KingCrimsonPhase5Spec.TIME_ERASE_STAMINA_PER_TICK, 0.0D, 100.0D);
        TIME_ERASE_SHIFT_PLAYERS = builder
                .comment("Whether the eraser sees other players ahead of where they are, more so the further they "
                        + "move, and they are put there when the Time Erase ends.")
                .define("timeEraseShiftPlayers", true);
        TIME_ERASE_SHIFT_PER_MOVE = builder
                .comment("Blocks another player's image runs ahead for each block he moves during a Time Erase.")
                .defineInRange("timeEraseShiftPerMove",
                        KingCrimsonPhase5Spec.TIME_ERASE_SHIFT_PER_MOVE, 0.0D, 2.0D);
        TIME_ERASE_SHIFT_CREATURES = builder
                .comment("With more than one player online: how many of the creatures nearest to each player a "
                        + "Time Erase shifts as it shifts players, drawn at random among twice as many. "
                        + "0 shifts no creature. Needs timeEraseShiftPlayers.")
                .defineInRange("timeEraseShiftCreatures",
                        KingCrimsonPhase5Spec.TIME_ERASE_SHIFT_CREATURES, 0, 64);
        TIME_ERASE_AMNESIA_SECONDS = builder
                .comment("Seconds the creatures around stop thinking when a full Time Erase at Resolve 4 ends. "
                        + "A shorter erase gives the same share of it. 0 turns it off.")
                .defineInRange("timeEraseAmnesiaSeconds",
                        KingCrimsonPhase5Spec.TIME_ERASE_AMNESIA_SECONDS, 0.0D, 10.0D);
        TIME_ERASE_AMNESIA_EXEMPT = builder
                .comment("Comma-separated entity type ids the end of a Time Erase never pauses.")
                .define("timeEraseAmnesiaExempt", "");
        builder.pop();

        builder.push("King Crimson Time Erase Reflex Config");
        REFLEX_ERASE = builder
                .comment("The passive skill: a heavy hit is escaped by a short Time Erase, a killing hit by a longer one.")
                .define("reflexErase", true);
        REFLEX_ERASE_THRESHOLD = builder
                .comment("A hit that would take more than this share of max health (0.0 to 1.0) sets off the reflex.")
                .defineInRange("reflexEraseThreshold",
                        KingCrimsonPhase5Spec.REFLEX_ERASE_THRESHOLD, 0.0D, 1.0D);
        REFLEX_ERASE_STAMINA = builder
                .comment("Stamina the reflex costs. Without it the hit lands.")
                .defineInRange("reflexEraseStamina",
                        (double) KingCrimsonPhase5Spec.REFLEX_ERASE_STAMINA, 0.0D, 10000.0D);
        REFLEX_ERASE_SECONDS = builder
                .comment("Seconds the reflex's Time Erase lasts, never longer than the user's own limit.")
                .defineInRange("reflexEraseSeconds",
                        KingCrimsonPhase5Spec.REFLEX_ERASE_SECONDS, 0.5D, 60.0D);
        REFLEX_ERASE_COOLDOWN_SECONDS = builder
                .comment("Seconds before the reflex can go off again. With the client setting No Stand ability "
                        + "cooldowns on it does not wait: a repeat within this time costs extra stamina instead.")
                .defineInRange("reflexEraseCooldownSeconds",
                        KingCrimsonPhase5Spec.REFLEX_ERASE_COOLDOWN_SECONDS, 0, 3600);
        LAST_CHANCE_ERASE_HEALTH_SHARE = builder
                .comment("At or below this share of max health (0.0 to 1.0) any hit sets off the last chance, "
                        + "as a killing hit does.")
                .defineInRange("lastChanceEraseHealthShare",
                        KingCrimsonPhase5Spec.LAST_CHANCE_ERASE_HEALTH_SHARE, 0.0D, 1.0D);
        LAST_CHANCE_ERASE_SECONDS = builder
                .comment("Seconds the last chance's Time Erase lasts, never longer than the user's own limit.")
                .defineInRange("lastChanceEraseSeconds",
                        KingCrimsonPhase5Spec.LAST_CHANCE_ERASE_SECONDS, 0.5D, 60.0D);
        LAST_CHANCE_ERASE_COOLDOWN_SECONDS = builder
                .comment("Seconds before the last chance can go off again; it costs nothing. It waits this long "
                        + "with the client setting No Stand ability cooldowns on as well.")
                .defineInRange("lastChanceEraseCooldownSeconds",
                        KingCrimsonPhase5Spec.LAST_CHANCE_ERASE_COOLDOWN_SECONDS, 0, 86400);
        builder.pop();

        builder.push("King Crimson Blood Throw Config");
        BLOOD_THROW_HEALTH_COST = builder
                .comment("Health Blood Throw takes from its user; it never takes the last point.")
                .defineInRange("bloodThrowHealthCost",
                        (double) KingCrimsonPhase5Spec.BLOOD_THROW_HEALTH_COST, 0.0D, 20.0D);
        BLOOD_THROW_STAMINA = builder
                .comment("Stamina Blood Throw costs.")
                .defineInRange("bloodThrowStamina",
                        (double) KingCrimsonPhase5Spec.BLOOD_THROW_STAMINA, 0.0D, 10000.0D);
        BLOOD_THROW_COOLDOWN_SECONDS = builder
                .comment("Blood Throw cooldown in seconds.")
                .defineInRange("bloodThrowCooldownSeconds",
                        KingCrimsonPhase5Spec.BLOOD_THROW_COOLDOWN_SECONDS, 0, 3600);
        builder.pop();

        builder.push("King Crimson Time Erase Step Config");
        TIME_ERASE_STEP_MAX_DISTANCE = builder
                .comment("The furthest a Time Erase Step reaches, in blocks. Below it the reach is what the user "
                        + "walks in his own longest Time Erase.")
                .defineInRange("timeEraseStepMaxDistance",
                        KingCrimsonPhase5Spec.TIME_ERASE_STEP_MAX_DISTANCE, 1, 192);
        builder.pop();

        builder.push("King Crimson Time Skip Config");
        TIME_SKIP_DURATION = builder
                .comment("Ticks one Time Skip moves the world ahead (high values stutter the game).")
                .defineInRange("timeSkipDuration", KingCrimsonPhase5Spec.TIME_SKIP_IMPLIED_TICKS, 0, 1200);
        TIME_SKIP_COOLDOWN = builder
                .comment("Time Skip cooldown in ticks.")
                .defineInRange("timeSkipCooldown", KingCrimsonPhase5Spec.TIME_SKIP_COOLDOWN_TICKS, 0, 12000);
        builder.pop();
        SPEC = builder.build();
    }

    private KingCrimsonConfig() {}

    public static float barrageHealthThreshold(boolean doppio) {
        return (float) read(doppio ? BARRAGE_HEALTH_THRESHOLD_DOPPIO : BARRAGE_HEALTH_THRESHOLD);
    }

    public static float eyejabHealthThreshold() {
        return (float) read(DESPERATE_EYEJAB_HEALTH_THRESHOLD);
    }

    public static int eyejabCooldownTicks() {
        return read(DESPERATE_EYEJAB_COOLDOWN);
    }

    public static int chopCooldownTicks() {
        return read(CHOP_COOLDOWN);
    }

    public static int impaleCooldownTicks() {
        return read(IMPALE_COOLDOWN);
    }

    public static int impaleGapingWoundAmplifier() {
        return read(IMPALE_GAPING_WOUND_LEVEL);
    }

    public static int projectileCooldownTicks() {
        return read(PROJECTILE_THROW_COOLDOWN);
    }

    public static int epitaphCooldownTicks() {
        return read(EPITAPH_COOLDOWN);
    }

    public static boolean epitaphEnabled() {
        return read(EPITAPH_ENABLED);
    }

    public static float epitaphStamina() {
        return read(EPITAPH_STAMINA);
    }

    public static float epitaphPlayerStamina() {
        return read(EPITAPH_PLAYER_STAMINA);
    }

    public static int epitaphPlayerCooldownTicks() {
        return read(EPITAPH_PLAYER_COOLDOWN_SECONDS) * 20;
    }

    public static boolean epitaphUserFollowsPhantom() {
        return read(EPITAPH_USER_FOLLOWS_PHANTOM);
    }

    public static boolean epitaphStepFreesUser() {
        return read(EPITAPH_STEP_FREES_USER);
    }

    public static boolean epitaphReleaseOnVoidBlow() {
        return read(EPITAPH_RELEASE_ON_VOID_BLOW);
    }

    public static boolean epitaphForcePlayerDeath() {
        return read(EPITAPH_FORCE_PLAYER_DEATH);
    }

    public static boolean epitaphPullBack() {
        return read(EPITAPH_PULL_BACK);
    }

    /** How far ahead a user at this Resolve level foresees, in ticks. */
    public static int epitaphForesightTicks(int resolveLevel) {
        return KingCrimsonEpitaphForesightSpec.foresightTicks(read(EPITAPH_MAX_FORESIGHT_SECONDS), resolveLevel);
    }

    public static double epitaphRadius() {
        return read(EPITAPH_RADIUS);
    }

    /** The phantom phase with several players online, in ticks. */
    public static int epitaphMultiplayerPhaseTicks(int resolveLevel) {
        return KingCrimsonEpitaphForesightSpec.multiplayerPhaseTicks(resolveLevel, read(EPITAPH_MULTIPLAYER_PHASE_SCALE));
    }

    public static boolean epitaphPhantomSounds() {
        return read(EPITAPH_PHANTOM_SOUNDS);
    }

    public static long epitaphSimBudgetNanos() {
        return read(EPITAPH_SIM_BUDGET_MILLIS) * 1_000_000L;
    }

    public static int epitaphMaxTargets() {
        return read(EPITAPH_MAX_TARGETS);
    }

    public static int epitaphMaxChildren() {
        return read(EPITAPH_MAX_CHILDREN);
    }

    /** Whether the setting leaves this type out of every prediction: by its id, or by its namespace. */
    public static boolean epitaphDenies(EntityType<?> type) {
        String raw = SPEC.isLoaded() ? EPITAPH_DENIED_ENTITIES.get() : EPITAPH_DENIED_ENTITIES.getDefault();
        if (raw.isBlank()) {
            return false;
        }
        ParsedIds parsed = parsedEpitaphDenied;
        if (parsed == null || !parsed.raw().equals(raw)) {
            Set<ResourceLocation> ids = new HashSet<>();
            for (String entry : raw.split(",")) {
                String id = entry.trim().toLowerCase(Locale.ROOT);
                // A namespace alone is kept as namespace:* with an empty path.
                ResourceLocation parsedId = id.endsWith(":*") || !id.contains(":")
                        ? ResourceLocation.tryBuild(id.replace(":*", ""), "")
                        : ResourceLocation.tryParse(id);
                if (parsedId != null) {
                    ids.add(parsedId);
                }
            }
            parsed = new ParsedIds(raw, Set.copyOf(ids));
            parsedEpitaphDenied = parsed;
        }
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return parsed.ids().contains(key) || parsed.ids().contains(ResourceLocation.tryBuild(key.getNamespace(), ""));
    }

    public static boolean epitaphVerifyLookups() {
        return read(EPITAPH_VERIFY_LOOKUPS);
    }

    public static boolean epitaphTrustTrack() {
        return read(EPITAPH_TRUST_TRACK);
    }

    public static double epitaphChaseReturnMin() {
        return read(EPITAPH_CHASE_RETURN_MIN);
    }

    public static double epitaphChaseReturnShare() {
        return read(EPITAPH_CHASE_RETURN_SHARE);
    }

    public static double epitaphChaseMaxOffset() {
        return read(EPITAPH_CHASE_MAX_OFFSET);
    }

    /** The cap: the longest Time Erase, at the top Resolve level, in ticks. */
    public static int timeEraseCapTicks() {
        return read(TIME_ERASE_MAX_SECONDS) * 20;
    }

    /** The longest Time Erase of a user at this Resolve level, in ticks; a level below the unlock takes the unlock's. */
    public static int timeEraseMaxTicks(int resolveLevel) {
        return KingCrimsonPhase5Spec.timeEraseTicks(timeEraseCapTicks(),
                Math.max(resolveLevel, KingCrimsonPhase5Spec.TIME_ERASE_UNLOCK_RESOLVE_LEVEL));
    }

    public static boolean timeEraseShiftsPlayers() {
        return SPEC.isLoaded() ? TIME_ERASE_SHIFT_PLAYERS.get() : TIME_ERASE_SHIFT_PLAYERS.getDefault();
    }

    public static double timeEraseShiftPerMove() {
        return read(TIME_ERASE_SHIFT_PER_MOVE);
    }

    /** How many creatures near each player an erase shifts when several players are online. */
    public static int timeEraseShiftCreatures() {
        return read(TIME_ERASE_SHIFT_CREATURES);
    }

    /** The amnesia after a full erase at the top Resolve level, in ticks. */
    public static int timeEraseAmnesiaTicks() {
        return (int) Math.round(read(TIME_ERASE_AMNESIA_SECONDS) * 20.0D);
    }

    public static boolean isAmnesiaExempt(EntityType<?> type) {
        String raw = SPEC.isLoaded() ? TIME_ERASE_AMNESIA_EXEMPT.get() : TIME_ERASE_AMNESIA_EXEMPT.getDefault();
        if (raw.isBlank()) {
            return false;
        }
        ParsedIds parsed = parsedAmnesiaExempt;
        if (parsed == null || !parsed.raw().equals(raw)) {
            Set<ResourceLocation> ids = new HashSet<>();
            for (String entry : raw.split(",")) {
                ResourceLocation id = ResourceLocation.tryParse(entry.trim().toLowerCase(Locale.ROOT));
                if (id != null) {
                    ids.add(id);
                }
            }
            parsed = new ParsedIds(raw, Set.copyOf(ids));
            parsedAmnesiaExempt = parsed;
        }
        return parsed.ids().contains(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    public static float timeEraseStaminaPerTick() {
        return (float) read(TIME_ERASE_STAMINA_TICK);
    }

    public static int timeSkipDurationTicks() {
        return read(TIME_SKIP_DURATION);
    }

    public static int timeSkipCooldownTicks() {
        return read(TIME_SKIP_COOLDOWN);
    }

    /**
     * The fixed cooldown: what 1.16 always set (KingCrimsonTimeErase.onTaskStopped stored the dynamic value in a
     * local it never used). Now what an erase waits with timeEraseDynamicCooldown off, or when its length is not known.
     */
    public static int timeEraseCooldownTicks() {
        return read(TIME_ERASE_COOLDOWN);
    }

    /**
     * The cooldown of an erase that ran so many ticks. Owner 2026-11-11: "時停和時刪在正常cd模式下邏輯趨同，包括cd隨發動時間的
     * 延長". The core's time stop waits 3 ticks for each tick it ran; the erase's own settings say the same with their
     * own numbers: the ticks it ran times timeEraseDynamicCooldownMultiplier (1), at least
     * timeEraseDynamicMinimumCooldown (120). With the setting off, or for a length that is not known (negative): the
     * fixed timeEraseCooldown.
     */
    public static int timeEraseCooldownTicks(int ranTicks) {
        if (ranTicks < 0 || !read(TIME_ERASE_DYNAMIC_COOLDOWN)) {
            return timeEraseCooldownTicks();
        }
        return Math.max(read(TIME_ERASE_DYNAMIC_MINIMUM_COOLDOWN),
                (int) Math.round(ranTicks * read(TIME_ERASE_DYNAMIC_COOLDOWN_MULTIPLIER)));
    }

    /**
     * The configured ticks in place of a spec-built cooldown. A built cooldown of 0 (creative, the no-cooldown
     * setting, or Resolve clearing a plain Stand cooldown) stays 0.
     */
    public static int configuredCooldown(int builtCooldown, int configuredTicks) {
        return builtCooldown > 0 ? Math.max(configuredTicks, 0) : 0;
    }

    /** Whether allowedProjectiles lists the item. */
    public static boolean allowsProjectile(Item item) {
        String raw = SPEC.isLoaded() ? ALLOWED_PROJECTILES.get() : ALLOWED_PROJECTILES.getDefault();
        ParsedProjectiles parsed = parsedProjectiles;
        if (parsed == null || !parsed.raw().equals(raw)) {
            parsed = new ParsedProjectiles(raw, parseIds(raw));
            parsedProjectiles = parsed;
        }
        return parsed.ids().contains(BuiltInRegistries.ITEM.getKey(item));
    }

    static Set<ResourceLocation> parseIds(String raw) {
        Set<ResourceLocation> ids = new HashSet<>();
        String coreNamespace = ModItems.KNIFE.getId().getNamespace();
        for (String entry : raw.split(",")) {
            ResourceLocation id = ResourceLocation.tryParse(entry.trim().toLowerCase(Locale.ROOT));
            if (id == null) {
                continue;
            }
            if (id.getNamespace().equals(LEGACY_CORE_NAMESPACE)) {
                id = ResourceLocation.fromNamespaceAndPath(coreNamespace, id.getPath());
            }
            ids.add(id);
        }
        return Set.copyOf(ids);
    }

    public static boolean reflexErase() {
        return read(REFLEX_ERASE);
    }

    public static float reflexEraseThreshold() {
        return (float) read(REFLEX_ERASE_THRESHOLD);
    }

    public static float reflexEraseStamina() {
        return (float) read(REFLEX_ERASE_STAMINA);
    }

    public static int reflexEraseTicks() {
        return secondsToTicks(read(REFLEX_ERASE_SECONDS));
    }

    public static int reflexEraseCooldownTicks() {
        return read(REFLEX_ERASE_COOLDOWN_SECONDS) * 20;
    }

    public static float lastChanceEraseHealthShare() {
        return (float) read(LAST_CHANCE_ERASE_HEALTH_SHARE);
    }

    public static int lastChanceEraseTicks() {
        return secondsToTicks(read(LAST_CHANCE_ERASE_SECONDS));
    }

    public static int lastChanceEraseCooldownTicks() {
        return read(LAST_CHANCE_ERASE_COOLDOWN_SECONDS) * 20;
    }

    public static int timeEraseStepMaxDistance() {
        return read(TIME_ERASE_STEP_MAX_DISTANCE);
    }

    public static float bloodThrowHealthCost() {
        return (float) read(BLOOD_THROW_HEALTH_COST);
    }

    public static float bloodThrowStamina() {
        return (float) read(BLOOD_THROW_STAMINA);
    }

    public static int bloodThrowCooldownTicks() {
        return read(BLOOD_THROW_COOLDOWN_SECONDS) * 20;
    }

    private static int secondsToTicks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0D));
    }

    private static boolean read(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static int read(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static double read(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private record ParsedProjectiles(String raw, Set<ResourceLocation> ids) {}

    private record ParsedIds(String raw, Set<ResourceLocation> ids) {}
}
