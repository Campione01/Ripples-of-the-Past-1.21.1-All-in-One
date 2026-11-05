package rotp.addons.highwaystar;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = HighwayStarAddon.MOD_ID)
public final class HighwayStarEvents {
    private HighwayStarEvents() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(HighwayStarAddon.HIGHWAY_STAR_ENTITY.get(), StandEntity.createAttributes().build());
        event.put(HighwayStarAddon.SOLE_ENTITY.get(), HighwayStarSoleEntity.createAttributes().build());
    }

    // 1.16 CapabilityEvents.onDeath
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && player.hasData(HighwayStarAddon.SMELL_TARGETS)) {
            player.getData(HighwayStarAddon.SMELL_TARGETS).clear();
        }
    }
}
