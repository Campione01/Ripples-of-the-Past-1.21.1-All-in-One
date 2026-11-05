package rotp.addons.highwaystar.smell;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class HighwayStarSmellHelper {
    private HighwayStarSmellHelper() {}

    public static boolean isSmellable(LivingEntity entity) {
        if (entity == null || entity instanceof StandEntity || entity instanceof HighwayStarSoleEntity) {
            return false;
        }
        if (entity instanceof Player player && player.isSpectator()) {
            return false;
        }
        return entity.isAlive();
    }
}
