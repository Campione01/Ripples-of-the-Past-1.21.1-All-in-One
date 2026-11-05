package rotp.addons.echoes.init;

import java.util.List;

import rotp.addons.echoes.RotpEchoes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * R772 F11: the states ACT2's words put on their target (the master's potion/*Effect classes, with their colours,
 * attribute changes and ticks). The words apply them without particles or icon, as the master did.
 */
public final class EchoesMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, RotpEchoes.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> ELECTRIC =
            MOB_EFFECTS.register("electric", () -> new WordEffect(0x44CCFF));
    public static final DeferredHolder<MobEffect, MobEffect> HOT =
            MOB_EFFECTS.register("hot", () -> new WordEffect(0xFF6633));
    public static final DeferredHolder<MobEffect, MobEffect> COLD =
            MOB_EFFECTS.register("cold", () -> new WordEffect(0x66CCFF)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, RotpEchoes.id("effect.cold"),
                            -0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect, MobEffect> FROZEN =
            MOB_EFFECTS.register("frozen", () -> new WordEffect(0x2266DD)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, RotpEchoes.id("effect.frozen_speed"),
                            -0.35D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, RotpEchoes.id("effect.frozen_attack"),
                            -0.35D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect, MobEffect> SLIPPERY =
            MOB_EFFECTS.register("slippery", () -> new WordEffect(0x66DDFF)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, RotpEchoes.id("effect.slippery"),
                            0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect, MobEffect> SINK =
            MOB_EFFECTS.register("sink", () -> new SinkEffect()
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, RotpEchoes.id("effect.sink"),
                            -0.9D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect, MobEffect> BRIGHT =
            MOB_EFFECTS.register("bright", BrightEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> ITCHY =
            MOB_EFFECTS.register("itchy", ItchyEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> PANIC =
            MOB_EFFECTS.register("panic", PanicEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> PRESSURE =
            MOB_EFFECTS.register("pressure", PressureEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> WET =
            MOB_EFFECTS.register("wet", WetEffect::new);

    // R772 F11, master SoundHauntEffect: ACT1's sound on an entity, drawn on it for Stand users
    public static final DeferredHolder<MobEffect, MobEffect> SOUND_HAUNT =
            MOB_EFFECTS.register("sound_haunt", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xFF66CC) {});

    private EchoesMobEffects() {}

    private static class WordEffect extends MobEffect {
        WordEffect(int color) {
            super(MobEffectCategory.HARMFUL, color);
        }
    }

    // master BrightEffect: blinded and glowing, renewed every second
    private static final class BrightEffect extends WordEffect {
        BrightEffect() {
            super(0xFFFF88);
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
            return true;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 20 == 0;
        }
    }

    // master ItchyEffect: a small sideways shove every 5 ticks
    private static final class ItchyEffect extends WordEffect {
        ItchyEffect() {
            super(0xDDAACC);
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (!entity.level().isClientSide()) {
                Vec3 motion = entity.getDeltaMovement();
                entity.setDeltaMovement(motion.x + (entity.getRandom().nextDouble() - 0.5D) * 0.15D, motion.y,
                        motion.z + (entity.getRandom().nextDouble() - 0.5D) * 0.15D);
                entity.hurtMarked = true;
            }
            return true;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 5 == 0;
        }
    }

    // master PanicEffect: a mob runs about at random
    private static final class PanicEffect extends WordEffect {
        PanicEffect() {
            super(0x9955CC);
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity instanceof Mob mob && entity.level().getGameTime() % 10L == 0L) {
                mob.getNavigation().moveTo(entity.getX() + (entity.getRandom().nextDouble() - 0.5D) * 1.5D,
                        entity.getY(), entity.getZ() + (entity.getRandom().nextDouble() - 0.5D) * 1.5D, 1.2D);
            }
            return true;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }
    }

    // master PressureEffect: every second the mobs within 8 blocks back off 3 blocks
    private static final class PressureEffect extends WordEffect {
        PressureEffect() {
            super(0x9933CC);
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity.level().isClientSide() || entity.level().getGameTime() % 20L != 0L) {
                return true;
            }
            List<Mob> nearby = entity.level().getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(8.0D),
                    mob -> mob != entity && mob.isAlive());
            for (Mob mob : nearby) {
                Vec3 away = mob.position().subtract(entity.position());
                if (away.lengthSqr() < 0.01D) {
                    continue;
                }
                away = away.normalize().scale(3.0D);
                mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.5D);
            }
            return true;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }
    }

    // master SinkEffect: the target can hardly move and does not rise
    private static final class SinkEffect extends WordEffect {
        SinkEffect() {
            super(0x554433);
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(motion.x * 0.1D, Math.min(motion.y, 0.0D), motion.z * 0.1D);
            if (entity instanceof Player player) {
                player.xxa = 0.0F;
                player.zza = 0.0F;
            }
            return true;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }
    }

    // master WetEffect: water drips from the target
    private static final class WetEffect extends WordEffect {
        WetEffect() {
            super(0x4488CC);
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity.level().isClientSide() && entity.getRandom().nextFloat() < 0.3F) {
                entity.level().addParticle(ParticleTypes.DRIPPING_WATER,
                        entity.getX() + (entity.getRandom().nextDouble() - 0.5D) * entity.getBbWidth(),
                        entity.getY() + entity.getRandom().nextDouble() * entity.getBbHeight(),
                        entity.getZ() + (entity.getRandom().nextDouble() - 0.5D) * entity.getBbWidth(),
                        0.0D, -0.1D, 0.0D);
            }
            return true;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }
    }
}
