package rotp.addons.kingcrimson.mixin.epitaph;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * The players of the server as its list holds them. A game test puts a player of its own on it for a moment: he is on no
 * connection the list knows, and sounds and level events go to the players of this list.
 */
@Mixin(PlayerList.class)
public interface PlayerListAccessor {
    @Accessor("players")
    List<ServerPlayer> kingCrimson$players();
}
