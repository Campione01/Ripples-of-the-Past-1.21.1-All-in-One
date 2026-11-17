package rotp.addons.kingcrimson.client.epitaph;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A human phantom on the client of the one who foresees: a player of its own, never in the level, that looks like the
 * real player it stands for. The packets of the frames move it and dress it as they would any other player.
 */
@OnlyIn(Dist.CLIENT)
public final class EpitaphRemotePlayer extends RemotePlayer {
    private final UUID standsFor;

    EpitaphRemotePlayer(ClientLevel level, GameProfile of) {
        super(level, of);
        this.standsFor = of.getId();
    }

    // The skin is looked up under the id of the real player: the phantom has an id of its own, which nobody knows.
    @Nullable
    @Override
    protected PlayerInfo getPlayerInfo() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null ? connection.getPlayerInfo(standsFor) : null;
    }
}
