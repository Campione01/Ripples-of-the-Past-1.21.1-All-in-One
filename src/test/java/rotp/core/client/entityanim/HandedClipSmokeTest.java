package rotp.core.client.entityanim;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;

/**
 * Real clips through the core's mirroring: the hand a clip file writes itself is played as written, the other is the file's mirrored clip, and a clip with no written
 * hand still gets both. Not the drawn game.
 */
public final class HandedClipSmokeTest {
	private HandedClipSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		KeyframesMolangEngine.init();
		Path assets = Path.of(System.getProperty("user.dir")).resolve("src/main/resources/assets/jojo_ripples");
		JsonObject defaults = clips(assets.resolve("animations/stand_default.animation.json"));
		JsonObject crazyDiamond = clips(assets.resolve("stand_skins/crazy_diamond/assets/jojo_ripples/animations/crazy_diamond.animation.json"));
		for (String clip : List.of("block_toss", "bow_shoot")) {
			Map<String, List<RotpAnimDefinition>> set = new HashMap<>();
			RotpAnimDefinition written = parsed(set, defaults, clip);
			RotpAnimDefinition writtenHand = parsed(set, defaults, clip + "_right");
			RotpAnimDefinition withoutAHand = parsed(set, crazyDiamond, "repair_item");
			AnimationMirror.doMirroringOnAnimSet(set);
			expect(set.get(clip + "_right").get(0) == writtenHand, clip + "_right is not the clip the file writes");
			expect(set.get(clip + "_left").get(0) == written, clip + "_left is not the clip it is written for");
			expect(!set.containsKey(clip), clip + " is still there under its base name, which no hand asks for");
			expect(set.get("repair_item_right").get(0) == withoutAHand, "the side a clip is written for is not played as written");
			expect(set.get("repair_item_left").get(0) != withoutAHand, "a clip with no hand written lost its mirrored copy");
		}
		System.out.println("Handed clip smoke test passed: a hand the file writes is played as written, a clip with none is still mirrored");
	}

	private static void expect(boolean what, String message) {
		if (!what) {
			throw new AssertionError(message);
		}
	}

	private static RotpAnimDefinition parsed(Map<String, List<RotpAnimDefinition>> set, JsonObject clips, String name) {
		expect(clips.has(name), "the clip file has no " + name);
		RotpAnimDefinition anim = ParseGeckoAnims.parseAnim(clips.getAsJsonObject(name));
		set.put(name, new ArrayList<>(List.of(anim)));
		return anim;
	}

	private static JsonObject clips(Path file) {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read " + file, e);
		}
	}
}
