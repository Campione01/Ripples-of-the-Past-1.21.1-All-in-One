package rotp.core.config.editor.client;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import rotp.core.client.shader.ModShaders;
import rotp.core.config.client.ClientModSettings;
import rotp.core.config.client.ClientModSettings.Settings;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.config.client.StandAuraSettings;
import rotp.core.config.editor.ConfigEditResult;
import rotp.core.config.editor.ConfigEditResult.Change;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.ConfigEntry.Bounds;
import rotp.core.config.editor.ConfigEntry.Kind;
import rotp.core.config.editor.ConfigEntry.Restart;
import rotp.core.config.editor.ConfigEntry.Side;
import rotp.core.network.c2s.ClBroadcastedModSettingsPacket;

/** Validated access to the client settings' persisted JSON field paths. */
public final class ClientConfigEditor {
	private static final String HUD = "hud";
	private static final String CONTROLS = "controls";
	private static final String DISPLAY = "standDisplay";
	private static final String EFFECTS = "effects";
	private static final String HAMON = "hamonAura";
	private static final String STAND_AURA = "standAura";
	private static final String BROADCAST = "broadcasted";
	private static final String CLIENT_LABEL = "jojo_ripples.config.client.";
	private static final String DISPLAY_LABEL = "jojo_ripples.stand_display.";
	private static final String TOGGLE_LABEL = "jojo_ripples.stand_toggles.";
	private static final Settings DEFAULTS = new Settings();
	private static final List<Field> FIELDS = fields();
	private static final Map<String, Field> BY_KEY = FIELDS.stream()
			.collect(Collectors.toUnmodifiableMap(Field::key, Function.identity()));

	private ClientConfigEditor() {}

	public static List<ConfigEntry> entries() {
		if (ClientModSettings.getInstance() == null) {
			return List.of();
		}
		Settings settings = ClientModSettings.getSettingsReadOnly();
		return FIELDS.stream().map(field -> field.entry(settings)).toList();
	}

	/** Every setting at its default, for what must not depend on a loaded settings file. */
	public static List<ConfigEntry> catalog() {
		return FIELDS.stream().map(field -> field.entry(DEFAULTS)).toList();
	}

	public static ConfigEditResult set(String key, JsonElement value) {
		ClientModSettings store = ClientModSettings.getInstance();
		if (store == null) {
			return ConfigEditResult.error(Status.UNAVAILABLE, key);
		}
		Field field = key == null ? null : BY_KEY.get(key);
		if (field == null) {
			return ConfigEditResult.error(Status.UNKNOWN_KEY, key);
		}
		JsonElement checked = field.validate(value);
		if (checked == null) {
			return ConfigEditResult.error(Status.INVALID_VALUE, key);
		}
		return apply(store, key, List.of(new Assignment(field, checked)));
	}

	public static ConfigEditResult reset(String key) {
		ClientModSettings store = ClientModSettings.getInstance();
		if (store == null) {
			return ConfigEditResult.error(Status.UNAVAILABLE, key);
		}
		Field field = key == null ? null : BY_KEY.get(key);
		if (field == null) {
			return ConfigEditResult.error(Status.UNKNOWN_KEY, key);
		}
		return apply(store, key, List.of(field.reset()));
	}

	public static ConfigEditResult resetGroup(String group) {
		ClientModSettings store = ClientModSettings.getInstance();
		if (store == null) {
			return ConfigEditResult.error(Status.UNAVAILABLE, group);
		}
		List<Assignment> resets = FIELDS.stream().filter(field -> field.group.equals(group)).map(Field::reset).toList();
		if (resets.isEmpty()) {
			return ConfigEditResult.error(Status.UNKNOWN_GROUP, group);
		}
		return apply(store, group, resets);
	}

