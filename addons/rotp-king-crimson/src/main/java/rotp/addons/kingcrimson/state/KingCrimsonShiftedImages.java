package rotp.addons.kingcrimson.state;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.phys.Vec3;

/**
 * On a Time Erase user's client: where the server says he sees the other players, by entity id. The position mixin
 * and the renderer read it; it is empty, and costs one read, on every other client and on a server.
 */
public final class KingCrimsonShiftedImages {
    private static volatile Int2ObjectMap<Vec3> images = new Int2ObjectOpenHashMap<>();
    private static volatile boolean any;

    private KingCrimsonShiftedImages() {}

    public static boolean any() {
        return any;
    }

    @Nullable
    public static Vec3 imageOf(int entityId) {
        return any ? images.get(entityId) : null;
    }

    public static Int2ObjectMap<Vec3> all() {
        return images;
    }

    public static void replace(Int2ObjectMap<Vec3> newImages) {
        images = newImages;
        any = !newImages.isEmpty();
    }

    public static void clear() {
        replace(new Int2ObjectOpenHashMap<>());
    }
}
