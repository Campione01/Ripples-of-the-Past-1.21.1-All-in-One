package rotp.addons.justice;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import rotp.addons.justice.client.JusticeClientState;

public record JusticeVisualPayload(UUID owner, ResourceLocation dimension, boolean fog, JusticeDisguise.Mode mode)
        implements CustomPacketPayload {
    public static final Type<JusticeVisualPayload> TYPE = new Type<>(JusticeAddon.id("visual_state"));
    private static final StreamCodec<RegistryFriendlyByteBuf, JusticeVisualPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, JusticeVisualPayload::owner,
            ResourceLocation.STREAM_CODEC, JusticeVisualPayload::dimension,
            ByteBufCodecs.BOOL, JusticeVisualPayload::fog,
            ByteBufCodecs.VAR_INT.map(JusticeVisualPayload::modeFromId, JusticeVisualPayload::modeId),
            JusticeVisualPayload::mode,
            JusticeVisualPayload::new);

    private static JusticeDisguise.Mode modeFromId(int id) {
        return switch (id) {
            case 1 -> JusticeDisguise.Mode.VILLAGER;
            case 2 -> JusticeDisguise.Mode.ZOMBIE;
            default -> JusticeDisguise.Mode.NONE;
        };
    }

    private static int modeId(JusticeDisguise.Mode mode) {
        return switch (mode) {
            case NONE -> 0;
            case VILLAGER -> 1;
            case ZOMBIE -> 2;
        };
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC,
                (payload, context) -> context.enqueueWork(() -> JusticeClientState.receive(payload)));
    }

    @Override
    public Type<JusticeVisualPayload> type() {
        return TYPE;
    }
}
