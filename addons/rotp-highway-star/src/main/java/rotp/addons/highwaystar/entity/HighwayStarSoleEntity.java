package rotp.addons.highwaystar.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.highwaystar.HighwayStarConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

public class HighwayStarSoleEntity extends TamableAnimal {
    private static final EntityDataAccessor<Boolean> REMOTE_CONTROLLED =
            SynchedEntityData.defineId(HighwayStarSoleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double DASH_SPEED = 0.833;
    private static final double NAV_SPEED = 8.3;
    private static final double CLIMB_Y = 0.2;
    private static final double FAR_DIST_SQ = 4.0;
    private static final int LOST_TICKS_TO_DETACH = 100;
    private static final int BREAK_COOLDOWN_TICKS = 4;
    private static final int BREAK_HITS_TO_DESTROY = 4;
    private static final int STUCK_TICKS_FOR_PATH = 20;

    @Nullable private UUID attachedTarget = null;
    private int attachIndex = 0;
    private boolean chasing = false;
    private boolean attached = false;
    private boolean returningToOwner = false;
    private int lostTicks = 0;
    private int wanderTicks = 0;
    private int pathRecalcTicks = 0;
    private int trackingTicks = 0;
    private int settleTicks = 0;
    private int breakCooldown = 0;
    private int stuckTicks = 0;
    private int pathModeTicks = 0;
    private final Map<BlockPos, Integer> breakProgress = new HashMap<>();

    public HighwayStarSoleEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setNoGravity(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(REMOTE_CONTROLLED, false);
    }

    public boolean isRemoteControlled() {
        return entityData.get(REMOTE_CONTROLLED);
    }

    public void setRemoteControlled(boolean remoteControlled) {
        entityData.set(REMOTE_CONTROLLED, remoteControlled);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean onClimbable() {
        return horizontalCollision || super.onClimbable();
    }

    private boolean isValidTargetEntity(@Nullable Entity entity) {
        if (entity == null || !entity.isAlive() || entity == this) {
            return false;
        }
        if (entity instanceof HighwayStarSoleEntity || entity instanceof StandEntity) {
            return false;
        }
        return entity instanceof LivingEntity;
    }

    private void faceMovementDirection(double dx, double dz) {
        if (!(dx * dx + dz * dz < 1.0E-4)) {
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            setYRot(yaw);
            yHeadRot = yaw;
            yBodyRot = yaw;
        }
    }

    public void attachTo(@Nullable UUID target, int index) {
        if (target == null || getUUID().equals(target)) {
            return;
        }
        attachedTarget = target;
        attachIndex = index;
        chasing = true;
        attached = false;
        returningToOwner = false;
        lostTicks = 0;
        wanderTicks = 0;
        pathRecalcTicks = 0;
        trackingTicks = 0;
        settleTicks = 0;
        breakCooldown = 0;
        stuckTicks = 0;
        pathModeTicks = 0;
        setNoGravity(false);
        noPhysics = false;
        getNavigation().stop();
    }

    public void detach() {
        attachedTarget = null;
        chasing = false;
        attached = false;
        lostTicks = 0;
        wanderTicks = 0;
        returningToOwner = true;
        if (getVehicle() != null) {
            stopRiding();
        }
        getNavigation().stop();
        pathRecalcTicks = 0;
        trackingTicks = 0;
        settleTicks = 0;
        stuckTicks = 0;
        pathModeTicks = 0;
        noPhysics = false;
        setNoGravity(false);
        clearBreakProgress();
    }

    public void returnToOwner() {
        detach();
    }

    private void clearBreakProgress() {
        if (!level().isClientSide()) {
            for (BlockPos pos : breakProgress.keySet()) {
                level().destroyBlockProgress(getId(), pos, -1);
            }
            breakProgress.clear();
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        clearBreakProgress();
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (breakCooldown > 0) {
            breakCooldown--;
        }
        if (onClimbable() && horizontalCollision) {
            Vec3 motion = getDeltaMovement();
            if (motion.y < CLIMB_Y) {
                setDeltaMovement(motion.x, CLIMB_Y, motion.z);
            }
        }

        LivingEntity owner = getOwner();
        if (owner == null) {
            if (tickCount > 20) {
                discard();
            }
            return;
        }
        UUID ownerUUID = getOwnerUUID();
        if (ownerUUID == null || !ownerUUID.equals(owner.getUUID()) || !owner.isAlive()) {
            discard();
            return;
        }
        if (attachedTarget == null && !returningToOwner && !chasing && !attached) {
            boolean standInactive;
            try {
                StandPower power = StandPower.get(owner);
                standInactive = power == null || !power.isSummoned();
            }
            catch (Exception e) {
                standInactive = true;
            }
            if (standInactive) {
                discard();
                return;
            }
        }
        if (isRemoved()) {
            return;
        }
        if (attachedTarget != null) {
            trackingTicks++;
            if (trackingTicks >= HighwayStarConfig.TRACKING_MAX_TICKS.get()) {
                detach();
            }
            else if (level() instanceof ServerLevel serverLevel) {
                Entity target = serverLevel.getEntity(attachedTarget);
                if (!isValidTargetEntity(target)) {
                    detach();
                }
                else {
                    LivingEntity living = (LivingEntity) target;
                    if (isScentMasked(living)) {
                        lostTicks++;
                        if (lostTicks >= LOST_TICKS_TO_DETACH) {
                            detach();
                            return;
                        }
                    }
                    else {
                        lostTicks = 0;
                    }
                    if (attachedTarget != null) {
                        if (chasing) {
                            chaseTarget(living);
                        }
                        else if (attached) {
                            positionAroundTarget(living);
                            if (tickCount % 20 == 0) {
                                drainTarget(living, owner);
                            }
                        }
                    }
                }
            }
        }
        else {
            followOwner(owner);
        }
    }

    private void followOwner(LivingEntity owner) {
        if (getVehicle() != null) {
            stopRiding();
        }
        noPhysics = false;
        setNoGravity(false);
        double distSq = distanceToSqr(owner);
        if (distSq <= FAR_DIST_SQ) {
            getNavigation().stop();
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(motion.x * 0.8, motion.y, motion.z * 0.8);
            pathRecalcTicks = 0;
            stuckTicks = 0;
            pathModeTicks = 0;
        }
        else {
            dashTowards(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5, owner.getZ(), owner);
        }
    }

    private void chaseTarget(LivingEntity target) {
        if (!isValidTargetEntity(target)) {
            detach();
            return;
        }
        if (getVehicle() != null) {
            stopRiding();
        }
        noPhysics = false;
        setNoGravity(false);
        double distSq = distanceToSqr(target);
        double threshold = target.getBbWidth() * 0.8 + 1.5;
        if (distSq <= threshold * threshold) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            chasing = false;
            attached = true;
            settleTicks = 2;
            noPhysics = true;
            setNoGravity(true);
            stuckTicks = 0;
            pathModeTicks = 0;
            clearBreakProgress();
        }
        else {
            dashTowards(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), target);
        }
    }

    private void dashTowards(double tx, double ty, double tz, @Nullable Entity destinationEntity) {
        if (pathModeTicks > 0) {
            pathModeTicks--;
            return;
        }
        double dx = tx - getX();
        double dy = ty - (getY() + getBbHeight() * 0.5);
        double dz = tz - getZ();
        double horizSq = dx * dx + dz * dz;
        if (horizSq < 1.0E-4) {
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            return;
        }
        double horizLen = Math.sqrt(horizSq);
        double nx = dx / horizLen;
        double nz = dz / horizLen;
        faceMovementDirection(nx, nz);
        double vy = getDeltaMovement().y;
        if (horizontalCollision && Math.abs(dy) > 0.5) {
            vy = dy > 0.0 ? Math.max(vy, CLIMB_Y) : Math.min(vy, -CLIMB_Y);
            if (onClimbable()) {
                vy = Math.max(vy, CLIMB_Y);
            }
        }
        setDeltaMovement(nx * DASH_SPEED, vy, nz * DASH_SPEED);
        boolean wasBlocked = horizontalCollision;
        tryHitObstacle(new Vec3(tx, ty, tz));
        if (wasBlocked) {
            stuckTicks++;
        }
        else {
            stuckTicks = 0;
        }
        if (stuckTicks >= STUCK_TICKS_FOR_PATH && destinationEntity != null) {
            stuckTicks = 0;
            pathModeTicks = 10;
            getNavigation().moveTo(destinationEntity, NAV_SPEED);
        }
    }

    private void tryHitObstacle(Vec3 destination) {
        if (breakCooldown > 0 || !(level() instanceof ServerLevel) || !horizontalCollision) {
            return;
        }
        Vec3 eye = position().add(0.0, getBbHeight() * 0.5, 0.0);
        Vec3 dir = destination.subtract(eye);
        if (dir.lengthSqr() < 1.0E-4) {
            return;
        }
        dir = dir.normalize();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int step = 1; step <= 2; step++) {
            BlockPos pos = BlockPos.containing(
                    getX() + dir.x * step,
                    getY() + getBbHeight() * 0.5,
                    getZ() + dir.z * step);
            BlockState state = level().getBlockState(pos);
            if (!state.isAir() && canSoleBreak(state, pos)) {
                double d = position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (d < bestDist) {
                    bestDist = d;
                    best = pos;
                }
            }
        }
        if (best != null) {
            int hits = breakProgress.getOrDefault(best, 0) + 1;
            breakProgress.put(best, hits);
            breakCooldown = BREAK_COOLDOWN_TICKS;
            float progress = Math.min(1.0F, (float) hits / BREAK_HITS_TO_DESTROY) * 0.99F;
            level().destroyBlockProgress(getId(), best, (int) (progress * 10.0F));
            if (hits >= BREAK_HITS_TO_DESTROY) {
                level().destroyBlock(best, true, this);
                breakProgress.remove(best);
                level().destroyBlockProgress(getId(), best, -1);
            }
        }
    }

