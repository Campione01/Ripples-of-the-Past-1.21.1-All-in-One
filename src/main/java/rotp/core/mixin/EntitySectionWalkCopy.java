package rotp.core.mixin;

import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;

/**
 * A chunk losing its ticket walks its sections and posts EntityLeaveLevelEvent for each entity. A listener that removes
 * another entity of the section, as the add-ons do with a mob's Stand, its rods or its swarm, then stopped the server with
 * a ConcurrentModificationException in the walk. It goes over a copy: an entity removed on the way still gets its leave.
 */
@Mixin(EntitySection.class)
public abstract class EntitySectionWalkCopy<T extends EntityAccess> {
	@Shadow @Final private ClassInstanceMultiMap<T> storage;

	@Inject(method = "getEntities()Ljava/util/stream/Stream;", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$walkACopy(CallbackInfoReturnable<Stream<T>> cir) {
		cir.setReturnValue(storage.getAllInstances().stream());
	}
}
