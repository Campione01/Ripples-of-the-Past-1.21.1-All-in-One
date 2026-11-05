package rotp.addons.beachboy.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.util.BeachBoyCombatUtil;
import rotp.addons.beachboy.util.BeachBoyLine;
import rotp.addons.beachboy.util.BeachBoyPathSwim;
import rotp.addons.beachboy.util.BeachBoyPlayerSync;
import rotp.addons.beachboy.util.BeachBoySettings;
import rotp.addons.beachboy.util.BeachBoySoundUtil;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.addons.beachboy.util.BeachBoyVanillaMotion;
import rotp.addons.beachboy.util.LineRedstone;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * 1.16 BeachBoyHookEntity: Beach Boy's hook and line. Cast from the rod it flies, sinks into blocks for a moment
 * (phasing), floats on water and solid ground like a bobber, fishes like a vanilla bobber in open water, hooks the
 * first creature or item it touches and drags it toward the rod; with the path line on, the line wraps around what
 * it passes and pulls along that path; remote controlled it walks, swims or flies by the user's keys and steers the
 * hooked host; planted as a trap it waits on a block, an item or a creature.
 */
public class BeachBoyHookEntity extends ThrowableProjectile implements IEntityWithComplexSpawn {
    private static final EntityDataAccessor<Float> DATA_MAX_LENGTH = define(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BLOCK_COLLISION = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_THORNS = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_THORNS_MULTIPLIER = define(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_THICKNESS = define(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_OWNER_ID = define(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HOOKED_ID = define(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_REMOTE = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_EMBEDDED = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SNAGGED = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RETRACTING = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_TRAP_ARMED = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_TRAP_POS =
            define(EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_FISHING_BITING = define(EntityDataSerializers.BOOLEAN);
    private static final int MAX_PATH_POINTS = 512;
    private static final int STRAIN_COOLDOWN = 8;

    /** Set by the client setup: the remote input and the reel sound of the local player's hook (1.16 DistExecutor). */
    public static Consumer<BeachBoyHookEntity> clientTick = hook -> {};

    private final List<Vec3> pathPoints = new ArrayList<>();
    @Nullable private Vec3 lastRodSample;
    @Nullable private Vec3 lastLineInteractPos;
    private final Set<BlockPos> lineInteractedBlocks = new HashSet<>();
    private boolean skipHookTrail;
    private boolean hookLeadsHost;
    private float remoteForward;
    private float remoteStrafe;
    private boolean remoteJump;
    private boolean remoteSneak;
    private float remoteYaw;
    private float remotePitch;
    private boolean remoteInputFromPacket;
    private float reelTugRight;
    private float reelTugUp;
    private float reelTugYaw;
    private boolean reelTugReceived;
    private int reelTugWindow;
    private boolean castPhasing = true;
    private int remainingPhaseTicks;
    private int maxBuriedTicks = 2;
    private int buriedTicks;
    private int strainCooldown;
    private long lastConductGameTime = Long.MIN_VALUE;
    private final Set<Integer> conductedProjectileIds = new HashSet<>();
    private boolean bobberSurfaceInitialized;
    private boolean wasAboveSurface = true;
    @Nullable private UUID pendingDeathLoot;
    private final List<ItemStack> deathLoot = new ArrayList<>();
    private ItemStack carriedItem = ItemStack.EMPTY;
    private int timeUntilLured;
    private int timeUntilHooked;
    private int nibble;
    private float fishAngle;
    private boolean reelVoicePlayed;
    private boolean skipLostVoice;
    private int ignoreRehookId = -1;
    private int ignoreRehookTicks;

    private static <T> EntityDataAccessor<T> define(net.minecraft.network.syncher.EntityDataSerializer<T> serializer) {
        return SynchedEntityData.defineId(BeachBoyHookEntity.class, serializer);
    }

    public BeachBoyHookEntity(EntityType<? extends BeachBoyHookEntity> type, Level level) {
        super(type, level);
        noCulling = true;
        noPhysics = true;
    }

    public BeachBoyHookEntity(Level level, LivingEntity owner) {
        this(level, owner, 0.0F);
    }

    /** 1.16: speed 1.15 to 3.7 by the charge times the Stand's cast multiplier, spread 0.08 tightened by the charge. */
    public BeachBoyHookEntity(Level level, LivingEntity owner, float charge) {
        this(BeachBoyAddon.BEACH_BOY_HOOK.get(), level);
        setOwner(owner);
        Vec3 eye = owner.getEyePosition(1.0F);
        setPos(eye.x, eye.y - 0.1, eye.z);
        float strength = Mth.clamp(charge, 0.0F, 1.0F);
        float speed = (1.15F + strength * 2.5500002F) * BeachBoyStandUtil.getCastSpeedMultiplier(owner);
        float inaccuracy = 0.08F * (1.0F - strength * 0.7F);
        shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, speed, inaccuracy);
        remainingPhaseTicks = 2 + Math.round(strength * 18.0F);
        maxBuriedTicks = 1 + Math.round(strength * 10.0F);
        castPhasing = remainingPhaseTicks > 0;
        StandPower power = StandPower.get(owner);
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(owner);
        setMaxLineLength(BeachBoySettings.getMaxLength(power));
        setLineBlockCollision(BeachBoySettings.isPathLine(power));
        if (stand != null) {
            setLineThornsEnabled(stand.isLineThornsEnabled());
            setLineThornsMultiplier(stand.getLineThornsMultiplier());
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MAX_LENGTH, BeachBoyLine.DEFAULT_LENGTH);
        builder.define(DATA_BLOCK_COLLISION, false);
        builder.define(DATA_THORNS, true);
        builder.define(DATA_THORNS_MULTIPLIER, BeachBoyLine.DEFAULT_THORNS_MULTIPLIER);
        builder.define(DATA_THICKNESS, BeachBoyLine.DEFAULT_THICKNESS);
        builder.define(DATA_OWNER_ID, -1);
        builder.define(DATA_HOOKED_ID, -1);
        builder.define(DATA_REMOTE, false);
        builder.define(DATA_EMBEDDED, false);
        builder.define(DATA_SNAGGED, false);
        builder.define(DATA_RETRACTING, false);
        builder.define(DATA_TRAP_ARMED, false);
        builder.define(DATA_TRAP_POS, Optional.empty());
        builder.define(DATA_FISHING_BITING, false);
    }

    @Override
    public void setOwner(@Nullable Entity owner) {
        super.setOwner(owner);
        entityData.set(DATA_OWNER_ID, owner == null ? -1 : owner.getId());
    }

    @Override
    @Nullable
    public Entity getOwner() {
        Entity owner = super.getOwner();
        if (owner != null && !owner.isRemoved()) {
            return owner;
        }
        int id = entityData.get(DATA_OWNER_ID);
        if (id >= 0) {
            Entity found = level().getEntity(id);
            if (found != null) {
                super.setOwner(found);
                return found;
            }
        }
        return owner;
    }

    @Override
    public void tick() {
        Vec3 linePrev = lastLineInteractPos;
        try {
            tickHook();
        }
        finally {
            finishLineInteract(linePrev);
        }
    }

    private void tickHook() {
        baseTick();
        yRotO = getYRot();
        xRotO = getXRot();
        if (!level().isClientSide()) {
            tickReelTugDecay();
        }
        if (strainCooldown > 0) {
            strainCooldown--;
        }
        if (ignoreRehookTicks > 0 && --ignoreRehookTicks <= 0) {
            ignoreRehookId = -1;
        }
        Entity owner = getOwner();
        if (level().isClientSide()) {
            clientTick.accept(this);
        }
        syncRemoteFromStand(owner);
        if (tickRetract(owner)) {
            hangNaturally(owner);
            interceptProjectilesOnLine();
            return;
        }
        if (!level().isClientSide() && (owner == null || !owner.isAlive()
                || !BeachBoyStandUtil.isHoldingRod(owner instanceof LivingEntity living ? living : null))) {
            discard();
            return;
        }
        Entity hooked = getHookedEntity();
        if (hooked != null) {
            if (hooked.isAlive()) {
                castPhasing = false;
                if (hooked instanceof ItemEntity carried) {
                    carried.setPickUpDelay(32767);
                    carried.setNoGravity(true);
                    tryMergeNearbyItems(carried);
                }
                if (isTrapArmed() && !isRetracting()) {
                    snapHookToHost(hooked);
                    tryTriggerItemTrap(hooked);
                    leashOwnerToTrap(owner);
                    hangNaturally(owner);
                    return;
                }
                if (BeachBoyStandUtil.isWhipSwinging(owner instanceof LivingEntity living ? living : null)) {
                    snapHookToHost(hooked);
                    hangNaturally(owner);
                    interceptProjectilesOnLine();
                    return;
                }
                if (isRemoteControlled()) {
                    applyHostControl(hooked);
                    if (!isPathLineEnabled()) {
                        payOutToCurrentSpan(owner, true);
                    }
                }
                if (isPathLineEnabled()) {
                    boolean justPulled = hookLeadsHost || skipHookTrail;
                    snapHookToHost(hooked);
                    updatePathTrail();
                    if (!justPulled && currentPathLength() >= getConstraintLength() - 0.08) {
                        dragHostAlongPath(hooked, getPathPullStep());
                    }
                    snapHookToHost(hooked);
                }
                else if (owner != null) {
                    dragHookedTowardOwner(hooked, owner);
                }
                applyReelTugIfAny(hooked, owner);
                hangNaturally(owner);
                interceptProjectilesOnLine();
                return;
            }
            if (hooked instanceof ItemEntity item) {
                absorbHookedItem(item);
            }
            setHookedEntity(null);
            setEmbedded(false);
            setSnagged(false);
            if (isTrapArmed() && getTrapBlockPos().isEmpty()) {
                disarmTrap();
            }
        }
        if (isTrapArmed() && !isRetracting()) {
            Optional<BlockPos> trap = getTrapBlockPos();
            if (trap.isPresent()) {
                Vec3 center = Vec3.atCenterOf(trap.get());
                setPos(center.x, center.y, center.z);
                setDeltaMovement(Vec3.ZERO);
            }
            leashOwnerToTrap(owner);
            hangNaturally(owner);
        }
        else if (isRemoteControlled()) {
            castPhasing = false;
            applyRemoteHookPhysics();
            if (!isPathLineEnabled()) {
                payOutToCurrentSpan(owner, true);
            }
            updatePathTrail();
            interceptProjectilesOnLine();
            tryHookEntity();
            tryHookItem();
            hangNaturally(owner);
        }
        else {
            if (castPhasing) {
                if (remainingPhaseTicks > 0) {
                    remainingPhaseTicks--;
                }
                if (remainingPhaseTicks <= 0) {
                    castPhasing = false;
                }
            }
            updateBurial();
            if (shouldParkPathHook()) {
                setDeltaMovement(Vec3.ZERO);
            }
            else {
                applyBobberPhysics(castPhasing, false);
            }
            tickVanillaFishing(owner);
            if (!isPathLineEnabled()) {
                payOutToCurrentSpan(owner, false);
                if (!castPhasing && owner != null) {
                    applyLineConstraint();
                }
            }
            updatePathTrail();
            interceptProjectilesOnLine();
            tryHookEntity();
            tryHookItem();
            hangNaturally(owner);
        }
    }

    // 0.1.13: the hook works levers, buttons, plates and tripwire hooks it passes (RevolverBulletRedstoneUtil)
    private void finishLineInteract(@Nullable Vec3 prev) {
        Vec3 now = position();
        if (level() instanceof ServerLevel serverLevel && prev != null && prev.distanceToSqr(now) > 1.0E-6) {
            Entity owner = getOwner();
            Vec3 reference;
            if (isRetracting() && owner != null) {
                reference = owner.position().add(0.0, owner.getEyeHeight(), 0.0);
            }
            else {
                Vec3 motion = now.subtract(prev);
                reference = motion.lengthSqr() > 1.0E-8 ? now.add(motion.normalize()) : now;
            }
            ServerPlayer player = LineRedstone.resolvePlayer(owner);
            if (player != null) {
                LineRedstone.activateAlongPath(serverLevel, prev, now, reference, player, lineInteractedBlocks);
            }
        }
        lastLineInteractPos = now;
    }

    private void tryTriggerItemTrap(Entity hooked) {
        if (hooked instanceof ItemEntity && isTrapArmed() && !level().isClientSide()) {
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class,
                    hooked.getBoundingBox().inflate(0.85), this::canItemTrapTrigger)) {
                if (triggerTrap(living)) {
                    return;
                }
            }
        }
    }

    private boolean canItemTrapTrigger(LivingEntity living) {
        if (living == null || !living.isAlive()) {
            return false;
        }
        Entity owner = getOwner();
        return living != owner && !(living instanceof StandEntity stand && owner instanceof LivingEntity
                && owner.equals(stand.getUser()));
    }

    private void applyBobberPhysics(boolean phaseThrough, boolean sneak) {
        Vec3 motion = getDeltaMovement();
        BlockPos pos = blockPosition();
        BlockState state = level().getBlockState(pos);
        FluidState fluid = state.getFluidState();
        boolean inWater = !fluid.isEmpty();
        boolean inSolid = !inWater && isSolidLikeWater(pos, state);
        if ((inWater || inSolid) && !phaseThrough) {
            motion = BeachBoyVanillaMotion.applyImmersedBuoyancy(this, motion, sneak);
        }
        else {
            motion = motion.add(0.0, -0.03, 0.0);
        }
        Vec3 start = position();
        Vec3 end = start.add(motion);
        if (!phaseThrough && !inWater && !inSolid && motion.y < 0.0) {
            BlockHitResult hit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection().getAxis() == Direction.Axis.Y) {
                end = new Vec3(end.x, BeachBoyVanillaMotion.restYFromSurface(hit.getLocation().y), end.z);
                motion = new Vec3(motion.x * 0.45, 0.0, motion.z * 0.45);
            }
        }
        if ((inWater || inSolid) && !phaseThrough) {
            double clampedY = BeachBoyVanillaMotion.clampImmersedY(this, end.y, sneak);
            if (clampedY != end.y) {
                double vy = clampedY < end.y ? Math.min(0.0, motion.y) : Math.max(0.0, motion.y);
                end = new Vec3(end.x, clampedY, end.z);
                motion = new Vec3(motion.x, vy, motion.z);
            }
            if (isFishingBiting()) {
                motion = new Vec3(motion.x, Math.min(motion.y, -0.12), motion.z);
            }
        }
        motion = BeachBoyVanillaMotion.applyBobberDrag(motion);
        end = limitMoveByPath(start, end);
        setPos(end.x, end.y, end.z);
        setDeltaMovement(motion);
        hasImpulse = true;
        sampleSurfaceFx(start.y, end.y, motion);
    }

    public boolean isSolidLikeWater(BlockPos pos, BlockState state) {
        return !state.isAir() && !state.getCollisionShape(level(), pos).isEmpty();
    }

    public boolean isFluidLikeAt(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        return !state.getFluidState().isEmpty() || isSolidLikeWater(pos, state);
    }

    public boolean isImmersedAt(Vec3 pos) {
        return isFluidLikeAt(BlockPos.containing(pos));
    }

    public boolean isOnWalkSurface() {
        Vec3 start = position();
        BlockHitResult hit = level().clip(new ClipContext(start, start.add(0.0, -0.08, 0.0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK && hit.getDirection().getAxis() == Direction.Axis.Y;
    }

    private boolean canVanillaFish() {
        return !level().isClientSide() && getHookedEntity() == null && !isTrapArmed() && !isRemoteControlled()
                && !isRetracting() && !castPhasing && level().getFluidState(blockPosition()).is(FluidTags.WATER);
    }

    private void tickVanillaFishing(@Nullable Entity owner) {
        if (level().isClientSide()) {
            return;
        }
        if (!canVanillaFish()) {
            resetFishing();
            return;
        }
        catchingFish(owner instanceof LivingEntity living ? living : null);
        if (isFishingBiting() && shouldParkPathHook()) {
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(motion.x, Math.min(motion.y, -0.08), motion.z);
        }
    }

    // 1.16 copy of vanilla FishingBobberEntity.catchingFish
    private void catchingFish(@Nullable LivingEntity owner) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        BlockPos pos = blockPosition();
        int luckBoost = 1;
        BlockPos above = pos.above();
        if (random.nextFloat() < 0.25F && level().isRainingAt(above)) {
            luckBoost++;
        }
        if (random.nextFloat() < 0.5F && !level().canSeeSky(above)) {
            luckBoost--;
        }
        if (nibble > 0) {
            nibble--;
            if (nibble <= 0) {
                timeUntilLured = 0;
                timeUntilHooked = 0;
                setFishingBiting(false);
            }
        }
        else if (timeUntilHooked > 0) {
            timeUntilHooked -= luckBoost;
            if (timeUntilHooked > 0) {
                fishAngle = (float) (fishAngle + random.nextGaussian() * 4.0);
                float angle = fishAngle * Mth.DEG_TO_RAD;
                float sin = Mth.sin(angle);
                float cos = Mth.cos(angle);
                double px = getX() + sin * timeUntilHooked * 0.1F;
                double py = Mth.floor(getY()) + 1.0F;
                double pz = getZ() + cos * timeUntilHooked * 0.1F;
                if (server.getBlockState(BlockPos.containing(px, py - 1.0, pz)).is(Blocks.WATER)) {
                    if (random.nextFloat() < 0.15F) {
                        server.sendParticles(ParticleTypes.BUBBLE, px, py - 0.1, pz, 1, sin, 0.1, cos, 0.0);
                    }
                    float fx = sin * 0.04F;
                    float fz = cos * 0.04F;
                    server.sendParticles(ParticleTypes.FISHING, px, py, pz, 0, fz, 0.01, -fx, 1.0);
                    server.sendParticles(ParticleTypes.FISHING, px, py, pz, 0, -fz, 0.01, fx, 1.0);
                }
            }
            else {
                playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F);
                double splashY = getY() + 0.5;
                int count = (int) (1.0F + getBbWidth() * 20.0F);
                server.sendParticles(ParticleTypes.BUBBLE, getX(), splashY, getZ(), count, getBbWidth(), 0.0, getBbWidth(), 0.2F);
                server.sendParticles(ParticleTypes.FISHING, getX(), splashY, getZ(), count, getBbWidth(), 0.0, getBbWidth(), 0.2F);
                nibble = Mth.nextInt(random, 20, 40);
                setFishingBiting(true);
            }
        }
        else if (timeUntilLured > 0) {
            timeUntilLured -= luckBoost;
            float splashChance = 0.15F;
            if (timeUntilLured < 20) {
                splashChance += (20 - timeUntilLured) * 0.05F;
            }
            else if (timeUntilLured < 40) {
                splashChance += (40 - timeUntilLured) * 0.02F;
            }
            else if (timeUntilLured < 60) {
                splashChance += (60 - timeUntilLured) * 0.01F;
            }
            if (random.nextFloat() < splashChance) {
                float splashAngle = Mth.nextFloat(random, 0.0F, 360.0F) * Mth.DEG_TO_RAD;
                float splashDist = Mth.nextFloat(random, 25.0F, 60.0F);
                double sx = getX() + Mth.sin(splashAngle) * splashDist * 0.1F;
                double sy = Mth.floor(getY()) + 1.0F;
                double sz = getZ() + Mth.cos(splashAngle) * splashDist * 0.1F;
                if (server.getBlockState(BlockPos.containing(sx, sy - 1.0, sz)).is(Blocks.WATER)) {
                    server.sendParticles(ParticleTypes.SPLASH, sx, sy, sz, 2 + random.nextInt(2), 0.1F, 0.0, 0.1F, 0.0);
                }
            }
            if (timeUntilLured <= 0) {
                fishAngle = Mth.nextFloat(random, 0.0F, 360.0F);
                timeUntilHooked = Mth.nextInt(random, 20, 80);
            }
        }
        else {
            ItemStack rod = owner != null ? BeachBoyStandUtil.getHeldRod(owner) : ItemStack.EMPTY;
            int lureTicks = owner != null && !rod.isEmpty()
                    ? (int) (EnchantmentHelper.getFishingTimeReduction(server, rod, owner) * 20.0F) : 0;
            timeUntilLured = Math.max(1, Mth.nextInt(random, 100, 600) - lureTicks);
        }
    }

    private void tryCatchFish() {
        if (!(level() instanceof ServerLevel server) || nibble <= 0) {
            return;
        }
        if (!(getOwner() instanceof Player player)) {
            resetFishing();
            return;
        }
        ItemStack rod = BeachBoyStandUtil.getHeldRod(player);
        ItemStack tool = rod.isEmpty() ? new ItemStack(BeachBoyAddon.BEACH_BOY_ROD.get()) : rod;
        LootParams params = new LootParams.Builder(server)
                .withParameter(LootContextParams.ORIGIN, position())
                .withParameter(LootContextParams.TOOL, tool)
                .withParameter(LootContextParams.THIS_ENTITY, this)
                .withParameter(LootContextParams.ATTACKING_ENTITY, player)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(server, tool, player) + player.getLuck())
                .create(LootContextParamSets.FISHING);
        LootTable table = server.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        List<ItemStack> drops = table.getRandomItems(params);
        for (ItemStack stack : drops) {
            ItemEntity item = new ItemEntity(level(), getX(), getY(), getZ(), stack);
            double dx = player.getX() - getX();
            double dy = player.getY() - getY();
            double dz = player.getZ() - getZ();
            item.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
            level().addFreshEntity(item);
            player.awardStat(Stats.FISH_CAUGHT, 1);
        }
        if (!drops.isEmpty()) {
            level().addFreshEntity(new ExperienceOrb(level(), player.getX(), player.getY() + 0.5, player.getZ() + 0.5,
                    random.nextInt(6) + 1));
        }
        resetFishing();
    }

    private void resetFishing() {
        nibble = 0;
        timeUntilLured = 0;
        timeUntilHooked = 0;
        setFishingBiting(false);
    }

    private boolean isFishingBiting() {
        return entityData.get(DATA_FISHING_BITING);
    }

    private void setFishingBiting(boolean biting) {
        entityData.set(DATA_FISHING_BITING, biting);
    }

    private boolean isHookCandidate(Entity candidate, @Nullable Entity owner) {
        return candidate.isAlive() && candidate instanceof LivingEntity && candidate.isPickable()
                && candidate != owner && candidate != this && candidate.getId() != ignoreRehookId;
    }

    private void tryHookEntity() {
        if (isTrapArmed() || getHookedEntity() != null || ignoreRehookTicks > 0) {
            return;
        }
        Vec3 start = position();
        Vec3 end = start.add(getDeltaMovement());
        EntityHitResult entityHit = findHitEntity(start, end);
        if (entityHit != null) {
            onHitEntity(entityHit);
            return;
        }
        Entity owner = getOwner();
        List<Entity> touching = level().getEntities(this, getBoundingBox().inflate(0.35),
                candidate -> isHookCandidate(candidate, owner));
        if (!touching.isEmpty()) {
            Entity entity = touching.get(0);
            onHitEntity(new EntityHitResult(entity, entity.position()));
        }
    }

    private void tryHookItem() {
        if (level().isClientSide() || isTrapArmed()) {
            return;
        }
        Entity hooked = getHookedEntity();
        if (hooked instanceof LivingEntity) {
            return;
        }
        Entity owner = getOwner();
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(0.55),
                candidate -> candidate.isAlive() && candidate != hooked && candidate != owner
                        && !candidate.getItem().isEmpty() && !BeachBoyStandUtil.isRodItem(candidate.getItem()))) {
            if (tryCarryItem(item)) {
                return;
            }
        }
    }

