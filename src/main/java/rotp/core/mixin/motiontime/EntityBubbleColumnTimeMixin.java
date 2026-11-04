package rotp.core.mixin.motiontime;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import rotp.core.api.time.EntityTimeRates;

import net.minecraft.world.entity.Entity;

/**
 * EntityTimeRates' MOTION rate r on a bubble column's push, the way the attributes scale walking: the speed caps and the
 * per-tick pushes times r, the water's drag per tick kept, so the entity rises or sinks r times as fast. At r = 1 every
 * value is vanilla.
 */
@Mixin(Entity.class)
public abstract class EntityBubbleColumnTimeMixin {
	private float jojo_ripples$motionRate() {
		return EntityTimeRates.rate(EntityTimeRates.Channel.MOTION, (Entity) (Object) this);
	}

	@ModifyConstant(method = "onAboveBubbleCol", constant = { @Constant(doubleValue = -0.9), @Constant(doubleValue = 1.8) })
	private double jojo_ripples$aboveColumnCap(double cap) {
		return cap * jojo_ripples$motionRate();
	}

	@ModifyConstant(method = "onAboveBubbleCol", constant = { @Constant(doubleValue = 0.03), @Constant(doubleValue = 0.1) })
	private double jojo_ripples$aboveColumnPush(double push) {
		return push * jojo_ripples$motionRate();
	}

	@ModifyConstant(method = "onInsideBubbleColumn", constant = { @Constant(doubleValue = -0.3), @Constant(doubleValue = 0.7) })
	private double jojo_ripples$insideColumnCap(double cap) {
		return cap * jojo_ripples$motionRate();
	}

	@ModifyConstant(method = "onInsideBubbleColumn", constant = { @Constant(doubleValue = 0.03), @Constant(doubleValue = 0.06) })
	private double jojo_ripples$insideColumnPush(double push) {
		return push * jojo_ripples$motionRate();
	}
}
