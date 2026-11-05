package rotp.addons.darkbluemoon.ability;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonConfig;
import rotp.addons.darkbluemoon.entity.ScaleShotEntity;
import rotp.addons.darkbluemoon.util.DbmWater;
import rotp.addons.darkbluemoon.util.ScaleShotAmmo;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 1.16 DarkBlueMoonScaleShotAction and its Shift variation DarkBlueMoonScaleShotFocusedAction ("MAZA" pose, 13 ticks,
 * 100 stamina, cooldown 20): a player with enough scales (5 a use of the 20) fires 5 scales from the Stand at 1.5, the
 * i-th spread by 12 x i degrees, or by 1.5 x i when focused. "No scales to fire" otherwise.
 */
public class DbmScaleShotAbility extends DbmStandAction {
    private final float spreadPerShot;

    public DbmScaleShotAbility(AbilityType<?> abilityType, AbilityId abilityId, boolean focused) {
        super(abilityType, abilityId, "MAZA", 13, 100.0F, 20, TargetRequirement.NONE);
        this.spreadPerShot = focused ? 1.5F : 12.0F;
    }

    @Override
    protected ConditionCheck checkDbmConditions(LivingEntity user, @Nullable StandPower power) {
        if (user instanceof Player player
                && ScaleShotAmmo.getAmmo(player) < DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.SCALE_SHOT_COUNT)) {
            return ConditionCheck.createNegative(ScaleShotAmmo.noAmmoMessage());
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    protected void standPerform(DbmAction action, StandEntity stand, LivingEntity user) {
        if (!(user instanceof Player player)) {
            return;
        }
        int count = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.SCALE_SHOT_COUNT);
        if (!ScaleShotAmmo.consumeAmmo(player, count)) {
            return;
        }
        Level level = stand.level();
        boolean inWater = DbmWater.isInWater(user);
        for (int i = 0; i < count; i++) {
            ScaleShotEntity scale = new ScaleShotEntity(user, level, inWater);
            scale.setPos(stand.getX(), stand.getEyeY() - 0.2, stand.getZ());
            scale.shootFromRotation(stand, 1.5F, spreadPerShot * i);
            level.addFreshEntity(scale);
        }
    }
}
