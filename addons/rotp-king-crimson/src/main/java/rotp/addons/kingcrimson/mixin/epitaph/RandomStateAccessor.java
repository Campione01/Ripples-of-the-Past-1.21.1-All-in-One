package rotp.addons.kingcrimson.mixin.epitaph;

import java.util.concurrent.atomic.AtomicLong;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;

/** The state of the random source entities and levels use, so a phantom can draw what its body would have drawn. */
@Mixin(LegacyRandomSource.class)
public interface RandomStateAccessor {
    @Accessor("seed")
    AtomicLong kingCrimson$seed();

    @Accessor("gaussianSource")
    MarsagliaPolarGaussian kingCrimson$gaussian();
}
