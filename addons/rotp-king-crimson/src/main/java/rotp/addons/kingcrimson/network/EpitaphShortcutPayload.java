package rotp.addons.kingcrimson.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.epitaph.EpitaphShortcutFlash;

/**
 * A body on a foreseen track took a short cut in time: it could not go where its track goes, and stands at a later
 * place of its track at once. For everybody who sees it: the crimson flash at the place it left.
 */
public record EpitaphShortcutPayload(int entity, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<EpitaphShortcutPayload> TYPE = new Type<>(KingCrimsonAddon.id("epitaph_shortcut"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EpitaphShortcutPayload> STREAM_CODEC =
            StreamCodec.ofMember(EpitaphShortcutPayload::encode, EpitaphShortcutPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entity);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
    }

    private static EpitaphShortcutPayload decode(RegistryFriendlyByteBuf buffer) {
        return new EpitaphShortcutPayload(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(EpitaphShortcutPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> EpitaphShortcutFlash.at(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
