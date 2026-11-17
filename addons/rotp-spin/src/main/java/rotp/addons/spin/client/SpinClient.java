package rotp.addons.spin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.breaker.BallBreakerClient;
import rotp.addons.spin.client.render.SteelBallRenderer;
import rotp.addons.spin.client.render.SpunBlockRenderer;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.entity.GyroTeacherEntity;
import rotp.addons.spin.entity.WreckingBall;
import rotp.addons.spin.equipment.SpinEquipmentClient;
import rotp.addons.spin.power.SpinData;
import rotp.addons.spin.power.SpinPowerType;
import rotp.core.client.ui.hud_power.HudElement;
import rotp.core.client.ui.hud_power.PowerHud;

@EventBusSubscriber(modid = SpinAddon.MOD_ID, value = Dist.CLIENT)
public final class SpinClient {
    private SpinClient() {}

    public static void register(IEventBus bus, ModContainer container) {
        SpinEquipmentClient.register(bus);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SpinAddon.GYRO_TEACHER.get(), GyroRenderer::new);
        event.registerEntityRenderer(SpinAddon.STEEL_BALL_ENTITY.get(), SteelBallRenderer::new);
        event.registerEntityRenderer(SpinAddon.SPUN_ITEM.get(), SpunBlockRenderer.SpunItemRenderer::new);
        event.registerEntityRenderer(SpinAddon.SPUN_BLOCK.get(), SpunBlockRenderer::new);
        BallBreakerClient.registerRenderers(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerBelowAll(SpinAddon.id("hemispatial_neglect"), SpinClient::neglect);
        PowerHud.abilityHUDInstance.addElement(new EnergyHud());
    }

    private static final class EnergyHud extends HudElement {
        private EnergyHud() {
            super("rotp_spin_energy", 81, 28, 92, 10);
        }

        @Override
        public boolean shouldRender() {
            var mc = Minecraft.getInstance();
            return !mc.options.hideGui && !hud.forContainerMenu.isTrue() && mc.player != null
                    && SpinData.of(mc.player).map(data -> data.displayMax() > 0).orElse(false);
        }

        @Override
        public void renderElement(GuiGraphics graphics, DeltaTracker delta) {
            var mc = Minecraft.getInstance();
            var data = SpinData.of(mc.player).orElse(null);
            if (data == null || data.displayMax() <= 0.0F) return;
            int x = getX(), y = getY(), width = getWidth(), height = getHeight();
            float current = SpinData.energy(mc.player);
            graphics.fill(x, y, x + width, y + height, 0xCC17181B);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF4A3E43);
            int fill = Mth.floor((width - 2) * Mth.clamp(current / data.displayMax(), 0.0F, 1.0F));
            if (fill > 0) graphics.fill(x + 1, y + 1, x + 1 + fill, y + height - 1, 0xFF000000 | SpinPowerType.COLOR);
            graphics.drawCenteredString(mc.font, Math.round(current) + " / " + Math.round(data.displayMax()),
                    x + width / 2, y + 1, 0xFFF0E6D2);
        }
    }

    private static void neglect(GuiGraphics graphics, DeltaTracker delta) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) return;
        var effect = player.getEffect(SpinAddon.NEGLECT);
        if (effect == null) return;
        float opacity = WreckingBall.neglectVeilOpacity(effect.getDuration(),
                SpinConfig.WRECKING_NEGLECT_VEIL_FADE_OUT_TICKS.get(), SpinConfig.WRECKING_NEGLECT_VEIL_OPACITY.get());
        if (opacity <= 0.0F) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight(), center = width / 2;
        int fade = Math.max(0, center - (int) (width * SpinConfig.WRECKING_NEGLECT_VEIL_FADE_WIDTH.get()));
        graphics.fill(0, 0, fade, height, (int) (255 * opacity) << 24);
        for (int x = fade; x < center; ++x) {
            int alpha = (int) (255 * opacity * (center - x - 0.5F) / (center - fade));
            graphics.fill(x, 0, x + 1, height, alpha << 24);
        }
    }

    private static final class GyroRenderer extends MobRenderer<GyroTeacherEntity, PlayerModel<GyroTeacherEntity>> {
        private static final ResourceLocation TEXTURE = SpinAddon.id("textures/entity/gyro_teacher.png");

        private GyroRenderer(EntityRendererProvider.Context context) {
            super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(GyroTeacherEntity entity) {
            return TEXTURE;
        }
    }
}
