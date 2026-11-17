package rotp.addons.kiss.entity;

import rotp.addons.kiss.KissAddon;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class KissStandEntity extends StandEntity {
    public KissStandEntity(EntityType<? extends StandEntity> type, Level level) {
        super(type, level);
    }

    // 1.16 ExampleStandEntity.playStandSummonSound: the Stand says it at every summon, automatic ones too, where the
    // core's summon shout stays silent under Shift, within 10 s of the last one and when the action summons the Stand.
    @Override
    public void playStandSummonSound() {
        super.playStandSummonSound();
        StandUtil.playStandEntitySound(this, KissAddon.SUMMON_VOICE, 1.0F, 1.0F);
    }
}
