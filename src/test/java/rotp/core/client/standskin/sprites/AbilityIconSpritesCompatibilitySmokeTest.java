package rotp.core.client.standskin.sprites;

import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.unlockableskill.UnlockableSkill;
import rotp.core.impl.stands._entitybase.StandEntityGrabReleaseAbility;

import net.minecraft.resources.ResourceLocation;

public final class AbilityIconSpritesCompatibilitySmokeTest {
	private AbilityIconSpritesCompatibilitySmokeTest() {}

	public static void run() {
		ResourceLocation legacy = AbilityIconSprites.memoize2.apply("heavy_punch");
		check("jojo_ripples".equals(legacy.getNamespace()),
				"legacy icon cache must keep the core namespace");
		check("ability/heavy_punch".equals(legacy.getPath()),
				"legacy icon cache must keep accepting String names");

		ResourceLocation addonAbility = ResourceLocation.fromNamespaceAndPath(
				"rotp_test", "heavy_punch");
		ResourceLocation namespaced = AbilityIconSprites.defaultSpritePath(addonAbility);
		check("rotp_test".equals(namespaced.getNamespace()),
				"namespaced icon cache must preserve the addon namespace");
		check("ability/heavy_punch".equals(namespaced.getPath()),
				"namespaced icon cache must use the standard ability path");

		AbilityType<StandEntityGrabReleaseAbility> releaseType = new AbilityType<>(
				ResourceLocation.fromNamespaceAndPath("jojo_ripples", "stand_grab_release"),
				StandEntityGrabReleaseAbility::new);
		StandEntityGrabReleaseAbility release = releaseType.createInstance(new AbilityId(
				null, ResourceLocation.fromNamespaceAndPath("rotp_test", "test_stand"), "grab_release"));
		check(ResourceLocation.fromNamespaceAndPath("jojo_ripples", "stand_grab_release")
				.equals(release.getSpriteId(null)), "addon release must use the shared release icon");
		check("jojo_ripples.ability.grab_release".equals(release.getTranslationKey()),
				"automatically added release must use its existing core translation");
		try (InputStream releaseIcon = AbilityIconSpritesCompatibilitySmokeTest.class.getResourceAsStream(
				"/assets/jojo_ripples/textures/ability/stand_grab_release.png")) {
			check(releaseIcon != null, "shared release icon must be packaged");
			BufferedImage icon = ImageIO.read(releaseIcon);
			check(icon != null && icon.getWidth() == 32 && icon.getHeight() == 32,
					"shared release icon must be a readable 32px PNG");
			int visiblePixels = 0;
			int transparentPixels = 0;
			for (int y = 0; y < icon.getHeight(); y++) {
				for (int x = 0; x < icon.getWidth(); x++) {
					if ((icon.getRGB(x, y) >>> 24) == 0) {
						transparentPixels++;
					}
					else {
						visiblePixels++;
					}
				}
			}
			check(visiblePixels > 0 && transparentPixels > 0,
					"shared release icon must have visible art on a transparent background");
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read the shared release icon", exception);
		}

		checkSkillIconAgreesWithHud();
	}

	// The skills screen must not take the core's icon of a name while the HUD shows the add-on's.
	private static void checkSkillIconAgreesWithHud() {
		ResourceLocation stand = ResourceLocation.fromNamespaceAndPath("rotp_test", "test_stand");
		AbilityType<Ability> type = new AbilityType<>(
				ResourceLocation.fromNamespaceAndPath("rotp_test", "plain"), Ability::new);
		AbilityType<Ability> fixedSpriteType = new AbilityType<>(
				ResourceLocation.fromNamespaceAndPath("rotp_test", "fixed_sprite"),
				(abilityType, id) -> new Ability(abilityType, id) {
					@Override public String getSpriteName(Power<?> context) { return "block"; }
				});
		Map<String, Ability> abilities = new LinkedHashMap<>();
		for (String name : List.of("punch", "second_of_group", "core_only")) {
			abilities.put(name, type.createInstance(new AbilityId(null, stand, name)));
		}
		abilities.put("guard", fixedSpriteType.createInstance(new AbilityId(null, stand, "guard")));
		Moveset moveset = new Moveset(abilities, null, stand);

		Set<String> icons = Set.of("rotp_test:punch", "jojo_ripples:punch",
				"rotp_test:block", "jojo_ripples:guard",
				"rotp_test:group", "jojo_ripples:group",
				"rotp_test:second_of_group", "jojo_ripples:core_only");

		checkEquals("rotp_test:punch", skillIcon(StandUnlockableSkill.startingAbility("punch"), moveset, stand, icons),
				"an add-on icon with a core name must win, as on the HUD");
		checkEquals("rotp_test:block", skillIcon(StandUnlockableSkill.startingAbility("guard"), moveset, stand, icons),
				"a fixed sprite of the ability must win over the core's icon of the skill's name, as on the HUD");
		checkEquals("rotp_test:group", skillIcon(new StandUnlockableSkill("group").withAbility("second_of_group"), moveset, stand, icons),
				"a skill with no ability of its name must take its own icon from its Stand's namespace");
		checkEquals("rotp_test:second_of_group", skillIcon(new StandUnlockableSkill("no_own_icon").withAbility("second_of_group"), moveset, stand, icons),
				"a skill with no icon of its own must take the icon of an ability it unlocks");
		checkEquals("jojo_ripples:core_only", skillIcon(StandUnlockableSkill.startingAbility("core_only"), moveset, stand, icons),
				"a skill with no add-on icon keeps the core's icon of that name");
		checkEquals(MISSING, skillIcon(new StandUnlockableSkill("nothing").withAbility("not_in_moveset"), moveset, stand, icons),
				"a skill with no icon anywhere is missing");
		checkEquals("jojo_ripples:punch", skillIcon(StandUnlockableSkill.startingAbility("punch"), null, null, icons),
				"with no power only the core's icon is left");
	}

	private static final String MISSING = "missing";

	private static String skillIcon(UnlockableSkill skill, Moveset moveset, ResourceLocation stand, Set<String> icons) {
		return AbilityIconSprites.resolveSkillIcon(skill, moveset, stand, null,
				id -> icons.contains(id.toString()) ? id.toString() : MISSING, MISSING);
	}

	private static void checkEquals(String expected, String actual, String message) {
		check(expected.equals(actual), message + ": expected " + expected + ", got " + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
