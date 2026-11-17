package rotp.core.client.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import net.minecraft.resources.ResourceLocation;
import rotp.core.client.ui.StandStatsRenderer.CosmeticStandStats;
import rotp.core.client.ui.StandStatsRenderer.HexagonStandStat;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;

final class CanonStandPanels {
	private static final String RESOURCE = "/assets/jojo_ripples/stand_panel_ranks.json";

	private CanonStandPanels() {}

	@Nullable
	static CosmeticStandStats find(ResourceLocation id) {
		return Bundled.PANELS.get(id);
	}

	private static class Bundled {
		private static final Map<ResourceLocation, CosmeticStandStats> PANELS = load();
	}

	private static Map<ResourceLocation, CosmeticStandStats> load() {
		InputStream resource = CanonStandPanels.class.getResourceAsStream(RESOURCE);
		if (resource == null) {
			throw new IllegalStateException("Missing canonical Stand panel table: " + RESOURCE);
		}
		try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
			return read(reader);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not read canonical Stand panel table: " + RESOURCE, exception);
		}
	}

	static Map<ResourceLocation, CosmeticStandStats> read(Reader reader) {
		Map<ResourceLocation, CosmeticStandStats> panels = new LinkedHashMap<>();
		HexagonStandStat[] axes = HexagonStandStat.values();
		try {
			JsonReader json = new JsonReader(reader);
			json.beginObject();
			while (json.hasNext()) {
				String key = json.nextName();
				ResourceLocation id = ResourceLocation.tryParse(key);
				if (id == null || !key.equals(id.toString())) {
					throw new JsonParseException("Invalid canonical Stand id: " + key);
				}
				if (panels.containsKey(id)) {
					throw new JsonParseException("Duplicate canonical Stand panel: " + key);
				}
				List<Rank> ranks = new ArrayList<>(axes.length);
				json.beginArray();
				while (json.hasNext()) {
					if (ranks.size() == axes.length || json.peek() != JsonToken.STRING) {
						throw new JsonParseException("Expected six rank strings for " + key);
					}
					String symbol = json.nextString();
					Rank rank = Rank.fromSymbol(symbol);
					if (rank == null) {
						throw new JsonParseException("Invalid rank " + symbol + " for " + key + " " + axes[ranks.size()]);
					}
					ranks.add(rank);
				}
				json.endArray();
				if (ranks.size() != axes.length) {
					throw new JsonParseException("Expected six rank strings for " + key);
				}
				panels.put(id, new CanonPanel(ranks));
			}
			json.endObject();
			if (json.peek() != JsonToken.END_DOCUMENT) {
				throw new JsonParseException("Unexpected content after the canonical Stand panel table");
			}
		}
		catch (IOException | IllegalStateException exception) {
			throw new JsonParseException("Invalid canonical Stand panel table", exception);
		}
		return Map.copyOf(panels);
	}

	private static final class CanonPanel extends CosmeticStandStats {
		private final List<Rank> ranks;

		private CanonPanel(List<Rank> ranks) {
			this.ranks = List.copyOf(ranks);
		}

		@Override
		public float statConvertedValue(HexagonStandStat stat, StandPower power, StandStats stats, float development) {
			return ranks.get(stat.ordinal()).value;
		}

		@Override
		public String statRankLetter(HexagonStandStat stat, StandPower power, double convertedValue) {
			return ranks.get(stat.ordinal()).symbol;
		}
	}

	private enum Rank {
		EMPTY("\u2205", 0), E("E", 1), D("D", 2), C("C", 3), B("B", 4), A("A", 5),
		UNKNOWN("?", 0), INFINITY("\u221e", 6);

		private final String symbol;
		private final float value;

		Rank(String symbol, float value) {
			this.symbol = symbol;
			this.value = value;
		}

		@Nullable
		static Rank fromSymbol(String symbol) {
			for (Rank rank : values()) {
				if (rank.symbol.equals(symbol)) {
					return rank;
				}
			}
			return null;
		}
	}
}
