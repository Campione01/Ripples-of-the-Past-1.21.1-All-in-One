package rotp.addons.kingcrimson.entity;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public final class KingCrimsonAfterimageEntity
        extends Entity implements IEntityWithComplexSpawn {
    /** The colour of 1.16's KC_RedGlow team (TextFormatting.RED), which coloured its afterimage outline. */
    private static final int TIME_ERASE_OUTLINE_COLOR =
            ChatFormatting.RED.getColor();
    private static volatile boolean haloReplacesOutline;

    @Nullable
    private Entity originEntity;
    private int originEntityId = -1;
    @Nullable
    private UUID originUuid;
    private int ticksDelayed;
    private int delayTicks;
    private int lifespanTicks;
    /** A shifted player's image: no half-second trail, it stands on its origin. */
    private boolean followsOrigin;
    /** The image of a thing that moves outside an erase (a projectile, a falling block): no creature. */
    private boolean movingThing;
    /** A Time Erase afterimage outlined in red through walls. */
    private boolean timeEraseOutline;
    /** The Time Erase user whose client holds this afterimage; it stands still while that erase is paused. */
    @Nullable
    private Player eraseObserver;
    private final Queue<PositionSample> originPositions =
            new ArrayDeque<>();

    public KingCrimsonAfterimageEntity(
            EntityType<? extends KingCrimsonAfterimageEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void initialize(
            LivingEntity origin,
            int delay,
            int lifespan) {
        setOriginEntity(origin);
        delayTicks = Math.max(delay, 0);
        lifespanTicks = Math.max(lifespan, 0);
    }

    public void initializeClient(
            int originId,
            UUID expectedOriginUuid,
            int delay,
            int lifespan) {
        originEntityId = originId;
        originUuid = expectedOriginUuid;
        delayTicks = Math.max(delay, 0);
        lifespanTicks = Math.max(lifespan, 0);
        resolveOrigin();
    }

    /**
     * Outlines this Time Erase afterimage in red, through walls, for as long as it lives. In 1.16 the outline was on
     * the full-color "present" copy (glowing, on the red KC_RedGlow team); that copy is gone, and the owner chose to
     * keep its outline on this moving one (2026-09-23). Only the Time Erase user's client ever holds these entities,
     * so, as in 1.16, the outline is that user's alone. On the client setGlowingTag only rewrites the glowing flag
     * from itself, so the flag is also set directly.
     */
    public void showTimeEraseOutline() {
        timeEraseOutline = true;
        setGlowingTag(true);
        if (level().isClientSide()) {
            setSharedFlag(FLAG_GLOWING, true);
        }
    }

    public void followErasePauseOf(@Nullable Player observer) {
        eraseObserver = observer;
    }

    /**
     * Turns this client entity into the image of a player a Time Erase shifts: it stands where his entity stands,
     * which the eraser's client has put on his image, for as long as the erase runs.
     */
    public void initializeFollowing(int originId, UUID expectedOriginUuid) {
        initializeClient(originId, expectedOriginUuid, 0, Integer.MAX_VALUE);
        followsOrigin = true;
    }

    public boolean followsOrigin() {
        return followsOrigin;
    }

    /**
     * Turns this client entity into the afterimage of a thing that moves in the world outside a Time Erase (EV1105AC
     * section 7): it trails its origin by a few ticks for as long as the erase runs and the thing is there.
     */
    public void initializeMoving(int originId, UUID expectedOriginUuid, int delay) {
        initializeClient(originId, expectedOriginUuid, delay, Integer.MAX_VALUE);
        movingThing = true;
    }

    public boolean isMovingThing() {
        return movingThing;
    }

    @Nullable
    public Entity getOriginEntity() {
        resolveOrigin();
        return originEntity;
    }

    public int originEntityId() {
        return originEntityId;
    }

    @Nullable
    public UUID originUuid() {
        return originUuid;
    }

    public int delayTicks() {
        return delayTicks;
    }

    public int lifespanTicks() {
        return lifespanTicks;
    }

    @Override
    public void tick() {
        super.tick();
        if (eraseObserver != null && KingCrimsonTemporalStateService.isErasePaused(eraseObserver)) {
            // T1: the erase's foresight freezes with the erase in someone's time stop
            if (lifespanTicks < Integer.MAX_VALUE) {
                lifespanTicks++;
            }
            return;
        }
        Entity origin = getOriginEntity();
        if (origin == null
                || !origin.isAlive()
                || !followsRunningErase() && (movingThing || tickCount > lifespanTicks)) {
            discard();
            return;
        }
        if (followsOrigin) {
            if (!followsRunningErase()) {
                discard();
                return;
            }
            setPos(origin.getX(), origin.getY(), origin.getZ());
            setYRot(origin.getYRot());
            setXRot(origin.getXRot());
            return;
        }

        originPositions.add(new PositionSample(
                origin.position(),
                origin.getXRot(),
                origin.getYRot()));
        if (++ticksDelayed > delayTicks) {
            PositionSample sample = originPositions.remove();
            moveTo(
                    sample.position().x,
                    sample.position().y,
                    sample.position().z,
                    sample.yRot(),
                    sample.xRot());
        }
    }

    // An erase held back by another ability (Made in Heaven's mode A) outlasts its length in client ticks; its
    // afterimages stay until the erase's end removes them.
    private boolean followsRunningErase() {
        return eraseObserver != null && KingCrimsonTemporalStateService.isErasing(eraseObserver);
    }

    @Override
    public boolean isInvisible() {
        Entity origin = getOriginEntity();
        return super.isInvisible()
                || origin != null && origin.isInvisible();
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        Entity origin = getOriginEntity();
        return super.isInvisibleTo(player)
                || origin != null && origin.isInvisibleTo(player);
    }

    @Override
    public boolean displayFireAnimation() {
        Entity origin = getOriginEntity();
        return origin != null && origin.displayFireAnimation();
    }

    @Override
    public int getTeamColor() {
        return timeEraseOutline
                ? TIME_ERASE_OUTLINE_COLOR
                : super.getTeamColor();
    }

    /** While the halo draws these afterimages itself, the vanilla outline stays out of it; on no shaders the outline is the look. */
    public static void setHaloReplacesOutline(boolean replaces) {
        haloReplacesOutline = replaces;
    }

    public boolean showsTimeEraseOutline() {
        return timeEraseOutline;
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return !(timeEraseOutline && haloReplacesOutline) && super.isCurrentlyGlowing();
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        delayTicks = tag.getInt("Delay");
        lifespanTicks = tag.getInt("Lifespan");
        if (tag.hasUUID("Origin")) {
            originUuid = tag.getUUID("Origin");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Delay", delayTicks);
        tag.putInt("Lifespan", lifespanTicks);
        if (originUuid != null) {
            tag.putUUID("Origin", originUuid);
        }
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(originEntityId);
        buffer.writeBoolean(originUuid != null);
        if (originUuid != null) {
            buffer.writeUUID(originUuid);
        }
        buffer.writeVarInt(delayTicks);
        buffer.writeVarInt(lifespanTicks);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        originEntityId = buffer.readVarInt();
        originUuid = buffer.readBoolean()
                ? buffer.readUUID()
                : null;
        delayTicks = buffer.readVarInt();
        lifespanTicks = buffer.readVarInt();
        resolveOrigin();
    }

    private void setOriginEntity(Entity origin) {
        originEntity = origin;
        originEntityId = origin.getId();
        originUuid = origin.getUUID();
        copyPosition(origin);
    }

    private void resolveOrigin() {
        if (originEntity != null
                && !originEntity.isRemoved()
                && originEntity.level() == level()
                && originEntity.getId() == originEntityId
                && originEntity.getUUID().equals(originUuid)) {
            return;
        }
        originEntity = null;
        Entity candidate = level().getEntity(originEntityId);
        if (candidate != null
                && candidate.getUUID().equals(originUuid)) {
            originEntity = candidate;
        }
    }

    private record PositionSample(
            Vec3 position, float xRot, float yRot) {
    }
}
