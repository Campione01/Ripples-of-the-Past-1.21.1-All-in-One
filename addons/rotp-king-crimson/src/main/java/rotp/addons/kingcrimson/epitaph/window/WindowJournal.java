package rotp.addons.kingcrimson.epitaph.window;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureFeatureIndexSavedData;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.scores.ScoreboardSaveData;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.core.api.entity.EntityStateRestore;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * What the participants of one window do to the world while it is open (EPITAPH-ARCH 3.5), written down so that the
 * pull-back can undo it: who acts at every moment, the blocks that changed under him, the entities he made, and those he
 * hurt, used, took or removed. What cannot be put back makes him a hole: he is not pulled back, and what he did stays.
 * Nothing is kept on an entity or on disk: the journal is memory, and is gone with its window.
 */
public final class WindowJournal {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Whether any window is open. Every hook of the journal and of the walls starts by reading it. */
    public static boolean active;
    private static final List<WindowJournal> OPEN = new ArrayList<>();
    /** Who acts now, on the server thread: a participant of an open window, or nobody. */
    @Nullable
    private static Participant actor;

    /** One who is pulled back when the window closes, unless he became a hole. */
    public static final class Participant {
        final WindowJournal journal;
        Entity entity;
        final WindowSnapshot snapshot;
        /** Whose he is: a Stand is its user, a shot its shooter. A hole of the owner is his hole. */
        @Nullable
        final Participant owner;
        boolean hole;
        String why = "";

        private Participant(WindowJournal journal, Entity entity, @Nullable Participant owner) {
            this.journal = journal;
            this.entity = entity;
            this.owner = owner;
            this.snapshot = WindowSnapshot.of(entity);
        }

        /** The entity as it is now: the one of the press, or the one that was made again in its place. */
        public Entity entity() {
            return entity;
        }

        public boolean isHole() {
            return hole;
        }

        public String why() {
            return why;
        }
    }

    /** A block as it was before the first participant changed it, and as the last one left it. */
    static final class BlockEntry {
        final BlockState old;
        @Nullable
        final CompoundTag oldData;
        BlockState left;
        final Set<Participant> by = new LinkedHashSet<>();

        private BlockEntry(BlockState old, @Nullable CompoundTag oldData) {
            this.old = old;
            this.oldData = oldData;
            this.left = old;
        }
    }

    /** An entity nobody of the window made, as it was before a participant first hurt, used, took or removed it. */
    static final class Touched {
        final Entity entity;
        final EntityStateRestore.Snapshot state;
        final Set<Participant> by = new LinkedHashSet<>();

        private Touched(Entity entity) {
            this.entity = entity;
            this.state = EntityStateRestore.capture(entity);
        }
    }

    /** An entity a participant made in the window. Gone, it is fine only if it went while he, or it itself, acted. */
    static final class Made {
        final Entity entity;
        final Participant owner;
        boolean goneUnderOwner;

        private Made(Entity entity, Participant owner) {
            this.entity = entity;
            this.owner = owner;
        }
    }

    /** Told when a participant becomes a hole, by the journal itself or at the pull-back. */
    public interface Listener {
        void onHole(Entity participant, String why);
    }

    final ServerLevel level;
    final String name;
    private final Listener listener;
    final Map<Integer, Participant> participants = new LinkedHashMap<>();
    final Map<Entity, Made> made = new IdentityHashMap<>();
    final Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
    final Map<Entity, Touched> touched = new IdentityHashMap<>();
    private boolean open = true;

    private WindowJournal(ServerLevel level, String name, Listener listener) {
        this.level = level;
        this.name = name;
        this.listener = listener;
    }

    /** Opens the journal of a window in this level; the name is for the log. */
    public static WindowJournal open(ServerLevel level, String name, Listener listener) {
        WindowJournal journal = new WindowJournal(level, name, listener);
        OPEN.add(journal);
        active = true;
        return journal;
    }

    /** Takes an entity into the window as it is now: it will be put back so. Whose it is must have joined before it. */
    public Participant join(Entity entity, @Nullable Entity ownedBy) {
        Participant owner = ownedBy != null ? participants.get(ownedBy.getId()) : null;
        Participant joined = new Participant(this, entity, owner != null && owner.entity == ownedBy ? owner : null);
        participants.put(entity.getId(), joined);
        return joined;
    }

    /** No new entry from here, and the walls of this window are down. What was written stays for the pull-back. */
    public void close() {
        if (open) {
            open = false;
            OPEN.remove(this);
            active = !OPEN.isEmpty();
            if (actor != null && actor.journal == this) {
                actor = null;
            }
        }
    }

    public boolean isOpen() {
        return open;
    }

    public List<Participant> participants() {
        return List.copyOf(participants.values());
    }

    /** The participant this very entity is in this window, hole or not; null for anything else. */
    @Nullable
    public Participant of(@Nullable Entity entity) {
        Participant found = entity != null ? participants.get(entity.getId()) : null;
        return found != null && found.entity == entity ? found : null;
    }

