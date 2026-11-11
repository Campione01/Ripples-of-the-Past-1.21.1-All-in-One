package rotp.core.impl.stands.goldexperience.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import rotp.core.client.input.InputHandler;
import rotp.core.client.input.controlscheme.ClientKey;
import rotp.core.client.ui.screen_jojomenu.IJojoMenuScreen;
import rotp.core.client.ui.screen_jojomenu.JojoMenuTabs;
import rotp.core.client.ui.screen_jojomenu.Tab;
import rotp.core.client.ui.screen_jojomenu.TabCategory;
import rotp.core.client.ui.utils.tooltip.TooltipParams;
import rotp.core.config.client.screen.PaperKit;
import rotp.core.impl.stands.goldexperience.GECreatedLifeformEffect;
import rotp.core.impl.stands.goldexperience.GETransformationEntity;
import rotp.core.impl.stands.goldexperience.GoldExperienceCreateLifeformAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster.Answer;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster.Row;
import rotp.core.network.c2s.ClGELifeformRosterPacket;
import rotp.core.network.s2c.TrGELifeformRosterPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The menu of a Gold Experience user's lifeforms, a page of the JoJo menu: every one he made, nearest first, and a
 * click reverts it. Revert opens it when it is pressed aimed at none of them. The server sends the rows and decides
 * every revert; this only asks.
 */
public class GoldExperienceLifeformsScreen extends Screen implements IJojoMenuScreen {
    private static final String TEXT = GoldExperienceLifeformText.TEXT;
    private static final int MAX_WIDTH = 320;
    private static final int MAX_HEIGHT = 300;
    private static final int MIN_WIDTH = 180;
    private static final int SIDE_TABS = 60;
    private static final int TALL_HEADER_FROM = 170;
    private static final int FOOTER = 26;
    private static final int REFRESH_TICKS = 10;
    private static final long NOTICE_MILLIS = 4000;
    private static final long TOOLTIP_DELAY_MILLIS = 350;
    private static final long REVERT_SPACING_MILLIS = 400;

    private final Tab tab;
    // the key that opened it from Revert: once let go, pressing it again closes the menu
    @Nullable private final ClientKey openedBy;
    private boolean openKeyReleased;
    private int windowWidth = DEFAULT_WIDTH;
    private int windowHeight = DEFAULT_HEIGHT;
    @Nullable private GoldExperienceLifeformList list;
    @Nullable private List<Row> rows;
    private final Set<UUID> pending = new HashSet<>();
    private int ticks;
    private int ticksSinceRows;
    @Nullable private Component notice;
    private int noticeColor;
    private long noticeUntil;
    @Nullable private GoldExperienceLifeformList.Entry hoverEntry;
    private long hoverSince;
    private long lastRevertAsked = Long.MIN_VALUE / 2;

    public GoldExperienceLifeformsScreen(Tab tab, @Nullable ClientKey openedBy) {
        super(Component.translatable(TEXT + "title"));
        this.tab = tab;
        this.openedBy = openedBy;
    }

