package rotp.addons.kingcrimson.epitaph.sandbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.EpitaphRecord;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.view.PhantomTracker;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionReplayState;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * The phantoms of one prediction, and what is theirs alone: the blocks they changed, their random streams, their
 * path cache, their clock. The phantoms stand in the real level without being registered in it; while the room is
 * entered, lookups made in the level answer from here.
 */
public final class SandboxRoom {
    /** One phantom and what the room keeps about it. */
    public static final class Phantom {
        public final Entity entity;
        public final ActorKey key;
        public final ActorKey owner;
        public final boolean child;
        public final EntityTrack track;
        /** The game's own bookkeeping of what a client was told of it: what changed in a tick is asked of it. */
        public final PhantomTracker tracker;
        /** What was taken out of the saved data the copy was made from, to be put back at the end. */
        public final Map<String, Tag> stripped;
        boolean ended;
        /** Not shown to the one who foresees, whatever its owner: the loot of a death that will not happen. */
        boolean unseen;
        boolean atEdge;
        boolean dying;
        boolean faulted;
        @Nullable
        ItemStack[] equipment;
        @Nullable
        Entity vehicle;
        final List<ActorKey> taken = new ArrayList<>();
        /** Per action slot: the generation the room last saw there, and whether it then held an action. */
        final long[] actionGenerations = new long[2];
        final boolean[] acting = new boolean[2];
        final ActionReplayState[] takenOver = new ActionReplayState[2];
        /** Per action slot: the phase the room last saw there. */
        final ActionPhase[] phases = new ActionPhase[2];
        /** What its Stand power waited for when the room last looked, and the tick of that look. */
        @Nullable
        List<PhantomActions.Wait> waits;
        int waitsAt;
        /** Something happened in the tick being lived by which a wait of its power may have risen. */
        boolean waitsDue;

        private Phantom(Entity entity, ActorKey owner, boolean child, EntityTrack track, Map<String, Tag> stripped,
                PhantomTracker tracker) {
            this.entity = entity;
            this.tracker = tracker;
            this.key = ActorKey.of(entity);
            this.owner = owner;
            this.child = child;
            this.track = track;
            this.stripped = stripped;
        }

        public boolean ended() {
            return ended;
        }

        /** By slot: what this copy took over from its body at the press, null where it took nothing. */
        public ActionReplayState[] takenOver() {
            return takenOver;
        }
    }

    /** What went wrong in a room, counted for the summary line of its prediction. */
    public static final class Counts {
        public int phantomFaults;
        public int notCopied;
        public int notShown;
        /** Packets room code sent straight to a real player (the core finds the tracker of a body by the int id): dropped. */
        public int dropped;
    }

    private final Counts counts = new Counts();
    private final ServerLevel level;
    private final EpitaphRecord record;
    private final int maxChildren;
    private final PhantomUuidRemap uuids = new PhantomUuidRemap();
    private final Map<Integer, Phantom> byId = new LinkedHashMap<>();
    private final Map<UUID, Phantom> byUuid = new HashMap<>();
    private final List<ServerPlayer> humans = new ArrayList<>();
    /** Roots whose part of the future is no longer shown: they, and what they make from now on. */
    private final Set<Integer> hidden = new HashSet<>();
    /** Roots whose Stand is taken over by no copy, though they will be held to the record. */
    private final Set<Integer> leftOut = new HashSet<>();
    private final SandboxOverlay overlay = new SandboxOverlay();
    private final SandboxEntityGetter getter = new SandboxEntityGetter(this);
    private final PathTypeCache pathTypes = new PathTypeCache();
    private final Map<ResourceLocation, RandomSource> sequences = new HashMap<>();
    private final Long2ObjectOpenHashMap<LevelChunk> edgeChunks = new Long2ObjectOpenHashMap<>();
    private final long gameTime;
    private final long dayTime;
    @Nullable
    private RandomState random;
    @Nullable
    private RandomState realRandom;
    private int tick;
    private boolean stepping;
    private int children;
    private int muted;
    private int silenced;
    private int exploding;
    @Nullable
    private SandboxView view;
    // While the drops of a dying phantom are gathered: what is added is its loot, not a spawn of its own.
    @Nullable
    private List<Entity> deathLoot;

