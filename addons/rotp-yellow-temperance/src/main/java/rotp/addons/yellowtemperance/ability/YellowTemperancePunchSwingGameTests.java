package rotp.addons.yellowtemperance.ability;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 yellow_temperance_punch was swingHand() with no hold: InputHandler.actionSwingsHand swung the user's main arm
 * on the press that went off. The controller entity is invisible, so that swing is all a left click shows.
 */
@GameTestHolder(YellowTemperanceAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YellowTemperancePunchSwingGameTests {
    private YellowTemperancePunchSwingGameTests() {}

    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_punch_swing",
            timeoutTicks = 60)
    public static void theLeftClickPunchSwingsTheUsersMainArm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "YTSwing"));
        Vec3 feet = YellowTemperanceDisguiseGameTests.sceneFeet(helper);
        player.setPos(feet.x, feet.y, feet.z);
        player.setNoGravity(true);
        level.addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        try {
            power.setStand(YellowTemperanceAddon.YELLOW_TEMPERANCE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(YellowTemperanceAddon.YELLOW_TEMPERANCE.get().summon(player, power),
                    "Fixture: Yellow Temperance's summon failed");
            YellowTemperanceControllerEntity stand = YellowTemperanceAddon.controller(player);
            helper.assertTrue(stand != null, "Fixture: the summon made no Yellow Temperance controller");
            YellowTemperanceDisguiseGameTests.assertTicks(helper, stand);
            helper.runAfterDelay(stand.summonLockTicks + 2, () -> {
                try {
                    press(helper, level, player, power, stand);
                }
                finally {
                    release(level, player, power);
                }
                LogUtils.getLogger().info("R772_YT_SWING_PASS main_arm");
                helper.succeed();
            });
        }
        catch (RuntimeException | Error failure) {
            release(level, player, power);
            throw failure;
        }
    }

    private static void press(GameTestHelper helper, ServerLevel level, FakePlayer player, StandPower power,
            YellowTemperanceControllerEntity stand) {
        var punch = power.getAbility(YellowTemperanceAddon.PUNCH_NAME);
        helper.assertTrue(punch instanceof YellowTemperancePunchAbility && punch.checkConditions(power).isPositive(),
                "Fixture: the registered punch refused the press");
        helper.assertTrue(!player.swinging, "Fixture: the user swings before the press");
        BufferingState buffering = BufferingState.clickOnly();
        punch.onKeyPress(level, player, null, InputMethod.CLICK, 0.0F, buffering);
        helper.assertTrue(buffering.isActionSuccess, "Fixture: the server did not accept the press");
        EntityActionInstance action = stand.getCurStandAction();
        helper.assertTrue(action instanceof YellowTemperancePunchAbility.YellowPunchAction,
                "Fixture: the press set no punch on the controller, got " + action);
        helper.assertTrue(player.swinging && player.swingingArm == InteractionHand.MAIN_HAND,
                "1.16 swung the user's main arm on the press that went off; swinging=" + player.swinging
                        + " arm=" + player.swingingArm);
        player.swinging = false;
        action.restoreInputLifecycle(action.captureInputLifecycle(), null);
        helper.assertTrue(!player.swinging,
                "A failed input that puts the punch back is no new press, but the user's arm swung again");
    }

    private static void release(ServerLevel level, FakePlayer player, StandPower power) {
        if (power.hasPower() && power.isSummoned()) {
            power.getPowerType().forceUnsummon(player, power);
        }
        if (!player.isRemoved()) {
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }
}
