package rotp.addons.stonefree.ability;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity.Kind;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder("rotp_sf_barrier_review")
@PrefixGameTestTemplate(false)
public final class StoneFreeOwnerTimeGameTests {
    private static final String SLOWED = "rotp_sf_gametest_body_string_slowed";
    private static final String MODE_B = "rotp_sf_gametest_body_string_mode_b";
    private static final float SLOW_RATE = 0.75F;
    private static final int FAST_RATE = 3;
    private static boolean registered;

    private StoneFreeOwnerTimeGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 60)
    public static void travellingBodyStringsUseOwnerTimeOnce(GameTestHelper helper) {
        registerRates();
        Vec3 origin = helper.absoluteVec(new Vec3(1.5D, 18.0D, 1.5D));
        Cow user = spawnCow(helper, origin);
        StandPower power = PowerClass.STAND.attachGet(user);
        try {
            power.setStand(StoneFreeAddon.STONE_FREE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            helper.assertTrue(StoneFreeAddon.STONE_FREE.get().summon(user, power), "Could not summon Stone Free");
            StoneFreeStandEntity stand = StoneFreeServices.controller(user);
            helper.assertTrue(stand != null, "Stone Free has no controller");
            stand.moveTo(origin.x, origin.y, origin.z, 0.0F, user.getXRot());
            stand.setYBodyRot(0.0F);
            for (Kind kind : List.of(Kind.STAND_STRING, Kind.USER_STRING, Kind.USER_SWEEP,
                    Kind.USER_WHIP, Kind.USER_SPIKE, Kind.EXTENDED_PUNCH)) {
                Flight normal = fly(helper, user, stand, kind, null);
                Flight slowed = fly(helper, user, stand, kind, SLOWED);
                Flight fast = fly(helper, user, stand, kind, MODE_B);
                helper.assertTrue(slowed.firstEntityTicks == 1
                                && Math.abs(slowed.tips.getFirst().length()
                                        - SLOW_RATE * normal.tips.getFirst().length()) < 1.0E-5D,
                        kind + " did not extend by 3/4 of its ordinary first step");
                // Four slowed updates take three owner ticks, including through the swing and turn-around.
                for (int i = 1; 4 * i <= slowed.tips.size() && 3 * i <= normal.tips.size(); i++) {
                    assertSameTip(helper, normal, 3 * i, slowed, 4 * i, kind + " at 3/4 speed");
                }
                helper.assertTrue(Math.abs(slowed.serverTicks() * SLOW_RATE - normal.serverTicks()) <= 1.0D,
                        kind + " returned at " + slowed.serverTicks() + " updates at 3/4 speed, versus "
                                + normal.serverTicks() + " at rate 1");

                helper.assertTrue(fast.firstEntityTicks == FAST_RATE && fast.entityTicks == normal.entityTicks
                                && fast.serverTicks() == (normal.serverTicks() + FAST_RATE - 1) / FAST_RATE,
                        kind + " did not return after exactly one effective Mode B scale: " + fast.entityTicks
                                + " entity ticks in " + fast.serverTicks() + " updates, versus " + normal.entityTicks);
                for (int i = 1; i <= fast.tips.size(); i++) {
                    assertSameTip(helper, normal, FAST_RATE * i, fast, i, kind + " in Mode B");
                }
            }
            assertSlowedBindExpiry(helper, user, stand);
        }
        finally {
            if (power.isSummoned()) {
                power.getPowerType().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }

    private static void registerRates() {
        if (!registered) {
            EntityTimeRates.register(StoneFreeAddon.id("gametest_body_string_time"), (entity, channel) -> {
                if (channel == EntityTimeRates.Channel.STAND_ACTIONS && entity instanceof Cow) {
                    if (entity.getTags().contains(SLOWED)) {
                        return SLOW_RATE;
                    }
                    if (entity.getTags().contains(MODE_B)) {
                        return FAST_RATE;
                    }
                }
                if (channel == EntityTimeRates.Channel.ENTITY_TICK && entity instanceof StoneFreeStringEntity
                        && entity.getTags().contains(MODE_B)) {
                    return FAST_RATE;
                }
                return EntityTimeRates.NO_RATE;
            });
            registered = true;
        }
    }

    private static Cow spawnCow(GameTestHelper helper, Vec3 pos) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "Could not create a body-string fixture cow");
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(pos.x, pos.y, pos.z, 0.0F, -75.0F);
        cow.setYBodyRot(0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a body-string fixture cow");
        return cow;
    }

    private record Flight(List<Vec3> tips, int firstEntityTicks, int entityTicks) {
        private int serverTicks() {
            return tips.size() + 1;
        }
    }

    private static Flight fly(GameTestHelper helper, Cow user, StoneFreeStandEntity stand, Kind kind, String tag) {
        ServerLevel level = helper.getLevel();
        StoneFreeStringEntity string = new StoneFreeStringEntity(kind, stand, level);
        if (kind == Kind.STAND_STRING) {
            string.setStandBindTicks(18);
            string.setRotOffsets(12.0F, 4.0F);
        }
        Vec3 origin = string.getOriginPoint(1.0F);
        string.setPos(origin);
        if (tag != null) {
            user.addTag(tag);
        }
        if (MODE_B.equals(tag)) {
            string.addTag(MODE_B);
        }
        try {
            helper.assertTrue(level.addFreshEntity(string), "Could not add " + kind);
            List<Vec3> tips = new ArrayList<>();
            int firstEntityTicks = 0;
            for (int update = 0; update < 40 && string.isAlive(); update++) {
                // Use the real server path so ENTITY_TICK can run its extra ticks.
                level.tickNonPassenger(string);
                if (update == 0) {
                    firstEntityTicks = string.tickCount;
                }
                if (string.isAlive()) {
                    tips.add(string.position().subtract(origin));
                }
            }
            helper.assertTrue(string.isRemoved() && !tips.isEmpty(), kind + " did not fly out and return: " + tag);
            return new Flight(tips, firstEntityTicks, string.tickCount);
        }
        finally {
            string.discard();
            if (tag != null) {
                user.removeTag(tag);
            }
        }
    }

    private static void assertSameTip(GameTestHelper helper, Flight normal, int normalTick,
            Flight scaled, int scaledTick, String what) {
        Vec3 expected = normal.tips.get(normalTick - 1);
        Vec3 actual = scaled.tips.get(scaledTick - 1);
        helper.assertTrue(actual.distanceTo(expected) < 1.0E-5D,
                what + " at owner tick " + normalTick + ": tip " + actual + " instead of " + expected);
    }

    private static void assertSlowedBindExpiry(GameTestHelper helper, Cow user, StoneFreeStandEntity stand) {
        ServerLevel level = helper.getLevel();
        StoneFreeStringEntity string = new StoneFreeStringEntity(Kind.USER_STRING, stand, level);
        string.setBinding(true);
        Vec3 origin = string.getOriginPoint(1.0F);
        string.setPos(origin);
        Vec3 aim = Vec3.directionFromRotation(user.getXRot(), user.getYRot());
        Cow target = spawnCow(helper, origin.add(aim.scale(3.0D)).add(0.0D, -0.6D, 0.0D));
        user.addTag(SLOWED);
        try {
            helper.assertTrue(level.addFreshEntity(string), "Could not add the slowed user bind");
            while (string.isAlive() && string.getEntityAttachedTo() == null && string.tickCount < 16) {
                level.tickNonPassenger(string);
            }
            helper.assertTrue(string.getEntityAttachedTo() == target, "The slowed user bind missed its catch");
            int lastLiveTick = 65; // 49 owner ticks at 3/4 speed, with expiry strictly after 49.
            MobEffectInstance hold = target.getEffect(ModStatusEffects.IMMOBILIZE);
            helper.assertTrue(hold != null && hold.getDuration() == lastLiveTick + 1 - string.tickCount,
                    "The slowed bind's immobilize did not cover its remaining lifetime: " + hold);
            while (string.isAlive() && string.tickCount < lastLiveTick) {
                level.tickNonPassenger(string);
            }
            helper.assertTrue(string.isAlive() && string.getEntityAttachedTo() == target,
                    "The slowed bind released its catch before its last live tick");
            level.tickNonPassenger(string);
            helper.assertTrue(string.isRemoved(), "The slowed bind did not expire past its 49 owner ticks");
        }
        finally {
            string.discard();
            target.discard();
            user.removeTag(SLOWED);
        }
    }
}
