package rotp.addons.gratefuldead.state;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.network.BlockAgingPayload;
import rotp.addons.gratefuldead.service.AgingService;
import rotp.core.impl.stands.goldexperience.GETreeLeavesDecayState;
import rotp.core.util.functions.JojoModUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BlockAgingData extends SavedData {
    public static final int MAX_BLOCK_RECORDS = 65_536;
    public static final int MAX_RECORDS_PER_CHUNK = 256;
    public static final int MAX_RECORD_TICKS_PER_TICK = 128;
    public static final int MAX_SPREADS_PER_TICK = 24;
    public static final int MAX_PENDING_SPREADS = 4_096;
    public static final int OWNER_FADE_TICKS = 60;
    // 0.1.11 AgingBlockTracker.BROADCAST_DELTA_THRESHOLD
    public static final float BROADCAST_DELTA = 0.04F;
    // 0.1.11 triggerTreeWither: TreeLeavesDecay.startDecay(world, log, 30, 6). The
    // donor started every tree at once; here one tree starts per interval.
    public static final int TREE_WITHER_INTERVAL_TICKS = 10;
    private static final int TREE_DECAY_TICKS = 30;
    private static final int TREE_DECAY_LEAVES_PER_TICK = 6;
    private static final int LEAF_LOG_SEARCH_LIMIT = 256;
    private static final float NEVER_SENT = -1.0F;
    private static final long NOT_STAMPED = -1L;
    private static final String FILE_NAME =
            "rotp_grateful_dead-block_aging";
    private static final String RECORDS_TAG = "Records";
    private static final SavedData.Factory<BlockAgingData>
            FACTORY = new SavedData.Factory<>(
                    BlockAgingData::new,
                    BlockAgingData::load);

    private final Map<Long, Entry> entries =
            new HashMap<>();
    private final Map<Long, LinkedHashSet<Long>> byChunk =
            new HashMap<>();
    private final ArrayDeque<Long> tickQueue =
            new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final ArrayDeque<Spread> spreadQueue =
            new ArrayDeque<>();
    private final ArrayDeque<Long> treeWitherQueue =
            new ArrayDeque<>();

    public static BlockAgingData get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static BlockAgingData load(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        BlockAgingData data = new BlockAgingData();
        for (Tag element :
                tag.getList(RECORDS_TAG, Tag.TAG_COMPOUND)) {
            if (data.entries.size() >= MAX_BLOCK_RECORDS) {
                break;
            }
            if (!(element instanceof CompoundTag entryTag)
                    || !entryTag.hasUUID("Owner")) {
                continue;
            }
            BlockPos position =
                    BlockPos.of(entryTag.getLong("Position"));
            float progress = Mth.clamp(
                    entryTag.getFloat("Progress"),
                    0.0F,
                    1.0F);
            boolean fading =
                    entryTag.getBoolean("Fading");
            int fadeTicksLeft = fading
                    ? Mth.clamp(
                            entryTag.contains(
                                            "FadeTicksLeft")
                                    ? entryTag.getInt(
                                            "FadeTicksLeft")
                                    : OWNER_FADE_TICKS,
                            1,
                            OWNER_FADE_TICKS)
                    : 0;
            float fadePerTick = fading
                    ? Mth.clamp(
                            entryTag.getFloat(
                                    "FadePerTick"),
                            0.0F,
                            1.0F)
                    : 0.0F;
            if (fading && fadePerTick <= 0.0F) {
                fadePerTick =
                        progress / fadeTicksLeft;
            }
            Entry entry = new Entry(
                    position,
                    entryTag.getUUID("Owner"),
                    Math.max(
                            0L,
                            entryTag.getLong(
                                    "OwnerGeneration")),
                    progress,
                    fading,
                    fadePerTick,
                    fadeTicksLeft,
                    NEVER_SENT,
                    NOT_STAMPED);
            long chunkKey =
                    new ChunkPos(position).toLong();
            Set<Long> chunkEntries =
                    data.byChunk.get(chunkKey);
            if (entry.progress() > 0.0F
                    && (chunkEntries == null
                            || chunkEntries.size()
                                    < MAX_RECORDS_PER_CHUNK)) {
                data.insert(entry);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        ListTag records = new ListTag();
        entries.values().stream()
                .sorted(Comparator.comparingLong(
                        entry -> entry.position().asLong()))
                .forEach(entry -> {
                    CompoundTag entryTag = new CompoundTag();
                    entryTag.putLong(
                            "Position",
                            entry.position().asLong());
                    entryTag.putUUID("Owner", entry.owner());
                    entryTag.putLong(
                            "OwnerGeneration",
                            entry.ownerGeneration());
                    entryTag.putFloat(
                            "Progress", entry.progress());
                    entryTag.putBoolean(
                            "Fading", entry.fading());
                    entryTag.putFloat(
                            "FadePerTick",
                            entry.fadePerTick());
                    entryTag.putInt(
                            "FadeTicksLeft",
                            entry.fadeTicksLeft());
                    records.add(entryTag);
                });
        tag.put(RECORDS_TAG, records);
        return tag;
    }

    public float progress(BlockPos position) {
        Entry entry = entries.get(position.asLong());
        return entry != null ? entry.progress() : 0.0F;
    }

    public Entry entry(BlockPos position) {
        return entries.get(position.asLong());
    }

    public int recordCount() {
        return entries.size();
    }

    public int tickQueueSize() {
        return tickQueue.size();
    }

    public boolean age(
            ServerLevel level,
            BlockPos position,
            UUID owner,
            long generation,
            float amount) {
        if (amount <= 0.0F
                || !level.hasChunkAt(position)
                || !AgingService.isAgeableBlock(
                        level.getBlockState(position))) {
            return false;
        }
        long key = position.asLong();
        Entry previous = entries.get(key);
        if (previous != null
                && !previous.owner().equals(owner)) {
            return false;
        }
        if (previous == null) {
            if (entries.size() >= MAX_BLOCK_RECORDS) {
                return false;
            }
            long chunkKey =
                    new ChunkPos(position).toLong();
            if (byChunk.getOrDefault(
                                    chunkKey,
                                    new LinkedHashSet<>())
                            .size()
                    >= MAX_RECORDS_PER_CHUNK) {
                return false;
            }
        }
        float before = previous != null
                ? previous.progress()
                : 0.0F;
        float nextProgress = Mth.clamp(
                before + amount,
                0.0F,
                1.0F);
        // A fully aged block in a running aura is hit again and again.
        if (previous != null
                && !previous.fading()
                && previous.ownerGeneration() == generation
                && nextProgress == before) {
            return true;
        }
        float lastSent = previous != null
                ? previous.lastSent()
                : NEVER_SENT;
        boolean send = significant(lastSent, nextProgress);
        Entry next = new Entry(
                position.immutable(),
                owner,
                generation,
                nextProgress,
                false,
                0.0F,
                0,
                send ? nextProgress : lastSent,
                NOT_STAMPED);
        replace(next);
        setDirty();
        if (send) {
            sync(level, next);
        }
        if (before < 1.0F && nextProgress >= 1.0F) {
            reachFullAge(level, next);
        }
        return true;
    }

    public void inherit(
            ServerLevel level,
            BlockPos position,
            ItemAgingState itemState) {
        if (itemState.progress() <= 0.0F
                || itemState.owner().isEmpty()) {
            return;
        }
        age(
                level,
                position,
                itemState.owner().get(),
                itemState.ownerGeneration(),
                itemState.progress());
    }

    public void remove(
            ServerLevel level, BlockPos position) {
        Entry removed =
                entries.remove(position.asLong());
        if (removed == null) {
            return;
        }
        removeIndexes(removed);
        setDirty();
        clearClient(level, position);
    }

    /** 0.1.13 Remove Aging on a block: its aging fades over the given ticks. */
    public boolean startFade(
            ServerLevel level, BlockPos position, int ticks) {
        Entry entry = entries.get(position.asLong());
        if (entry == null || entry.fading()) {
            return false;
        }
        int duration = Math.max(1, ticks);
        Entry next = new Entry(
                entry.position(),
                entry.owner(),
                entry.ownerGeneration(),
                entry.progress(),
                true,
                entry.progress() / duration,
                duration,
                entry.lastSent(),
                level.getGameTime());
        replace(next);
        setDirty();
        return true;
    }

    /** 0.1.11 AgedBlockContactHandler.handleEntityContact: a fully aged block touched by a player. */
    public void crumbleOnContact(
            ServerLevel level, AABB box) {
        if (entries.isEmpty()) {
            return;
        }
        AABB contact = box.inflate(0.08D);
        for (BlockPos position : BlockPos.betweenClosed(
                Mth.floor(contact.minX),
                Mth.floor(contact.minY - 0.05D),
                Mth.floor(contact.minZ),
                Mth.floor(contact.maxX),
                Mth.floor(contact.maxY + 0.05D),
                Mth.floor(contact.maxZ))) {
            Entry entry = entries.get(position.asLong());
            if (entry != null && entry.progress() >= 1.0F) {
                crumble(level, entry);
            }
        }
    }

    public void tick(ServerLevel level) {
        int recordBudget = Math.min(
                MAX_RECORD_TICKS_PER_TICK,
                tickQueue.size());
        for (int index = 0;
                index < recordBudget;
                index++) {
            Long key = tickQueue.pollFirst();
            if (key == null) {
                break;
            }
            queued.remove(key);
            Entry entry = entries.get(key);
            if (entry == null) {
                continue;
            }
            tickEntry(level, entry);
            if (entries.containsKey(key)) {
                enqueue(key);
            }
        }

        for (int index = 0;
                index < MAX_SPREADS_PER_TICK;
                index++) {
            Spread spread = spreadQueue.pollFirst();
            if (spread == null) {
                break;
            }
            // Removal ends the spreading it left queued.
            if (AgingRemovalData.get(level.getServer())
                            .generation(spread.owner())
                    > spread.ownerGeneration()) {
                continue;
            }
            age(
                    level,
                    spread.position(),
                    spread.owner(),
                    spread.ownerGeneration(),
                    spread.amount());
        }

        if (level.getGameTime() % TREE_WITHER_INTERVAL_TICKS == 0L) {
            witherNextTree(level);
        }
    }

    public void syncChunk(
            ServerPlayer player, ChunkPos chunk) {
        Set<Long> positions =
                byChunk.get(chunk.toLong());
        if (positions == null) {
            return;
        }
        int sent = 0;
        for (long key : positions) {
            Entry entry = entries.get(key);
            if (entry != null) {
                PacketDistributor.sendToPlayer(
                        player,
                        payload(entry));
                if (++sent >= MAX_RECORDS_PER_CHUNK) {
                    break;
                }
            }
        }
    }

    private void tickEntry(
            ServerLevel level, Entry entry) {
        boolean chunkLoaded = level.hasChunkAt(entry.position());
        if (chunkLoaded
                && !AgingService.isAgeableBlock(
                        level.getBlockState(entry.position()))) {
            remove(level, entry.position());
            return;
        }
        boolean removalRequested =
                AgingRemovalData.get(level.getServer())
                                .generation(entry.owner())
                        > entry.ownerGeneration();
        ServerPlayer ownerPlayer = level.getServer()
                .getPlayerList()
                .getPlayer(entry.owner());
        boolean outOfRange = ownerPlayer == null
                || !AgingService.hasGratefulDead(ownerPlayer)
                || ownerPlayer.level() != level
                || ownerPlayer.distanceToSqr(
                                entry.position().getX() + 0.5D,
                                entry.position().getY() + 0.5D,
                                entry.position().getZ() + 0.5D)
                        > AgingService.AURA_RADIUS
                                * AgingService.AURA_RADIUS;
        // No client tracks this chunk, so nothing has to be drawn: left alone the record
        // would never fade, come back with the chunk and count against the cap.
        if (!chunkLoaded) {
            if (removalRequested
                    || outOfRange
                    || entry.fading()) {
                remove(level, entry.position());
            }
            return;
        }
        if (removalRequested
                || outOfRange
                || entry.fading()) {
            int fadeTicksLeft = entry.fading()
                    ? entry.fadeTicksLeft()
                    : OWNER_FADE_TICKS;
            float fadePerTick = entry.fading()
                    ? entry.fadePerTick()
                    : entry.progress()
                            / OWNER_FADE_TICKS;
            long now = level.getGameTime();
            // A fade lasts its ticks, however rarely the 128-a-tick budget reaches
            // this record: per visit, 40000 records would fade for 16 minutes.
            int elapsed = entry.fadeStamp() != NOT_STAMPED
                    ? (int) Math.clamp(
                            now - entry.fadeStamp(),
                            1L,
                            (long) OWNER_FADE_TICKS)
                    : 1 + tickQueue.size()
                            / MAX_RECORD_TICKS_PER_TICK;
            float nextProgress = Math.max(
                    0.0F,
                    entry.progress()
                            - fadePerTick * elapsed);
            int nextFadeTicksLeft =
                    fadeTicksLeft - elapsed;
            if (nextProgress <= 0.0F
                    || nextFadeTicksLeft <= 0) {
                remove(level, entry.position());
                return;
            }
            boolean send = significant(
                    entry.lastSent(), nextProgress);
            Entry next = new Entry(
                    entry.position(),
                    entry.owner(),
                    entry.ownerGeneration(),
                    nextProgress,
                    true,
                    fadePerTick,
                    nextFadeTicksLeft,
                    send ? nextProgress : entry.lastSent(),
                    now);
            entries.put(
                    entry.position().asLong(), next);
            setDirty();
            if (send) {
                sync(level, next);
            }
        }
    }

    // 0.1.11 applyTerminalTransform: only trees, flowers and saplings change when
    // they reach full age; anything else stays until a player touches it.
    private void reachFullAge(
            ServerLevel level, Entry entry) {
        BlockPos position = entry.position();
        BlockState state = level.getBlockState(position);
        if (isTreePart(state)) {
            queueTreeNeighbors(level, entry);
            if (treeWitherQueue.size() < MAX_PENDING_SPREADS) {
                treeWitherQueue.addLast(position.asLong());
            }
        }
        else if (state.is(BlockTags.TALL_FLOWERS)
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos lower = state.getValue(
                            BlockStateProperties.DOUBLE_BLOCK_HALF)
                    == DoubleBlockHalf.LOWER
                    ? position
                    : position.below();
            BlockPos upper = lower.above();
            level.setBlock(
                    upper,
                    Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            level.setBlockAndUpdate(
                    lower,
                    Blocks.WITHER_ROSE.defaultBlockState());
            remove(level, lower);
            remove(level, upper);
        }
        else if (state.is(BlockTags.SMALL_FLOWERS)) {
            level.setBlockAndUpdate(
                    position,
                    Blocks.WITHER_ROSE.defaultBlockState());
            remove(level, position);
        }
        else if (state.is(BlockTags.SAPLINGS)) {
            level.setBlockAndUpdate(
                    position,
                    Blocks.DEAD_BUSH.defaultBlockState());
            remove(level, position);
        }
    }

    private void witherNextTree(ServerLevel level) {
        Long key;
        while ((key = treeWitherQueue.pollFirst()) != null) {
            Entry entry = entries.get(key);
            if (entry == null
                    || entry.fading()
                    || !level.hasChunkAt(entry.position())) {
                continue;
            }
            BlockPos position = entry.position();
            BlockState state = level.getBlockState(position);
            if (!isTreePart(state)) {
                continue;
            }
            if (!ownerMayBreakBlocks(level, entry)) {
                return;
            }
            BlockPos log = state.is(BlockTags.LOGS)
                    ? position
                    : findNearbyLog(level, position);
            if (log != null) {
                GETreeLeavesDecayState.startDecay(
                        level,
                        log,
                        TREE_DECAY_TICKS,
                        TREE_DECAY_LEAVES_PER_TICK);
            }
            else {
                Block.dropResources(state, level, position);
                level.destroyBlock(position, false);
                remove(level, position);
            }
            return;
        }
    }

    // 0.1.11 findNearbyLog
    private static BlockPos findNearbyLog(
            ServerLevel level, BlockPos seed) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        visited.add(seed);
        frontier.add(seed);
        while (!frontier.isEmpty()
                && visited.size() <= LEAF_LOG_SEARCH_LIMIT) {
            BlockPos current = frontier.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (visited.contains(next)
                        || !level.hasChunkAt(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.is(BlockTags.LOGS)) {
                    return next;
                }
                if (state.getBlock() instanceof LeavesBlock) {
                    visited.add(next);
                    frontier.add(next);
                }
            }
        }
        return null;
    }

    private static boolean isTreePart(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.getBlock() instanceof LeavesBlock;
    }

    // 0.1.11 isAgedSoil, crumblesOnContact and dropPlantAgingDust.
    private void crumble(
            ServerLevel level, Entry entry) {
        BlockPos position = entry.position();
        BlockState state = level.getBlockState(position);
        if (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.MYCELIUM)) {
            level.setBlockAndUpdate(
                    position,
                    Blocks.DIRT.defaultBlockState());
            remove(level, position);
        }
        else if (crumblesOnContact(state)
                && ownerMayBreakBlocks(level, entry)) {
            level.destroyBlock(position, false);
            if (level.random.nextInt(3) == 0) {
                ItemEntity dust = new ItemEntity(
                        level,
                        position.getX() + 0.5D,
                        position.getY() + 0.25D,
                        position.getZ() + 0.5D,
                        new ItemStack(
                                GratefulDeadAddon
                                        .PLANT_AGING_DUST.get()));
                dust.setDefaultPickUpDelay();
                level.addFreshEntity(dust);
            }
            remove(level, position);
        }
    }

    // What aging destroys is the Grateful Dead user's doing: the game rule and his Stand switch decide, as for any
    // Stand's blocks. An owner who is not online leaves the rule alone.
    private static boolean ownerMayBreakBlocks(
            ServerLevel level, Entry entry) {
        return JojoModUtil.breakingBlocksEnabled(
                level,
                level.getServer().getPlayerList().getPlayer(entry.owner()));
    }

    private static boolean crumblesOnContact(BlockState state) {
        Block block = state.getBlock();
        return block instanceof VineBlock
                || block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof BambooStalkBlock
                || block instanceof BambooSaplingBlock
                || block instanceof BushBlock
                || block instanceof GrowingPlantBlock
                || block == Blocks.PUMPKIN
                || block == Blocks.MELON;
    }

    private void queueTreeNeighbors(
            ServerLevel level, Entry source) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor =
                    source.position().relative(direction);
            if (level.hasChunkAt(neighbor)
                    && isTreePart(
                            level.getBlockState(neighbor))
                    && spreadQueue.size()
                            < MAX_PENDING_SPREADS) {
                spreadQueue.addLast(new Spread(
                        neighbor.immutable(),
                        source.owner(),
                        source.ownerGeneration(),
                        0.25F));
            }
        }
    }

    private void insert(Entry entry) {
        entries.put(entry.position().asLong(), entry);
        long chunkKey =
                new ChunkPos(entry.position()).toLong();
        byChunk.computeIfAbsent(
                        chunkKey,
                        ignored -> new LinkedHashSet<>())
                .add(entry.position().asLong());
        enqueue(entry.position().asLong());
    }

    private void replace(Entry entry) {
        if (entries.containsKey(entry.position().asLong())) {
            entries.put(entry.position().asLong(), entry);
            enqueue(entry.position().asLong());
        }
        else {
            insert(entry);
        }
    }

    private void enqueue(long key) {
        if (queued.add(key)) {
            tickQueue.addLast(key);
        }
    }

    // The key stays in tickQueue until its turn: taking it out of queued here
    // would let a record aged again take a second place in the queue for good.
    private void removeIndexes(Entry entry) {
        long key = entry.position().asLong();
        long chunkKey =
                new ChunkPos(entry.position()).toLong();
        Set<Long> chunkEntries = byChunk.get(chunkKey);
        if (chunkEntries != null) {
            chunkEntries.remove(key);
            if (chunkEntries.isEmpty()) {
                byChunk.remove(chunkKey);
            }
        }
    }

    private static void sync(
            ServerLevel level, Entry entry) {
        PacketDistributor.sendToPlayersTrackingChunk(
                level,
                new ChunkPos(entry.position()),
                payload(entry));
    }

    // 0.1.11 broadcastIfSignificant
    private static boolean significant(
            float lastSent, float progress) {
        return lastSent < 0.0F
                || progress >= 1.0F && lastSent < 1.0F
                || Math.abs(progress - lastSent) >= BROADCAST_DELTA;
    }

    private static BlockAgingPayload payload(Entry entry) {
        return new BlockAgingPayload(
                entry.position(), entry.progress());
    }

    private static void clearClient(
            ServerLevel level, BlockPos position) {
        PacketDistributor.sendToPlayersTrackingChunk(
                level,
                new ChunkPos(position),
                new BlockAgingPayload(position, 0.0F));
    }

    public record Entry(
            BlockPos position,
            UUID owner,
            long ownerGeneration,
            float progress,
            boolean fading,
            float fadePerTick,
            int fadeTicksLeft,
            float lastSent,
            long fadeStamp) {}

    private record Spread(
            BlockPos position,
            UUID owner,
            long ownerGeneration,
            float amount) {}
}
