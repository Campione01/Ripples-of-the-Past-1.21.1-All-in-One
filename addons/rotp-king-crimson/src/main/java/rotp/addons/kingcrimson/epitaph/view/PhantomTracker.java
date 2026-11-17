package rotp.addons.kingcrimson.epitaph.view;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.neoforged.neoforge.network.bundle.PacketAndPayloadAcceptor;

/**
 * The private tracker of one phantom: the game's own bookkeeping of what a client has been told about an entity, kept
 * for a copy that is in no level. What it would send a player who tracked the phantom is handed back instead of sent:
 * the packets that make the entity on a client, and after each tick of the room the ones that move it, turn it and
 * change how it looks.
 */
public final class PhantomTracker {
    private final Entity phantom;
    private final ServerEntity tracker;
    private final List<Packet<?>> out = new ArrayList<>();

    public PhantomTracker(ServerLevel level, Entity phantom) {
        this.phantom = phantom;
        tracker = new ServerEntity(level, phantom, phantom.getType().updateInterval(), phantom.getType().trackDeltas(), out::add);
    }

    /** What a client is sent when it begins to track the phantom. */
    public List<Packet<?>> pairing(ServerPlayer viewer) {
        List<Packet<?>> packets = new ArrayList<>();
        tracker.sendPairingData(viewer, new PacketAndPayloadAcceptor<ClientGamePacketListener>(packets::add));
        return packets;
    }

    /** What changed since the last call: asked once after each tick of the phantom. */
    public List<Packet<?>> changes() {
        out.clear();
        if (phantom instanceof ItemFrame frame && frame.getItem().has(DataComponents.MAP_ID)) {
            // The tracker of a framed map marks the map as carried by every player of the level: of this one only
            // its data is asked.
            List<SynchedEntityData.DataValue<?>> dirty = phantom.getEntityData().packDirty();
            return dirty != null ? List.of(new ClientboundSetEntityDataPacket(phantom.getId(), dirty)) : List.of();
        }
        tracker.sendChanges();
        return List.copyOf(out);
    }
}
