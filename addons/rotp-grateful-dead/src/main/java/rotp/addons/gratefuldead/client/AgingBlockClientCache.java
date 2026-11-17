package rotp.addons.gratefuldead.client;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.service.AgingService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

/**
 * Thread-safe read side for section-build workers and main-thread updates.
 */
public final class AgingBlockClientCache {
    public static final float SMOOTH_STEP_PER_TICK = 0.025F;
    public static final float RENDER_THRESHOLD = 0.04F;
    public static final float SNAP_TO_TARGET_EPSILON = 0.001F;
    // A running aura changes blocks in every surface section it reaches; each
    // section is rebuilt once however many of its blocks changed.
    public static final int MAX_SECTION_REBUILDS_PER_TICK = 8;

    private static final Map<BlockPos, Entry> ENTRIES =
            new ConcurrentHashMap<>();
    private static final Set<BlockPos> MOVING = new HashSet<>();
    // A chunk line unloads a whole row of chunks at once: each must touch its own entries only.
    private static final Map<Long, Set<BlockPos>> BY_CHUNK = new HashMap<>();
    private static final ArrayDeque<Long> SECTION_QUEUE =
            new ArrayDeque<>();
    private static final Set<Long> SECTIONS_QUEUED =
            new HashSet<>();

    private AgingBlockClientCache() {}

    public static void handleUpdate(
            BlockPos position, float progress) {
        if (position == null) {
            return;
        }
        BlockPos key = position.immutable();
        float clamped = progress <= 0.0F
                ? 0.0F
                : Math.min(progress, 1.0F);
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            if (clamped <= 0.0F) {
                return;
            }
            entry = new Entry();
            ENTRIES.put(key, entry);
            BY_CHUNK.computeIfAbsent(chunkKey(key), chunk -> new HashSet<>()).add(key);
        }
        entry.target = clamped;
        MOVING.add(key);
        if (entry.display == 0.0F
                && clamped > 0.0F
                && clamped < 0.05F) {
            entry.display = clamped;
            renderIfNeeded(key, entry);
        }
    }

    public static float getProgress(BlockPos position) {
        if (position == null) {
            return 0.0F;
        }
        Entry entry = ENTRIES.get(position);
        return entry != null ? entry.display : 0.0F;
    }

    public static void clear() {
        ENTRIES.clear();
        BY_CHUNK.clear();
        MOVING.clear();
        SECTION_QUEUE.clear();
        SECTIONS_QUEUED.clear();
    }

    /** The server sends a chunk's records again when the chunk is sent again. */
    public static void forgetChunk(int chunkX, int chunkZ) {
        Set<BlockPos> positions = BY_CHUNK.remove(ChunkPos.asLong(chunkX, chunkZ));
        if (positions != null) {
            ENTRIES.keySet().removeAll(positions);
            MOVING.removeAll(positions);
        }
    }

    private static long chunkKey(BlockPos position) {
        return ChunkPos.asLong(
                SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getZ()));
    }

    private static void forgetEntry(BlockPos position, Entry entry) {
        if (ENTRIES.remove(position, entry)) {
            long chunk = chunkKey(position);
            Set<BlockPos> positions = BY_CHUNK.get(chunk);
            if (positions != null) {
                positions.remove(position);
                if (positions.isEmpty()) {
                    BY_CHUNK.remove(chunk);
                }
            }
        }
    }

    public static void tickClient() {
        tickBlockEntries();
        processSectionQueue();
    }

    static boolean hasActiveAura(Player player) {
        return auraTicks(player) >= 0;
    }

    /** The ticks the player's aging aura has been on, -1 without one. */
    static int auraTicks(Player player) {
        if (player == null || !player.isAlive()) {
            return -1;
        }
        StandPower power = StandPower.get(player);
        return power == null
                ? -1
                : power.userStandEffects
                        .getEffectOfType(
                                GratefulDeadAddon
                                        .AGING_AURA_EFFECT.get())
                        .map(effect -> effect.tickCount)
                        .orElse(-1);
    }

    /** 0.1.13: the radius the aura has grown to, at least 1 for its visuals. */
    static double auraRadius(Player player) {
        return Math.max(
                1.0D, AgingService.auraRadius(auraTicks(player)));
    }

    static boolean hasWarmActiveAura(Player player) {
        return hasActiveAura(player)
                && !AgingService.isCold(
                        player.level(),
                        player.blockPosition());
    }

    private static void tickBlockEntries() {
        Iterator<BlockPos> moving = MOVING.iterator();
        while (moving.hasNext()) {
            BlockPos position = moving.next();
            Entry entry = ENTRIES.get(position);
            if (entry == null) {
                moving.remove();
                continue;
            }
            float difference = entry.target - entry.display;
            if (Math.abs(difference)
                    <= SNAP_TO_TARGET_EPSILON) {
                if (entry.target <= 0.0F) {
                    if (entry.lastRendered > 0.0F) {
                        entry.display = 0.0F;
                        renderIfNeeded(position, entry);
                    }
                    forgetEntry(position, entry);
                }
                moving.remove();
                continue;
            }
            entry.display =
                    Math.abs(difference)
                                    <= SMOOTH_STEP_PER_TICK
                            ? entry.target
                            : Mth.clamp(
                                    entry.display
                                            + Math.signum(difference)
                                                    * SMOOTH_STEP_PER_TICK,
                                    0.0F,
                                    1.0F);
            renderIfNeeded(position, entry);
        }
    }

    private static void processSectionQueue() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.levelRenderer == null) {
            return;
        }
        for (int index = 0;
                index < MAX_SECTION_REBUILDS_PER_TICK;
                index++) {
            Long key = SECTION_QUEUE.pollFirst();
            if (key == null) {
                break;
            }
            SECTIONS_QUEUED.remove(key);
            minecraft.levelRenderer.setSectionDirty(
                    SectionPos.x(key),
                    SectionPos.y(key),
                    SectionPos.z(key));
        }
    }

    private static void renderIfNeeded(
            BlockPos position, Entry entry) {
        boolean crossesZero = entry.lastRendered > 0.0F
                && entry.display <= 0.0F;
        boolean reachesFull = entry.lastRendered < 1.0F
                && entry.display >= 0.999F;
        if (Math.abs(
                                entry.display
                                        - entry.lastRendered)
                        >= RENDER_THRESHOLD
                || crossesZero
                || reachesFull) {
            // LevelRenderer.setBlockDirty(pos, state, state) does nothing for an
            // unchanged state, unlike the 1.16 notifyBlockUpdate the donor called.
            long section = SectionPos.asLong(position);
            if (SECTIONS_QUEUED.add(section)) {
                SECTION_QUEUE.addLast(section);
            }
            entry.lastRendered = entry.display;
        }
    }

    private static final class Entry {
        private volatile float target;
        private volatile float display;
        private volatile float lastRendered;
    }
}
