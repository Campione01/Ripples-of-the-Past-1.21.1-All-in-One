package rotp.addons.whitealbum.ability;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.whitealbum.WhiteAlbumAddon;
import rotp.addons.whitealbum.entity.WhiteAlbumControllerEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 wa_punch was swingHand() with no hold: InputHandler.actionSwingsHand swung the user's main arm on the press
 * that went off. The controller entity is invisible and the suit is drawn on the user, so that swing is all a
 * left click shows.
 */
@GameTestHolder(WhiteAlbumAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WhiteAlbumPunchSwingGameTests {
    private WhiteAlbumPunchSwingGameTests() {}

    @GameTest(template = "empty", templateNamespace = WhiteAlbumAddon.MOD_ID, batch = "wa_punch_swing",
            timeoutTicks = 60)
    public static void theLeftClickPunchSwingsTheUsersMainArm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "WASwing"));
        BlockPos origin = helper.absolutePos(new BlockPos(1, 4, 1));
        player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        player.setNoGravity(true);
        level.addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        try {
            power.setStand(WhiteAlbumAddon.WHITE_ALBUM.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(WhiteAlbumAddon.WHITE_ALBUM.get().summon(player, power),
                    "Fixture: White Album's summon failed");
            WhiteAlbumControllerEntity stand = WhiteAlbumAddon.controller(player);
            helper.assertTrue(stand != null, "Fixture: the summon made no White Album controller");
            helper.runAfterDelay(stand.summonLockTicks + 2, () -> {
                try {
                    press(helper, level, player, power, stand);
                }
                finally {
                    release(level, player, power);
                }
                LogUtils.getLogger().info("R772_WA_SWING_PASS main_arm");
                helper.succeed();
            });
        }
        catch (RuntimeException | Error failure) {
            release(level, player, power);
            throw failure;
        }
    }

    // The owner's rule of 2026-11-05 (13.png), against 1.16, whose flags died with the Stand entity: both toggles go on
    // through a recall, the next summon takes them over, and they end with the loss of the Stand.
    @GameTest(template = "empty", templateNamespace = WhiteAlbumAddon.MOD_ID, batch = "wa_recall", timeoutTicks = 60)
    public static void theTogglesGoOnThroughARecall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "WARecall"));
        BlockPos origin = helper.absolutePos(new BlockPos(1, 4, 1));
        player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        player.setNoGravity(true);
        level.addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        try {
            power.setStand(WhiteAlbumAddon.WHITE_ALBUM.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(WhiteAlbumAddon.WHITE_ALBUM.get().summon(player, power), "Fixture: the summon failed");
            WhiteAlbumControllerEntity first = WhiteAlbumAddon.controller(player);
            first.setGentlyWeepsActive(true);
            first.setFreezeFloorActive(true);

            WhiteAlbumAddon.WHITE_ALBUM.get().forceUnsummon(player, power);
            EventHooks.firePlayerTickPost(player);
            helper.assertTrue(WhiteAlbumControllerEntity.recalled(player) == first
                            && first.isGentlyWeepsActive() && first.isFreezeFloorActive(),
                    "The toggles ended with the recall");

            helper.assertTrue(WhiteAlbumAddon.WHITE_ALBUM.get().summon(player, power), "Fixture: the next summon failed");
            WhiteAlbumControllerEntity next = WhiteAlbumAddon.controller(player);
            helper.assertTrue(next != first && next.isGentlyWeepsActive() && next.isFreezeFloorActive()
                            && WhiteAlbumControllerEntity.recalled(player) == null,
                    "The next summon must take the toggles over, so their own keys can end them");

            WhiteAlbumAddon.WHITE_ALBUM.get().forceUnsummon(player, power);
            power.setStamina(1.0F);
            EventHooks.firePlayerTickPost(player);
            helper.assertTrue(!next.isGentlyWeepsActive() && !next.isFreezeFloorActive()
                            && WhiteAlbumControllerEntity.recalled(player) == null,
                    "The recalled toggles must still end when the stamina runs out");

            power.setStamina(power.getMaxStamina());
            helper.assertTrue(WhiteAlbumAddon.WHITE_ALBUM.get().summon(player, power), "Fixture: the third summon failed");
            WhiteAlbumAddon.controller(player).setGentlyWeepsActive(true);
            WhiteAlbumAddon.WHITE_ALBUM.get().forceUnsummon(player, power);
            power.setStand(null);
            helper.assertTrue(WhiteAlbumControllerEntity.recalled(player) == null,
                    "The toggles must end when the user loses the Stand");
            LogUtils.getLogger().info("R772_WA_RECALL_PASS toggles");
            helper.succeed();
        }
        finally {
            release(level, player, power);
        }
    }

    private static void press(GameTestHelper helper, ServerLevel level, FakePlayer player, StandPower power,
            WhiteAlbumControllerEntity stand) {
        var punch = power.getAbility(WhiteAlbumAddon.PUNCH_NAME);
        helper.assertTrue(punch instanceof WhiteAlbumPunchAbility && punch.checkConditions(power).isPositive(),
                "Fixture: the registered punch refused the press");
        helper.assertTrue(!player.swinging, "Fixture: the user swings before the press");
        BufferingState buffering = BufferingState.clickOnly();
        punch.onKeyPress(level, player, null, InputMethod.CLICK, 0.0F, buffering);
        helper.assertTrue(buffering.isActionSuccess, "Fixture: the server did not accept the press");
        EntityActionInstance action = stand.getCurStandAction();
        helper.assertTrue(action instanceof WhiteAlbumPunchAbility.WhiteAlbumPunch,
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
