package rotp.addons.beachboy.network;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.util.BeachBoySettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 1.16 BeachBoySettingsEffect.syncWithUserOnly: the owner's copy of its switches and line length. */
public record BeachBoySettingsPayload(boolean pathLine, boolean crushDamage, boolean soundSense, float maxLength)
        implements CustomPacketPayload {
    public static final Type<BeachBoySettingsPayload> TYPE = new Type<>(BeachBoyAddon.id("settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeachBoySettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BeachBoySettingsPayload::pathLine,
            ByteBufCodecs.BOOL, BeachBoySettingsPayload::crushDamage,
            ByteBufCodecs.BOOL, BeachBoySettingsPayload::soundSense,
            ByteBufCodecs.FLOAT, BeachBoySettingsPayload::maxLength,
            BeachBoySettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BeachBoySettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null) {
                BeachBoySettings.applyFromServer(player, payload.pathLine(), payload.crushDamage(),
                        payload.soundSense(), payload.maxLength());
            }
        });
    }
}
