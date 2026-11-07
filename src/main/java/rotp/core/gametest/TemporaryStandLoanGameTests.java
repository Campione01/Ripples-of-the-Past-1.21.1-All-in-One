package rotp.core.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.stand.StandPowerTransitions.Status;
import rotp.core.api.stand.StandPowerTransitions.TransitionContext;
import rotp.core.core.EventHandler;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.subsystems.ServerDuplicateCounter.StandHolders;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TemporaryStandLoanGameTests {
	private static final ResourceLocation SOURCE = JojoMod.resLoc("gametest_temporary_stand_loan");
	private static final ThreadLocal<Veto> VETO = new ThreadLocal<>();

	static {
		StandPowerTransitions.registerVeto(SOURCE, query ->
				query.context().source().equals(SOURCE) && VETO.get() == Veto.REMOVE);
		StandPowerTransitions.registerMutationVeto(SOURCE, query ->
				query.context().source().equals(SOURCE) && VETO.get() == Veto.GIVE);
	}

	private TemporaryStandLoanGameTests() {}

	private enum Veto {
		REMOVE, GIVE
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void temporaryStandLoanKeepsBothVetoesAndCountsAtomic(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			CompoundTag untouched = f.power.serializeNBT(f.player.registryAccess());
			for (Veto veto : Veto.values()) {
				VETO.set(veto);
				helper.assertTrue(StandPowerTransitions.beginTemporaryLoan(f.power, new StandInstance(f.borrowed), f.context())
								.status() == Status.VETOED
								&& untouched.equals(f.power.serializeNBT(f.player.registryAccess())),
						"Borrowing did not leave the original unchanged under " + veto + " veto");
			}
			VETO.remove();
			int preLoanDiscs = f.counts.getOnDisc(f.borrowed.getId());
			int priorBorrowedLevel = ((StandTypePersistentData) f.power.getPowerTypeData(f.borrowed)).getResolveLevel();
			for (int cycle = 0; cycle < 2; cycle++) {
				if (cycle == 1) {
					f.counts.putOnDisc(f.borrowed.getId());
					f.ownsTestDiscCount = true;
				}
				int expectedDiscs = preLoanDiscs + cycle;
				UUID token = f.begin();
				helper.assertTrue(f.counts.getHeld(f.player.getUUID()).equals(f.original.getStandId())
								&& f.counts.getOnDisc(f.borrowed.getId()) == expectedDiscs,
						"The original slot and escrowed disc count must not change during the loan");
				f.power.setResolveLevel(2);
				f.power.setStamina(42.0F);
				CompoundTag pending = f.power.serializeNBT(f.player.registryAccess());
				helper.assertTrue(StandPowerTransitions.extract(f.power, f.borrowed.getId()).status() == Status.VETOED
								&& StandPowerTransitions.replace(f.power, f.borrowed.getId(), f.original, f.context()).status() == Status.VETOED
								&& StandPowerTransitions.fullReset(f.power, f.context()).status() == Status.VETOED,
						"Ordinary ownership operations must not export or destroy a borrowed Stand");
				helper.assertTrue(StandPowerTransitions.restoreTemporaryLoan(f.power, UUID.randomUUID(), f.context())
						.status() == Status.SOURCE_MISMATCH, "An unrelated token restored the loan");
				for (Veto veto : Veto.values()) {
					VETO.set(veto);
					helper.assertTrue(StandPowerTransitions.restoreTemporaryLoan(f.power, token, f.context()).status() == Status.VETOED
									&& pending.equals(f.power.serializeNBT(f.player.registryAccess()))
									&& f.counts.getOnDisc(f.borrowed.getId()) == expectedDiscs,
							"Restore changed pending state before clearing the " + veto + " veto");
				}
				VETO.remove();
				f.restore(f.player, f.power, token);
				helper.assertTrue(((StandTypePersistentData) f.power.getPowerTypeData(f.borrowed)).getResolveLevel() == priorBorrowedLevel
								&& f.counts.getOnDisc(f.borrowed.getId()) == expectedDiscs,
						"Restore changed the borrowed Stand's prior Resolve level or disc count");
				helper.assertTrue(StandPowerTransitions.restoreTemporaryLoan(f.power, token, f.context())
								.status() == Status.SOURCE_MISMATCH,
						"A spent loan token was accepted a second time");
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "rotp_temporary_loan_clone")
	public static void temporaryStandLoanSurvivesNbtAndADeathThatDropsTheBorrowedStand(GameTestHelper helper) {
		ModConfigSpec.BooleanValue keep = JojoModConfig.COMMON_SPEC.getValues()
				.get(List.of("Keep Powers After Death", "keepStandOnDeath"));
		boolean previousKeep = keep.get();
		try (Fixture f = new Fixture(helper)) {
			keep.set(false);
			UUID token = f.begin();
			FakePlayer reload = new FakePlayer(helper.getLevel(), f.player.getGameProfile());
			FakePlayer clone = new FakePlayer(helper.getLevel(), f.player.getGameProfile());
			try {
				StandPower reloaded = PowerClass.STAND.attachGet(reload);
				reloaded.deserializeNBT(reload.registryAccess(), f.power.serializeNBT(f.player.registryAccess()));
				reloaded.afterNbtRead();
				StandUtil.trackTakenStand(reload, reloaded.getPowerType());
				helper.assertTrue(token.equals(reloaded.temporaryLoanId()) && reloaded.getPowerType() == f.borrowed
								&& f.original.getStandId().equals(f.counts.getHeld(reload.getUUID())),
						"Loading a borrowed Stand forgot the original reservation");
				EventHandler.onPlayerClone(new PlayerEvent.Clone(clone, reload, true));
				StandPower copied = StandPower.get(clone);
				helper.assertTrue(copied != null && !copied.hasPower() && token.equals(copied.temporaryLoanId()),
						"Death with Stand retention disabled lost the loan escrow");
				f.restore(clone, copied, token);
			}
			finally {
				clone.discard();
				reload.discard();
			}
		}
		finally {
			keep.set(previousKeep);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void conflictedTemporaryLoanDoesNotOverwriteAnotherStand(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			UUID token = f.begin();
			StandType unrelated = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
			f.power.setStand(unrelated);
			helper.assertTrue(StandPowerTransitions.restoreTemporaryLoan(f.power, token, f.context()).status() == Status.SOURCE_MISMATCH
							&& f.power.getPowerType() == unrelated && token.equals(f.power.temporaryLoanId()),
					"A conflicting direct replacement was overwritten or destroyed the recovery token");
		}
		helper.succeed();
	}

	private static final class Fixture implements AutoCloseable {
		final GameTestHelper helper;
		final FakePlayer player;
		final StandPower power;
		final StandHolders counts;
		final StandType borrowed;
		final StandInstance original;
		final CompoundTag originalProgress;
		boolean ownsTestDiscCount;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TemporaryStandLoan"));
			power = PowerClass.STAND.attachGet(player);
			counts = StandHolders.get(helper.getLevel().getServer());
			StandType initial = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			borrowed = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
			original = new StandInstance(initial);
			original.removePart(StandPart.LEGS);
			original.setCustomSkin(Optional.of(JojoMod.resLoc("gametest_original_skin")));
			helper.assertTrue(StandPowerTransitions.insert(power, original).applied(), "Could not give the original Stand");
			power.skipProgression();
			power.setResolveLevel(3);
			power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP, 23.0F,
					TimeStopLearning.getMaxTrainingPoints(power), power);
			power.setStamina(50.0F);
			originalProgress = power.getCurTypeData().serializeNBT(player.registryAccess());
		}

		TransitionContext context() {
			return new TransitionContext(SOURCE, player);
		}

		UUID begin() {
			var start = StandPowerTransitions.beginTemporaryLoan(power, new StandInstance(borrowed), context());
			helper.assertTrue(start.applied() && start.token() != null && start.originalProgressionSkipped()
							&& start.originalResolveLevel() == 3 && power.getPowerType() == borrowed
							&& power.getStamina() == 0.0F,
					"Expected a real borrowed Stand and the unchanged original progression snapshot");
			return start.token();
		}

		void restore(FakePlayer owner, StandPower currentPower, UUID token) {
			helper.assertTrue(StandPowerTransitions.restoreTemporaryLoan(currentPower, token,
							new TransitionContext(SOURCE, owner)).applied()
							&& currentPower.getStandInstance().filter(original::equals).isPresent()
							&& currentPower.wasProgressionSkipped() && currentPower.getStamina() == 0.0F
							&& originalProgress.equals(currentPower.getCurTypeData().serializeNBT(owner.registryAccess()))
							&& original.getStandId().equals(counts.getHeld(owner.getUUID())),
					"Restore did not preserve the exact Stand, per-type progression and ownership");
		}

		@Override
		public void close() {
			VETO.remove();
			if (ownsTestDiscCount) {
				counts.takeFromDisc(borrowed.getId());
			}
			counts.setHeld(player.getUUID(), null);
			player.discard();
		}
	}
}
