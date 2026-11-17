package rotp.addons.spin.ability;

import java.util.Locale;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.power.SpinBrace;
import rotp.addons.spin.power.SpinData;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entity_puppetcontrol.StandManualInput;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

public final class SpinLessonAbilities {
    private SpinLessonAbilities() {}

    public static final class Hijack extends SpinActionAbility {
        public Hijack(AbilityType<?> type, AbilityId id) {
            super(type, id, 2, HijackAction::new);
            setDefaultPhaseLength(ActionPhase.WINDUP, 0);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
            setResetsAttackStrengthOnPerform();
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            ConditionCheck check = super.checkSpecificConditions(context);
            if (!check.isPositive()) return check;
            LivingEntity user = context.getUser();
            if (!hasEnergy(user, SpinConfig.HIJACK_ENERGY_COST.get())) return ConditionCheck.createNegative("no_energy_spin");
            return target(user, LivingComponentAction.getAimTarget(user)) != null
                    ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
        }

        private static LivingEntity target(LivingEntity user, ActionTarget aim) {
            if (aim == null || aim.getType() != ActionTarget.TargetType.ENTITY
                    || !HitResultUtil.isTargetWithinRange(aim, user, user.level(), 0, SpinConfig.HIJACK_REACH.get())) return null;
            Entity aimed = aim.getMainEntity();
            LivingEntity body = aimed instanceof StandEntity stand ? stand.getUser()
                    : aimed instanceof LivingEntity living ? living : null;
            if (body == null || !body.isAlive() || body.is(user) || body.isSpectator() || body.level() != user.level()) return null;
            if (!user.level().isClientSide() && !user.hasLineOfSight(aimed)) return null;
            if (body instanceof Player victim && user instanceof Player player && !player.canHarmPlayer(victim)) return null;
            return body;
        }

        public static final class HijackAction extends EntityActionInstance {
            public HijackAction(EntityActionType type) { super(type); }

            @Override
            public void onActionSet(EntityActionInstance previous) {
                if (getPowerUser() instanceof Player player) captureActionTargetFromAim(player);
            }

            @Override
            public void actionPerformStart() {
                if (!(level() instanceof ServerLevel server) || !(ability instanceof Hijack hijack)) return;
                LivingEntity user = getPowerUser();
                if (user == null) return;
                LivingEntity body = target(user, getActionTargetSnapshot(server));
                if (body == null || !spend(user, SpinConfig.HIJACK_ENERGY_COST.get())) return;
                hijack.beginCooldown(user, SpinConfig.HIJACK_COOLDOWN_TICKS.get());
                if (body instanceof ServerPlayer victim && SpinConfig.HIJACK_DISARM_PLAYERS.get()
                        && !victim.getMainHandItem().isEmpty()) {
                    victim.drop(true);
                } else if (body instanceof Mob mob && SpinConfig.HIJACK_FORCED_ACTION.get()) {
                    forceAttack(user, mob);
                }
                body.addEffect(new MobEffectInstance(ModStatusEffects.STUN, SpinConfig.HIJACK_STUN_TICKS.get(), 0,
                        false, false, true));
                user.swing(InteractionHand.MAIN_HAND, true);
                server.sendParticles(ParticleTypes.ENCHANTED_HIT, body.getX(), body.getY(0.5D), body.getZ(),
                        12, body.getBbWidth() * 0.5D, body.getBbHeight() * 0.3D, body.getBbWidth() * 0.5D, 0.1D);
                server.playSound(null, body.getX(), body.getY(), body.getZ(),
                        SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 1.4F);
                var data = data(user);
                if (data != null) data.practiceHijack(user);
            }
        }

