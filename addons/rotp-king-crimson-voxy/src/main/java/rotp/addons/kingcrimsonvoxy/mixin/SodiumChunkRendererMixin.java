package rotp.addons.kingcrimsonvoxy.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.addons.kingcrimsonvoxy.KcVoxyHooks;

/** During an erase the real sections draw nothing, but this call must go on: Voxy's own draw hangs on it. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class SodiumChunkRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void rotpKcVoxy$bind(CallbackInfo ci) {
        KcVoxyHooks.bindBuffers();
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private ChunkRenderListIterable rotpKcVoxy$noSections(ChunkRenderListIterable lists) {
        return KcVoxyHooks.live() ? SortedRenderLists.empty() : lists;
    }
}
