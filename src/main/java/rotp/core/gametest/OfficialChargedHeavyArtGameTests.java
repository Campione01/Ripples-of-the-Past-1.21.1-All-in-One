package rotp.core.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The official port's charged heavy art (the owner's archive of 2026-11-12, R772.1): a Stand's charged heavy punch lands
 * with its own heavy punch's sound, and Star Platinum and Crazy Diamond show the official charged heavy icon instead of
 * falling back to the heavy punch's.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OfficialChargedHeavyArtGameTests {
	private OfficialChargedHeavyArtGameTests() {}

	private static final String CHARGED = "stand_punch_heavy_charged";
	// the event each Stand's heavy_punch lands with (StandInit*: Crazy Diamond and Gold Experience set their own)
	private static final Map<String, String> PLAIN_HEAVY = Map.of(
			"star_platinum", "stand_punch_heavy",
			"the_world", "stand_punch_heavy",
			"crazy_diamond", "crazy_diamond_punch_heavy",
			"gold_experience", "gold_experience_punch_heavy");
	private static final List<String> CHARGED_ICONS = List.of("star_platinum", "crazy_diamond");

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theChargedHeavyLandsWithTheStandsOwnHeavySound(GameTestHelper helper) {
		JsonObject root = readJson(helper, "sounds.json");
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, String> stand : PLAIN_HEAVY.entrySet()) {
			JsonObject skin = readJson(helper, "stand_skins/" + stand.getKey() + "/assets/jojo_ripples/sounds.json");
			List<String> charged = sounds(CHARGED, skin, root, 0);
			List<String> plain = sounds(stand.getValue(), skin, root, 0);
			if (!skin.has(CHARGED) || !charged.equals(plain)) {
				wrong.add(stand.getKey() + ": charged " + charged + ", heavy " + plain);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "A Stand's charged heavy must land with its heavy punch's sounds: " + wrong);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void starPlatinumAndCrazyDiamondShowTheOfficialChargedHeavyIcon(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String stand : CHARGED_ICONS) {
			String dir = "/assets/jojo_ripples/stand_skins/" + stand + "/assets/jojo_ripples/textures/ability/";
			int[] charged = pixels(helper, dir + "heavy_charged.png");
			int[] heavy = pixels(helper, dir + "heavy_punch.png");
			if (charged == null || charged.length != 32 * 32 || Arrays.equals(charged, heavy)
					|| Arrays.stream(charged).noneMatch(argb -> argb >>> 24 != 0)) {
				wrong.add(stand);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "These skins lack a 32px charged heavy icon of their own: " + wrong);
		helper.succeed();
	}

	// the sound files an event plays, the skin's entry first, then the core's, events followed through
	private static List<String> sounds(String event, JsonObject skin, JsonObject root, int depth) {
		JsonObject entry = skin.has(event) ? skin.getAsJsonObject(event) : root.getAsJsonObject(event);
		List<String> out = new ArrayList<>();
		if (entry == null || depth > 4) {
			out.add("<no " + event + ">");
			return out;
		}
		for (JsonElement sound : entry.getAsJsonArray("sounds")) {
			if (sound.isJsonObject()) {
				JsonObject o = sound.getAsJsonObject();
				if (o.has("type") && "event".equals(o.get("type").getAsString())) {
					out.addAll(sounds(path(o.get("name").getAsString()), skin, root, depth + 1));
				}
				else {
					out.add(o.get("name").getAsString() + (o.has("weight") ? "x" + o.get("weight").getAsInt() : ""));
				}
			}
			else {
				out.add(sound.getAsString());
			}
		}
		out.sort(null);
		return out;
	}

	private static String path(String id) {
		return id.startsWith(JojoMod.MOD_ID + ":") ? id.substring(JojoMod.MOD_ID.length() + 1) : id;
	}

	private static int[] pixels(GameTestHelper helper, String resource) {
		try (InputStream in = OfficialChargedHeavyArtGameTests.class.getResourceAsStream(resource)) {
			if (in == null) {
				return null;
			}
			BufferedImage image = ImageIO.read(in);
			return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read " + resource, e);
		}
	}

	private static JsonObject readJson(GameTestHelper helper, String assetPath) {
		try (InputStream in = OfficialChargedHeavyArtGameTests.class.getResourceAsStream("/assets/jojo_ripples/" + assetPath)) {
			helper.assertTrue(in != null, "Missing asset " + assetPath);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read " + assetPath, e);
		}
	}
}
