package rotp.core.impl.stands.crazydiamond;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.StatusEffectModified;
import rotp.core.init.ModStatusEffects;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * A body part Crazy Diamond restored wrongly (the Disfiguring Punch, the official "misheal"). A player's part is felt in the
 * controls (client side, MisshapenControls); a mob's part works here: a face that cannot hold a target, arms whose blows do
 * no damage, legs at half speed that cannot jump. Crazy Diamond's Heal restores it properly ({@link #cure}).
 */
public class MisshapenEffect extends StatusEffectModified {
	public enum Part { FACE, ARMS, LEGS }

	static final ResourceLocation LEGS_SPEED_ID = JojoMod.resLoc("effect.misshapen_legs.movement_speed");
	static final ResourceLocation LEGS_JUMP_ID = JojoMod.resLoc("effect.misshapen_legs.jump_strength");
	private static final AttributeModifier LEGS_SPEED = new AttributeModifier(LEGS_SPEED_ID, -0.5,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier LEGS_JUMP = new AttributeModifier(LEGS_JUMP_ID, -1.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

	public final Part part;

	public MisshapenEffect(Part part) {
		super(MobEffectCategory.HARMFUL, 0x808080);
		this.part = part;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return part != Part.ARMS;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		if (entity instanceof Player || entity.level().isClientSide()) {
			return true;
		}
		switch (part) {
		case FACE -> {
			if (entity instanceof Mob mob) {
				dropTarget(mob);
			}
		}
		case LEGS -> {
			addOnce(entity.getAttribute(Attributes.MOVEMENT_SPEED), LEGS_SPEED);
			addOnce(entity.getAttribute(Attributes.JUMP_STRENGTH), LEGS_JUMP);
		}
		case ARMS -> {}
		}
		return true;
	}

	// every way an effect ends (time, milk, a cure, /effect clear, death) goes through here; a player never had them
	@Override
	public void removeAttributeModifiers(AttributeMap attributeMap) {
		super.removeAttributeModifiers(attributeMap);
		if (part == Part.LEGS) {
			removeIfPresent(attributeMap, Attributes.MOVEMENT_SPEED, LEGS_SPEED_ID);
			removeIfPresent(attributeMap, Attributes.JUMP_STRENGTH, LEGS_JUMP_ID);
		}
	}

	private static void addOnce(AttributeInstance attribute, AttributeModifier modifier) {
		if (attribute != null && !attribute.hasModifier(modifier.id())) {
			attribute.addTransientModifier(modifier);
		}
	}

	private static void removeIfPresent(AttributeMap attributeMap, Holder<Attribute> attribute, ResourceLocation id) {
		AttributeInstance instance = attributeMap.getInstance(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	private static void dropTarget(Mob mob) {
		if (mob.getTarget() != null) {
			mob.setTarget(null);
		}
		if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
	}

	public static List<Holder<MobEffect>> effects() {
		return List.of(ModStatusEffects.MISSHAPEN_FACE, ModStatusEffects.MISSHAPEN_ARMS, ModStatusEffects.MISSHAPEN_LEGS);
	}

	public static boolean isMisshapen(LivingEntity entity) {
		for (Holder<MobEffect> effect : effects()) {
			if (entity.hasEffect(effect)) {
				return true;
			}
		}
		return false;
	}

	// Crazy Diamond restores the parts properly
	public static boolean cure(LivingEntity entity) {
		boolean cured = false;
		for (Holder<MobEffect> effect : effects()) {
			cured |= entity.removeEffect(effect);
		}
		return cured;
	}

	static boolean isMobMeleeBlow(DamageSource source) {
		return source.getEntity() instanceof Mob attacker && source.getDirectEntity() == attacker
				&& (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))
				&& !source.is(DamageTypeTags.IS_PROJECTILE);
	}


	@EventBusSubscriber(modid = JojoMod.MOD_ID)
	public static class MobEvents {

		@SubscribeEvent
		public static void faceTakesNoTarget(LivingChangeTargetEvent event) {
			LivingEntity mob = event.getEntity();
			if (!(mob instanceof Player) && event.getNewAboutToBeSetTarget() != null && mob.hasEffect(ModStatusEffects.MISSHAPEN_FACE)) {
				event.setCanceled(true);
			}
		}

		@SubscribeEvent
		public static void armsDealNoMeleeDamage(LivingIncomingDamageEvent event) {
			DamageSource source = event.getSource();
			if (isMobMeleeBlow(source) && ((Mob) source.getEntity()).hasEffect(ModStatusEffects.MISSHAPEN_ARMS)) {
				event.setCanceled(true);
			}
		}
	}
}
