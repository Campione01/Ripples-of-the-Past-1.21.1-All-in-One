package rotp.core.mechanics.voiceline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Every 1.16 shout the official voice packs have a trigger for says it with that trigger, so a pack line can replace
 * it, or be added where 1.16 says nothing. VoiceLinePackGameTests plays the summon and Tommy Gun ones in the game;
 * StarFinger's is pinned in AbilityShoutSmokeTest.
 */
public final class VoiceLineTriggerWiringSmokeTest {
	private static final String ROOT = "src/main/java/rotp/core/";

	private VoiceLineTriggerWiringSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		require("powersystem/standpower/type/StandType.java",
				"JojoModUtil.sayVoiceLine(user,shout,ModSoundEvents.VOICELINE_STAND_SUMMON,1.0F,1.0F,200,false);");
		require("impl/stands/theworld/TimeStopAbility.java",
				"JojoModUtil.sayVoiceLine(user,resolved,ModSoundEvents.VOICELINE_TIME_STOP,1.0F,1.0F,0,false)");
		require("subsystems/timestop/TimeStopState.java",
				"JojoModUtil.sayVoiceLine(resumeVoiceLineUser,voiceLine,ModSoundEvents.VOICELINE_TIME_RESUME,1.0F,1.0F,200,false);");
		String tommyGun = "item/TommyGunItem.java";
		require(tommyGun, "josephVoiceLine(entity,ModSoundEvents.JOSEPH_SCREAM_SHOOTING,ModSoundEvents.VOICELINE_TOMMY_GUN_SCREAM);");
		require(tommyGun, "josephVoiceLine(entity,ModSoundEvents.JOSEPH_WAR_DECLARATION,ModSoundEvents.VOICELINE_TOMMY_GUN_SHOT_MAGAZINE);");
		require(tommyGun, "JojoModUtil.sayVoiceLine(user,ModSoundEvents.JOSEPH_SHOOT,ModSoundEvents.VOICELINE_TOMMY_GUN_PUNCH,1.0F,1.0F,200,false);");
	}

	private static void require(String path, String token) {
		if (!compact(source(path)).contains(token)) {
			throw new AssertionError("a voice pack trigger is no longer said with its 1.16 shout in " + path + ": " + token);
		}
	}

	private static String compact(String source) {
		return source
				.replaceAll("(?s)/\\*.*?\\*/", "")
				.replaceAll("//[^\\n]*", "")
				.replaceAll("\\s+", "");
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(ROOT + path));
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read " + ROOT + path, exception);
		}
	}
}
