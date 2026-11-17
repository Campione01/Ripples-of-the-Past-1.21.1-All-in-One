package rotp.addons.stonefree.ability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

/** Owned actors, blocks and chunk lifetime for the Stone Free action tests. */
final class StoneFreeActionFixture {
    static final short REMOVE_KEY = 1;
    static final short GRAPPLE_KEY = 2;

    private StoneFreeActionFixture() {}

    static Fixture fixture(GameTestHelper helper, boolean summon) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int x = (origin.getX() & ~15) + Mth.clamp(origin.getX() & 15, 2, 13);
        int z = (origin.getZ() & ~15) + Mth.clamp(origin.getZ() & 15, 2, 13);
        return fixture(helper, summon, new BlockPos(x, origin.getY(), z));
    }

    static Fixture fixture(GameTestHelper helper, boolean summon, BlockPos column) {
        Cow owner = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(owner != null, "Missing Stone Free action owner fixture");
        owner.setNoAi(true);
        return new Fixture(helper, owner, column, summon, true);
    }

    // The remote grapple cases supply their own exact ticket and intentional ticking boundary.
    static Fixture remote(GameTestHelper helper, LivingEntity owner, BlockPos column, Runnable restore) {
        Fixture fixture = new Fixture(helper, owner, column, false, false);
        fixture.afterwards.add(restore);
        return fixture;
    }

    static void assertTicksEntities(GameTestHelper helper, Fixture fixture) {
        BlockPos ownerAt = fixture.owner.blockPosition();
        boolean ownerTicks = helper.getLevel().isPositionEntityTicking(ownerAt);
        BlockPos standAt = fixture.stand == null ? null : fixture.stand.blockPosition();
        boolean standTicks = standAt == null || helper.getLevel().isPositionEntityTicking(standAt);
        helper.assertTrue(ownerTicks && standTicks,
                "The fixture's chunk does not tick entities, so nothing placed there acts: owner at " + ownerAt
                        + " ticks " + ownerTicks + ", Stand at " + standAt + " ticks " + standTicks
                        + ", test origin " + helper.absolutePos(BlockPos.ZERO));
    }

    static void later(GameTestHelper helper, Fixture fixture, int ticks, Runnable assertions) {
        Runnable schedule = () -> helper.runAfterDelay(ticks, () -> {
            if (fixture.closed) {
                return;
            }
            try {
                assertions.run();
            }
            catch (Throwable failure) {
                fixture.close();
                throw failure;
            }
        });
        if (fixture.tickets != null && !fixture.started) {
            fixture.started = true;
            fixture.tickets.whenTicking(fixture::close, schedule);
        }
        else {
            schedule.run();
        }
    }

    static void succeed(GameTestHelper helper, Fixture fixture) {
        fixture.close();
        helper.succeed();
    }

    static final class Fixture implements GameTestListener {
        final LivingEntity owner;
        final StandPower power;
        final BlockPos column;
        final Vec3 home;
        final List<Entity> extras = new ArrayList<>();
        final List<Runnable> afterwards = new ArrayList<>();
        StoneFreeStandEntity stand;

        private final ServerLevel level;
        private final GameTestChunkTickets tickets;
        private final Map<BlockPos, BlockState> replacedBlocks = new LinkedHashMap<>();
        private boolean started;
        private boolean closed;

        private Fixture(GameTestHelper helper, LivingEntity owner, BlockPos column, boolean summon, boolean ownTickets) {
            this.level = helper.getLevel();
            this.owner = owner;
            this.column = column.immutable();
            home = new Vec3(column.getX() + 1.5D, column.getY() + 2.0D, column.getZ() + 0.5D);
            owner.setNoGravity(true);
            owner.setPos(home);
            owner.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            owner.setHealth(100.0F);
            if (owner instanceof ServerPlayer player) {
                level.addNewPlayer(player);
                helper.assertTrue(level.getEntity(player.getId()) == player, "Could not add the action player owner");
            }
            else {
                helper.assertTrue(level.addFreshEntity(owner), "Could not add the action owner");
            }
            power = PowerClass.STAND.attachGet(owner);
            helper.testInfo.addListener(this);
            tickets = ownTickets ? new GameTestChunkTickets(helper).cover(home) : null;
            power.setStand(StoneFreeAddon.STONE_FREE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            if (summon) {
                helper.assertTrue(StoneFreeAddon.STONE_FREE.get().summon(owner, power),
                        "Could not normally summon Stone Free");
                stand = StoneFreeServices.controller(owner);
                helper.assertTrue(stand != null, "Normal summon produced no controller");
            }
        }

        void setBlock(GameTestHelper helper, BlockPos pos, BlockState state) {
            replacedBlocks.putIfAbsent(pos.immutable(), helper.getLevel().getBlockState(pos));
            helper.getLevel().setBlockAndUpdate(pos, state);
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                for (short key = REMOVE_KEY; key <= 4; key++) {
                    AbilityInput.keyRelease(key, owner);
                }
                LivingComponentAction.getComponent(owner).entityAim.setTarget(ActionTarget.EMPTY);
                // Take the ownership snapshot before unsummoning can detach a controller.
                List<StoneFreeStringEntity> strings = new ArrayList<>();
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof StoneFreeStringEntity string && owns(string)) {
                        strings.add(string);
                    }
                }
                strings.forEach(Entity::discard);
                if (power.hasPower()) {
                    power.getPowerType().forceUnsummon(owner, power);
                }
                extras.forEach(Entity::discard);
                if (owner instanceof ServerPlayer player) {
                    level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                }
                else {
                    owner.discard();
                }
            }
            finally {
                try {
                    replacedBlocks.forEach(level::setBlockAndUpdate);
                    afterwards.forEach(Runnable::run);
                }
                finally {
                    if (tickets != null) {
                        tickets.release();
                    }
                }
            }
        }

        private boolean owns(StoneFreeStringEntity string) {
            UUID id = owner.getUUID();
            return StoneFreeServices.userOf(string.getOwner()) == owner || string.isActiveUserStringFor(id)
                    || string.isUserGrappleFor(id) || string.isBarrierFor(id);
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            close();
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            close();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            close();
        }
    }
}
