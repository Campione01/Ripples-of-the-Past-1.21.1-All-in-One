package rotp.addons.spin.ability;

import java.util.function.Function;

import net.minecraft.world.entity.LivingEntity;
import rotp.addons.spin.power.SpinData;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

public class SpinActionAbility extends EntityActionAbility {
    private final int requiredLesson;

    protected SpinActionAbility(AbilityType<?> type, AbilityId id, int requiredLesson,
            Function<EntityActionType, ? extends EntityActionInstance> factory) {
        super(type, id, factory);
        this.requiredLesson = requiredLesson;
        spriteName = id.nameInMoveset();
    }

    @Override
    public String getTranslationKey(String postfix) {
        String action = name();
        return "action.rotp_spin." + action.substring(action.lastIndexOf(':') + 1) + postfix;
    }

    @Override
    public ConditionCheck getUnlockConditionCheck(Power<?> context) {
        ConditionCheck check = super.getUnlockConditionCheck(context);
        if (!check.isPositive()) return check;
        return context.getCurTypeData() instanceof SpinData data
                && data.effectiveLesson(context.getUser()) >= requiredLesson
                ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("not_unlocked");
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) return check;
        return context.getCurTypeData() instanceof SpinData data && !data.onCooldown(name())
                ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
    }

    protected static SpinData data(LivingEntity user) {
        return SpinData.of(user).orElse(null);
    }

    protected static boolean spend(LivingEntity user, double amount) {
        return SpinData.consumeEnergy(user, (float) amount);
    }

    protected static boolean hasEnergy(LivingEntity user, double amount) {
        return SpinData.hasEnergy(user, (float) amount);
    }

    protected void beginCooldown(LivingEntity user, int ticks) {
        SpinData.setCooldown(user, name(), ticks);
    }
}
