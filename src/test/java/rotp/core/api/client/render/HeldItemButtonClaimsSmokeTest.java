package rotp.core.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.resources.ResourceLocation;

/**
 * OQ-C22: a held item can claim a mouse button from the Stand's moves (Sex Pistols' revolver takes the left button), and
 * the HUD then shows the item in that button's slot. A claim that throws counts as none; one owner registers once. What the
 * slot looks like is an in-game check; rotp-sex-pistols registers the revolver's claim.
 */
public final class HeldItemButtonClaimsSmokeTest {
	private HeldItemButtonClaimsSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		require(!HeldItemButtonClaims.claims(null, null, HeldItemButtonClaims.Button.LEFT), "nothing claims a button by default");
		HeldItemButtonClaims.register(ResourceLocation.fromNamespaceAndPath("rotp_test", "throws"), (player, held, button) -> {
			throw new IllegalStateException("a broken claim");
		});
		HeldItemButtonClaims.register(ResourceLocation.fromNamespaceAndPath("rotp_test", "gun"),
				(player, held, button) -> button == HeldItemButtonClaims.Button.LEFT);
		require(HeldItemButtonClaims.claims(null, null, HeldItemButtonClaims.Button.LEFT),
				"a held item that claims the left button takes it, past a claim that throws");
		require(!HeldItemButtonClaims.claims(null, null, HeldItemButtonClaims.Button.RIGHT), "it leaves the right button");
		boolean refused = false;
		try {
			HeldItemButtonClaims.register(ResourceLocation.fromNamespaceAndPath("rotp_test", "gun"), (player, held, button) -> true);
		}
		catch (IllegalStateException expected) {
			refused = true;
		}
		require(refused, "an owner registers its claim once");

		String hud = compact(source("client/ui/hud_power/PowerHudControlsElement.java"));
		require(hud.contains("ItemStackclaimingItem=HeldItemButtonClaims.claimingItem(mc.player,claimedButton(ability.key));"
				+ "if(!claimingItem.isEmpty()){"), "the HUD asks the claims for each mouse-button slot");
		require(hud.contains("GuiItemIcon.render(guiGraphics,null,claimingItem,x+3,y+3,alpha);"),
				"a claimed slot shows the held item");
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
			throw new IllegalStateException("HeldItemButtonClaimsSmokeTest: cannot read " + path, e);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException("HeldItemButtonClaimsSmokeTest: " + message);
		}
	}
}
