package rotp.addons.darkbluemoon.effect;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.DarkBlueMoonConfig;
import rotp.addons.darkbluemoon.entity.BarnacleEntity;
import rotp.core.api.stand.StandAbilityFatigue;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 BarnacleBindEffect: every 20 ticks 2 magic damage, Slowness V and Mining Fatigue V for 40 ticks, a pull down,
 * Dark Blue Moon's attacks and barnacle skill held 20 ticks on cooldown, and more barnacles grown on the body (3-5 while
 * fewer than 10, else 1-3, up to 50). Barnacles near the bound one that are not on it fall off.
 */
public class BarnacleBindEffect extends MobEffect {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    public static final int MIN_BARNACLES = 10;

    public BarnacleBindEffect() {
        super(MobEffectCategory.HARMFUL, 2771562);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        Level level = entity.level();
        if (level.isClientSide() || entity instanceof StandEntity) {
            return true;
        }
        for (BarnacleEntity barnacle : level.getEntitiesOfClass(BarnacleEntity.class, entity.getBoundingBox().inflate(8.0))) {
            if (barnacle.getTarget() != entity) {
                barnacle.discard();
            }
        }
        entity.hurt(entity.damageSources().magic(), 2.0F);
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 4, false, false, false));
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, motion.y - 0.08, motion.z);
        entity.hurtMarked = true;
        if (entity instanceof Player player) {
            StandPower power = StandPower.get(player);
            if (power != null && power.hasPower() && power.getPowerType() == DarkBlueMoonAddon.DARK_BLUE_MOON.get()) {
                // an effect tick runs outside any press or action: these are locks, no use of the abilities
                // in the no-cooldown mode
                StandAbilityFatigue.withoutFatigue(() -> {
                    for (String ability : DarkBlueMoonAddon.BARNACLE_BIND_COOLDOWN_ABILITIES) {
                        power.setAbilityCooldown(ability, 20);
                    }
                });
            }
        }
        int maxBarnacles = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.BARNACLE_MAX_COUNT);
        int current = countBarnacles(level, entity);
        if (current < maxBarnacles) {
            int toSpawn = current < MIN_BARNACLES ? 3 + RANDOM.nextInt(3) : 1 + RANDOM.nextInt(3);
            spawnBarnacles(level, entity, Math.min(toSpawn, maxBarnacles - current));
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    public static void spawnBarnacles(Level level, LivingEntity target, int count) {
        if (level.isClientSide()) {
            return;
        }
        for (int i = 0; i < count; i++) {
            level.addFreshEntity(new BarnacleEntity(level, target, randomOffsetOnBody(target)));
        }
    }

    public static int countBarnacles(Level level, LivingEntity target) {
        int count = 0;
        for (BarnacleEntity barnacle : level.getEntitiesOfClass(BarnacleEntity.class, target.getBoundingBox().inflate(4.0))) {
            if (barnacle.getTarget() == target) {
                count++;
            }
        }
        return count;
    }

    private static Vec3 randomOffsetOnBody(LivingEntity target) {
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double radius = target.getBbWidth() * 0.5;
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        double y = RANDOM.nextDouble() * target.getBbHeight() * 0.8 + target.getBbHeight() * 0.1;
        return new Vec3(x, y, z);
    }
}
