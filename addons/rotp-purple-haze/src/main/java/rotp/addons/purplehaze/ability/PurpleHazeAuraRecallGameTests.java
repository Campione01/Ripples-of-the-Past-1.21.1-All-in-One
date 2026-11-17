package rotp.addons.purplehaze.ability;

import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.addons.purplehaze.PurpleHazeAuraEvents;
import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The owner's rule of 2026-11-06: the virus aura goes on with Purple Haze recalled. With no Stand it spreads from the
 * user, who must not catch his own virus. 1.16's rule that ends it with the Stand near its user waits for the next
 * summon, which comes out at his side.
 */
@GameTestHolder(PurpleHazeAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PurpleHazeAuraRecallGameTests {
    private PurpleHazeAuraRecallGameTests() {}

    // The summon key's way: the unsummon brings the Stand back to its user, where the old rule would have ended the aura.
    @GameTest(template = "empty", batch = "ph_aura_recall", timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void theAuraGoesOnFromTheUserThroughARecall(GameTestHelper helper) {
        scene(helper, scene -> {
            scene.stand.setAuraActive(true);
            scene.stand.onUnsummonUserInput();
            helper.assertTrue(scene.stand.hasAura() && scene.stand.isUnsummoning(),
                    "Fixture: the unsummon did not start with the aura on");
            int unsummon = scene.stand.getUnsummonDuration();
            helper.runAfterDelay(unsummon / 2, () -> helper.assertTrue(
                    scene.stand.isRemoved() || scene.stand.hasAura(),
                    "The way back to the user must not end the aura"));
            helper.runAfterDelay(unsummon + 4, () -> {
                helper.assertTrue(scene.power.getSummonedStandEntity() == null, "Fixture: the recall left a Stand");
                helper.assertTrue(scene.user.hasData(PurpleHazeAddon.RECALLED_AURA), "The aura ended with the recall");

                Pig pig = EntityType.PIG.create(scene.level);
                pig.setNoAi(true);
                pig.setPos(scene.user.position().add(1.0D, 0.0D, 0.0D));
                scene.level.addFreshEntity(pig);
                Pig far = EntityType.PIG.create(scene.level);
                far.setNoAi(true);
                far.setPos(scene.user.position().add(6.0D, 0.0D, 0.0D));
                scene.level.addFreshEntity(far);
                helper.runAfterDelay(3, () -> {
                    helper.assertTrue(pig.hasEffect(PurpleHazeAddon.PH_VIRUS),
                            "With no Stand the aura must spread from its user");
                    helper.assertTrue(!far.hasEffect(PurpleHazeAddon.PH_VIRUS),
                            "The aura's radius from the user is the Stand's");
                    helper.assertTrue(!scene.user.hasEffect(PurpleHazeAddon.PH_VIRUS),
                            "The user must not catch his own virus");
                    pig.discard();
                    far.discard();
                    scene.finish(helper);
                });
            });
        });
    }

    // The next summon stands at its user's side, where 1.16's rule ends the aura, so it hurts nobody near him any more.
    @GameTest(template = "empty", batch = "ph_aura_recall", timeoutTicks = 60 + GameTestChunkTickets.WAIT_TICKS)
    public static void theNextSummonTakesTheAuraOverAndEndsItAtItsUsersSide(GameTestHelper helper) {
        scene(helper, scene -> {
            scene.stand.setAuraActive(true);
            scene.power.getPowerType().forceUnsummon(scene.user, scene.power);
            helper.assertTrue(scene.user.hasData(PurpleHazeAddon.RECALLED_AURA), "The aura ended with the recall");
            helper.assertTrue(PurpleHazeAddon.PURPLE_HAZE.get().summon(scene.user, scene.power), "Fixture: no next summon");
            PurpleHazeEntity next = (PurpleHazeEntity) scene.power.getSummonedStandEntity();
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(!scene.user.hasData(PurpleHazeAddon.RECALLED_AURA),
                        "The next summon must take the aura over");
                helper.assertTrue(!next.hasAura(), "1.16's rule ends the aura with the Stand near its user");
                helper.assertTrue(!scene.user.hasEffect(PurpleHazeAddon.PH_VIRUS),
                        "The user must not catch his own virus at the summon");
                scene.finish(helper);
            });
        });
    }

    // R772.1 OQ-C04: the HUD marks the aura's slot, by asking the ability, only while the aura goes on with the Stand
    // recalled; with the Stand out its own slot drawing tells the state.
    @GameTest(template = "empty", batch = "ph_aura_recall", timeoutTicks = 60 + GameTestChunkTickets.WAIT_TICKS)
    public static void theHudMarksTheAuraSlotOnlyWhileTheAuraRunsWithTheStandRecalled(GameTestHelper helper) {
        scene(helper, scene -> {
            Ability aura = scene.power.getAbility(PurpleHazeAddon.VIRUS_AURA_NAME);
            helper.assertTrue(aura != null, "Fixture: Purple Haze has no aura ability");
            helper.assertTrue(!aura.cl_runsWithStandRecalled(scene.user), "The slot is marked before any aura");
            scene.stand.setAuraActive(true);
            helper.assertTrue(!aura.cl_runsWithStandRecalled(scene.user), "The slot is marked with the aura on and the Stand out");
            scene.power.getPowerType().forceUnsummon(scene.user, scene.power);
            helper.assertTrue(aura.cl_runsWithStandRecalled(scene.user),
                    "The slot is not marked while the aura goes on with the Stand recalled");
            helper.assertTrue(PurpleHazeAddon.PURPLE_HAZE.get().summon(scene.user, scene.power), "Fixture: no next summon");
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(!aura.cl_runsWithStandRecalled(scene.user),
                        "The slot is still marked after the next summon took the aura over");
                scene.finish(helper);
            });
        });
    }

    @GameTest(template = "empty", batch = "ph_aura_recall", timeoutTicks = 60 + GameTestChunkTickets.WAIT_TICKS)
    public static void theAuraEndsWithItsUserAndALossOfTheStand(GameTestHelper helper) {
        scene(helper, scene -> {
            againOn(helper, scene);
            CompoundTag saved = scene.user.serializeAttachments(scene.level.registryAccess());
            helper.assertTrue(saved == null || !saved.contains("rotp_ph:recalled_aura"),
                    "A relog must not bring the aura back: " + saved);

            PurpleHazeAuraEvents.onDeath(new LivingDeathEvent(scene.user, scene.user.damageSources().magic()));
            helper.assertTrue(!scene.user.hasData(PurpleHazeAddon.RECALLED_AURA), "Death must end the aura");

            againOn(helper, scene);
            PurpleHazeAuraEvents.onLogout(new PlayerEvent.PlayerLoggedOutEvent(scene.user));
            helper.assertTrue(!scene.user.hasData(PurpleHazeAddon.RECALLED_AURA), "A logout must end the aura");

            againOn(helper, scene);
            PurpleHazeAuraEvents.onChangedDimension(
                    new PlayerEvent.PlayerChangedDimensionEvent(scene.user, Level.OVERWORLD, Level.NETHER));
            helper.assertTrue(!scene.user.hasData(PurpleHazeAddon.RECALLED_AURA), "A change of level must end the aura");

            againOn(helper, scene);
            scene.power.setStand(null);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(!scene.user.hasData(PurpleHazeAddon.RECALLED_AURA), "The loss of the Stand must end the aura");
                scene.user.setData(PurpleHazeAddon.RECALLED_AURA, Boolean.TRUE);
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(!scene.user.hasData(PurpleHazeAddon.RECALLED_AURA),
                            "A user with no Purple Haze holds no aura");
                    scene.finish(helper);
                });
            });
        });
    }

    private static void againOn(GameTestHelper helper, Scene scene) {
        if (scene.power.getSummonedStandEntity() == null) {
            helper.assertTrue(PurpleHazeAddon.PURPLE_HAZE.get().summon(scene.user, scene.power), "Fixture: no summon");
        }
        ((PurpleHazeEntity) scene.power.getSummonedStandEntity()).setAuraActive(true);
        scene.power.getPowerType().forceUnsummon(scene.user, scene.power);
        helper.assertTrue(scene.user.hasData(PurpleHazeAddon.RECALLED_AURA), "Fixture: the aura did not go on");
    }

    private static void scene(GameTestHelper helper, Consumer<Scene> body) {
        GameTestChunkTickets.aroundOrigin(helper).whenTicking(() -> {}, h -> {
            ServerLevel level = h.getLevel();
            FakePlayer user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "PHAuraRecall"));
            Vec3 origin = h.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            user.setNoGravity(true);
            level.addNewPlayer(user);
            StandPower power = StandPower.get(user);
            h.assertTrue(power != null, "The fake player has no Stand power");
            power.setStand(PurpleHazeAddon.PURPLE_HAZE.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            h.assertTrue(PurpleHazeAddon.PURPLE_HAZE.get().summon(user, power), "Could not summon Purple Haze");
            PurpleHazeEntity stand = (PurpleHazeEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            body.accept(new Scene(level, user, power, stand));
        });
    }

    private record Scene(ServerLevel level, FakePlayer user, StandPower power, PurpleHazeEntity stand) {
        void finish(GameTestHelper helper) {
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(user, power);
            }
            if (user.hasData(PurpleHazeAddon.RECALLED_AURA)) {
                user.removeData(PurpleHazeAddon.RECALLED_AURA);
            }
            level.removePlayerImmediately(user, Entity.RemovalReason.DISCARDED);
            helper.succeed();
        }
    }
}
