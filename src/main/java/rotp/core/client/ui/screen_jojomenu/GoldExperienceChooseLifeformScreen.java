package rotp.core.client.ui.screen_jojomenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.InputHandler;
import rotp.core.client.input.controlscheme.ClientKey;
import rotp.core.client.ui.ScreenLetsUseWASD;
import rotp.core.client.ui.utils.tooltip.TooltipParams;
import rotp.core.config.client.screen.PaperKit;
import rotp.core.network.c2s.ClGELifeformButtonPacket;
import rotp.core.network.c2s.ClGELifeformUiPacket;
import rotp.core.util.mc.entitysubtype.EntitySubtype;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformState;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeforms;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Choose Lifeform: the lifeforms Gold Experience has met, to pick the one Create Lifeform makes. On paper like the
 * lifeform menu beside it: portraits in a grid or a list, a search with mod: words, the All / Favourites / New filters.
 * A choice is only sent with Done; holding the key that opened it and letting go closes it, as before.
 */
public class GoldExperienceChooseLifeformScreen extends Screen implements ScreenLetsUseWASD {
    private static final String TEXT = "jojo_ripples.choose_lifeform.";
    private static final long TOOLTIP_DELAY_MILLIS = 350;
    private static String savedSearchFilter = "";
    private static FilterMode savedFilterMode = FilterMode.ALL;
    private static ViewMode savedViewMode = ViewMode.GRID;

    @Nullable private final ClientKey keyHeld;
    private List<EntitySubtype<?>> visibleChoices = List.of();
    @Nullable private String selectedLifeformId;
    private int ticksKeyHeld;
    private boolean holdsButton = true;
    private boolean holdMode;
    private boolean selectionDirty;
    private boolean revealChosen = true;
    private GoldExperienceChooseLifeformLayout layout;
    @Nullable private GoldExperienceChooseLifeformList list;
    @Nullable private PaperKit.Field searchBox;
    @Nullable private PaperKit.Action clearSearchButton;
    @Nullable private PaperKit.Action unlockAllButton;
    @Nullable private PaperKit.Action viewModeButton;
    @Nullable private PaperKit.Action favoriteButton;
    @Nullable private PaperKit.Action clearNewButton;
    @Nullable private PaperKit.Action doneButton;
    @Nullable private Object hoverTarget;
    private long hoverSince;

    private enum FilterMode {
        ALL("jojo.ui.lifeform_ui_mode.all"),
        FAVORITES("jojo.ui.lifeform_ui_mode.favs"),
        NEW("jojo.ui.lifeform_ui_mode.new");

        private final String translationKey;

        FilterMode(String translationKey) {
            this.translationKey = translationKey;
        }

        private Component label() {
            return Component.translatable(translationKey);
        }
    }

    private enum ViewMode {
        GRID("jojo.ge_lifeform.view.grid"),
        LIST("jojo.ge_lifeform.view.list");

        private final String translationKey;

        ViewMode(String translationKey) {
            this.translationKey = translationKey;
        }

        private Component label() {
            return Component.translatable(translationKey);
        }

        private ViewMode opposite() {
            return this == GRID ? LIST : GRID;
        }
    }

    public GoldExperienceChooseLifeformScreen() {
        this(InputHandler.lastActionKey);
    }

    private GoldExperienceChooseLifeformScreen(@Nullable ClientKey keyHeld) {
        super(Component.translatable("jojo_ripples.ability.choose_lifeform"));
        this.keyHeld = keyHeld;
    }

