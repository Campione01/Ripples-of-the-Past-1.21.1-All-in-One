package rotp.addons.metallica.gametest;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.entity.MetallicaStandEntity;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 Metallica kept StandEntity.offsetDefault, StandRelativeOffset.withYOffset(-0.75, 0.2, -0.75): at view yaw 0
 * the idle Stand stood 0.75 to the right of the view, 0.75 behind it and 0.2 above its user's feet, the side setting
 * LEFT mirrored it, and a sneaking user with no task brought it down to the feet.
 */
@GameTestHolder(MetallicaAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MetallicaIdleGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double EPS = 1.0E-4D;

    private MetallicaIdleGameTests() {}

    @GameTest(template = "empty", batch = "metallica_idle", timeoutTicks = 20)
    public static void theIdleStandKeepsThe116DefaultPlace(GameTestHelper helper) {
        ServerPlayer owner = MetallicaNoCapGameTests.makeServerPlayer(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 8, 1)));
        StandPower power = null;
        try {
            owner.setNoGravity(true);
            owner.setGameMode(GameType.SURVIVAL);
            // the summoned Stand keeps a reference to this attachment
            PlayerClientBroadcastedSettings settings =
                    owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
            settings.standSide = HumanoidArm.RIGHT;
            power = PowerClass.STAND.attachGet(owner);
            power.setStand(MetallicaAddon.METALLICA.get());
            power.skipProgression();
            helper.assertTrue(MetallicaAddon.METALLICA.get().summon(owner, power)
                            && power.getSummonedStandEntity() instanceof MetallicaStandEntity,
                    "Production Metallica summon failed");
            MetallicaStandEntity stand = (MetallicaStandEntity) power.getSummonedStandEntity();
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode() && stand.offsetFromUser.isIdle(),
                    "setup: the summoned Metallica is not an idle full Stand following its user");
            helper.assertTrue(stand.Y_OFFSET == 0.2D,
                    "1.16 Metallica's default y, which its tasks used, was 0.2: Y_OFFSET " + stand.Y_OFFSET);

            owner.setYRot(0.0F);
            owner.setYHeadRot(0.0F);
            Vec3 standing = idleAt(helper, owner, stand, -0.75D, 0.2D, -0.75D, "a standing user at view yaw 0");
            settings.standSide = HumanoidArm.LEFT;
            idleAt(helper, owner, stand, 0.75D, 0.2D, -0.75D, "view yaw 0, side LEFT");
            settings.standSide = HumanoidArm.RIGHT;
            owner.setShiftKeyDown(true);
            idleAt(helper, owner, stand, -0.75D, 0.0D, -0.75D, "a sneaking user with no task at view yaw 0");
            owner.setShiftKeyDown(false);
            LOGGER.info("R770_METALLICA_IDLE_PASS default offset={}", standing);
        }
        finally {
            if (power != null && power.hasPower() && power.isSummoned()) {
                MetallicaAddon.METALLICA.get().forceUnsummon(owner, power);
            }
            owner.discard();
        }
        helper.succeed();
    }

    // The owner's rule of 2026-11-05 (13.png), against the port's check for a Stand out: Invisibility goes on through a
    // recall, at the same stamina, and ends when its user loses the Stand.
    @GameTest(template = "empty", batch = "metallica_recall", timeoutTicks = 20)
    public static void invisibilityGoesOnThroughARecall(GameTestHelper helper) {
        ServerPlayer owner = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "metallica-recall"));
        StandPower power = PowerClass.STAND.attachGet(owner);
        try {
            power.setStand(MetallicaAddon.METALLICA.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(MetallicaAddon.METALLICA.get().summon(owner, power), "Fixture: the summon failed");
            var state = MetallicaAddon.invisibility(owner);
            helper.assertTrue(state.activate(owner) && state.active(), "Fixture: Invisibility did not start");

            MetallicaAddon.METALLICA.get().forceUnsummon(owner, power);
            helper.assertTrue(MetallicaAddon.controller(owner) == null, "Fixture: the Stand is still out");
            state.tickServer(owner);
            helper.assertTrue(state.active() && owner.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY),
                    "Invisibility ended with the recall: " + state.lastClearReason());

            power.setStand(null);
            state.tickServer(owner);
            helper.assertTrue(!state.active(), "Invisibility must end when its user loses the Stand");
            LOGGER.info("R772_METALLICA_RECALL_PASS invisibility");
            helper.succeed();
        }
        finally {
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            owner.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            owner.discard();
        }
    }

    private static Vec3 idleAt(GameTestHelper helper, ServerPlayer owner, MetallicaStandEntity stand,
            double dx, double dy, double dz, String when) {
        stand.updatePosition(owner);
        Vec3 actual = stand.position().subtract(owner.position());
        helper.assertTrue(Math.abs(actual.x - dx) < EPS && Math.abs(actual.y - dy) < EPS
                        && Math.abs(actual.z - dz) < EPS,
                "1.16 put the idle Metallica at its user's feet + (" + dx + ", " + dy + ", " + dz + ") with " + when
                        + ", got " + actual + " (offset " + stand.offsetFromUser.getRelativeOffset() + ")");
        return actual;
    }
}
