package rotp.core.config.editor.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import rotp.core.JojoModConfig;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.ConfigEntry.Bounds;
import rotp.core.config.editor.ConfigEntry.Kind;
import rotp.core.config.editor.ConfigEntry.Side;

/**
 * What the settings screen makes of a catalog entry: which widget, which section, which text. It reads only the entry,
 * so a key added to either store gets a working row without code here; the lists below only give known keys a place.
 */
public final class ConfigWidgetRules {
	public enum WidgetKind { TOGGLE, SLIDER, NUMBER, CYCLE, TEXT, LIST }

	public record Section(String id, List<ConfigEntry> entries) {}

	private record SectionRule(String id, List<String> leaves, @Nullable String prefix) {}

	public static final String OTHER_SECTION = "other";
	public static final String SECTION_TEXT = "jojo_ripples.config.section.";
	public static final String OPTION_TEXT = "jojo_ripples.config.option.";
	public static final String CLIENT_TEXT = "jojo_ripples.config.client.";

	// A switch that does nothing would lie to the player. Nothing reads the first two: the hotbar buttons that set them are
	// commented out in GuiEventHandler. The third belongs to the unfinished test key: LmbRmbKeyMapping.setKey writes it
	// from the key binds screen, and all it gates is a test sound (InputHandler.doTheThing).
	private static final Set<String> HIDDEN = Set.of("toggleLmbHotbar", "toggleRmbHotbar", "poseOnLmbRmb");

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
			section("time_stop", "timeStopChunkRange", "timeStopChargeSeconds", "timeStopDamageMultiplier",
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

	private ConfigWidgetRules() {}

	private static SectionRule section(String id, String... leaves) {
		return new SectionRule(id, List.of(leaves), null);
	}

	public static boolean isShown(ConfigEntry entry) {
		return !HIDDEN.contains(entry.key());
	}

	public static Set<String> hiddenKeys() {
		return HIDDEN;
	}

	public static WidgetKind widgetKind(ConfigEntry entry) {
		return switch (entry.kind()) {
			case BOOLEAN -> WidgetKind.TOGGLE;
			case ENUM -> WidgetKind.CYCLE;
			case LIST -> WidgetKind.LIST;
			case STRING -> entry.choices().isEmpty() ? WidgetKind.TEXT : WidgetKind.CYCLE;
			case INTEGER, DECIMAL -> sliderFits(entry) ? WidgetKind.SLIDER : WidgetKind.NUMBER;
		};
	}

	// A slider needs a range a hand can place a value in. A multiplier of 0 to 5000 whose useful values are 1 and 1.5 does not.
	private static boolean sliderFits(ConfigEntry entry) {
		Bounds bounds = entry.bounds();
		if (bounds == null || bounds.maximum() <= bounds.minimum()) {
			return false;
		}
		double range = bounds.maximum() - bounds.minimum();
		return entry.kind() == Kind.INTEGER ? range <= 200 : range <= 10 || entry.side() == Side.CLIENT;
	}

	public static BigDecimal sliderStep(ConfigEntry entry) {
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
		if (sliderStep(entry).compareTo(BigDecimal.ONE) >= 0) {
			return new BigDecimal(value.getAsString()).setScale(0, RoundingMode.HALF_UP).toPlainString();
		}
		return formatNumber(entry.kind(), value);
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
		List<ConfigEntry> shown = entries.stream().filter(ConfigWidgetRules::isShown).toList();
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

	public static String leaf(String key) {
		int dot = key.lastIndexOf('.');
		return dot < 0 ? key : key.substring(dot + 1);
	}

	/** The entry's own key, or the screen's when the store names none. */
	public static String labelKey(ConfigEntry entry) {
		return entry.translationKey() != null ? entry.translationKey() : CLIENT_TEXT + entry.key();
	}

	public static String tooltipKey(ConfigEntry entry) {
		return labelKey(entry) + ".tooltip";
	}

	/** A readable name for a key that has no text yet: timeStopChunkRange reads Time Stop Chunk Range. */
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
				if (Character.isUpperCase(c)) {
					name.append(' ');
				}
				name.append(c);
			}
		}
		return name.toString();
	}

	public static String optionKey(ConfigEntry entry, String choice) {
		String lower = choice.toLowerCase(Locale.ROOT);
		return switch (entry.key()) {
			case "broadcasted.standSide" -> "jojo_ripples.config.client.option.stand_" + lower;
			case "standAura.mode" -> "jojo_ripples.stand_display.aura_mode." + lower;
			default -> OPTION_TEXT + leaf(entry.key()) + "." + lower;
		};
	}

	public static boolean isByDifficulty(ConfigEntry entry) {
		return BY_DIFFICULTY.contains(leaf(entry.key()));
	}

	public static boolean listItemsAreIds(ConfigEntry entry) {
		return "bannedStands".equals(leaf(entry.key()));
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
