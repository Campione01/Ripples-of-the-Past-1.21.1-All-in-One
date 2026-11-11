package rotp.core.config.editor.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import net.minecraft.resources.ResourceLocation;
import rotp.core.JojoModConfig;
import rotp.core.config.editor.AddonConfigKeys;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.StandConfigKeys;
import rotp.core.config.editor.ConfigEntry.Bounds;
import rotp.core.config.editor.ConfigEntry.Kind;
import rotp.core.config.editor.ConfigEntry.Side;

/**
 * What the settings screen makes of a catalog entry: which widget, which section, which text. It reads only the entry,
 * so a key added to either store gets a working row without code here; the lists below only give known keys a place.
 */
public final class ConfigWidgetRules {
	public enum WidgetKind { TOGGLE, SLIDER, NUMBER, CYCLE, TEXT, LIST, CHECKLIST, COLOR }

	public record Section(String id, List<ConfigEntry> entries) {}

	/** One add-on's settings on one page, its own groups in the order of its file. */
	public record Block(String modId, List<Group> groups) {
		public List<String> keys() {
			return groups.stream().flatMap(group -> group.entries().stream()).map(ConfigEntry::key).toList();
		}
	}

	/** A group's dotted path inside its add-on; empty for the values at the top of the file. */
	public record Group(String path, List<ConfigEntry> entries) {}

	/**
	 * One Stand on the Stands page: its own rows in their groups ({@link #STAND_GROUPS}), then the settings of the add-on
	 * that registered it, and how many Stands that add-on registered (its settings show in the block of each).
	 */
	public record StandBlock(ResourceLocation standId, List<Group> groups, @Nullable Block addon, int addonStands) {
		public List<String> keys() {
			List<String> keys = new ArrayList<>();
			groups.forEach(group -> group.entries().forEach(entry -> keys.add(entry.key())));
			if (addon != null) {
				keys.addAll(addon.keys());
			}
			return keys;
		}
	}

	/** The groups of a Stand's own rows, in the order of the page. */
	public static final List<String> STAND_GROUPS = List.of("obtaining", "stats", "shares", "blocks", "special");

	private record SectionRule(String id, List<String> leaves, @Nullable String prefix) {}

	public static final String OTHER_SECTION = "other";
	public static final String SECTION_TEXT = "jojo_ripples.config.section.";
	public static final String OPTION_TEXT = "jojo_ripples.config.option.";
	public static final String CLIENT_TEXT = "jojo_ripples.config.client.";

	// A switch that does nothing would lie to the player. This one belongs to the unfinished test key: LmbRmbKeyMapping.setKey
	// writes it from the key binds screen, and all it gates is a test sound (InputHandler.doTheThing).
	private static final Set<String> HIDDEN = Set.of("poseOnLmbRmb");

	private static final List<SectionRule> CLIENT_SECTIONS = List.of(
			section("effects", "characterVoiceLines", "menacingParticles", "ownAfkMenacingFirstPerson", "screenSplash",
					"resolveShaders", "timeStopAnimation"),
			section("hud", "showLockedSlots", "compactStandHotbar", "standExpBar", "standStatsTranslucency", "standStatsInvertBnW"),
			section("controls", "abilitySelectionWheel", "abilitySelectionScroll", "releaseToUseAbility", "toggleDisableHotbars"),
			section("your_stand", "broadcasted.standSide", "broadcasted.noStandAbilityCooldown",
					"broadcasted.standAttackTargetLock", "broadcasted.standAimAssist", "broadcasted.standBreaksBlocks",
					"broadcasted.standPicksUpItems", "broadcasted.standMovesBeyondEffectiveRange"),
			section("stand_display", "standMotionTilt", "standAimMarker", "standTransparency", "standOthersTransparency",
					"classicStandObstruction", "standOutline"),
			section("hamon_vampire", "thirdPersonHamonAura", "firstPersonHamonAura", "hamonAuraBlur",
					"broadcasted.vampireGlowingEyes"),
			new SectionRule("stand_aura", List.of("standAura.enabled", "standAura.mode"), "standAura."));