    public SandboxRoom(ServerLevel level, EpitaphRecord record, int maxChildren) {
        this.level = level;
        this.record = record;
        this.maxChildren = maxChildren;
        this.gameTime = level.getGameTime();
        this.dayTime = level.getDayTime();
        this.random = RandomState.of(level.random);
    }

    public ServerLevel level() {
        return level;
    }

    public EpitaphRecord record() {
        return record;
    }

    public PhantomUuidRemap uuids() {
        return uuids;
    }

    public Counts counts() {
        return counts;
    }

    /** From now on whoever foresees is shown what the phantoms do. */
    public void watch(SandboxView eyes) {
        view = eyes;
    }

    @Nullable
    public SandboxView view() {
        return view;
    }

    /** Whether this phantom is shown to the one who foresees. */
    public boolean shows(@Nullable Entity phantom) {
        Phantom info = info(phantom);
        return view != null && info != null && !hides(info) && view.shows(phantom);
    }

    private boolean hides(Phantom phantom) {
        return phantom.unseen || hidden.contains(phantom.owner.id());
    }

    /** From now on this root and whatever it makes are not shown; the room lives on with them. */
    public void hide(ActorKey root) {
        hidden.add(root.id());
    }

    /** What the Stand of this root is in the middle of at the press is taken over by no copy. */
    public void leaveOut(ActorKey root) {
        leftOut.add(root.id());
    }

    boolean leavesOut(ActorKey root) {
        return leftOut.contains(root.id());
    }

    /** Whether what this phantom does is no longer shown. */
    public boolean hides(@Nullable Entity phantom) {
        Phantom info = info(phantom);
        return info != null && hides(info);
    }

    /** This one phantom is not shown any more. */
    public void unsee(ActorKey key) {
        Phantom phantom = byId.get(key.id());
        if (phantom != null) {
            phantom.unseen = true;
        }
    }

    /** A packet for the eyes the room is lived for, of the tick the room is working on: something the acting phantom did. */
    public void show(Packet<?> packet) {
        showOf(EpitaphSandbox.actor(), packet, 0);
    }

    /** A packet that tells of this phantom, for a tick that is this many ticks ahead of the one being worked on. */
    public void showOf(@Nullable Entity about, Packet<?> packet, int ticksFromNow) {
        if (view == null || silenced > 0) {
            return;
        }
        Phantom info = info(about);
        if (info == null || !hides(info)) {
            view.show(workingTick() + ticksFromNow, info != null ? info.key.id() : -1, packet);
        }
    }

    /** While silenced, what room code would send is neither recorded nor shown. */
    void silence() {
        silenced++;
    }

    void unsilence() {
        silenced--;
    }

    /** How many ticks of the future the room has lived. */
    public int tick() {
        return tick;
    }

    /** The tick whose ops are being made: the one a step is working on, else the last one lived. */
    public int workingTick() {
        return stepping ? tick + 1 : tick;
    }

    void beginStep() {
        stepping = true;
    }

    void endStep() {
        stepping = false;
        tick++;
    }

    public long gameTime() {
        return gameTime + workingTick();
    }

    public long dayTime() {
        return dayTime + workingTick();
    }

    public SandboxOverlay overlay() {
        return overlay;
    }

    public PathTypeCache pathTypes() {
        return pathTypes;
    }

    public LevelEntityGetter<Entity> getter() {
        return getter;
    }

    /** The human phantoms: the players of the level, as room code sees them. */
    public List<ServerPlayer> players() {
        return humans;
    }

