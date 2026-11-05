package rotp.addons.madeinheaven.client;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.state.TimeLagRates;
import rotp.addons.madeinheaven.state.TimeLagService;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(
        modid = MadeInHeavenAddon.MOD_ID,
        value = Dist.CLIENT)
public final class MadeInHeavenLagClientEvents {
    private MadeInHeavenLagClientEvents() {}

    // A slowed player's walking, swimming and flying speeds are attributes,
    // but the push of the keys in mid-air is a fixed 0.02 per tick
    // (Player.getFlyingSpeed): without this a jump would carry him at full
    // speed, for m times as long.
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (player.onGround()
                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || player.isPassenger()
                || player.isFallFlying()
                || player.getAbilities().flying
                || TimeLagService.mode(player)
                        != TimeLagService.Mode.ATTRIBUTE) {
            return;
        }
        float rate = (float) TimeLagRates.rateFactor(
                TimeLagService.multiplier(player.level()));
        Input input = event.getInput();
        input.leftImpulse *= rate;
        input.forwardImpulse *= rate;
    }
}
