package rotp.core.init;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rotp.core.init.CreativeTabSections.Header;
import rotp.core.init.CreativeTabSections.Layout;
import rotp.core.init.CreativeTabSections.Section;
import rotp.core.mechanics.clothes.ClothesItem;
import rotp.core.mechanics.clothes.itemdata.ClothesDataComponent;
import rotp.core.mechanics.standdisc.StandDiscItem;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Sorts what the tab holds after every add-on has put its items in: a stack goes to its section by its item, so an add-on's
 * disc, clothes or anything else lands in the right one with no work of its own. The search tab keeps its plain list.
 */
public class SectionedCreativeTab extends CreativeModeTab {
	public enum ItemSection { ITEMS, STAND_DISCS, CLOTHES }

	private Collection<ItemStack> sectioned;
	private List<Header<Component>> headers = List.of();

	public SectionedCreativeTab(CreativeModeTab.Builder builder) {
		super(builder);
	}

	public static ItemSection sectionOf(ItemStack stack) {
		if (stack.getItem() instanceof StandDiscItem) {
			return ItemSection.STAND_DISCS;
		}
		if (stack.getItem() instanceof ClothesItem || stack.is(ModItems.SEWING_MACHINE.get()) || stack.is(ModItems.SEWING_NEEDLE.get())
				|| stack.is(ModItems.MANNEQUIN.get()) || stack.is(ModItems.MANNEQUIN_SLIM.get())) {
			return ItemSection.CLOTHES;
		}
		return ItemSection.ITEMS;
	}

	@Override
	public void buildContents(CreativeModeTab.ItemDisplayParameters parameters) {
		sectioned = null;
		super.buildContents(parameters);
		List<ItemStack> items = new ArrayList<>();
		List<ItemStack> discs = new ArrayList<>();
		List<ItemStack> clothesTools = new ArrayList<>();
		Map<Object, List<ItemStack>> clothesSets = new LinkedHashMap<>();
		for (ItemStack stack : super.getDisplayItems()) {
			switch (sectionOf(stack)) {
				case STAND_DISCS -> discs.add(stack);
				case CLOTHES -> {
					ClothesDataComponent clothes = stack.get(ModItemDataComponents.CLOTHES_PIECE.get());
					if (clothes != null) {
						clothesSets.computeIfAbsent(clothes.getClothesSet(), __ -> new ArrayList<>()).add(stack);
					}
					else {
						clothesTools.add(stack);
					}
				}
				default -> items.add(stack);
			}
		}
		List<List<ItemStack>> clothesGroups = new ArrayList<>();
		clothesGroups.add(clothesTools);
		clothesGroups.addAll(clothesSets.values());
		Layout<ItemStack, Component> layout = CreativeTabSections.layout(List.of(
				new Section<>(null, List.of(items)),
				new Section<>(Component.translatable("jojo_ripples.creative_tab.stand_discs"), List.of(discs)),
				new Section<>(Component.translatable("jojo_ripples.creative_tab.clothes"), clothesGroups)),
				ItemStack.EMPTY);
		sectioned = layout.slots();
		headers = layout.headers();
	}

	@Override
	public Collection<ItemStack> getDisplayItems() {
		return sectioned != null ? sectioned : super.getDisplayItems();
	}

	public List<Header<Component>> getHeaders() {
		return headers;
	}
}
