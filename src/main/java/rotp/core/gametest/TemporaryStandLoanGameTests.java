package rotp.core.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import rotp.core.JojoModConfig;
import rotp.core.ServerSavedData;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.stand.StandPowerTransitions.Status;
import rotp.core.api.stand.StandPowerTransitions.TransitionContext;
import rotp.core.core.EventHandler;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.npc.rps.RockPaperScissorsGame;
import rotp.core.impl.npc.rps.RockPaperScissorsGame.Pick;
import rotp.core.impl.npc.rps.RockPaperScissorsKidEntity;
import rotp.core.impl.stands.boyiiman.BoyIIManStandPartTakenEffect;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.standarrow.StandArrowItem;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.network.c2s.ClRPSGameInputPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.subsystems.ServerDuplicateCounter.StandHolders;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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

	/** EchoesForms.changeForm, behind the selector and ACT0's hatching: a direct setStand, then the Resolve level carried over. */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aDirectStandChangeIsAFormOfTheBorrowedStand(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			UUID token = f.begin();
			StandType form = f.type("crazy_diamond");
			int ownFormLevel = resolveLevelOf(f.power, form);
			int ownBorrowedLevel = resolveLevelOf(f.power, f.borrowed);
			f.power.setResolveLevel(2);
			for (StandType next : List.of(form, f.borrowed, form)) {
				f.power.setStand(next);
				f.power.setResolveLevel(2);
				helper.assertTrue(f.power.getPowerType() == next && token.equals(f.power.temporaryLoanId())
								&& f.original.getStandId().equals(f.counts.getHeld(f.player.getUUID())),
						"A form change must keep the loan and the reserved Stand's holder slot");
			}
			f.restore(f.player, f.power, token);
			helper.assertTrue(resolveLevelOf(f.power, form) == ownFormLevel && resolveLevelOf(f.power, f.borrowed) == ownBorrowedLevel,
					"Every form must get its own Resolve level back: " + resolveLevelOf(f.power, form) + ", "
							+ resolveLevelOf(f.power, f.borrowed));
			helper.assertTrue(f.restoreStatus(token) == Status.SOURCE_MISMATCH, "A spent loan token was accepted a second time");
		}
		helper.succeed();
	}

	/** Echoes ACT3 borrowing ACT1 and picking ACT3 again: nothing seems to change, yet the restore still owes the original's state. */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aFormEqualToTheReservedStandStillRestoresItsState(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			UUID token = f.begin();
			f.power.setStandInstance(Optional.of(f.original.copy()));
			f.power.setResolveLevel(1);
			helper.assertTrue(f.power.getStandInstance().filter(f.original::equals).isPresent() && !f.power.wasProgressionSkipped()
							&& f.power.getResolveLevel() == 1,
					"The form did not take over the reserved Stand's id");
			f.restore(f.player, f.power, token);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aFormOrALossSurvivesAReload(GameTestHelper helper) {
		for (boolean lost : List.of(false, true)) {
			try (Fixture f = new Fixture(helper)) {
				UUID token = f.begin();
				f.power.setStand(lost ? null : f.type("crazy_diamond"));
				FakePlayer reload = new FakePlayer(helper.getLevel(), f.player.getGameProfile());
				try {
					StandPower reloaded = PowerClass.STAND.attachGet(reload);
					reloaded.deserializeNBT(reload.registryAccess(), f.power.serializeNBT(f.player.registryAccess()));
					reloaded.afterNbtRead();
					f.restore(reload, reloaded, token);
				}
				finally {
					reload.discard();
				}
			}
		}
		helper.succeed();
	}

	/** Real rounds through the packet's handler: arms, legs and at last the whole borrowed Stand go to Boy II Man. */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void losingRockPaperScissorsToBoyIIManStillEndsInTheRestore(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			UUID token = f.begin();
			StandPower boyIIMan = loseAMatchToBoyIIMan(helper, f.player);
			helper.assertTrue(!f.power.hasPower() && token.equals(f.power.temporaryLoanId()),
					"The final round must take the borrowed Stand and leave the loan");
			f.restore(f.player, f.power, token);
			endEffects(boyIIMan);
			helper.assertTrue(f.power.getStandInstance().filter(f.original::equals).isPresent(),
					"Boy II Man's effects ending must not touch the Stand that came back");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void boyIIManGivingTheBorrowedStandBackLeavesTheLoanRestorable(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			UUID token = f.begin();
			StandPower boyIIMan = loseAMatchToBoyIIMan(helper, f.player);
			endEffects(boyIIMan);
			helper.assertTrue(f.power.getPowerType() == f.borrowed && token.equals(f.power.temporaryLoanId()),
					"Boy II Man's effects ending during the loan must give the borrowed Stand back");
			f.restore(f.player, f.power, token);
		}
		helper.succeed();
	}

	/** Limbs Boy II Man took before the loan, handed back during it: on the borrowed Stand or with none on hand, the original gets them. */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void boyIIMansPartsReturnedDuringALoanGoToTheReservedStand(GameTestHelper helper) {
		for (boolean borrowedLost : List.of(false, true)) {
			try (Fixture f = new Fixture(helper)) {
				FakePlayer boyIIMan = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TemporaryStandBoyIIMan"));
				try {
					StandInstance legs = new StandInstance(f.original.getStandType());
					List.of(StandPart.MAIN_BODY, StandPart.ARMS).forEach(legs::removePart);
					BoyIIManStandPartTakenEffect taken = new BoyIIManStandPartTakenEffect(legs);
					taken.withTarget(f.player);
					StandPower boyIIManPower = PowerClass.STAND.attachGet(boyIIMan);
					boyIIManPower.userStandEffects.addEffect(taken);
					UUID token = f.begin();
					if (borrowedLost) {
						f.power.setStand(null);
					}
					boyIIManPower.userStandEffects.removeEffect(taken);
					helper.assertTrue(f.power.hasPower() != borrowedLost && f.power.getStandInstance().map(StandInstance::getStandType).orElse(f.borrowed) == f.borrowed,
							"The parts of the reserved Stand must not change what is on hand during the loan");
					helper.assertTrue(StandPowerTransitions.restoreTemporaryLoan(f.power, token, f.context()).applied()
									&& f.power.getStandInstance().filter(stand -> stand.getStandType() == f.original.getStandType()
											&& stand.hasPart(StandPart.LEGS)).isPresent(),
							"The original came back without the legs Boy II Man gave back during the loan");
				}
				finally {
					boyIIMan.discard();
				}
			}
		}
		helper.succeed();
	}

	/**
	 * keepStandOnDeath off: a user with no Stand on hand. The core's Arrow says he has one; its grant, which the virus runs
	 * and which Ball Breaker's manifestation and Hermit Purple's grant match with their own setStand, still ends in the restore.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = "rotp_temporary_loan_clone")
	public static void aStandGainedAfterADeathIsAFormOfTheBorrowedStand(GameTestHelper helper) {
		ModConfigSpec.BooleanValue keep = deathConfig("keepStandOnDeath");
		boolean previousKeep = keep.get();
		try (Fixture f = new Fixture(helper)) {
			keep.set(false);
			UUID token = f.begin();
			FakePlayer clone = new FakePlayer(helper.getLevel(), f.player.getGameProfile());
			try {
				EventHandler.onPlayerClone(new PlayerEvent.Clone(clone, f.player, true));
				StandPower copied = StandPower.get(clone);
				helper.assertTrue(copied != null && !copied.hasPower() && token.equals(copied.temporaryLoanId()),
						"Death with Stand retention disabled lost the loan escrow");
				helper.assertFalse(StandArrowItem.onPiercedByArrow(clone, new ItemStack(ModItems.STAND_ARROW.get()),
								helper.getLevel(), Optional.empty()) || clone.hasEffect(ModStatusEffects.STAND_VIRUS),
						"A Stand Arrow must refuse a user whose Stand is reserved");
				StandType gained = f.type("crazy_diamond");
				helper.assertTrue(StandArrowItem.giveStand(helper.getLevel(), clone, gained) && copied.getPowerType() == gained
								&& f.original.getStandId().equals(f.counts.getHeld(clone.getUUID())),
						"The grant did not land on the borrowed Stand's place");
				f.restore(clone, copied, token);
			}
			finally {
				clone.discard();
			}
		}
		finally {
			keep.set(previousKeep);
		}
		helper.succeed();
	}

	/**
	 * keepStandOnDeath off and dropStandDisc on, the real event: the borrowed Stand's one disc is in escrow, so a death during
	 * a loan drops none and the restore leaves one. A death with no loan still drops its disc.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = "rotp_temporary_loan_clone")
	public static void aDeathDuringALoanDropsNoSecondDisc(GameTestHelper helper) {
		ModConfigSpec.BooleanValue keep = deathConfig("keepStandOnDeath");
		ModConfigSpec.BooleanValue drop = deathConfig("dropStandDisc");
		boolean previousKeep = keep.get();
		boolean previousDrop = drop.get();
		Vec3 at = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
		AABB area = new AABB(at, at).inflate(4.0D);
		try (Fixture f = new Fixture(helper)) {
			keep.set(false);
			drop.set(true);
			f.player.moveTo(at.x, at.y, at.z);
			UUID token = f.begin();
			NeoForge.EVENT_BUS.post(new LivingDeathEvent(f.player, f.player.damageSources().generic()));
			FakePlayer clone = new FakePlayer(helper.getLevel(), f.player.getGameProfile());
			FakePlayer noLoan = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TemporaryStandNoLoan"));
			try {
				EventHandler.onPlayerClone(new PlayerEvent.Clone(clone, f.player, true));
				f.restore(clone, StandPower.get(clone), token);
				int escrowed = 1;
				helper.assertTrue(discsIn(helper, area) + escrowed == 1,
						"A death during a loan left " + (discsIn(helper, area) + escrowed) + " discs of a Stand with one");

				noLoan.moveTo(at.x, at.y, at.z);
				helper.assertTrue(StandPowerTransitions.insert(PowerClass.STAND.attachGet(noLoan), new StandInstance(f.borrowed)).applied(),
						"Could not give the Stand with no loan");
				NeoForge.EVENT_BUS.post(new LivingDeathEvent(noLoan, noLoan.damageSources().generic()));
				helper.assertTrue(discsIn(helper, area) == 1, "A death with no loan must still drop the Stand's disc");
			}
			finally {
				f.counts.setHeld(noLoan.getUUID(), null);
				noLoan.discard();
				clone.discard();
			}
		}
		finally {
			keep.set(previousKeep);
			drop.set(previousDrop);
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
		}
		helper.succeed();
	}

	private static int discsIn(GameTestHelper helper, AABB area) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().getItem() instanceof StandDiscItem).stream()
				.mapToInt(item -> item.getItem().getCount()).sum();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void standCommandsEndTheLoanInsteadOfLeavingItBehind(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			UUID token = f.begin();
			helper.assertTrue(f.run("stand remove @s"), "/stand remove failed during a loan");
			helper.assertTrue(!f.power.hasPower() && !StandPowerTransitions.isTemporaryLoanActive(f.power, token)
							&& f.counts.getHeld(f.player.getUUID()) == null && f.restoreStatus(token) == Status.SOURCE_MISMATCH,
					"/stand remove must leave no Stand, no loan and no holder slot");
		}
		try (Fixture f = new Fixture(helper)) {
			UUID token = f.begin();
			StandType given = f.type("crazy_diamond");
			helper.assertTrue(f.run("stand give @s " + given.getId() + " true"), "/stand give with replace failed during a loan");
			helper.assertTrue(f.power.getPowerType() == given && !StandPowerTransitions.isTemporaryLoanActive(f.power, token)
							&& given.getId().equals(f.counts.getHeld(f.player.getUUID())) && f.restoreStatus(token) == Status.SOURCE_MISMATCH,
					"/stand give with replace must end the loan and leave the given Stand");
			helper.assertTrue(f.originalProgress.equals(((StandTypePersistentData) f.power.getPowerTypeData(f.original.getStandType()))
							.serializeNBT(f.player.registryAccess())),
					"A replace keeps the replaced Stand's progression, also when that Stand was reserved");
		}
		try (Fixture f = new Fixture(helper)) {
			UUID token = f.begin();
			helper.assertTrue(f.run("stand random @s true"), "/stand random with replace failed during a loan");
			helper.assertTrue(f.power.hasPower() && !StandPowerTransitions.isTemporaryLoanActive(f.power, token)
							&& f.power.getPowerType().getId().equals(f.counts.getHeld(f.player.getUUID())),
					"/stand random with replace must end the loan and leave the rolled Stand");
		}
		// without replace a reservation counts as a Stand, even with the borrowed one lost
		try (Fixture f = new Fixture(helper)) {
			UUID token = f.begin();
			f.power.setStand(null);
			helper.assertFalse(f.run("stand give @s " + f.type("crazy_diamond").getId()), "/stand give replaced a reserved Stand unasked");
			f.run("stand random @s");
			helper.assertTrue(!f.power.hasPower() && token.equals(f.power.temporaryLoanId()),
					"A command without replace must leave the loan alone");
			f.restore(f.player, f.power, token);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void abortEndsTheLoanInAnyStateAndNoVetoStopsIt(GameTestHelper helper) {
		for (int state = 0; state < 3; state++) {
			try (Fixture f = new Fixture(helper)) {
				UUID token = f.begin();
				if (state == 1) {
					f.power.setStand(f.type("crazy_diamond"));
				}
				else if (state == 2) {
					f.power.setStand(null);
				}
				VETO.set(state == 0 ? Veto.REMOVE : Veto.GIVE);
				helper.assertTrue(StandPowerTransitions.abortTemporaryLoan(f.power).applied(), "The abort was refused in state " + state);
				VETO.remove();
				f.assertOriginalBack(f.player, f.power);
				helper.assertTrue(!StandPowerTransitions.isTemporaryLoanActive(f.power, token) && f.restoreStatus(token) == Status.SOURCE_MISMATCH
								&& StandPowerTransitions.abortTemporaryLoan(f.power).status() == Status.SOURCE_MISMATCH,
						"An aborted loan must be over for its token and for a second abort");
				f.assertOriginalBack(f.player, f.power);
			}
		}
		// the reserved Stand's add-on is gone: a restore keeps waiting for it, only an abort can end the loan
		try (Fixture f = new Fixture(helper)) {
			UUID token = f.begin();
			CompoundTag saved = f.power.serializeNBT(f.player.registryAccess());
			saved.getCompound("TemporaryStandLoan").getCompound("OriginalStand").putString("stand_type", "rotp_gametest:removed_stand");
			FakePlayer reload = new FakePlayer(helper.getLevel(), f.player.getGameProfile());
			try {
				StandPower reloaded = PowerClass.STAND.attachGet(reload);
				reloaded.deserializeNBT(reload.registryAccess(), saved);
				helper.assertTrue(StandPowerTransitions.restoreTemporaryLoan(reloaded, token, new TransitionContext(SOURCE, reload))
								.status() == Status.INVALID_REPLACEMENT && StandPowerTransitions.isTemporaryLoanActive(reloaded, token),
						"A restore must keep waiting for a reserved Stand whose type is gone");
				helper.assertTrue(StandPowerTransitions.abortTemporaryLoan(reloaded).applied() && !reloaded.hasPower()
								&& !StandPowerTransitions.isTemporaryLoanActive(reloaded, token)
								&& reloaded.getStandInstance().filter(stand -> stand.getStandId().toString().equals("rotp_gametest:removed_stand")).isPresent(),
						"An abort must end a loan whose reserved Stand's type is gone and keep that Stand as a load keeps it");
			}
			finally {
				reload.discard();
			}
		}
		helper.succeed();
	}

	private static StandPower loseAMatchToBoyIIMan(GameTestHelper helper, FakePlayer player) {
		ServerSavedData data = ServerSavedData.get(helper.getLevel().getServer());
		RockPaperScissorsKidEntity kid = helper.spawn(ModEntityTypes.RPS_KID.get(), new BlockPos(2, 2, 3));
		kid.mobInteract(player, InteractionHand.MAIN_HAND);
		try {
			for (int round = 1; round <= RockPaperScissorsGame.WINS_NEEDED; round++) {
				RockPaperScissorsGame game = data.rpsPvpGames.get(player.getUUID());
				helper.assertTrue(game != null && game.opponentIsNpc(), "The match with the kid ended before round " + round);
				// the kid plays its thoughts pick, so scissors loses every round
				game.setOpponentThoughts(Pick.ROCK);
				ClRPSGameInputPacket.handleOnServer(player, ClRPSGameInputPacket.pick(Pick.SCISSORS));
			}
			helper.assertFalse(data.rpsPvpGames.has(player.getUUID()), "The lost match was kept");
		}
		finally {
			data.rpsPvpGames.remove(player.getUUID());
		}
		return StandPower.get(kid);
	}

	private static void endEffects(StandPower power) {
		for (StandEffectInstance effect : List.copyOf(power.userStandEffects.getEffects())) {
			power.userStandEffects.removeEffect(effect);
		}
	}

	private static int resolveLevelOf(StandPower power, StandType type) {
		return ((StandTypePersistentData) power.getPowerTypeData(type)).getResolveLevel();
	}

	private static ModConfigSpec.BooleanValue deathConfig(String key) {
		return JojoModConfig.COMMON_SPEC.getValues().get(List.of("Keep Powers After Death", key));
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

		StandType type(String id) {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
			helper.assertTrue(type != null, "Missing registered Stand " + id);
			return type;
		}

		Status restoreStatus(UUID token) {
			return StandPowerTransitions.restoreTemporaryLoan(power, token, context()).status();
		}

		void restore(FakePlayer owner, StandPower currentPower, UUID token) {
			helper.assertTrue(StandPowerTransitions.restoreTemporaryLoan(currentPower, token,
							new TransitionContext(SOURCE, owner)).applied(),
					"The restore that returns the disc was refused");
			assertOriginalBack(owner, currentPower);
		}

		void assertOriginalBack(FakePlayer owner, StandPower currentPower) {
			helper.assertTrue(currentPower.getStandInstance().filter(original::equals).isPresent()
							&& currentPower.wasProgressionSkipped() && currentPower.getStamina() == 0.0F
							&& originalProgress.equals(currentPower.getCurTypeData().serializeNBT(owner.registryAccess()))
							&& original.getStandId().equals(counts.getHeld(owner.getUUID()))
							&& currentPower.temporaryLoanId() == null,
					"Restore did not preserve the exact Stand, per-type progression and ownership");
		}

		boolean run(String command) {
			CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
					.withEntity(player).withLevel(helper.getLevel()).withPermission(4).withSuppressedOutput();
			try {
				helper.getLevel().getServer().getCommands().getDispatcher().execute(JojoMod.MOD_ID + " " + command, source);
				return true;
			}
			catch (CommandSyntaxException refused) {
				return false;
			}
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
