package rotp.addons.gratefuldead.client;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.GratefulDeadConfig;
import rotp.addons.gratefuldead.state.LivingAgingState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import rotp.addons.gratefuldead.service.AgingService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(
        modid = GratefulDeadAddon.MOD_ID,
        value = Dist.CLIENT)
public final class GratefulDeadClientGameEvents {
    private static boolean localPoseForced;

    private GratefulDeadClientGameEvents() {}

    @SubscribeEvent
    public static void onClientTick(
            ClientTickEvent.Post event) {
        AgingBlockClientCache.tickClient();
        AgingBlurPostEffect.syncFromLocalPlayer();
        tickAgingMist();
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            localPoseForced = LivingAgingState.applyClientPose(
                    player, localPoseForced);
        }
        else {
            localPoseForced = false;
        }
    }

    // 0.1.13 spawnMistParticles: around the camera inside an aging aura,
    // on its edge facing the camera from outside (up to 18 blocks out),
    // centred on the Stand and as wide as the aura has grown.
    private static void tickAgingMist() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()
                || !GratefulDeadConfig.agingAuraMistParticles()
                || minecraft.getCameraEntity() == null) {
            return;
        }
        RandomSource random = minecraft.level.getRandom();
        Vec3 camera = minecraft.getCameraEntity().getEyePosition(1.0F);
        for (Player owner : minecraft.level.players()) {
            if (!AgingBlockClientCache.hasWarmActiveAura(owner)) {
                continue;
            }
            double radius = AgingService.auraRadius(
                    AgingBlockClientCache.auraTicks(owner));
            if (radius < 0.35D) {
                continue;
            }
            var source = AgingService.auraCenter(owner);
            Vec3 center = new Vec3(source.getX(), source.getY() + source.getBbHeight() * 0.45D, source.getZ());
            double distance = camera.distanceTo(center);
            if (distance > radius + 18.0D) {
                continue;
            }
            boolean inside = distance <= radius + 2.0D;
            if (random.nextInt(inside ? 5 : 8) != 0) {
                continue;
            }
            double x;
            double y;
            double z;
            if (inside) {
                x = camera.x + (random.nextDouble() - 0.5D) * 16.0D;
                y = camera.y + (random.nextDouble() - 0.35D) * 3.8D;
                z = camera.z + (random.nextDouble() - 0.5D) * 16.0D;
                if (center.distanceToSqr(x, y, z) > radius * radius) {
                    continue;
                }
            }
            else {
                Vec3 direction = camera.subtract(center).normalize();
                double shell = radius * (0.82D + random.nextDouble() * 0.16D);
                x = center.x + direction.x * shell + (random.nextDouble() - 0.5D) * 4.0D;
                y = center.y + (random.nextDouble() - 0.2D) * 2.4D;
                z = center.z + direction.z * shell + (random.nextDouble() - 0.5D) * 4.0D;
            }
            minecraft.level.addParticle(
                    GratefulDeadAddon.AGING_MIST.get(), x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void computeFogColor(
            ViewportEvent.ComputeFogColor event) {
        FogState state = fogState(event);
        if (!state.active()) {
            return;
        }
        float strength = state.owner()
                ? Mth.clamp(0.18F, 0.0F, 0.4F)
                : Mth.clamp(
                        state.strength() * 0.92F,
                        0.0F,
                        0.96F);
        event.setRed(Mth.lerp(
                strength, event.getRed(), 0.085F));
        event.setGreen(Mth.lerp(
                strength, event.getGreen(), 0.08F));
        event.setBlue(Mth.lerp(
                strength, event.getBlue(), 0.072F));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderFog(
            ViewportEvent.RenderFog event) {
        FogState state = fogState(event);
        if (!state.active() || state.owner()) {
            return;
        }
        float strength = state.strength();
        float originalFar = event.getFarPlaneDistance();
        boolean sky = event.getMode() == FogMode.FOG_SKY;
        float end = Mth.lerp(
                strength,
                originalFar,
                sky ? 10.0F : 5.0F);
        float start = Mth.lerp(
                strength,
                originalFar * 0.62F,
                sky ? 0.0F : 0.35F);
        event.setNearPlaneDistance(
                Math.min(start, end * 0.85F));
        event.setFarPlaneDistance(Math.max(end, 1.0F));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        AgingBlockClientCache.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            AgingBlockClientCache.clear();
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            AgingBlockClientCache.forgetChunk(
                    event.getChunk().getPos().x,
                    event.getChunk().getPos().z);
        }
    }

    private static FogState fogState(ViewportEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null) {
            return FogState.NONE;
        }
        if (AgingBlockClientCache.hasActiveAura(
                minecraft.player)) {
            return FogState.OWNER;
        }
        var cameraPosition =
                event.getCamera().getPosition();
        float strongest = 0.0F;
        for (Player owner : minecraft.level.players()) {
            if (owner == minecraft.player
                    || !AgingBlockClientCache
                            .hasWarmActiveAura(owner)) {
                continue;
            }
            double distance =
                    cameraPosition.distanceTo(
                            AgingService.auraCenter(owner).position());
            double radius = AgingBlockClientCache.auraRadius(owner);
            if (distance >= radius) {
                continue;
            }
            float closeness = (float) (
                    1.0D - distance / radius);
            float smooth = closeness * closeness
                    * (3.0F - 2.0F * closeness);
            strongest = Math.max(strongest, smooth);
        }
        return strongest > 0.0F
                ? new FogState(
                        false,
                        Mth.clamp(
                                strongest, 0.0F, 0.98F))
                : FogState.NONE;
    }

    private record FogState(
            boolean owner, float strength) {
        private static final FogState NONE =
                new FogState(false, 0.0F);
        private static final FogState OWNER =
                new FogState(true, 1.0F);

        private boolean active() {
            return owner || strength > 0.0F;
        }
    }
}
