package rotp.addons.beachboy.network;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.util.BeachBoyPathSwim;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 1.16 BeachBoySwimPosePacket: a creature dragged or slammed by the line lies swimming for that many ticks. */
public record BeachBoySwimPosePayload(int entityId, int remainingTicks) implements CustomPacketPayload {
    public static final Type<BeachBoySwimPosePayload> TYPE = new Type<>(BeachBoyAddon.id("swim_pose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeachBoySwimPosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BeachBoySwimPosePayload::entityId,
            ByteBufCodecs.VAR_INT, BeachBoySwimPosePayload::remainingTicks,
            BeachBoySwimPosePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BeachBoySwimPosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null) {
                BeachBoyPathSwim.applyFromPacket(player.level().getEntity(payload.entityId()), payload.remainingTicks());
            }
        });
    }
}
