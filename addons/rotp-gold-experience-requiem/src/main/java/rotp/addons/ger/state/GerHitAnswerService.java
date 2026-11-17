package rotp.addons.ger.state;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.GoldExperienceRequiemGameEvents;
import rotp.addons.ger.ability.GerDisablerAbility;
import rotp.addons.ger.damage.GerUnavoidableDamage;
import rotp.addons.ger.network.GerAnswerPayloads;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A hit on a Requiem user returned to zero, by the owner's two rules of
 * 2026-11-08, which differ only in their {@link Policy}: for the Disabler's 9
 * seconds every hit, free; while Requiem is summoned a hit that would leave a
 * lasting harmful effect or take more than 7% of his maximum health, paid in
 * Stand stamina. The hit is refused before it lands, so nothing of it has to
 * be undone on him but what its cause did beforehand; then its attacker is
 * put back to where its attack began.
 */
@EventBusSubscriber(modid = GoldExperienceRequiemAddon.MOD_ID)
public final class GerHitAnswerService {
    /** The Disabler's own length: its user is untouchable for as long as its victims are held. */
    public static final int UNTOUCHABLE_TICKS =
            GerDisablerAbility.LEGACY_DURATION_TICKS;
    /** The owner: "傷害大於7%最大生命值的攻擊". */
    public static final float HEAVY_HIT_SHARE = 0.07F;
    public static final float STAMINA_PER_DAMAGE = 10.0F;
    /** A lasting effect counts as a set amount. */
    public static final float STAMINA_PER_EFFECT = 50.0F;
    /** As long as vanilla's hurt cooldown keeps the same harm from landing again. */
    public static final int EFFECT_ANSWER_TICKS = 10;
    private static final int GLOW_RESEND_TICKS = 20;
    /** The rewind's gold, as its storm and its ghosts have it. */
    private static final Vector3f GOLD =
            new Vector3f(1.0F, 0.9F, 0.31F);
    private static final ThreadLocal<Integer> OWN_EFFECTS =
            ThreadLocal.withInitial(() -> 0);

    private GerHitAnswerService() {}

    public enum Policy {
        /** The Disabler's window: no threshold and no cost. */
        FREE,
        /** Requiem summoned: above the threshold, for stamina. */
        PAID
    }

    @Nullable
    public static Policy policyOf(LivingEntity user) {
        if (GerUnavoidableDamage.staysOut(user)) {
            return null;
        }
        if (GoldExperienceRequiemAddon.livingState(user).untouchable()) {
            return Policy.FREE;
        }
        return GoldExperienceRequiemAddon.activeStand(user) != null
                ? Policy.PAID
                : null;
    }

    /** The Disabler went off: its user cannot be touched while it lasts. */
    public static void startUntouchable(ServerPlayer user) {
        GoldExperienceRequiemAddon.livingState(user)
                .startUntouchable(UNTOUCHABLE_TICKS);
        sendGlow(user, UNTOUCHABLE_TICKS);
    }

    /** Runs the effects a lockdown gives itself, which are no hit to answer. */
    public static void applyingOwnEffects(Runnable effects) {
        OWN_EFFECTS.set(OWN_EFFECTS.get() + 1);
        try {
            effects.run();
        }
        finally {
            OWN_EFFECTS.set(OWN_EFFECTS.get() - 1);
        }
    }

    /** Every tick of every living entity: one number kept, and the window counted down. */
    public static void tick(LivingEntity living, GerLivingState state) {
        state.sampleFire(living.getRemainingFireTicks());
        if (!state.untouchable()) {
            return;
        }
        int left = state.tickUntouchable();
        if (left == 0 || left % GLOW_RESEND_TICKS == 0) {
            sendGlow(living, left);
        }
    }

