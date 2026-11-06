package rotp.core.util;

import net.minecraft.util.RandomSource;

public class OOPMoment {
	// Both logical sides draw from this, and in single player those are two threads of one process.
	public static final RandomSource RANDOM = RandomSource.createThreadSafe();
}
