package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** What the server keeps of a player whose client says it is off the ground: a walked player is not kicked for it. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface GameListenerStateAccessor {
    @Accessor("clientIsFloating")
    void kingCrimson$setClientIsFloating(boolean floating);
}
