package rotp.addons.survivor.util;

import rotp.addons.survivor.SurvivorConfig;
import net.minecraft.world.entity.LivingEntity;

public final class AngerHelper {
    public static final String ANGER = "SurvivorAnger";
    public static final String ANGER_TIMER = "SurvivorAngerTimer";
    public static final String WARMUP_TIMER = "SurvivorWarmupTimer";

    private AngerHelper() {}

    public static int getAnger(LivingEntity entity) {
        return entity.getPersistentData().getInt(ANGER);
    }

    public static void setAnger(LivingEntity entity, int value) {
        int max = SurvivorConfig.MAX_ANGER.get();
        entity.getPersistentData().putInt(ANGER, Math.max(0, Math.min(max, value)));
    }

    public static void addAnger(LivingEntity entity, int value) {
        setAnger(entity, getAnger(entity) + value);
    }

    public static void resetAnger(LivingEntity entity) {
        entity.getPersistentData().putInt(ANGER, 0);
        entity.getPersistentData().remove(ANGER_TIMER);
        entity.getPersistentData().remove(WARMUP_TIMER);
    }

    public static boolean isAngry(LivingEntity entity) {
        return getAnger(entity) >= SurvivorConfig.MAX_ANGER.get();
    }

    public static void startWarmup(LivingEntity entity) {
        if (!entity.getPersistentData().contains(WARMUP_TIMER)
                && !entity.getPersistentData().contains(ANGER_TIMER)) {
            entity.getPersistentData().putInt(WARMUP_TIMER, SurvivorConfig.WARMUP_DURATION.get());
        }
    }

    public static void tickWarmup(LivingEntity entity) {
        if (entity.getPersistentData().contains(WARMUP_TIMER)) {
            int timer = entity.getPersistentData().getInt(WARMUP_TIMER);
            if (--timer <= 0) {
                entity.getPersistentData().remove(WARMUP_TIMER);
                setAnger(entity, SurvivorConfig.MAX_ANGER.get());
                entity.getPersistentData().putInt(ANGER_TIMER, SurvivorConfig.ANGER_DURATION.get());
            } else {
                entity.getPersistentData().putInt(WARMUP_TIMER, timer);
            }
        }
    }

    public static void tickAngerTimer(LivingEntity entity) {
        if (entity.getPersistentData().contains(ANGER_TIMER)) {
            int timer = entity.getPersistentData().getInt(ANGER_TIMER);
            if (--timer <= 0) {
                resetAnger(entity);
            } else {
                entity.getPersistentData().putInt(ANGER_TIMER, timer);
            }
        }
    }

    public static void tickAngerGrowth(LivingEntity entity) {
        if (WetHelper.isWet(entity)) {
            boolean inRain = WetHelper.isInRain(entity);
            int min = inRain ? SurvivorConfig.ANGER_GROWTH_RAIN_MIN.get() : SurvivorConfig.ANGER_GROWTH_MIN.get();
            int max = inRain ? SurvivorConfig.ANGER_GROWTH_RAIN_MAX.get() : SurvivorConfig.ANGER_GROWTH_MAX.get();
            int increase = min + entity.level().random.nextInt(Math.max(1, max - min + 1));
            addAnger(entity, increase);
        }
    }
}