	private static final List<SectionRule> SERVER_SECTIONS = List.of(
			section("keep_on_death", "keepStandOnDeath", "keepVampirismOnDeath", "keepHamonOnDeath", "dropStandDisc"),
			section("stand", "skipStandProgression", "standExpMultiplier", "resolveLvlPoints", "standPowerMultiplier",
					"standResistanceMultiplier", "standStamina", "standBodyThroughWalls", "soulAscension"),
			section("arrows", "standArrowMode", "bannedStands", "standXpCostInitial", "standXpCostIncrease",
					"arrowDurability", "arrowDurabilityBeetle"),
			section("time_stop", "timeStopRangeChunks", "timeStopChargeSeconds", "timeStopDamageMultiplier",
					"endermenBeyondTimeSpace"),
			section("hamon", "hamonPointsMultiplier", "hamonDamageMultiplier", "breathingTrainingMultiplier",
					"breathingTrainingDeterioration", "breathingHamonStatGap", "hamonEnergyTicksDown", "mixHamonTechniques",
					"techniqueSkillRequirements"),
			section("vampirism", "maxBloodMultiplier", "bloodDrainMultiplier", "bloodTickDown", "bloodHealCost",
					"vampiresAggroMobs", "undeadMobsSunDamage", "vampirismCuringDuration"),
			section("world", "hamonTempleSpawn", "meteoriteSpawn", "pillarManTempleSpawn", "spawnCocoJumboTurtle",
					"saveDestroyedBlocks"));

	// One value per difficulty, in the order the game reads them: VampirismUtil.difficultyValue.
	private static final Set<String> BY_DIFFICULTY = Set.of("maxBloodMultiplier", "bloodDrainMultiplier", "bloodTickDown", "bloodHealCost");
	private static final List<String> DIFFICULTY_LABELS = List.of("options.difficulty.peaceful", "options.difficulty.easy",
			"options.difficulty.normal", "options.difficulty.hard");

	// the Outline setting only draws with the classic obstruction on: StandDisplaySettingsScreen.updateControls
	private static final Map<String, String> ENABLED_BY = Map.of("standOutline", "classicStandObstruction");

	private static final Map<String, String> ICONS = Map.of("abilitySelectionWheel", "ability_selection_wheel",
			"standAimMarker", "stand_aim_marker");

	// What add-on titles wrap their name in, in the languages they ship. A list of names has no use for "... Configuration" on every line.
	private static final Pattern TITLE_BEFORE = Pattern.compile("^(?:Configuraci[oó]n|Configura[cç][aã]o|Настройки|Налаштування)\\s+(?:de\\s+|do\\s+|da\\s+)?");
	private static final Pattern TITLE_AFTER = Pattern.compile("\\s*(?:Configuration|附属配置|配置|设置|設定|设定|之設)$");
	private static final Pattern TITLE_MOD = Pattern.compile("^(?:RotP|PotP)\\s*[-:：]?\\s*");
	private static final Pattern DISPLAY_NAME_AFTER = Pattern.compile("\\s+(?:RotP\\s+)?(?:Addon|Stand)$");

	private ConfigWidgetRules() {}

	private static SectionRule section(String id, String... leaves) {
		return new SectionRule(id, List.of(leaves), null);
	}

	public static boolean isShown(ConfigEntry entry) {
		return !HIDDEN.contains(entry.key()) && !AddonConfigKeys.isHidden(entry.key());
	}

	public static Set<String> hiddenKeys() {
		return HIDDEN;
	}

	public static WidgetKind widgetKind(ConfigEntry entry) {
		return switch (entry.kind()) {
			case BOOLEAN -> WidgetKind.TOGGLE;
			case ENUM -> WidgetKind.CYCLE;
			case LIST -> isChecklist(entry) ? WidgetKind.CHECKLIST : WidgetKind.LIST;
			case STRING -> entry.choices().isEmpty() ? WidgetKind.TEXT : WidgetKind.CYCLE;
			case INTEGER, DECIMAL -> isColor(entry) ? WidgetKind.COLOR : sliderFits(entry) ? WidgetKind.SLIDER : WidgetKind.NUMBER;
		};
	}

