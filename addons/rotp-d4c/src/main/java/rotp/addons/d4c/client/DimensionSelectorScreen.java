package rotp.addons.d4c.client;

import java.util.List;
import java.util.UUID;

import rotp.addons.d4c.network.OpenDimensionSelectorPayload;
import rotp.addons.d4c.network.SelectDimensionPayload;
import rotp.addons.d4c.travel.D4CDimensionTargets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DimensionSelectorScreen extends Screen {
    private static final int BUTTON_WIDTH = 156;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private final UUID sessionId;
    private final long expiryGameTick;
    private final List<ResourceLocation> targets;
    private final ResourceLocation selected;
    private int page;
    private int pages = 1;
    private int pageLabelY = -1;

    public DimensionSelectorScreen(
            OpenDimensionSelectorPayload payload) {
        super(Component.translatable(
                "rotp_d4c.screen.dimension_selector"));
        sessionId = payload.sessionId();
        expiryGameTick = payload.expiryGameTick();
        targets = payload.targets();
        selected = payload.selected();
    }

    @Override
    protected void init() {
        int columns = width >= BUTTON_WIDTH * 2 + 52 ? 2 : 1;
        // a pack with many dimensions gets pages instead of running off
        // the screen
        int fitRows = Math.max(
                1,
                (height - 42 - BUTTON_HEIGHT - GAP * 3)
                        / (BUTTON_HEIGHT + GAP));
        int perPage = fitRows * columns;
        pages = Math.max(1, (targets.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int first = page * perPage;
        int shown = Math.min(perPage, targets.size() - first);
        int rows = (shown + columns - 1) / columns;
        int totalWidth = columns * BUTTON_WIDTH
                + (columns - 1) * GAP;
        int startX = (width - totalWidth) / 2;
        int startY = Math.max(
                42,
                (height - rows * (BUTTON_HEIGHT + GAP)) / 2);

        for (int slot = 0; slot < shown; slot++) {
            ResourceLocation target = targets.get(first + slot);
            int column = slot % columns;
            int row = slot / columns;
            Component label = target.equals(selected)
                    ? Component.literal("* ").append(
                            D4CDimensionTargets.label(target))
                    : D4CDimensionTargets.label(target);
            addRenderableWidget(Button.builder(
                            label,
                            button -> select(target))
                    .bounds(
                            startX + column * (BUTTON_WIDTH + GAP),
                            startY + row * (BUTTON_HEIGHT + GAP),
                            BUTTON_WIDTH,
                            BUTTON_HEIGHT)
                    .build());
        }
        if (pages > 1) {
            int y = startY + rows * (BUTTON_HEIGHT + GAP) + GAP;
            Button previous = addRenderableWidget(Button.builder(
                            Component.literal("<"),
                            button -> turn(-1))
                    .bounds(width / 2 - 52, y, 20, BUTTON_HEIGHT)
                    .build());
            Button next = addRenderableWidget(Button.builder(
                            Component.literal(">"),
                            button -> turn(1))
                    .bounds(width / 2 + 32, y, 20, BUTTON_HEIGHT)
                    .build());
            previous.active = page > 0;
            next.active = page < pages - 1;
            pageLabelY = y + 6;
        }
        else {
            pageLabelY = -1;
        }
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages - 1, page + delta));
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft != null
                && minecraft.level != null
                && minecraft.level.getGameTime() > expiryGameTick) {
            onClose();
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        graphics.drawCenteredString(
                font,
                title,
                width / 2,
                18,
                0xFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "rotp_d4c.screen.dimension_selector.current",
                        D4CDimensionTargets.label(selected)),
                width / 2,
                30,
                0xB8C7D9);
        if (pageLabelY >= 0) {
            graphics.drawCenteredString(
                    font,
                    Component.literal((page + 1) + " / " + pages),
                    width / 2,
                    pageLabelY,
                    0xB8C7D9);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * The 1.16 dimension selector (a title over its buttons) drew no background: the world stays clear behind it. In
     * 1.21 Screen.render draws the blurred, dimmed background by itself, and the port also called it first, so it ran
     * twice; it is switched off here the way the host's own GameModeSwitcherScreen does it.
     */
    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void select(ResourceLocation target) {
        if (!targets.contains(target)) {
            return;
        }
        PacketDistributor.sendToServer(
                new SelectDimensionPayload(sessionId, target));
        onClose();
    }
}
