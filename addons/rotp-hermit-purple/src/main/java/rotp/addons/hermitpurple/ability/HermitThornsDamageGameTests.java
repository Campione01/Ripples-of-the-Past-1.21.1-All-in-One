package rotp.addons.hermitpurple.ability;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleGameEvents;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandTuning;

@GameTestHolder("rotp_hp_lash")
@PrefixGameTestTemplate(false)
public final class HermitThornsDamageGameTests {
    private static final short KEY = 91;

    private HermitThornsDamageGameTests() {}

    // R772.1 EV-STANDCFG item3: the thorns' reflected hit is dealt from an event handler, outside any code the core runs
    // as the Stand's, so the handler says it is the Stand's work: at a damage share of 50 % to mobs the cow that hit the
    // user takes half of the 1.0 the thorns return, at the default share the whole of it.
    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 80, batch = "hp_thorns_share")
    public static void theThornsReflectTakesTheStandsDamageShare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPThornsShare"));
        BlockPos origin = helper.absolutePos(new BlockPos(1, 8, 1));
        player.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        player.setNoGravity(true);
        level.addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        StandTuning tuning = HermitPurpleAddon.HERMIT_PURPLE.get().getTuning();
        Cow[] cow = {null};
        Runnable leave = () -> {
            tuning.restoreDefaults();
            AbilityInput.keyRelease(KEY, player);
            if (cow[0] != null) {
                cow[0].discard();
            }
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            if (!player.isRemoved()) {
                level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
        };
        try {
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power), "Fixture: the summon failed");
        }
        catch (RuntimeException | Error failure) {
            leave.run();
            throw failure;
        }
        HermitPurpleStandEntity stand = HermitPurpleAddon.controller(player);
        helper.startSequence()
                .thenIdle(stand.summonLockTicks + 2)
                .thenExecute(() -> {
                    try {
                        Ability block = power.getAbility(HermitPurpleAddon.HP_BLOCK_NAME);
                        BufferingState buffering = BufferingState.clickOnly();
                        AbilityInput.keyPress(KEY, block, player, null, InputMethod.HOLD, 0.0F, buffering,
                                block.getAbilityId());
                        helper.assertTrue(buffering.isActionSuccess, "Fixture: the thorns block did not start");
                    }
                    catch (RuntimeException | Error failure) {
                        leave.run();
                        throw failure;
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(stand.isStandBlocking(), "Fixture: the stand is not blocking"))
                .thenExecute(() -> {
                    try {
                        cow[0] = EntityType.COW.create(level);
                        cow[0].setNoAi(true);
                        cow[0].moveTo(origin.getX() + 2.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
                        helper.assertTrue(level.addFreshEntity(cow[0]), "Fixture: the cow was not added");
                        float whole = reflectedOnto(level, player, cow[0]);
                        helper.assertTrue(Math.abs(whole - HermitPurpleSpec.THORNS_REFLECT_DAMAGE) < 1.0E-4F,
                                "At the default share the thorns return " + HermitPurpleSpec.THORNS_REFLECT_DAMAGE
                                        + " to the cow; it took " + whole);
                        JsonObject half = new JsonObject();
                        half.addProperty("damageVsMobs", 50);
                        tuning.applyConfig(half);
                        float halved = reflectedOnto(level, player, cow[0]);
                        helper.assertTrue(Math.abs(halved - HermitPurpleSpec.THORNS_REFLECT_DAMAGE / 2.0F) < 1.0E-4F,
                                "At 50 % damage to mobs the thorns return half of "
                                        + HermitPurpleSpec.THORNS_REFLECT_DAMAGE + "; the cow took " + halved);
                        LogUtils.getLogger().info("R772_HP_THORNS_PASS reflect_share");
                    }
                    finally {
                        leave.run();
                    }
                })
                .thenSucceed();
    }

    // a cow hits the user; what the thorns made the cow lose
    private static float reflectedOnto(ServerLevel level, FakePlayer user, Cow cow) {
        cow.setHealth(cow.getMaxHealth());
        cow.invulnerableTime = 0;
        DamageSource hit = level.damageSources().mobAttack(cow);
        HermitPurpleGameEvents.onIncomingDamage(new LivingIncomingDamageEvent(user, new DamageContainer(hit, 4.0F)));
        return cow.getMaxHealth() - cow.getHealth();
    }
}