	private static ConfigEditResult apply(ClientModSettings store, String key, List<Assignment> assignments) {
		Settings settings = ClientModSettings.getSettingsReadOnly();
		List<Assignment> pending = new ArrayList<>();
		List<Change> changes = new ArrayList<>();
		boolean broadcast = false;
		for (Assignment assignment : assignments) {
			Field field = assignment.field;
			JsonElement before = field.getter.apply(settings);
			if (!before.equals(assignment.value)) {
				pending.add(assignment);
				changes.add(new Change(field.key, before, assignment.value, Restart.NONE));
				broadcast |= field.synced();
			}
		}
		if (pending.isEmpty()) {
			return ConfigEditResult.ok(List.of());
		}
		if (!store.tryEditSettings(candidate -> pending.forEach(assignment -> assignment.apply(candidate)))) {
			return ConfigEditResult.error(Status.SAVE_FAILED, key);
		}
		if (broadcast && FMLEnvironment.dist == Dist.CLIENT) {
			ConnectedClient.broadcast(settings.broadcasted);
		}
		if (FMLEnvironment.dist == Dist.CLIENT && !settings.resolveShaders
				&& changes.stream().anyMatch(change -> "resolveShaders".equals(change.key()))) {
			ConnectedClient.stopResolveShader();
		}
		return ConfigEditResult.ok(changes);
	}