    public static void openWindowOnClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            ClientProxy.openScreen(new GoldExperienceChooseLifeformScreen(InputHandler.lastActionKey));
        }
    }

    Font font() {
        return font;
    }

    @Override
    protected void init() {
        super.init();
        layout = GoldExperienceChooseLifeformLayout.forScreen(width, height);
        GoldExperienceLifeformState state = playerState();
        if (!selectionDirty) {
            selectedLifeformId = state != null && Minecraft.getInstance().level != null
                    ? state.selectedOrFirstMetId(Minecraft.getInstance().level) : null;
        }

        addSearchRow();
        addFilterRow();
        double scroll = list != null ? list.getScrollAmount() : 0;
        list = addRenderableWidget(new GoldExperienceChooseLifeformList(minecraft, this, layout.x() + 2, layout.listTop(),
                layout.width() - 4, Math.max(GoldExperienceChooseLifeformLayout.LIST_ROW, layout.listBottom() - layout.listTop()),
                savedViewMode == ViewMode.GRID));
        list.setScrollAmount(scroll);
        addFooter();
        updateChoiceButtons();
    }

    private void addSearchRow() {
        int y = layout.searchY();
        int right = layout.right();
        Component unlockLabel = Component.translatable("jojo.ge_lifeform.unlock_all");
        int unlockWidth = Math.min(Math.max(48, font.width(unlockLabel) + 14), layout.inner() / 3);
        // only a creative player has it, as before; the search takes its room otherwise
        boolean unlockShown = creative();
        int clearRight = unlockShown ? right - unlockWidth - GoldExperienceChooseLifeformLayout.GAP : right;
        int fieldRight = clearRight - PaperKit.CONTROL_HEIGHT - GoldExperienceChooseLifeformLayout.GAP;
        // added in the order Tab goes through them: the search first
        searchBox = new PaperKit.Field(font, Component.translatable("jojo.ge_lifeform.search_field"));
        searchBox.setMaxLength(80);
        searchBox.setHint(Component.translatable("jojo.ge_lifeform.search_field").withColor(PaperKit.INK_FAINT & 0xFFFFFF));
        searchBox.place(layout.left(), y, fieldRight - layout.left(), PaperKit.CONTROL_HEIGHT);
        searchBox.setValue(savedSearchFilter);
        searchBox.setResponder(value -> {
            savedSearchFilter = value;
            updateChoiceButtons();
        });
        addRenderableWidget(searchBox);
        clearSearchButton = addRenderableWidget(new PaperKit.Action(clearRight - PaperKit.CONTROL_HEIGHT, y,
                PaperKit.CONTROL_HEIGHT, PaperKit.CONTROL_HEIGHT, Component.literal("×"), () -> {
                    if (searchBox != null) {
                        searchBox.setValue("");
                    }
                }));
        unlockAllButton = addRenderableWidget(new PaperKit.Action(right - unlockWidth, y, unlockWidth, PaperKit.CONTROL_HEIGHT,
                unlockLabel, this::unlockAll));
        unlockAllButton.visible = unlockShown;
    }

    private void addFilterRow() {
        int y = layout.filterY();
        Component viewLabel = Component.translatable("jojo.ge_lifeform.view.toggle", savedViewMode.label());
        int viewWidth = Math.min(Math.max(52, font.width(viewLabel) + 14), layout.inner() / 3);
        int room = layout.inner() - viewWidth - GoldExperienceChooseLifeformLayout.GAP;
        FilterMode[] modes = FilterMode.values();
        int each = room / modes.length;
        for (int i = 0; i < modes.length; i++) {
            FilterMode mode = modes[i];
            int x = layout.left() + i * each;
            int w = i == modes.length - 1 ? room - i * each : each;
            addRenderableWidget(new FilterChoice(x, y, w, mode));
        }
        viewModeButton = addRenderableWidget(new PaperKit.Action(layout.right() - viewWidth, y, viewWidth, PaperKit.CONTROL_HEIGHT,
                viewLabel, () -> {
                    savedViewMode = savedViewMode.opposite();
                    // the other view starts at the top, with the chosen one in sight
                    list = null;
                    revealChosen = true;
                    rebuildWidgets();
                }));
    }

    private void addFooter() {
        Component favorite = Component.translatable("jojo_ripples.lifeform.favorite");
        Component unfavorite = Component.translatable("jojo_ripples.lifeform.unfavorite");
        Component clearNew = Component.translatable("jojo_ripples.lifeform.clear_new");
        int y = layout.buttonsY();
        int lastY = layout.lastButtonsY();
        int gap = GoldExperienceChooseLifeformLayout.GAP;
        int favoriteWidth;
        int clearNewWidth;
        int cancelWidth;
        int doneWidth;
        if (layout.twoFooterRows()) {
            favoriteWidth = clearNewWidth = cancelWidth = doneWidth = (layout.inner() - gap) / 2;
        }
        else {
            favoriteWidth = Math.max(52, Math.max(font.width(favorite), font.width(unfavorite)) + 16);
            clearNewWidth = Math.max(52, font.width(clearNew) + 16);
            cancelWidth = Math.max(52, font.width(CommonComponents.GUI_CANCEL) + 16);
            doneWidth = Math.max(52, font.width(CommonComponents.GUI_DONE) + 16);
            int room = layout.inner() - 3 * gap - 6;
            int needed = favoriteWidth + clearNewWidth + cancelWidth + doneWidth;
            if (needed > room) {
                favoriteWidth = clearNewWidth = cancelWidth = doneWidth = (room) / 4;
            }
        }
        favoriteButton = addRenderableWidget(new PaperKit.Action(layout.left(), y, favoriteWidth, PaperKit.CONTROL_HEIGHT,
                favorite, () -> {
                    if (selectedLifeformId != null) {
                        toggleFavorite(selectedLifeformId);
                    }
                }));
        clearNewButton = addRenderableWidget(new PaperKit.Action(layout.left() + favoriteWidth + gap, y, clearNewWidth,
                PaperKit.CONTROL_HEIGHT, clearNew, () -> {
                    GoldExperienceLifeformState currentState = playerState();
                    if (currentState != null) {
                        PacketDistributor.sendToServer(ClGELifeformUiPacket.clearUnseen());
                        currentState.clearNewUnseenLifeformsFromSync();
                        updateChoiceButtons();
                    }
                }));
        int right = layout.right();
        addRenderableWidget(new PaperKit.Action(right - doneWidth - gap - cancelWidth, lastY, cancelWidth,
                PaperKit.CONTROL_HEIGHT, CommonComponents.GUI_CANCEL, this::onClose));
        doneButton = addRenderableWidget(new PaperKit.Action(right - doneWidth, lastY, doneWidth, PaperKit.CONTROL_HEIGHT,
                CommonComponents.GUI_DONE, () -> {
                    if (selectedLifeformId != null) {
                        PacketDistributor.sendToServer(new ClGELifeformButtonPacket(selectedLifeformId));
                        GoldExperienceLifeformState currentState = playerState();
                        if (currentState != null) {
                            currentState.setSelectedFromSync(selectedLifeformId);
                        }
                    }
                    onClose();
                }));
    }

    private void unlockAll() {
        PacketDistributor.sendToServer(ClGELifeformUiPacket.unlockAll());
        Minecraft mc = Minecraft.getInstance();
        GoldExperienceLifeformState currentState = playerState();
        if (currentState != null && mc.level != null && creative()) {
            currentState.learnAllValidLifeforms(mc.level);
            updateChoiceButtons();
        }
    }

    private static boolean creative() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getAbilities().instabuild;
    }

    private void setFilterMode(FilterMode filterMode) {
        if (savedFilterMode != filterMode) {
            savedFilterMode = filterMode;
            updateChoiceButtons();
        }
    }

    void choose(String lifeformId) {
        selectedLifeformId = lifeformId;
        selectionDirty = true;
        updateChoiceButtons();
    }

    boolean isChosen(String lifeformId) {
        return lifeformId.equals(selectedLifeformId);
    }

    boolean isFavorite(String lifeformId) {
        GoldExperienceLifeformState state = playerState();
        return state != null && state.isFavorite(lifeformId);
    }

    boolean isNew(String lifeformId) {
        GoldExperienceLifeformState state = playerState();
        return state != null && state.isNewUnseen(lifeformId);
    }

    @Override
    public void tick() {
        super.tick();
        if (holdsButton) {
            if (!isKeyBeingHeld()) {
                holdsButton = false;
            }
            else if (++ticksKeyHeld == 5) {
                holdMode = true;
            }
        }
        GoldExperienceLifeformState state = playerState();
        if (!selectionDirty && state != null && Minecraft.getInstance().level != null) {
            String syncedId = state.selectedOrFirstMetId(Minecraft.getInstance().level);
            if (syncedId != null && !syncedId.equals(selectedLifeformId)) {
                selectedLifeformId = syncedId;
                revealChosen = true;
                updateChoiceButtons();
            }
        }
    }

    private boolean isKeyBeingHeld() {
        if (keyHeld == null) {
            return false;
        }

        InputConstants.Key vanillaKey = keyHeld.getVanillaKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        return switch (vanillaKey.getType()) {
            case MOUSE -> GLFW.glfwGetMouseButton(window, vanillaKey.getValue()) == GLFW.GLFW_PRESS;
            case KEYSYM -> GLFW.glfwGetKey(window, vanillaKey.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }

    private void updateChoiceButtons() {
        GoldExperienceLifeformState state = playerState();
        if (state != null && Minecraft.getInstance().level != null) {
            visibleChoices = filterChoices(state.visibleLifeforms(Minecraft.getInstance().level), state);
        }
        else {
            visibleChoices = List.of();
        }
        if (selectedLifeformId == null && state != null && Minecraft.getInstance().level != null) {
            selectedLifeformId = state.selectedOrFirstMetId(Minecraft.getInstance().level);
        }
        if (list != null) {
            list.show(visibleChoices);
            if (revealChosen && selectedLifeformId != null) {
                list.reveal(selectedLifeformId);
                revealChosen = false;
            }
        }

        boolean hasSelectedMet = state != null && selectedLifeformId != null && state.hasMetLifeform(selectedLifeformId);
        if (doneButton != null) {
            doneButton.active = hasSelectedMet;
        }
        if (favoriteButton != null) {
            favoriteButton.active = hasSelectedMet;
            boolean favorite = hasSelectedMet && state.isFavorite(selectedLifeformId);
            favoriteButton.setMessage(Component.translatable(favorite
                    ? "jojo_ripples.lifeform.unfavorite"
                    : "jojo_ripples.lifeform.favorite"));
        }
        if (clearNewButton != null) {
            clearNewButton.active = state != null && state.hasAnyNewUnseenLifeforms();
        }
        if (clearSearchButton != null) {
            clearSearchButton.active = searchBox != null && !searchBox.getValue().isEmpty();
        }
        if (unlockAllButton != null) {
            unlockAllButton.visible = creative();
        }
    }

    private List<EntitySubtype<?>> filterChoices(List<EntitySubtype<?>> choices, GoldExperienceLifeformState state) {
        return choices.stream()
                .filter(choice -> filterModeMatches(state, choice))
                .filter(choice -> searchMatches(choice, savedSearchFilter))
                .toList();
    }

    private static boolean filterModeMatches(GoldExperienceLifeformState state, EntitySubtype<?> choice) {
        String id = choice.getId().toString();
        return switch (savedFilterMode) {
            case ALL -> true;
            case FAVORITES -> state.isFavorite(id);
            case NEW -> state.isNewUnseen(id);
        };
    }

    private static boolean searchMatches(EntitySubtype<?> choice, String rawSearch) {
        if (rawSearch.isBlank()) {
            return true;
        }

        String[] words = rawSearch.toLowerCase(Locale.ROOT).split("\\s+");
        List<String> modFilters = new ArrayList<>();
        StringBuilder nameFilter = new StringBuilder();
        boolean maybeTypingKeyword = !rawSearch.endsWith(" ");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.startsWith("mod:")) {
                String modSearch = word.substring("mod:".length());
                if (!modSearch.isBlank()) {
                    modFilters.add(modSearch);
                }
            }
            else {
                boolean isTypingKeyword = maybeTypingKeyword && i == words.length - 1 && "mod".startsWith(word);
                if (!isTypingKeyword) {
                    if (nameFilter.length() > 0) {
                        nameFilter.append(' ');
                    }
                    nameFilter.append(word);
                }
            }
        }

        String nameNeedle = nameFilter.toString();
        String description = choice.getDescription().getString().toLowerCase(Locale.ROOT);
        String id = choice.getId().toString().toLowerCase(Locale.ROOT);
        boolean nameMatches = nameNeedle.isBlank() || description.contains(nameNeedle) || id.contains(nameNeedle);

        String modName = modName(choice).toLowerCase(Locale.ROOT);
        boolean modMatches = modFilters.isEmpty() || modFilters.stream().anyMatch(modName::contains);
        return nameMatches && modMatches;
    }

    private static String modName(EntitySubtype<?> choice) {
        String namespace = choice.getId().withoutSubtype().getNamespace();
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }

    @Nullable
    private static GoldExperienceLifeformState playerState() {
        return Minecraft.getInstance().player != null
                ? GoldExperienceLifeformState.get(Minecraft.getInstance().player)
                : null;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(PaperKit.WINDOW, layout.x(), layout.y(), layout.width(), layout.height());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        renderHeader(gui);
        renderEmptyMessage(gui);
        renderTooltips(mouseX, mouseY);
        if (!holdsButton && holdMode) {
            onClose();
        }
    }

    private Component selectedText() {
        return visibleChoicesKnown() && selectedLifeformId != null
                ? Component.translatable("jojo_ripples.lifeform.selected", GoldExperienceLifeforms.displayName(selectedLifeformId))
                : Component.translatable("jojo_ripples.lifeform.none");
    }

    private boolean visibleChoicesKnown() {
        GoldExperienceLifeformState state = playerState();
        return state != null && state.hasAnyMetLifeforms();
    }

    private void renderHeader(GuiGraphics graphics) {
        int x = layout.x();
        int y = layout.y();
        Component name = title.copy().withStyle(ChatFormatting.BOLD);
        if (layout.summaryLine()) {
            PaperKit.text(graphics, font, name, x + (layout.width() - font.width(name)) / 2, layout.titleY(), PaperKit.INK);
            String selected = PaperKit.oneLine(font, selectedText().getString(), layout.inner());
            PaperKit.text(graphics, font, Component.literal(selected), x + (layout.width() - font.width(selected)) / 2,
                    layout.summaryY(), PaperKit.INK_SOFT);
        }
        else {
            String shownTitle = PaperKit.oneLine(font, name.getString(), layout.inner() / 2);
            PaperKit.text(graphics, font, Component.literal(shownTitle).withStyle(ChatFormatting.BOLD), layout.left(),
                    layout.titleY(), PaperKit.INK);
            int room = layout.inner() - font.width(shownTitle) - 8;
            String selected = PaperKit.oneLine(font, selectedText().getString(), room);
            PaperKit.text(graphics, font, Component.literal(selected), layout.right() - font.width(selected), layout.titleY(),
                    PaperKit.INK_SOFT);
        }
        int lineY = layout.listTop() - 3;
        graphics.fill(x + 8, lineY, x + layout.width() - 8, lineY + 1, PaperKit.LINE_SOFT);
        int footerY = layout.footerTop();
        graphics.fill(x + 8, footerY, x + layout.width() - 8, footerY + 1, PaperKit.LINE_SOFT);
    }

    private void renderEmptyMessage(GuiGraphics graphics) {
        if (!visibleChoices.isEmpty()) {
            return;
        }
        Component text = visibleChoicesKnown() ? Component.translatable(TEXT + "no_match")
                : Component.translatable("jojo_ripples.lifeform.none");
        List<FormattedCharSequence> lines = PaperKit.fit(font, text.getString(), layout.inner() - 20, 3);
        int y = (layout.listTop() + layout.listBottom()) / 2 - lines.size() * 5;
        for (FormattedCharSequence line : lines) {
            PaperKit.text(graphics, font, line, layout.x() + (layout.width() - font.width(line)) / 2, y, PaperKit.INK_SOFT);
            y += 10;
        }
    }

    private void renderTooltips(int mouseX, int mouseY) {
        long now = Util.getMillis();
        Object hovered = null;
        List<Component> text = null;
        ScreenRectangle anchor = null;
        GoldExperienceChooseLifeformList.Cell cell = list != null ? list.cellAt(mouseX, mouseY) : null;
        if (cell != null) {
            hovered = cell;
        }
        else if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY) && !searchBox.isFocused()) {
            hovered = searchBox;
        }
        else if (clearSearchButton != null && clearSearchButton.active && clearSearchButton.isMouseOver(mouseX, mouseY)) {
            hovered = clearSearchButton;
        }
        if (hovered != hoverTarget) {
            hoverTarget = hovered;
            hoverSince = now;
        }
        if (hovered != null && now - hoverSince >= TOOLTIP_DELAY_MILLIS) {
            if (hovered == cell) {
                text = choiceTooltip(cell);
                anchor = cellRectangle(cell, mouseX);
            }
            else if (hovered == searchBox) {
                text = List.of(Component.translatable(TEXT + "search_tip"));
                anchor = rectangle(searchBox);
            }
            else {
                text = List.of(Component.translatable(TEXT + "clear_search"));
                anchor = rectangle(clearSearchButton);
            }
        }
        else if (hovered == null && minecraft.getLastInputType().isKeyboard() && list != null && list.focusedCell() != null
                && getFocused() == list) {
            GoldExperienceChooseLifeformList.Cell focused = list.focusedCell();
            text = choiceTooltip(focused);
            anchor = cellRectangle(focused, focused.getX() + focused.getWidth() / 2);
        }
        if (text == null || anchor == null) {
            return;
        }
        int wrap = Math.min(220, Math.max(120, width - 40));
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int i = 0; i < text.size(); i++) {
            int color = i == 0 ? PaperKit.INK : i == text.size() - 1 && text.size() > 1 ? PaperKit.INK_FAINT : PaperKit.INK_SOFT;
            lines.addAll(font.split(text.get(i).copy().withColor(color & 0xFFFFFF), wrap));
        }
        setTooltipForNextRenderPass(lines, new BelowOrAboveWidgetTooltipPositioner(anchor), true);
        TooltipParams.set(TooltipParams.paperStyle(1));
    }

    private List<Component> choiceTooltip(GoldExperienceChooseLifeformList.Cell cell) {
        List<Component> lines = new ArrayList<>();
        lines.add(cell.choice.getDescription());
        lines.add(Component.literal(cell.id));
        String namespace = cell.choice.getId().withoutSubtype().getNamespace();
        if (!"minecraft".equals(namespace)) {
            lines.add(Component.literal(modName(cell.choice)));
        }
        lines.add(Component.translatable(TEXT + "hint"));
        return lines;
    }

    @Nullable
    private ScreenRectangle cellRectangle(GoldExperienceChooseLifeformList.Cell cell, int anchorX) {
        if (list == null) {
            return null;
        }
        int top = cell.getY();
        if (top + cell.getHeight() < list.getY() || top > list.getBottom()) {
            return null;
        }
        int clampedTop = Mth.clamp(top, list.getY(), list.getBottom());
        int clampedBottom = Mth.clamp(top + cell.getHeight(), list.getY(), list.getBottom());
        return new ScreenRectangle(anchorX, clampedTop, 1, Math.max(1, clampedBottom - clampedTop));
    }

    private static ScreenRectangle rectangle(AbstractWidget widget) {
        return new ScreenRectangle(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && list != null) {
            GoldExperienceChooseLifeformList.Cell cell = list.cellAt(mouseX, mouseY);
            if (cell != null) {
                toggleFavorite(cell.id);
                return true;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            hoverSince = Long.MAX_VALUE;
            return true;
        }
        return closeOnSecondClick(ClientKey.make(InputConstants.Type.MOUSE, button));
    }

    private void toggleFavorite(String lifeformId) {
        GoldExperienceLifeformState currentState = playerState();
        if (currentState == null || !currentState.hasMetLifeform(lifeformId)) {
            return;
        }
        if (currentState.isFavorite(lifeformId)) {
            PacketDistributor.sendToServer(ClGELifeformUiPacket.removeFavorite(lifeformId));
            currentState.setFavoriteFromSync(lifeformId, false);
        }
        else {
            PacketDistributor.sendToServer(ClGELifeformUiPacket.addFavorite(lifeformId));
            currentState.setFavoriteFromSync(lifeformId, true);
        }
        updateChoiceButtons();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closeOnSecondClick(ClientKey.make(keyCode == GLFW.GLFW_KEY_UNKNOWN
                ? InputConstants.Type.SCANCODE
                : InputConstants.Type.KEYSYM, keyCode == GLFW.GLFW_KEY_UNKNOWN ? scanCode : keyCode))) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean closeOnSecondClick(ClientKey keyPressed) {
        if (!holdMode && keyHeld != null && keyHeld.equals(keyPressed)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        GoldExperienceLifeformState state = playerState();
        if (state != null && state.hasAnyNewUnseenLifeforms()) {
            PacketDistributor.sendToServer(ClGELifeformUiPacket.clearUnseen());
            state.clearNewUnseenLifeformsFromSync();
        }
        super.onClose();
    }

    /** One of the All / Favourites / New filters: the one in force is filled. */
    private final class FilterChoice extends AbstractButton {
        private final FilterMode mode;

        private FilterChoice(int x, int y, int width, FilterMode mode) {
            super(x, y, width, PaperKit.CONTROL_HEIGHT, mode.label());
            this.mode = mode;
        }

        @Override
        public void onPress() {
            setFilterMode(mode);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean on = savedFilterMode == mode;
            if (on) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), PaperKit.ACCENT);
                graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, PaperKit.ACCENT_FILL);
            }
            else {
                graphics.blitSprite(PaperButton.ENABLED, getX(), getY(), width, height);
                if (isHovered()) {
                    graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, PaperKit.PANEL);
                }
            }
            PaperButton.renderScrollingString(graphics, font, getMessage(), getX() + width / 2, getX() + 3, getY(),
                    getRight() - 3, getBottom(), on ? PaperKit.ACCENT : PaperKit.INK, false);
            if (isFocused()) {
                graphics.renderOutline(getX() - 1, getY() - 1, width + 2, height + 2, PaperKit.INK);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
