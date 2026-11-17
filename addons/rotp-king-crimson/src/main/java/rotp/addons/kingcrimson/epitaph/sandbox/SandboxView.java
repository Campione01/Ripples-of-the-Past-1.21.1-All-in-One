package rotp.addons.kingcrimson.epitaph.sandbox;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The eyes a room is lived for: whoever foresees is shown what the phantoms do, as the packets a client would get of
 * them, each kept for the tick of the future it was made in.
 */
public interface SandboxView {
    /** The player the phantoms are shown to: what a tracker asks about its viewer is asked of him. */
    ServerPlayer viewer();

    /** Whether this phantom is shown at all. */
    boolean shows(Entity phantom);

    /** Whether the sounds of the room are shown too. */
    boolean sounds();

    /**
     * A packet of one tick of the future, handed over at the moment it is made; about is the id of the phantom it tells
     * of or that caused it, -1 when there is none.
     */
    void show(int tick, int about, Packet<?> packet);
}
