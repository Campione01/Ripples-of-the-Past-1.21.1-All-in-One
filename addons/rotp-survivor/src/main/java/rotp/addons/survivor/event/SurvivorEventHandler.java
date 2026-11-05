package rotp.addons.survivor.event;

import rotp.addons.survivor.SurvivorConfig;
import rotp.addons.survivor.util.AngerHelper;
import rotp.addons.survivor.util.WetHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Wetness and anger of every living entity, Survivor user nearby or not, as in 1.16. */
public final class SurvivorEventHandler {
    private SurvivorEventHandler() {}

    static void onLivingTick(LivingEntity entity) {
        if (entity instanceof Player && !SurvivorConfig.PLAYER_ANGER.get()) {
            return;
        }
        if (WetHelper.isInWater(entity)) {
            WetHelper.setWet(entity);
            AngerHelper.startWarmup(entity);
        }

        WetHelper.tickWet(entity);
        if (WetHelper.isWet(entity)) {
            if (entity.tickCount % 2 == 0 && !AngerHelper.isAngry(entity)) {
                AngerHelper.tickAngerGrowth(entity);
            }
            AngerHelper.tickWarmup(entity);
            AngerHelper.tickAngerTimer(entity);
        } else {
            AngerHelper.resetAnger(entity);
        }
    }
}