    /** 1.16: the hook carries one stack of items, merging more of the same into it. */
    public boolean tryCarryItem(@Nullable ItemEntity item) {
        if (item == null || !item.isAlive() || item.getItem().isEmpty() || BeachBoyStandUtil.isRodItem(item.getItem())) {
            return false;
        }
        Entity hooked = getHookedEntity();
        if (hooked instanceof LivingEntity) {
            return false;
        }
        ItemStack incoming = item.getItem();
        ItemStack slot = hooked instanceof ItemEntity hookedItem ? hookedItem.getItem() : getCarriedItem();
        if (!slot.isEmpty() && !canMerge(slot, incoming)) {
            return false;
        }
        if (slot.isEmpty()) {
            int take = Math.min(incoming.getCount(), incoming.getMaxStackSize());
            if (take < incoming.getCount()) {
                ItemStack leftover = incoming.copy();
                leftover.shrink(take);
                incoming.setCount(take);
                item.setItem(incoming);
                ItemEntity extra = new ItemEntity(level(), item.getX(), item.getY(), item.getZ(), leftover);
                extra.setDeltaMovement(item.getDeltaMovement());
                extra.setPickUpDelay(20);
                level().addFreshEntity(extra);
            }
            item.setPickUpDelay(32767);
            item.setNoGravity(true);
            item.setDeltaMovement(Vec3.ZERO);
            embedIn(item);
            return true;
        }
        int room = slot.getMaxStackSize() - slot.getCount();
        if (room <= 0) {
            return false;
        }
        int take = Math.min(room, incoming.getCount());
        slot.grow(take);
        incoming.shrink(take);
        if (incoming.isEmpty()) {
            item.discard();
        }
        else {
            item.setItem(incoming);
        }
        if (hooked instanceof ItemEntity hookedItem) {
            hookedItem.setItem(slot);
        }
        else {
            carriedItem = slot.copy();
        }
        return true;
    }

