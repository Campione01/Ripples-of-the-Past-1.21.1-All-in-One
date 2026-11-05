package rotp.addons.echoes.network;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.state.EchoesForms;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** R772 E1: the ACT picked in the selector (master EchoesActSyncPacket); the server checks it is on offer. */
public record EchoesSelectActPayload(int act) implements CustomPacketPayload {
    public static final Type<EchoesSelectActPayload> TYPE = new Type<>(RotpEchoes.id("select_act"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoesSelectActPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, EchoesSelectActPayload::act, EchoesSelectActPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, EchoesSelectActPayload::handle);
    }

    private static void handle(EchoesSelectActPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                EchoesForms.select(player, payload.act());
            }
        });
    }
}
