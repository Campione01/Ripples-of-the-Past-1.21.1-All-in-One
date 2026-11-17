package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.ai.goal.WrappedGoal;

/** A goal of a copy runs on from where its twin is: it is marked as running without being started again. */
@Mixin(WrappedGoal.class)
public interface WrappedGoalAccessor {
    @Accessor("isRunning")
    void kingCrimson$setRunning(boolean running);
}
