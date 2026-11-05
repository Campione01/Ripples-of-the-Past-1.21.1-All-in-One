package rotp.addons.nutkingcall.network;

import java.util.Optional;
import java.util.UUID;

import rotp.addons.nutkingcall.NutKingCallAddon;
import rotp.addons.nutkingcall.state.NailState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 1.16 NutKingCallCapSyncPacket. */
public record NailStatePayload(
        int entityId,
        boolean inGround,
        boolean wallClimbing,
        BlockPos climbStartPos,
        int nailCount,
        int nutCount,
        boolean pinned,
        BlockPos pinPos,
        Direction pinFace,
        int struggleCount,
        int pinStartTick,
        Optional<UUID> pinOwner) implements CustomPacketPayload {
    public static final Type<NailStatePayload> TYPE = new Type<>(NutKingCallAddon.id("nail_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NailStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public NailStatePayload decode(RegistryFriendlyByteBuf buf) {
                    int entityId = buf.readInt();
                    boolean inGround = buf.readBoolean();
                    boolean wallClimbing = buf.readBoolean();
                    BlockPos climbStartPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
                    int nailCount = buf.readInt();
                    int nutCount = buf.readInt();
                    boolean pinned = buf.readBoolean();
                    BlockPos pinPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
                    Direction pinFace = Direction.from3DDataValue(buf.readInt());
                    int struggleCount = buf.readInt();
                    int pinStartTick = buf.readInt();
                    Optional<UUID> pinOwner = buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty();
                    return new NailStatePayload(entityId, inGround, wallClimbing, climbStartPos, nailCount,
                            nutCount, pinned, pinPos, pinFace, struggleCount, pinStartTick, pinOwner);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, NailStatePayload packet) {
                    buf.writeInt(packet.entityId());
                    buf.writeBoolean(packet.inGround());
                    buf.writeBoolean(packet.wallClimbing());
                    buf.writeInt(packet.climbStartPos().getX());
                    buf.writeInt(packet.climbStartPos().getY());
                    buf.writeInt(packet.climbStartPos().getZ());
                    buf.writeInt(packet.nailCount());
                    buf.writeInt(packet.nutCount());
                    buf.writeBoolean(packet.pinned());
                    buf.writeInt(packet.pinPos().getX());
                    buf.writeInt(packet.pinPos().getY());
                    buf.writeInt(packet.pinPos().getZ());
                    buf.writeInt(packet.pinFace().get3DDataValue());
                    buf.writeInt(packet.struggleCount());
                    buf.writeInt(packet.pinStartTick());
                    buf.writeBoolean(packet.pinOwner().isPresent());
                    packet.pinOwner().ifPresent(buf::writeUUID);
                }
            };

    public static NailStatePayload of(NailState state) {
        return new NailStatePayload(
                state.getEntityId(),
                state.isInGround(),
                state.isWallClimbing(),
                state.getClimbStartPos(),
                state.getNailCount(),
                state.getNutCount(),
                state.isPinned(),
                state.getPinPos(),
                state.getPinFace(),
                state.getStruggleCount(),
                state.getPinStartTick(),
                Optional.ofNullable(state.getPinOwnerUUID()));
    }

    public static void handle(NailStatePayload packet, IPayloadContext context) {
        Entity entity = context.player().level().getEntity(packet.entityId());
        if (entity instanceof LivingEntity living) {
            NailState state = NailState.get(living);
            state.setEntityId(packet.entityId());
            state.setEntity(living);
            state.deserializeFromPacket(packet);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
