package rotp.core.config.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonElement;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import rotp.core.config.editor.ConfigEntry.Side;
import rotp.core.config.editor.SpecValues.Target;
import rotp.core.core.JojoMod;

/**
 * The settings of every mod that depends on the core: its {@code registerConfig} call is all it takes to appear. COMMON
 * and SERVER values have the server for their owner, CLIENT values the client's own files.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class AddonConfigCatalog {
	private record Catalog(Map<String, Target> server, Map<String, Target> client) {}

	private static final Catalog NONE = new Catalog(Map.of(), Map.of());
	private static final Set<String> WARNED = new HashSet<>();
	private static volatile boolean modsConstructed;
	private static volatile @Nullable Catalog discovered;

	private AddonConfigCatalog() {}

	@SubscribeEvent
	public static void onCommonSetup(FMLCommonSetupEvent event) {
		modsConstructed = true;
	}

	public static Map<String, Target> server() {
		return catalog().server();
	}

	public static Map<String, Target> client() {
		return catalog().client();
	}

	// an add-on still constructing may not have registered yet, so nothing is kept until they all have
	private static Catalog catalog() {
		Catalog known = discovered;
		if (known != null) {
			return known;
		}
		if (!modsConstructed) {
			return NONE;
		}
		synchronized (AddonConfigCatalog.class) {
			if (discovered == null) {
				discovered = discover();
			}
			return discovered;
		}
	}

	private static Catalog discover() {
		Catalog catalog = new Catalog(new LinkedHashMap<>(), new LinkedHashMap<>());
		for (var mod : ModList.get().getMods()) {
			String modId = mod.getModId();
			if (!modId.equals(JojoMod.MOD_ID)
					&& mod.getDependencies().stream().anyMatch(dependency -> JojoMod.MOD_ID.equals(dependency.getModId()))) {
				for (ModConfig config : ModConfigs.getModConfigs(modId)) {
					add(catalog, modId, config.getType(), config.getSpec());
				}
			}
		}
		return catalog;
	}

	private static void add(Catalog catalog, String modId, ModConfig.Type type, IConfigSpec spec) {
		if (type != ModConfig.Type.STARTUP && spec instanceof ModConfigSpec modSpec) {
			collect(modId, modSpec, modSpec.getValues(), type == ModConfig.Type.CLIENT ? catalog.client() : catalog.server());
		}
	}

	private static void collect(String modId, ModConfigSpec spec, UnmodifiableConfig config, Map<String, Target> targets) {
		for (Object child : config.valueMap().values()) {
			if (child instanceof UnmodifiableConfig section) {
				collect(modId, spec, section, targets);
			}
			else if (child instanceof ModConfigSpec.ConfigValue<?> value) {
				String key = AddonConfigKeys.key(modId, value.getPath());
				try {
					Object defaultValue = value.getDefault();
					if (SpecValues.kind(defaultValue) == ConfigEntry.Kind.LIST) {
						SpecValues.listKind((List<?>) defaultValue);
					}
				}
				catch (RuntimeException unknown) {
					JojoMod.getLogger().warn("Core config editor leaves {} off the settings screen: {}", key, unknown.getMessage());
					continue;
				}
				if (targets.putIfAbsent(key, new Target(spec, value)) != null) {
					JojoMod.getLogger().warn("Core config editor leaves out a second value named {}", key);
				}
			}
		}
	}

	/** The rows of these values. A value that cannot be described costs its own row and one log line, never the page. */
	public static List<ConfigEntry> describe(Map<String, Target> targets, Side side, boolean defaultsOnly) {
		List<ConfigEntry> entries = new ArrayList<>(targets.size());
		targets.forEach((key, target) -> {
			var value = target.value();
			try {
				String own = value.getSpec().getTranslationKey();
				String label = own != null ? own : AddonConfigKeys.modId(key) + ".configuration." + value.getPath().getLast();
				entries.add(SpecValues.describe(key, value, !defaultsOnly && target.spec().isLoaded(), side, false, element -> null, List.of(), label));
			}
			catch (RuntimeException broken) {
				if (WARNED.add(key)) {
					JojoMod.getLogger().warn("Core config editor could not describe {}", key, broken);
				}
			}
		});
		return entries;
	}

	/** Each shown value of the group at its default. A hidden one stays as its file has it: the page never said it would change. */
	public static Map<String, JsonElement> groupDefaults(Map<String, Target> targets, String group) {
		Map<String, JsonElement> defaults = new LinkedHashMap<>();
		targets.forEach((key, target) -> {
			if (AddonConfigKeys.group(key).equals(group) && !AddonConfigKeys.isHidden(key)) {
				defaults.put(key, SpecValues.json(target.value().getDefault()));
			}
		});
		return defaults;
	}

	/** A test's stand-in for an add-on, through the same path as a discovered one. */
	public static void addForTest(String modId, ModConfig.Type type, IConfigSpec spec) {
		add(catalog(), modId, type, spec);
	}

	public static void removeForTest(String modId) {
		Catalog catalog = catalog();
		catalog.server().keySet().removeIf(key -> AddonConfigKeys.modId(key).equals(modId));
		catalog.client().keySet().removeIf(key -> AddonConfigKeys.modId(key).equals(modId));
	}
}
