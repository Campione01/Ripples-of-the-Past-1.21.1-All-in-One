package rotp.addons.kingcrimson.client.epitaph;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rotp.addons.kingcrimson.network.EpitaphNoticePayload;

/**
 * The sign on the screen of a player who is told that a prediction of Epitaph is aimed at him (EPITAPH-ARCH 3.7): one
 * line of text at the top, for as long as the server says so. How it looks is to be settled by looking.
 */
@OnlyIn(Dist.CLIENT)
public final class EpitaphNotice implements LayeredDraw.Layer {
    private static final int CRIMSON = 0xFFDC143C;
    private static final int TOP = 12;
    /** The prediction the local player is told of, -1 for none. */
    private static int session = -1;

    /** From the network handler, on the client thread. */
    public static void accept(EpitaphNoticePayload payload) {
        if (payload.on()) {
            session = payload.session();
        } else if (session == payload.session()) {
            session = -1;
        }
    }

    /** Whether the prediction of this number is one the local player is told of: it is not his own. */
    public static boolean isOf(int id) {
        return session == id;
    }

    /** The player left the server: nothing is aimed at him. */
    public static void clear() {
        session = -1;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (session < 0 || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        graphics.drawCenteredString(minecraft.font, Component.translatable("rotp_kingcrimson.message.epitaph_foreseen"),
                graphics.guiWidth() / 2, TOP, CRIMSON);
    }
}