    private void tryMergeNearbyItems(ItemEntity carried) {
        ItemStack slot = carried.getItem();
        if (slot.isEmpty() || slot.getCount() >= slot.getMaxStackSize()) {
            return;
        }
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(0.8),
                candidate -> candidate != carried && candidate.isAlive() && canMerge(slot, candidate.getItem()))) {
            tryCarryItem(item);
            if (carried.getItem().getCount() >= carried.getItem().getMaxStackSize()) {
                return;
            }
        }
    }

    private static boolean canMerge(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
    }

    public ItemStack getCarriedItem() {
        if (getHookedEntity() instanceof ItemEntity item) {
            return item.getItem();
        }
        return carriedItem == null ? ItemStack.EMPTY : carriedItem;
    }

    private void absorbHookedItem(ItemEntity item) {
        if (!item.getItem().isEmpty()) {
            carriedItem = item.getItem().copy();
            item.discard();
        }
    }

    public void markPendingDeathLoot(@Nullable LivingEntity host) {
        if (host != null) {
            pendingDeathLoot = host.getUUID();
        }
    }

    public boolean isPendingDeathLoot(@Nullable LivingEntity host) {
        return host != null && pendingDeathLoot != null && pendingDeathLoot.equals(host.getUUID());
    }

    public void addDeathLoot(@Nullable ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            deathLoot.add(stack.copy());
        }
    }

    public void clearPendingDeathLoot() {
        pendingDeathLoot = null;
    }

    private void giveLootToOwner() {
        Entity owner = getOwner();
        ItemStack slot = takeHookedItemStack();
        if (slot.isEmpty() && carriedItem != null && !carriedItem.isEmpty()) {
            slot = carriedItem;
        }
        carriedItem = ItemStack.EMPTY;
        if (owner instanceof Player player && !level().isClientSide()) {
            giveStack(player, slot);
            for (ItemStack stack : deathLoot) {
                giveStack(player, stack);
            }
            deathLoot.clear();
        }
        else {
            if (!slot.isEmpty()) {
                deathLoot.add(0, slot);
            }
            dropLootAt(position());
        }
    }

    private ItemStack takeHookedItemStack() {
        if (!(getHookedEntity() instanceof ItemEntity item)) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = item.getItem().copy();
        item.setItem(ItemStack.EMPTY);
        item.setDeltaMovement(Vec3.ZERO);
        item.setNoGravity(false);
        entityData.set(DATA_HOOKED_ID, -1);
        item.discard();
        return taken;
    }

    private void dropLootAt(Vec3 pos) {
        if (level().isClientSide()) {
            return;
        }
        ItemStack slot = getCarriedItem();
        if (!slot.isEmpty() && !(getHookedEntity() instanceof ItemEntity)) {
            level().addFreshEntity(new ItemEntity(level(), pos.x, pos.y, pos.z, slot.copy()));
        }
        carriedItem = ItemStack.EMPTY;
        for (ItemStack stack : deathLoot) {
            if (!stack.isEmpty()) {
                level().addFreshEntity(new ItemEntity(level(), pos.x, pos.y, pos.z, stack.copy()));
            }
        }
        deathLoot.clear();
    }

    private static void giveStack(Player player, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY() + 0.2, player.getZ(), stack.copy());
            drop.setDeltaMovement(Vec3.ZERO);
            drop.setPickUpDelay(20);
            player.level().addFreshEntity(drop);
            stack.setCount(0);
        }
    }

    @Nullable
    private EntityHitResult findHitEntity(Vec3 start, Vec3 end) {
        Entity owner = getOwner();
        Entity closest = null;
        Vec3 closestHit = null;
        double closestDist = Double.MAX_VALUE;
        for (Entity entity : level().getEntities(this, getBoundingBox().expandTowards(getDeltaMovement()).inflate(0.35),
                candidate -> isHookCandidate(candidate, owner))) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(0.2).clip(start, end);
            if (hit.isPresent()) {
                double dist = start.distanceToSqr(hit.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                    closestHit = hit.get();
                }
            }
        }
        return closest != null ? new EntityHitResult(closest, closestHit) : null;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target != getOwner()) {
            if (target instanceof ItemEntity item) {
                tryCarryItem(item);
            }
            else if (target instanceof LivingEntity) {
                embedIn(target);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {}

    public void embedIn(Entity target) {
        embedIn(target, true);
    }

    public void embedIn(Entity target, boolean shout) {
        if (target == getOwner() || !(target instanceof LivingEntity || target instanceof ItemEntity)) {
            return;
        }
        castPhasing = false;
        setHookedEntity(target);
        setEmbedded(true);
        snapHookToHost(target);
        setDeltaMovement(Vec3.ZERO);
        if (shout && target instanceof LivingEntity living) {
            BeachBoySoundUtil.onHookedLiving(this, target);
            if (getOwner() instanceof LivingEntity owner) {
                BeachBoyCombatUtil.tryResolveAutoRip(owner, living);
            }
        }
    }

    public void detachFromHost() {
        Entity host = getHookedEntity();
        if (host == null) {
            return;
        }
        skipLostVoice = true;
        if (isTrapArmed() && getTrapBlockPos().isEmpty()) {
            disarmTrap();
        }
        setEmbedded(false);
        setSnagged(false);
        setHookedEntity(null);
        ignoreRehookId = host.getId();
        ignoreRehookTicks = 40;
        Vec3 pos = host.position().add(0.0, host.getBbHeight() * 0.55, 0.0);
        setPos(pos.x, pos.y, pos.z);
        Entity owner = getOwner();
        Vec3 away = owner != null ? pos.subtract(owner.position()) : host.getLookAngle();
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(0.0, 0.2, 0.0);
        }
        setDeltaMovement(away.normalize().scale(0.45));
        hasImpulse = true;
    }

    public void ejectFromHost(Vec3 motion) {
        Entity host = getHookedEntity();
        disarmTrap();
        setEmbedded(false);
        setSnagged(false);
        setHookedEntity(null);
        remainingPhaseTicks = 0;
        castPhasing = false;
        setRetracting(false);
        if (host != null && host.isAlive()) {
            Vec3 pos = host.position().add(0.0, host.getBbHeight() * 0.55, 0.0);
            setPos(pos.x, pos.y, pos.z);
        }
        setDeltaMovement(motion);
        hasImpulse = true;
    }

    /** A second retract stops reeling; starting one also lands a biting fish (1.16 beginRetract). */
    public void beginRetract() {
        if (isRetracting()) {
            cancelRetract();
            return;
        }
        tryCatchFish();
        BeachBoySoundUtil.onStartRetract(this);
        markReelTugWindow();
        skipLostVoice = true;
        disarmTrap();
        setEmbedded(false);
        setSnagged(false);
        skipHookTrail = true;
        hookLeadsHost = true;
        setRetracting(true);
    }

    public void cancelRetract() {
        if (isRetracting()) {
            skipHookTrail = false;
            hookLeadsHost = false;
            skipLostVoice = false;
            setRetracting(false);
            setDeltaMovement(Vec3.ZERO);
            hasImpulse = true;
        }
    }

    public void reelIn() {
        beginRetract();
    }

    public void setRemoteInput(float forward, float strafe, boolean jump, boolean sneak, float yaw, float pitch) {
        remoteForward = Mth.clamp(forward, -1.0F, 1.0F);
        remoteStrafe = Mth.clamp(strafe, -1.0F, 1.0F);
        remoteJump = jump;
        remoteSneak = sneak;
        remoteYaw = yaw;
        remotePitch = pitch;
        remoteInputFromPacket = true;
    }

    public void setReelTug(float right, float up, float viewYaw) {
        reelTugRight = Mth.clamp(right, -1.0F, 1.0F);
        reelTugUp = Mth.clamp(up, -1.0F, 1.0F);
        reelTugYaw = viewYaw;
        reelTugReceived = true;
    }

    private void markReelTugWindow() {
        reelTugWindow = 12;
    }

    private void tickReelTugDecay() {
        if (!reelTugReceived) {
            reelTugRight *= 0.48F;
            reelTugUp *= 0.48F;
            if (Math.abs(reelTugRight) < 0.02F) {
                reelTugRight = 0.0F;
            }
            if (Math.abs(reelTugUp) < 0.02F) {
                reelTugUp = 0.0F;
            }
        }
        reelTugReceived = false;
        if (reelTugWindow > 0) {
            reelTugWindow--;
        }
    }

    private void applyReelTugIfAny(Entity hooked, @Nullable Entity owner) {
        boolean canTug = !level().isClientSide() && !isRemoteControlled() && (isRetracting() || reelTugWindow > 0);
        if (!canTug || hooked == owner || Math.abs(reelTugRight) < 0.001F && Math.abs(reelTugUp) < 0.001F) {
            return;
        }
        float distScale = 1.0F;
        if (owner != null) {
            float far = (float) Mth.clamp((owner.distanceTo(hooked) - 6.0) / 18.0, 0.0, 1.0);
            distScale = 1.0F - far * 0.62F;
        }
        BeachBoyVanillaMotion.applyReelTug(hooked, reelTugRight, reelTugUp, reelTugYaw, distScale);
    }

    private void syncRemoteFromStand(@Nullable Entity owner) {
        if (!(owner instanceof LivingEntity living)) {
            return;
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(living);
        if (stand == null) {
            if (!level().isClientSide()) {
                setRemoteControlled(false);
            }
            return;
        }
        boolean remote = stand.isManuallyControlled();
        if (!level().isClientSide()) {
            setRemoteControlled(remote);
        }
        if (!remote) {
            remoteInputFromPacket = false;
            return;
        }
        if (!remoteInputFromPacket) {
            remoteForward = 0.0F;
            remoteStrafe = 0.0F;
            remoteJump = false;
            remoteSneak = owner.isShiftKeyDown();
            remoteYaw = stand.getYRot();
            remotePitch = stand.getXRot();
        }
        remoteInputFromPacket = false;
    }

    private void hangNaturally(@Nullable Entity owner) {
        setXRot(90.0F);
        Vec3 from = owner != null ? getLineStart(1.0F) : position().add(0.0, 1.0, 0.0);
        if (!pathPoints.isEmpty()) {
            from = pathPoints.get(pathPoints.size() - 1);
        }
        Vec3 delta = position().subtract(from);
        if (delta.x * delta.x + delta.z * delta.z > 1.0E-6) {
            setYRot((float) (Mth.atan2(delta.x, delta.z) * Mth.RAD_TO_DEG));
        }
    }

    public void snapToHost() {
        Entity hooked = getHookedEntity();
        if (hooked != null && hooked.isAlive()) {
            snapHookToHost(hooked);
        }
    }

    private void snapHookToHost(Entity hooked) {
        setPos(hooked.getX(), hooked.getY(0.85), hooked.getZ());
        setDeltaMovement(Vec3.ZERO);
    }

    private static Vec3 attachPos(Entity hooked) {
        return new Vec3(hooked.getX(), hooked.getY(0.85), hooked.getZ());
    }

    private record PathPullResult(boolean blocked, double moved) {
        static final PathPullResult NONE = new PathPullResult(false, 0.0);
    }

    private PathPullResult dragHostAlongPath(Entity hooked, double maxStep) {
        Vec3 target = hookRewindTarget();
        if (target == null || maxStep <= 1.0E-4) {
            return PathPullResult.NONE;
        }
        Vec3 attach = attachPos(hooked);
        double dist = attach.distanceTo(target);
        if (dist < 0.16) {
            if (!pathPoints.isEmpty()) {
                pathPoints.remove(pathPoints.size() - 1);
            }
            setSnagged(false);
            BeachBoyPathSwim.set(hooked, false);
            return PathPullResult.NONE;
        }
        Vec3 pull = target.subtract(attach);
        double pullLen = pull.length();
        pull = pull.scale(Math.min(maxStep, pullLen) / pullLen);
        Vec3 before = hooked.position();
        hooked.move(MoverType.SELF, pull);
        double moved = hooked.position().distanceTo(before);
        double distAfter = attachPos(hooked).distanceTo(target);
        boolean blocked = dist > 0.16 && !(distAfter < dist - 0.012);
        Vec3 motion = hooked.getDeltaMovement();
        double vy = motion.y;
        if (pull.y > 0.04) {
            hooked.fallDistance = 0.0F;
            vy = Math.max(vy, pull.y * 0.35);
        }
        else if (!hooked.onGround()) {
            vy -= 0.08;
        }
        hooked.setDeltaMovement(motion.x * 0.45 + pull.x * 0.3, vy, motion.z * 0.45 + pull.z * 0.3);
        hooked.hurtMarked = true;
        hooked.hasImpulse = true;
        if (hooked instanceof ServerPlayer player) {
            BeachBoyPlayerSync.sendPositionKeepLook(player);
        }
        boolean swim = hooked instanceof Player && pull.y < -0.05
                && (hooked.onGround() || blocked || hooked.verticalCollision);
        BeachBoyPathSwim.set(hooked, swim);
        if (blocked) {
            setSnagged(true);
            strainHooked(hooked, getOwner());
        }
        else {
            setSnagged(false);
            Vec3 last = pathPoints.isEmpty() ? target : pathPoints.get(pathPoints.size() - 1);
            if (attachPos(hooked).distanceToSqr(last) < 0.0256 && !pathPoints.isEmpty()) {
                pathPoints.remove(pathPoints.size() - 1);
            }
        }
        return new PathPullResult(blocked, moved);
    }

    private void rewindPathFromEnd(double amount) {
        double left = amount;
        int guard = 0;
        while (left > 0.001 && guard++ < 520 && !pathPoints.isEmpty()) {
            Vec3 last = pathPoints.get(pathPoints.size() - 1);
            Vec3 prev = pathPoints.size() >= 2 ? pathPoints.get(pathPoints.size() - 2) : getLineStart(1.0F);
            double seg = last.distanceTo(prev);
            if (seg > left + 0.02 && seg >= 1.0E-4) {
                pathPoints.set(pathPoints.size() - 1, last.add(prev.subtract(last).scale(left / seg)));
                left = 0.0;
            }
            else {
                pathPoints.remove(pathPoints.size() - 1);
                left -= seg;
            }
        }
    }

    private void applyRemoteHookPhysics() {
        if (isPathLineEnabled()) {
            BeachBoyVanillaMotion.travelHookFree(this, remoteStrafe, remoteForward, remoteYaw, remotePitch, remoteJump,
                    remoteSneak);
        }
        else {
            BeachBoyVanillaMotion.travelHook(this, remoteStrafe, remoteForward, remoteYaw, remoteJump, remoteSneak);
        }
    }

    private void applyHostControl(Entity hooked) {
        if (hooked instanceof LivingEntity host) {
            BeachBoyVanillaMotion.travelLiving(host, remoteStrafe, remoteForward, remoteYaw, remotePitch, remoteJump,
                    remoteSneak);
            if (host instanceof ServerPlayer player) {
                BeachBoyPlayerSync.sendPositionKeepLook(player);
            }
        }
    }

    private void payOutToCurrentSpan(@Nullable Entity owner, boolean always) {
        if (owner == null || isPathLineEnabled()) {
            return;
        }
        double dist = getLineStart(1.0F).distanceTo(position());
        float cap = getStandRangeCap();
        if (dist > cap) {
            applySyncedLength(cap, owner);
            applyLineConstraint();
        }
        else if ((always || dist > getMaxLineLength()) && dist > getMaxLineLength() - 0.01) {
            applySyncedLength((float) dist, owner);
        }
    }

    private void applySyncedLength(float length, @Nullable Entity owner) {
        setMaxLineLength(length);
        if (owner instanceof LivingEntity living) {
            BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(living);
            if (stand != null) {
                stand.setMaxLineLength(length);
            }
        }
    }

    /** The mouse wheel: pays out or reels in by one step (1.16 adjustLineLength). False when the line is snagged. */
    public boolean adjustLineLength(float delta) {
        if (isRetracting()) {
            return true;
        }
        if (isPathLineEnabled()) {
            if (delta < 0.0F) {
                markReelTugWindow();
                reelPathBy(-delta);
                tryRetractIfAtMinLength();
            }
            return true;
        }
        Entity owner = getOwner();
        if (delta >= 0.0F) {
            applySyncedLength(getMaxLineLength() + delta, owner);
            return true;
        }
        Entity hooked = getHookedEntity();
        if (hooked != null && hooked.isAlive()) {
            float old = getMaxLineLength();
            float next = BeachBoyLine.clampLength(old + delta,
                    owner instanceof LivingEntity living ? BeachBoyStandUtil.getStand(living) : null);
            if (next >= old) {
                tryRetractIfAtMinLength();
                return true;
            }
            setMaxLineLength(next);
            markReelTugWindow();
            dragHookedTowardOwner(hooked, owner);
            if (isSnagged()) {
                applySyncedLength(old, owner);
                strainHooked(hooked, owner);
                return false;
            }
            applySyncedLength(next, owner);
            tryRetractIfAtMinLength();
            return true;
        }
        applySyncedLength(getMaxLineLength() + delta, owner);
        if (owner != null) {
            applyLineConstraint();
        }
        tryRetractIfAtMinLength();
        return true;
    }

    private void tryRetractIfAtMinLength() {
        if (level().isClientSide() || isRetracting()) {
            return;
        }
        if (isPathLineEnabled() ? currentPathLength() <= 3.08 : getMaxLineLength() <= 3.01F) {
            beginRetract();
        }
    }

    /** 1.16 strainHooked: a snagged creature takes the crush damage, if the crush switch is on. */
    private void strainHooked(Entity hooked, @Nullable Entity owner) {
        if (level().isClientSide() || strainCooldown > 0 || hooked instanceof ItemEntity) {
            return;
        }
        if (owner instanceof LivingEntity living && !BeachBoyStandUtil.isCrushDamageEnabled(StandPower.get(living))) {
            return;
        }
        strainCooldown = STRAIN_COOLDOWN;
        hooked.hurt(BeachBoyLine.createCrushDamage(this), getCrushDamage());
        playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.9F, 0.7F + random.nextFloat() * 0.2F);
    }

    private void dragHookedTowardOwner(Entity hooked, @Nullable Entity owner) {
        if (owner == null || hooked == owner) {
            return;
        }
        Vec3 origin = getLineStart(1.0F);
        Vec3 anchor = pathPoints.isEmpty() ? origin : pathPoints.get(pathPoints.size() - 1);
        double remaining = Math.max(0.05, getConstraintLength() - pathLengthTo(anchor));
        Vec3 pos = hooked.position();
        double dist = anchor.distanceTo(pos);
        if (dist <= remaining + 0.02 || dist < 1.0E-4) {
            setSnagged(false);
            snapHookToHost(hooked);
            return;
        }
        Vec3 toAnchor = anchor.subtract(pos);
        Vec3 horiz = new Vec3(toAnchor.x, 0.0, toAnchor.z);
        double horizDist = horiz.length();
        double excess = dist - remaining;
        Vec3 pull;
        if (horizDist > remaining) {
            pull = horiz.normalize().scale(Math.min(0.55, horizDist - remaining));
        }
        else if (horizDist > 1.0E-4) {
            pull = horiz.normalize().scale(Math.min(0.28, excess * 0.35));
        }
        else {
            pull = Vec3.ZERO;
        }
        boolean mustLift = horizDist <= remaining + 0.05 && pos.y < anchor.y - 0.2 && dist > remaining;
        if (mustLift) {
            pull = pull.add(0.0, Math.min(0.22, excess * 0.25), 0.0);
        }
        Vec3 before = hooked.position();
        hooked.move(MoverType.SELF, pull);
        Vec3 after = hooked.position();
        Vec3 motion = hooked.getDeltaMovement();
        double vy = motion.y;
        if (!hooked.onGround()) {
            vy -= 0.08;
        }
        hooked.setDeltaMovement(motion.x * 0.55 + pull.x * 0.25, vy, motion.z * 0.55 + pull.z * 0.25);
        double distAfter = after.distanceTo(anchor);
        boolean blocked = distAfter > remaining + 0.12 && !(distAfter < dist - 0.015);
        setSnagged(blocked);
        if (blocked) {
            strainHooked(hooked, owner);
        }
        hooked.hurtMarked = true;
        hooked.hasImpulse = true;
        if (mustLift && after.y > before.y) {
            hooked.fallDistance = 0.0F;
        }
        snapHookToHost(hooked);
    }

    private void updateBurial() {
        BlockPos pos = blockPosition();
        BlockState state = level().getBlockState(pos);
        if (isSolidLikeWater(pos, state) && state.getFluidState().isEmpty()) {
            buriedTicks++;
            if (buriedTicks >= maxBuriedTicks) {
                castPhasing = false;
            }
        }
        else {
            buriedTicks = 0;
        }
    }

    public float getRemoteYaw() {
        return remoteYaw;
    }

    public float getRemotePitch() {
        return remotePitch;
    }

    public boolean markConductedThisTick() {
        long time = level().getGameTime();
        if (time == lastConductGameTime) {
            return false;
        }
        lastConductGameTime = time;
        return true;
    }

    public boolean markProjectileConducted(int projectileId) {
        return conductedProjectileIds.add(projectileId);
    }

    public boolean hasConductedProjectile(int projectileId) {
        return conductedProjectileIds.contains(projectileId);
    }

    private void applyLineConstraint() {
        Vec3 origin = getLineStart(1.0F);
        Vec3 pos = position();
        double dist = origin.distanceTo(pos);
        double remaining = getMaxLineLength();
        if (dist <= remaining || dist < 1.0E-4) {
            return;
        }
        Vec3 dir = pos.subtract(origin).normalize();
        Vec3 clamped = origin.add(dir.scale(remaining));
        setPos(clamped.x, clamped.y, clamped.z);
        Vec3 motion = getDeltaMovement();
        double outward = motion.dot(dir);
        if (outward > 0.0) {
            motion = motion.subtract(dir.scale(outward));
        }
        setDeltaMovement(motion);
    }

    /** With the path line, a move that would use more line than the Stand's range pulls the user along instead. */
    public Vec3 limitMoveByPath(Vec3 from, Vec3 to) {
        if (!isPathLineEnabled() || isRetracting()) {
            return to;
        }
        double max = getConstraintLength();
        Vec3 rewind = hookRewindTarget();
        boolean towardTrail = rewind != null && isCloser(to, from, rewind);
        if (!towardTrail && pathPoints.size() >= 2) {
            Vec3 a = pathPoints.get(pathPoints.size() - 2);
            Vec3 b = pathPoints.get(pathPoints.size() - 1);
            towardTrail = segmentProgress(a, b, to) < segmentProgress(a, b, from);
        }
        double usedTo = pathLengthTo(to);
        if (usedTo <= max + 1.0E-4 || towardTrail) {
            return to;
        }
        double usedFrom = pathLengthTo(from);
        if (usedTo <= usedFrom + 1.0E-6) {
            return to;
        }
        if (!level().isClientSide()) {
            pullOwnerTowardPath(Math.min(usedTo - max, 0.55));
            usedTo = pathLengthTo(to);
            usedFrom = pathLengthTo(from);
            if (usedTo <= max + 1.0E-4 || usedTo <= usedFrom + 1.0E-6) {
                return to;
            }
        }
        double remaining = max - usedFrom;
        double step = from.distanceTo(to);
        return remaining > 1.0E-4 && step >= 1.0E-6
                ? from.add(to.subtract(from).normalize().scale(Math.min(step, remaining))) : from;
    }

    private static boolean isCloser(Vec3 candidate, Vec3 origin, Vec3 target) {
        return candidate.distanceToSqr(target) + 1.0E-6 < origin.distanceToSqr(target);
    }

    @Nullable
    private Vec3 hookRewindTarget() {
        if (!pathPoints.isEmpty()) {
            return pathPoints.get(pathPoints.size() - 1);
        }
        return getOwner() != null ? getLineStart(1.0F) : null;
    }

    private void updatePathTrail() {
        if (isPathLineEnabled() && !isRetracting()) {
            updateOwnerTrail();
            updateHookTrail();
            enforcePathLength();
            skipHookTrail = hookLeadsHost;
            hookLeadsHost = false;
        }
        else {
            if (!isPathLineEnabled()) {
                pathPoints.clear();
                lastRodSample = null;
            }
            skipHookTrail = false;
        }
    }

    private void updateOwnerTrail() {
        Vec3 anchor = ownerTrailAnchor();
        if (anchor == null) {
            return;
        }
        if (lastRodSample == null || getHookedEntity() != null) {
            lastRodSample = anchor;
            return;
        }
        consumePassedOwnerWaypoints(anchor);
        if (hookLeadsHost || skipHookTrail || pathPoints.isEmpty()) {
            lastRodSample = anchor;
            return;
        }
        Vec3 first = pathPoints.get(0);
        if (anchor.distanceToSqr(first) + 1.0E-8 < lastRodSample.distanceToSqr(first)) {
            lastRodSample = anchor;
            return;
        }
        if (lastRodSample.distanceToSqr(anchor) >= 0.09 && pathPoints.size() < MAX_PATH_POINTS
                && lastRodSample.distanceToSqr(first) >= 0.0225) {
            pathPoints.add(0, lastRodSample);
        }
        lastRodSample = anchor;
    }

    private void updateHookTrail() {
        if (getHookedEntity() != null) {
            return;
        }
        consumePassedHookWaypoints();
        boolean rewinding = skipHookTrail || hookLeadsHost || isRetracting();
        skipHookTrail = false;
        if (rewinding) {
            return;
        }
        Vec3 now = position();
        Vec3 prevPos = new Vec3(xo, yo, zo);
        Vec3 rewind = hookRewindTarget();
        if (rewind != null && isCloser(now, prevPos, rewind)) {
            consumePassedHookWaypoints();
            return;
        }
        if (pathPoints.isEmpty()) {
            if (now.distanceToSqr(getLineStart(1.0F)) >= 0.09) {
                pathPoints.add(now);
            }
            return;
        }
        Vec3 last = pathPoints.get(pathPoints.size() - 1);
        if (pathPoints.size() >= 2) {
            Vec3 prev = pathPoints.get(pathPoints.size() - 2);
            if (segmentProgress(prev, last, now) <= 1.0) {
                return;
            }
            Vec3 lastToNow = now.subtract(last);
            Vec3 prevToLast = last.subtract(prev);
            if (lastToNow.lengthSqr() > 1.0E-8 && prevToLast.lengthSqr() > 1.0E-8 && lastToNow.dot(prevToLast) <= 0.0) {
                return;
            }
        }
        if (now.distanceToSqr(last) + 1.0E-8 < prevPos.distanceToSqr(last)
                || pathLengthTo(now) >= getConstraintLength() - 0.02) {
            return;
        }
        if (now.distanceToSqr(last) >= 0.09 && pathPoints.size() < MAX_PATH_POINTS) {
            pathPoints.add(now);
        }
    }

    @Nullable
    private Vec3 ownerTrailAnchor() {
        Entity owner = getOwner();
        return owner == null ? null : new Vec3(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5, owner.getZ());
    }

    private static double segmentProgress(Vec3 start, Vec3 end, Vec3 point) {
        Vec3 delta = end.subtract(start);
        double den = delta.lengthSqr();
        return den < 1.0E-8 ? 0.0 : point.subtract(start).dot(delta) / den;
    }

    private void consumePassedHookWaypoints() {
        if (getHookedEntity() != null) {
            return;
        }
        double pickup = 0.12;
        int guard = 0;
        while (!pathPoints.isEmpty() && guard++ < MAX_PATH_POINTS) {
            Vec3 last = pathPoints.get(pathPoints.size() - 1);
            Vec3 hookPos = position();
            if (hookPos.distanceToSqr(last) < pickup * pickup) {
                pathPoints.remove(pathPoints.size() - 1);
                continue;
            }
            if (pathPoints.size() < 2) {
                break;
            }
            Vec3 prev = pathPoints.get(pathPoints.size() - 2);
            if (segmentProgress(prev, last, hookPos) < 0.15) {
                pathPoints.remove(pathPoints.size() - 1);
                continue;
            }
            Vec3 lastToHook = hookPos.subtract(last);
            Vec3 prevToLast = last.subtract(prev);
            if (lastToHook.lengthSqr() > 1.0E-8 && prevToLast.lengthSqr() > 1.0E-8 && lastToHook.dot(prevToLast) < 0.0) {
                pathPoints.remove(pathPoints.size() - 1);
                continue;
            }
            if (!(hookPos.distanceToSqr(prev) + 1.0E-6 < last.distanceToSqr(prev))
                    || !(hookPos.distanceToSqr(prev) <= hookPos.distanceToSqr(last) + 0.08)) {
                break;
            }
            pathPoints.remove(pathPoints.size() - 1);
        }
    }

    private void consumePassedOwnerWaypoints(Vec3 anchor) {
        double pickup = 0.12;
        int guard = 0;
        while (!pathPoints.isEmpty() && guard++ < MAX_PATH_POINTS) {
            Vec3 first = pathPoints.get(0);
            if (anchor.distanceToSqr(first) < pickup * pickup) {
                pathPoints.remove(0);
                continue;
            }
            if (pathPoints.size() < 2 || !(segmentProgress(first, pathPoints.get(1), anchor) > 0.15)) {
                break;
            }
            pathPoints.remove(0);
        }
    }

    private void pullBackToMaxLength() {
        double cap = getConstraintLength();
        Entity hooked = getHookedEntity();
        skipHookTrail = true;
        hookLeadsHost = true;
        int guard = 0;
        if (hooked != null && hooked.isAlive()) {
            while (currentPathLength() > cap + 0.05 && guard++ < 520) {
                PathPullResult result = dragHostAlongPath(hooked, getPathPullStep());
                snapHookToHost(hooked);
                if (result.blocked()) {
                    rewindPathFromEnd(currentPathLength() - cap);
                    break;
                }
                if (result.moved() < 0.001) {
                    break;
                }
            }
        }
        else {
            while (currentPathLength() > cap + 0.05 && guard++ < 520) {
                consumePassedHookWaypoints();
                Vec3 pos = position();
                Vec3 target = hookRewindTarget();
                if (target == null) {
                    break;
                }
                boolean pop = !pathPoints.isEmpty();
                double extra = currentPathLength() - cap;
                double span = pos.distanceTo(target);
                if (span > extra + 0.02) {
                    if (span > 1.0E-4) {
                        Vec3 dir = target.subtract(pos).normalize();
                        setPos(pos.x + dir.x * extra, pos.y + dir.y * extra, pos.z + dir.z * extra);
                    }
                    break;
                }
                setPos(target.x, target.y, target.z);
                if (!pop) {
                    break;
                }
                pathPoints.remove(pathPoints.size() - 1);
            }
        }
        setDeltaMovement(Vec3.ZERO);
        hasImpulse = true;
    }

    /** With the path line, reeling winds the line back along its path (the hook, or the hooked creature, follows it). */
    public void reelPathBy(double amount) {
        if (amount <= 1.0E-4 || !isPathLineEnabled() || isRetracting()) {
            return;
        }
        skipHookTrail = true;
        hookLeadsHost = true;
        Entity hooked = getHookedEntity();
        if (hooked != null && hooked.isAlive()) {
            reelHookedAlongPath(hooked, amount);
            return;
        }
        double left = amount;
        int guard = 0;
        while (left > 0.001 && guard++ < 520) {
            consumePassedHookWaypoints();
            Vec3 target = hookRewindTarget();
            if (target == null) {
                break;
            }
            boolean pop = !pathPoints.isEmpty();
            Vec3 pos = position();
            double dist = pos.distanceTo(target);
            if (dist < 1.0E-4) {
                if (!pop) {
                    break;
                }
                pathPoints.remove(pathPoints.size() - 1);
                continue;
            }
            double step = Math.min(left, dist);
            Vec3 dir = target.subtract(pos).scale(step / dist);
            setPos(pos.x + dir.x, pos.y + dir.y, pos.z + dir.z);
            left -= step;
            consumePassedHookWaypoints();
            if (pop && step >= dist - 0.001 && !pathPoints.isEmpty()
                    && position().distanceToSqr(pathPoints.get(pathPoints.size() - 1)) < 0.04) {
                pathPoints.remove(pathPoints.size() - 1);
            }
            else if (!pop) {
                break;
            }
        }
        setDeltaMovement(Vec3.ZERO);
        hasImpulse = true;
    }

    private void reelHookedAlongPath(Entity hooked, double amount) {
        double left = amount;
        int guard = 0;
        while (left > 0.001 && guard++ < 520) {
            Vec3 target = hookRewindTarget();
            if (target == null) {
                break;
            }
            if (attachPos(hooked).distanceTo(target) < 0.16 && !pathPoints.isEmpty()) {
                pathPoints.remove(pathPoints.size() - 1);
                continue;
            }
            PathPullResult result = dragHostAlongPath(hooked, Math.min(left, getPathPullStep()));
            snapHookToHost(hooked);
            if (result.blocked()) {
                rewindPathFromEnd(left);
                break;
            }
            left -= Math.max(result.moved(), 0.05);
        }
        snapHookToHost(hooked);
        setDeltaMovement(Vec3.ZERO);
        hasImpulse = true;
    }

    private void enforcePathLength() {
        if (isPathLineEnabled() && !isRetracting() && currentPathLength() > getConstraintLength() + 0.05) {
            pullBackToMaxLength();
        }
    }

    private void pullOwnerTowardPath(double extra) {
        if (extra <= 0.001 || !(getOwner() instanceof LivingEntity owner) || !owner.isAlive()) {
            return;
        }
        Vec3 rod = getLineStart(1.0F);
        Vec3 toward = pathPoints.isEmpty() ? position() : pathPoints.get(0);
        Vec3 delta = toward.subtract(rod);
        double dist = delta.length();
        if (dist < 1.0E-4) {
            return;
        }
        owner.move(MoverType.SELF, delta.scale(Math.min(extra, dist) / dist));
        owner.hurtMarked = true;
        owner.hasImpulse = true;
        if (owner instanceof ServerPlayer player) {
            BeachBoyPlayerSync.sendPositionKeepLook(player);
        }
    }

    private float getStandRangeCap() {
        return getOwner() instanceof LivingEntity owner
                ? BeachBoyLine.standRangeCap(BeachBoyStandUtil.getStand(owner)) : BeachBoyLine.ABSOLUTE_MAX_LENGTH;
    }

    public float getConstraintLength() {
        return isPathLineEnabled() ? getStandRangeCap() : getMaxLineLength();
    }

    private boolean tickRetract(@Nullable Entity owner) {
        if (!isRetracting()) {
            return false;
        }
        skipHookTrail = true;
        hookLeadsHost = true;
        Entity hooked = getHookedEntity();
        if (hooked != null && hooked.isAlive()) {
            PathPullResult result = dragHostAlongPath(hooked, getRetractSpeed());
            snapHookToHost(hooked);
            if (result.blocked()) {
                rewindPathFromEnd(getRetractSpeed());
            }
            applyReelTugIfAny(hooked, owner);
            snapHookToHost(hooked);
            Vec3 rod = owner != null ? getLineStart(1.0F) : position();
            if (pathPoints.isEmpty() && attachPos(hooked).distanceTo(rod) < 0.65 && !level().isClientSide()) {
                finishRetract();
            }
            setDeltaMovement(Vec3.ZERO);
            hasImpulse = true;
            return true;
        }
        consumePassedHookWaypoints();
        Vec3 target;
        if (!pathPoints.isEmpty()) {
            target = pathPoints.get(pathPoints.size() - 1);
        }
        else {
            if (owner == null) {
                if (!level().isClientSide()) {
                    finishRetract();
                }
                return true;
            }
            target = getLineStart(1.0F);
        }
        Vec3 pos = position();
        Vec3 delta = target.subtract(pos);
        double dist = delta.length();
        if (dist > getRetractSpeed() && dist >= 0.08) {
            Vec3 step = delta.scale(getRetractSpeed() / dist);
            setPos(pos.x + step.x, pos.y + step.y, pos.z + step.z);
        }
        else {
            setPos(target.x, target.y, target.z);
            if (!pathPoints.isEmpty()) {
                pathPoints.remove(pathPoints.size() - 1);
            }
            else if (!level().isClientSide()) {
                finishRetract();
            }
        }
        consumePassedHookWaypoints();
        setDeltaMovement(Vec3.ZERO);
        hasImpulse = true;
        return true;
    }

    private double pathPolylineLength() {
        double length = 0.0;
        for (int i = 1; i < pathPoints.size(); i++) {
            length += pathPoints.get(i - 1).distanceTo(pathPoints.get(i));
        }
        return length;
    }

    private double pathLengthTo(Vec3 pos) {
        double length = pathPolylineLength();
        Vec3 rod = getOwner() != null ? getLineStart(1.0F) : null;
        if (rod != null) {
            if (pathPoints.isEmpty()) {
                length += rod.distanceTo(pos);
            }
            else {
                length += rod.distanceTo(pathPoints.get(0));
                length += pathPoints.get(pathPoints.size() - 1).distanceTo(pos);
            }
        }
        else if (!pathPoints.isEmpty()) {
            length += pathPoints.get(pathPoints.size() - 1).distanceTo(pos);
        }
        return length;
    }

    private double currentPathLength() {
        return pathLengthTo(position());
    }

    public List<Vec3> getWrapPoints() {
        return Collections.unmodifiableList(pathPoints);
    }

    public List<Vec3> getLineAnchors(float partialTicks) {
        List<Vec3> anchors = new ArrayList<>(pathPoints.size() + 2);
        anchors.add(getLineStart(partialTicks));
        if (isPathLineEnabled()) {
            anchors.addAll(pathPoints);
        }
        anchors.add(getLineEnd(partialTicks));
        return anchors;
    }

    /** 1.16: projectiles crossing a thorned line with a creature on it are conducted to that creature. */
    private void interceptProjectilesOnLine() {
        if (level().isClientSide() || !isLineThornsEnabled()) {
            return;
        }
        BeachBoyLine.of(this).ifPresent(line -> {
            List<Vec3> path = line.getPath(1.0F);
            if (path.size() < 2) {
                conductedProjectileIds.clear();
                return;
            }
            AABB box = new AABB(path.get(0), path.get(0));
            for (Vec3 point : path) {
                box = box.minmax(new AABB(point, point));
            }
            box = box.inflate(line.getCombatThickness() + 0.75);
            Set<Integer> touching = new HashSet<>();
            for (Projectile projectile : level().getEntitiesOfClass(Projectile.class, box,
                    candidate -> candidate.isAlive() && candidate != this && !(candidate instanceof BeachBoyHookEntity))) {
                if (line.crossesProjectilePath(projectile)) {
                    touching.add(projectile.getId());
                    line.tryConductProjectile(projectile);
                }
            }
            conductedProjectileIds.retainAll(touching);
        });
    }

    /** 1.16 getRodTip: the rod's tip in the user's hand, as other players see it. */
    public static Vec3 getRodTip(LivingEntity owner, float partialTicks) {
        int side = 1;
        if (owner instanceof Player player) {
            side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        }
        if (BeachBoyStandUtil.isRodItem(owner.getOffhandItem()) && !BeachBoyStandUtil.isRodItem(owner.getMainHandItem())) {
            side = -side;
        }
        float bodyYaw = Mth.lerp(partialTicks, owner.yBodyRotO, owner.yBodyRot) * Mth.DEG_TO_RAD;
        double sin = Mth.sin(bodyYaw);
        double cos = Mth.cos(bodyYaw);
        double handSide = side * 0.35;
        double x = Mth.lerp(partialTicks, owner.xo, owner.getX()) - cos * handSide - sin * 0.8;
        double y = owner.yo + owner.getEyeHeight() + (owner.getY() - owner.yo) * partialTicks - 0.45
                + (owner.isCrouching() ? -0.1875 : 0.0);
        double z = Mth.lerp(partialTicks, owner.zo, owner.getZ()) - sin * handSide + cos * 0.8;
        return new Vec3(x - sin * 0.88, y + 0.18, z + cos * 0.88);
    }

    public Vec3 getLineEnd(float partialTicks) {
        return new Vec3(Mth.lerp(partialTicks, xo, getX()),
                Mth.lerp(partialTicks, yo, getY()) + getBbHeight() + 0.14,
                Mth.lerp(partialTicks, zo, getZ()));
    }

    public Vec3 getLineStart(float partialTicks) {
        return getOwner() instanceof LivingEntity owner ? getRodTip(owner, partialTicks) : position();
    }

    /** 1.16: the remote hook walks at the Stand's speed (its movement speed or speed stat), 1.28 times, at least 0.18. */
    public float getRemoteMoveSpeed() {
        float fromLiving = 0.0F;
        float fromStat = 0.0F;
        if (getOwner() instanceof LivingEntity owner) {
            BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(owner);
            if (stand != null) {
                fromLiving = stand.getSpeed();
                StandPower power = stand.getUserPower();
                double stat = power != null && power.getPowerType() != null
                        ? power.getPowerType().getStandStats().speed(power.getStatsDevelopment()) : stand.getAttackSpeed();
                fromStat = stat <= 0.0 ? 0.0F : 0.1F * (float) (stat / 8.0);
            }
        }
        float speed = Math.max(fromLiving, fromStat);
        if (speed < 1.0E-4F) {
            speed = 0.1F;
        }
        return Math.max(speed * 1.28F, 0.18F);
    }

    private double getRetractSpeed() {
        return BeachBoyStandUtil.getRetractSpeed(getOwner() instanceof LivingEntity owner ? owner : null);
    }

    private double getPathPullStep() {
        return BeachBoyStandUtil.getPathPullStep(getOwner() instanceof LivingEntity owner ? owner : null);
    }

    private float getCrushDamage() {
        return BeachBoyStandUtil.getCrushDamage(getOwner() instanceof LivingEntity owner ? owner : null);
    }

    /** 1.16 sampleSurfaceFx: splashes when the bobber crosses the surface, ripples and bubbles near it. */
    public void sampleSurfaceFx(double prevY, double newY, Vec3 motion) {
        BeachBoyVanillaMotion.BuoyancyVolume volume = BeachBoyVanillaMotion.findBuoyancyVolume(this);
        if (volume == null) {
            bobberSurfaceInitialized = false;
            wasAboveSurface = true;
            return;
        }
        boolean nowAbove = newY >= volume.restY() - 0.02;
        boolean crossed = bobberSurfaceInitialized && nowAbove != wasAboveSurface;
        double horiz = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        double near = Math.abs(newY - volume.restY());
        if (!level().isClientSide()) {
            if (crossed) {
                spawnBobberSplash(volume.restY(), motion, true);
            }
            else if (near < 0.28 && (Math.abs(motion.y) > 0.012 || horiz > 0.02)
                    && random.nextFloat() < 0.22F + (float) Math.min(0.35, horiz * 2.4)) {
                spawnBobberSplash(volume.restY(), motion, false);
            }
            else if (isImmersedAt(position()) && random.nextFloat() < 0.12F) {
                addBobberParticle(ParticleTypes.BUBBLE, getX() + (random.nextDouble() - 0.5) * 0.16, newY + 0.04,
                        getZ() + (random.nextDouble() - 0.5) * 0.16, 0.0, 0.04 + random.nextDouble() * 0.03, 0.0);
            }
        }
        wasAboveSurface = nowAbove;
        bobberSurfaceInitialized = true;
    }

    private void spawnBobberSplash(double surfaceY, Vec3 motion, boolean burst) {
        double x = getX();
        double z = getZ();
        int count = burst ? 8 + random.nextInt(6) : 2 + random.nextInt(3);
        if (burst) {
            level().playSound(null, x, surfaceY, z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL,
                    0.28F + random.nextFloat() * 0.18F, 0.9F + random.nextFloat() * 0.25F);
        }
        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - 0.5) * 0.28;
            double oz = (random.nextDouble() - 0.5) * 0.28;
            double vx = motion.x * 0.35 + (random.nextDouble() - 0.5) * 0.08;
            double vz = motion.z * 0.35 + (random.nextDouble() - 0.5) * 0.08;
            double vy = 0.04 + random.nextDouble() * 0.12;
            addBobberParticle(ParticleTypes.SPLASH, x + ox, surfaceY + 0.02, z + oz, vx, vy, vz);
            if (burst || random.nextBoolean()) {
                addBobberParticle(ParticleTypes.BUBBLE, x + ox * 0.6, surfaceY - 0.08 - random.nextDouble() * 0.12,
                        z + oz * 0.6, 0.0, 0.06 + random.nextDouble() * 0.05, 0.0);
            }
            if (burst || random.nextFloat() < 0.45F) {
                addBobberParticle(ParticleTypes.FISHING, x + ox * 1.4, surfaceY, z + oz * 1.4, vx * 1.6, 0.02, vz * 1.6);
            }
        }
    }

    private void addBobberParticle(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(particle, x, y, z, 0, vx, vy, vz, 1.0);
        }
        else {
            level().addParticle(particle, x, y, z, vx, vy, vz);
        }
    }

    private void finishRetract() {
        if (!level().isClientSide()) {
            giveLootToOwner();
            playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0F, 1.0F);
            discard();
        }
    }

    @Nullable
    public Entity getHookedEntity() {
        int id = entityData.get(DATA_HOOKED_ID);
        return id >= 0 ? level().getEntity(id) : null;
    }

    public void setHookedEntity(@Nullable Entity entity) {
        Entity previous = getHookedEntity();
        entityData.set(DATA_HOOKED_ID, entity == null ? -1 : entity.getId());
        if (entity != null) {
            resetFishing();
        }
        if (entity != previous) {
            reelVoicePlayed = false;
        }
        if (entity == null) {
            setEmbedded(false);
            setSnagged(false);
            if (previous instanceof ItemEntity item && item.isAlive() && !item.getItem().isEmpty()) {
                item.setDeltaMovement(Vec3.ZERO);
                item.setNoGravity(false);
                item.setPickUpDelay(20);
            }
            if (previous instanceof LivingEntity living) {
                BeachBoyPathSwim.clear(living);
                if (!skipLostVoice && !isRetracting() && living.isAlive()) {
                    BeachBoySoundUtil.onLostEmbeddedTarget(this, living);
                }
            }
            skipLostVoice = false;
        }
    }

    public boolean wasReelVoicePlayed() {
        return reelVoicePlayed;
    }

    public void markReelVoicePlayed() {
        reelVoicePlayed = true;
    }

    public boolean isRemoteControlled() {
        return entityData.get(DATA_REMOTE);
    }

    public void setRemoteControlled(boolean remote) {
        boolean wasRemote = isRemoteControlled();
        entityData.set(DATA_REMOTE, remote);
        if (wasRemote && !remote && isPathLineEnabled() && getHookedEntity() == null) {
            setDeltaMovement(Vec3.ZERO);
        }
    }

    private boolean shouldParkPathHook() {
        return isTrapArmed() || isPathLineEnabled() && !isRemoteControlled() && !isRetracting() && !castPhasing;
    }

    public boolean isTrapArmed() {
        return entityData.get(DATA_TRAP_ARMED);
    }

    public Optional<BlockPos> getTrapBlockPos() {
        return entityData.get(DATA_TRAP_POS);
    }

    private void resetForTrap() {
        remainingPhaseTicks = 0;
        castPhasing = false;
        setRetracting(false);
        setDeltaMovement(Vec3.ZERO);
        pathPoints.clear();
        lastRodSample = null;
        skipHookTrail = false;
        hookLeadsHost = false;
    }

    public void plantTrap(@Nullable LivingEntity entity, @Nullable BlockPos block) {
        resetForTrap();
        if (entity != null) {
            entityData.set(DATA_TRAP_POS, Optional.empty());
            embedIn(entity, false);
            entityData.set(DATA_TRAP_ARMED, true);
        }
        else if (block != null) {
            setHookedEntity(null);
            Vec3 center = Vec3.atCenterOf(block);
            setPos(center.x, center.y, center.z);
            setEmbedded(true);
            entityData.set(DATA_TRAP_POS, Optional.of(block));
            entityData.set(DATA_TRAP_ARMED, true);
        }
    }

    public void plantTrapOnItem(@Nullable ItemEntity item) {
        resetForTrap();
        lineInteractedBlocks.clear();
        if (item != null && item.isAlive() && !item.getItem().isEmpty()) {
            entityData.set(DATA_TRAP_POS, Optional.empty());
            embedIn(item, false);
            item.setPickUpDelay(32767);
            item.setNoGravity(true);
            entityData.set(DATA_TRAP_ARMED, true);
        }
    }

    /** Whoever but the owner (and its Stand) steps on, touches or hits the trap is hooked. */
    public boolean triggerTrap(@Nullable Entity triggerer) {
        if (!isTrapArmed() || !(triggerer instanceof LivingEntity) || !triggerer.isAlive()) {
            return false;
        }
        Entity owner = getOwner();
        if (triggerer == owner
                || triggerer instanceof StandEntity stand && owner instanceof LivingEntity && owner.equals(stand.getUser())) {
            return false;
        }
        disarmTrap();
        embedIn(triggerer);
        if (getHookedEntity() == triggerer) {
            playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0F, 0.7F);
            return true;
        }
        return false;
    }

    public void disarmTrap() {
        entityData.set(DATA_TRAP_ARMED, false);
        entityData.set(DATA_TRAP_POS, Optional.empty());
    }

    private void leashOwnerToTrap(@Nullable Entity owner) {
        if (!(owner instanceof LivingEntity user) || !isTrapArmed()) {
            return;
        }
        Vec3 anchor = getTrapAnchor();
        double max = getMaxLineLength();
        Vec3 from = user.position();
        double dist = from.distanceTo(anchor);
        if (dist <= max) {
            return;
        }
        Vec3 dir = anchor.subtract(from);
        if (dir.lengthSqr() < 1.0E-8) {
            return;
        }
        dir = dir.normalize();
        double extra = dist - max;
        user.setPos(from.x + dir.x * extra, from.y + dir.y * extra, from.z + dir.z * extra);
        user.setDeltaMovement(user.getDeltaMovement().add(dir.scale(0.28)));
        user.hurtMarked = true;
        user.fallDistance = 0.0F;
    }

    private Vec3 getTrapAnchor() {
        Optional<BlockPos> trap = getTrapBlockPos();
        if (trap.isPresent()) {
            return Vec3.atCenterOf(trap.get());
        }
        Entity hooked = getHookedEntity();
        return hooked != null ? hooked.position().add(0.0, hooked.getBbHeight() * 0.5, 0.0) : position();
    }

    public boolean isEmbedded() {
        return entityData.get(DATA_EMBEDDED);
    }

    public void setEmbedded(boolean embedded) {
        entityData.set(DATA_EMBEDDED, embedded);
    }

    public boolean isSnagged() {
        return entityData.get(DATA_SNAGGED);
    }

    public void setSnagged(boolean snagged) {
        entityData.set(DATA_SNAGGED, snagged);
    }

    public float getMaxLineLength() {
        return entityData.get(DATA_MAX_LENGTH);
    }

    public float getVisualReelLength() {
        if (!isRetracting()) {
            return getMaxLineLength();
        }
        double remaining;
        if (isPathLineEnabled() && !pathPoints.isEmpty()) {
            remaining = currentPathLength();
        }
        else {
            remaining = getOwner() != null ? getLineStart(1.0F).distanceTo(position()) : getMaxLineLength();
        }
        return Math.max(BeachBoyLine.MIN_LENGTH, (float) remaining);
    }

    public void setMaxLineLength(float length) {
        BeachBoyStandEntity stand = getOwner() instanceof LivingEntity owner ? BeachBoyStandUtil.getStand(owner) : null;
        entityData.set(DATA_MAX_LENGTH, BeachBoyLine.clampLength(length, stand));
    }

    public boolean isPathLineEnabled() {
        return entityData.get(DATA_BLOCK_COLLISION);
    }

    public boolean hasLineBlockCollision() {
        return isPathLineEnabled();
    }

    public void setLineBlockCollision(boolean collide) {
        boolean wasPath = isPathLineEnabled();
        entityData.set(DATA_BLOCK_COLLISION, collide);
        if (wasPath && !collide) {
            pathPoints.clear();
            lastRodSample = null;
            skipHookTrail = false;
        }
    }

    public boolean isRetracting() {
        return entityData.get(DATA_RETRACTING);
    }

    public void setRetracting(boolean retracting) {
        entityData.set(DATA_RETRACTING, retracting);
    }

    public boolean isLineThornsEnabled() {
        return entityData.get(DATA_THORNS);
    }

    public void setLineThornsEnabled(boolean enabled) {
        entityData.set(DATA_THORNS, enabled);
    }

    public float getLineThornsMultiplier() {
        return entityData.get(DATA_THORNS_MULTIPLIER);
    }

    public void setLineThornsMultiplier(float multiplier) {
        entityData.set(DATA_THORNS_MULTIPLIER, Math.max(0.0F, multiplier));
    }

    public float getLineThickness() {
        return entityData.get(DATA_THICKNESS);
    }

    public void setLineThickness(float thickness) {
        entityData.set(DATA_THICKNESS, Math.max(0.02F, thickness));
    }

    @Override
    public float getPickRadius() {
        return 0.45F;
    }

    @Override
    public boolean isPickable() {
        return !isTrapArmed();
    }

    /** 1.16 attackEntityFrom: a hit on the hook itself is conducted to the hooked creature. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (BeachBoyLine.tryReflectAttack(this, source, amount)) {
            markHurt();
            return true;
        }
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && super.canHitEntity(entity);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putFloat("MaxLength", getMaxLineLength());
        nbt.putBoolean("BlockCollision", hasLineBlockCollision());
        nbt.putBoolean("Thorns", isLineThornsEnabled());
        nbt.putFloat("ThornsMul", getLineThornsMultiplier());
        nbt.putFloat("Thickness", getLineThickness());
        nbt.putInt("HookedId", entityData.get(DATA_HOOKED_ID));
        nbt.putBoolean("Remote", isRemoteControlled());
        nbt.putBoolean("Embedded", isEmbedded());
        nbt.putBoolean("Snagged", isSnagged());
        nbt.putInt("PhaseTicks", remainingPhaseTicks);
        nbt.putInt("MaxBuried", maxBuriedTicks);
        nbt.putBoolean("Retracting", isRetracting());
        nbt.putBoolean("TrapArmed", isTrapArmed());
        if (!carriedItem.isEmpty()) {
            nbt.put("CarriedItem", carriedItem.save(registryAccess()));
        }
        ListTag lootNbt = new ListTag();
        for (ItemStack stack : deathLoot) {
            if (!stack.isEmpty()) {
                lootNbt.add(stack.save(registryAccess()));
            }
        }
        nbt.put("DeathLoot", lootNbt);
        getTrapBlockPos().ifPresent(trap -> {
            nbt.putInt("TrapX", trap.getX());
            nbt.putInt("TrapY", trap.getY());
            nbt.putInt("TrapZ", trap.getZ());
        });
        ListTag pathNbt = new ListTag();
        for (Vec3 point : pathPoints) {
            CompoundTag pointNbt = new CompoundTag();
            pointNbt.putDouble("X", point.x);
            pointNbt.putDouble("Y", point.y);
            pointNbt.putDouble("Z", point.z);
            pathNbt.add(pointNbt);
        }
        nbt.put("PathPoints", pathNbt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("MaxLength")) {
            setMaxLineLength(nbt.getFloat("MaxLength"));
        }
        setLineBlockCollision(nbt.getBoolean("BlockCollision"));
        setLineThornsEnabled(!nbt.contains("Thorns") || nbt.getBoolean("Thorns"));
        if (nbt.contains("ThornsMul")) {
            setLineThornsMultiplier(nbt.getFloat("ThornsMul"));
        }
        if (nbt.contains("Thickness")) {
            setLineThickness(nbt.getFloat("Thickness"));
        }
        if (nbt.contains("HookedId")) {
            entityData.set(DATA_HOOKED_ID, nbt.getInt("HookedId"));
        }
        setRemoteControlled(nbt.getBoolean("Remote"));
        setEmbedded(nbt.getBoolean("Embedded"));
        setSnagged(nbt.getBoolean("Snagged"));
        if (nbt.contains("PhaseTicks")) {
            remainingPhaseTicks = nbt.getInt("PhaseTicks");
            castPhasing = remainingPhaseTicks > 0;
        }
        if (nbt.contains("MaxBuried")) {
            maxBuriedTicks = Math.max(1, nbt.getInt("MaxBuried"));
        }
        pathPoints.clear();
        ListTag pathNbt = nbt.getList("PathPoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < pathNbt.size(); i++) {
            CompoundTag pointNbt = pathNbt.getCompound(i);
            pathPoints.add(new Vec3(pointNbt.getDouble("X"), pointNbt.getDouble("Y"), pointNbt.getDouble("Z")));
        }
        setRetracting(nbt.getBoolean("Retracting"));
        if (nbt.getBoolean("TrapArmed")) {
            if (nbt.contains("TrapX")) {
                plantTrap(null, new BlockPos(nbt.getInt("TrapX"), nbt.getInt("TrapY"), nbt.getInt("TrapZ")));
            }
            else {
                entityData.set(DATA_TRAP_ARMED, true);
            }
        }
        carriedItem = nbt.contains("CarriedItem")
                ? ItemStack.parseOptional(registryAccess(), nbt.getCompound("CarriedItem")) : ItemStack.EMPTY;
        deathLoot.clear();
        ListTag lootNbt = nbt.getList("DeathLoot", Tag.TAG_COMPOUND);
        for (int i = 0; i < lootNbt.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registryAccess(), lootNbt.getCompound(i));
            if (!stack.isEmpty()) {
                deathLoot.add(stack);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && !isRemoved()) {
            dropLootAt(position());
        }
        super.remove(reason);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        Entity owner = getOwner();
        buffer.writeInt(owner == null ? -1 : owner.getId());
        buffer.writeVarInt(remainingPhaseTicks);
        buffer.writeVarInt(maxBuriedTicks);
        buffer.writeVarInt(pathPoints.size());
        for (Vec3 point : pathPoints) {
            buffer.writeDouble(point.x);
            buffer.writeDouble(point.y);
            buffer.writeDouble(point.z);
        }
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        int id = buffer.readInt();
        if (id >= 0) {
            Entity entity = level().getEntity(id);
            if (entity != null) {
                setOwner(entity);
            }
            else {
                entityData.set(DATA_OWNER_ID, id);
            }
        }
        remainingPhaseTicks = buffer.readVarInt();
        maxBuriedTicks = Math.max(1, buffer.readVarInt());
        castPhasing = remainingPhaseTicks > 0;
        pathPoints.clear();
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            pathPoints.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        }
    }
}
