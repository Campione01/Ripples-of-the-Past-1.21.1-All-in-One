package rotp.addons.justice;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

public final class JusticeEntity extends StandEntity {
    public JusticeEntity(EntityType<? extends JusticeEntity> type, Level level) {
        super(type, level);
        // The legacy withYOffset arguments were left, vertical, forward.
        offsetFromUser = new StandOffsetFromUser(this, new Vec3(0, 0.5, -0.5), StandOffsetFromUser.Rotations.HEAD)
                .armsOnlyIdleOffset(Vec3.ZERO, StandOffsetFromUser.Rotations.HEAD);
    }
}
