package rotp.addons.sexpistols.content.revolver.item;

import java.util.List;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.sexpistols.config.SexPistolsConfig;
import rotp.addons.sexpistols.content.accessory.MistaHatItem;
import rotp.addons.sexpistols.content.revolver.RevolverRecoil;
import rotp.addons.sexpistols.content.revolver.casing.RevolverCasings;
import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.content.revolver.state.RevolverCooldowns;
import rotp.addons.sexpistols.content.revolver.state.RevolverMemberAttachments;
import rotp.addons.sexpistols.content.revolver.state.RevolverReload;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers.ShotType;
import rotp.addons.sexpistols.content.revolver.state.RevolverState;
import rotp.addons.sexpistols.content.revolver.state.RevolverState.Chamber;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsTargetMode;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsAim;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsJoyful;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsStamina;
import rotp.addons.sexpistols.network.RevolverVisualPayloads;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsDataComponents;
import rotp.addons.sexpistols.registry.SexPistolsEntities;
import rotp.addons.sexpistols.registry.SexPistolsItems;
import rotp.addons.sexpistols.registry.SexPistolsSounds;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RevolverItem extends Item {
    public static final String RELOAD_KEY_NAME =
            "key.rotp_sex_pistols.reload_revolver";
    // the core's "disable hotbars" key, Left Alt unless rebound
    public static final String OWN_HAND_KEY_NAME =
            "jojo_ripples.key.disable_hotbars";
    public static final int RESOLVE_FIRE_COOLDOWN_TICKS = 3;
    public static final int USE_DURATION_TICKS = 72_000;
    public static final float ATTACHED_FIRE_STAMINA_PER_MEMBER =
            40.0F;
    public static final float SPECIAL_FIRE_STAMINA_PER_MEMBER =
            50.0F;
    public static final float ENCIRCLEMENT_FIRE_EXTRA_STAMINA =
            60.0F;
    private static final int CLOSED_BAR_COLOR = 0xC68642;
    private static final int OPEN_BAR_COLOR = 0xE75D2F;
    private static final int LUCKY_GUNNER_COLOR = 0xE75D2F;

    private final RevolverStats stats;

    public RevolverItem(Properties properties, RevolverStats stats) {
        super(properties
                .stacksTo(1)
                .component(
                        SexPistolsDataComponents.REVOLVER_STATE.get(),
                        RevolverState.EMPTY));
        this.stats = java.util.Objects.requireNonNull(stats);
    }

    public RevolverStats stats() {
        return stats;
    }

    public static RevolverState state(ItemStack stack) {
        return stack.getOrDefault(
                SexPistolsDataComponents.REVOLVER_STATE.get(),
                RevolverState.EMPTY);
    }

    private static void setState(
            ItemStack stack,
            RevolverState state) {
        stack.set(
                SexPistolsDataComponents.REVOLVER_STATE.get(),
                state);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND
                || player.getMainHandItem() != stack) {
            return InteractionResultHolder.fail(stack);
        }
        RevolverState state = state(stack);

        if (state.reloadMode()) {
            if (!level.isClientSide()) {
                if (player.isShiftKeyDown()) {
                    extractOneChamber(
                            (ServerLevel) level,
                            player,
                            stack,
                            state);
                }
                else {
                    loadOneChamber(
                            (ServerLevel) level,
                            player,
                            stack);
                }
            }
            return InteractionResultHolder.consume(stack);
        }

        // Donor 025: with the cylinder closed the use key only aims, sneaking
        // or not. The shot is the attack key's and the cylinder its own key's.
        if (!level.isClientSide()) {
            setState(stack, state.withAiming(true));
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // Donor 025 handleServerToggleReloadMode.
    public void toggleReloadMode(
            ServerLevel level,
            Player player,
            ItemStack stack) {
        if (!isMainHandStack(player, stack)) {
            return;
        }
        RevolverState state = state(stack);
        if (state.reloadMode()) {
            closeReloadMode(level, player, stack);
        }
        else {
            openReload(level, player, stack, state);
        }
    }

    // The donor's toggle leaves the selection: a live chamber stays up for
    // Stand Reload, and a load finds the next empty chamber by itself.
    private void openReload(
            ServerLevel level,
            Player player,
            ItemStack stack,
            RevolverState state) {
        RevolverState ejected = ejectSpentCasings(
                player, stack, state.withAiming(false));
        player.stopUsingItem();
        setState(stack, ejected.withReloadMode(true));
        play(
                level,
                player,
                SexPistolsSounds.REVOLVER_OPEN.get(),
                0.85F, 1.0F);
    }

    private RevolverState ejectSpentCasings(
            Player player,
            ItemStack stack,
            RevolverState state) {
        int spent = state.spentCount();
        if (spent <= 0) {
            return state;
        }

        RevolverState ejected = state;
        for (int chamber = 0;
                chamber < RevolverState.CHAMBER_COUNT;
                chamber++) {
            if (ejected.chamber(chamber)
                    == Chamber.SPENT) {
                RevolverSpecialChambers.clearChamber(
                        stack, chamber);
                ejected = ejected.withChamber(
                        chamber, Chamber.EMPTY);
            }
        }

        if (player instanceof ServerPlayer serverPlayer) {
            RevolverCasings.eject(serverPlayer, spent);
        }
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SexPistolsSounds.REVOLVER_SHELLS_OUT.get(),
                player.getSoundSource(),
                0.75F,
                1.0F);
        return ejected;
    }

    private void extractOneChamber(
            ServerLevel level,
            Player player,
            ItemStack stack,
            RevolverState state) {
        if (!canReload(player, stack)
                || !state.reloadMode()) {
            return;
        }
        int chamber = findNextExtractableChamber(state);
        if (chamber < 0) {
            play(
                    level,
                    player,
                    SexPistolsSounds.DRY_FIRE.get(),
                    0.45F, 1.2F);
            return;
        }

        Chamber extracted = state.chamber(chamber);
        RevolverSpecialChambers.clearChamber(stack, chamber);
        RevolverState next = state
                .withSelectedChamber(chamber)
                .withChamber(chamber, Chamber.EMPTY)
                .advanceSelected(1);
        setState(stack, next);
        if (extracted == Chamber.LIVE) {
            releaseAttachedMembers(level, player, stack, chamber);
            giveAmmoItem(player);
        }
        else {
            if (player instanceof ServerPlayer serverPlayer) {
                RevolverCasings.eject(serverPlayer, 1);
            }
        }
        play(
                level,
                player,
                SexPistolsSounds.REVOLVER_SHELLS_OUT.get(),
                0.85F, 1.0F);
        applyReloadCooldown(player, stack);
    }

    private static int findNextExtractableChamber(
            RevolverState state) {
        for (int offset = 0;
                offset < RevolverState.CHAMBER_COUNT;
                offset++) {
            int chamber = Math.floorMod(
                    state.selectedChamber() + offset,
                    RevolverState.CHAMBER_COUNT);
            if (state.chamber(chamber) != Chamber.EMPTY) {
                return chamber;
            }
        }
        return -1;
    }

    private static void releaseAttachedMembers(
            ServerLevel level,
            Player player,
            ItemStack stack,
            int chamber) {
        int memberMask = RevolverMemberAttachments.detachChamber(
                stack, chamber);
        if (memberMask == 0
                || !player.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            return;
        }
        SexPistolsAttachments.coordinator(player)
                .releaseAttachedMembers(
                        level,
                        player,
                        memberMask,
                        player.position());
    }

    private static void giveAmmoItem(Player player) {
        ItemStack ammo = new ItemStack(
                SexPistolsItems.REVOLVER_AMMO.get());
        if (!player.getInventory().add(ammo)
                && !ammo.isEmpty()) {
            player.drop(ammo, false);
        }
    }

    private void loadOneChamber(
            ServerLevel level,
            Player player,
            ItemStack stack) {
        if (!canReload(player, stack)) {
            return;
        }
        // 1.16 handleServerFillChamber: a full cylinder clicks at pitch 1.6,
        // missing ammunition at 1.0.
        boolean full = state(stack).firstUnloadedChamber() < 0;
        // 0.1.13 speedloader: with the Mista hat or shoes worn one load fills every empty chamber.
        int limit = MistaHatItem.isSpeedloaderReady(player)
                ? RevolverState.CHAMBER_COUNT
                : 1;
        if (full
                || loadAvailableAmmoIntoEmptyChambers(
                        player, stack, limit) <= 0) {
            play(
                    level,
                    player,
                    SoundEvents.DISPENSER_FAIL,
                    0.45F, full ? 1.6F : 1.0F);
            return;
        }

        play(
                level,
                player,
                SexPistolsSounds.REVOLVER_SPEEDLOADER.get(),
                0.85F, 1.0F);
    }

    private static boolean consumeAmmo(Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack candidate =
                    player.getInventory().getItem(slot);
            if (candidate.is(
                    SexPistolsItems.REVOLVER_AMMO.get())) {
                candidate.shrink(1);
                return true;
            }
        }
        for (ItemStack armorStack : player.getArmorSlots()) {
            if (MistaHatItem.consumeOneAmmo(armorStack)) {
                return true;
            }
        }
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            if (MistaHatItem.consumeOneAmmo(
                    player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    public static boolean openForStandReload(
            ServerLevel level,
            Player player,
            ItemStack stack,
            boolean preserveSelectedChamber,
            boolean ejectSpentCasings) {
        if (!(stack.getItem() instanceof RevolverItem revolver)
                || !isMainHandStack(player, stack)
                || !canReload(player, stack)) {
            return false;
        }
        RevolverState state = state(stack);
        if (state.reloadMode()) {
            return true;
        }
        // Donor 025: only Stand Reload / Quick Reload eject on opening; the
        // special shots leave the casings in their chambers.
        RevolverState opened = ejectSpentCasings
                ? revolver.ejectSpentCasings(
                        player, stack, state.withAiming(false))
                : state.withAiming(false);
        int empty = opened.firstChamber(Chamber.EMPTY);
        opened = opened.withSelectedChamber(
                RevolverReload.selectionOnOpen(
                        state.selectedChamber(),
                        empty,
                        preserveSelectedChamber));
        setState(stack, opened.withReloadMode(true));
        player.stopUsingItem();
        // Donor 025 Stand actions open the cylinder at 0.85 volume, pitch 1.0.
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SexPistolsSounds.REVOLVER_OPEN.get(),
                player.getSoundSource(),
                0.85F,
                1.0F);
        return true;
    }

    public static int loadAvailableAmmoIntoEmptyChambers(
            Player player,
            ItemStack stack,
            int limit) {
        return loadAvailableAmmoIntoEmptyChambers(
                player, stack, limit, false);
    }

    public static int loadAvailableAmmoIntoEmptyChambers(
            Player player,
            ItemStack stack,
            int limit,
            boolean preserveSelectedChamber) {
        if (!(stack.getItem() instanceof RevolverItem)
                || !isMainHandStack(player, stack)
                || limit <= 0
                || !canReload(player, stack)) {
            return 0;
        }
        RevolverState loaded = state(stack);
        if (!loaded.reloadMode()) {
            return 0;
        }
        int loadedCount = 0;
        int lastChamber = loaded.selectedChamber();
        while (loadedCount < limit) {
            int chamber = loaded.firstUnloadedChamber();
            if (chamber < 0 || !consumeAmmo(player)) {
                break;
            }
            RevolverSpecialChambers.clearChamber(
                    stack, chamber);
            loaded = loaded.withChamber(chamber, Chamber.LIVE);
            loadedCount++;
            lastChamber = chamber;
        }
        if (loadedCount <= 0) {
            return 0;
        }
        int nextEmpty = loaded.firstUnloadedChamber();
        loaded = loaded.withSelectedChamber(
                RevolverReload.selectionAfterLoad(
                        loaded.selectedChamber(),
                        nextEmpty,
                        lastChamber,
                        preserveSelectedChamber));
        setState(stack, loaded);
        applyReloadCooldown(player, stack);
        return loadedCount;
    }

    public static boolean loadSelectedChamberIfEmpty(
            Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof RevolverItem)) {
            return false;
        }
        RevolverState state = state(stack);
        if (!isMainHandStack(player, stack)
                || !state.reloadMode()
                || !canReload(player, stack)) {
            return false;
        }
        int chamber = state.selectedChamber();
        if (state.chamber(chamber) == Chamber.LIVE) {
            return true;
        }
        // 1.16 loadAmmoIntoSelectedChamber loads over a spent casing as well.
        if (!consumeAmmo(player)) {
            return false;
        }
        RevolverSpecialChambers.clearChamber(stack, chamber);
        setState(
                stack,
                state.withChamber(chamber, Chamber.LIVE));
        applyReloadCooldown(player, stack);
        return true;
    }

    public static boolean closeReloadMode(
            ServerLevel level,
            Player player,
            ItemStack stack) {
        if (!(stack.getItem() instanceof RevolverItem)
                || !isMainHandStack(player, stack)) {
            return false;
        }
        RevolverState state = state(stack);
        if (!state.reloadMode()) {
            return false;
        }
        setState(stack, state.clearTransient());
        player.stopUsingItem();
        play(
                level,
                player,
                SexPistolsSounds.REVOLVER_CLOSE.get(),
                0.85F, 1.0F);
        return true;
    }

    public static boolean switchSelectedChamber(
            ServerLevel level,
            Player player,
            ItemStack stack,
            boolean forward) {
        if (!(stack.getItem() instanceof RevolverItem)) {
            return false;
        }
        if (!isMainHandStack(player, stack)) {
            return false;
        }
        RevolverState state = state(stack);
        if (!state.reloadMode()) {
            return false;
        }
        setState(
                stack,
                state.advanceSelected(forward ? 1 : -1));
        play(
                level,
                player,
                SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                0.5F, forward ? 1.15F : 0.9F);
        return true;
    }

    public static boolean clearTransientState(ItemStack stack) {
        if (!(stack.getItem() instanceof RevolverItem)) {
            return false;
        }
        RevolverState state = state(stack);
        RevolverState cleared = state.clearTransient();
        if (state.equals(cleared)) {
            return false;
        }
        setState(stack, cleared);
        return true;
    }

    @Override
    public void releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity entity,
            int remainingTicks) {
        if (level.isClientSide()) {
            return;
        }
        setState(stack, state(stack).withAiming(false));
    }

    // Donor 025 handleServerShoot: the attack key's press. An aim held on the
    // use key stays through the shot and narrows it.
    public boolean fire(
            ServerLevel serverLevel,
            Player player,
            ItemStack stack) {
        RevolverState state = state(stack);
        if (state.reloadMode()
                || shotCooldown(stack) > 0
                || !isMainHandStack(player, stack)) {
            return false;
        }

        int chamber = state.selectedChamber();
        if (state.chamber(chamber) != Chamber.LIVE) {
            RevolverSpecialChambers.clearChamber(
                    stack, chamber);
            setState(stack, state.advanceSelected(1));
            play(
                    serverLevel,
                    player,
                    SexPistolsSounds.DRY_FIRE.get(),
                    0.75F, 1.0F);
            applyShotCooldown(
                    stack, shotCooldownTicks(player, stack));
            return false;
        }

        ShotType primedType =
                RevolverSpecialChambers.modeForFiring(
                        stack, chamber);
        ShotType firedType = primedType;
        RevolverBulletEntity bullet =
                new RevolverBulletEntity(
                        SexPistolsEntities.REVOLVER_BULLET.get(),
                        player,
                        serverLevel);
        Vec3 look = player.getLookAngle();
        Vec3 muzzle = player.getEyePosition()
                .add(look.scale(0.45D));
        bullet.setPos(muzzle);
        float inaccuracy = state.aiming()
                ? stats.adsInaccuracy()
                : stats.hipInaccuracy();
        if (hasResolve(player)) {
            inaccuracy *= 0.55F;
        }
        bullet.setGunStats(stats);
        bullet.shootFromRotation(
                player,
                stats.bulletSpeed(),
                inaccuracy);
        boolean critical = rollGunCritical(player);
        bullet.setFireCritical(critical);

        int attachedMask =
                RevolverMemberAttachments.chamberMemberMask(
                        stack, chamber);
        SexPistolsCoordinator coordinator = null;
        boolean memberFlightStarted = false;
        StandPower standPower = StandPower.get(player);
        boolean hasCoordinator = player.hasData(
                SexPistolsAttachments
                        .SEX_PISTOLS_COORDINATOR);
        boolean validStand = standPower != null
                && standPower.getPowerType()
                        == SexPistolsStands.SEX_PISTOLS.get();
        if (hasCoordinator) {
            coordinator =
                    SexPistolsAttachments.coordinator(player);
            // 1.16 guided the shot from the swarm effect alone, with the
            // Stand out or put away.
            validStand &= coordinator.isSwarmEstablished();
        }
        else {
            validStand = false;
        }

        boolean canGuide = validStand
                && (attachedMask == 0
                        || coordinator.beginMemberFlight(
                                player, attachedMask));
        if (canGuide && attachedMask != 0) {
            memberFlightStarted = true;
        }
        if (validStand
                && coordinator.targetMode()
                        == SexPistolsTargetMode.AIM) {
            bullet.lockAimTarget(SexPistolsAim.findAimTarget(player));
        }
        float fireCost = firedType != ShotType.NONE
                ? Math.max(1, Integer.bitCount(attachedMask))
                        * SPECIAL_FIRE_STAMINA_PER_MEMBER
                        + (firedType == ShotType.ENCIRCLEMENT
                                ? ENCIRCLEMENT_FIRE_EXTRA_STAMINA
                                : 0.0F)
                : Integer.bitCount(attachedMask)
                        * ATTACHED_FIRE_STAMINA_PER_MEMBER;
        boolean paid = fireCost <= 0.0F
                || canGuide
                        && SexPistolsStamina.tryConsume(
                                standPower, player, fireCost);
        if (paid) {
            bullet.setAttachedMemberMask(attachedMask);
            bullet.setSpecialShotType(firedType);
        }
        else {
            if (memberFlightStarted && coordinator != null) {
                coordinator.cancelMemberFlight(
                        player, attachedMask);
                memberFlightStarted = false;
            }
            if (attachedMask != 0) {
                RevolverMemberAttachments.detachChamber(
                        stack, chamber);
                if (coordinator != null) {
                    coordinator.releaseAttachedMembers(
                            serverLevel,
                            player,
                            attachedMask,
                            player.position().add(
                                    0.0D,
                                    player.getBbHeight() * 0.65D,
                                    0.0D));
                }
                attachedMask = 0;
            }
            firedType = ShotType.NONE;
            bullet.setSpecialShotType(ShotType.NONE);
        }

        if (!serverLevel.addFreshEntity(bullet)) {
            if (memberFlightStarted && coordinator != null) {
                coordinator.cancelMemberFlight(
                        player, attachedMask);
            }
            return false;
        }
        if (memberFlightStarted) {
            RevolverMemberAttachments.detachChamber(
                    stack, chamber);
        }
        RevolverSpecialChambers.clearChamber(stack, chamber);

        setState(
                stack,
                state.withChamber(chamber, Chamber.SPENT)
                        .advanceSelected(1));
        if (stats.heavyFireSound()) {
            play(
                    serverLevel,
                    player,
                    SexPistolsSounds.REVOLVER_SHOT.get(),
                    1.15F, 0.92F);
        }
        else {
            play(
                    serverLevel,
                    player,
                    SexPistolsSounds.FIRE.get(),
                    0.95F, 1.0F);
        }
        if (firedType != ShotType.ENCIRCLEMENT) {
            RevolverRecoil.applyServer(
                    player,
                    stats,
                    state.aiming(),
                    player.getRandom().nextFloat());
        }
        RevolverVisualPayloads.sendMuzzle(
                player,
                state.aiming(),
                player.getRandom().nextFloat(),
                firedType,
                critical);
        if (validStand && attachedMask != 0) {
            JojoModUtil.sayVoiceLine(
                    player,
                    SexPistolsSounds.randomMistaLoadedFire(
                            player.getRandom()));
        }
        applyShotCooldown(
                stack, shotCooldownTicks(player, stack));
        return true;
    }

    // Donor 025 handleServerMeleeAttack, the numbers of the gun's 0.1.13
    // stats: the arm swings only when the blow lands.
    public boolean meleeAttack(
            ServerLevel level,
            Player player,
            ItemStack stack) {
        if (state(stack).reloadMode()
                || !isMainHandStack(player, stack)) {
            return false;
        }
        LivingEntity target = findMeleeTarget(player);
        if (target == null) {
            return false;
        }
        float damage = (float) (stats.meleeDamage()
                * SexPistolsConfig.meleeDamageMultiplier());
        if (!target.hurt(
                player.damageSources().playerAttack(player),
                damage)) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        target.setDeltaMovement(
                target.getDeltaMovement().add(
                        look.x * stats.meleeKnockback(),
                        0.12D,
                        look.z * stats.meleeKnockback()));
        target.hasImpulse = true;
        target.hurtMarked = true;
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(
                null,
                target.blockPosition(),
                SoundEvents.PLAYER_ATTACK_WEAK,
                SoundSource.PLAYERS,
                0.7F,
                1.15F);
        return true;
    }

    private LivingEntity findMeleeTarget(Player player) {
        double range = stats.meleeRange();
        double radius = stats.meleeTraceRadius();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 reach = eye.add(look.scale(range));
        AABB area = player.getBoundingBox()
                .expandTowards(look.scale(range))
                .inflate(radius);
        LivingEntity best = null;
        double bestDistance = range * range;
        for (LivingEntity candidate
                : player.level().getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        target -> target != player
                                && !isOwnStand(player, target)
                                && target.isAlive()
                                && !target.isSpectator()
                                && target.isPickable())) {
            Vec3 center = candidate.getBoundingBox().getCenter();
            double allowed = Math.max(
                    radius, candidate.getBbWidth() * 0.5D + radius);
            double forward = center.subtract(eye).dot(look);
            double distance = player.distanceToSqr(candidate);
            if (distanceToSegmentSqr(center, eye, reach)
                            > allowed * allowed
                    || forward < 0.0D
                    || forward > range
                    || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            best = candidate;
        }
        return best;
    }

    // Not in the donor: the user's own Stand, the nearest body, would take
    // the blow meant for what stands behind it.
    private static boolean isOwnStand(
            Player player, LivingEntity target) {
        return target instanceof StandEntity stand
                && stand.getUser() == player;
    }

    private static double distanceToSegmentSqr(
            Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0E-6D) {
            return point.distanceToSqr(start);
        }
        double along = Mth.clamp(
                point.subtract(start).dot(segment) / lengthSqr,
                0.0D,
                1.0D);
        return point.distanceToSqr(
                start.add(segment.scale(along)));
    }

    @Override
    public boolean shouldCauseReequipAnimation(
            ItemStack oldStack,
            ItemStack newStack,
            boolean slotChanged) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int itemSlot,
            boolean isSelected) {
        tickCooldowns(stack);
        if (level.isClientSide()) {
            return;
        }
        if (entity instanceof Player player) {
            int attached =
                    RevolverMemberAttachments
                            .attachedMemberMask(stack);
            if (attached != 0) {
                SexPistolsCoordinator coordinator =
                        player.hasData(
                                SexPistolsAttachments
                                        .SEX_PISTOLS_COORDINATOR)
                                ? SexPistolsAttachments
                                        .coordinator(player)
                                : null;
                int allowed = coordinator != null
                        ? coordinator.loadedMemberMask()
                                & ~coordinator
                                        .inFlightMemberMask()
                        : 0;
                RevolverMemberAttachments.retainMembers(
                        stack, attached & allowed);
            }
        }
        RevolverState state = state(stack);
        if (!state.aiming() && !state.reloadMode()) {
            return;
        }
        LivingEntity living = entity instanceof LivingEntity value
                ? value
                : null;
        boolean mainHand = living != null
                && living.getMainHandItem() == stack;
        boolean activelyUsing = mainHand
                && living.isUsingItem()
                && living.getUseItem() == stack
                && living.getUsedItemHand()
                        == InteractionHand.MAIN_HAND;
        RevolverState next = state;
        if (state.aiming() && !activelyUsing) {
            next = next.withAiming(false);
        }
        if (state.reloadMode() && !mainHand) {
            next = next.withReloadMode(false);
        }
        if (!next.equals(state)) {
            setState(stack, next);
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(
            ItemStack stack,
            LivingEntity entity) {
        return USE_DURATION_TICKS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack)
                || RevolverMemberAttachments
                        .attachedMemberMask(stack) != 0;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(
                13.0F * state(stack).liveCount()
                        / RevolverState.CHAMBER_COUNT);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return state(stack).reloadMode()
                ? OPEN_BAR_COLOR
                : CLOSED_BAR_COLOR;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        RevolverState state = state(stack);
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.revolver.loaded",
                        state.liveCount(),
                        RevolverState.CHAMBER_COUNT)
                .withStyle(ChatFormatting.GRAY));
        if (state.reloadMode()) {
            tooltip.add(Component.translatable(
                            "item.rotp_sex_pistols.revolver.reload_open")
                    .withStyle(ChatFormatting.GOLD));
        }
        int attached = Integer.bitCount(
                RevolverMemberAttachments
                        .attachedMemberMask(stack));
        if (attached > 0) {
            tooltip.add(Component.translatable(
                            "item.rotp_sex_pistols.revolver.attached",
                            attached)
                    .withStyle(ChatFormatting.AQUA));
        }
        ShotType selectedSpecial =
                RevolverSpecialChambers.modeForFiring(
                        stack, state.selectedChamber());
        if (selectedSpecial != ShotType.NONE) {
            tooltip.add(Component.translatable(
                            "rotp_sex_pistols.revolver.special."
                                    + selectedSpecial.name()
                                            .toLowerCase(
                                                    java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.revolver.control.fire",
                        Component.keybind("key.attack"))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.revolver.control.aim",
                        Component.keybind("key.use"))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.revolver.control.melee",
                        Component.keybind("key.sneak"),
                        Component.keybind("key.attack"))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.revolver.control.open",
                        Component.keybind(RELOAD_KEY_NAME))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.revolver.control.extract",
                        Component.keybind("key.sneak"),
                        Component.keybind("key.use"))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.revolver.control.load",
                        Component.keybind("key.use"))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.revolver.control.select")
                .withStyle(ChatFormatting.DARK_GRAY));
        // 1.16 (and the core) leave the use button to a Stand that is out;
        // the core's hold key takes the Stand's buttons away.
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.revolver.control.own_hand",
                        Component.keybind(OWN_HAND_KEY_NAME))
                .withStyle(ChatFormatting.DARK_GRAY));
        if (stats.criticalBonus() > 0.0F) {
            tooltip.add(Component.translatable(
                    "item.rotp_sex_pistols.suit.effect.line.simple",
                    Component.translatable(
                                    "item.rotp_sex_pistols.suit.effect.lucky_gunner")
                            .withColor(LUCKY_GUNNER_COLOR),
                    Component.translatable(
                            "item.rotp_sex_pistols.suit.effect.lucky_gunner.desc")));
        }
        if (flag.isAdvanced()) {
            tooltip.add(Component.translatable(
                            "item.rotp_sex_pistols.revolver.stats.damage",
                            format1(stats.closeDamage()),
                            format1(stats.stableDamage()),
                            format1(stats.minDistanceDamage()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(
                            "item.rotp_sex_pistols.revolver.stats.range",
                            String.format(java.util.Locale.ROOT,
                                    "%.0f", stats.closeRange()),
                            String.format(java.util.Locale.ROOT,
                                    "%.0f", stats.stableRange()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(
                            "item.rotp_sex_pistols.revolver.stats.recoil",
                            format1(stats.recoilPitch()),
                            format1(stats.recoilYaw()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.revolver.reference_quote")
                .withStyle(
                        ChatFormatting.GRAY,
                        ChatFormatting.ITALIC));
    }

    private static String format1(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    // 0.1.13 rollGunCritical: Sex Pistols' crit chance plus the gun's own.
    private boolean rollGunCritical(Player player) {
        float chance = SexPistolsJoyful.criticalChance(player)
                + stats.criticalBonus();
        return chance > 0.0F
                && player.getRandom().nextFloat()
                        < Math.min(1.0F, chance);
    }

    private static boolean hasResolve(LivingEntity entity) {
        return entity.hasEffect(ModStatusEffects.RESOLVE);
    }

    public static int shotCooldown(ItemStack stack) {
        return cooldowns(stack).shotTicks();
    }

    public static int reloadCooldown(ItemStack stack) {
        return cooldowns(stack).reloadTicks();
    }

    public static void applyReloadCooldown(
            LivingEntity entity, ItemStack stack) {
        setCooldowns(
                stack,
                cooldowns(stack).withReloadTicks(
                        hasResolve(entity)
                                ? 0
                                : RevolverStats.of(stack)
                                        .reloadCooldownTicks()));
    }

    private static boolean canReload(
            LivingEntity entity, ItemStack stack) {
        return hasResolve(entity)
                || reloadCooldown(stack) <= 0;
    }

    private static int shotCooldownTicks(
            LivingEntity entity, ItemStack stack) {
        int base = RevolverStats.of(stack).shotCooldownTicks();
        return hasResolve(entity)
                ? Math.min(base, RESOLVE_FIRE_COOLDOWN_TICKS)
                : base;
    }

    private static void applyShotCooldown(
            ItemStack stack, int ticks) {
        setCooldowns(
                stack,
                cooldowns(stack).withShotTicks(ticks));
    }

    private static void tickCooldowns(ItemStack stack) {
        RevolverCooldowns current = cooldowns(stack);
        RevolverCooldowns next = current.tick();
        if (!next.equals(current)) {
            setCooldowns(stack, next);
        }
    }

    private static RevolverCooldowns cooldowns(ItemStack stack) {
        return stack.getOrDefault(
                SexPistolsDataComponents.REVOLVER_COOLDOWNS.get(),
                RevolverCooldowns.EMPTY);
    }

    private static void setCooldowns(
            ItemStack stack,
            RevolverCooldowns cooldowns) {
        if (cooldowns.equals(RevolverCooldowns.EMPTY)) {
            stack.remove(
                    SexPistolsDataComponents
                            .REVOLVER_COOLDOWNS.get());
        }
        else {
            stack.set(
                    SexPistolsDataComponents
                            .REVOLVER_COOLDOWNS.get(),
                    cooldowns);
        }
    }

    private static boolean isMainHandStack(
            Player player, ItemStack stack) {
        return player.getMainHandItem() == stack;
    }

    // 1.16 RevolverItem played every sound from the user's block position
    // at a fixed volume and pitch.
    private static void play(
            ServerLevel level,
            Player player,
            net.minecraft.sounds.SoundEvent sound,
            float volume,
            float pitch) {
        level.playSound(
                null,
                player.blockPosition(),
                sound,
                player.getSoundSource(),
                volume,
                pitch);
    }
}
