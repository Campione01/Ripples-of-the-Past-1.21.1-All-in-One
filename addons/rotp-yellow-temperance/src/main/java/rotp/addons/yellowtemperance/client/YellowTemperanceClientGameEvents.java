package rotp.addons.yellowtemperance.client;

import rotp.core.api.client.render.ScopedPlayerModelVisibility;
import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.disguise.YellowTemperanceDisguiseState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(
        modid = YellowTemperanceAddon.MOD_ID,
        value = Dist.CLIENT)
public final class YellowTemperanceClientGameEvents {
    private YellowTemperanceClientGameEvents() {}

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity()
                instanceof AbstractClientPlayer player)) {
            return;
        }
        YellowTemperanceDisguiseState disguise =
                YellowTemperanceDisguiseState.of(player);
        if (disguise.mode() == DisguiseMode.PLAYER
                && !disguise.playerName().isBlank()) {
            event.setContent(Component.literal(
                    disguise.playerName()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hidePlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity()
                instanceof AbstractClientPlayer player)
                || !YellowTemperanceDisguiseLayer.canRender(
                        player, event.getPartialTick())) {
            return;
        }
        ScopedPlayerModelVisibility.hideForCurrentRender(
                player,
                event.getRenderer().getModel());
    }

    @SubscribeEvent
    public static void restorePlayer(RenderPlayerEvent.Post event) {
        if (event.getEntity() instanceof AbstractClientPlayer player) {
            YellowTemperanceDisguiseLayer.renderEntityDisguise(
                    player,
                    event.getRenderer().getModel(),
                    event.getPoseStack(),
                    event.getMultiBufferSource(),
                    event.getPackedLight(),
                    event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        YellowTemperanceDisguiseLayer.clearCache();
    }
}
