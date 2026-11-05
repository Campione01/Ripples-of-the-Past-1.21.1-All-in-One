package rotp.addons.nextalbum;

import rotp.core.init.ModStatusEffects;

import rotp.addons.nextalbum.state.ResolveDowned;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Next Album 0.1.13 ResolveDownedEvents. */
@EventBusSubscriber(modid = NextAlbumIds.MOD_ID)
public final class ResolveDownedGameEvents {
    private ResolveDownedGameEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (ResolveDowned.isDowned(player)) {
                ResolveDowned.onTrueDeath(player);
            } else if (ResolveDowned.tryStart(player)) {
                event.setCanceled(true);
            }
        }
    }

    // The core's own HIGHEST death listener registers first and would spawn
    // the soul, drop the disc and end the Stand's effects before the veto
    // above, so a lethal hit is caught where it lands, after the damage is
    // taken and before vanilla's totem and death; the totem and the core's
    // Cheat Death keep their turn, as 0.1.13's veto came after them.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLethalDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.isDeadOrDying()
                && !ResolveDowned.isDowned(player)
                && !hasDeathProtection(player)) {
            ResolveDowned.tryStart(player);
        }
    }

    private static boolean hasDeathProtection(Player player) {
        return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                || player.hasEffect(ModStatusEffects.CHEAT_DEATH);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResolveDowned.tick(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        ResolveDowned.copyOnClone(event.getOriginal(), event.getEntity(),
                event.isWasDeath());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResolveDowned.syncToAll(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ResolveDowned.isDowned(player)) {
            ResolveDowned.sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ResolveDowned.isDowned(player)) {
            ResolveDowned.sync(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target
                && event.getEntity() instanceof ServerPlayer viewer) {
            ResolveDowned.sendTo(viewer, target);
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (ResolveDowned.isDowned(entity)) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (ResolveDowned.isDowned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfDowned(event.getEntity(), event.getHand(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfDowned(event.getEntity(), event.getHand(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfDowned(event.getEntity(), event.getHand(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfDowned(event.getEntity(), event.getHand(), event);
    }

    private static void cancelIfDowned(Player player, InteractionHand hand,
            ICancellableEvent event) {
        if (ResolveDowned.isDowned(player)) {
            event.setCanceled(true);
            if (hand == InteractionHand.MAIN_HAND) {
                player.stopUsingItem();
            }
        }
    }
}
