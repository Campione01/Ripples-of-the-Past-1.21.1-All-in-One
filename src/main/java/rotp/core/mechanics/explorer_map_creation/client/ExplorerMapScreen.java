package rotp.core.mechanics.explorer_map_creation.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.core.core.JojoMod;
import rotp.core.mechanics.explorer_map_creation.ClExplorerMapDataPacket;
import rotp.core.mechanics.explorer_map_creation.ExplorerMapMenu;
import rotp.core.mechanics.explorer_map_creation.ExplorerMapRules;

/** The explorer map mode of the cartography table: the destination's X and Z, its icon and the map's name. */
public class ExplorerMapScreen extends AbstractContainerScreen<ExplorerMapMenu> {
	private static final ResourceLocation BACKGROUND = JojoMod.resLoc("textures/gui/container/cartography_table2.png");
	private static final ResourceLocation NAME_FIELD = JojoMod.resLoc("textures/gui/container/cartography_table2_name_field.png");
	private static final int ICON = 16;
	private static final int ICONS_IN_ROW = 5;
	private static final int ICON_ROWS_SHOWN = 3;

	private final List<IconChoice> choices = new ArrayList<>();
	private IconChoice chosenButton;
	private EditBox nameField;
	private EditBox xField;
	private EditBox zField;
	private boolean choosing;
	private int firstRow;

	public ExplorerMapScreen(ExplorerMapMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.titleLabelY -= 2;
	}

	@Override
	protected void init() {
		super.init();
		for (Button button : ExplorerMapModeButtons.create(this, true)) {
			addRenderableWidget(button);
		}
		choices.clear();
		minecraft.level.registryAccess().registryOrThrow(Registries.MAP_DECORATION_TYPE).holders()
				.filter(ExplorerMapRules::canMark)
				.forEach(holder -> choices.add(addRenderableWidget(new IconChoice(holder, button -> choose(holder)))));
		chosenButton = addRenderableWidget(new IconChoice(menu.icon, button -> choosing = !choosing));
		chosenButton.setPosition(leftPos + 27, topPos + 41);

		xField = numberField(leftPos + 57, topPos + 34, "X", menu.posX, value -> {
			if (menu.setTargetPosX(ExplorerMapRules.clampToBorder(value, minecraft.level.getWorldBorder(), true))) {
				PacketDistributor.sendToServer(ClExplorerMapDataPacket.posX(menu.posX));
			}
		});
		zField = numberField(leftPos + 57, topPos + 50, "Z", menu.posZ, value -> {
			if (menu.setTargetPosZ(ExplorerMapRules.clampToBorder(value, minecraft.level.getWorldBorder(), false))) {
				PacketDistributor.sendToServer(ClExplorerMapDataPacket.posZ(menu.posZ));
			}
		});
		nameField = new EditBox(font, leftPos + 39, topPos + 18, 103, 12, CommonComponents.EMPTY);
		nameField.setTextColor(-1);
		nameField.setTextColorUneditable(-1);
		nameField.setBordered(false);
		nameField.setMaxLength(ExplorerMapRules.NAME_MAX);
		nameField.setValue(menu.itemName);
		nameField.setResponder(name -> {
			if (menu.setMapItemName(name)) {
				PacketDistributor.sendToServer(ClExplorerMapDataPacket.name(menu.itemName));
			}
		});
		addRenderableWidget(nameField);
	}

	private EditBox numberField(int x, int y, String name, int value, IntConsumer onValue) {
		EditBox field = new EditBox(font, x, y, 70, 14, Component.literal(name));
		field.setFilter(text -> text.matches("-?\\d{0,8}"));
		field.setValue(String.valueOf(value));
		field.setResponder(text -> {
			try {
				onValue.accept(Integer.parseInt(text));
			}
			catch (NumberFormatException notYet) {
				// "" or "-" while typing
			}
		});
		return addRenderableWidget(field);
	}

	private void choose(Holder<MapDecorationType> icon) {
		if (menu.setTargetIcon(icon)) {
			chosenButton.icon = icon;
			PacketDistributor.sendToServer(ClExplorerMapDataPacket.icon(icon));
		}
		choosing = false;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		chosenButton.visible = !choosing;
		xField.visible = !choosing;
		zField.visible = !choosing;
		nameField.visible = !choosing;
		int rows = (choices.size() + ICONS_IN_ROW - 1) / ICONS_IN_ROW;
		firstRow = Math.max(0, Math.min(firstRow, rows - ICON_ROWS_SHOWN));
		for (int i = 0; i < choices.size(); i++) {
			int row = i / ICONS_IN_ROW - firstRow;
			IconChoice choice = choices.get(i);
			choice.visible = choosing && row >= 0 && row < ICON_ROWS_SHOWN;
			choice.setPosition(leftPos + 40 + i % ICONS_IN_ROW * ICON, topPos + 15 + row * ICON);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (choosing && scrollY != 0) {
			firstRow += scrollY > 0 ? -1 : 1;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		if (nameField.visible) {
			graphics.blit(NAME_FIELD, leftPos + 36, topPos + 14, 0, 0, 110, 16, 110, 16);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		super.renderLabels(graphics, mouseX, mouseY);
		if (xField.visible) {
			graphics.drawString(font, "X", xField.getX() - leftPos - 9, xField.getY() - topPos + 3, 0x404040, false);
			graphics.drawString(font, "Z", zField.getX() - leftPos - 9, zField.getY() - topPos + 3, 0x404040, false);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			minecraft.player.closeContainer();
			return true;
		}
		// typing in a field must not drop items or close the screen with the inventory key
		for (EditBox field : List.of(nameField, xField, zField)) {
			if (field.isFocused() && field.visible && (field.keyPressed(keyCode, scanCode, modifiers) || field.canConsumeInput())) {
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** A map icon as a button: its sprite from the map icons, or the whole texture for an icon this mod draws itself. */
	static class IconChoice extends Button {
		Holder<MapDecorationType> icon;

		IconChoice(Holder<MapDecorationType> icon, Button.OnPress onPress) {
			super(0, 0, ICON, ICON, CommonComponents.EMPTY, onPress, Button.DEFAULT_NARRATION);
			this.icon = icon;
			icon.unwrapKey().ifPresent(key -> setTooltip(Tooltip.create(Component.literal(key.location().toString()))));
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			drawIcon(graphics, icon, getX(), getY());
			if (isHoveredOrFocused()) {
				graphics.fill(getX(), getY(), getX() + ICON, getY() + ICON, 0x80FFFFFF);
			}
		}
	}

	static void drawIcon(GuiGraphics graphics, Holder<MapDecorationType> icon, int x, int y) {
		TextureAtlasSprite sprite = Minecraft.getInstance().getMapDecorationTextures()
				.get(new MapDecoration(icon, (byte) 0, (byte) 0, (byte) 0, Optional.empty()));
		if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
			// the pillarman temple and the meteorite have their own renderer and textures/map/<name>.png
			ResourceLocation asset = icon.value().assetId();
			graphics.blit(asset.withPath("textures/map/" + asset.getPath() + ".png"), x, y, 0, 0, ICON, ICON, ICON, ICON);
		}
		else {
			graphics.blit(x, y, 0, ICON, ICON, sprite);
		}
	}
}
