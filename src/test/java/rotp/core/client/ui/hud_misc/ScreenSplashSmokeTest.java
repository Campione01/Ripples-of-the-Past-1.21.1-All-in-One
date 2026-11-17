package rotp.core.client.ui.hud_misc;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import rotp.core.client.ui.hud_misc.ScreenSplash.Kind;
import rotp.core.client.ui.hud_misc.ScreenSplash.Quad;
import rotp.core.client.ui.hud_misc.ScreenSplash.Stack;
import rotp.core.config.client.ClientModSettings;

/**
 * The screen splash off screen: off by default and nothing kept when off, a cluster of drops rather than a flat overlay,
 * blood that runs, a wipe that cuts what it passes and leaves a smear, and time held while the viewer is stopped. How it
 * looks in the game can only be seen there.
 */
public final class ScreenSplashSmokeTest {
	private static final int W = 640;
	private static final int H = 360;

	private ScreenSplashSmokeTest() {}

	public static void main(String[] args) throws IOException {
		check(!new ClientModSettings.Settings().screenSplash, "the screen splash setting must default to off");

		ScreenSplash blood = new ScreenSplash(Kind.BLOOD, 1, ScreenSplash.BLOOD_TINT, 11);
		check(blood.frame(-0.5F, W, H).isEmpty(), "a splash that has not landed draws nothing");
		check(blood.frame(blood.lifetimeTicks(), W, H).isEmpty(), "a finished splash draws nothing");
		check(blood.frame(10, W, H).equals(new ScreenSplash(Kind.BLOOD, 1, ScreenSplash.BLOOD_TINT, 11).frame(10, W, H)),
				"one seed must give one picture");
		check(new ScreenSplash(Kind.BLOOD, 0.2F, ScreenSplash.BLOOD_TINT, 11).dropCount() < blood.dropCount() / 2,
				"a weak splash must have far fewer drops than a full one");

		List<Quad> held = blood.frame(10, W, H);
		for (Quad quad : held) {
			check(area(quad) < 0.1F * W * H, "no quad may cover the screen like a flat overlay: " + quad);
			check(quad.u0() >= 0 && quad.u1() <= ScreenSplash.TEXTURE_SIZE && quad.v0() >= 0 && quad.v1() <= ScreenSplash.TEXTURE_SIZE,
					"a quad reads outside the texture: " + quad);
		}
		float middle = coverage(held, W / 4, H / 4, W * 3 / 4, H * 3 / 4);
		float corners = coverage(held, 0, 0, W / 8, H / 8);
		check(middle > 0.5F && middle > corners * 2, "a full splash blinds the middle of the view more than its edge: middle "
				+ middle + " corner " + corners);

		float runBefore = lowestBloodRun(blood, 8);
		float runAfter = lowestBloodRun(blood, 20);
		check(runBefore > 0 && runAfter > runBefore, "blood must run down: " + runBefore + " then " + runAfter);
		ScreenSplash sand = new ScreenSplash(Kind.SAND, 1, 0xDBD3A0, 13);
		check(lowestBloodRun(sand, 20) == 0, "sand does not run");
		for (Quad quad : sand.frame(10, W, H)) {
			check((quad.color() & 0xFFFFFF) == 0xDBD3A0, "sand must take the block's colour: " + Integer.toHexString(quad.color()));
			check(quad.v0() >= 128, "sand must read the sand part of the texture: " + quad);
		}

		checkWipe(new ScreenSplash(Kind.BLOOD, 1, ScreenSplash.BLOOD_TINT, 11));
		checkWipe(new ScreenSplash(Kind.BLOOD, 1, ScreenSplash.BLOOD_TINT, 14));
		checkWipe(sand);

		Stack stack = new Stack();
		check(!stack.add(false, () -> {
			throw new AssertionError("with the setting off a splash must not even be built");
		}) && stack.isEmpty(), "with the setting off a splash must leave nothing");
		check(stack.add(true, () -> blood) && stack.frame(0, W, H).isEmpty(), "a new splash lands on the next tick, not now");
		stack.tick(true, true);
		stack.tick(true, true);
		check(stack.frame(0.5F, W, H).isEmpty(), "with the viewer's time stopped, or paused, a splash must not land or age");
		stack.tick(true, false);
		check(!stack.frame(0, W, H).isEmpty() || !stack.frame(0.9F, W, H).isEmpty() || frameAfter(stack, 3), "time running again lets it land");
		check(!stack.add(true, () -> new ScreenSplash(Kind.BLOOD, 0.5F, ScreenSplash.BLOOD_TINT, 1)) && stack.size() == 1,
				"a weaker splash inside the merge window adds nothing, so a barrage cannot paint the screen solid");
		check(stack.add(true, () -> new ScreenSplash(Kind.SAND, 0.5F, 0xDBD3A0, 1)), "sand does not merge into blood");
		for (int i = 0; i < Stack.MAX_SPLASHES + 3; i++) {
			stack.tick(true, false);
			for (int t = 0; t < Stack.MERGE_TICKS; t++) stack.tick(true, false);
			long seed = i;
			stack.add(true, () -> new ScreenSplash(Kind.BLOOD, 0.5F, ScreenSplash.BLOOD_TINT, seed));
		}
		check(stack.size() <= Stack.MAX_SPLASHES, "at most " + Stack.MAX_SPLASHES + " splashes at once: " + stack.size());
		stack.tick(false, false);
		check(stack.isEmpty() && stack.frame(0, W, H).isEmpty(), "turning the setting off must clear the screen at once");
		Stack ending = new Stack();
		ending.add(true, () -> blood);
		for (int i = 0; i <= blood.lifetimeTicks(); i++) ending.tick(true, false);
		check(ending.isEmpty(), "a finished splash must leave the list");

		Path root = Path.of(System.getProperty("user.dir"), "src/main");
		String layer = Files.readString(root.resolve("java/rotp/core/client/ui/hud_misc/ScreenSplashLayer.java"));
		check(layer.contains("event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,"),
				"the splash must be the GUI layer right above the camera overlays: past the level pass, under the whole HUD");
		check(layer.contains("ClientModSettings.getSettingsReadOnly().screenSplash, () -> new ScreenSplash("),
				"a splash from the server must pass through the client's setting");
		String bleeding = Files.readString(root.resolve("java/rotp/core/mechanics/BleedingEffect.java"));
		check(bleeding.contains("ScreenSplashes.blood((ServerLevel) level, splashPos, radius, bleedAmount, ownerEntity);"),
				"every blood splash must offer itself to the screen");

		BufferedImage art = ImageIO.read(root.resolve("resources/assets/jojo_ripples/textures/hud/screen_splash.png").toFile());
		check(art.getWidth() == ScreenSplash.TEXTURE_SIZE && art.getHeight() == ScreenSplash.TEXTURE_SIZE, "the texture's size");
		for (int y = 128; y < 256; y += 3) {
			for (int x = 0; x < 256; x += 3) {
				int argb = art.getRGB(x, y);
				check((argb >>> 24) == 0 || (argb >> 16 & 255) == (argb & 255) && (argb >> 8 & 255) == (argb & 255),
						"the sand part of the texture must be grey, to take a block's colour: " + x + "," + y);
			}
		}
		check((art.getRGB(32, 32) >>> 24) > 200 && (art.getRGB(32, 32) >> 16 & 255) > (art.getRGB(32, 32) & 255) * 3,
				"the middle of a big blood splat must be solid and red");

		String[] languages = { "en_pt", "en_us", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br", "ru_ru", "uk_ua", "zh_cn", "zh_tw" };
		for (String language : languages) {
			String text = Files.readString(root.resolve("resources/assets/jojo_ripples/lang/" + language + ".json"));
			check(text.contains("\"jojo_ripples.config.client.screenSplash\": \"")
					&& text.contains("\"jojo_ripples.config.client.screenSplash.tooltip\": \""), language + " lacks the setting's text");
		}

		System.out.println("Screen splash smoke test passed");
	}

	private static boolean frameAfter(Stack stack, int ticks) {
		for (int i = 0; i < ticks; i++) stack.tick(true, false);
		return !stack.frame(0, W, H).isEmpty();
	}

	/** Mid wipe: the wiped side holds smears only, the side ahead still its drops; after it, smears alone, fading to nothing. */
	private static void checkWipe(ScreenSplash splash) {
		int hold = splash.holdTicks();
		int before = dropQuads(splash.frame(hold - 2, W, H)).size();
		check(before > 0, "drops must hold until the wipe");
		float mid = hold + 6;
		List<Quad> frame = splash.frame(mid, W, H);
		List<Quad> drops = dropQuads(frame);
		check(drops.size() < before, "the wipe must have taken drops by its middle");
		check(frame.size() > drops.size(), "the wipe must leave smears behind it");
		for (Quad quad : drops) {
			float y = (quad.y0() + quad.y1()) / 2;
			float line = splash.wipeLineX(mid, Math.round(y) / (float) H, W);
			// a drop may stick out of the line by its rounding only; its centre fixed the cut
			boolean ahead = splash.wipeDirection() > 0 ? quad.x1() > line - 2 : quad.x0() < line + 2;
			check(ahead, "a drop survives behind the wipe line at " + line + ": " + quad);
		}
		check(dropQuads(splash.frame(hold + 13, W, H)).isEmpty(), "past the wipe no drop may be left, only smears");
		int early = maxAlpha(splash.frame(hold + 13, W, H));
		int late = maxAlpha(splash.frame(splash.lifetimeTicks() - 0.5F, W, H));
		check(late < early, "the smears must fade: " + early + " then " + late);
	}

	private static int maxAlpha(List<Quad> quads) {
		return quads.stream().mapToInt(quad -> quad.color() >>> 24).max().orElse(0);
	}

	// the smear cells are 96 by 16 and stretched; every other cell is a drop or a run
	private static List<Quad> dropQuads(List<Quad> quads) {
		return quads.stream().filter(quad -> Math.abs(quad.v1() - quad.v0()) != 16 || Math.abs(quad.u1() - quad.u0()) <= 16).toList();
	}

	private static float lowestBloodRun(ScreenSplash splash, float age) {
		float lowest = 0;
		for (Quad quad : splash.frame(age, W, H)) {
			if (quad.u0() >= 192 && quad.u1() <= 224 && quad.v1() <= 112 && quad.v0() >= 64) {
				lowest = Math.max(lowest, quad.y1() - quad.y0());
			}
		}
		return lowest;
	}

	private static float area(Quad quad) {
		return (quad.x1() - quad.x0()) * (quad.y1() - quad.y0());
	}

	private static float coverage(List<Quad> quads, int x0, int y0, int x1, int y1) {
		int covered = 0;
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				for (Quad quad : quads) {
					if (x >= quad.x0() && x < quad.x1() && y >= quad.y0() && y < quad.y1()) {
						covered++;
						break;
					}
				}
			}
		}
		return covered / (float) ((x1 - x0) * (y1 - y0));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
