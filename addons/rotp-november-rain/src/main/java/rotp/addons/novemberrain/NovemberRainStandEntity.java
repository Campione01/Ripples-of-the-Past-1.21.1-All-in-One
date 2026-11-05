package rotp.addons.novemberrain;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class NovemberRainStandEntity extends StandEntity {
    public NovemberRainStandEntity(
            EntityType<? extends NovemberRainStandEntity> type,
            Level level) {
        super(type, level);
        // 1.0.4 remake NovemberRainEntity: idle and arms-only offsets
        // withYOffset(0, 0, 0), so its model stands over its user.
        Y_OFFSET = 0.0D;
        offsetFromUser = new StandOffsetFromUser(
                this,
                new Vec3(0.0D, Y_OFFSET, 0.0D),
                StandOffsetFromUser.Rotations.HEAD)
                .grabOffset(offsetFromUser.grabIdleOffset);
    }
}
