package rotp.addons.highwaystar.network;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarEntity;
import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import rotp.addons.highwaystar.smell.SmellTargets;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The 1.16 "highwaystar:main" and "highwaystar:smell" channels. */
public final class HighwayStarPayloads {
    private static Consumer<SmellStatePayload> clientSmellHandler = payload -> {};
    private static Consumer<OpenTrackingScreenPayload> clientOpenTrackingHandler = payload -> {};

    private HighwayStarPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").executesOn(HandlerThread.MAIN);
        registrar.playToClient(SmellStatePayload.TYPE, SmellStatePayload.STREAM_CODEC,
                (payload, context) -> clientSmellHandler.accept(payload));
        registrar.playToClient(OpenTrackingScreenPayload.TYPE, OpenTrackingScreenPayload.STREAM_CODEC,
                (payload, context) -> clientOpenTrackingHandler.accept(payload));
        registrar.playToServer(TrackTargetPayload.TYPE, TrackTargetPayload.STREAM_CODEC,
                HighwayStarPayloads::handleTrackTarget);
    }

    public static void installClientHandlers(Consumer<SmellStatePayload> smell,
            Consumer<OpenTrackingScreenPayload> openTracking) {
        clientSmellHandler = Objects.requireNonNull(smell);
        clientOpenTrackingHandler = Objects.requireNonNull(openTracking);
    }

    public static void sendSmell(ServerPlayer player, boolean enabled, float radius) {
        PacketDistributor.sendToPlayer(player, new SmellStatePayload(enabled, player.getUUID(), radius));
    }

    public static void openTrackingScreen(ServerPlayer player, List<UUID> targets) {
        PacketDistributor.sendToPlayer(player, new OpenTrackingScreenPayload(targets));
    }

    public static void sendTrackTarget(UUID target) {
        PacketDistributor.sendToServer(new TrackTargetPayload(target));
    }

    // 1.16 C2STrackTargetPacket.handle
    private static void handleTrackTarget(TrackTargetPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || payload.target().equals(player.getUUID())) {
            return;
        }
        SmellTargets smelled = player.getData(HighwayStarAddon.SMELL_TARGETS);
        if (!smelled.getTargets().contains(payload.target())) {
            return;
        }
        Entity target = player.level() instanceof ServerLevel serverLevel ? serverLevel.getEntity(payload.target()) : null;
        if (target == null || target == player || target instanceof HighwayStarSoleEntity
                || target instanceof StandEntity || target.distanceToSqr(player) > 4096.0) {
            smelled.removeTarget(payload.target());
            return;
        }
        smelled.setSelectedTrackingTarget(payload.target());
        StandPower power = StandPower.get(player);
        if (power != null && power.getSummonedStandEntity() instanceof HighwayStarEntity hs) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    HighwayStarAddon.TRACKING_VOICE.get(), player.getSoundSource(), 1.0F, 1.0F);
            hs.setTrackingTarget(payload.target());
            if (hs.isSplit()) {
                int i = 0;
                for (HighwayStarSoleEntity sole : HighwayStarEntity.ownedSoles(player.level(), player, 128.0)) {
                    sole.attachTo(payload.target(), i++);
                }
            }
            else {
                hs.forceSplit();
            }
        }
    }

    public record SmellStatePayload(boolean enabled, UUID caster, float radius) implements CustomPacketPayload {
        public static final Type<SmellStatePayload> TYPE = new Type<>(HighwayStarAddon.id("smell"));
        public static final StreamCodec<FriendlyByteBuf, SmellStatePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SmellStatePayload::enabled,
                UUIDUtil.STREAM_CODEC, SmellStatePayload::caster,
                ByteBufCodecs.FLOAT, SmellStatePayload::radius,
                SmellStatePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenTrackingScreenPayload(List<UUID> targets) implements CustomPacketPayload {
        public static final Type<OpenTrackingScreenPayload> TYPE = new Type<>(HighwayStarAddon.id("open_tracking_screen"));
        public static final StreamCodec<FriendlyByteBuf, OpenTrackingScreenPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenTrackingScreenPayload::targets,
                OpenTrackingScreenPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TrackTargetPayload(UUID target) implements CustomPacketPayload {
        public static final Type<TrackTargetPayload> TYPE = new Type<>(HighwayStarAddon.id("track_target"));
        public static final StreamCodec<FriendlyByteBuf, TrackTargetPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, TrackTargetPayload::target,
                TrackTargetPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
