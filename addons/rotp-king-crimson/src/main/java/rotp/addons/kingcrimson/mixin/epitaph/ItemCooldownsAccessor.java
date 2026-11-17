package rotp.addons.kingcrimson.mixin.epitaph;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;

/** The item cooldowns of a player, which his saved data does not hold: each as a CooldownInstanceAccessor. */
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {
    @Accessor("cooldowns")
    Map<Item, Object> kingCrimson$cooldowns();

    @Accessor("tickCount")
    int kingCrimson$tickCount();
}
