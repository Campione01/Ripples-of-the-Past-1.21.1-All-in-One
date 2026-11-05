package rotp.addons.nutkingcall.entity;

import javax.annotation.Nullable;

import rotp.addons.nutkingcall.NutKingCallAddon;
import rotp.addons.nutkingcall.state.NailState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A nail or nut riding the living entity it was stuck in, or the nail that pins it. */
public final class NutKingCallAttachedNailEntity extends Entity {
    public static final int CLICKS_TO_REMOVE = 3;
    public static final int PIN_REMOVE_CLICKS = 10;
    public static final int BLEED_DURATION_TICKS = 100;
    public static final int BLEED_INTERVAL_TICKS = 20;
    public static final float BLEED_DAMAGE = 1.0F;

    private int clickCount;
    private boolean isNut;
    private boolean isPinned;
    @Nullable private BlockPos pinnedBlockPos;

    public NutKingCallAttachedNailEntity(EntityType<? extends NutKingCallAttachedNailEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public NutKingCallAttachedNailEntity(Level level, @Nullable LivingEntity target, boolean isNut) {
        this(NutKingCallAddon.ATTACHED_NAIL_ENTITY.get(), level);
        this.isNut = isNut;
        if (target != null) {
            setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            Entity vehicle = getVehicle();
            if (vehicle == null) {
                discard();
            }
            else if (vehicle instanceof LivingEntity target) {
                if (!target.isAlive()) {
                    discard();
                    return;
                }
                if (!isPinned && !isNut && tickCount <= BLEED_DURATION_TICKS && tickCount % BLEED_INTERVAL_TICKS == 0) {
                    target.hurt(damageSources().generic(), BLEED_DAMAGE);
                }
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        clickCount++;
        int required = isPinned ? PIN_REMOVE_CLICKS : CLICKS_TO_REMOVE;
        if (clickCount >= required) {
            removeNail();
        }
        return InteractionResult.SUCCESS;
    }

    private void removeNail() {
        if (isPinned && pinnedBlockPos != null) {
            level().destroyBlock(pinnedBlockPos, true);
        }
        if (getVehicle() instanceof LivingEntity target) {
            NailState state = NailState.getExisting(target);
            if (state != null) {
                if (isNut) {
                    state.removeNut();
                }
                else {
                    state.removeNail();
                }
                state.clearPinned();
            }
        }
        discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        clickCount = nbt.getInt("ClickCount");
        isNut = nbt.getBoolean("IsNut");
        isPinned = nbt.getBoolean("IsPinned");
        pinnedBlockPos = nbt.contains("PinnedBlockX")
                ? new BlockPos(nbt.getInt("PinnedBlockX"), nbt.getInt("PinnedBlockY"), nbt.getInt("PinnedBlockZ"))
                : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putInt("ClickCount", clickCount);
        nbt.putBoolean("IsNut", isNut);
        nbt.putBoolean("IsPinned", isPinned);
        if (pinnedBlockPos != null) {
            nbt.putInt("PinnedBlockX", pinnedBlockPos.getX());
            nbt.putInt("PinnedBlockY", pinnedBlockPos.getY());
            nbt.putInt("PinnedBlockZ", pinnedBlockPos.getZ());
        }
    }

    @Override
    public boolean isPickable() {
        return false;
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
        return false;
    }

    public boolean isPinned() {
        return isPinned;
    }

    @Nullable
    public BlockPos getPinnedBlockPos() {
        return pinnedBlockPos;
    }

    public static void attachNail(Level level, LivingEntity target) {
        attach(level, target, false);
    }

    public static void attachNut(Level level, LivingEntity target) {
        attach(level, target, true);
    }

    private static void attach(Level level, LivingEntity target, boolean nut) {
        NutKingCallAttachedNailEntity attached = new NutKingCallAttachedNailEntity(level, target, nut);
        level.addFreshEntity(attached);
        if (!attached.startRiding(target, true)) {
            attached.discard();
        }
        else {
            NailState state = NailState.get(target);
            if (nut) {
                state.addNut();
            }
            else {
                state.addNail();
            }
        }
    }

    public static void pinEntity(Level level, LivingEntity target) {
        pinEntity(level, target, null);
    }

    public static void pinEntity(Level level, LivingEntity target, @Nullable LivingEntity owner) {
        NailState state = NailState.get(target);
        Direction pinFace = findPinFace(level, target);
        BlockPos pinPos = findPinPos(target, pinFace);
        NutKingCallAttachedNailEntity nail = new NutKingCallAttachedNailEntity(level, target, false);
        nail.isPinned = true;
        nail.pinnedBlockPos = pinPos;
        nail.setPos(pinPos.getX() + 0.5D, pinPos.getY() + 0.5D, pinPos.getZ() + 0.5D);
        level.addFreshEntity(nail);
        nail.startRiding(target, true);
        state.setPinned(true);
        state.setPinPos(pinPos);
        state.setPinFace(pinFace);
        state.setStruggleCount(0);
        state.setPinOwnerUUID(owner != null ? owner.getUUID() : null);
    }

    public static void unpin(Level level, LivingEntity target, boolean destroyBlock) {
        NailState state = NailState.getExisting(target);
        if (state != null && state.isPinned()) {
            BlockPos pinPos = state.getPinPos();
            for (NutKingCallAttachedNailEntity nail : level.getEntitiesOfClass(
                    NutKingCallAttachedNailEntity.class, target.getBoundingBox().inflate(2.0D))) {
                if (nail.isPinned() && nail.getVehicle() == target) {
                    nail.discard();
                }
            }
            state.clearPinned();
            if (destroyBlock && pinPos != null && !pinPos.equals(BlockPos.ZERO)) {
                level.destroyBlock(pinPos, true);
            }
        }
    }

    // 1.16: the first neighbour, in Direction order, whose facing side is sturdy
    private static Direction findPinFace(Level level, LivingEntity target) {
        BlockPos pos = target.blockPosition();
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState state = level.getBlockState(neighbor);
            if (state.isFaceSturdy(level, neighbor, dir.getOpposite())) {
                return dir;
            }
        }
        return Direction.DOWN;
    }

    private static BlockPos findPinPos(LivingEntity target, Direction face) {
        BlockPos pos = target.blockPosition();
        return face == Direction.DOWN ? pos : pos.relative(face);
    }
}
