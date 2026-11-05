package rotp.addons.whitesnake.network;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.buffdisc.InjectEffectClientBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** PATCH1 InjectEffect opened its screen in the client copy of the action; here the server tells the user to. */
public record OpenInjectEffectPayload() implements CustomPacketPayload {
    public static final Type<OpenInjectEffectPayload> TYPE =
            new Type<>(WhitesnakeAddon.id("open_inject_effect"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenInjectEffectPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenInjectEffectPayload());

    public static void handle(
            OpenInjectEffectPayload payload,
            IPayloadContext context) {
        context.enqueueWork(InjectEffectClientBridge::open);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
