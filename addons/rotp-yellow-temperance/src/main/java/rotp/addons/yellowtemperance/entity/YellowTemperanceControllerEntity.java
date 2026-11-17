package rotp.addons.yellowtemperance.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.disguise.DisguiseRetaliationService;
import rotp.addons.yellowtemperance.disguise.DisguiseScaleService;
import rotp.addons.yellowtemperance.disguise.YellowTemperanceDisguiseState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class YellowTemperanceControllerEntity
        extends StandEntity {
    public static final long SELECTION_WINDOW_TICKS = 600L;
    public static final long REQUEST_INTERVAL_TICKS = 5L;
    /**
     * The 1.16 doll toggle lived in the user's {@code LivingData} capability (saved with the player and copied on
     * death), so it survived unsummoning, relogging and respawning; the port keeps the same per-player preference
     * in {@link Player#PERSISTED_NBT_TAG} and mirrors it onto its user's disguise state.
     */
    public static final String DOLL_PREFERENCE =
            "rotp_yellowtemperance.doll_rendering";
    // StandOffsetFromUser.createDefault: 1.16's place for a Stand, behind the user's right, whichever Stand it shows.
    public static final Vec3 BESIDE_OFFSET = new Vec3(-0.75D, 0.2D, -0.75D);
    // 1.16 InitStands: new StandEntityType(YellowTemperanceEntity::new, 0.7f, 2.1f)
    private static final EntityDimensions BESIDE_BODY = EntityDimensions.scalable(0.7F, 2.1F);

    private static final EntityDataAccessor<Boolean> BLOCKING =
            SynchedEntityData.defineId(
                    YellowTemperanceControllerEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private DisguiseMode pendingMode = DisguiseMode.NONE;
    private long selectionExpiresAt = Long.MIN_VALUE;
    private long lastRequestAt = Long.MIN_VALUE;
    private boolean dollPreferenceApplied;

    public YellowTemperanceControllerEntity(
            EntityType<? extends YellowTemperanceControllerEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BLOCKING, false);
    }

    public DisguiseMode getDisguiseMode() {
        return state().mode();
    }

    public Optional<ResourceLocation> getDisguiseId() {
        return state().disguiseId();
    }

    public Optional<UUID> getDisguisePlayer() {
        return state().playerId();
    }

    public String getDisguisePlayerName() {
        return state().playerName();
    }

    /** Whether the user himself looks like something else; a fake Stand beside him does not count (1.16 isUserDisguised). */
    public boolean isDisguised() {
        return state().isDisguised();
    }

    public Optional<ResourceLocation> getBesideStand() {
        return state().besideStand();
    }

    public boolean hasStandDisguise() {
        return state().hasStandDisguise();
    }

    public boolean isDollEnabled() {
        return state().doll();
    }

    public boolean isYellowBlocking() {
        return entityData.get(BLOCKING);
    }

    private YellowTemperanceDisguiseState state() {
        return YellowTemperanceDisguiseState.of(getUser());
    }

    private void putState(YellowTemperanceDisguiseState state) {
        YellowTemperanceDisguiseState.put(getUser(), state);
    }

    public void setDollEnabled(boolean enabled) {
        if (!level().isClientSide()) {
            putState(state().withDoll(enabled));
            if (getUser() instanceof Player player) {
                CompoundTag persisted = player.getPersistentData()
                        .getCompound(Player.PERSISTED_NBT_TAG);
                persisted.putBoolean(DOLL_PREFERENCE, enabled);
                player.getPersistentData().put(
                        Player.PERSISTED_NBT_TAG, persisted);
            }
        }
    }

    private void applyStoredDollPreference(LivingEntity user) {
        dollPreferenceApplied = true;
        boolean stored = user instanceof Player player
                && player.getPersistentData()
                        .getCompound(Player.PERSISTED_NBT_TAG)
                        .getBoolean(DOLL_PREFERENCE);
        if (stored != isDollEnabled()) {
            putState(state().withDoll(stored));
        }
    }

    public void setYellowBlocking(boolean active) {
        if (!level().isClientSide()) {
            entityData.set(BLOCKING, active, true);
        }
    }

    public void beginSelection(DisguiseMode mode) {
        if (level().isClientSide() || mode == DisguiseMode.NONE) {
            return;
        }
        // 1.16 kept the Stand disguise apart from the user's: choosing one left the other on
        if (mode != DisguiseMode.STAND) {
            clearDisguise();
        }
        pendingMode = mode;
        selectionExpiresAt =
                level().getGameTime() + SELECTION_WINDOW_TICKS;
    }

    public boolean canAcceptSelection(
            DisguiseMode mode, long gameTime) {
        return !level().isClientSide()
                && mode != DisguiseMode.NONE
                && (pendingMode == mode
                        || pendingMode == DisguiseMode.STAND
                                && mode == DisguiseMode.STAND_SELF)
                && gameTime <= selectionExpiresAt;
    }

    public boolean acquireSelectionPermit(long gameTime) {
        if (lastRequestAt != Long.MIN_VALUE
                && gameTime >= lastRequestAt
                && gameTime - lastRequestAt
                        < REQUEST_INTERVAL_TICKS) {
            return false;
        }
        lastRequestAt = gameTime;
        return true;
    }

    public void applyEntityOrStandDisguise(
            DisguiseMode mode,
            ResourceLocation id,
            EntityDimensions dimensions) {
        if (level().isClientSide()
                || !canAcceptSelection(
                        mode, level().getGameTime())) {
            return;
        }
        putState(state()
                .withUserDisguise(
                        mode, Optional.of(id), Optional.empty(), "")
                .withBesideStand(Optional.empty()));
        applyScale(dimensions);
        cancelSelection();
    }

    // 1.16 StandDisguiseSetPacket: only the Stand changes; the user keeps his size and any disguise of his own.
    public void applyBesideStand(ResourceLocation id) {
        if (level().isClientSide()
                || !canAcceptSelection(
                        DisguiseMode.STAND, level().getGameTime())) {
            return;
        }
        putState(state().withBesideStand(Optional.of(id)));
        cancelSelection();
    }

    public void applyPlayerDisguise(Player target) {
        if (level().isClientSide()
                || target == null
                || !canAcceptSelection(
                        DisguiseMode.PLAYER,
                        level().getGameTime())) {
            return;
        }
        putState(state().withUserDisguise(
                DisguiseMode.PLAYER,
                Optional.empty(),
                Optional.of(target.getUUID()),
                target.getGameProfile().getName()));
        refreshUserDisplayName(); // chat shows the disguise name
        applyScale(target.getType().getDimensions());
        cancelSelection();
    }

    public void clearDisguise() {
        if (level().isClientSide()) {
            return;
        }
        LivingEntity user = getUser();
        DisguiseScaleService.reset(user);
        if (isDisguised()) {
            boolean namedAsPlayer = getDisguiseMode() == DisguiseMode.PLAYER;
            putState(state().withUserDisguise(
                    DisguiseMode.NONE,
                    Optional.empty(),
                    Optional.empty(),
                    ""));
            if (namedAsPlayer) {
                refreshUserDisplayName(); // chat shows the real name again
            }
        }
        cancelSelection();
    }

    // 1.16 clearDisguisesStand: an entity or player disguise of the user stays on.
    public void clearStandDisguise() {
        if (level().isClientSide()) {
            return;
        }
        putState(state().withBesideStand(Optional.empty()));
        if (getDisguiseMode() == DisguiseMode.STAND_SELF) {
            clearDisguise();
        }
        cancelSelection();
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
        LivingEntity user = getUser();
        if (user == null || !user.isAlive() || user.isRemoved()) {
            if (!level().isClientSide()) {
                setYellowBlocking(false);
            }
            return;
        }
        setPos(user.getX(), user.getY(), user.getZ());
        setYRot(user.getYRot());
        setXRot(user.getXRot());
        yBodyRot = user.yBodyRot;
        yBodyRotO = user.yBodyRotO;
        yHeadRot = user.yHeadRot;
        yHeadRotO = user.yHeadRotO;

        if (!level().isClientSide()) {
            StandPower power = StandPower.get(user);
            if (power == null
                    || power.getPowerType()
                            != YellowTemperanceAddon
                                    .YELLOW_TEMPERANCE.get()
                    || power.getSummonedStandEntity() != this) {
                setYellowBlocking(false);
                return;
            }
            if (!dollPreferenceApplied) {
                applyStoredDollPreference(user);
            }
            if (selectionExpiresAt != Long.MIN_VALUE
                    && level().getGameTime() > selectionExpiresAt) {
                cancelSelection();
            }
        }
    }

    // The owner's rule of 2026-11-06, over 1.16's: the disguises, the doll and the Protection go on with the Stand
    // recalled, from the user's own state. Only the block, a hold, stops.
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()) {
            setYellowBlocking(false);
            cancelSelection();
        }
        super.remove(reason);
    }

    /** Whether the fake Stand of the Stand disguise stands beside the user, where this Stand's body then is. */
    public boolean showsBesideStand() {
        LivingEntity user = getUser();
        return user != null && YellowTemperanceDisguiseState.of(user).besideStand().isPresent();
    }

    /** Where the fake Stand stands: 1.16's place for a Stand, behind the user's right, turned with his head. */
    public static Vec3 besidePosition(LivingEntity user, float partialTick) {
        Vec3 offset = StandOffsetFromUser.relativeToAbsolute(
                BESIDE_OFFSET,
                StandOffsetFromUser.Rotations.HEAD,
                user.getViewYRot(partialTick),
                user.getViewXRot(partialTick),
                user instanceof Player player
                        ? PlayerClientBroadcastedSettings.getPlayerSettings(player)
                        : Optional.empty(),
                true);
        return user.getPosition(partialTick).add(offset);
    }

    // 1.16 YellowTemperanceEntity: the Stand disguise was this Stand's own body, 0.7 by 2.1 at its place behind the
    // user, struck as any Stand is, its hits passing to the user through the health link. The entity stays at the
    // user's feet, which every range is measured from; only its box stands with the fake Stand.
    @Override
    protected AABB makeBoundingBox() {
        LivingEntity user = getUser();
        if (user != null && YellowTemperanceDisguiseState.of(user).besideStand().isPresent()) {
            return BESIDE_BODY.makeBoundingBox(besidePosition(user, 1.0F));
        }
        return super.makeBoundingBox();
    }

    // the core's move by the box would carry the entity to where the box stands
    @Override
    protected void moveWithoutCollision(Vec3 moveVec) {
        setPos(position().add(moveVec));
    }

    @Override
    public boolean isPickable() {
        return showsBesideStand() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    private void applyScale(EntityDimensions dimensions) {
        LivingEntity user = getUser();
        DisguiseScaleService.apply(user, dimensions);
        if (user instanceof Player player) {
            DisguiseRetaliationService.clearNearby(player);
        }
    }

    // Recomputes the cached display name through PlayerEvent.NameFormat.
    private void refreshUserDisplayName() {
        if (getUser() instanceof Player player) {
            player.refreshDisplayName();
        }
    }

    private void cancelSelection() {
        pendingMode = DisguiseMode.NONE;
        selectionExpiresAt = Long.MIN_VALUE;
    }

}
