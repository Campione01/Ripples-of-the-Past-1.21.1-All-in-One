package rotp.addons.spin.breaker;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import rotp.core.powersystem.standpower.entity.StandEntity;

public final class BallBreakerEntity extends StandEntity {
    public BallBreakerEntity(EntityType<? extends StandEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && !isRemoved()) {
            BallBreakerClient.tickAura(this);
        }
    }
}
