package rotp.core.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.MergingDatapackRegistry;

import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;

@Mixin(targets = "net/minecraft/resources/RegistryDataLoader$Loader")
public class RegistryDataLoaderLoaderMixin<T> {
	@Shadow @Final private RegistryDataLoader.RegistryData<T> data;
	@Shadow @Final private WritableRegistry<T> registry;
	@Shadow @Final private Map<ResourceKey<?>, Exception> loadingErrors;

	@Inject(method = "loadFromResources(Lnet/minecraft/server/packs/resources/ResourceManager;"
			+ "Lnet/minecraft/resources/RegistryOps$RegistryInfoLookup;)V",
			at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$mergeAcrossPacks(ResourceManager resourceManager, RegistryOps.RegistryInfoLookup registryInfoLookup, CallbackInfo ci) {
		if (MergingDatapackRegistry.onLoadFromResources(resourceManager, registryInfoLookup, data, registry, loadingErrors)) {
			ci.cancel();
		}
	}
}
