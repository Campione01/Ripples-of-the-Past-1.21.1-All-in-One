package rotp.addons.nextalbum.network;

import io.netty.buffer.ByteBuf;
import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.state.ResolveDowned;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Next Album 0.1.13 ResolveDownedGiveUpPacket: whether the downed player holds sneak + jump. */
public record ResolveDownedGiveUpPayload(boolean holding)
        implements CustomPacketPayload {
    public static final Type<ResolveDownedGiveUpPayload> TYPE =
            new Type<>(NextAlbumIds.id("resolve_downed_give_up"));
    public static final StreamCodec<ByteBuf, ResolveDownedGiveUpPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.BOOL, ResolveDownedGiveUpPayload::holding,
                    ResolveDownedGiveUpPayload::new);

    static void handle(
            ResolveDownedGiveUpPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ResolveDowned.holdGiveUp(player, payload.holding());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
