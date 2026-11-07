package rotp.core.config.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import rotp.core.client.ui.screen_jojomenu.IJojoMenuScreen;
import rotp.core.client.ui.screen_jojomenu.IJojoMenuTab;
import rotp.core.client.ui.screen_jojomenu.JojoMenuTabs;
import rotp.core.client.ui.screen_jojomenu.Tab;
import rotp.core.client.ui.screen_jojomenu.TabCategory;
import rotp.core.client.ui.utils.tooltip.TooltipParams;
import rotp.core.config.client.screen.ConfigControls.PaperAction;
import rotp.core.config.client.screen.PaperUi.Glyph;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.CoreConfigPresets;
import rotp.core.config.editor.client.ClientConfigStore;
import rotp.core.config.editor.client.ConfigEditSession;
import rotp.core.config.editor.client.ConfigStore;
import rotp.core.config.editor.client.ConfigStore.Access;
import rotp.core.config.editor.client.ConfigWidgetRules;

/**
 * The mod's settings: the client's own, and the server's gameplay values. A sheet of the JoJo menu's paper, wider than
 * its other pages because each row carries a name, a control and a reset. Every change is sent at once, so the only
 * edit that can be lost is one still in hand, and closing sends that first.
 */
public final class ModConfigScreen extends Screen implements IJojoMenuScreen, ConfigScreenHost {
	public enum Page { CLIENT, GAMEPLAY }

	private static final String TEXT = "jojo_ripples.config.screen.";
	private static final int MAX_WIDTH = 480;
	private static final int MAX_HEIGHT = 380;
	private static final int SIDE_TABS = 60;
	private static final int TALL_HEADER_FROM = 190;
	private static final int FOOTER = 26;
	private static final long TOOLTIP_DELAY_MILLIS = 350;
	private static final long OK_NOTICE_MILLIS = 2500;
	private static final long ERROR_NOTICE_MILLIS = 7000;
	// what is folded stays folded while the game runs: the aura's twenty-odd tuning values start folded
	private static final Set<String> COLLAPSED = new HashSet<>(Set.of("stand_aura"));

	private final @Nullable Screen lastScreen;
	private final Map<Page, Double> scrollByPage = new HashMap<>();
	private final Map<String, Long> refusedAt = new HashMap<>();
	private Page page;
	private @Nullable ClientConfigStore clientStore;
	private @Nullable ConfigEditSession serverStore;
	private @Nullable ConfigRowList list;
	private PaperAction resetAll;
	private PaperAction retry;
	private int windowWidth = DEFAULT_WIDTH;
	private int windowHeight = DEFAULT_HEIGHT;
	private Access builtFor;
	private int builtEntries;
	private @Nullable Component statusText;
	private int statusColor;
	private long statusUntil;
	private boolean closing;
	private long closingSince;
	private @Nullable List<Component> tooltipNow;
	private @Nullable ConfigRowList.Row hoverRow;
	private long hoverSince;
	private boolean clickedHoverRow;

	public ModConfigScreen(@Nullable Screen lastScreen, Page page) {
		super(Component.translatable(TEXT + "title"));
		this.lastScreen = lastScreen;
		this.page = page;
	}

	@Override
	public ConfigStore store() {
		return page == Page.CLIENT ? clientStore() : serverStore();
	}

	private ClientConfigStore clientStore() {
		if (clientStore == null) {
			clientStore = new ClientConfigStore();
		}
		return clientStore;
	}

	ConfigEditSession serverStore() {
		if (serverStore == null) {
			serverStore = ServerConfigStores.open();
		}
		return serverStore;
	}

	@Override
	public net.minecraft.client.gui.Font font() {
		return font;
	}

	@Override
	public boolean editable() {
		return store().access() == Access.EDITABLE;
	}

	// the interface centres its own fixed size
	@Override
	public int getWindowX(Screen screen) {
		return (screen.width - windowWidth) / 2;
	}

	@Override
	public int getWindowY(Screen screen) {
		return (screen.height - windowHeight) / 2;
	}

	@Override
	public int getWindowWidth() {
		return windowWidth;
	}

	@Override
	public int getWindowHeight() {
		return windowHeight;
	}

