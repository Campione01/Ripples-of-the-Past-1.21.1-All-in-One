package rotp.addons.echoes.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.echoes.EchoesGameEvents;
import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.ability.EchoesGravityAbility;
import rotp.addons.echoes.block.EchoesOnomatopoeiaMarkBlock;
import rotp.addons.echoes.block.EchoesOnomatopoeiaMarkBlockEntity;
import rotp.addons.echoes.block.EchoesSoundMarkBlockEntity;
import rotp.addons.echoes.entity.EchoesOnomatopoeiaWordEntity;
import rotp.addons.echoes.entity.EchoesTailNeedleEntity;
import rotp.addons.echoes.init.EchoesBlocks;
import rotp.addons.echoes.init.EchoesDataComponents;
import rotp.addons.echoes.init.EchoesMobEffects;
import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.stand.EchoesStandEntity;
import rotp.addons.echoes.state.EchoesFreezeState;
import rotp.addons.echoes.state.EchoesForms;
import rotp.addons.echoes.state.EchoesGravityService;
import rotp.addons.echoes.state.EchoesOnomatopoeia;
import rotp.addons.echoes.state.EchoesOnomatopoeiaService;
import rotp.addons.echoes.state.EchoesSoundService;
import rotp.addons.echoes.state.EchoesUserState;
import rotp.addons.echoes.state.GravityMarkSavedData;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder("rotp_echoes_pilot")
@PrefixGameTestTemplate(false)
public final class EchoesPilotGameTests {
    private static final Vec3 MOTION = new Vec3(0.2D, 0.4D, -0.2D);
    private static final short SHORT_BAR_KEY = 171;

    private EchoesPilotGameTests() {}

