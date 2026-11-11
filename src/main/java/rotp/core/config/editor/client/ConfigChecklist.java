package rotp.core.config.editor.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import rotp.core.config.editor.ConfigChoices.Choice;
import rotp.core.config.editor.ConfigEntry;

/**
 * The tick boxes of a list whose items the server offers: which are ticked, which ticked one the server no longer offers,
 * the order the card shows and the value it writes. The order is fixed when the card opens, so a row never moves under
 * the hand: items the server no longer offers first (they want a look), then the ticked ones, then the rest, each in the
 * server's order. The value written keeps the server's order, then what it no longer offers in the stored order.
 */
public final class ConfigChecklist {
	public record Item(String id, String name, @Nullable String translationKey, boolean offered) {}

	private final List<String> offeredOrder = new ArrayList<>();
	private final List<Item> items = new ArrayList<>();
	private final Set<String> ticked = new LinkedHashSet<>();

	public ConfigChecklist(ConfigEntry entry) {
		List<Choice> offered = entry.listChoices() != null ? entry.listChoices() : List.of();
		offered.forEach(choice -> offeredOrder.add(choice.id()));
		Set<String> stored = ids(entry.value());
		for (String id : stored) {
			if (!offeredOrder.contains(id)) {
				items.add(gone(id));
			}
		}
		offered.stream().filter(choice -> stored.contains(choice.id())).forEach(choice -> items.add(item(choice)));
		offered.stream().filter(choice -> !stored.contains(choice.id())).forEach(choice -> items.add(item(choice)));
		ticked.addAll(stored);
	}

	private static Item item(Choice choice) {
		return new Item(choice.id(), choice.name(), choice.translationKey(), true);
	}

	private static Item gone(String id) {
		return new Item(id, id, null, false);
	}

	public List<Item> items() {
		return List.copyOf(items);
	}

	/** The items whose shown name or id has the query in it, any case; all for an empty query. */
	public List<Item> shown(String query, Function<Item, String> shownName) {
		String wanted = query.trim().toLowerCase(Locale.ROOT);
		if (wanted.isEmpty()) {
			return items();
		}
		return items.stream().filter(item -> shownName.apply(item).toLowerCase(Locale.ROOT).contains(wanted)
				|| item.id().toLowerCase(Locale.ROOT).contains(wanted)).toList();
	}

	public boolean isTicked(String id) {
		return ticked.contains(id);
	}

	public void toggle(String id) {
		if (!ticked.remove(id) && items.stream().anyMatch(item -> item.id().equals(id))) {
			ticked.add(id);
		}
	}

	/** Ticks what the value names and nothing else (Reset to default); an item it names that the server does not offer joins the top. */
	public void setTo(JsonElement value) {
		ticked.clear();
		Set<String> wanted = ids(value);
		int at = 0;
		for (String id : wanted) {
			if (items.stream().noneMatch(item -> item.id().equals(id))) {
				items.add(at++, gone(id));
			}
		}
		ticked.addAll(wanted);
	}

	public int tickedCount() {
		return ticked.size();
	}

	public int offeredCount() {
		return offeredOrder.size();
	}

	public JsonArray value() {
		JsonArray array = new JsonArray(ticked.size());
		offeredOrder.stream().filter(ticked::contains).forEach(array::add);
		ticked.stream().filter(id -> !offeredOrder.contains(id)).forEach(array::add);
		return array;
	}

	/** The same items ticked as the value names, in any order: a hand-ordered file is not a change. */
	public boolean sameAs(JsonElement value) {
		return ticked.equals(ids(value));
	}

	/** How many items a checklist entry has ticked, and how many the server offers: the row's summary. */
	public static int tickedCount(ConfigEntry entry) {
		return ids(entry.value()).size();
	}

	public static int offeredCount(ConfigEntry entry) {
		return entry.listChoices() != null ? entry.listChoices().size() : 0;
	}

	private static Set<String> ids(JsonElement value) {
		Set<String> ids = new LinkedHashSet<>();
		if (value != null && value.isJsonArray()) {
			for (JsonElement item : value.getAsJsonArray()) {
				if (item.isJsonPrimitive()) {
					ids.add(item.getAsString());
				}
			}
		}
		return ids;
	}
}
