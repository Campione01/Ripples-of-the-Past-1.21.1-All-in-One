package rotp.addons.kingcrimson.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.epitaph.ForcedWalkClient;

/**
 * For a player who is walked along a foreseen track (EPITAPH-ARCH 3.4), and for him alone: that the walk begins, where
 * his body stands at each tick of the server, and that it is over. His client moves him there and takes his hands off
 * the keys meanwhile.
 */
public record EpitaphForcedWalkPayload(Kind kind, double x, double y, double z) implements CustomPacketPayload {
    public enum Kind {
        BEGIN,
        STEP,
        RELEASE
    }

    public static final Type<EpitaphForcedWalkPayload> TYPE = new Type<>(KingCrimsonAddon.id("epitaph_forced_walk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EpitaphForcedWalkPayload> STREAM_CODEC =
            StreamCodec.ofMember(EpitaphForcedWalkPayload::encode, EpitaphForcedWalkPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(kind);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
    }

    private static EpitaphForcedWalkPayload decode(RegistryFriendlyByteBuf buffer) {
        return new EpitaphForcedWalkPayload(buffer.readEnum(Kind.class), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble());
    }

    public static void handle(EpitaphForcedWalkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ForcedWalkClient.accept(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
