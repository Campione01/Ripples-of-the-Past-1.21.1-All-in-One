package rotp.addons.cmoon.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.GravityFieldAbility;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.addons.cmoon.field.GravityField;
import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * R772.1 G25: the Gravity Field of a player's C-Moon build, on the core's directional gravity. Who it holds, how they fall
 * and every way it ends. A test has its own batch: 100 blocks reach whatever else would run beside it.
 */
@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonGravityFieldGameTests {
    private static final short KEY = 7741;
    // above what the other tests of this level build, so that the nearest surface is the one a test puts there
    private static final int HEIGHT = 48;

    private CMoonGravityFieldGameTests() {}

    @GameTest(template = "empty", batch = "cmFieldSupport", timeoutTicks = 20)
    public static void theNearestWallOrCeilingIsTheNewFloorAndTheFloorOnlyIfThereIsNeither(GameTestHelper helper) {
        BlockPos base = new BlockPos(0, 64, 0);
        UUID someone = new UUID(1L, 2L);
        support(helper, Direction.UP, someone, base.above(3), base.east(5), base.below());
        support(helper, Direction.EAST, someone, base.east(2), base.above(3), base.below());
        support(helper, Direction.DOWN, someone, base.below());
        support(helper, Direction.NORTH, someone, base.north(50), base.below());
        support(helper, Direction.DOWN, someone, base.north(51), base.above(51), base.below(50));
        support(helper, null, someone, base.north(51), base.below(51));

        Set<Direction> ties = EnumSet.noneOf(Direction.class);
        for (long i = 0; i < 64; i++) {
            UUID user = UUID.nameUUIDFromBytes(new byte[] {(byte) i});
            Direction first = find(user, base.north(4), base.south(4));
            helper.assertTrue(first == find(user, base.north(4), base.south(4)), "A tie must always fall the same way");
            ties.add(first);
        }
        helper.assertTrue(ties.equals(EnumSet.of(Direction.NORTH, Direction.SOUTH)),
                "A tie goes by the user, either way, got " + ties);
        helper.succeed();
    }

    private static void support(GameTestHelper helper, Direction expected, UUID user, BlockPos... solid) {
        Direction found = find(user, solid);
        helper.assertTrue(found == expected, "Support " + expected + " expected, got " + found + " with " + List.of(solid));
    }

    private static Direction find(UUID user, BlockPos... solid) {
        Set<BlockPos> blocks = new HashSet<>(List.of(solid));
        return GravityField.findSupportDirection(new BlockPos(0, 64, 0), user, blocks::contains);
    }

    @GameTest(template = "empty", batch = "cmFieldWho", timeoutTicks = 200)
    public static void itHoldsWhatFallsInRangeAndLetsGoOfWhatLeaves(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-who", GravityField.RADIUS + 8.0D);
        scene.ceiling(3);
        Vec3 origin = scene.origin;
        Cow near = scene.cow(origin.add(2.0D, 0.0D, 0.0D));
        Vec3 outside = origin.add(GravityField.RADIUS + 6.0D, 0.0D, 0.0D);
        Cow far = scene.cow(outside);
        ItemEntity item = scene.item(origin.add(0.0D, 0.0D, 2.0D));
        ItemEntity floating = scene.item(origin.add(0.0D, 0.0D, -2.0D));
        floating.setNoGravity(true);
        Arrow flying = scene.arrow(origin.add(-2.0D, 0.0D, 0.0D));
        RecordingServerPlayer spectator = scene.other("cm-field-spectator", origin.add(1.0D, 0.0D, 2.0D));
        spectator.player.setGameMode(GameType.SPECTATOR);

        GravityField.toggle(scene.user);
        GravityField field = GravityField.of(scene.user);
        helper.assertTrue(field != null && field.direction() == Direction.UP, "Premise: the ceiling is the nearest surface");

        scene.held(field, scene.user, "the user");
        scene.held(field, scene.stand, "the user's Stand");
        scene.held(field, near, "a cow in range");
        helper.assertTrue(GravityField.holding(far) == null
                        && DirectionalGravityApi.getEffectiveDirection(far) == Direction.DOWN,
                "A cow out of range was taken");
        helper.assertTrue(GravityField.holding(item) == field && GravityField.holding(flying) == field,
                "An item and an arrow in range must fall with the field");
        helper.assertTrue(GravityField.holding(floating) == null,
                "What does not fall by itself must be left alone");
        helper.assertTrue(GravityField.holding(spectator.player) == null
                        && DirectionalGravityApi.getEffectiveDirection(spectator.player) == Direction.DOWN,
                "A spectator was taken");

        // its own tick would let an arrow fall that nothing holds, so it is asked before any tick
        flying.inGround = true;
        GravityField.pullBeforeTick(flying);
        helper.assertTrue(flying.getDeltaMovement().lengthSqr() == 0.0D,
                "A stuck arrow must not store the pull: " + flying.getDeltaMovement());
        flying.inGround = false;

        double itemStart = item.getY();
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(item.getY() > itemStart && item.getDeltaMovement().y > 0.0D,
                    "The item must fall up: y " + itemStart + " -> " + item.getY() + " at " + item.getDeltaMovement());
            helper.assertTrue(flying.getDeltaMovement().y > 0.0D, "The arrow must fall up: " + flying.getDeltaMovement());

            near.moveTo(outside.x, outside.y, outside.z + 1.0D);
            far.moveTo(origin.x + 2.0D, origin.y, origin.z);
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(GravityField.holding(near) == null
                            && DirectionalGravityApi.getEffectiveDirection(near) == Direction.DOWN
                            && near.hasEffect(MobEffects.SLOW_FALLING),
                    "What leaves the range falls normally again, gently");
            scene.held(field, far, "a cow that came into range");
            Cow late = scene.cow(origin.add(-1.0D, 0.0D, -1.0D));
            helper.runAfterDelay(2, () -> {
                scene.held(field, late, "a cow born in range");
                scene.finish();
            });
        });
    }

    @GameTest(template = "empty", batch = "cmFieldFall", timeoutTicks = 200)
    public static void everyoneIsOnTheNewFloorAtOnce(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-fall", 8.0D);
        int ceiling = scene.ceiling(20);
        Cow cow = scene.cow(scene.origin.add(1.0D, 0.0D, 0.0D));
        GravityField.toggle(scene.user);
        GravityField field = GravityField.of(scene.user);
        helper.assertTrue(field != null && field.direction() == Direction.UP, "Premise: the ceiling is the nearest surface");
        double[] fastest = {0.0D};
        helper.onEachTick(() -> fastest[0] = Math.max(fastest[0], Math.abs(cow.getDeltaMovement().y)));

        scene.user.setDeltaMovement(new Vec3(0.0D, 0.1D, 0.0D));
        helper.runAfterDelay(1, () -> helper.assertTrue(scene.user.getDeltaMovement().y <= 0.1D,
                "A player's fall is his own client's to speed up, the server gave " + scene.user.getDeltaMovement()));
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(Math.abs(cow.getBoundingBox().maxY - ceiling) < 0.01D && cow.onGround(),
                    "The build's fall, six times the tick's, has it on the ceiling within 8 ticks; its top is at "
                            + cow.getBoundingBox().maxY + " under " + ceiling + ", moving " + cow.getDeltaMovement());
            // ServerGamePacketListenerImpl turns a player back who moves over 10 blocks a tick
            helper.assertTrue(fastest[0] > 1.0D && fastest[0] < 10.0D,
                    "The fall must be fast and stay under the server's limit for a player: " + fastest[0]);
            helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "A fall to the new floor hurts like any other");
            scene.finish();
        });
    }

    @GameTest(template = "empty", batch = "cmFieldRest", timeoutTicks = 200)
    public static void whatIsNotAliveRestsOnTheNewFloorAndAClientsCopyStaysThere(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-rest", 24.0D);
        int ceiling = scene.ceiling(4, 22);
        GravityField.toggle(scene.user);
        GravityField field = GravityField.of(scene.user);
        helper.assertTrue(field != null && field.direction() == Direction.UP, "Premise: the ceiling is the nearest surface");

        // an experience orb goes to a player within 8 blocks; a falling block lands as a block (aFallingBlockLands...)
        List<Entity> things = List.of(
                scene.tnt(scene.origin.add(9.5D, 1.0D, 0.0D)),
                scene.thing(new ExperienceOrb(scene.level, 0.0D, 0.0D, 0.0D, 1), scene.origin.add(13.5D, 1.0D, 0.0D)),
                scene.thing(EntityType.BOAT.create(scene.level), scene.origin.add(16.0D, 1.0D, 0.0D)),
                scene.thing(EntityType.MINECART.create(scene.level), scene.origin.add(18.5D, 1.0D, 0.0D)),
                scene.item(scene.origin.add(20.5D, 1.0D, 0.0D)));

        helper.runAfterDelay(50, () -> {
            for (Entity thing : things) {
                helper.assertTrue(Math.abs(thing.getBoundingBox().maxY - ceiling) < 0.001D,
                        thing + " must rest on the ceiling at " + ceiling + ", its top is at " + thing.getBoundingBox().maxY);
                Vec3 told = toldToAClient(thing.position());
                thing.setPos(told.x, told.y, told.z);
            }
        });
        helper.runAfterDelay(60, () -> {
            List<String> wrong = new ArrayList<>();
            for (Entity thing : things) {
                if (Math.abs(thing.getBoundingBox().maxY - ceiling) >= 0.001D) {
                    wrong.add(thing.getType().toShortString() + " top at " + thing.getBoundingBox().maxY);
                }
            }
            helper.assertTrue(wrong.isEmpty(),
                    "What a client is told sent these on from the ceiling at " + ceiling + ": " + wrong);
            scene.finish();
        });
    }

    @GameTest(template = "empty", batch = "cmFieldWall", timeoutTicks = 100)
    public static void aClientsCopyOfABoxAsWideAsATntStaysOffAWall(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-wall", 8.0D);
        int wall = scene.wallToTheEast(3);
        GravityField.toggle(scene.user);
        GravityField field = GravityField.of(scene.user);
        helper.assertTrue(field != null && field.direction() == Direction.EAST, "Premise: the wall is the nearest surface");
        Entity tnt = scene.tnt(scene.origin.add(0.0D, 1.0D, 2.0D));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(Math.abs(tnt.getBoundingBox().maxX - wall) < 0.001D,
                    "The TNT must rest on the wall at " + wall + ", its side is at " + tnt.getBoundingBox().maxX);
            Vec3 told = toldToAClient(tnt.position());
            tnt.setPos(told.x, told.y, told.z);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(Math.abs(tnt.getBoundingBox().maxX - wall) < 0.001D,
                    "What a client is told of the TNT sent it on through the wall at " + wall
                            + ", its side is at " + tnt.getBoundingBox().maxX);
            scene.finish();
        });
    }

    @GameTest(template = "empty", batch = "cmFieldInside", timeoutTicks = 100)
    public static void whatStartsInsideTheNewFloorComesOutOnTheNearSide(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-inside", 8.0D);
        int ceiling = scene.ceiling(3);
        GravityField.toggle(scene.user);
        helper.assertTrue(GravityField.of(scene.user) != null, "Premise: the field started");
        // summoned at the feet of one who stands on the ceiling
        Entity tnt = scene.tnt(scene.origin.add(2.0D, 3.0D, 0.0D));
        // tossed from the eye of one who crawls there
        Entity item = scene.item(scene.origin.add(-2.0D, 2.9D, 0.0D));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(Math.abs(tnt.getBoundingBox().maxY - ceiling) < 0.001D,
                    "The TNT must come out under the ceiling at " + ceiling + ", its top is at " + tnt.getBoundingBox().maxY);
            helper.assertTrue(Math.abs(item.getBoundingBox().maxY - ceiling) < 0.001D,
                    "The item must come out under the ceiling at " + ceiling + ", its top is at " + item.getBoundingBox().maxY);
            scene.finish();
        });
    }

    // ServerEntity sends what moved by a short delta in these steps, and VecDeltaCodec is what a client decodes it with.
    private static Vec3 toldToAClient(Vec3 position) {
        VecDeltaCodec codec = new VecDeltaCodec();
        return codec.decode(codec.encodeX(position), codec.encodeY(position), codec.encodeZ(position));
    }

    @GameTest(template = "empty", batch = "cmFieldKey", timeoutTicks = 300)
    public static void itsKeyStartsAndEndsItAtThePlayersNumbers(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-key", 8.0D);
        scene.ceiling(3);
        Cow cow = scene.cow(scene.origin.add(2.0D, 0.0D, 0.0D));
        StandPower power = scene.power;
        Ability ability = power.getAbility(CMoonAddon.GRAVITY_FIELD);
        helper.assertTrue(ability instanceof GravityFieldAbility, "Premise: the field's ability: " + ability);

        power.setStamina(GravityFieldAbility.STAMINA_COST - 1.0F);
        ConditionCheck low = ability.checkSpecificConditions(power);
        helper.assertTrue(!low.isPositive() && low.getWarning() == null, "Below 200 stamina it is refused silently");

        float full = power.getMaxStamina();
        power.setStamina(full);
        EntityActionInstance early = scene.press(ability);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(early.getPhase() == ActionPhase.BUTTON_CHARGE, "Premise: still charging");
            AbilityInput.keyRelease(KEY, scene.user);
            helper.assertTrue(!GravityField.isRunning(scene.user) && power.getStamina() == full
                            && power.getAbilityCooldown(CMoonAddon.GRAVITY_FIELD) == 0,
                    "Letting go before the 25 ticks must cost nothing and start nothing");
        });
        helper.runAfterDelay(8, () -> scene.press(ability));
        helper.runAfterDelay(8 + GravityFieldAbility.HOLD_TO_FIRE_TICKS - 2, () -> helper.assertTrue(
                !GravityField.isRunning(scene.user), "The field started before its 25 ticks"));
        helper.runAfterDelay(8 + GravityFieldAbility.HOLD_TO_FIRE_TICKS + 4, () -> {
            AbilityInput.keyRelease(KEY, scene.user);
            helper.assertTrue(GravityField.isRunning(scene.user), "A full hold must start the field");
            // the bar fills again by some 2 a tick
            helper.assertTrue(power.getStamina() <= full - GravityFieldAbility.STAMINA_COST + 20.0F,
                    "It costs 200, left " + power.getStamina() + " of " + full);
            helper.assertTrue(power.getAbilityCooldown(CMoonAddon.GRAVITY_FIELD) > 0, "It takes its cooldown");
            helper.assertTrue(DirectionalGravityApi.getEffectiveDirection(cow) == Direction.UP, "The cow was not taken");
            power.setStamina(0.0F);
        });
        helper.runAfterDelay(8 + GravityFieldAbility.HOLD_TO_FIRE_TICKS + 30, () -> {
            helper.assertTrue(power.getAbilityCooldown(CMoonAddon.GRAVITY_FIELD) == 0, "Premise: the cooldown is over");
            helper.assertTrue(ability.checkSpecificConditions(power).isPositive(),
                    "A running field can be turned off on an empty bar");
            scene.press(ability);
        });
        helper.runAfterDelay(8 + GravityFieldAbility.HOLD_TO_FIRE_TICKS * 2 + 34, () -> {
            AbilityInput.keyRelease(KEY, scene.user);
            helper.assertTrue(!GravityField.isRunning(scene.user), "The second hold must end the field");
            scene.nobodyIsHeld(cow);
            helper.assertTrue(scene.user.hasEffect(MobEffects.SLOW_FALLING) && cow.hasEffect(MobEffects.SLOW_FALLING),
                    "The end gives everyone the build's Slow Falling");
            helper.assertTrue(scene.user.getEffect(MobEffects.SLOW_FALLING).getDuration() <= GravityField.FALL_PROTECTION_TICKS,
                    "Slow Falling of 100 ticks");
            scene.finish();
        });
    }

    @GameTest(template = "empty", batch = "cmFieldRecall", timeoutTicks = 120)
    public static void itGoesOnThroughARecallAndEndsWithTheStand(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-recall", 8.0D);
        scene.ceiling(3);
        Cow cow = scene.cow(scene.origin.add(2.0D, 0.0D, 0.0D));
        GravityField.toggle(scene.user);
        GravityField field = GravityField.of(scene.user);
        helper.assertTrue(field != null, "Premise: the field started");

        CMoonAddon.C_MOON.get().forceUnsummon(scene.user, scene.power);
        helper.assertTrue(!scene.power.isSummoned() && scene.stand.isRemoved(), "Premise: the Stand was not recalled");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(GravityField.of(scene.user) == field, "The field ended with the recall");
            scene.held(field, scene.user, "the user after the recall");
            scene.held(field, cow, "a cow after the recall");
            Cow late = scene.cow(scene.origin.add(-2.0D, 0.0D, 0.0D));
            helper.runAfterDelay(2, () -> {
                scene.held(field, late, "a cow that came with the Stand away");
                helper.assertTrue(CMoonAddon.C_MOON.get().summon(scene.user, scene.power), "Premise: the next summon");
                CMoonStandEntity next = (CMoonStandEntity) scene.power.getSummonedStandEntity();
                helper.runAfterDelay(2, () -> {
                    scene.held(field, next, "the next Stand");
                    helper.assertTrue(GravityField.of(scene.user) == field, "The next summon started another field");

                    CMoonAddon.C_MOON.get().forceUnsummon(scene.user, scene.power);
                    scene.power.setStand(null);
                    helper.runAfterDelay(2, () -> {
                        helper.assertTrue(!GravityField.isRunning(scene.user), "The field outlived the Stand");
                        scene.nobodyIsHeld(cow, late);
                        scene.finish();
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "cmFieldDeath", timeoutTicks = 60)
    public static void itEndsWithItsUsersDeath(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-death", 8.0D);
        scene.ceiling(3);
        Cow cow = scene.cow(scene.origin.add(2.0D, 0.0D, 0.0D));
        GravityField.toggle(scene.user);
        helper.assertTrue(GravityField.of(scene.user) != null, "Premise: the field started");
        scene.user.kill();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!scene.user.isAlive() && !GravityField.isRunning(scene.user), "The field outlived its user");
            scene.nobodyIsHeld(cow);
            helper.assertTrue(cow.hasEffect(MobEffects.SLOW_FALLING), "The cow must come down gently");
            scene.finish();
        });
    }

    @GameTest(template = "empty", batch = "cmFieldLogout", timeoutTicks = 60)
    public static void itEndsWithItsUsersLogoutAndALeavingPlayerIsLetGoBeforeHeIsSaved(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-logout", 8.0D);
        scene.ceiling(3);
        Cow cow = scene.cow(scene.origin.add(2.0D, 0.0D, 0.0D));
        RecordingServerPlayer leaving = scene.other("cm-field-leaving", scene.origin.add(-2.0D, 0.0D, 0.0D));
        GravityField.toggle(scene.user);
        GravityField field = GravityField.of(scene.user);
        helper.assertTrue(field != null, "Premise: the field started");
        scene.held(field, leaving.player, "another player");

        Vec3 hanging = leaving.player.position();
        EventHooks.firePlayerLoggedOut(leaving.player);
        helper.assertTrue(GravityField.holding(leaving.player) == null
                        && DirectionalGravityApi.getEffectiveDirection(leaving.player) == Direction.DOWN,
                "A player who leaves under a field would be saved inside the ceiling he hung from");
        helper.assertTrue(leaving.player.getBoundingBox().minY < hanging.y,
                "The saved body must stand clear of the ceiling: feet at " + leaving.player.getY() + ", hung at " + hanging.y);
        helper.assertTrue(GravityField.of(scene.user) == field, "One leaving player ended the field");

        EventHooks.firePlayerLoggedOut(scene.user);
        helper.assertTrue(!GravityField.isRunning(scene.user), "The field outlived its user's logout");
        scene.nobodyIsHeld(cow);
        scene.finish();
    }

    @GameTest(template = "empty", batch = "cmFieldUnload", timeoutTicks = 60)
    public static void itEndsWhenItsUserLeavesTheLevelAnyOtherWay(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-unload", 8.0D);
        scene.ceiling(3);
        Cow owner = scene.cow(scene.origin.add(0.0D, 0.0D, 3.0D));
        StandPower power = PowerClass.STAND.attachGet(owner);
        helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(CMoonAddon.C_MOON.get())).status()
                == StandPowerTransitions.Status.APPLIED, "Premise: a mob can hold C-Moon");
        Cow cow = scene.cow(scene.origin.add(2.0D, 0.0D, 0.0D));
        GravityField.toggle(owner);
        helper.assertTrue(GravityField.of(owner) != null, "Premise: the mob's field started");
        owner.discard();
        scene.nobodyIsHeld(cow);
        helper.assertTrue(GravityField.holding(scene.user) == null, "The user was left held");
        scene.finish();
    }

    @GameTest(template = "empty", batch = "cmFieldLevel", timeoutTicks = 60)
    public static void itEndsWhenItsUserChangesLevelAndLetsGoOfOneWhoDoes(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-level", 8.0D);
        scene.ceiling(3);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "Premise: the server has a Nether");
        Cow cow = scene.cow(scene.origin.add(2.0D, 0.0D, 0.0D));
        RecordingServerPlayer traveller = scene.other("cm-field-traveller", scene.origin.add(-2.0D, 0.0D, 0.0D));
        GravityField.toggle(scene.user);
        GravityField field = GravityField.of(scene.user);
        helper.assertTrue(field != null, "Premise: the field started");
        scene.held(field, traveller.player, "another player");

        Vec3 there = new Vec3(0.5D, 200.0D, 0.5D);
        traveller.player.changeDimension(new DimensionTransition(nether, there, Vec3.ZERO, 0.0F, 0.0F,
                DimensionTransition.DO_NOTHING));
        helper.assertTrue(field.gravityDirection(traveller.player) == Direction.DOWN,
                "The field reaches into another level");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(GravityField.holding(traveller.player) == null
                            && DirectionalGravityApi.getEffectiveDirection(traveller.player) == Direction.DOWN,
                    "One who went through a portal was left held");
            helper.assertTrue(GravityField.of(scene.user) == field, "One traveller ended the field");

            scene.user.changeDimension(new DimensionTransition(nether, there, Vec3.ZERO, 0.0F, 0.0F,
                    DimensionTransition.DO_NOTHING));
            helper.assertTrue(!GravityField.isRunning(scene.user), "The field went through a portal with its user");
            scene.nobodyIsHeld(cow);
            scene.finish();
        });
    }

    @GameTest(template = "empty", batch = "cmFieldTwo", timeoutTicks = 60)
    public static void aSecondFieldWaitsForTheFirstToLetGo(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-two", 8.0D);
        scene.ceiling(3);
        Cow cow = scene.cow(scene.origin.add(2.0D, 0.0D, 0.0D));
        RecordingServerPlayer second = scene.other("cm-field-second", scene.origin.add(-2.0D, 0.0D, 0.0D));
        StandPower power = PowerClass.STAND.attachGet(second.player);
        helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(CMoonAddon.C_MOON.get())).status()
                == StandPowerTransitions.Status.APPLIED, "Premise: the second player has C-Moon");
        GravityField.toggle(scene.user);
        GravityField first = GravityField.of(scene.user);
        GravityField.toggle(second.player);
        GravityField other = GravityField.of(second.player);
        helper.assertTrue(first != null && other != null && first != other, "Premise: two fields");
        scene.held(first, cow, "the cow, by the first field");
        scene.held(first, second.player, "the second user, by the first field");

        first.end();
        helper.assertTrue(DirectionalGravityApi.getEffectiveDirection(scene.user) == Direction.DOWN
                || GravityField.holding(scene.user) == other, "The first field left its user held by itself");
        helper.runAfterDelay(2, () -> {
            scene.held(other, cow, "the cow, by the field still running");
            other.end();
            scene.nobodyIsHeld(cow);
            scene.finish();
        });
    }

    @GameTest(template = "empty", batch = "cmFieldSync", timeoutTicks = 60)
    public static void aClientIsToldWhichWayItFallsAndWhenItStops(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-sync", 8.0D);
        scene.ceiling(3);
        scene.recorded.clear();
        GravityField.toggle(scene.user);
        helper.assertTrue(GravityField.of(scene.user) != null, "Premise: the field started");
        scene.toldOf(CMoonAddon.USER_GRAVITY_FIELD.get(), Direction.UP, "his own field");
        scene.toldOf(CMoonAddon.HELD_BY_GRAVITY_FIELD.get(), Direction.UP, "being held");

        scene.recorded.clear();
        GravityField.toggle(scene.user);
        helper.assertTrue(GravityField.of(scene.user) == null, "Premise: the field ended");
        scene.toldOf(CMoonAddon.USER_GRAVITY_FIELD.get(), null, "the end of his own field");
        scene.toldOf(CMoonAddon.HELD_BY_GRAVITY_FIELD.get(), null, "being let go");
        scene.finish();
    }

    @GameTest(template = "empty", batch = "cmFieldEdge", timeoutTicks = 300)
    public static void oneWhoFallsPastTheEdgeOfHisWallFallsToTheNextSurfaceAndEveryoneTurnsWithHim(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-edge", 8.0D);
        new CMoonChunkTickets(helper).hold(scene.origin.add(-8.0D, 0.0D, -8.0D), scene.origin.add(8.0D, 0.0D, 20.0D));
        // the wall ends at z 4; past its edge a ceiling 6 up is all there is
        scene.wallToTheEast(3);
        scene.plate(6, -3, 3, 9, 15);
        Cow cow = scene.cow(scene.origin.add(1.0D, 0.0D, 0.0D));
        GravityField.toggle(scene.user);
        GravityField field = GravityField.of(scene.user);
        helper.assertTrue(field != null && field.direction() == Direction.EAST, "Premise: the wall is the nearest surface");
        scene.inTheAir(scene.origin);
        int search = GravityField.UNSUPPORTED_TICKS_TO_SEARCH;
        Vec3 pastTheEdge = scene.origin.add(0.0D, 0.0D, 12.0D);

        helper.runAfterDelay(search + 10, () -> {
            helper.assertTrue(field.direction() == Direction.EAST && GravityField.of(scene.user) == field,
                    "A jump off the wall, still within reach of it, turned the field to " + field.direction());
            scene.inTheAir(pastTheEdge);
            scene.user.fallDistance = 40.0F;
            scene.recorded.clear();
        });
        helper.runAfterDelay(search * 2 + 10 - 3, () -> helper.assertTrue(field.direction() == Direction.EAST,
                "The field turned before about a second of falling with nothing along it"));
        helper.runAfterDelay(search * 2 + 10 + 3, () -> {
            helper.assertTrue(GravityField.of(scene.user) == field && field.direction() == Direction.UP,
                    "A second past the wall's edge the ceiling over him must be the new floor, the field falls "
                            + field.direction() + ", running " + (GravityField.of(scene.user) == field));
            scene.held(field, scene.user, "the user after the turn");
            scene.held(field, cow, "a cow on the old wall");
            helper.assertTrue(scene.user.fallDistance == 0.0F,
                    "The sideways fall counts as a height: " + scene.user.fallDistance);
            scene.toldOf(CMoonAddon.USER_GRAVITY_FIELD.get(), Direction.UP, "the turn of his own field");
            scene.toldOf(CMoonAddon.HELD_BY_GRAVITY_FIELD.get(), Direction.UP, "the turn of what holds him");
            // over the ceiling now, with only that ceiling below within reach
            scene.inTheAir(pastTheEdge.add(0.0D, 40.0D, 0.0D));
        });
        helper.runAfterDelay(search * 3 + 10 + 6, () -> {
            helper.assertTrue(GravityField.of(scene.user) == field && field.direction() == Direction.DOWN,
                    "With only a floor in reach the field must fall down, as one started there: " + field.direction());
            scene.nobodyIsHeld(cow);
            helper.assertTrue(scene.user.hasEffect(MobEffects.SLOW_FALLING) && cow.hasEffect(MobEffects.SLOW_FALLING),
                    "Those let go come down gently");
            scene.inTheAir(pastTheEdge.add(0.0D, 120.0D, 0.0D));
        });
        helper.runAfterDelay(search * 4 + 10 + 12, () -> {
            helper.assertTrue(GravityField.of(scene.user) == field && field.direction() == Direction.DOWN,
                    "A field on the floor searched again: running " + (GravityField.of(scene.user) == field)
                            + ", falling " + field.direction());
            scene.finish();
        });
    }

    @GameTest(template = "empty", batch = "cmFieldNowhere", timeoutTicks = 120)
    public static void withNothingToLandOnAnywhereTheFieldEnds(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-nowhere", 8.0D);
        scene.wallToTheEast(3);
        Cow cow = scene.cow(scene.origin.add(1.0D, 0.0D, 0.0D));
        GravityField.toggle(scene.user);
        GravityField field = GravityField.of(scene.user);
        helper.assertTrue(field != null && field.direction() == Direction.EAST, "Premise: the wall is the nearest surface");
        // 55 over the wall's top: nothing within 50 either way
        scene.inTheAir(scene.origin.add(0.0D, 60.0D, 0.0D));
        int search = GravityField.UNSUPPORTED_TICKS_TO_SEARCH;
        helper.runAfterDelay(search - 3, () -> helper.assertTrue(GravityField.of(scene.user) == field,
                "The field ended before about a second of falling"));
        helper.runAfterDelay(search + 3, () -> {
            helper.assertTrue(!GravityField.isRunning(scene.user),
                    "With nothing within 50 blocks anywhere the field must end, as it does at the start");
            scene.nobodyIsHeld(cow);
            helper.assertTrue(scene.user.hasEffect(MobEffects.SLOW_FALLING), "The end gives the build's Slow Falling");
            scene.finish();
        });
    }

    @GameTest(template = "empty", batch = "cmFieldSand", timeoutTicks = 100)
    public static void aFallingBlockLandsOnACeilingAsABlock(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-sand", 8.0D);
        scene.ceiling(4);
        landsAsABlock(scene, Direction.UP, scene.origin.add(2.0D, 1.0D, 0.0D), new BlockPos(2, 3, 0));
    }

    @GameTest(template = "empty", batch = "cmFieldSandWall", timeoutTicks = 100)
    public static void aFallingBlockLandsOnAWallAsABlock(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-sand-wall", 8.0D);
        scene.wallToTheEast(3);
        landsAsABlock(scene, Direction.EAST, scene.origin.add(1.0D, 1.0D, 2.0D), new BlockPos(2, 1, 2));
    }

    private static void landsAsABlock(Scene scene, Direction expected, Vec3 at, BlockPos onTheFloor) {
        GameTestHelper helper = scene.helper;
        GravityField.toggle(scene.user);
        GravityField field = GravityField.of(scene.user);
        helper.assertTrue(field != null && field.direction() == expected, "Premise: the field falls " + expected);
        BlockPos landing = scene.base.offset(onTheFloor);
        scene.blocks.add(landing);
        Entity sand = scene.thing(EntityType.FALLING_BLOCK.create(scene.level), at);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(sand.isRemoved(), "The falling block hangs on the new floor at " + sand.position());
            helper.assertTrue(scene.level.getBlockState(landing).is(Blocks.SAND),
                    "It must land as a block next to the floor at " + landing + ", there is "
                            + scene.level.getBlockState(landing));
            List<ItemEntity> dropped = scene.level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(landing).inflate(4.0D));
            helper.assertTrue(dropped.isEmpty(), "It dropped as an item: " + dropped);
            scene.finish();
        });
    }

    @GameTest(template = "empty", batch = "cmFieldMob", timeoutTicks = 60)
    public static void aStandBearingMobHasNeitherGravitySkillToPress(GameTestHelper helper) {
        Scene scene = new Scene(helper, "cm-field-mob", 8.0D);
        Cow owner = scene.cow(scene.origin.add(0.0D, 0.0D, 3.0D));
        StandPower power = PowerClass.STAND.attachGet(owner);
        helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(CMoonAddon.C_MOON.get())).status()
                == StandPowerTransitions.Status.APPLIED, "Premise: a mob can hold C-Moon");
        power.setResolveLevel(power.getMaxResolveLevel());
        helper.assertTrue(CMoonAddon.C_MOON.get().summon(owner, power), "Premise: the mob's C-Moon summoned");
        power.setStamina(power.getMaxStamina());
        helper.runAfterDelay(3, () -> {
            // the pool a Stand-bearing mob picks its moves from
            Set<String> mob = usable(power);
            Set<String> player = usable(scene.power);
            for (String name : List.of(CMoonAddon.GRAVITY_FIELD, CMoonAddon.GRAVITY_SHIFT)) {
                helper.assertTrue(player.contains(name), "Premise: a player can press " + name + ", he has " + player);
                helper.assertTrue(!mob.contains(name), "A mob can press " + name);
                var checked = power.updateAvailableMoves().getContextVariationContainer(power.getAbility(name));
                helper.assertTrue(checked != null && !AbilityInput.withConditionCheck(checked, owner, InputMethod.HOLD),
                        "A mob's press of " + name + " went through");
            }
            helper.assertTrue(mob.contains(CMoonAddon.CENTER_OF_GRAVITY),
                    "The mob lost its other skills too: " + mob);
            if (power.isSummoned()) {
                CMoonAddon.C_MOON.get().forceUnsummon(owner, power);
            }
            scene.finish();
        });
    }

    private static Set<String> usable(StandPower power) {
        Set<String> names = new HashSet<>();
        power.updateAvailableMoves().inMovesetAndCanBeUsed.values().forEach(ability -> names.add(ability.name()));
        return names;
    }

    private static final class Scene implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final Vec3 origin;
        final BlockPos base;
        final RecordingServerPlayer recorded;
        final ServerPlayer user;
        final StandPower power;
        final CMoonStandEntity stand;
        final List<Entity> entities = new ArrayList<>();
        final List<RecordingServerPlayer> others = new ArrayList<>();
        final List<BlockPos> blocks = new ArrayList<>();
        boolean closed;

        Scene(GameTestHelper helper, String name, double reachX) {
            this.helper = helper;
            level = helper.getLevel();
            base = helper.absolutePos(BlockPos.ZERO).above(HEIGHT);
            origin = Vec3.atBottomCenterOf(base);
            helper.testInfo.addListener(this);
            new CMoonChunkTickets(helper).hold(origin.add(-8.0D, 0.0D, -8.0D), origin.add(reachX, 0.0D, 8.0D));
            recorded = new RecordingServerPlayer(helper, name, origin);
            user = recorded.player;
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(CMoonAddon.C_MOON.get())).status()
                    == StandPowerTransitions.Status.APPLIED, "Premise: C-Moon granted");
            power.setResolveLevel(power.getMaxResolveLevel());
            helper.assertTrue(CMoonAddon.C_MOON.get().summon(user, power)
                    && power.getSummonedStandEntity() instanceof CMoonStandEntity, "Premise: C-Moon summoned");
            stand = (CMoonStandEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            power.setStamina(power.getMaxStamina());
        }

        /** A stone plate that blocks above, with air on the way to it on every side, so the plate is the nearest surface. */
        int ceiling(int height) {
            return ceiling(height, 3);
        }

        int ceiling(int height, int toTheEast) {
            for (int x = -3; x <= toTheEast; x++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos plate = base.offset(x, height, z);
                    level.setBlockAndUpdate(plate, Blocks.STONE.defaultBlockState());
                    blocks.add(plate);
                }
            }
            return base.getY() + height;
        }

        int wallToTheEast(int distance) {
            for (int y = -3; y <= 5; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos plate = base.offset(distance, y, z);
                    level.setBlockAndUpdate(plate, Blocks.STONE.defaultBlockState());
                    blocks.add(plate);
                }
            }
            return base.getX() + distance;
        }

        /** A stone plate at this height over the box between the corners, which goes when the test ends. */
        void plate(int y, int fromX, int toX, int fromZ, int toZ) {
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    BlockPos plate = base.offset(x, y, z);
                    level.setBlockAndUpdate(plate, Blocks.STONE.defaultBlockState());
                    blocks.add(plate);
                }
            }
        }

        /** The user as a falling body: a server moves no player, so he stays where a test puts him. */
        void inTheAir(Vec3 at) {
            user.setNoGravity(false);
            user.moveTo(at.x, at.y, at.z);
            user.setOnGround(false);
        }

        Entity tnt(Vec3 at) {
            PrimedTnt tnt = EntityType.TNT.create(level);
            helper.assertTrue(tnt != null, "Premise: no TNT");
            tnt.setFuse(400);
            return thing(tnt, at);
        }

        Entity thing(Entity thing, Vec3 at) {
            helper.assertTrue(thing != null, "Premise: nothing to add at " + at);
            thing.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            thing.setDeltaMovement(Vec3.ZERO);
            add(thing);
            return thing;
        }

        Cow cow(Vec3 at) {
            Cow cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Premise: no cow");
            cow.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            cow.setPersistenceRequired();
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            cow.setHealth(200.0F);
            add(cow);
            return cow;
        }

        ItemEntity item(Vec3 at) {
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STICK));
            item.setDeltaMovement(Vec3.ZERO);
            item.setNeverPickUp();
            add(item);
            return item;
        }

        Arrow arrow(Vec3 at) {
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setPos(at);
            arrow.setDeltaMovement(Vec3.ZERO);
            add(arrow);
            return arrow;
        }

        RecordingServerPlayer other(String name, Vec3 at) {
            RecordingServerPlayer other = new RecordingServerPlayer(helper, name, at);
            others.add(other);
            return other;
        }

        private void add(Entity entity) {
            helper.assertTrue(level.addFreshEntity(entity), "Premise: " + entity + " was not added");
            entities.add(entity);
        }

        EntityActionInstance press(Ability ability) {
            var checked = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(checked != null && AbilityInput.withConditionCheck(checked, user, InputMethod.HOLD),
                    "Premise: the input was refused");
            BufferingState buffering = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, checked.ability, user, null, InputMethod.HOLD, 0, buffering, ability.getAbilityId());
            helper.assertTrue(buffering.isActionSuccess, "Premise: the press did not start");
            return power.getSummonedStandEntity().getCurStandAction();
        }

        void held(GravityField field, Entity entity, String what) {
            helper.assertTrue(GravityField.holding(entity) == field, what + " is not held");
            helper.assertTrue(DirectionalGravityApi.getEffectiveDirection(entity) == field.direction(),
                    what + " does not fall with the field: " + DirectionalGravityApi.getEffectiveDirection(entity));
        }

        void nobodyIsHeld(Entity... rest) {
            List<Entity> all = new ArrayList<>(List.of(rest));
            all.add(user);
            for (Entity entity : all) {
                helper.assertTrue(GravityField.holding(entity) == null, entity + " is still marked as held");
                helper.assertTrue(DirectionalGravityApi.getEffectiveDirection(entity) == Direction.DOWN
                                && DirectionalGravityApi.getDirection(entity) == Direction.DOWN,
                        entity + " has gravity left: " + DirectionalGravityApi.getEffectiveDirection(entity));
            }
        }

        void toldOf(AttachmentType<?> type, Direction direction, String what) {
            boolean told = false;
            for (ClientboundCustomPayloadPacket packet : recorded.packets(ClientboundCustomPayloadPacket.class)) {
                if (packet.payload() instanceof SyncAttachmentsPayload sync
                        && sync.target() instanceof SyncAttachmentsPayload.EntityTarget target
                        && target.entity() == user.getId()
                        && sync.types().equals(List.of(type))) {
                    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(sync.syncPayload()));
                    boolean present = buffer.readBoolean();
                    told = direction == null
                            ? !present
                            : present && GravityField.CLIENT_VIEW.decode(buffer).direction() == direction;
                }
            }
            helper.assertTrue(told, "The player's client was not told of " + what);
        }

        void finish() {
            close();
            helper.succeed();
        }

        void close() {
            if (closed) return;
            closed = true;
            AbilityInput.keyRelease(KEY, user);
            if (power.isSummoned()) {
                CMoonAddon.C_MOON.get().forceUnsummon(user, power);
            }
            entities.forEach(Entity::discard);
            others.forEach(RecordingServerPlayer::close);
            recorded.close();
            blocks.forEach(plate -> level.setBlockAndUpdate(plate, Blocks.AIR.defaultBlockState()));
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) { close(); }
    }
}