    @Nullable
    public Entity phantom(int id) {
        Phantom phantom = byId.get(id);
        return phantom != null && !phantom.ended ? phantom.entity : null;
    }

    @Nullable
    public Entity phantom(UUID uuid) {
        Phantom phantom = byUuid.get(uuid);
        return phantom != null && !phantom.ended ? phantom.entity : null;
    }

    @Nullable
    public Phantom info(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        Phantom phantom = byId.get(entity.getId());
        return phantom != null && phantom.entity == entity ? phantom : null;
    }

    @Nullable
    public Phantom info(ActorKey key) {
        return byId.get(key.id());
    }

    /** Whether this very object is a phantom of the room. A body has its phantom's id and is not. */
    public boolean owns(@Nullable Entity entity) {
        return info(entity) != null;
    }

    /**
     * Whether room code has reached something real: an entity that is in the level and is no phantom of the room. What
     * room code has just made and not yet added (a Stand its user is summoning, a child before it is taken in) is neither.
     */
    public boolean isReal(@Nullable Entity entity) {
        return entity != null && !owns(entity) && entity.isAddedToLevel();
    }

    public List<Phantom> all() {
        return new ArrayList<>(byId.values());
    }

    /** The phantoms that are still in the room. */
    public List<Entity> phantoms() {
        List<Entity> present = new ArrayList<>(byId.size());
        for (Phantom phantom : byId.values()) {
            if (!phantom.ended) {
                present.add(phantom.entity);
            }
        }
        return present;
    }

    /** Puts a root phantom in the room; its track starts at the press with the state it was copied in. */
    public Phantom addRoot(Entity phantom, ActorKey owner, Map<String, Tag> stripped) {
        return add(phantom, owner, false, stripped);
    }

    private Phantom add(Entity entity, ActorKey owner, boolean child, Map<String, Tag> stripped) {
        ActorKey key = ActorKey.of(entity);
        PhantomIdentity.mark(entity);
        EntityTrack track = record.open(key, owner.isNone() ? key : owner, child ? workingTick() : 0, child);
        Phantom phantom = new Phantom(entity, owner.isNone() ? key : owner, child, track, stripped,
                new PhantomTracker(level, entity));
        byId.put(key.id(), phantom);
        byUuid.put(entity.getUUID(), phantom);
        if (entity instanceof ServerPlayer human) {
            humans.add(human);
        }
        return phantom;
    }

    /**
     * An entity that room code adds to the level: it is not added. It becomes a child of the room, with the id its
     * constructor drew, and is recorded to be spawned for real at this tick. Past the cap of children, or for a type
     * that is not predicted, it is recorded and not simulated.
     */
    public boolean adopt(Entity child) {
        if (child.isRemoved() || owns(child)) {
            return false;
        }
        if (deathLoot != null) {
            deathLoot.add(child);
            return true;
        }
        Entity actor = EpitaphSandbox.actor();
        Phantom maker = info(actor);
        boolean free = child instanceof ItemEntity || child instanceof ExperienceOrb;
        boolean simulated = children < maxChildren && !SandboxGuards.denied(child.getType());
        if (child instanceof StandEntity stand) {
            record(new RecordedOp.StandSummon(ActorKey.of(actor), ActorKey.of(child), ActorKey.of(stand.getUser()),
                    stand.isArmsOnlyMode(), stand.showArm(InteractionHand.MAIN_HAND), stand.showArm(InteractionHand.OFF_HAND)));
        } else {
            record(new RecordedOp.Spawn(ActorKey.of(actor), ActorKey.of(child), child.getType(),
                    child.saveWithoutId(new CompoundTag()), free || !simulated));
        }
        if (simulated) {
            children++;
            Phantom born = add(child, maker != null ? maker.owner : ActorKey.NONE, true, Map.of());
            child.onAddedToLevel();
            SandboxDriver.pair(this, born);
            SandboxDriver.joinQuietly(this, child);
        }
        return true;
    }

