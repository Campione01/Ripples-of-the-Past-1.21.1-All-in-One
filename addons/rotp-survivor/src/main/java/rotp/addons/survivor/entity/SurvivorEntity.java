package rotp.addons.survivor.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class SurvivorEntity extends StandEntity {
    public SurvivorEntity(EntityType<? extends SurvivorEntity> type, Level level) {
        super(type, level);
    }
}
