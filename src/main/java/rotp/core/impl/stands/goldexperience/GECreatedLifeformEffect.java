package rotp.core.impl.stands.goldexperience;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.core.customobjects.ObjectEntity;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.util.functions.NBTUtil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/*
 * One lifeform its user created, and the server's record of it: the creature is followed wherever it goes, through
 * unloaded chunks and other dimensions, and the record ends when it dies, is reverted or no longer exists.
 */
public class GECreatedLifeformEffect extends StandEffectInstance {
    private static final double MAX_LIFEFORM_DISTANCE = 128.0;
    // the owner: a lifeform that has lived 7 minutes no longer takes one of its creator's 16 places
    public static final int COUNTED_TICKS = 7 * 60 * 20;
    static final String BORN_TAG = "jojo_ripples:ge_lifeform_born";
    private static final int RELOCATE_INTERVAL = 20;
    private static final Map<UUID, GECreatedLifeformEffect> BY_CREATURE = new ConcurrentHashMap<>();
    @Nullable private UUID indexedId;
    private ItemStack sourceItem = ItemStack.EMPTY;
    private ItemStack sourceEntityItemView = ItemStack.EMPTY;
    @Nullable private CompoundTag sourceEntityNbt;
    private BlockState sourceBlock;
    @Nullable private CompoundTag sourceBlockEntityNbt;
    private UUID followTarget;
    private GETransformationEntity.FollowTargetMode followTargetMode;
    private Component sourceName = Component.empty();
    private boolean productEffectsApplied;
    private boolean toothSource;
    private long createdGameTime = -1;
    // a record saved before the creation time was kept: its age is guessed until the creature tells it
    private boolean createdTimeGuessed;
    @Nullable private ResourceKey<Level> lastDimension;
    @Nullable private Vec3 lastPos;
    @Nullable private ResourceLocation lifeformType;
    @Nullable private Component lifeformName;

    public GECreatedLifeformEffect(EntityCustomEffectType<?> effectType) {
        super(effectType);
        needsTarget = true;
    }

    public void setSourceItem(ItemStack sourceItem) {
        this.sourceItem = sourceItem.copy();
        if (!this.sourceItem.isEmpty()) {
            this.sourceItem.setCount(1);
        }
        this.toothSource = false;
        refreshSourceName();
    }

    public void setSourceBlock(BlockState sourceBlock) {
        setSourceBlock(sourceBlock, null);
    }

    public void setSourceBlock(BlockState sourceBlock, @Nullable CompoundTag sourceBlockEntityNbt) {
        this.sourceBlock = sourceBlock;
        this.sourceBlockEntityNbt = sourceBlockEntityNbt != null ? sourceBlockEntityNbt.copy() : null;
        this.toothSource = false;
        refreshSourceName();
    }

    public void setSourceEntity(CompoundTag sourceEntityNbt, ItemStack sourceEntityItemView) {
        this.sourceEntityNbt = sourceEntityNbt.copy();
        this.sourceEntityItemView = sourceEntityItemView.copy();
        if (!this.sourceEntityItemView.isEmpty()) {
            this.sourceEntityItemView.setCount(1);
        }
        this.toothSource = ObjectEntity.isToothSourceTag(this.sourceEntityNbt);
        refreshSourceName();
    }

    public void setFollowTarget(UUID followTarget, GETransformationEntity.FollowTargetMode followTargetMode) {
        this.followTarget = followTarget;
        this.followTargetMode = followTargetMode;
    }

    public ItemStack getItemView() {
        if (!sourceItem.isEmpty()) {
            return sourceItem.copy();
        }
        if (!sourceEntityItemView.isEmpty()) {
            return sourceEntityItemView.copy();
        }
        if (sourceBlock != null) {
            return new ItemStack(sourceBlock.getBlock());
        }
        return ItemStack.EMPTY;
    }

    public Component getSourceName() {
        return sourceName;
    }

    public boolean isToothSource() {
        return toothSource;
    }

    /** The game time it was created at, -1 when not known (a client that was not sent it). */
    public long getCreatedGameTime() {
        return createdGameTime;
    }

    public long getAge(long gameTime) {
        return createdGameTime < 0 ? 0 : Math.max(gameTime - createdGameTime, 0);
    }

