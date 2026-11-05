package rotp.addons.rhcp.client;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Gui.updateVignetteBrightness darkens the screen corners by the light at the camera
 * entity's eyes. Inside a solid conductor (a redstone block, a lamp) that light is 0, so the
 * view of a travelling RHCP slowly turned black at the corners. While the user's RHCP is the
 * camera inside a conductor, the vignette follows the light around that block instead.
 */
@EventBusSubscriber(
        modid = RhcpAddon.MOD_ID,
        value = Dist.CLIENT)
public final class RhcpConductorView {
    // Gui.updateVignetteBrightness's rate
    private static final float VIGNETTE_RATE = 0.01F;
    private static float vignetteBeforeTick = Float.NaN;

    private RhcpConductorView() {}

    @SubscribeEvent
    public static void beforeTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        vignetteBeforeTick = travellingCamera(minecraft) != null
                ? minecraft.gui.vignetteBrightness : Float.NaN;
    }

    @SubscribeEvent
    public static void afterTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        float before = vignetteBeforeTick;
        vignetteBeforeTick = Float.NaN;
        RhcpStandEntity stand = travellingCamera(minecraft);
        if (stand == null || Float.isNaN(before) || minecraft.isPaused()) {
            return;
        }
        float target = Mth.clamp(1.0F - surroundingBrightness(stand), 0.0F, 1.0F);
        minecraft.gui.vignetteBrightness = before + (target - before) * VIGNETTE_RATE;
    }

    private static RhcpStandEntity travellingCamera(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null
                || !(minecraft.getCameraEntity() instanceof RhcpStandEntity stand)
                || stand.getUser() != minecraft.player
                || !RhcpAddon.state(minecraft.player).inConductor()) {
            return null;
        }
        return stand;
    }

    private static float surroundingBrightness(RhcpStandEntity stand) {
        Level level = stand.level();
        BlockPos eyes = BlockPos.containing(stand.getX(), stand.getEyeY(), stand.getZ());
        int light = level.getMaxLocalRawBrightness(eyes);
        for (Direction direction : Direction.values()) {
            light = Math.max(light, level.getMaxLocalRawBrightness(eyes.relative(direction)));
        }
        return LightTexture.getBrightness(level.dimensionType(), light);
    }
}
