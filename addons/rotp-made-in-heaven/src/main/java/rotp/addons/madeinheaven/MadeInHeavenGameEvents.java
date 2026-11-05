package rotp.addons.madeinheaven;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.madeinheaven.state.TimeAccelerationService;
import rotp.addons.madeinheaven.state.TimeLagService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = MadeInHeavenAddon.MOD_ID)
public final class MadeInHeavenGameEvents {
    private MadeInHeavenGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                MadeInHeavenAddon
                        .MADE_IN_HEAVEN_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onServerTick(
            ServerTickEvent.Post event) {
        TimeAccelerationService.tick(event.getServer());
    }

    // Rule G3: a rewind between two ticks does not set the day time back.
    @SubscribeEvent
    public static void onServerTickStart(
            ServerTickEvent.Pre event) {
        TimeAccelerationService.reassertDayTime(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            TimeLagService.onPlayerLogin(player);
            TimeAccelerationService.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(
            LivingDeathEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            TimeAccelerationService.clearIfOwner(
                    player,
                    TimeAccelerationService
                            .ClearReason.OWNER_DEATH);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            TimeAccelerationService.clearIfOwner(
                    player,
                    TimeAccelerationService
                            .ClearReason.OWNER_LOGOUT);
            TimeLagService.onPlayerLogout(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            TimeAccelerationService.clearIfOwner(
                    player,
                    TimeAccelerationService
                            .ClearReason.DIMENSION_CHANGE);
            // The client starts the new level with an empty snapshot.
            TimeAccelerationService.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            TimeAccelerationService.syncTo(player);
        }
    }

    // After the core's time stop, which cancels a frozen being's tick.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityTickPre(
            EntityTickEvent.Pre event) {
        TimeLagService.onEntityTickPre(event);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityTickPost(
            EntityTickEvent.Post event) {
        TimeLagService.onEntityTickPost(event);
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(
            EntityLeaveLevelEvent event) {
        TimeLagService.onEntityLeaveLevel(event.getEntity());
    }

    @SubscribeEvent
    public static void onUseItemTick(
            LivingEntityUseItemEvent.Tick event) {
        TimeLagService.onUseItemTick(event);
    }

    // Before the core stacks a stopped target's knockback.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKnockBack(
            LivingKnockBackEvent event) {
        TimeLagService.onKnockBack(event);
    }

    @SubscribeEvent
    public static void onExplosionKnockback(
            ExplosionKnockbackEvent event) {
        TimeLagService.onExplosionKnockback(event);
    }

    @SubscribeEvent
    public static void onServerStopping(
            ServerStoppingEvent event) {
        TimeAccelerationService.clear(
                event.getServer().overworld(),
                TimeAccelerationService
                        .ClearReason.SERVER_STOP);
    }
}
