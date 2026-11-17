package rotp.addons.ger.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import rotp.core.api.time.EntityTimeRates;
import rotp.core.api.time.LevelTimeRates;
import rotp.core.api.time.event.RewindAppliedEvent;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.SummonedStand;
import rotp.core.subsystems.timestop.TimeStopState;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.ability.GerReturnToZeroAbility;
import rotp.addons.ger.ability.GerReturnToZeroMarkAbility;
import rotp.addons.ger.entity.GerStandEntity;
import rotp.addons.ger.network.GerRtzPayloads;
import rotp.addons.ger.network.GerRtzPayloads.GhostTrail;
import rotp.addons.ger.network.GerRtzPayloads.MarkStatePayload;
import rotp.addons.ger.network.GerRtzPayloads.StartPayload;
import rotp.addons.ger.network.GerRtzPayloads.StopPayload;
import rotp.addons.ger.state.GerRtzTimeStopPolicy.GenerationTracker;
import rotp.addons.ger.state.GerRtzTimeStopPolicy.InstanceKey;
import rotp.addons.ger.state.GerRtzTimeStopPolicy.InstanceRegion;
import javax.annotation.Nullable;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.state.KingCrimsonCreatureErase;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import org.slf4j.Logger;

/**
 * Transient, module-owned Return to Zero history with a staged atomic commit
 * and an inverse journal. Other time abilities answer it through the core's
 * time events and the time stop's re-pin, not through shared state.
 */
public final class GerRtzRewindService {
    public static final int MAX_BLOCK_OPS = 200_000;
    public static final int ENTITY_SAMPLE_INTERVAL_TICKS = 10;
    public static final int MAX_ENTITY_FRAMES = 1_200;
    public static final int MAX_GHOST_POINTS_PER_ENTITY = 120;
    public static final double MIN_GHOST_DISTANCE = 0.75D;
    public static final int END_SOUND_MIN_DURATION_TICKS = 200;
    public static final int VFX_FADE_IN_TICKS = 8;
    public static final int VFX_FADE_OUT_TICKS = 15;
    public static final int VFX_EXPAND_TICKS = 4;
    public static final int VFX_CLOSE_TICKS = 0;
    public static final int VFX_MODE_NORMAL = 0;
    public static final int VFX_MODE_TIME_ERASE = 1;
    public static final int AUTOMATIC_RTZ_DELAY_TICKS = 1;
    public static final int ENEMY_AI_PAUSE_TICKS = 10;
    public static final ResourceLocation RETURN_TO_ZERO_SOURCE =
            GoldExperienceRequiemAddon.id("return_to_zero");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KING_CRIMSON_MOD_ID =
            "rotp_kingcrimson";
    private static final int BLOCK_UPDATE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS;
    private static final Map<MinecraftServer, ServerHistory> HISTORIES =
            new WeakHashMap<>();
    private static final ThreadLocal<Integer> APPLY_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    /** Whether any server has a mark: with none, no block change is captured. */
    private static volatile boolean anyMark;
    /** Block preimages captured since start, read by the game tests. */
    private static long blockCaptures;

    private GerRtzRewindService() {}

    public enum Result {
        SUCCESS(""),
        FORCE_CLOSED(""),
        PREDICTION_BROKEN(""),
        WRONG_DIMENSION(
                "geraddon.return_to_zero.wrong_dimension"),
        NO_MARK("geraddon.return_to_zero.no_mark"),
        NOT_ENOUGH_STAMINA("not_enough_stamina"),
        UNLOADED_TARGET(
                "geraddon.return_to_zero.unloaded_target"),
        UNSAFE_HISTORY(
                "geraddon.return_to_zero.unsafe_history"),
        COMMIT_FAILED(
                "geraddon.return_to_zero.commit_failed");

        private final String translationKey;

        Result(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public record BlockPreimage(
            long tick,
            BlockPos position,
            CompoundTag state,
            CompoundTag blockEntity,
            boolean restorable) {
        private BlockPreimage copy() {
            return new BlockPreimage(
                    tick,
                    position.immutable(),
                    state.copy(),
                    blockEntity != null
                            ? blockEntity.copy()
                            : null,
                    restorable);
        }
    }

    public static Result setMark(
            ServerLevel level,
            ServerPlayer owner,
            int chunkRadius) {
        if (owner.level() != level) {
            return Result.WRONG_DIMENSION;
        }
        ServerHistory history = history(level.getServer());
        Mark mark;
        try {
            mark = Mark.capture(
                    level,
                    owner,
                    Math.max(0, chunkRadius),
                    history);
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "Could not capture GER Return to Zero mark",
                    exception);
            return Result.UNSAFE_HISTORY;
        }
        history.pendingAutomaticRtz.remove(owner.getUUID());
        history.marks.put(owner.getUUID(), mark);
        refreshAnyMark();
        syncMark(owner, true);
        return Result.SUCCESS;
    }

    public static boolean hasMark(
            ServerPlayer owner) {
        Mark mark = history(owner.server)
                .marks.get(owner.getUUID());
        return mark != null
                && mark.dimension().equals(
                        owner.serverLevel().dimension());
    }

    public static boolean hasActivePresentation(
            ServerPlayer owner) {
        return history(owner.server)
                .presentations
                .containsKey(owner.getUUID());
    }

    /**
     * Owner Q4 (row T3-15): a summoned Requiem returns to zero, mark or no
     * mark, an erase that starts on its user or inside the region its Return
     * to Zero takes (the chunks within MARK_CHUNK_RADIUS of the user's chunk).
     */
    public static boolean returnsTimeEraseToZero(
            ServerLevel level,
            LivingEntity eraser,
            Vec3 position) {
        return summonedRequiemWithin(
                level,
                eraser,
                position,
                GerReturnToZeroMarkAbility.MARK_CHUNK_RADIUS);
    }

    /**
     * The owner, 2026-11-08: an eraser who comes within 4 chunks of a living
     * user with a summoned Requiem in a running erase is thrown out of it,
     * where he stands, with Requiem's gold on him ("直接被強制退出時刪並顯示
     * 黃鎮的歸零特效"). For the handler of the core's event of a running
     * erase.
     *
     * @return the user whose Requiem throws him out, or null
     */
    @Nullable
    public static LivingEntity throwsEraserOut(
            ServerLevel level, LivingEntity eraser, Vec3 position) {
        LivingEntity holder = summonedRequiemUserWithin(
                level, eraser, position, GerCoreSeams.ERASE_NEAR_CHUNKS);
        if (holder != null) {
            GerHitAnswerService.showOn(eraser);
        }
        return holder;
    }

    public static boolean summonedRequiemWithin(
            ServerLevel level,
            LivingEntity eraser,
            Vec3 position,
            int chunkRadius) {
        return summonedRequiemUserWithin(
                level, eraser, position, chunkRadius) != null;
    }

    /** Chunk distance from the real place of a living user whose Requiem is out; never the eraser's own. */
    @Nullable
    private static LivingEntity summonedRequiemUserWithin(
            ServerLevel level,
            LivingEntity eraser,
            Vec3 position,
            int chunkRadius) {
        ChunkPos erased = new ChunkPos(BlockPos.containing(position));
        for (GerStandEntity stand : level.getEntities(
                EntityTypeTest.forClass(GerStandEntity.class),
                stand -> true)) {
            LivingEntity user = stand.getUser();
            if (user == null
                    || user == eraser
                    || !user.isAlive()
                    || user.level() != level
                    || GoldExperienceRequiemAddon.activeStand(user)
                            != stand) {
                continue;
            }
            ChunkPos home = user.chunkPosition();
            if (Math.abs(erased.x - home.x) <= chunkRadius
                    && Math.abs(erased.z - home.z) <= chunkRadius) {
                return user;
            }
        }
        return null;
    }

    /**
     * Owner Q3 (row T3-29): a summoned Requiem keeps Bites the Dust from
     * detonating on its user, mark or no mark.
     */
    public static boolean returnsDetonationToZero(Entity target) {
        return target instanceof LivingEntity user
                && user.isAlive()
                && GoldExperienceRequiemAddon.activeStand(user) != null;
    }

    private static AABB regionAround(
            Level level,
            int centerChunkX,
            int centerChunkZ,
            int chunkRadius) {
        return new AABB(
                (centerChunkX - chunkRadius) * 16.0D,
                level.getMinBuildHeight(),
                (centerChunkZ - chunkRadius) * 16.0D,
                (centerChunkX + chunkRadius + 1) * 16.0D,
                level.getMaxBuildHeight(),
                (centerChunkZ + chunkRadius + 1) * 16.0D);
    }

