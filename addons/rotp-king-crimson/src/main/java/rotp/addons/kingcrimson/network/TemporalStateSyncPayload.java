package rotp.addons.kingcrimson.network;

import java.util.UUID;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TemporalStateSyncPayload(
        int entityId,
        UUID entityUuid,
        int modeOrdinal,
        long startedAt,
        long expiresAt,
        boolean erasePaused,
        int eraseKindOrdinal,
        boolean eraseKeyUp) implements CustomPacketPayload {
    public static final Type<TemporalStateSyncPayload> TYPE =
            new Type<>(KingCrimsonAddon.id("temporal_state_sync"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            TemporalStateSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TemporalStateSyncPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new TemporalStateSyncPayload(
                            buffer.readVarInt(),
                            buffer.readUUID(),
                            buffer.readVarInt(),
                            buffer.readVarLong(),
                            buffer.readVarLong(),
                            buffer.readBoolean(),
                            buffer.readVarInt(),
                            buffer.readBoolean());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        TemporalStateSyncPayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeUUID(payload.entityUuid());
                    buffer.writeVarInt(payload.modeOrdinal());
                    buffer.writeVarLong(payload.startedAt());
                    buffer.writeVarLong(payload.expiresAt());
                    buffer.writeBoolean(payload.erasePaused());
                    buffer.writeVarInt(payload.eraseKindOrdinal());
                    buffer.writeBoolean(payload.eraseKeyUp());
                }
            };

    public static TemporalStateSyncPayload from(
            Player player, KingCrimsonTemporalState state) {
        return new TemporalStateSyncPayload(
                player.getId(),
                player.getUUID(),
                state.mode().ordinal(),
                state.startedAt(),
                state.expiresAt(),
                state.isErasePaused(),
                state.eraseKind().ordinal(),
                state.isKeyUp());
    }

    public static void handle(
            TemporalStateSyncPayload payload,
            IPayloadContext context) {
        Player localPlayer = context.player();
        Entity entity =
                localPlayer.level().getEntity(payload.entityId());
        if (entity instanceof Player player
                && player.getUUID().equals(payload.entityUuid())) {
            KingCrimsonAddon.temporalState(player)
                    .applyClientSnapshot(
                            payload.modeOrdinal(),
                            payload.startedAt(),
                            payload.expiresAt(),
                            payload.erasePaused(),
                            payload.eraseKindOrdinal(),
                            payload.eraseKeyUp());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