        private static void forceAttack(LivingEntity user, Mob mob) {
            double range = SpinConfig.HIJACK_FORCED_RANGE.get(), closest = range * range;
            LivingEntity victim = null;
            for (LivingEntity target : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(range),
                    other -> !other.is(mob) && !other.is(user) && other.isAlive()
                            && !(other instanceof StandEntity) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(other))) {
                double distance = mob.distanceToSqr(target);
                if (distance <= closest) {
                    closest = distance;
                    victim = target;
                }
            }
            if (victim == null) return;
            mob.getLookControl().setLookAt(victim, 360.0F, 360.0F);
            if (mob instanceof RangedAttackMob ranged) ranged.performRangedAttack(victim, 1.0F);
            else if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                mob.swing(InteractionHand.MAIN_HAND);
                mob.doHurtTarget(victim);
            }
        }
    }

    public abstract static class HoldAbility extends SpinActionAbility {
        protected HoldAbility(AbilityType<?> type, AbilityId id, int lesson, int max,
                Function<EntityActionType, ? extends EntityActionInstance> factory) {
            super(type, id, lesson, factory);
            setButtonHoldPhase(ActionPhase.PERFORM);
            setDefaultPhaseLength(ActionPhase.WINDUP, 0);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
            if (max > 0) setDefaultPhaseLength(ActionPhase.PERFORM, max);
        }

        protected static ConditionCheck energyCheck(LivingEntity user, double cost) {
            return hasEnergy(user, cost) ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("no_energy_spin");
        }
    }

    public abstract static class HoldAction extends EntityActionInstance {
        protected int held;

        protected HoldAction(EntityActionType type) { super(type); }

        protected void stop() {
            forceStop();
            syncPhaseChanges();
        }

        @Override
        public void onButtonStopHold() {
            stop();
        }
    }

    public static final class Medicine extends HoldAbility {
        public Medicine(AbilityType<?> type, AbilityId id) {
            super(type, id, 2, 0, MedicineAction::new);
            setCancelHeldOnGettingAttacked();
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            ConditionCheck check = super.checkSpecificConditions(context);
            return check.isPositive() ? energyCheck(context.getUser(), SpinConfig.HEALING_ENERGY_PER_TICK.get()) : check;
        }

        public static final class MedicineAction extends HoldAction {
            public MedicineAction(EntityActionType type) { super(type); }

            @Override
            public void actionTick() {
                if (!(level() instanceof ServerLevel server) || getPhase() != ActionPhase.PERFORM) return;
                LivingEntity user = getPowerUser();
                if (user == null || !spend(user, SpinConfig.HEALING_ENERGY_PER_TICK.get())) {
                    stop();
                    return;
                }
                int interval = SpinConfig.HEALING_INTERVAL_TICKS.get();
                if (++held % interval != 0) return;
                LivingEntity patient = patient(user);
                BlockPos water = findWater(server, patient, SpinConfig.HEALING_XRAY_WATER_RADIUS.get());
                float amount = SpinConfig.HEALING_AMOUNT.get().floatValue();
                if (water != null) {
                    amount *= SpinConfig.HEALING_XRAY_AMOUNT_MULT.get().floatValue();
                    server.sendParticles(ParticleTypes.FISHING, water.getX() + 0.5D, water.getY() + 0.9D,
                            water.getZ() + 0.5D, 10, 0.3D, 0, 0.3D, 0.02D);
                    if (held == interval && user instanceof ServerPlayer player) diagnose(player, patient);
                }
                if (amount > 0.0F) patient.heal(amount);
                if (SpinConfig.HEALING_CURES_HARMFUL.get()) {
                    patient.removeEffect(MobEffects.POISON);
                    patient.removeEffect(MobEffects.WITHER);
                    patient.removeEffect(ModStatusEffects.BLEEDING);
                }
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, patient.getX(), patient.getY(0.5D), patient.getZ(), 6,
                        patient.getBbWidth() * 0.4D, patient.getBbHeight() * 0.3D, patient.getBbWidth() * 0.4D, 0);
            }
        }

        private static LivingEntity patient(LivingEntity user) {
            if (StandManualInput.isShiftHeld(user)) {
                var target = LivingComponentAction.getAimTarget(user);
                double reach = SpinConfig.HEALING_TARGET_REACH.get();
                if (target != null && target.getType() == ActionTarget.TargetType.ENTITY
                        && target.getMainEntity() instanceof LivingEntity living && !(living instanceof StandEntity)
                        && living.isAlive() && living.distanceToSqr(user) <= reach * reach
                        && (user.level().isClientSide() || user.hasLineOfSight(living))) return living;
            }
            return user;
        }

        private static BlockPos findWater(ServerLevel level, LivingEntity patient, int radius) {
            if (radius <= 0) return null;
            BlockPos center = patient.blockPosition(), best = null;
            double nearest = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {
                if (!level.hasChunkAt(pos) || !(level.getFluidState(pos).is(FluidTags.WATER)
                        || level.getBlockState(pos).is(Blocks.WATER_CAULDRON))) continue;
                double d = pos.distSqr(center);
                if (d < nearest) {
                    nearest = d;
                    best = pos.immutable();
                }
            }
            return best;
        }

        private static void diagnose(ServerPlayer viewer, LivingEntity patient) {
            MutableComponent ailments = Component.empty();
            boolean found = false;
            for (var effect : patient.getActiveEffects()) {
                if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) continue;
                if (found) ailments.append(", ");
                ailments.append(effect.getEffect().value().getDisplayName());
                found = true;
            }
            String hp = String.format(Locale.ROOT, "%.1f/%.1f", patient.getHealth(), patient.getMaxHealth());
            viewer.displayClientMessage((found ? Component.translatable("rotp_spin.message.xray_diagnosis",
                    patient.getDisplayName(), ailments, hp) : Component.translatable("rotp_spin.message.xray_healthy",
                    patient.getDisplayName(), hp)).withStyle(ChatFormatting.AQUA), false);
        }
    }

    public static final class GoldenFrame extends HoldAbility {
        public GoldenFrame(AbilityType<?> type, AbilityId id) {
            super(type, id, 4, 1200, FrameAction::new);
            setCancelHeldOnGettingAttacked();
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            ConditionCheck check = super.checkSpecificConditions(context);
            if (!check.isPositive()) return check;
            ConditionCheck hands = hands(context.getUser());
            return hands.isPositive() ? energyCheck(context.getUser(), SpinConfig.HAND_FRAME_ENERGY_PER_TICK.get()) : hands;
        }

        @Override
        protected ConditionCheck checkHeldSpecificConditions(EntityActionInstance action, Power<?> context) {
            return hands(context.getUser());
        }

        private static ConditionCheck hands(LivingEntity user) {
            return user.getMainHandItem().isEmpty() && user.getOffhandItem().isEmpty() ? ConditionCheck.POSITIVE
                    : ConditionCheck.createNegative("rotp_spin.hands_busy");
        }

        public static final class FrameAction extends HoldAction {
            private boolean completed;

            public FrameAction(EntityActionType type) { super(type); }

            @Override
            public void actionTick() {
                if (!(level() instanceof ServerLevel server) || getPhase() != ActionPhase.PERFORM || completed) return;
                LivingEntity user = getPowerUser();
                if (user == null || !hands(user).isPositive() || !spend(user, SpinConfig.HAND_FRAME_ENERGY_PER_TICK.get())) {
                    stop();
                    return;
                }
                if (++held != SpinConfig.HAND_FRAME_HOLD_TICKS.get()) return;
                completed = true;
                var data = data(user);
                int duration = SpinConfig.HAND_FRAME_DURATION_TICKS.get();
                if (data != null) data.frameGoldenRectangle(server.getGameTime(), duration);
                server.sendParticles(ParticleTypes.END_ROD, user.getX(), user.getEyeY() - 0.2D, user.getZ(),
                        8, 0.25D, 0.15D, 0.25D, 0.01D);
                if (user instanceof Player player) player.displayClientMessage(
                        Component.translatable("rotp_spin.message.golden_frame", duration / 20).withStyle(ChatFormatting.GOLD), true);
            }

            @Override
            public void onActionCleared(EntityActionInstance next) {
                if (completed && getPowerUser() instanceof ServerPlayer player && ability instanceof GoldenFrame frame) {
                    frame.beginCooldown(player, SpinConfig.HAND_FRAME_COOLDOWN_TICKS.get());
                }
            }
        }
    }

    public static final class Brace extends HoldAbility {
        private static final int FADE_TICKS = 8;

        public Brace(AbilityType<?> type, AbilityId id) {
            super(type, id, 1, 1200, BraceAction::new);
            setDefaultPhaseLength(ActionPhase.RECOVERY, FADE_TICKS);
        }

        @Override
        public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
            return action.getPowerUser() == null || action.getPowerUser().isPassenger()
                    ? null : super.getEntityAnim(action);
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            ConditionCheck check = super.checkSpecificConditions(context);
            return check.isPositive() ? energyCheck(context.getUser(), SpinConfig.BRACE_ENERGY_PER_TICK.get()) : check;
        }

        public static final class BraceAction extends HoldAction {
            private boolean cooled;

            public BraceAction(EntityActionType type) { super(type); }

            @Override
            public void actionTick() {
                if (getPhase() != ActionPhase.PERFORM) return;
                LivingEntity user = getPowerUser();
                if (!(level() instanceof ServerLevel server)) {
                    if (user != null) clientSpiral(user, ++held);
                    return;
                }
                if (user == null || !spend(user, SpinConfig.BRACE_ENERGY_PER_TICK.get())) {
                    stop();
                    return;
                }
                int windup = SpinConfig.BRACE_WINDUP_TICKS.get();
                if (!SpinBrace.isBraced(++held, windup)) return;
                int slowness = SpinConfig.BRACE_SLOWNESS_AMPLIFIER.get();
                if (slowness >= 0) user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, slowness, false, false, false));
                if (held == windup) server.sendParticles(ParticleTypes.CRIT, user.getX(), user.getY(0.5D), user.getZ(),
                        10, 0.3D, 0.5D, 0.3D, 0.05D);
            }

            private void clientSpiral(LivingEntity user, int ticks) {
                var particle = SpinBrace.isBraced(ticks, SpinConfig.BRACE_WINDUP_TICKS.get()) ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT;
                float angle = ticks * 0.9F;
                double radius = 0.55D;
                double height = user.getBbHeight() * (0.15D + 0.7D * (ticks % 12 / 12.0D));
                for (int i = 0; i < 2; i++) {
                    float a = angle + i * Mth.PI;
                    level().addParticle(particle, user.getX() + Mth.cos(a) * radius, user.getY() + height,
                            user.getZ() + Mth.sin(a) * radius, 0, 0, 0);
                }
            }

            @Override
            protected void stop() {
                if (getPhase() != ActionPhase.RECOVERY) {
                    setPhaseStart(ActionPhase.RECOVERY);
                    syncPhaseChanges();
                }
            }

            @Override
            public void onSetPhase(ActionPhase newPhase) {
                if (newPhase == ActionPhase.RECOVERY) beginBraceCooldown();
            }

            // The fade is only the stance coming down: whatever is pressed next goes ahead.
            @Override
            public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
                return getPhase() == ActionPhase.RECOVERY || super.canBeCancelledInto(cancellingAbility);
            }

            @Override
            public void onActionCleared(EntityActionInstance next) {
                beginBraceCooldown();
            }

            private void beginBraceCooldown() {
                if (!cooled && SpinBrace.isBraced(held, SpinConfig.BRACE_WINDUP_TICKS.get())
                        && getPowerUser() instanceof ServerPlayer player && ability instanceof Brace brace) {
                    cooled = true;
                    brace.beginCooldown(player, SpinConfig.BRACE_COOLDOWN_TICKS.get());
                }
            }
        }
    }

    public static boolean isBraced(LivingEntity entity) {
        return LivingComponentAction.getCurEntityAction(entity, PowerClass.PLAYER_POWER) instanceof Brace.BraceAction action
                && !action.isOver() && action.getPhase() == ActionPhase.PERFORM
                && SpinBrace.isBraced(action.held, SpinConfig.BRACE_WINDUP_TICKS.get());
    }

    public static float absorbBeforeArmor(Player player, DamageSource source, float amount) {
        if (!(player instanceof ServerPlayer user) || !isBraced(user)) return amount;
        Entity direct = source.getDirectEntity();
        boolean melee = direct instanceof LivingEntity && direct == source.getEntity();
        boolean magic = source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
        if (!SpinBrace.isBraceable(source.is(DamageTypeTags.IS_PROJECTILE), source.is(DamageTypeTags.IS_EXPLOSION),
                melee, source.is(DamageTypeTags.BYPASSES_ARMOR), source.is(DamageTypeTags.IS_FIRE), magic)) return amount;
        float energy = SpinData.energy(user);
        float perDamage = user.getAbilities().instabuild ? 0.0F : SpinConfig.BRACE_ENERGY_PER_DAMAGE.get().floatValue();
        float absorbed = SpinBrace.absorbed(amount, SpinConfig.BRACE_DAMAGE_REDUCTION.get().floatValue(), energy, perDamage);
        if (absorbed <= 0.0F || !SpinData.consumeEnergy(user, SpinBrace.energyCost(absorbed, perDamage, energy))) return amount;
        double knockback = SpinConfig.BRACE_ATTACKER_KNOCKBACK.get();
        if (melee && knockback > 0 && direct instanceof LivingEntity attacker) {
            attacker.knockback(knockback, user.getX() - attacker.getX(), user.getZ() - attacker.getZ());
            attacker.hurtMarked = true;
        }
        var level = user.serverLevel();
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.SHIELD_BLOCK,
                user.getSoundSource(), 1.0F, 0.8F + level.random.nextFloat() * 0.3F);
        level.sendParticles(ParticleTypes.CRIT, user.getX(), user.getY(0.5D), user.getZ(),
                14, 0.35D, 0.5D, 0.35D, 0.25D);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, user.getX(), user.getY(0.6D), user.getZ(),
                8, 0.3D, 0.4D, 0.3D, 0.1D);
        user.displayClientMessage(Component.translatable("rotp_spin.message.brace_absorbed",
                String.format(Locale.ROOT, "%.1f", absorbed), String.format(Locale.ROOT, "%.1f", amount - absorbed))
                .withStyle(ChatFormatting.AQUA), true);
        SpinData.sync(user);
        return amount - absorbed;
    }
}
