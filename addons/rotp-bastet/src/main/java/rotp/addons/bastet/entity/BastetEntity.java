package rotp.addons.bastet.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class BastetEntity extends StandEntity {
    public BastetEntity(EntityType<? extends BastetEntity> type, Level level) {
        super(type, level);
    }
}
