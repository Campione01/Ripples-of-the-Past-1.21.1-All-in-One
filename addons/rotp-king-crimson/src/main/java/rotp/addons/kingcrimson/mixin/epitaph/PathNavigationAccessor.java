package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.ai.navigation.PathNavigation;

@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {
    @Accessor("speedModifier")
    double kingCrimson$speedModifier();
}
