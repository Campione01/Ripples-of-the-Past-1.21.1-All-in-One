package rotp.addons.rhcp.client;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.RhcpService;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * 1.16 RHCPEntity.moveStandManually hopped along the conductors from the raw keys of every tick. The core's manual
 * control hands a Stand only its motion, which is zero on a press's first tick, so the user's RHCP in a conductor
 * takes the keys here: after the core's controller read them (HIGHEST) and before it clears them (LOWEST).
 */
@EventBusSubscriber(
        modid = RhcpAddon.MOD_ID,
        value = Dist.CLIENT)
public final class RhcpConductorInput {
    private RhcpConductorInput() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player) {
            return;
        }
        RhcpStandEntity stand = RhcpService.activeStand(minecraft.player);
        if (stand == null || !stand.isManuallyControlled()
                || !stand.isControlledByLocalInstance()
                || !RhcpAddon.state(minecraft.player).inConductor()) {
            return;
        }
        Input input = event.getInput();
        stand.conductorTravelInput(input.leftImpulse, input.forwardImpulse,
                input.jumping, input.shiftKeyDown);
    }
}
