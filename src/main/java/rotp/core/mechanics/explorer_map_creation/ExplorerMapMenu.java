package rotp.core.mechanics.explorer_map_creation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import rotp.core.init.ModContainers;

/**
 * The cartography table's second mode, from the official version: an empty map becomes a map of any place in the world,
 * marked with a chosen icon and named, at the scale of a vanilla explorer map.
 */
public class ExplorerMapMenu extends AbstractContainerMenu {
	public static final Component VANILLA_TITLE = Component.translatable("container.cartography_table");
	public static final Component EXPLORER_TITLE = Component.translatable("jojo_ripples.container.explorer_cartography_table");
	public static final byte MAP_SCALE = 2;
	private static final int MAP_SLOT = 0;
	private static final int RESULT_SLOT = 1;
	private static final int INV_SLOT_START = 2;
	private static final int INV_SLOT_END = 29;
	private static final int USE_ROW_SLOT_END = 38;

	private final ContainerLevelAccess access;
	private long lastSoundTime;
	public final Slot inputMapSlot;
	public final Slot resultMapSlot;

	public int posX;
	public int posZ;
	public Holder<MapDecorationType> icon = MapDecorationTypes.TARGET_X;
	public int itemColor = MapItemColor.DEFAULT.rgb();
	public String itemName = "";

	private final Container inputContainer = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			slotsChanged(this);
			super.setChanged();
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	};

	private final ResultContainer resultContainer = new ResultContainer() {
		@Override
		public void setChanged() {
			slotsChanged(this);
			super.setChanged();
		}
	};

	public ExplorerMapMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, ContainerLevelAccess.NULL);
	}

	public ExplorerMapMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
		super(ModContainers.EXPLORER_MAP_TABLE.get(), containerId);
		this.access = access;
		inputMapSlot = addSlot(new Slot(inputContainer, 0, 15, 15) {
			@Override
			public boolean mayPlace(ItemStack item) {
				return item.is(Items.MAP);
			}
		});
		resultMapSlot = addSlot(new Slot(resultContainer, 0, 145, 39) {
			@Override
			public boolean mayPlace(ItemStack item) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack item) {
				inputMapSlot.remove(1);
				item.getItem().onCraftedBy(item, player.level(), player);
				ExplorerMapMenu.this.access.execute((level, blockPos) -> {
					if (level instanceof ServerLevel serverLevel) {
						finalizeMap(item, serverLevel, posX, posZ);
					}
					long time = level.getGameTime();
					if (lastSoundTime != time) {
						level.playSound(null, blockPos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
						lastSoundTime = time;
					}
				});
				super.onTake(player, item);
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
		}
		BlockPos playerPos = playerInventory.player.blockPosition();
		posX = playerPos.getX();
		posZ = playerPos.getZ();
	}

	/** The map item before it is taken: its name, colour and mark, which the client shows as the result. */
	public static void describeMap(ItemStack item, Component name, int posX, int posZ, Holder<MapDecorationType> icon, int color) {
		item.set(DataComponents.ITEM_NAME, name);
		item.update(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY,
				decorations -> decorations.withDecoration("+", new MapDecorations.Entry(icon, posX, posZ, 180.0F)));
		item.set(DataComponents.MAP_COLOR, new MapItemColor(color));
	}

	/** Taken: a new map centred on the destination, with the biome preview a vanilla explorer map has. */
	public static void finalizeMap(ItemStack item, ServerLevel level, int posX, int posZ) {
		MapItemSavedData data = MapItemSavedData.createFresh(posX, posZ, MAP_SCALE, true, true, level.dimension());
		MapId mapId = level.getFreeMapId();
		level.setMapData(mapId, data);
		item.set(DataComponents.MAP_ID, mapId);
		MapItem.renderBiomePreviewMap(level, item);
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, Blocks.CARTOGRAPHY_TABLE);
	}

	@Override
	public void slotsChanged(Container inventory) {
		setupResultSlot();
	}

	private void setupResultSlot() {
		if (!inputMapSlot.getItem().isEmpty()) {
			access.execute((level, blockPos) -> {
				ItemStack result = new ItemStack(Items.FILLED_MAP);
				describeMap(result, itemName.isEmpty() ? Component.translatable("item.minecraft.filled_map") : Component.literal(itemName),
						posX, posZ, icon, itemColor);
				resultContainer.setItem(0, result);
				broadcastChanges();
			});
		}
		else if (!resultMapSlot.getItem().isEmpty()) {
			resultContainer.removeItemNoUpdate(0);
		}
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != resultContainer && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack clicked = slot.getItem();
		ItemStack copy = clicked.copy();
		if (index == RESULT_SLOT) {
			clicked.getItem().onCraftedBy(clicked, player.level(), player);
			if (!moveItemStackTo(clicked, INV_SLOT_START, USE_ROW_SLOT_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(clicked, copy);
		}
		else if (index != MAP_SLOT) {
			if (clicked.is(Items.MAP)) {
				if (!moveItemStackTo(clicked, MAP_SLOT, MAP_SLOT + 1, false)) {
					return ItemStack.EMPTY;
				}
			}
			else if (index < INV_SLOT_END ? !moveItemStackTo(clicked, INV_SLOT_END, USE_ROW_SLOT_END, false)
					: !moveItemStackTo(clicked, INV_SLOT_START, INV_SLOT_END, false)) {
				return ItemStack.EMPTY;
			}
		}
		else if (!moveItemStackTo(clicked, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
			return ItemStack.EMPTY;
		}
		if (clicked.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		}
		slot.setChanged();
		if (clicked.getCount() == copy.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, clicked);
		broadcastChanges();
		return copy;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		resultContainer.removeItemNoUpdate(0);
		access.execute((level, blockPos) -> clearContainer(player, inputContainer));
	}

	// the server keeps each value inside its rules (ExplorerMapRules); the client's own copy follows what it typed

	public boolean setTargetPosX(int value) {
		int clamped = access.evaluate((level, pos) -> ExplorerMapRules.clampToBorder(value, level.getWorldBorder(), true), value);
		if (posX != clamped) {
			posX = clamped;
			setupResultSlot();
			return true;
		}
		return false;
	}

	public boolean setTargetPosZ(int value) {
		int clamped = access.evaluate((level, pos) -> ExplorerMapRules.clampToBorder(value, level.getWorldBorder(), false), value);
		if (posZ != clamped) {
			posZ = clamped;
			setupResultSlot();
			return true;
		}
		return false;
	}

	public boolean setTargetIcon(Holder<MapDecorationType> decoration) {
		if (ExplorerMapRules.canMark(decoration) && !icon.is(decoration)) {
			icon = decoration;
			itemColor = decoration.value().hasMapColor() ? decoration.value().mapColor() : MapItemColor.DEFAULT.rgb();
			setupResultSlot();
			return true;
		}
		return false;
	}

	public boolean setMapItemName(String name) {
		String cleaned = ExplorerMapRules.cleanName(name);
		if (!itemName.equals(cleaned)) {
			itemName = cleaned;
			setupResultSlot();
			return true;
		}
		return false;
	}
}
