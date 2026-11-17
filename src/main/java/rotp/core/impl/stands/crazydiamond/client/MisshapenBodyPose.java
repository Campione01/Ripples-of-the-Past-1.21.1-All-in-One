package rotp.core.impl.stands.crazydiamond.client;

import rotp.core.api.client.render.HumanoidModelPostSetup;
import rotp.core.core.JojoMod;
import rotp.core.init.ModStatusEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * How a misshapen part looks on a humanoid: the head upside down, the arms or the legs turned round where they join the
 * body, so they swing the wrong way. The official misheal has no texture for it.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MisshapenBodyPose {

	private MisshapenBodyPose() {}

	@SubscribeEvent
	public static void register(FMLClientSetupEvent event) {
		HumanoidModelPostSetup.register(JojoMod.resLoc("misshapen_body"), MisshapenBodyPose::apply);
	}

	static void apply(LivingEntity entity, HumanoidModel<?> model) {
		boolean face = entity.hasEffect(ModStatusEffects.MISSHAPEN_FACE);
		boolean arms = entity.hasEffect(ModStatusEffects.MISSHAPEN_ARMS);
		boolean legs = entity.hasEffect(ModStatusEffects.MISSHAPEN_LEGS);
		if (!(face || arms || legs)) {
			return;
		}
		// the first-person hand is drawn through the same setup
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		pose(model.head, model.hat, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg, face, arms, legs);
	}

	public static void pose(ModelPart head, ModelPart hat, ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg,
			ModelPart leftLeg, boolean face, boolean arms, boolean legs) {
		if (face) {
			head.zRot += Mth.PI;
			hat.copyFrom(head);
		}
		if (arms) {
			rightArm.yRot += Mth.PI;
			leftArm.yRot += Mth.PI;
		}
		if (legs) {
			rightLeg.yRot += Mth.PI;
			leftLeg.yRot += Mth.PI;
		}
	}
}