	@Override
	public List<TabCategory> activeCategories() {
		// the title screen has no player for the power categories to ask
		return minecraft.player != null ? TabCategory.getActiveCategories() : List.of(JojoMenuTabs.CATEGORY_SETTINGS);
	}

	@Override
	public TabCategory getTabCategory() {
		return JojoMenuTabs.CATEGORY_SETTINGS;
	}

	@Override
	public Tab getTab() {
		return page == Page.CLIENT ? JojoMenuTabs.SETTINGS_CLIENT : JojoMenuTabs.SETTINGS_GAMEPLAY;
	}

	private int headerHeight() {
		return windowHeight >= TALL_HEADER_FROM ? 33 : 21;
	}

	private @Nullable Component bandMessage() {
		return switch (store().access()) {
			case READ_ONLY -> Component.translatable(TEXT + "read_only.not_op");
			case NO_WORLD -> Component.translatable(TEXT + "read_only.no_world");
			default -> null;
		};
	}

	private int bandHeight() {
		Component band = bandMessage();
		if (band == null) {
			return 0;
		}
		return PaperUi.fit(font, band.getString(), windowWidth - 36, 2).size() == 1 ? 15 : 25;
	}

	@Override
	protected void init() {
		windowWidth = Math.max(180, Math.min(MAX_WIDTH, width - SIDE_TABS));
		windowHeight = Math.min(MAX_HEIGHT, height - 8);
		int x = getWindowX(this);
		int y = getWindowY(this);
		double scroll = list != null ? list.getScrollAmount() : scrollByPage.getOrDefault(page, 0D);

		int listY = y + headerHeight() + bandHeight();
		int listHeight = Math.max(PaperUi.ROW_HEIGHT, y + windowHeight - FOOTER - listY);
		list = addRenderableWidget(new ConfigRowList(minecraft, this, x + 2, listY, windowWidth - 4, listHeight));

		int buttonY = y + windowHeight - FOOTER + 5;
		int doneWidth = Math.max(50, font.width(CommonComponents.GUI_DONE) + 16);
		addRenderableWidget(new PaperAction(x + windowWidth - 9 - doneWidth, buttonY, doneWidth, PaperUi.CONTROL_HEIGHT,
				CommonComponents.GUI_DONE, button -> onClose()));
		Component resetText = Component.translatable(TEXT + "reset_all");
		resetAll = addRenderableWidget(new PaperAction(x + 9, buttonY, Math.max(50, font.width(resetText) + 16), PaperUi.CONTROL_HEIGHT,
				resetText, button -> confirmResetAll()));
		Component retryText = Component.translatable(TEXT + "retry");
		retry = addRenderableWidget(new PaperAction(x + 9, buttonY, Math.max(50, font.width(retryText) + 16), PaperUi.CONTROL_HEIGHT,
				retryText, button -> serverStore().retry(Util.getMillis())));

		list.rebuild(page == Page.CLIENT ? ConfigEntry.Side.CLIENT : ConfigEntry.Side.SERVER, COLLAPSED);
		list.setScrollAmount(scroll);
		builtFor = store().access();
		builtEntries = store().entries().size();
	}

	@Override
	public void rowsChanged() {
		if (list != null) {
			list.rebuild(page == Page.CLIENT ? ConfigEntry.Side.CLIENT : ConfigEntry.Side.SERVER, COLLAPSED);
		}
	}

	private void showPage(Page next) {
		if (next == page) {
			return;
		}
		if (list != null) {
			list.sendUnsentEdits();
			scrollByPage.put(page, list.getScrollAmount());
		}
		list = null;
		page = next;
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
		JojoMenuTabs.onTabOpened(getTab());
		rebuildWidgets();
	}

	@Override
	public void tick() {
		long now = Util.getMillis();
		stepStores(now);
		if (list != null) {
			list.tickControls(now);
		}
		if (builtFor != store().access() || builtEntries != store().entries().size()) {
			rebuildWidgets();
		}
		if (closing && (!store().busy() || now - closingSince > ConfigEditSession.ANSWER_TIMEOUT_MILLIS)) {
			leave();
		}
	}

