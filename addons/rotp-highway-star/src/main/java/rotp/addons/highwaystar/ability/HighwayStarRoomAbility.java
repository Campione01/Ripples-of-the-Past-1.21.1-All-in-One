package rotp.addons.highwaystar.ability;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import rotp.addons.highwaystar.room.HighwayStarRoomService;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;

public final class HighwayStarRoomAbility extends Ability {
    public static final float STAMINA_COST = 300.0F;
    public static final int COOLDOWN_TICKS = 600;
    private final boolean remove;

    public HighwayStarRoomAbility(AbilityType<?> abilityType, AbilityId abilityId, boolean remove) {
        super(abilityType, abilityId);
        this.remove = remove;
        usageGroup = AbilityUsageGroup.UTILITY;
        cooldown(remove ? 0 : COOLDOWN_TICKS, 0, 0.0F);
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return remove ? 0.0F : STAMINA_COST;
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        if (!(context instanceof StandPower power) || !(power.getUser() instanceof Player player)) {
            return ConditionCheck.createNegative(Component.translatable("action.highwaystar.condition.room_player_only"));
        }
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        return remove || HighwayStarRoomService.findPlacement(player) != null ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(Component.translatable("action.highwaystar.condition.room_needs_wall"));
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (!(user instanceof ServerPlayer player) || !HighwayStarRoomService.hasHighwayStar(player)) {
            return;
        }
        if (remove) {
            HighwayStarRoomService.invalidate(player);
        }
        else if (HighwayStarRoomService.place(player) != null) {
            StandPower power = StandPower.get(player);
            StandAbilityStamina.consume(this, power, STAMINA_COST, false);
            setCooldownOnUse(power);
        }
    }
}