	private static List<Field> fields() {
		List<Field> fields = new ArrayList<>(List.of(
				decimal("standStatsTranslucency", HUD, null, 0.1, 1.0,
						s -> s.standStatsTranslucency, (s, v) -> s.standStatsTranslucency = v),
				bool("standStatsInvertBnW", HUD, null, s -> s.standStatsInvertBnW, (s, v) -> s.standStatsInvertBnW = v),
				bool("showLockedSlots", HUD, CLIENT_LABEL + "showLockedSlots", s -> s.showLockedSlots, (s, v) -> s.showLockedSlots = v),
				bool("compactStandHotbar", HUD, DISPLAY_LABEL + "compact_hotbar", s -> s.compactStandHotbar, (s, v) -> s.compactStandHotbar = v),
				bool("standExpBar", HUD, CLIENT_LABEL + "standExpBar", s -> s.standExpBar, (s, v) -> s.standExpBar = v),

				bool("abilitySelectionWheel", CONTROLS, CLIENT_LABEL + "abilitySelectionWheel", s -> s.abilitySelectionWheel, (s, v) -> s.abilitySelectionWheel = v),
				bool("abilitySelectionScroll", CONTROLS, CLIENT_LABEL + "abilitySelectionScroll", s -> s.abilitySelectionScroll, (s, v) -> s.abilitySelectionScroll = v),
				bool("releaseToUseAbility", CONTROLS, CLIENT_LABEL + "releaseToUseAbility", s -> s.releaseToUseAbility, (s, v) -> s.releaseToUseAbility = v),
				bool("poseOnLmbRmb", CONTROLS, null, s -> s.poseOnLmbRmb, (s, v) -> s.poseOnLmbRmb = v),
				bool("toggleLmbHotbar", CONTROLS, null, s -> s.toggleLmbHotbar, (s, v) -> s.toggleLmbHotbar = v),
				bool("toggleRmbHotbar", CONTROLS, null, s -> s.toggleRmbHotbar, (s, v) -> s.toggleRmbHotbar = v),
				bool("toggleDisableHotbars", CONTROLS, null, s -> s.toggleDisableHotbars, (s, v) -> s.toggleDisableHotbars = v),

				bool("standMotionTilt", DISPLAY, CLIENT_LABEL + "standMotionTilt", s -> s.standMotionTilt, (s, v) -> s.standMotionTilt = v),
				bool("standAimMarker", DISPLAY, CLIENT_LABEL + "standAimMarker", s -> s.standAimMarker, (s, v) -> s.standAimMarker = v),
				decimal("standTransparency", DISPLAY, DISPLAY_LABEL + "own_transparency", 0, 100,
						s -> s.standTransparency, (s, v) -> s.standTransparency = v),
				decimal("standOthersTransparency", DISPLAY, DISPLAY_LABEL + "other_transparency", 0, 100,
						s -> s.standOthersTransparency, (s, v) -> s.standOthersTransparency = v),
				bool("classicStandObstruction", DISPLAY, DISPLAY_LABEL + "classic_obstruction", s -> s.classicStandObstruction, (s, v) -> s.classicStandObstruction = v),
				bool("standOutline", DISPLAY, DISPLAY_LABEL + "classic_outline", s -> s.standOutline, (s, v) -> s.standOutline = v),

				bool("resolveShaders", EFFECTS, CLIENT_LABEL + "resolveShaders", s -> s.resolveShaders, (s, v) -> s.resolveShaders = v),
				bool("timeStopAnimation", EFFECTS, CLIENT_LABEL + "timeStopAnimation", s -> s.timeStopAnimation, (s, v) -> s.timeStopAnimation = v),
				bool("menacingParticles", EFFECTS, CLIENT_LABEL + "menacingParticles", s -> s.menacingParticles, (s, v) -> s.menacingParticles = v),
				bool("ownAfkMenacingFirstPerson", EFFECTS, CLIENT_LABEL + "ownAfkMenacingFirstPerson", s -> s.ownAfkMenacingFirstPerson, (s, v) -> s.ownAfkMenacingFirstPerson = v),
				bool("characterVoiceLines", EFFECTS, CLIENT_LABEL + "characterVoiceLines", s -> s.characterVoiceLines, (s, v) -> s.characterVoiceLines = v),
				bool("screenSplash", EFFECTS, CLIENT_LABEL + "screenSplash", s -> s.screenSplash, (s, v) -> s.screenSplash = v),

				bool("thirdPersonHamonAura", HAMON, CLIENT_LABEL + "thirdPersonHamonAura", s -> s.thirdPersonHamonAura, (s, v) -> s.thirdPersonHamonAura = v),
				bool("firstPersonHamonAura", HAMON, CLIENT_LABEL + "firstPersonHamonAura", s -> s.firstPersonHamonAura, (s, v) -> s.firstPersonHamonAura = v),
				bool("hamonAuraBlur", HAMON, CLIENT_LABEL + "hamonAuraBlur", s -> s.hamonAuraBlur, (s, v) -> s.hamonAuraBlur = v),

				bool("standAura.enabled", STAND_AURA, DISPLAY_LABEL + "aura_enabled", s -> s.standAura.enabled, (s, v) -> s.standAura.enabled = v),
				enumeration("standAura.mode", STAND_AURA, DISPLAY_LABEL + "aura_mode", StandAuraSettings.Mode.class,
						s -> s.standAura.mode, (s, v) -> s.standAura.mode = v)));
		for (StandAuraSettings.Parameter parameter : StandAuraSettings.PARAMETERS) {
			String name = parameter.name();
			fields.add(decimal("standAura." + name, STAND_AURA, "jojo_ripples.stand_aura.parameter." + name,
					decimalBound(parameter.minimum()), decimalBound(parameter.maximum()),
					s -> parameter.get(s.standAura), (s, v) -> parameter.set(s.standAura, v)));
		}
		fields.addAll(List.of(
				enumeration("broadcasted.standSide", BROADCAST, CLIENT_LABEL + "standSide", HumanoidArm.class,
						s -> s.broadcasted.standSide, (s, v) -> s.broadcasted.standSide = v),
				bool("broadcasted.vampireGlowingEyes", BROADCAST, CLIENT_LABEL + "vampireGlowingEyes", s -> s.broadcasted.vampireGlowingEyes, (s, v) -> s.broadcasted.vampireGlowingEyes = v),
				bool("broadcasted.noStandAbilityCooldown", BROADCAST, CLIENT_LABEL + "noStandAbilityCooldown", s -> s.broadcasted.noStandAbilityCooldown, (s, v) -> s.broadcasted.noStandAbilityCooldown = v),
				bool("broadcasted.standAttackTargetLock", BROADCAST, DISPLAY_LABEL + "attack_target_lock", s -> s.broadcasted.standAttackTargetLock, (s, v) -> s.broadcasted.standAttackTargetLock = v),
				bool("broadcasted.standAimAssist", BROADCAST, DISPLAY_LABEL + "aim_assist", s -> s.broadcasted.standAimAssist, (s, v) -> s.broadcasted.standAimAssist = v),
				bool("broadcasted.standBreaksBlocks", BROADCAST, TOGGLE_LABEL + "break_blocks", s -> s.broadcasted.standBreaksBlocks, (s, v) -> s.broadcasted.standBreaksBlocks = v),
				bool("broadcasted.standPicksUpItems", BROADCAST, TOGGLE_LABEL + "pick_up_items", s -> s.broadcasted.standPicksUpItems, (s, v) -> s.broadcasted.standPicksUpItems = v),
				bool("broadcasted.standMovesBeyondEffectiveRange", BROADCAST, TOGGLE_LABEL + "beyond_effective_range", s -> s.broadcasted.standMovesBeyondEffectiveRange, (s, v) -> s.broadcasted.standMovesBeyondEffectiveRange = v)));
		return List.copyOf(fields);
	}

