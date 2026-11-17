package rotp.addons.kingcrimson.mixin.epitaph;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/** Which running goal holds each flag: the goals of a copy that run on from where those of its body are hold the same. */
@Mixin(GoalSelector.class)
public interface GoalSelectorAccessor {
    @Accessor("lockedFlags")
    Map<Goal.Flag, WrappedGoal> kingCrimson$lockedFlags();
}
