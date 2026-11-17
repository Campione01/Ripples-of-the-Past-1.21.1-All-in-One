package rotp.addons.kingcrimson.epitaph.sandbox;

import javax.annotation.Nullable;

import net.minecraft.util.RandomSource;
import rotp.addons.kingcrimson.mixin.epitaph.GaussianStateAccessor;
import rotp.addons.kingcrimson.mixin.epitaph.RandomStateAccessor;

/**
 * The state of a random source, to copy from a body to its phantom and to swap under the level while a room is
 * entered. Only the source vanilla gives entities and levels is known; another kind answers null and is left alone.
 */
public record RandomState(long seed, double gaussian, boolean hasGaussian) {

    @Nullable
    public static RandomState of(RandomSource source) {
        if (!(source instanceof RandomStateAccessor legacy)) {
            return null;
        }
        GaussianStateAccessor gaussian = (GaussianStateAccessor) legacy.kingCrimson$gaussian();
        return new RandomState(legacy.kingCrimson$seed().get(), gaussian.kingCrimson$next(), gaussian.kingCrimson$hasNext());
    }

    /** Whether the state was written. */
    public boolean writeTo(RandomSource source) {
        if (!(source instanceof RandomStateAccessor legacy)) {
            return false;
        }
        legacy.kingCrimson$seed().set(seed);
        GaussianStateAccessor into = (GaussianStateAccessor) legacy.kingCrimson$gaussian();
        into.kingCrimson$setNext(gaussian);
        into.kingCrimson$setHasNext(hasGaussian);
        return true;
    }

    /** Copies the state of one source onto another; false when either is of a kind that is not known. */
    public static boolean copy(RandomSource from, RandomSource to) {
        RandomState state = of(from);
        return state != null && state.writeTo(to);
    }
}
