package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.PowerClass;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * DamageSourceModified.copyModifications: a source rebuilt for other entities (a recorded hit King Crimson's Epitaph
 * carries out with the real attacker) takes every value the core keeps on a damage source. Every such field of the
 * mixin is set by reflection, so a field added later without its copy fails here.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DamageSourceCopyGameTests {
	private static final String FIELD_PREFIX = "jojo_ripples$";

	private DamageSourceCopyGameTests() {}

	@GameTest(template = "empty")
	public static void aRebuiltSourceTakesEveryValueTheCoreKeepsOnADamageSource(GameTestHelper helper) {
		Zombie recorded = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		Zombie real = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
		Cow countered = helper.spawn(EntityType.COW, new BlockPos(1, 1, 3));
		DamageSource from = helper.getLevel().damageSources().mobAttack(recorded);
		DamageSource to = helper.getLevel().damageSources().mobAttack(real);
		List<Field> fields = new ArrayList<>();
		try {
			int n = 0;
			for (Field field : DamageSource.class.getDeclaredFields()) {
				if (!field.getName().startsWith(FIELD_PREFIX) || Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				field.setAccessible(true);
				fields.add(field);
				n++;
				Class<?> type = field.getType();
				if (type == float.class) {
					field.setFloat(from, 0.125F * n);
				}
				else if (type == int.class) {
					if (field.getName().endsWith("counteredTargetId")) {
						continue; // an entity id, set below
					}
					field.setInt(from, 3 + n);
				}
				else if (type == boolean.class) {
					field.setBoolean(from, !field.getBoolean(from));
				}
				else if (type.isInstance(PowerClass.STAND.attachGet(recorded))) {
					field.set(from, PowerClass.STAND.attachGet(recorded));
				}
				else {
					throw new IllegalStateException("Setup: no test value for " + field);
				}
			}
			((DamageSourceModified) from).jojo_ripples$setCounteredTarget(countered);
			helper.assertTrue(fields.size() >= 14, "Setup: only " + fields.size() + " fields of the core on a damage source");
			helper.assertTrue(DamageSourceModified.copyModifications(from, to) == to, "The copy did not answer its target");
			List<String> differ = new ArrayList<>();
			for (Field field : fields) {
				if (!Objects.equals(field.get(from), field.get(to))) {
					differ.add(field.getName() + " " + field.get(from) + " -> " + field.get(to));
				}
			}
			helper.assertTrue(differ.isEmpty(), "A rebuilt source lost values of the core: " + differ);
			helper.assertTrue(((DamageSourceModified) to).jojo_ripples$isCounteredOn(countered)
					&& to.getEntity() == real && to.typeHolder().equals(from.typeHolder()),
					"A rebuilt source is not countered on the cow, or lost its own attacker: " + to.getEntity());
		}
		catch (IllegalAccessException error) {
			throw new IllegalStateException("Setup: could not read the core's damage source fields", error);
		}
		helper.succeed();
	}
}