	// A slider needs a range a hand can place a value in. A multiplier of 0 to 5000 whose useful values are 1 and 1.5 does not.
	private static boolean sliderFits(ConfigEntry entry) {
		Bounds bounds = entry.bounds();
		if (bounds == null || bounds.maximum() <= bounds.minimum()) {
			return false;
		}
		double range = bounds.maximum() - bounds.minimum();
		if (isPercent(entry)) {
			return true;
		}
		// the client's own wide sliders are percentages this file knows; an add-on's wide range is a rule like the server's
		return entry.kind() == Kind.INTEGER ? range <= 200 : range <= 10 || entry.side() == Side.CLIENT && !isAddon(entry);
	}

	public static BigDecimal sliderStep(ConfigEntry entry) {
		// a Stand's share reads in fives: 0 to 500 is a hundred steps under a hand
		if (isPercent(entry)) {
			return BigDecimal.valueOf(5);
		}
		if (entry.kind() == Kind.INTEGER) {
			return BigDecimal.ONE;
		}
		double range = entry.bounds().maximum() - entry.bounds().minimum();
		// a rule's value is a round number: a hand on a slider this wide lands on 1.05, not on 1.03
		if (entry.side() == Side.SERVER && range >= 1) {
			return new BigDecimal("0.05");
		}
		return BigDecimal.ONE.scaleByPowerOfTen((int) Math.floor(Math.log10(range)) - 2);
	}

	public static double sliderFraction(ConfigEntry entry, JsonElement value) {
		Bounds bounds = entry.bounds();
		double raw = new BigDecimal(value.getAsString()).doubleValue();
		return (raw - bounds.minimum()) / (bounds.maximum() - bounds.minimum());
	}

	public static JsonElement sliderValue(ConfigEntry entry, double fraction) {
		Bounds bounds = entry.bounds();
		double raw = bounds.minimum() + Math.max(0, Math.min(1, fraction)) * (bounds.maximum() - bounds.minimum());
		BigDecimal step = sliderStep(entry);
		BigDecimal snapped = BigDecimal.valueOf(raw).divide(step, 0, RoundingMode.HALF_UP).multiply(step);
		snapped = snapped.max(BigDecimal.valueOf(bounds.minimum())).min(BigDecimal.valueOf(bounds.maximum()));
		return entry.kind() == Kind.INTEGER ? new JsonPrimitive(snapped.intValue()) : new JsonPrimitive(snapped.doubleValue());
	}

	/** A slider's number: as fine as its steps, so a percentage reads 25 and not 25.0. */
	public static String sliderText(ConfigEntry entry, JsonElement value) {
		if (isPercent(entry)) {
			return valueNumber(entry, value);
		}
		if (sliderStep(entry).compareTo(BigDecimal.ONE) >= 0) {
			return new BigDecimal(value.getAsString()).setScale(0, RoundingMode.HALF_UP).toPlainString();
		}
		return formatNumber(entry.kind(), value);
	}

	/** A number as its row shows it, a Stand's share with its percent sign, a colour as #RRGGBB. */
	public static String valueNumber(ConfigEntry entry, JsonElement value) {
		if (isColor(entry)) {
			return formatColor(value);
		}
		String number = formatNumber(entry.kind(), value);
		return isPercent(entry) ? number + "%" : number;
	}

	private static final double RGB_MAX = 0xFFFFFF;

	/**
	 * A colour, which an add-on marks with no code for the screen: a whole number from 0 to 16777215 (0xFFFFFF, the
	 * range of an RGB colour) whose name says colour ("textColor", "auraColour").
	 */
	public static boolean isColor(ConfigEntry entry) {
		Bounds bounds = entry.bounds();
		if (entry.kind() != Kind.INTEGER || bounds == null || bounds.minimum() != 0 || bounds.maximum() != RGB_MAX) {
			return false;
		}
		String name = leaf(entry.key()).toLowerCase(Locale.ROOT);
		return name.contains("color") || name.contains("colour");
	}

