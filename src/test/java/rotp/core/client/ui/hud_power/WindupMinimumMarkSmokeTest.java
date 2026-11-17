package rotp.core.client.ui.hud_power;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OQ-C02: the crosshair's charge fill marks the time stop's minimum charge (half the charge, TimeStopAbility's
 * getMinReleaseChargeTicks), the share at which a release starts a stop; other fills have no mark. The dashes themselves
 * are an in-game check.
 */
public final class WindupMinimumMarkSmokeTest {
	private WindupMinimumMarkSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		WindupIndicator charge = new WindupIndicator();
		charge.maxValue = 30;
		charge.value = 4;
		charge.minValue = 15;
		require(charge.markRatio() == 0.5F, "a 30-tick charge whose release works from 15 is marked at half: " + charge.markRatio());
		require(WindupIndicator.fillHeight(0.5F, 15) == 7.5F && WindupIndicator.fillHeight(0, 15) == 3
				&& WindupIndicator.fillHeight(1, 15) == 15, "the fill grows from 3 to 12 pixels of the 15-pixel crosshair, then fills it");

		WindupIndicator copy = new WindupIndicator();
		copy.copyFrom(charge);
		require(copy.markRatio() == 0.5F, "the crosshair's copy keeps the mark");

		WindupIndicator plain = new WindupIndicator();
		plain.maxValue = 20;
		plain.value = 5;
		require(plain.markRatio() < 0, "a fill with no minimum has no mark");
		plain.minValue = 20;
		require(plain.markRatio() < 0, "a minimum at the full charge is no mark");

		String timeStop = compact(source("impl/stands/theworld/TimeStopAbility.java"));
		require(timeStop.contains("indicator.minValue=getMinReleaseChargeTicks(indicator.maxValue);"),
				"the time stop's charge fill takes its minimum from the release rule");
		require(compact(source("client/ui/hud_power/PowerHud.java")).contains("activeActionWindup.minValue=-1;"
				+ "WindupIndicatorwindup=ability.cl_windupIndicator(player,activeActionWindup,partialTick);"),
				"the HUD's shared indicator forgets the last ability's minimum before it asks the next");
		require(compact(source("client/ui/hud_power/WindupAtCrosshair.java")).contains("floatmarkRatio=_renderWindup.markRatio();"),
				"the crosshair draws the mark");
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
			throw new IllegalStateException("WindupMinimumMarkSmokeTest: cannot read " + path, e);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException("WindupMinimumMarkSmokeTest: " + message);
		}
	}
}
