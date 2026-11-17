package rotp.addons.ger.damage;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.annotation.Nullable;

import rotp.core.api.damage.DefencePiercing;
import rotp.core.customobjects.DamageSourceModified;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.state.GerSides;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * The owner, 2026-11-08: Requiem's damage, and its holder's melee while it is
 * summoned, ignore blocking, invulnerability frames, invulnerable states and
 * damage caps. The Stand's hits carry Requiem's own damage types, whose tags
 * do most of it; the holder's melee keeps vanilla's type, so its part is done
 * here by hand. Creative and spectator players stay out.
 */
@EventBusSubscriber(modid = GoldExperienceRequiemAddon.MOD_ID)
public final class GerUnavoidableDamage {
    private static final ThreadLocal<Deque<Dealing>> DEALING =
            ThreadLocal.withInitial(ArrayDeque::new);

    private GerUnavoidableDamage() {}

    /** The hit Requiem is dealing now, with the amount it means the target to take. */
    private record Dealing(Entity target, DamageSource source, float amount) {}

    /** Test tools: no Requiem hit reaches a creative or spectator player. */
    public static boolean staysOut(@Nullable Entity target) {
        return target instanceof Player player
                && (player.isCreative() || player.isSpectator());
    }

    public static boolean isRequiemHit(DamageSource source) {
        return source.is(GerDamageTypes.REQUIEM) || isHolderMelee(source);
    }

    /**
     * The rule the core asks for a living attacker's direct hits, its melee: with his Requiem out a holder's own hit
     * passes the core's guards, invulnerable states and damage caps. Not mitigation, which only true damage passes.
     */
    public static boolean holderMeleePierces(
            LivingEntity attacker,
            DamageSource source,
            DefencePiercing.Defence defence) {
        return defence != DefencePiercing.Defence.MITIGATION
                && GoldExperienceRequiemAddon.activeStand(attacker) != null;
    }

    /** A direct hit of a holder whose Requiem is out, as that rule is asked; the Stand's own hits have their types. */
    public static boolean isHolderMelee(DamageSource source) {
        return source.getEntity() instanceof LivingEntity holder
                && holder == source.getDirectEntity()
                && GoldExperienceRequiemAddon.activeStand(holder) != null;
    }

    /**
     * Deals a hit of one of Requiem's types past the target's hurt cooldown,
     * which it leaves as it was. A cap the target's own code put on the
     * amount before any event is taken off again.
     */
    public static boolean deal(
            Entity target, DamageSource source, float amount) {
        if (target.level().isClientSide() || staysOut(target)) {
            return false;
        }
        Deque<Dealing> dealing = DEALING.get();
        dealing.push(new Dealing(target, source, amount));
        try {
            return rotp.core.util.functions.DamageUtil
                    .hurtThroughInvulTicks(target, source, amount);
        }
        finally {
            dealing.pop();
        }
    }

    /** Runs a hit the core deals for Requiem's Stand, so that {@link #deal}'s cap rule holds for it too. */
    public static boolean dealing(
            Entity target,
            DamageSource source,
            float amount,
            java.util.function.BooleanSupplier hit) {
        Deque<Dealing> dealing = DEALING.get();
        dealing.push(new Dealing(target, source, amount));
        try {
            return hit.getAsBoolean();
        }
        finally {
            dealing.pop();
        }
    }

    // Creative and spectator players stay out, though vanilla's tag on the types would let a hit through to them.
    // The invulnerable flag is lifted for the holder's melee only, whose vanilla type cannot carry that tag.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInvulnerabilityCheck(
            EntityInvulnerabilityCheckEvent event) {
        DamageSource source = event.getSource();
        boolean typed = source.is(GerDamageTypes.REQUIEM);
        if (!typed && !isHolderMelee(source)) {
            return;
        }
        Entity target = event.getEntity();
        if (staysOut(target)) {
            event.setInvulnerable(true);
        }
        else if (!typed
                && event.isInvulnerable()
                && target.isInvulnerable()
                && !target.isRemoved()) {
            event.setInvulnerable(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        boolean typed = source.is(GerDamageTypes.REQUIEM);
        if (!typed && !isHolderMelee(source)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (staysOut(target)) {
            event.setCanceled(true);
            return;
        }
        // The loop's damage is its victim's. Turned back on its caster or his own, as the core turns a hit on a Gold
        // Experience lifeform back on its attacker, it is nothing.
        if (source.is(GerDamageTypes.DEATH_LOOP)
                && source.getEntity() instanceof LivingEntity caster
                && GerSides.isOwn(caster, target)) {
            event.setCanceled(true);
            return;
        }
        Dealing dealing = DEALING.get().peek();
        if (dealing != null
                && dealing.target() == target
                && dealing.source() == source
                && event.getAmount() < dealing.amount()
                && !(source instanceof DamageSourceModified modified
                        && modified.jojo_ripples$isNonLethal())) {
            event.setAmount(dealing.amount());
        }
        if (!typed) {
            // The types skip Resistance by their tag.
            event.addReductionModifier(
                    DamageContainer.Reduction.MOB_EFFECTS,
                    (container, reduction) -> 0.0F);
        }
    }

    // Any shield, vanilla's or another mod's passive one.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked()
                && isRequiemHit(event.getDamageSource())
                && !staysOut(event.getEntity())) {
            event.setBlocked(false);
        }
    }

    /**
     * Vanilla gives no totem against damage that passes invulnerability, and
     * the owner took the totem away from the death loop only: any other of
     * Requiem's hits still leaves it to its holder.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        DamageSource source = event.getSource();
        if (living.level().isClientSide()
                || !source.is(GerDamageTypes.REQUIEM)
                || source.is(GerDamageTypes.DEATH_LOOP)
                || GoldExperienceRequiemAddon.livingState(living)
                        .undieActive()) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = living.getItemInHand(hand);
            if (held.is(Items.TOTEM_OF_UNDYING)
                    && CommonHooks.onLivingUseTotem(
                            living, source, held, hand)) {
                ItemStack used = held.copy();
                held.shrink(1);
                if (living instanceof ServerPlayer player) {
                    player.awardStat(
                            Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING), 1);
                    CriteriaTriggers.USED_TOTEM.trigger(player, used);
                    living.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
                }
                living.setHealth(1.0F);
                living.removeEffectsCuredBy(
                        EffectCures.PROTECTED_BY_TOTEM);
                living.addEffect(new MobEffectInstance(
                        MobEffects.REGENERATION, 900, 1));
                living.addEffect(new MobEffectInstance(
                        MobEffects.ABSORPTION, 100, 1));
                living.addEffect(new MobEffectInstance(
                        MobEffects.FIRE_RESISTANCE, 800, 0));
                living.level().broadcastEntityEvent(
                        living, EntityEvent.TALISMAN_ACTIVATE);
                event.setCanceled(true);
                return;
            }
        }
    }
}
