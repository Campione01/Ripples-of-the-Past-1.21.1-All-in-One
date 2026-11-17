package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;

@Mixin(MarsagliaPolarGaussian.class)
public interface GaussianStateAccessor {
    @Accessor("nextNextGaussian")
    double kingCrimson$next();

    @Accessor("nextNextGaussian")
    void kingCrimson$setNext(double value);

    @Accessor("haveNextNextGaussian")
    boolean kingCrimson$hasNext();

    @Accessor("haveNextNextGaussian")
    void kingCrimson$setHasNext(boolean value);
}
