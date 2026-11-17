package rotp.addons.kingcrimson.epitaph.record;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * Clientbound game packets as bytes and back, through the codec of the game protocol. A packet is encoded when the
 * room makes it, not when it is sent: some payloads read live entity state as they are written, and by the time the
 * bodies get there that state has moved on. A bundle is taken apart, since the codec does not write the bundle
 * object itself (the bundler of the connection does).
 */
public final class PacketBytes {
    private static RegistryAccess boundTo;
    private static StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> codec;

    private PacketBytes() {}

    private static StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> codec(RegistryAccess registries) {
        if (codec == null || boundTo != registries) {
            ProtocolInfo<ClientGamePacketListener> protocol = GameProtocols.CLIENTBOUND_TEMPLATE
                    .bind(RegistryFriendlyByteBuf.decorator(registries, ConnectionType.NEOFORGE));
            codec = protocol.codec();
            boundTo = registries;
        }
        return codec;
    }

    /** The packet, or the packets of a bundle one after the other, each with its length before it. */
    @SuppressWarnings("unchecked")
    public static byte[] encode(Packet<?> packet, RegistryAccess registries) {
        List<Packet<?>> parts = new ArrayList<>();
        flatten(packet, parts);
        ByteBuf all = Unpooled.buffer();
        ByteBuf one = Unpooled.buffer();
        try {
            for (Packet<?> part : parts) {
                one.clear();
                codec(registries).encode(one, (Packet<? super ClientGamePacketListener>) part);
                all.writeInt(one.readableBytes());
                all.writeBytes(one);
            }
            byte[] bytes = new byte[all.readableBytes()];
            all.readBytes(bytes);
            return bytes;
        }
        finally {
            one.release();
            all.release();
        }
    }

    public static List<Packet<? super ClientGamePacketListener>> decode(byte[] bytes, RegistryAccess registries) {
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        ByteBuf all = Unpooled.wrappedBuffer(bytes);
        try {
            while (all.readableBytes() >= 4) {
                int length = all.readInt();
                packets.add(codec(registries).decode(all.readSlice(length)));
            }
            return packets;
        }
        finally {
            all.release();
        }
    }

    private static void flatten(Packet<?> packet, List<Packet<?>> into) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> part : bundle.subPackets()) {
                flatten(part, into);
            }
        } else {
            into.add(packet);
        }
    }
}
