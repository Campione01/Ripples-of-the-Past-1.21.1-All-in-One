package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.core.JojoMod;
import rotp.core.init.CreativeTabSections;
import rotp.core.init.CreativeTabSections.Header;
import rotp.core.init.ModItems;
import rotp.core.init.SectionedCreativeTab;
import rotp.core.init.SectionedCreativeTab.ItemSection;
import rotp.core.mechanics.standdisc.StandDiscItem;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The main creative tab in sections, built as the client builds it, and the round's text in all 12 core languages. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeTabSectionsGameTests {
	private static final String[] LOCALES = {
		"en_us", "en_pt", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br", "ru_ru", "uk_ua", "zh_cn", "zh_tw"
	};
	private static final List<String> NEW_TEXT = List.of(
			"jojo_ripples.menu.stand.toggles", "ripples_hud.stand_toggles", "ripples_hud.stand_toggles.desc",
			"jojo_ripples.creative_tab.stand_discs", "jojo_ripples.creative_tab.clothes",
			"jojo_ripples.stand_toggles.break_blocks", "jojo_ripples.stand_toggles.break_blocks.tooltip",
			"jojo_ripples.stand_toggles.pick_up_items", "jojo_ripples.stand_toggles.pick_up_items.tooltip",
			"jojo_ripples.stand_toggles.beyond_effective_range", "jojo_ripples.stand_toggles.beyond_effective_range.tooltip",
			"jojo_ripples.key.stand_cancel_action", "jojo_ripples.key.stand_cancel_action.desc",
			"jojo_ripples.key.stand_item_toss", "jojo_ripples.key.stand_item_toss.desc",
			"jojo_ripples.key.stand_item_swap_hands", "jojo_ripples.key.stand_item_swap_hands.desc",
			"jojo_ripples.key.stand_toggle_break_blocks", "jojo_ripples.key.stand_toggle_break_blocks.desc",
			"jojo_ripples.key.stand_toggle_pick_up_items", "jojo_ripples.key.stand_toggle_pick_up_items.desc",
			"jojo_ripples.key.stand_toggle_beyond_effective_range", "jojo_ripples.key.stand_toggle_beyond_effective_range.desc");

	private CreativeTabSectionsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theMainTabHasItemsThenDiscsThenClothesEachOnItsOwnRows(GameTestHelper helper) {
		CreativeModeTab built = ModItems.MAIN_TAB.get();
		built.buildContents(new CreativeModeTab.ItemDisplayParameters(
				helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess()));
		helper.assertTrue(built instanceof SectionedCreativeTab, "the main tab is not sectioned: " + built.getClass());
		SectionedCreativeTab tab = (SectionedCreativeTab) built;
		List<ItemStack> slots = new ArrayList<>(tab.getDisplayItems());
		List<Header<Component>> headers = tab.getHeaders();
		helper.assertTrue(headers.size() == 2, "the tab has " + headers.size() + " section names, wanted discs and clothes");
		helper.assertTrue(key(headers.get(0).name()).equals("jojo_ripples.creative_tab.stand_discs")
				&& key(headers.get(1).name()).equals("jojo_ripples.creative_tab.clothes"), "the sections are not discs then clothes");

		int discsFrom = headers.get(0).row() * CreativeTabSections.ROW_LENGTH;
		int clothesFrom = headers.get(1).row() * CreativeTabSections.ROW_LENGTH;
		for (int i = 0; i < slots.size(); i++) {
			ItemStack stack = slots.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			ItemSection section = SectionedCreativeTab.sectionOf(stack);
			ItemSection place = i < discsFrom ? ItemSection.ITEMS : i < clothesFrom ? ItemSection.STAND_DISCS : ItemSection.CLOTHES;
			helper.assertTrue(section == place, stack + " is in slot " + i + " of the " + place + " section, it belongs to " + section);
			helper.assertTrue(i < discsFrom || i % CreativeTabSections.ROW_LENGTH >= 0 && !isHeaderRow(i, headers),
					stack + " is on a row a section's name is drawn on");
		}
		helper.assertTrue(slots.stream().anyMatch(stack -> stack.getItem() instanceof StandDiscItem), "the tab has no Stand discs");
		helper.assertTrue(tab.getSearchTabDisplayItems().stream().noneMatch(ItemStack::isEmpty), "the search tab got the layout's blanks");
		helper.assertTrue(tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.getItem() instanceof StandDiscItem),
				"the search tab lost the Stand discs");
		helper.assertTrue(SectionedCreativeTab.sectionOf(new ItemStack(Items.APPLE)) == ItemSection.ITEMS,
				"an add-on's plain item would not fall into the items section");
		helper.succeed();
	}

	private static boolean isHeaderRow(int slot, List<Header<Component>> headers) {
		return headers.stream().anyMatch(header -> header.row() == slot / CreativeTabSections.ROW_LENGTH);
	}

	private static String key(Component name) {
		return name.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : name.getString();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theNewTextIsInAllTwelveLanguages(GameTestHelper helper) {
		JsonObject english = readLang("en_us");
		List<String> missing = new ArrayList<>();
		for (String locale : LOCALES) {
			JsonObject lang = readLang(locale);
			for (String key : NEW_TEXT) {
				JsonElement value = lang.get(key);
				String text = value != null && value.isJsonPrimitive() ? value.getAsString() : null;
				if (text == null || text.isBlank()) {
					missing.add(locale + " " + key);
				}
				else if (!locale.startsWith("en_") && text.equals(english.get(key).getAsString())) {
					missing.add(locale + " " + key + " is the English text");
				}
				else if (text.contains("%s") != english.get(key).getAsString().contains("%s")) {
					missing.add(locale + " " + key + " differs from English in its %s");
				}
			}
		}
		helper.assertTrue(missing.isEmpty(), "new text missing: " + missing);
		helper.succeed();
	}

	private static JsonObject readLang(String locale) {
		String path = "assets/" + JojoMod.MOD_ID + "/lang/" + locale + ".json";
		try (InputStream in = CreativeTabSectionsGameTests.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new GameTestAssertException(path + " is missing");
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		catch (IOException e) {
			throw new GameTestAssertException("Could not read " + path + ": " + e);
		}
	}
}
