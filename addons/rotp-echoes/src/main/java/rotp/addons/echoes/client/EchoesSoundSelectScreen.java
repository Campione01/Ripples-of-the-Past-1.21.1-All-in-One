package rotp.addons.echoes.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.network.EchoesSoundPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * R772 F11: ACT1's sound picker (the master's SoundSelectScreen, SoundSelectListScreen, SoundList, SoundEntry,
 * SoundIcon and SoundRenameScreen): the game's sounds by namespace, each with its mob's icon, its name (the player's
 * own, else its subtitle) and a rename button; filters, a search box and "Clear Sound" on the right. The master kept a
 * Favourites filter its list had no way to fill; each row here has a star that does.
 */
public final class EchoesSoundSelectScreen extends Screen {
    private static final int LIST_WIDTH = 180;
    private static final int ROW_HEIGHT = 13;
    private static final int FILTER_STEP = 16;
    private static final Set<String> COLLAPSED = new HashSet<>();
    private static final Map<ResourceLocation, Optional<ResourceLocation>> ICONS = new HashMap<>();
    private static final Map<String, String> ICON_ALIASES = Map.of(
            "mooshroom", "red_mooshroom",
            "horse", "horse_white",
            "parrot", "parrot_red_blue",
            "tropical_fish", "tropical_a",
            "ender_dragon", "dragon",
            "turtle", "big_sea_turtle",
            "polar_bear", "polarbear",
            "snow_golem", "snowman");
    private static String savedSearch = "";
    private static Filter savedFilter = Filter.ALL;

    private enum Filter {
        ALL("all", id -> true),
        FAVORITES("favorites", EchoesSoundClient::isFavorite),
        ENTITY("entity", id -> id.getPath().startsWith("entity.") || id.getPath().startsWith("mob.")),
        BLOCK("block", id -> id.getPath().startsWith("block.")),
        AMBIENT("ambient", id -> id.getPath().startsWith("ambient.") || id.getPath().startsWith("weather.")),
        ITEM("item", id -> id.getPath().startsWith("item.") || id.getPath().startsWith("player."));

        private final String key;
        private final Predicate<ResourceLocation> test;

        Filter(String key, Predicate<ResourceLocation> test) {
            this.key = key;
            this.test = test;
        }
    }

    private SoundList list;
    private final List<Button> filterButtons = new ArrayList<>();

    public EchoesSoundSelectScreen() {
        super(Component.translatable("rotp_echoes.message.sound_select_title"));
    }

