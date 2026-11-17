package rotp.addons.kingcrimson.state;

import javax.annotation.Nullable;

import rotp.core.api.damage.DefencePiercing;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.entity.XpOrbTargetingEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * Owner 2026-11-07: "時刪狀態下玩家不能受到任何傷害或負面buff影響", "時刪時玩家不受包括蜘蛛絲減速等效果影響", "時刪中紅王應該不會碰到任何實體，
 * 單論與其他實體間邏輯和旁觀者模式無異". A Time Erase's user and his Stand are out of the world's reach for as long as the
 * erase runs, whatever began it, without the Spectator game mode, which other mods would see. The mixins named
 * TimeEraseReach* and the handlers here all ask {@link #isOutOfReach}, which costs one static read while no erase runs.
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class KingCrimsonEraseReach {
    private KingCrimsonEraseReach() {}

    /**
     * Whether the entity is a Time Erase's user in his erase, or his Stand. What the erase of a creature keeps in the
     * world, its fake body and the bodies that one hunts, is as such a user is.
     */
    public static boolean isOutOfReach(@Nullable Entity entity) {
        if (KingCrimsonCreatureErase.isPart(entity)) {
            return true;
        }
        if (!KingCrimsonTemporalState.anyErase() || entity == null) {
            return false;
        }
        if (entity instanceof Player player) {
            return KingCrimsonTemporalStateService.isErasing(player);
        }
        return entity instanceof StandEntity stand
                && stand.getUser() instanceof Player user
                && KingCrimsonTemporalStateService.isErasing(user);
    }

    /**
     * What still reaches him: an operator's /kill, and what the core calls piercing invulnerable states
     * (jojo_ripples:pierces_invulnerability), the owner's rule for Gold Experience Requiem's death loop: "應該能破除
     * 包括不死圖騰，紅王時刪在內的所有規避手段". Nothing else: a hit that only pierces guards or damage caps does not.
     */
    public static boolean pierces(DamageSource source) {
        return source.is(DamageTypes.GENERIC_KILL) || DefencePiercing.piercesInvulnerability(source);
    }

    // Before the damage events: no hurt sound, no flash, no knockback, no armour wear, and the void does not kill him.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (isOutOfReach(event.getEntity()) && !pierces(event.getSource())) {
            event.setInvulnerable(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (isOutOfReach(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // A harmful effect he already carries is taken off for the erase by the service; none can be put on him in it.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance() != null
                && event.getEffectInstance().getEffect().value().getCategory() == MobEffectCategory.HARMFUL
                && isOutOfReach(event.getEntity())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    // The swirls of the effects he keeps would mark the spot where nobody stands.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectParticles(EffectParticleModificationEvent event) {
        if (isOutOfReach(event.getEntity())) {
            event.setVisible(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (isOutOfReach(event.getNewAboutToBeSetTarget())
                // The fake body of a creature's erase hunts the bodies its erase keeps for it, and nothing else does.
                && !KingCrimsonCreatureErase.isOwnHunt(event.getEntity(), event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && isOutOfReach(hit.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        if (isOutOfReach(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // An orb drifting toward an empty spot would give him away.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onXpOrbTargeting(XpOrbTargetingEvent event) {
        if (isOutOfReach(event.getFollowingPlayer())) {
            event.setFollowingPlayer(null);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMount(EntityMountEvent event) {
        if (event.isMounting()
                && (isOutOfReach(event.getEntityMounting()) || isOutOfReach(event.getEntityBeingMounted()))) {
            event.setCanceled(true);
        }
    }

    // Nobody else's hand finds him either.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isOutOfReach(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isOutOfReach(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isOutOfReach(event.getTarget())) {
            event.setCanceled(true);
        }
    }
}
