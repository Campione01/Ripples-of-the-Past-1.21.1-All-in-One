package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.config.client.StandToggle;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModGamerules;

/**
 * Owner (OFFB): what a Stand breaks follows its user's "breaks blocks" switch and the jojoAbilitiesBreakBlocks rule. The
 * one thing King Crimson breaks blocks with is a thrown fire charge, a ghast's fireball. Thrown by the real ability at a
 * glass wall half a block ahead of where it bursts: with the switch and the rule on the wall loses glass; with either
 * off the fireball still bursts there and the wall is whole.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonThrowBlockSwitchGameTests {
    private KingCrimsonThrowBlockSwitchGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 800, batch = "kc_throw_block_switch")
    public static void aThrownFireChargeBreaksBlocksOnlyWhereItsUsersStandMay(GameTestHelper helper) {
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowBlockSwitchGameTests.class);
        var suite = new SwitchSuite(helper);
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    private static final class SwitchSuite extends KingCrimsonThrowGameTestFixture.Suite {
        enum Kind { ALLOWED, SWITCH_OFF, RULE_OFF }
        final boolean oldRule;
        final List<BlockPos> wall = new ArrayList<>();
        Kind kind;
        int bursts;
        int taken;

        SwitchSuite(GameTestHelper helper) {
            super(helper, "KC-THROW-BLOCK-SWITCH", Kind.values().length);
            oldRule = helper.getLevel().getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
        }

        @Override void prepare(int index) {
            kind = Kind.values()[index];
            bursts = 0;
            taken = 0;
            scene = new KingCrimsonThrowGameTestFixture(helper, "block-switch/" + kind,
                    KingCrimsonThrowGameTestFixture.Mode.FINITE, 600, 73);
            // The fire charge leaves his eye at 3 blocks a tick: after one tick it is half a block before this wall.
            BlockPos base = BlockPos.containing(scene.origin);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 3; dy++) {
                    BlockPos pos = base.offset(dx, dy, 4);
                    scene.premise(scene.level.getBlockState(pos).isAir(), "The wall's place is free");
                    scene.level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
                    wall.add(pos);
                }
            }
            scene.level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(kind != Kind.RULE_OFF, scene.level.getServer());
            scene.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standBreaksBlocks = kind != Kind.SWITCH_OFF;
            scene.premise(scene.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                            && scene.level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS) == (kind != Kind.RULE_OFF)
                            && PlayerClientBroadcastedSettings.isStandToggleOn(StandToggle.BREAK_BLOCKS, scene.user)
                                    == (kind != Kind.SWITCH_OFF),
                    "mobGriefing is on, the Stand rule and the user's switch are as the scene says");
            scene.listen(ExplosionEvent.Detonate.class, event -> {
                if (event.getExplosion().getDirectSourceEntity() instanceof LargeFireball fireball
                        && fireball.getOwner() == scene.user) {
                    bursts++;
                    taken += event.getAffectedBlocks().size();
                }
            });
            scene.aim(0.0F, 0.0F);
            scene.hand(new ItemStack(Items.FIRE_CHARGE, 2));
            var press = scene.press(KingCrimsonThrowGameTestFixture.SINGLE, KingCrimsonThrowGameTestFixture.FIRST);
            scene.premise(press.conditions() && press.admitted() && !press.buffered(), "Fire charge throw admitted");
        }

        @Override boolean verify() {
            if (!scene.performed || scene.spawns.isEmpty() || !scene.spawns.get(0).entity().isRemoved()) {
                return false;
            }
            scene.premise(scene.spawns.size() == 1 && scene.spawns.get(0).entity() instanceof LargeFireball && bursts == 1,
                    "One fire charge was thrown and burst once: " + scene.spawns.size() + " thrown, " + bursts + " bursts");
            int broken = 0;
            for (BlockPos pos : wall) {
                if (!scene.level.getBlockState(pos).is(Blocks.GLASS)) {
                    broken++;
                }
            }
            if (kind == Kind.ALLOWED) {
                checks.check(broken > 0 && taken > 0, scene.id + ": with the switch and the rule on the burst took " + taken
                        + " blocks and the wall lost " + broken + " of its " + wall.size());
            } else {
                checks.check(broken == 0 && taken == 0, scene.id + ": where the Stand may not break blocks the burst took "
                        + taken + " blocks and the wall lost " + broken + " of its " + wall.size());
            }
            clearWall();
            return true;
        }

        private void clearWall() {
            for (BlockPos pos : wall) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            wall.clear();
        }

        @Override void release() {
            clearWall();
            helper.getLevel().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(oldRule, helper.getLevel().getServer());
        }
    }
}
