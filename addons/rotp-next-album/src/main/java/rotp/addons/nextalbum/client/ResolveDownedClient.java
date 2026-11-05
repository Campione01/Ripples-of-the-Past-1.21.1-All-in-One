package rotp.addons.nextalbum.client;

import java.util.Iterator;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.client.render.StandMaterialTintPolicies;
import rotp.core.init.ModParticles;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.network.ResolveDownedGiveUpPayload;
import rotp.addons.nextalbum.state.ResolveDowned;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/** Next Album 0.1.13 ResolveDownedClient: the downed view, the give-up hold, the timer bar and the Stand fade. */
@EventBusSubscriber(
        modid = NextAlbumIds.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class ResolveDownedClient {
    private static final ResourceLocation ADDITIONAL_UI =
            ResourceLocation.fromNamespaceAndPath("jojo_ripples", "textures/gui/additional.png");
    private static final int BAR_WIDTH = 182;
    private static boolean lastGiveUpPacket;

    private ResolveDownedClient() {}

    /** 0.1.13 StandEntityDownedMixin.getAlpha: a downed user's Stand (not Sex Pistols) fades out. */
    public static void registerStandFade() {
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_STAND_MATERIAL_TINT_POLICIES_V1)) {
            return;
        }
        StandMaterialTintPolicies.register(NextAlbumIds.id("resolve_downed"), query -> {
            StandEntity stand = query.stand();
            LivingEntity user = stand.getUser();
            if (user == null || ResolveDowned.isSexPistolsUser(user)) {
                return null;
            }
            float fade = ResolveDowned.standAlpha(user);
            if (fade >= 0.999F) {
                return null;
            }
            return argb -> {
                int alpha = Math.round((argb >>> 24) * fade);
                return alpha << 24 | argb & 0xFFFFFF;
            };
        });
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        spawnSoulParticles(mc);
        pruneDeadStates(mc);
        ResolveDowned.View view = ResolveDowned.clientView(player.getId());
        if (view == null) {
            if (lastGiveUpPacket) {
                PacketDistributor.sendToServer(new ResolveDownedGiveUpPayload(false));
                lastGiveUpPacket = false;
            }
            return;
        }
        player.deathTime = ResolveDowned.DOWNED_POSE_TICKS;
        boolean holding = player.isShiftKeyDown() && mc.options.keyJump.isDown();
        if (holding != lastGiveUpPacket) {
            PacketDistributor.sendToServer(new ResolveDownedGiveUpPayload(holding));
            lastGiveUpPacket = holding;
        }
        if (holding) {
            player.displayClientMessage(Component.translatable(
                    "message.rotp_nextalbum.resolve_downed.give_up"), true);
        }
    }

    private static void pruneDeadStates(Minecraft mc) {
        Iterator<Integer> iterator = ResolveDowned.clientViewIds().iterator();
        while (iterator.hasNext()) {
            Entity entity = mc.level.getEntity(iterator.next());
            if (!(entity instanceof LivingEntity living) || ResolveDowned.isActuallyDead(living)) {
                iterator.remove();
            }
        }
    }

    private static void spawnSoulParticles(Minecraft mc) {
        long gameTime = mc.level.getGameTime();
        for (Integer entityId : ResolveDowned.clientViewIds()) {
            if (!(mc.level.getEntity(entityId) instanceof LivingEntity living)) {
                continue;
            }
            if (gameTime % 10L == 5L) {
                mc.level.addParticle(ParticleTypes.SOUL,
                        living.getRandomX(1.0D),
                        living.getY(living.getRandom().nextDouble() * 0.25D),
                        living.getRandomZ(1.0D),
                        living.getRandom().nextGaussian() * 0.02D,
                        living.getRandom().nextGaussian() * 0.02D,
                        living.getRandom().nextGaussian() * 0.02D);
            }
            if (gameTime % 8L == 0L) {
                for (int i = 0; i < 3; i++) {
                    mc.level.addParticle(ModParticles.SOUL_CLOUD.get(),
                            living.getRandomX(1.0D),
                            living.getRandomY(),
                            living.getRandomZ(1.0D),
                            living.getRandom().nextGaussian() * 0.02D,
                            living.getRandom().nextGaussian() * 0.02D,
                            living.getRandom().nextGaussian() * 0.02D);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!ResolveDowned.isDowned(event.getEntity())) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.jumping = false;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        if (!event.getEntity().isShiftKeyDown()
                || !Minecraft.getInstance().options.keyJump.isDown()) {
            input.shiftKeyDown = event.getEntity().isShiftKeyDown();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void cancelHands(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ResolveDowned.isDowned(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void cancelDeathScreen(ScreenEvent.Opening event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getNewScreen() instanceof DeathScreen
                && player != null && ResolveDowned.isDowned(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!ResolveDowned.isDowned(entity)) {
            return;
        }
        if (ResolveDowned.isActuallyDead(entity)) {
            ResolveDowned.setClientView(entity.getId(), null);
            return;
        }
        entity.deathTime = ResolveDowned.DOWNED_POSE_TICKS;
    }

    // 0.1.13 replaced the experience bar with the time left, on the core's bar sprite.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void renderTimer(RenderGuiLayerEvent.Pre event) {
        boolean bar = VanillaGuiLayers.EXPERIENCE_BAR.equals(event.getName());
        if (!bar && !VanillaGuiLayers.EXPERIENCE_LEVEL.equals(event.getName())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ResolveDowned.View view = player != null && !player.isSpectator()
                ? ResolveDowned.clientView(player.getId())
                : null;
        if (view == null) {
            return;
        }
        event.setCanceled(true);
        if (!bar) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - 32 + 3;
        float remaining = view.duration() <= 0 ? 0.0F : (float) view.ticksLeft() / view.duration();
        int fill = (int) (remaining * (BAR_WIDTH + 1));
        graphics.blit(ADDITIONAL_UI, x, y, 0, 0.0F, 208.0F, BAR_WIDTH, 5, 256, 256);
        if (fill > 0) {
            graphics.blit(ADDITIONAL_UI, x, y, 0, 0.0F, 213.0F, Math.min(fill, BAR_WIDTH), 5, 256, 256);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ResolveDowned.clearClientViews();
        lastGiveUpPacket = false;
    }
}