    public static void openOnRevert() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new GoldExperienceLifeformsScreen(JojoMenuTabs.STAND_LIFEFORMS, InputHandler.lastActionKey));
        }
    }

    public static void onRoster(TrGELifeformRosterPacket packet) {
        if (Minecraft.getInstance().screen instanceof GoldExperienceLifeformsScreen screen) {
            screen.receive(packet);
        }
    }

    /** Whether the Stand has a Revert (Gold Experience's, or one an add-on reuses), which this menu belongs to. */
    public static boolean hasMenu(@Nullable StandPower power) {
        Ability revert = power != null ? GoldExperienceLifeformRoster.revertAbility(power) : null;
        return revert != null && revert.isAbilityUnlocked(power);
    }

    Font font() {
        return font;
    }

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
    public TabCategory getTabCategory() {
        return tab.getCategory();
    }

    @Override
    public Tab getTab() {
        return tab;
    }

    private int headerHeight() {
        return windowHeight >= TALL_HEADER_FROM ? 33 : 21;
    }

    @Override
    protected void init() {
        windowWidth = Math.max(Math.min(MIN_WIDTH, width - 8), Math.min(MAX_WIDTH, width - SIDE_TABS));
        windowHeight = Math.min(MAX_HEIGHT, height - 8);
        int x = getWindowX(this);
        int y = getWindowY(this);
        double scroll = list != null ? list.getScrollAmount() : 0;
        int listY = y + headerHeight();
        int listHeight = Math.max(GoldExperienceLifeformList.ROW_HEIGHT, y + windowHeight - FOOTER - listY);
        list = addRenderableWidget(new GoldExperienceLifeformList(minecraft, this, x + 2, listY, windowWidth - 4, listHeight));
        if (rows != null) {
            list.show(rows, false);
        }
        list.setScrollAmount(scroll);

        int doneWidth = Math.max(50, font.width(CommonComponents.GUI_DONE) + 16);
        addRenderableWidget(new PaperKit.Action(x + windowWidth - 9 - doneWidth, y + windowHeight - FOOTER + 5, doneWidth,
                PaperKit.CONTROL_HEIGHT, CommonComponents.GUI_DONE, this::onClose));
        if (rows == null) {
            askForRows();
        }
    }

    private void askForRows() {
        PacketDistributor.sendToServer(ClGELifeformRosterPacket.list());
    }

    void requestRevert(Row row) {
        long now = Util.getMillis();
        // the rows close up as one goes: the second click of a double click would land on the next one
        if (now - lastRevertAsked >= REVERT_SPACING_MILLIS && pending.add(row.id())) {
            lastRevertAsked = now;
            PacketDistributor.sendToServer(ClGELifeformRosterPacket.revert(row.id()));
        }
    }

    boolean isPending(UUID id) {
        return pending.contains(id);
    }

    private void receive(TrGELifeformRosterPacket packet) {
        rows = packet.rows();
        ticksSinceRows = 0;
        if (packet.answer() != Answer.NONE) {
            pending.clear();
            showAnswer(packet.answer(), packet.subject());
        }
        else {
            pending.removeIf(id -> rows.stream().noneMatch(row -> row.id().equals(id)));
        }
        if (list != null) {
            list.show(rows, keepsOrder());
        }
    }

    // the row under the pointer or the keyboard stays where it is until they leave the list
    private boolean keepsOrder() {
        if (list == null || minecraft == null) {
            return false;
        }
        double mouseX = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
        return list.isMouseOver(mouseX, mouseY) || list.getFocused() != null && minecraft.getLastInputType().isKeyboard();
    }

    private void showAnswer(Answer answer, @Nullable Component subject) {
        Component name = subject != null ? subject : Component.empty();
        notice = switch (answer) {
            case REVERTED -> Component.translatable(TEXT + "answer.reverted", name);
            case GONE -> Component.translatable(TEXT + "answer.gone");
            case NOT_HERE -> Component.translatable(TEXT + "answer.not_here");
            case TOO_FAR -> Component.translatable(TEXT + "answer.too_far");
            case REFUSED -> Component.translatable(TEXT + "answer.refused");
            case NONE -> null;
        };
        noticeColor = answer == Answer.REVERTED ? PaperKit.ON : PaperKit.ERROR;
        noticeUntil = Util.getMillis() + NOTICE_MILLIS;
    }

    @Nullable
    private Entity clientEntity(Row row) {
        if (row.entityId() < 0 || minecraft == null || minecraft.level == null) {
            return null;
        }
        Entity entity = minecraft.level.getEntity(row.entityId());
        return entity != null && entity.isAlive() && entity.getUUID().equals(row.id()) ? entity : null;
    }

    /** Where the lifeform is now: as this client sees it when it can, else where the server last said. */
    @Nullable
    Vec3 livePos(Row row, float partialTick) {
        Entity entity = clientEntity(row);
        return entity != null ? entity.getPosition(partialTick) : row.pos();
    }

    /** The lifeform itself to draw, when this client has it (not while it still forms). */
    @Nullable
    Entity liveEntity(Row row) {
        Entity entity = clientEntity(row);
        return entity instanceof GETransformationEntity ? null : entity;
    }

    long liveAge(Row row) {
        return row.age() + ticksSinceRows;
    }

    boolean liveCounts(Row row) {
        return row.counts() && liveAge(row) < GECreatedLifeformEffect.COUNTED_TICKS;
    }

    @Override
    public void tick() {
        ticks++;
        ticksSinceRows++;
        if (ticks % REFRESH_TICKS == 0) {
            askForRows();
        }
        if (openedBy != null && !openKeyReleased && !isDown(openedBy)) {
            openKeyReleased = true;
        }
        if (!hasMenu(JojoMenuTabs.getPowerForMenu(PowerClass.STAND))) {
            onClose();
        }
    }

    private boolean isDown(ClientKey key) {
        InputConstants.Key vanillaKey = key.getVanillaKey();
        long window = minecraft.getWindow().getWindow();
        return switch (vanillaKey.getType()) {
            case MOUSE -> GLFW.glfwGetMouseButton(window, vanillaKey.getValue()) == GLFW.GLFW_PRESS;
            case KEYSYM -> GLFW.glfwGetKey(window, vanillaKey.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }

    private boolean isOpeningKey(ClientKey pressed) {
        return openedBy != null && openKeyReleased && openedBy.equals(pressed);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ClientKey pressed = ClientKey.make(keyCode == GLFW.GLFW_KEY_UNKNOWN ? InputConstants.Type.SCANCODE : InputConstants.Type.KEYSYM,
                keyCode == GLFW.GLFW_KEY_UNKNOWN ? scanCode : keyCode);
        if (isOpeningKey(pressed)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && getTabAt(mouseX, mouseY, this) == tab) {
            return true;
        }
        if (clickTab(mouseX, mouseY, button, this)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            hoverEntry = list != null ? list.hoveredEntry() : null;
            hoverSince = Long.MAX_VALUE;
            return true;
        }
        if (isOpeningKey(ClientKey.make(InputConstants.Type.MOUSE, button))) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(PaperKit.WINDOW, getWindowX(this), getWindowY(this), windowWidth, windowHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHeader(graphics);
        renderEmptyMessage(graphics);
        renderFooter(graphics);
        renderTabs(graphics, this);
        renderRowTooltip(mouseX, mouseY);
        renderTabTooltip(graphics, this, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics graphics) {
        int x = getWindowX(this);
        int y = getWindowY(this);
        Component name = title.copy().withStyle(ChatFormatting.BOLD);
        PaperKit.text(graphics, font, name, x + (windowWidth - font.width(name)) / 2, y + 7, PaperKit.INK);
        if (windowHeight >= TALL_HEADER_FROM && rows != null && !rows.isEmpty()) {
            long counted = rows.stream().filter(this::liveCounts).count();
            String summary = PaperKit.oneLine(font, Component.translatable(TEXT + "counted", counted,
                    GoldExperienceCreateLifeformAbility.MAX_CREATED_LIFEFORMS).getString(), windowWidth - 24);
            PaperKit.text(graphics, font, Component.literal(summary), x + (windowWidth - font.width(summary)) / 2, y + 18,
                    counted >= GoldExperienceCreateLifeformAbility.MAX_CREATED_LIFEFORMS ? PaperKit.PENDING : PaperKit.INK_SOFT);
        }
        int lineY = y + headerHeight() - 2;
        graphics.fill(x + 8, lineY, x + windowWidth - 8, lineY + 1, PaperKit.LINE_SOFT);
        int footerY = y + windowHeight - FOOTER;
        graphics.fill(x + 8, footerY, x + windowWidth - 8, footerY + 1, PaperKit.LINE_SOFT);
    }

    private void renderEmptyMessage(GuiGraphics graphics) {
        if (rows != null && !rows.isEmpty()) {
            return;
        }
        Component text = Component.translatable(TEXT + (rows == null ? "loading" : "empty"));
        List<FormattedCharSequence> lines = PaperKit.fit(font, text.getString(), windowWidth - 40, 3);
        int y = getWindowY(this) + headerHeight() + (windowHeight - headerHeight() - FOOTER) / 2 - lines.size() * 5;
        for (FormattedCharSequence line : lines) {
            PaperKit.text(graphics, font, line, getWindowX(this) + (windowWidth - font.width(line)) / 2, y, PaperKit.INK_SOFT);
            y += 10;
        }
    }

    private void renderFooter(GuiGraphics graphics) {
        int x = getWindowX(this);
        int doneWidth = Math.max(50, font.width(CommonComponents.GUI_DONE) + 16);
        int left = x + 9;
        int right = x + windowWidth - 9 - doneWidth - 6;
        int y = getWindowY(this) + windowHeight - FOOTER + 10;
        Component text;
        int color;
        if (notice != null && Util.getMillis() < noticeUntil) {
            text = notice;
            color = noticeColor;
        }
        else if (rows != null && !rows.isEmpty()) {
            text = Component.translatable(TEXT + "hint");
            color = PaperKit.INK_FAINT;
        }
        else {
            return;
        }
        String shown = PaperKit.oneLine(font, text.getString(), right - left);
        PaperKit.text(graphics, font, Component.literal(shown), left, y, color);
    }

    private void renderRowTooltip(int mouseX, int mouseY) {
        if (list == null) {
            return;
        }
        long now = Util.getMillis();
        GoldExperienceLifeformList.Entry hovered = list.isMouseOver(mouseX, mouseY) ? list.hoveredEntry() : null;
        if (hovered != hoverEntry) {
            hoverEntry = hovered;
            hoverSince = now;
        }
        GoldExperienceLifeformList.Entry shown = null;
        int anchorX = mouseX;
        if (hovered != null && now - hoverSince >= TOOLTIP_DELAY_MILLIS) {
            shown = hovered;
        }
        else if (hovered == null && minecraft.getLastInputType().isKeyboard() && list.getFocused() != null) {
            shown = list.getFocused();
            anchorX = getWindowX(this) + windowWidth / 2;
        }
        if (shown == null) {
            return;
        }
        int top = list.topOf(shown);
        if (top + GoldExperienceLifeformList.ROW_HEIGHT < list.getY() || top > list.getBottom()) {
            return;
        }
        int wrap = Math.min(230, Math.max(120, width - 40));
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Component> text = shown.tooltip();
        for (int i = 0; i < text.size(); i++) {
            int color = i == 0 ? PaperKit.INK : PaperKit.INK_SOFT;
            lines.addAll(font.split(text.get(i).copy().withColor(color & 0xFFFFFF), wrap));
        }
        int clampedTop = Mth.clamp(top - 2, list.getY(), list.getBottom());
        setTooltipForNextRenderPass(lines, new BelowOrAboveWidgetTooltipPositioner(
                new ScreenRectangle(anchorX, clampedTop, 1, GoldExperienceLifeformList.ROW_HEIGHT - 2)), true);
        TooltipParams.set(TooltipParams.paperStyle(1));
    }
}
