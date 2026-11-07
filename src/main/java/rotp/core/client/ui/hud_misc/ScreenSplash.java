package rotp.core.client.ui.hud_misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * One splash of blood or sand on the screen, as textured quads for a given age. It lands in a cluster around the middle
 * of the view, blood runs down, then a curved line crosses the screen and cuts every drop it passes, leaving a smear
 * that fades. No Minecraft types, so the whole look can be tested and previewed off screen.
 */
public final class ScreenSplash {
	public enum Kind { BLOOD, SAND }

	public record Quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color) {}

	/** Blood is coloured in the texture; sand is grey there and takes the block's colour. */
	public static final int BLOOD_TINT = 0xFFFFFF;
	public static final int TEXTURE_SIZE = 256;

	private static final int WIPE_TICKS = 12;
	private static final int SMEAR_TICKS = 14;
	private static final float DROP_ALPHA = 0.96F;
	private static final float SMEAR_ALPHA_BLOOD = 0.7F;
	private static final float SMEAR_ALPHA_SAND = 0.45F;
	private static final float SMEAR_GROWTH_PER_TICK = 0.03F;
	private static final float RUN_START_TICK = 5;
	private static final float RUN_TEXELS_PER_TICK = 1.6F;
	private static final int RUN_TEXELS = 48;
	private static final int RUN_WIDTH = 8;

	private static final int[][] BLOOD_BIG = cells(64, 0, 0, 4);
	private static final int[][] BLOOD_MID = cells(32, 0, 64, 4);
	private static final int[][] BLOOD_SMALL = { { 128, 64 }, { 144, 64 }, { 160, 64 }, { 176, 64 }, { 128, 80 }, { 144, 80 }, { 160, 80 }, { 176, 80 } };
	private static final int[][] BLOOD_RUN = cells(8, 192, 64, 4);
	private static final int[][] BLOOD_SMEAR = cells(96, 0, 96, 2);
	private static final int[][] SAND_BIG = cells(64, 0, 128, 4);
	private static final int[][] SAND_MID = cells(32, 0, 192, 4);
	private static final int[][] SAND_SMALL = { { 128, 192 }, { 144, 192 }, { 160, 192 }, { 176, 192 }, { 128, 208 }, { 144, 208 }, { 160, 208 }, { 176, 208 } };
	private static final int[][] SAND_SMEAR = cells(96, 0, 224, 2);

	private final Kind kind;
	private final float strength;
	private final int tint;
	private final int holdTicks;
	private final int wipeDirection;
	private final float wipeCurve;
	private final List<Drop> drops = new ArrayList<>();

	public ScreenSplash(Kind kind, float strength, int tint, long seed) {
		this.kind = kind;
		this.strength = Math.max(0, Math.min(1, strength));
		this.tint = tint & 0xFFFFFF;
		Random random = new Random(seed);
		holdTicks = kind == Kind.BLOOD ? Math.round(14 + 22 * this.strength) : Math.round(10 + 16 * this.strength);
		wipeDirection = random.nextBoolean() ? 1 : -1;
		wipeCurve = 0.1F + random.nextFloat() * 0.2F;
		float centerX = (random.nextFloat() - 0.5F) * 0.3F;
		float centerY = (random.nextFloat() - 0.5F) * 0.16F;
		int[][] big = kind == Kind.BLOOD ? BLOOD_BIG : SAND_BIG;
		int[][] mid = kind == Kind.BLOOD ? BLOOD_MID : SAND_MID;
		int[][] small = kind == Kind.BLOOD ? BLOOD_SMALL : SAND_SMALL;
		addDrops(random, Math.round(36 * this.strength * this.strength), 64, big, centerX, centerY, 0.2F);
		addDrops(random, Math.round(3 + 26 * this.strength), 32, mid, centerX, centerY, 0.32F);
		addDrops(random, Math.round(5 + 34 * this.strength), 16, small, centerX, centerY, 0.48F);
	}

	private void addDrops(Random random, int count, int size, int[][] cells, float centerX, float centerY, float spread) {
		for (int i = 0; i < count; i++) {
			int[] cell = cells[random.nextInt(cells.length)];
			Drop drop = new Drop();
			drop.x = centerX + (float) random.nextGaussian() * spread * 1.35F;
			drop.y = centerY + (float) random.nextGaussian() * spread;
			drop.u = cell[0];
			drop.v = cell[1];
			drop.size = size;
			drop.delay = random.nextFloat() * 2;
			drop.slide = (0.05F + random.nextFloat() * 0.25F) * (kind == Kind.BLOOD ? 1 : 3) * (64F / size) / 4;
			drop.runs = kind == Kind.BLOOD && size >= 16 && random.nextFloat() < (size == 16 ? 0.25F : 0.6F);
			drop.runCell = random.nextInt(BLOOD_RUN.length);
			drop.smearCell = random.nextInt(2);
			drops.add(drop);
		}
	}

	public int lifetimeTicks() {
		return holdTicks + WIPE_TICKS + SMEAR_TICKS;
	}

	public int dropCount() {
		return drops.size();
	}

	public int wipeDirection() {
		return wipeDirection;
	}

	public int holdTicks() {
		return holdTicks;
	}

	/** The screen x of the wipe line at a given height; a drop keeps only what the line has not reached. */
	public float wipeLineX(float age, float yFraction, int width) {
		float bow = Math.max(-1, Math.min(1, (yFraction - 0.5F) * 2));
		float arc = wipeCurve * width;
		float progress = (age - holdTicks) / WIPE_TICKS;
		float x = -arc + progress * (width + 2 * arc) - arc * bow * bow;
		return wipeDirection > 0 ? x : width - x;
	}

	public List<Quad> frame(float age, int width, int height) {
		List<Quad> quads = new ArrayList<>();
		if (age < 0 || age >= lifetimeTicks()) {
			return quads;
		}
		float pixel = texelSize(height);
		int rgb = kind == Kind.BLOOD ? BLOOD_TINT : tint;
		// the smears go under the drops, the runs under their own drop
		for (Drop drop : drops) {
			addSmear(quads, drop, age, width, height, pixel, rgb);
		}
		if (age >= holdTicks + WIPE_TICKS) {
			return quads;
		}
		for (Drop drop : drops) {
			if (drop.runs) {
				addRun(quads, drop, age, width, height, pixel, rgb);
			}
			addDrop(quads, drop, age, width, height, pixel, rgb);
		}
		return quads;
	}

	/** One texel on screen, in GUI pixels: the art keeps its size against the screen and its hard pixels at any GUI scale. */
	static float texelSize(int height) {
		return Math.max(1, Math.round(height / 360F));
	}

	private void addDrop(List<Quad> quads, Drop drop, float age, int width, int height, float pixel, int rgb) {
		float time = age - drop.delay;
		if (time < 0) {
			return;
		}
		float landing = Math.min(1, time / 2);
		float size = drop.size * pixel * (0.7F + 0.3F * landing);
		float centerX = snap(width / 2F + drop.x * height);
		float centerY = snap(height / 2F + drop.y * height + slide(drop, time) * pixel);
		float x0 = centerX - size / 2;
		float x1 = x0 + size;
		float y0 = centerY - size / 2;
		float y1 = y0 + size;
		float u0 = drop.u;
		float u1 = drop.u + drop.size;
		float line = wipeLineX(age, centerY / height, width);
		if (wipeDirection > 0) {
			if (line >= x1) return;
			if (line > x0) {
				float cut = (float) Math.ceil((line - x0) / pixel) * pixel;
				x0 += cut;
				u0 += cut / size * drop.size;
			}
		}
		else {
			if (line <= x0) return;
			if (line < x1) {
				float cut = (float) Math.ceil((x1 - line) / pixel) * pixel;
				x1 -= cut;
				u1 -= cut / size * drop.size;
			}
		}
		if (x1 <= x0) return;
		quads.add(new Quad(x0, y0, x1, y1, u0, drop.v, u1, drop.v + drop.size, color(DROP_ALPHA * (0.75F + 0.25F * landing), rgb)));
	}

	private void addRun(List<Quad> quads, Drop drop, float age, int width, int height, float pixel, int rgb) {
		float time = age - drop.delay - RUN_START_TICK;
		if (time <= 0) {
			return;
		}
		float slide = slide(drop, age - drop.delay);
		float centerX = snap(width / 2F + drop.x * height);
		float centerY = snap(height / 2F + drop.y * height + slide * pixel);
		float texels = (float) Math.floor(Math.min(RUN_TEXELS, time * RUN_TEXELS_PER_TICK * (drop.size == 16 ? 0.6F : 1)));
		if (texels < 1) {
			return;
		}
		float line = wipeLineX(age, centerY / height, width);
		boolean wiped = wipeDirection > 0 ? line >= centerX : line <= centerX;
		if (wiped) {
			return;
		}
		int[] cell = BLOOD_RUN[drop.runCell];
		float x0 = centerX - RUN_WIDTH * pixel / 2;
		quads.add(new Quad(x0, centerY, x0 + RUN_WIDTH * pixel, centerY + texels * pixel,
				cell[0], cell[1] + RUN_TEXELS - texels, cell[0] + RUN_WIDTH, cell[1] + RUN_TEXELS, color(DROP_ALPHA, rgb)));
	}

	private void addSmear(List<Quad> quads, Drop drop, float age, int width, int height, float pixel, int rgb) {
		if (drop.size < 32) {
			return;
		}
		float time = age - drop.delay;
		float centerX = snap(width / 2F + drop.x * height);
		float centerY = snap(height / 2F + drop.y * height + slide(drop, time) * pixel);
		float size = drop.size * pixel;
		float wipedFor = wipedFor(age, wipeDirection > 0 ? centerX - size / 2 : centerX + size / 2, centerY / height, width);
		if (wipedFor <= 0 || wipedFor >= SMEAR_TICKS) {
			return;
		}
		float alpha = (kind == Kind.BLOOD ? SMEAR_ALPHA_BLOOD : SMEAR_ALPHA_SAND) * (1 - wipedFor / SMEAR_TICKS);
		float length = size + wipedFor * SMEAR_GROWTH_PER_TICK * width;
		float thick = size * 0.5F;
		float x0 = wipeDirection > 0 ? centerX - size / 2 : centerX + size / 2 - length;
		int[] cell = (kind == Kind.BLOOD ? BLOOD_SMEAR : SAND_SMEAR)[drop.smearCell];
		float u0 = wipeDirection > 0 ? cell[0] : cell[0] + 96;
		float u1 = wipeDirection > 0 ? cell[0] + 96 : cell[0];
		quads.add(new Quad(x0, centerY - thick / 2, x0 + length, centerY + thick / 2, u0, cell[1], u1, cell[1] + 16, color(alpha, rgb)));
	}

	/** How long ago the wipe line passed this screen x, in ticks; zero or less if it has not yet. */
	private float wipedFor(float age, float x, float yFraction, int width) {
		float line = wipeLineX(age, yFraction, width);
		float speed = (width + 2 * wipeCurve * width) / WIPE_TICKS;
		return (wipeDirection > 0 ? line - x : x - line) / speed;
	}

	private float slide(Drop drop, float time) {
		return Math.max(0, time - 4) * drop.slide;
	}

	private static float snap(float value) {
		return Math.round(value);
	}

	private static int color(float alpha, int rgb) {
		return Math.round(alpha * 255) << 24 | rgb;
	}

	private static int[][] cells(int step, int x, int y, int count) {
		int[][] cells = new int[count][];
		for (int i = 0; i < count; i++) {
			cells[i] = new int[] { x + i * step, y };
		}
		return cells;
	}

	private static final class Drop {
		float x;
		float y;
		int u;
		int v;
		int size;
		float delay;
		float slide;
		boolean runs;
		int runCell;
		int smearCell;
	}


	/**
	 * The splashes on screen. Off means nothing is kept, so turning the setting off clears the screen at once. While the
	 * viewer's own time is stopped, or the game is paused, they neither age nor land: a splash that arrives then stays at
	 * age -1, not drawn, until time runs again.
	 */
	public static final class Stack {
		public static final int MAX_SPLASHES = 5;
		public static final int MERGE_TICKS = 6;

		private final List<Entry> entries = new ArrayList<>();

		/** Off, not even the splash is built. */
		public boolean add(boolean enabled, Supplier<ScreenSplash> supplier) {
			if (!enabled) {
				return false;
			}
			ScreenSplash splash = supplier.get();
			for (Entry entry : entries) {
				if (entry.age < MERGE_TICKS && entry.splash.kind == splash.kind && entry.splash.strength >= splash.strength) {
					return false;
				}
			}
			if (entries.size() >= MAX_SPLASHES) {
				entries.remove(0);
			}
			entries.add(new Entry(splash));
			return true;
		}

		public void tick(boolean enabled, boolean frozen) {
			if (!enabled) {
				entries.clear();
			}
			if (frozen) {
				return;
			}
			entries.removeIf(entry -> ++entry.age >= entry.splash.lifetimeTicks());
		}

		public void clear() {
			entries.clear();
		}

		public boolean isEmpty() {
			return entries.isEmpty();
		}

		public int size() {
			return entries.size();
		}

		public List<Quad> frame(float partialTick, int width, int height) {
			List<Quad> quads = new ArrayList<>();
			for (Entry entry : entries) {
				quads.addAll(entry.splash.frame(entry.age + partialTick, width, height));
			}
			return quads;
		}

		private static final class Entry {
			final ScreenSplash splash;
			int age = -1;

			Entry(ScreenSplash splash) {
				this.splash = splash;
			}
		}
	}
}
