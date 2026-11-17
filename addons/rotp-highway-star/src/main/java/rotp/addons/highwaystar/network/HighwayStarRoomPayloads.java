package rotp.addons.highwaystar.network;

import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.room.HighwayStarRoomLure;

public final class HighwayStarRoomPayloads {
    private static Consumer<LurePayload> clientLure = payload -> {};
    private static Consumer<ViewStartPayload> clientViewStart = payload -> {};
    private static Consumer<ViewEndPayload> clientViewEnd = payload -> {};

    private HighwayStarRoomPayloads() {}

    public static void installClientHandlers(Consumer<LurePayload> lure, Consumer<ViewStartPayload> viewStart,
            Consumer<ViewEndPayload> viewEnd) {
        clientLure = lure;
        clientViewStart = viewStart;
        clientViewEnd = viewEnd;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").executesOn(HandlerThread.MAIN);
        registrar.playToClient(LurePayload.TYPE, LurePayload.STREAM_CODEC,
                (payload, context) -> clientLure.accept(payload));
        registrar.playToClient(ViewStartPayload.TYPE, ViewStartPayload.STREAM_CODEC,
                (payload, context) -> clientViewStart.accept(payload));
        registrar.playToClient(ViewEndPayload.TYPE, ViewEndPayload.STREAM_CODEC,
                (payload, context) -> clientViewEnd.accept(payload));
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.isAcceptingMessages()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HighwayStarAddon.MOD_ID, path);
    }

    public record LurePayload(UUID roomId, ResourceLocation dimension, HighwayStarRoomLure lure)
            implements CustomPacketPayload {
        public static final Type<LurePayload> TYPE = new Type<>(id("room_lure"));
        public static final StreamCodec<FriendlyByteBuf, LurePayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, LurePayload::roomId,
                ResourceLocation.STREAM_CODEC, LurePayload::dimension,
                HighwayStarRoomLure.STREAM_CODEC, LurePayload::lure,
                LurePayload::new);

        @Override
        public Type<LurePayload> type() {
            return TYPE;
        }
    }

    public record ViewStartPayload(UUID roomId, ResourceLocation dimension, BlockPos doorBlockPos, Direction facing,
            Vec3 entryOrigin, int durationTicks, HighwayStarRoomLure lure) implements CustomPacketPayload {
        public static final Type<ViewStartPayload> TYPE = new Type<>(id("room_view_start"));
        public static final StreamCodec<FriendlyByteBuf, ViewStartPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeUUID(payload.roomId());
                    buf.writeResourceLocation(payload.dimension());
                    buf.writeBlockPos(payload.doorBlockPos());
                    Direction.STREAM_CODEC.encode(buf, payload.facing());
                    buf.writeVec3(payload.entryOrigin());
                    buf.writeVarInt(payload.durationTicks());
                    HighwayStarRoomLure.STREAM_CODEC.encode(buf, payload.lure());
                },
                buf -> new ViewStartPayload(buf.readUUID(), buf.readResourceLocation(), buf.readBlockPos(),
                        Direction.STREAM_CODEC.decode(buf), buf.readVec3(), buf.readVarInt(),
                        HighwayStarRoomLure.STREAM_CODEC.decode(buf)));

        @Override
        public Type<ViewStartPayload> type() {
            return TYPE;
        }
    }

    public record ViewEndPayload(UUID roomId, ResourceLocation dimension) implements CustomPacketPayload {
        public static final Type<ViewEndPayload> TYPE = new Type<>(id("room_view_end"));
        public static final StreamCodec<FriendlyByteBuf, ViewEndPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, ViewEndPayload::roomId,
                ResourceLocation.STREAM_CODEC, ViewEndPayload::dimension,
                ViewEndPayload::new);

        @Override
        public Type<ViewEndPayload> type() {
            return TYPE;
        }
    }
}
