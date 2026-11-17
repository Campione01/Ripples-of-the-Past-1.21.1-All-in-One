package rotp.addons.kingcrimson.epitaph.sandbox;

import net.minecraft.world.entity.Entity;

/**
 * A phantom has the int id of its body, and to the game two entities with one id are equal: a table that finds an
 * entity by equals holds one entry for both. So every phantom is marked, and a marked entity equals only itself
 * (EntityBarrierMixin; EPITAPH-ARCH, ruling R7). The mark is a field the mixin adds to the entity. The flag here stands
 * in front of it: true from the first copy of a run on, on either side, and never false again, since a copy can be a
 * key in the table of somebody else long after its prediction is over.
 */
public final class PhantomIdentity {
    public static boolean any;

    /** What the mixin makes of every entity. */
    public interface Marked {
        void kingCrimson$markPhantom();

        boolean kingCrimson$isPhantom();
    }

    private PhantomIdentity() {}

    /** Where a copy is made, where a child joins a room, and on a client where a puppet is taken in. */
    public static void mark(Entity phantom) {
        any = true;
        ((Marked) phantom).kingCrimson$markPhantom();
    }

    public static boolean isMarked(Object entity) {
        return entity instanceof Marked marked && marked.kingCrimson$isPhantom();
    }
}
