package rotp.addons.echoes.client;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.init.EchoesBlocks;
import rotp.addons.echoes.init.EchoesDataComponents;
import rotp.addons.echoes.init.EchoesEntities;
import rotp.addons.echoes.network.EchoesOnomatopoeiaPayloads;
import rotp.addons.echoes.network.EchoesSoundPayloads;
import rotp.core.client.ui.marker.MarkerRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = RotpEchoes.MOD_ID, value = Dist.CLIENT)
public final class EchoesClientEvents {
    private EchoesClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                EchoesEntities.ACT0.get(), EchoesStandRenderer::new);
        event.registerEntityRenderer(
                EchoesEntities.TAIL_NEEDLE.get(), EchoesTailNeedleRenderer::new);
        event.registerEntityRenderer(
                EchoesEntities.ACT1.get(), EchoesStandRenderer::new);
        event.registerEntityRenderer(
                EchoesEntities.ACT2.get(), EchoesStandRenderer::new);
        event.registerEntityRenderer(
                EchoesEntities.ACT3.get(), EchoesStandRenderer::new);
        event.registerEntityRenderer(
                EchoesEntities.ONOMATOPOEIA_BLOCK.get(), EchoesOnomatopoeiaWordRenderer::new);
        event.registerBlockEntityRenderer(
                EchoesBlocks.ONOMATOPOEIA_MARK_ENTITY.get(), EchoesOnomatopoeiaMarkRenderer::new);
        event.registerBlockEntityRenderer(
                EchoesBlocks.SOUND_MARK_ENTITY.get(), EchoesSoundMarkRenderer::new);
    }

    // R772 F11, master ClientInit.addLayers: ACT1's sound haunt on every living entity and player
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof LivingEntityRenderer renderer) {
                renderer.addLayer(new EchoesSoundHauntLayer<>(renderer));
            }
        }
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof LivingEntityRenderer renderer) {
                renderer.addLayer(new EchoesSoundHauntLayer<>(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        EchoesOnomatopoeiaPayloads.installClientMarkHandler(EchoesOnomatopoeiaClient::onMark);
        EchoesSoundPayloads.installClientHandlers(EchoesSoundClient::onSync, EchoesSoundAudio::onPlay,
                EchoesSoundAudio::onLoop, EchoesSoundClient::onGiveTargets);
        event.enqueueWork(() -> MarkerRenderer.registerMarkerRenderer(new EchoesSoundGiveMarker(Minecraft.getInstance())));
    }

    // R772 F11: ACT2's words on living entities
    @EventBusSubscriber(modid = RotpEchoes.MOD_ID, value = Dist.CLIENT)
    public static final class Onomatopoeia {
        private Onomatopoeia() {}

        @SubscribeEvent
        public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
            EchoesOnomatopoeiaClient.render(event);
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            EchoesOnomatopoeiaClient.clear();
            EchoesSoundClient.clear();
            EchoesSoundAudio.clear();
        }
    }

    // 092 ClientEventHandler.onItemTooltip: mark gravity-infused stacks.
    // Game-bus listener kept apart from the mod-bus renderer hook.
    @EventBusSubscriber(modid = RotpEchoes.MOD_ID, value = Dist.CLIENT)
    public static final class Tooltips {
        private Tooltips() {}

        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            Long until = event.getItemStack().get(
                    EchoesDataComponents.GRAVITY_INFUSED_UNTIL);
            ClientLevel level = Minecraft.getInstance().level;
            if (until == null || level == null
                    || until <= level.getGameTime()) {
                return;
            }
            event.getToolTip().add(Component.literal("\u2726 ")
                    .append(Component.translatable(
                            "rotp_echoes.tooltip.gravity_infused"))
                    .withStyle(ChatFormatting.DARK_PURPLE));
            event.getToolTip().add(Component.translatable(
                            "rotp_echoes.tooltip.gravity_infused.desc")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