    public static void timeStopAdded(
            ServerLevel level,
            TimeStopState.Instance instance) {
        if (!instance.isActive()) {
            return;
        }
        ServerHistory history = history(level.getServer());
        InstanceKey key = registerTimeStopAdded(
                history, level, instance);
        InstanceRegion region = new InstanceRegion(
                key,
                instance.centerPos().x,
                instance.centerPos().z,
                instance.chunkRange());
        history.marks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(UUID::toString)))
                .forEach(entry -> {
                    UUID owner = entry.getKey();
                    Mark mark = entry.getValue();
                    if (mark.dimension().equals(level.dimension())
                            && GerRtzTimeStopPolicy.shouldTerminate(
                                    mark.timeStopInstances(),
                                    mark.centerChunkX(),
                                    mark.centerChunkZ(),
                                    mark.chunkRadius(),
                                    region)) {
                        history.pendingAutomaticRtz.putIfAbsent(
                                owner,
                                new PendingAutomaticRtz(
                                        level.dimension(),
                                        (long) level.getServer()
                                                .getTickCount()
                                                + AUTOMATIC_RTZ_DELAY_TICKS,
                                        mark));
                    }
                });
    }

    public static void timeStopRemoved(
            ServerLevel level,
            TimeStopState.Instance instance) {
        ServerHistory history = history(level.getServer());
        boolean replacementActive = level.hasData(
                        ModDataAttachmentTypes.TIME_STOP.get())
                && level.getData(ModDataAttachmentTypes.TIME_STOP.get())
                        .getInstance(instance.id())
                        .isPresent();
        GenerationTracker<TimeStopState.Instance> active =
                history.activeTimeStops.get(level.dimension());
        if (active == null) {
            return;
        }
        active.removed(instance.id(), replacementActive);
        if (active.isEmpty()) {
            history.activeTimeStops.remove(level.dimension());
        }
    }

    public static Result rewind(
            ServerLevel level,
            ServerPlayer owner,
            LivingEntity performer,
            StandPower power,
            float staminaCost) {
        ServerHistory history = history(level.getServer());
        if (history.presentations.containsKey(owner.getUUID())) {
            closePresentation(
                    level.getServer(), history, owner.getUUID(), true);
            return Result.FORCE_CLOSED;
        }
        Mark mark = history.marks.get(owner.getUUID());
        if (mark == null) {
            syncMark(owner, false);
            return Result.NO_MARK;
        }
        if (owner.level() != level
                || !mark.dimension().equals(level.dimension())) {
            return Result.WRONG_DIMENSION;
        }
        Set<UUID> activeTimeEraseUsers =
                captureActiveTimeEraseUsers(level);

        StageResult staged;
        try {
            staged = stage(
                    level, owner, performer, mark);
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "Could not stage GER Return to Zero",
                    exception);
            return Result.UNSAFE_HISTORY;
        }
        if (staged.failure() != null) {
            return staged.failure();
        }
        RewindPlan plan = staged.plan();
        float inverseStamina =
                power != null ? power.getStamina() : 0.0F;
        if (power == null
                || !power.consumeStamina(staminaCost)) {
            if (power != null) {
                power.setStamina(inverseStamina);
            }
            return Result.NOT_ENOUGH_STAMINA;
        }
        if (!commit(level, plan)) {
            power.setStamina(inverseStamina);
            return Result.COMMIT_FAILED;
        }

        pauseEnemies(plan);
        terminatePostMarkTimeStops(history, level, mark);
        terminateTimeStopsHolding(level, owner);
        // Rule G1 (A2-EVENTS): the core ends the stops and the erase add-on
        // the erases that began after the mark inside its region (D6).
        RewindAppliedEvent.post(
                owner,
                level,
                mark.tick(),
                mark.region(level),
                RETURN_TO_ZERO_SOURCE);
        boolean timeEraseReturned =
                anyTimeEraseEnded(level, activeTimeEraseUsers);
        // Rule G1 (T3-15): an erase the event left running began before the
        // mark and goes on, but the lockdown would end its held action.
        Set<UUID> erasingAfterRewind = captureActiveTimeEraseUsers(level);
        history.marks.remove(owner.getUUID());
        refreshAnyMark();
        history.pendingAutomaticRtz.remove(owner.getUUID());
        syncMark(owner, false);
        int elapsed = (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(1L,
                        level.getGameTime() - mark.tick()));
        int keepTicks = Math.max(
                1,
                elapsed
                        - VFX_FADE_IN_TICKS
                        - VFX_FADE_OUT_TICKS);
        beginPresentation(
                history,
                level,
                owner,
                performer,
                mark,
                plan,
                keepTicks,
                timeEraseReturned
                        ? VFX_MODE_TIME_ERASE
                        : VFX_MODE_NORMAL,
                erasingAfterRewind);
        if (elapsed > END_SOUND_MIN_DURATION_TICKS) {
            history.pendingEndSounds.put(
                    owner.getUUID(),
                    new PendingEndSound(
                            level.dimension(),
                            level.getGameTime()
                                    + END_SOUND_MIN_DURATION_TICKS,
                            Vec3.atCenterOf(mark.center()),
                            owner.getUUID()));
        }
        return Result.SUCCESS;
    }

    // The owner, 2026-11-08: "只在歸零瞬間把敵對生物ai暫停0.5s". Their AI only, and not the lockdown's length.
    private static void pauseEnemies(RewindPlan plan) {
        for (EntityMutation mutation : plan.entities()) {
            if (mutation.side() == Side.ENEMY
                    && mutation.live() instanceof Mob mob
                    && mob.isAlive()) {
                GerCoreSeams.pauseAi(mob, ENEMY_AI_PAUSE_TICKS);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        ServerHistory history = history(server);
        processAutomaticRtz(server, history);
        for (Mark mark : List.copyOf(history.marks.values())) {
            ServerLevel level =
                    server.getLevel(mark.dimension());
            if (level == null) {
                continue;
            }
            long now = level.getGameTime();
            if (now % ENTITY_SAMPLE_INTERVAL_TICKS == 0L) {
                mark.capturePoseFrame(level, now);
            }
        }

        List<UUID> completed = new ArrayList<>();
        for (Map.Entry<UUID, Presentation> entry :
                history.presentations.entrySet()) {
            Presentation presentation = entry.getValue();
            ServerLevel level =
                    server.getLevel(presentation.dimension());
            if (level == null
                    || level.getGameTime()
                            >= presentation.endTick()) {
                completed.add(entry.getKey());
            }
        }
        for (UUID owner : completed) {
            closePresentation(server, history, owner, true);
        }

        List<UUID> dueSounds = new ArrayList<>();
        for (Map.Entry<UUID, PendingEndSound> entry :
                history.pendingEndSounds.entrySet()) {
            PendingEndSound pending = entry.getValue();
            ServerLevel level =
                    server.getLevel(pending.dimension());
            if (level != null
                    && level.getGameTime()
                            >= pending.playTick()) {
                playEndSound(level, pending);
                dueSounds.add(entry.getKey());
            }
        }
        dueSounds.forEach(history.pendingEndSounds::remove);
    }

    private static void processAutomaticRtz(
            MinecraftServer server,
            ServerHistory history) {
        List<UUID> owners = new ArrayList<>(
                history.pendingAutomaticRtz.keySet());
        owners.sort(Comparator.comparing(UUID::toString));
        for (UUID ownerId : owners) {
            PendingAutomaticRtz pending =
                    history.pendingAutomaticRtz.get(ownerId);
            if (pending == null) {
                continue;
            }
            ServerLevel level = server.getLevel(
                    pending.dimension());
            if (level == null) {
                history.pendingAutomaticRtz.remove(
                        ownerId, pending);
                continue;
            }
            if ((long) server.getTickCount()
                    < pending.executeServerTick()) {
                continue;
            }
            // Consume first so rewind lifecycle hooks cannot re-enter it.
            if (!history.pendingAutomaticRtz.remove(
                    ownerId, pending)) {
                continue;
            }
            try {
                tryAutomaticRtz(
                        server,
                        history,
                        level,
                        ownerId,
                        pending);
            }
            catch (RuntimeException exception) {
                LOGGER.error(
                        "Could not execute the automatic GER "
                                + "Return to Zero time-stop response",
                        exception);
            }
        }
    }

    private static void tryAutomaticRtz(
            MinecraftServer server,
            ServerHistory history,
            ServerLevel level,
            UUID ownerId,
            PendingAutomaticRtz pending) {
        Mark mark = pending.mark();
        if (history.marks.get(ownerId) != mark
                || !mark.dimension().equals(level.dimension())
                || level.getGameTime() <= mark.tick()) {
            return;
        }
        ServerPlayer owner = server.getPlayerList()
                .getPlayer(ownerId);
        if (owner == null
                || owner.serverLevel() != level
                || !owner.isAlive()
                || !level.hasData(
                        ModDataAttachmentTypes.TIME_STOP.get())) {
            return;
        }
        TimeStopState state = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        if (!state.shouldFreeze(owner)
                || !hasPostMarkIntersectingTimeStop(
                        history, level, mark, state)) {
            return;
        }
        StandPower power = StandPower.get(owner);
        if (power == null || power.getMoveset() == null) {
            return;
        }
        Ability configured = power.getMoveset().getAbility(
                GoldExperienceRequiemAddon
                        .GER_RETURN_TO_ZERO_NAME);
        if (configured instanceof GerReturnToZeroAbility rtz) {
            rtz.tryAutomaticTimeStopResponse(
                    level, owner, power);
        }
    }

    public static void clear(MinecraftServer server) {
        synchronized (HISTORIES) {
            HISTORIES.remove(server);
        }
        refreshAnyMark();
    }

    private static void refreshAnyMark() {
        synchronized (HISTORIES) {
            boolean any = false;
            for (ServerHistory history : HISTORIES.values()) {
                if (!history.marks.isEmpty()) {
                    any = true;
                    break;
                }
            }
            anyMark = any;
        }
    }

    /** Whether a block change at this place would be journalled: only inside a mark of its dimension. */
    public static boolean journals(ServerLevel level, BlockPos position) {
        return journalling(level, position) != null;
    }

    /** The server's history when a mark of the level's dimension covers the place, else null. */
    @Nullable
    private static ServerHistory journalling(
            ServerLevel level, BlockPos position) {
        if (!anyMark) {
            return null;
        }
        ServerHistory history;
        synchronized (HISTORIES) {
            history = HISTORIES.get(level.getServer());
        }
        if (history == null) {
            return null;
        }
        for (Mark mark : history.marks.values()) {
            if (mark.dimension().equals(level.dimension())
                    && mark.contains(position)) {
                return history;
            }
        }
        return null;
    }

    public static void syncPlayer(ServerPlayer player) {
        ServerHistory history = history(player.server);
        Mark mark = history.marks.get(player.getUUID());
        boolean available = mark != null
                && mark.dimension().equals(
                        player.serverLevel().dimension());
        syncMark(player, available);
        GoldExperienceRequiemAddon.playerState(player)
                .setReturnToZeroPresentationActive(
                        history.presentations.containsKey(
                                player.getUUID()));

        for (Presentation presentation :
                history.presentations.values()) {
            if (!presentation.dimension().equals(
                            player.serverLevel().dimension())) {
                continue;
            }
            StartPayload payload = presentation.payload();
            int elapsed = (int) Math.min(
                    Integer.MAX_VALUE,
                    Math.max(
                            0L,
                            player.serverLevel().getGameTime()
                                    - presentation.startTick()));
            sendToPlayer(
                    player,
                    new StartPayload(
                            payload.owner(),
                            payload.sourceEntityId(),
                            payload.expandTicks(),
                            payload.keepTicks(),
                            payload.closeTicks(),
                            payload.fadeInTicks(),
                            payload.fadeOutTicks(),
                            elapsed,
                            payload.mode(),
                            payload.center(),
                            payload.trails()));
        }
    }

    public static void playerLoggedOut(ServerPlayer player) {
        history(player.server).pendingAutomaticRtz.remove(
                player.getUUID());
        GoldExperienceRequiemAddon.playerState(player)
                .setReturnToZeroMarkAvailable(false);
        GoldExperienceRequiemAddon.playerState(player)
                .setReturnToZeroPresentationActive(false);
    }

    public static BlockPreimage captureBlockPreimage(
            Level level, BlockPos position) {
        return level instanceof ServerLevel serverLevel
                        && journals(serverLevel, position)
                        && !isApplying()
                ? capture(serverLevel, position)
                : null;
    }

    private static BlockPreimage capture(
            ServerLevel serverLevel, BlockPos position) {
        blockCaptures++;
        BlockState state = serverLevel.getBlockState(position);
        CompoundTag blockEntityTag = null;
        boolean restorable = true;
        BlockEntity blockEntity =
                serverLevel.getBlockEntity(position);
        if (blockEntity != null) {
            try {
                blockEntityTag =
                        blockEntity.saveWithFullMetadata(
                                serverLevel.registryAccess());
            }
            catch (RuntimeException exception) {
                restorable = false;
            }
        }
        return new BlockPreimage(
                serverLevel.getGameTime(),
                position.immutable(),
                NbtUtils.writeBlockState(state),
                blockEntityTag,
                restorable);
    }

    public static void recordBlockMutation(
            ServerLevel level, BlockPreimage preimage) {
        if (preimage == null || isApplying()) {
            return;
        }
        ServerHistory history;
        synchronized (HISTORIES) {
            history = HISTORIES.get(level.getServer());
        }
        if (history != null) {
            record(level, history, preimage);
        }
    }

    private static void record(
            ServerLevel level,
            ServerHistory history,
            BlockPreimage preimage) {
        List<UUID> invalidated = new ArrayList<>();
        for (Map.Entry<UUID, Mark> entry :
                history.marks.entrySet()) {
            Mark mark = entry.getValue();
            if (!mark.dimension().equals(level.dimension())
                    || preimage.tick() < mark.tick()
                    || !mark.contains(preimage.position())) {
                continue;
            }
            if (!mark.recordBlock(preimage.copy())) {
                invalidated.add(entry.getKey());
            }
        }
        if (!invalidated.isEmpty()) {
            invalidated.forEach(history.marks::remove);
            refreshAnyMark();
        }
        for (UUID owner : invalidated) {
            history.pendingAutomaticRtz.remove(owner);
            ServerPlayer player = level.getServer()
                    .getPlayerList()
                    .getPlayer(owner);
            if (player != null) {
                syncMark(player, false);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component
                                .translatable(
                                        Result.UNSAFE_HISTORY
                                                .translationKey()),
                        true);
            }
        }
    }

    public static void recordBlockInteraction(
            ServerLevel level, BlockPos position) {
        ServerHistory history = journalling(level, position);
        if (history != null && !isApplying()) {
            record(level, history, capture(level, position));
        }
    }

    public static void recordBlockSnapshot(
            BlockSnapshot snapshot) {
        if (!(snapshot.getLevel()
                        instanceof ServerLevel level)
                || isApplying()) {
            return;
        }
        ServerHistory history =
                journalling(level, snapshot.getPos());
        if (history == null) {
            return;
        }
        blockCaptures++;
        record(level, history, new BlockPreimage(
                level.getGameTime(),
                snapshot.getPos().immutable(),
                NbtUtils.writeBlockState(
                        snapshot.getState()),
                snapshot.getTag() != null
                        ? snapshot.getTag().copy()
                        : null,
                true));
    }

    public static boolean isApplying() {
        return APPLY_DEPTH.get() > 0;
    }

    private static StageResult stage(
            ServerLevel level,
            ServerPlayer owner,
            LivingEntity performer,
            Mark mark) {
        List<BlockPreimage> blocks =
                new ArrayList<>(mark.blocks().values());
        blocks.sort(Comparator.comparingLong(
                        BlockPreimage::tick)
                .reversed());
        for (BlockPreimage target : blocks) {
            if (!target.restorable()) {
                return StageResult.failure(
                        Result.UNSAFE_HISTORY);
            }
            if (!level.isLoaded(target.position())
                    || !level.getWorldBorder()
                            .isWithinBounds(target.position())) {
                return StageResult.failure(
                        Result.UNLOADED_TARGET);
            }
            BlockState state = decodeState(level, target.state());
            if (!validateBlockEntity(
                    level,
                    target.position(),
                    state,
                    target.blockEntity())) {
                return StageResult.failure(
                        Result.UNSAFE_HISTORY);
            }
        }

        UUID protectedPerformer =
                performer != null
                        ? performer.getUUID()
                        : null;
        List<EntityMutation> entities = new ArrayList<>();
        Set<UUID> stagedEntities = new HashSet<>();
        for (Entity live : snapshotEntities(
                level.getAllEntities())) {
            if (live instanceof Player
                    || live instanceof StandEntity) {
                continue;
            }
            EntitySnapshot target =
                    mark.entities().get(live.getUUID());
            if (target != null) {
                ResourceLocation liveType =
                        BuiltInRegistries.ENTITY_TYPE
                                .getKey(live.getType());
                if (liveType == null
                        || !liveType.toString().equals(
                                target.typeId())) {
                    return StageResult.failure(
                            Result.UNSAFE_HISTORY);
                }
                Side side = sideOf(owner, live);
                // His side keeps its place, so only where the others go back to has to be loaded.
                if (side != Side.FRIEND
                        && !isLoadedTarget(
                                level, target.position())) {
                    return StageResult.failure(
                            Result.UNLOADED_TARGET);
                }
                entities.add(
                        EntityMutation.restore(live, target, side));
                stagedEntities.add(live.getUUID());
                continue;
            }
            if (mark.contains(live.blockPosition())
                    && !live.getUUID().equals(
                            protectedPerformer)) {
                entities.add(EntityMutation.remove(live));
                stagedEntities.add(live.getUUID());
            }
        }
        for (EntitySnapshot target :
                mark.entities().values()) {
            if (stagedEntities.contains(target.uuid())) {
                continue;
            }
            Entity existing = findEntity(
                    level.getServer(), target.uuid());
            if (existing != null) {
                return StageResult.failure(
                        Result.WRONG_DIMENSION);
            }
            // A creature that died since the mark comes back if it was not
            // the user's enemy when it died; one whose end nobody saw (it
            // left, or despawned) is left alone.
            if (target.living()
                    && !Boolean.FALSE.equals(
                            mark.deaths().get(target.uuid()))) {
                continue;
            }
            if (!isLoadedTarget(level, target.position())) {
                return StageResult.failure(
                        Result.UNLOADED_TARGET);
            }
            if (!canRecreate(target)) {
                return StageResult.failure(
                        Result.UNSAFE_HISTORY);
            }
            entities.add(EntityMutation.recreate(target));
            stagedEntities.add(target.uuid());
        }
        entities.sort(Comparator.comparing(
                mutation -> mutation.uuid().toString()));

        List<PlayerMutation> players = new ArrayList<>();
        for (PlayerSnapshot snapshot :
                mark.players().values()) {
            ServerPlayer live = snapshot.uuid().equals(owner.getUUID())
                    ? owner
                    : level.getServer()
                            .getPlayerList()
                            .getPlayer(snapshot.uuid());
            // A player body the list does not hold was still marked in this level.
            if (live == null
                    && level.getEntity(snapshot.uuid())
                            instanceof ServerPlayer present) {
                live = present;
            }
            if (live == null) {
                continue;
            }
            if (live.serverLevel() != level) {
                return StageResult.failure(
                        Result.WRONG_DIMENSION);
            }
            boolean restorePosition =
                    !live.getUUID().equals(owner.getUUID());
            if (restorePosition
                    && !isLoadedTarget(
                            level, snapshot.position())) {
                return StageResult.failure(
                        Result.UNLOADED_TARGET);
            }
            players.add(new PlayerMutation(
                    live, snapshot, restorePosition));
        }
        players.sort(Comparator.comparing(
                mutation -> mutation.live()
                        .getUUID()
                        .toString()));

        List<StandMutation> stands = new ArrayList<>();
        for (StandSnapshot snapshot :
                mark.stands().values()) {
            Entity live = findEntity(
                    level.getServer(), snapshot.uuid());
            if (live != null && live.level() != level) {
                return StageResult.failure(
                        Result.WRONG_DIMENSION);
            }
            if (!(live instanceof StandEntity stand)
                    || stand.getUser() == owner) {
                continue;
            }
            if (!isLoadedTarget(
                    level, snapshot.position())) {
                return StageResult.failure(
                        Result.UNLOADED_TARGET);
            }
            stands.add(new StandMutation(stand, snapshot));
        }
        stands.sort(Comparator.comparing(
                mutation -> mutation.live()
                        .getUUID()
                        .toString()));

        Set<UUID> disabledLiving = new HashSet<>();
        for (Entity entity :
                snapshotEntities(level.getAllEntities())) {
            // The owner, 2026-11-08: never the user's own power.
            if (entity instanceof LivingEntity
                    && entity != owner
                    && !(entity instanceof StandEntity stand
                            && stand.getUser() == owner)
                    && mark.contains(entity.blockPosition())) {
                disabledLiving.add(entity.getUUID());
            }
        }
        return StageResult.success(new RewindPlan(
                mark.dayTime(),
                restoresDayTime(level, owner, mark),
                List.copyOf(blocks),
                List.copyOf(entities),
                List.copyOf(players),
                List.copyOf(stands),
                Set.copyOf(disabledLiving)));
    }

    /**
     * Rule G3: Made in Heaven's acceleration keeps the day time. The owner,
     * 2026-11-08: unless the one who accelerates stands inside the rewound
     * area ("只有和天堂製造使用者在八個區塊內使用才會倒回").
     */
    private static boolean restoresDayTime(
            ServerLevel level, ServerPlayer owner, Mark mark) {
        return LevelTimeRates.rate(
                        LevelTimeRates.Channel.TIME, level) <= 1.0F
                || acceleratingUserInside(level, owner, mark);
    }

    /**
     * The core tells how fast a level's time runs, not who runs it. The one
     * who accelerates is ahead of everyone else in his own time, so he is
     * read off the core's per-entity rates: a player in the area whose own
     * time runs faster than the Requiem user's.
     */
    private static boolean acceleratingUserInside(
            ServerLevel level, ServerPlayer owner, Mark mark) {
        float own = EntityTimeRates.rate(
                EntityTimeRates.Channel.BEING, owner);
        for (ServerPlayer player : level.players()) {
            if (player != owner
                    && player.isAlive()
                    && mark.contains(player.blockPosition())
                    && EntityTimeRates.rate(
                                    EntityTimeRates.Channel.BEING, player)
                            > own) {
                return true;
            }
        }
        return false;
    }

    /** Where a creature stands to the user at the press; a thing is neither. */
    private static Side sideOf(ServerPlayer owner, Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return Side.THING;
        }
        return GerSides.isHostile(owner, entity)
                ? Side.ENEMY
                : Side.FRIEND;
    }

    /** A real death inside a marked area, with whose side the creature was on as it died. */
    public static void recordDeath(LivingEntity dead) {
        if (!(dead.level() instanceof ServerLevel level)
                || dead instanceof Player
                || !anyMark) {
            return;
        }
        ServerHistory history;
        synchronized (HISTORIES) {
            history = HISTORIES.get(level.getServer());
        }
        if (history == null || history.marks.isEmpty()) {
            return;
        }
        for (Mark mark : history.marks.values()) {
            if (mark.dimension().equals(level.dimension())
                    && mark.entities().containsKey(dead.getUUID())) {
                mark.deaths().put(
                        dead.getUUID(),
                        GerSides.isHostile(
                                level.getServer()
                                        .getPlayerList()
                                        .getPlayer(mark.owner()),
                                dead));
            }
        }
    }

    private static boolean commit(
            ServerLevel level, RewindPlan plan) {
        long inverseDayTime = level.getDayTime();
        List<BlockInverse> blockInverses =
                new ArrayList<>();
        List<EntityInverse> entityInverses =
                new ArrayList<>();
        List<PlayerInverse> playerInverses =
                new ArrayList<>();
        List<StandInverse> standInverses =
                new ArrayList<>();
        List<UUID> created = new ArrayList<>();

        enterApply();
        try {
            for (BlockPreimage target : plan.blocks()) {
                blockInverses.add(new BlockInverse(
                        target.position(),
                        captureBlockState(
                                level, target.position())));
            }
            for (EntityMutation mutation :
                    plan.entities()) {
                if (mutation.live() != null) {
                    entityInverses.add(new EntityInverse(
                            mutation.uuid(),
                            EntitySnapshot.capture(
                                    mutation.live(),
                                    level.getGameTime())));
                }
            }
            for (PlayerMutation mutation :
                    plan.players()) {
                playerInverses.add(new PlayerInverse(
                        mutation.live(),
                        PlayerSnapshot.capture(
                                mutation.live())));
            }
            for (StandMutation mutation :
                    plan.stands()) {
                standInverses.add(new StandInverse(
                        mutation.live(),
                        StandSnapshot.capture(
                                mutation.live())));
            }

            for (BlockPreimage target : plan.blocks()) {
                applyBlockState(
                        level,
                        target.position(),
                        target.state(),
                        target.blockEntity());
            }
            for (EntityMutation mutation : plan.entities()) {
                switch (mutation.operation()) {
                    case RESTORE -> applyEntitySnapshot(
                            level,
                            mutation.live(),
                            mutation.target(),
                            mutation.side());
                    case REMOVE -> mutation.live().remove(
                            RemovalReason.DISCARDED);
                    case RECREATE -> {
                        Entity recreated = recreate(
                                level, mutation.target());
                        created.add(recreated.getUUID());
                    }
                }
            }
            for (PlayerMutation mutation : plan.players()) {
                applyPlayerSnapshot(
                        level,
                        mutation.live(),
                        mutation.target(),
                        mutation.restorePosition());
                if (!mutation.restorePosition()) {
                    restoreOwner(mutation.live(), mutation.target());
                }
            }
            for (StandMutation mutation : plan.stands()) {
                applyStandSnapshot(
                        level,
                        mutation.live(),
                        mutation.target());
            }
            restoreRelationships(level, plan.entities());
            if (plan.restoreDayTime()) {
                level.setDayTime(plan.dayTime());
            }
            syncMutations(level, plan);
            return true;
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "GER Return to Zero commit failed; applying "
                            + "inverse journal",
                    exception);
            if (!rollback(
                    level,
                    plan.restoreDayTime(),
                    inverseDayTime,
                    blockInverses,
                    entityInverses,
                    playerInverses,
                    standInverses,
                    created)) {
                LOGGER.error(
                        "GER Return to Zero inverse journal was "
                                + "not fully restored");
            }
            return false;
        }
        finally {
            exitApply();
        }
    }

    private static boolean rollback(
            ServerLevel level,
            boolean restoreDayTime,
            long dayTime,
            List<BlockInverse> blocks,
            List<EntityInverse> entities,
            List<PlayerInverse> players,
            List<StandInverse> stands,
            List<UUID> created) {
        boolean complete = true;
        List<EntityMutation> relationships =
                new ArrayList<>();
        for (UUID uuid : created) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                try {
                    entity.remove(RemovalReason.DISCARDED);
                }
                catch (RuntimeException exception) {
                    complete = false;
                    LOGGER.error(
                            "Could not remove RTZ-created entity {}",
                            uuid,
                            exception);
                }
            }
        }
        for (EntityInverse inverse : entities) {
            try {
                Entity entity = level.getEntity(inverse.uuid());
                if (entity == null) {
                    entity = recreate(level, inverse.snapshot());
                }
                applyFullSnapshot(
                        level, entity, inverse.snapshot());
                relationships.add(EntityMutation.restore(
                        entity, inverse.snapshot(), Side.THING));
            }
            catch (RuntimeException exception) {
                complete = false;
                LOGGER.error(
                        "Could not inverse-restore RTZ entity {}",
                        inverse.uuid(),
                        exception);
            }
        }
        for (PlayerInverse inverse : players) {
            try {
                applyPlayerSnapshot(
                        level,
                        inverse.live(),
                        inverse.snapshot(),
                        true);
                restoreBodyExactly(
                        inverse.live(), inverse.snapshot());
            }
            catch (RuntimeException exception) {
                complete = false;
                LOGGER.error(
                        "Could not inverse-restore RTZ player {}",
                        inverse.live().getUUID(),
                        exception);
            }
        }
        for (StandInverse inverse : stands) {
            try {
                applyStandSnapshot(
                        level,
                        inverse.live(),
                        inverse.snapshot());
            }
            catch (RuntimeException exception) {
                complete = false;
                LOGGER.error(
                        "Could not inverse-restore RTZ Stand {}",
                        inverse.live().getUUID(),
                        exception);
            }
        }
        try {
            restoreRelationships(level, relationships);
        }
        catch (RuntimeException exception) {
            complete = false;
            LOGGER.error(
                    "Could not inverse-restore RTZ relationships",
                    exception);
        }
        for (int index = blocks.size() - 1;
                index >= 0;
                index--) {
            BlockInverse inverse = blocks.get(index);
            try {
                applyBlockState(
                        level,
                        inverse.position(),
                        inverse.snapshot().state(),
                        inverse.snapshot().blockEntity());
            }
            catch (RuntimeException exception) {
                complete = false;
                LOGGER.error(
                        "Could not inverse-restore RTZ block {}",
                        inverse.position(),
                        exception);
            }
        }
        try {
            if (restoreDayTime) {
                level.setDayTime(dayTime);
            }
        }
        catch (RuntimeException exception) {
            complete = false;
            LOGGER.error(
                    "Could not inverse-restore RTZ day time",
                    exception);
        }
        return complete;
    }

    private static void beginPresentation(
            ServerHistory history,
            ServerLevel level,
            ServerPlayer owner,
            LivingEntity performer,
            Mark mark,
            RewindPlan plan,
            int keepTicks,
            int mode,
            Set<UUID> erasingAfterRewind) {
        Set<UUID> disabled = new HashSet<>();
        for (UUID uuid : plan.disabledLiving()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living
                    && !erasesOn(living, erasingAfterRewind)) {
                GoldExperienceRequiemAddon
                        .livingState(living)
                        .startReturnToZeroDisable(
                                owner.getUUID(),
                                living,
                                keepTicks);
                disabled.add(uuid);
            }
        }
        int sourceEntityId = performer != null
                ? performer.getId()
                : owner.getId();
        StartPayload payload = new StartPayload(
                owner.getUUID(),
                sourceEntityId,
                VFX_EXPAND_TICKS,
                keepTicks,
                VFX_CLOSE_TICKS,
                VFX_FADE_IN_TICKS,
                VFX_FADE_OUT_TICKS,
                0,
                mode,
                Vec3.atCenterOf(mark.center()),
                buildTrails(level, mark));
        history.presentations.put(
                owner.getUUID(),
                new Presentation(
                        owner.getUUID(),
                        level.dimension(),
                        level.getGameTime(),
                        level.getGameTime()
                                + presentationTicks(payload),
                        Set.copyOf(disabled),
                        payload));
        GoldExperienceRequiemAddon.playerState(owner)
                .setReturnToZeroPresentationActive(true);
        broadcast(level, payload);
    }

    // The Time Erase action may run in the eraser's Stand slot, so his
    // summoned Stand stays free as well.
    private static boolean erasesOn(
            LivingEntity living, Set<UUID> erasers) {
        if (erasers.isEmpty()) {
            return false;
        }
        LivingEntity body = living instanceof StandEntity stand
                ? stand.getUser() : living;
        return body != null && erasers.contains(body.getUUID());
    }

    private static List<GhostTrail> buildTrails(
            ServerLevel level, Mark mark) {
        List<GhostTrail> trails = new ArrayList<>();
        List<Map.Entry<UUID, Deque<PoseFrame>>> ordered =
                new ArrayList<>(mark.poseFrames().entrySet());
        ordered.sort(Comparator.comparing(
                entry -> entry.getKey().toString()));
        for (Map.Entry<UUID, Deque<PoseFrame>> entry :
                ordered) {
            if (trails.size()
                    >= GerRtzPayloads.MAX_TRAIL_ENTITIES) {
                break;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) {
                continue;
            }
            List<Vec3> points = new ArrayList<>();
            List<PoseFrame> frames =
                    new ArrayList<>(entry.getValue());
            for (int index = frames.size() - 1;
                    index >= 0
                            && points.size()
                                    < MAX_GHOST_POINTS_PER_ENTITY;
                    index--) {
                Vec3 point = frames.get(index).position();
                if (points.isEmpty()
                        || points.get(points.size() - 1)
                                .distanceTo(point)
                                >= MIN_GHOST_DISTANCE) {
                    points.add(point);
                }
            }
            if (!points.isEmpty()) {
                trails.add(new GhostTrail(
                        entity.getId(), points));
            }
        }
        return List.copyOf(trails);
    }

    private static void closePresentation(
            MinecraftServer server,
            ServerHistory history,
            UUID owner,
            boolean broadcastStop) {
        Presentation presentation =
                history.presentations.remove(owner);
        if (presentation == null) {
            return;
        }
        ServerPlayer presentationOwner =
                server.getPlayerList()
                        .getPlayer(owner);
        if (presentationOwner != null) {
            GoldExperienceRequiemAddon
                    .playerState(presentationOwner)
                    .setReturnToZeroPresentationActive(false);
        }
        ServerLevel level =
                server.getLevel(presentation.dimension());
        if (level == null) {
            return;
        }
        for (UUID uuid : presentation.disabledLiving()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                GoldExperienceRequiemAddon
                        .livingState(living)
                        .clearReturnToZeroDisable(owner);
            }
        }
        if (broadcastStop) {
            broadcast(level, new StopPayload(owner));
        }
    }

    private static int presentationTicks(StartPayload payload) {
        long ticks = (long) payload.expandTicks()
                + payload.keepTicks()
                + Math.max(
                        payload.closeTicks(),
                        payload.fadeOutTicks());
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(1L, ticks));
    }

    private static void playEndSound(
            ServerLevel level, PendingEndSound pending) {
        ServerPlayer owner = level.getServer()
                .getPlayerList()
                .getPlayer(pending.owner());
        Vec3 position = owner != null
                        && owner.serverLevel() == level
                ? owner.position()
                : pending.center();
        level.playSound(
                null,
                position.x,
                position.y,
                position.z,
                GoldExperienceRequiemAddon.RTZ_END_SOUND.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }

    private static void broadcast(
            ServerLevel level,
            net.minecraft.network.protocol.common.custom
                    .CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) {
            sendToPlayer(player, payload);
        }
    }

    private static void syncMark(
            ServerPlayer player, boolean available) {
        GoldExperienceRequiemAddon.playerState(player)
                .setReturnToZeroMarkAvailable(available);
        sendToPlayer(
                player,
                new MarkStatePayload(
                        player.getUUID(), available));
    }

    private static void sendToPlayer(
            ServerPlayer player,
            net.minecraft.network.protocol.common.custom
                    .CustomPacketPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        }
        catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not send GER RTZ payload {} to {}",
                    payload.type().id(),
                    player.getGameProfile().getName(),
                    exception);
        }
    }

    private static Set<InstanceKey> captureTimeStopInstances(
            ServerLevel level,
            ServerHistory history) {
        if (!level.hasData(
                ModDataAttachmentTypes.TIME_STOP.get())) {
            return Set.of();
        }
        TimeStopState state = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        Set<InstanceKey> instances = new HashSet<>();
        for (TimeStopState.Instance instance :
                state.getInstances()) {
            if (instance.isActive()) {
                instances.add(ensureTimeStopGeneration(
                        history, level, instance));
            }
        }
        return Set.copyOf(instances);
    }

    private static void terminatePostMarkTimeStops(
            ServerHistory history,
            ServerLevel level,
            Mark mark) {
        if (!level.hasData(
                ModDataAttachmentTypes.TIME_STOP.get())) {
            return;
        }
        TimeStopState state = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        List<InstanceRegion> currentInstances = new ArrayList<>();
        for (TimeStopState.Instance instance :
                state.getInstances()) {
            if (instance.isActive()) {
                currentInstances.add(instanceRegion(
                        history, level, instance));
            }
        }
        GerRtzTimeStopPolicy.terminatePostMarkInstances(
                mark.timeStopInstances(),
                mark.centerChunkX(),
                mark.centerChunkZ(),
                mark.chunkRadius(),
                currentInstances,
                state::removeInstance);
    }

    // Owner Q9: a Return to Zero that goes off frees its frozen owner, past rule
    // G1, whatever the age of the stop that holds him and its place to the mark.
    private static void terminateTimeStopsHolding(
            ServerLevel level,
            ServerPlayer owner) {
        if (!TimeStopState.shouldFreezeOnServer(owner)) {
            return;
        }
        TimeStopState state = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        ChunkPos ownerChunk = new ChunkPos(owner.blockPosition());
        for (TimeStopState.Instance instance :
                List.copyOf(state.getInstances())) {
            if (instance.isActive() && instance.covers(ownerChunk)) {
                state.removeInstance(instance.id());
            }
        }
    }

    private static boolean hasPostMarkIntersectingTimeStop(
            ServerHistory history,
            ServerLevel level,
            Mark mark,
            TimeStopState state) {
        for (TimeStopState.Instance instance :
                state.getInstances()) {
            if (instance.isActive()
                    && GerRtzTimeStopPolicy.shouldTerminate(
                            mark.timeStopInstances(),
                            mark.centerChunkX(),
                            mark.centerChunkZ(),
                            mark.chunkRadius(),
                            instanceRegion(
                                    history, level, instance))) {
                return true;
            }
        }
        return false;
    }

    private static InstanceRegion instanceRegion(
            ServerHistory history,
            ServerLevel level,
            TimeStopState.Instance instance) {
        return new InstanceRegion(
                ensureTimeStopGeneration(
                        history, level, instance),
                instance.centerPos().x,
                instance.centerPos().z,
                instance.chunkRange());
    }

    // Added is the generation authority. The awaiting flag prevents a mark
    // captured in the same event stack from incrementing that generation.
    private static InstanceKey registerTimeStopAdded(
            ServerHistory history,
            ServerLevel level,
            TimeStopState.Instance instance) {
        GenerationTracker<TimeStopState.Instance> active =
                history.activeTimeStops.computeIfAbsent(
                        level.dimension(),
                        ignored -> new GenerationTracker<>());
        return active.added(instance.id(), instance);
    }

    private static InstanceKey ensureTimeStopGeneration(
            ServerHistory history,
            ServerLevel level,
            TimeStopState.Instance instance) {
        GenerationTracker<TimeStopState.Instance> active =
                history.activeTimeStops.computeIfAbsent(
                        level.dimension(),
                        ignored -> new GenerationTracker<>());
        return active.observe(instance.id(), instance);
    }

    private static Set<UUID> captureActiveTimeEraseUsers(
            ServerLevel level) {
        if (!ModList.get().isLoaded(KING_CRIMSON_MOD_ID)) {
            return Set.of();
        }
        try {
            return KingCrimsonBridge.capture(level);
        }
        catch (RuntimeException | LinkageError exception) {
            LOGGER.warn(
                    "Could not inspect the public King Crimson "
                            + "Time Erase state before GER rewind",
                    exception);
            return Set.of();
        }
    }

    /** Whether the rewind's event ended one of the erases running before it. */
    private static boolean anyTimeEraseEnded(
            ServerLevel level, Set<UUID> activeUsers) {
        if (activeUsers.isEmpty()
                || !ModList.get().isLoaded(KING_CRIMSON_MOD_ID)) {
            return false;
        }
        try {
            return KingCrimsonBridge.anyEnded(level, activeUsers);
        }
        catch (RuntimeException | LinkageError exception) {
            LOGGER.warn(
                    "Could not inspect the public King Crimson "
                            + "Time Erase state after GER rewind",
                    exception);
            return false;
        }
    }

    /**
     * King Crimson's two hooks for a Requiem holder, when it is loaded:
     * Epitaph tells him of a prediction aimed at him instead of walking him,
     * and a creature's erase against him (the player it fights, or the one
     * whose hit would kill it) is returned to zero as it would begin, with
     * Requiem's effect on the creature (rule C6).
     */
    public static void registerKingCrimsonHooks() {
        if (!ModList.get().isLoaded(KING_CRIMSON_MOD_ID)) {
            return;
        }
        try {
            KingCrimsonBridge.register();
        }
        catch (RuntimeException | LinkageError exception) {
            LOGGER.warn(
                    "Could not register GER's answers with King Crimson",
                    exception);
        }
    }

    /**
     * EPITAPH-ARCH 3.7: whether this press of Return to Zero broke a
     * prediction aimed at its user; such a press does nothing else.
     */
    public static boolean breaksEpitaphPrediction(ServerPlayer owner) {
        if (!ModList.get().isLoaded(KING_CRIMSON_MOD_ID)) {
            return false;
        }
        try {
            return KingCrimsonBridge.breakPrediction(owner);
        }
        catch (RuntimeException | LinkageError exception) {
            LOGGER.warn(
                    "Could not ask King Crimson's Epitaph at a GER press",
                    exception);
            return false;
        }
    }

    // The presentation's time-erase mode needs to know whether an erase runs
    // (1.16 CheckTools.isKingCrimsonTimeEraseActive), which no core event
    // tells; ending the erases is RewindAppliedEvent's. Every class of King
    // Crimson is named here only, behind ModList.
    private static final class KingCrimsonBridge {
        private KingCrimsonBridge() {}

        private static void register() {
            EpitaphPredictions.registerExemption(
                    GoldExperienceRequiemAddon.id("requiem_holder"),
                    GoldExperienceRequiemAddon::isGerUser);
            KingCrimsonCreatureErase.registerAnswer(
                    GoldExperienceRequiemAddon.id("requiem_holder"),
                    new KingCrimsonCreatureErase.Answer() {
                        @Override
                        public boolean answers(
                                ServerPlayer holder, Mob creature) {
                            return GoldExperienceRequiemAddon
                                    .isGerUser(holder);
                        }

                        @Override
                        public void show(
                                ServerPlayer holder, Mob creature) {
                            GerHitAnswerService.showOn(creature);
                        }
                    });
        }

        private static boolean breakPrediction(ServerPlayer owner) {
            return EpitaphPredictions.breakPrediction(
                    owner, RETURN_TO_ZERO_SOURCE);
        }

        private static Set<UUID> capture(ServerLevel level) {
            Set<UUID> active = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                if (KingCrimsonTemporalStateService.isErasing(player)) {
                    active.add(player.getUUID());
                }
            }
            return Set.copyOf(active);
        }

        private static boolean anyEnded(
                ServerLevel level, Set<UUID> activeUsers) {
            for (UUID uuid : activeUsers) {
                ServerPlayer player = level.getServer()
                        .getPlayerList()
                        .getPlayer(uuid);
                if (player != null
                        && !KingCrimsonTemporalStateService
                                .isErasing(player)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void applyBlockState(
            ServerLevel level,
            BlockPos position,
            CompoundTag stateTag,
            CompoundTag blockEntityTag) {
        BlockState target = decodeState(level, stateTag);
        BlockState current = level.getBlockState(position);
        if (!current.equals(target)
                && !level.setBlock(
                        position,
                        target,
                        BLOCK_UPDATE_FLAGS)) {
            throw new IllegalStateException(
                    "Could not restore RTZ block " + position);
        }
        if (blockEntityTag != null) {
            BlockEntity currentBlockEntity =
                    level.getBlockEntity(position);
            BlockEntity decoded = BlockEntity.loadStatic(
                    position,
                    target,
                    blockEntityTag.copy(),
                    level.registryAccess());
            if (currentBlockEntity == null
                    || decoded == null
                    || decoded.getType()
                            != currentBlockEntity.getType()) {
                throw new IllegalStateException(
                        "Could not restore RTZ block entity "
                                + position);
            }
            currentBlockEntity.loadWithComponents(
                    blockEntityTag.copy(),
                    level.registryAccess());
            currentBlockEntity.setChanged();
        }
        level.sendBlockUpdated(
                position,
                current,
                target,
                Block.UPDATE_CLIENTS);
    }

    /**
     * The owner, 2026-11-08. An enemy goes back to its place at the mark and
     * keeps what was done to it: its present health, and the harmful effects
     * and the fire it carries. One of the user's side keeps its place and
     * gets back the health it had at the mark, never less than it has now;
     * its effects and its fire are the mark's.
     */
    private static void applyEntitySnapshot(
            ServerLevel level,
            Entity entity,
            EntitySnapshot snapshot,
            Side side) {
        if (!(entity instanceof LivingEntity living)) {
            applyFullSnapshot(level, entity, snapshot);
            return;
        }
        float health = living.getHealth();
        float absorption = living.getAbsorptionAmount();
        int fire = living.getRemainingFireTicks();
        if (side == Side.FRIEND) {
            applyFullSnapshot(
                    level, entity, snapshot.at(living));
            health = Math.max(health, living.getHealth());
            absorption = Math.max(
                    absorption, living.getAbsorptionAmount());
        }
        else {
            List<MobEffectInstance> harm = new ArrayList<>();
            for (MobEffectInstance effect : living.getActiveEffects()) {
                if (effect.getEffect().value().getCategory()
                        == MobEffectCategory.HARMFUL) {
                    harm.add(new MobEffectInstance(effect));
                }
            }
            applyFullSnapshot(level, entity, snapshot);
            // what the rewind puts back itself is no hit for a Requiem to answer
            GerHitAnswerService.applyingOwnEffects(() -> {
                for (MobEffectInstance effect : harm) {
                    living.removeEffect(effect.getEffect());
                    living.forceAddEffect(effect, null);
                }
            });
            if (fire > living.getRemainingFireTicks()) {
                living.setRemainingFireTicks(fire);
            }
        }
        living.setHealth(health);
        living.setAbsorptionAmount(absorption);
    }

    /**
     * The user himself: his health back to the mark's where that is more,
     * and his fire gone. Of the effects he gained since the mark the
     * harmful ones go and the others stay (Resolve's among them); those he
     * had at the mark stay as they are now. The coordinator, R772.1.
     */
    private static void restoreOwner(
            ServerPlayer owner, PlayerSnapshot snapshot) {
        if (snapshot.health() > owner.getHealth()) {
            owner.setHealth(snapshot.health());
        }
        if (snapshot.absorption() > owner.getAbsorptionAmount()) {
            owner.setAbsorptionAmount(snapshot.absorption());
        }
        for (MobEffectInstance effect :
                List.copyOf(owner.getActiveEffects())) {
            if (effect.getEffect().value().getCategory()
                            == MobEffectCategory.HARMFUL
                    && snapshot.effects().stream().noneMatch(
                            marked -> marked.getEffect()
                                    .equals(effect.getEffect()))) {
                owner.removeEffect(effect.getEffect());
            }
        }
        if (owner.getRemainingFireTicks() > snapshot.fireTicks()) {
            owner.setRemainingFireTicks(snapshot.fireTicks());
        }
    }

    // The inverse journal's side of restoreOwner: everything as it was captured.
    private static void restoreBodyExactly(
            ServerPlayer player, PlayerSnapshot snapshot) {
        player.setHealth(snapshot.health());
        player.setAbsorptionAmount(snapshot.absorption());
        player.setRemainingFireTicks(snapshot.fireTicks());
        for (MobEffectInstance effect :
                List.copyOf(player.getActiveEffects())) {
            player.removeEffect(effect.getEffect());
        }
        GerHitAnswerService.applyingOwnEffects(() -> {
            for (MobEffectInstance effect : snapshot.effects()) {
                player.forceAddEffect(new MobEffectInstance(effect), null);
            }
        });
    }

    private static void applyFullSnapshot(
            ServerLevel level,
            Entity entity,
            EntitySnapshot snapshot) {
        Set<Holder<MobEffect>> oldEffects =
                effectTypes(entity);
        // Defect D1: a time stop sets a frozen mob's NoAI and keeps its own
        // AI state until it lets go, so a snapshot taken frozen carries the
        // freeze's NoAI, and a live mob frozen now must keep the freeze's.
        Boolean keptNoAi = entity instanceof Mob mob
                        && (snapshot.frozen()
                                || TimeStopState.shouldFreezeOnServer(mob))
                ? mob.isNoAi()
                : null;
        CompoundTag tag = snapshot.tag().copy();
        tag.remove("id");
        tag.remove("Passengers");
        tag.putUUID("UUID", entity.getUUID());
        if (entity instanceof LivingEntity living) {
            // A load only adds effects: the ones gained since the snapshot would stay.
            for (Holder<MobEffect> effect : oldEffects) {
                living.removeEffect(effect);
            }
        }
        loadKeepingSummonedStand(entity, tag);
        entity.noPhysics = snapshot.noPhysics();
        if (keptNoAi != null && entity instanceof Mob mob) {
            mob.setNoAi(keptNoAi);
        }
        // Rule G2 (A2-REPIN): a frozen entity stays where it was put.
        TimeStopState.repinOrReleaseOnServer(entity);
        syncRemovedEffects(level, entity, oldEffects);
        syncEntity(level, entity);
    }

    private static void loadKeepingSummonedStand(
            Entity entity, CompoundTag tag) {
        LivingEntity user =
                entity instanceof LivingEntity living ? living : null;
        StandPower previous = StandPower.get(user);
        SummonedStand summoned = previous != null
                ? previous.getSummonedStand()
                : null;
        StandType type = previous != null
                ? previous.getPowerType()
                : null;
        try {
            entity.load(tag);
        }
        finally {
            // The load puts a new power attachment, and the summoned Stand
            // is not in the saved data: the new one would not know it.
            StandPower restored = StandPower.get(user);
            if (summoned != null && restored != previous) {
                if (restored != null
                        && restored.getPowerType() == type) {
                    restored.setSummonedStand(summoned);
                }
                else if (type != null) {
                    type.forceUnsummon(user, previous);
                }
            }
        }
    }

    private static void applyPlayerSnapshot(
            ServerLevel level,
            ServerPlayer player,
            PlayerSnapshot snapshot,
            boolean restorePosition) {
        player.getInventory().load(
                snapshot.inventory().copy());
        player.getInventory().selected =
                snapshot.selectedSlot();
        if (restorePosition) {
            player.stopRiding();
            player.connection.teleport(
                    snapshot.position().x,
                    snapshot.position().y,
                    snapshot.position().z,
                    snapshot.yRot(),
                    snapshot.xRot());
            restoreVehicle(
                    level, player, snapshot.vehicle());
            TimeStopState.repinOrReleaseOnServer(player);
        }
        player.inventoryMenu.broadcastFullState();
        player.containerMenu.broadcastFullState();
        player.connection.send(
                new ClientboundSetCarriedItemPacket(
                        player.getInventory().selected));
    }

    private static void applyStandSnapshot(
            ServerLevel level,
            StandEntity stand,
            StandSnapshot snapshot) {
        stand.stopRiding();
        stand.teleportTo(
                snapshot.position().x,
                snapshot.position().y,
                snapshot.position().z);
        stand.setYRot(snapshot.yRot());
        stand.setXRot(snapshot.xRot());
        stand.setDeltaMovement(Vec3.ZERO);
        stand.hasImpulse = true;
        restoreVehicle(level, stand, snapshot.vehicle());
        TimeStopState.repinOrReleaseOnServer(stand);
    }

    private static void restoreVehicle(
            ServerLevel level,
            Entity entity,
            UUID vehicleUuid) {
        if (vehicleUuid == null) {
            return;
        }
        Entity vehicle = level.getEntity(vehicleUuid);
        if (vehicle != null) {
            entity.startRiding(vehicle, true);
        }
    }

    private static Entity recreate(
            ServerLevel level, EntitySnapshot snapshot) {
        CompoundTag tag = snapshot.tag().copy();
        tag.remove("Passengers");
        Entity entity = net.minecraft.world.entity.EntityType
                .loadEntityRecursive(
                        tag, level, current -> current);
        if (entity == null || !level.addFreshEntity(entity)) {
            throw new IllegalStateException(
                    "Could not recreate RTZ entity "
                            + snapshot.uuid());
        }
        entity.noPhysics = snapshot.noPhysics();
        TimeStopState.repinOrReleaseOnServer(entity);
        return entity;
    }

    private static void restoreRelationships(
            ServerLevel level,
            List<EntityMutation> mutations) {
        for (EntityMutation mutation : mutations) {
            EntitySnapshot snapshot = mutation.target();
            if (snapshot == null) {
                continue;
            }
            Entity entity = level.getEntity(snapshot.uuid());
            if (entity == null) {
                continue;
            }
            // One of the user's side keeps its place, its seat included.
            if (mutation.side() != Side.FRIEND) {
                entity.stopRiding();
                if (snapshot.vehicle() != null) {
                    Entity vehicle =
                            level.getEntity(snapshot.vehicle());
                    if (vehicle != null) {
                        entity.startRiding(vehicle, true);
                    }
                }
            }
            if (entity instanceof Mob mob) {
                Entity target = snapshot.mobTarget() != null
                        ? level.getEntity(snapshot.mobTarget())
                        : null;
                mob.setTarget(
                        target instanceof LivingEntity living
                                ? living
                                : null);
            }
            if (entity instanceof Projectile projectile) {
                projectile.setOwner(
                        snapshot.projectileOwner() != null
                                ? level.getEntity(
                                        snapshot
                                                .projectileOwner())
                                : null);
            }
        }
    }

    private static void syncMutations(
            ServerLevel level, RewindPlan plan) {
        for (EntityMutation mutation : plan.entities()) {
            Entity entity = level.getEntity(mutation.uuid());
            if (entity != null) {
                syncEntity(level, entity);
            }
        }
        for (PlayerMutation mutation : plan.players()) {
            syncEntity(level, mutation.live());
        }
        for (StandMutation mutation : plan.stands()) {
            syncEntity(level, mutation.live());
        }
    }

    private static void syncEntity(
            ServerLevel level, Entity entity) {
        level.getChunkSource().broadcastAndSend(
                entity,
                new ClientboundTeleportEntityPacket(entity));
        if (entity instanceof LivingEntity living) {
            for (var effect :
                    living.getActiveEffectsMap().values()) {
                level.getChunkSource().broadcastAndSend(
                        entity,
                        new ClientboundUpdateMobEffectPacket(
                                entity.getId(),
                                effect,
                                true));
            }
            List<Pair<EquipmentSlot, ItemStack>> equipment =
                    new ArrayList<>();
            for (EquipmentSlot slot : EnumSet.of(
                    EquipmentSlot.MAINHAND,
                    EquipmentSlot.OFFHAND,
                    EquipmentSlot.FEET,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.HEAD)) {
                equipment.add(Pair.of(
                        slot,
                        living.getItemBySlot(slot).copy()));
            }
            level.getChunkSource().broadcastAndSend(
                    entity,
                    new ClientboundSetEquipmentPacket(
                            entity.getId(), equipment));
        }
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot());
            player.inventoryMenu.broadcastFullState();
            player.containerMenu.broadcastFullState();
            player.connection.send(
                    new ClientboundSetCarriedItemPacket(
                            player.getInventory().selected));
            player.resetSentInfo();
        }
    }

    private static Set<Holder<MobEffect>> effectTypes(
            Entity entity) {
        return entity instanceof LivingEntity living
                ? Set.copyOf(
                        living.getActiveEffectsMap().keySet())
                : Set.of();
    }

    private static void syncRemovedEffects(
            ServerLevel level,
            Entity entity,
            Set<Holder<MobEffect>> oldEffects) {
        for (Holder<MobEffect> effect : oldEffects) {
            level.getChunkSource().broadcastAndSend(
                    entity,
                    new ClientboundRemoveMobEffectPacket(
                            entity.getId(), effect));
        }
    }

    private static BlockStateSnapshot captureBlockState(
            ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        BlockEntity blockEntity =
                level.getBlockEntity(position);
        CompoundTag blockEntityTag = blockEntity != null
                ? blockEntity.saveWithFullMetadata(
                        level.registryAccess())
                : null;
        return new BlockStateSnapshot(
                NbtUtils.writeBlockState(state),
                blockEntityTag);
    }

    private static BlockState decodeState(
            ServerLevel level, CompoundTag stateTag) {
        return NbtUtils.readBlockState(
                level.registryAccess()
                        .lookupOrThrow(
                                net.minecraft.core.registries
                                        .Registries.BLOCK),
                stateTag);
    }

    private static boolean validateBlockEntity(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            CompoundTag blockEntityTag) {
        if (blockEntityTag == null) {
            return true;
        }
        try {
            return BlockEntity.loadStatic(
                    position,
                    state,
                    blockEntityTag.copy(),
                    level.registryAccess()) != null;
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean canRecreate(
            EntitySnapshot snapshot) {
        ResourceLocation type =
                ResourceLocation.tryParse(snapshot.typeId());
        return type != null
                && BuiltInRegistries.ENTITY_TYPE
                        .containsKey(type)
                && snapshot.tag().contains(
                        "id", Tag.TAG_STRING);
    }

    private static boolean isLoadedTarget(
            ServerLevel level, Vec3 position) {
        BlockPos block = BlockPos.containing(position);
        return level.isLoaded(block)
                && level.getWorldBorder()
                        .isWithinBounds(block);
    }

    private static List<Entity> snapshotEntities(
            Iterable<Entity> entities) {
        List<Entity> snapshot = new ArrayList<>();
        for (Entity entity : entities) {
            snapshot.add(entity);
        }
        return snapshot;
    }

    private static Entity findEntity(
            MinecraftServer server, UUID uuid) {
        for (ServerLevel candidate :
                server.getAllLevels()) {
            Entity entity = candidate.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static void enterApply() {
        APPLY_DEPTH.set(APPLY_DEPTH.get() + 1);
    }

    private static void exitApply() {
        int depth = APPLY_DEPTH.get() - 1;
        if (depth <= 0) {
            APPLY_DEPTH.remove();
        }
        else {
            APPLY_DEPTH.set(depth);
        }
    }

    // isStopped turns true after the server's last tick and its ServerStoppingEvent, before stopServer saves the
    // levels and logs the players out (playerLoggedOut lands here). A stopped server gets a history that is not kept.
    private static ServerHistory history(
            MinecraftServer server) {
        synchronized (HISTORIES) {
            if (server.isStopped()) {
                HISTORIES.remove(server);
                return new ServerHistory();
            }
            return HISTORIES.computeIfAbsent(
                    server,
                    ignored -> new ServerHistory());
        }
    }

    // Read by the game tests.
    public static boolean hasHistoryForTests(MinecraftServer server) {
        synchronized (HISTORIES) {
            return HISTORIES.containsKey(server);
        }
    }

    public static long blockCapturesForTests() {
        return blockCaptures;
    }

    /** An empty mark of radius 0 around center, as setMark leaves one; for a server that has no players. */
    public static void markForTests(
            ServerLevel level, UUID owner, BlockPos center) {
        history(level.getServer()).marks.put(owner, new Mark(
                owner, level.dimension(), 0L, 0L, center, 0,
                Set.of(), Map.of(), Map.of(), Map.of()));
        refreshAnyMark();
    }

    /** The block records of the owner's mark, or -1 if it has none. */
    public static int markedBlocksForTests(
            MinecraftServer server, UUID owner) {
        ServerHistory history;
        synchronized (HISTORIES) {
            history = HISTORIES.get(server);
        }
        Mark mark = history != null ? history.marks.get(owner) : null;
        return mark != null ? mark.blocks().size() : -1;
    }

    private enum EntityOperation {
        RESTORE,
        REMOVE,
        RECREATE
    }

    private record BlockStateSnapshot(
            CompoundTag state,
            CompoundTag blockEntity) {}

    private record BlockInverse(
            BlockPos position,
            BlockStateSnapshot snapshot) {}

    private record EntityInverse(
            UUID uuid,
            EntitySnapshot snapshot) {}

    private record PlayerInverse(
            ServerPlayer live,
            PlayerSnapshot snapshot) {}

    private record StandInverse(
            StandEntity live,
            StandSnapshot snapshot) {}

    private record EntitySnapshot(
            UUID uuid,
            long tick,
            String typeId,
            CompoundTag tag,
            Vec3 position,
            boolean noPhysics,
            boolean living,
            boolean frozen,
            UUID mobTarget,
            UUID vehicle,
            UUID projectileOwner) {
        /** The same state at the place where the entity stands now. */
        private EntitySnapshot at(Entity entity) {
            CompoundTag placed = tag.copy();
            Vec3 motion = entity.getDeltaMovement();
            placed.put("Pos", doubles(
                    entity.getX(), entity.getY(), entity.getZ()));
            placed.put("Motion", doubles(
                    motion.x, motion.y, motion.z));
            ListTag rotation = new ListTag();
            rotation.add(FloatTag.valueOf(entity.getYRot()));
            rotation.add(FloatTag.valueOf(entity.getXRot()));
            placed.put("Rotation", rotation);
            placed.putFloat("FallDistance", entity.fallDistance);
            placed.putBoolean("OnGround", entity.onGround());
            return new EntitySnapshot(
                    uuid,
                    tick,
                    typeId,
                    placed,
                    entity.position(),
                    noPhysics,
                    living,
                    frozen,
                    mobTarget,
                    vehicle,
                    projectileOwner);
        }

        private static ListTag doubles(double x, double y, double z) {
            ListTag list = new ListTag();
            list.add(DoubleTag.valueOf(x));
            list.add(DoubleTag.valueOf(y));
            list.add(DoubleTag.valueOf(z));
            return list;
        }

        private static EntitySnapshot capture(
                Entity entity, long tick) {
            CompoundTag tag =
                    entity.saveWithoutId(new CompoundTag());
            ResourceLocation type =
                    BuiltInRegistries.ENTITY_TYPE
                            .getKey(entity.getType());
            if (type == null) {
                throw new IllegalStateException(
                        "Unregistered RTZ entity type");
            }
            tag.putString("id", type.toString());
            tag.remove("Passengers");
            UUID mobTarget =
                    entity instanceof Mob mob
                                    && mob.getTarget() != null
                            ? mob.getTarget().getUUID()
                            : null;
            UUID vehicle = entity.getVehicle() != null
                    ? entity.getVehicle().getUUID()
                    : null;
            UUID projectileOwner =
                    entity instanceof Projectile projectile
                                    && projectile.getOwner() != null
                            ? projectile.getOwner().getUUID()
                            : null;
            return new EntitySnapshot(
                    entity.getUUID(),
                    tick,
                    type.toString(),
                    tag,
                    entity.position(),
                    entity.noPhysics,
                    entity instanceof LivingEntity,
                    TimeStopState.shouldFreezeOnServer(entity),
                    mobTarget,
                    vehicle,
                    projectileOwner);
        }
    }

    private record PlayerSnapshot(
            UUID uuid,
            Vec3 position,
            float yRot,
            float xRot,
            ListTag inventory,
            int selectedSlot,
            UUID vehicle,
            float health,
            float absorption,
            int fireTicks,
            List<MobEffectInstance> effects) {
        private static PlayerSnapshot capture(
                ServerPlayer player) {
            List<MobEffectInstance> effects = new ArrayList<>();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                effects.add(new MobEffectInstance(effect));
            }
            return new PlayerSnapshot(
                    player.getUUID(),
                    player.position(),
                    player.getYRot(),
                    player.getXRot(),
                    player.getInventory()
                            .save(new ListTag()),
                    player.getInventory().selected,
                    player.getVehicle() != null
                            ? player.getVehicle().getUUID()
                            : null,
                    player.getHealth(),
                    player.getAbsorptionAmount(),
                    player.getRemainingFireTicks(),
                    List.copyOf(effects));
        }
    }

    /** Whose a restored entity is at the press. */
    private enum Side {
        THING,
        FRIEND,
        ENEMY
    }

    private record StandSnapshot(
            UUID uuid,
            Vec3 position,
            float yRot,
            float xRot,
            UUID vehicle) {
        private static StandSnapshot capture(
                StandEntity stand) {
            return new StandSnapshot(
                    stand.getUUID(),
                    stand.position(),
                    stand.getYRot(),
                    stand.getXRot(),
                    stand.getVehicle() != null
                            ? stand.getVehicle().getUUID()
                            : null);
        }
    }

    private record PoseFrame(
            long tick, Vec3 position) {}

    private static final class Mark {
        private final UUID owner;
        private final ResourceKey<Level> dimension;
        private final long tick;
        private final long dayTime;
        private final BlockPos center;
        private final int centerChunkX;
        private final int centerChunkZ;
        private final int chunkRadius;
        private final Set<InstanceKey> timeStopInstances;
        private final Map<UUID, EntitySnapshot> entities;
        private final Map<UUID, PlayerSnapshot> players;
        private final Map<UUID, StandSnapshot> stands;
        private final LinkedHashMap<BlockPos, BlockPreimage>
                blocks = new LinkedHashMap<>();
        private final Map<UUID, Deque<PoseFrame>>
                poseFrames = new HashMap<>();
        /** The marked creatures that died since, and whether each was the user's enemy as it died. */
        private final Map<UUID, Boolean> deaths = new HashMap<>();
        private int recordedBlockOps;

        private Mark(
                UUID owner,
                ResourceKey<Level> dimension,
                long tick,
                long dayTime,
                BlockPos center,
                int chunkRadius,
                Set<InstanceKey> timeStopInstances,
                Map<UUID, EntitySnapshot> entities,
                Map<UUID, PlayerSnapshot> players,
                Map<UUID, StandSnapshot> stands) {
            this.owner = owner;
            this.dimension = dimension;
            this.tick = tick;
            this.dayTime = dayTime;
            this.center = center.immutable();
            ChunkPos chunk = new ChunkPos(center);
            this.centerChunkX = chunk.x;
            this.centerChunkZ = chunk.z;
            this.chunkRadius = chunkRadius;
            this.timeStopInstances =
                    Set.copyOf(timeStopInstances);
            this.entities = Map.copyOf(entities);
            this.players = Map.copyOf(players);
            this.stands = Map.copyOf(stands);
        }

        private static Mark capture(
                ServerLevel level,
                ServerPlayer owner,
                int chunkRadius,
                ServerHistory history) {
            Set<InstanceKey> timeStopInstances =
                    captureTimeStopInstances(level, history);
            Mark shell = new Mark(
                    owner.getUUID(),
                    level.dimension(),
                    level.getGameTime(),
                    level.getDayTime(),
                    owner.blockPosition(),
                    chunkRadius,
                    timeStopInstances,
                    Map.of(),
                    Map.of(),
                    Map.of());
            Map<UUID, EntitySnapshot> entities =
                    new HashMap<>();
            Map<UUID, PlayerSnapshot> players =
                    new HashMap<>();
            Map<UUID, StandSnapshot> stands =
                    new HashMap<>();
            for (Entity entity :
                    snapshotEntities(level.getAllEntities())) {
                if (!shell.contains(entity.blockPosition())) {
                    continue;
                }
                if (entity instanceof ServerPlayer player) {
                    players.put(
                            player.getUUID(),
                            PlayerSnapshot.capture(player));
                }
                else if (entity instanceof StandEntity stand) {
                    stands.put(
                            stand.getUUID(),
                            StandSnapshot.capture(stand));
                }
                else {
                    entities.put(
                            entity.getUUID(),
                            EntitySnapshot.capture(
                                    entity,
                                    level.getGameTime()));
                }
            }
            Mark mark = new Mark(
                    owner.getUUID(),
                    level.dimension(),
                    level.getGameTime(),
                    level.getDayTime(),
                    owner.blockPosition(),
                    chunkRadius,
                    timeStopInstances,
                    entities,
                    players,
                    stands);
            mark.capturePoseFrame(
                    level, level.getGameTime());
            return mark;
        }

        private boolean contains(BlockPos position) {
            ChunkPos chunk = new ChunkPos(position);
            return Math.abs(chunk.x - centerChunkX)
                            <= chunkRadius
                    && Math.abs(chunk.z - centerChunkZ)
                            <= chunkRadius;
        }

        /** The chunks contains() takes, the level's full height. */
        private AABB region(Level level) {
            return regionAround(
                    level, centerChunkX, centerChunkZ, chunkRadius);
        }

        private boolean recordBlock(
                BlockPreimage preimage) {
            recordedBlockOps++;
            if (recordedBlockOps > MAX_BLOCK_OPS) {
                return false;
            }
            blocks.putIfAbsent(
                    preimage.position(), preimage);
            return true;
        }

        private void capturePoseFrame(
                ServerLevel level, long now) {
            List<Entity> candidates =
                    snapshotEntities(level.getAllEntities());
            candidates.removeIf(entity ->
                    !contains(entity.blockPosition())
                            || entity.getUUID().equals(owner)
                            || !wasPresentAtMark(
                                    entity.getUUID()));
            candidates.sort(Comparator.comparing(
                    entity -> entity.getUUID().toString()));
            int limit = Math.min(
                    candidates.size(),
                    GerRtzPayloads.MAX_TRAIL_ENTITIES);
            for (int candidateIndex = 0;
                    candidateIndex < limit;
                    candidateIndex++) {
                Entity entity = candidates.get(candidateIndex);
                Deque<PoseFrame> frames =
                        poseFrames.computeIfAbsent(
                                entity.getUUID(),
                                ignored -> new ArrayDeque<>());
                frames.addLast(new PoseFrame(
                        now, entity.position()));
                while (frames.size() > MAX_ENTITY_FRAMES) {
                    frames.removeFirst();
                }
            }
        }

        private boolean wasPresentAtMark(UUID uuid) {
            return entities.containsKey(uuid)
                    || players.containsKey(uuid)
                    || stands.containsKey(uuid);
        }

        private UUID owner() {
            return owner;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private long tick() {
            return tick;
        }

        private long dayTime() {
            return dayTime;
        }

        private BlockPos center() {
            return center;
        }

        private int centerChunkX() {
            return centerChunkX;
        }

        private int centerChunkZ() {
            return centerChunkZ;
        }

        private int chunkRadius() {
            return chunkRadius;
        }

        private Set<InstanceKey> timeStopInstances() {
            return timeStopInstances;
        }

        private Map<UUID, EntitySnapshot> entities() {
            return entities;
        }

        private Map<UUID, PlayerSnapshot> players() {
            return players;
        }

        private Map<UUID, StandSnapshot> stands() {
            return stands;
        }

        private LinkedHashMap<BlockPos, BlockPreimage>
                blocks() {
            return blocks;
        }

        private Map<UUID, Deque<PoseFrame>> poseFrames() {
            return poseFrames;
        }

        private Map<UUID, Boolean> deaths() {
            return deaths;
        }
    }

    private static final class ServerHistory {
        private final Map<UUID, Mark> marks =
                new HashMap<>();
        private final Map<UUID, PendingAutomaticRtz>
                pendingAutomaticRtz = new HashMap<>();
        private final Map<ResourceKey<Level>,
                GenerationTracker<TimeStopState.Instance>>
                activeTimeStops = new HashMap<>();
        private final Map<UUID, Presentation> presentations =
                new HashMap<>();
        private final Map<UUID, PendingEndSound>
                pendingEndSounds = new HashMap<>();
    }

    private record PendingAutomaticRtz(
            ResourceKey<Level> dimension,
            long executeServerTick,
            Mark mark) {}

    private record EntityMutation(
            EntityOperation operation,
            UUID uuid,
            Entity live,
            EntitySnapshot target,
            Side side) {
        private static EntityMutation restore(
                Entity live, EntitySnapshot target, Side side) {
            return new EntityMutation(
                    EntityOperation.RESTORE,
                    live.getUUID(),
                    live,
                    target,
                    side);
        }

        private static EntityMutation remove(Entity live) {
            return new EntityMutation(
                    EntityOperation.REMOVE,
                    live.getUUID(),
                    live,
                    null,
                    Side.THING);
        }

        private static EntityMutation recreate(
                EntitySnapshot target) {
            return new EntityMutation(
                    EntityOperation.RECREATE,
                    target.uuid(),
                    null,
                    target,
                    Side.THING);
        }
    }

    private record PlayerMutation(
            ServerPlayer live,
            PlayerSnapshot target,
            boolean restorePosition) {}

    private record StandMutation(
            StandEntity live,
            StandSnapshot target) {}

    private record RewindPlan(
            long dayTime,
            boolean restoreDayTime,
            List<BlockPreimage> blocks,
            List<EntityMutation> entities,
            List<PlayerMutation> players,
            List<StandMutation> stands,
            Set<UUID> disabledLiving) {}

    private record StageResult(
            RewindPlan plan,
            Result failure) {
        private static StageResult success(
                RewindPlan plan) {
            return new StageResult(plan, null);
        }

        private static StageResult failure(
                Result result) {
            return new StageResult(null, result);
        }
    }

    private record Presentation(
            UUID owner,
            ResourceKey<Level> dimension,
            long startTick,
            long endTick,
            Set<UUID> disabledLiving,
            StartPayload payload) {}

    private record PendingEndSound(
            ResourceKey<Level> dimension,
            long playTick,
            Vec3 center,
            UUID owner) {}
}