    /** From here a bracket: what happens is the doing of the participant this entity is, was made by, or was first touched by. */
    public static void act(@Nullable Entity entity) {
        EpitaphSandbox.countPass();
        actor = entity != null ? actorOf(entity) : null;
    }

    /** From here a bracket: the tick of a block or a fluid at a place a participant changed is his who changed it last. */
    public static void actAt(Level level, BlockPos pos) {
        EpitaphSandbox.countPass();
        actor = null;
        for (WindowJournal journal : OPEN) {
            BlockEntry entry = journal.level == level ? journal.blocks.get(pos) : null;
            if (entry != null) {
                for (Participant by : entry.by) {
                    actor = by.hole ? actor : by;
                }
            }
        }
    }

    /** The same work as a bracket of its own: for what is queued to the server thread on behalf of this entity. */
    public static Runnable bracket(Entity entity, Runnable work) {
        return () -> bracket(entity, () -> {
            work.run();
            return null;
        }).get();
    }

    /** The same, for work that answers. Who acted around it acts again when it is done. */
    public static <T> Supplier<T> bracket(Entity entity, Supplier<T> work) {
        return () -> {
            Participant around = actor;
            act(entity);
            try {
                return work.get();
            }
            finally {
                actor = around;
            }
        };
    }

    /** The end of a bracket: nobody acts. */
    public static void rest() {
        actor = null;
    }

    @Nullable
    static Participant actor() {
        return actor;
    }

    /** The participant who stands behind an entity: itself, the one who made it, or the first who touched it. No hole. */
    @Nullable
    static Participant actorOf(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        for (WindowJournal journal : OPEN) {
            Participant found = journal.of(entity);
            if (found == null) {
                Made thing = journal.made.get(entity);
                found = thing != null ? thing.owner : null;
            }
            if (found == null) {
                Touched other = journal.touched.get(entity);
                found = other != null && !other.by.isEmpty() ? other.by.iterator().next() : null;
            }
            if (found != null) {
                return found.hole ? null : found;
            }
        }
        return null;
    }

    /** Who acts now, or failing that who stands behind the first of these entities that somebody stands behind. */
    @Nullable
    static Participant actorOr(@Nullable Entity first, @Nullable Entity second) {
        if (actor != null) {
            return actor;
        }
        Participant behind = actorOf(first);
        return behind != null ? behind : actorOf(second);
    }

    /** The participant of an open window this very entity is, null for a hole and for anything else. */
    @Nullable
    public static Participant participantOf(@Nullable Entity entity) {
        for (WindowJournal journal : OPEN) {
            Participant found = journal.of(entity);
            if (found != null) {
                return found.hole ? null : found;
            }
        }
        return null;
    }

    public static boolean isParticipant(@Nullable Entity entity) {
        return active && participantOf(entity) != null;
    }

    /** Whether what this player earns is held back: he is in a window, and what he does may be undone. */
    public static boolean holdsBack(@Nullable Player player) {
        return participantOf(player) != null;
    }

    /** The participant who made this entity in an open window, null when nobody did. */
    @Nullable
    static Participant ownerOf(@Nullable Entity entity) {
        for (WindowJournal journal : OPEN) {
            Made thing = entity != null ? journal.made.get(entity) : null;
            if (thing != null) {
                return thing.owner;
            }
        }
        return null;
    }

