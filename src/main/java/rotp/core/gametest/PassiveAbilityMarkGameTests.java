package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * An add-on marks an ability that acts by itself as passive (Ability#passive, or an override of Ability#isPassive): the
 * Stand skills screen lists it under its passive skills, and the controls screen does not offer it. Ability classes as
 * King Crimson's passive erases are (an Ability with no key).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PassiveAbilityMarkGameTests {
	private PassiveAbilityMarkGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void anAbilityMarkedPassiveIsPassiveAndOnNoControl(GameTestHelper helper) {
		Ability plain = create("passive_mark_test_plain");
		Ability marked = create("passive_mark_test_passive").passive();
		Ability overriding = new AbilityType<Ability>(JojoMod.resLoc("passive_mark_test_override"), (type, id) -> new Ability(type, id) {
			@Override
			public boolean isPassive() {
				return true;
			}
		}).createInstance(id("passive_mark_test_override"));
		helper.assertTrue(!plain.isPassive() && plain.addToControlSchemeEditing(),
				"An ability not marked passive is passive " + plain.isPassive() + ", on the controls screen " + plain.addToControlSchemeEditing());
		helper.assertTrue(marked.isPassive() && !marked.addToControlSchemeEditing(),
				"An ability marked passive is passive " + marked.isPassive() + ", on the controls screen " + marked.addToControlSchemeEditing());
		helper.assertTrue(overriding.isPassive() && !overriding.addToControlSchemeEditing(),
				"An ability class that says it is passive is on the controls screen " + overriding.addToControlSchemeEditing());
		helper.succeed();
	}

	private static Ability create(String name) {
		return new AbilityType<Ability>(JojoMod.resLoc(name), Ability::new).createInstance(id(name));
	}

	private static AbilityId id(String name) {
		return new AbilityId(PowerClass.STAND, JojoMod.resLoc("star_platinum"), name);
	}
}