    private static void sendGlow(LivingEntity user, int ticks) {
        if (user.level() instanceof ServerLevel level) {
            GerAnswerPayloads.GlowPayload payload =
                    new GerAnswerPayloads.GlowPayload(user.getId(), ticks);
            for (ServerPlayer player : level.players()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity user = event.getEntity();
        DamageSource source = event.getSource();
        float amount = event.getAmount();
        if (user.level().isClientSide()
                || amount <= 0.0F
                // an operator's command is no hit
                || source.is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        Policy policy = policyOf(user);
        if (policy == null) {
            return;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && !GerSides.isOwn(user, attacker)) {
            GerAttackOrigins.watch(user, bodyOf(attacker));
        }
        // What vanilla's hurt cooldown or his shield stops by itself needs no answer.
        boolean cooling = user.invulnerableTime > 10
                && !source.is(DamageTypeTags.BYPASSES_COOLDOWN);
        if (cooling && amount <= user.lastHurt
                || user.isDamageSourceBlocked(source)) {
            return;
        }
        GerLivingState state =
                GoldExperienceRequiemAddon.livingState(user);
        float taken = damageTaken(
                user, source, cooling ? amount - user.lastHurt : amount);
        boolean ignites = ignites(user, state, source);
        float cost = 0.0F;
        if (policy == Policy.PAID) {
            boolean heavy =
                    taken > HEAVY_HIT_SHARE * user.getMaxHealth();
            if (!heavy && !ignites) {
                return;
            }
            cost = (heavy ? taken * STAMINA_PER_DAMAGE : 0.0F)
                    + (ignites ? STAMINA_PER_EFFECT : 0.0F);
        }
        if (!pay(user, state, attacker, cost)) {
            return;
        }
        event.setCanceled(true);
        if (!source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            // as the hit would have left him, so the same harm does not come back every tick
            user.invulnerableTime = 20;
            user.lastHurt = amount;
        }
        if (ignites) {
            user.setRemainingFireTicks(
                    source.is(DamageTypes.ON_FIRE)
                            ? 0
                            : Math.min(
                                    state.fireSample(),
                                    user.getRemainingFireTicks()));
        }
        answer(user, source.getDirectEntity(), attacker, policy);
    }

    /**
     * What the hit would take from his body: the amount after his armour, his
     * Resistance and his armour's enchantments, by vanilla's own rules.
     */
    public static float damageTaken(
            LivingEntity user, DamageSource source, float amount) {
        float damage = amount;
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            damage = CombatRules.getDamageAfterAbsorb(
                    user,
                    damage,
                    source,
                    user.getArmorValue(),
                    (float) user.getAttributeValue(
                            Attributes.ARMOR_TOUGHNESS));
        }
        if (source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            return damage;
        }
        MobEffectInstance resistance =
                user.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null
                && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            damage = Math.max(
                    damage * (25 - (resistance.getAmplifier() + 1) * 5)
                            / 25.0F,
                    0.0F);
        }
        if (damage > 0.0F
                && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                && user.level() instanceof ServerLevel level) {
            float protection = EnchantmentHelper.getDamageProtection(
                    level, user, source);
            if (protection > 0.0F) {
                damage = CombatRules.getDamageAfterMagicAbsorb(
                        damage, protection);
            }
        }
        return damage;
    }

    // Burning itself, or fire that just took hold of him: the causes of fire set him alight before they hurt.
    private static boolean ignites(
            LivingEntity user, GerLivingState state, DamageSource source) {
        return source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypeTags.IS_FIRE)
                        && user.getRemainingFireTicks()
                                > Math.max(state.fireSample(), 0);
    }

    /** One cause is paid once a tick, however many hits and effects it brings. */
    private static boolean pay(
            LivingEntity user,
            GerLivingState state,
            @Nullable Entity cause,
            float cost) {
        long now = user.level().getGameTime();
        int causeId = cause != null ? cause.getId() : -1;
        if (cost <= 0.0F || state.answeredNow(now, causeId)) {
            state.markAnswered(now, causeId);
            return true;
        }
        StandPower power = StandPower.get(user);
        if (power == null) {
            return false;
        }
        // Not lifted by Resolve, as the Disabler's and Return to Zero's own costs are not.
        if (!power.isStaminaInfinite()
                && (power.getStamina() < cost
                        || !power.consumeStamina(cost))) {
            return false;
        }
        state.markAnswered(now, causeId);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEffectApplicable(
            MobEffectEvent.Applicable event) {
        LivingEntity user = event.getEntity();
        MobEffectInstance effect = event.getEffectInstance();
        if (user.level().isClientSide()
                || effect == null
                || OWN_EFFECTS.get() > 0
                || effect.getEffect().value().getCategory()
                        != MobEffectCategory.HARMFUL
                || effect.getEffect().value().isInstantenous()) {
            return;
        }
        Policy policy = policyOf(user);
        if (policy == null) {
            return;
        }
        Entity cause = event.getEffectSource();
        // What he takes himself, or is given by his own, is no hit.
        if (cause == null && user.isUsingItem()
                || GerSides.isOwn(user, cause)
                || cause instanceof TraceableEntity owned
                        && GerSides.isOwn(user, owned.getOwner())) {
            return;
        }
        GerLivingState state =
                GoldExperienceRequiemAddon.livingState(user);
        long now = user.level().getGameTime();
        boolean repeated =
                state.effectAnswered(effect.getEffect(), now);
        if (!repeated
                && !pay(user,
                        state,
                        cause,
                        policy == Policy.PAID
                                ? STAMINA_PER_EFFECT
                                : 0.0F)) {
            return;
        }
        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        if (!repeated) {
            state.markEffectAnswered(
                    effect.getEffect(), now + EFFECT_ANSWER_TICKS);
            Entity attacker = cause instanceof TraceableEntity owned
                            && owned.getOwner() != null
                    ? owned.getOwner()
                    : cause;
            answer(user, cause != attacker ? cause : null, attacker, policy);
        }
    }

    // An explosion pushes whoever it reaches whether or not its damage landed.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExplosionKnockback(
            ExplosionKnockbackEvent event) {
        if (event.getAffectedEntity() instanceof LivingEntity user
                && !user.level().isClientSide()
                && GoldExperienceRequiemAddon.livingState(user)
                        .answeredAt(user.level().getGameTime())) {
            event.setKnockbackVelocity(Vec3.ZERO);
        }
    }

