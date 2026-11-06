package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputBindTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import rotp.core.powersystem.standpower.type.StandType;

import com.mojang.datafixers.util.Pair;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16's shiftVariationOf: X uses a slot's base, Shift + X its variation, and the same on LMB. A variation has no slot of
 * its own, and none of them takes a key from grab, charged heavy, throw or release (the grab key is Shift + RMB).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandSlotVariationGameTests {
	private record SlotVariation(String stand, String base, String variation, InputMethod inputMethod) {}
	private record KeyVariation(String stand, String base, String variation, InputMethod inputMethod) {}

	private static final List<SlotVariation> ON_THE_BAR = List.of(
			new SlotVariation("star_platinum", "time_stop", "time_stop_blink", InputMethod.CLICK),
			new SlotVariation("the_world", "time_stop", "time_stop_blink", InputMethod.CLICK),
			new SlotVariation("hierophant_green", "grapple", "grapple_entity", InputMethod.HOLD),
			new SlotVariation("magicians_red", "crossfire_hurricane", "crossfire_hurricane_special", InputMethod.HOLD),
			new SlotVariation("crazy_diamond", "repair_item", "revert_state", InputMethod.HOLD),
			new SlotVariation("crazy_diamond", "block_anchor", "block_anchor_make", InputMethod.CLICK),
			new SlotVariation("gold_experience", "create_lifeform", "revert_lifeform", InputMethod.CLICK),
			new SlotVariation("gold_experience", "heal", "heal_other", InputMethod.CLICK));

	private static final List<KeyVariation> ON_THE_ATTACK_BUTTON = List.of(
			new KeyVariation("hierophant_green", "string_attack", "string_bind", InputMethod.CLICK),
			new KeyVariation("hierophant_green", "emerald_splash", "emerald_splash_concentrated", InputMethod.HOLD));

	private static final List<String> STANDS = List.of("star_platinum", "the_world", "hierophant_green", "magicians_red",
			"crazy_diamond", "gold_experience", "silver_chariot");

	private StandSlotVariationGameTests() {}

	private static ControlSchemeTemplate template(GameTestHelper helper, String stand) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(stand));
		helper.assertTrue(type != null, "Missing Stand type " + stand);
		return type.makeDefaultControlSchemeTemplate();
	}

	private static List<Map<InputKey.Modifier, Map<InputMethod, String>>> slots(ControlSchemeTemplate template) {
		List<Map<InputKey.Modifier, Map<InputMethod, String>>> slots = new ArrayList<>();
		for (ControlSchemeTemplate.GroupTemplate group : template.groups.values()) {
			for (ControlSchemeTemplate.AbilitiesHotbar hotbar : group.hotbars) {
				slots.addAll(hotbar.slots);
			}
		}
		return slots;
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void variationsSitUnderTheirBaseOnTheBar(GameTestHelper helper) {
		for (SlotVariation row : ON_THE_BAR) {
			String where = row.stand() + " " + row.variation();
			boolean found = false;
			for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : slots(template(helper, row.stand()))) {
				Map<InputMethod, String> base = slot.get(null);
				helper.assertTrue(base == null || !base.containsValue(row.variation()), where + " still has a slot of its own");
				if (base != null && base.containsValue(row.base())) {
					Map<InputMethod, String> shift = slot.get(InputKey.Modifier.SHIFT);
					found = shift != null && row.variation().equals(shift.get(row.inputMethod()));
				}
			}
			helper.assertTrue(found, where + " is not the Shift " + row.inputMethod() + " of " + row.base());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void attackButtonVariationsAreOnShiftAndLeftClick(GameTestHelper helper) {
		for (KeyVariation row : ON_THE_ATTACK_BUTTON) {
			String where = row.stand() + " " + row.variation();
			ControlSchemeTemplate template = template(helper, row.stand());
			for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : slots(template)) {
				for (Map<InputMethod, String> methods : slot.values()) {
					helper.assertTrue(!methods.containsValue(row.variation()), where + " is still on the skill bar");
				}
			}
			Map<String, Pair<InputMethod, InputBindTemplate>> binds = template.defaultGroup.separateBinds;
			Pair<InputMethod, InputBindTemplate> base = binds.get(row.base());
			helper.assertTrue(base != null && base.getFirst() == row.inputMethod() && base.getSecond() == InputKey.LMB,
					row.base() + " left the plain left click");
			Pair<InputMethod, InputBindTemplate> variation = binds.get(row.variation());
			helper.assertTrue(variation != null && variation.getFirst() == row.inputMethod()
					&& variation.getSecond() == InputKey.LMB.withModifier(InputKey.Modifier.SHIFT),
					where + " is not on Shift + left click");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void noVariationTakesTheGrabKey(GameTestHelper helper) {
		for (String stand : STANDS) {
			ControlSchemeTemplate template = template(helper, stand);
			for (var bind : template.defaultGroup.separateBinds.entrySet()) {
				helper.assertTrue(bind.getValue().getSecond() != InputKey.RMB.withModifier(InputKey.Modifier.SHIFT),
						stand + " " + bind.getKey() + " took Shift + RMB, the default of grab and charged heavy");
			}
			for (String keepsTheMapping : new String[] { "grab", "heavy_charged" }) {
				Pair<InputMethod, InputBindTemplate> bind = template.defaultGroup.separateBinds.get(keepsTheMapping);
				helper.assertTrue(bind == null || bind.getSecond() instanceof InputUseVanillaMapping mapping
						&& MovesetBuilder.GRAB_CHARGED_HEAVY_KEY_MAPPING_NAME.equals(mapping.keyMappingName),
						stand + " " + keepsTheMapping + " left its own key mapping");
			}
			Pair<InputMethod, InputBindTemplate> grabThrow = template.defaultGroup.separateBinds.get("grab_throw");
			helper.assertTrue(grabThrow == null || grabThrow.getSecond() == MovesetBuilder.DEFAULT_GRAB_THROW_INPUT
					&& MovesetBuilder.DEFAULT_GRAB_THROW_INPUT instanceof InputUseVanillaMapping,
					stand + " grab_throw left its key mapping");
			Pair<InputMethod, InputBindTemplate> release = template.defaultGroup.separateBinds.get("grab_release");
			helper.assertTrue(release == null || release.getSecond() == MovesetBuilder.DEFAULT_GRAB_RELEASE_INPUT
					&& MovesetBuilder.DEFAULT_GRAB_RELEASE_INPUT instanceof InputUseVanillaMapping,
					stand + " grab_release left its key mapping");
		}
		helper.succeed();
	}
}
