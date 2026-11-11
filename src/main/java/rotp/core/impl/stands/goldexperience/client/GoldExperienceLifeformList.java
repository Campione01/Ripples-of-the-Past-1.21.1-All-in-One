package rotp.core.impl.stands.goldexperience.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import rotp.core.config.client.screen.PaperKit;
import rotp.core.impl.stands.goldexperience.GECreatedLifeformEffect;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster.Row;
import rotp.core.network.s2c.TrGELifeformRosterPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** The rows of the lifeform menu: one a lifeform, nearest first, with its portrait, where it is and how old it is. */
final class GoldExperienceLifeformList extends PaperKit.PaperListBase<GoldExperienceLifeformList.Entry> {
    static final int ROW_HEIGHT = 30;
    private static final int PORTRAIT = 24;
    private static final int TEXT_X = PORTRAIT + 7;
    private static final String TEXT = GoldExperienceLifeformText.TEXT;
    private static final int TILE = 0xFFE4E7EC;
    // narrower than this, a row drops the words around its numbers
    private static final int ROOMY_WIDTH = 236;

    private final GoldExperienceLifeformsScreen screen;

    GoldExperienceLifeformList(Minecraft minecraft, GoldExperienceLifeformsScreen screen, int x, int y, int width, int height) {
        super(minecraft, x, y, width, height, ROW_HEIGHT);
        this.screen = screen;
    }

    /**
     * Shows the server's rows. With {@code keepOrder} the rows already shown stay where they are, so the one under the
     * pointer or the keyboard is not moved away; new ones go to the end.
     */
    void show(List<Row> rows, boolean keepOrder) {
        Map<UUID, Row> fresh = new HashMap<>();
        rows.forEach(row -> fresh.put(row.id(), row));
        Map<UUID, Entry> shown = new HashMap<>();
        children().forEach(entry -> shown.put(entry.row.id(), entry));
        List<Entry> next = new ArrayList<>();
        if (keepOrder) {
            for (Entry entry : children()) {
                Row row = fresh.remove(entry.row.id());
                if (row != null) {
                    entry.row = row;
                    next.add(entry);
                }
            }
            rows.stream().filter(row -> fresh.containsKey(row.id())).forEach(row -> next.add(new Entry(row)));
        }
        else {
            List<Row> sorted = new ArrayList<>(rows);
            sorted.sort(nearestFirst());
            for (Row row : sorted) {
                Entry entry = shown.get(row.id());
                if (entry != null) {
                    entry.row = row;
                }
                next.add(entry != null ? entry : new Entry(row));
            }
        }
        Entry focused = getFocused();
        replaceEntries(next);
        if (focused != null) {
            if (next.contains(focused)) {
                setSelected(focused);
            }
            else {
                setFocused(null);
            }
        }
        // only when rows went: setting the scroll stops a glide the wheel started
        if (getScrollAmount() > getMaxScroll()) {
            setScrollAmount(getMaxScroll());
        }
    }

    private Comparator<Row> nearestFirst() {
        LocalPlayer player = minecraft.player;
        ResourceLocation dimension = player != null ? player.level().dimension().location() : null;
        Vec3 from = player != null ? player.position() : Vec3.ZERO;
        return Comparator.comparing((Row row) -> dimension == null || !dimension.equals(row.dimension()))
                .thenComparingDouble(row -> {
                    Vec3 pos = screen.livePos(row, 1.0F);
                    return pos != null ? pos.distanceToSqr(from) : Double.MAX_VALUE;
                });
    }

    @Nullable
    Entry hoveredEntry() {
        return getHovered();
    }

    int topOf(Entry entry) {
        return getRowTop(children().indexOf(entry));
    }

    final class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private Row row;
        private final RowButton button = new RowButton();

        private Entry(Row row) {
            this.row = row;
        }

        UUID id() {
            return row.id();
        }

        private boolean revertible() {
            return row.place().revertible() && !screen.isPending(row.id());
        }

        private boolean sameDimension() {
            LocalPlayer player = minecraft.player;
            return player != null && player.level().dimension().location().equals(row.dimension());
        }

