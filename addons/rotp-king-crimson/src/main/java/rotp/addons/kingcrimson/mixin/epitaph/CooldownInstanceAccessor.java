package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** When one item cooldown ends, on the clock of its ItemCooldowns. */
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {
    @Accessor("endTime")
    int kingCrimson$endTime();
}
