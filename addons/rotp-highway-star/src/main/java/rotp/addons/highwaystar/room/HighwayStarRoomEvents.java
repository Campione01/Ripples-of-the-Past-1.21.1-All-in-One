package rotp.addons.highwaystar.room;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarRoomEntity;

@EventBusSubscriber(modid = HighwayStarAddon.MOD_ID)
public final class HighwayStarRoomEvents {
    private HighwayStarRoomEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HighwayStarRoomService.invalidate(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HighwayStarRoomService.invalidate(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HighwayStarRoomService.invalidate(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            HighwayStarRoomService.invalidate(original);
        }
        if (event.getEntity() instanceof ServerPlayer replacement) {
            HighwayStarRoomService.invalidate(replacement);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && HighwayStarRoomService.getRoomRef(player) != null
                && !HighwayStarRoomService.hasHighwayStar(player)) {
            HighwayStarRoomService.invalidate(player);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)
                || !(event.getNewDamage() > 0.0F || event.getReduction(DamageContainer.Reduction.ABSORPTION) > 0.0F)) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            if (victim instanceof ServerPlayer viewer) {
                HighwayStarRoomService.rememberCombat(viewer, attacker);
            }
            if (attacker instanceof ServerPlayer viewer) {
                HighwayStarRoomService.rememberCombat(viewer, victim);
            }
        }
        // A pending victim farther away already ends the view by distance on the next room tick.
        for (HighwayStarRoomEntity room : level.getEntitiesOfClass(HighwayStarRoomEntity.class,
                victim.getBoundingBox().inflate(HighwayStarRoomEntity.ESCAPE_RANGE),
                candidate -> candidate.hasPendingVictim(victim.getUUID()))) {
            room.interruptView();
        }
    }
}
