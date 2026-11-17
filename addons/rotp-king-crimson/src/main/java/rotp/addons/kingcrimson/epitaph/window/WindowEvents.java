package rotp.addons.kingcrimson.epitaph.window;

import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal.Participant;

/**
 * What the game tells the journal of a window (EPITAPH-ARCH 3.5): who acts in the tick of an entity, and what a
 * participant hurts, uses and takes. Dead while no window is open.
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class WindowEvents {
    private WindowEvents() {}

    // After everybody who may cancel the tick: a tick that does not happen has no end, and nobody would stop acting.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityTickStart(EntityTickEvent.Pre event) {
        if (!WindowJournal.active || event.getEntity().level().isClientSide()) {
            return;
        }
        WindowJournal.act(event.getEntity());
    }

    // After the core, which ticks the powers and the actions of the entity here.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityTickEnd(EntityTickEvent.Post event) {
        if (!WindowJournal.active || event.getEntity().level().isClientSide()) {
            return;
        }
        WindowJournal.rest();
    }

    // Nobody acts between the brackets: one that never ended, because what it was around threw, ends here at the latest.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelTickStart(LevelTickEvent.Pre event) {
        if (!WindowJournal.active || event.getLevel().isClientSide()) {
            return;
        }
        WindowJournal.rest();
    }

    @SubscribeEvent
    public static void onServerTickEnd(ServerTickEvent.Post event) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.rest();
    }

    // After the wall: a hit that does not land is nothing to undo.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!WindowJournal.active || event.getEntity().level().isClientSide()) {
            return;
        }
        Participant by = WindowJournal.actorOr(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (by != null) {
            WindowJournal.touch(by, event.getEntity());
        }
    }

    // Before the effect is on it: what a potion of his did to a creature is taken off with the rest.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEffect(MobEffectEvent.Added event) {
        if (!WindowJournal.active || event.getEntity().level().isClientSide()) {
            return;
        }
        Participant by = WindowJournal.actorOr(event.getEffectSource(), null);
        if (by != null) {
            WindowJournal.touch(by, event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!WindowJournal.active || event.getLevel().isClientSide()) {
            return;
        }
        Participant by = WindowJournal.participantOf(event.getEntity());
        if (by != null) {
            WindowJournal.touch(by, event.getTarget());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!WindowJournal.active || event.getLevel().isClientSide()) {
            return;
        }
        Participant by = WindowJournal.participantOf(event.getEntity());
        if (by != null) {
            WindowJournal.touch(by, event.getTarget());
        }
    }

    // What lies on the ground and a participant takes is kept as it lies, before it is in his hands.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!WindowJournal.active || event.getPlayer().level().isClientSide() || event.canPickup() == TriState.FALSE) {
            return;
        }
        Participant by = WindowJournal.participantOf(event.getPlayer());
        if (by != null) {
            WindowJournal.touch(by, event.getItemEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExperiencePickup(PlayerXpEvent.PickupXp event) {
        if (!WindowJournal.active || event.getEntity().level().isClientSide()) {
            return;
        }
        Participant by = WindowJournal.participantOf(event.getEntity());
        if (by != null) {
            WindowJournal.touch(by, event.getOrb());
        }
    }

    // What falls from one who dies is his who stands behind the death: whoever struck the last blow, the loot goes with
    // the pull-back that brings the dead back.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (!WindowJournal.active || event.getEntity().level().isClientSide()) {
            return;
        }
        Participant behind = WindowJournal.actorOf(event.getEntity());
        if (behind == null) {
            behind = WindowJournal.actor();
        }
        if (behind != null) {
            for (ItemEntity drop : event.getDrops()) {
                WindowJournal.mark(behind, drop);
            }
        }
    }

    @SubscribeEvent
    public static void onGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!WindowJournal.active || event.getEntity().level().isClientSide()) {
            return;
        }
        WindowJournal.holeIfParticipant(event.getEntity(), "his game mode was changed");
    }
}
