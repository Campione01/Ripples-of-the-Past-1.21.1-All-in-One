package rotp.addons.gratefuldead.state;

import java.util.Optional;
import java.util.UUID;

import rotp.core.init.ModEntityAttributes;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.service.AgingDamage;
import rotp.addons.gratefuldead.service.AgingRate;
import rotp.addons.gratefuldead.service.AgingService;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class LivingAgingState
        implements INBTSerializable<CompoundTag> {
    public static final int DEFAULT_FADE_TICKS = 40;
    public static final int COLD_FADE_TICKS = 200;
    // 0.1.13 AgingEntityUtil on its 0..2 scale, here on 0..1
    public static final float STRENGTH_START = 0.1F;
    public static final float STRENGTH_ZERO = 0.8F;
    public static final float DURABILITY_ZERO = 0.8F;
    public static final float PRECISION_START = 0.3F;
    public static final float PRECISION_ZERO = 0.9F;
    public static final float MAX_PROGRESS_EPSILON = 0.99995F;
    public static final int AGING_DAMAGE_INTERVAL = 200;
    public static final float RECOVERY_TICKS_PER_SPEED = 1200.0F;
    public static final float DOWNED_AGING_FLOOR = 0.5F;
    private static final ResourceLocation MOVEMENT_MODIFIER =
            GratefulDeadAddon.id("aging_movement");
    private static final ResourceLocation FLYING_MODIFIER =
            GratefulDeadAddon.id("aging_flying");
    private static final ResourceLocation ATTACK_DAMAGE_MODIFIER =
            GratefulDeadAddon.id("aging_attack_damage");
    private static final ResourceLocation ATTACK_SPEED_MODIFIER =
            GratefulDeadAddon.id("aging_attack_speed");
    private static final ResourceLocation FOLLOW_RANGE_MODIFIER =
            GratefulDeadAddon.id("aging_follow_range");
    private static final ResourceLocation JUMP_MODIFIER =
            GratefulDeadAddon.id("aging_jump");
    private static final ResourceLocation MINING_MODIFIER =
            GratefulDeadAddon.id("aging_mining");
    private static final ResourceLocation STAND_STRENGTH_MODIFIER =
            GratefulDeadAddon.id("aging_stand_strength");
    private static final ResourceLocation STAND_SPEED_MODIFIER =
            GratefulDeadAddon.id("aging_stand_speed");
    private static final ResourceLocation STAND_DURABILITY_MODIFIER =
            GratefulDeadAddon.id("aging_stand_durability");
    private static final ResourceLocation STAND_PRECISION_MODIFIER =
            GratefulDeadAddon.id("aging_stand_precision");

    public static final StreamCodec<
            RegistryFriendlyByteBuf, LivingAgingState>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public LivingAgingState decode(
                        RegistryFriendlyByteBuf buffer) {
                    LivingAgingState state =
                            new LivingAgingState();
                    state.progress = buffer.readFloat();
                    state.owner = buffer.readBoolean()
                            ? buffer.readUUID()
                            : null;
                    state.ownerGeneration =
                            buffer.readVarLong();
                    state.fading = buffer.readBoolean();
                    state.fadeTicks = buffer.readVarInt();
                    state.fadePerTick = buffer.readFloat();
                    state.woolDropped = buffer.readBoolean();
                    state.progress = Mth.clamp(
                            state.progress, 0.0F, 1.0F);
                    state.ownerGeneration = Math.max(
                            0L, state.ownerGeneration);
                    state.fadeTicks = Mth.clamp(
                            state.fadeTicks,
                            1,
                            COLD_FADE_TICKS);
                    state.fadePerTick = Mth.clamp(
                            state.fadePerTick, 0.0F, 1.0F);
                    if (state.fading
                            && state.fadePerTick <= 0.0F) {
                        state.fadePerTick =
                                state.progress
                                        / state.fadeTicks;
                    }
                    return state;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        LivingAgingState state) {
                    buffer.writeFloat(state.progress);
                    buffer.writeBoolean(state.owner != null);
                    if (state.owner != null) {
                        buffer.writeUUID(state.owner);
                    }
                    buffer.writeVarLong(
                            state.ownerGeneration);
                    buffer.writeBoolean(state.fading);
                    buffer.writeVarInt(state.fadeTicks);
                    buffer.writeFloat(state.fadePerTick);
                    buffer.writeBoolean(state.woolDropped);
                }
            };

    private float progress;
    private UUID owner;
    private long ownerGeneration;
    private boolean fading;
    private int fadeTicks = DEFAULT_FADE_TICKS;
    private float fadePerTick;
    private boolean woolDropped;
    private boolean forcedSwim;
    private boolean forcedNoAi;
    private int lastOverkillTick = -1;
    private float lastSyncedProgress = -1.0F;

    public static LivingAgingState get(
            LivingEntity entity) {
        return entity.getData(
                GratefulDeadAddon.LIVING_AGING);
    }

    public float progress() {
        return progress;
    }

    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public boolean fading() {
        return fading;
    }

    public boolean active() {
        return progress > 0.0F;
    }

    public void age(
            LivingEntity target,
            UUID newOwner,
            long generation,
            float amount) {
        age(target, newOwner, generation, amount, false);
    }

    /**
     * 0.1.13 addProgress: a contact (touch, self-aging) on a fully aged
     * target deals 1 aging damage a tick.
     */
    public void age(
            LivingEntity target,
            UUID newOwner,
            long generation,
            float amount,
            boolean contact) {
        if (target.level().isClientSide()
                || amount <= 0.0F) {
            return;
        }
        if (contact && progress >= MAX_PROGRESS_EPSILON
                && target.tickCount != lastOverkillTick) {
            lastOverkillTick = target.tickCount;
            AgingDamage.hurt(target, 1.0F);
        }
        progress = Mth.clamp(
                progress + amount, 0.0F, 1.0F);
        owner = newOwner;
        ownerGeneration = Math.max(0L, generation);
        fading = false;
        fadeTicks = DEFAULT_FADE_TICKS;
        fadePerTick = 0.0F;
        applyModifiers(target);
        applyThresholdSemantics(target);
        sync(target, true);
    }

    public void startFading(
            LivingEntity target, int duration) {
        if (progress <= 0.0F) {
            clear(target);
            return;
        }
        beginFade(duration);
        sync(target, true);
    }

    public void clearImmediately(LivingEntity target) {
        if (target != null && target.level() instanceof ServerLevel) {
            clear(target);
        }
    }

    public void tickServer(
            ServerLevel level, LivingEntity target) {
        if (progress <= 0.0F) {
            removeModifiers(target);
            return;
        }
        if (owner != null
                && AgingRemovalData.get(
                                level.getServer())
                        .generation(owner)
                        > ownerGeneration) {
            beginFade(DEFAULT_FADE_TICKS);
        }
        // 0.1.13 tickSignedRecovery: below a zero aging rate aging recedes
        // (it replaced 0.1.11's fade in the cold).
        if (!fading) {
            float speed = AgingRate.speed(target);
            // 0.1.13: a body downed in Resolve recedes no further than half aged.
            float floor = AgingRateState.isResolveDowned(target)
                    ? DOWNED_AGING_FLOOR
                    : 0.0F;
            if (speed < 0.0F && progress > floor) {
                progress = Math.max(
                        floor,
                        progress + speed / RECOVERY_TICKS_PER_SPEED);
                if (progress <= 0.0F) {
                    clear(target);
                    return;
                }
            }
        }
        if (owner != null) {
            net.minecraft.server.level.ServerPlayer ownerPlayer =
                    level.getServer()
                            .getPlayerList()
                            .getPlayer(owner);
            if (ownerPlayer == null
                    || !AgingService.hasGratefulDead(
                            ownerPlayer)
                    || ownerPlayer.level() != level
                    || ownerPlayer.distanceToSqr(
                                    target)
                            > AgingService.AURA_RADIUS
                                    * AgingService
                                            .AURA_RADIUS) {
                beginFade(DEFAULT_FADE_TICKS);
            }
        }
        if (fading) {
            progress = Math.max(
                    0.0F, progress - fadePerTick);
            fadeTicks--;
            if (progress <= 0.0F || fadeTicks <= 0) {
                clear(target);
                return;
            }
        }
        applyModifiers(target);
        applyThresholdSemantics(target);
        tickForcedStates(target);
        if (progress >= MAX_PROGRESS_EPSILON
                && target.tickCount % AGING_DAMAGE_INTERVAL == 0) {
            AgingDamage.hurt(target, 1.0F);
        }
        if (target instanceof Mob mob) {
            tickAgedMob(mob);
        }
        if (target.tickCount % 10 == 0) {
            sync(target, false);
        }
    }

    /** The aged entity is its own owner: no Stand penalties (0.1.13 isSelfInflicted). */
    public boolean selfInflicted(LivingEntity target) {
        return owner != null && owner.equals(target.getUUID());
    }

    public static float rangeT(
            float progress, float start, float end) {
        if (progress <= start) {
            return 0.0F;
        }
        return progress >= end
                ? 1.0F
                : (progress - start) / Math.max(1.0E-4F, end - start);
    }

    public static double easeOutQuad(double t) {
        if (t <= 0.0D) {
            return 0.0D;
        }
        double remain = 1.0D - Math.min(1.0D, t);
        return 1.0D - remain * remain;
    }

    public static double easeInCirc(double t) {
        if (t <= 0.0D) {
            return 0.0D;
        }
        return t >= 1.0D ? 1.0D : 1.0D - Math.sqrt(1.0D - t * t);
    }

    public static double easeOutExpo(double t) {
        if (t <= 0.0D) {
            return 0.0D;
        }
        return t >= 1.0D ? 1.0D : 1.0D - Math.pow(2.0D, -10.0D * t);
    }

    /** Strength, speed, attack speed and jump: 1 to 0 between 10% and 80%. */
    public static double strengthEfficiency(float progress) {
        return 1.0D - easeOutQuad(
                rangeT(progress, STRENGTH_START, STRENGTH_ZERO));
    }

    /** Stand durability: 0 at 80%, where the Stand can no longer be used. */
    public static double durabilityEfficiency(float progress) {
        return 1.0D - easeInCirc(
                rangeT(progress, 0.0F, DURABILITY_ZERO));
    }

    public static double precisionEfficiency(float progress) {
        return 1.0D - easeInCirc(
                rangeT(progress, PRECISION_START, PRECISION_ZERO));
    }

    public static double followRangeEfficiency(float progress) {
        return 1.0D - easeInCirc(
                rangeT(progress, PRECISION_START, PRECISION_ZERO))
                * 0.92D;
    }

    public boolean speedCollapsed() {
        return progress > 0.0F
                && strengthEfficiency(progress) <= 0.001D;
    }

    /** 0.1.13 cannotUseStand: an aged (not self-aged) user's Stand at 0 durability. */
    public boolean cannotUseStand(LivingEntity target) {
        return progress > 0.0F
                && !selfInflicted(target)
                && durabilityEfficiency(progress) <= 0.001D;
    }

    /**
     * NeoForge does not sync a forced pose, and the client decodes a fresh
     * state on every sync: the player's own client keeps its flag itself.
     * @return whether the pose is forced now
     */
    public static boolean applyClientPose(Player player, boolean forced) {
        boolean collapsed = player.hasData(GratefulDeadAddon.LIVING_AGING)
                && get(player).speedCollapsed();
        if (collapsed) {
            player.setForcedPose(Pose.SWIMMING);
            player.setSwimming(true);
            player.setDeltaMovement(
                    0.0D,
                    Math.min(0.0D, player.getDeltaMovement().y),
                    0.0D);
            return true;
        }
        if (forced) {
            if (player.getForcedPose() == Pose.SWIMMING) {
                player.setForcedPose(null);
            }
            player.setSwimming(false);
        }
        return false;
    }

    /** 0.1.13 tickForcedStates: a collapsed player lies down, a collapsed mob stops. */
    private void tickForcedStates(LivingEntity target) {
        boolean collapsed = speedCollapsed();
        if (target instanceof Player player) {
            if (collapsed) {
                forcedSwim = true;
                player.setForcedPose(Pose.SWIMMING);
                player.setSwimming(true);
                player.setPose(Pose.SWIMMING);
                player.setDeltaMovement(
                        0.0D,
                        Math.min(0.0D, player.getDeltaMovement().y),
                        0.0D);
            }
            else if (forcedSwim) {
                releaseForcedSwim(player);
            }
        }
        if (target instanceof Mob mob) {
            if (collapsed) {
                if (!mob.isNoAi()) {
                    forcedNoAi = true;
                    mob.setNoAi(true);
                }
            }
            else if (forcedNoAi) {
                forcedNoAi = false;
                mob.setNoAi(false);
            }
        }
    }

    private void releaseForcedSwim(Player player) {
        forcedSwim = false;
        if (player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
        player.setSwimming(false);
        if (player.getPose() == Pose.SWIMMING && !player.isInWater()) {
            player.setPose(Pose.STANDING);
        }
        player.refreshDimensions();
    }

    private void releaseForcedStates(LivingEntity target) {
        if (forcedSwim && target instanceof Player player) {
            releaseForcedSwim(player);
        }
        if (forcedNoAi && target instanceof Mob mob) {
            mob.setNoAi(false);
        }
        forcedSwim = false;
        forcedNoAi = false;
    }

    /** 0.1.13 tickAgedMobBehavior: an aged mob loses far targets, then all, and slows. */
    private void tickAgedMob(Mob mob) {
        if (mob.isNoAi()) {
            return;
        }
        float visionT = rangeT(progress, 0.3F, 0.9F);
        float vitalityT = (float) easeInCirc(rangeT(progress, 0.1F, 0.8F));
        if (mob.getTarget() != null) {
            double follow = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
            if (visionT > 0.0F
                    && mob.distanceToSqr(mob.getTarget()) > follow * follow * 0.85D
                    || vitalityT > 0.55F) {
                mob.setTarget(null);
            }
        }
        if (vitalityT > 0.35F) {
            mob.getNavigation().stop();
            mob.setDeltaMovement(mob.getDeltaMovement().multiply(
                    1.0D - vitalityT * 0.85D,
                    1.0D,
                    1.0D - vitalityT * 0.85D));
        }
    }

    private void beginFade(int duration) {
        if (fading || progress <= 0.0F) {
            return;
        }
        fading = true;
        fadeTicks = Math.max(1, duration);
        fadePerTick = progress / fadeTicks;
    }

    private void clear(LivingEntity target) {
        progress = 0.0F;
        owner = null;
        ownerGeneration = 0L;
        fading = false;
        fadeTicks = DEFAULT_FADE_TICKS;
        fadePerTick = 0.0F;
        woolDropped = false;
        lastOverkillTick = -1;
        releaseForcedStates(target);
        removeModifiers(target);
        sync(target, true);
    }

    private void applyModifiers(LivingEntity target) {
        double strength = strengthEfficiency(progress);
        updateModifier(
                target,
                Attributes.MOVEMENT_SPEED,
                MOVEMENT_MODIFIER,
                strength - 1.0D);
        updateModifier(
                target,
                Attributes.FLYING_SPEED,
                FLYING_MODIFIER,
                strength - 1.0D);
        updateModifier(
                target,
                Attributes.ATTACK_DAMAGE,
                ATTACK_DAMAGE_MODIFIER,
                strength - 1.0D);
        updateModifier(
                target,
                Attributes.ATTACK_SPEED,
                ATTACK_SPEED_MODIFIER,
                strength - 1.0D);
        updateModifier(
                target,
                Attributes.FOLLOW_RANGE,
                FOLLOW_RANGE_MODIFIER,
                followRangeEfficiency(progress) - 1.0D);
        updateModifier(
                target,
                Attributes.JUMP_STRENGTH,
                JUMP_MODIFIER,
                strength - 1.0D);
        updateModifier(
                target,
                Attributes.BLOCK_BREAK_SPEED,
                MINING_MODIFIER,
                strength - 1.0D);
        if (selfInflicted(target)) {
            remove(target, ModEntityAttributes.STAND_STRENGTH, STAND_STRENGTH_MODIFIER);
            remove(target, ModEntityAttributes.STAND_SPEED, STAND_SPEED_MODIFIER);
            remove(target, ModEntityAttributes.STAND_DURABILITY, STAND_DURABILITY_MODIFIER);
            remove(target, ModEntityAttributes.STAND_PRECISION, STAND_PRECISION_MODIFIER);
            return;
        }
        updateModifier(
                target,
                ModEntityAttributes.STAND_STRENGTH,
                STAND_STRENGTH_MODIFIER,
                strength - 1.0D);
        updateModifier(
                target,
                ModEntityAttributes.STAND_SPEED,
                STAND_SPEED_MODIFIER,
                strength - 1.0D);
        updateModifier(
                target,
                ModEntityAttributes.STAND_DURABILITY,
                STAND_DURABILITY_MODIFIER,
                durabilityEfficiency(progress) - 1.0D);
        updateModifier(
                target,
                ModEntityAttributes.STAND_PRECISION,
                STAND_PRECISION_MODIFIER,
                precisionEfficiency(progress) - 1.0D);
    }

    private void applyThresholdSemantics(
            LivingEntity target) {
        if (progress >= 0.60F
                && !woolDropped
                && target instanceof Sheep sheep
                && sheep.readyForShearing()) {
            sheep.shear(SoundSource.NEUTRAL);
            woolDropped = true;
        }
    }

    private static void updateModifier(
            LivingEntity target,
            Holder<Attribute> attribute,
            ResourceLocation id,
            double amount) {
        AttributeInstance instance =
                target.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(
                    new AttributeModifier(
                            id,
                            amount,
                            AttributeModifier.Operation
                                    .ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeModifiers(
            LivingEntity target) {
        remove(
                target,
                Attributes.MOVEMENT_SPEED,
                MOVEMENT_MODIFIER);
        remove(
                target,
                Attributes.FLYING_SPEED,
                FLYING_MODIFIER);
        remove(
                target,
                Attributes.ATTACK_DAMAGE,
                ATTACK_DAMAGE_MODIFIER);
        remove(
                target,
                Attributes.ATTACK_SPEED,
                ATTACK_SPEED_MODIFIER);
        remove(
                target,
                Attributes.FOLLOW_RANGE,
                FOLLOW_RANGE_MODIFIER);
        remove(
                target,
                Attributes.JUMP_STRENGTH,
                JUMP_MODIFIER);
        remove(
                target,
                Attributes.BLOCK_BREAK_SPEED,
                MINING_MODIFIER);
        remove(
                target,
                ModEntityAttributes.STAND_STRENGTH,
                STAND_STRENGTH_MODIFIER);
        remove(
                target,
                ModEntityAttributes.STAND_SPEED,
                STAND_SPEED_MODIFIER);
        remove(
                target,
                ModEntityAttributes.STAND_DURABILITY,
                STAND_DURABILITY_MODIFIER);
        remove(
                target,
                ModEntityAttributes.STAND_PRECISION,
                STAND_PRECISION_MODIFIER);
    }

    private static void remove(
            LivingEntity target,
            Holder<Attribute> attribute,
            ResourceLocation id) {
        AttributeInstance instance =
                target.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private void sync(
            LivingEntity target, boolean force) {
        if (!target.level().isClientSide()
                && (force
                        || Math.abs(
                                        progress
                                                - lastSyncedProgress)
                                >= 0.01F)) {
            lastSyncedProgress = progress;
            target.syncData(
                    GratefulDeadAddon.LIVING_AGING);
        }
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Progress", progress);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putLong(
                "OwnerGeneration", ownerGeneration);
        tag.putBoolean("Fading", fading);
        tag.putInt("FadeTicks", fadeTicks);
        tag.putFloat("FadePerTick", fadePerTick);
        tag.putBoolean("WoolDropped", woolDropped);
        tag.putBoolean("ForcedSwim", forcedSwim);
        tag.putBoolean("ForcedNoAi", forcedNoAi);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        progress = Mth.clamp(
                tag.getFloat("Progress"), 0.0F, 1.0F);
        owner = tag.hasUUID("Owner")
                ? tag.getUUID("Owner")
                : null;
        ownerGeneration = Math.max(
                0L, tag.getLong("OwnerGeneration"));
        fading = tag.getBoolean("Fading");
        fadeTicks = Mth.clamp(
                tag.contains("FadeTicks")
                        ? tag.getInt("FadeTicks")
                        : DEFAULT_FADE_TICKS,
                1,
                COLD_FADE_TICKS);
        fadePerTick = Mth.clamp(
                tag.getFloat("FadePerTick"),
                0.0F,
                1.0F);
        if (fading && fadePerTick <= 0.0F) {
            fadePerTick = progress / fadeTicks;
        }
        woolDropped = tag.getBoolean("WoolDropped");
        forcedSwim = tag.getBoolean("ForcedSwim");
        forcedNoAi = tag.getBoolean("ForcedNoAi");
    }
}
