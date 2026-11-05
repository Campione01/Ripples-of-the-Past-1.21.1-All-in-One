package rotp.addons.beachboy.util;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;

/** 1.16 BeachBoyPlayerSync: a dragged player is moved on its own client too, keeping its look. */
public final class BeachBoyPlayerSync {
    private static final Set<RelativeMovement> KEEP_LOOK = EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT);
    // the client confirms this id, which the server never waits for
    private static final int NO_AWAIT_TELEPORT_ID = Integer.MIN_VALUE;

    private BeachBoyPlayerSync() {}

    public static void sendPositionKeepLook(ServerPlayer player) {
        if (player != null && player.connection != null) {
            player.hurtMarked = true;
            player.hasImpulse = true;
            player.connection.send(new ClientboundPlayerPositionPacket(
                    player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F, KEEP_LOOK, NO_AWAIT_TELEPORT_ID));
        }
    }
}
