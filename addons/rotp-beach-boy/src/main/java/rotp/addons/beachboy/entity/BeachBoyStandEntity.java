package rotp.addons.beachboy.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.item.BeachBoyRodItem;
import rotp.addons.beachboy.util.BeachBoyCombatUtil;
import rotp.addons.beachboy.util.BeachBoyLine;
import rotp.addons.beachboy.util.BeachBoySettings;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.itemtracking.ItemTracker;
import rotp.core.subsystems.itemtracking.ItemTracking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 BeachBoyStandEntity: a bodiless, invulnerable Stand that keeps its user's rod in hand, carries the line
 * settings to the hook and, under remote control, sits on the hook (or the creature on it) as the camera while the
 * hook moves by the user's keys.
 */
public class BeachBoyStandEntity extends StandEntity {
    private static final String ROD_TRACKER_KEY = "BeachBoyRodTracker";
    private static final EntityDataAccessor<Float> DATA_MAX_LENGTH = define(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BLOCK_COLLISION = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_THORNS = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_THORNS_MULTIPLIER = define(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_SOUND_SENSE = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_WHIP_TICKS = define(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WHIP_VERTICAL = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WHIP_LTR = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WHIP_UPWARD = define(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CRUSH_DAMAGE = define(EntityDataSerializers.BOOLEAN);

    private static <T> EntityDataAccessor<T> define(net.minecraft.network.syncher.EntityDataSerializer<T> serializer) {
        return SynchedEntityData.defineId(BeachBoyStandEntity.class, serializer);
    }

    public BeachBoyStandEntity(EntityType<? extends BeachBoyStandEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        entityData.set(DATA_MAX_LENGTH, BeachBoyLine.standRangeCap(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MAX_LENGTH, BeachBoyLine.DEFAULT_LENGTH);
        builder.define(DATA_BLOCK_COLLISION, false);
        builder.define(DATA_THORNS, true);
        builder.define(DATA_THORNS_MULTIPLIER, BeachBoyLine.DEFAULT_THORNS_MULTIPLIER);
        builder.define(DATA_SOUND_SENSE, true);
        builder.define(DATA_WHIP_TICKS, 0);
        builder.define(DATA_WHIP_VERTICAL, false);
        builder.define(DATA_WHIP_LTR, false);
        builder.define(DATA_WHIP_UPWARD, true);
        builder.define(DATA_CRUSH_DAMAGE, true);
    }

    @Override
    public void tick() {
        super.tick();
        followHookCamera();
        int whip = getWhipTicks();
        if (whip > 0 && !level().isClientSide()) {
            BeachBoyCombatUtil.tickWhippedHost(this);
            entityData.set(DATA_WHIP_TICKS, Math.max(0, getWhipTicks() - 1));
        }
        if (!level().isClientSide()) {
            LivingEntity user = getUser();
            if (user instanceof Player player) {
                applySettingsFromUser(player);
                ensureSingleRod(player);
                if (!isAlive()) {
                    return;
                }
                syncLineSettingsToHook(player);
                syncRodVisuals(player);
            }
            stopRemoteIfNoHook();
        }
    }

    // the hook moves under remote control, the Stand only follows it as the camera
    @Override
    public void manualControlInput(Vec3 motionInput) {
        super.manualControlInput(Vec3.ZERO);
    }

    @Override
    public void setManuallyControlled(boolean value) {
        if (!level().isClientSide()) {
            if (value) {
                ensureHookForRemote();
            }
            else {
                BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(getUser());
                if (hook != null) {
                    hook.setRemoteControlled(false);
                }
            }
        }
        super.setManuallyControlled(value);
    }

    /** 1.16 setManualControl(false, false), as the trap hook and a lost hook end the remote control. */
    public void leaveRemoteControl() {
        if (isManuallyControlled() || getStandFlag(StandFlag.MANUAL_CONTROL)) {
            StandEntityManualControlToggle.off(level(), this, false);
        }
    }

    public boolean isControlJumping() {
        return jumping;
    }

    public boolean isControlSneaking() {
        return isShiftKeyDown();
    }

    private void followHookCamera() {
        if (!isManuallyControlled()) {
            return;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(getUser());
        if (hook != null && hook.isAlive()) {
            if (!level().isClientSide()) {
                hook.setRemoteControlled(true);
            }
            Entity host = hook.getHookedEntity();
            if (host != null && host.isAlive()) {
                setPos(host.getX(), host.getY(0.85), host.getZ());
            }
            else {
                setPos(hook.getX(), hook.getY(), hook.getZ());
            }
            setDeltaMovement(Vec3.ZERO);
        }
    }

    private void ensureHookForRemote() {
        if (getUser() instanceof Player player) {
            BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(player);
            if (hook == null) {
                hook = new BeachBoyHookEntity(level(), player);
                level().addFreshEntity(hook);
                level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW,
                        SoundSource.NEUTRAL, 0.5F, 0.8F);
            }
            hook.setRemoteControlled(true);
        }
    }

    private void stopRemoteIfNoHook() {
        if (getStandFlag(StandFlag.MANUAL_CONTROL) && BeachBoyStandUtil.findHook(getUser()) == null) {
            leaveRemoteControl();
        }
    }

    private void applySettingsFromUser(Player player) {
        BeachBoySettings.State state = BeachBoySettings.stateOf(player);
        if (state != null) {
            copySettings(state);
        }
    }

    /** 1.16 copySettingsFromEffect: the user's switches and line length onto the Stand and its hook. */
    public void copySettings(@Nullable BeachBoySettings.State state) {
        if (state == null) {
            return;
        }
        entityData.set(DATA_BLOCK_COLLISION, state.pathLine());
        entityData.set(DATA_CRUSH_DAMAGE, state.crushDamage());
        entityData.set(DATA_SOUND_SENSE, state.soundSense());
        entityData.set(DATA_MAX_LENGTH, BeachBoyLine.clampLength(state.maxLength(), this));
        if (getUser() instanceof Player player) {
            syncLineSettingsToHook(player);
            if (!level().isClientSide()) {
                syncRodVisuals(player);
            }
        }
    }

    // 1.16 kept one tracked rod: a missing rod comes back, unless the tracked one lies somewhere else, which unsummons
    private void ensureSingleRod(Player user) {
        int count = countRods(user);
        if (count == 1) {
            return;
        }
        if (count > 1) {
            int extra = count - 1;
            for (int i = 0; i < user.getInventory().getContainerSize() && extra > 0; i++) {
                ItemStack stack = user.getInventory().getItem(i);
                if (BeachBoyStandUtil.isRodItem(stack)) {
                    int removed = Math.min(extra, stack.getCount());
                    stack.shrink(removed);
                    extra -= removed;
                }
            }
            return;
        }
        UUID trackerId = getRodTrackerId(user);
        if (trackerId != null && level() instanceof ServerLevel serverLevel) {
            ItemTracker tracker = ItemTracking.getItemTracker(trackerId, serverLevel);
            if (tracker != null && tracker.getItem() != null && !tracker.getItem().isEmpty()
                    && tracker.checkItemIsThere(serverLevel)) {
                unsummonMissingRod(user);
                return;
            }
        }
        giveRod(user);
    }

    private void unsummonMissingRod(Player user) {
        StandPower power = StandPower.get(user);
        if (power != null && power.hasPower() && power.getPowerType() != null) {
            power.getPowerType().forceUnsummon(user, power);
        }
    }

    private void giveRod(Player user) {
        ItemStack rod = new ItemStack(BeachBoyAddon.BEACH_BOY_ROD.get());
        BeachBoyRodItem.setReel(rod, getMaxLineLength());
        BeachBoyRodItem.setCast(rod, BeachBoyStandUtil.findHook(user) != null);
        if (user instanceof ServerPlayer && level() instanceof ServerLevel serverLevel) {
            clearTrackedRod(user);
            ItemTracker tracker = ItemTracking.getItemTracking(serverLevel).startTracking(rod, serverLevel);
            if (tracker != null) {
                tracker.setTrackedByPlayer(user);
                setRodTrackerId(user, tracker.trackerId);
            }
        }
        int selected = user.getInventory().selected;
        ItemStack held = user.getInventory().getItem(selected);
        if (held.isEmpty()) {
            user.getInventory().setItem(selected, rod);
        }
        else if (!user.getInventory().add(rod)) {
            user.drop(held.copy(), false);
            user.getInventory().setItem(selected, rod);
        }
    }

    private static int countRods(Player user) {
        int count = 0;
        for (int i = 0; i < user.getInventory().getContainerSize(); i++) {
            ItemStack stack = user.getInventory().getItem(i);
            if (BeachBoyStandUtil.isRodItem(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void syncLineSettingsToHook(Player user) {
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
        if (hook != null) {
            hook.setMaxLineLength(getMaxLineLength());
            hook.setLineBlockCollision(hasLineBlockCollision());
            hook.setLineThornsEnabled(isLineThornsEnabled());
            hook.setLineThornsMultiplier(getLineThornsMultiplier());
        }
    }

    private void syncRodVisuals(Player user) {
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
        boolean cast = hook != null || getWhipTicks() > 0;
        float reel = hook != null ? hook.getVisualReelLength() : getMaxLineLength();
        boolean changed = false;
        for (int i = 0; i < user.getInventory().getContainerSize(); i++) {
            ItemStack stack = user.getInventory().getItem(i);
            if (BeachBoyStandUtil.isRodItem(stack)) {
                if (BeachBoyRodItem.isCast(stack) != cast) {
                    BeachBoyRodItem.setCast(stack, cast);
                    changed = true;
                }
                if (Float.compare(BeachBoyRodItem.getReel(stack), reel) != 0) {
                    BeachBoyRodItem.setReel(stack, reel);
                    changed = true;
                }
            }
        }
        if (changed) {
            user.getInventory().setChanged();
        }
    }

    public float getMaxLineLength() {
        return entityData.get(DATA_MAX_LENGTH);
    }

    public void setMaxLineLength(float length) {
        float clamped = BeachBoyLine.clampLength(length, this);
        entityData.set(DATA_MAX_LENGTH, clamped);
        if (getUser() instanceof Player player) {
            syncLineSettingsToHook(player);
            if (!level().isClientSide()) {
                syncRodVisuals(player);
                BeachBoySettings.setMaxLength(player, clamped);
            }
        }
    }

    public float adjustMaxLineLength(float delta) {
        setMaxLineLength(getMaxLineLength() + delta);
        return getMaxLineLength();
    }

    public boolean hasLineBlockCollision() {
        return entityData.get(DATA_BLOCK_COLLISION);
    }

    public void setLineBlockCollision(boolean collide) {
        entityData.set(DATA_BLOCK_COLLISION, collide);
        if (getUser() instanceof Player player) {
            syncLineSettingsToHook(player);
        }
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

    public boolean isSoundSenseEnabled() {
        return entityData.get(DATA_SOUND_SENSE);
    }

    public void setSoundSenseEnabled(boolean enabled) {
        entityData.set(DATA_SOUND_SENSE, enabled);
    }

    public boolean isCrushDamageEnabled() {
        return entityData.get(DATA_CRUSH_DAMAGE);
    }

    public void setCrushDamageEnabled(boolean enabled) {
        entityData.set(DATA_CRUSH_DAMAGE, enabled);
    }

    /** 1.16 playWhip: each lash swings the other way, each slash the other way up; 18 ticks. */
    public void playWhip(boolean vertical) {
        entityData.set(DATA_WHIP_VERTICAL, vertical);
        if (vertical) {
            entityData.set(DATA_WHIP_UPWARD, !entityData.get(DATA_WHIP_UPWARD));
        }
        else {
            entityData.set(DATA_WHIP_LTR, !entityData.get(DATA_WHIP_LTR));
        }
        entityData.set(DATA_WHIP_TICKS, BeachBoyCombatUtil.WHIP_ANIM_TICKS);
    }

    public void stopWhip() {
        entityData.set(DATA_WHIP_TICKS, 0);
    }

    public int getWhipTicks() {
        return entityData.get(DATA_WHIP_TICKS);
    }

    public boolean isWhipVertical() {
        return entityData.get(DATA_WHIP_VERTICAL);
    }

    public boolean isWhipLeftToRight() {
        return entityData.get(DATA_WHIP_LTR);
    }

    public boolean isWhipUpward() {
        return entityData.get(DATA_WHIP_UPWARD);
    }

    @Nullable
    private static UUID getRodTrackerId(Player user) {
        CompoundTag data = user.getPersistentData();
        return data.hasUUID(ROD_TRACKER_KEY) ? data.getUUID(ROD_TRACKER_KEY) : null;
    }

    private static void setRodTrackerId(Player user, UUID uuid) {
        user.getPersistentData().putUUID(ROD_TRACKER_KEY, uuid);
    }

    private void clearTrackedRod(Player player) {
        UUID trackerId = getRodTrackerId(player);
        if (trackerId != null && level() instanceof ServerLevel serverLevel) {
            ItemTracker tracker = ItemTracking.getItemTracker(trackerId, serverLevel);
            if (tracker != null && tracker.getItem() != null && !tracker.getItem().isEmpty()) {
                tracker.getItem().setCount(0);
            }
            ItemTracking.getItemTracking(serverLevel).stopTracking(trackerId, serverLevel);
        }
        player.getPersistentData().remove(ROD_TRACKER_KEY);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return true;
    }

    @Override
    public float getLeapStrength() {
        return 0.0F;
    }

    @Override
    protected double leapBaseStrength() {
        return 0.0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putFloat("MaxLength", getMaxLineLength());
        nbt.putBoolean("BlockCollision", hasLineBlockCollision());
        nbt.putBoolean("Thorns", isLineThornsEnabled());
        nbt.putFloat("ThornsMul", getLineThornsMultiplier());
        nbt.putBoolean("SoundSense", isSoundSenseEnabled());
        nbt.putBoolean("CrushDamage", isCrushDamageEnabled());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("MaxLength")) {
            entityData.set(DATA_MAX_LENGTH, BeachBoyLine.clampLength(nbt.getFloat("MaxLength"), this));
        }
        setLineBlockCollision(nbt.getBoolean("BlockCollision"));
        setLineThornsEnabled(!nbt.contains("Thorns") || nbt.getBoolean("Thorns"));
        if (nbt.contains("ThornsMul")) {
            setLineThornsMultiplier(nbt.getFloat("ThornsMul"));
        }
        setSoundSenseEnabled(!nbt.contains("SoundSense") || nbt.getBoolean("SoundSense"));
        setCrushDamageEnabled(!nbt.contains("CrushDamage") || nbt.getBoolean("CrushDamage"));
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide() && getUser() instanceof Player player) {
            BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(player);
            if (hook != null) {
                hook.discard();
            }
            BeachBoyStandUtil.removeAllRods(player);
            clearTrackedRod(player);
        }
    }
}