    public boolean countsTowardLimit(long gameTime) {
        return createdGameTime < 0 || gameTime - createdGameTime < COUNTED_TICKS;
    }

    @Nullable
    public ResourceKey<Level> getLastDimension() {
        return lastDimension;
    }

    @Nullable
    public Vec3 getLastPos() {
        return lastPos;
    }

    /** What the lifeform is, or is turning into while it forms. */
    @Nullable
    public ResourceLocation getLifeformType() {
        return lifeformType;
    }

    @Nullable
    public Component getLifeformName() {
        return lifeformName;
    }

    @Override
    protected void start() {
        if (level != null && !level.isClientSide()) {
            if (createdGameTime < 0) {
                createdGameTime = level.getGameTime() - tickCount;
                createdTimeGuessed = tickCount > 0;
            }
            index();
        }
    }

    @Override
    public void updateTarget(Level effectLevel) {
        // a player keeps his effects through a portal: his lifeforms are looked for from where he is now
        LivingEntity user = getStandUser();
        if (user != null && user.level() != level) {
            level = user.level();
        }
        if (!level.isClientSide()) {
            Entity target = getTarget();
            if (target instanceof GETransformationEntity transformation && !target.isAlive()) {
                Entity transformationTarget = transformation.getTransformationTarget();
                if (transformationTarget != null && transformationTarget.isAlive()) {
                    setTargetEntity(transformationTarget);
                }
                else {
                    clearTarget();
                    return;
                }
            }
            else if (target instanceof LivingEntity living && living.isDeadOrDying()) {
                // 1.16: keep the killed body as target so stop() turns it back into its source
                remove();
                return;
            }
            else if (target != null && target.isRemoved()) {
                Entity.RemovalReason reason = target.getRemovalReason();
                if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
                    // gone without the death that turns it back: nothing is left to revert
                    clearTarget();
                    remove();
                    return;
                }
                setTargetEntity(null);
                if (reason == Entity.RemovalReason.CHANGED_DIMENSION) {
                    relocate();
                }
            }
        }
        super.updateTarget(level);
        if (!level.isClientSide()) {
            if (getTarget() == null && getTargetUUID() != null && tickCount % RELOCATE_INTERVAL == 0) {
                relocate();
            }
            Entity target = getTarget();
            applyLifeformSpawnEffects(target);
            remember(target);
            index();
        }
    }

    // the lifeform in another dimension than its user's, or back in his
    private void relocate() {
        UUID id = getTargetUUID();
        MinecraftServer server = level.getServer();
        if (id == null || server == null) {
            return;
        }
        for (ServerLevel candidate : server.getAllLevels()) {
            Entity found = candidate.getEntity(id);
            if (found != null && found.isAlive()) {
                setTargetEntity(found);
                return;
            }
        }
    }

    private void remember(@Nullable Entity target) {
        if (target == null) {
            return;
        }
        lastDimension = target.level().dimension();
        lastPos = target.position();
        Entity creature = target instanceof GETransformationEntity transformation
                ? transformation.getTransformationTarget() : target;
        if (creature != null) {
            lifeformType = BuiltInRegistries.ENTITY_TYPE.getKey(creature.getType());
            lifeformName = creature.getCustomName();
            if (creature == target) {
                stampBirth(creature);
            }
        }
    }

    // the age is kept with the creature too, so a record that lost its own takes it back
    private void stampBirth(Entity creature) {
        CompoundTag data = creature.getPersistentData();
        if (data.contains(BORN_TAG, Tag.TAG_LONG)) {
            if (createdTimeGuessed) {
                createdGameTime = data.getLong(BORN_TAG);
                createdTimeGuessed = false;
            }
        }
        else if (createdGameTime >= 0) {
            data.putLong(BORN_TAG, createdGameTime);
        }
    }

    // the server's records by their creature, for an arrival to find its record without a look at every entity's data
    private void index() {
        UUID id = getTargetUUID();
        if (!Objects.equals(id, indexedId)) {
            if (indexedId != null) {
                BY_CREATURE.remove(indexedId, this);
            }
            if (id != null) {
                BY_CREATURE.put(id, this);
            }
            indexedId = id;
        }
    }

    private void unindex() {
        if (indexedId != null) {
            BY_CREATURE.remove(indexedId, this);
            indexedId = null;
        }
    }

    /**
     * A creature came into a level of the server, as its chunk loaded or it arrived from another dimension, maybe where
     * nobody has the chunk loaded: if it is a lifeform, its record learns where it is before it can be reached.
     */
    static void noteArrival(Entity creature) {
        GECreatedLifeformEffect effect = BY_CREATURE.get(creature.getUUID());
        if (effect == null || effect.isStopped()) {
            return;
        }
        Entity target = effect.getTarget();
        // through a portal the record still holds the creature that left until its next tick
        if (target == null || target.isRemoved()) {
            effect.lastDimension = creature.level().dimension();
            effect.lastPos = creature.position();
        }
    }

    static void forgetServerRecords() {
        BY_CREATURE.clear();
    }

    @Override
    protected void tick() {
        if (!level.isClientSide()) {
            Entity target = getTarget();
            Entity lifeform = target instanceof GETransformationEntity transformation
                    && transformation.getTransformationTarget() != null ? transformation.getTransformationTarget() : target;
            // 1.16 ties a lifeform to its creator while it still takes one of the 16 places: it costs him stamina every
            // tick and turns back when he has none, or when he is too far in its own dimension. Past the 7 minutes it is
            // free of him and goes on existing wherever he goes
            boolean tied = countsTowardLimit(level.getGameTime());
            if (tied && userPower != null
                    && !userPower.consumeStamina(GoldExperienceCreateLifeformAbility.getStaminaCostTicking(userPower, lifeform), true)) {
                remove();
                return;
            }
            if (target != null && tied && target.level() == getStandUser().level()
                    && target.distanceToSqr(getStandUser()) > MAX_LIFEFORM_DISTANCE * MAX_LIFEFORM_DISTANCE) {
                remove();
                return;
            }
            if (target != null && followTarget != null && followTargetMode != null && target.level() instanceof ServerLevel serverLevel) {
                Entity followTargetEntity = serverLevel.getEntity(followTarget);
                if (followTargetMode == GETransformationEntity.FollowTargetMode.DELIVERY
                        && followTargetEntity != null && followTargetEntity.distanceToSqr(target) < 4.0) {
                    remove();
                    return;
                }
                if (target instanceof Mob mob && followTargetEntity != null && !mob.isAggressive()) {
                    mob.getNavigation().moveTo(followTargetEntity, 1.0);
                }
            }
        }
    }

    private void applyLifeformSpawnEffects(@Nullable Entity target) {
        if (target instanceof Mob mob) {
            LivingEntity standUser = getStandUser();
            if (standUser != null) {
                makeMobNeutralTo(mob, standUser);
            }
        }
        applyProductEffects(target);
    }

    private void applyProductEffects(@Nullable Entity target) {
        if (productEffectsApplied || target instanceof GETransformationEntity || !(target instanceof LivingEntity living)) {
            return;
        }

        List<MobEffectInstance> effects = GEProductEffectsState.getItemEffects(sourceItem);
        if (effects.isEmpty() && !sourceEntityItemView.isEmpty()) {
            effects = GEProductEffectsState.getItemEffects(sourceEntityItemView);
        }
        if (!effects.isEmpty()) {
            GEProductEffectsState.get(living).setProductEffects(effects);
        }
        productEffectsApplied = true;
    }

    private static void makeMobNeutralTo(Mob mob, LivingEntity neutralTo) {
        UUID neutralUuid = neutralTo.getUUID();
        if (clearTargetIfNeutral(mob, neutralUuid)) {
            for (WrappedGoal goal : mob.targetSelector.getAvailableGoals()) {
                if (goal.isRunning()) {
                    goal.stop();
                }
            }
        }
        for (WrappedGoal goal : mob.targetSelector.getAvailableGoals()) {
            if (goal.getGoal() instanceof NeutralToEntityGoal neutralGoal && neutralGoal.isNeutralTo(neutralUuid)) {
                return;
            }
        }
        mob.targetSelector.addGoal(0, new NeutralToEntityGoal(mob, neutralUuid));
    }

    private static boolean clearTargetIfNeutral(Mob mob, UUID neutralUuid) {
        LivingEntity target = mob.getTarget();
        if (target != null && neutralUuid.equals(target.getUUID())) {
            mob.setTarget(null);
            return true;
        }
        return false;
    }

    private static final class NeutralToEntityGoal extends Goal {
        private final Mob mob;
        private final UUID neutralUuid;

        private NeutralToEntityGoal(Mob mob, UUID neutralUuid) {
            this.mob = mob;
            this.neutralUuid = neutralUuid;
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        private boolean isNeutralTo(UUID uuid) {
            return neutralUuid.equals(uuid);
        }

        @Override
        public boolean canUse() {
            return hasNeutralTarget();
        }

        @Override
        public boolean canContinueToUse() {
            return hasNeutralTarget();
        }

        @Override
        public void start() {
            clearTargetIfNeutral(mob, neutralUuid);
        }

        @Override
        public void tick() {
            clearTargetIfNeutral(mob, neutralUuid);
        }

        private boolean hasNeutralTarget() {
            LivingEntity target = mob.getTarget();
            return target != null && neutralUuid.equals(target.getUUID());
        }
    }

    @Override
    protected void stop() {
        if (!level.isClientSide()) {
            unindex();
            Entity target = getTarget();
            if (target != null) {
                GETransformationEntity.restoreEntitySource(target, getStandUser(), sourceItem, sourceBlock,
                        sourceBlockEntityNbt, sourceEntityNbt, sourceEntityItemView);
            }
        }
    }

    @Override
    public void writeAdditionalPacketData(FriendlyByteBuf buf, boolean sendingToUser) {
        super.writeAdditionalPacketData(buf, sendingToUser);
        if (sendingToUser) {
            writeSourceItem(buf);
            if (buf instanceof RegistryFriendlyByteBuf registryBuf) {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(registryBuf, getSourceName());
            }
            buf.writeBoolean(toothSource);
            // the user's client counts its lifeforms toward the limit by age, as the server does
            buf.writeLong(createdGameTime);
        }
    }

    @Override
    public void readAdditionalPacketData(FriendlyByteBuf buf, boolean clientIsUser) {
        super.readAdditionalPacketData(buf, clientIsUser);
        if (clientIsUser) {
            sourceItem = readSourceItem(buf);
            if (buf instanceof RegistryFriendlyByteBuf registryBuf) {
                sourceName = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(registryBuf);
            }
            toothSource = buf.readBoolean();
            createdGameTime = buf.readLong();
        }
    }

    @Override
    protected void writeAdditionalSaveData(CompoundTag nbt) {
        super.writeAdditionalSaveData(nbt);
        if (!sourceItem.isEmpty()) {
            nbt.put("GESourceItem", sourceItem.save(level.registryAccess()));
        }
        if (sourceEntityNbt != null) {
            nbt.put("GESourceEntity", sourceEntityNbt.copy());
        }
        if (!sourceEntityItemView.isEmpty()) {
            nbt.put("GESourceEntityItemView", sourceEntityItemView.save(level.registryAccess()));
        }
        NBTUtil.put(nbt, "GESourceBlock", sourceBlock, BlockState.CODEC);
        if (sourceBlockEntityNbt != null) {
            nbt.put("GESourceBlockEntity", sourceBlockEntityNbt.copy());
        }
        if (followTarget != null && followTargetMode != null) {
            nbt.putUUID("GEFollowTarget", followTarget);
            nbt.putString("GEFollowMode", followTargetMode.name());
        }
        nbt.putBoolean("GEProductEffectsApplied", productEffectsApplied);
        nbt.putBoolean("GEToothSource", toothSource);
        if (createdGameTime >= 0 && !createdTimeGuessed) {
            nbt.putLong("GECreatedTime", createdGameTime);
        }
        if (lastDimension != null && lastPos != null) {
            nbt.putString("GELastDimension", lastDimension.location().toString());
            nbt.put("GELastPos", newDoubleList(lastPos.x, lastPos.y, lastPos.z));
        }
        if (lifeformType != null) {
            nbt.putString("GELifeformType", lifeformType.toString());
        }
        if (lifeformName != null) {
            nbt.putString("GELifeformName", Component.Serializer.toJson(lifeformName, level.registryAccess()));
        }
    }

    private static ListTag newDoubleList(double... values) {
        ListTag list = new ListTag();
        for (double value : values) {
            list.add(DoubleTag.valueOf(value));
        }
        return list;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("GESourceItem", Tag.TAG_COMPOUND)) {
            sourceItem = ItemStack.parseOptional(level.registryAccess(), nbt.getCompound("GESourceItem"));
        }
        sourceEntityNbt = nbt.contains("GESourceEntity", Tag.TAG_COMPOUND)
                ? nbt.getCompound("GESourceEntity").copy() : null;
        if (nbt.contains("GESourceEntityItemView", Tag.TAG_COMPOUND)) {
            sourceEntityItemView = ItemStack.parseOptional(level.registryAccess(), nbt.getCompound("GESourceEntityItemView"));
        }
        sourceBlock = NBTUtil.getOptional(nbt, "GESourceBlock", BlockState.CODEC).orElse(null);
        sourceBlockEntityNbt = nbt.contains("GESourceBlockEntity", Tag.TAG_COMPOUND)
                ? nbt.getCompound("GESourceBlockEntity").copy() : null;
        followTarget = nbt.hasUUID("GEFollowTarget") ? nbt.getUUID("GEFollowTarget") : null;
        followTargetMode = null;
        if (followTarget != null && nbt.contains("GEFollowMode", Tag.TAG_STRING)) {
            try {
                followTargetMode = GETransformationEntity.FollowTargetMode.valueOf(nbt.getString("GEFollowMode"));
            }
            catch (IllegalArgumentException ignored) {
                followTarget = null;
            }
        }
        productEffectsApplied = nbt.getBoolean("GEProductEffectsApplied");
        toothSource = nbt.contains("GEToothSource", Tag.TAG_BYTE)
                ? nbt.getBoolean("GEToothSource")
                : ObjectEntity.isToothSourceTag(sourceEntityNbt);
        createdGameTime = nbt.contains("GECreatedTime", Tag.TAG_LONG) ? nbt.getLong("GECreatedTime") : -1;
        lastDimension = null;
        lastPos = null;
        ResourceLocation dimensionId = ResourceLocation.tryParse(nbt.getString("GELastDimension"));
        ListTag pos = nbt.getList("GELastPos", Tag.TAG_DOUBLE);
        if (dimensionId != null && pos.size() == 3) {
            lastDimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            lastPos = new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
        }
        lifeformType = nbt.contains("GELifeformType", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(nbt.getString("GELifeformType")) : null;
        lifeformName = null;
        if (nbt.contains("GELifeformName", Tag.TAG_STRING)) {
            try {
                lifeformName = Component.Serializer.fromJson(nbt.getString("GELifeformName"), level.registryAccess());
            }
            catch (RuntimeException ignored) {
                lifeformName = null;
            }
        }
        refreshSourceName();
    }

    private void writeSourceItem(FriendlyByteBuf buf) {
        ItemStack itemView = getItemView();
        buf.writeBoolean(!itemView.isEmpty());
        if (!itemView.isEmpty()) {
            buf.writeNbt(itemView.save(level.registryAccess()));
        }
    }

    private ItemStack readSourceItem(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return ItemStack.EMPTY;
        }
        CompoundTag itemTag = buf.readNbt();
        return itemTag != null ? ItemStack.parseOptional(level.registryAccess(), itemTag) : ItemStack.EMPTY;
    }

    private void refreshSourceName() {
        sourceName = makeSourceName();
    }

    private Component makeSourceName() {
        if (sourceEntityNbt != null && level != null) {
            Entity sourceEntity = EntityType.create(sourceEntityNbt, level).orElse(null);
            if (sourceEntity != null) {
                return sourceEntity.getDisplayName();
            }
        }
        if (!sourceEntityItemView.isEmpty()) {
            return sourceEntityItemView.getHoverName();
        }
        if (!sourceItem.isEmpty()) {
            return sourceItem.getHoverName();
        }
        if (sourceBlock != null) {
            return sourceBlock.getBlock().getName();
        }
        return Component.empty();
    }
}
