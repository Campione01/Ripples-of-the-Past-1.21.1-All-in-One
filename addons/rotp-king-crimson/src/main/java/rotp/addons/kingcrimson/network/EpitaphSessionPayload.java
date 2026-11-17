package rotp.addons.kingcrimson.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.epitaph.EpitaphClientSession;
import rotp.addons.kingcrimson.epitaph.EpitaphMode;

/**
 * Tells the one who foresees that his prediction began, that its phantom phase is over, and that it ended. With the
 * beginning come its mode, how far it reaches and how long its phantoms run, whether their sounds are heard, and the
 * ids of the bodies that are held to their tracks: alone on the server they live slower while the phantoms run, and
 * so do their animations.
 */
public record EpitaphSessionPayload(int session, Kind kind, EpitaphMode mode, int foresightTicks, int phaseTicks,
        boolean sounds, int[] bodies) implements CustomPacketPayload {
    public enum Kind {
        BEGIN,
        PHASE_OVER,
        END
    }

    private static final int MAX_BODIES = 4096;

    public static final Type<EpitaphSessionPayload> TYPE = new Type<>(KingCrimsonAddon.id("epitaph_session"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EpitaphSessionPayload> STREAM_CODEC =
            StreamCodec.ofMember(EpitaphSessionPayload::encode, EpitaphSessionPayload::decode);

    public EpitaphSessionPayload {
        bodies = bodies.clone();
    }

    public static EpitaphSessionPayload of(int session, Kind kind) {
        return new EpitaphSessionPayload(session, kind, EpitaphMode.SOLO_SLOW, 0, 0, false, new int[0]);
    }

    @Override
    public int[] bodies() {
        return bodies.clone();
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(session);
        buffer.writeEnum(kind);
        buffer.writeEnum(mode);
        buffer.writeVarInt(foresightTicks);
        buffer.writeVarInt(phaseTicks);
        buffer.writeBoolean(sounds);
        buffer.writeVarIntArray(bodies);
    }

    private static EpitaphSessionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new EpitaphSessionPayload(buffer.readVarInt(), buffer.readEnum(Kind.class), buffer.readEnum(EpitaphMode.class),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarIntArray(MAX_BODIES));
    }

    public static void handle(EpitaphSessionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> EpitaphClientSession.accept(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
