package rotp.addons.justice.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import rotp.addons.justice.JusticeAddon;
import rotp.addons.justice.JusticeDisguise;
import rotp.addons.justice.JusticeSelectionPredicates;
import rotp.addons.justice.JusticeVisualPayload;

@EventBusSubscriber(modid = JusticeAddon.MOD_ID, value = Dist.CLIENT)
public final class JusticeClientState {
    private static final Map<UUID, JusticeVisualPayload> STATE = new HashMap<>();

    private JusticeClientState() {}

    public static void receive(JusticeVisualPayload state) {
        if (!state.fog() && state.mode() == JusticeDisguise.Mode.NONE) {
            STATE.remove(state.owner());
        }
        else {
            STATE.put(state.owner(), state);
        }
    }

    private static JusticeVisualPayload state(Player player) {
        if (player == null) {
            return null;
        }
        JusticeVisualPayload state = STATE.get(player.getUUID());
        return state != null && state.dimension().equals(player.level().dimension().location()) ? state : null;
    }

    static JusticeDisguise.Mode mode(Player player) {
        JusticeVisualPayload state = state(player);
        return state != null ? state.mode() : JusticeDisguise.Mode.NONE;
    }

    private static boolean localFog() {
        JusticeVisualPayload state = state(Minecraft.getInstance().player);
        return state != null && state.fog();
    }

    static boolean greenSelection(String move, LivingEntity user) {
        JusticeVisualPayload state = user instanceof Player player ? state(player) : null;
        return state != null && JusticeSelectionPredicates.donorGreenSelection(move, state.fog(), state.mode());
    }

    // The donor kept the terrain fog off while the camera sits on another entity, such as a piloted Stand.
    @SubscribeEvent
    public static void setFog(ViewportEvent.RenderFog event) {
        if (event.getMode() == FogRenderer.FogMode.FOG_TERRAIN && localFog()
                && event.getCamera().getEntity() == Minecraft.getInstance().player) {
            event.setNearPlaneDistance(0);
            event.setFarPlaneDistance(10);
            event.setFogShape(FogShape.SPHERE);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (localFog()) {
            event.setRed(0.58F);
            event.setGreen(0.58F);
            event.setBlue(0.58F);
        }
    }

    @SubscribeEvent
    public static void entityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Player player) {
            JusticeVisualPayload state = STATE.get(player.getUUID());
            if (state != null && state.dimension().equals(player.level().dimension().location())) {
                STATE.remove(player.getUUID(), state);
            }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATE.clear();
    }
}
