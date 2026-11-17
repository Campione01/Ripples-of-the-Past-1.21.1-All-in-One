package rotp.addons.kingcrimson.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.epitaph.PhantomFrameApplier;

/**
 * What the phantoms of a prediction did in one tick of the foreseen future, for the eyes of the one who foresees: the
 * very packets the server would send a player who tracked them, as bytes written when they were made. The client
 * handles them apart from the world. A frame of tick {@link #NOW} is not of the foreseen future: it is applied at
 * once (a phantom whose body has reached it goes).
 */
public record EpitaphPhantomFramePayload(int session, int tick, List<byte[]> packets) implements CustomPacketPayload {
    public static final int NOW = -1;
    private static final int MAX_PACKETS = 8192;
    private static final int MAX_PACKET_BYTES = 1 << 20;

    public static final Type<EpitaphPhantomFramePayload> TYPE = new Type<>(KingCrimsonAddon.id("epitaph_phantom_frame"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EpitaphPhantomFramePayload> STREAM_CODEC =
            StreamCodec.ofMember(EpitaphPhantomFramePayload::encode, EpitaphPhantomFramePayload::decode);

    public EpitaphPhantomFramePayload {
        packets = List.copyOf(packets);
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(session);
        buffer.writeVarInt(tick + 1);
        buffer.writeVarInt(packets.size());
        for (byte[] packet : packets) {
            buffer.writeByteArray(packet);
        }
    }

    private static EpitaphPhantomFramePayload decode(RegistryFriendlyByteBuf buffer) {
        int session = buffer.readVarInt();
        int tick = buffer.readVarInt() - 1;
        int count = Math.min(buffer.readVarInt(), MAX_PACKETS);
        List<byte[]> packets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            packets.add(buffer.readByteArray(MAX_PACKET_BYTES));
        }
        return new EpitaphPhantomFramePayload(session, tick, packets);
    }

    public static void handle(EpitaphPhantomFramePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PhantomFrameApplier.accept(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
