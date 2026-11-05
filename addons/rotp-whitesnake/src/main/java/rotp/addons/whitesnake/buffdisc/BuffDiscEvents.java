package rotp.addons.whitesnake.buffdisc;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = WhitesnakeAddon.MOD_ID)
public final class BuffDiscEvents {
    private BuffDiscEvents() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living) {
            DiscItem.tickPendingExplosion(living);
        }
    }
}
