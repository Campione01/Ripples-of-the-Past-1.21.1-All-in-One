package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.network.BloodThrowSplashPayload;
import rotp.addons.kingcrimson.network.TemporalStateSyncPayload;
import rotp.addons.kingcrimson.state.KingCrimsonBloodThrowService;
import rotp.addons.kingcrimson.state.KingCrimsonEraseStamina;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseShiftService;
import rotp.core.api.time.event.TimeEraseRunningEvent;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;

/**
 * Blood Throw (EV1105AC section 8, the owner: "在時刪時可以通過時刪結束這個鍵的變體操作來對準星位置敵人釋放以血矇眼"), through the erase key
 * as a client presses it and lets it go: the price, what counts as a target, when the blood lands and on what, and
 * what a throw leaves of the erase. A test's callbacks and the fixture's tick of its players run in no fixed order
 * within a server tick: what is read here is read inside the callback that causes it, or ticks later.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonBloodThrowGameTests {
    private static final String BLOOD = KingCrimsonBloodThrowService.ABILITY;
    private static final String ERASE = "kingcrimson_timeerase";

    private KingCrimsonBloodThrowGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_blood_key")
    public static void theEraseKeyLetGoWithTheVariationKeyDownThrowsBloodAndTheEraseGoesOnUntilItsKeyIsPressed(
            GameTestHelper helper) {
        String title = "Blood Throw by the erase key";
        Scene scene = new Scene(helper, "kc-blood-key", 14, 8);
        ServerPlayer eraser = scene.eraser;
        StandPower power = scene.power;
        Pig pig = scene.place(EntityType.PIG.create(helper.getLevel()), 8.5D, 4.5D);
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        helper.runAfterDelay(12, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(eraser), "no erase is running", title);
            scene.aim(pig);
            float stamina = power.getStamina();
            int sent = KingCrimsonEraseFixture.listener(eraser).sentCount();
            boolean keyUp = scene.letGo(true);
            scene.rule(keyUp && KingCrimsonEraseFixture.erasing(eraser), "the erase key let go with the variation key "
                    + "down and a pig under the crosshair left " + scene.erase());
            scene.rule(eraser.getHealth() == 18.0F, "the throw took " + (20.0F - eraser.getHealth()) + " health, not 2");
            scene.rule(Math.abs(stamina - power.getStamina() - 50.0F) < 1.0F, "the throw took "
                    + (stamina - power.getStamina()) + " stamina, not 50");
            scene.rule(power.getAbilityCooldown(BLOOD) == 200, "the throw's cooldown is "
                    + power.getAbilityCooldown(BLOOD) + " ticks, not 200");
            scene.rule(keyUpSyncs(eraser, sent) == 1, "the eraser's client was told " + keyUpSyncs(eraser, sent)
                    + " times that his erase goes on with its key up");
            scene.rule(scene.blindness(pig) == null, "the blood landed as it was thrown");
        }));
        helper.runAfterDelay(22, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(eraser), "ten ticks after its key was let go for a throw "
                    + "the erase is over", title);
            scene.rule(scene.blindness(pig) == null, "blood that had reached the pig landed inside the erased time");
            // On its cooldown the variation is refused, and nothing is taken.
            scene.rule(!scene.throwByKey() && eraser.getHealth() == 18.0F, "on its cooldown blood was thrown again "
                    + "(health " + eraser.getHealth() + ")");
            // With nothing under the crosshair it is refused with the message, and nothing is taken.
            power.setAbilityCooldown(BLOOD, 0, 0);
            scene.aimAt(eraser.getEyePosition().add(0.0D, 10.0D, 0.0D));
            int sent = KingCrimsonEraseFixture.listener(eraser).sentCount();
            float stamina = power.getStamina();
            boolean thrown = scene.throwByKey();
            scene.rule(!thrown && eraser.getHealth() == 18.0F && Math.abs(power.getStamina() - stamina) < 0.01F
                            && power.getAbilityCooldown(BLOOD) == 0,
                    "with nothing under the crosshair: thrown " + thrown + ", health " + eraser.getHealth()
                            + ", stamina taken " + (stamina - power.getStamina()) + ", cooldown "
                            + power.getAbilityCooldown(BLOOD));
            scene.rule(noTargetMessages(eraser, sent) == 1, "with nothing under the crosshair the eraser was told "
                    + noTargetMessages(eraser, sent) + " times that there is no target");
            // With a target again, the erase key with the variation key throws.
            scene.aim(pig);
            thrown = scene.throwByKey();
            scene.rule(thrown && eraser.getHealth() == 16.0F && power.getAbilityCooldown(BLOOD) == 200
                            && KingCrimsonEraseFixture.erasing(eraser),
                    "the erase key with the variation key, in an erase that goes on with its key up: thrown " + thrown
                            + ", health " + eraser.getHealth() + ", cooldown " + power.getAbilityCooldown(BLOOD) + ", "
                            + scene.erase());
            // Never the last point of his health.
            power.setAbilityCooldown(BLOOD, 0, 0);
            eraser.setHealth(2.5F);
            thrown = scene.throwByKey();
            scene.rule(thrown && eraser.getHealth() == 1.0F, "a throw at 2.5 health: thrown " + thrown + ", health left "
                    + eraser.getHealth() + ", not 1");
            power.setAbilityCooldown(BLOOD, 0, 0);
            thrown = scene.throwByKey();
            scene.rule(thrown && eraser.getHealth() == 1.0F, "a throw at 1 health: thrown " + thrown + ", health left "
                    + eraser.getHealth() + ", not 1");
        }));
        helper.runAfterDelay(28, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(eraser), "the erase ended before its key was pressed", title);
            float pigHealth = pig.getHealth();
            float stamina = power.getStamina();
            // Owner 2026-11-11: the end of an erase gives back what it did not use of its start, as a time stop does.
            KingCrimsonTemporalState running = KingCrimsonEraseFixture.state(eraser);
            float back = KingCrimsonEraseStamina.unusedStart(power.getStaminaCostShare(StandAbilityStamina.effectiveCost(power,
                    KingCrimsonPhase5Spec.TIME_ERASE_ACTIVATION_STAMINA)), running.eraseLengthTicks(),
                    running.elapsedWorldTicks(eraser.level().getGameTime()));
            boolean taken = scene.endByKey();
            scene.rule(taken && scene.idle(), "a plain press of the erase key, in an erase that goes on with its key "
                    + "up: taken " + taken + ", and it left " + scene.erase());
            scene.rule(back > 100.0F && Math.abs(power.getStamina() - stamina - back) < 0.01F, "the press that ended the"
                    + " erase left the bar " + (power.getStamina() - stamina) + " higher: not by the " + back
                    + " that the end of the erase gives back, and nothing else");
            scene.rule(power.getAbilityCooldown(ERASE) > 0, "the erase ended by its key has no cooldown");
            MobEffectInstance blind = scene.blindness(pig);
            scene.rule(blind != null && blind.getDuration() == 100,
                    "in the tick time came back the pig's half blindness is "
                            + (blind == null ? "none" : blind.getDuration() + " ticks") + ", not 100 ticks");
            scene.rule(pig.getHealth() == pigHealth, "the blood took " + (pigHealth - pig.getHealth())
                    + " health from the pig");
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
        }));
        helper.runAfterDelay(34, () -> scene.fixture.guarded(() -> {
            scene.premise(scene.idle(), "the erase goes on after its key ended it", title);
            power.setAbilityCooldown(BLOOD, 0, 0);
            eraser.setHealth(20.0F);
            scene.aim(pig);
            power._curAvailableMoves.update(power, power.getMoveset());
            scene.rule(power._curAvailableMoves.getContextVariationContainer(BLOOD) == null,
                    "outside an erase Blood Throw is among the user's moves");
            scene.rule(!scene.throwByKey() && !KingCrimsonBloodThrowService.throwAtAim(eraser)
                    && eraser.getHealth() == 20.0F, "outside an erase blood was thrown (health " + eraser.getHealth() + ")");
            scene.finish(title);
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 260, batch = "kc_blood_player")
    public static void aPlayerIsAimedAtWhereTheEraserSeesHimAndTheBloodCoversHisScreenWhenHeCanSeeAgain(
            GameTestHelper helper) {
        String title = "Blood Throw at a player";
        Scene scene = new Scene(helper, "kc-blood-player", 24, 16);
        ServerPlayer eraser = scene.eraser;
        ServerLevel level = helper.getLevel();
        ServerPlayer target = scene.fixture.player("kc-blood-target", 14.5D, 2.0D, 1.5D);
        int[] sent = new int[1];
        int[] flight = new int[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        // He walks eight blocks across the eraser's view, and the eraser sees him ahead of where he is.
        for (int tick = 0; tick < 32; tick++) {
            helper.runAfterDelay(8 + tick, () -> target.setPos(target.getX(), target.getY(), target.getZ() + 0.25D));
        }
        helper.runAfterDelay(46, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(eraser), "no erase is running", title);
            Vec3 image = KingCrimsonTimeEraseShiftService.imageOf(eraser, target);
            scene.premise(image.distanceTo(target.position()) > 2.0D, "the other player is seen only "
                    + image.distanceTo(target.position()) + " blocks from where he is", title);
            scene.aimAt(target.getEyePosition());
            scene.rule(!KingCrimsonBloodThrowService.hasTarget(eraser), "the crosshair on where the other player is, "
                    + "and not on where the eraser sees him, has a target");
            Vec3 seenEyes = image.add(0.0D, target.getEyeHeight(), 0.0D);
            scene.aimAt(seenEyes);
            // A drop flies 1.6 blocks in a tick.
            flight[0] = Mth.ceil(eraser.getEyePosition().distanceTo(seenEyes) / 1.6D);
            scene.premise(flight[0] >= 9 && flight[0] <= 11, "the flight to his image is " + flight[0] + " ticks", title);
            sent[0] = KingCrimsonEraseFixture.listener(target).sentCount();
            scene.premise(scene.letGo(true), "no blood was thrown at the other player's image", title);
        }));
        helper.runAfterDelay(49, () -> scene.fixture.guarded(() -> {
            scene.premise(scene.endByKey() && scene.idle(), "the erase key did not end the erase", title);
            scene.fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            scene.rule(splashes(target, sent[0]) == 0, "blood three ticks into a flight of " + flight[0]
                    + " covered his screen as the erase ended");
        }));
        helper.runAfterDelay(53, () -> scene.fixture.guarded(() ->
                scene.rule(splashes(target, sent[0]) == 0, "blood seven ticks into a flight of " + flight[0]
                        + " covered his screen")));
        helper.runAfterDelay(61, () -> scene.fixture.guarded(() -> {
            scene.rule(splashes(target, sent[0]) == 1, "blood that arrived after the erase covered the other player's "
                    + "screen " + splashes(target, sent[0]) + " times, not once");
            scene.rule(scene.blindness(target) == null, "a player whose screen the blood covers is half blinded too");
            scene.rule(splashes(eraser, 0) == 0, "the eraser's own screen was covered");
            scene.rule(target.getHealth() == target.getMaxHealth(), "the blood took "
                    + (target.getMaxHealth() - target.getHealth()) + " health from the other player");
        }));
        // Blood that has arrived waits for time to come back, and lands in that tick.
        helper.runAfterDelay(66, () -> scene.fixture.guarded(scene::pressAgain));
        helper.runAfterDelay(76, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(eraser), "no second erase is running", title);
            scene.aimAt(KingCrimsonTimeEraseShiftService.imageOf(eraser, target).add(0.0D, target.getEyeHeight(), 0.0D));
            sent[0] = KingCrimsonEraseFixture.listener(target).sentCount();
            scene.premise(scene.letGo(true), "no blood was thrown at the other player standing still", title);
        }));
        helper.runAfterDelay(92, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(eraser), "the second erase is over", title);
            scene.rule(splashes(target, sent[0]) == 0, "blood that had arrived covered his screen inside the erased time");
            scene.premise(scene.endByKey() && scene.idle(), "the erase key did not end the second erase", title);
            scene.rule(splashes(target, sent[0]) == 1, "in the tick time came back the blood that had arrived covered "
                    + "his screen " + splashes(target, sent[0]) + " times, not once");
            scene.finish(title);
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 260, batch = "kc_blood_release")
    public static void theKeyLetGoWithNothingThrownEndsTheEraseAndAUserThrownOutOfHisEraseBlindsNobody(
            GameTestHelper helper) {
        String title = "The erase key let go";
        Scene scene = new Scene(helper, "kc-blood-release", 14, 8);
        ServerPlayer eraser = scene.eraser;
        StandPower power = scene.power;
        Pig pig = scene.place(EntityType.PIG.create(helper.getLevel()), 8.5D, 4.5D);
        boolean[] throwOut = {false};
        Consumer<TimeEraseRunningEvent> other = event -> {
            if (event.getEraser() == eraser && throwOut[0]) {
                throwOut[0] = false;
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TimeEraseRunningEvent.class, other);
        scene.fixture.onClose(() -> NeoForge.EVENT_BUS.unregister(other));
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        helper.runAfterDelay(12, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(eraser), "no erase is running", title);
            scene.aim(pig);
            boolean keyUp = scene.letGo(false);
            scene.rule(!keyUp && scene.idle() && eraser.getHealth() == 20.0F && power.getAbilityCooldown(BLOOD) == 0,
                    "the key let go without the variation key, a pig under the crosshair: it left " + scene.erase()
                            + ", health " + eraser.getHealth() + ", Blood Throw's cooldown "
                            + power.getAbilityCooldown(BLOOD));
        }));
        helper.runAfterDelay(18, () -> scene.fixture.guarded(scene::pressAgain));
        helper.runAfterDelay(28, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(eraser), "no second erase is running", title);
            scene.aimAt(eraser.getEyePosition().add(0.0D, 10.0D, 0.0D));
            boolean keyUp = scene.letGo(true);
            scene.rule(!keyUp && scene.idle() && eraser.getHealth() == 20.0F,
                    "the key let go with the variation key down and nothing under the crosshair: it left "
                            + scene.erase() + ", health " + eraser.getHealth());
        }));
        helper.runAfterDelay(34, () -> scene.fixture.guarded(scene::pressAgain));
        helper.runAfterDelay(44, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(eraser), "no third erase is running", title);
            scene.aim(pig);
            power.setAbilityCooldown(BLOOD, 100);
            boolean keyUp = scene.letGo(true);
            scene.rule(!keyUp && scene.idle() && eraser.getHealth() == 20.0F,
                    "the key let go with the variation key down, a pig under the crosshair and Blood Throw on its "
                            + "cooldown: it left " + scene.erase() + ", health " + eraser.getHealth());
        }));
        // Thrown out of his erase by another time ability, his blood lands on nobody.
        helper.runAfterDelay(50, () -> scene.fixture.guarded(scene::pressAgain));
        helper.runAfterDelay(60, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(eraser), "no fourth erase is running", title);
            scene.aim(pig);
            scene.premise(scene.letGo(true), "no blood was thrown at the pig", title);
            throwOut[0] = true;
        }));
        helper.runAfterDelay(64, () -> scene.fixture.guarded(() ->
                scene.premise(scene.idle() && !throwOut[0], "the eraser was not thrown out of his erase", title)));
        helper.runAfterDelay(96, () -> scene.fixture.guarded(() -> {
            scene.rule(scene.blindness(pig) == null, "thrown out of his erase a tick after the throw, his blood half "
                    + "blinded the pig all the same");
            scene.finish(title);
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_blood_targets")
    public static void bloodIsThrownOnlyAtALivingThingInReachWithNothingSolidBeforeIt(GameTestHelper helper) {
        String title = "What Blood Throw is thrown at";
        Scene scene = new Scene(helper, "kc-blood-targets", 40, 12);
        ServerPlayer eraser = scene.eraser;
        StandPower power = scene.power;
        ServerLevel level = helper.getLevel();
        Pig near = scene.place(EntityType.PIG.create(level), 8.5D, 1.5D);
        // 35 blocks from his eyes; the reach is 32.
        Pig far = scene.place(EntityType.PIG.create(level), 37.5D, 8.5D);
        Pig walled = scene.place(EntityType.PIG.create(level), 8.5D, 8.5D);
        ArmorStand stand = scene.place(EntityType.ARMOR_STAND.create(level), 4.5D, 9.5D);
        for (int z = 6; z <= 8; z++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            }
        }
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        helper.runAfterDelay(12, () -> scene.fixture.guarded(() -> {
            scene.premise(KingCrimsonEraseFixture.erasing(eraser), "no erase is running", title);
            scene.aim(near);
            scene.premise(scene.letGo(true), "no blood was thrown at the pig in plain sight", title);
            power.setAbilityCooldown(BLOOD, 0, 0);
            scene.aim(far);
            scene.rule(!scene.throwByKey(), "blood was thrown at a pig 35 blocks away");
            power.setAbilityCooldown(BLOOD, 0, 0);
            scene.aim(walled);
            scene.rule(!scene.throwByKey(), "blood was thrown at a pig behind a wall");
            power.setAbilityCooldown(BLOOD, 0, 0);
            scene.aim(stand);
            scene.rule(!scene.throwByKey(), "blood was thrown at an armour stand");
            // Each was aimed at truly: with the wall gone and the far pig brought into reach, both are thrown at.
            for (int z = 6; z <= 8; z++) {
                for (int y = 2; y <= 4; y++) {
                    helper.setBlock(new BlockPos(5, y, z), Blocks.AIR);
                }
            }
            power.setAbilityCooldown(BLOOD, 0, 0);
            scene.aim(walled);
            scene.premise(scene.throwByKey(), "with the wall gone no blood was thrown at the pig behind it", title);
            far.setPos(helper.absoluteVec(new Vec3(30.5D, 2.0D, 8.5D)));
            power.setAbilityCooldown(BLOOD, 0, 0);
            scene.aim(far);
            scene.premise(scene.throwByKey(), "no blood was thrown at the far pig brought to 28 blocks", title);
        }));
        helper.runAfterDelay(36, () -> scene.fixture.guarded(() -> {
            scene.premise(scene.endByKey() && scene.idle(), "the erase key did not end the erase", title);
            scene.rule(scene.blindness(near) != null && scene.blindness(walled) != null && scene.blindness(far) != null,
                    "of the three pigs blood was thrown at, half blinded as time came back: the near one "
                            + (scene.blindness(near) != null) + ", the one that was behind the wall "
                            + (scene.blindness(walled) != null) + ", the one brought into reach "
                            + (scene.blindness(far) != null));
            scene.finish(title);
        }));
    }

    private static int keyUpSyncs(ServerPlayer player, int index) {
        int syncs = 0;
        for (TemporalStateSyncPayload sync
                : KingCrimsonEraseFixture.listener(player).payloadsSince(index, TemporalStateSyncPayload.class)) {
            if (sync.eraseKeyUp()) {
                syncs++;
            }
        }
        return syncs;
    }

    private static int splashes(ServerPlayer player, int index) {
        return KingCrimsonEraseFixture.listener(player).payloadsSince(index, BloodThrowSplashPayload.class).size();
    }

    private static int noTargetMessages(ServerPlayer player, int index) {
        int messages = 0;
        for (Packet<?> packet : KingCrimsonEraseFixture.listener(player).sentSince(index)) {
            if (packet instanceof ClientboundSystemChatPacket bar && bar.overlay()
                    && bar.content().getContents() instanceof TranslatableContents text
                    && text.getKey().equals("rotp_kingcrimson.message.bloodthrow_no_target")) {
                messages++;
            }
        }
        return messages;
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final ServerPlayer eraser;
        private final StandPower power;
        private final List<String> broken = new ArrayList<>();
        private boolean finished;

        private Scene(GameTestHelper helper, String name, int length, int width) {
            this.helper = helper;
            fixture = new KingCrimsonEraseFixture(helper);
            fixture.floor(length, width);
            eraser = fixture.player(name, 2.5D, 2.0D, 4.5D);
            power = fixture.kingCrimson(eraser, 4);
            // Too hungry to heal by himself: the price is read off his health.
            eraser.getFoodData().setFoodLevel(10);
        }

        /** Something that stays where it is put. */
        private <T extends Entity> T place(T entity, double x, double z) {
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
            }
            entity.setPos(helper.absoluteVec(new Vec3(x, 2.0D, z)));
            helper.getLevel().addFreshEntity(entity);
            return fixture.track(entity);
        }

        private void press() {
            helper.assertTrue(fixture.holdErase(eraser).isActionSuccess, "Setup: the held Time Erase did not start");
        }

        private void pressAgain() {
            if (finished) {
                return;
            }
            power.setAbilityCooldown(ERASE, 0, 0);
            power.setAbilityCooldown(BLOOD, 0, 0);
            power.setStamina(power.getMaxStamina());
            eraser.setHealth(eraser.getMaxHealth());
            press();
        }

        private void aim(Entity target) {
            aimAt(target.getBoundingBox().getCenter());
        }

        private void aimAt(Vec3 point) {
            eraser.lookAt(EntityAnchorArgument.Anchor.EYES, point);
        }

        /** The erase key is let go, the variation key down or up; whether the erase goes on with its key up. */
        private boolean letGo(boolean variationKey) {
            fixture.variationKey(eraser, variationKey);
            fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            return KingCrimsonEraseFixture.state(eraser).isKeyUp();
        }

        /** The erase key with the variation key down, pressed and let go. */
        private boolean throwByKey() {
            return fixture.press(eraser, BLOOD, KingCrimsonEraseFixture.ERASE_KEY, InputMethod.CLICK, true)
                    .isActionSuccess;
        }

        /** The erase key alone, pressed. */
        private boolean endByKey() {
            return fixture.press(eraser, ERASE, KingCrimsonEraseFixture.ERASE_KEY, InputMethod.HOLD, false)
                    .isActionSuccess;
        }

        private boolean idle() {
            return KingCrimsonEraseFixture.state(eraser).isIdle();
        }

        private String erase() {
            KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(eraser);
            return state.isIdle() ? "no erase" : state.isKeyUp() ? "an erase with its key up" : "an erase with its key down";
        }

        private MobEffectInstance blindness(LivingEntity entity) {
            MobEffectInstance left = entity.getEffect(KingCrimsonAddon.HALF_BLINDNESS_LEFT);
            return left != null ? left : entity.getEffect(KingCrimsonAddon.HALF_BLINDNESS_RIGHT);
        }

        private void rule(boolean holds, String broke) {
            if (!holds && !finished) {
                broken.add(broke);
            }
        }

        /** A step the rest of the test stands on: where it fails, the test ends with the rules broken before it. */
        private void premise(boolean holds, String what, String title) {
            if (!holds && !finished) {
                broken.add("Setup: " + what);
                finish(title);
            }
        }

        private void finish(String title) {
            if (finished) {
                return;
            }
            finished = true;
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(), title + ": " + String.join("; ", broken)));
            fixture.succeed();
        }
    }
}
