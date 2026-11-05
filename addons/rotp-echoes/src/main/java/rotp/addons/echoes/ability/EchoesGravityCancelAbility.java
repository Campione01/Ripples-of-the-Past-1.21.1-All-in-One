package rotp.addons.echoes.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.state.EchoesGravityService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The master's cancel actions (GravityFreezeCancelAction, HandFreezeCancelAction, GravityFreezeSelfCancelAction,
 * HandFreezeSelfCancelAction, GravityInfuseCancelAction): plain Stand actions that end one running effect without
 * summoning the Stand, so a freeze can be lifted while its cooldown runs.
 */
public final class EchoesGravityCancelAbility extends Ability {
    private final EchoesGravityAbility.Mode mode;

    public EchoesGravityCancelAbility(AbilityType<?> abilityType, AbilityId abilityId, EchoesGravityAbility.Mode mode) {
        super(abilityType, abilityId);
        this.mode = mode;
        usageGroup = AbilityUsageGroup.UTILITY;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        if (!(context instanceof StandPower power) || !(power.getUser() instanceof Player player)
                || !RotpEchoes.isEchoesUser(player)) {
            return ConditionCheck.NEGATIVE;
        }
        if (mode == EchoesGravityAbility.Mode.INFUSE_CANCEL && !EchoesGravityService.holdsInfusedItem(player)) {
            return ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (!level.isClientSide() && user instanceof ServerPlayer player) {
            EchoesGravityService.perform(player, mode);
        }
    }
}
