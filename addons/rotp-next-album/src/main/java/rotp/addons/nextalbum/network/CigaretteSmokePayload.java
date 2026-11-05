package rotp.addons.nextalbum.network;

import io.netty.buffer.ByteBuf;
import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.client.ClientCigaretteState;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to trackers: an exhale puff, denser the longer the inhale. */
public record CigaretteSmokePayload(int entityId, int intensity)
        implements CustomPacketPayload {
    public static final Type<CigaretteSmokePayload> TYPE =
            new Type<>(NextAlbumIds.id("cigarette_smoke"));
    public static final StreamCodec<ByteBuf, CigaretteSmokePayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CigaretteSmokePayload::entityId,
                    ByteBufCodecs.VAR_INT, CigaretteSmokePayload::intensity,
                    CigaretteSmokePayload::new);

    public static void sendAround(LivingEntity entity, int intensity) {
        if (!entity.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    entity,
                    new CigaretteSmokePayload(entity.getId(), intensity));
        }
    }

    static void handle(
            CigaretteSmokePayload payload, IPayloadContext context) {
        ClientCigaretteState.handleSmoke(
                payload.entityId(), payload.intensity());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
