package rotp.addons.spin.ability;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.item.SteelBallItem;
import rotp.addons.spin.power.SpinCharge;
import rotp.addons.spin.power.SpinData;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

public final class SpinBallChargeAbility extends SpinActionAbility {
    private static final ActionAnimIdentifier LEFT_CHARGE = new ActionAnimIdentifier("spin_ball_charge_left", false);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(0.92F, 0.78F, 0.19F), 1);
    private static final DustParticleOptions GOLD_SMALL = new DustParticleOptions(new Vector3f(0.92F, 0.78F, 0.19F), 0.6F);
    private static final int FADE_TICKS = 5;

    public SpinBallChargeAbility(AbilityType<?> type, AbilityId id) {
        super(type, id, 1, ChargeAction::new);
        setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
        setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 1200);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 0);
        setDefaultPhaseLength(ActionPhase.RECOVERY, FADE_TICKS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        return check.isPositive() ? chargeCondition(context.getUser() instanceof Player player ? player : null) : check;
    }

    @Override
    protected ConditionCheck checkHeldSpecificConditions(EntityActionInstance action, Power<?> context) {
        return chargeCondition(context.getUser() instanceof Player player ? player : null);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        if (!(action.getPowerUser() instanceof Player player) || player.isPassenger()) return null;
        // The fade plays after the ball has left the hand, so it goes by the arm that held it.
        HumanoidArm arm = action.getPhase() == ActionPhase.RECOVERY && action instanceof ChargeAction charge
                ? charge.ballArm : armOfBall(player);
        if (arm == null) return null;
        return arm == HumanoidArm.LEFT ? LEFT_CHARGE : super.getEntityAnim(action);
    }

    private static HumanoidArm armOfBall(Player player) {
        InteractionHand hand = SteelBallItem.handWithBall(player, true);
        if (hand == null) return null;
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    private static ConditionCheck chargeCondition(Player player) {
        if (!SpinConfig.CHARGE_ENABLED.get() || player == null || player.isSpectator() || !player.isAlive()) {
            return ConditionCheck.NEGATIVE;
        }
        InteractionHand hand = SteelBallItem.handWithBall(player, true);
        if (hand == null) {
            return ConditionCheck.createNegative(SteelBallItem.handWithBall(player, false) != null
                    ? "rotp_spin.only_plain_steel_ball" : "rotp_spin.no_steel_ball_in_hand");
        }
        if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand).getItem())) {
            return ConditionCheck.NEGATIVE;
        }
        float reserve = player.level().isClientSide() ? SpinData.of(player).map(SpinData::displayCost)
                .orElse(SpinConfig.BALL_SPIN_COST.get().floatValue()) : SpinConfig.BALL_SPIN_COST.get().floatValue();
        return hasEnergy(player, reserve) ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("no_energy_spin");
    }

    public static final class ChargeAction extends EntityActionInstance {
        private int paidTicks;
        private int heldTicks;
        private boolean released;
        private HumanoidArm ballArm;

        public ChargeAction(EntityActionType type) {
            super(type);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.BUTTON_CHARGE || !(getPowerUser() instanceof Player player)) {
                return;
            }
            InteractionHand hand = SteelBallItem.handWithBall(player, true);
            if (hand == null) {
                return;
            }
            ballArm = armOfBall(player);
            boolean chipped = SteelBallItem.isChipped(player.getItemInHand(hand));
            heldTicks++;
            if (!(level() instanceof ServerLevel server)) {
                SpinData data = SpinData.of(player).orElse(null);
                // A tracked player's data syncs no charge settings, as the donor's ClientSpinState fell back to the config.
                boolean synced = data != null && data.displayChargeTicks() > 0;
                int maxTicks = synced ? data.displayChargeTicks() : SpinConfig.CHARGE_MAX_TICKS.get();
                double cap = synced ? data.displayChippedMax() : SpinConfig.CHARGE_CHIPPED_MAX.get();
                clientParticles(player, hand, chipped, maxTicks, cap);
                return;
            }
            int maxTicks = SpinConfig.CHARGE_MAX_TICKS.get();
            double cap = SpinConfig.CHARGE_CHIPPED_MAX.get();
            boolean wasFull = SpinCharge.isFull(paidTicks, maxTicks, chipped, cap);
            float perTick = SpinConfig.CHARGE_ENERGY_PER_TICK.get().floatValue();
            float reserve = SpinConfig.BALL_SPIN_COST.get().floatValue();
            if (!wasFull && (player.hasInfiniteMaterials() || SpinData.energy(player) - perTick >= reserve && spend(player, perTick))) {
                paidTicks++;
            }
            boolean full = SpinCharge.isFull(paidTicks, maxTicks, chipped, cap);
            double fraction = SpinCharge.cap(SpinCharge.fraction(paidTicks, maxTicks), chipped, cap);
            int slowness = SpinConfig.CHARGE_SLOWNESS_AMPLIFIER.get();
            if (slowness >= 0) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, slowness, false, false, false));
            }
            if (!full && heldTicks % 4 == 0) {
                server.playSound(null, player, SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.35F, 0.6F + (float) fraction);
            }
            if (full && !wasFull) {
                server.playSound(null, player, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6F, 1.6F);
                for (int i = 0; i < 16; i++) {
                    double angle = Math.PI * 2 * i / 16;
                    server.sendParticles(GOLD, player.getX() + Math.cos(angle) * 0.9, player.getY() + 0.1,
                            player.getZ() + Math.sin(angle) * 0.9, 1, 0, 0, 0, 0);
                }
                server.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY(0.6), player.getZ(),
                        20, 0.4, 0.4, 0.4, 0.2);
            }
            if (heldTicks % 5 == 0 || full && !wasFull) {
                player.displayClientMessage(Component.translatable("rotp_spin.message.ball_charge", SpinCharge.percent(fraction))
                        .withStyle(full ? ChatFormatting.GOLD : ChatFormatting.YELLOW), true);
            }
        }

        private void clientParticles(Player player, InteractionHand hand, boolean chipped, int maxTicks, double cap) {
            double fraction = SpinCharge.cap(SpinCharge.fraction(heldTicks, maxTicks), chipped, cap);
            boolean full = SpinCharge.isFull(heldTicks, maxTicks, chipped, cap);
            HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            double sign = side == HumanoidArm.RIGHT ? 1 : -1;
            double yaw = Math.toRadians(player.yBodyRot);
            double x = player.getX() - Math.cos(yaw) * sign * 0.4 - Math.sin(yaw) * 0.35;
            double y = player.getY(0.5);
            double z = player.getZ() - Math.sin(yaw) * sign * 0.4 + Math.cos(yaw) * 0.35;
            double radius = 0.6 - 0.45 * fraction;
            int arms = full ? 3 : 2;
            for (int i = 0; i < arms; i++) {
                double a = heldTicks * (0.5 + 0.8 * fraction) + i * Math.PI * 2 / arms;
                level().addParticle(full ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT,
                        x + Math.cos(a) * radius, y + Math.sin(a * 2) * 0.05, z + Math.sin(a) * radius, 0, 0, 0);
            }
            if (full || heldTicks % 3 == 0) {
                level().addParticle(full ? GOLD : GOLD_SMALL, x, y, z, 0, 0, 0);
            }
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() != ActionPhase.BUTTON_CHARGE || released) {
                return;
            }
            setPhaseStart(ActionPhase.RECOVERY);
            syncPhaseChanges();
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            if (newPhase == ActionPhase.RECOVERY) {
                fire();
            }
        }

        // The fade is only the arm coming down: whatever is pressed next goes ahead.
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return getPhase() == ActionPhase.RECOVERY || super.canBeCancelledInto(cancellingAbility);
        }

        @Override
        public void onActionCleared(EntityActionInstance next) {
            super.onActionCleared(next);
            fire();
        }

        // Any end of the hold throws, as the donor's stoppedHolding ignored willFire: a stun or the hold limit never costs the charge paid.
        private void fire() {
            if (released) {
                return;
            }
            released = true;
            if (!(level() instanceof ServerLevel server) || !(getPowerUser() instanceof Player player)
                    || !(ability instanceof SpinBallChargeAbility charge) || !player.isAlive() || player.isRemoved()) {
                return;
            }
            InteractionHand hand = SteelBallItem.handWithBall(player, true);
            if (hand == null || player.getCooldowns().isOnCooldown(player.getItemInHand(hand).getItem())) {
                return;
            }
            int maxTicks = SpinConfig.CHARGE_MAX_TICKS.get();
            double fraction = SpinCharge.cap(SpinCharge.fraction(paidTicks, maxTicks),
                    SteelBallItem.isChipped(player.getItemInHand(hand)), SpinConfig.CHARGE_CHIPPED_MAX.get());
            if (SteelBallItem.throwFromHand(server, player, hand, fraction)) {
                int cooldown = (int) Math.round(SpinConfig.CHARGE_COOLDOWN_TICKS.get() * SpinCharge.fraction(heldTicks, maxTicks));
                charge.beginCooldown(player, cooldown);
                player.swing(hand, true);
                if (fraction > 0) {
                    server.sendParticles(ParticleTypes.CRIT, player.getX(), player.getEyeY() - 0.2, player.getZ(),
                            (int) (6 + 14 * fraction), 0.2, 0.2, 0.2, 0.3);
                }
            }
        }
    }
}
