package rotp.addons.d4c;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.d4c.ability.D4CPhase2BAbility;
import rotp.addons.d4c.block.D4CPortalBlock;
import rotp.addons.d4c.entity.D4CMarkerEntity;
import rotp.addons.d4c.travel.D4CDimensionTargets;
import rotp.addons.d4c.travel.D4CDimensionUnlocks;
import rotp.core.api.perception.PerceptionBlockers;

/**
 * T3-19 (RS-T3, R772 EV-C PC1 n5): the pull-in's door opens in its target's block and carries whatever stands in it, so
 * an erasing King Crimson who stays on his own decoy's spot was pulled in through the decoy. A target whose door block
 * holds an entity the D4C user cannot perceive is no target, and the pull marker opens no door there; the target alone
 * may be pulled once that entity has left.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CUnseenDoorGameTests {
    private static final String UNSEEN_TAG = "rotp_d4c_gametest_unseen";
    private static final String DOOR_TAG = "rotp_d4c_gametest_unseen_door";
    private static final String SELECTED = "rotp_d4c.phase2a.selected_dimension";
    private static final AtomicBoolean BLOCKER = new AtomicBoolean();

    private D4CUnseenDoorGameTests() {}

    // blocks only entities carrying the tag, for every viewer
    private static void registerBlocker() {
        if (BLOCKER.compareAndSet(false, true)) {
            PerceptionBlockers.register(D4CAddon.id("gametest_unseen"),
                    (viewer, target, sight) -> target.getTags().contains(UNSEEN_TAG));
        }
    }

    @GameTest(template = "empty", batch = "d4cUnseenDoor", timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void pullInRefusesATargetWhoseDoorHoldsAnEntityItsUserCannotPerceive(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> unseenDoorBody(h));
    }

    private static void unseenDoorBody(GameTestHelper helper) {
        registerBlocker();
        D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-unseen-door");
        try {
            startUnseenDoor(helper, scene);
        }
        catch (RuntimeException failure) {
            scene.close();
            throw failure;
        }
    }

    private static void startUnseenDoor(GameTestHelper helper, D4CGameTestScene scene) {
        Cow target = scene.cow(new Vec3(0.0D, 0.6D, 2.5D));
        Cow unseen = scene.cow(new Vec3(0.0D, 0.6D, 2.5D));
        unseen.addTag(UNSEEN_TAG);
        helper.assertTrue(PerceptionBlockers.isBlocked(scene.owner, unseen),
                "fixture: the tagged cow is perceived by the D4C user");
        helper.assertTrue(D4CPhase2BService.findPullInTarget(scene.owner) == null
                        && !D4CPhase2BService.canExecute(D4CPhase2BAbility.Mode.PULL_IN_TRAVEL, scene.owner),
                "T3-19: an unperceived entity in the target's block: no pull-in target, found "
                        + D4CPhase2BService.findPullInTarget(scene.owner));
        unseen.moveTo(unseen.position().add(3.0D, 0.0D, 0.0D));
        helper.assertTrue(D4CPhase2BService.findPullInTarget(scene.owner) == target,
                "T3-19: the unperceived entity 3 blocks aside: the target alone is pulled, found "
                        + D4CPhase2BService.findPullInTarget(scene.owner));

        // a destination this server has (the parallel world may be missing here)
        scene.owner.getPersistentData().putString(SELECTED, D4CDimensionTargets.NETHER.toString());
        D4CDimensionUnlocks.record(scene.owner, D4CDimensionTargets.NETHER);
        target.addTag(DOOR_TAG);
        unseen.addTag(DOOR_TAG);
        D4CMarkerEntity marker = D4CAddon.FLAG.get().create(helper.getLevel());
        helper.assertTrue(marker != null, "fixture: no pull marker");
        marker.initialize(scene.owner, target, D4CMarkerEntity.Mode.PULL);
        helper.assertTrue(helper.getLevel().addFreshEntity(marker), "fixture: the pull marker could not join");
        scene.spawned.add(marker);
        // the unperceived entity steps into the door block after the press
        unseen.moveTo(target.position());
        int[] openedAt = {-1};
        boolean[] released = {false};
        helper.onEachTick(() -> {
            if (openedAt[0] < 0 && marker.saveWithoutId(new CompoundTag()).getBoolean("PortalPlaced")) {
                openedAt[0] = marker.ageTicks();
            }
        });
        helper.runAfterDelay(D4CPhase2BSpec.PORTAL_ARM_TICKS + 8, () -> {
            if (openedAt[0] >= 0) {
                finish(helper, scene);
                helper.fail("T3-19: the pull marker opened its door at age " + openedAt[0] + " in a block that holds "
                        + "an entity its user cannot perceive");
                return;
            }
            // control: the same marker opens its door once nothing unperceived stands there
            unseen.removeTag(UNSEEN_TAG);
            unseen.moveTo(unseen.position().add(3.0D, 0.0D, 0.0D));
            released[0] = true;
        });
        helper.runAfterDelay(D4CPhase2BSpec.PORTAL_ARM_TICKS + 14, () -> {
            finish(helper, scene);
            helper.assertTrue(released[0] && openedAt[0] > D4CPhase2BSpec.PORTAL_ARM_TICKS + 7,
                    "control: the pull marker did not open its door once the unperceived entity had left (opened "
                            + "at age " + openedAt[0] + ")");
            helper.succeed();
        });
    }

    // the door may have carried the cows to the Nether; its block may still stand where they were
    private static void finish(GameTestHelper helper, D4CGameTestScene scene) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether != null) {
            for (Entity entity : nether.getEntities(EntityType.COW, cow -> cow.getTags().contains(DOOR_TAG))) {
                entity.discard();
            }
        }
        for (Entity entity : scene.spawned) {
            if (entity instanceof Cow cow) {
                BlockPos at = cow.blockPosition();
                if (helper.getLevel().getBlockState(at).getBlock() instanceof D4CPortalBlock) {
                    helper.getLevel().setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
                }
            }
        }
        scene.owner.getPersistentData().remove(SELECTED);
        scene.close();
    }
}
