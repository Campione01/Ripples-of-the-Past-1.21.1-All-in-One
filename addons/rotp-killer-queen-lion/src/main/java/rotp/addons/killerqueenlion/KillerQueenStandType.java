package rotp.addons.killerqueenlion;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

import rotp.addons.killerqueenlion.state.KillerQueenStateService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public class KillerQueenStandType extends EntityStandType {
    // Kept for data-driven Stands that use Killer Queen as their base.
    public KillerQueenStandType(
            StandStats stats,
            MovesetBuilder moveset,
            ResourceLocation id) {
        super(stats, moveset, id);
    }

    public KillerQueenStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> entityType,
            ResourceLocation id) {
        super(stats, moveset, entityType, id);
    }

    // Removing or replacing the Stand (stand remove, stand give ... true)
    // drops the block bomb; a recall does not, unlike 1.16 (owner's rule of 2026-11-05).
    @Override
    public StandTypePersistentData newDataInstance() {
        return new KillerQueenStandData(this);
    }

    private static final class KillerQueenStandData
            extends StandTypePersistentData {
        private KillerQueenStandData(KillerQueenStandType powerType) {
            super(powerType);
        }

        @Override
        public void onPowerCleared(
                Power<?> userPower, PowerType newType) {
            super.onPowerCleared(userPower, newType);
            KillerQueenStateService.onStandCleared(userPower.getUser());
        }
    }
}
