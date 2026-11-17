package rotp.addons.kingcrimson.epitaph.record;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;

/**
 * The identity a phantom and its body share: the int id. A root phantom takes its body's id; a child keeps the id its
 * phantom drew when it was made, and its body takes that id when it is spawned for real.
 */
public record ActorKey(int id) {
    /** Nobody: a block, the level, or an entity that was not in the room. */
    public static final ActorKey NONE = new ActorKey(Integer.MIN_VALUE);

    public static ActorKey of(@Nullable Entity entity) {
        return entity == null ? NONE : new ActorKey(entity.getId());
    }

    public boolean isNone() {
        return id == Integer.MIN_VALUE;
    }
}
