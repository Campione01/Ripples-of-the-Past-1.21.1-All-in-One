package rotp.addons.kingcrimson.epitaph.human;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The profiles human phantoms are made under. Making a player for a profile the server has not seen reads its
 * statistics and registers a listener for every advancement; the server keeps both by UUID, so a profile that is
 * used again costs neither. A profile is taken for the length of a prediction and handed back at its end; the body
 * is made anew each time, so nothing of one prediction is left on the next.
 */
public final class PhantomPlayerPool {
    private static final Deque<GameProfile> FREE = new ArrayDeque<>();
    private static int made;

    private PhantomPlayerPool() {}

    public static EpitaphPlayerPhantom make(ServerLevel level, ServerPlayer real) {
        GameProfile profile = FREE.poll();
        if (profile == null) {
            profile = new GameProfile(UUID.randomUUID(), "Epitaph" + (++made));
        }
        EpitaphPlayerPhantom phantom = new EpitaphPlayerPhantom(level, profile, real);
        // No criterion of an advancement listens for a phantom.
        phantom.getAdvancements().stopListening();
        return phantom;
    }

    public static void release(EpitaphPlayerPhantom phantom) {
        FREE.add(phantom.getGameProfile());
    }

    public static void reset() {
        FREE.clear();
        made = 0;
    }
}
