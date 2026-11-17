package rotp.core.config.editor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import rotp.core.core.JojoMod;

/**
 * The items a list setting may hold, offered by the server: the settings screen shows such a list as one tick box per
 * item, and a write that names an item the server does not offer is refused. An item already stored, or in the default,
 * stays allowed when the server stops offering it (a removed mod's dimension), so it can be shown as such and unticked.
 * <p>
 * An add-on registers once, for one of its own list-of-strings values, for example right after building its spec:
 * <pre>{@code ConfigChoices.register(D4CConfig.PARALLEL_DIMENSIONS, ConfigChoices.dimensions());}</pre>
 * A list without a source stays the plain list editor.
 */
public final class ConfigChoices {
	/** As many items as a list value may hold; a source that offers more is cut here, with one log line. */
	public static final int LIMIT = 1024;
	// what the screen's wire takes for one id, name or key, and what a stored list item may be
	static final int TEXT_LIMIT = 160;
	private static final int ITEM_BYTES = 256;

	private static final Map<ModConfigSpec.ConfigValue<?>, Source> SOURCES = new ConcurrentHashMap<>();
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private ConfigChoices() {}

	/**
	 * One item: the id the list stores, the name shown, and a translation key the client may know for that name (else the
	 * name is shown as it is).
	 */
	public record Choice(String id, String name, @Nullable String translationKey) {
		public Choice {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(name, "name");
		}

		public static Choice of(String id, String name) {
			return new Choice(id, name, null);
		}
	}

	@FunctionalInterface
	public interface Source {
		/** The items offered now. Called on the server thread each time the screen reads the settings and each time one is written. */
		List<Choice> choices(MinecraftServer server);
	}

	/**
	 * Offers this list's items from {@code source}. The value must be a list of strings (an empty default counts as one);
	 * a second call for the same value replaces the first.
	 */
	public static void register(ModConfigSpec.ConfigValue<? extends List<? extends String>> value, Source source) {
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(source, "source");
		Object defaultValue = value.getDefault();
		if (!(defaultValue instanceof List<?> list) || !list.stream().allMatch(String.class::isInstance)) {
			throw new IllegalArgumentException("A choice source needs a list of strings: " + String.join(".", value.getPath()));
		}
		SOURCES.put(value, source);
	}

	/** Whether the value's items are offered by a source. */
	public static boolean hasSource(ModConfigSpec.ConfigValue<?> value) {
		return SOURCES.containsKey(value);
	}

	/**
	 * The dimensions of the running server, the Overworld first, then by id. The name is the dimension's path in words; the
	 * key is the core's own for the three of the base game, and {@code dimension.<namespace>.<path>} for the others.
	 */
	public static Source dimensions() {
		return server -> {
			List<ResourceLocation> ids = new ArrayList<>();
			server.levelKeys().forEach(key -> ids.add(key.location()));
			ids.sort(Comparator.comparing((ResourceLocation id) -> !id.equals(Level.OVERWORLD.location()))
					.thenComparing(id -> !"minecraft".equals(id.getNamespace()))
					.thenComparing(ResourceLocation::toString));
			List<Choice> choices = new ArrayList<>(ids.size());
			for (ResourceLocation id : ids) {
				String key = "minecraft".equals(id.getNamespace()) && Set.of("overworld", "the_nether", "the_end").contains(id.getPath())
						? JojoMod.MOD_ID + ".config.dimension." + id.getPath()
						: "dimension." + id.getNamespace() + "." + id.getPath();
				choices.add(new Choice(id.toString(), words(id.getPath()), key));
			}
			return choices;
		};
	}

	/** The items offered now, cut to what one entry carries; null when no source offers this value's items. */
	@Nullable
	static List<Choice> offered(ModConfigSpec.ConfigValue<?> value) {
		Source source = SOURCES.get(value);
		if (source == null) {
			return null;
		}
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return List.of();
		}
		String name = String.join(".", value.getPath());
		List<Choice> raw;
		try {
			raw = source.choices(server);
		}
		// an add-on's source that fails costs its items, never the page: what is stored stays shown and can be unticked
		catch (RuntimeException broken) {
			if (WARNED.add(name + " failed")) {
				JojoMod.getLogger().warn("Core config editor: the choices of {} could not be read", name, broken);
			}
			return List.of();
		}
		if (raw == null) {
			return List.of();
		}
		List<Choice> kept = new ArrayList<>(Math.min(raw.size(), LIMIT));
		Set<String> ids = new HashSet<>();
		int left = 0;
		for (Choice choice : raw) {
			if (choice == null || !fitsItem(choice.id()) || !ids.add(choice.id()) || kept.size() == LIMIT) {
				left++;
				continue;
			}
			String shown = choice.name().isBlank() ? choice.id() : clip(choice.name());
			String key = choice.translationKey() != null && choice.translationKey().length() <= TEXT_LIMIT ? choice.translationKey() : null;
			kept.add(new Choice(choice.id(), shown, key));
		}
		if (left > 0 && WARNED.add(name + " left")) {
			JojoMod.getLogger().warn("Core config editor leaves {} of the choices of {} out: a blank, repeated or too long id, or more than {}",
					left, name, LIMIT);
		}
		return List.copyOf(kept);
	}

	/**
	 * Whether a write of these items keeps to the value's source: each item offered now, stored now or in the default, and
	 * none twice. A value without a source takes any items its spec takes.
	 */
	static boolean allows(ModConfigSpec.ConfigValue<?> value, List<?> items) {
		List<Choice> offered = offered(value);
		if (offered == null) {
			return true;
		}
		Set<String> allowed = new HashSet<>();
		offered.forEach(choice -> allowed.add(choice.id()));
		addStrings(allowed, value.getDefault());
		try {
			addStrings(allowed, value.getRaw());
		}
		catch (IllegalStateException notLoaded) {
			// nothing is stored yet: only what is offered and the default
		}
		Set<Object> seen = new HashSet<>();
		for (Object item : items) {
			if (!(item instanceof String id) || !allowed.contains(id) || !seen.add(id)) {
				return false;
			}
		}
		return true;
	}

	private static void addStrings(Set<String> into, @Nullable Object list) {
		if (list instanceof List<?> items) {
			items.forEach(item -> {
				if (item instanceof String id) {
					into.add(id);
				}
			});
		}
	}

	private static boolean fitsItem(@Nullable String id) {
		return id != null && !id.isBlank() && id.length() <= TEXT_LIMIT && id.getBytes(StandardCharsets.UTF_8).length <= ITEM_BYTES;
	}

	private static String clip(String name) {
		return name.length() <= TEXT_LIMIT ? name : name.substring(0, TEXT_LIMIT - 3) + "...";
	}

	// "twilight_forest" reads "Twilight Forest"
	private static String words(String path) {
		String last = path.substring(path.lastIndexOf('/') + 1);
		StringBuilder words = new StringBuilder(last.length());
		for (String word : last.split("[_\\-]+")) {
			if (!word.isEmpty()) {
				if (!words.isEmpty()) {
					words.append(' ');
				}
				words.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
			}
		}
		return words.isEmpty() ? path : words.toString();
	}
}
