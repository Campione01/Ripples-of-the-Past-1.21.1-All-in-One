package rotp.addons.echoes.client;

import java.util.ArrayList;
import java.util.List;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.network.EchoesSelectActPayload;
import rotp.addons.echoes.state.EchoesForms;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * R772 E1: the master's ACT selector (client/ui/screen/EchoesChangeActScreen and EchoesSelectorActWidget): the
 * author's act_gui panel with one slot per ACT on offer and his ACT icons; the hovered ACT, or else the ACT in use,
 * is lit in Echoes green, and a click on a slot changes to that ACT.
 */
public final class EchoesChangeActScreen extends Screen {
    private static final ResourceLocation MENU = RotpEchoes.id("textures/gui/act_gui.png");
    private static final int TEXTURE_SIZE = 128;
    private static final int PANEL_WIDTH = 125;
    private static final int PANEL_HEIGHT = 75;
    private static final int SLOT_SIZE = 25;
    private static final int SLOT_STEP = 30;
    private static final int ALL_SLOTS_WIDTH = 3 * SLOT_STEP - 5;

    private final int currentAct;
    private final int highestAct;
    private final List<ActSlot> slots = new ArrayList<>();

    private EchoesChangeActScreen(int currentAct, int highestAct) {
        super(Component.translatable("rotp_echoes.ability.select_act"));
        this.currentAct = currentAct;
        this.highestAct = highestAct;
    }

    public static void openWindowOnClick(LivingEntity user) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || user != mc.player) {
            return;
        }
        StandPower power = StandPower.get(mc.player);
        int highest = EchoesForms.highestAct(power);
        if (highest > 1) {
            mc.setScreen(new EchoesChangeActScreen(EchoesForms.actOf(power.getPowerType()), highest));
        }
    }

    @Override
    protected void init() {
        slots.clear();
        for (int act = 1; act <= highestAct; act++) {
            ActSlot slot = new ActSlot(width / 2 - ALL_SLOTS_WIDTH / 2 + (act - 1) * SLOT_STEP, height / 2 - 30, act);
            slots.add(slot);
            addRenderableWidget(slot);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(MENU, width / 2 - 62, height / 2 - 57, 0, 0, PANEL_WIDTH, PANEL_HEIGHT,
                TEXTURE_SIZE, TEXTURE_SIZE);
        super.render(graphics, mouseX, mouseY, partialTick);
        ActSlot lit = litSlot();
        if (lit != null) {
            graphics.drawCenteredString(font, lit.getMessage(), width / 2, height / 2 - 50, 0xFFFFFF);
        }
        graphics.drawCenteredString(font, title, width / 2, height / 2 + 5, 0xFFFFFF);
        RenderSystem.disableBlend();
    }

    // As the game mode switcher the master copies: the world stays clear behind the panel.
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ActSlot litSlot() {
        for (ActSlot slot : slots) {
            if (slot.isHoveredOrFocused()) {
                return slot;
            }
        }
        for (ActSlot slot : slots) {
            if (slot.act == currentAct) {
                return slot;
            }
        }
        return null;
    }

    private void select(int act) {
        if (act != currentAct) {
            PacketDistributor.sendToServer(new EchoesSelectActPayload(act));
        }
        onClose();
    }

    private final class ActSlot extends Button {
        private final int act;
        private final ResourceLocation icon;

        private ActSlot(int x, int y, int act) {
            super(Button.builder(Component.translatable("rotp_echoes.gui.act" + act), button -> select(act))
                    .bounds(x, y, SLOT_SIZE, SLOT_SIZE));
            this.act = act;
            this.icon = RotpEchoes.id("textures/power/echoes_act" + act + ".png");
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blit(MENU, getX(), getY(), 0, 75, SLOT_SIZE, SLOT_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + 4.5F, getY() + 4.5F, 0.0F);
            graphics.blit(icon, 0, 0, 16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
            graphics.pose().popPose();
            if (litSlot() == this) {
                RenderSystem.setShaderColor(0.0F, 1.0F, 0.4F, 1.0F);
                graphics.blit(MENU, getX(), getY(), 25, 75, SLOT_SIZE, SLOT_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }
}
