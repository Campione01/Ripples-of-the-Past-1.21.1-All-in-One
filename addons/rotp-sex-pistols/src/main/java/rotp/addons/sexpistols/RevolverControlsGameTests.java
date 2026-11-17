package rotp.addons.sexpistols;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.content.revolver.item.RevolverButtonClaim;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.revolver.item.RevolverStats;
import rotp.addons.sexpistols.content.revolver.state.RevolverMemberAttachments;
import rotp.addons.sexpistols.content.revolver.state.RevolverState;
import rotp.addons.sexpistols.content.revolver.state.RevolverState.Chamber;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.network.RevolverChamberControlPayload;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsItems;
import rotp.core.api.client.render.HeldItemButtonClaims;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The server's half of Next Album's revolver controls (RevolverItem.java and RevolverClientEvents.java of 0.1.11): the
 * attack key fires, sneak and the attack key strike and swing only on a hit, the use key only aims, and the reload key
 * opens and closes the cylinder. Each press goes through the payload the client sends. The client's choice of payload is
 * read, not tested.
 */
@GameTestHolder(SexPistolsIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RevolverControlsGameTests {
    private static final int TIMEOUT = 60 + GameTestChunkTickets.WAIT_TICKS;
    private static final short STAND_RELOAD_KEY = 75;

    private RevolverControlsGameTests() {}

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void rightClickOnlyAims(GameTestHelper helper) {
        run(helper, "aims", (owner, gun) -> {
            useKey(owner, gun);
            helper.assertTrue(owner.isUsingItem() && RevolverItem.state(gun).aiming(),
                    "The use key did not aim.");
            owner.releaseUsingItem();
            RevolverState state = RevolverItem.state(gun);
            helper.assertTrue(bullets(owner).isEmpty() && state.chamber(state.selectedChamber()) == Chamber.LIVE
                            && !state.aiming() && RevolverItem.shotCooldown(gun) == 0,
                    "Letting go of the use key must only end the aim: bullets=" + bullets(owner).size()
                            + ", state=" + state + ", cooldown=" + RevolverItem.shotCooldown(gun) + ".");
        });
    }

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void sneakingRightClickStillAims(GameTestHelper helper) {
        run(helper, "sneak-aims", (owner, gun) -> {
            owner.setShiftKeyDown(true);
            useKey(owner, gun);
            owner.setShiftKeyDown(false);
            RevolverState state = RevolverItem.state(gun);
            helper.assertTrue(!state.reloadMode() && state.aiming() && owner.isUsingItem(),
                    "Sneak and the use key with the cylinder closed aim in the donor; state=" + state + ".");
        });
    }

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void leftClickFiresOneShotPerCooldownForAnyUser(GameTestHelper helper) {
        run(helper, "fires", (owner, gun) -> {
            StandPower power = StandPower.get(owner);
            helper.assertTrue(power != null, "setup: no Stand power");
            press(owner, RevolverChamberControlPayload.FIRE);
            helper.assertTrue(bullets(owner).size() == 1, "A user with no Stand must fire on the attack key; bullets="
                    + bullets(owner).size() + ".");
            helper.assertTrue(RevolverItem.state(gun).spentCount() == 1 && RevolverItem.state(gun).selectedChamber() == 1
                            && RevolverItem.shotCooldown(gun) == RevolverStats.MISTA.shotCooldownTicks()
                            && !RevolverItem.state(gun).aiming(),
                    "A hip shot spends a chamber, moves on and starts the cooldown; state="
                            + RevolverItem.state(gun) + ", cooldown=" + RevolverItem.shotCooldown(gun) + ".");
            press(owner, RevolverChamberControlPayload.FIRE);
            helper.assertTrue(bullets(owner).size() == 1 && RevolverItem.state(gun).spentCount() == 1,
                    "A second press inside the cooldown must do nothing; bullets=" + bullets(owner).size() + ".");

            RevolverBulletEntity.discardOwnedBy(owner.serverLevel(), owner.getUUID());
            power.setStand(ModStands.STAR_PLATINUM.get());
            ItemStack next = loadedGun(helper, owner, SexPistolsItems.REVOLVER.get().getDefaultInstance());
            press(owner, RevolverChamberControlPayload.FIRE);
            helper.assertTrue(bullets(owner).size() == 1 && RevolverItem.state(next).spentCount() == 1,
                    "Another Stand's user must fire on the attack key too; bullets=" + bullets(owner).size() + ".");
        });
    }

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void aimedShotKeepsTheAimAndWaitsForTheCooldown(GameTestHelper helper) {
        run(helper, "aimed", (owner, gun) -> {
            useKey(owner, gun);
            press(owner, RevolverChamberControlPayload.FIRE);
            helper.assertTrue(bullets(owner).size() == 1 && RevolverItem.state(gun).aiming() && owner.isUsingItem(),
                    "A shot with the use key held fires and stays aimed; bullets=" + bullets(owner).size()
                            + ", state=" + RevolverItem.state(gun) + ", using=" + owner.isUsingItem() + ".");
            press(owner, RevolverChamberControlPayload.FIRE);
            helper.assertTrue(bullets(owner).size() == 1, "The next aimed shot must wait for the cooldown.");
        });
    }

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void meleeHitsSwingsAndKnocksBack(GameTestHelper helper) {
        run(helper, "melee", (owner, gun) -> {
            Cow cow = cow(helper, owner, 2.0D);
            float health = cow.getHealth();
            SexPistolsGameTests.forgetSentPackets(owner);
            press(owner, RevolverChamberControlPayload.MELEE);
            RevolverStats stats = RevolverStats.MISTA;
            Vec3 movement = cow.getDeltaMovement();
            RevolverState state = RevolverItem.state(gun);
            helper.assertTrue(Math.abs(health - cow.getHealth() - stats.meleeDamage()) < 0.001F,
                    "The blow does the gun's melee damage; took " + (health - cow.getHealth()) + ", the gun's "
                            + stats.meleeDamage() + ".");
            helper.assertTrue(movement.z >= stats.meleeKnockback() && movement.y >= 0.12D - 0.001D,
                    "The blow knocks the target along the look; movement=" + movement + ".");
            helper.assertTrue(owner.swinging && owner.swingingArm == InteractionHand.MAIN_HAND,
                    "A blow that lands swings the main arm; swinging=" + owner.swinging + ".");
            helper.assertTrue(bullets(owner).isEmpty() && state.spentCount() == 0
                            && state.chamber(state.selectedChamber()) == Chamber.LIVE,
                    "The blow fires nothing; bullets=" + bullets(owner).size() + ", state=" + state + ".");
            helper.assertTrue(heardTheBlow(owner), "The blow's sound was not sent: weak attack at 0.7 / 1.15.");
            cow.discard();
        });
    }

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void meleeSwingsOnlyOnAHit(GameTestHelper helper) {
        run(helper, "melee-miss", (owner, gun) -> {
            press(owner, RevolverChamberControlPayload.MELEE);
            helper.assertTrue(!owner.swinging, "A blow at nothing must not swing the arm.");
            Cow far = cow(helper, owner, RevolverStats.MISTA.meleeRange() + 3.0D);
            float health = far.getHealth();
            press(owner, RevolverChamberControlPayload.MELEE);
            helper.assertTrue(!owner.swinging && far.getHealth() == health,
                    "A target out of reach is neither hit nor swung at; swinging=" + owner.swinging + ".");
            far.discard();
        });
    }

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void noShotOrMeleeWithTheCylinderOpen(GameTestHelper helper) {
        run(helper, "open", (owner, gun) -> {
            Cow cow = cow(helper, owner, 2.0D);
            float health = cow.getHealth();
            press(owner, RevolverChamberControlPayload.TOGGLE_CYLINDER);
            helper.assertTrue(RevolverItem.state(gun).reloadMode(), "The reload key did not open the cylinder.");
            helper.assertTrue(RevolverItem.state(gun).chamber(RevolverItem.state(gun).selectedChamber()) == Chamber.LIVE,
                    "setup: the live chamber is not up; state=" + RevolverItem.state(gun) + ".");
            press(owner, RevolverChamberControlPayload.FIRE);
            press(owner, RevolverChamberControlPayload.MELEE);
            helper.assertTrue(bullets(owner).isEmpty() && !owner.swinging && cow.getHealth() == health
                            && RevolverItem.state(gun).reloadMode() && RevolverItem.state(gun).liveCount() == 1
                            && RevolverItem.shotCooldown(gun) == 0,
                    "With the cylinder open the attack key does nothing, as in the donor; bullets="
                            + bullets(owner).size() + ", swinging=" + owner.swinging + ", state="
                            + RevolverItem.state(gun) + ".");
            cow.discard();
        });
    }

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void reloadKeyOpensAndCloses(GameTestHelper helper) {
        run(helper, "toggle", (owner, gun) -> {
            useKey(owner, gun);
            press(owner, RevolverChamberControlPayload.TOGGLE_CYLINDER);
            RevolverState open = RevolverItem.state(gun);
            helper.assertTrue(open.reloadMode() && !open.aiming() && !owner.isUsingItem(),
                    "Opening the cylinder ends the aim; state=" + open + ", using=" + owner.isUsingItem() + ".");
            useKey(owner, gun);
            helper.assertTrue(!owner.isUsingItem() && !RevolverItem.state(gun).aiming(),
                    "With the cylinder open the use key loads and does not aim.");
            press(owner, RevolverChamberControlPayload.TOGGLE_CYLINDER);
            helper.assertTrue(!RevolverItem.state(gun).reloadMode(), "The reload key did not close the cylinder.");
        });
    }

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void meleeSkipsTheOwnersOwnMembers(GameTestHelper helper) {
        run(helper, "members", (owner, gun) -> {
            StandPower power = SexPistolsGameTests.prepareSexPistols(helper, owner);
            SexPistolsCoordinator coordinator = SexPistolsAttachments.coordinator(owner);
            float memberHealth = memberHealth(owner, coordinator);
            Cow cow = cow(helper, owner, 2.0D);
            float health = cow.getHealth();
            press(owner, RevolverChamberControlPayload.MELEE);
            float memberHealthAfter = memberHealth(owner, coordinator);
            helper.assertTrue(memberHealth > 0.0F && cow.getHealth() < health && memberHealthAfter == memberHealth,
                    "The blow must pass the user's own members and land on the target; target took "
                            + (health - cow.getHealth()) + ", members took " + (memberHealth - memberHealthAfter)
                            + ", power=" + power.isSummoned() + ".");
            cow.discard();
            coordinator.cleanup(owner.serverLevel(), owner);
        });
    }

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void aGunInTheOffHandTakesNoPress(GameTestHelper helper) {
        run(helper, "off-hand", (owner, gun) -> {
            owner.setItemSlot(EquipmentSlot.OFFHAND, gun);
            owner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            press(owner, RevolverChamberControlPayload.FIRE);
            press(owner, RevolverChamberControlPayload.TOGGLE_CYLINDER);
            helper.assertTrue(bullets(owner).isEmpty() && !RevolverItem.state(gun).reloadMode(),
                    "A gun in the off hand must take no attack or reload press.");
        });
    }

    // Donor 025 handleServerToggleReloadMode leaves the selection, and SexPistolsStandReloadAction.reloadSelected asks
    // for a bullet in the selected chamber.
    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void standReloadTakesTheChamberTheReloadKeyLeftUp(GameTestHelper helper) {
        run(helper, "stand-reload-open", (owner, gun) -> {
            StandPower power = SexPistolsGameTests.prepareSexPistols(helper, owner);
            SexPistolsCoordinator coordinator = SexPistolsAttachments.coordinator(owner);
            owner.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400));
            press(owner, RevolverChamberControlPayload.TOGGLE_CYLINDER);
            RevolverState open = RevolverItem.state(gun);
            helper.assertTrue(open.reloadMode() && open.selectedChamber() == 0 && open.chamber(0) == Chamber.LIVE,
                    "The reload key must leave the selection on the live chamber; state=" + open + ".");
            standReload(helper, owner, power, "on the live chamber");
            helper.assertTrue(coordinator.loadedMemberMask() == 1 && RevolverMemberAttachments.chamberMemberMask(gun, 0) == 1,
                    "Stand Reload after the reload key did not load a member on the round; loaded="
                            + coordinator.loadedMemberMask() + ".");
            coordinator.cleanup(owner.serverLevel(), owner);
        });
    }

    // What a player has to do: a round in the chamber first, then that chamber up. SexPistolsStandReloadAction refused
    // an empty one too, with only a click; the text is the port's.
    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void standReloadOnAnEmptyChamberSaysSoAndWorksOnceARoundIsUp(GameTestHelper helper) {
        run(helper, "stand-reload-empty", (owner, loaded) -> {
            StandPower power = SexPistolsGameTests.prepareSexPistols(helper, owner);
            SexPistolsCoordinator coordinator = SexPistolsAttachments.coordinator(owner);
            owner.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400));
            ItemStack gun = new ItemStack(SexPistolsItems.REVOLVER.get());
            owner.setItemSlot(EquipmentSlot.MAINHAND, gun);
            owner.getInventory().add(new ItemStack(SexPistolsItems.REVOLVER_AMMO.get(), 2));
            press(owner, RevolverChamberControlPayload.TOGGLE_CYLINDER);
            SexPistolsGameTests.forgetSentPackets(owner);
            standReload(helper, owner, power, "on an empty chamber");
            helper.assertTrue(coordinator.loadedMemberMask() == 0 && RevolverItem.state(gun).liveCount() == 0,
                    "Stand Reload on an empty chamber must take neither a member nor a round; loaded="
                            + coordinator.loadedMemberMask() + ", live=" + RevolverItem.state(gun).liveCount() + ".");
            List<String> said = actionBarKeys(owner);
            helper.assertTrue(said.contains("jojo.message.action_condition.sex_pistols_reload_no_round"),
                    "The refusal must say the selected chamber holds no round; said=" + said + ".");
            useKey(owner, gun);
            helper.assertTrue(RevolverItem.state(gun).chamber(0) == Chamber.LIVE
                            && RevolverItem.state(gun).selectedChamber() == 1,
                    "setup: the use key did not load chamber 0 and move on; state=" + RevolverItem.state(gun) + ".");
            standReload(helper, owner, power, "on the next chamber, still empty");
            helper.assertTrue(coordinator.loadedMemberMask() == 0,
                    "Stand Reload took a member with an empty chamber up.");
            press(owner, RevolverChamberControlPayload.SWITCH_BACKWARD);
            standReload(helper, owner, power, "with the round brought back up");
            helper.assertTrue(coordinator.loadedMemberMask() == 1 && RevolverMemberAttachments.chamberMemberMask(gun, 0) == 1,
                    "Stand Reload with the round up did not load a member on it; loaded="
                            + coordinator.loadedMemberMask() + ".");
            coordinator.cleanup(owner.serverLevel(), owner);
        });
    }

    // The in-game pass's state (EV-PASS2 REVOLVER#5): six members out, a new gun, the rounds in the off hand. Each X goes
    // the way the server takes an ability key (ClAbilityInputPacket.handlePress). SexPistolsStandReloadAction.reloadSelected
    // refused an empty selected chamber; handleServerFillChamber moved the selection past the round it loaded.
    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void standReloadKeyFromThePassStateLoadsOnceARoundIsUp(GameTestHelper helper) {
        run(helper, "stand-reload-key", (owner, loaded) -> {
            StandPower power = SexPistolsGameTests.prepareSexPistols(helper, owner);
            SexPistolsCoordinator coordinator = SexPistolsAttachments.coordinator(owner);
            // the test server's players are creative, whose loads take no round
            owner.setGameMode(GameType.SURVIVAL);
            owner.getInventory().clearContent();
            ItemStack gun = new ItemStack(SexPistolsItems.REVOLVER.get());
            owner.setItemSlot(EquipmentSlot.MAINHAND, gun);
            owner.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(SexPistolsItems.REVOLVER_AMMO.get(), 22));
            press(owner, RevolverChamberControlPayload.TOGGLE_CYLINDER);
            SexPistolsGameTests.forgetSentPackets(owner);
            standReloadKey(helper, owner, power, "on the new gun's empty chamber");
            helper.assertTrue(coordinator.loadedMemberMask() == 0 && RevolverItem.state(gun).liveCount() == 0
                            && owner.getOffhandItem().getCount() == 22,
                    "X on an empty chamber must take neither a member nor a round; loaded="
                            + coordinator.loadedMemberMask() + ", state=" + RevolverItem.state(gun)
                            + ", off hand=" + owner.getOffhandItem().getCount() + ".");
            List<String> said = actionBarKeys(owner);
            helper.assertTrue(said.contains("jojo.message.action_condition.sex_pistols_reload_no_round"),
                    "The refusal must say the selected chamber holds no round; said=" + said + ".");
            useKey(owner, gun);
            helper.assertTrue(RevolverItem.state(gun).chamber(0) == Chamber.LIVE
                            && RevolverItem.state(gun).selectedChamber() == 1
                            && owner.getOffhandItem().getCount() == 21,
                    "The use key must load chamber 0 from the off hand and move on; state=" + RevolverItem.state(gun)
                            + ", off hand=" + owner.getOffhandItem().getCount() + ".");
            // the load started the gun's reload cooldown (1.16 applyReloadCooldown), which Stand Reload waits out (canReload)
            for (int tick = 0; tick < 40 && RevolverItem.reloadCooldown(gun) > 0; tick++) {
                owner.getInventory().tick();
            }
            helper.assertTrue(RevolverItem.reloadCooldown(gun) == 0,
                    "setup: the reload cooldown did not run out; left=" + RevolverItem.reloadCooldown(gun) + ".");
            press(owner, RevolverChamberControlPayload.SWITCH_BACKWARD);
            int firstNumber = SexPistolsIds.SEX_PISTOLS_NUMBERS[0];
            standReloadKey(helper, owner, power, "with the round brought back up");
            helper.assertTrue(coordinator.loadedMemberMask() == 1 && RevolverMemberAttachments.chamberMemberMask(gun, 0) == 1
                            && coordinator.member(owner.serverLevel(), firstNumber) == null
                            && RevolverItem.state(gun).liveCount() == 1,
                    "X with the round up must hang member No." + firstNumber + " on it; loaded="
                            + coordinator.loadedMemberMask() + ", on chamber 0="
                            + RevolverMemberAttachments.chamberMemberMask(gun, 0) + ".");
            coordinator.cleanup(owner.serverLevel(), owner);
        });
    }

    // OQ-C22: the HUD shows the gun on the buttons it takes. 1.16 (S02): the left button is the gun's; the right one too in
    // the main hand with no Stand out, open (loads) or closed (aims), since a Stand that is out keeps it.
    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void theGunClaimsTheLeftButtonAndTheRightWithNoStandOut(GameTestHelper helper) {
        run(helper, "button-claims", (owner, gun) -> {
            HeldItemButtonClaims.Button left = HeldItemButtonClaims.Button.LEFT;
            HeldItemButtonClaims.Button right = HeldItemButtonClaims.Button.RIGHT;
            helper.assertTrue(!RevolverItem.state(gun).reloadMode() && RevolverButtonClaim.claims(owner, gun, left)
                            && RevolverButtonClaim.claims(owner, gun, right),
                    "A closed gun with no Stand out aims on the right button, so it must claim both.");
            press(owner, RevolverChamberControlPayload.TOGGLE_CYLINDER);
            helper.assertTrue(RevolverItem.state(gun).reloadMode() && RevolverButtonClaim.claims(owner, gun, right),
                    "An open gun with no Stand out must claim the right button.");
            owner.setItemSlot(EquipmentSlot.OFFHAND, gun);
            owner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            helper.assertTrue(RevolverButtonClaim.claims(owner, gun, left) && !RevolverButtonClaim.claims(owner, gun, right),
                    "An open gun in the off hand loads nothing, so it must not claim the right button.");
            owner.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            owner.setItemSlot(EquipmentSlot.MAINHAND, gun);
            SexPistolsGameTests.prepareSexPistols(helper, owner);
            helper.assertTrue(RevolverButtonClaim.claims(owner, gun, left) && !RevolverButtonClaim.claims(owner, gun, right),
                    "With the Stand out the right button is the Stand's, as in 1.16; the left one stays the gun's.");
            press(owner, RevolverChamberControlPayload.TOGGLE_CYLINDER);
            helper.assertTrue(!RevolverItem.state(gun).reloadMode() && RevolverButtonClaim.claims(owner, gun, left)
                            && !RevolverButtonClaim.claims(owner, gun, right),
                    "A closed gun with the Stand out leaves the right button to the Stand.");
            ItemStack heavy = new ItemStack(SexPistolsItems.HEAVY_REVOLVER.get());
            helper.assertTrue(RevolverButtonClaim.claims(owner, heavy, left), "The heavy revolver must claim the left button.");
            ItemStack other = new ItemStack(net.minecraft.world.item.Items.STICK);
            helper.assertTrue(!RevolverButtonClaim.claims(owner, other, left) && !RevolverButtonClaim.claims(owner, other, right),
                    "Another item must claim no button.");
            SexPistolsAttachments.coordinator(owner).cleanup(owner.serverLevel(), owner);
        });
    }

    // OQ-S02: the controls stay 1.16's; the tooltip names the key, as bound, that takes the Stand's buttons away.
    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty",
            batch = "sexPistolsRevolverControls", timeoutTicks = TIMEOUT)
    public static void tooltipNamesTheBoundKeyThatGivesTheOwnHandBack(GameTestHelper helper) {
        run(helper, "own-hand-tooltip", (owner, gun) -> {
            for (ItemStack stack : List.of(gun, new ItemStack(SexPistolsItems.HEAVY_REVOLVER.get()))) {
                List<Component> lines = new ArrayList<>();
                stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
                boolean named = false;
                for (Component line : lines) {
                    if (line.getContents() instanceof TranslatableContents text
                            && text.getKey().equals(OWN_HAND_LINE)
                            && text.getArgs().length == 1
                            && text.getArgs()[0] instanceof Component key
                            && key.getContents() instanceof KeybindContents bind
                            && bind.getName().equals("jojo_ripples.key.disable_hotbars")) {
                        named = true;
                    }
                }
                helper.assertTrue(named, "The tooltip of " + stack.getItem() + " lacks the own-hand line with the core's "
                        + "bound key; lines=" + lines.size() + ".");
            }
            for (String file : List.of("/assets/rotp_sex_pistols/lang/",
                    "/assets/rotp_sex_pistols/stand_skins/sex_pistols/assets/rotp_sex_pistols/lang/")) {
                for (String language : List.of("en_us", "zh_cn", "pt_br")) {
                    String path = file + language + ".json";
                    try (java.io.InputStream in = RevolverControlsGameTests.class.getResourceAsStream(path)) {
                        helper.assertTrue(in != null, "Missing " + path);
                        boolean has = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,
                                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().has(OWN_HAND_LINE);
                        helper.assertTrue(has, path + " lacks " + OWN_HAND_LINE);
                    }
                    catch (java.io.IOException error) {
                        throw new IllegalStateException(path, error);
                    }
                }
            }
        });
    }

    private static final String OWN_HAND_LINE = "item.rotp_sex_pistols.revolver.control.own_hand";

    @FunctionalInterface
    private interface Body {
        void run(ServerPlayer owner, ItemStack gun);
    }

    private static void run(GameTestHelper helper, String name, Body body) {
        GameTestChunkTickets.aroundOrigin(helper).whenTicking(() -> {}, h -> {
            ServerPlayer owner = SexPistolsGameTests.makeCompatibleMockPlayer(h, "revolver-controls-" + name);
            try {
                SexPistolsGameTests.positionPlayer(h, owner, 1, 2, 1);
                owner.setNoGravity(true);
                owner.setYRot(0.0F);
                owner.setYHeadRot(0.0F);
                owner.setXRot(0.0F);
                ItemStack gun = loadedGun(h, owner, new ItemStack(SexPistolsItems.REVOLVER.get()));
                body.run(owner, gun);
                h.succeed();
            }
            finally {
                RevolverBulletEntity.discardOwnedBy(owner.serverLevel(), owner.getUUID());
                owner.connection.disconnect(net.minecraft.network.chat.Component.literal("Revolver controls test over"));
            }
        });
    }

    private static ItemStack loadedGun(GameTestHelper helper, ServerPlayer owner, ItemStack gun) {
        ServerLevel level = helper.getLevel();
        owner.setItemSlot(EquipmentSlot.MAINHAND, gun);
        owner.getInventory().add(new ItemStack(SexPistolsItems.REVOLVER_AMMO.get()));
        helper.assertTrue(RevolverItem.openForStandReload(level, owner, gun, true, true)
                        && RevolverItem.loadSelectedChamberIfEmpty(owner, gun)
                        && RevolverItem.closeReloadMode(level, owner, gun),
                "setup: the revolver could not be loaded");
        return gun;
    }

    private static void useKey(ServerPlayer owner, ItemStack gun) {
        ((RevolverItem) gun.getItem()).use(owner.serverLevel(), owner, InteractionHand.MAIN_HAND);
    }

    private static void press(ServerPlayer owner, int operation) {
        RevolverChamberControlPayload.apply(owner, operation);
    }

    private static void standReload(GameTestHelper helper, ServerPlayer owner, StandPower power, String what) {
        SexPistolsGameTests.pressReload(helper, owner, power, SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH,
                STAND_RELOAD_KEY, "Stand Reload " + what);
    }

    // A plain X on the slot: the server resolves the slot's ability anew (the moves are cached once a tick), checks it for
    // a click and presses it under the slot's id.
    private static void standReloadKey(GameTestHelper helper, ServerPlayer owner, StandPower power, String what) {
        Ability base = power.getAbility(SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH);
        helper.assertTrue(base != null, "Sex Pistols has no Stand Reload.");
        owner.setShiftKeyDown(false);
        power._curAvailableMoves.update(power, power.getMoveset());
        AbilityConditionCheck container = power.updateAvailableMoves().getContextVariationContainer(base);
        helper.assertTrue(container != null && container.ability == base
                        && AbilityInput.withConditionCheck(container, owner, InputMethod.CLICK),
                "The server refused X on the Stand Reload slot " + what + " before the press: "
                        + (container == null ? "no ability" : container.ability.name() + " "
                                + container.conditionCheck.isPositive()) + ".");
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AbilityInput.keyPress(STAND_RELOAD_KEY, container.ability, owner, input, InputMethod.CLICK, 0.0F,
                    BufferingState.clickCanBuffer(), base.abilityId);
        }
        finally {
            input.release();
            AbilityInput.keyRelease(STAND_RELOAD_KEY, owner);
        }
    }

    private static List<String> actionBarKeys(ServerPlayer owner) {
        EmbeddedChannel channel = SexPistolsGameTests.sentPackets(owner);
        channel.flushOutbound();
        List<String> keys = new ArrayList<>();
        for (Object message : channel.outboundMessages()) {
            if (message instanceof ClientboundSystemChatPacket bar && bar.overlay()
                    && bar.content().getContents() instanceof TranslatableContents text) {
                keys.add(text.getKey());
            }
        }
        return keys;
    }

    private static List<RevolverBulletEntity> bullets(ServerPlayer owner) {
        return owner.serverLevel().getEntitiesOfClass(RevolverBulletEntity.class,
                new AABB(owner.blockPosition()).inflate(16.0D), bullet -> bullet.getOwner() == owner);
    }

    private static Cow cow(GameTestHelper helper, ServerPlayer owner, double ahead) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "setup: no cow");
        cow.setNoAi(true);
        cow.setNoGravity(true);
        Vec3 at = owner.position().add(owner.getLookAngle().scale(ahead));
        cow.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(cow), "setup: the cow could not be added");
        return cow;
    }

    private static float memberHealth(ServerPlayer owner, SexPistolsCoordinator coordinator) {
        float health = 0.0F;
        for (int number : SexPistolsIds.SEX_PISTOLS_NUMBERS) {
            SexPistolsEntity member = coordinator.member(owner.serverLevel(), number);
            if (member != null) {
                health += member.getHealth();
            }
        }
        return health;
    }

    private static boolean heardTheBlow(ServerPlayer owner) {
        EmbeddedChannel channel = SexPistolsGameTests.sentPackets(owner);
        channel.flushOutbound();
        for (Object message : channel.outboundMessages()) {
            if (message instanceof ClientboundSoundPacket sound
                    && sound.getSound().value() == SoundEvents.PLAYER_ATTACK_WEAK
                    && sound.getVolume() == 0.7F
                    && sound.getPitch() == 1.15F) {
                return true;
            }
        }
        return false;
    }
}
