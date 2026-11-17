package rotp.addons.spin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.entity.WreckingBall;

@EventBusSubscriber(modid = SpinAddon.MOD_ID, value = Dist.CLIENT)
public final class SpinClientGameEvents {
    private SpinClientGameEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(SpinAddon.NEGLECT) && player.getVehicle() instanceof AbstractHorse horse
                && horse.getControllingPassenger() == player) {
            player.setYRot(WreckingBall.veerRight(player.getYRot(), SpinConfig.WRECKING_NEGLECT_VEER_DEGREES.get()));
        }
    }
}
