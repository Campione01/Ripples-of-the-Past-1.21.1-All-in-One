package rotp.core.gametest;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.StandInitStarPlatinum;

/**
 * A Stand registered under another mod's namespace, as an add-on registers its own: the Stands page's rows and every
 * share must reach it with no code of its own. It is Star Platinum's build under that name, so its punches, its
 * cooldowns and its stamina run through the same paths an add-on's would, and it has none of the core's special values.
 * Only the backend config game test server registers it.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class AddonStandStandIn {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(CommonConfigEditorGameTests.NAMESPACE, "addon_stand");

	private AddonStandStandIn() {}

	@SubscribeEvent
	public static void register(RegisterEvent event) {
		String namespaces = System.getProperty("neoforge.enabledGameTestNamespaces", "");
		if (event.getRegistryKey().equals(JojoRegistries.DEFAULT_STANDS_REG_KEY) && namespaces.contains(CommonConfigEditorGameTests.NAMESPACE)) {
			event.register(JojoRegistries.DEFAULT_STANDS_REG_KEY, helper -> helper.register(ID, StandInitStarPlatinum.create(ID)));
		}
	}
}
