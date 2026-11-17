package rotp.addons.sexpistols.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

// The donor's reload key. Its R is the Stand's summon key in this core, and the two together would recall the Stand with
// every reload.
public final class RevolverKeyMappings {
    public static final KeyMapping RELOAD = new KeyMapping(
            RevolverItem.RELOAD_KEY_NAME,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.gameplay");

    private RevolverKeyMappings() {}
}
