package rotp.core.client.sound;

import rotp.core.core.JojoMod;
import rotp.core.mechanics.voiceline.VoiceLinePacks;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Loads the voice packs ({@link VoiceLinePacks}) with the client's resource packs. */
public final class ClientVoiceLinePacksLoader extends SimplePreparableReloadListener<VoiceLinePacks> {
	private static volatile VoiceLinePacks packs = VoiceLinePacks.EMPTY;

	private ClientVoiceLinePacksLoader() {}

	public static void init(RegisterClientReloadListenersEvent event) {
		event.registerReloadListener(new ClientVoiceLinePacksLoader());
	}

	public static VoiceLinePacks getPacks() {
		return packs;
	}

	@Override
	protected VoiceLinePacks prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
		return VoiceLinePacks.load(resourceManager);
	}

	@Override
	protected void apply(VoiceLinePacks loaded, ResourceManager resourceManager, ProfilerFiller profiler) {
		packs = loaded;
		JojoMod.getLogger().info("Loaded {} voice line packs", loaded.packCount());
	}
}