	/** #RRGGBB, or RRGGBB, in either case; null for anything else. */
	public static @Nullable JsonElement parseColor(String typed) {
		String hex = typed.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		return hex.matches("[0-9a-fA-F]{6}") ? new JsonPrimitive(Integer.parseInt(hex, 16)) : null;
	}

	public static String formatColor(JsonElement value) {
		return String.format(Locale.ROOT, "#%06X", value.getAsInt() & 0xFFFFFF);
	}

	/**
	 * A setting's name from a text made for a button that says its value too: "Aim assist: %s" reads "Aim assist". Each
	 * language writes the value's place its own way, so what goes is the placeholder and the punctuation around it.
	 */
	public static String labelWithoutValue(String pattern) {
		return pattern.replaceAll("%(\\d+\\$)?s|%%", "").replaceAll("^[\\s:：]+|[\\s:：]+$", "");
	}

	/** A number as a player reads it: 12, 1.5, 0.75, with at least one decimal on a decimal setting. */
	public static String formatNumber(Kind kind, JsonElement value) {
		BigDecimal number = new BigDecimal(value.getAsString()).stripTrailingZeros();
		if (kind == Kind.INTEGER) {
			return number.setScale(0, RoundingMode.HALF_UP).toPlainString();
		}
		return (number.scale() < 1 ? number.setScale(1, RoundingMode.UNNECESSARY) : number).toPlainString();
	}

	/** The value a typed text stands for, or null when it is not one this setting takes. */
	public static @Nullable JsonElement parseScalar(Kind kind, @Nullable Bounds bounds, String typed) {
		String text = typed.trim();
		if (kind == Kind.STRING) {
			return text.isEmpty() ? null : new JsonPrimitive(text);
		}
		try {
			BigDecimal number = new BigDecimal(text);
			if (kind == Kind.INTEGER) {
				int value = number.intValueExact();
				return bounds == null || bounds.contains(value) ? new JsonPrimitive(value) : null;
			}
			double value = number.doubleValue();
			return bounds == null || bounds.contains(value) ? new JsonPrimitive(value) : null;
		}
		catch (NumberFormatException | ArithmeticException notANumber) {
			return null;
		}
	}

	public static boolean isModified(ConfigEntry entry) {
		return !entry.value().equals(entry.defaultValue());
	}

	/** The choice this value means. A file from the R770 test builds may still hold their names for the two body modes. */
	public static String shownChoice(ConfigEntry entry, JsonElement value) {
		String text = value.isJsonPrimitive() ? value.getAsString() : "";
		if (!entry.choices().contains(text)) {
			if (JojoModConfig.LEGACY_BODY_MODE_1_16.equals(text) && entry.choices().contains(JojoModConfig.BODY_MODE_STANDARD)) {
				return JojoModConfig.BODY_MODE_STANDARD;
			}
			if (JojoModConfig.LEGACY_BODY_MODE_MANGA.equals(text) && entry.choices().contains(JojoModConfig.BODY_MODE_PHASING)) {
				return JojoModConfig.BODY_MODE_PHASING;
			}
		}
		return text;
	}

	public static String nextChoice(ConfigEntry entry, JsonElement value, int direction) {
		List<String> choices = entry.choices();
		int index = choices.indexOf(shownChoice(entry, value));
		return choices.get(index < 0 ? 0 : Math.floorMod(index + direction, choices.size()));
	}

