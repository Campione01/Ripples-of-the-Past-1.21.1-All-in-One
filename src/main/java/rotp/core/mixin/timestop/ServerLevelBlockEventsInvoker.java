package rotp.core.mixin.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.server.level.ServerLevel;

@Mixin(ServerLevel.class)
public interface ServerLevelBlockEventsInvoker {
	@Invoker("runBlockEvents")
	void jojo_ripples$runBlockEvents();
}