    /** Whether a block was changed in an open window by somebody other than this participant: it is theirs till the window ends. */
    static boolean changedByOthers(LevelAccessor level, BlockPos pos, @Nullable Participant by) {
        for (WindowJournal journal : OPEN) {
            BlockEntry entry = journal.level == level ? journal.blocks.get(pos) : null;
            if (entry != null && (by == null || !entry.by.contains(by))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a participant of an open window made this entity. */
    public static boolean isMade(@Nullable Entity entity) {
        return active && ownerOf(entity) != null;
    }

    /** Whether this item is kept where it can be found again: a participant dropped it, and the walls stand. */
    public static boolean walls(ItemEntity item) {
        return WindowBoundary.wallsOn && ownerOf(item) != null;
    }

    /** Whether what a participant made is kept from this player: only he collects it, while the walls stand. */
    public static boolean keeps(Entity made, Player from) {
        Participant owner = WindowBoundary.wallsOn ? ownerOf(made) : null;
        return owner != null && owner != participantOf(from);
    }

    /** A participant is gone from the game as it was at the press (dead, logged out, elsewhere): he is a hole. */
    public static void holeIfParticipant(Entity entity, String why) {
        Participant found = participantOf(entity);
        if (found != null) {
            found.journal.hole(found, why);
        }
    }

    /** He is not pulled back: what he made is his for good, and what he did stays. Whose owner he is goes with him. */
    void hole(Participant gone, String why) {
        if (gone.hole) {
            return;
        }
        leave(gone.entity, why);
        listener.onHole(gone.entity, why);
        for (Participant other : List.copyOf(participants.values())) {
            if (other.owner == gone) {
                hole(other, "whose it is is not pulled back");
            }
        }
    }

    /** The prediction itself says that this participant is out: the same, with no word back to it. */
    public void leave(Entity entity, String why) {
        Participant found = of(entity);
        if (found != null && !found.hole) {
            found.hole = true;
            found.why = why;
            made.values().removeIf(thing -> thing.owner == found);
            if (actor == found) {
                actor = null;
            }
            LOGGER.info("Epitaph[hole] {}: {} is not pulled back: {}", name, describe(found.entity), why);
        }
    }

    static String describe(Entity entity) {
        return entity instanceof Player player ? player.getScoreboardName()
                : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + "#" + entity.getId();
    }

    /** From the head of Level.setBlock: a block is about to change while a participant acts. Kept once, as it was. */
    public static void blockChanging(Level level, BlockPos pos) {
        EpitaphSandbox.countPass();
        Participant by = actor;
        if (by == null || by.journal.level != level) {
            return;
        }
        BlockEntry entry = by.journal.blocks.get(pos);
        if (entry == null) {
            BlockEntity data = level.getBlockEntity(pos);
            entry = new BlockEntry(level.getBlockState(pos), data != null ? data.saveWithFullMetadata(level.registryAccess()) : null);
            by.journal.blocks.put(pos.immutable(), entry);
        }
        entry.by.add(by);
    }

    /** From the end of Level.setBlock: how the participant left the block. Changed again by another hand, it is not put back. */
    public static void blockChanged(Level level, BlockPos pos) {
        Participant by = actor;
        BlockEntry entry = by != null && by.journal.level == level ? by.journal.blocks.get(pos) : null;
        if (entry != null) {
            entry.left = level.getBlockState(pos);
        }
    }

    /** A block entity changed while a participant acted, where he changed no block: what went into it cannot be found again. */
    public static void blockEntityChanged(Level level, BlockPos pos) {
        Participant by = actor;
        if (by != null && by.journal.level == level && !by.journal.blocks.containsKey(pos)) {
            by.journal.hole(by, "a block entity changed under him at " + pos.toShortString());
        }
    }

    /**
     * An entity came into the level. It is his who acts; with nobody acting (what a key of a mod set off runs outside any
     * bracket), his who shot it, threw it, or whose Stand it is.
     */
    public static void entityAdded(Entity entity) {
        EpitaphSandbox.countPass();
        Participant owner = actor;
        if (owner == null) {
            owner = actorOf(entity instanceof Projectile shot ? shot.getOwner() : entity instanceof ItemEntity item ? item.getOwner()
                    : entity instanceof StandEntity stand ? stand.getUser() : null);
        }
        if (owner != null && owner.journal.level == entity.level() && owner.journal.of(entity) == null) {
            mark(owner, entity);
        }
    }

    static void mark(Participant owner, Entity entity) {
        if (owner.journal.open && !owner.hole) {
            owner.journal.made.put(entity, new Made(entity, owner));
        }
    }

    /**
     * From the head of Entity.setRemoved. What a participant made may go while he, or it itself, acts: an arrow that hit,
     * an item he took back. What he did not make and takes out of the world is kept as it is, to be made again.
     */
    public static void entityRemoving(Entity entity, Entity.RemovalReason reason) {
        if (entity.level().isClientSide()) {
            return;
        }
        EpitaphSandbox.countPass();
        for (WindowJournal journal : OPEN) {
            Made thing = journal.made.get(entity);
            if (thing != null) {
                thing.goneUnderOwner = reason.shouldDestroy() && actor == thing.owner;
                return;
            }
        }
        if (actor != null && reason.shouldDestroy()) {
            touch(actor, entity);
        }
    }

    /** An entity nobody of the window made is hurt, used, taken or removed by a participant: kept as it is now, once. */
    static void touch(Participant by, Entity entity) {
        WindowJournal journal = by.journal;
        if (!journal.open || by.hole || entity instanceof Player || entity.level() != journal.level || journal.of(entity) != null
                || journal.made.containsKey(entity)) {
            return;
        }
        Touched kept = journal.touched.get(entity);
        if (kept == null) {
            kept = new Touched(entity);
            journal.touched.put(entity, kept);
        }
        kept.by.add(by);
    }

    /** Saved data of the world was marked changed while a participant acted: but for what the game itself keeps so, a hole. */
    public static void savedDataChanged(SavedData data) {
        EpitaphSandbox.countPass();
        Participant by = actor;
        if (by == null || data instanceof MapItemSavedData || data instanceof MapIndex || data instanceof ScoreboardSaveData
                || data instanceof Raids || data instanceof RandomSequences || data instanceof ForcedChunksSavedData
                || data instanceof StructureFeatureIndexSavedData) {
            return;
        }
        by.journal.hole(by, "saved data changed under him: " + data.getClass().getName());
    }
}
