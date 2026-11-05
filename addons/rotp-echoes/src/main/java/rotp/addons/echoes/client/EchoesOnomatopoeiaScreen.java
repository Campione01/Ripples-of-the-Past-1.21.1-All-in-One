package rotp.addons.echoes.client;

import java.util.Locale;

import rotp.addons.echoes.network.EchoesOnomatopoeiaPayloads;
import rotp.addons.echoes.state.EchoesOnomatopoeia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * R772 F11: ACT2's word picker (the master's OnomatopoeiaSelectScreen): the 27 words in their four kinds, each in its
 * colour; a click chooses the word the tail carries next.
 */
public final class EchoesOnomatopoeiaScreen extends Screen {
    private static final int COLUMN_WIDTH = 92;
    private static final int ROW_HEIGHT = 18;

    private EchoesOnomatopoeiaScreen() {
        super(Component.translatable("rotp_echoes.ability.onomatopoeia_select"));
    }

    public static void openWindowOnClick(LivingEntity user) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null && user == mc.player) {
            mc.setScreen(new EchoesOnomatopoeiaScreen());
        }
    }

    @Override
    protected void init() {
        EchoesOnomatopoeia.Type[] types = EchoesOnomatopoeia.Type.values();
        int left = width / 2 - types.length * COLUMN_WIDTH / 2;
        int top = height / 2 - 100;
        int[] rows = new int[types.length];
        for (EchoesOnomatopoeia word : EchoesOnomatopoeia.values()) {
            int column = word.type().ordinal();
            int x = left + column * COLUMN_WIDTH + 2;
            int y = top + 14 + rows[column]++ * ROW_HEIGHT;
            Component label = Component.translatable(word.translationKey())
                    .withStyle(style -> style.withColor(TextColor.fromRgb(word.color())));
            addRenderableWidget(Button.builder(label, button -> choose(word))
                    .bounds(x, y, COLUMN_WIDTH - 4, ROW_HEIGHT - 2)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        EchoesOnomatopoeia.Type[] types = EchoesOnomatopoeia.Type.values();
        int left = width / 2 - types.length * COLUMN_WIDTH / 2;
        int top = height / 2 - 100;
        graphics.drawCenteredString(font, title, width / 2, top - 14, 0xFFFFFF);
        for (EchoesOnomatopoeia.Type type : types) {
            graphics.drawCenteredString(font,
                    Component.translatable("rotp_echoes.onomatopoeia.type." + type.name().toLowerCase(Locale.ROOT)),
                    left + type.ordinal() * COLUMN_WIDTH + COLUMN_WIDTH / 2, top, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void choose(EchoesOnomatopoeia word) {
        PacketDistributor.sendToServer(new EchoesOnomatopoeiaPayloads.Select(word.id()));
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("rotp_echoes.message.onomatopoeia_selected",
                    Component.translatable(word.translationKey())
                            .withStyle(style -> style.withColor(TextColor.fromRgb(word.color())))), true);
        }
        onClose();
    }
}
