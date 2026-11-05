package rotp.addons.cream;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.impl.stands._entitybase.StandMeleeReach;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.cream.entity.CreamEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = CreamAddon.MOD_ID)
public final class CreamGameEvents {
    private CreamGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                CreamAddon.CREAM_ENTITY.get(),
                CreamEntity.createAttributes().build());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(CreamAddon.BLEEDING)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        CreamEntity active = CreamEntity.activeFor(target);
        // 1.16 SemiVoidUserDamageMixin let a Stand's own blow reach the user
        // inside Cream; the ball and the dash (VoidedEffect) let nothing in.
        if (active != null
                && active.isInVoidState()
                && !event.getSource().is(
                        DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !(active.getVoidMode() == CreamEntity.VoidMode.SEMI
                        && event.getSource().getDirectEntity()
                                instanceof StandEntity)) {
            event.setCanceled(true);
            return;
        }

        // A hit during a void charge is the core held-action rule
        // (CreamVoidAbility opts in behind its config options).
        applyCreamMelee(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAbilityUse(
            RipplesAbilityKeyPressEvent event) {
        CreamEntity stand = CreamEntity.activeFor(
                event.getEntity());
        if (stand == null || !stand.isInVoidState()) {
            return;
        }
        ResourceLocation abilityId =
                event.getAbility().abilityType.registryKey;
        String path = abilityId.getPath();
        if (!CreamAddon.CREAM_VOID_DASH_NAME.equals(path)
                && !CreamAddon.CREAM_VOID_BALL_CANCEL_NAME
                        .equals(path)
                && !CreamAddon.CREAM_SEMI_VOID_CANCEL_NAME
                        .equals(path)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living.level().isClientSide()) {
            return;
        }
        boolean trueBlind =
                living.hasEffect(CreamAddon.TRUE_BLINDNESS);
        if (trueBlind) {
            living.setSprinting(false);
            living.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS,
                    4,
                    0,
                    false,
                    false,
                    false));
            living.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS,
                    4,
                    0,
                    false,
                    false,
                    false));
        }

        if ((living.hasEffect(CreamAddon.VOIDED)
                        || living.hasEffect(
                                CreamAddon.INSIDE_CREAM))
                && !CreamEntity.isInVoidState(living)) {
            living.removeEffect(CreamAddon.VOIDED);
            living.removeEffect(CreamAddon.INSIDE_CREAM);
            living.removeEffect(CreamAddon.TRUE_BLINDNESS);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(PlayerEvent.BreakSpeed event) {
        if (isInteractionRestricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (isInteractionRestricted(event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(
            BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player
                && isInteractionRestricted(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isInteractionRestricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (isInteractionRestricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (isInteractionRestricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(
            PlayerInteractEvent.LeftClickBlock event) {
        if (isInteractionRestricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimensionChange(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        cleanup(event.getOriginal());
        cleanup(event.getEntity());
    }

    private static void applyCreamMelee(
            LivingIncomingDamageEvent event) {
        Entity source = event.getSource().getEntity();
        CreamEntity stand = source instanceof CreamEntity cream
                ? cream
                : event.getSource().getDirectEntity()
                        instanceof CreamEntity cream
                                ? cream
                                : null;
        if (stand == null) {
            return;
        }
        EntityActionInstance action = stand.getCurStandAction();
        ResourceLocation id = action != null
                        && action.ability instanceof Ability ability
                ? ability.abilityType.registryKey
                : null;
        if (id == null || !CreamAddon.MOD_ID.equals(id.getNamespace())) {
            return;
        }

        LivingEntity target = event.getEntity();
        // Owner T2: past the base reach these blows set their own damage
        // and knockback over the core's weakened ones, so they and their
        // effects take the core's factor (1 within the base reach).
        float reach = StandMeleeReach.strikeFactor(stand, target);
        String path = id.getPath();
        switch (path) {
            case CreamAddon.CREAM_CLAW_NAME -> {
                event.setAmount(Math.max(
                        1.0F,
                        (float) stand.getAttackDamage() - 10.0F)
                        * reach);
                applyStackingBleeding(stand, target, 25, reach);
                reduceKnockback(event, 1.5F, reach);
            }
            case CreamAddon.CREAM_HEAVY_CLAW_NAME -> {
                event.setAmount(Math.max(
                        1.0F,
                        (float) stand.getAttackDamage() - 0.5F)
                        * reach);
                applyEffect(target, CreamAddon.BLEEDING,
                        scaled(35, reach), 1);
                interruptBlocking(stand, target, reach);
                reduceKnockback(event, 3.0F, reach);
            }
            case CreamAddon.CREAM_DOUBLE_CLAW_FINISHER_NAME -> {
                applyEffect(target, CreamAddon.BLEEDING,
                        scaled(40, reach), 2);
                interruptBlocking(stand, target, reach);
                reduceKnockback(event, 15.0F, reach);
            }
            case CreamAddon.CREAM_FINISHER_PUNCH_NAME -> {
                applyEffect(
                        target,
                        MobEffects.MOVEMENT_SLOWDOWN,
                        scaled(120, reach),
                        1);
                applyEffect(target, MobEffects.CONFUSION,
                        scaled(120, reach), 0);
                interruptBlocking(stand, target, reach);
                reduceKnockback(event, 15.0F, reach);
            }
            case CreamAddon.CREAM_CHOP_NAME -> {
                event.setAmount(
                        (float) stand.getAttackDamage() * 1.05F
                                * reach);
                applyEffect(target, CreamAddon.WEAK_NECK,
                        scaled(35, reach), 0);
                applyEffect(target, CreamAddon.BLEEDING,
                        scaled(35, reach), 2);
                applyEffect(
                        target,
                        MobEffects.MOVEMENT_SLOWDOWN,
                        scaled(120, reach),
                        1);
                // 1.16 CreamChop :97: Effects.field_76437_t, WEAKNESS
                applyEffect(target, MobEffects.WEAKNESS,
                        scaled(120, reach), 0);
                interruptBlocking(stand, target, reach);
                reduceKnockback(event, 5.0F, reach);
            }
            default -> {}
        }
    }

    private static int scaled(int duration, float reach) {
        return reach >= 1.0F
                ? duration
                : Math.max(1, Math.round(duration * reach));
    }

    // A weakened claw still bleeds; its stack climbs by the factor's chance.
    private static void applyStackingBleeding(
            CreamEntity stand,
            LivingEntity target,
            int duration,
            float reach) {
        MobEffectInstance current =
                target.getEffect(CreamAddon.BLEEDING);
        int amplifier = current == null
                ? 0
                : reach >= 1.0F
                                || stand.getRandom().nextFloat() < reach
                        ? Math.min(2, current.getAmplifier() + 1)
                        : current.getAmplifier();
        target.removeEffect(CreamAddon.BLEEDING);
        applyEffect(
                target, CreamAddon.BLEEDING,
                scaled(duration, reach), amplifier);
    }

    private static void applyEffect(
            LivingEntity target,
            net.minecraft.core.Holder<
                    net.minecraft.world.effect.MobEffect> effect,
            int duration,
            int amplifier) {
        target.addEffect(new MobEffectInstance(
                effect,
                duration,
                amplifier,
                false,
                false,
                true));
    }

    // 1.16 StandEntityPunch.reduceKnockback(value) set the knockback to
    // clamp(value, 0, 1): Cream's 1.5 to 15 kept the plain hit's knockback
    // and dropped the attack's extra push.
    private static void reduceKnockback(
            LivingIncomingDamageEvent event,
            float legacyValue,
            float reach) {
        if (event.getSource()
                instanceof DamageSourceModified modified) {
            modified.jojo_ripples$modifyKnockback(
                    0.0F,
                    Mth.clamp(legacyValue, 0.0F, 1.0F) * reach);
        }
    }

    // A guard break has no strength: past the base reach it lands with
    // the factor as its chance.
    private static void interruptBlocking(
            CreamEntity stand, LivingEntity target, float reach) {
        if (target.isBlocking()
                && (reach >= 1.0F
                        || stand.getRandom().nextFloat() < reach)) {
            target.stopUsingItem();
        }
    }

    private static boolean isInteractionRestricted(Player player) {
        return CreamEntity.isInVoidState(player)
                || player.hasEffect(CreamAddon.VOIDED)
                || player.hasEffect(CreamAddon.INSIDE_CREAM);
    }

    private static void cleanup(LivingEntity living) {
        if (living == null || living.level().isClientSide()) {
            return;
        }
        CreamEntity active = CreamEntity.activeFor(living);
        if (active != null) {
            active.endVoidMode(false);
        }
        living.removeEffect(CreamAddon.VOIDED);
        living.removeEffect(CreamAddon.INSIDE_CREAM);
        living.removeEffect(CreamAddon.TRUE_BLINDNESS);
    }
}