    private boolean canSoleBreak(BlockState state, BlockPos pos) {
        if (state.getDestroySpeed(level(), pos) < 0.0F || state.liquid()) {
            return false;
        }
        return !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.BARRIER)
                && !state.is(Blocks.END_PORTAL_FRAME)
                && !state.is(Blocks.END_PORTAL)
                && !state.is(Blocks.END_GATEWAY)
                && !state.is(Blocks.NETHER_PORTAL)
                && !state.is(Blocks.COMMAND_BLOCK)
                && !state.is(Blocks.CHAIN_COMMAND_BLOCK)
                && !state.is(Blocks.REPEATING_COMMAND_BLOCK)
                && !state.is(Blocks.STRUCTURE_BLOCK)
                && !state.is(Blocks.JIGSAW);
    }

    private void positionAroundTarget(LivingEntity target) {
        if (!isValidTargetEntity(target)) {
            detach();
            return;
        }
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        noPhysics = true;
        if (getVehicle() == null || getVehicle() != target) {
            if (!startRiding(target, true)) {
                chasing = true;
                attached = false;
                noPhysics = false;
                setNoGravity(false);
            }
            return;
        }
        double angle = (Math.PI / 4) * attachIndex;
        double radius = target.getBbWidth() * 0.8 + 0.4;
        double dx = Math.cos(angle) * radius;
        double dz = Math.sin(angle) * radius;
        double dy = (attachIndex % 3) * target.getBbHeight() * 0.3;
        setPos(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
        int amplifier = Math.min(countAttachedSoles(target) - 1, 3);
        if (amplifier >= 0) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, amplifier, false, false));
        }
    }

    private int countAttachedSoles(LivingEntity target) {
        int count = 0;
        for (Entity passenger : target.getPassengers()) {
            if (passenger instanceof HighwayStarSoleEntity) {
                count++;
            }
        }
        return Math.max(count, 1);
    }

    private boolean isScentMasked(LivingEntity target) {
        if (target.isInWater() && target.getAirSupply() < target.getMaxAirSupply() - 20) {
            return true;
        }
        if (target.hasEffect(MobEffects.CONFUSION) || target.isOnFire() || target.isInLava()) {
            return true;
        }
        BlockPos pos = target.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockState state = level().getBlockState(pos.offset(dx, dy, dz));
                    if (state.is(Blocks.CAMPFIRE)
                            || state.is(Blocks.SOUL_CAMPFIRE)
                            || state.is(Blocks.FIRE)
                            || state.is(Blocks.SOUL_FIRE)
                            || state.is(Blocks.MAGMA_BLOCK)
                            || state.is(Blocks.SLIME_BLOCK)
                            || state.is(Blocks.CLAY)
                            || state.is(Blocks.SOUL_SAND)) {
                        return true;
                    }
                }
            }
        }
        // 1.16 Biome.Category SWAMP, BEACH, OCEAN and RIVER
        Holder<Biome> biome = level().getBiome(pos);
        return biome.is(Tags.Biomes.IS_SWAMP) || biome.is(BiomeTags.IS_BEACH)
                || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER);
    }

    private void drainTarget(LivingEntity target, LivingEntity owner) {
        DamageSource magic = level().damageSources().magic();
        if (target.isInvulnerableTo(magic) || target.isDeadOrDying()) {
            return;
        }
        float drain = HighwayStarConfig.DRAIN_DAMAGE.get().floatValue();
        if (target.isUsingItem() && target.getUseItem().has(net.minecraft.core.component.DataComponents.FOOD)) {
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, false, false));
            return;
        }
        if (target instanceof Player player && player.getFoodData().getFoodLevel() > 0) {
            player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 1));
            feedOwner(owner);
            return;
        }
        boolean hurt = target.hurt(magic, drain);
        if (hurt) {
            target.invulnerableTime = 0;
        }
        feedOwner(owner);
    }

    private void feedOwner(LivingEntity owner) {
        float healthAmount = HighwayStarConfig.FEED_HEALTH_AMOUNT.get().floatValue();
        float hungerAmount = HighwayStarConfig.FEED_HUNGER_AMOUNT.get().floatValue();
        if (owner instanceof Player player) {
            if (player.getFoodData().getFoodLevel() < 20) {
                player.getFoodData().eat((int) hungerAmount, 0.1F);
                return;
            }
            if (player.getHealth() < player.getMaxHealth()) {
                player.heal(healthAmount);
                return;
            }
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false));
        }
        else if (owner.getHealth() < owner.getMaxHealth()) {
            owner.heal(healthAmount);
        }
        else {
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false));
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return false;
        }
        Entity attacker = source.getEntity();
        LivingEntity owner = getOwner();
        boolean fromSelf = attacker == this;
        boolean fromOwner = owner != null && attacker == owner;
        boolean ownerInvulnerable = owner instanceof Player player && player.getAbilities().invulnerable
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        boolean fireOff = source.is(DamageTypeTags.IS_FIRE)
                && !level().getGameRules().getBoolean(GameRules.RULE_FIRE_DAMAGE);
        if (fromSelf || fromOwner || ownerInvulnerable || fireOff) {
            return true;
        }
        return !canTakeDamageFrom(source) || isInvulnerable();
    }

    public boolean canTakeDamageFrom(DamageSource source) {
        return DamageUtil.canHurtStands(source)
                || source.getMsgId().contains("stand")
                || source.is(DamageTypes.ON_FIRE);
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        LivingEntity owner = getOwner();
        if (owner != null) {
            return entity == owner || entity.isAlliedTo(owner);
        }
        return super.isAlliedTo(entity);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (getOwner() != null && target == getOwner()) {
            return false;
        }
        return !(target instanceof HighwayStarSoleEntity) && super.canAttack(target);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean canBreed() {
        return false;
    }
}
