package rotp.addons.justice;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.addons.justice.JusticeAddon.Move;

@EventBusSubscriber(modid = JusticeAddon.MOD_ID)
public final class JusticeGameEvents {
    private static final Map<MinecraftServer, JusticeFogPuppet> FOG_BY_SERVER = new IdentityHashMap<>();

    private JusticeGameEvents() {}

    static void perform(ServerPlayer player, Move move) {
        var server = player.serverLevel().getServer();
        if (move == Move.FOG) {
            var fog = FOG_BY_SERVER.computeIfAbsent(server, ignored -> new JusticeFogPuppet());
            if (!fog.toggle(player)) {
                clearSubactions(player);
            }
            publish(player);
            return;
        }
        var fog = FOG_BY_SERVER.get(server);
        if (fog == null || !fog.isEnabled(player.getUUID())) {
            return;
        }
        switch (move) {
            case PUPPET -> fog.trigger(player);
            case ILLUSION -> JusticeIllusion.spawn(player);
            case DISGUISE -> {
                JusticeDisguise.cycle(player);
                publish(player);
            }
            case FOG -> throw new IllegalStateException("Fog is handled at entry");
        }
    }

    static boolean hasFog(ServerPlayer player) {
        var fog = FOG_BY_SERVER.get(player.serverLevel().getServer());
        return fog != null && fog.isEnabled(player.getUUID());
    }

    private static void clearSubactions(ServerPlayer player) {
        JusticeIllusion.dismiss(player.serverLevel().getServer(), player.getUUID());
        JusticeDisguise.clear(player);
    }

    private static void clear(ServerPlayer player, boolean publish) {
        var fog = FOG_BY_SERVER.get(player.serverLevel().getServer());
        boolean active = fog != null && fog.stop(player.getUUID());
        if (active || JusticeDisguise.getMode(player) != JusticeDisguise.Mode.NONE) {
            clearSubactions(player);
            if (publish) {
                publish(player);
            }
        }
    }

    private static JusticeVisualPayload visualState(ServerPlayer player) {
        return new JusticeVisualPayload(player.getUUID(), player.level().dimension().location(), hasFog(player),
                JusticeDisguise.getMode(player));
    }

    private static void publish(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, visualState(player));
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var fog = FOG_BY_SERVER.get(player.serverLevel().getServer());
            if (fog != null) {
                fog.tick(player);
            }
            JusticeDisguise.tick(player);
        }
    }

    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        JusticeDisguise.onLivingChangeTarget(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWound(LivingIncomingDamageEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            var fog = FOG_BY_SERVER.get(level.getServer());
            if (fog != null) {
                fog.onHurt(event.getEntity(), event.getAmount());
            }
        }
    }

    @SubscribeEvent
    public static void entityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living && living.level() instanceof ServerLevel level) {
            var fog = FOG_BY_SERVER.get(level.getServer());
            if (fog != null) {
                fog.tickInjured(living);
            }
        }
    }

    @SubscribeEvent
    public static void entityLeave(EntityLeaveLevelEvent event) {
        JusticeDisguise.onEntityLeaveLevel(event);
        if (event.getEntity() instanceof LivingEntity living && event.getLevel() instanceof ServerLevel level) {
            var fog = FOG_BY_SERVER.get(level.getServer());
            if (fog != null) {
                fog.forgetInjured(living);
            }
        }
    }

    @SubscribeEvent
    public static void cleanupPendingHouses(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 0) {
            JusticeIllusion.cleanupLoaded(level);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player, true);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player, false);
        }
    }

    @SubscribeEvent
    public static void onTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer watcher && event.getTarget() instanceof ServerPlayer target
                && (hasFog(target) || JusticeDisguise.getMode(target) != JusticeDisguise.Mode.NONE)) {
            PacketDistributor.sendToPlayer(watcher, visualState(target));
        }
    }

    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && (hasFog(player) || JusticeDisguise.getMode(player) != JusticeDisguise.Mode.NONE)) {
            publish(player);
        }
    }

    // Leaving the End makes a new player object with no death or dimension event; both states follow the UUID.
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && (hasFog(player) || JusticeDisguise.getMode(player) != JusticeDisguise.Mode.NONE)) {
            publish(player);
        }
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        JusticeDisguise.clearServer(event.getServer());
        var fog = FOG_BY_SERVER.remove(event.getServer());
        if (fog != null) {
            fog.clear();
        }
    }
}