	/** The stores keep answering while a dialog is on top: the dialog calls this from its own tick. */
	void stepStores(long now) {
		if (serverStore != null) {
			serverStore.tick(now);
			readNotice(serverStore, now);
		}
		if (clientStore != null) {
			clientStore.tick(now);
			readNotice(clientStore, now);
		}
	}

	private void readNotice(ConfigStore source, long now) {
		ConfigStore.Notice notice = source.takeNotice();
		if (notice == null) {
			return;
		}
		boolean error = notice.severity() == ConfigStore.Severity.ERROR;
		setStatus(Component.translatable(notice.textKey(), notice.args().toArray()), error);
		if (error) {
			closing = false;
			if (notice.flashKey() != null) {
				refusedAt.put(notice.flashKey(), now);
			}
		}
	}

	void setStatus(Component text, boolean error) {
		statusText = text;
		statusColor = error ? PaperUi.ERROR : PaperUi.ON;
		statusUntil = Util.getMillis() + (error ? ERROR_NOTICE_MILLIS : OK_NOTICE_MILLIS);
	}

	@Override
	public long refusalAge(String key) {
		Long at = refusedAt.get(key);
		return at == null ? -1 : Util.getMillis() - at;
	}

	@Override
	public void showTooltip(List<Component> lines) {
		tooltipNow = lines;
	}

	@Override
	public void openList(ConfigEntry entry) {
		minecraft.setScreen(new ConfigListEditScreen(this, store(), entry));
	}

	@Override
	public void typedValueRejected(ConfigEntry entry) {
		setStatus(entry.bounds() != null ? ConfigTooltips.rangeOf(entry.kind(), entry.bounds())
				: Component.translatable(TEXT + "status.invalid_value"), true);
	}

	@Override
	public void confirmResetKeys(Component sectionTitle, List<String> keys) {
		List<String> modified = keys.stream().filter(key -> {
			ConfigEntry entry = store().entry(key);
			return entry != null && ConfigWidgetRules.isShown(entry) && ConfigWidgetRules.isModified(entry);
		}).toList();
		ConfigStore target = store();
		minecraft.setScreen(ConfigDiffScreen.ofChanges(this, Component.translatable(TEXT + "reset_section_title", sectionTitle),
				Component.translatable(TEXT + "confirm_reset"), toDefaults(target, modified),
				done -> target.resetKeys(modified, done)));
	}

	private void confirmResetAll() {
		ConfigStore target = store();
		List<String> modified = target.entries().stream().filter(entry -> ConfigWidgetRules.isShown(entry) && ConfigWidgetRules.isModified(entry))
				.map(ConfigEntry::key).toList();
		minecraft.setScreen(ConfigDiffScreen.ofChanges(this, Component.translatable(TEXT + "reset_all_title", pageName(page)),
				Component.translatable(TEXT + "confirm_reset"), toDefaults(target, modified),
				done -> target.resetAll(done)));
	}

	@Override
	public void confirmPreset(CoreConfigPresets.Preset preset) {
		minecraft.setScreen(ConfigDiffScreen.ofPreset(this, serverStore(), preset));
	}

	@Override
	public void confirmPresetDefaults() {
		ConfigEditSession session = serverStore();
		Set<String> keys = new LinkedHashSet<>();
		List<CoreConfigPresets.Preset> presets = CoreConfigPresets.entries();
		presets.forEach(preset -> keys.addAll(preset.values().keySet()));
		List<String> modified = keys.stream().filter(key -> {
			ConfigEntry entry = session.entry(key);
			return entry != null && ConfigWidgetRules.isModified(entry);
		}).toList();
		minecraft.setScreen(ConfigDiffScreen.ofChanges(this, Component.translatable("jojo_ripples.config.preset.defaults_title"),
				Component.translatable(TEXT + "confirm_reset"), toDefaults(session, modified), done -> {
					for (int i = 0; i < presets.size(); i++) {
						session.resetPreset(presets.get(i).id(), i == presets.size() - 1 ? done : null);
					}
				}));
	}

	private static List<ConfigDiffScreen.Row> toDefaults(ConfigStore source, List<String> keys) {
		List<ConfigDiffScreen.Row> rows = new ArrayList<>();
		for (String key : keys) {
			ConfigEntry entry = source.entry(key);
			if (entry != null) {
				rows.add(ConfigDiffScreen.Row.of(entry, entry.value(), entry.defaultValue(), entry.restart()));
			}
		}
		return rows;
	}

