package rotp.addons.kingcrimson.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.epitaph.EpitaphNotice;

/**
 * Tells a player that a prediction of Epitaph is aimed at him, and that it no longer is (EPITAPH-ARCH 3.7). Only a
 * player a rule of another add-on exempts is ever sent it: the holder of Gold Experience Requiem. Anybody else a
 * prediction is aimed at is told nothing.
 */
public record EpitaphNoticePayload(int session, boolean on) implements CustomPacketPayload {
    public static final Type<EpitaphNoticePayload> TYPE = new Type<>(KingCrimsonAddon.id("epitaph_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EpitaphNoticePayload> STREAM_CODEC =
            StreamCodec.ofMember(EpitaphNoticePayload::encode, EpitaphNoticePayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(session);
        buffer.writeBoolean(on);
    }

    private static EpitaphNoticePayload decode(RegistryFriendlyByteBuf buffer) {
        return new EpitaphNoticePayload(buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(EpitaphNoticePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> EpitaphNotice.accept(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