	private static double decimalBound(float value) {
		return Double.parseDouble(Float.toString(value));
	}

	private static Field bool(String key, String group, @Nullable String label,
			Function<Settings, Boolean> getter, BiConsumer<Settings, Boolean> setter) {
		return new Field(key, group, Kind.BOOLEAN, null, List.of(), label,
				s -> new JsonPrimitive(getter.apply(s)), (s, v) -> setter.accept(s, v.getAsBoolean()));
	}

	private static Field decimal(String key, String group, @Nullable String label, double min, double max,
			Function<Settings, Float> getter, BiConsumer<Settings, Float> setter) {
		return new Field(key, group, Kind.DECIMAL, new Bounds(min, max), List.of(), label,
				s -> new JsonPrimitive(getter.apply(s)), (s, v) -> setter.accept(s, v.getAsFloat()));
	}

	private static <E extends Enum<E>> Field enumeration(String key, String group, String label, Class<E> type,
			Function<Settings, E> getter, BiConsumer<Settings, E> setter) {
		List<String> choices = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
		return new Field(key, group, Kind.ENUM, null, choices, label, s -> {
			E value = getter.apply(s);
			return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value.name());
		}, (s, v) -> setter.accept(s, Enum.valueOf(type, v.getAsString())));
	}

	private record Assignment(Field field, JsonElement value) {
		void apply(Settings settings) {
			field.setter.accept(settings, value);
		}
	}

	private record Field(String key, String group, Kind kind, @Nullable Bounds bounds, List<String> choices,
			@Nullable String label, Function<Settings, JsonElement> getter, BiConsumer<Settings, JsonElement> setter) {
		boolean synced() {
			return BROADCAST.equals(group);
		}

		ConfigEntry entry(Settings settings) {
			return new ConfigEntry(key, group, Side.CLIENT, kind, null, getter.apply(DEFAULTS), getter.apply(settings),
					bounds, null, choices, Restart.NONE, synced(), label);
		}

		Assignment reset() {
			return new Assignment(this, getter.apply(DEFAULTS));
		}

		@Nullable JsonElement validate(@Nullable JsonElement value) {
			if (!(value instanceof JsonPrimitive primitive)) {
				return null;
			}
			return switch (kind) {
				case BOOLEAN -> primitive.isBoolean() ? primitive : null;
				case ENUM -> primitive.isString() && choices.contains(primitive.getAsString()) ? primitive : null;
				case DECIMAL -> validateDecimal(primitive);
				default -> null;
			};
		}

		private JsonElement validateDecimal(JsonPrimitive value) {
			if (!value.isNumber()) {
				return null;
			}
			try {
				BigDecimal number = value.getAsBigDecimal();
				if (number.compareTo(BigDecimal.valueOf(bounds.minimum())) >= 0
						&& number.compareTo(BigDecimal.valueOf(bounds.maximum())) <= 0) {
					return new JsonPrimitive(number.floatValue());
				}
			}
			catch (NumberFormatException ignored) {
				// Non-finite numbers have no JSON decimal representation.
			}
			return null;
		}
	}

	private static class ConnectedClient {
		static void broadcast(PlayerClientBroadcastedSettings settings) {
			Minecraft minecraft = Minecraft.getInstance();
			var connection = minecraft.getConnection();
			if (minecraft.player != null && connection != null
					&& connection.hasChannel(new ClBroadcastedModSettingsPacket(settings).type())) {
				settings.broadcastToServer();
			}
		}

		// the old settings button did this: a shader already running would otherwise stay on until it ended by itself
		static void stopResolveShader() {
			ModShaders shaders = ModShaders.getInstance();
			if (shaders != null && shaders.resolveShaderManager != null) {
				shaders.resolveShaderManager.stopResolveShader();
			}
		}
	}
}
