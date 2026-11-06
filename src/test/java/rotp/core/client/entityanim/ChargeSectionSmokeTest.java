package rotp.core.client.entityanim;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.powersystem.entityaction.ActionPhase;

/**
 * BUTTON_CHARGE on the real clips through the real clip player: a clip with a charge section starts it at the press, with no jump to its windup mark; a
 * 1.16 clip with none draws the charge in its windup. Not the drawn game.
 */
public final class ChargeSectionSmokeTest {
	private ChargeSectionSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		KeyframesMolangEngine.init();
		Path root = Path.of(System.getProperty("user.dir"));
		String assets = "src/main/resources/assets/jojo_ripples/";
		RotpAnimDefinition withSection = clip(root.resolve(assets + "animations/stand_default.animation.json"), "grab_throw");
		atCompletion(withSection, ActionPhase.BUTTON_CHARGE, 0, 0, "grab_throw at the press");
		atCompletion(withSection, ActionPhase.BUTTON_CHARGE, 1, 0.5F, "grab_throw at the end of the charge");
		RotpAnimDefinition withoutSection = clip(root.resolve(assets
				+ "stand_skins/gold_experience/assets/jojo_ripples/animations/gold_experience.animation.json"), "heavy_punch");
		float windupEnd = firstMark(withoutSection, ActionPhase.PERFORM);
		atCompletion(withoutSection, ActionPhase.BUTTON_CHARGE, 0, 0, "a clip with no charge section at the press");
		atCompletion(withoutSection, ActionPhase.BUTTON_CHARGE, 1, windupEnd, "a clip with no charge section at the end of the charge");
		System.out.println("Charge section smoke test passed: a clip's charge section starts at the press, a clip with none charges in its windup");
	}

	private static void atCompletion(RotpAnimDefinition anim, ActionPhase phase, float completion, float seconds, String what) {
		LivingAnimState state = new LivingAnimState();
		state.reset();
		state.actionPhase = phase;
		state.phaseCompletion = completion;
		state.phaseTime = 0;
		state.time = 0;
		float got = anim.getAnimTime(state);
		if (Math.abs(got - seconds) > 1.0E-4F) {
			throw new AssertionError(what + " draws the clip at " + got + " s, not " + seconds + " s");
		}
	}

	private static float firstMark(RotpAnimDefinition anim, ActionPhase phase) {
		for (var section : anim.instructionTimelines.phases.getEntries()) {
			if (section.getValue().phase == phase) {
				return section.getFloatKey();
			}
		}
		throw new AssertionError("setup: the clip has no " + phase + " mark");
	}

	private static RotpAnimDefinition clip(Path file, String name) {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			return ParseGeckoAnims.parseAnim(root.getAsJsonObject("animations").getAsJsonObject(name));
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read " + file, e);
		}
	}
}
