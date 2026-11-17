package rotp.addons.kingcrimson.gametest;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseStepService;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandTuning;

/**
 * The Stands settings page gives each Stand a cooldown share: every cooldown its code sets is taken in it once. A
 * cooldown King Crimson puts back from one it read back is in that share already and must not be scaled again: the
 * step's sixth of what Time Erase took at an erase's end, the erase's stretch for a user held to a slower time of his
 * own, and the barrage's cooldown as it was when an input rollback puts the held barrage back. Each is shown at a share
 * of 50 %, where a second scaling halves it.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonCooldownShareGameTests {
    private static final String ERASE = "kingcrimson_timeerase";
    private static final String STEP = KingCrimsonTimeEraseStepService.ABILITY;
    private static final String HALF_TIME_TAG = "rotp_kingcrimson_gametest_half_time";
    private static final short HOLD_KEY = 13;
    private static final int LOCK_TICKS = 77;
    private static final int LOCK_TOTAL = 90;
    private static boolean providerRegistered;

    private KingCrimsonCooldownShareGameTests() {}

    // Providers cannot be unregistered: this one answers only for an entity carrying the test's tag.
    private static synchronized void registerProvider() {
        if (!providerRegistered) {
            EntityTimeRates.register(KingCrimsonAddon.id("gametest_half_time"), (entity, channel) ->
                    channel == EntityTimeRates.Channel.BEING && entity.getTags().contains(HALF_TIME_TAG)
                            ? 0.5F : EntityTimeRates.NO_RATE);
            providerRegistered = true;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "kc_cooldown_share_step")
    public static void atHalfTheCooldownShareAnErasesEndGivesTheStepASixthOfWhatTimeEraseTook(GameTestHelper helper) {
        eraseAtHalfTheShare(helper, "kc-share-step", false);
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "kc_cooldown_share_own_time")
    public static void atHalfTheCooldownShareAUserHeldToHalfHisTimeWaitsTwiceWhatTimeEraseTook(GameTestHelper helper) {
        registerProvider();
        eraseAtHalfTheShare(helper, "kc-share-time", true);
    }

    private static void eraseAtHalfTheShare(GameTestHelper helper, String name, boolean heldToHalfHisTime) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        StandTuning tuning = StandTuning.of(power);
        // The owner's rule of 2026-11-11: the cooldown goes by the ticks the erase ran, here the shortest it gives.
        int plain = KingCrimsonPhase5Spec.TIME_ERASE_MINIMUM_COOLDOWN_TICKS;
        boolean began = false;
        try {
            helper.assertTrue(tuning != null && tuning.isDefault(), "Setup: King Crimson's rules are not at their defaults");
            halveCooldowns(tuning);
            helper.assertTrue(plain >= 60 && power.getAbilityCooldownShare(plain) == plain / 2,
                    "Setup: at a share of 50 % a cooldown of " + plain + " ticks is "
                            + power.getAbilityCooldownShare(plain) + ", not " + plain / 2);
            if (heldToHalfHisTime) {
                player.addTag(HALF_TIME_TAG);
                helper.assertTrue(EntityTimeRates.rate(EntityTimeRates.Channel.BEING, player) == 0.5F
                                && EntityTimeRates.rate(EntityTimeRates.Channel.STAND_ACTIONS, player) == 1.0F,
                        "Setup: the user is not held to half his own time with his Stand's cooldowns counted in full");
            }
            began = KingCrimsonTemporalStateService.beginTimeErase(player);
            helper.assertTrue(began, "Setup: the time erase did not start");
        } finally {
            if (!began) {
                if (tuning != null) {
                    tuning.restoreDefaults();
                }
                helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            }
        }
        helper.runAfterDelay(10, () -> {
            int erase;
            int step;
            try {
                KingCrimsonTemporalStateService.terminate(player);
                erase = power.getAbilityCooldown(ERASE);
                step = power.getAbilityCooldown(STEP);
            } finally {
                tuning.restoreDefaults();
                KingCrimsonTemporalStateService.terminate(player);
                helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            }
            // In the share Time Erase takes half; a user held to half his own time waits twice that, in his time.
            int expected = heldToHalfHisTime ? plain : plain / 2;
            helper.assertTrue(erase == expected, "A cooldown share of 50 %"
                    + (heldToHalfHisTime ? ", its user held to half his own time" : "") + ": after an erase Time Erase waits "
                    + erase + " ticks, not " + expected);
            helper.assertTrue(step == Math.round(expected / 6.0F), "A cooldown share of 50 %: after an erase that left "
                    + "Time Erase " + erase + " ticks the step waits " + step + ", not a sixth of it, "
                    + Math.round(expected / 6.0F));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_cooldown_share_barrage")
    public static void aHeldBarragePutBackByAnInputRollbackHasTheCooldownItHad(GameTestHelper helper) {
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        try (var scene = new KingCrimsonGroundGameTestFixture(helper, "share/barrage", 1)) {
            StandTuning tuning = StandTuning.of(scene.power);
            scene.premise(tuning != null && tuning.isDefault(), "King Crimson's rules are at their defaults");
            halveCooldowns(tuning);
            try {
                scene.user.setHealth(60.0F);
                Ability base = scene.power.getAbility("barrage");
                var selected = scene.power.updateAvailableMoves().getContextVariationContainer(base);
                scene.premise(selected != null && AbilityInput.withConditionCheck(selected, scene.user, InputMethod.HOLD),
                        "The barrage is admitted");
                Ability barrage = selected.ability;
                BufferingState buffer = BufferingState.clickOnly();
                AbilityInput.keyPress(HOLD_KEY, barrage, scene.user, null, InputMethod.HOLD, 0, buffer, base.getAbilityId());
                EntityActionInstance action = scene.stand.getCurStandAction();
                scene.premise(buffer.isActionSuccess && action != null && action.ability == barrage, "Held barrage started");
                for (int tick = 0; action.getPhase() == ActionPhase.WINDUP && tick < 40; tick++) {
                    action._tickAction();
                }
                scene.premise(action.getPhase() == ActionPhase.PERFORM, "Barrage reached PERFORM");
                LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
                var held = component.captureTransactionSnapshot();
                // A lock another Stand's ability put on the barrage during the hold: exactly these ticks.
                scene.power.restoreAbilityCooldown(barrage.name(), LOCK_TICKS, LOCK_TOTAL);
                // What a failing press does: it replaces the held barrage, and its transaction puts the barrage back.
                component.setAction(null, SyncType.NO_SYNC);
                int ended = scene.power.getAbilityCooldown(barrage.name());
                component.rollbackFailedInputAction(held);
                scene.premise(scene.stand.getCurStandAction() == action, "The rollback put the held barrage back");
                int cooldown = scene.power.getAbilityCooldown(barrage.name());
                int total = scene.power.getAbilityCooldownTotal(barrage.name());
                checks.check(cooldown == LOCK_TICKS && total == LOCK_TOTAL, "at a cooldown share of 50 % a held barrage "
                        + "put back by an input rollback has the cooldown it had, " + LOCK_TICKS + " of " + LOCK_TOTAL
                        + " ticks: " + cooldown + " of " + total + " (its end had set " + ended + ")");
            } finally {
                tuning.restoreDefaults();
                AbilityInput.keyRelease(HOLD_KEY, scene.user);
            }
        }
        checks.finish(helper, "KC-COOLDOWN-SHARE", 1);
    }

    private static void halveCooldowns(StandTuning tuning) {
        JsonObject half = new JsonObject();
        half.addProperty(StandTuning.Percent.COOLDOWNS.nameInJson, 50);
        tuning.applyConfig(half);
    }
}