	private static Component pageName(Page page) {
		return Component.translatable(TEXT + (page == Page.CLIENT ? "page.client" : "page.gameplay"));
	}

	@Override
	public void onClose() {
		if (list != null) {
			list.sendUnsentEdits();
		}
		if (store().busy()) {
			closing = true;
			closingSince = Util.getMillis();
			return;
		}
		leave();
	}

	private void leave() {
		closing = false;
		minecraft.setScreen(lastScreen);
	}

	@Override
	public void removed() {
		if (list != null) {
			list.sendUnsentEdits();
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && list != null && list.dropFocusedEdit()) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			IJojoMenuTab tab = getTabAt(mouseX, mouseY, this);
			if (tab == JojoMenuTabs.SETTINGS_CLIENT) {
				showPage(Page.CLIENT);
				return true;
			}
			if (tab == JojoMenuTabs.SETTINGS_GAMEPLAY) {
				showPage(Page.GAMEPLAY);
				return true;
			}
			if (tab == JojoMenuTabs.CATEGORY_SETTINGS) {
				return true;
			}
		}
		if (clickTab(mouseX, mouseY, button, this)) {
			return true;
		}
		// a click answers the row: its tooltip waits until the pointer has been elsewhere
		if (list != null) {
			hoverRow = list.rowAt(mouseX, mouseY);
			clickedHoverRow = true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return lastScreen != null;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blitSprite(PaperUi.WINDOW, getWindowX(this), getWindowY(this), windowWidth, windowHeight);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		boolean noAnswer = store().access() == Access.NO_ANSWER;
		retry.visible = noAnswer;
		resetAll.visible = !noAnswer && !store().entries().isEmpty();
		resetAll.active = editable() && store().entries().stream().anyMatch(entry -> ConfigWidgetRules.isShown(entry) && ConfigWidgetRules.isModified(entry));
		tooltipNow = null;
		super.render(graphics, mouseX, mouseY, partialTick);
		renderHeader(graphics);
		renderBand(graphics);
		renderEmptyMessage(graphics);
		renderStatus(graphics);
		renderTabs(graphics, this);
		renderTooltips(mouseX, mouseY);
		renderTabTooltip(graphics, this, mouseX, mouseY);
	}

	private void renderHeader(GuiGraphics graphics) {
		int x = getWindowX(this);
		int y = getWindowY(this);
		Component name = pageName(page).copy().withStyle(net.minecraft.ChatFormatting.BOLD);
		PaperUi.text(graphics, font, name, x + (windowWidth - font.width(name)) / 2, y + 7, PaperUi.INK);
		if (windowHeight >= TALL_HEADER_FROM) {
			String description = Component.translatable(TEXT + (page == Page.CLIENT ? "page.client.desc" : "page.gameplay.desc")).getString();
			String shown = PaperUi.oneLine(font, description, windowWidth - 24);
			PaperUi.text(graphics, font, Component.literal(shown), x + (windowWidth - font.width(shown)) / 2, y + 18, PaperUi.INK_SOFT);
		}
		int lineY = y + headerHeight() - 2;
		graphics.fill(x + 8, lineY, x + windowWidth - 8, lineY + 1, PaperUi.LINE_SOFT);
		int footerY = y + windowHeight - FOOTER;
		graphics.fill(x + 8, footerY, x + windowWidth - 8, footerY + 1, PaperUi.LINE_SOFT);
	}

	private void renderBand(GuiGraphics graphics) {
		Component band = bandMessage();
		if (band == null) {
			return;
		}
		int x = getWindowX(this) + 8;
		int y = getWindowY(this) + headerHeight() - 1;
		List<FormattedCharSequence> lines = PaperUi.fit(font, band.getString(), windowWidth - 36, 2);
		int height = bandHeight() - 1;
		graphics.fill(x, y, x + windowWidth - 16, y + height, PaperUi.PANEL);
		PaperUi.glyph(graphics, Glyph.LOCK, x + 4, y + (height - 9) / 2, PaperUi.INK_SOFT);
		int textY = y + (height - lines.size() * 10 + 2) / 2;
		for (FormattedCharSequence line : lines) {
			PaperUi.text(graphics, font, line, x + 16, textY, PaperUi.INK);
			textY += 10;
		}
	}

