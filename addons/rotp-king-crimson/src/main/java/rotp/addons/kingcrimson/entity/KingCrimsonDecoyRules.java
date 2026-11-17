package rotp.addons.kingcrimson.entity;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entity_playerwrapper.EntityAsPlayerWrapper;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.entity.XpOrbTargetingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * What a Time Erase's decoy never does, whatever its body, its Stand or the core's player wrapper of it would do by
 * themselves: it deals no damage (owner 2026-11-07: "我們的ai不打傷害", thorns on the armour it copied included) and it
 * takes nothing from the world, as all of it disappears with it ("直接消失"). And what its mind needs to know of its
 * user: whom he last hit.
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class KingCrimsonDecoyRules {
    private KingCrimsonDecoyRules() {}

    /** The decoy this entity is or is part of: the decoy, its Stand, or the player the core wraps round it. */
    @Nullable
    public static KingCrimsonTimeEraseDecoyEntity decoyOf(@Nullable Entity entity) {
        if (entity instanceof KingCrimsonTimeEraseDecoyEntity decoy) {
            return decoy;
        }
        if (entity instanceof EntityAsPlayerWrapper wrapper
                && wrapper.getEntity() instanceof KingCrimsonTimeEraseDecoyEntity decoy) {
            return decoy;
        }
        return entity instanceof StandEntity stand && stand.getUser() instanceof KingCrimsonTimeEraseDecoyEntity decoy
                ? decoy
                : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (decoyOf(event.getSource().getEntity()) != null || decoyOf(event.getSource().getDirectEntity()) != null) {
            event.setCanceled(true);
        }
    }

    // Whom a King Crimson user last hit, himself or through his Stand: his decoy will take that one for its threat.
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        Entity attacker = event.getSource().getEntity();
        LivingEntity user = attacker instanceof LivingEntity living ? StandUtil.getStandUser(living) : null;
        if (user instanceof ServerPlayer player && user != event.getEntity()
                && KingCrimsonAddon.isKingCrimsonUser(player)) {
            KingCrimsonAddon.temporalState(player).noteHit(event.getEntity(), player.level().getGameTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (decoyOf(event.getPlayer()) != null) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        if (decoyOf(event.getEntity()) != null) {
            event.setCanceled(true);
        }
    }

    // The core sends orbs to its player-like mobs; a decoy takes none.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onXpOrbTargeting(XpOrbTargetingEvent event) {
        if (decoyOf(event.getFollowingPlayer()) != null) {
            event.setFollowingPlayer(null);
        }
    }
}
