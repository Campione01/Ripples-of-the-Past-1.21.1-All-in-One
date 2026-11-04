package rotp.core.api.client.time;

import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Answers how fast an entity's client clock runs: a rate above 0 (1 is
 * normal, below 1 slower, above 1 faster), or
 * {@link ClientEntityClockRates#NO_RATE} (any value that is not a finite
 * positive number) when this owner has nothing to say about the entity.
 */
@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface ClientEntityClockRateProvider {
	float rate(Entity entity);
}
