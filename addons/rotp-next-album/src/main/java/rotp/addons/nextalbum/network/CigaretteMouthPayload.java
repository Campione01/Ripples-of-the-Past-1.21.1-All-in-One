package rotp.addons.nextalbum.network;

import io.netty.buffer.ByteBuf;
import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.client.ClientCigaretteState;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Server to trackers: how many lit cigarettes a player holds in the mouth. */
public record CigaretteMouthPayload(int entityId, int count)
        implements CustomPacketPayload {
    public static final Type<CigaretteMouthPayload> TYPE =
            new Type<>(NextAlbumIds.id("cigarette_mouth"));
    public static final StreamCodec<ByteBuf, CigaretteMouthPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CigaretteMouthPayload::entityId,
                    ByteBufCodecs.VAR_INT, CigaretteMouthPayload::count,
                    CigaretteMouthPayload::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                TYPE, STREAM_CODEC, CigaretteMouthPayload::handle);
        registrar.playToClient(
                CigaretteSmokePayload.TYPE,
                CigaretteSmokePayload.STREAM_CODEC,
                CigaretteSmokePayload::handle);
    }

    public static void sendToTrackingAndSelf(ServerPlayer player, int count) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player, new CigaretteMouthPayload(player.getId(), count));
    }

    public static void sendTo(
            ServerPlayer receiver, ServerPlayer subject, int count) {
        PacketDistributor.sendToPlayer(
                receiver, new CigaretteMouthPayload(subject.getId(), count));
    }

    private static void handle(
            CigaretteMouthPayload payload, IPayloadContext context) {
        ClientCigaretteState.handleMouthSync(
                payload.entityId(), payload.count());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