	public static List<Section> sections(Side side, List<ConfigEntry> entries) {
		List<ConfigEntry> shown = entries.stream().filter(entry -> isShown(entry) && isCore(entry)).toList();
		Set<String> placed = new LinkedHashSet<>();
		List<Section> sections = new ArrayList<>();
		for (SectionRule rule : side == Side.CLIENT ? CLIENT_SECTIONS : SERVER_SECTIONS) {
			List<ConfigEntry> rows = new ArrayList<>();
			for (String leaf : rule.leaves()) {
				shown.stream().filter(entry -> entry.key().equals(leaf) || leaf(entry.key()).equals(leaf) && side == Side.SERVER)
						.findFirst().ifPresent(entry -> {
							rows.add(entry);
							placed.add(entry.key());
						});
			}
			if (rule.prefix() != null) {
				shown.stream().filter(entry -> entry.key().startsWith(rule.prefix()) && placed.add(entry.key())).forEach(rows::add);
			}
			if (!rows.isEmpty()) {
				sections.add(new Section(rule.id(), List.copyOf(rows)));
			}
		}
		List<ConfigEntry> rest = shown.stream().filter(entry -> !placed.contains(entry.key())).toList();
		if (!rest.isEmpty()) {
			sections.add(new Section(OTHER_SECTION, rest));
		}
		return List.copyOf(sections);
	}

	/** The add-ons' blocks of these entries, sorted by the name shown. An add-on whose every row is hidden has none. */
	public static List<Block> blocks(List<ConfigEntry> entries, Function<String, String> nameOf) {
		Map<String, Map<String, List<ConfigEntry>>> byMod = new LinkedHashMap<>();
		for (ConfigEntry entry : entries) {
			if (isAddon(entry) && isShown(entry)) {
				byMod.computeIfAbsent(AddonConfigKeys.modId(entry.key()), mod -> new LinkedHashMap<>())
						.computeIfAbsent(AddonConfigKeys.groupPath(entry.key()), path -> new ArrayList<>()).add(entry);
			}
		}
		List<Block> blocks = new ArrayList<>();
		byMod.forEach((modId, groups) -> {
			List<Group> ordered = new ArrayList<>();
			// the values at the top of a file come first, whatever line they are declared on
			if (groups.containsKey("")) {
				ordered.add(new Group("", List.copyOf(groups.get(""))));
			}
			groups.forEach((path, rows) -> {
				if (!path.isEmpty()) {
					ordered.add(new Group(path, List.copyOf(rows)));
				}
			});
			blocks.add(new Block(modId, List.copyOf(ordered)));
		});
		blocks.sort(Comparator.comparing((Block block) -> nameOf.apply(block.modId()), String.CASE_INSENSITIVE_ORDER).thenComparing(Block::modId));
		return List.copyOf(blocks);
	}