        private Component kind() {
            Component kind = TrGELifeformRosterPacket.kindName(row.type());
            return kind.getString().isEmpty() ? Component.translatable(TEXT + "unknown_kind") : kind;
        }

        Component title() {
            return row.name() != null
                    ? Component.empty().append(row.name()).append("  ").append(kind())
                    : kind();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = screen.font();
            boolean revertible = revertible();
            boolean pending = screen.isPending(row.id());
            int rowRight = left + width;
            button.active = revertible;
            button.setRectangle(width + 4, ROW_HEIGHT - 2, left - 2, top - 2);
            if (hovering && revertible) {
                graphics.fill(left - 2, top - 2, rowRight + 2, top + ROW_HEIGHT - 4, PaperKit.HOVER);
            }

            int tileX = left + 1;
            graphics.fill(tileX - 1, top - 1, tileX + PORTRAIT + 1, top + PORTRAIT + 1, PaperKit.LINE_SOFT);
            graphics.fill(tileX, top, tileX + PORTRAIT, top + PORTRAIT, TILE);
            LifeformPortrait.render(graphics, screen.liveEntity(row), row.type(), tileX, top, PORTRAIT,
                    !row.place().revertible(), partialTick);

            boolean roomy = width >= ROOMY_WIDTH;
            int textX = left + TEXT_X;
            int lineOne = top + 3;
            int lineTwo = top + 14;
            int strong = revertible ? PaperKit.INK : PaperKit.INK_SOFT;
            int soft = revertible ? PaperKit.INK_SOFT : PaperKit.INK_FAINT;

            int oneRight = rowRight - 3;
            Vec3 pos = screen.livePos(row, partialTick);
            LocalPlayer player = minecraft.player;
            if (pending) {
                String dots = "...";
                PaperKit.text(graphics, font, Component.literal(dots), oneRight - font.width(dots), lineOne, soft);
                oneRight -= font.width(dots) + 6;
            }
            else if (sameDimension() && pos != null && player != null) {
                double distance = Math.sqrt(pos.distanceToSqr(player.position()));
                String text = Component.translatable(TEXT + "distance", Math.round(distance)).getString();
                PaperKit.text(graphics, font, Component.literal(text), oneRight - font.width(text), lineOne, soft);
                oneRight -= font.width(text) + 4;
                if (distance >= 1.5) {
                    float yaw = GoldExperienceLifeformText.relativeYaw(player.getX(), player.getZ(), player.getYRot(), pos.x, pos.z);
                    arrow(graphics, oneRight - 4, lineOne + 3.5F, yaw, soft);
                    oneRight -= 11;
                }
            }
            else {
                String dimension = PaperKit.oneLine(font, GoldExperienceLifeformText.dimensionName(row.dimension()).getString(),
                        Math.max(width / 3, 40));
                PaperKit.text(graphics, font, Component.literal(dimension), oneRight - font.width(dimension), lineOne, soft);
                oneRight -= font.width(dimension) + 6;
            }
            drawTitle(graphics, font, textX, lineOne, oneRight - textX, strong, soft);

            int twoRight = rowRight - 3;
            String age = roomy
                    ? Component.translatable(TEXT + "age", GoldExperienceLifeformText.age(screen.liveAge(row))).getString()
                    : GoldExperienceLifeformText.age(screen.liveAge(row));
            PaperKit.text(graphics, font, Component.literal(age), twoRight - font.width(age), lineTwo, soft);
            twoRight -= font.width(age) + 5;
            if (screen.liveCounts(row)) {
                String counts = Component.translatable(TEXT + "counts").getString();
                int pillWidth = font.width(counts) + 6;
                graphics.fill(twoRight - pillWidth, lineTwo - 2, twoRight, lineTwo + 9,
                        revertible ? PaperKit.ACCENT_FILL : PaperKit.LINE_SOFT);
                PaperKit.text(graphics, font, Component.literal(counts), twoRight - pillWidth + 3, lineTwo,
                        revertible ? PaperKit.ACCENT : PaperKit.INK_FAINT);
                twoRight -= pillWidth + 5;
            }
            String place = PaperKit.oneLine(font, placeText(pos).getString(), twoRight - textX);
            PaperKit.text(graphics, font, Component.literal(place), textX, lineTwo, soft);

            if (button.isFocused()) {
                graphics.renderOutline(left - 2, top - 2, width + 4, ROW_HEIGHT - 2, PaperKit.INK);
            }
        }

