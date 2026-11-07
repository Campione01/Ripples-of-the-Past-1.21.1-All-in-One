package rotp.core.mixin.client.screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenScrollAccessor {
	@Accessor("scrollOffs")
	float jojo_ripples$scrollOffs();
}
