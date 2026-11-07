package rotp.core.mixin.timestop;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;

@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataTimeStopMixin {
	@Shadow @Final private SyncedDataHolder entity;

	@Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
			at = @At(value = "FIELD", target = "Lnet/minecraft/network/syncher/SynchedEntityData;isDirty:Z",
					opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void jojo_ripples$queueDirtyEntityData(CallbackInfo ci) {
		if (entity instanceof Entity owner) {
			TimeStopState.onEntityDataDirty(owner);
		}
	}
}
