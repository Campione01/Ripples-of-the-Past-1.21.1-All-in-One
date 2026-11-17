package rotp.addons.echoes.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
import rotp.addons.echoes.init.EchoesSounds;
import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.network.EchoesOnomatopoeiaPayloads;
import rotp.addons.echoes.stand.EchoesStandEntity;
import rotp.addons.echoes.state.EchoesChatService;
import rotp.addons.echoes.state.EchoesFreezeState;
import rotp.addons.echoes.state.EchoesForms;
import rotp.addons.echoes.state.EchoesGravityService;
import rotp.addons.echoes.state.EchoesOnomatopoeia;
import rotp.addons.echoes.state.EchoesOnomatopoeiaService;
import rotp.addons.echoes.state.EchoesSoundService;
import rotp.addons.echoes.state.EchoesUserState;
import rotp.addons.echoes.state.GravityInfusion;
import rotp.addons.echoes.state.GravityMarkSavedData;
import rotp.core.api.leap.LeapAccessPolicies;
import rotp.core.api.leap.LeapSource;
import rotp.core.client.entityanim.AnimationSet;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModGamerules;
import rotp.core.impl.stands._entitybase.StandBearingShotAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandTuning;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder("rotp_echoes_pilot")
@PrefixGameTestTemplate(false)
public final class EchoesPilotGameTests {
    private static final Vec3 MOTION = new Vec3(0.2D, 0.4D, -0.2D);
    private static final short SHORT_BAR_KEY = 171;
    // a 1 in 1000 chance a tick: e^-20 to miss a line this many ticks
    private static final int PASSIVE_TRIES = 20_000;
    // 016's strike and counter come at 35% and 40% a try: 0.65^200 to miss them all
    private static final int AUTO_TRIES = 200;
    // more than one 20-tick pressure period
    private static final int PRESSURE_TICKS = 25;
    // a press is handled and the Stand has synced its tail within these
    private static final int SETTLE_TICKS = 3;

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
                    // the author's drag leaves the fall alone, and presses a carrier in the air by 0.08
                    motion(helper, player.getDeltaMovement(),
                            new Vec3(MOTION.x * factors[i], MOTION.y - 0.08D, MOTION.z * factors[i]), "carrier drag");
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
                            && !EchoesGravityService.isInfused(player.getInventory().getItem(0))
                            && !EchoesGravityService.isInfused(player.getInventory().getItem(1)),
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
     * The master's and the author's 1.21.1 DisablerTagHandler.disable: Slowness, Mining Fatigue and Weakness at 255, no
     * jump, no Hunger (092's), no Resistance and no Blindness; DisablerTagHandler.enable removes them. The frozen target
     * cannot walk, jump, mine or deal melee damage, and it can still be hurt. A hand freeze (master HandFreezeHandler) has
     * Slowness IV, so its target still shuffles along.
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
            boolean handFreeze = mode == EchoesGravityAbility.Mode.HAND_FREEZE;
            for (var effect : List.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.WEAKNESS)) {
                MobEffectInstance active = target.getEffect(effect);
                int expected = handFreeze && effect == MobEffects.MOVEMENT_SLOWDOWN ? 3 : 255;
                helper.assertTrue(active != null && active.getAmplifier() == expected,
                        mode + " gives Slowness at " + (handFreeze ? 3 : 255) + ", Mining Fatigue and Weakness at 255"
                                + " and no Hunger, Blindness or Resistance; the port gave" + effects);
            }
            MobEffectInstance resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(resistance != null && resistance.getAmplifier() == 0
                            && !target.hasEffect(MobEffects.BLINDNESS) && !target.hasEffect(MobEffects.JUMP)
                            && !target.hasEffect(MobEffects.HUNGER),
                    mode + " adds no Resistance, Blindness, Jump Boost or Hunger; the port gave" + effects);
            float health = target.getHealth();
            target.hurt(target.damageSources().generic(), 4.0F);
            helper.assertTrue(target.getHealth() < health,
                    "1.16: a target frozen by " + mode + " can be hurt; the port's took no damage");
            float rise = jumpRise(target);
            helper.assertTrue((target.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.0D) == handFreeze && rise == 0.0F,
                    "A target frozen by " + mode + " cannot jump, and only a hand freeze lets it shuffle; speed "
                            + target.getAttributeValue(Attributes.MOVEMENT_SPEED) + ", jump " + rise);
            float frozenDigSpeed = target.getDestroySpeed(Blocks.STONE.defaultBlockState());
            helper.assertTrue(frozenDigSpeed < digSpeed * 0.001F
                            && target.getAttributeValue(Attributes.ATTACK_DAMAGE) == 0.0D,
                    "1.16: a target frozen by " + mode + " cannot mine or deal melee damage; dig speed "
                            + frozenDigSpeed + " of " + digSpeed + ", attack damage "
                            + target.getAttributeValue(Attributes.ATTACK_DAMAGE));