    // A creature that takes him as its target is followed from then on.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null || target.level().isClientSide()) {
            return;
        }
        LivingEntity user = target instanceof StandEntity stand
                        && stand.getUser() != null
                ? stand.getUser()
                : target;
        if (policyOf(user) != null) {
            GerAttackOrigins.watch(user, event.getEntity());
        }
    }

    private static void answer(
            LivingEntity user,
            @Nullable Entity direct,
            @Nullable Entity attacker,
            Policy policy) {
        Entity putBack = putAttackerBack(user, direct, attacker);
        flash(user, policy == Policy.PAID);
        if (putBack != null) {
            flash(putBack, policy == Policy.PAID);
        }
        if (user.level() instanceof ServerLevel level) {
            GerAnswerPayloads.FlashPayload payload =
                    new GerAnswerPayloads.FlashPayload(
                            user.getId(),
                            putBack != null ? putBack.getId() : -1,
                            policy == Policy.PAID);
            for (ServerPlayer player : level.players()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /**
     * The attacker's side of the answer: what it shot is gone, its running
     * action is cut, and it stands again where its attack began.
     *
     * @return whom the answer reached, to show it on, or null
     */
    @Nullable
    private static Entity putAttackerBack(
            LivingEntity user,
            @Nullable Entity direct,
            @Nullable Entity attacker) {
        int attackTicks = 0;
        if (direct != null
                && direct != attacker
                && !(direct instanceof LivingEntity)) {
            attackTicks = direct.tickCount;
            direct.discard();
        }
        Entity body = attacker != null ? bodyOf(attacker) : null;
        if (body == null || GerSides.isOwn(user, body)) {
            return null;
        }
        if (body instanceof LivingEntity living) {
            attackTicks = Math.max(attackTicks, cutAttack(living));
        }
        Vec3 origin =
                GerAttackOrigins.positionAgo(user, body, attackTicks);
        if (origin != null
                && origin.distanceToSqr(body.position()) > 1.0E-4D) {
            if (body instanceof ServerPlayer player) {
                player.connection.teleport(
                        origin.x,
                        origin.y,
                        origin.z,
                        player.getYRot(),
                        player.getXRot());
            }
            else {
                body.teleportTo(origin.x, origin.y, origin.z);
            }
            body.setDeltaMovement(Vec3.ZERO);
            body.hasImpulse = true;
            TimeStopState.repinOrReleaseOnServer(body);
        }
        return body;
    }

    /** Requiem's short gold Return to Zero effect on one entity, for everyone. */
    public static void showOn(Entity entity) {
        flash(entity, true);
        if (entity.level() instanceof ServerLevel level) {
            GerAnswerPayloads.FlashPayload payload =
                    new GerAnswerPayloads.FlashPayload(
                            entity.getId(), -1, true);
            for (ServerPlayer player : level.players()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static Entity bodyOf(Entity attacker) {
        return attacker instanceof StandEntity stand
                        && stand.getUser() != null
                ? stand.getUser()
                : attacker;
    }

    /** @return how many ticks the action it cut had run, the longest of its own and its Stand's */
    private static int cutAttack(LivingEntity body) {
        int ticks = runningTicks(body);
        StandPower power = StandPower.get(body);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        if (stand != null) {
            ticks = Math.max(ticks, runningTicks(stand));
            GoldExperienceRequiemGameEvents.interruptAction(stand);
        }
        GoldExperienceRequiemGameEvents.interruptAction(body);
        if (body instanceof Mob mob && mob.isUsingItem()) {
            mob.stopUsingItem();
        }
        return ticks;
    }

    private static int runningTicks(LivingEntity performer) {
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(performer);
        return action != null
                ? (int) Math.ceil(action.getFullTicksPassed())
                : 0;
    }

    // Vanilla dust, which every client draws whatever it has installed.
    private static void flash(Entity entity, boolean full) {
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(
                    new DustParticleOptions(GOLD, full ? 1.2F : 0.8F),
                    entity.getX(),
                    entity.getY(0.5D),
                    entity.getZ(),
                    full ? 16 : 5,
                    entity.getBbWidth() * 0.5D,
                    entity.getBbHeight() * 0.3D,
                    entity.getBbWidth() * 0.5D,
                    0.0D);
        }
    }
}
