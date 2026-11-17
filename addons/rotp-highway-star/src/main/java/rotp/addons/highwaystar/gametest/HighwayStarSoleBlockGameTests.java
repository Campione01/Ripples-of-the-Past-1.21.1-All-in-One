package rotp.addons.highwaystar.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModGamerules;

@GameTestHolder(HighwayStarAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HighwayStarSoleBlockGameTests {
    private static final String NAMESPACE = HighwayStarAddon.MOD_ID;
    private static final int PHASE_TICKS = 50;

    private HighwayStarSoleBlockGameTests() {}

    /**
     * R772.1 OQ-B01: a sole that chases through a wall breaks it only while jojoAbilitiesBreakBlocks is on and its
     * owner's "breaks blocks" Stand switch is on.
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 200)
    public static void soleBreaksTheWallOnlyWhereTheRuleAndTheOwnersSwitchAllow(GameTestHelper helper) {
        RoomTestScene.run(helper, s -> {
            ServerLevel level = s.level;
            boolean ruleBefore = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
            ServerPlayer owner = s.owner();
            PlayerClientBroadcastedSettings switches =
                    owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
            BlockPos floor = helper.absolutePos(new BlockPos(0, 30, 0));
            for (int x = 0; x <= 8; x++) {
                for (int z = 0; z <= 4; z++) {
                    s.setBlock(floor.offset(x, 0, z), Blocks.STONE);
                }
            }
            // a corridor one block high: under a ceiling the sole cannot climb the wall and has to break it
            BlockPos wall = floor.offset(4, 1, 1);
            for (int z = 0; z <= 3; z++) {
                s.setBlock(floor.offset(4, 1, z), Blocks.STONE);
                for (int x = 0; x <= 5; x++) {
                    s.setBlock(floor.offset(x, 2, z), Blocks.STONE);
                }
            }
            // A husk: the test world is always at morning, and a zombie past the ceiling caught fire in the sun at random.
            // Fire masks its scent, so the sole let it go after 100 ticks and, its Stand not out, was gone.
            Zombie prey = s.spawn(EntityType.HUSK, Vec3.atBottomCenterOf(floor.offset(7, 1, 1)));
            HighwayStarSoleEntity sole = s.spawn(HighwayStarAddon.SOLE_ENTITY.get(),
                    Vec3.atBottomCenterOf(floor.offset(1, 1, 1)));
            sole.setNoAi(false);
            sole.setNoGravity(false);
            sole.setOwnerUUID(owner.getUUID());
            sole.attachTo(prey.getUUID(), 0);
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(false, level.getServer());
            s.after(PHASE_TICKS, () -> {
                restoreRuleOnFailure(s, level, ruleBefore, () -> {
                    checkChase(s, sole, prey);
                    s.check(level.getBlockState(wall).is(Blocks.STONE),
                            "A sole broke the wall with jojoAbilitiesBreakBlocks off");
                    level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(true, level.getServer());
                    switches.standBreaksBlocks = false;
                });
                s.after(PHASE_TICKS, () -> {
                    restoreRuleOnFailure(s, level, ruleBefore, () -> {
                        checkChase(s, sole, prey);
                        s.check(level.getBlockState(wall).is(Blocks.STONE),
                                "A sole broke the wall with its owner's Stand switch off");
                        switches.standBreaksBlocks = true;
                    });
                    s.after(PHASE_TICKS + 20, () -> {
                        level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(ruleBefore, level.getServer());
                        s.check(!prey.isOnFire(), "Fixture: the prey caught fire, which masks its scent from the sole");
                        s.check(level.getBlockState(wall).isAir(),
                                "With the rule and the switch on a sole must break the wall that bars its way: sole at "
                                        + sole.position() + " alive=" + sole.isAlive() + " wall at " + wall
                                        + " floor " + floor + " collided=" + sole.horizontalCollision);
                        sole.discard();
                        s.finish();
                    });
                });
            });
        });
    }

    // the phases before the last one need the sole still on the prey's trail behind the wall
    private static void checkChase(RoomTestScene s, HighwayStarSoleEntity sole, Zombie prey) {
        s.check(!prey.isOnFire(), "Fixture: the prey caught fire, which masks its scent from the sole");
        s.check(sole.isAlive(), "Fixture: the sole was gone before the rule and the switch were on");
    }

    private static void restoreRuleOnFailure(RoomTestScene s, ServerLevel level, boolean ruleBefore, Runnable body) {
        try {
            body.run();
        }
        catch (RuntimeException | Error failure) {
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(ruleBefore, level.getServer());
            throw failure;
        }
    }
}
