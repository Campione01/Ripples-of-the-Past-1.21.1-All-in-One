package rotp.addons.kingcrimson.epitaph.record;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.powersystem.entityaction.ActionPhase;

/**
 * One phantom's future: a frame per tick of the room, from the tick it first stood in the room (0 for a root, its spawn
 * tick for a child) to the tick its simulation ended.
 */
public final class EntityTrack {
    /** Why a track has its last frame where it has it. */
    public enum End {
        /** Still being simulated. */
        OPEN,
        /** The foresight ran out: the phantom was alive and there at its last tick. */
        FORESIGHT,
        /** The phantom was removed (an arrow that hit, an item picked up, a block that landed). */
        REMOVED,
        /** The phantom died. */
        DEATH,
        /** It reached the edge of the loaded chunks. */
        EDGE,
        /** Its tick threw. */
        FAULT
    }

    private final ActorKey key;
    private final ActorKey owner;
    private final int firstTick;
    private final boolean child;
    private final List<TrackFrame> frames = new ArrayList<>();
    /** Where the action of a slot stood after a tick, for the ticks on which the slot held one: by tick and slot. */
    private final Map<Long, ActionMark> actions = new HashMap<>();
    private End end = End.OPEN;
    @Nullable
    private Checkpoint checkpoint;

    public EntityTrack(ActorKey key, ActorKey owner, int firstTick, boolean child) {
        this.key = key;
        this.owner = owner;
        this.firstTick = firstTick;
        this.child = child;
    }

    public ActorKey key() {
        return key;
    }

    /** The root whose future this phantom belongs to: itself for a root, its maker's owner for a child. */
    public ActorKey owner() {
        return owner;
    }

    public boolean isChild() {
        return child;
    }

    public int firstTick() {
        return firstTick;
    }

    /** The last tick this track has a frame for. */
    public int lastTick() {
        return firstTick + frames.size() - 1;
    }

    public void add(TrackFrame frame) {
        frames.add(frame);
    }

    public boolean has(int tick) {
        return tick >= firstTick && tick <= lastTick();
    }

    public TrackFrame frame(int tick) {
        return frames.get(Math.max(0, Math.min(tick - firstTick, frames.size() - 1)));
    }

    /** The phase an action was in after a tick of the room, and how far into it. */
    public record ActionMark(ActionPhase phase, int phaseTick) {}

    public void mark(int tick, int slot, ActionMark mark) {
        actions.put(tick * 2L + slot, mark);
    }

    /** Where the action of this slot stood after this tick, null when the slot held none. */
    @Nullable
    public ActionMark mark(int tick, int slot) {
        return actions.get(tick * 2L + slot);
    }

    public End end() {
        return end;
    }

    public boolean isOpen() {
        return end == End.OPEN;
    }

    public void close(End how, @Nullable Checkpoint state) {
        if (end == End.OPEN) {
            end = how;
            checkpoint = state;
        }
    }

    /** The phantom's state at the end of a track it outlived, null when it did not. */
    @Nullable
    public Checkpoint checkpoint() {
        return checkpoint;
    }
}
