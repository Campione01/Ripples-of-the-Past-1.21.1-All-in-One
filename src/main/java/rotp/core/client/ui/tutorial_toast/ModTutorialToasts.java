package rotp.core.client.ui.tutorial_toast;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import rotp.core.client.ClientPowerCache;
import rotp.core.config.client.ClientModSettings;
import rotp.core.config.client.TutorialProgress;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.client_screens.StandSkillsScreen;

/** Shows the tutorial's tips (TutorialTips) and marks each one learned once the player does what it says. */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class ModTutorialToasts {
	private static final TutorialTips.Schedule SCHEDULE = new TutorialTips.Schedule();

	private ModTutorialToasts() {}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		TutorialProgress progress = TutorialProgress.current();
		if (mc.player == null || progress == null || mc.isPaused()) {
			return;
		}
		ClientModSettings.Settings settings = ClientModSettings.getSettingsReadOnly();
		StandPower power = ClientPowerCache.getPower(PowerClass.STAND);
		boolean hasStand = power != null && power.hasPower();
		boolean summoned = hasStand && power.isSummoned();
		if (hasStand) {
			boolean skillsScreenOpen = mc.screen instanceof StandSkillsScreen;
			for (String tip : TutorialTips.ALL) {
				if (!progress.seen().contains(tip) && TutorialTips.learned(tip, summoned, skillsScreenOpen)) {
					progress.add(tip);
				}
			}
		}
		boolean canSummon = TutorialTips.canSummon(power);
		String next = SCHEDULE.tick(settings.tutorialEnabled, mc.player.tickCount, progress.seen(),
				tip -> hasStand && TutorialTips.wanted(tip, canSummon, summoned,
						TutorialTips.SKILLS.equals(tip) && TutorialTips.canLearnStandSkill(power)));
		if (next != null) {
			mc.getToasts().addToast(toastFor(next));
		}
	}

	private static TutorialToast toastFor(String tip) {
		Component line1;
		Component line2 = null;
		if (TutorialTips.SUMMON.equals(tip)) {
			line1 = Component.translatable("jojo_ripples.tutorial.summon_stand", Component.keybind(JojoMod.MOD_ID + ".key.toggle_stand"));
		}
		else {
			line1 = Component.translatable("jojo_ripples.tutorial.stand_skills.1", Component.keybind(JojoMod.MOD_ID + ".key.jojo_menu"));
			line2 = Component.translatable("jojo_ripples.tutorial.stand_skills.2");
		}
		return new TutorialToast(line1, line2, () -> tip.equals(SCHEDULE.showing()), SCHEDULE);
	}
}
