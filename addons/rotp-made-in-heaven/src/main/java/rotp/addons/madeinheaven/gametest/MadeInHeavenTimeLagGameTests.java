package rotp.addons.madeinheaven.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.TimeAuraAbility;
import rotp.addons.madeinheaven.state.TimeAccelerationMode;
import rotp.addons.madeinheaven.state.TimeAccelerationService;
import rotp.addons.madeinheaven.state.TimeAccelerationState;
import rotp.addons.madeinheaven.state.TimeLagRates;
import rotp.addons.madeinheaven.state.TimeLagService;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.api.time.LevelTimeRates;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * The manga's acceleration (owner 2026-10-29): every other living being falls behind the owner by the phase
 * multiplier, and every path that ends the acceleration ends that lag. Owner report F09: Time Aura only strikes beings.
 */
@GameTestHolder(LightSpeedDashGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MadeInHeavenTimeLagGameTests {
    private static final double DRAG = 0.98F;
    private static final double GRAVITY = 0.08D;

    private MadeInHeavenTimeLagGameTests() {}

    @GameTest(template = "empty", templateNamespace = LightSpeedDashGameTests.NAMESPACE,
            batch = "mihTimeLagTick", timeoutTicks = 40)
    public static void aBeingTheServerMovesRunsOnEveryMthTickAndIsCarriedSmoothlyBetween(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TimeAccelerationState state = MadeInHeavenAddon.accelerationState(level);
        helper.assertTrue(!state.active(), "Setup: another acceleration is running");
        Pig pig = EntityType.PIG.create(level);
        helper.assertTrue(pig != null, "Setup: no pig");
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 12.0D, 1.5D));
        pig.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        pig.setPersistenceRequired();
        helper.assertTrue(level.addFreshEntity(pig), "Setup: could not add the pig");
        try {
            state.begin(UUID.randomUUID(), TimeAccelerationMode.OTHERS_FALL_BEHIND);
            helper.assertTrue(TimeLagService.mode(pig) == TimeLagService.Mode.TICK
                            && TimeLagService.multiplier(level) == 2,
                    "Phase 0 must hold every other being back by 2, got " + TimeLagService.mode(pig) + " x"
                            + TimeLagService.multiplier(level));
            double y0 = pig.getY();
            pig.setDeltaMovement(0.0D, -0.5D, 0.0D);
            pig.invulnerableTime = 10;

            level.tickNonPassenger(pig);
            double v1 = (-0.5D - GRAVITY) * DRAG;
            assertNear(helper, pig.getY(), y0 - 0.25D, "After its real tick a being lagging by 2 shows half its step");
            assertNear(helper, pig.getDeltaMovement().y, v1, "Its real tick ran its physics");

            level.tickNonPassenger(pig);
            assertNear(helper, pig.getY(), y0 - 0.5D, "The skipped tick carries it the rest of the step");
            assertNear(helper, pig.getDeltaMovement().y, v1, "A skipped tick must not run its physics");
            helper.assertTrue(pig.invulnerableTime == 8,
                    "Its hurt cooldown runs in real time, in the skipped tick too: " + pig.invulnerableTime);

            level.tickNonPassenger(pig);
            assertNear(helper, pig.getY(), y0 - 0.5D + v1 / 2.0D, "The next real tick starts the next step");

            TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            helper.assertTrue(!state.active() && TimeLagService.mode(pig) == TimeLagService.Mode.NONE,
                    "The manual stop must end the lag");
            assertNear(helper, pig.getY(), y0 - 0.5D + v1, "Ending the lag completes the step at once");
            double v2 = (v1 - GRAVITY) * DRAG;
            level.tickNonPassenger(pig);
            assertNear(helper, pig.getY(), y0 - 0.5D + v1 + v2, "After the end every tick is a real one again");
        } finally {
            if (state.active()) {
                TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            }
            pig.discard();
        }
        helper.succeed();
    }

    // EA4-03: Mob.serverAiStep reselects goals only when (tickCount + id) is even, so a held-back mob's tickCount must
    // count its own real ticks, or at an even multiplier one id parity never targets or walks.
    @GameTest(template = "empty", templateNamespace = LightSpeedDashGameTests.NAMESPACE,
            batch = "mihTimeLagMobs", timeoutTicks = 40)
    public static void heldBackMobsOfBothIdParitiesTargetAndReachAtEvenMultipliers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TimeAccelerationState state = MadeInHeavenAddon.accelerationState(level);
        helper.assertTrue(!state.active(), "Setup: another acceleration is running");
        helper.assertTrue(level.getLevelData() instanceof PrimaryLevelData, "Setup: no primary level data");
        PrimaryLevelData levelData = (PrimaryLevelData) level.getLevelData();
        long gameTime = levelData.getGameTime();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 10; z++) {
                level.setBlockAndUpdate(origin.offset(x, 1, z), Blocks.STONE.defaultBlockState());
            }
        }
        ServerPlayer target = player(helper, "mih-mob-target", new Vec3(2.5D, 2.0D, 4.5D));
        List<Zombie> zombies = new ArrayList<>();
        try {
            for (int multiplier : new int[] {2, 6}) {
                state.begin(UUID.randomUUID(), TimeAccelerationMode.OTHERS_FALL_BEHIND);
                state.advance(multiplier == 2 ? 0 : 21);
                helper.assertTrue(TimeLagService.multiplier(level) == multiplier,
                        "Setup: expected x" + multiplier + ", got x" + TimeLagService.multiplier(level));
                zombies.clear();
                for (int index = 0; index < 2; index++) {
                    Zombie zombie = EntityType.ZOMBIE.create(level);
                    helper.assertTrue(zombie != null, "Setup: no zombie");
                    Vec3 at = helper.absoluteVec(new Vec3(10.5D, 2.0D, 3.5D + 2.0D * index));
                    zombie.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
                    zombie.setPersistenceRequired();
                    zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                    helper.assertTrue(level.addFreshEntity(zombie), "Setup: could not add a zombie");
                    zombies.add(zombie);
                }
                helper.assertTrue(((zombies.get(0).getId() ^ zombies.get(1).getId()) & 1) == 1,
                        "Setup: the zombies must have ids of both parities");
                double[] start = new double[2];
                for (int index = 0; index < 2; index++) {
                    start[index] = zombies.get(index).distanceTo(target);
                }
                // goals such as MeleeAttackGoal gate on the game time, which stands still inside one tick
                for (int tick = 0; tick < 150 * multiplier; tick++) {
                    levelData.setGameTime(gameTime + tick);
                    for (Zombie zombie : zombies) {
                        level.tickNonPassenger(zombie);
                    }
                }
                levelData.setGameTime(gameTime);
                for (int index = 0; index < 2; index++) {
                    Zombie zombie = zombies.get(index);
                    helper.assertTrue(zombie.getTarget() == target,
                            "x" + multiplier + ": the zombie with id parity " + (zombie.getId() & 1)
                                    + " never targeted the player");
                    helper.assertTrue(zombie.distanceTo(target) < start[index] - 3.0D,
                            "x" + multiplier + ": the zombie with id parity " + (zombie.getId() & 1)
                                    + " did not walk to the player: " + start[index] + " -> "
                                    + zombie.distanceTo(target));
                    helper.assertTrue(zombie.tickCount == 150,
                            "x" + multiplier + ": tickCount must hold the real ticks only, got " + zombie.tickCount);
                    zombie.discard();
                }
                zombies.clear();
                TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            }
        } finally {
            levelData.setGameTime(gameTime);
            if (state.active()) {
                TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            }
            for (Zombie zombie : zombies) {
                zombie.discard();
            }
            target.discard();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 10; z++) {
                    level.setBlockAndUpdate(origin.offset(x, 1, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = LightSpeedDashGameTests.NAMESPACE,
            batch = "mihTimeLagPlayer", timeoutTicks = 40)
    public static void aPlayerFallsBehindThroughAttributesUntilTheAccelerationEnds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TimeAccelerationState state = MadeInHeavenAddon.accelerationState(level);
        helper.assertTrue(!state.active(), "Setup: another acceleration is running");
        ServerPlayer player = player(helper, "mih-lag-player", new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer owner = player(helper, "mih-lag-owner", new Vec3(2.5D, 2.0D, 2.5D));
        try {
            player.getAbilities().mayfly = true;
            float flying = player.getAbilities().getFlyingSpeed();
            double[] base = values(player);
            state.begin(owner.getUUID(), TimeAccelerationMode.OTHERS_FALL_BEHIND);
            state.advance(26);
            int multiplier = TimeLagService.multiplier(level);
            helper.assertTrue(multiplier == 8, "Phase 26 must hold the others back by 8, got " + multiplier);

            TimeLagService.reconcileAttributes(List.of(player, owner));
            helper.assertTrue(TimeLagService.mode(player) == TimeLagService.Mode.ATTRIBUTE
                            && TimeLagService.mode(owner) == TimeLagService.Mode.NONE,
                    "A player falls behind through attributes, the owner not at all");
            assertScaled(helper, player, base, multiplier, "the slowed player");
            assertNear(helper, player.getAbilities().getFlyingSpeed(), flying / 8.0D, "Flying speed at 1/m");
            assertScaled(helper, owner, values(owner), 1, "the owner");

            player.setGameMode(GameType.CREATIVE);
            TimeLagService.reconcileAttributes(List.of(player, owner));
            assertScaled(helper, player, base, 1, "a creative player (the core's time-stop game-mode rule)");
            player.setGameMode(GameType.SURVIVAL);
            TimeLagService.reconcileAttributes(List.of(player, owner));
            assertScaled(helper, player, base, multiplier, "the player back in survival");

            TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            assertScaled(helper, player, base, 1, "the player after the manual stop");
            assertNear(helper, player.getAbilities().getFlyingSpeed(), flying, "The flying speed is restored");
        } finally {
            if (state.active()) {
                TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            }
            player.discard();
            owner.discard();
        }
        helper.succeed();
    }

    // Owner report F09: with no mob around Time Aura blinked to its user's own Stand and struck it.
    @GameTest(template = "empty", templateNamespace = LightSpeedDashGameTests.NAMESPACE,
            batch = "mihAuraTargets", timeoutTicks = 40)
    public static void timeAuraStrikesBeingsNotItsOwnStandOrAnArmorStand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer user = player(helper, "mih-aura-user", new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer creative = player(helper, "mih-aura-creative", new Vec3(2.5D, 2.0D, 0.5D));
        ArmorStand armorStand = EntityType.ARMOR_STAND.create(level);
        Pig pig = EntityType.PIG.create(level);
        try {
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(MadeInHeavenAddon.MADE_IN_HEAVEN.get());
            power.skipProgression();
            helper.assertTrue(MadeInHeavenAddon.MADE_IN_HEAVEN.get().summon(user, power),
                    "Setup: Made in Heaven could not be summoned");
            StandEntity stand = power.getSummonedStandEntity();
            creative.setGameMode(GameType.CREATIVE);
            for (var entity : List.of(armorStand, pig)) {
                Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2.0D, 2.5D));
                entity.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
                helper.assertTrue(level.addFreshEntity(entity), "Setup: could not add " + entity);
            }
            helper.assertTrue(stand != null && !TimeAuraAbility.isEnemy(user, stand),
                    "1.16 never took the user's own Stand (StandEntity.isAlliedTo)");
            helper.assertTrue(!TimeAuraAbility.isEnemy(user, armorStand), "An armor stand is no being to strike");
            helper.assertTrue(!TimeAuraAbility.isEnemy(user, creative),
                    "A player that cannot be hurt is no target");
            helper.assertTrue(TimeAuraAbility.isEnemy(user, pig), "A mob stays a target");

            TimeAccelerationState state = MadeInHeavenAddon.accelerationState(level);
            helper.assertTrue(!state.active(), "Setup: another acceleration is running");
            state.begin(UUID.randomUUID(), TimeAccelerationMode.OTHERS_FALL_BEHIND);
            try {
                helper.assertTrue(TimeLagService.mode(user) == TimeLagService.Mode.ATTRIBUTE
                                && TimeLagService.mode(stand) == TimeLagService.Mode.NONE,
                        "A slowed player's Stand keeps to its user (the core's time-stop rule), got "
                                + TimeLagService.mode(stand));
            } finally {
                TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            }
            state.begin(user.getUUID(), TimeAccelerationMode.OTHERS_FALL_BEHIND);
            try {
                helper.assertTrue(TimeLagService.mode(stand) == TimeLagService.Mode.NONE
                                && TimeLagService.mode(pig) == TimeLagService.Mode.TICK
                                && TimeLagService.mode(armorStand) == TimeLagService.Mode.NONE,
                        "The owner's Stand keeps his pace, a mob falls behind, an armor stand is no being");
            } finally {
                TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            }
            power.setSummonedStand(null);
            stand.discard();
        } finally {
            armorStand.discard();
            pig.discard();
            user.discard();
            creative.discard();
        }
        helper.succeed();
    }

    // Owner 2026-11-04 (MIH-FRAME.md, mode B): living beings keep their own time, the non-living world runs m times
    // faster through the core's rates, the owner adapts; mode A gives none of it.
    @GameTest(template = "empty", templateNamespace = LightSpeedDashGameTests.NAMESPACE,
            batch = "mihWorldAccelerates", timeoutTicks = 40)
    public static void inModeBTheNonLivingWorldRunsFasterAndOnlyTheOwnerAdapts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TimeAccelerationState state = MadeInHeavenAddon.accelerationState(level);
        helper.assertTrue(!state.active(), "Setup: another acceleration is running");
        ServerPlayer owner = player(helper, "mih-world-owner", new Vec3(0.5D, 2.0D, 0.5D));
        ServerPlayer other = player(helper, "mih-world-other", new Vec3(2.5D, 2.0D, 2.5D));
        Pig pig = EntityType.PIG.create(level);
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2.0D, 2.5D));
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STICK));
        try {
            helper.assertTrue(pig != null, "Setup: no pig");
            pig.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(pig) && level.addFreshEntity(item), "Setup: could not add");
            double[] ownerBase = values(owner);
            double[] otherBase = values(other);
            state.begin(owner.getUUID(), TimeAccelerationMode.WORLD_ACCELERATES);
            state.advance(26);
            float m = TimeLagService.multiplier(level);
            helper.assertTrue(m == 8.0F, "Phase 26 runs the world 8 times faster, got " + m);
            helper.assertTrue(TimeLagService.mode(pig) == TimeLagService.Mode.NONE
                            && TimeLagService.mode(other) == TimeLagService.Mode.NONE,
                    "Living beings keep their own time in mode B");
            helper.assertTrue(EntityTimeRates.rate(EntityTimeRates.Channel.ENTITY_TICK, item) == m
                            && EntityTimeRates.rate(EntityTimeRates.Channel.STAND_ACTIONS, owner) == m
                            && EntityTimeRates.rate(EntityTimeRates.Channel.STAND_ACTIONS, other) == 1.0F
                            && EntityTimeRates.rate(EntityTimeRates.Channel.MOTION, owner) == 1.0F
                            && EntityTimeRates.rate(EntityTimeRates.Channel.BEING, owner) == m
                            && EntityTimeRates.rate(EntityTimeRates.Channel.BEING, pig) == 1.0F,
                    "Entity rates: item x8, the owner's Stand actions x8, everyone else's 1, the owner walks normally");
            for (LevelTimeRates.Channel channel : LevelTimeRates.Channel.values()) {
                helper.assertTrue(LevelTimeRates.rate(channel, level) == m,
                        "The level's " + channel + " runs 8 times faster, got " + LevelTimeRates.rate(channel, level));
            }

            TimeLagService.reconcileAttributes(List.of(owner, other));
            assertNear(helper, owner.getAttributeValue(Attributes.ATTACK_SPEED), ownerBase[1] * m,
                    "The owner attacks at the world's pace");
            assertNear(helper, owner.getAttributeValue(Attributes.BLOCK_BREAK_SPEED), ownerBase[2] * m,
                    "The owner mines at the world's pace");
            assertNear(helper, owner.getAttributeValue(Attributes.MOVEMENT_SPEED), ownerBase[0],
                    "The owner walks at normal speed");
            assertScaled(helper, other, otherBase, 1, "another player in mode B");
            owner.setSprinting(true);
            double sprinting = owner.getAttributeValue(Attributes.MOVEMENT_SPEED);
            TimeLagService.reconcileAttributes(List.of(owner, other));
            double sprint = TimeLagRates.ownerSprintFactor(26);
            assertNear(helper, owner.getAttributeValue(Attributes.MOVEMENT_SPEED), sprinting * sprint,
                    "A sprinting owner takes 1.16's boost of phase 26 (" + sprint + "x)");
            assertNear(helper, owner.getAttributeValue(Attributes.STEP_HEIGHT), TimeLagRates.ownerStepHeight(26),
                    "From phase 4 the sprinting owner steps up as in 1.16");
            owner.setSprinting(false);

            TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            assertScaled(helper, owner, ownerBase, 1, "the owner after the manual stop");
            helper.assertTrue(owner.getAttributeValue(Attributes.STEP_HEIGHT) == owner.getAttributeBaseValue(
                            Attributes.STEP_HEIGHT)
                            && EntityTimeRates.rate(EntityTimeRates.Channel.ENTITY_TICK, item) == 1.0F
                            && EntityTimeRates.rate(EntityTimeRates.Channel.STAND_ACTIONS, owner) == 1.0F
                            && LevelTimeRates.rate(LevelTimeRates.Channel.RANDOM_TICKS, level) == 1.0F,
                    "Everything is back to normal after the manual stop");

            state.begin(owner.getUUID(), TimeAccelerationMode.OTHERS_FALL_BEHIND);
            helper.assertTrue(EntityTimeRates.rate(EntityTimeRates.Channel.ENTITY_TICK, item) == 1.0F
                            && LevelTimeRates.rate(LevelTimeRates.Channel.RANDOM_TICKS, level) == 1.0F
                            && LevelTimeRates.rate(LevelTimeRates.Channel.TIME, level) == 2.0F
                            && EntityTimeRates.rate(EntityTimeRates.Channel.STAND_ACTIONS, other) == 0.5F
                            && EntityTimeRates.rate(EntityTimeRates.Channel.MOTION, other) == 0.5F
                            && EntityTimeRates.rate(EntityTimeRates.Channel.BEING, pig) == 0.5F,
                    "Mode A: the non-living world keeps its pace, the level's time runs 2 times faster, other "
                            + "players' Stand actions and motion and every other being run 2 times slower");
        } finally {
            if (state.active()) {
                TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            }
            item.discard();
            if (pig != null) {
                pig.discard();
            }
            owner.discard();
            other.discard();
        }
        helper.succeed();
    }

    // Rule G3 (RS-T3 rows T3-22 to T3-24): during the acceleration the day time belongs to Made in Heaven.
    @GameTest(template = "empty", templateNamespace = LightSpeedDashGameTests.NAMESPACE,
            batch = "mihDayTime", timeoutTicks = 40)
    public static void aRewindDoesNotSetTheAcceleratedDayTimeBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TimeAccelerationState state = MadeInHeavenAddon.accelerationState(level);
        helper.assertTrue(!state.active(), "Setup: another acceleration is running");
        long original = level.getDayTime();
        try {
            state.begin(UUID.randomUUID(), TimeAccelerationMode.WORLD_ACCELERATES);
            long owned = original + 6000L;
            level.setDayTime(owned);
            state.ownDayTime(owned);
            level.setDayTime(owned - 120L);
            TimeAccelerationService.reassertDayTime(level.getServer());
            helper.assertTrue(level.getDayTime() == owned,
                    "A rewind's setback is undone: expected " + owned + ", got " + level.getDayTime());
            level.setDayTime(owned + 24000L);
            TimeAccelerationService.reassertDayTime(level.getServer());
            helper.assertTrue(level.getDayTime() == owned + 24000L,
                    "A forward change (a night slept through) goes along, got " + level.getDayTime());
            TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            level.setDayTime(owned - 120L);
            TimeAccelerationService.reassertDayTime(level.getServer());
            helper.assertTrue(level.getDayTime() == owned - 120L,
                    "Without the acceleration the day time is nobody's");
        } finally {
            if (state.active()) {
                TimeAccelerationService.clear(level, TimeAccelerationService.ClearReason.MANUAL);
            }
            level.setDayTime(original);
        }
        helper.succeed();
    }

    private static final List<Holder<Attribute>> SCALED = List.of(
            Attributes.MOVEMENT_SPEED,
            Attributes.ATTACK_SPEED,
            Attributes.BLOCK_BREAK_SPEED,
            Attributes.JUMP_STRENGTH,
            Attributes.GRAVITY);

    private static double[] values(ServerPlayer player) {
        double[] values = new double[SCALED.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = player.getAttributeValue(SCALED.get(index));
        }
        return values;
    }

    private static void assertScaled(
            GameTestHelper helper, ServerPlayer player, double[] base, int multiplier, String who) {
        double rate = 1.0D / multiplier;
        double[] expected = {
                base[0] * rate,
                base[1] * rate,
                base[2] * rate,
                base[3] * TimeLagRates.jumpStrengthFactor(multiplier),
                base[4] * rate * rate};
        for (int index = 0; index < expected.length; index++) {
            assertNear(helper, player.getAttributeValue(SCALED.get(index)), expected[index],
                    SCALED.get(index).getRegisteredName() + " of " + who + " at x" + multiplier);
        }
    }

    private static void assertNear(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-5D,
                message + ": expected " + expected + ", got " + actual);
    }

    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relative) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentListener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        Vec3 position = helper.absoluteVec(relative);
        player.moveTo(position.x, position.y, position.z);
        level.addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        return player;
    }

    private static final class SilentListener extends ServerGamePacketListenerImpl {
        private SilentListener(
                MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener sendListener) {}
    }
}
