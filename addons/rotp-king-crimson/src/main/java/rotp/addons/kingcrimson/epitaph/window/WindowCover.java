package rotp.addons.kingcrimson.epitaph.window;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.neoforge.network.handling.ServerPayloadContext;

/**
 * Whether the journal of a window can cover what the payload of a mod does for a player (EPITAPH-ARCH 3.5, and the
 * ruling of the coordinator on entry 29). The bracket hangs on a class that is internal to the loader, by an injector
 * that asks nothing of the game: where a later loader no longer has the method, the game starts all the same and no
 * window opens, since the pull-back would double what such a payload places out of an inventory.
 */
public final class WindowCover {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Whether the loader has what the bracket of payloads hangs on. Asked once at startup; a test may set it. */
    public static boolean payloads = true;

    private WindowCover() {}

    /** Whether this class has a public method of this name with exactly this descriptor: what an injector matches by. */
    public static boolean has(Class<?> owner, String name, MethodType descriptor) {
        try {
            MethodHandles.publicLookup().findVirtual(owner, name, descriptor);
            return true;
        }
        catch (ReflectiveOperationException | LinkageError missing) {
            return false;
        }
    }

    /** At startup, once: looks for the two methods the bracket of payloads is hung on. */
    public static void check() {
        boolean found;
        try {
            found = has(ServerPayloadContext.class, "enqueueWork", MethodType.methodType(CompletableFuture.class, Runnable.class))
                    && has(ServerPayloadContext.class, "enqueueWork", MethodType.methodType(CompletableFuture.class, Supplier.class));
        }
        catch (LinkageError gone) {
            found = false;
        }
        payloads = found;
        if (!found) {
            LOGGER.error("Epitaph[window] this loader no longer has ServerPayloadContext.enqueueWork as this add-on knows it. What"
                    + " the payload of a mod does for a player could not be written down, so no window opens: with"
                    + " epitaphPullBack on, a player under the crosshair is walked from the press as with it off");
        }
    }
}
