package rotp.addons.nextalbum.network;

import io.netty.buffer.ByteBuf;
import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.state.ProsciuttoShadow;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Server to trackers: whether a Prosciutto suit wearer is a shadow. */
public record ProsciuttoShadowPayload(int entityId, boolean shadow)
        implements CustomPacketPayload {
    public static final Type<ProsciuttoShadowPayload> TYPE =
            new Type<>(NextAlbumIds.id("prosciutto_shadow"));
    public static final StreamCodec<ByteBuf, ProsciuttoShadowPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ProsciuttoShadowPayload::entityId,
                    ByteBufCodecs.BOOL, ProsciuttoShadowPayload::shadow,
                    ProsciuttoShadowPayload::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                TYPE, STREAM_CODEC, ProsciuttoShadowPayload::handle);
        registrar.playToServer(
                ProsciuttoSuitModePayload.TYPE,
                ProsciuttoSuitModePayload.STREAM_CODEC,
                ProsciuttoSuitModePayload::handle);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new ProsciuttoShadowPayload(player.getId(),
                        ProsciuttoShadow.isShadowFlag(player)));
    }

    public static void sendTo(ServerPlayer receiver, ServerPlayer subject) {
        PacketDistributor.sendToPlayer(receiver,
                new ProsciuttoShadowPayload(subject.getId(),
                        ProsciuttoShadow.isShadowFlag(subject)));
    }

    private static void handle(
            ProsciuttoShadowPayload payload, IPayloadContext context) {
        ProsciuttoShadow.handleClientSync(
                payload.entityId(), payload.shadow());
        if (context.player().level().getEntity(payload.entityId())
                instanceof net.minecraft.world.entity.player.Player player) {
            player.refreshDisplayName();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
