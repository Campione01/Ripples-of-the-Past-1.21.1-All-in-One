package rotp.addons.killerqueen.btd;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.api.time.event.BitesTheDustDetonateEvent;
import rotp.core.api.time.event.RewindAppliedEvent;
import rotp.core.client.ClientProxy;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.network.BtdSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Bite the Dust of the player's pack build RotP-Killer_Queen-1.6.10 (2026-10-20, com.zeml.rotp_zkq.btd
 * BiteTheDustManager): Killer Queen is placed inside another player (the carrier). While it is inside, its user's
 * Stand is locked and his stamina drains by half a point a tick (ACTIVE). If he dies then, his death is undone and time
 * loops back (TIME_LOOP): he and the carrier go back to where they stood when it was placed, the day time with them;
 * the carrier then has as many ticks as it lasted to kill him (COUNTDOWN), or is blown up (EXPIRED). Killed by the
 * carrier in that time, he is not saved again (SAVED); the carrier dying first, or the user losing his stamina, his
 * Stand or his connection while it is inside, ends it (CANCELLED).
 *
 * <p>Additions of the port: a time stop holds the clock of a frozen player (the user's while it is inside, the
 * carrier's in the countdown; rule T3-07), the loop posts the core's rewind event and re-pins frozen players it moves
 * (rules G1, G2), and the blast on the carrier asks the core's detonation event first (owner Q3).
 */
@EventBusSubscriber(modid = KillerQueenAddon.MOD_ID)
public final class BiteTheDustManager {
    public static final float STAMINA_DRAIN_PER_TICK = 0.5F;
    public static final double MAX_INSERT_DISTANCE = 12.0D;
    public static final int MIN_COUNTDOWN_TICKS = 1;
    public static final ResourceKey<DamageType> BITE_THE_DUST_DAMAGE =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    KillerQueenAddon.id("bite_the_dust"));
    public static final ResourceLocation REWIND_SOURCE =
            KillerQueenAddon.id("bite_the_dust");

    private BiteTheDustManager() {}

    public static boolean isStandLocked(@Nullable LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.level().isClientSide()) {
            return entity == ClientProxy.getClientPlayer()
                    && BtdClientState.isOwnerStandLocked();
        }
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return false;
        }
        BtdInstance inst = BtdSavedData.get(server)
                .getByOwner(entity.getUUID());
        return inst != null && inst.state.locksOwnerStand();
    }

    public static boolean isOwnerBusy(LivingEntity owner) {
        return !owner.level().isClientSide()
                ? isStandLocked(owner)
                : owner == ClientProxy.getClientPlayer()
                        && BtdClientState.isOwner()
                        && BtdClientState.getState().locksOwnerStand();
    }

    /** The refusal's condition key, or null when the user may place it in the target. */
    @Nullable
    public static String checkInsert(ServerPlayer owner, LivingEntity target) {
        if (!(target instanceof ServerPlayer)
                || target == owner
                || !target.isAlive()
                || target.isSpectator()
                || !owner.isAlive()) {
            return "btd_invalid_target";
        }
        if (owner.level() != target.level()) {
            return "btd_other_dimension";
        }
        if (owner.distanceToSqr(target)
                > MAX_INSERT_DISTANCE * MAX_INSERT_DISTANCE) {
            return "btd_too_far";
        }
        BtdSavedData data = BtdSavedData.get(owner.server);
        if (data.getByOwner(owner.getUUID()) != null) {
            return "btd_already_active";
        }
        return data.getByCarrier(target.getUUID()) != null
                ? "btd_target_busy"
                : null;
    }

    public static boolean activate(
            ServerPlayer owner, ServerPlayer carrier, StandPower power) {
        if (checkInsert(owner, carrier) != null) {
            return false;
        }
        MinecraftServer server = owner.server;
        BtdSavedData data = BtdSavedData.get(server);
        BtdInstance inst = new BtdInstance(owner.getUUID(), carrier.getUUID());
        inst.ownerSnapshot = BtdInstance.Snapshot.of(owner);
        inst.carrierSnapshot = BtdInstance.Snapshot.of(carrier);
        inst.dayTime = server.overworld().getDayTime();
        inst.gameTime = owner.serverLevel().getGameTime();
        inst.standWasSummoned = power.getSummonedStandEntity() != null;
        data.add(inst);
        lockOwnerStand(owner, power);
        sync(inst, owner, carrier);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        BtdSavedData data = BtdSavedData.get(server);
        if (!data.getPendingKills().isEmpty()) {
            for (UUID uuid : new ArrayList<>(data.getPendingKills())) {
                ServerPlayer carrier =
                        server.getPlayerList().getPlayer(uuid);
                if (carrier != null && carrier.isAlive()) {
                    data.getPendingKills().remove(uuid);
                    data.setDirty();
                    detonate(server, null, carrier);
                }
            }
        }
        for (BtdInstance inst : data.all()) {
            ServerPlayer owner =
                    server.getPlayerList().getPlayer(inst.owner);
            ServerPlayer carrier =
                    server.getPlayerList().getPlayer(inst.carrier);
            switch (inst.state) {
                case ACTIVE -> tickActive(data, inst, owner, carrier);
                case TIME_LOOP -> tickTimeLoop(server, data, inst, owner, carrier);
                case COUNTDOWN -> tickCountdown(data, inst, owner, carrier);
                default -> data.remove(inst);
            }
        }
    }

    private static void tickActive(
            BtdSavedData data,
            BtdInstance inst,
            @Nullable ServerPlayer owner,
            @Nullable ServerPlayer carrier) {
        if (owner == null || carrier == null
                || !owner.isAlive() || !carrier.isAlive()) {
            finish(data, inst, BtdState.CANCELLED, owner, carrier);
            return;
        }
        StandPower power = StandPower.get(owner);
        if (power == null
                || power.getPowerType() != KillerQueenAddon.KILLER_QUEEN.get()) {
            finish(data, inst, BtdState.CANCELLED, owner, carrier);
            return;
        }
        lockOwnerStand(owner, power);
        // its user frozen in a time stop, the time it lasted (and drained) waits
        if (TimeStopState.shouldFreezeOnServer(owner)) {
            return;
        }
        if (!power.usesStamina()
                || power.isStaminaInfinite()
                || power.getStamina() > 0.0F
                        && power.consumeStamina(STAMINA_DRAIN_PER_TICK, true)) {
            inst.activeTicks++;
            data.setDirty();
        }
        else {
            finish(data, inst, BtdState.CANCELLED, owner, carrier);
        }
    }

    private static void tickTimeLoop(
            MinecraftServer server,
            BtdSavedData data,
            BtdInstance inst,
            @Nullable ServerPlayer owner,
            @Nullable ServerPlayer carrier) {
        rollback(server, inst, owner, carrier);
        inst.countdownTicks = Math.max(MIN_COUNTDOWN_TICKS, inst.activeTicks);
        inst.state = BtdState.COUNTDOWN;
        data.setDirty();
        sync(inst, owner, carrier);
    }

    private static void tickCountdown(
            BtdSavedData data,
            BtdInstance inst,
            @Nullable ServerPlayer owner,
            @Nullable ServerPlayer carrier) {
        if (owner != null) {
            StandPower power = StandPower.get(owner);
            if (power != null) {
                lockOwnerStand(owner, power);
            }
        }
        // the bomb is part of the world a time stop holds: a frozen carrier's time is not running out
        if (carrier != null && TimeStopState.shouldFreezeOnServer(carrier)) {
            return;
        }
        if (--inst.countdownTicks <= 0) {
            finish(data, inst, BtdState.EXPIRED, owner, carrier);
        }
        else {
            data.setDirty();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead)) {
            return;
        }
        BtdSavedData data = BtdSavedData.get(dead.server);
        BtdInstance asOwner = data.getByOwner(dead.getUUID());
        if (asOwner != null) {
            switch (asOwner.state) {
                case ACTIVE -> {
                    ServerPlayer loopCarrier = dead.server.getPlayerList()
                            .getPlayer(asOwner.carrier);
                    // owner Q3: asked before the loop starts. Held, nothing loops or detonates and the instance
                    // stays active; defused (a summoned Requiem on the carrier), it ends for good and the death
                    // stands
                    BitesTheDustDetonateEvent.Outcome outcome = loopCarrier != null
                            ? BitesTheDustDetonateEvent.postOutcome(
                                    dead, loopCarrier,
                                    event.getSource().getEntity())
                            : BitesTheDustDetonateEvent.Outcome.DETONATE;
                    if (outcome == BitesTheDustDetonateEvent.Outcome.HELD) {
                        if (isKilledBy(event.getSource(), asOwner.carrier)) {
                            cancelDeath(event, dead);
                        }
                        return;
                    }
                    if (outcome == BitesTheDustDetonateEvent.Outcome.DEFUSED) {
                        finish(data, asOwner, BtdState.CANCELLED, dead, loopCarrier);
                        return;
                    }
                    asOwner.state = BtdState.TIME_LOOP;
                    data.setDirty();
                    cancelDeath(event, dead);
                    explosionEffect(dead);
                    showTitle(dead);
                    if (loopCarrier != null) {
                        showTitle(loopCarrier);
                    }
                }
                case TIME_LOOP -> cancelDeath(event, dead);
                case COUNTDOWN -> {
                    if (isKilledBy(event.getSource(), asOwner.carrier)) {
                        ServerPlayer carrier = dead.server.getPlayerList()
                                .getPlayer(asOwner.carrier);
                        finish(data, asOwner, BtdState.SAVED, dead, carrier);
                    }
                }
                default -> {}
            }
            return;
        }
        BtdInstance asCarrier = data.getByCarrier(dead.getUUID());
        if (asCarrier != null) {
            ServerPlayer owner = dead.server.getPlayerList()
                    .getPlayer(asCarrier.owner);
            finish(data, asCarrier, BtdState.CANCELLED, owner, dead);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BtdSavedData data = BtdSavedData.get(player.server);
        BtdInstance asOwner = data.getByOwner(player.getUUID());
        BtdInstance asCarrier = data.getByCarrier(player.getUUID());
        if (asOwner != null) {
            send(player, asOwner.state, asOwner.countdownTicks, true);
        }
        else if (asCarrier != null) {
            send(player, asCarrier.state, asCarrier.countdownTicks, false);
        }
        else {
            send(player, BtdState.INACTIVE, 0, false);
        }
    }

    private static void cancelDeath(LivingDeathEvent event, ServerPlayer player) {
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
    }

    private static boolean isKilledBy(DamageSource source, UUID carrier) {
        for (Entity entity : new Entity[] {
                source.getEntity(), source.getDirectEntity() }) {
            if (entity == null) {
                continue;
            }
            if (entity.getUUID().equals(carrier)) {
                return true;
            }
            if (entity instanceof StandEntity stand) {
                LivingEntity user = stand.getUser();
                if (user != null && user.getUUID().equals(carrier)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void finish(
            BtdSavedData data,
            BtdInstance inst,
            BtdState result,
            @Nullable ServerPlayer owner,
            @Nullable ServerPlayer carrier) {
        inst.state = result;
        data.remove(inst);
        if (owner != null) {
            send(owner, BtdState.INACTIVE, 0, true);
        }
        if (carrier != null) {
            send(carrier, BtdState.INACTIVE, 0, false);
        }
        returnStand(inst, owner);
        if (result == BtdState.EXPIRED) {
            if (carrier != null && carrier.isAlive()) {
                detonate(carrier.server, owner, carrier);
            }
            else if (carrier == null) {
                data.getPendingKills().add(inst.carrier);
                data.setDirty();
            }
        }
    }

    // owner Q3: a summoned Gold Experience Requiem returns the blast on its user to zero (no blast, he lives); held
    // or defused, the instance is already gone, so both end here
    private static void detonate(
            MinecraftServer server,
            @Nullable ServerPlayer owner,
            ServerPlayer carrier) {
        // the core event has no ownerless form; an absent user is stood in for by the carrier
        LivingEntity eventOwner = owner != null ? owner : carrier;
        if (!BitesTheDustDetonateEvent.post(eventOwner, carrier, null)) {
            return;
        }
        explosionEffect(carrier);
        showTitle(carrier);
        kill(carrier);
    }

    private static void returnStand(BtdInstance inst, @Nullable ServerPlayer owner) {
        if (owner == null || !owner.isAlive() || !inst.standWasSummoned) {
            return;
        }
        StandPower power = StandPower.get(owner);
        if (power != null
                && power.getPowerType() == KillerQueenAddon.KILLER_QUEEN.get()
                && !power.isSummoned()) {
            power.getPowerType().summon(owner, power);
        }
    }

    private static void explosionEffect(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                KillerQueenAddon.BITES_THE_DUST.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void showTitle(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("rotp_zkq.btd.title")
                        .withStyle(ChatFormatting.DARK_RED)));
    }

    private static void kill(ServerPlayer player) {
        DamageSource source = player.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(BITE_THE_DUST_DAMAGE)
                .<DamageSource>map(DamageSource::new)
                .orElseGet(() -> player.damageSources().genericKill());
        player.hurt(source, Float.MAX_VALUE);
        if (player.isAlive()) {
            player.setHealth(0.0F);
            player.die(source);
        }
    }

    private static void lockOwnerStand(ServerPlayer owner, StandPower power) {
        if (power.getPowerType() == KillerQueenAddon.KILLER_QUEEN.get()
                && power.getSummonedStandEntity() != null) {
            power.getPowerType().forceUnsummon(owner, power);
        }
    }

    private static void rollback(
            MinecraftServer server,
            BtdInstance inst,
            @Nullable ServerPlayer owner,
            @Nullable ServerPlayer carrier) {
        if (owner != null && inst.ownerSnapshot != null) {
            restore(server, inst, owner, inst.ownerSnapshot);
        }
        if (carrier != null && inst.carrierSnapshot != null) {
            restore(server, inst, carrier, inst.carrierSnapshot);
        }
        server.overworld().setDayTime(inst.dayTime);
    }

    private static void restore(
            MinecraftServer server,
            BtdInstance inst,
            ServerPlayer player,
            BtdInstance.Snapshot snap) {
        ServerLevel from = player.serverLevel();
        AABB before = player.getBoundingBox();
        ResourceLocation dimension = ResourceLocation.tryParse(snap.dimension());
        ServerLevel level = dimension != null
                ? server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension))
                : null;
        if (level == null) {
            level = from;
        }
        player.teleportTo(level, snap.x(), snap.y(), snap.z(), Set.of(),
                snap.yRot(), snap.xRot());
        // rule G2: a frozen player put back stays where the loop put him
        TimeStopState.repinOrReleaseOnServer(player);
        player.setHealth(Math.max(1.0F, Math.min(snap.health(), player.getMaxHealth())));
        player.getFoodData().setFoodLevel(snap.food());
        player.clearFire();
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        // rule G1: the time abilities begun after the placing where he stood and where he is put end
        RewindAppliedEvent.post(server.getPlayerList().getPlayer(inst.owner),
                from, inst.gameTime, before, REWIND_SOURCE);
        RewindAppliedEvent.post(server.getPlayerList().getPlayer(inst.owner),
                player.serverLevel(), inst.gameTime, player.getBoundingBox(), REWIND_SOURCE);
    }

    private static void sync(
            BtdInstance inst,
            @Nullable ServerPlayer owner,
            @Nullable ServerPlayer carrier) {
        if (owner != null) {
            send(owner, inst.state, inst.countdownTicks, true);
        }
        if (carrier != null) {
            send(carrier, inst.state, inst.countdownTicks, false);
        }
    }

    private static void send(ServerPlayer player, BtdState state, int ticks, boolean owner) {
        PacketDistributor.sendToPlayer(player, new BtdSyncPayload(state, ticks, owner));
    }
}
