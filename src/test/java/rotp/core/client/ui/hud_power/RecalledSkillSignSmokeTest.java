package rotp.core.client.ui.hud_power;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OQ-C04: a Stand skill that goes on with its Stand recalled (the owner's 收回仍持續) lights its HUD slot while the Stand
 * is away; with the Stand out, for another power, or for a skill that does not say so, nothing changes. The add-ons answer
 * Ability#cl_runsWithStandRecalled from their user-side state; the gold fill itself is an in-game check.
 */
public final class RecalledSkillSignSmokeTest {
	private RecalledSkillSignSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		require(PowerHudControlsElement.showsRunningWithStandRecalled(false, true, () -> true),
				"a skill going on with its Stand recalled lights its slot");
		require(!PowerHudControlsElement.showsRunningWithStandRecalled(true, true, () -> true),
				"with the Stand out its slot is drawn as ever");
		require(!PowerHudControlsElement.showsRunningWithStandRecalled(false, true, () -> false),
				"a skill that does not say it runs stays dark");
		require(!PowerHudControlsElement.showsRunningWithStandRecalled(false, false, () -> true),
				"another power's skill is not the Stand's to light");

		String hud = compact(source("client/ui/hud_power/PowerHudControlsElement.java"));
		require(count(hud, "renderRunningWithStandRecalled(guiGraphics,x,y,") == 2,
				"both the key binds and the hotbar slots draw the sign");
		require(hud.contains("ability.abilityId.powerClass()==PowerClass.STAND,()->ability.cl_runsWithStandRecalled(mc.player))"),
				"the HUD asks the ability itself, for the client's player");
		String ability = compact(source("powersystem/ability/Ability.java"));
		require(ability.contains("publicbooleancl_runsWithStandRecalled(LivingEntityuser){returnfalse;}"),
				"an ability that does not answer stays dark");
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
			n++;
		}
		return n;
	}

	private static String compact(String text) {
		return text.replaceAll("\\s+", "");
	}

	private static String source(String file) {
		Path path = Path.of(System.getProperty("user.dir")).resolve("src/main/java/rotp/core").resolve(file);
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw new IllegalStateException("RecalledSkillSignSmokeTest: cannot read " + path, e);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException("RecalledSkillSignSmokeTest: " + message);
		}
	}
}
