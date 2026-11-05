package rotp.addons.highwaystar.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import rotp.addons.highwaystar.network.HighwayStarPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;

/** 1.16 TrackingScreen: the creatures the user has smelled, by initial, with a name search and filters. */
public final class TrackingScreen extends Screen {
    private static final int COLS = 6;
    private static final int PADDING = 12;
    private static final Component SELECT_TARGET = Component.translatable("gui.highwaystar.tracking",
            Component.translatable("gui.highwaystar.tracking").withStyle(ChatFormatting.WHITE));
    public static final ResourceLocation SLOT_TEX = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");

    private final List<UUID> allValidTargets = new ArrayList<>();
    private final List<UUID> filteredTargets = new ArrayList<>();
    private FilterType currentFilter = FilterType.ALL;
    private String searchText = "";
    private EditBox searchField;
    private Button[] filterButtons;
    private int scrollOffset = 0;
    private int startX;
    private int startY;
    private int totalW;
    private int totalH;
    private int listTop;
    private int viewHeight;

    public TrackingScreen(List<UUID> targets) {
        super(Component.empty());
        Player self = Minecraft.getInstance().player;
        UUID selfUUID = self != null ? self.getUUID() : null;
        for (UUID uuid : targets) {
            if (selfUUID == null || !uuid.equals(selfUUID)) {
                allValidTargets.add(uuid);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        totalW = 200;
        totalH = 175;
        startX = width / 2 - totalW / 2;
        startY = height / 2 - totalH / 2;
        searchField = new EditBox(font, startX + PADDING, startY + 26, totalW - 24, 16,
                Component.translatable("gui.highwaystar.tracking.search"));
        searchField.setResponder(s -> {
            searchText = s == null ? "" : s.toLowerCase(Locale.ROOT);
            updateFilteredList();
        });
        addRenderableWidget(searchField);
        FilterType[] modes = FilterType.values();
        filterButtons = new Button[modes.length];
        int btnW = (totalW - 24 - (modes.length - 1) * 2) / modes.length;
        for (int i = 0; i < modes.length; i++) {
            FilterType type = modes[i];
            int bx = startX + PADDING + i * (btnW + 2);
            int by = startY + 48;
            filterButtons[i] = Button.builder(type.name, b -> {
                currentFilter = type;
                for (Button btn : filterButtons) {
                    btn.active = true;
                }
                b.active = false;
                updateFilteredList();
            }).bounds(bx, by, btnW, 14).build();
            if (type == currentFilter) {
                filterButtons[i].active = false;
            }
            addRenderableWidget(filterButtons[i]);
        }
        listTop = startY + 68;
        viewHeight = totalH - 68 - PADDING;
        updateFilteredList();
    }

    private void updateFilteredList() {
        filteredTargets.clear();
        for (UUID uuid : allValidTargets) {
            LivingEntity entity = findEntity(uuid);
            if (entity != null && entity != Minecraft.getInstance().player
                    && matchesSearch(entity) && matchesFilter(entity)) {
                filteredTargets.add(uuid);
            }
        }
        scrollOffset = 0;
    }

    private static String nameOf(LivingEntity entity) {
        return entity.hasCustomName() ? entity.getCustomName().getString() : entity.getType().getDescription().getString();
    }

    private boolean matchesSearch(LivingEntity entity) {
        return searchText.isEmpty() || nameOf(entity).toLowerCase(Locale.ROOT).contains(searchText);
    }

    private boolean matchesFilter(LivingEntity entity) {
        if (entity instanceof HighwayStarSoleEntity || entity instanceof StandEntity) {
            return false;
        }
        boolean undead = entity.getType().is(EntityTypeTags.UNDEAD);
        return switch (currentFilter) {
            case ALL -> true;
            case PLAYER -> entity instanceof Player;
            case ANIMAL -> entity instanceof Animal;
            case HOSTILE -> entity instanceof Monster || entity instanceof Slime || entity instanceof Mob && undead;
            case NEUTRAL -> entity instanceof Mob && !(entity instanceof Monster) && !(entity instanceof Animal)
                    && !(entity instanceof Slime) && !undead;
            case NAMED -> entity.hasCustomName();
        };
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        gui.fill(startX, startY, startX + totalW, startY + totalH, 0xFFC6C6C6);
        gui.fill(startX + 2, startY + 2, startX + totalW - 2, startY + totalH - 2, 0xFF000000);
        gui.fill(startX + 3, startY + 3, startX + totalW - 3, startY + totalH - 3, 0xFF555555);
        gui.fill(startX + 4, startY + 4, startX + totalW - 4, startY + totalH - 4, 0xFF373737);
        gui.drawCenteredString(font, SELECT_TARGET, startX + totalW / 2, startY + 6, 0xFFFFFF);
        gui.fill(startX + PADDING, startY + 16, startX + totalW - PADDING, startY + 17, 0xFFAAAAAA);
        gui.fill(startX + PADDING, startY + 65, startX + totalW - PADDING, startY + 66, 0xFFAAAAAA);
        for (var widget : renderables) {
            widget.render(gui, mouseX, mouseY, partialTick);
        }

        int maxScroll = Math.max(0, filteredTargets.size() / COLS * 30 - viewHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        gui.enableScissor(startX + PADDING, listTop, startX + totalW - PADDING, listTop + viewHeight);
        LivingEntity hoveredEntity = null;
        for (int i = 0; i < filteredTargets.size(); i++) {
            LivingEntity entity = findEntity(filteredTargets.get(i));
            if (entity == null) {
                continue;
            }
            int x = startX + PADDING + (i % COLS) * 30;
            int y = listTop + (i / COLS) * 30 - scrollOffset;
            boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20
                    && mouseY >= listTop && mouseY <= listTop + viewHeight;
            gui.blit(SLOT_TEX, x, y, 7.0F, 83.0F, 20, 20, 256, 256);
            String name = nameOf(entity);
            String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            gui.drawString(font, letter, x + 6, y + 6, 0xFFFFFF, true);
            if (hovered) {
                gui.fill(x, y, x + 20, y + 20, 0x55FFFFFF);
                hoveredEntity = entity;
            }
        }
        gui.disableScissor();
        if (hoveredEntity != null) {
            gui.renderTooltip(font, hoveredEntity.getDisplayName(), mouseX, mouseY);
        }

        if (maxScroll > 0) {
            int barX = startX + totalW - 8;
            gui.fill(barX, listTop, barX + 4, listTop + viewHeight, 0xFF222222);
            int thumbH = Math.max(16, viewHeight * viewHeight / (viewHeight + maxScroll));
            int thumbY = listTop + (viewHeight - thumbH) * scrollOffset / maxScroll;
            gui.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xFFCCCCCC);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < startX || mouseX > startX + totalW || mouseY < listTop || mouseY > listTop + viewHeight) {
            return false;
        }
        scrollOffset -= (int) (scrollY * 26.0);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || mouseY < listTop || mouseY > listTop + viewHeight) {
            return false;
        }
        for (int i = 0; i < filteredTargets.size(); i++) {
            UUID uuid = filteredTargets.get(i);
            if (findEntity(uuid) == null) {
                continue;
            }
            int x = startX + PADDING + (i % COLS) * 30;
            int y = listTop + (i / COLS) * 30 - scrollOffset;
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) {
                HighwayStarPayloads.sendTrackTarget(uuid);
                Minecraft.getInstance().setScreen(null);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private LivingEntity findEntity(UUID uuid) {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && entity.getUUID().equals(uuid)) {
                return living;
            }
        }
        return null;
    }

    public enum FilterType {
        ALL("gui.highwaystar.filter.all"),
        PLAYER("gui.highwaystar.filter.player"),
        ANIMAL("gui.highwaystar.filter.animal"),
        NEUTRAL("gui.highwaystar.filter.neutral"),
        HOSTILE("gui.highwaystar.filter.hostile"),
        NAMED("gui.highwaystar.filter.named");

        public final Component name;

        FilterType(String key) {
            this.name = Component.translatable(key);
        }
    }
}
