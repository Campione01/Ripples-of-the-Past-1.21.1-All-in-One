package rotp.core.impl.powers.vampirism;

/**
 * How long the sun that burns a vampire ({@link SunWeakness#isSunny}: Level#isDay) still has to shine. A game test pins
 * these two against a real level in clear weather.
 */
public final class SunsetClock {
	public static final int DAY_LENGTH = 24000;
	public static final int SUN_UP_TIME = 23460;
	public static final int SUN_DOWN_TIME = 12542;

	private SunsetClock() {}

	/** The ticks left to sunset at this time of day, or -1 with the sun already down. */
	public static int ticksToSunset(long dayTime) {
		int time = (int) Math.floorMod(dayTime, (long) DAY_LENGTH);
		if (time >= SUN_UP_TIME) {
			return DAY_LENGTH - time + SUN_DOWN_TIME;
		}
		return time < SUN_DOWN_TIME ? SUN_DOWN_TIME - time : -1;
	}
}
