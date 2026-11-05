package rotp.addons.beachboy.network;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.addons.beachboy.event.BeachBoyEvents;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.powersystem.standpower.StandPower;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 1.16 NetworkHandler's Beach Boy packets. */
public final class BeachBoyPayloads {
    private BeachBoyPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(LineLength.TYPE, LineLength.STREAM_CODEC, LineLength::handle);
        registrar.playToServer(HitLine.TYPE, HitLine.STREAM_CODEC, HitLine::handle);
        registrar.playToServer(HookControl.TYPE, HookControl.STREAM_CODEC, HookControl::handle);
        registrar.playToServer(ReelTug.TYPE, ReelTug.STREAM_CODEC, ReelTug::handle);
        registrar.playToClient(BeachBoySettingsPayload.TYPE, BeachBoySettingsPayload.STREAM_CODEC,
                BeachBoySettingsPayload::handle);
        registrar.playToClient(BeachBoySwimPosePayload.TYPE, BeachBoySwimPosePayload.STREAM_CODEC,
                BeachBoySwimPosePayload::handle);
    }

    /** 1.16 BeachBoyLineLengthPacket: the mouse wheel over the rod pays the line out or reels it in by one step. */
    public record LineLength(boolean lengthen) implements CustomPacketPayload {
        public static final Type<LineLength> TYPE = new Type<>(BeachBoyAddon.id("line_length"));
        public static final StreamCodec<ByteBuf, LineLength> STREAM_CODEC =
                ByteBufCodecs.BOOL.map(LineLength::new, LineLength::lengthen);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(LineLength payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player) || !BeachBoyStandUtil.isHoldingRod(player)
                        || !BeachBoyStandUtil.isBeachBoySummoned(player)) {
                    return;
                }
                BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(player);
                if (stand == null) {
                    return;
                }
                float step = BeachBoyStandUtil.getReelStep(player);
                BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(player);
                if (hook == null) {
                    if (!BeachBoyStandUtil.isPathLineEnabled(StandPower.get(player)) || !payload.lengthen()) {
                        stand.adjustMaxLineLength(payload.lengthen() ? step : -step);
                    }
                }
                else {
                    hook.adjustLineLength(payload.lengthen() ? step : -step);
                }
            });
        }
    }

    /** 1.16 BeachBoyHitLinePacket: a swing at the air may strike a line in reach. */
    public record HitLine() implements CustomPacketPayload {
        public static final Type<HitLine> TYPE = new Type<>(BeachBoyAddon.id("hit_line"));
        public static final StreamCodec<ByteBuf, HitLine> STREAM_CODEC = StreamCodec.unit(new HitLine());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(HitLine payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    BeachBoyEvents.tryHitLine(player);
                }
            });
        }
    }

    /** 1.16 BeachBoyHookControlPacket: the remote hook's movement keys and view. */
    public record HookControl(float forward, float strafe, boolean jump, boolean sneak, float yaw, float pitch)
            implements CustomPacketPayload {
        public static final Type<HookControl> TYPE = new Type<>(BeachBoyAddon.id("hook_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HookControl> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, HookControl::forward,
                ByteBufCodecs.FLOAT, HookControl::strafe,
                ByteBufCodecs.BOOL, HookControl::jump,
                ByteBufCodecs.BOOL, HookControl::sneak,
                ByteBufCodecs.FLOAT, HookControl::yaw,
                ByteBufCodecs.FLOAT, HookControl::pitch,
                HookControl::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(HookControl payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) {
                    return;
                }
                BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(player);
                if (hook == null) {
                    return;
                }
                BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(player);
                if (hook.isRemoteControlled() || stand != null && stand.isManuallyControlled()) {
                    hook.setRemoteControlled(true);
                    hook.setRemoteInput(payload.forward(), payload.strafe(), payload.jump(), payload.sneak(),
                            payload.yaw(), payload.pitch());
                }
            });
        }
    }

    /** 1.16 BeachBoyReelTugPacket: turning the view while reeling tugs the hooked creature sideways or up. */
    public record ReelTug(float right, float up, float viewYaw) implements CustomPacketPayload {
        public static final Type<ReelTug> TYPE = new Type<>(BeachBoyAddon.id("reel_tug"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReelTug> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, ReelTug::right,
                ByteBufCodecs.FLOAT, ReelTug::up,
                ByteBufCodecs.FLOAT, ReelTug::viewYaw,
                ReelTug::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(ReelTug payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) {
                    return;
                }
                BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(player);
                if (hook == null || hook.getOwner() != player) {
                    return;
                }
                BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(player);
                if (stand == null || !stand.isManuallyControlled()) {
                    hook.setReelTug(Mth.clamp(payload.right(), -1.0F, 1.0F), Mth.clamp(payload.up(), -1.0F, 1.0F),
                            payload.viewYaw());
                }
            });
        }
    }
}