    // Its near pig stands outside the test's own bounds; a default-batch neighbour's succeed() discarded it.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesCarrierField")
    public static void carrierInventoryTickAppliesDragAndPinsOnlyEligibleNeighbours(GameTestHelper helper) {
        ServerPlayer player = player(helper, "EchoesCarrier");
        Pig near = pig(helper, player.position().add(2.79D, 0.0D, 0.0D));
        Pig far = pig(helper, player.position().add(-3.2D, 0.0D, 0.0D));
        BlockPos water = player.blockPosition();
        helper.runAfterDelay(1, () -> {
            try {
                ItemStack expired = new ItemStack(Items.COBBLESTONE);
                expired.set(EchoesDataComponents.GRAVITY_INFUSED_UNTIL.get(), helper.getLevel().getGameTime());
                player.getInventory().setItem(0, expired);
                helper.assertTrue(expired.has(EchoesDataComponents.GRAVITY_INFUSED_UNTIL.get()),
                        "Setup: the expired stack has no infusion component");
                player.setDeltaMovement(MOTION);
                near.setDeltaMovement(MOTION);
                EchoesGameEvents.onPlayerTick(new PlayerTickEvent.Post(player));
                helper.assertTrue(!expired.has(EchoesDataComponents.GRAVITY_INFUSED_UNTIL.get()),
                        "The server inventory tick retained an expired infusion component");
                motion(helper, player.getDeltaMovement(), MOTION, "expired infusion must not drag its carrier");
                motion(helper, near.getDeltaMovement(), MOTION, "expired infusion must not pin neighbours");
                int[] counts = {1, 2, 5, 6, 30};
                double[] factors = {0.85D, 0.7D, 0.25D, 0.2D, 0.2D};
                for (int i = 0; i < counts.length; i++) {
                    inventory(player, counts[i]);
                    player.setDeltaMovement(MOTION);
                    near.setDeltaMovement(MOTION);
                    far.setDeltaMovement(MOTION);
                    EchoesGameEvents.onPlayerTick(new PlayerTickEvent.Post(player));
                    motion(helper, player.getDeltaMovement(), MOTION.scale(factors[i]), "carrier drag");
                    motion(helper, near.getDeltaMovement(), new Vec3(0.06D, -0.1D, -0.06D), "near field");
                    motion(helper, far.getDeltaMovement(), MOTION, "outside field");
                }
                // A real server player tick posts PlayerTickEvent.Post on the game bus; no gravity keeps the
                // carrier in place so the neighbour stays inside the field.
                inventory(player, 1);
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                player.setNoGravity(true);
                player.setDeltaMovement(Vec3.ZERO);
                near.setDeltaMovement(MOTION);
                player.doTick();
                player.setNoGravity(false);
                helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                        "A server player tick did not reach the carrier handler");
                motion(helper, near.getDeltaMovement(), new Vec3(0.06D, -0.1D, -0.06D), "player tick near field");
                for (GameType mode : new GameType[] {GameType.CREATIVE, GameType.SPECTATOR}) {
                    player.setGameMode(mode);
                    near.setDeltaMovement(MOTION);
                    EchoesGameEvents.onPlayerTick(new PlayerTickEvent.Post(player));
                    motion(helper, near.getDeltaMovement(), MOTION, mode + " must not pin neighbours");
                }
                player.setGameMode(GameType.SURVIVAL);
                inventory(player, 1);
                helper.getLevel().setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(water.above(), Blocks.WATER.defaultBlockState());
                player.baseTick();
                helper.assertTrue(player.isInWater(), "Setup: the carrier is not in real water");
                player.setSwimming(true);
                player.xxa = 1.0F;
                player.zza = 1.0F;
                player.setDeltaMovement(MOTION);
                EchoesGameEvents.onPlayerTick(new PlayerTickEvent.Post(player));
                motion(helper, player.getDeltaMovement(), new Vec3(0.02D, -0.8D, -0.02D), "water drag");
                helper.assertTrue(!player.isSwimming() && player.xxa == 0.0F && player.zza == 0.0F,
                        "The infused carrier kept its swimming input");
            } finally {
                near.discard();
                far.discard();
                helper.getLevel().setBlockAndUpdate(water, Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(water.above(), Blocks.AIR.defaultBlockState());
                remove(helper, player);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void finisherCleanupEndsSelfAndInfusionButKeepsTargetAndHandFreeze(GameTestHelper helper) {
        ServerPlayer player = player(helper, "EchoesFinisher");
        EchoesUserState state = RotpEchoes.userState(player);
        GravityMarkSavedData marks = GravityMarkSavedData.get(helper.getLevel());
        BlockPos mark = player.blockPosition().below();
        BlockPos otherMark = mark.east(2);
        UUID otherOwner = UUID.randomUUID();
        try {
            EchoesGravityService.perform(player, EchoesGravityAbility.Mode.FREEZE_SELF);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.FREEZE_SELF
                            && player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && player.hasEffect(MobEffects.WEAKNESS)
                            && heldJump(player, EchoesGravityService.SELF_FREEZE_JUMP_MODIFIER),
                    "Setup: Freeze Self did not start");
            EchoesGravityService.onAct3FinisherHit(player);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.NONE
                            && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && !player.hasEffect(MobEffects.WEAKNESS)
                            && !heldJump(player, EchoesGravityService.SELF_FREEZE_JUMP_MODIFIER),
                    "The finisher did not release the user's self-freeze effects");
            state.activateGravityInfuse();
            inventory(player, 2);
            marks.mark(mark, player.getUUID(), helper.getLevel().getGameTime() + 600L);
            marks.mark(otherMark, otherOwner, helper.getLevel().getGameTime() + 600L);
            EchoesGravityService.onAct3FinisherHit(player);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.NONE
                            && marks.mark(mark, helper.getLevel().getGameTime()) == null
                            && !EchoesGravityService.isInfused(player.getInventory().getItem(0),
                                    helper.getLevel().getGameTime(), false)
                            && !EchoesGravityService.isInfused(player.getInventory().getItem(1),
                                    helper.getLevel().getGameTime(), false),
                    "The finisher left infused inventory or its owned block mark");
            GravityMarkSavedData.Mark kept = marks.mark(otherMark, helper.getLevel().getGameTime());
            helper.assertTrue(kept != null && otherOwner.equals(kept.owner()),
                    "The finisher removed a block mark owned by another player");
            UUID target = UUID.randomUUID();
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 10));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 2));
            state.activateTargetFreeze(target);
            EchoesGravityService.onAct3FinisherHit(player);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.FREEZE_TARGET
                            && target.equals(state.frozenTarget()), "The finisher released a target freeze");
            helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && player.hasEffect(MobEffects.WEAKNESS),
                    "The finisher stripped self-freeze effects during a target freeze");
            state.activateHandFreeze(target);
            EchoesGravityService.onAct3FinisherHit(player);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.HAND_FREEZE
                            && target.equals(state.frozenTarget()), "The finisher released a hand freeze");
        } finally {
            marks.removeOwner(otherOwner);
            marks.removeOwner(player.getUUID());
            remove(helper, player);
        }
        helper.succeed();
    }

    // 092 GravityInfuseAction.getTargetBlock cast an OUTLINE ray: a short grass in front of a wall is the block that
    // gets the mark, not the wall behind it.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void infuseMarksTheGrassItLooksAtNotTheWallBehindIt(GameTestHelper helper) {
        ServerPlayer player = freezer(helper, "EchoesInfuseGrass");
        GravityMarkSavedData marks = GravityMarkSavedData.get(helper.getLevel());
        BlockPos eyeBlock = BlockPos.containing(player.getEyePosition());
        BlockPos grass = eyeBlock.south(3);
        BlockPos wall = eyeBlock.south(6);
        try {
            player.setYRot(0.0F);
            player.setXRot(0.0F);
            helper.getLevel().setBlock(grass, Blocks.SHORT_GRASS.defaultBlockState(), 3);
            helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            EchoesGravityService.perform(player, EchoesGravityAbility.Mode.INFUSE);
            long time = helper.getLevel().getGameTime();
            helper.assertTrue(marks.mark(grass, time) != null && marks.mark(wall, time) == null,
                    "Gravity Infuse looked at a short grass in front of a wall: 092's OUTLINE ray marks the grass, grass "
                            + "mark=" + marks.mark(grass, time) + ", wall mark=" + marks.mark(wall, time));
        } finally {
            marks.removeOwner(player.getUUID());
            helper.getLevel().setBlock(grass, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            remove(helper, player);
        }
        helper.succeed();
    }

    // 092 GravityFreezeSelfAction: Slowness XI, jump -10 and Weakness III (field_76437_t) while it lasts, all three
    // removed when it ends. Jump -10 kept the 1.16 user on the ground; 1.21 clamps it to Jump Boost I.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void selfFreezeWeakensItsUserAndReleasesTheWeakness(GameTestHelper helper) {
        ServerPlayer player = player(helper, "EchoesSelfFreeze");
        EchoesUserState state = RotpEchoes.userState(player);
        try {
            EchoesGravityService.perform(player, EchoesGravityAbility.Mode.FREEZE_SELF);
            MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.FREEZE_SELF
                            && player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "Setup: Freeze Self did not start");
            helper.assertTrue(weakness != null && weakness.getAmplifier() == 2
                            && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                    "1.16 Freeze Self gives its user Weakness III, not Resistance; the port gave weakness "
                            + weakness + ", resistance " + player.getEffect(MobEffects.DAMAGE_RESISTANCE));
            float rise = jumpRise(player);
            helper.assertTrue(rise == 0.0F && !player.hasEffect(MobEffects.JUMP),
                    "1.16 Freeze Self's jump -10 keeps its user on the ground; the port's user jumps " + rise
                            + " with jump " + player.getEffect(MobEffects.JUMP));
            EchoesGravityService.perform(player, EchoesGravityAbility.Mode.FREEZE_SELF);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.NONE
                            && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && !player.hasEffect(MobEffects.WEAKNESS)
                            && jumpRise(player) > 0.0F,
                    "Ending Freeze Self left its slowness, weakness or held jump on the user");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void targetFreezeGivesTheDonorDisablerEffects(GameTestHelper helper) {
        freezeScene(helper, EchoesGravityAbility.Mode.FREEZE_TARGET);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void handFreezeGivesTheDonorDisablerEffects(GameTestHelper helper) {
        freezeScene(helper, EchoesGravityAbility.Mode.HAND_FREEZE);
    }

    /**
     * 092 GravityFreezeAction and HandFreezeAction call DisablerTagHandler.disable: Slowness, Mining Fatigue, Weakness,
     * Jump and Hunger at 255, no Resistance and no Blindness, and DisablerTagHandler.enable removes those five. The
     * frozen target cannot walk, jump, mine or deal melee damage, its food bar drains, and it can still be hurt.
     */
    private static void freezeScene(GameTestHelper helper, EchoesGravityAbility.Mode mode) {
        ServerPlayer user = freezer(helper, "EchoesFreezer");
        StandPower power = PowerClass.STAND.attachGet(user);
        Player target = null;
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            stand.moveTo(user.getX(), user.getY(), user.getZ());
            target = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "EchoesFrozen")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            target.setNoGravity(true);
            target.moveTo(user.getX(), user.getY(), user.getZ() + 2.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Setup: the target was not added");
            // A potion the target already had is not the freeze's to take.
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            float digSpeed = target.getDestroySpeed(Blocks.STONE.defaultBlockState());
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());

            EchoesGravityService.perform(user, mode);
            EchoesFreezeState frozen = RotpEchoes.freezeState(target);
            helper.assertTrue(frozen.active(), "Setup: " + mode + " did not freeze the target");
            StringBuilder effects = new StringBuilder();
            for (MobEffectInstance effect : target.getActiveEffects()) {
                effects.append(' ').append(effect);
            }
            for (var effect : List.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.WEAKNESS,
                    MobEffects.HUNGER)) {
                MobEffectInstance active = target.getEffect(effect);
                helper.assertTrue(active != null && active.getAmplifier() == 255,
                        "1.16 " + mode + " gives Slowness, Mining Fatigue, Weakness, Jump and Hunger at 255 and no"
                                + " Blindness or Resistance; the port gave" + effects);
            }
            MobEffectInstance resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(resistance != null && resistance.getAmplifier() == 0
                            && !target.hasEffect(MobEffects.BLINDNESS) && !target.hasEffect(MobEffects.JUMP),
                    "1.16 " + mode + " adds no Resistance, Blindness or Jump Boost; the port gave" + effects);
            float health = target.getHealth();
            target.hurt(target.damageSources().generic(), 4.0F);
            helper.assertTrue(target.getHealth() < health,
                    "1.16: a target frozen by " + mode + " can be hurt; the port's took no damage");
            float rise = jumpRise(target);
            helper.assertTrue(target.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.0D && rise == 0.0F,
                    "1.16: a target frozen by " + mode + " cannot walk or jump; speed "
                            + target.getAttributeValue(Attributes.MOVEMENT_SPEED) + ", jump " + rise);
            float frozenDigSpeed = target.getDestroySpeed(Blocks.STONE.defaultBlockState());
            helper.assertTrue(frozenDigSpeed < digSpeed * 0.001F
                            && target.getAttributeValue(Attributes.ATTACK_DAMAGE) == 0.0D,
                    "1.16: a target frozen by " + mode + " cannot mine or deal melee damage; dig speed "
                            + frozenDigSpeed + " of " + digSpeed + ", attack damage "
                            + target.getAttributeValue(Attributes.ATTACK_DAMAGE));
            float exhaustion = target.getFoodData().getExhaustionLevel();
            target.getEffect(MobEffects.HUNGER).tick(target, () -> {});
            float drained = target.getFoodData().getExhaustionLevel() - exhaustion;
            helper.assertTrue(drained > 1.0F,
                    "1.16 Hunger 255 drains the frozen target's food bar by 1.28 a tick; the port drained " + drained);

            EchoesGravityService.perform(user, mode);
            resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(!frozen.active() && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && !target.hasEffect(MobEffects.DIG_SLOWDOWN) && !target.hasEffect(MobEffects.WEAKNESS)
                            && !target.hasEffect(MobEffects.HUNGER) && jumpRise(target) > 0.0F
                            && resistance != null && resistance.getAmplifier() == 0,
                    "1.16 DisablerTagHandler.enable removes exactly the freeze's effects when " + mode
                            + " ends; the target kept" + activeEffects(target) + ", jump " + jumpRise(target));
        } finally {
            if (target != null) {
                target.discard();
            }
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            remove(helper, user);
        }
        helper.succeed();
    }

    // 092 GravityFreezeSelfAction and the other gravity actions have no stamina condition: StandEntityTask pays
    // staminaCost(50) when the action performs and ignores the result.
    // Every fixture here puts its user at the centre of the template's chunk, so the tests of a batch that share a
    // chunk share that spot; this user stays there for ticks, and a freeze ray that starts inside it takes it.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 60,
            batch = "rotpEchoesShortBarFreezeSelf")
    public static void freezeSelfStartsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesShortBar");
        StandPower power = PowerClass.STAND.attachGet(user);
        EchoesUserState state = RotpEchoes.userState(user);
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.summonLockTicks = 0;
            stand.setNoGravity(true);
            helper.assertTrue(!power.isStaminaInfinite() && power.getMaxStamina() > 50.0F,
                    "Setup: the user needs a finite stamina bar");
            power.setStamina(10.0F);
            Ability freezeSelf = power.getAbility("freeze_self");
            helper.assertTrue(freezeSelf != null, "Setup: ACT3 has no freeze_self");
            var selected = power.updateAvailableMoves().getContextVariationContainer(freezeSelf);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "1.16 starts Freeze Self on any stamina; the port refused it with " + power.getStamina()
                            + " of 50 on the bar");
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(SHORT_BAR_KEY, selected.ability, user, null, InputMethod.CLICK, 0.0F, buffer,
                    freezeSelf.getAbilityId());
            helper.assertTrue(buffer.isActionSuccess, "1.16 starts Freeze Self on any stamina; the port's key press"
                    + " with " + power.getStamina() + " on the bar started nothing");
        } catch (RuntimeException | Error failure) {
            cleanupShortBar(helper, user, power);
            throw failure;
        }
        int[] waited = {0};
        helper.onEachTick(() -> {
            if (state.activeSkill() != EchoesUserState.ActiveSkill.FREEZE_SELF) {
                if (++waited[0] > 40) {
                    cleanupShortBar(helper, user, power);
                    helper.fail("1.16 performs Freeze Self on a short bar; the port's Freeze Self never started");
                }
                return;
            }
            float stamina = power.getStamina();
            cleanupShortBar(helper, user, power);
            helper.assertTrue(stamina == 0.0F,
                    "1.16 pays Freeze Self's 50 from a bar of 10 by draining it to 0; the bar holds " + stamina);
            helper.succeed();
        });
    }

    // 092 GravityProjectileAction.standPerform pays staminaCost(100) itself once it finds a gravity item, and
    // StandEntityTask pays it again after the perform, both results ignored: a throw costs 200.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void gravityProjectileThrowCostsTheDonorTwoHundred(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesThrower");
        StandPower power = PowerClass.STAND.attachGet(user);
        StandEntity stand = null;
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            helper.assertTrue(!power.isStaminaInfinite() && power.getMaxStamina() >= 250.0F,
                    "Setup: the user needs a finite stamina bar of at least 250");
            Ability projectile = power.getAbility("gravity_projectile");
            helper.assertTrue(projectile instanceof EchoesGravityAbility,
                    "Setup: ACT3 has no gravity projectile: " + projectile);
            // the bar before the throw and after it
            float[][] bars = {{250.0F, 50.0F}, {150.0F, 0.0F}};
            for (float[] bar : bars) {
                inventory(user, 1);
                power.setStamina(bar[0]);
                EntityActionInstance action = ((EchoesGravityAbility) projectile)
                        .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
                action.actionPerformStart();
                LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
                discardThrownItems(helper, user);
                helper.assertTrue(user.getInventory().getItem(0).isEmpty(),
                        "Setup: the throw did not take the gravity item");
                helper.assertTrue(power.getStamina() == bar[1],
                        "1.16 pays the gravity throw's 100 twice, 200 in all; from " + bar[0] + " the bar ends at "
                                + bar[1] + ", the port's at " + power.getStamina());
            }
        } finally {
            discardThrownItems(helper, user);
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            remove(helper, user);
        }
        helper.succeed();
    }

    // 092 GravityProjectileAction has no gravity-item condition: pressed with no gravity item it throws nothing, and
    // StandEntityTask still pays its staminaCost(100) after the perform.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 60,
            batch = "rotpEchoesEmptyThrow")
    public static void gravityThrowWithNoItemStillRunsAndPaysItsHundred(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesEmptyThrow");
        StandPower power = PowerClass.STAND.attachGet(user);
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.summonLockTicks = 0;
            stand.setNoGravity(true);
            helper.assertTrue(!power.isStaminaInfinite() && power.getMaxStamina() >= 250.0F,
                    "Setup: the user needs a finite stamina bar of at least 250");
            // 092 unlocks the gravity throw at Resolve level 4.
            power.setResolveLevel(4);
            user.getInventory().clearContent();
            power.setStamina(250.0F);
            Ability projectile = power.getAbility("gravity_projectile");
            helper.assertTrue(projectile instanceof EchoesGravityAbility,
                    "Setup: ACT3 has no gravity projectile: " + projectile);
            var selected = power.updateAvailableMoves().getContextVariationContainer(projectile);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "1.16 runs the gravity throw with no gravity item; the port refused the press ("
                            + (selected != null ? selected.conditionCheck.getWarning() : "not available") + ")");
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(SHORT_BAR_KEY, selected.ability, user, null, InputMethod.CLICK, 0.0F, buffer,
                    projectile.getAbilityId());
            helper.assertTrue(buffer.isActionSuccess,
                    "1.16 runs the gravity throw with no gravity item; the port's key press started nothing");
        } catch (RuntimeException | Error failure) {
            cleanupShortBar(helper, user, power);
            throw failure;
        }
        int[] waited = {0};
        boolean[] closed = {false};
        helper.onEachTick(() -> {
            if (closed[0]) {
                return;
            }
            if (power.getStamina() == 250.0F) {
                if (++waited[0] > 40) {
                    closed[0] = true;
                    cleanupShortBar(helper, user, power);
                    helper.fail("1.16 pays an empty gravity throw's 100 when it performs; the port's bar never moved");
                }
                return;
            }
            closed[0] = true;
            float stamina = power.getStamina();
            int thrown = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, user.getBoundingBox().inflate(16.0D)).size();
            cleanupShortBar(helper, user, power);
            helper.assertTrue(stamina == 150.0F && thrown == 0,
                    "1.16 pays an empty gravity throw's 100 and throws nothing; the bar went from 250 to " + stamina
                            + ", " + thrown + " item(s) thrown");
            helper.succeed();
        });
    }

    // 092 GravityItemEvents gives a carrier Jump -min(count, 5), which 1.16 adds to the jump as 0.1 * (1 - level):
    // no change with one item, 0.1 less for each item from two up to five, and nothing once none is carried. It does
    // not lift a user whom a freeze keeps on the ground.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void carriedGravityItemsLowerTheJumpFromTwoUp(GameTestHelper helper) {
        ServerPlayer player = freezer(helper, "EchoesHeavyCarrier");
        try {
            float plain = jumpRise(player);
            helper.assertTrue(plain > 0.4F, "Setup: the carrier's own jump is " + plain);
            for (int count : new int[] {1, 2, 3, 5, 6, 0}) {
                inventory(player, count);
                EchoesGravityService.tickInventoryGravity(player);
                float expected = plain - 0.1F * Math.max(Math.min(count, 5) - 1, 0);
                float rise = jumpRise(player);
                helper.assertTrue(Math.abs(rise - expected) < 1.0E-4F && !player.hasEffect(MobEffects.JUMP),
                        "1.16 Jump -" + Math.min(count, 5) + " lifts a carrier of " + count + " gravity items by "
                                + expected + "; the port lifts it by " + rise + " with jump "
                                + player.getEffect(MobEffects.JUMP));
            }
            inventory(player, 1);
            EchoesGravityService.perform(player, EchoesGravityAbility.Mode.FREEZE_SELF);
            EchoesGravityService.tickInventoryGravity(player);
            float rise = jumpRise(player);
            helper.assertTrue(rise == 0.0F,
                    "1.16: Freeze Self keeps a carrier of one gravity item on the ground; the port's jumps " + rise
                            + " with jump " + player.getEffect(MobEffects.JUMP));
        } finally {
            EchoesGravityService.cleanupUser(player, true);
            remove(helper, player);
        }
        helper.succeed();
    }

    // 092 GravityItemEvents' Jump -min(count, 5) also moved the safe fall distance of 1.16 calculateFallDamage by
    // 1 - level: no change with one item, a block less for each item from two up to five, and none once no item is
    // carried. A 10-block fall hurts a carrier of no item or one item 7.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesCarrierAttributes")
    public static void carriedGravityItemsMakeFallsHurtMoreFromTwoUp(GameTestHelper helper) {
        FallingCarrier player = carrier(helper, "EchoesFallingCarrier");
        try {
            int plain = player.fallDamage(10.0F);
            helper.assertTrue(plain == 7, "Setup: a 10-block fall hurts the carrier " + plain);
            for (int count : new int[] {1, 2, 3, 5, 6, 0}) {
                inventory(player, count);
                EchoesGravityService.tickInventoryGravity(player);
                int expected = plain + Math.max(Math.min(count, 5) - 1, 0);
                int damage = player.fallDamage(10.0F);
                helper.assertTrue(damage == expected,
                        "1.16 Jump -" + Math.min(count, 5) + " makes a 10-block fall of a carrier of " + count
                                + " gravity items hurt " + expected + "; the port's hurts " + damage
                                + " (safe fall distance "
                                + player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) + ")");
            }
        } finally {
            EchoesGravityService.cleanupUser(player, true);
            remove(helper, player);
        }
        helper.succeed();
    }

    // The carrier's jump and fall modifiers live on synced attributes: they go to the client when the count of
    // gravity items changes them, not on every tick that carries the same items.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesCarrierAttributes")
    public static void carrierJumpAndFallAreSentOnlyWhenTheyChange(GameTestHelper helper) {
        ServerPlayer player = freezer(helper, "EchoesSteadyCarrier");
        try {
            AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
            AttributeInstance fall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
            Set<AttributeInstance> toSync = player.getAttributes().getAttributesToSync();
            inventory(player, 3);
            EchoesGravityService.tickInventoryGravity(player);
            helper.assertTrue(toSync.contains(jump) && toSync.contains(fall),
                    "Setup: three gravity items did not change the carrier's jump and fall");
            toSync.clear();
            for (int tick = 0; tick < 3; tick++) {
                EchoesGravityService.tickInventoryGravity(player);
            }
            helper.assertTrue(!toSync.contains(jump) && !toSync.contains(fall),
                    "The carrier of the same three gravity items has its jump " + toSync.contains(jump)
                            + " and fall " + toSync.contains(fall) + " sent again on a tick that changes neither");
            inventory(player, 4);
            EchoesGravityService.tickInventoryGravity(player);
            helper.assertTrue(toSync.contains(jump) && toSync.contains(fall)
                            && Math.abs(jump.getValue() - (jump.getBaseValue() - 0.3D)) < 1.0E-6D
                            && Math.abs(fall.getValue() - (fall.getBaseValue() - 3.0D)) < 1.0E-6D,
                    "A fourth gravity item must change and send the carrier's jump and fall; jump "
                            + jump.getValue() + " (sent " + toSync.contains(jump) + "), fall " + fall.getValue()
                            + " (sent " + toSync.contains(fall) + ")");
            toSync.clear();
            inventory(player, 0);
            EchoesGravityService.tickInventoryGravity(player);
            helper.assertTrue(toSync.contains(jump) && toSync.contains(fall)
                            && !jump.hasModifier(EchoesGravityService.CARRIER_JUMP_MODIFIER)
                            && !fall.hasModifier(EchoesGravityService.CARRIER_FALL_MODIFIER),
                    "Dropping the last gravity item must remove and send the carrier's jump and fall modifiers");
        } finally {
            EchoesGravityService.cleanupUser(player, true);
            remove(helper, player);
        }
        helper.succeed();
    }

    /**
     * 092 DisablerTagHandler (both freezes) and HandFreezeHandler (the hand freeze): a frozen player stops using its
     * item, has no movement input and cannot fly, and a hand-frozen mob loses its target. On release 092 gave every
     * player mayfly, so survival players could fly; the port gives back the flight the player had before.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot",
            timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void frozenPlayerStopsUsingItemsAndCannotFly(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> frozenPlayerStopsUsingItemsAndCannotFlyBody(h));
    }

    private static void frozenPlayerStopsUsingItemsAndCannotFlyBody(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesRestrainer");
        StandPower power = PowerClass.STAND.attachGet(user);
        Player target = null;
        Zombie zombie = null;
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            stand.moveTo(user.getX(), user.getY(), user.getZ());
            target = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "EchoesRestrained")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            target.setNoGravity(true);
            target.moveTo(user.getX(), user.getY(), user.getZ() + 2.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Setup: the target was not added");
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            for (EchoesGravityAbility.Mode mode
                    : List.of(EchoesGravityAbility.Mode.FREEZE_TARGET, EchoesGravityAbility.Mode.HAND_FREEZE)) {
                // a survival player first, then one that could fly and was flying
                boolean flier = mode == EchoesGravityAbility.Mode.HAND_FREEZE;
                target.getAbilities().mayfly = flier;
                target.getAbilities().flying = flier;
                target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                target.startUsingItem(InteractionHand.OFF_HAND);
                target.xxa = 1.0F;
                target.zza = 1.0F;
                helper.assertTrue(target.isUsingItem(), "Setup: the target is not raising its shield");

                EchoesGravityService.perform(user, mode);
                EchoesFreezeState frozen = RotpEchoes.freezeState(target);
                helper.assertTrue(frozen.active(), "Setup: " + mode + " did not freeze the target");
                helper.assertTrue(!target.isUsingItem() && target.xxa == 0.0F && target.zza == 0.0F
                                && !target.getAbilities().flying && !target.getAbilities().mayfly,
                        "1.16: a player frozen by " + mode + " stops using its item, has no movement input and"
                                + " cannot fly; the port's target is using an item " + target.isUsingItem()
                                + ", input " + target.xxa + "/" + target.zza + ", flying "
                                + target.getAbilities().flying + ", mayfly " + target.getAbilities().mayfly);

                EchoesGravityService.perform(user, mode);
                helper.assertTrue(!frozen.active() && target.getAbilities().mayfly == flier
                                && !target.getAbilities().flying,
                        "When " + mode + " ends, the player keeps the flight it had before the freeze (mayfly "
                                + flier + ") and is not flying; the port's has mayfly "
                                + target.getAbilities().mayfly + ", flying " + target.getAbilities().flying);
            }
            Vec3 spot = target.position();
            target.discard();
            target = null;
            zombie = EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertTrue(zombie != null, "Setup: no zombie");
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.moveTo(spot.x, spot.y, spot.z, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Setup: the zombie was not added");
            user.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getBoundingBox().getCenter());
            for (EchoesGravityAbility.Mode mode
                    : List.of(EchoesGravityAbility.Mode.FREEZE_TARGET, EchoesGravityAbility.Mode.HAND_FREEZE)) {
                zombie.setTarget(user);
                EchoesGravityService.perform(user, mode);
                helper.assertTrue(RotpEchoes.freezeState(zombie).active(),
                        "Setup: " + mode + " did not freeze the zombie");
                boolean handFreeze = mode == EchoesGravityAbility.Mode.HAND_FREEZE;
                helper.assertTrue((zombie.getTarget() == null) == handFreeze,
                        "1.16 HandFreezeHandler takes a hand-frozen mob's target, and only the hand freeze does;"
                                + " after " + mode + " the zombie's target is " + zombie.getTarget());
                EchoesGravityService.perform(user, mode);
            }
        } finally {
            if (target != null) {
                target.discard();
            }
            if (zombie != null) {
                zombie.discard();
            }
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            remove(helper, user);
        }
        helper.succeed();
    }

    /**
     * 092 DisablerTagHandler.applyFreezeEffects and updateFreeze call stopUsingItem (func_184602_cy) on a frozen
     * player: a bow drawn for a second is lowered, and no arrow flies.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 100,
            batch = "rotpEchoesFrozenArcher")
    public static void frozenArcherLowersItsDrawnBowWithoutShooting(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesArcherFreezer");
        StandPower power = PowerClass.STAND.attachGet(user);
        Player[] archer = {null};
        Runnable cleanup = () -> {
            discardArrows(helper, user);
            if (archer[0] != null) {
                archer[0].discard();
            }
            EchoesGravityService.cleanupUser(user, true);
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            remove(helper, user);
        };
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            stand.moveTo(user.getX(), user.getY(), user.getZ());
            Player target = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "EchoesArcher")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            archer[0] = target;
            target.setNoGravity(true);
            target.moveTo(user.getX(), user.getY(), user.getZ() + 2.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Setup: the archer was not added");
            target.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            target.getInventory().setItem(9, new ItemStack(Items.ARROW, 64));
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            target.startUsingItem(InteractionHand.MAIN_HAND);
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        List<EchoesGravityAbility.Mode> modes =
                List.of(EchoesGravityAbility.Mode.FREEZE_TARGET, EchoesGravityAbility.Mode.HAND_FREEZE);
        int[] step = {0};
        int[] drawn = {0};
        helper.onEachTick(() -> {
            if (step[0] >= modes.size()) {
                return;
            }
            try {
                Player target = archer[0];
                if (++drawn[0] < 25) {
                    return;
                }
                EchoesGravityAbility.Mode mode = modes.get(step[0]);
                helper.assertTrue(target.isUsingItem() && target.getTicksUsingItem() >= 20,
                        "Setup: the archer has not drawn its bow for 20 ticks: using " + target.isUsingItem() + ", "
                                + target.getTicksUsingItem() + " ticks");
                EchoesGravityService.perform(user, mode);
                helper.assertTrue(RotpEchoes.freezeState(target).active(),
                        "Setup: " + mode + " did not freeze the archer");
                List<AbstractArrow> arrows = helper.getLevel().getEntitiesOfClass(
                        AbstractArrow.class, user.getBoundingBox().inflate(32.0D));
                int left = target.getInventory().getItem(9).getCount();
                helper.assertTrue(!target.isUsingItem() && arrows.isEmpty() && left == 64,
                        "1.16: a player frozen by " + mode + " lowers a bow it has drawn for "
                                + "20 ticks and shoots nothing; the port's archer is using it " + target.isUsingItem()
                                + ", " + arrows.size() + " arrow(s) flew, " + left + " of 64 arrows left");
                EchoesGravityService.perform(user, mode);
                helper.assertTrue(!RotpEchoes.freezeState(target).active(),
                        "Setup: " + mode + " did not release the archer");
                step[0]++;
                drawn[0] = 0;
                if (step[0] < modes.size()) {
                    target.startUsingItem(InteractionHand.MAIN_HAND);
                    return;
                }
                cleanup.run();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                step[0] = modes.size();
                cleanup.run();
                throw failure;
            }
        });
    }

    /**
     * 092 HandFreezeHandler.tickFreeze, every tick of a hand freeze: the frozen player stops using its item and swings
     * both hands, and in the last 20 of the 600 ticks Slowness, Mining Fatigue and Weakness come off (Hunger and the
     * held jump stay). The target freeze does neither.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 80,
            batch = "rotpEchoesHandFreezeTicks")
    public static void handFrozenPlayerSwingsBothHandsAndEasesOffAtTheEnd(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesHandHolder");
        StandPower power = PowerClass.STAND.attachGet(user);
        Player[] held = {null};
        boolean[] closed = {false};
        // the master presses a frozen target down 4.5 blocks a tick, so the held player stands on a floor
        BlockPos floor = BlockPos.containing(user.getX(), user.getY() - 0.5D, user.getZ() + 2.0D);
        Runnable cleanup = () -> {
            if (closed[0]) {
                return;
            }
            closed[0] = true;
            helper.getLevel().setBlockAndUpdate(floor, Blocks.AIR.defaultBlockState());
            if (held[0] != null) {
                EchoesGravityService.releaseFrozenTarget(held[0]);
                held[0].discard();
            }
            EchoesGravityService.cleanupUser(user, true);
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            disconnect(user);
        };
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            stand.moveTo(user.getX(), user.getY(), user.getZ());
            Player target = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "EchoesHandHeld")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            held[0] = target;
            helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            target.setNoGravity(true);
            target.moveTo(user.getX(), user.getY(), user.getZ() + 2.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Setup: the target was not added");
            target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.HAND_FREEZE);
            helper.assertTrue(RotpEchoes.freezeState(target).active(), "Setup: HAND_FREEZE did not freeze the target");
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (closed[0]) {
                return;
            }
            try {
                Player target = held[0];
                EchoesFreezeState frozen = RotpEchoes.freezeState(target);
                int tick = ++ticks[0];
                if (tick <= 12) {
                    helper.assertTrue(frozen.active(), "Setup: the freeze was not held on tick " + tick);
                }
                if (tick == 3) {
                    target.startUsingItem(InteractionHand.OFF_HAND);
                } else if (tick == 4) {
                    helper.assertTrue(!target.isUsingItem(),
                            "1.16: a hand-frozen player who raises its shield during the freeze lowers it on the"
                                    + " next tick; the port's still holds it up");
                } else if (tick == 9) {
                    // A player on the server never counts its swing down, so the swing is cleared by hand.
                    target.swinging = false;
                    target.swingingArm = InteractionHand.MAIN_HAND;
                } else if (tick == 10) {
                    helper.assertTrue(target.swinging && target.swingingArm == InteractionHand.OFF_HAND,
                            "1.16 HandFreezeHandler swings both hands of a hand-frozen player every tick, the off"
                                    + " hand last; on its 10th tick the port's target is swinging " + target.swinging
                                    + " with " + target.swingingArm);
                    // As if 590 of the freeze's 600 ticks had passed.
                    frozen.activate(target, user.getUUID(), frozen.slowAmplifier(), EchoesFreezeState.Kind.HAND,
                            helper.getLevel().getGameTime() + 10L);
                    // A reload in that window: the transient jump hold is not saved.
                    target.getAttribute(Attributes.JUMP_STRENGTH)
                            .removeModifier(EchoesGravityService.FREEZE_JUMP_MODIFIER);
                } else if (tick == 11) {
                    helper.assertTrue(!target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                                    && !target.hasEffect(MobEffects.DIG_SLOWDOWN)
                                    && !target.hasEffect(MobEffects.WEAKNESS)
                                    && target.hasEffect(MobEffects.HUNGER) && jumpRise(target) == 0.0F,
                            "1.16 HandFreezeHandler takes Slowness, Mining Fatigue and Weakness off a hand freeze in"
                                    + " its last 20 ticks, and Hunger and the held jump stay; with 9 ticks left the"
                                    + " port's target has" + activeEffects(target) + ", jump " + jumpRise(target));
                    EchoesGravityService.perform(user, EchoesGravityAbility.Mode.HAND_FREEZE);
                    EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_TARGET);
                    helper.assertTrue(frozen.active() && frozen.kind() == EchoesFreezeState.Kind.TARGET,
                            "Setup: FREEZE_TARGET did not freeze the target");
                    frozen.activate(target, user.getUUID(), frozen.slowAmplifier(), EchoesFreezeState.Kind.TARGET,
                            helper.getLevel().getGameTime() + 10L);
                    target.swinging = false;
                } else if (tick == 12) {
                    helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                                    && target.hasEffect(MobEffects.DIG_SLOWDOWN)
                                    && target.hasEffect(MobEffects.WEAKNESS),
                            "1.16 keeps a target freeze's effects to its end; with 9 ticks left the port's target has"
                                    + activeEffects(target));
                } else if (tick == 20) {
                    helper.assertTrue(!target.swinging,
                            "1.16 swings only a hand-frozen player's hands; the target freeze's is swinging "
                                    + target.swingingArm);
                    cleanup.run();
                    helper.succeed();
                }
            } catch (RuntimeException | Error failure) {
                cleanup.run();
                throw failure;
            }
        });
    }

    /**
     * 092 DisablerTagHandler.updateFreeze gives a frozen player or mob in water a motion of 0, -0.5, 0 just before it
     * moves, so it sinks to the bottom instead of hanging where it was frozen.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 60,
            batch = "rotpEchoesFrozenInWater")
    public static void frozenMobInWaterSinksToTheBottom(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesWaterHolder");
        StandPower power = PowerClass.STAND.attachGet(user);
        Pig[] held = {null};
        List<BlockPos> water = new ArrayList<>();
        List<BlockPos> glass = new ArrayList<>();
        boolean[] closed = {false};
        Runnable cleanup = () -> {
            if (closed[0]) {
                return;
            }
            closed[0] = true;
            for (BlockPos pos : water) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            for (BlockPos pos : glass) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            if (held[0] != null) {
                EchoesGravityService.releaseFrozenTarget(held[0]);
                held[0].discard();
            }
            EchoesGravityService.cleanupUser(user, true);
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            disconnect(user);
        };
        double[] frozenAt = {0.0D};
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            stand.moveTo(user.getX(), user.getY(), user.getZ());
            Pig pig = EntityType.PIG.create(helper.getLevel());
            helper.assertTrue(pig != null, "Setup: no pig");
            held[0] = pig;
            pig.setNoGravity(true);
            pig.moveTo(user.getX(), user.getY() + 1.0D, user.getZ() + 2.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(pig), "Setup: the pig was not added");
            user.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_TARGET);
            helper.assertTrue(RotpEchoes.freezeState(pig).active(), "Setup: FREEZE_TARGET did not freeze the pig");
            frozenAt[0] = pig.getY();
            // A column of three water blocks around the pig, walled in glass, with a glass floor two blocks under
            // its feet.
            BlockPos column = pig.blockPosition();
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = column.above(dy);
                water.add(pos);
                glass.add(pos.north());
                glass.add(pos.south());
                glass.add(pos.east());
                glass.add(pos.west());
            }
            glass.add(column.below(2));
            for (BlockPos pos : glass) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
            }
            for (BlockPos pos : water) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            }
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (closed[0]) {
                return;
            }
            try {
                Pig pig = held[0];
                EchoesFreezeState frozen = RotpEchoes.freezeState(pig);
                helper.assertTrue(frozen.active(), "Setup: the freeze was not held on tick " + (ticks[0] + 1));
                double floor = frozenAt[0] - 1.0D;
                if (++ticks[0] < 20) {
                    return;
                }
                helper.assertTrue(pig.isInWater() && Math.abs(pig.getY() - floor) < 0.01D
                                && Math.abs(frozen.anchor().y - pig.getY()) < 0.01D,
                        "1.16: a mob frozen in water sinks to the bottom, from " + frozenAt[0] + " to " + floor
                                + ", and is held there; after 20 ticks the port's pig is at " + pig.getY()
                                + " (in water " + pig.isInWater() + "), held at " + frozen.anchor().y);
                cleanup.run();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                cleanup.run();
                throw failure;
            }
        });
    }

    // A server player in the server's player list, which the freeze's per-tick hold looks its owner up in.
    private static ServerPlayer connectedFreezer(GameTestHelper helper, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setNoGravity(true);
        player.setPos(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX())) + 8.5D,
                origin.getY() + 5.0D,
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 8.5D);
        return player;
    }

    private static void disconnect(ServerPlayer player) {
        player.connection.disconnect(Component.literal("Echoes freeze GameTest complete"));
    }

    private static void discardArrows(GameTestHelper helper, ServerPlayer user) {
        for (AbstractArrow arrow : helper.getLevel().getEntitiesOfClass(
                AbstractArrow.class, user.getBoundingBox().inflate(32.0D))) {
            arrow.discard();
        }
    }

    private static void discardThrownItems(GameTestHelper helper, ServerPlayer user) {
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, user.getBoundingBox().inflate(16.0D))) {
            item.discard();
        }
    }

    private static void cleanupShortBar(GameTestHelper helper, ServerPlayer user, StandPower power) {
        if (user.isRemoved()) {
            return;
        }
        AbilityInput.keyRelease(SHORT_BAR_KEY, user);
        EchoesGravityService.cleanupUser(user, true);
        if (power.isSummoned()) {
            EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
        }
        remove(helper, user);
    }

    // How high a jump from standing would lift the entity this tick.
    private static float jumpRise(LivingEntity entity) {
        Vec3 before = entity.getDeltaMovement();
        entity.setDeltaMovement(Vec3.ZERO);
        entity.jumpFromGround();
        float rise = (float) entity.getDeltaMovement().y;
        entity.setDeltaMovement(before);
        return rise;
    }

    private static boolean heldJump(LivingEntity entity, ResourceLocation modifier) {
        return entity.getAttribute(Attributes.JUMP_STRENGTH).hasModifier(modifier);
    }

    private static String activeEffects(LivingEntity entity) {
        StringBuilder effects = new StringBuilder();
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            effects.append(' ').append(effect);
        }
        return effects.toString();
    }

    // Clear of the barrier ring the runner puts one and two blocks above each structure block, which would stop
    // the freeze's aim.
    /**
     * R772 E1 (master EchoesChangeActScreen and EchoesActSyncPacket): the selector offers every ACT up to the Resolve
     * level, the Resolve level stays the Stand's across forms, and a summoned Stand changes form where it stands
     * (owner 2026-11-03) instead of being retracted and summoned again; an unsummoned one just changes form.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void actSelectorChangesFormInPlaceAndKeepsTheResolveLevel(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesSelector");
        StandPower power = PowerClass.STAND.attachGet(user);
        try {
            power.setStand(EchoesStands.ECHOES_ACT1.get());
            power.setResolveLevel(2);
            helper.assertTrue(power.getResolveLevel() == 2, "Setup: the user did not reach Resolve level 2");
            helper.assertTrue(EchoesForms.highestAct(power) == 2 && !EchoesForms.select(user, 3)
                            && power.getPowerType() == EchoesStands.ECHOES_ACT1.get(),
                    "Resolve 2 offers ACT1 and ACT2 only; highest " + EchoesForms.highestAct(power));
            helper.assertTrue(EchoesStands.ECHOES_ACT1.get().summon(user, power), "Setup: could not summon ACT1");
            StandEntity act1 = power.getSummonedStandEntity();
            helper.assertTrue(act1 != null, "Setup: no summoned ACT1");
            act1.setNoGravity(true);
            Vec3 spot = user.position().add(1.5D, 0.0D, -1.0D);
            act1.moveTo(spot.x, spot.y, spot.z, 40.0F, 0.0F);

            helper.assertTrue(EchoesForms.select(user, 2), "The selector refused ACT2 at Resolve 2");
            StandEntity act2 = power.getSummonedStandEntity();
            helper.assertTrue(power.getPowerType() == EchoesStands.ECHOES_ACT2.get() && act1.isRemoved()
                            && act2 != null && act2 != act1,
                    "ACT1 was not replaced by a summoned ACT2");
            helper.assertTrue(act2.position().distanceTo(spot) < 1.0E-4D && Math.abs(act2.getYRot() - 40.0F) < 1.0E-3F,
                    "ACT2 must appear where ACT1 stood (" + spot + "), not at the user; it is at " + act2.position());
            helper.assertTrue(power.getResolveLevel() == 2 && EchoesForms.highestAct(power) == 2,
                    "The Resolve level must stay with the Stand across forms; ACT2 has " + power.getResolveLevel());

            EchoesStands.ECHOES_ACT2.get().forceUnsummon(user, power);
            helper.assertTrue(EchoesForms.select(user, 1) && power.getPowerType() == EchoesStands.ECHOES_ACT1.get()
                            && !power.isSummoned(),
                    "An unsummoned Echoes must change form without being summoned");
        } finally {
            if (power.isSummoned()) {
                EchoesForms.typeOf(EchoesForms.actOf(power.getPowerType())).forceUnsummon(user, power);
            }
            remove(helper, user);
        }
        helper.succeed();
    }

    /**
     * R772 F11 (master EchoesOnomatopoeiaAttachAction, OnomatopoeiaAttachHandler, OnomatopoeiaEffect): ACT2 puts the
     * chosen word on the first living target in the user's aim within 5 blocks and its tail stays out; a word acts
     * only while ACT2 is out; Don hits once for 0.25 and knocks the target back and up; taking the words back frees the
     * target and returns the tail.
     */
    // A connected user stays at its chunk centre until its disconnect is handled, after the test has returned, where a
    // default-batch freeze ray that starts inside it takes it (see the short-bar test); these tests run in batches of
    // their own.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesWordOnTarget")
    public static void act2WordLandsOnTheAimedTargetActsAndComesBack(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesWords");
        StandPower power = PowerClass.STAND.attachGet(user);
        Zombie target = null;
        try {
            power.setStand(EchoesStands.ECHOES_ACT2.get());
            target = new Zombie(EntityType.ZOMBIE, helper.getLevel());
            target.setNoAi(true);
            target.setNoGravity(true);
            target.moveTo(user.getX(), user.getY(), user.getZ() + 3.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Setup: the zombie was not added");
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());

            helper.assertTrue(!EchoesOnomatopoeiaService.attachInAim(user),
                    "With no word chosen the tail must carry nothing");
            EchoesOnomatopoeiaService.select(user, EchoesOnomatopoeia.DON);
            helper.assertTrue(EchoesOnomatopoeiaService.attachInAim(user)
                            && EchoesOnomatopoeiaService.markOf(target) != null
                            && EchoesOnomatopoeiaService.markOf(target).word() == EchoesOnomatopoeia.DON
                            && RotpEchoes.userState(user).tailOut(),
                    "The chosen word must land on the aimed zombie and the tail stay out");
            helper.assertTrue(!EchoesOnomatopoeiaService.attachInAim(user),
                    "A second word needs the tail back first");

            float health = target.getHealth();
            EchoesOnomatopoeiaService.tickMarked(target);
            helper.assertTrue(target.getHealth() == health,
                    "A word acts only while ACT2 is out; the unsummoned ACT2's word hurt the zombie");
            helper.assertTrue(EchoesStands.ECHOES_ACT2.get().summon(user, power), "Setup: could not summon ACT2");
            power.getSummonedStandEntity().setNoGravity(true);
            EchoesOnomatopoeiaService.tickMarked(target);
            float afterOnce = target.getHealth();
            helper.assertTrue(afterOnce < health && target.getDeltaMovement().y > 0.3D,
                    "Don hits once and throws the target up and back; health " + health + " -> " + afterOnce
                            + ", motion " + target.getDeltaMovement());
            target.setDeltaMovement(Vec3.ZERO);
            EchoesOnomatopoeiaService.tickMarked(target);
            helper.assertTrue(target.getHealth() == afterOnce, "Don acts only once");

            EchoesOnomatopoeiaService.takeBackMarks(user);
            helper.assertTrue(EchoesOnomatopoeiaService.markOf(target) == null && !RotpEchoes.userState(user).tailOut(),
                    "Taking the words back must free the zombie and return the tail");

            // master EchoesTailNeedleAction / EchoesTailNeedleRecallAction: one tail at a time, recalled in flight
            helper.assertTrue(EchoesOnomatopoeiaService.shootNeedle(user) && RotpEchoes.userState(user).tailOut()
                            && !EchoesOnomatopoeiaService.shootNeedle(user),
                    "The tail must fly once with the word and not again while it is out");
            List<EchoesTailNeedleEntity> needles = helper.getLevel().getEntitiesOfClass(
                    EchoesTailNeedleEntity.class, user.getBoundingBox().inflate(4.0D));
            helper.assertTrue(needles.size() == 1 && needles.get(0).word() == EchoesOnomatopoeia.DON
                            && needles.get(0).getOwner() == user && !needles.get(0).isReturning(),
                    "Expected one needle carrying Don from the user, found " + needles.size());
            EchoesOnomatopoeiaService.recall(user);
            helper.assertTrue(needles.get(0).isReturning() && RotpEchoes.userState(user).tailOut(),
                    "Recalled in flight, the needle turns back and the tail is in only when it arrives");
            needles.get(0).discard();
        } finally {
            if (target != null) {
                target.discard();
            }
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT2.get().forceUnsummon(user, power);
            }
            disconnect(user);
        }
        helper.succeed();
    }

    /**
     * R772 F11 (master EchoesOnomatopoeiaAttachAction 104-126, OnomatopoeiaAttachHandler.attachToBlock,
     * OnomatopoeiaMarkTileEntity, OnomatopoeiaBlockEntity, OnomatopoeiaTailNeedleEntity.onHitBlock): with no living
     * target in the aim the word goes on the face of the block looked at (not Youhuo), in the cell in front of it, and
     * acts on what stands there, the state ending when the mark comes off; Yingbangbang hardens the block; the recall
     * sends the tail back from the mark; the entity form makes the word a thing at the block's centre; a tail needle
     * that hits the block leaves its word there; ACT2's tail tip is hidden while the tail is out.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 60,
            batch = "rotpEchoesWordOnBlock")
    public static void act2WordMarksTheAimedBlockFaceAndComesBack(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesBlockWords");
        StandPower power = PowerClass.STAND.attachGet(user);
        ServerLevel level = helper.getLevel();
        BlockPos wall = BlockPos.containing(user.getEyePosition()).south(3);
        BlockPos markPos = wall.north();
        Pig[] pig = new Pig[1];
        Runnable cleanup = () -> {
            EchoesOnomatopoeiaService.takeBackMarks(user);
            if (pig[0] != null) {
                pig[0].discard();
            }
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT2.get().forceUnsummon(user, power);
            }
            level.setBlock(markPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            disconnect(user);
        };
        EchoesStandEntity[] act2 = new EchoesStandEntity[1];
        try {
            power.setStand(EchoesStands.ECHOES_ACT2.get());
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            user.setYRot(0.0F);
            user.setXRot(0.0F);
            EchoesUserState state = RotpEchoes.userState(user);

            EchoesOnomatopoeiaService.select(user, EchoesOnomatopoeia.YOUHUO);
            helper.assertTrue(!EchoesOnomatopoeiaService.attachInAim(user) && !state.tailOut(),
                    "Youhuo must not go on a block");
            EchoesOnomatopoeiaService.select(user, EchoesOnomatopoeia.YINGBANGBANG);
            helper.assertTrue(EchoesOnomatopoeiaService.attachInAim(user) && state.tailOut(),
                    "The word must go on the wall face in the aim and the tail stay out");
            BlockState mark = level.getBlockState(markPos);
            helper.assertTrue(mark.is(EchoesBlocks.ONOMATOPOEIA_MARK.get())
                            && mark.getValue(EchoesOnomatopoeiaMarkBlock.FACING) == Direction.NORTH
                            && EchoesOnomatopoeiaMarkBlockEntity.wordAt(level, markPos) == EchoesOnomatopoeia.YINGBANGBANG,
                    "Expected the Yingbangbang mark facing north in front of the wall, found " + mark);
            helper.assertTrue(EchoesOnomatopoeiaService.isHardened(level, wall)
                            && !EchoesOnomatopoeiaService.isHardened(level, wall.below()),
                    "Yingbangbang must harden the block it is on, and only that one");
            helper.assertTrue(!user.gameMode.destroyBlock(wall) && level.getBlockState(wall).is(Blocks.STONE)
                            && !EventHooks.onEntityDestroyBlock(user, wall, level.getBlockState(wall)),
                    "Neither survival mining nor a Stand's punch (LivingDestroyBlockEvent) may break the hardened block");

            EchoesOnomatopoeiaService.recall(user);
            List<EchoesTailNeedleEntity> needles = level.getEntitiesOfClass(
                    EchoesTailNeedleEntity.class, new AABB(markPos).inflate(1.0D));
            helper.assertTrue(level.getBlockState(markPos).isAir() && needles.size() == 1
                            && needles.get(0).isReturning() && needles.get(0).word() == EchoesOnomatopoeia.YINGBANGBANG
                            && state.tailOut() && !EchoesOnomatopoeiaService.isHardened(level, wall),
                    "The recall must take the mark off and send the tail back from it; needles " + needles.size());
            EchoesOnomatopoeiaService.takeBackMarks(user);
            helper.assertTrue(needles.get(0).isRemoved() && !state.tailOut(), "Taking back must drop the tail in flight");

            state.setEntityForm(true);
            helper.assertTrue(EchoesOnomatopoeiaService.attachInAim(user), "The entity form must go on the wall too");
            List<EchoesOnomatopoeiaWordEntity> things = level.getEntitiesOfClass(
                    EchoesOnomatopoeiaWordEntity.class, new AABB(wall).inflate(1.0D));
            helper.assertTrue(things.size() == 1 && things.get(0).word() == EchoesOnomatopoeia.YINGBANGBANG
                            && things.get(0).position().distanceTo(Vec3.atCenterOf(wall)) < 1.0E-4D
                            && level.getBlockState(markPos).isAir(),
                    "The entity form must stand at the wall's centre and leave no mark; things " + things.size());
            EchoesOnomatopoeiaService.takeBackMarks(user);
            helper.assertTrue(things.get(0).isRemoved() && !state.tailOut(), "Taking back must remove the thing");
            state.setEntityForm(false);

            helper.assertTrue(EchoesStands.ECHOES_ACT2.get().summon(user, power), "Setup: could not summon ACT2");
            act2[0] = (EchoesStandEntity) power.getSummonedStandEntity();
            act2[0].setNoGravity(true);
            EchoesOnomatopoeiaService.select(user, EchoesOnomatopoeia.NIANHUHU);
            helper.assertTrue(EchoesOnomatopoeiaService.attachInAim(user), "Setup: Nianhuhu did not go on the wall");
            pig[0] = pig(helper, Vec3.atBottomCenterOf(markPos));
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pig[0].hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                                && act2[0].isTailOut(),
                        "The mark must slow the pig in its cell and ACT2 hide its tail while it is out"))
                .thenExecute(() -> {
                    EchoesOnomatopoeiaService.takeBackMarks(user);
                    helper.assertTrue(!pig[0].hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                            "The state must end when the mark comes off");
                    pig[0].discard();
                    helper.assertTrue(EchoesOnomatopoeiaService.shootNeedle(user), "Setup: the tail did not fly");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        EchoesOnomatopoeiaMarkBlockEntity.wordAt(level, markPos) == EchoesOnomatopoeia.NIANHUHU
                                && level.getEntitiesOfClass(EchoesTailNeedleEntity.class,
                                        user.getBoundingBox().inflate(8.0D)).isEmpty()
                                && RotpEchoes.userState(user).tailOut(),
                        "The tail needle must leave its word on the wall it hits and stay out"))
                .thenExecute(cleanup)
                .thenSucceed();
    }

    /**
     * R772 F11 (master SoundSelectPacket, SoundSelfAction, SoundMarkBlockAction, SoundMarkBlockHandler,
     * SoundMarkEffectHandler, SoundGiveEffectHandler): ACT1 takes only a sound the game has; the self sound needs one
     * and haunts its user; a mark goes in the cell in front of the face, three at most (a fourth takes the oldest), and
     * aiming at the user's own mark takes it back; a mob standing in a mark is stunned and haunted; a sound given to a
     * mob stuns it until it is taken back. The heavy punch's key takes the sound-giving finisher (master
     * EchoesACT1FinisherAction) at Resolve 2 on a finisher meter of 0.5, not at Resolve 1 or on an empty meter.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 60,
            batch = "rotpEchoesSoundMarks")
    public static void act1SoundMarksBlocksAndIsGiven(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesSounds");
        StandPower power = PowerClass.STAND.attachGet(user);
        ServerLevel level = helper.getLevel();
        BlockPos base = user.blockPosition().below(2).south();
        BlockPos[] stones = new BlockPos[4];
        Pig[] pigs = new Pig[2];
        Runnable cleanup = () -> {
            EchoesSoundService.clearMarks(user);
            EchoesSoundService.cancelAllGives(user);
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT1.get().forceUnsummon(user, power);
            }
            for (Pig pig : pigs) {
                if (pig != null) {
                    pig.discard();
                }
            }
            for (BlockPos stone : stones) {
                if (stone != null) {
                    level.setBlock(stone.above(), Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            disconnect(user);
        };
        try {
            power.setStand(EchoesStands.ECHOES_ACT1.get());
            EchoesUserState state = RotpEchoes.userState(user);
            helper.assertTrue(!EchoesSoundService.toggleSelf(user) && !state.selfSound(),
                    "The self sound needs a chosen sound");
            EchoesSoundService.select(user, ResourceLocation.fromNamespaceAndPath("rotp_echoes", "no_such_sound"));
            helper.assertTrue(state.sound() == null, "A sound the game does not have must be refused");
            ResourceLocation zombie = ResourceLocation.withDefaultNamespace("entity.zombie.ambient");
            EchoesSoundService.select(user, zombie);
            helper.assertTrue(zombie.equals(state.sound()), "The chosen sound was not kept");
            helper.assertTrue(EchoesSoundService.toggleSelf(user) && state.selfSound()
                            && user.hasEffect(EchoesMobEffects.SOUND_HAUNT),
                    "The self sound must start and haunt its user");
            EchoesSoundService.toggleSelf(user);
            helper.assertTrue(!state.selfSound() && !user.hasEffect(EchoesMobEffects.SOUND_HAUNT),
                    "The self sound must stop on the second use");

            // master InitStands 95-109: on a finisher meter of 0.5 the heavy's key takes its finisher from Resolve 2
            helper.assertTrue(EchoesStands.ECHOES_ACT1.get().summon(user, power), "Setup: could not summon ACT1");
            StandEntity act1 = power.getSummonedStandEntity();
            act1.setNoGravity(true);
            act1.setFinisherMeter(0.6F);
            power.setResolveLevel(1);
            String heavyAtResolve1 = heavyKey(power);
            power.setResolveLevel(2);
            String heavyAtResolve2 = heavyKey(power);
            act1.setFinisherMeter(0.0F);
            String heavyOnEmptyMeter = heavyKey(power);
            EchoesStands.ECHOES_ACT1.get().forceUnsummon(user, power);
            helper.assertTrue("heavy_punch".equals(heavyAtResolve1) && "act1_finisher".equals(heavyAtResolve2)
                            && "heavy_punch".equals(heavyOnEmptyMeter),
                    "The heavy key must take the finisher at Resolve 2 on a full enough meter only; Resolve 1 "
                            + heavyAtResolve1 + ", Resolve 2 " + heavyAtResolve2 + ", empty meter " + heavyOnEmptyMeter);

            for (int i = 0; i < stones.length; i++) {
                stones[i] = base.east(i);
                level.setBlock(stones[i], Blocks.STONE.defaultBlockState(), 3);
                helper.assertTrue(EchoesSoundService.markBlock(user, stones[i], Direction.UP),
                        "Mark " + i + " did not go on the stone's top face");
            }
            EchoesSoundMarkBlockEntity newest = EchoesSoundMarkBlockEntity.at(level, stones[3].above());
            helper.assertTrue(EchoesSoundMarkBlockEntity.marksOf(user.getUUID()).size() == EchoesSoundService.MAX_MARKS
                            && level.getBlockState(stones[0].above()).isAir()
                            && newest != null && zombie.equals(newest.sound()),
                    "Three marks at most: the fourth must take the oldest away");
            helper.assertTrue(EchoesSoundService.markBlock(user, stones[1].above(), Direction.NORTH)
                            && level.getBlockState(stones[1].above()).isAir()
                            && EchoesSoundMarkBlockEntity.marksOf(user.getUUID()).size() == 2,
                    "Aiming at the user's own mark must take it back");

            pigs[0] = pig(helper, Vec3.atBottomCenterOf(stones[3].above()));
            pigs[1] = pig(helper, Vec3.atBottomCenterOf(base.north(2)));
            helper.assertTrue(EchoesSoundService.give(user, pigs[1]) && EchoesSoundService.hasGiven(user, pigs[1])
                            && pigs[1].hasEffect(EchoesMobEffects.SOUND_HAUNT),
                    "The sound must be given to the pig and haunt it");
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pigs[0].hasEffect(MobEffects.CONFUSION)
                                && pigs[0].hasEffect(EchoesMobEffects.SOUND_HAUNT)
                                && pigs[1].hasEffect(MobEffects.CONFUSION),
                        "The pig in the mark and the pig given the sound must be stunned"))
                .thenExecute(() -> {
                    EchoesSoundService.cancelGive(user, pigs[1]);
                    helper.assertTrue(!EchoesSoundService.hasGiven(user, pigs[1])
                                    && !pigs[1].hasEffect(MobEffects.CONFUSION),
                            "Taking the sound back must end its stun");
                })
                .thenExecute(cleanup)
                .thenSucceed();
    }

    /**
     * R772 EC3-05 (master EchoesACT1FinisherAction: the sound goes to the living target the strike takes): an AI zombie
     * 1.9 blocks ahead attacking the user is in ACT1's reach, takes the heavy hit and the knockback, and then loses 2
     * health a second, stunned.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot",
            timeoutTicks = 120 + GameTestChunkTickets.WAIT_TICKS, batch = "rotpEchoesFinisherStrike")
    public static void act1FinisherOnAnAttackingZombieGivesTheSound(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> act1FinisherOnAnAttackingZombieBody(h));
    }

    private static void act1FinisherOnAnAttackingZombieBody(GameTestHelper helper) {
        FinisherScene scene = new FinisherScene(helper, "EchoesFinisherHit");
        EntityActionInstance[] finisher = new EntityActionInstance[1];
        float[] healthAtStrike = new float[1];
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> finisher[0] = scene.pressFinisher())
                .thenWaitUntil(() -> helper.assertTrue(finisher[0] != null && finisher[0].punchedTarget != null,
                        "The strike has not come"))
                .thenExecute(() -> {
                    scene.assertTook(finisher[0]);
                    double pushed = scene.zombie.getZ() - scene.startZ;
                    helper.assertTrue(pushed > 1.0D, "The heavy hit must knock the zombie back; pushed " + pushed);
                    healthAtStrike[0] = scene.zombie.getHealth();
                    helper.assertTrue(healthAtStrike[0] <= 16.0F && healthAtStrike[0] >= 13.0F,
                            "The heavy hit deals 5 through the zombie's armour; health 20 -> " + healthAtStrike[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        Math.abs(scene.zombie.getHealth() - (healthAtStrike[0] - 2.0F)) < 0.001F,
                        "The sound must take 2 health in a second; " + healthAtStrike[0] + " -> "
                                + scene.zombie.getHealth()))
                .thenExecute(() -> helper.assertTrue(scene.zombie.hasEffect(MobEffects.CONFUSION),
                        "The zombie given the sound must be stunned"))
                .thenExecute(scene::close)
                .thenSucceed();
    }

    /**
     * R772 EC3-05, the re-test's numbers: a zombie held at movement speed 0 is pushed out of ACT1's reach by the first
     * two jabs (health 20 -> 17.6375, about 4 blocks), which the Stand's offset of 2 blocks in front cannot make up. The
     * finisher then strikes nothing, as 1.16's StandEntity.isTargetInReach and StandEntityHeavyAttack's offset ruled:
     * no hit, no push, no sound. Back in reach, the next finisher gives it.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot",
            timeoutTicks = 240 + GameTestChunkTickets.WAIT_TICKS, batch = "rotpEchoesFinisherReach")
    public static void act1FinisherMissesAZombieJabbedOutOfReach(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> act1FinisherMissesAZombieJabbedOutOfReachBody(h));
    }

    private static void act1FinisherMissesAZombieJabbedOutOfReachBody(GameTestHelper helper) {
        FinisherScene scene = new FinisherScene(helper, "EchoesFinisherMiss");
        EntityActionInstance[] finisher = new EntityActionInstance[1];
        float[] healthBefore = new float[1];
        double[] zBefore = new double[1];
        GameTestSequence sequence = helper.startSequence().thenIdle(20);
        for (int jab = 0; jab < 8; jab++) {
            sequence.thenExecute(() -> scene.press("punch", "punch")).thenIdle(14);
        }
        sequence.thenExecute(() -> {
                    healthBefore[0] = scene.zombie.getHealth();
                    zBefore[0] = scene.zombie.getZ();
                    helper.assertTrue(healthBefore[0] < 20.0F && healthBefore[0] > 15.0F,
                            "Only the first jabs reach a zombie that cannot walk back; health " + healthBefore[0]);
                    helper.assertTrue(zBefore[0] - scene.startZ > 3.0D && scene.eyeToBox() > scene.reach(),
                            "The jabs must have pushed it out of reach; pushed " + (zBefore[0] - scene.startZ)
                                    + ", eyes to box " + scene.eyeToBox() + ", reach " + scene.reach());
                    finisher[0] = scene.pressFinisher();
                })
                .thenWaitUntil(() -> helper.assertTrue(finisher[0] != null && finisher[0].punchedTarget != null,
                        "The strike has not come"))
                .thenExecute(() -> {
                    helper.assertTrue(finisher[0].punchedTarget.getType() == ActionTarget.TargetType.EMPTY,
                            "Out of reach the strike takes nothing; took " + finisher[0].punchedTarget.getType());
                    helper.assertTrue(scene.zombie.getHealth() == healthBefore[0]
                                    && Math.abs(scene.zombie.getZ() - zBefore[0]) < 0.01D
                                    && !EchoesSoundService.hasGiven(scene.user, scene.zombie),
                            "A strike that took nothing must not hurt, push or give; health " + scene.zombie.getHealth()
                                    + ", moved " + (scene.zombie.getZ() - zBefore[0]));
                })
                .thenWaitUntil(() -> helper.assertTrue(LivingComponentAction.getCurEntityAction(scene.act1) == null,
                        "The finisher's recovery has not ended"))
                .thenExecute(() -> {
                    scene.zombie.moveTo(scene.user.getX(), scene.user.getY(), scene.startZ);
                    scene.zombie.setDeltaMovement(Vec3.ZERO);
                })
                .thenIdle(5)
                .thenExecute(() -> finisher[0] = scene.pressFinisher())
                .thenWaitUntil(() -> helper.assertTrue(finisher[0] != null && finisher[0].punchedTarget != null,
                        "The strike has not come"))
                .thenExecute(() -> {
                    scene.assertTook(finisher[0]);
                    helper.assertTrue(scene.zombie.getHealth() <= healthBefore[0] - 3.0F,
                            "Back in reach the finisher hits; health " + healthBefore[0] + " -> "
                                    + scene.zombie.getHealth());
                })
                .thenExecute(scene::close)
                .thenSucceed();
    }

    private static final class FinisherScene {
        private static final short KEY = 172;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final ServerPlayer user;
        private final StandPower power;
        private final StandEntity act1;
        private final Zombie zombie;
        private final BlockPos floorCentre;
        private final double startZ;

        private FinisherScene(GameTestHelper helper, String name) {
            this.helper = helper;
            level = helper.getLevel();
            user = connectedFreezer(helper, name);
            power = PowerClass.STAND.attachGet(user);
            floorCentre = user.blockPosition().below();
            setFloor(Blocks.STONE.defaultBlockState());
            user.setPos(user.getX(), floorCentre.getY() + 1.0D, user.getZ());
            power.setStand(EchoesStands.ECHOES_ACT1.get());
            power.setResolveLevel(2);
            EchoesSoundService.select(user, ResourceLocation.withDefaultNamespace("entity.zombie.ambient"));
            helper.assertTrue(EchoesStands.ECHOES_ACT1.get().summon(user, power), "Setup: could not summon ACT1");
            act1 = power.getSummonedStandEntity();
            act1.summonLockTicks = 0;
            act1.gradualSummonWeaknessTicks = 0;
            zombie = new Zombie(EntityType.ZOMBIE, level);
            startZ = user.getZ() + 1.9D;
            zombie.moveTo(user.getX(), user.getY(), startZ, 180.0F, 0.0F);
            // too far to hit the user without walking, so the Echoes counter-attack has nothing to answer
            zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            // a zombie burns in daylight, which would pass for the sound's drain
            zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1000));
            helper.assertTrue(level.addFreshEntity(zombie), "Setup: the zombie was not added");
            zombie.setTarget(user);
            user.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getBoundingBox().getCenter());
            // what the user's client keeps sending while the crosshair is on the zombie
            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(zombie));
        }

        private void setFloor(BlockState state) {
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 15; z++) {
                    level.setBlock(floorCentre.offset(x, 0, z), state, 3);
                }
            }
        }

        private EntityActionInstance press(String base, String expected) {
            var selected = power.updateAvailableMoves().getContextVariationContainer(base);
            helper.assertTrue(selected != null && expected.equals(selected.ability.name())
                            && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "The " + base + " key must take " + expected);
            BufferingState state = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0.0F, state,
                    selected.ability.getAbilityId());
            helper.assertTrue(state.isActionSuccess, expected + " was not started");
            return LivingComponentAction.getCurEntityAction(act1);
        }

        private EntityActionInstance pressFinisher() {
            act1.setFinisherMeter(0.6F);
            return press("heavy_punch", "act1_finisher");
        }

        private void assertTook(EntityActionInstance finisher) {
            helper.assertTrue(finisher.punchedTarget.getType() == ActionTarget.TargetType.ENTITY
                            && finisher.punchedTarget.getEntity() == zombie,
                    "The strike must take the zombie; took " + finisher.punchedTarget.getType());
            helper.assertTrue(EchoesSoundService.hasGiven(user, zombie) && zombie.hasEffect(EchoesMobEffects.SOUND_HAUNT),
                    "The strike must give the zombie the sound");
        }

        private double eyeToBox() {
            return Math.sqrt(zombie.getBoundingBox().distanceToSqr(act1.getEyePosition()));
        }

        private double reach() {
            return act1.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        }

        private void close() {
            EchoesSoundService.cancelAllGives(user);
            EchoesStands.ECHOES_ACT1.get().forceUnsummon(user, power);
            zombie.discard();
            setFloor(Blocks.AIR.defaultBlockState());
            disconnect(user);
        }
    }

    // resolved afresh: the power caches its resolved keys for the tick, and the test does not tick it
    private static String heavyKey(StandPower power) {
        AvailableAbilities moves = new AvailableAbilities();
        moves.update(power, power.getMoveset());
        Ability heavy = moves.getContextVariation("heavy_punch");
        return heavy != null ? heavy.name() : null;
    }

    private static ServerPlayer freezer(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setPos(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX())) + 8.5D,
                origin.getY() + 5.0D,
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 8.5D);
        player.setNoGravity(true);
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static FallingCarrier carrier(GameTestHelper helper, String name) {
        FallingCarrier player = new FallingCarrier(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setPos(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX())) + 8.5D,
                origin.getY() + 5.0D,
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 8.5D);
        player.setNoGravity(true);
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    // A fake player is never hurt, so the fall damage it would take is read from LivingEntity.calculateFallDamage.
    private static final class FallingCarrier extends FakePlayer {
        private FallingCarrier(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        private int fallDamage(float distance) {
            return calculateFallDamage(distance, 1.0F);
        }
    }

    private static void inventory(ServerPlayer player, int count) {
        player.getInventory().clearContent();
        for (int slot = 0; slot < count; slot++) {
            ItemStack stack = new ItemStack(Items.COBBLESTONE);
            EchoesGravityService.infuse(stack, player.level().getGameTime());
            player.getInventory().setItem(slot, stack);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setPos(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX())) + 8.0D,
                origin.getY() + 2.0D,
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 8.0D);
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static Pig pig(GameTestHelper helper, Vec3 position) {
        Pig pig = EntityType.PIG.create(helper.getLevel());
        pig.setPos(position);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        helper.getLevel().addFreshEntity(pig);
        return pig;
    }

    private static void motion(GameTestHelper helper, Vec3 actual, Vec3 expected, String label) {
        helper.assertTrue(actual.distanceToSqr(expected) < 1.0E-12D,
                label + " expected " + expected + ", got " + actual);
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
    }
}