	private void renderEmptyMessage(GuiGraphics graphics) {
		if (!store().entries().isEmpty()) {
			return;
		}
		String key = switch (store().access()) {
			case LOADING -> "loading";
			case UNSUPPORTED -> "unsupported";
			default -> "no_answer";
		};
		List<FormattedCharSequence> lines = PaperUi.fit(font, Component.translatable(TEXT + key).getString(), windowWidth - 40, 3);
		int y = getWindowY(this) + windowHeight / 2 - lines.size() * 5;
		for (FormattedCharSequence line : lines) {
			PaperUi.text(graphics, font, line, getWindowX(this) + (windowWidth - font.width(line)) / 2, y, PaperUi.INK_SOFT);
			y += 10;
		}
	}

	private void renderStatus(GuiGraphics graphics) {
		int x = getWindowX(this);
		int doneWidth = Math.max(50, font.width(CommonComponents.GUI_DONE) + 16);
		int left = x + 9 + (resetAll.visible ? resetAll.getWidth() : retry.visible ? retry.getWidth() : 0) + 6;
		int right = x + windowWidth - 9 - doneWidth - 6;
		int y = getWindowY(this) + windowHeight - FOOTER + 10;
		Component text;
		int color;
		if (store().busy()) {
			text = Component.translatable(TEXT + "status.waiting");
			color = PaperUi.PENDING;
		}
		else if (statusText != null && Util.getMillis() < statusUntil) {
			text = statusText;
			color = statusColor;
		}
		else {
			return;
		}
		String full = text.getString();
		String shown = PaperUi.oneLine(font, full, right - left);
		PaperUi.text(graphics, font, Component.literal(shown), left + (right - left - font.width(shown)) / 2, y, color);
	}

	private void renderTooltips(int mouseX, int mouseY) {
		long now = Util.getMillis();
		if (tooltipNow != null) {
			setPaperTooltip(tooltipNow, null);
			return;
		}
		if (list == null) {
			return;
		}
		ConfigRowList.Row hovered = list.isMouseOver(mouseX, mouseY) ? list.hoveredRow() : null;
		if (hovered != hoverRow) {
			hoverRow = hovered;
			hoverSince = now;
			clickedHoverRow = false;
		}
		if (hovered != null) {
			List<Component> lines = hovered.tooltip();
			if (!clickedHoverRow && !lines.isEmpty() && now - hoverSince >= TOOLTIP_DELAY_MILLIS) {
				// under or over the row, so what the pointer is about to press stays in sight
				int rowTop = list.topOf(hovered);
				setPaperTooltip(lines, new ScreenRectangle(mouseX, rowTop, 1, PaperUi.ROW_HEIGHT - 2));
			}
			return;
		}
		if (minecraft.getLastInputType().isKeyboard() && list.getFocused() != null) {
			AbstractWidget widget = list.getFocused().focusedWidget();
			List<Component> lines = list.getFocused().tooltip();
			if (!lines.isEmpty() && widget != null) {
				setPaperTooltip(lines, widget.getRectangle());
			}
		}
	}

	private void setPaperTooltip(List<Component> lines, @Nullable ScreenRectangle beside) {
		int wrap = Math.min(230, Math.max(120, width - 40));
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			int color = i == 0 ? PaperUi.INK : PaperUi.INK_SOFT;
			wrapped.addAll(font.split(lines.get(i).copy().withColor(color & 0xFFFFFF), wrap));
		}
		setTooltipForNextRenderPass(wrapped, beside != null ? new BelowOrAboveWidgetTooltipPositioner(beside)
				: DefaultTooltipPositioner.INSTANCE, true);
		TooltipParams.set(TooltipParams.paperStyle(1));
	}

	/** Used by the dialogs: draws this screen behind them, its own controls out of reach. */
	void renderBehindDialog(GuiGraphics graphics, float partialTick) {
		render(graphics, -1, -1, partialTick);
	}
}