        private void drawTitle(GuiGraphics graphics, Font font, int x, int y, int room, int strong, int soft) {
            if (row.name() == null) {
                String kind = PaperKit.oneLine(font, kind().getString(), room);
                PaperKit.text(graphics, font, Component.literal(kind), x, y, strong);
                return;
            }
            String name = PaperKit.oneLine(font, row.name().getString(), room);
            PaperKit.text(graphics, font, Component.literal(name), x, y, strong);
            int kindX = x + font.width(name) + 6;
            if (room - (kindX - x) > 20) {
                String kind = PaperKit.oneLine(font, kind().getString(), room - (kindX - x));
                PaperKit.text(graphics, font, Component.literal(kind), kindX, y, soft);
            }
        }

        private Component placeText(@Nullable Vec3 pos) {
            if (pos == null) {
                return Component.translatable(TEXT + "unknown_place");
            }
            String coordinates = GoldExperienceLifeformText.coordinates(pos);
            return switch (row.place()) {
                case FORMING -> Component.translatable(TEXT + "place.forming", coordinates);
                case UNLOADED -> Component.translatable(TEXT + "place.unloaded", coordinates);
                default -> Component.literal(coordinates);
            };
        }

        List<Component> tooltip() {
            List<Component> lines = new ArrayList<>();
            lines.add(title());
            if (!row.source().getString().isEmpty()) {
                lines.add(Component.translatable(TEXT + "tooltip.source", row.source()));
            }
            Vec3 pos = screen.livePos(row, 1.0F);
            Component dimension = GoldExperienceLifeformText.dimensionName(row.dimension());
            String coordinates = pos != null ? GoldExperienceLifeformText.coordinates(pos) : "?";
            lines.add(switch (row.place()) {
                case HERE -> Component.translatable(TEXT + "tooltip.here", dimension, coordinates);
                case FORMING -> Component.translatable(TEXT + "tooltip.forming", dimension, coordinates);
                case ELSEWHERE -> Component.translatable(TEXT + "tooltip.elsewhere", dimension, coordinates);
                case UNLOADED -> Component.translatable(TEXT + "tooltip.unloaded", dimension, coordinates);
            });
            long age = screen.liveAge(row);
            lines.add(screen.liveCounts(row)
                    ? Component.translatable(TEXT + "tooltip.counts",
                            GoldExperienceLifeformText.age(GECreatedLifeformEffect.COUNTED_TICKS - age))
                    : Component.translatable(TEXT + "tooltip.free"));
            if (revertible()) {
                lines.add(Component.translatable(TEXT + "tooltip.revert"));
            }
            return lines;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(button);
        }

        private final class RowButton extends AbstractButton {
            private RowButton() {
                super(0, 0, 100, ROW_HEIGHT - 2, Component.empty());
            }

            @Override
            public Component getMessage() {
                return title();
            }

            @Override
            public void onPress() {
                if (revertible()) {
                    screen.requestRevert(row);
                }
            }

            // the row draws itself; the button only takes the click, the keys and the focus
            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            }

            @Override
            protected void updateWidgetNarration(NarrationElementOutput output) {
                defaultButtonNarrationText(output);
            }
        }
    }

    // a dart pointing where the lifeform is, turned from straight ahead (up) by the yaw
    private static void arrow(GuiGraphics graphics, float x, float y, float yaw, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(yaw));
        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        buffer.addVertex(matrix, 0.0F, -4.5F, 0.0F).setColor(color);
        buffer.addVertex(matrix, -3.5F, 3.5F, 0.0F).setColor(color);
        buffer.addVertex(matrix, 0.0F, 1.5F, 0.0F).setColor(color);
        buffer.addVertex(matrix, 3.5F, 3.5F, 0.0F).setColor(color);
        graphics.pose().popPose();
    }
}
