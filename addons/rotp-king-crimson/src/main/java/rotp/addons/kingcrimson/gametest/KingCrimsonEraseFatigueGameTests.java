package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.state.KingCrimsonEraseStamina;
import rotp.addons.kingcrimson.state.KingCrimsonReflexErase;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState.EraseKind;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseStepService;
import rotp.core.api.stand.StandAbilityFatigue;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The erase and the core's fatigue (StandAbilityFatigue: in the mode with no cooldowns a player waits for nothing, and
 * a use while the cooldown it skipped would still run costs extra stamina). The owner, 2026-11-11: the erase's repeats
 * are priced by the core, and the reflex is a small practical skill. The erase sets its cooldown
 * at its end, so it meets the fatigue as the core's time stop does: the repeat is paid as it begins, and its end adds
 * the cooldown that the mode skips, once. The owner, 2026-11-12: the reflex waits for nothing in that mode and pays the
 * practical repeat; the last chance keeps its own wait in both modes, so it skips nothing, adds no fatigue and is no
 * practical skill.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEraseFatigueGameTests {
    private static final String ERASE = "kingcrimson_timeerase";
    private static final String STEP = KingCrimsonTimeEraseStepService.ABILITY;
    private static final String REFLEX = KingCrimsonReflexErase.REFLEX;
    private static final String LAST_CHANCE = KingCrimsonReflexErase.LAST_CHANCE;

    private KingCrimsonEraseFatigueGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_erase_fatigue")
    public static void inTheModeWithNoCooldownsAnErasePaysTheCoresRepeatAsItBeginsAndAddsItsFatigueAsItEnds(GameTestHelper helper) {
        Scene scene = new Scene(helper, 72, 16);
        // The longest erase is five seconds here: 100 ticks at Resolve level 4.
        int longest = KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.get();
        scene.fixture.onClose(() -> KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(longest));
        KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(5);
        ServerPlayer tired = scene.player("kc-fatigue-tired", 2.5D, true);
        ServerPlayer fresh = scene.player("kc-fatigue-fresh", 5.5D, true);
        ServerPlayer stepper = scene.player("kc-fatigue-stepper", 9.5D, true);
        ServerPlayer waiter = scene.player("kc-fatigue-waiter", 13.5D, false);
        StandPower power = StandPower.get(tired);
        StandPower freshPower = StandPower.get(fresh);
        Ability key = power.getAbility(ERASE);
        float start = power.getStaminaCostShare(
                StandAbilityStamina.effectiveCost(power, KingCrimsonPhase5Spec.TIME_ERASE_ACTIVATION_STAMINA));
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(scene.fixture.holdErase(tired).isActionSuccess, "Setup: the first erase did not start");
            // The step works out its own wait: with cooldowns it is stored, in the mode with none the core is told.
            StandPower stepped = StandPower.get(stepper);
            StandPower waited = StandPower.get(waiter);
            helper.assertTrue(scene.step(stepper) && scene.step(waiter) && waited.getAbilityCooldown(STEP) >= 20,
                    "Setup: a step did not go off, or with cooldowns it waits " + waited.getAbilityCooldown(STEP) + " ticks");
            scene.rule(Math.abs(StandAbilityFatigue.getFatigueTicks(stepped, STEP) - waited.getAbilityCooldown(STEP)) < 0.01F
                            && stepped.getAbilityCooldown(STEP) == 0 && stepped.getAbilityCooldown(ERASE) == 0
                            && StandAbilityFatigue.getFatigueTicks(waited, STEP) == 0.0F,
                    "after the same step, one with cooldowns waits " + waited.getAbilityCooldown(STEP) + " ticks and has "
                            + StandAbilityFatigue.getFatigueTicks(waited, STEP) + " of fatigue; one in the mode with none waits "
                            + stepped.getAbilityCooldown(STEP) + " (Time Erase " + stepped.getAbilityCooldown(ERASE)
                            + ") and his step has " + StandAbilityFatigue.getFatigueTicks(stepped, STEP)
                            + " ticks of fatigue: not the wait he skipped");
        }));
        int[] skipped = new int[1];
        helper.runAfterDelay(30, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(tired), "Setup: the first erase is over before its key was let go");
            int elapsed = KingCrimsonEraseFixture.state(tired).elapsedWorldTicks(helper.getLevel().getGameTime());
            scene.fixture.release(tired, KingCrimsonEraseFixture.ERASE_KEY);
            int normal = key.getNormalCooldown(power, elapsed);
            skipped[0] = power.getAbilityCooldownShare(normal);
            float fatigue = StandAbilityFatigue.getFatigueTicks(power, ERASE);
            // 25 ticks are under the shortest cooldown of 120.
            scene.rule(normal == 120 && Math.abs(fatigue - skipped[0]) < 0.01F && StandAbilityFatigue.getRepeats(power, ERASE) == 0
                            && power.getAbilityCooldown(ERASE) == 0,
                    "after an erase of " + elapsed + " ticks in the mode with no cooldowns its key waits "
                            + power.getAbilityCooldown(ERASE) + " ticks and has " + fatigue + " ticks of fatigue with "
                            + StandAbilityFatigue.getRepeats(power, ERASE) + " repeats: not the " + skipped[0]
                            + " ticks it skipped (the cooldown of " + normal + "), once");
        }));
        float[] bar = new float[2];
        float[] fatigueAtPress = new float[1];
        helper.runAfterDelay(36, () -> scene.fixture.guarded(() -> {
            power.setStamina(power.getMaxStamina());
            freshPower.setStamina(freshPower.getMaxStamina());
            bar[0] = power.getStamina();
            bar[1] = freshPower.getStamina();
            fatigueAtPress[0] = StandAbilityFatigue.getFatigueTicks(power, ERASE);
            helper.assertTrue(scene.fixture.holdErase(tired).isActionSuccess && scene.fixture.holdErase(fresh).isActionSuccess,
                    "Setup: the second erase, or the first of another player, did not start");
        }));
        helper.runAfterDelay(42, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(tired) && KingCrimsonEraseFixture.erasing(fresh)
                    && power.getStamina() > 100.0F, "Setup: an erase is over six ticks after its press, or the bar is empty");
            // Both began in the same tick, for the same start and upkeep: what the tired one paid on top is his repeat.
            float extra = (bar[0] - power.getStamina()) - (bar[1] - freshPower.getStamina());
            float most = power.getStaminaCostShare(StandAbilityFatigue.repeatCost(fatigueAtPress[0], skipped[0], 1, false));
            float least = power.getStaminaCostShare(
                    StandAbilityFatigue.repeatCost(StandAbilityFatigue.getFatigueTicks(power, ERASE), skipped[0], 1, false));
            scene.rule(StandAbilityFatigue.getRepeats(power, ERASE) == 1 && least > 20.0F && extra > least - 0.01F
                            && extra < most + 0.01F,
                    "an erase begun " + (skipped[0] - fatigueAtPress[0]) + " ticks after the last one ended, with "
                            + fatigueAtPress[0] + " ticks of its fatigue left, cost " + extra + " more than a first erase as"
                            + " it began, with " + StandAbilityFatigue.getRepeats(power, ERASE) + " repeats counted: not the"
                            + " core's first repeat, between " + least + " and " + most);
            scene.rule(StandAbilityFatigue.getRepeats(freshPower, ERASE) == 0
                            && StandAbilityFatigue.getFatigueTicks(freshPower, ERASE) == 0.0F,
                    "a first erase that still runs has " + StandAbilityFatigue.getFatigueTicks(freshPower, ERASE)
                            + " ticks of fatigue and " + StandAbilityFatigue.getRepeats(freshPower, ERASE) + " repeats");
        }));
        helper.runAfterDelay(60, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(tired), "Setup: the second erase is over before its key was let go");
            int elapsed = KingCrimsonEraseFixture.state(tired).elapsedWorldTicks(helper.getLevel().getGameTime());
            float left = StandAbilityFatigue.getFatigueTicks(power, ERASE);
            float before = power.getStamina();
            scene.fixture.release(tired, KingCrimsonEraseFixture.ERASE_KEY);
            float back = power.getStamina() - before;
            float due = KingCrimsonEraseStamina.unusedStart(start, 100, elapsed);
            float stacked = StandAbilityFatigue.stackedFatigue(left,
                    power.getAbilityCooldownShare(key.getNormalCooldown(power, elapsed)));
            // The repeat was paid as the erase began: its end pays nothing, and adds its own cooldown once.
            scene.rule(Math.abs(back - due) < 0.01F && StandAbilityFatigue.getRepeats(power, ERASE) == 1,
                    "the end of the second erase moved the bar by " + back + " with " + StandAbilityFatigue.getRepeats(power, ERASE)
                            + " repeats counted: not by the " + due + " it gives back alone, with the one repeat of its start");
            scene.rule(left > 20.0F && Math.abs(StandAbilityFatigue.getFatigueTicks(power, ERASE) - stacked) < 0.01F,
                    "with " + left + " ticks of fatigue left, the end of the second erase left "
                            + StandAbilityFatigue.getFatigueTicks(power, ERASE) + ": not " + stacked + ", its own cooldown added once");
            scene.fixture.release(fresh, KingCrimsonEraseFixture.ERASE_KEY);
            scene.finish("The erase and the core's fatigue");
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_erase_fatigue_reflex")
    public static void inTheModeWithNoCooldownsTheReflexEraseWaitsForNothingAndPaysThePracticalRepeat(GameTestHelper helper) {
        Scene scene = new Scene(helper, 12, 14);
        boolean on = KingCrimsonConfig.REFLEX_ERASE.get();
        scene.fixture.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(on));
        KingCrimsonConfig.REFLEX_ERASE.set(true);
        ServerPlayer player = scene.player("kc-fatigue-reflex", 2.5D, true);
        ServerPlayer waiter = scene.player("kc-fatigue-waits", 6.5D, false);
        ServerPlayer switched = scene.player("kc-fatigue-switched", 10.5D, true);
        StandPower power = StandPower.get(player);
        StandPower waited = StandPower.get(waiter);
        StandPower switchedPower = StandPower.get(switched);
        float[] own = new float[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            scene.rule(power.getAbility(REFLEX).isPracticalSkill(power) && !power.getAbility(ERASE).isPracticalSkill(power)
                            && !power.getAbility(STEP).isPracticalSkill(power),
                    "the reflex erase is " + (power.getAbility(REFLEX).isPracticalSkill(power) ? "a" : "no")
                            + " small practical skill for the core's fatigue; the erase and its step must be none");
            scene.restore(player);
            scene.restore(waiter);
            float bar = power.getStamina();
            boolean landed = scene.hit(player, 6.0F);
            boolean waiterLanded = scene.hit(waiter, 6.0F);
            helper.assertTrue(!landed && !waiterLanded && scene.inReflex(player) && scene.inReflex(waiter),
                    "Setup: a hit of 6 on 20 health set off no reflex erase, in the mode with no cooldowns or with them");
            own[0] = bar - power.getStamina();
            int wait = power.getAbilityCooldownShare(KingCrimsonConfig.reflexEraseCooldownTicks());
            float fatigue = StandAbilityFatigue.getFatigueTicks(power, REFLEX);
            helper.assertTrue(own[0] > 1.0F && wait >= StandAbilityFatigue.TRIVIAL_COOLDOWN_TICKS,
                    "Setup: a first reflex erase took " + own[0] + " stamina, and its own wait is " + wait + " ticks");
            scene.rule(power.getAbilityCooldown(REFLEX) == 0 && Math.abs(fatigue - wait) < 0.01F
                            && StandAbilityFatigue.getRepeats(power, REFLEX) == 0,
                    "in the mode with no cooldowns a first reflex erase leaves its key a wait of "
                            + power.getAbilityCooldown(REFLEX) + " ticks and " + fatigue + " ticks of fatigue with "
                            + StandAbilityFatigue.getRepeats(power, REFLEX) + " repeats: not no wait, the " + wait
                            + " ticks it skipped as fatigue, and no repeat yet");
            int waitedShare = waited.getAbilityCooldownShare(KingCrimsonConfig.reflexEraseCooldownTicks());
            scene.rule(waited.getAbilityCooldown(REFLEX) == waitedShare
                            && StandAbilityFatigue.getFatigueTicks(waited, REFLEX) == 0.0F,
                    "with cooldowns a reflex erase leaves its key a wait of " + waited.getAbilityCooldown(REFLEX)
                            + " ticks (its own is " + waitedShare + ") and "
                            + StandAbilityFatigue.getFatigueTicks(waited, REFLEX) + " ticks of fatigue");
            KingCrimsonTemporalStateService.terminate(player);
            KingCrimsonTemporalStateService.terminate(waiter);
            // A wait left from the mode with cooldowns, as when the setting is switched on in the middle of it.
            StandAbilityFatigue.withoutFatigue(
                    () -> switchedPower.setAbilityCooldown(REFLEX, KingCrimsonConfig.reflexEraseCooldownTicks()));
        }));
        helper.runAfterDelay(6, () -> scene.fixture.guarded(() -> {
            scene.restore(player);
            scene.restore(waiter);
            scene.restore(switched);
            float left = StandAbilityFatigue.getFatigueTicks(power, REFLEX);
            int wait = power.getAbilityCooldownShare(KingCrimsonConfig.reflexEraseCooldownTicks());
            float bar = power.getStamina();
            boolean landed = scene.hit(player, 6.0F);
            boolean waiterLanded = scene.hit(waiter, 6.0F);
            float more = bar - power.getStamina() - own[0];
            // The first repeat of a small practical skill: a sixty-fourth of the full price of what it skips.
            float repeat = power.getStaminaCostShare(StandAbilityFatigue.repeatCost(left, wait, 1, true));
            scene.rule(!landed && player.getHealth() == player.getMaxHealth() && scene.inReflex(player),
                    "in the mode with no cooldowns a second heavy hit, four ticks after the first, "
                            + (landed ? "landed" : "was escaped") + " (health " + player.getHealth()
                            + "): the reflex waits for nothing there");
            scene.rule(repeat > 1.0F && Math.abs(more - repeat) < 0.05F && StandAbilityFatigue.getRepeats(power, REFLEX) == 1,
                    "the second reflex erase cost " + more + " stamina more than the first, with "
                            + StandAbilityFatigue.getRepeats(power, REFLEX) + " repeats counted: not the " + repeat
                            + " of the core's first repeat of a small practical skill, with " + left
                            + " ticks of fatigue left");
            float stacked = StandAbilityFatigue.stackedFatigue(left, wait);
            scene.rule(power.getAbilityCooldown(REFLEX) == 0
                            && Math.abs(StandAbilityFatigue.getFatigueTicks(power, REFLEX) - stacked) < 0.01F,
                    "the second reflex erase left its key a wait of " + power.getAbilityCooldown(REFLEX) + " ticks and "
                            + StandAbilityFatigue.getFatigueTicks(power, REFLEX) + " ticks of fatigue: not no wait and "
                            + stacked + ", the wait it skipped added once more");
            scene.rule(waiterLanded && waiter.getHealth() == 14.0F && KingCrimsonEraseFixture.state(waiter).isIdle(),
                    "with cooldowns a second heavy hit inside the reflex's wait "
                            + (waiterLanded ? "landed" : "was escaped") + " (health " + waiter.getHealth()
                            + "): there the wait holds");
            helper.assertTrue(switchedPower.getAbilityCooldown(REFLEX) > 0,
                    "Setup: the wait left from the mode with cooldowns is gone before the hit");
            boolean switchedLanded = scene.hit(switched, 6.0F);
            scene.rule(!switchedLanded && scene.inReflex(switched),
                    "in the mode with no cooldowns a heavy hit on a holder whose reflex still has "
                            + switchedPower.getAbilityCooldown(REFLEX) + " ticks of a wait left from the mode with cooldowns "
                            + (switchedLanded ? "landed" : "was escaped") + ": no wait holds the reflex back there");
            scene.end(player);
            scene.end(waiter);
            scene.end(switched);
            scene.finish("The reflex erase and the core's fatigue");
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_erase_fatigue_last_chance")
    public static void inTheModeWithNoCooldownsTheLastChanceEraseKeepsItsOwnWaitAndIsNoPracticalSkill(GameTestHelper helper) {
        Scene scene = new Scene(helper, 12, 10);
        boolean on = KingCrimsonConfig.REFLEX_ERASE.get();
        scene.fixture.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(on));
        KingCrimsonConfig.REFLEX_ERASE.set(true);
        ServerPlayer player = scene.player("kc-fatigue-last", 2.5D, true);
        ServerPlayer fresh = scene.player("kc-fatigue-last-fresh", 6.5D, true);
        StandPower power = StandPower.get(player);
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            scene.rule(!power.getAbility(LAST_CHANCE).isPracticalSkill(power),
                    "the last-chance erase is a small practical skill for the core's fatigue: it keeps its wait in the mode"
                            + " with no cooldowns, so it has no repeat to price");
            scene.restore(player);
            float bar = power.getStamina();
            boolean landed = scene.hit(player, 1000.0F);
            KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(player);
            helper.assertTrue(!landed && player.isAlive() && KingCrimsonEraseFixture.erasing(player)
                            && state.eraseKind() == EraseKind.LAST_CHANCE,
                    "Setup: in the mode with no cooldowns a hit that would kill set off no last-chance erase");
            int wait = power.getAbilityCooldownShare(KingCrimsonConfig.lastChanceEraseCooldownTicks());
            scene.rule(wait > 0 && power.getAbilityCooldown(LAST_CHANCE) == wait
                            && StandAbilityFatigue.getFatigueTicks(power, LAST_CHANCE) == 0.0F && power.getStamina() == bar,
                    "in the mode with no cooldowns the last chance waits " + power.getAbilityCooldown(LAST_CHANCE)
                            + " ticks (its own wait is " + wait + "), has "
                            + StandAbilityFatigue.getFatigueTicks(power, LAST_CHANCE) + " ticks of fatigue and took "
                            + (bar - power.getStamina()) + " stamina: its wait is served there too, so it skips nothing,"
                            + " and it is free");
            KingCrimsonTemporalStateService.terminate(player);
        }));
        helper.runAfterDelay(6, () -> scene.fixture.guarded(() -> {
            // Nearly dead, and a hit too small for the reflex: the last chance alone could answer it.
            scene.restore(player);
            scene.restore(fresh);
            player.setHealth(1.5F);
            fresh.setHealth(1.5F);
            boolean freshLanded = scene.hit(fresh, 1.0F);
            helper.assertTrue(!freshLanded && fresh.getHealth() == 1.5F && KingCrimsonEraseFixture.erasing(fresh)
                            && KingCrimsonEraseFixture.state(fresh).eraseKind() == EraseKind.LAST_CHANCE,
                    "Setup: a hit of 1 on a holder with 1.5 of 20 health, whose last chance is ready, set off no"
                            + " last-chance erase");
            boolean landed = scene.hit(player, 1.0F);
            scene.rule(landed && player.getHealth() == 0.5F && KingCrimsonEraseFixture.state(player).isIdle(),
                    "in the mode with no cooldowns a hit of 1 on a holder with 1.5 health, four ticks after his last"
                            + " chance, " + (landed ? "landed" : "was escaped") + " (health " + player.getHealth()
                            + "): its wait holds there too, it is no second life to repeat");
            scene.end(player);
            scene.end(fresh);
            scene.finish("The last-chance erase and the core's fatigue");
        }));
    }

    /** Players with King Crimson at Resolve level 4 in a row along the floor, each in the mode he is given. */
    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final List<String> broken = new ArrayList<>();

        private Scene(GameTestHelper helper, int length, int width) {
            this.helper = helper;
            fixture = new KingCrimsonEraseFixture(helper);
            fixture.floor(length, width);
        }

        private ServerPlayer player(String name, double z, boolean noCooldowns) {
            ServerPlayer player = fixture.player(name, 2.5D, 2.0D, z);
            fixture.kingCrimson(player, 4);
            // The mode with no cooldowns is a player's own setting.
            player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = noCooldowns;
            player.moveTo(player.getX(), player.getY(), player.getZ(), -90.0F, 0.0F);
            player.setYHeadRot(-90.0F);
            return player;
        }

        /** The step's key, looking along the floor at nothing. */
        private boolean step(ServerPlayer player) {
            return fixture.press(player, STEP, KingCrimsonEraseFixture.OTHER_KEY, InputMethod.CLICK, false).isActionSuccess;
        }

        /** A hit that armour and absorption take from, and that no difficulty scales. */
        private boolean hit(ServerPlayer player, float amount) {
            player.invulnerableTime = 0;
            DamageSource cactus = player.damageSources().cactus();
            return player.hurt(cactus, amount);
        }

        private void restore(ServerPlayer player) {
            StandPower power = StandPower.get(player);
            player.setHealth(player.getMaxHealth());
            power.setStamina(power.getMaxStamina());
        }

        private boolean inReflex(ServerPlayer player) {
            return KingCrimsonEraseFixture.erasing(player)
                    && KingCrimsonEraseFixture.state(player).eraseKind() == EraseKind.REFLEX;
        }

        private void end(ServerPlayer player) {
            if (!KingCrimsonEraseFixture.state(player).isIdle()) {
                KingCrimsonTemporalStateService.terminate(player);
            }
        }

        private void rule(boolean holds, String broke) {
            if (!holds) {
                broken.add(broke);
            }
        }

        private void finish(String title) {
            // The report of a failed test is a book: a page holds so much.
            String told = title + ": " + String.join("; ", broken);
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(), told.length() > 900 ? told.substring(0, 900) : told));
            fixture.succeed();
        }
    }
}