    /** A phantom is about to press an ability: after the tick its power is looked at for what the press set. */
    public void notePress(Entity presser) {
        Phantom phantom = info(presser);
        if (phantom != null && presser instanceof LivingEntity living) {
            if (phantom.waits == null) {
                phantom.waits = PhantomActions.cooldowns(living);
                phantom.waitsAt = workingTick() - 1;
            }
            phantom.waitsDue = true;
        }
    }

    /** A phantom made by the room itself for the loot of a death: in the room, with no spawn of its own. */
    Phantom addLoot(Entity loot, ActorKey owner) {
        children++;
        Phantom phantom = add(loot, owner, true, Map.of());
        // What a hidden phantom kills will not die of it for real: its loot is not shown.
        phantom.unseen = hides(EpitaphSandbox.actor());
        loot.onAddedToLevel();
        SandboxDriver.pair(this, phantom);
        return phantom;
    }

    void beginDeathLoot() {
        deathLoot = new ArrayList<>();
    }

    List<Entity> endDeathLoot() {
        List<Entity> loot = deathLoot != null ? deathLoot : List.of();
        deathLoot = null;
        return loot;
    }

    void end(Phantom phantom) {
        phantom.ended = true;
        if (phantom.entity instanceof ServerPlayer human) {
            humans.remove(human);
        }
    }

    public void record(RecordedOp op) {
        if (muted == 0) {
            record.add(workingTick(), op);
        }
    }

    /** While muted, what room code does to the world is absorbed without a record: it will not happen again for real. */
    public void mute() {
        muted++;
    }

    public void unmute() {
        muted--;
    }

    public boolean muted() {
        return muted > 0;
    }

    /** A random sequence of the room in place of a saved one of the world, which is not to be drawn from. */
    public RandomSource sequence(ResourceLocation id) {
        return sequences.computeIfAbsent(id, key -> RandomSource.create(gameTime * 31L + key.hashCode()));
    }

    /** The chunk room code gets where none is loaded: empty, never stored. The acting phantom stops there. */
    public LevelChunk edgeChunk(int x, int z) {
        Phantom acting = info(EpitaphSandbox.actor());
        if (acting != null) {
            acting.atEdge = true;
        }
        return edgeChunks.computeIfAbsent(ChunkPos.asLong(x, z), key -> new EmptyLevelChunk(level, new ChunkPos(x, z),
                level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS)));
    }

    void onEnter() {
        if (random != null) {
            realRandom = RandomState.of(level.random);
            random.writeTo(level.random);
        }
    }

    void onExit() {
        if (random != null && realRandom != null) {
            random = RandomState.of(level.random);
            realRandom.writeTo(level.random);
            realRandom = null;
        }
    }

    void beginExplosion() {
        exploding++;
    }

    void endExplosion() {
        exploding--;
    }

    /** An explosion of the room is at work: the game event of the blast is the real one's to post. */
    public boolean exploding() {
        return exploding > 0;
    }

    /** A block write of room code: the new state goes into the overlay, and the write is recorded. */
    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        if (level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState old = level.getBlockState(pos);
        if (old == state) {
            return false;
        }
        BlockPos at = pos.immutable();
        overlay.put(at, state);
        record(new RecordedOp.SetBlock(EpitaphSandbox.actorKey(), at, state, flags, old));
        return true;
    }

    /** A block destroyed by room code, taken whole: its effect, its drops and its game event are the real one's. */
    public boolean destroyBlock(BlockPos pos, boolean drop) {
        BlockState old = level.getBlockState(pos);
        if (old.isAir()) {
            return false;
        }
        BlockPos at = pos.immutable();
        overlay.put(at, old.getFluidState().createLegacyBlock());
        record(new RecordedOp.DestroyBlock(EpitaphSandbox.actorKey(), at, drop, old));
        return true;
    }
}
