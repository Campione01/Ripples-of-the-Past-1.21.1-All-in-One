package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.state.KingCrimsonEraseReach;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.api.damage.DefencePiercing;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * Owner 2026-11-07: "時刪狀態下玩家不能受到任何傷害或負面buff影響，但所有buff自身持續時間也要正常流逝。同時時刪時玩家不受包括蜘蛛絲減速等效果
 * 影響" and "時刪中紅王應該不會碰到任何實體，單論與其他實體間邏輯和旁觀者模式無異". Each test first sees the world act on the player
 * with no erase, then holds the real key and sees it not act. A test names every rule it found broken, so several can
 * be told apart in one run.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEraseReachGameTests {
    private static final int BEGIN = 8;

    private KingCrimsonEraseReachGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80, batch = "kc_reach_damage")
    public static void noDamageReachesAnEraserButAnOperatorsKillAndWhatPiercesAnErase(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-reach-damage");
        ServerPlayer player = scene.player;
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(player.hurt(player.damageSources().magic(), 1.0F) && player.getHealth() < 20.0F,
                    "Setup: with no erase a hit lands");
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            scene.press();
        }));
        helper.runAfterDelay(BEGIN, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            ServerLevel level = helper.getLevel();
            Map<String, DamageSource> sources = new LinkedHashMap<>();
            sources.put("a plain hit", player.damageSources().generic());
            sources.put("magic", player.damageSources().magic());
            sources.put("the void", player.damageSources().fellOutOfWorld());
            sources.put("starving", player.damageSources().starve());
            sources.put("an explosion", player.damageSources().explosion(null, null));
            sources.put("lava", player.damageSources().lava());
            sources.put("falling", player.damageSources().fall());
            sources.put("lightning", player.damageSources().lightningBolt());
            for (Map.Entry<String, DamageSource> source : sources.entrySet()) {
                player.invulnerableTime = 0;
                boolean hurt = player.hurt(source.getValue(), 6.0F);
                scene.rule(!hurt && player.getHealth() == player.getMaxHealth(),
                        source.getKey() + " hurt an eraser (health " + player.getHealth() + ")");
            }
            // Under the world: the void's own damage each tick.
            Vec3 stood = player.position();
            player.setPos(stood.x, level.getMinBuildHeight() - 80.0D, stood.z);
            player.doTick();
            player.doTick();
            scene.rule(player.getHealth() == player.getMaxHealth() && player.isAlive(),
                    "an eraser under the world lost health to the void (health " + player.getHealth() + ")");
            player.setPos(stood);
            scene.rule(scene.stand.isInvulnerableTo(player.damageSources().fellOutOfWorld()),
                    "an eraser's Stand is not out of the void's reach");
            // What another add-on marks as piercing an erase lands, as Gold Experience Requiem's death loop must.
            player.invulnerableTime = 0;
            Holder<DamageType> magic = player.damageSources().magic().typeHolder();
            for (TagKey<DamageType> other : List.of(DefencePiercing.GUARD, DefencePiercing.DAMAGE_CAPS)) {
                player.invulnerableTime = 0;
                scene.rule(!player.hurt(new DamageSource(new PiercingType(magic, other)), 3.0F)
                                && player.getHealth() == player.getMaxHealth(),
                        "damage tagged only " + other.location() + " reached an eraser");
            }
            player.invulnerableTime = 0;
            boolean pierced = player.hurt(new DamageSource(new PiercingType(magic, DefencePiercing.INVULNERABILITY)),
                    3.0F);
            scene.rule(pierced && player.getHealth() < player.getMaxHealth(),
                    "damage tagged " + DefencePiercing.INVULNERABILITY.location() + " did not reach an eraser");
            player.invulnerableTime = 0;
            player.kill();
            scene.rule(player.isDeadOrDying(), "an operator's /kill did not kill an eraser");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_reach_effects")
    public static void harmfulEffectsDoNothingInAnEraseAndComeBackWithTheirTimeRunOn(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-reach-effects");
        ServerPlayer player = scene.player;
        double plainSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        long[] givenAt = {0L};
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            givenAt[0] = helper.getLevel().getGameTime();
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 1));
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 2));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, MobEffectInstance.INFINITE_DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 0));
            helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < plainSpeed,
                    "Setup: Slowness does not slow the player");
            scene.press();
        }));
        helper.runAfterDelay(BEGIN, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            player.setHealth(player.getMaxHealth());
        }));
        helper.runAfterDelay(BEGIN + 80, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            for (var harmful : List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.MOVEMENT_SLOWDOWN,
                    MobEffects.BLINDNESS, MobEffects.WEAKNESS)) {
                scene.rule(!player.hasEffect(harmful),
                        harmful.getRegisteredName() + " is still on the eraser, where his client would show it");
            }
            scene.rule(player.getHealth() == player.getMaxHealth(),
                    "Poison or Wither went on hurting in the erase (health " + player.getHealth() + ")");
            // The erase's own speed is twice the plain one; Slowness III would have taken 45% off.
            scene.rule(player.getAttributeValue(Attributes.MOVEMENT_SPEED) >= plainSpeed * 1.9D,
                    "Slowness still slows the eraser: speed " + player.getAttributeValue(Attributes.MOVEMENT_SPEED));
            scene.rule(!player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0))
                            && !player.hasEffect(MobEffects.POISON),
                    "a new harmful effect was put on the eraser");
            scene.rule(player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 0)),
                    "a new beneficial effect was refused");
            MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
            scene.rule(strength != null && strength.getDuration() < 325 && strength.getDuration() > 305,
                    "a beneficial effect's time did not run on normally: " + strength);
            scene.fixture.release(player, KingCrimsonEraseFixture.ERASE_KEY);
            helper.assertTrue(!KingCrimsonEraseFixture.erasing(player), "Setup: letting the key go did not end the erase");
            int ran = (int) (helper.getLevel().getGameTime() - givenAt[0]);
            for (var back : List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.MOVEMENT_SLOWDOWN)) {
                MobEffectInstance effect = player.getEffect(back);
                scene.rule(effect != null && Math.abs(effect.getDuration() - (400 - ran)) <= 3,
                        back.getRegisteredName() + " did not come back with its time run on by " + ran + " ticks: "
                                + effect);
            }
            MobEffectInstance poison = player.getEffect(MobEffects.POISON);
            scene.rule(poison != null && poison.getAmplifier() == 1, "Poison II came back as " + poison);
            scene.rule(!player.hasEffect(MobEffects.BLINDNESS), "Blindness of 40 ticks came back after " + ran);
            MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
            scene.rule(weakness != null && weakness.isInfiniteDuration(), "an endless Weakness came back as " + weakness);
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_reach_blocks")
    public static void blocksNeitherHoldAnEraserBackNorFeelHim(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-reach-blocks");
        ServerPlayer player = scene.player;
        ServerLevel level = helper.getLevel();
        BlockPos plate = helper.absolutePos(new BlockPos(3, 2, 1));
        BlockPos ore = helper.absolutePos(new BlockPos(5, 1, 1));
        BlockPos farmland = helper.absolutePos(new BlockPos(7, 1, 1));
        BlockPos slime = helper.absolutePos(new BlockPos(9, 1, 1));
        BlockPos soulSand = helper.absolutePos(new BlockPos(11, 1, 1));
        BlockPos honey = helper.absolutePos(new BlockPos(3, 1, 4));
        BlockPos powderSnow = helper.absolutePos(new BlockPos(5, 2, 4));
        Map<String, BooleanSupplier> probes = new LinkedHashMap<>();
        probes.put("a pressure plate felt him", () -> {
            level.setBlock(plate, Blocks.STONE_PRESSURE_PLATE.defaultBlockState(), 3);
            standAt(player, Vec3.atBottomCenterOf(plate));
            player.move(MoverType.SELF, new Vec3(0.0D, -0.01D, 0.0D));
            return level.getBlockState(plate).getValue(PressurePlateBlock.POWERED);
        });
        probes.put("redstone ore lit under his step", () -> {
            level.setBlock(ore, Blocks.REDSTONE_ORE.defaultBlockState(), 3);
            standAt(player, Vec3.atBottomCenterOf(ore.above()));
            player.move(MoverType.SELF, new Vec3(0.0D, -0.01D, 0.0D));
            return level.getBlockState(ore).getValue(RedStoneOreBlock.LIT);
        });
        probes.put("farmland was trampled under him", () -> {
            level.setBlock(farmland, Blocks.FARMLAND.defaultBlockState(), 3);
            standAt(player, Vec3.atBottomCenterOf(farmland).add(0.0D, 0.9375D, 0.0D));
            player.fallDistance = 5.0F;
            player.doCheckFallDamage(0.0D, -1.0D, 0.0D, true);
            player.setHealth(player.getMaxHealth());
            return !level.getBlockState(farmland).is(Blocks.FARMLAND);
        });
        probes.put("a slime block bounced him", () -> {
            level.setBlock(slime, Blocks.SLIME_BLOCK.defaultBlockState(), 3);
            standAt(player, Vec3.atBottomCenterOf(slime.above()).add(0.0D, 0.3D, 0.0D));
            player.setDeltaMovement(0.0D, -1.0D, 0.0D);
            player.move(MoverType.SELF, new Vec3(0.0D, -0.5D, 0.0D));
            return player.getDeltaMovement().y > 0.0D;
        });
        probes.put("a cobweb held him", () -> {
            standAt(player, Vec3.atBottomCenterOf(plate.east().east().above(2)));
            Vec3 from = player.position();
            player.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), new Vec3(0.25D, 0.05D, 0.25D));
            player.move(MoverType.SELF, new Vec3(0.4D, 0.0D, 0.0D));
            return player.position().x - from.x < 0.2D;
        });
        probes.put("soul sand slowed him", () -> {
            level.setBlock(soulSand, Blocks.SOUL_SAND.defaultBlockState(), 3);
            standAt(player, Vec3.atBottomCenterOf(soulSand).add(0.0D, 0.875D, 0.0D));
            player.setDeltaMovement(0.5D, 0.0D, 0.0D);
            player.move(MoverType.SELF, new Vec3(0.0D, -0.01D, 0.0D));
            return player.getDeltaMovement().x < 0.3D;
        });
        probes.put("honey held his jump down", () -> {
            level.setBlock(honey, Blocks.HONEY_BLOCK.defaultBlockState(), 3);
            standAt(player, Vec3.atBottomCenterOf(honey).add(0.0D, 0.9375D, 0.0D));
            player.setDeltaMovement(Vec3.ZERO);
            player.jumpFromGround();
            return player.getDeltaMovement().y < 0.3D;
        });
        probes.put("powder snow took him in", () -> {
            level.setBlock(powderSnow, Blocks.POWDER_SNOW.defaultBlockState(), 3);
            standAt(player, Vec3.atBottomCenterOf(powderSnow));
            player.isInPowderSnow = false;
            player.move(MoverType.SELF, new Vec3(0.0D, -0.01D, 0.0D));
            return player.isInPowderSnow;
        });
        probes.put("block triggers read him", () -> !player.isIgnoringBlockTriggers());
        probes.put("sculk hears him", () -> !player.dampensVibrations());
        probes.put("a piston moves him", () -> player.getPistonPushReaction() != PushReaction.IGNORE);
        probes.put("fire takes on him", () -> {
            player.igniteForSeconds(5.0F);
            boolean burning = player.isOnFire();
            player.clearFire();
            return burning;
        });
        scene.controlThenErase(probes, new Vec3(1.5D, 2.0D, 1.5D));
    }

    @GameTest(template = "empty", timeoutTicks = 140, batch = "kc_reach_fluids")
    public static void waterAndLavaAreNotThereForAnEraser(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-reach-fluids");
        ServerPlayer player = scene.player;
        ServerLevel level = helper.getLevel();
        BlockPos water = helper.absolutePos(new BlockPos(4, 2, 1));
        BlockPos lava = helper.absolutePos(new BlockPos(8, 2, 1));
        Runnable pools = () -> {
            for (BlockPos pool : List.of(water, lava)) {
                BlockState fluid = pool == water ? Blocks.WATER.defaultBlockState() : Blocks.LAVA.defaultBlockState();
                for (BlockPos wall : List.of(pool.north(), pool.south(), pool.east(), pool.west())) {
                    level.setBlock(wall, Blocks.GLASS.defaultBlockState(), 3);
                    level.setBlock(wall.above(), Blocks.GLASS.defaultBlockState(), 3);
                }
                level.setBlock(pool, fluid, 3);
                level.setBlock(pool.above(), fluid, 3);
            }
        };
        int[] airBefore = {0};
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            pools.run();
            standAt(player, Vec3.atBottomCenterOf(water));
        }));
        helper.runAfterDelay(12, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(player.isInWater() && player.isPushedByFluid() && player.getAirSupply() < player.getMaxAirSupply(),
                    "Setup: with no erase a player under water is in it and loses air: in water " + player.isInWater()
                            + ", air " + player.getAirSupply());
            standAt(player, Vec3.atBottomCenterOf(lava));
        }));
        helper.runAfterDelay(16, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(player.isInLava() && player.getRemainingFireTicks() > 0
                            && player.getHealth() < player.getMaxHealth(),
                    "Setup: with no erase lava burns: in lava " + player.isInLava() + ", fire "
                            + player.getRemainingFireTicks() + ", health " + player.getHealth());
            standAt(player, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
            player.clearFire();
            player.setHealth(player.getMaxHealth());
            player.setAirSupply(player.getMaxAirSupply());
            player.invulnerableTime = 0;
            scene.press();
        }));
        helper.runAfterDelay(16 + BEGIN, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            standAt(player, Vec3.atBottomCenterOf(water));
            airBefore[0] = player.getAirSupply();
        }));
        helper.runAfterDelay(16 + BEGIN + 30, () -> scene.fixture.guarded(() -> {
            scene.rule(!player.isInWater(), "water is there for an eraser's movement");
            scene.rule(!player.isPushedByFluid(), "a current pushes an eraser");
            scene.rule(player.getAirSupply() >= airBefore[0],
                    "an eraser lost air under water: " + airBefore[0] + " to " + player.getAirSupply());
            standAt(player, Vec3.atBottomCenterOf(lava));
        }));
        helper.runAfterDelay(16 + BEGIN + 50, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            scene.rule(!player.isInLava(), "lava is there for an eraser's movement");
            scene.rule(player.getRemainingFireTicks() <= 0 && !player.isOnFire(),
                    "lava set an eraser on fire: " + player.getRemainingFireTicks());
            scene.rule(player.getHealth() == player.getMaxHealth(), "lava hurt an eraser: " + player.getHealth());
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_reach_entities")
    public static void towardOtherEntitiesAnEraserIsAsASpectator(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-reach-entities");
        ServerPlayer player = scene.player;
        ServerLevel level = helper.getLevel();
        Vec3 home = player.position();
        Map<String, BooleanSupplier> probes = new LinkedHashMap<>();
        probes.put("he pushed a pig, or it him", () -> {
            Pig pig = scene.fixture.track(EntityType.PIG.create(level));
            pig.setNoAi(true);
            pig.setPos(home.x + 0.2D, home.y, home.z);
            level.addFreshEntity(pig);
            // A pig without AI does not travel: a push shows in its motion.
            pig.setDeltaMovement(Vec3.ZERO);
            for (int tick = 0; tick < 5; tick++) {
                pig.tick();
                player.doTick();
            }
            boolean pushed = pig.getDeltaMovement().horizontalDistanceSqr() > 1.0E-8D;
            pig.discard();
            return pushed;
        });
        probes.put("an arrow hit him", () -> {
            Arrow arrow = scene.fixture.track(new Arrow(level, home.x - 3.0D, home.y + 1.0D, home.z,
                    new ItemStack(Items.ARROW), null));
            arrow.setDeltaMovement(1.5D, 0.0D, 0.0D);
            level.addFreshEntity(arrow);
            for (int tick = 0; tick < 6; tick++) {
                arrow.tick();
            }
            boolean passed = arrow.isAlive() && arrow.getX() > home.x + 2.0D;
            arrow.discard();
            return !passed;
        });
        probes.put("he took an arrow from the ground", () -> {
            Arrow arrow = scene.fixture.track(new Arrow(level, home.x, home.y + 0.2D, home.z,
                    new ItemStack(Items.ARROW), null));
            arrow.pickup = AbstractArrow.Pickup.ALLOWED;
            arrow.setNoPhysics(true);
            arrow.setNoGravity(true);
            level.addFreshEntity(arrow);
            player.doTick();
            boolean taken = !arrow.isAlive();
            arrow.discard();
            player.getInventory().clearContent();
            return taken;
        });
        probes.put("he picked up an item", () -> {
            ItemEntity item = scene.fixture.track(new ItemEntity(level, home.x, home.y + 0.2D, home.z,
                    new ItemStack(Items.DIAMOND)));
            item.setNoPickUpDelay();
            item.setNoGravity(true);
            level.addFreshEntity(item);
            player.doTick();
            boolean taken = !item.isAlive();
            item.discard();
            player.getInventory().clearContent();
            return taken;
        });
        probes.put("he took an experience orb", () -> {
            ExperienceOrb orb = scene.fixture.track(new ExperienceOrb(level, home.x, home.y + 0.2D, home.z, 3));
            level.addFreshEntity(orb);
            player.takeXpDelay = 0;
            player.doTick();
            boolean taken = !orb.isAlive();
            orb.discard();
            return taken;
        });
        probes.put("a knockback moved him", () -> {
            player.setDeltaMovement(Vec3.ZERO);
            player.knockback(1.0D, 1.0D, 0.0D);
            boolean moved = player.getDeltaMovement().lengthSqr() > 1.0E-6D;
            player.setDeltaMovement(Vec3.ZERO);
            return moved;
        });
        probes.put("an explosion threw him", () -> {
            player.setDeltaMovement(Vec3.ZERO);
            player.invulnerableTime = 0;
            level.explode(null, home.x + 1.5D, home.y + 0.5D, home.z, 3.0F, Level.ExplosionInteraction.NONE);
            boolean thrown = player.getDeltaMovement().lengthSqr() > 1.0E-6D;
            player.setDeltaMovement(Vec3.ZERO);
            player.setHealth(player.getMaxHealth());
            return thrown;
        });
        probes.put("he mounted a pig", () -> {
            Pig pig = scene.fixture.track(EntityType.PIG.create(level));
            pig.setNoAi(true);
            pig.setPos(home.x + 2.0D, home.y, home.z);
            level.addFreshEntity(pig);
            boolean mounted = player.startRiding(pig, true);
            player.stopRiding();
            pig.discard();
            player.setPos(home);
            return mounted;
        });
        probes.put("an attack can be aimed at him", player::isAttackable);
        probes.put("a crosshair picks him", player::isPickable);
        probes.put("a crosshair picks his Stand", () -> scene.stand.isPickable());
        probes.put("mobs and sensors see him", player::canBeSeenByAnyone);
        probes.put("a husk beside him took him as its target", () -> {
            Husk husk = scene.fixture.track(EntityType.HUSK.create(level));
            // Close enough to see an invisible player without armour, as vanilla's own invisibility leaves him.
            husk.setPos(home.x + 1.6D, home.y, home.z);
            level.addFreshEntity(husk);
            for (int tick = 0; tick < 300 && husk.getTarget() != player; tick++) {
                husk.tick();
            }
            boolean targeted = husk.getTarget() == player;
            husk.discard();
            return targeted;
        });
        scene.controlThenErase(probes, new Vec3(1.5D, 2.0D, 1.5D));
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "kc_reach_mount")
    public static void ridingAtThePressTheEraserLeavesTheMountAndTheDecoyTakesHisSeat(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-reach-mount");
        ServerPlayer player = scene.player;
        Pig pig = scene.fixture.track(EntityType.PIG.create(helper.getLevel()));
        pig.setNoAi(true);
        pig.setPos(player.position());
        helper.getLevel().addFreshEntity(pig);
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(player.startRiding(pig, true), "Setup: the player did not mount");
            scene.press();
        }));
        helper.runAfterDelay(BEGIN, () -> scene.fixture.guarded(() -> {
            scene.assertErasing();
            List<KingCrimsonTimeEraseDecoyEntity> decoys = scene.fixture.decoys(player);
            helper.assertTrue(decoys.size() == 1, "Setup: the erase left " + decoys.size() + " decoys");
            scene.rule(!player.isPassenger(), "the eraser is still riding");
            scene.rule(decoys.get(0).getVehicle() == pig, "the decoy did not take his seat: it rides "
                    + decoys.get(0).getVehicle());
            scene.finish();
        }));
    }

    private static void standAt(ServerPlayer player, Vec3 at) {
        player.setPos(at);
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(false);
        player.fallDistance = 0.0F;
    }

    /** Magic damage that carries one of the core's piercing tags, as another add-on's damage type would through a data file. */
    private record PiercingType(Holder<DamageType> type, TagKey<DamageType> carried) implements Holder<DamageType> {
        @Override
        public DamageType value() {
            return type.value();
        }

        @Override
        public boolean isBound() {
            return type.isBound();
        }

        @Override
        public boolean is(ResourceLocation location) {
            return type.is(location);
        }

        @Override
        public boolean is(ResourceKey<DamageType> key) {
            return type.is(key);
        }

        @Override
        public boolean is(Predicate<ResourceKey<DamageType>> predicate) {
            return type.is(predicate);
        }

        @Override
        public boolean is(TagKey<DamageType> tag) {
            return tag == carried || type.is(tag);
        }

        @Override
        public boolean is(Holder<DamageType> holder) {
            return type.is(holder);
        }

        @Override
        public Stream<TagKey<DamageType>> tags() {
            return Stream.concat(type.tags(), Stream.of(carried));
        }

        @Override
        public Either<ResourceKey<DamageType>, DamageType> unwrap() {
            return type.unwrap();
        }

        @Override
        public Optional<ResourceKey<DamageType>> unwrapKey() {
            return type.unwrapKey();
        }

        @Override
        public Kind kind() {
            return type.kind();
        }

        @Override
        public boolean canSerializeIn(HolderOwner<DamageType> owner) {
            return type.canSerializeIn(owner);
        }
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final ServerPlayer player;
        private final StandPower power;
        private final StandEntity stand;
        private final List<String> broken = new ArrayList<>();
        private long startedAt;

        private Scene(GameTestHelper helper, String name) {
            this.helper = helper;
            fixture = new KingCrimsonEraseFixture(helper);
            fixture.floor(13, 6);
            player = fixture.player(name, 1.5D, 2.0D, 1.5D);
            power = fixture.kingCrimson(player, 4);
            // The probes hit him with no erase running, to show that they do hit: the passive must not answer them.
            boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
            KingCrimsonConfig.REFLEX_ERASE.set(false);
            fixture.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(reflex));
            stand = power.getSummonedStandEntity();
            helper.onEachTick(() -> {
                if (!player.isRemoved()) {
                    power.setStamina(power.getMaxStamina());
                }
            });
        }

        private void press() {
            helper.assertTrue(fixture.holdErase(player).isActionSuccess, "Setup: the held Time Erase did not start");
        }

        private void assertErasing() {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(player), "Setup: the held key began no erase");
            startedAt = KingCrimsonEraseFixture.state(player).startedAt();
        }

        private void rule(boolean holds, String broke) {
            if (!holds) {
                broken.add(broke);
            }
        }

        /** Every probe must find the world acting on the player with no erase, and not acting in one. */
        private void controlThenErase(Map<String, BooleanSupplier> probes, Vec3 home) {
            Vec3 at = helper.absoluteVec(home);
            helper.runAfterDelay(2, () -> fixture.guarded(() -> {
                List<String> idle = new ArrayList<>();
                for (Map.Entry<String, BooleanSupplier> probe : probes.entrySet()) {
                    if (!probe.getValue().getAsBoolean()) {
                        idle.add(probe.getKey());
                    }
                    player.setHealth(player.getMaxHealth());
                    player.invulnerableTime = 0;
                }
                helper.assertTrue(idle.isEmpty(), "Setup: with no erase these did not happen: " + idle);
                // The decoy stays where the key was pressed, away from what the probes do.
                standAt(player, helper.absoluteVec(new Vec3(11.5D, 2.0D, 5.5D)));
                player.setOnGround(true);
                press();
            }));
            helper.runAfterDelay(2 + BEGIN, () -> fixture.guarded(() -> {
                assertErasing();
                standAt(player, at);
                for (Map.Entry<String, BooleanSupplier> probe : probes.entrySet()) {
                    rule(!probe.getValue().getAsBoolean(), probe.getKey());
                    helper.assertTrue(KingCrimsonTemporalStateService.isErasing(player),
                            "Setup: the erase ended at the probe '" + probe.getKey() + "'");
                }
                finish();
            }));
        }

        private void finish() {
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(), "In a Time Erase: " + String.join("; ", broken)));
            fixture.succeed();
        }
    }
}
