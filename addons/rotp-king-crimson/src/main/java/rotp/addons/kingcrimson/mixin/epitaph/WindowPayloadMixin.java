package rotp.addons.kingcrimson.mixin.epitaph;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.server.network.ServerPlayerConnection;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/**
 * In a window what the payload of a mod makes a participant do is his (EPITAPH-ARCH 3.5). The loader does not handle such
 * a payload in the method that receives it: its handler is queued as work for the server thread, so the bracket goes
 * around that work. The flag is read on the thread of the connection: a payload that comes in the instant a window opens
 * may run with nobody named, and then what it adds is his who shot it, threw it or whose Stand it is. The class is
 * internal to the loader, so neither injector is required: WindowCover asks at startup whether the two methods are still
 * there, and without them no window opens.
 */
@Mixin(ServerPayloadContext.class)
public abstract class WindowPayloadMixin {
    @Shadow
    @Final
    private ServerCommonPacketListener listener;

    @ModifyVariable(method = "enqueueWork(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"),
            argsOnly = true, require = 0)
    private Runnable kingCrimson$windowWork(Runnable work) {
        if (!WindowJournal.active) {
            return work;
        }
        return listener instanceof ServerPlayerConnection sender ? WindowJournal.bracket(sender.getPlayer(), work) : work;
    }

    @ModifyVariable(method = "enqueueWork(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"),
            argsOnly = true, require = 0)
    private <T> Supplier<T> kingCrimson$windowAnswer(Supplier<T> work) {
        if (!WindowJournal.active) {
            return work;
        }
        return listener instanceof ServerPlayerConnection sender ? WindowJournal.bracket(sender.getPlayer(), work) : work;
    }
}
