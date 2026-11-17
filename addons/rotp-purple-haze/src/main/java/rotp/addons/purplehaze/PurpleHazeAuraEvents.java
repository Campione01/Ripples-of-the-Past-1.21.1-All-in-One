package rotp.addons.purplehaze;

import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** The virus aura that went on with Purple Haze recalled: it runs from its user's tick and ends with him. */
@EventBusSubscriber(modid = PurpleHazeAddon.MOD_ID)
public final class PurpleHazeAuraEvents {
    private PurpleHazeAuraEvents() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living
                && !living.level().isClientSide()
                && living.hasData(PurpleHazeAddon.RECALLED_AURA)) {
            PurpleHazeEntity.tickRecalledAura(living);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        endAura(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        endAura(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        endAura(event.getEntity());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        endAura(event.getOriginal());
        endAura(event.getEntity());
    }

    private static void endAura(LivingEntity living) {
        if (!living.level().isClientSide()
                && living.hasData(PurpleHazeAddon.RECALLED_AURA)) {
            living.removeData(PurpleHazeAddon.RECALLED_AURA);
        }
    }
}
