package rotp.addons.nextalbum.network;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem.Suit;
import rotp.addons.nextalbum.state.ProsciuttoShadow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Client to server: a worn Prosciutto piece was right-clicked in an
 * inventory screen; the server cycles the full set's look.
 */
public record ProsciuttoSuitModePayload() implements CustomPacketPayload {
    public static final ProsciuttoSuitModePayload INSTANCE =
            new ProsciuttoSuitModePayload();
    public static final Type<ProsciuttoSuitModePayload> TYPE =
            new Type<>(NextAlbumIds.id("prosciutto_suit_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            ProsciuttoSuitModePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    static void handle(
            ProsciuttoSuitModePayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !CharacterSuitArmorItem.hasFullSet(player, Suit.PROSCIUTTO)) {
            return;
        }
        int mode = CharacterSuitArmorItem.cycleWornMode(player);
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS,
                0.8F, 1.2F);
        if (mode == CharacterSuitArmorItem.MODE_STEALTH) {
            ProsciuttoShadow.onEnterStealthMode(player);
        } else {
            ProsciuttoShadow.setShadow(player, false);
        }
        player.refreshDisplayName();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
