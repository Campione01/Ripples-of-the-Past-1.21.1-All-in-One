package rotp.addons.khnum.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.khnum.KhnumAddon;
import rotp.addons.khnum.disguise.DisguiseMode;
import rotp.addons.khnum.disguise.DisguiseRetaliationService;
import rotp.addons.khnum.disguise.DisguiseScaleService;
import rotp.addons.khnum.disguise.KhnumDisguiseState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class KhnumControllerEntity extends StandEntity {
    public static final long SELECTION_WINDOW_TICKS = 600L;
    public static final long REQUEST_INTERVAL_TICKS = 5L;

    private DisguiseMode pendingMode = DisguiseMode.NONE;
    private long selectionExpiresAt = Long.MIN_VALUE;
    private long lastRequestAt = Long.MIN_VALUE;

    public KhnumControllerEntity(
            EntityType<? extends KhnumControllerEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    public DisguiseMode getDisguiseMode() {
        return KhnumDisguiseState.of(getUser()).mode();
    }

    public Optional<ResourceLocation> getDisguiseId() {
        return KhnumDisguiseState.of(getUser()).entityId();
    }

    public Optional<UUID> getDisguisePlayer() {
        return KhnumDisguiseState.of(getUser()).playerId();
    }

    public String getDisguisePlayerName() {
        return KhnumDisguiseState.of(getUser()).playerName();
    }

    public boolean isDisguised() {
        return KhnumDisguiseState.of(getUser()).isDisguised();
    }

    public void beginSelection(DisguiseMode mode) {
        if (level().isClientSide() || mode == DisguiseMode.NONE) {
            return;
        }
        clearDisguise();
        pendingMode = mode;
        selectionExpiresAt =
                level().getGameTime() + SELECTION_WINDOW_TICKS;
    }

    public boolean canAcceptSelection(
            DisguiseMode mode, long gameTime) {
        return !level().isClientSide()
                && mode != DisguiseMode.NONE
                && pendingMode == mode
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

    public void applyEntityDisguise(
            ResourceLocation id, EntityDimensions dimensions) {
        if (level().isClientSide()
                || !canAcceptSelection(
                        DisguiseMode.ENTITY,
                        level().getGameTime())) {
            return;
        }
        setState(new KhnumDisguiseState(
                DisguiseMode.ENTITY,
                Optional.of(id),
                Optional.empty(),
                ""));
        applyScale(dimensions);
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
        setState(new KhnumDisguiseState(
                DisguiseMode.PLAYER,
                Optional.empty(),
                Optional.of(target.getUUID()),
                target.getGameProfile().getName()));
        applyScale(target.getType().getDimensions());
        cancelSelection();
    }

    public void clearDisguise() {
        if (level().isClientSide()) {
            return;
        }
        KhnumDisguiseState.clear(getUser());
        cancelSelection();
    }

    public void clearForLifecycle() {
        if (level().isClientSide()) {
            return;
        }
        LivingEntity user = getUser();
        clearDisguise();
        if (user instanceof Player player) {
            DisguiseRetaliationService.clear(player);
        }
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
        LivingEntity user = getUser();
        if (user == null || !user.isAlive() || user.isRemoved()) {
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
                    || power.getPowerType() != KhnumAddon.KHNUM.get()
                    || power.getSummonedStandEntity() != this) {
                discard();
                return;
            }
            if (selectionExpiresAt != Long.MIN_VALUE
                    && level().getGameTime() > selectionExpiresAt) {
                cancelSelection();
            }
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

    private void setState(KhnumDisguiseState state) {
        LivingEntity user = getUser();
        if (user != null) {
            user.setData(KhnumAddon.DISGUISE_STATE, state);
        }
    }

    private void cancelSelection() {
        pendingMode = DisguiseMode.NONE;
        selectionExpiresAt = Long.MIN_VALUE;
    }
}
