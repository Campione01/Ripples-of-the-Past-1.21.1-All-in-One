package rotp.addons.cmoon;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.cmoon.ability.HeartInversionAbility;
import rotp.addons.cmoon.ability.InversionUtil;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.addons.cmoon.field.GravityField;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = CMoonAddon.MOD_ID)
public final class CMoonEvents {
    private static final String DESTROY_STAMINA_REGEN_TICKS =
            CMoonAddon.MOD_ID + ":DestroyStaminaRegenTicks";
    private static final int DESTROY_REGEN_DURATION = 100;
    private static final int DESTROY_REGEN_AMPLIFIER = 4;

    private CMoonEvents() {}

    public static void grantDestroyStaminaRegen(
            LivingEntity user) {
        if (!user.level().isClientSide()) {
            user.getPersistentData().putInt(
                    DESTROY_STAMINA_REGEN_TICKS,
                    DESTROY_REGEN_DURATION);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(
            EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)
                || entity.level().isClientSide()) {
            return;
        }
        int ticks = entity.getPersistentData().getInt(
                DESTROY_STAMINA_REGEN_TICKS);
        if (ticks <= 0) {
            return;
        }

        StandPower power = StandPower.get(entity);
        if (power != null && power.hasPower()) {
            float restored =
                    (DESTROY_REGEN_AMPLIFIER + 1)
                            * power.getMaxStamina()
                            / 1000.0F;
            power.setStamina(power.getStamina() + restored);
        }

        if (ticks == 1) {
            entity.getPersistentData().remove(
                    DESTROY_STAMINA_REGEN_TICKS);
        }
        else {
            entity.getPersistentData().putInt(
                    DESTROY_STAMINA_REGEN_TICKS,
                    ticks - 1);
        }
    }

    // ahead of every listener that may cancel the hit: 1.16 rolled before the hurt
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHeartInversionLanded(
            LivingIncomingDamageEvent event) {
        HeartInversionAbility.HeartInversion.onStandAttackLanded(
                event.getEntity(), event.getSource());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()
                || event.getAmount() <= 0.0F) {
            return;
        }

        if (event.getSource().getEntity()
                instanceof CMoonStandEntity attackingStand) {
            InversionUtil.applyStandard(target, attackingStand);
        }
    }

    // 1.16 LivingHurtEvent: inside actuallyHurt, past the hurt cooldown, so the countered hit
    // still lands at 0. Ahead of the damage-stage listeners a countered hit never reached in 1.16.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onGravityCounter(
            LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()
                || event.getOriginalDamage() <= 0.0F) {
            return;
        }

        CMoonStandEntity defendingStand = CMoonStandEntity.current(target);
        if (defendingStand == null
                || !defendingStand.isCounterActive()) {
            return;
        }
        // 1.16: attackerless damage passes, counter stays armed;
        // the stun goes to the damaging entity itself (a Stand, not its user)
        if (!(event.getSource().getEntity()
                        instanceof LivingEntity attacker)
                || userOf(attacker) == target) {
            return;
        }
        defendingStand.setCounterActive(false);
        attacker.addEffect(new MobEffectInstance(
                ModStatusEffects.STUN,
                75,
                1,
                false,
                false,
                true));
        target.level().playSound(
                null,
                target.blockPosition(),
                CMoonAddon.C_MOON_GRAVITY_COUNTER_SOUND.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        // 1.16 cancelled the hit at LivingHurtEvent: no LivingDamageEvent fired for this target, so neither
        // the Resolve bookkeeping nor the stop of held actions sees it.
        ((DamageSourceModified) event.getSource())
                .jojo_ripples$setCounteredTarget(target);
        event.setNewDamage(0.0F);
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!event.getEntity().level().isClientSide()
                && event.getEntity().hasEffect(
                        CMoonAddon.CM_INVERSION)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUserTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity user
                && !user.level().isClientSide()) {
            CMoonStandEntity.tickRecalled(user);
            GravityField.tickUser(user);
        }
    }

    @SubscribeEvent
    public static void onFieldPull(EntityTickEvent.Pre event) {
        GravityField.pullBeforeTick(event.getEntity());
    }

    @SubscribeEvent
    public static void onFieldFall(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            GravityField.fallAfterTick(entity);
        }
        else {
            GravityField.landAfterTick(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity user
                && !event.getLevel().isClientSide()) {
            GravityField.endWithUser(user);
        }
    }

    // Comes before the player list saves him: see letGoOfLeavingPlayer.
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CMoonStandEntity.endShiftOf(event.getEntity());
        GravityField.letGoOfLeavingPlayer(event.getEntity());
    }

    private static LivingEntity userOf(LivingEntity entity) {
        return entity instanceof StandEntity stand
                ? stand.getUser()
                : entity;
    }
}
