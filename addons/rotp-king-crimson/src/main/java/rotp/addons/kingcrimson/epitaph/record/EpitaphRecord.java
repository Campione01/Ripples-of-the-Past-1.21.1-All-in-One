package rotp.addons.kingcrimson.epitaph.record;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Everything a room produced: a track per phantom and, per tick, the ops in the order they were done. Tick n holds
 * what happened while the room went from tick n - 1 to tick n; tick 0 is the press. Memory only, never saved.
 */
public final class EpitaphRecord {
    private final int foresight;
    private final Map<Integer, EntityTrack> tracks = new LinkedHashMap<>();
    private final List<List<RecordedOp>> ops = new ArrayList<>();
    private int skipped;
    private int failed;

    public EpitaphRecord(int foresightTicks) {
        this.foresight = foresightTicks;
        for (int tick = 0; tick <= foresightTicks; tick++) {
            ops.add(new ArrayList<>());
        }
    }

    public int foresight() {
        return foresight;
    }

    public EntityTrack open(ActorKey key, ActorKey owner, int firstTick, boolean child) {
        EntityTrack track = new EntityTrack(key, owner, firstTick, child);
        tracks.put(key.id(), track);
        return track;
    }

    @Nullable
    public EntityTrack track(ActorKey key) {
        return tracks.get(key.id());
    }

    @Nullable
    public EntityTrack track(int id) {
        return tracks.get(id);
    }

    public Collection<EntityTrack> tracks() {
        return Collections.unmodifiableCollection(tracks.values());
    }

    public void add(int tick, RecordedOp op) {
        if (tick >= 0 && tick < ops.size()) {
            ops.get(tick).add(op);
        }
    }

    public List<RecordedOp> ops(int tick) {
        return tick >= 0 && tick < ops.size() ? Collections.unmodifiableList(ops.get(tick)) : List.of();
    }

    /** The root whose future an op belongs to. */
    public ActorKey ownerOf(RecordedOp op) {
        EntityTrack track = track(op.actor());
        return track != null ? track.owner() : ActorKey.NONE;
    }

    /** Ops that were not carried out because the world or the session had changed under them. */
    public int skipped() {
        return skipped;
    }

    public void countSkipped() {
        skipped++;
    }

    /** Ops that threw when they were carried out: each is among the skipped too. */
    public int failed() {
        return failed;
    }

    public void countFailed() {
        failed++;
    }

    public int opCount() {
        int count = 0;
        for (List<RecordedOp> ofTick : ops) {
            count += ofTick.size();
        }
        return count;
    }
}
