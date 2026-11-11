package rotp.core.powersystem.standpower;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.config.JsonConfigurable;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.objects_java.DefaultedValue;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A Stand's rules beside its stats, from the "tuning" member of its data pack config
 * ({@code data/<namespace>/jojostandpowers/<stand>/tuning.json}, the file the settings screen's Stands page writes into the
 * world's jojoconfig pack): its damage to players and to mobs, its cooldowns and its stamina costs, each in percent of what
 * its own code asks, the highest block tier it breaks, the hardness it breaks in percent of its strength rule, and the special
 * values its own code reads ({@link StandSpecials}). Every default is the Stand as it is without the file.
 */
public final class StandTuning implements JsonConfigurable {
	public static final String MEMBER = "tuning";
	public static final String SPECIAL = "special";
	public static final int PERCENT_DEFAULT = 100;
	public static final int PERCENT_MAX = 500;

	public enum Percent {
		DAMAGE_VS_PLAYERS("damageVsPlayers"),
		DAMAGE_VS_MOBS("damageVsMobs"),
		COOLDOWNS("cooldowns"),
		STAMINA_COSTS("staminaCosts"),
		MINING_STRENGTH("miningStrength");

		public final String nameInJson;

		Percent(String nameInJson) {
			this.nameInJson = nameInJson;
		}
	}

	/** The harvest tier a Stand's blows break blocks up to: by its strength (the 1.16 rule), none at all, or a fixed tier. */
	public enum MiningTier {
		BY_STRENGTH, NONE, HAND, WOOD, STONE, IRON, DIAMOND, NETHERITE, ANY;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		@Nullable
		public static MiningTier byId(@Nullable String id) {
			for (MiningTier tier : values()) {
				if (tier.id().equals(id)) {
					return tier;
				}
			}
			return null;
		}
	}

	public static final String MINING_TIER = "miningTier";

	private final EnumMap<Percent, DefaultedValue.Int> percents = new EnumMap<>(Percent.class);
	private final DefaultedValue<MiningTier> miningTier = new DefaultedValue<>(MiningTier.BY_STRENGTH);
	private final Map<String, Double> special = new LinkedHashMap<>();

	public StandTuning() {
		for (Percent field : Percent.values()) {
			percents.put(field, new DefaultedValue.Int(PERCENT_DEFAULT));
		}
	}

	private static volatile boolean bypassed;

	@Nullable
	public static StandTuning of(@Nullable StandPower power) {
		return of(power != null ? power.getPowerType() : null);
	}

	/** The Stand's rules, or null when it has none to apply: no Stand. */
	@Nullable
	public static StandTuning of(@Nullable StandType type) {
		return type != null && !bypassed ? type.getTuning() : null;
	}

	/** The game test that compares play with these rules at their defaults and without them turns them off for "without". */
	@ApiStatus.Internal
	public static void bypassForTest(boolean bypass) {
		bypassed = bypass;
	}

	public int percent(Percent field) {
		return percents.get(field).value;
	}

	public MiningTier miningTier() {
		return miningTier.value;
	}

	/** The special value's override in this Stand's file, or null when the file has none. */
	@Nullable
	public Double special(String key) {
		return special.get(key);
	}

	/** What a hit of this Stand deals to this target, from what its own code dealt. */
	public float damage(float amount, @Nullable LivingEntity target) {
		int share = percent(target instanceof Player ? Percent.DAMAGE_VS_PLAYERS : Percent.DAMAGE_VS_MOBS);
		return share == PERCENT_DEFAULT ? amount : amount * share / 100F;
	}

	public int cooldown(int ticks) {
		int share = percent(Percent.COOLDOWNS);
		return share == PERCENT_DEFAULT || ticks <= 0 ? ticks : (int) Math.round(ticks * (double) share / 100);
	}

	public float staminaCost(float amount) {
		int share = percent(Percent.STAMINA_COSTS);
		return share == PERCENT_DEFAULT || amount <= 0 ? amount : amount * share / 100F;
	}

	/** The hardness limit of the 1.16 strength rule, e^(strength / 4), in this Stand's share. */
	public double miningHardnessLimit(double limit) {
		int share = percent(Percent.MINING_STRENGTH);
		return share == PERCENT_DEFAULT ? limit : limit * share / 100;
	}

	public boolean isDefault() {
		return percents.values().stream().allMatch(value -> value.value == PERCENT_DEFAULT)
				&& miningTier.value == MiningTier.BY_STRENGTH && special.isEmpty();
	}

	@Override
	public JsonElement makeConfigTemplate() {
		JsonObject json = new JsonObject();
		for (Percent field : Percent.values()) {
			json.addProperty(field.nameInJson, percents.get(field).defaultValue);
		}
		json.addProperty(MINING_TIER, miningTier.defaultValue.id());
		return json;
	}

	/** A value out of range is brought into it, an unknown tier or a wrong type is left at its default. */
	@Override
	public void applyConfig(JsonElement json) {
		if (!json.isJsonObject()) {
			return;
		}
		JsonObject config = json.getAsJsonObject();
		for (Percent field : Percent.values()) {
			JsonPrimitive value = number(config.get(field.nameInJson));
			if (value != null) {
				percents.get(field).value = Mth.clamp((int) Math.round(value.getAsDouble()), 0, PERCENT_MAX);
			}
		}
		JsonElement tier = config.get(MINING_TIER);
		if (tier != null && tier.isJsonPrimitive() && MiningTier.byId(tier.getAsString()) != null) {
			miningTier.value = MiningTier.byId(tier.getAsString());
		}
		JsonElement specials = config.get(SPECIAL);
		if (specials != null && specials.isJsonObject()) {
			for (var entry : specials.getAsJsonObject().entrySet()) {
				JsonPrimitive value = number(entry.getValue());
				if (value != null && Double.isFinite(value.getAsDouble())) {
					special.put(entry.getKey(), value.getAsDouble());
				}
			}
		}
	}

	@Override
	public void restoreDefaults() {
		percents.values().forEach(DefaultedValue.Int::reset);
		miningTier.reset();
		special.clear();
	}

	@Nullable
	private static JsonPrimitive number(@Nullable JsonElement element) {
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
				? element.getAsJsonPrimitive() : null;
	}
}
