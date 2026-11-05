package rotp.addons.killerqueen.network;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.btd.BtdClientState;
import rotp.addons.killerqueen.btd.BtdState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 1.6.10 (2026-10-20) com.zeml.rotp_zkq.network.server.BtdSyncPacket: the receiver's Bite the Dust state. */
public record BtdSyncPayload(int state, int ticks, boolean owner)
        implements CustomPacketPayload {
    public static final Type<BtdSyncPayload> TYPE =
            new Type<>(KillerQueenAddon.id("btd_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BtdSyncPayload>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public BtdSyncPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new BtdSyncPayload(
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readBoolean());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        BtdSyncPayload payload) {
                    buffer.writeVarInt(payload.state());
                    buffer.writeVarInt(payload.ticks());
                    buffer.writeBoolean(payload.owner());
                }
            };

    public BtdSyncPayload(BtdState state, int ticks, boolean owner) {
        this(state.ordinal(), Math.max(ticks, 0), owner);
    }

    public static void handle(BtdSyncPayload payload, IPayloadContext context) {
        BtdClientState.set(
                BtdState.byOrdinal(payload.state()),
                payload.ticks(),
                payload.owner());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