            // The owner's rule of 2026-11-05 (13.png), against 1.16's clearTargetEffects: the freeze goes on through a recall.
            EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            EchoesGravityService.tickUser(user);
            helper.assertTrue(frozen.active() && RotpEchoes.userState(user).frozenTarget() != null,
                    mode + " ended with the recall");
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: the next summon was refused");

            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_CANCEL);
            resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(!frozen.active() && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && !target.hasEffect(MobEffects.DIG_SLOWDOWN) && !target.hasEffect(MobEffects.WEAKNESS)
                            && jumpRise(target) > 0.0F
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

    // master GravityProjectileAction and GravityInfuseAction, the author's 1.21.1 conditions: a press with nothing to do
    // is refused, says why and costs nothing (092 ran it and paid). What he holds opens the keys.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 60,
            batch = "rotpEchoesEmptyThrow")
    public static void gravityKeysWithNothingToDoRefuseAndSayWhy(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesEmptyThrow");
        StandPower power = PowerClass.STAND.attachGet(user);
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.summonLockTicks = 0;
            stand.setNoGravity(true);
            user.setYRot(0.0F);
            user.setXRot(0.0F);
            power.setResolveLevel(4);
            user.getInventory().clearContent();
            power.setStamina(250.0F);
            Ability projectile = power.getAbility("gravity_projectile");
            refuses(helper, projectile, power, "rotp_echoes.message.need_gravity_item");
            var selected = power.updateAvailableMoves().getContextVariationContainer(projectile);
            helper.assertTrue(selected == null || !AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "The throw's press must be refused with no gravity item");
            refuses(helper, power.getAbility("gravity_infuse"), power, "rotp_echoes.message.need_held_item");
            refuses(helper, power.getAbility("gravity_infuse_cancel"), power,
                    "rotp_echoes.message.need_own_gravity_item");
            refuses(helper, power.getAbility("freeze"), power, "rotp_echoes.message.need_freeze_target");
            refuses(helper, power.getAbility("hand_freeze"), power, "rotp_echoes.message.need_freeze_target");

            user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE));
            helper.assertTrue(power.getAbility("gravity_infuse").checkSpecificConditions(power).isPositive(),
                    "An item in the off hand must open Gravity Infuse");
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.INFUSE);
            helper.assertTrue(EchoesGravityService.isInfused(user.getOffhandItem())
                            && projectile.checkSpecificConditions(power).isPositive()
                            && power.getAbility("gravity_infuse_cancel").checkSpecificConditions(power).isPositive(),
                    "A gravity item in the off hand must open the throw and the cancel");
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.PROJECTILE);
            helper.assertTrue(user.getOffhandItem().isEmpty(),
                    "The throw must take the gravity item from the off hand, which the author's check passed and his throw"
                            + " left there");
        } finally {
            discardThrownItems(helper, user);
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            remove(helper, user);
        }
        helper.succeed();
    }

    /**
     * The master's and the author's GravityItemHelper have no timer (092's lasted 5 seconds): gravity stays on an item
     * until it is cancelled. One item at a time, so a second press moves it. On the ground it ends when its owner is more
     * than 5 blocks away or gone, and in the pocket when he has left ACT3.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 160,
            batch = "rotpEchoesGravityItems")
    public static void gravityStaysOnAnItemUntilItsOwnerGoesOrCancelsIt(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesItemOwner");
        StandPower power = PowerClass.STAND.attachGet(user);
        power.setStand(EchoesStands.ECHOES_ACT3.get());
        ItemStack first = new ItemStack(Items.COBBLESTONE);
        user.setItemInHand(InteractionHand.MAIN_HAND, first);
        EchoesGravityService.perform(user, EchoesGravityAbility.Mode.INFUSE);
        helper.assertTrue(user.getMainHandItem() == first && EchoesGravityService.isInfused(first),
                "A single held item must take the gravity and stay in the hand");
        helper.runAfterDelay(110, () -> {
            try {
                EchoesGravityService.tickInventoryGravity(user);
                helper.assertTrue(EchoesGravityService.isInfused(first), "Gravity must not run out after 5 seconds");

                ItemStack second = new ItemStack(Items.DIRT);
                user.setItemInHand(InteractionHand.OFF_HAND, second);
                EchoesGravityService.perform(user, EchoesGravityAbility.Mode.INFUSE);
                helper.assertTrue(EchoesGravityService.isInfused(second) && !EchoesGravityService.isInfused(first),
                        "A second press must move the gravity to the other item");
                EchoesGravityService.perform(user, EchoesGravityAbility.Mode.INFUSE_CANCEL);
                helper.assertTrue(!EchoesGravityService.isInfused(second), "The cancel must lift the held item's gravity");

                ItemEntity near = lyingGravityItem(helper, user, user.getUUID(), 4.0D);
                EchoesGravityService.tickInfusedItem(near);
                helper.assertTrue(EchoesGravityService.isInfused(near.getItem()) && near.hasPickUpDelay(),
                        "An item on the ground near its owner keeps its gravity and cannot be picked up");
                ItemEntity far = lyingGravityItem(helper, user, user.getUUID(), 6.0D);
                EchoesGravityService.tickInfusedItem(far);
                helper.assertTrue(!EchoesGravityService.isInfused(far.getItem()),
                        "An item on the ground more than 5 blocks from its owner loses its gravity");
                ItemEntity orphan = lyingGravityItem(helper, user, UUID.randomUUID(), 1.0D);
                EchoesGravityService.tickInfusedItem(orphan);
                helper.assertTrue(!EchoesGravityService.isInfused(orphan.getItem()),
                        "An item whose owner is gone loses its gravity, or nobody could ever pick it up");

                ItemStack own = new ItemStack(Items.STONE);
                EchoesGravityService.infuse(own, user.getUUID());
                ItemStack others = new ItemStack(Items.GRANITE);
                EchoesGravityService.infuse(others, UUID.randomUUID());
                user.getInventory().setItem(9, own);
                user.getInventory().setItem(10, others);
                power.setStand(EchoesStands.ECHOES_ACT2.get());
                EchoesGravityService.tickInventoryGravity(user);
                helper.assertTrue(!EchoesGravityService.isInfused(own) && EchoesGravityService.isInfused(others),
                        "A user who left ACT3 loses the gravity on his own items, not on another's");
            } finally {
                remove(helper, user);
            }
            helper.succeed();
        });
    }

    private static ItemEntity lyingGravityItem(GameTestHelper helper, ServerPlayer user, UUID owner, double blocks) {
        ItemStack stack = new ItemStack(Items.COBBLESTONE);
        EchoesGravityService.infuse(stack, owner);
        return new ItemEntity(helper.getLevel(), user.getX() + blocks, user.getY(), user.getZ(), stack);
    }

    /**
     * The master's GravityItemEvents and MCUtilMixin: with a gravity item in the hands there is no jump and no leap, and
     * a fall hurts 9 more (Jump -10). From the pocket it is the count's lower jump only.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesCarrierAttributes")
    public static void aGravityItemInTheHandsKeepsItsCarrierDown(GameTestHelper helper) {
        FallingCarrier player = carrier(helper, "EchoesHoldingCarrier");
        try {
            float plain = jumpRise(player);
            int plainFall = player.fallDamage(10.0F);
            inventory(player, 1);
            EchoesGravityService.tickInventoryGravity(player);
            helper.assertTrue(jumpRise(player) == 0.0F && player.fallDamage(10.0F) == plainFall + 9,
                    "A gravity item in the hand stops the jump and makes a fall hurt 9 more; jump " + jumpRise(player)
                            + ", a 10-block fall hurts " + player.fallDamage(10.0F));
            helper.assertTrue(!LeapAccessPolicies.allowsHud(player, LeapSource.STAND)
                            && !LeapAccessPolicies.allowsHud(player, LeapSource.PLAYER_POWER),
                    "A gravity item in the hand stops the leap");
            player.getInventory().selected = 8;
            EchoesGravityService.tickInventoryGravity(player);
            helper.assertTrue(Math.abs(jumpRise(player) - plain) < 1.0E-4F && player.fallDamage(10.0F) == plainFall
                            && LeapAccessPolicies.allowsHud(player, LeapSource.STAND),
                    "Out of the hand one gravity item leaves the jump, the fall and the leap as they were; jump "
                            + jumpRise(player) + " of " + plain);
        } finally {
            EchoesGravityService.cleanupUser(player, true);
            remove(helper, player);
        }
        helper.succeed();
    }

    /**
     * The author's GravityProjectileAction and GravityItemEvents.onItemEntityTick: a throw is a flight, so nothing is hurt
     * at the press, and the first living thing in its way takes it, not the one the user aims at behind it. It lands
     * with its gravity on.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS, batch = "rotpEchoesGravityThrow")
    public static void aThrownGravityItemFliesAndHitsWhatIsInItsWay(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> gravityThrowBody(h));
    }

    private static void gravityThrowBody(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesItemThrower");
        StandPower power = PowerClass.STAND.attachGet(user);
        power.setStand(EchoesStands.ECHOES_ACT3.get());
        helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
        StandEntity stand = power.getSummonedStandEntity();
        stand.setNoGravity(true);
        Zombie near = standingZombie(helper, user, 4.0D);
        Zombie behind = standingZombie(helper, user, 8.0D);
        user.setYRot(0.0F);
        user.setXRot(0.0F);
        ItemStack stack = new ItemStack(Items.COBBLESTONE);
        EchoesGravityService.infuse(stack, user.getUUID());
        user.setItemInHand(InteractionHand.MAIN_HAND, stack);
        EchoesGravityService.perform(user, EchoesGravityAbility.Mode.PROJECTILE);
        helper.assertTrue(near.getHealth() == near.getMaxHealth() && behind.getHealth() == behind.getMaxHealth(),
                "A throw is a flight: nothing is hurt at the press");
        ItemEntity thrown = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                user.getBoundingBox().inflate(4.0D)).get(0);
        helper.assertTrue(thrown.getOwner() == user, "The thrown item must know its thrower");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(near.getHealth() < near.getMaxHealth(),
                        "The item must hit the zombie in its way"))
                .thenExecute(() -> {
                    GravityInfusion infusion = thrown.getItem().get(EchoesDataComponents.GRAVITY_INFUSION);
                    helper.assertTrue(behind.getHealth() == behind.getMaxHealth() && infusion != null
                                    && !infusion.thrown() && user.getHealth() == user.getMaxHealth(),
                            "It hits once and no one behind, and keeps its gravity; zombie behind " + behind.getHealth()
                                    + ", item " + infusion + ", user " + user.getHealth());
                    thrown.discard();
                    near.discard();
                    behind.discard();
                    EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
                    remove(helper, user);
                })
                .thenSucceed();
    }

    // its middle at the user's eyes, straight ahead of a user who looks along yaw 0
    private static Zombie standingZombie(GameTestHelper helper, ServerPlayer user, double blocks) {
        Zombie zombie = new Zombie(EntityType.ZOMBIE, helper.getLevel());
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        // a zombie burns in daylight, which would pass for the hit
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1000));
        zombie.moveTo(user.getX(), user.getEyeY() - zombie.getBbHeight() / 2.0D, user.getZ() + blocks, 180.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Setup: the zombie was not added");
        return zombie;
    }

    /**
     * The master's DisablerTagHandler.onFrozenAttackerHurt and the author's, with his onLivingKnockBack: what is frozen or
     * self-frozen deals no damage and takes no knockback, and both come back when it is lifted.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesFrozenAttacker")
    public static void whatIsFrozenDealsNoDamageAndTakesNoKnockback(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesFrozenAttacker");
        PowerClass.STAND.attachGet(user).setStand(EchoesStands.ECHOES_ACT3.get());
        Zombie zombie = standingZombie(helper, user, 3.0D);
        Pig victim = pig(helper, user.position().add(-2.0D, 0.0D, 0.0D));
        try {
            user.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getBoundingBox().getCenter());
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_TARGET);
            helper.assertTrue(RotpEchoes.freezeState(zombie).active(), "Setup: the zombie is not frozen");
            float health = victim.getHealth();
            victim.hurt(zombie.damageSources().mobAttack(zombie), 4.0F);
            helper.assertTrue(victim.getHealth() == health, "A frozen mob must deal no damage");
            zombie.setDeltaMovement(Vec3.ZERO);
            zombie.knockback(1.0D, 1.0D, 0.0D);
            helper.assertTrue(zombie.getDeltaMovement().lengthSqr() == 0.0D, "A frozen mob must take no knockback");

            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_SELF);
            helper.assertTrue(!RotpEchoes.freezeState(zombie).active(), "Setup: the self freeze keeps the zombie frozen");
            victim.hurt(user.damageSources().playerAttack(user), 4.0F);
            helper.assertTrue(victim.getHealth() == health, "A self-frozen user must deal no damage");
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_SELF);

            victim.hurt(zombie.damageSources().mobAttack(zombie), 4.0F);
            helper.assertTrue(victim.getHealth() < health, "A mob let go deals damage again");
            zombie.knockback(1.0D, 1.0D, 0.0D);
            helper.assertTrue(zombie.getDeltaMovement().lengthSqr() > 0.0D, "A mob let go takes knockback again");
        } finally {
            zombie.discard();
            victim.discard();
            EchoesGravityService.cleanupUser(user, true);
            remove(helper, user);
        }
        helper.succeed();
    }

    /**
     * What the master's lockMovement and the author's updateFreeze do to a player, whose motion his client owns: the server
     * leaves his gravity on, where the port's noGravity left a flier hanging in the air, and his client takes him down at
     * 4.5 blocks a tick. His held moves stop (the master's interruptHeldActions) and he cannot leap (its mixins).
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesFrozenPlayer")
    public static void aFrozenPlayerFallsOnHisClientAndLetsGoOfHeldMoves(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesPlayerFreezer");
        StandPower userPower = PowerClass.STAND.attachGet(user);
        userPower.setStand(EchoesStands.ECHOES_ACT3.get());
        ServerPlayer target = freezer(helper, "EchoesFrozenFlier");
        StandPower targetPower = PowerClass.STAND.attachGet(target);
        try {
            target.setNoGravity(false);
            target.setPos(user.getX(), user.getY(), user.getZ() + 3.0D);
            targetPower.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(target, targetPower),
                    "Setup: could not summon the target's ACT3");
            StandEntity targetStand = targetPower.getSummonedStandEntity();
            targetStand.summonLockTicks = 0;
            targetStand.gradualSummonWeaknessTicks = 0;
            targetStand.setNoGravity(true);
            Ability guard = targetPower.getAbility("guard");
            BufferingState pressed = BufferingState.clickOnly();
            AbilityInput.keyPress(SHORT_BAR_KEY, guard, target, null, InputMethod.HOLD, 0.0F, pressed, guard.getAbilityId());
            helper.assertTrue(pressed.isActionSuccess && AbilityInput.hasHeldInput(target, PowerClass.STAND),
                    "Setup: the target does not hold its guard");
            // out of the ray, which takes another's Stand as the first living thing in the aim
            targetStand.moveTo(target.getX() + 3.0D, target.getY(), target.getZ());
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_TARGET);
            helper.assertTrue(RotpEchoes.freezeState(target).active(), "Setup: the target is not frozen");

            Vec3 place = target.position();
            EchoesGravityService.tickFrozenTarget(target);
            helper.assertTrue(!target.isNoGravity() && target.position().equals(place),
                    "The server must leave a frozen player's gravity and place to his client; no gravity "
                            + target.isNoGravity() + ", moved to " + target.position());
            helper.assertTrue(!AbilityInput.hasHeldInput(target, PowerClass.STAND),
                    "A frozen player must let go of his held moves");
            helper.assertTrue(!LeapAccessPolicies.allowsHud(target, LeapSource.STAND), "A frozen player cannot leap");
            target.setOnGround(false);
            target.setDeltaMovement(0.1D, 0.4D, 0.1D);
            EchoesGravityService.tickLocalFreeze(target);
            motion(helper, target.getDeltaMovement(), new Vec3(0.0D, -4.5D, 0.0D), "frozen player in the air");
            target.setOnGround(true);
            target.setDeltaMovement(0.1D, 0.0D, 0.1D);
            EchoesGravityService.tickLocalFreeze(target);
            motion(helper, target.getDeltaMovement(), Vec3.ZERO, "frozen player on the ground");

            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_CANCEL);
            target.setDeltaMovement(0.1D, 0.4D, 0.1D);
            EchoesGravityService.tickLocalFreeze(target);
            motion(helper, target.getDeltaMovement(), new Vec3(0.1D, 0.4D, 0.1D), "player let go");
            helper.assertTrue(LeapAccessPolicies.allowsHud(target, LeapSource.STAND), "A player let go can leap");
        } finally {
            EchoesGravityService.releaseFrozenTarget(target);
            if (targetPower.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(target, targetPower);
            }
            remove(helper, target);
            disconnect(user);
        }
        helper.succeed();
    }

    /**
     * The author's and the master's freezes take whoever is in the aim, so a press with a freeze on moves it there, and
     * either cancel key lifts it, without aim. A user another has frozen cannot freeze. A freeze ends beyond 5 blocks from its
     * user, Stand out or not: both measure from him.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesFreezeMoves")
    public static void aFreezeMovesToWhoIsInTheAimAndEndsOutOfRange(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesFreezeMover");
        StandPower power = PowerClass.STAND.attachGet(user);
        power.setStand(EchoesStands.ECHOES_ACT3.get());
        Zombie first = standingZombie(helper, user, 3.0D);
        Zombie second = standingZombie(helper, user, -3.0D);
        try {
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            power.getSummonedStandEntity().setNoGravity(true);
            user.lookAt(EntityAnchorArgument.Anchor.EYES, first.getBoundingBox().getCenter());
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_TARGET);
            helper.assertTrue(RotpEchoes.freezeState(first).active(), "Setup: the first zombie is not frozen");
            user.lookAt(EntityAnchorArgument.Anchor.EYES, second.getBoundingBox().getCenter());
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_TARGET);
            helper.assertTrue(!RotpEchoes.freezeState(first).active() && RotpEchoes.freezeState(second).active(),
                    "A second press must move the freeze to the zombie in the aim");
            user.setYRot(90.0F);
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.HAND_FREEZE_CANCEL);
            helper.assertTrue(!RotpEchoes.freezeState(second).active(),
                    "Either cancel key must lift a target freeze, with no aim");

            user.lookAt(EntityAnchorArgument.Anchor.EYES, second.getBoundingBox().getCenter());
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.HAND_FREEZE);
            helper.assertTrue(RotpEchoes.freezeState(second).active(), "Setup: the hand freeze did not start");
            EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            EchoesGravityService.tickFrozenTarget(second);
            helper.assertTrue(RotpEchoes.freezeState(second).active(), "Within its range a freeze outlives the recall");
            second.moveTo(user.getX(), second.getY(), user.getZ() - 7.0D);
            EchoesGravityService.tickFrozenTarget(second);
            helper.assertTrue(!RotpEchoes.freezeState(second).active(),
                    "With the Stand recalled a freeze still ends once its user is out of its range");

            user.lookAt(EntityAnchorArgument.Anchor.EYES, first.getBoundingBox().getCenter());
            RotpEchoes.freezeState(user).activate(user, UUID.randomUUID(), 0, EchoesFreezeState.Kind.TARGET,
                    helper.getLevel().getGameTime() + 100L);
            helper.assertTrue(!power.getAbility("freeze").checkSpecificConditions(power).isPositive(),
                    "A user another has frozen cannot freeze");
            EchoesGravityService.releaseFrozenTarget(user);
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_SELF);
            helper.assertTrue(power.getAbility("freeze").checkSpecificConditions(power).isPositive(),
                    "His own self freeze only gives way to a new freeze, as in the master");
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.HAND_FREEZE_SELF_CANCEL);
            helper.assertTrue(RotpEchoes.userState(user).activeSkill() == EchoesUserState.ActiveSkill.NONE
                            && !user.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "Either cancel key must lift a self freeze");
        } finally {
            first.discard();
            second.discard();
            EchoesGravityService.cleanupUser(user, true);
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            disconnect(user);
        }
        helper.succeed();
    }

    /**
     * The master's tickSelfFreeze, the owner's choice over the author's fixed 30 seconds: a self freeze is a toggle that
     * drains the stamina and ends when it runs out. It takes the mining as both do, and presses its user down as the
     * author's does; the self hand freeze only stops him going up. And his FreezeSoundHelper has no wait between voices.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesSelfFreezeTime")
    public static void aSelfFreezeRunsUntilTheStaminaDoesAndCriesOutEveryPress(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesSelfTimer");
        StandPower power = PowerClass.STAND.attachGet(user);
        power.setStand(EchoesStands.ECHOES_ACT3.get());
        EchoesUserState state = RotpEchoes.userState(user);
        try {
            soundsSent(user, EchoesSounds.FREEZE_VOICE.get());
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_SELF);
            helper.assertTrue(user.getEffect(MobEffects.DIG_SLOWDOWN) != null
                            && user.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier() == 255,
                    "A self freeze takes the mining");
            helper.assertTrue(!power.isStaminaInfinite(), "Setup: the user needs a finite stamina bar");
            power.setStamina(10.0F);
            for (user.tickCount = 18; user.tickCount <= 21; user.tickCount++) {
                EchoesGravityService.tickUser(user);
            }
            helper.assertTrue(Math.abs(power.getStamina() - 9.95F) < 1.0E-4F
                            && state.activeSkill() == EchoesUserState.ActiveSkill.FREEZE_SELF,
                    "A self freeze drains 0.05 of the stamina a second; the bar went from 10 to " + power.getStamina());
            user.setOnGround(false);
            user.setDeltaMovement(0.1D, 0.4D, 0.1D);
            EchoesGravityService.tickLocalFreeze(user);
            motion(helper, user.getDeltaMovement(), new Vec3(0.0D, -4.5D, 0.0D), "self-frozen user in the air");
            power.setStamina(0.03F);
            user.tickCount = 40;
            EchoesGravityService.tickUser(user);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.NONE
                            && !user.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && !user.hasEffect(MobEffects.DIG_SLOWDOWN),
                    "A self freeze must end when the stamina runs out, with its effects");

            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.HAND_FREEZE_SELF);
            helper.assertTrue(soundsSent(user, EchoesSounds.FREEZE_VOICE.get()) == 2,
                    "Each press has its voice, with no 30 seconds between them");
            user.setDeltaMovement(0.1D, 0.4D, 0.1D);
            EchoesGravityService.tickLocalFreeze(user);
            motion(helper, user.getDeltaMovement(), Vec3.ZERO, "self hand freeze going up");
            user.setDeltaMovement(0.1D, -0.3D, 0.1D);
            EchoesGravityService.tickLocalFreeze(user);
            motion(helper, user.getDeltaMovement(), new Vec3(0.0D, -0.3D, 0.0D), "self hand freeze falling");
        } finally {
            EchoesGravityService.cleanupUser(user, true);
            disconnect(user);
        }
        helper.succeed();
    }

    /**
     * R772.1 (EV-STANDCFG item4): the self freeze's upkeep is a stamina cost, so it is paid in the Stands page's stamina
     * share and weighed in it: at 50 % the bar loses 0.025 a second, where the direct set took the whole 0.05, and a bar
     * of 0.03 still pays it.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesSelfFreezeShare")
    public static void aSelfFreezesUpkeepIsPaidInTheStaminaShare(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesSelfShare");
        StandPower power = PowerClass.STAND.attachGet(user);
        power.setStand(EchoesStands.ECHOES_ACT3.get());
        EchoesUserState state = RotpEchoes.userState(user);
        StandTuning tuning = EchoesStands.ECHOES_ACT3.get().getTuning();
        JsonObject half = new JsonObject();
        half.addProperty("staminaCosts", 50);
        try {
            tuning.applyConfig(half);
            helper.assertTrue(Math.abs(power.getStaminaCostShare(EchoesGravityService.SELF_STAMINA_DRAIN) - 0.025F) < 1.0E-6F,
                    "Setup: the 50% stamina share is not on");
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_SELF);
            helper.assertTrue(!power.isStaminaInfinite() && state.activeSkill() == EchoesUserState.ActiveSkill.FREEZE_SELF,
                    "Setup: no self freeze on a finite stamina bar");
            power.setStamina(10.0F);
            for (user.tickCount = 18; user.tickCount <= 21; user.tickCount++) {
                EchoesGravityService.tickUser(user);
            }
            helper.assertTrue(Math.abs(power.getStamina() - 9.975F) < 1.0E-4F,
                    "At a 50% stamina share the self freeze's upkeep is 0.025 a second; the bar went from 10 to "
                            + power.getStamina());
            power.setStamina(0.03F);
            user.tickCount = 40;
            EchoesGravityService.tickUser(user);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.FREEZE_SELF
                            && Math.abs(power.getStamina() - 0.005F) < 1.0E-4F,
                    "A bar of 0.03 pays the halved upkeep of 0.025 and the freeze goes on; it is "
                            + state.activeSkill() + " at " + power.getStamina());
        } finally {
            tuning.restoreDefaults();
            EchoesGravityService.cleanupUser(user, true);
            disconnect(user);
        }
        helper.succeed();
    }

    /**
     * R772.1 EV-STANDCFG item3: a word's damage is dealt by whichever tick delivers it (a marked entity's, a block's, a
     * thing on a block), none of them the Stand's own code, so it is applied in its owner's name: at a damage share of
     * 50 % to mobs Jian's 0.25 is 0.125, at the default share the whole 0.25.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesWordShare")
    public static void aWordsDamageIsPaidInTheStandsDamageShare(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesWordShare");
        StandPower power = PowerClass.STAND.attachGet(user);
        power.setStand(EchoesStands.ECHOES_ACT3.get());
        Zombie zombie = standingZombie(helper, user, 3.0D);
        StandTuning tuning = EchoesStands.ECHOES_ACT3.get().getTuning();
        try {
            float whole = wordDamage(user, zombie);
            helper.assertTrue(Math.abs(whole - 0.25F) < 1.0E-5F,
                    "At the default share Jian pricks for 0.25; the zombie took " + whole);
            JsonObject half = new JsonObject();
            half.addProperty("damageVsMobs", 50);
            tuning.applyConfig(half);
            float halved = wordDamage(user, zombie);
            helper.assertTrue(Math.abs(halved - 0.125F) < 1.0E-5F,
                    "At 50 % damage to mobs Jian pricks for 0.125; the zombie took " + halved);
        } finally {
            tuning.restoreDefaults();
            zombie.discard();
            disconnect(user);
        }
        helper.succeed();
    }

    // Jian applied in the user's name; what the zombie lost
    private static float wordDamage(ServerPlayer user, Zombie zombie) {
        zombie.setHealth(zombie.getMaxHealth());
        zombie.invulnerableTime = 0;
        float before = zombie.getHealth();
        EchoesOnomatopoeiaService.applyBy(user.getUUID(), EchoesOnomatopoeia.JIAN, zombie);
        return before - zombie.getHealth();
    }

    /**
     * The author's AddonStands and EchoesHeavyChargedAbility: ACT3 has the core's bearing shot, shown under the core's
     * name, and cries out as the charged heavy starts.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesAct3Author")
    public static void act3HasTheBearingShotAndCriesAtTheChargeStart(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesChargeCry");
        StandPower power = PowerClass.STAND.attachGet(user);
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            stand.setNoGravity(true);
            Ability bearing = power.getAbility("bearing_shot");
            helper.assertTrue(bearing instanceof StandBearingShotAbility
                            && bearing.getTranslationKey().startsWith("jojo_ripples."),
                    "ACT3 must have the core's bearing shot under the core's name: " + bearing);
            stand.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_NUGGET));
            helper.assertTrue(bearing.isAbilityAvailable(power), "A nugget in ACT3's hand must open the bearing shot");
            stand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            soundsSent(user, EchoesSounds.HEAVY_ATTACK.get());
            ((EntityActionAbility) power.getAbility("heavy_charged"))
                    .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
            helper.assertTrue(soundsSent(user, EchoesSounds.HEAVY_ATTACK.get()) == 1,
                    "ACT3 must cry out once as the charged heavy starts");
        } finally {
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            disconnect(user);
        }
        helper.succeed();
    }

    /**
     * The master's SheerHeartAttackCompat: while a Sheer Heart Attack is frozen its owner's hands are, wherever he is, with
     * no press and no range; it ends with the car's freeze. The real car is in the Killer Queen add-ons, which this test
     * server does not load, so a zombie of the name the master also goes by stands in for it, with its getter.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesSheerHeart")
    public static void aFrozenSheerHeartAttackFreezesItsOwnersHands(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesCarFreezer");
        PowerClass.STAND.attachGet(user).setStand(EchoesStands.ECHOES_ACT3.get());
        ServerPlayer owner = freezer(helper, "EchoesCarOwner");
        SheerHeart car = new SheerHeart(helper.getLevel(), owner);
        try {
            car.setNoAi(true);
            car.setNoGravity(true);
            car.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1000));
            car.moveTo(user.getX(), user.getEyeY() - car.getBbHeight() / 2.0D, user.getZ() + 3.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(car), "Setup: the car was not added");
            owner.setPos(user.getX() + 40.0D, user.getY(), user.getZ());
            owner.getAbilities().mayfly = true;
            user.lookAt(EntityAnchorArgument.Anchor.EYES, car.getBoundingBox().getCenter());
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_TARGET);
            helper.assertTrue(RotpEchoes.freezeState(car).active(), "Setup: the car is not frozen");
            helper.assertTrue(!EchoesGravityService.isHandFrozen(owner), "Setup: the owner's hands are free before its tick");

            EchoesGravityService.tickFrozenTarget(car);
            MobEffectInstance slowness = owner.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            helper.assertTrue(EchoesGravityService.isHandFrozen(owner) && EchoesGravityService.isFrozen(owner)
                            && slowness != null && slowness.getAmplifier() == EchoesGravityService.HAND_FREEZE_SLOWNESS
                            && owner.hasEffect(MobEffects.DIG_SLOWDOWN) && !owner.getAbilities().mayfly,
                    "A frozen Sheer Heart Attack must freeze its owner's hands; the owner has" + activeEffects(owner));
            helper.assertTrue(jumpRise(owner) == 0.0F && !LeapAccessPolicies.allowsHud(owner, LeapSource.STAND),
                    "The owner of a frozen car cannot jump or leap; jump " + jumpRise(owner));

            EchoesGravityService.tickFrozenTarget(owner);
            helper.assertTrue(EchoesGravityService.isHandFrozen(owner), "It has no range: the owner is 40 blocks away");
            owner.setDeltaMovement(0.1D, 0.4D, 0.1D);
            EchoesGravityService.tickLocalFreeze(owner);
            motion(helper, owner.getDeltaMovement(), new Vec3(0.1D, 0.4D, 0.1D), "the owner's own motion");

            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_CANCEL);
            EchoesGravityService.tickFrozenTarget(owner);
            helper.assertTrue(!EchoesGravityService.isHandFrozen(owner) && !owner.hasEffect(MobEffects.DIG_SLOWDOWN)
                            && owner.getAbilities().mayfly && jumpRise(owner) > 0.0F,
                    "The owner's hands must come free with the car; effects" + activeEffects(owner) + ", mayfly "
                            + owner.getAbilities().mayfly + ", jump " + jumpRise(owner));
        } finally {
            EchoesGravityService.releaseFrozenTarget(owner);
            EchoesGravityService.releaseFrozenTarget(car);
            car.discard();
            remove(helper, owner);
            disconnect(user);
        }
        helper.succeed();
    }

    public static final class SheerHeart extends Zombie {
        private final LivingEntity owner;

        private SheerHeart(Level level, LivingEntity owner) {
            super(EntityType.ZOMBIE, level);
            this.owner = owner;
        }

        public LivingEntity getKillerQueenOwner() {
            return owner;
        }
    }

    // how many of this sound were sent to this player since the last call
    private static int soundsSent(ServerPlayer player, SoundEvent sound) {
        EmbeddedChannel channel = (EmbeddedChannel) player.connection.getConnection().channel();
        channel.flushOutbound();
        int count = 0;
        for (Object sent = channel.readOutbound(); sent != null; sent = channel.readOutbound()) {
            if (sent instanceof ClientboundSoundPacket packet && packet.getSound().value() == sound) {
                count++;
            }
        }
        return count;
    }

    // 092 GravityItemEvents gives a carrier Jump -min(count, 5), which 1.16 adds to the jump as 0.1 * (1 - level):
    // no change with one item, 0.1 less for each item from two up to five, and nothing once none is carried. It does
    // not lift a user whom a freeze keeps on the ground.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void carriedGravityItemsLowerTheJumpFromTwoUp(GameTestHelper helper) {
        ServerPlayer player = freezer(helper, "EchoesHeavyCarrier");
        try {
            // out of the hands, which stop the jump: aGravityItemInTheHandsKeepsItsCarrierDown
            player.getInventory().selected = 8;
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
            player.getInventory().selected = 8;
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
            player.getInventory().selected = 8;
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

                EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_CANCEL);
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
                EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_CANCEL);
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
                EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_CANCEL);
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
     * both hands, and in the last 20 of the 600 ticks Slowness, Mining Fatigue and Weakness come off (the held jump
     * stays). The target freeze does neither.
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
                                    && jumpRise(target) == 0.0F,
                            "1.16 HandFreezeHandler takes Slowness, Mining Fatigue and Weakness off a hand freeze in"
                                    + " its last 20 ticks, and the held jump stays; with 9 ticks left the"
                                    + " port's target has" + activeEffects(target) + ", jump " + jumpRise(target));
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
        helper.testInfo.addListener(new Leaver(player));
        return player;
    }

    // A test that fails before its own disconnect would leave its Echoes user beside the next tests' scenes.
    private record Leaver(ServerPlayer player) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {}

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            leave();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            leave();
        }

        private void leave() {
            if (!player.hasDisconnected()) {
                disconnect(player);
            }
        }
    }

    /** R772.1: a connected Echoes user leaves the world when his test fails, whichever step it fails at. */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 20)
    public static void aFailedTestTakesItsConnectedUserOut(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesLeaver");
        UUID id = user.getUUID();
        Leaver leaver = helper.testInfo.getListeners()
                .filter(Leaver.class::isInstance)
                .map(Leaver.class::cast)
                .filter(listener -> listener.player() == user)
                .findFirst()
                .orElse(null);
        if (leaver == null) {
            disconnect(user);
            helper.fail("A connected test user has nothing that takes him out when his test fails");
        }
        leaver.testFailed(helper.testInfo, null);
        helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(id) == null,
                "A failed test must take its connected Echoes user out of the world");
        helper.succeed();
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
     * while its user has ACT2, Stand out or not (the owner's rule of 2026-11-05 over the master, which asked for it out);
     * Don hits once for 0.25 and knocks the target back and up; taking the words back frees the target and returns the
     * tail. A word acts for nobody who has lost ACT2, and it ends with its user's logout, death or change of level.
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
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            EchoesOnomatopoeiaService.tickMarked(target);
            helper.assertTrue(target.getHealth() == health,
                    "A word acts only for a user who has ACT2; another form's user hurt the zombie");
            power.setStand(EchoesStands.ECHOES_ACT2.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT2.get().summon(user, power), "Setup: could not summon ACT2");
            power.getSummonedStandEntity().setNoGravity(true);
            EchoesStands.ECHOES_ACT2.get().forceUnsummon(user, power);
            EchoesOnomatopoeiaService.tickMarked(target);
            float afterOnce = target.getHealth();
            helper.assertTrue(afterOnce < health && target.getDeltaMovement().y > 0.3D,
                    "With ACT2 recalled the word goes on: Don hits once and throws the target up and back; health "
                            + health + " -> " + afterOnce
                            + ", motion " + target.getDeltaMovement());
            target.setDeltaMovement(Vec3.ZERO);
            EchoesOnomatopoeiaService.tickMarked(target);
            helper.assertTrue(target.getHealth() == afterOnce, "Don acts only once");

            EchoesOnomatopoeiaService.takeBackMarks(user);
            helper.assertTrue(EchoesOnomatopoeiaService.markOf(target) == null && !RotpEchoes.userState(user).tailOut(),
                    "Taking the words back must free the zombie and return the tail");

            helper.assertTrue(EchoesOnomatopoeiaService.attachInAim(user)
                            && EchoesOnomatopoeiaService.markOf(target) != null,
                    "Setup: a second word did not land");
            EchoesOnomatopoeiaService.endWordsOnOthers(user);
            helper.assertTrue(EchoesOnomatopoeiaService.markOf(target) == null,
                    "A word must end with its user's logout, death or change of level");
            EchoesOnomatopoeiaService.takeBackMarks(user);

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
     * R772.1 (master OnomatopoeiaAttachedEntity.tick and remove): a word on a player ends with his death, logout, change
     * of level and the loss of his Stand, taking its state along, and his client is told each time; so it is at the
     * respawn, whose new player keeps the old entity id a client draws the word by. Another ACT only puts it to sleep.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesWordEnds")
    public static void aWordOnThePlayerEndsAndHisClientIsTold(GameTestHelper helper) {
        ServerPlayer first = connectedFreezer(helper, "EchoesSelfWord");
        ServerPlayer last = first;
        try {
            StandPower power = PowerClass.STAND.attachGet(first);
            power.setStand(EchoesStands.ECHOES_ACT2.get());
            EchoesOnomatopoeiaService.select(first, EchoesOnomatopoeia.RERE);

            selfWord(helper, first);
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            EchoesOnomatopoeiaService.tickMarked(first);
            helper.assertTrue(EchoesOnomatopoeiaService.markOf(first) != null, "Another ACT only puts the word to sleep");
            power.setStand(ModStands.STAR_PLATINUM.get());
            EchoesOnomatopoeiaService.tickMarked(first);
            wordOver(helper, first, "the loss of the Stand");
            power.setStand(EchoesStands.ECHOES_ACT2.get());

            selfWord(helper, first);
            EchoesGameEvents.onPlayerChangedDimension(
                    new PlayerEvent.PlayerChangedDimensionEvent(first, Level.OVERWORLD, Level.NETHER));
            wordOver(helper, first, "a change of level");

            selfWord(helper, first);
            EchoesGameEvents.onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent(first));
            wordOver(helper, first, "a logout");

            selfWord(helper, first);
            first.kill();
            wordOver(helper, first, "death");

            last = helper.getLevel().getServer().getPlayerList().respawn(first, false, Entity.RemovalReason.KILLED);
            helper.assertTrue(last != first && last.getId() == first.getId(),
                    "Setup: the respawned player must keep the entity id a client draws the word by");
            EchoesOnomatopoeiaPayloads.Mark told = lastMarkSent(last);
            helper.assertTrue(EchoesOnomatopoeiaService.markOf(last) == null && told != null && told.word().isEmpty(),
                    "The respawn must tell the client there is no word; it told " + told);
        } finally {
            disconnect(last);
        }
        helper.succeed();
    }

    private static void selfWord(GameTestHelper helper, ServerPlayer user) {
        helper.assertTrue(EchoesOnomatopoeiaService.attachToSelf(user), "Setup: the self word did not land");
        EchoesOnomatopoeia word = EchoesOnomatopoeiaService.markOf(user).word();
        EchoesOnomatopoeiaService.apply(word, user);
        EchoesOnomatopoeiaPayloads.Mark told = lastMarkSent(user);
        helper.assertTrue(told != null && word.id().equals(told.word()) && user.hasEffect(EchoesMobEffects.HOT),
                "Setup: the client was not told of the word, or it gave no state; told " + told);
    }

    private static void wordOver(GameTestHelper helper, ServerPlayer user, String why) {
        EchoesOnomatopoeiaPayloads.Mark told = lastMarkSent(user);
        helper.assertTrue(EchoesOnomatopoeiaService.markOf(user) == null, "The word must end with " + why);
        helper.assertTrue(told != null && told.word().isEmpty(),
                "The client must be told the word ended with " + why + "; it was told " + told);
        helper.assertTrue(!user.hasEffect(EchoesMobEffects.HOT), "The word's state must end with " + why);
    }

    // what the server last told this player's client about the word on himself, of all sent since the last call
    @Nullable
    private static EchoesOnomatopoeiaPayloads.Mark lastMarkSent(ServerPlayer player) {
        EmbeddedChannel channel = (EmbeddedChannel) player.connection.getConnection().channel();
        channel.flushOutbound();
        EchoesOnomatopoeiaPayloads.Mark last = null;
        for (Object sent = channel.readOutbound(); sent != null; sent = channel.readOutbound()) {
            if (sent instanceof ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof EchoesOnomatopoeiaPayloads.Mark mark
                    && mark.entityId() == player.getId()) {
                last = mark;
            }
        }
        return last;
    }

    /**
     * R772.1 REVIEWFIX#8 (master EchoesTailNeedleAction, EchoesOnomatopoeiaAttachAction): through the press, as a player
     * does it. With no word, or with the tail out, the keys refuse and say why, which ours did silently; a needle at a
     * husk hits for 2 and leaves the word, whose state the husk then has; taken back, it ends at once. The attach lands
     * too, the word ends with the husk's death, and the tail stays out until it is called back.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot",
            timeoutTicks = 300 + GameTestChunkTickets.WAIT_TICKS, batch = "rotpEchoesWordPress")
    public static void act2KeysPutAWordOnAHuskAndARefusedPressSaysWhy(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> wordKeysBody(h));
    }

    private static void wordKeysBody(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesWordKeys");
        StandPower power = PowerClass.STAND.attachGet(user);
        power.setStand(EchoesStands.ECHOES_ACT2.get());
        helper.assertTrue(EchoesStands.ECHOES_ACT2.get().summon(user, power), "Setup: could not summon ACT2");
        StandEntity stand = power.getSummonedStandEntity();
        stand.setNoGravity(true);
        stand.summonLockTicks = 0;
        stand.gradualSummonWeaknessTicks = 0;
        Husk husk = new Husk(EntityType.HUSK, helper.getLevel());
        husk.setNoAi(true);
        husk.setNoGravity(true);
        husk.moveTo(user.getX(), user.getY(), user.getZ() + 3.0D, 180.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(husk), "Setup: the husk was not added");
        user.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getBoundingBox().getCenter());
        Ability needle = power.getAbility("tail_needle");
        Ability attach = power.getAbility("onomatopoeia_attach");
        Ability self = power.getAbility("onomatopoeia_self");
        float health = husk.getHealth();

        refuses(helper, needle, power, "rotp_echoes.message.need_onomatopoeia");
        refuses(helper, attach, power, "rotp_echoes.message.need_onomatopoeia");
        refuses(helper, self, power, "rotp_echoes.message.need_onomatopoeia");
        EchoesOnomatopoeiaService.select(user, EchoesOnomatopoeia.RERE);
        press(helper, user, needle);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(EchoesOnomatopoeiaService.markOf(husk) != null
                                && EchoesOnomatopoeiaService.markOf(husk).word() == EchoesOnomatopoeia.RERE
                                && husk.getHealth() <= health - 2.0F && RotpEchoes.userState(user).tailOut(),
                        "The needle must hit the husk for 2 and leave the word; health " + health + " -> "
                                + husk.getHealth()))
                .thenWaitUntil(() -> helper.assertTrue(husk.hasEffect(EchoesMobEffects.HOT),
                        "Hot on the husk must give it the hot state within half a second"))
                .thenWaitUntil(() -> helper.assertTrue(LivingComponentAction.getCurEntityAction(stand) == null,
                        "The needle's action has not ended"))
                .thenExecute(() -> {
                    refuses(helper, needle, power, "rotp_echoes.message.needle_already_fired");
                    refuses(helper, attach, power, "rotp_echoes.message.needle_already_fired");
                    refuses(helper, self, power, "rotp_echoes.message.needle_already_fired");
                    EchoesOnomatopoeiaService.takeBackMarks(user);
                    helper.assertTrue(EchoesOnomatopoeiaService.markOf(husk) == null
                                    && !husk.hasEffect(EchoesMobEffects.HOT)
                                    && attach.checkSpecificConditions(power).isPositive(),
                            "Taken back, the word and its state must end and the keys work again");
                    press(helper, user, attach);
                })
                .thenWaitUntil(() -> helper.assertTrue(EchoesOnomatopoeiaService.markOf(husk) != null
                                && RotpEchoes.userState(user).tailOut(),
                        "The attach key must leave the word on the husk in the aim"))
                .thenWaitUntil(() -> helper.assertTrue(LivingComponentAction.getCurEntityAction(stand) == null,
                        "The attach's action has not ended"))
                .thenExecute(() -> {
                    husk.kill();
                    helper.assertTrue(EchoesOnomatopoeiaService.markOf(husk) == null,
                            "The word must end with the husk's death");
                    refuses(helper, attach, power, "rotp_echoes.message.needle_already_fired");
                    EchoesOnomatopoeiaService.recall(user);
                    helper.assertTrue(attach.checkSpecificConditions(power).isPositive(),
                            "Called back, the tail must be free again");
                    husk.discard();
                    EchoesStands.ECHOES_ACT2.get().forceUnsummon(user, power);
                    disconnect(user);
                })
                .thenSucceed();
    }

    /**
     * R772.1 OQ-E02 (master EchoesOnomatopoeiaSelfAction, SelfCancelAction, TailNeedleRecallAction, EchoesEntity.tick):
     * through the press, the self word counts as the tail fired. ACT2's tail tip is hidden; needle, attach and a second
     * self word refuse with the master's message; the self cancel, or the recall, takes the word off and frees the tail.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS, batch = "rotpEchoesSelfWordTail")
    public static void theSelfWordHoldsTheTailUntilItIsTakenBack(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> selfWordTailBody(h));
    }

    private static void selfWordTailBody(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesSelfTail");
        StandPower power = PowerClass.STAND.attachGet(user);
        power.setStand(EchoesStands.ECHOES_ACT2.get());
        helper.assertTrue(EchoesStands.ECHOES_ACT2.get().summon(user, power), "Setup: could not summon ACT2");
        EchoesStandEntity stand = (EchoesStandEntity) power.getSummonedStandEntity();
        stand.setNoGravity(true);
        stand.summonLockTicks = 0;
        stand.gradualSummonWeaknessTicks = 0;
        Ability needle = power.getAbility("tail_needle");
        Ability attach = power.getAbility("onomatopoeia_attach");
        Ability self = power.getAbility("onomatopoeia_self");
        Ability selfCancel = power.getAbility("onomatopoeia_self_cancel");
        Ability recall = power.getAbility("tail_needle_recall");
        EchoesUserState state = RotpEchoes.userState(user);
        EchoesOnomatopoeiaService.select(user, EchoesOnomatopoeia.RERE);
        Runnable leave = () -> {
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT2.get().forceUnsummon(user, power);
            }
            disconnect(user);
        };
        // fixed delays, not waits: a wait would end on the wrapper's deadline and hide which step failed
        stepOrLeave(leave, () -> press(helper, user, self));
        helper.startSequence()
                .thenExecuteAfter(SETTLE_TICKS, () -> stepOrLeave(leave, () -> {
                    helper.assertTrue(EchoesOnomatopoeiaService.hasSelfMark(user) && state.tailOut()
                                    && stand.isTailOut(),
                            "The self word must count as the tail fired and hide ACT2's tail tip; word "
                                    + EchoesOnomatopoeiaService.hasSelfMark(user) + ", tail out " + state.tailOut()
                                    + ", tip hidden " + stand.isTailOut());
                    refuses(helper, needle, power, "rotp_echoes.message.needle_already_fired");
                    refuses(helper, attach, power, "rotp_echoes.message.needle_already_fired");
                    refuses(helper, self, power, "rotp_echoes.message.needle_already_fired");
                    press(helper, user, selfCancel);
                }))
                .thenExecuteAfter(SETTLE_TICKS, () -> stepOrLeave(leave, () -> {
                    helper.assertTrue(!EchoesOnomatopoeiaService.hasSelfMark(user) && !state.tailOut()
                                    && !stand.isTailOut() && needle.checkSpecificConditions(power).isPositive(),
                            "Taken off, the self word must free the tail; word "
                                    + EchoesOnomatopoeiaService.hasSelfMark(user) + ", tail out " + state.tailOut());
                    press(helper, user, self);
                }))
                .thenExecuteAfter(SETTLE_TICKS, () -> stepOrLeave(leave, () -> {
                    helper.assertTrue(EchoesOnomatopoeiaService.hasSelfMark(user) && state.tailOut(),
                            "Setup: the second self word did not land");
                    press(helper, user, recall);
                }))
                .thenExecuteAfter(SETTLE_TICKS, () -> stepOrLeave(leave, () -> helper.assertTrue(
                        !EchoesOnomatopoeiaService.hasSelfMark(user) && !state.tailOut()
                                && attach.checkSpecificConditions(power).isPositive(),
                        "The recall must take the self word back and free the tail; word "
                                + EchoesOnomatopoeiaService.hasSelfMark(user) + ", tail out " + state.tailOut())))
                .thenExecute(leave)
                .thenSucceed();
    }

    // a failed step takes its connected player out, or he would stay an Echoes user beside the next tests
    private static void stepOrLeave(Runnable leave, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            leave.run();
            throw failure;
        }
    }

    private static void refuses(GameTestHelper helper, Ability ability, StandPower power, String key) {
        ConditionCheck check = ability.checkSpecificConditions(power);
        helper.assertTrue(!check.isPositive() && check.getWarning() != null
                        && check.getWarning().getContents() instanceof TranslatableContents text
                        && key.equals(text.getKey()),
                ability.name() + " must refuse and say " + key + ", says " + check.getWarning());
    }

    private static void press(GameTestHelper helper, ServerPlayer user, Ability ability) {
        BufferingState state = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(SHORT_BAR_KEY, ability, user, null, InputMethod.CLICK, 0.0F, state, ability.getAbilityId());
        helper.assertTrue(state.isActionSuccess, ability.name() + " was not started");
    }

    /**
     * R772.1: the idle and counter lines are signed ACT3 (016's one Stand, EchoAutoActionHandler and EchoAIHandler), so
     * they come from ACT3 alone, not from ACT2 though it also strikes back.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void act3sLinesComeOnlyFromAct3(GameTestHelper helper) {
        ListeningPlayer user = new ListeningPlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "EchoesLines"));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        user.setPos(origin.getX() + 0.5D, origin.getY() + 2.0D, origin.getZ() + 0.5D);
        user.setNoGravity(true);
        helper.getLevel().addNewPlayer(user);
        user.setGameMode(GameType.SURVIVAL);
        StandPower power = PowerClass.STAND.attachGet(user);
        try {
            power.setStand(EchoesStands.ECHOES_ACT2.get());
            EchoesChatService.showCounterLine(user);
            for (int tick = 0; tick < PASSIVE_TRIES; tick++) {
                EchoesChatService.tickPassive(user);
            }
            helper.assertTrue(user.lines.isEmpty(), "ACT2 must not speak ACT3's lines: " + user.lines);

            power.setStand(EchoesStands.ECHOES_ACT3.get());
            EchoesChatService.showCounterLine(user);
            helper.assertTrue(user.lines.size() == 1, "ACT3 keeps its counter line: " + user.lines);
            for (int tick = 0; tick < PASSIVE_TRIES && user.lines.size() == 1; tick++) {
                EchoesChatService.tickPassive(user);
            }
            helper.assertTrue(user.lines.size() == 2, "ACT3 keeps its idle lines: " + user.lines);
        } finally {
            remove(helper, user);
        }
        helper.succeed();
    }

    /**
     * R772.1 OQ-E01 (016 EchoAutoActionHandler, whose one Stand is ACT3): the strike at a monster within 4 blocks and the
     * counter on a blow are ACT3's. ACT2 does neither and is not summoned for them, so a recalled ACT2 stays recalled.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesAutoAttack")
    public static void onlyAct3StrikesAndCountersByItself(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesAutoAttack");
        StandPower power = PowerClass.STAND.attachGet(user);
        Zombie zombie = standingZombie(helper, user, 2.0D);
        try {
            power.setStand(EchoesStands.ECHOES_ACT2.get());
            float health = zombie.getHealth();
            for (int tries = 0; tries < AUTO_TRIES; tries++) {
                user.tickCount = tries * 10;
                EchoesGameEvents.onPlayerTick(new PlayerTickEvent.Post(user));
                EchoesGameEvents.onIncomingDamage(blowFrom(user, zombie));
            }
            helper.assertTrue(zombie.getHealth() == health && !power.isSummoned(),
                    "ACT2 must neither strike, counter nor come out by itself; zombie " + health + " -> "
                            + zombie.getHealth() + ", summoned " + power.isSummoned());

            power.setStand(EchoesStands.ECHOES_ACT3.get());
            for (int tries = 0; tries < AUTO_TRIES && zombie.getHealth() == health; tries++) {
                user.tickCount = tries * 10;
                EchoesGameEvents.onPlayerTick(new PlayerTickEvent.Post(user));
            }
            helper.assertTrue(zombie.getHealth() < health && power.isSummoned(),
                    "ACT3 keeps 016's strike at a near monster, summoning itself; zombie " + health + " -> "
                            + zombie.getHealth() + ", summoned " + power.isSummoned());

            long now = helper.getLevel().getGameTime();
            RotpEchoes.userState(user).resetAutoActionCooldown(now - 100L);
            zombie.invulnerableTime = 0;
            float struck = zombie.getHealth();
            for (int tries = 0; tries < AUTO_TRIES && zombie.getHealth() == struck; tries++) {
                EchoesGameEvents.onIncomingDamage(blowFrom(user, zombie));
            }
            helper.assertTrue(zombie.getHealth() < struck,
                    "ACT3 keeps 016's counter on a blow; zombie " + struck + " -> " + zombie.getHealth());
        } finally {
            zombie.discard();
            if (power.isSummoned()) {
                power.getPowerType().forceUnsummon(user, power);
            }
            disconnect(user);
        }
        helper.succeed();
    }

    /**
     * R772.1 OQ-B01 (092 GravityItemEvents' pressure, which asked no rule): the block under an entity frozen near a
     * gravity carrier breaks only when the Echoes user who makes the pressure may break blocks, by the game rule
     * jojoAbilitiesBreakBlocks and his Stand's toggle (the core's breakingBlocksEnabled with him as the breaker). The
     * rule is set only around each tick's press, so no other test sees it changed.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 120,
            batch = "rotpEchoesPressureRule")
    public static void gravityPressureBreaksOnlyWhereTheBlockRuleAndToggleAllow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean ruleBefore = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
        ServerPlayer carrier = freezer(helper, "EchoesPressCarrier");
        // over the section's middle where other tests stand their Echoes users, beyond the 3-block pressure
        carrier.setPos(carrier.getX(), carrier.getY() + 7.0D, carrier.getZ());
        inventory(carrier, 1);
        ServerPlayer presser = freezer(helper, "EchoesPresser");
        PowerClass.STAND.attachGet(presser).setStand(EchoesStands.ECHOES_ACT1.get());
        presser.setPos(carrier.getX() + 1.0D, carrier.getY(), carrier.getZ());
        BlockPos floor = BlockPos.containing(carrier.getX() - 1.5D, carrier.getY() - 1.0D, carrier.getZ());
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        Pig frozen = pig(helper, Vec3.atBottomCenterOf(floor.above()));
        PlayerClientBroadcastedSettings toggles = presser.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
        helper.startSequence()
                .thenExecuteFor(PRESSURE_TICKS, () -> pressUnderRule(level, false, ruleBefore, frozen, presser, carrier))
                .thenExecute(() -> {
                    helper.assertTrue(level.getBlockState(floor).is(Blocks.STONE),
                            "The pressure broke a block with jojoAbilitiesBreakBlocks off");
                    toggles.standBreaksBlocks = false;
                })
                .thenExecuteFor(PRESSURE_TICKS, () -> pressUnderRule(level, true, ruleBefore, frozen, presser, carrier))
                .thenExecute(() -> {
                    helper.assertTrue(level.getBlockState(floor).is(Blocks.STONE),
                            "The pressure broke a block with its Echoes user's Stand toggle off");
                    toggles.standBreaksBlocks = true;
                })
                .thenWaitUntil(() -> {
                    pressUnderRule(level, true, ruleBefore, frozen, presser, carrier);
                    helper.assertTrue(level.getBlockState(floor).isAir(),
                            "With the rule and the toggle on, the pressure must still break the block under the frozen");
                })
                .thenExecute(() -> {
                    frozen.discard();
                    discardThrownItems(helper, carrier);
                    remove(helper, carrier);
                    remove(helper, presser);
                })
                .thenSucceed();
    }

    // one carrier tick under the rule given, the entity kept frozen though its owner is not in the player list
    private static void pressUnderRule(ServerLevel level, boolean rule, boolean ruleBefore, LivingEntity frozen,
            ServerPlayer presser, ServerPlayer carrier) {
        level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(rule, level.getServer());
        try {
            if (!RotpEchoes.freezeState(frozen).active()) {
                RotpEchoes.freezeState(frozen).activate(frozen, presser.getUUID(), 0);
            }
            EchoesGravityService.tickInventoryGravity(carrier);
        } finally {
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(ruleBefore, level.getServer());
        }
    }

    private static LivingIncomingDamageEvent blowFrom(ServerPlayer user, LivingEntity attacker) {
        return new LivingIncomingDamageEvent(user, new DamageContainer(user.damageSources().mobAttack(attacker), 2.0F));
    }

    private static final class ListeningPlayer extends FakePlayer {
        private final List<Component> lines = new ArrayList<>();

        private ListeningPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void displayClientMessage(Component line, boolean actionBar) {
            lines.add(line);
        }
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
            // each jab comes in the last one's recovery, so the key takes the next of the author's four punches
            String expected = comboPunch("punch", jab);
            sequence.thenExecute(() -> scene.press("punch", expected)).thenIdle(14);
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

    /**
     * R772.1 E12: the author's 1.21.1 AddonStands declares four punches and his clips punch to punch4 play them. Pressed
     * in the last punch's recovery the key takes the next of the four on every ACT, each plays its own clip of his in
     * his order, none plays the hand mirror of a Stand with one punch, and after the fourth the combo starts over.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot",
            timeoutTicks = 600 + GameTestChunkTickets.WAIT_TICKS, batch = "rotpEchoesPunchCombo")
    public static void everyActPlaysItsFourPunchesInTheAuthorsOrder(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> fourPunchesBody(h));
    }

    private static void fourPunchesBody(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesPunches");
        StandPower power = PowerClass.STAND.attachGet(user);
        GameTestSequence sequence = helper.startSequence();
        fourPunches(helper, sequence, user, power, EchoesStands.ECHOES_ACT1.get(), "echoes_act1", "punch");
        fourPunches(helper, sequence, user, power, EchoesStands.ECHOES_ACT2.get(), "echoes_act2", "act2_punch");
        fourPunches(helper, sequence, user, power, EchoesStands.ECHOES_ACT3.get(), "echoes_act3", "punch");
        sequence.thenExecute(() -> disconnect(user)).thenSucceed();
    }

    private static void fourPunches(GameTestHelper helper, GameTestSequence sequence, ServerPlayer user,
            StandPower power, EntityStandType type, String act, String base) {
        JsonObject clips = skinClips(helper, act);
        sequence.thenExecute(() -> {
            power.setStand(type);
            helper.assertTrue(type.summon(user, power), "Setup: could not summon " + act);
            StandEntity stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
        });
        for (int press = 0; press < 5; press++) {
            int nth = press;
            sequence.thenExecute(() -> pressComboPunch(helper, user, power, clips, act, base, nth))
                    .thenWaitUntil(() -> {
                        EntityActionInstance action =
                                LivingComponentAction.getCurEntityAction(power.getSummonedStandEntity());
                        helper.assertTrue(action != null && action.getPhase() == ActionPhase.RECOVERY,
                                act + " punch " + nth + " has not reached its recovery");
                    });
        }
        sequence.thenExecute(() -> type.forceUnsummon(user, power));
    }

    private static void pressComboPunch(GameTestHelper helper, ServerPlayer user, StandPower power, JsonObject clips,
            String act, String base, int nth) {
        String expected = comboPunch(base, nth);
        // resolved afresh, as heavyKey: the power caches its resolved keys for the tick
        AvailableAbilities moves = new AvailableAbilities();
        moves.update(power, power.getMoveset());
        var selected = moves.getContextVariationContainer(base);
        helper.assertTrue(selected != null && expected.equals(selected.ability.name())
                        && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                act + " press " + nth + " must take " + expected + ", took "
                        + (selected != null ? selected.ability.name() : null));
        BufferingState state = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(SHORT_BAR_KEY, selected.ability, user, null, InputMethod.CLICK, 0.0F, state,
                selected.ability.getAbilityId());
        helper.assertTrue(state.isActionSuccess, act + " " + expected + " was not started");
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(power.getSummonedStandEntity());
        helper.assertTrue(action != null && expected.equals(((Ability) action.ability).name())
                        && action instanceof StandEntityPunchAbility.StandEntityPunch punch
                        && !punch.usesHandedAnimation(),
                act + " " + expected + " must run as a combo punch, not a mirrored single punch");
        ActionAnimIdentifier anim = ((EntityActionAbility) action.ability).getEntityAnim(action);
        String clip = comboClip(anim.index(), "punch");
        String played = clips.has(anim.name()) ? comboClip(anim.index(), anim.name())
                : comboClip(anim.index(), AnimationSet.portedActionClipName(anim.name(), act, "stand_punch", false,
                        name -> clips.has(comboClip(anim.index(), name))));
        helper.assertTrue(clip.equals(comboClip(nth % 4, "punch")) && clip.equals(played) && clips.has(played),
                act + " " + expected + " must play the author's " + comboClip(nth % 4, "punch") + ", plays " + played);
    }

    /*
     * R772.1: ACT2's guard goes back to the idle through a recovery of its own, which its clip plays
     * (install_master_clips own_guard), and it guards only until the button is let go. ACT3's ends at once, as the core's.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void act2GuardGoesBackToTheIdleThroughARecovery(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesGuard");
        StandPower power = PowerClass.STAND.attachGet(user);
        try {
            guardLetGo(helper, user, power, EchoesStands.ECHOES_ACT3.get(), "guard", 0.0F);
            guardLetGo(helper, user, power, EchoesStands.ECHOES_ACT2.get(), "act2_guard", 3.0F);
        } finally {
            remove(helper, user);
        }
        helper.succeed();
    }

    private static void guardLetGo(GameTestHelper helper, ServerPlayer user, StandPower power, EntityStandType type,
            String name, float recovery) {
        power.setStand(type);
        helper.assertTrue(type.summon(user, power), "Setup: could not summon for " + name);
        StandEntity stand = power.getSummonedStandEntity();
        stand.setNoGravity(true);
        EntityActionInstance action = ((EntityActionAbility) power.getAbility(name))
                .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
        helper.assertTrue(action.phasesLength.getFloat(ActionPhase.RECOVERY) == recovery,
                name + " must have a recovery of " + recovery + " ticks, has "
                        + action.phasesLength.getFloat(ActionPhase.RECOVERY));
        LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
        helper.assertTrue(stand.isStandBlocking(), name + " must guard while held");
        action.onButtonStopHold();
        helper.assertTrue(!stand.isStandBlocking(), name + " must stop guarding when the button is let go");
        LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
        type.forceUnsummon(user, power);
    }

    // the moveset's name of the nth press: punch, punch2, punch3, punch4, punch...
    private static String comboPunch(String base, int nth) {
        return comboClip(nth % 4, base);
    }

    private static String comboClip(int index, String name) {
        return index > 0 ? name + (index + 1) : name;
    }

    private static JsonObject skinClips(GameTestHelper helper, String act) {
        String path = "/assets/rotp_echoes/stand_skins/" + act + "/assets/rotp_echoes/animations/" + act
                + ".animation.json";
        try (InputStream in = EchoesPilotGameTests.class.getResourceAsStream(path)) {
            helper.assertTrue(in != null, "Missing asset " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("animations");
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
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
            // another's, as these carriers have no ACT3 to hold up their own
            EchoesGravityService.infuse(stack, UUID.randomUUID());
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
