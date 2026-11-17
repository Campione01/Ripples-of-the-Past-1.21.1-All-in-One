package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.entity.KingCrimsonDecoyMind.Mode;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;

/**
 * Owner 2026-11-07: "時刪者留下的假人ai...我們的ai不打傷害，只做逃跑或拉扯". What its user was doing at the press decides what the
 * decoy does; it keeps a threat at a distance or runs from it and never hurts anything; left alone it goes on with its
 * user's keys, round what would stop them and never off a cliff or into lava; it takes and leaves nothing. Every case
 * begins with the real key.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonDecoyMindGameTests {
    private static final int BEGUN = 10;
    /** Ticks between what the user does and his press: his Stand has come to where he stands. */
    private static final int SETTLE = 6;

    private KingCrimsonDecoyMindGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 460, batch = "kc_decoy_mind_choice")
    public static void whatItsUserWasDoingAtThePressDecidesWhatTheDecoyDoes(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-decoy-choice", 16, 10);
        ServerPlayer user = scene.user;
        Husk[] husk = new Husk[1];
        // Each case: what the user does before the press, and what the decoy is then found doing.
        scene.nextCase(() -> {}, decoy -> scene.rule(decoy.mind().mode() == Mode.CARRY_ON && decoy.mind().threat() == null,
                "with nothing near its user the decoy " + doing(decoy) + ", it does not carry on"));
        scene.nextCase(() -> {
            husk[0] = scene.husk(5.5D, 5.5D);
            scene.hit(husk[0]);
        }, decoy -> scene.rule(decoy.mind().mode() == Mode.KITE && decoy.mind().threat() == husk[0],
                "its user had just hit a husk in front of him and the decoy " + doing(decoy) + ", it does not kite that husk"));
        scene.nextCase(() -> {
            scene.hit(husk[0]);
            scene.turn(90.0F);
        }, decoy -> scene.rule(decoy.mind().mode() == Mode.FLEE,
                "its user had the husk he fought behind him and the decoy " + doing(decoy) + ", it does not flee"));
        scene.nextCase(() -> {
            scene.hit(husk[0]);
            PlayerMovementInputData.get(user).setValues(0.0F, -1.0F, false, false, false);
        }, decoy -> scene.rule(decoy.mind().mode() == Mode.FLEE,
                "its user was backing away from the husk he fought and the decoy " + doing(decoy) + ", it does not flee"));
        scene.nextCase(() -> {
            scene.hit(husk[0]);
            user.setHealth(4.0F);
        }, decoy -> scene.rule(decoy.mind().mode() == Mode.FLEE,
                "its user was down to 4 health in a fight and the decoy " + doing(decoy) + ", it does not flee"));
        scene.nextCase(() -> {
            husk[0].discard();
            husk[0] = scene.husk(5.5D, 5.5D);
            husk[0].setTarget(user);
        }, decoy -> scene.rule(decoy.mind().mode() == Mode.KITE && decoy.mind().threat() == husk[0],
                "a husk was after its user, who had hit nothing, and the decoy " + doing(decoy) + ", it does not kite that husk"));
        scene.nextCase(() -> {
            husk[0].discard();
            husk[0] = scene.husk(5.5D, 5.5D);
            user.invulnerableTime = 0;
            helper.assertTrue(user.hurt(user.damageSources().mobAttack(husk[0]), 1.0F), "Setup: the husk's blow did not land");
        }, decoy -> scene.rule(decoy.mind().mode() == Mode.KITE && decoy.mind().threat() == husk[0],
                "a husk had just hit its user and the decoy " + doing(decoy) + ", it does not kite that husk"));
        scene.nextCase(() -> {
            husk[0].discard();
            husk[0] = scene.husk(5.5D, 5.5D);
            husk[0].setTarget(user);
            scene.turn(90.0F);
        }, decoy -> scene.rule(decoy.mind().mode() == Mode.FLEE && decoy.mind().threat() == husk[0],
                "a husk was after its user from behind him and the decoy " + doing(decoy) + ", it does not flee that husk"));
        scene.runCases(30, "The decoy's choice at the press");
    }

    @GameTest(template = "empty", timeoutTicks = 420, batch = "kc_decoy_mind_kite")
    public static void aKitingDecoyKeepsItsThreatAtADistanceAndRunsOnceHitTwice(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-decoy-kite", 40, 30);
        // It runs off the floor when it flees.
        scene.fixture.hold(-30, -30, 70, 60);
        scene.user.setPos(helper.absoluteVec(new Vec3(14.5D, 2.0D, 15.5D)));
        Husk husk = scene.husk(16.5D, 15.5D);
        double[] aside = {0.0D};
        boolean[] sprinted = {false};
        Vec3[] last = new Vec3[1];
        List<Double> fled = new ArrayList<>();
        double[] cameBack = {0.0D};
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            scene.hit(husk);
            scene.press();
        }));
        helper.onEachTick(() -> {
            KingCrimsonTimeEraseDecoyEntity decoy = scene.decoyOrNull();
            if (decoy == null) {
                return;
            }
            sprinted[0] |= decoy.isSprinting();
            if (decoy.mind().mode() == Mode.FLEE) {
                // Half a second against half a second before: how far it came back towards what it runs from.
                fled.add(flat(decoy.position().subtract(husk.position())));
                if (fled.size() > 10) {
                    cameBack[0] = Math.max(cameBack[0], fled.get(fled.size() - 11) - fled.get(fled.size() - 1));
                }
            }
            if (last[0] != null) {
                // How far it steps to the side of the line to its threat.
                Vec3 toHusk = husk.position().subtract(decoy.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
                Vec3 step = decoy.position().subtract(last[0]);
                aside[0] += Math.abs(step.x * -toHusk.z + step.z * toHusk.x);
            }
            last[0] = decoy.position();
        });
        helper.runAfterDelay(90, () -> scene.fixture.guarded(() -> {
            KingCrimsonTimeEraseDecoyEntity decoy = scene.decoy();
            helper.assertTrue(decoy.mind().mode() == Mode.KITE, "Setup: the decoy " + doing(decoy));
            double distance = flat(decoy.position().subtract(husk.position()));
            scene.rule(distance >= 2.5D && distance <= 8.5D, "80 ticks after a husk stood 2 blocks from it, a kiting decoy is "
                    + distance + " blocks from it, not about 4 to 7");
            scene.rule(Math.abs(Mth.wrapDegrees(yawTo(decoy.position(), husk.position()) - decoy.getYRot())) < 35.0F,
                    "a kiting decoy looks " + Mth.wrapDegrees(yawTo(decoy.position(), husk.position()) - decoy.getYRot())
                            + " degrees off its threat");
            scene.rule(aside[0] > 2.0D, "a kiting decoy stepped " + aside[0] + " blocks to the side in 80 ticks");
            // The threat falls back: the decoy comes after it, to the band again.
            husk.setPos(decoy.getX() + 11.0D, husk.getY(), decoy.getZ());
        }));
        helper.runAfterDelay(170, () -> scene.fixture.guarded(() -> {
            KingCrimsonTimeEraseDecoyEntity decoy = scene.decoy();
            double distance = flat(decoy.position().subtract(husk.position()));
            scene.rule(distance >= 2.5D && distance <= 8.5D, "80 ticks after its threat stood 11 blocks away, a kiting decoy "
                    + "is " + distance + " blocks from it, not about 4 to 7");
            scene.rule(husk.getHealth() == husk.getMaxHealth(), "a kiting decoy hurt its threat: " + husk.getHealth() + " health");
            sprinted[0] = false;
            for (int blow = 0; blow < 2; blow++) {
                decoy.invulnerableTime = 0;
                helper.assertTrue(decoy.hurt(decoy.damageSources().mobAttack(husk), 1.0F), "Setup: the blow did not land");
            }
            scene.rule(decoy.mind().mode() == Mode.FLEE, "hit twice, the decoy " + doing(decoy) + ", it does not flee");
        }));
        helper.runAfterDelay(250, () -> scene.fixture.guarded(() -> {
            KingCrimsonTimeEraseDecoyEntity decoy = scene.decoy();
            double distance = flat(decoy.position().subtract(husk.position()));
            // One that does not flee stays within 11 blocks. One that flees was 25.7 to 34.3 blocks away in 20 runs
            // (median 30.8); while it still turned back at each new path it was 15.9 to 31.4 away (median 18.8), and
            // once 11.8.
            scene.rule(distance > 12.0D && sprinted[0], "80 ticks after it turned to flee the decoy is " + distance
                    + " blocks from its threat, and it sprinted: " + sprinted[0] + " (on the ground " + decoy.onGround()
                    + ", path done " + decoy.getNavigation().isDone() + ", path " + decoy.getNavigation().getPath()
                    + ", wanted " + decoy.getMoveControl().hasWanted() + ", motion " + decoy.getDeltaMovement()
                    + ", at " + decoy.position().subtract(helper.absoluteVec(Vec3.ZERO)) + ")");
            scene.rule(cameBack[0] <= 0.5D, "while it fled, the decoy came " + cameBack[0]
                    + " blocks back towards its threat within half a second");
            scene.rule(decoy.mind().mode() == Mode.FLEE, "a fleeing decoy turned back to " + decoy.mind().mode());
            scene.finish("A kiting decoy");
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_decoy_mind_harmless")
    public static void nothingOfTheDecoyHurtsWhatFightsIt(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-decoy-harmless", 16, 10);
        ServerLevel level = helper.getLevel();
        ItemStack thorned = new ItemStack(Items.DIAMOND_CHESTPLATE);
        thorned.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.THORNS), 3);
        scene.user.setItemSlot(EquipmentSlot.CHEST, thorned);
        Husk husk = scene.husk(4.5D, 5.5D);
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        helper.runAfterDelay(BEGUN + 4, () -> scene.fixture.guarded(() -> {
            KingCrimsonTimeEraseDecoyEntity decoy = scene.decoy();
            helper.assertTrue(decoy.getItemBySlot(EquipmentSlot.CHEST).isEnchanted(), "Setup: the decoy wears no thorns");
            int landed = 0;
            // A long fight: forty blows of a husk on thorned armour, which would give most of them back.
            for (int blow = 0; blow < 40 && decoy.isAlive(); blow++) {
                decoy.invulnerableTime = 0;
                decoy.setHealth(decoy.getMaxHealth());
                if (husk.doHurtTarget(decoy)) {
                    landed++;
                }
            }
            helper.assertTrue(landed >= 30, "Setup: only " + landed + " of the husk's 40 blows landed on the decoy");
            scene.rule(husk.getHealth() == husk.getMaxHealth(), "the decoy's thorns hurt the husk that hit it: "
                    + husk.getHealth() + " of " + husk.getMaxHealth() + " health");
            scene.rule(!decoy.doHurtTarget(husk) && husk.getHealth() == husk.getMaxHealth(), "the decoy struck a husk");
            scene.finish("A decoy in a fight");
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 460, batch = "kc_decoy_mind_way")
    public static void leftAloneTheDecoyGoesOnRoundAWallAndStopsAtADropAndAtLava(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-decoy-way", 30, 24);
        ServerPlayer user = scene.user;
        BlockState stone = Blocks.STONE.defaultBlockState();
        // A wall across his way with a way round it, three blocks high.
        for (int z = 2; z <= 6; z++) {
            for (int y = 2; y <= 4; y++) {
                scene.place(new BlockPos(8, y, z), stone);
            }
        }
        // A raised walk that ends in a drop of five.
        for (int x = 2; x <= 9; x++) {
            for (int y = 2; y <= 6; y++) {
                scene.place(new BlockPos(x, y, 12), stone);
            }
        }
        // A trench of lava across the whole width of a lane walled on every side: no way round it.
        for (int x = 1; x <= 14; x++) {
            for (int y = 2; y <= 4; y++) {
                scene.place(new BlockPos(x, y, 17), stone);
                scene.place(new BlockPos(x, y, 21), stone);
            }
        }
        for (int z = 18; z <= 20; z++) {
            for (int y = 2; y <= 4; y++) {
                scene.place(new BlockPos(1, y, z), stone);
                scene.place(new BlockPos(14, y, z), stone);
            }
        }
        for (int z = 18; z <= 20; z++) {
            for (int x = 9; x <= 10; x++) {
                scene.place(new BlockPos(x, 0, z), stone);
                scene.place(new BlockPos(x, 1, z), Blocks.LAVA.defaultBlockState());
            }
        }
        Vec3 origin = helper.absoluteVec(Vec3.ZERO);
        scene.nextCase(() -> {
            user.setPos(helper.absoluteVec(new Vec3(3.5D, 2.0D, 4.5D)));
            PlayerMovementInputData.get(user).setValues(0.0F, 1.0F, false, false, false);
        }, decoy -> {
            Vec3 at = decoy.position().subtract(origin);
            scene.rule(at.x > 9.5D, "100 ticks on, a decoy whose user walked at a wall with a way round it is at " + at
                    + ", not past the wall at x 8");
        });
        scene.nextCase(() -> {
            user.setPos(helper.absoluteVec(new Vec3(3.5D, 7.0D, 12.5D)));
            PlayerMovementInputData.get(user).setValues(0.0F, 1.0F, false, false, false);
        }, decoy -> {
            Vec3 at = decoy.position().subtract(origin);
            scene.rule(Math.abs(at.y - 7.0D) < 1.0E-3D && at.x > 8.0D && at.x < 10.31D,
                    "100 ticks on, a decoy whose user walked at a drop of five blocks is at " + at
                            + ", not standing at its edge (x 10, y 7)");
        });
        scene.nextCase(() -> {
            user.setPos(helper.absoluteVec(new Vec3(3.5D, 2.0D, 19.5D)));
            PlayerMovementInputData.get(user).setValues(0.0F, 1.0F, false, false, false);
        }, decoy -> {
            Vec3 at = decoy.position().subtract(origin);
            scene.rule(Math.abs(at.y - 2.0D) < 1.0E-3D && at.x > 6.0D && at.x < 8.71D && !decoy.isOnFire(),
                    "100 ticks on, a decoy whose user walked at lava is at " + at + ", on fire " + decoy.isOnFire()
                            + ", not standing short of the lava at x 9");
        });
        scene.runCases(120, "A decoy left alone");
    }

    @GameTest(template = "empty", timeoutTicks = 140, batch = "kc_decoy_mind_nothing")
    public static void theDecoyTakesNothingAndLeavesNothingAndCarriesItsUsersName(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-decoy-nothing", 16, 10);
        ServerPlayer user = scene.user;
        ServerLevel level = helper.getLevel();
        user.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        user.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        ItemEntity[] diamond = new ItemEntity[1];
        ExperienceOrb[] orb = new ExperienceOrb[1];
        helper.runAfterDelay(2, () -> scene.fixture.guarded(scene::press));
        helper.runAfterDelay(BEGUN, () -> scene.fixture.guarded(() -> {
            KingCrimsonTimeEraseDecoyEntity decoy = scene.decoy();
            scene.rule(decoy.shouldShowName() && decoy.getName().getString().equals(user.getGameProfile().getName()),
                    "the decoy's name is '" + decoy.getName().getString() + "', shown " + decoy.shouldShowName()
                            + ", not its user's, " + user.getGameProfile().getName());
            // The user steps away, so that what lies at the decoy's feet is the decoy's to take or leave.
            user.setPos(user.getX(), user.getY(), user.getZ() + 4.0D);
            diamond[0] = scene.fixture.track(new ItemEntity(level, decoy.getX(), decoy.getY(), decoy.getZ(),
                    new ItemStack(Items.DIAMOND)));
            diamond[0].setNoPickUpDelay();
            diamond[0].setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(diamond[0]);
            orb[0] = scene.fixture.track(new ExperienceOrb(level, decoy.getX(), decoy.getY(), decoy.getZ(), 5));
            level.addFreshEntity(orb[0]);
        }));
        helper.runAfterDelay(BEGUN + 50, () -> scene.fixture.guarded(() -> {
            KingCrimsonTimeEraseDecoyEntity decoy = scene.decoy();
            scene.rule(!diamond[0].isRemoved(), "the decoy took a diamond from the ground");
            scene.rule(!orb[0].isRemoved(), "the decoy took an experience orb");
            Vec3 at = decoy.position();
            diamond[0].discard();
            orb[0].discard();
            helper.assertTrue(decoy.getMainHandItem().is(Items.IRON_SWORD), "Setup: the decoy holds no sword");
            decoy.invulnerableTime = 0;
            decoy.hurt(level.damageSources().generic(), 1000.0F);
            helper.assertTrue(decoy.isRemoved(), "Setup: the decoy was not killed");
            List<ItemEntity> left = level.getEntitiesOfClass(ItemEntity.class, user.getBoundingBox().inflate(12.0D),
                    item -> item.position().distanceTo(at) < 6.0D);
            scene.rule(left.isEmpty(), "a killed decoy left in the world: "
                    + left.stream().map(item -> item.getItem().toString()).toList());
            scene.finish("What a decoy takes and leaves");
        }));
    }

    private static String doing(KingCrimsonTimeEraseDecoyEntity decoy) {
        return "is in " + decoy.mind().mode() + " of " + (decoy.mind().threat() == null ? "nothing"
                : decoy.mind().threat().getType().toShortString());
    }

    private static double flat(Vec3 between) {
        return Math.sqrt(between.x * between.x + between.z * between.z);
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final ServerPlayer user;
        private final StandPower power;
        private final Vec3 home;
        private final List<String> broken = new ArrayList<>();
        private final List<Runnable> arranges = new ArrayList<>();
        private final List<Consumer<KingCrimsonTimeEraseDecoyEntity>> checks = new ArrayList<>();

        private Scene(GameTestHelper helper, String name, int length, int width) {
            this.helper = helper;
            fixture = new KingCrimsonEraseFixture(helper);
            fixture.floor(length, width);
            user = fixture.player(name, 2.5D, 2.0D, 5.5D);
            home = user.position();
            power = fixture.kingCrimson(user, 4);
            helper.onEachTick(() -> {
                if (!user.isRemoved()) {
                    power.setStamina(power.getMaxStamina());
                }
            });
        }

        /** A husk that stands where it is put: something to be wary of. */
        private Husk husk(double x, double z) {
            Husk husk = EntityType.HUSK.create(helper.getLevel());
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            husk.setPos(helper.absoluteVec(new Vec3(x, 2.0D, z)));
            helper.getLevel().addFreshEntity(husk);
            return fixture.track(husk);
        }

        /** A real blow of the user on it. */
        private void hit(Husk husk) {
            husk.invulnerableTime = 0;
            husk.setHealth(husk.getMaxHealth());
            helper.assertTrue(husk.hurt(user.damageSources().playerAttack(user), 1.0F), "Setup: the user's blow did not land");
            husk.setHealth(husk.getMaxHealth());
        }

        private void turn(float yaw) {
            user.setYRot(yaw);
            user.setYHeadRot(yaw);
            user.yBodyRot = yaw;
        }

        private void place(BlockPos relative, BlockState state) {
            helper.getLevel().setBlock(helper.absolutePos(relative), state, 3);
        }

        private void press() {
            power.setAbilityCooldown("kingcrimson_timeerase", 0, 0);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(fixture.holdErase(user).isActionSuccess, "Setup: the held Time Erase did not start");
        }

        private KingCrimsonTimeEraseDecoyEntity decoyOrNull() {
            List<KingCrimsonTimeEraseDecoyEntity> decoys = fixture.decoys(user);
            return decoys.size() == 1 ? decoys.get(0) : null;
        }

        private KingCrimsonTimeEraseDecoyEntity decoy() {
            List<KingCrimsonTimeEraseDecoyEntity> decoys = fixture.decoys(user);
            helper.assertTrue(decoys.size() == 1, "Setup: the erase has " + decoys.size() + " decoys; the user is "
                    + "erasing " + KingCrimsonEraseFixture.erasing(user) + " at "
                    + user.position().subtract(helper.absoluteVec(Vec3.ZERO)) + ", on the ground " + user.onGround()
                    + ", his Stand " + power.getSummonedStandEntity() + " doing "
                    + (power.getSummonedStandEntity() == null || power.getSummonedStandEntity().getCurStandAction() == null
                            ? "nothing" : power.getSummonedStandEntity().getCurStandAction().ability + " in "
                                    + power.getSummonedStandEntity().getCurStandAction().getPhase()));
            return decoys.get(0);
        }

        private void nextCase(Runnable arrange, Consumer<KingCrimsonTimeEraseDecoyEntity> check) {
            arranges.add(arrange);
            checks.add(check);
        }

        /** Each case in turn: the user as he stood, what he does, the real press, the check so many ticks on, the key let go. */
        private void runCases(int ticksEach, String title) {
            int span = ticksEach + SETTLE + 16;
            for (int i = 0; i < arranges.size(); i++) {
                Runnable arrange = arranges.get(i);
                Consumer<KingCrimsonTimeEraseDecoyEntity> check = checks.get(i);
                helper.runAfterDelay(2 + (long) i * span, () -> fixture.guarded(() -> {
                    user.setPos(home);
                    turn(-90.0F);
                    user.setHealth(user.getMaxHealth());
                    user.invulnerableTime = 0;
                    PlayerMovementInputData.get(user).setValues(0.0F, 0.0F, false, false, false);
                    arrange.run();
                    user.setDeltaMovement(Vec3.ZERO);
                    user.fallDistance = 0.0F;
                    user.setOnGround(true);
                }));
                helper.runAfterDelay(2 + (long) i * span + SETTLE, () -> fixture.guarded(this::press));
                helper.runAfterDelay(2 + (long) i * span + SETTLE + ticksEach, () -> fixture.guarded(() -> {
                    check.accept(decoy());
                    fixture.release(user, KingCrimsonEraseFixture.ERASE_KEY);
                }));
            }
            helper.runAfterDelay(2 + (long) arranges.size() * span, () -> finish(title));
        }

        private void rule(boolean holds, String broke) {
            if (!holds) {
                broken.add(broke);
            }
        }

        private void finish(String title) {
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(), title + ": " + String.join("; ", broken)));
            fixture.succeed();
        }
    }
}
