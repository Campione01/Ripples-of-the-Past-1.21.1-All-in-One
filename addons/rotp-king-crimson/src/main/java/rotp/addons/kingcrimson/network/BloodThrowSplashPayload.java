package rotp.addons.kingcrimson.network;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.KingCrimsonBloodSplashOverlay;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Blood Throw landed on the player this is sent to, and to nobody else: the blood goes over his screen. */
public record BloodThrowSplashPayload() implements CustomPacketPayload {
    public static final BloodThrowSplashPayload INSTANCE = new BloodThrowSplashPayload();
    public static final Type<BloodThrowSplashPayload> TYPE = new Type<>(KingCrimsonAddon.id("blood_throw_splash"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BloodThrowSplashPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    public static void handle(BloodThrowSplashPayload payload, IPayloadContext context) {
        context.enqueueWork(KingCrimsonBloodSplashOverlay::splash);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
