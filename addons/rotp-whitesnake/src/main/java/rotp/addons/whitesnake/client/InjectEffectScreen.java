package rotp.addons.whitesnake.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.buffdisc.DiscItem;
import rotp.addons.whitesnake.buffdisc.EffectRegistry;
import rotp.addons.whitesnake.network.InjectEffectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * PATCH1 InjectEffectUI: a searchable 5 x 3 page of every injectable effect, with favourites; picking one sends
 * it to the server and closes. Port fixes: the star toggles the favourite (PATCH1's effect button, listed first,
 * took that click), typing keeps the search text (PATCH1 rebuilt the field empty on every key), the hover name is
 * a normal tooltip (PATCH1 clamped it to the button's own width and drew it off screen), and the background is the
 * lower-case buff.png the screen asks for (PATCH1 shipped Buff.png).
 */
public final class InjectEffectScreen extends Screen {
    private static final ResourceLocation BG_TEXTURE =
            WhitesnakeAddon.id("textures/gui/buff.png");
    private static final int BUTTON_SIZE = 30;
    private static final int CELL = BUTTON_SIZE + 4;
    private static final int COLS = 5;
    private static final int ROWS = 3;
    private static final int PER_PAGE = COLS * ROWS;
    private static final int GRID_WIDTH = 166;
    private static final int GRID_HEIGHT = 98;
    private static final int TEXTURE_SIZE = 128;
    private static final String[] SHORT_NAME_KEYWORDS = {
            "\u901f\u5ea6", "\u8fdf\u7f13", "\u6025\u8feb", "\u6316\u6398", "\u529b\u91cf", "\u77ac\u95f4", "\u8df3\u8dc3",
            "\u53cd\u80c3", "\u518d\u751f", "\u6297\u6027", "\u9632\u706b", "\u6c34\u4e0b", "\u9690\u8eab", "\u5931\u660e",
            "\u591c\u89c6", "\u9965\u997f", "\u865a\u5f31", "\u4e2d\u6bd2", "\u51cb\u96f6", "\u751f\u547d", "\u5438\u6536",
            "\u9971\u548c", "\u53d1\u5149", "\u98d8\u6d6e", "\u5e78\u8fd0", "\u9709\u8fd0", "\u7f13\u964d", "\u6f6e\u6d8c",
            "\u6d77\u8c5a", "\u4e0d\u7965", "\u6751\u5e84", "\u9ed1\u6697", "\u7206\u70b8"};
    // PATCH1 kept these in the client player's persistent data, which lasted until relog or respawn
    private static final Set<ResourceLocation> FAVORITES =
            new LinkedHashSet<>();

    private final List<ResourceLocation> allEffects;
    private final List<ResourceLocation> filteredEffects =
            new ArrayList<>();
    private final List<AbstractWidget> pageWidgets = new ArrayList<>();
    private EditBox searchField;
    private String searchText = "";
    private boolean showFavoritesOnly;
    private int page;
    private int totalPages = 1;

    public InjectEffectScreen() {
        super(Component.translatable(
                "gui.rotp_whitesnake.inject_effect.title"));
        allEffects = new ArrayList<>(EffectRegistry.getAllEffects());
        allEffects.sort(Comparator.comparing(ResourceLocation::toString));
        updateFilteredAndPages();
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new InjectEffectScreen());
    }

    @Override
    protected void init() {
        super.init();
        searchField = new EditBox(
                font,
                width / 2 - 80,
                42,
                160,
                16,
                Component.translatable(
                        "gui.rotp_whitesnake.inject_effect.search"));
        searchField.setMaxLength(20);
        searchField.setValue(searchText);
        searchField.setResponder(text -> {
            searchText = text;
            page = 0;
            updateFilteredAndPages();
            rebuildPage();
        });
        addRenderableWidget(searchField);
        addRenderableWidget(Button.builder(
                        Component.literal(showFavoritesOnly
                                ? "\u2605" : "\u2606"),
                        button -> {
                            showFavoritesOnly = !showFavoritesOnly;
                            button.setMessage(Component.literal(
                                    showFavoritesOnly
                                            ? "\u2605" : "\u2606"));
                            page = 0;
                            updateFilteredAndPages();
                            rebuildPage();
                        })
                .bounds(width / 2 + 85, 42, 50, 16)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable(
                                "gui.rotp_whitesnake.inject_effect.close"),
                        button -> onClose())
                .bounds(width / 2 - 30, height - 30, 60, 20)
                .build());
        rebuildPage();
    }

    private void rebuildPage() {
        pageWidgets.forEach(this::removeWidget);
        pageWidgets.clear();
        int startX = (width - GRID_WIDTH) / 2;
        int startY = (height - GRID_HEIGHT) / 2 + 15;
        List<ResourceLocation> pageEffects = getPageEffects();
        List<AbstractWidget> effectButtons = new ArrayList<>();
        List<AbstractWidget> favoriteButtons = new ArrayList<>();
        for (int i = 0; i < pageEffects.size(); i++) {
            ResourceLocation effectId = pageEffects.get(i);
            int x = startX + i % COLS * CELL;
            int y = startY + i / COLS * CELL;
            String fullName = getEffectDisplayName(effectId);
            EffectButton effectButton = new EffectButton(
                    x, y, getShortDisplayName(fullName),
                    button -> inject(effectId));
            effectButton.setTooltip(
                    Tooltip.create(Component.literal(fullName)));
            effectButtons.add(effectButton);
            favoriteButtons.add(new FavoriteButton(
                    x + BUTTON_SIZE - 12,
                    y + 2,
                    FAVORITES.contains(effectId),
                    button -> {
                        toggleFavorite(effectId);
                        rebuildPage();
                    }));
        }
        // the star sits on its effect button: it takes the click first and is drawn over it
        favoriteButtons.forEach(this::addWidget);
        effectButtons.forEach(this::addWidget);
        effectButtons.forEach(this::addRenderableOnly);
        favoriteButtons.forEach(this::addRenderableOnly);
        pageWidgets.addAll(favoriteButtons);
        pageWidgets.addAll(effectButtons);

        int pageButtonY = startY + GRID_HEIGHT + 8;
        int pageButtonX = width / 2 - 50;
        if (page > 0) {
            pageWidgets.add(addRenderableWidget(Button.builder(
                            Component.literal("<"),
                            button -> {
                                page--;
                                rebuildPage();
                            })
                    .bounds(pageButtonX, pageButtonY, 40, 20)
                    .build()));
        }
        if (page < totalPages - 1) {
            pageWidgets.add(addRenderableWidget(Button.builder(
                            Component.literal(">"),
                            button -> {
                                page++;
                                rebuildPage();
                            })
                    .bounds(pageButtonX + 60, pageButtonY, 40, 20)
                    .build()));
        }
    }

    private void inject(ResourceLocation effectId) {
        PacketDistributor.sendToServer(
                new InjectEffectPayload(effectId));
        onClose();
    }

    private void updateFilteredAndPages() {
        String query = searchText.toLowerCase(Locale.ROOT);
        filteredEffects.clear();
        for (ResourceLocation id : allEffects) {
            if (!query.isEmpty()
                    && !getEffectDisplayName(id)
                            .toLowerCase(Locale.ROOT).contains(query)
                    && !id.toString().toLowerCase(Locale.ROOT)
                            .contains(query)) {
                continue;
            }
            if (showFavoritesOnly && !FAVORITES.contains(id)) {
                continue;
            }
            filteredEffects.add(id);
        }
        totalPages = Math.max(
                1, (filteredEffects.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1));
    }

    private List<ResourceLocation> getPageEffects() {
        int start = page * PER_PAGE;
        if (start >= filteredEffects.size()) {
            return List.of();
        }
        return List.copyOf(filteredEffects.subList(
                start,
                Math.min(start + PER_PAGE, filteredEffects.size())));
    }

    private void toggleFavorite(ResourceLocation effectId) {
        if (!FAVORITES.remove(effectId)) {
            FAVORITES.add(effectId);
        }
        updateFilteredAndPages();
    }

    private static String getEffectDisplayName(ResourceLocation id) {
        return DiscItem.effectDisplayName(id).getString();
    }

    private static String getShortDisplayName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "?";
        }
        if (fullName.length() <= 2) {
            return fullName;
        }
        for (String keyword : SHORT_NAME_KEYWORDS) {
            if (fullName.contains(keyword)) {
                return keyword;
            }
        }
        return fullName.substring(0, 2);
    }

    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        graphics.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(
                font, title, width / 2, 15, 0xFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "gui.rotp_whitesnake.inject_effect.unlocked",
                        filteredEffects.size() + " / "
                                + allEffects.size()),
                width / 2,
                30,
                0xAAAAAA);
        if (totalPages > 1) {
            graphics.drawCenteredString(
                    font,
                    (page + 1) + " / " + totalPages,
                    width / 2,
                    height / 2 + GRID_HEIGHT / 2 + 25,
                    0x888888);
        }
        if (filteredEffects.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(showFavoritesOnly
                            ? "gui.rotp_whitesnake.inject_effect.no_favorites"
                            : "gui.rotp_whitesnake.inject_effect.empty"),
                    width / 2,
                    height / 2,
                    0xFF5555);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class EffectButton extends Button {
        private final String shortName;

        private EffectButton(
                int x, int y, String shortName, OnPress onPress) {
            super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty(),
                    onPress, DEFAULT_NARRATION);
            this.shortName = shortName;
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(
                    BG_TEXTURE,
                    getX(),
                    getY(),
                    0.0F,
                    0.0F,
                    width,
                    height,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE);
            RenderSystem.disableBlend();
            if (isHovered()) {
                graphics.fill(
                        getX(), getY(),
                        getX() + width, getY() + height,
                        0xAAFFAA00);
            }
            graphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    shortName,
                    getX() + BUTTON_SIZE / 2,
                    getY() + BUTTON_SIZE / 2 - 4,
                    0xFFFFFF);
        }
    }

    private static final class FavoriteButton extends Button {
        private final boolean favorite;

        private FavoriteButton(
                int x, int y, boolean favorite, OnPress onPress) {
            super(x, y, 10, 10, Component.empty(),
                    onPress, DEFAULT_NARRATION);
            this.favorite = favorite;
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            graphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    favorite ? "\u2605" : "\u2606",
                    getX() + 5,
                    getY() - 1,
                    favorite ? 0xFFFF00 : 0x666666);
        }
    }
}