    public static void openWindowOnClick(LivingEntity user) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null && user == mc.player) {
            mc.setScreen(new EchoesSoundSelectScreen());
        }
    }

    @Override
    protected void init() {
        list = new SoundList(minecraft, LIST_WIDTH, height - 80 - 27, 27);
        list.setX(4);
        addRenderableWidget(list);
        filterButtons.clear();
        Filter[] filters = Filter.values();
        // The master started the six filters at height - 130, so Ambient and Item ran under the search box at
        // height - 55; the column now ends above it.
        int searchY = height - 55;
        int filterTop = searchY - 6 - filters.length * FILTER_STEP;
        for (int i = 0; i < filters.length; i++) {
            Filter filter = filters[i];
            Button button = Button.builder(Component.translatable(
                            "rotp_echoes.message.sound_select_filter." + filter.key), b -> {
                        savedFilter = filter;
                        refresh();
                    })
                    .bounds(width - 110, filterTop + i * FILTER_STEP, 100, 14).build();
            filterButtons.add(addRenderableWidget(button));
        }
        EditBox search = new EditBox(font, width - 110, searchY, 100, 20,
                Component.translatable("rotp_echoes.message.sound_select_search"));
        search.setHint(Component.translatable("rotp_echoes.message.sound_select_search"));
        search.setValue(savedSearch);
        search.setResponder(value -> {
            savedSearch = value;
            refresh();
        });
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("rotp_echoes.message.sound_select_clear"), b -> {
                    PacketDistributor.sendToServer(new EchoesSoundPayloads.Select(""));
                    onClose();
                })
                .bounds(width / 2 - 50, height - 25, 100, 20).build());
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < filterButtons.size(); i++) {
            filterButtons.get(i).active = Filter.values()[i] != savedFilter;
        }
        String search = savedSearch.toLowerCase(Locale.ROOT);
        Map<String, List<ResourceLocation>> byNamespace = new TreeMap<>();
        for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (!"minecraft".equals(id.getNamespace()) || !savedFilter.test.test(id)
                    || !search.isEmpty() && !id.getPath().toLowerCase(Locale.ROOT).contains(search)
                            && !displayName(id).getString().toLowerCase(Locale.ROOT).contains(search)) {
                continue;
            }
            byNamespace.computeIfAbsent(id.getNamespace(), key -> new ArrayList<>()).add(id);
        }
        list.show(byNamespace);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component title = getTitle().copy().withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, title, 4 + (LIST_WIDTH - font.width(title)) / 2, 11, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static Component displayName(ResourceLocation id) {
        String own = EchoesSoundClient.name(id);
        if (own != null && !own.isEmpty()) {
            return Component.literal(own);
        }
        String subtitle = "subtitles." + id.getPath().replace('/', '.');
        if (I18n.exists(subtitle)) {
            return Component.translatable(subtitle);
        }
        if (!"minecraft".equals(id.getNamespace())) {
            return Component.translatable("rotp_echoes.message.sound_select.unknown");
        }
        String[] parts = id.getPath().split("\\.");
        if (parts.length >= 3) {
            String mob = "entity.minecraft." + parts[1];
            return Component.literal((I18n.exists(mob) ? I18n.get(mob) : parts[1]) + " - " + parts[parts.length - 1]);
        }
        return Component.literal(parts.length == 2 ? parts[0] + " - " + parts[1] : id.getPath());
    }

    // master SoundIcon: the mob's icon by the sound's second (and third) word, with a few renamed mobs
    @Nullable
    private static ResourceLocation icon(ResourceLocation id) {
        return ICONS.computeIfAbsent(id, key -> {
            String[] parts = key.getPath().split("\\.");
            if (parts.length < 2) {
                return Optional.empty();
            }
            List<String> names = new ArrayList<>();
            if (parts.length >= 3) {
                names.add(parts[1] + "_" + parts[2]);
            }
            names.add(parts[1]);
            String alias = ICON_ALIASES.get(names.get(0));
            names.add(alias != null ? alias : ICON_ALIASES.getOrDefault(parts[1], parts[1]));
            for (String name : names) {
                ResourceLocation texture = RotpEchoes.id("textures/entity_icon/" + name + ".png");
                if (Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()) {
                    return Optional.of(texture);
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    private final class SoundList extends ContainerObjectSelectionList<Row> {
        SoundList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, ROW_HEIGHT);
        }

        void show(Map<String, List<ResourceLocation>> byNamespace) {
            clearEntries();
            byNamespace.forEach((namespace, ids) -> {
                addEntry(new NamespaceRow(namespace));
                if (!COLLAPSED.contains(namespace)) {
                    ids.sort(Comparator.comparing(ResourceLocation::getPath));
                    ids.forEach(id -> addEntry(new SoundRow(id)));
                }
            });
            setScrollAmount(getScrollAmount());
        }

        @Override
        public int getRowWidth() {
            return width;
        }

        @Override
        protected int getScrollbarPosition() {
            return getX() + width - 6;
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {}

        @Override
        protected void renderListSeparators(GuiGraphics graphics) {}
    }

    private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {}

    private final class NamespaceRow extends Row {
        private final String namespace;
        private final Button toggle;

        NamespaceRow(String namespace) {
            this.namespace = namespace;
            this.toggle = Button.builder(Component.literal(COLLAPSED.contains(namespace) ? "+" : "-"), b -> {
                if (!COLLAPSED.remove(namespace)) {
                    COLLAPSED.add(namespace);
                }
                refresh();
            }).bounds(0, 0, 10, 10).build();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(toggle);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(toggle);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                int mouseX, int mouseY, boolean hovering, float partialTick) {
            toggle.setPosition(left + 29, top);
            toggle.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawString(font, Component.literal(namespace).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC),
                    left + 43, top + 1, 0xFFFFFF);
        }
    }

    private final class SoundRow extends Row {
        private final ResourceLocation id;
        private final Button choose;
        private final Button rename;
        private final Button favorite;

        SoundRow(ResourceLocation id) {
            this.id = id;
            this.choose = Button.builder(displayName(id), b -> {
                PacketDistributor.sendToServer(new EchoesSoundPayloads.Select(id.toString()));
                onClose();
            }).bounds(0, 0, 110, ROW_HEIGHT).build();
            this.rename = Button.builder(Component.literal("✎"),
                    b -> minecraft.setScreen(new RenameScreen(id, EchoesSoundSelectScreen.this)))
                    .bounds(0, 0, 16, ROW_HEIGHT).build();
            this.favorite = Button.builder(star(EchoesSoundClient.isFavorite(id)), b -> {
                boolean now = !EchoesSoundClient.isFavorite(id);
                PacketDistributor.sendToServer(new EchoesSoundPayloads.Favorite(id.toString(), now));
                EchoesSoundClient.putFavorite(id, now);
                b.setMessage(star(now));
            }).bounds(0, 0, 14, ROW_HEIGHT).build();
        }

        private static Component star(boolean on) {
            return Component.literal(on ? "★" : "☆");
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(choose, rename, favorite);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(choose, rename, favorite);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                int mouseX, int mouseY, boolean hovering, float partialTick) {
            ResourceLocation texture = icon(id);
            if (texture != null) {
                graphics.blit(texture, left + 4, top + 1, 0.0F, 0.0F, 16, 16, 16, 16);
            }
            else {
                String path = id.getPath();
                String label = path.isEmpty() ? "?" : path.substring(0, Math.min(2, path.length()));
                graphics.drawString(font, label, left + 4 + (16 - font.width(label)) / 2, top + 4, 0xFFFFFF);
            }
            choose.setPosition(left + 25, top + 1);
            choose.render(graphics, mouseX, mouseY, partialTick);
            rename.setPosition(left + 140, top + 1);
            rename.render(graphics, mouseX, mouseY, partialTick);
            favorite.setPosition(left + 158, top + 1);
            favorite.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /** master SoundRenameScreen: the player's own name for a sound; an empty name drops it. */
    private static final class RenameScreen extends Screen {
        private final ResourceLocation id;
        private final EchoesSoundSelectScreen parent;
        private EditBox field;

        RenameScreen(ResourceLocation id, EchoesSoundSelectScreen parent) {
            super(Component.translatable("rotp_echoes.message.sound_select.rename_title"));
            this.id = id;
            this.parent = parent;
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int cy = height / 2;
            field = new EditBox(font, cx - 100, cy - 10, 200, 20,
                    Component.translatable("rotp_echoes.message.sound_select.rename_hint"));
            field.setMaxLength(rotp.addons.echoes.state.EchoesSoundService.MAX_NAME_LENGTH);
            field.setHint(Component.translatable("rotp_echoes.message.sound_select.rename_hint"));
            String current = EchoesSoundClient.name(id);
            if (current != null) {
                field.setValue(current);
            }
            addRenderableWidget(field);
            setInitialFocus(field);
            addRenderableWidget(Button.builder(Component.translatable("rotp_echoes.message.sound_select.rename_confirm"),
                    b -> {
                        PacketDistributor.sendToServer(new EchoesSoundPayloads.Rename(id.toString(), field.getValue()));
                        EchoesSoundClient.putName(id, field.getValue());
                        onClose();
                    }).bounds(cx - 100, cy + 20, 95, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("rotp_echoes.message.sound_select.rename_cancel"),
                    b -> onClose()).bounds(cx + 5, cy + 20, 95, 20).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, getTitle(), width / 2, height / 2 - 30, 0xFFFFFF);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