	/** An add-on's page title as a name in a list: "RotP King Crimson Configuration" reads King Crimson. */
	public static String blockName(String title) {
		String name = TITLE_AFTER.matcher(TITLE_BEFORE.matcher(title.trim()).replaceFirst("")).replaceFirst("");
		name = TITLE_MOD.matcher(name).replaceFirst("").trim();
		if (name.isEmpty()) {
			return title.trim();
		}
		return name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT) + name.substring(name.offsetByCodePoints(0, 1));
	}

	/** The same from a mod's display name, for an add-on that has no title yet. */
	public static String blockNameFromDisplayName(String displayName) {
		return blockName(DISPLAY_NAME_AFTER.matcher(displayName.trim()).replaceFirst(""));
	}

	public static boolean isAddon(ConfigEntry entry) {
		return AddonConfigKeys.isAddonKey(entry.key());
	}

	/** A list whose items the server offers: one tick box per item. A list without a source stays the plain list editor. */
	public static boolean isChecklist(ConfigEntry entry) {
		return entry.kind() == Kind.LIST && entry.listChoices() != null;
	}

	public static boolean isStand(ConfigEntry entry) {
		return StandConfigKeys.isStandKey(entry.key());
	}

	/** A row of the core's own sections: neither an add-on's nor a Stand's. */
	public static boolean isCore(ConfigEntry entry) {
		return !isAddon(entry) && !isStand(entry);
	}

	/** The namespaces of the Stands the server listed: the mod ids whose settings join their Stands' blocks. */
	public static Set<String> standNamespaces(List<ConfigEntry> entries) {
		Set<String> namespaces = new LinkedHashSet<>();
		for (ConfigEntry entry : entries) {
			ResourceLocation id = isStand(entry) ? StandConfigKeys.standId(entry.key()) : null;
			if (id != null) {
				namespaces.add(id.getNamespace());
			}
		}
		return namespaces;
	}

	/**
	 * Whether the Stands page shows this server row: a Stand's own, or one of an add-on that registered Stands (COMMON and
	 * SERVER values; an add-on's CLIENT values stay on the Client page, which has its own store). Every other row is the
	 * Gameplay page's.
	 */
	public static boolean isOnStandsPage(ConfigEntry entry, Set<String> standNamespaces) {
		return isStand(entry) || isAddon(entry) && entry.side() == Side.SERVER
				&& standNamespaces.contains(AddonConfigKeys.modId(entry.key()));
	}

	/** The group of the Stands page a Stand's own row goes in. */
	public static String standGroup(ConfigEntry entry) {
		String field = StandConfigKeys.field(entry.key());
		if (field.startsWith(StandConfigKeys.SPECIAL)) {
			return "special";
		}
		return switch (field) {
			case "obtainable", "randomWeight" -> "obtaining";
			case "damageVsPlayers", "damageVsMobs", "cooldowns", "staminaCosts" -> "shares";
			case "miningTier", "miningStrength" -> "blocks";
			default -> "stats";
		};
	}

	public static boolean isPercent(ConfigEntry entry) {
		if (!isStand(entry)) {
			return false;
		}
		return switch (StandConfigKeys.field(entry.key())) {
			case "damageVsPlayers", "damageVsMobs", "cooldowns", "staminaCosts", "miningStrength" -> true;
			default -> false;
		};
	}

	/**
	 * The Stands of these rows, each with its own rows in the page's groups and, when the add-on that registered it has
	 * settings, those as one block of their own groups. The order is the server's (by id): the screen sorts by part and name.
	 */
	public static List<StandBlock> standBlocks(List<ConfigEntry> entries) {
		Map<ResourceLocation, Map<String, List<ConfigEntry>>> byStand = new LinkedHashMap<>();
		for (ConfigEntry entry : entries) {
			ResourceLocation id = isStand(entry) && isShown(entry) ? StandConfigKeys.standId(entry.key()) : null;
			if (id != null) {
				Map<String, List<ConfigEntry>> groups = byStand.computeIfAbsent(id, stand -> new LinkedHashMap<>());
				STAND_GROUPS.forEach(group -> groups.putIfAbsent(group, new ArrayList<>()));
				groups.get(standGroup(entry)).add(entry);
			}
		}
		Map<String, Long> standsPerMod = new LinkedHashMap<>();
		byStand.keySet().forEach(id -> standsPerMod.merge(id.getNamespace(), 1L, Long::sum));
		Map<String, Block> addonBlocks = new LinkedHashMap<>();
		for (Block block : blocks(entries.stream().filter(entry -> entry.side() == Side.SERVER).toList(), mod -> mod)) {
			addonBlocks.put(block.modId(), block);
		}
		List<StandBlock> stands = new ArrayList<>();
		byStand.forEach((id, groups) -> {
			List<Group> shown = new ArrayList<>();
			groups.forEach((group, rows) -> {
				if (!rows.isEmpty()) {
					shown.add(new Group(group, List.copyOf(rows)));
				}
			});
			stands.add(new StandBlock(id, List.copyOf(shown), addonBlocks.get(id.getNamespace()),
					standsPerMod.get(id.getNamespace()).intValue()));
		});
		return List.copyOf(stands);
	}

	/** What a search finds in a Stand's block: its shown name, its id, the name of its add-on. */
	public static boolean matchesSearch(String query, String standName, ResourceLocation standId, @Nullable String addonName) {
		String wanted = query.trim().toLowerCase(Locale.ROOT);
		if (wanted.isEmpty()) {
			return true;
		}
		return standName.toLowerCase(Locale.ROOT).contains(wanted) || standId.toString().contains(wanted)
				|| addonName != null && addonName.toLowerCase(Locale.ROOT).contains(wanted);
	}

	public static String leaf(String key) {
		int cut = Math.max(key.lastIndexOf('.'), key.indexOf('/'));
		return cut < 0 ? key : key.substring(cut + 1);
	}

	/** The entry's own key, or the screen's when the store names none. */
	public static String labelKey(ConfigEntry entry) {
		return entry.translationKey() != null ? entry.translationKey() : CLIENT_TEXT + entry.key();
	}

	public static String tooltipKey(ConfigEntry entry) {
		return labelKey(entry) + ".tooltip";
	}

	/** A readable name for a key that has no text yet: timeStopRangeChunks reads Time Stop Range Chunks. */
	public static String fallbackName(String key) {
		String leaf = leaf(key);
		StringBuilder name = new StringBuilder();
		for (int i = 0; i < leaf.length(); i++) {
			char c = leaf.charAt(i);
			if (i == 0) {
				name.append(Character.toUpperCase(c));
			}
			else if (c == '_') {
				name.append(' ');
			}
			else {
				// "King Crimson" keeps its one space and "maxHP" its letters together
				boolean startsWord = Character.isUpperCase(c) && leaf.charAt(i - 1) != ' ' && leaf.charAt(i - 1) != '_'
						&& (!Character.isUpperCase(leaf.charAt(i - 1)) || i + 1 < leaf.length() && Character.isLowerCase(leaf.charAt(i + 1)));
				if (startsWord) {
					name.append(' ');
				}
				name.append(c);
			}
		}
		return name.toString();
	}

	/** A readable name for a choice that has no text: TICK_RATE reads Tick rate. */
	public static String fallbackChoice(String choice) {
		String words = choice.replace('_', ' ').toLowerCase(Locale.ROOT);
		return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	public static String optionKey(ConfigEntry entry, String choice) {
		String lower = choice.toLowerCase(Locale.ROOT);
		if (isAddon(entry)) {
			return entry.translationKey() != null && entry.translationKey().endsWith("." + leaf(entry.key()))
					? entry.translationKey() + "." + lower
					: AddonConfigKeys.modId(entry.key()) + ".configuration." + leaf(entry.key()) + "." + lower;
		}
		return switch (entry.key()) {
			case "broadcasted.standSide" -> "jojo_ripples.config.client.option.stand_" + lower;
			case "standAura.mode" -> "jojo_ripples.stand_display.aura_mode." + lower;
			default -> OPTION_TEXT + leaf(entry.key()) + "." + lower;
		};
	}

	public static boolean isByDifficulty(ConfigEntry entry) {
		return !isAddon(entry) && BY_DIFFICULTY.contains(leaf(entry.key()));
	}

	public static boolean listItemsAreIds(ConfigEntry entry) {
		return !isAddon(entry) && "bannedStands".equals(leaf(entry.key()));
	}

	public static @Nullable String listRowLabel(ConfigEntry entry, int index) {
		return isByDifficulty(entry) && index < DIFFICULTY_LABELS.size() ? DIFFICULTY_LABELS.get(index) : null;
	}

	public static @Nullable String enabledBy(String key) {
		return ENABLED_BY.get(key);
	}

	public static @Nullable String iconName(String key) {
		return ICONS.get(key);
	}

	/** Whether the settings hold exactly these values right now; no preset is ever stored as chosen. */
	public static boolean holdsValues(Map<String, JsonElement> values, List<ConfigEntry> entries) {
		for (var value : values.entrySet()) {
			ConfigEntry entry = entries.stream().filter(candidate -> candidate.key().equals(value.getKey())).findFirst().orElse(null);
			if (entry == null || !entry.value().equals(value.getValue())) {
				return false;
			}
		}
		return !values.isEmpty();
	}
}
