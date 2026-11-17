package rotp.addons.spin.entity;

import java.util.Comparator;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import org.joml.Vector3f;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.item.SteelBallItem;
import rotp.addons.spin.item.WreckingBallItem;
import rotp.addons.spin.power.SpinData;
import rotp.addons.spin.power.SpinGolden;
import rotp.addons.spin.power.SpinResonance;
import rotp.addons.spin.compat.tusk.SpinTuskCompat;
import rotp.addons.spin.power.SpinSqueeze;
import rotp.addons.spin.power.SpinSteer;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.JojoModUtil;

public final class SteelBallEntity extends AbstractArrow {
    private static final EntityDataAccessor<Boolean> SPINNING = flag();
    private static final EntityDataAccessor<Boolean> RETURNING = flag();
    private static final EntityDataAccessor<Boolean> SATELLITES_RELEASED = flag();
    private static final EntityDataAccessor<Boolean> WRECKING = flag();
    private static final EntityDataAccessor<Boolean> SATELLITE = flag();
    private static final EntityDataAccessor<Byte> ROPE = SynchedEntityData.defineId(SteelBallEntity.class, EntityDataSerializers.BYTE);
    private static final byte ROPE_NONE = 0, ROPE_FLYING = 1, ROPE_ANCHORED = 2;
    private static final DustParticleOptions ROPE_DUST = new DustParticleOptions(new Vector3f(0.76F, 0.64F, 0.43F), 0.6F);
    private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new Vector3f(0.92F, 0.78F, 0.19F), 1);
    private static final ResourceLocation SPUN_ITEM = ResourceLocation.fromNamespaceAndPath("rotp_spin", "spun_item");
    private static final ResourceLocation SPUN_BLOCK = ResourceLocation.fromNamespaceAndPath("rotp_spin", "spun_block");
    private static final ResourceKey<MobEffect> NEGLECT = ResourceKey.create(Registries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("rotp_spin", "hemispatial_neglect"));
    private static final ResourceKey<MobEffect> DESICCATION = ResourceKey.create(Registries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("rotp_spin", "desiccation"));

    private static ReturnStorage returnStorage = (player, stack) -> false;
    private static ImpactHooks impactHooks = new ImpactHooks() {};

    private int returnTicks;
    private int steeredTicks;
    private int bounces;
    private int ropeTicks;
    private int resonanceTicks;
    private int resonanceSources;
    private boolean resonanceRung;
    private boolean fromHolster;
    private boolean shockwaveDone;
    private boolean bulletsCut;
    private boolean breakerPayload;

    public SteelBallEntity(EntityType<? extends SteelBallEntity> type, Level level) {
        super(type, level);
    }

    public SteelBallEntity(Level level, LivingEntity owner, ItemStack stack, boolean spinning) {
        this(level, owner, stack, spinning, false);
        double damage = spinning ? SpinConfig.BALL_SPIN_DAMAGE.get() : SpinConfig.BALL_PLAIN_DAMAGE.get();
        boolean chipped = SteelBallItem.isChipped(stack);
        if (chipped) {
            damage *= SpinConfig.BALL_DAMAGED_MULTIPLIER.get();
        }
        if (spinning) {
            double golden = SpinData.goldenMultiplier(level, owner, chipped);
            if (chipped) {
                golden = SpinGolden.applyChipped(golden, SpinConfig.GOLDEN_CHIPPED_RETENTION.get());
            }
            damage *= impactHooks.damageMultiplier(owner, golden);
            if (level instanceof ServerLevel && SpinConfig.RESONANCE_ENABLED.get()) {
                int sources = countResonanceSources(owner);
                int cap = SpinConfig.RESONANCE_MAX_SOURCES.get();
                damage *= SpinResonance.damageMultiplier(sources, cap,
                        SpinConfig.RESONANCE_DAMAGE_PER_SOURCE.get(), SpinConfig.RESONANCE_MAX_MULTIPLIER.get());
                resonanceTicks = SpinResonance.extraFlightTicks(sources, cap, SpinConfig.RESONANCE_EXTRA_FLIGHT_TICKS_PER_SOURCE.get());
                resonanceSources = SpinResonance.effectiveSources(sources, cap);
            }
        }
        setBaseDamage(damage);
    }

    private SteelBallEntity(Level level, LivingEntity owner, ItemStack stack, boolean spinning, boolean satellite) {
        super(SpinAddon.STEEL_BALL_ENTITY.get(), owner, level, stack.copyWithCount(1), null);
        entityData.set(SPINNING, spinning);
        entityData.set(WRECKING, stack.getItem() instanceof WreckingBallItem);
        entityData.set(SATELLITE, satellite);
        if (satellite) {
            pickup = Pickup.DISALLOWED;
            setBaseDamage(SpinConfig.WRECKING_SATELLITE_DAMAGE.get());
        }
    }

    private static EntityDataAccessor<Boolean> flag() {
        return SynchedEntityData.defineId(SteelBallEntity.class, EntityDataSerializers.BOOLEAN);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPINNING, false).define(RETURNING, false).define(SATELLITES_RELEASED, false)
                .define(WRECKING, false).define(SATELLITE, false).define(ROPE, ROPE_NONE);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(SpinAddon.STEEL_BALL.get());
    }

    public boolean isSpinning() { return entityData.get(SPINNING); }
    public boolean isReturning() { return entityData.get(RETURNING); }
    public boolean isWrecking() { return entityData.get(WRECKING); }
    public boolean isSatellite() { return entityData.get(SATELLITE); }
    public boolean areSatellitesReleased() { return entityData.get(SATELLITES_RELEASED); }
    public boolean isInGround() { return inGround; }
    public void setFromHolster(boolean value) { fromHolster = value; }

    public void makeRope() {
        if (!level().isClientSide()) {
            entityData.set(ROPE, ROPE_FLYING);
        }
    }

    @Override
    public void tick() {
        if (isSatellite() && (inGround || tickCount > SpinConfig.WRECKING_SATELLITE_LIFETIME.get())) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        if (level() instanceof ServerLevel server) {
            if (resonanceSources > 0) {
                emitResonance(server);
            }
            if (tickCount - steeredTicks >= SpinConfig.WRECKING_RELEASE_AFTER_TICKS.get()) {
                releaseSatellites(server, null);
            }
            if (entityData.get(ROPE) == ROPE_ANCHORED) {
                tickRope(server);
            }
            if (isSpinning() && !isReturning() && !isSatellite() && entityData.get(ROPE) != ROPE_ANCHORED
                    && (inGround || tickCount - steeredTicks >= SpinConfig.BALL_RETURN_AFTER_TICKS.get() + resonanceTicks)) {
                startReturning();
            }
        }
        else if (isSatellite()) {
            level().addParticle(GOLD_DUST, getX(), getY(), getZ(), 0, 0, 0);
        }
        if (isReturning()) {
            Entity owner = getOwner();
            if (!level().isClientSide() && (!acceptableReturnOwner(owner) || ++returnTicks > SpinConfig.BALL_MAX_RETURN_TICKS.get())) {
                loseSpin();
            }
            else if (owner != null && owner.level() == level()) {
                setNoPhysics(true);
                Vec3 toOwner = owner.getEyePosition().subtract(position());
                setDeltaMovement(getDeltaMovement().scale(0.95)
                        .add(toOwner.normalize().scale(SpinConfig.BALL_RETURN_ACCELERATION.get())));
            }
        }
        super.tick();
    }

    private boolean acceptableReturnOwner(@Nullable Entity owner) {
        return owner != null && owner.level() == level() && owner.isAlive() && !owner.isSpectator();
    }

    private void startReturning() {
        inGround = false;
        returnTicks = 0;
        setNoPhysics(true);
        entityData.set(ROPE, ROPE_NONE);
        entityData.set(RETURNING, true);
        hasImpulse = true;
    }

    private void loseSpin() {
        entityData.set(ROPE, ROPE_NONE);
        entityData.set(RETURNING, false);
        entityData.set(SPINNING, false);
        setNoPhysics(false);
        hasImpulse = true;
    }

    public boolean canBeSteered() {
        return isAlive() && isSpinning() && !isReturning() && !inGround && !isSatellite();
    }

    public boolean canBeRecalled() {
        return isAlive() && !isSatellite() && !isSpinning() && !isReturning();
    }

    public void steerTowards(Vec3 aimPoint) {
        if (!level().isClientSide() && canBeSteered()) {
            setDeltaMovement(SpinSteer.steer(getDeltaMovement(), aimPoint.subtract(position()),
                    SpinConfig.STEER_TURN_RATE.get(), SpinConfig.STEER_MIN_SPEED.get()));
            steeredTicks++;
            hasImpulse = true;
        }
    }

    public void respinAndReturn() {
        if (!level().isClientSide() && canBeRecalled()) {
            entityData.set(SPINNING, true);
            startReturning();
        }
    }

    @Nullable
    public static SteelBallEntity findSteerable(LivingEntity user, double range) {
        return findOwned(user, range, SteelBallEntity::canBeSteered);
    }

    @Nullable
    public static SteelBallEntity findRecallable(LivingEntity user, double range) {
        return findOwned(user, range, SteelBallEntity::canBeRecalled);
    }

    @Nullable
    private static SteelBallEntity findOwned(LivingEntity user, double range, Predicate<SteelBallEntity> state) {
        return user.level().getEntitiesOfClass(SteelBallEntity.class, user.getBoundingBox().inflate(range),
                        ball -> state.test(ball) && ball.getOwner() != null && ball.getOwner().is(user))
                .stream().min(Comparator.comparingDouble(user::distanceToSqr)).orElse(null);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }
        Entity owner = getOwner();
        return !(target instanceof Projectile projectile && owner != null && owner.is(projectile.getOwner()));
    }

    @Override
    public boolean isPickable() {
        return isAlive();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return isAlive();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide() && hit.getType() == HitResult.Type.BLOCK
                && random.nextDouble() < SpinConfig.BALL_DAMAGED_CHANCE_ON_BLOCK_HIT.get()) {
            SteelBallItem.setChipped(getPickupItemStackOrigin(), true);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (entityData.get(ROPE) == ROPE_FLYING && isSpinning() && !isReturning()) {
            if (level() instanceof ServerLevel) {
                Entity owner = getOwner();
                if (acceptableReturnOwner(owner) && SpinRope.canAnchor(distanceTo(owner), SpinConfig.ROPE_MAX_LENGTH.get())) {
                    entityData.set(ROPE, ROPE_ANCHORED);
                    ropeTicks = 0;
                    playSound(SoundEvents.TRIPWIRE_ATTACH, 1, 0.8F);
                }
                else {
                    entityData.set(ROPE, ROPE_NONE);
                }
            }
        }
        else {
            if (level() instanceof ServerLevel server) {
                if (isWrecking() && !isSatellite() && isSpinning() && !isReturning() && !shockwaveDone) {
                    shockwaveDone = true;
                    emitShockwave(server);
                }
                releaseSatellites(server, hit.getLocation());
                applyFriction(server, hit);
            }
            if (isSpinning() && !isReturning()) {
                Vec3 reflected = SpinRicochet.reflect(getDeltaMovement(), Vec3.atLowerCornerOf(hit.getDirection().getNormal()),
                        SpinConfig.BALL_RICOCHET_SPEED_RETENTION.get());
                if (SpinRicochet.canBounce(bounces, SpinConfig.BALL_RICOCHET_MAX_BOUNCES.get(),
                        reflected.length(), SpinConfig.BALL_RICOCHET_MIN_SPEED.get())) {
                    bounces++;
                    setDeltaMovement(reflected);
                    hasImpulse = true;
                    if (!level().isClientSide()) {
                        playSound(SoundEvents.ANVIL_LAND, 0.25F, 1.8F);
                    }
                    return;
                }
            }
        }
        BlockPos pos = hit.getBlockPos();
        setSoundEvent(level().getBlockState(pos).getSoundType(level(), pos, this).getBreakSound());
        super.onHitBlock(hit);
        shakeTime = 0;
        setNoGravity(false);
        setSoundEvent(getDefaultHitGroundSoundEvent());
    }

    private void applyFriction(ServerLevel level, BlockHitResult hit) {
        if (!(getOwner() instanceof ServerPlayer player) || player.level() != level
                || !player.isAlive() || !level.mayInteract(player, hit.getBlockPos())
                || !player.mayUseItemAt(hit.getBlockPos(), hit.getDirection(), ItemStack.EMPTY)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        double speed = getDeltaMovement().length();
        if (SpinFriction.canStripBark(SpinConfig.FRICTION_BARK_STRIPPING.get(), isSpinning(),
                isReturning(), speed, SpinConfig.FRICTION_BARK_MIN_SPEED.get())) {
            BlockState stripped = AxeItem.getAxeStrippingState(state);
            if (stripped != null && level.setBlock(pos, stripped, 11)) {
                level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1, 1);
                setDeltaMovement(getDeltaMovement().scale(SpinConfig.FRICTION_BARK_SPEED_RETENTION.get()));
                hasImpulse = true;
            }
        }
        if (!state.is(Blocks.IRON_BLOCK) || !SpinFriction.canCutBullets(SpinConfig.FRICTION_BULLET_CUTTING.get(),
                isSpinning(), isReturning(), bulletsCut, getDeltaMovement().length(), SpinConfig.FRICTION_BULLET_MIN_SPEED.get())
                || !JojoModUtil.breakingBlocksEnabled(level, player)
                || CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled()) {
            return;
        }
        bulletsCut = true;
        int count = SpinConfig.FRICTION_BULLETS_PER_CUT.get();
        Vec3 point = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.25));
        ItemEntity drop = new ItemEntity(level, point.x, point.y, point.z, new ItemStack(Items.IRON_NUGGET, count));
        drop.setDefaultPickUpDelay();
        if (level.addFreshEntity(drop) && random.nextDouble() < SpinFriction.blockConsumeChance(count, SpinConfig.FRICTION_BULLET_NUGGETS_PER_BLOCK.get())) {
            level.destroyBlock(pos, false, player);
        }
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.8F, 1.6F);
        setDeltaMovement(getDeltaMovement().scale(SpinConfig.FRICTION_BULLET_SPEED_RETENTION.get()));
        hasImpulse = true;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Entity target = hit.getEntity();
        Entity owner = getOwner();
        if (owner instanceof LivingEntity living) {
            living.setLastHurtMob(target);
            ItemStack stack = getPickupItemStackOrigin();
            stack.hurtAndBreak(1, server, living, item -> {});
            if (stack.isEmpty()) {
                discard();
                return;
            }
        }
        boolean enderman = target.getType() == EntityType.ENDERMAN;
        int previousFireTicks = target.getRemainingFireTicks();
        if (isOnFire() && !enderman) {
            target.igniteForSeconds(5);
        }
        DamageSource source = damageSources().arrow(this, owner != null ? owner : this);
        float damage = (float) (getDeltaMovement().length() * getBaseDamage());
        boolean hurt;
        if (isSpinning() && !isSatellite() && owner instanceof LivingEntity living) {
            boolean chipped = SteelBallItem.isChipped(getPickupItemStackOrigin());
            if (target instanceof AbstractHorse horse && SpinData.tryDetour(living, horse, chipped)) {
                hurt = true;
            }
            else if (target instanceof LivingEntity victim) {
                SpinTuskCompat.beforeSpinningHit(victim, living, chipped);
                impactHooks.beforeLivingHit(this, living, victim, chipped);
                hurt = impactHooks.hurtLiving(this, living, victim, chipped, () -> target.hurt(source, damage));
                if (hurt) {
                    SpinData.practiceHit(living, victim);
                    impactHooks.afterLivingHit(this, living, victim, chipped);
                    if (victim.isAlive()) {
                        squeeze(server, living, victim, chipped, source);
                    }
                }
            }
            else {
                hurt = target.hurt(source, damage);
            }
        }
        else {
            hurt = target.hurt(source, damage);
        }
        if (hurt) {
            if (enderman) {
                return;
            }
            if (target instanceof LivingEntity living) {
                doPostHurtEffects(living);
            }
            playSound(getHitGroundSoundEvent(), 1, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
            changeMovementAfterHit(server);
        }
        else {
            target.setRemainingFireTicks(previousFireTicks);
            setDeltaMovement(getDeltaMovement().scale(-0.1));
            setYRot(getYRot() + 180);
            yRotO += 180;
            if (getDeltaMovement().lengthSqr() < 1.0E-7) {
                changeMovementAfterHit(server);
            }
        }
    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity target, DamageSource source) {
        Entity attacker = source.getEntity();
        return attacker == null ? super.calculateHorizontalHurtKnockbackDirection(target, source)
                : DoubleDoubleImmutablePair.of(target.getX() - attacker.getX(), target.getZ() - attacker.getZ());
    }

    private void changeMovementAfterHit(ServerLevel server) {
        if (isSatellite()) {
            discard();
        }
        else if (isSpinning()) {
            if (!isReturning()) {
                releaseSatellites(server, null);
                startReturning();
            }
        }
        else {
            setDeltaMovement(getDeltaMovement().multiply(-0.01, -0.1, -0.01));
        }
    }

    private void squeeze(ServerLevel server, LivingEntity owner, LivingEntity target, boolean chipped, DamageSource source) {
        if (!SpinSqueeze.applies(SpinConfig.SQUEEZE_ENABLED.get(), SpinData.lessonOf(owner), SpinConfig.SQUEEZE_MIN_LESSON.get())) {
            return;
        }
        double damagedMult = SpinConfig.BALL_DAMAGED_MULTIPLIER.get();
        double hitY = getY() + getBbHeight() * 0.5;
        SpinSqueeze.Zone zone = SpinSqueeze.zone(SpinSqueeze.relativeHeight(hitY, target.getY(), target.getBbHeight()),
                SpinConfig.SQUEEZE_LEG_HEIGHT.get(), SpinConfig.SQUEEZE_HEAD_HEIGHT.get());
        int limbTicks = SpinSqueeze.duration(SpinConfig.SQUEEZE_LIMB_TICKS.get(), chipped, damagedMult);
        if (limbTicks > 0 && zone != SpinSqueeze.Zone.HEAD) {
            target.addEffect(new MobEffectInstance(zone == SpinSqueeze.Zone.LEGS ? MobEffects.MOVEMENT_SLOWDOWN : MobEffects.WEAKNESS,
                    limbTicks, SpinConfig.SQUEEZE_LIMB_AMPLIFIER.get()));
        }
        int dryTicks = SpinSqueeze.duration(SpinConfig.SQUEEZE_DRY_TICKS.get(), chipped, damagedMult);
        if (dryTicks > 0) {
            BuiltInRegistries.MOB_EFFECT.getHolder(DESICCATION)
                    .ifPresent(effect -> target.addEffect(new MobEffectInstance(effect, dryTicks)));
            target.clearFire();
            server.sendParticles(ParticleTypes.DRIPPING_WATER, target.getX(), hitY, target.getZ(), 12,
                    target.getBbWidth() * 0.4, 0.2, target.getBbWidth() * 0.4, 0);
            float bonus = SpinConfig.SQUEEZE_WATER_MOB_BONUS_DAMAGE.get().floatValue();
            if (bonus > 0 && (target.getType().is(EntityTypeTags.AQUATIC) || target.getType() == EntityType.DROWNED)) {
                target.invulnerableTime = 0;
                target.hurt(damageSources().indirectMagic(this, source.getEntity()), bonus);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof StandEntity stand) {
            Entity owner = getOwner();
            LivingEntity standUser = stand.getUser();
            if (shouldDropOnStandParry(isSpinning() || isReturning() || isSatellite(),
                    owner == null ? null : owner.getUUID(), standUser == null ? null : standUser.getUUID())) {
                if (!level().isClientSide()) {
                    if (isSatellite()) {
                        discard();
                    }
                    else {
                        loseSpin();
                        setDeltaMovement(Vec3.ZERO);
                        playSound(SoundEvents.ANVIL_LAND, 0.25F, 1.8F);
                    }
                }
                return true;
            }
        }
        return super.hurt(source, amount);
    }

    static boolean shouldDropOnStandParry(boolean active, @Nullable UUID owner, @Nullable UUID standUser) {
        return active && (owner == null || standUser == null || !owner.equals(standUser));
    }

    @Override
    public void playerTouch(Player player) {
        if (level().isClientSide() || !isAlive() || isSatellite()) {
            return;
        }
        Entity owner = getOwner();
        boolean owned = owner != null && player.is(owner);
        if (isReturning()) {
            if (!owned) {
                return;
            }
            if (pickup == Pickup.ALLOWED) {
                ItemStack ball = getPickupItem();
                if (fromHolster ? !returnStorage.store(player, ball) && !putInEmptyHand(player, ball)
                        : !putInEmptyHand(player, ball) && !returnStorage.store(player, ball)) {
                    if (!player.getInventory().add(ball)) {
                        spawnAtLocation(ball);
                    }
                }
            }
            finishPickup(player);
        }
        else if ((inGround || owner == null || owned) && (inGround || isNoPhysics() || leftOwner) && tryPickup(player)) {
            finishPickup(player);
        }
    }

    private static boolean putInEmptyHand(Player player, ItemStack ball) {
        if (!player.getMainHandItem().isEmpty()) {
            return false;
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ball);
        return true;
    }

    private void finishPickup(Player player) {
        player.take(this, 1);
        discard();
    }

    @Override
    protected void tickDespawn() {
        if (pickup != Pickup.ALLOWED) {
            super.tickDespawn();
        }
    }

    private void tickRope(ServerLevel server) {
        Entity owner = getOwner();
        if (!acceptableReturnOwner(owner) || !inGround || !isSpinning()) {
            releaseRope();
            return;
        }
        Vec3 toAnchor = position().subtract(owner.position().add(0, owner.getBbHeight() * 0.5, 0));
        if (SpinRope.shouldRelease(toAnchor.length(), ++ropeTicks, SpinConfig.ROPE_RELEASE_DISTANCE.get(), SpinConfig.ROPE_MAX_TICKS.get())) {
            releaseRope();
            return;
        }
        owner.setDeltaMovement(SpinRope.pull(owner.getDeltaMovement(), toAnchor,
                SpinConfig.ROPE_PULL_STRENGTH.get(), SpinConfig.ROPE_MAX_SPEED.get()));
        owner.hasImpulse = true;
        owner.hurtMarked = true;
        owner.resetFallDistance();
        if (ropeTicks % 2 == 0) {
            Vec3 from = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
            Vec3 span = position().subtract(from);
            int points = Math.min(24, Math.max(2, (int) (span.length() * 1.5)));
            for (int i = 1; i < points; i++) {
                Vec3 point = from.add(span.scale((double) i / points));
                server.sendParticles(ROPE_DUST, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void releaseRope() {
        entityData.set(ROPE, ROPE_NONE);
        if (isSpinning()) {
            startReturning();
        }
    }

    private void releaseSatellites(ServerLevel server, @Nullable Vec3 impact) {
        if (!isWrecking() || isSatellite() || !isSpinning() || isReturning() || areSatellitesReleased()) {
            return;
        }
        entityData.set(SATELLITES_RELEASED, true);
        Vec3 from = impact == null ? position() : impact.add(getDeltaMovement().normalize().scale(-0.3));
        if (getOwner() instanceof LivingEntity owner && owner.level() == server) {
            LivingEntity victim = server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SpinConfig.WRECKING_SATELLITE_RANGE.get()),
                            e -> e.isAlive() && !e.is(owner))
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            Vec3 aim = victim == null ? getDeltaMovement() : victim.position().add(0, victim.getBbHeight() * 0.5, 0).subtract(position());
            if (aim.lengthSqr() < 1.0E-6) {
                aim = getLookAngle();
            }
            for (Vec3 v : WreckingBall.satelliteVelocities(aim, SpinConfig.WRECKING_SATELLITES.get(), SpinConfig.WRECKING_SATELLITE_SPEED.get())) {
                SteelBallEntity satellite = new SteelBallEntity(server, owner, new ItemStack(SpinAddon.WRECKING_BALL.get()), true, true);
                satellite.setPos(from);
                satellite.shoot(v.x, v.y, v.z, (float) v.length(), 0);
                server.addFreshEntity(satellite);
            }
            playSound(SoundEvents.TRIDENT_THROW.value(), 0.6F, 1.6F);
        }
        server.sendParticles(GOLD_DUST, from.x, from.y, from.z, 24, 0.25, 0.25, 0.25, 0);
    }

    private void emitShockwave(ServerLevel server) {
        Entity owner = getOwner();
        for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SpinConfig.WRECKING_SHOCKWAVE_RADIUS.get()),
                e -> e.isAlive() && !e.is(owner))) {
            victim.hurt(damageSources().thrown(this, owner == null ? this : owner), SpinConfig.WRECKING_SHOCKWAVE_DAMAGE.get().floatValue());
            BuiltInRegistries.MOB_EFFECT.getHolder(NEGLECT)
                    .ifPresent(effect -> victim.addEffect(new MobEffectInstance(effect, SpinConfig.WRECKING_NEGLECT_DURATION.get())));
            Vec3 away = new Vec3(victim.getX() - getX(), 0, victim.getZ() - getZ());
            if (away.lengthSqr() > 1.0E-6) {
                away = away.normalize();
                victim.push(away.x * 0.6, 0.25, away.z * 0.6);
            }
        }
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.5F);
    }

    private int countResonanceSources(LivingEntity owner) {
        double radius = SpinConfig.RESONANCE_RADIUS.get();
        if (radius <= 0) {
            return 0;
        }
        return level().getEntities(this, owner.getBoundingBox().inflate(radius), source -> {
            if (!source.isAlive()) {
                return false;
            }
            if (source instanceof SteelBallEntity ball) {
                return ball.isSpinning() && !ball.isSatellite();
            }
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
            return SPUN_BLOCK.equals(type) || SPUN_ITEM.equals(type) && source.getDeltaMovement().lengthSqr() > 1.0E-4;
        }).size();
    }

    private void emitResonance(ServerLevel server) {
        if (!resonanceRung) {
            resonanceRung = true;
            int points = 12 * resonanceSources;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * i / points;
                server.sendParticles(GOLD_DUST, getX() + Math.cos(angle) * 0.6, getY(), getZ() + Math.sin(angle) * 0.6,
                        1, 0, 0, 0, 0);
            }
            playSound(SoundEvents.BELL_BLOCK, 0.5F, 1.6F);
        }
        if (isSpinning() && !isReturning() && !inGround) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), resonanceSources, 0.05, 0.05, 0.05, 0);
        }
        else {
            resonanceSources = 0;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Age", tickCount);
        nbt.putBoolean("Spinning", isSpinning());
        nbt.putBoolean("Returning", isReturning());
        nbt.putInt("ReturnTicks", returnTicks);
        nbt.putInt("SteeredTicks", steeredTicks);
        nbt.putInt("Bounces", bounces);
        nbt.putBoolean("Wrecking", isWrecking());
        nbt.putBoolean("Satellite", isSatellite());
        nbt.putBoolean("SatellitesReleased", areSatellitesReleased());
        nbt.putBoolean("ShockwaveDone", shockwaveDone);
        nbt.putBoolean("BulletsCut", bulletsCut);
        nbt.putByte("Rope", entityData.get(ROPE));
        nbt.putInt("RopeTicks", ropeTicks);
        nbt.putBoolean("FromHolster", fromHolster);
        nbt.putInt("ResonanceTicks", resonanceTicks);
        nbt.putBoolean("BreakerPayload", breakerPayload);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        tickCount = nbt.getInt("Age");
        entityData.set(SPINNING, nbt.getBoolean("Spinning"));
        entityData.set(RETURNING, nbt.getBoolean("Returning"));
        returnTicks = nbt.getInt("ReturnTicks");
        steeredTicks = nbt.getInt("SteeredTicks");
        bounces = nbt.getInt("Bounces");
        entityData.set(WRECKING, nbt.getBoolean("Wrecking"));
        entityData.set(SATELLITE, nbt.getBoolean("Satellite"));
        entityData.set(SATELLITES_RELEASED, nbt.getBoolean("SatellitesReleased"));
        shockwaveDone = nbt.getBoolean("ShockwaveDone");
        bulletsCut = nbt.getBoolean("BulletsCut");
        entityData.set(ROPE, nbt.getByte("Rope"));
        ropeTicks = nbt.getInt("RopeTicks");
        fromHolster = nbt.getBoolean("FromHolster");
        resonanceTicks = nbt.getInt("ResonanceTicks");
        breakerPayload = nbt.getBoolean("BreakerPayload");
        setNoPhysics(isReturning());
        if (isSatellite()) {
            pickup = Pickup.DISALLOWED;
        }
    }

    @FunctionalInterface
    public interface ReturnStorage {
        boolean store(Player player, ItemStack ball);
    }

    public static void setReturnStorage(ReturnStorage storage) {
        returnStorage = storage;
    }

    public interface ImpactHooks {
        default double damageMultiplier(LivingEntity owner, double golden) { return golden; }
        default void onSpinThrow(Player owner, ItemStack ball) {}
        default void beforeLivingHit(SteelBallEntity ball, LivingEntity owner, LivingEntity target, boolean chipped) {}
        default boolean hurtLiving(SteelBallEntity ball, LivingEntity owner, LivingEntity target, boolean chipped, BooleanSupplier hurt) {
            return hurt.getAsBoolean();
        }
        default void afterLivingHit(SteelBallEntity ball, LivingEntity owner, LivingEntity target, boolean chipped) {}
        default boolean takesPayload(LivingEntity owner) { return false; }
    }

    public static void setImpactHooks(ImpactHooks hooks) {
        impactHooks = hooks;
    }

    public void onSpinThrow(Player owner) {
        impactHooks.onSpinThrow(owner, getPickupItem());
        breakerPayload = impactHooks.takesPayload(owner);
    }

    public boolean hasBreakerPayload() {
        return breakerPayload;
    }
}
