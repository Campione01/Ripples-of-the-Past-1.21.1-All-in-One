package rotp.addons.kingcrimson.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase3Spec;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;
import rotp.addons.kingcrimson.network.EpitaphPhantomFramePayload;
import rotp.addons.kingcrimson.network.EpitaphSessionPayload;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.network.s2c.PlayVoiceLinePacket;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * Epitaph, the key (EPITAPH-ARCH 5.1): a press, as a client sends it, starts a prediction of the creatures that hunt the
 * user and of what is under his crosshair, is paid for, and tells nobody but him.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphKeyGameTests {
    private static final short EPITAPH_KEY = 31;

    private KingCrimsonEpitaphKeyGameTests() {}

    private static int epitaphSounds(List<Packet<?>> packets) {
        int heard = 0;
        for (Packet<?> packet : KingCrimsonEpitaphFixture.flat(packets)) {
            if (packet instanceof ClientboundSoundPacket sound && sound.getSound().value() == KingCrimsonAddon.KINGCRIMSON_EPITAPH.get()
                    || packet instanceof ClientboundSoundEntityPacket onEntity
                            && onEntity.getSound().value() == KingCrimsonAddon.KINGCRIMSON_EPITAPH.get()) {
                heard++;
            }
        }
        return heard;
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_key")
    public static void theKeyOfEpitaphStartsAPredictionAndTellsNobodyElse(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-key", 4, 24, 10);
        // The room waits: what the phantom of the user begins with is read before it has lived a tick.
        fixture.budget(0);
        ServerPlayer user = fixture.user;
        StandPower power = fixture.power;
        boolean enabled = KingCrimsonConfig.EPITAPH_ENABLED.get();
        fixture.base.onClose(() -> KingCrimsonConfig.EPITAPH_ENABLED.set(enabled));
        int cost = KingCrimsonConfig.EPITAPH_STAMINA.get();
        fixture.base.onClose(() -> KingCrimsonConfig.EPITAPH_STAMINA.set(cost));
        // Further from the zombie than the user is, and near enough to the user to track him.
        ServerPlayer beside = fixture.witness("kc-ep-near", 0.5D, 6.5D);
        Zombie hunter = fixture.hunter(EntityType.ZOMBIE, 8.5D, 6.5D);
        // Straight ahead of the user: under his crosshair.
        Cow aimedAt = fixture.mob(EntityType.COW, 5.5D, 2.5D);
        Cow bystander = fixture.mob(EntityType.COW, 5.5D, 8.5D);
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEpitaphFixture.tracks(beside, user) && hunter.getTarget() == user,
                    "Setup: the player beside the user does not track him, or the zombie is after somebody else");
            float before = power.getStamina();
            int fromUser = KingCrimsonEraseFixture.listener(user).sentCount();
            int fromBeside = KingCrimsonEraseFixture.listener(beside).sentCount();
            BufferingState pressed = fixture.base.press(user, "kingcrimson_epitaph", EPITAPH_KEY, InputMethod.CLICK, false);
            EpitaphSession session = EpitaphSessions.of(user);
            helper.assertTrue(pressed.isActionSuccess && session != null, "The key of Epitaph began no prediction");
            fixture.rule(session.record().track(hunter.getId()) != null, "the zombie that hunts the user is not foreseen");
            fixture.rule(session.record().track(aimedAt.getId()) != null, "the cow under the crosshair is not foreseen");
            fixture.rule(session.record().track(bystander.getId()) == null, "a cow that stands by is foreseen");
            float paid = before - power.getStamina();
            fixture.rule(KingCrimsonGroundGameTestFixture.near(paid, KingCrimsonPhase3Spec.EPITAPH_ACTIVATION_STAMINA),
                    "the press took " + paid + " stamina, not " + KingCrimsonPhase3Spec.EPITAPH_ACTIVATION_STAMINA + " as it is set");
            fixture.rule(power.getAbilityCooldown("kingcrimson_epitaph") > 0, "Epitaph is not on cooldown after its press");
            // The press is paid before the copies are made: his phantom begins with what he has left.
            SandboxRoom.Phantom own = session.room().info(ActorKey.of(user));
            StandPower copied = own != null && own.entity instanceof LivingEntity copy ? StandPower.get(copy) : null;
            fixture.rule(copied != null && Math.abs(copied.getStamina() - power.getStamina()) < 0.01F, "after a press that took " + paid
                    + " stamina the user has " + power.getStamina() + " and his phantom begins with "
                    + (copied != null ? copied.getStamina() : -1.0F));
            var toUser = KingCrimsonEraseFixture.listener(user);
            var toBeside = KingCrimsonEraseFixture.listener(beside);
            fixture.rule(epitaphSounds(toUser.sentSince(fromUser)) == 1
                            && toUser.payloadsSince(fromUser, PlayVoiceLinePacket.class).size() == 1,
                    "the user heard the sound of Epitaph " + epitaphSounds(toUser.sentSince(fromUser)) + " times and his line "
                            + toUser.payloadsSince(fromUser, PlayVoiceLinePacket.class).size() + " times, not once each");
            fixture.rule(epitaphSounds(toBeside.sentSince(fromBeside)) == 0
                            && toBeside.payloadsSince(fromBeside, PlayVoiceLinePacket.class).isEmpty()
                            && toBeside.payloadsSince(fromBeside, EpitaphSessionPayload.class).isEmpty()
                            && toBeside.payloadsSince(fromBeside, EpitaphPhantomFramePayload.class).isEmpty(),
                    "the player beside the user was told of the prediction: " + epitaphSounds(toBeside.sentSince(fromBeside))
                            + " sounds, " + toBeside.payloadsSince(fromBeside, PlayVoiceLinePacket.class).size() + " lines, "
                            + toBeside.payloadsSince(fromBeside, EpitaphSessionPayload.class).size() + " messages");

            // One prediction at a time: a second press, its cooldown aside, is refused and costs nothing.
            power.setAbilityCooldown("kingcrimson_epitaph", 0, 0);
            float held = power.getStamina();
            BufferingState again = fixture.base.press(user, "kingcrimson_epitaph", EPITAPH_KEY, InputMethod.CLICK, false);
            fixture.rule(!again.isActionSuccess && EpitaphSessions.of(user) == session && power.getStamina() == held,
                    "a second press while a prediction runs was not refused: success=" + again.isActionSuccess
                            + ", stamina " + held + " -> " + power.getStamina());
            session.end(EpitaphEnd.CANCELLED);

            // What a press costs is a setting.
            KingCrimsonConfig.EPITAPH_STAMINA.set(60);
            power.setAbilityCooldown("kingcrimson_epitaph", 0, 0);
            BufferingState cheaper = fixture.base.press(user, "kingcrimson_epitaph", EPITAPH_KEY, InputMethod.CLICK, false);
            EpitaphSession second = EpitaphSessions.of(user);
            fixture.rule(cheaper.isActionSuccess && second != null && KingCrimsonGroundGameTestFixture.near(held - power.getStamina(), 60.0F),
                    "with the cost of Epitaph set to 60 a press took " + (held - power.getStamina()) + " stamina");
            if (second != null) {
                second.end(EpitaphEnd.CANCELLED);
            }
            held = power.getStamina();

            // Switched off in the settings, the key does nothing.
            KingCrimsonConfig.EPITAPH_ENABLED.set(false);
            power.setAbilityCooldown("kingcrimson_epitaph", 0, 0);
            BufferingState off = fixture.base.press(user, "kingcrimson_epitaph", EPITAPH_KEY, InputMethod.CLICK, false);
            fixture.rule(!off.isActionSuccess && EpitaphSessions.of(user) == null && power.getStamina() == held,
                    "with Epitaph switched off its key still began a prediction or took stamina");
            fixture.finish("The key of Epitaph");
        }));
    }

    // C6 (EPITAPH-ARCH section 4, T1): first the foresight, then the erase.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_not_in_erase")
    public static void epitaphIsRefusedDuringAnErase(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-t1", 4, 16, 8);
        ServerPlayer user = fixture.user;
        StandPower power = fixture.power;
        fixture.hunter(EntityType.ZOMBIE, 10.5D, 2.5D);
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            // An erase that no key holds: the key of Epitaph is free to be pressed.
            helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(user, KingCrimsonTemporalState.EraseKind.REFLEX)
                    && KingCrimsonEraseFixture.erasing(user), "Setup: no erase began");
            float before = power.getStamina();
            ConditionCheck check = power.getAbility("kingcrimson_epitaph").checkConditions(power);
            BufferingState pressed = fixture.base.press(user, "kingcrimson_epitaph", EPITAPH_KEY, InputMethod.CLICK, false);
            fixture.rule(!pressed.isActionSuccess && EpitaphSessions.of(user) == null && power.getStamina() == before
                    && power.getAbilityCooldown("kingcrimson_epitaph") <= 0, "pressed during an erase, Epitaph was not refused: success="
                            + pressed.isActionSuccess + ", a prediction " + (EpitaphSessions.of(user) != null ? "runs" : "does not run")
                            + ", stamina " + before + " -> " + power.getStamina());
            fixture.rule(!check.isPositive() && check.getWarning() != null
                    && check.getWarning().getContents() instanceof TranslatableContents says
                    && says.getKey().equals("rotp_kingcrimson.message.epitaph_during_erase"),
                    "the refusal during an erase does not say why: " + (check.getWarning() == null ? "no message" : check.getWarning().getString()));
            // The erase over, the key foresees again.
            KingCrimsonTemporalStateService.terminate(user);
            helper.assertTrue(!KingCrimsonEraseFixture.erasing(user), "Setup: the erase did not end");
            power.setStamina(power.getMaxStamina());
            BufferingState after = fixture.base.press(user, "kingcrimson_epitaph", EPITAPH_KEY, InputMethod.CLICK, false);
            EpitaphSession session = EpitaphSessions.of(user);
            fixture.rule(after.isActionSuccess && session != null, "after the erase the key of Epitaph began no prediction");
            if (session != null) {
                session.end(EpitaphEnd.CANCELLED);
            }
            fixture.finish("Epitaph is refused during an erase");
        }));
    }

    // R4 (rulings after stages A and B): a busy Stand does not refuse the press, and the press ends what it was doing.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_press_seam")
    public static void thePressEndsWhatTheStandWasDoing(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-seam", 4, 16, 8);
        ServerPlayer user = fixture.user;
        StandPower power = fixture.power;
        fixture.hunter(EntityType.ZOMBIE, 10.5D, 2.5D);
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            StandEntity stand = power.getSummonedStandEntity();
            fixture.base.press(user, "heavy_punch", KingCrimsonEraseFixture.OTHER_KEY, InputMethod.CLICK, false);
            EntityActionInstance blow = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(blow != null && !blow.isOver(), "Setup: the Stand began no heavy punch");
            BufferingState pressed = fixture.base.press(user, "kingcrimson_epitaph", EPITAPH_KEY, InputMethod.CLICK, false);
            EpitaphSession session = EpitaphSessions.of(user);
            helper.assertTrue(pressed.isActionSuccess && session != null, "A Stand in the middle of a blow refused the press of Epitaph");
            EntityActionInstance now = LivingComponentAction.getCurEntityAction(stand);
            fixture.rule(now == null || now.isOver(), "after the press the Stand is still in the blow it had begun");
            fixture.rule(power.isSummoned() && power.getSummonedStandEntity() == stand && !stand.isRemoved(),
                    "the press took the Stand away");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The press of Epitaph ends what the Stand was doing");
        }));
    }
}
