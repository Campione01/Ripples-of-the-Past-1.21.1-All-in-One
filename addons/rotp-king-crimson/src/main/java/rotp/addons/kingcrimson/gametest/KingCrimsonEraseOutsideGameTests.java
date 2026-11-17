package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.state.KingCrimsonEraseOutsideRules;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * What the erasing user is shown of the world outside his erase beside its creatures (EV1105AC section 7, "投射物/方塊實體
 * 運動的效果"): the rules his client draws by, asked of real entities. The drawing itself is his client's and is not
 * checked here.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEraseOutsideGameTests {
    private KingCrimsonEraseOutsideGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_outside_rules")
    public static void theEraserIsShownWhatMovesOutsideHisEraseAndTheSkillsAtWorkThereButNothingOfHisOwn(
            GameTestHelper helper) {
        KingCrimsonEraseFixture fixture = new KingCrimsonEraseFixture(helper);
        fixture.floor(8, 6);
        ServerLevel level = helper.getLevel();
        ServerPlayer eraser = fixture.player("kc-outside", 2.5D, 2.0D, 2.5D);
        StandPower power = fixture.kingCrimson(eraser, 4);
        StandEntity stand = power.getSummonedStandEntity();
        List<String> broken = new ArrayList<>();
        fixture.guarded(() -> {
            Skeleton skeleton = EntityType.SKELETON.create(level);
            Pig pig = EntityType.PIG.create(level);
            ItemStack arrow = new ItemStack(Items.ARROW);
            Entity[] shown = {
                new Arrow(level, skeleton, arrow, null),
                new Snowball(level, skeleton),
                EntityType.FALLING_BLOCK.create(level),
                EntityType.TNT.create(level),
                EntityType.EVOKER_FANGS.create(level),
                EntityType.SMALL_FIREBALL.create(level)};
            for (Entity entity : shown) {
                rule(broken, KingCrimsonEraseOutsideRules.isShownMoving(entity, eraser), "the eraser is not shown "
                        + name(entity) + " in motion");
                rule(broken, KingCrimsonEraseOutsideRules.isEffectSource(entity, eraser), "what happens by "
                        + name(entity) + " is not shown as a skill at work");
            }
            Entity[] own = {new Arrow(level, eraser, arrow, null), new Snowball(level, stand)};
            for (Entity entity : own) {
                rule(broken, !KingCrimsonEraseOutsideRules.isShownMoving(entity, eraser)
                                && !KingCrimsonEraseOutsideRules.isEffectSource(entity, eraser),
                        "the eraser is shown his own " + name(entity) + " as a thing of the outside world");
            }
            Entity[] still = {
                new ItemEntity(level, 0.0D, 0.0D, 0.0D, arrow),
                EntityType.EXPERIENCE_ORB.create(level),
                EntityType.ITEM_FRAME.create(level),
                EntityType.MINECART.create(level)};
            for (Entity entity : still) {
                rule(broken, !KingCrimsonEraseOutsideRules.isShownMoving(entity, eraser)
                                && !KingCrimsonEraseOutsideRules.isEffectSource(entity, eraser),
                        name(entity) + " is shown as a moving thing of the outside world");
            }
            // Creatures have the afterimages the server gives; what happens by them is a skill at work.
            rule(broken, !KingCrimsonEraseOutsideRules.isShownMoving(pig, eraser)
                    && KingCrimsonEraseOutsideRules.isEffectSource(pig, eraser), "a pig: a moving thing "
                    + KingCrimsonEraseOutsideRules.isShownMoving(pig, eraser) + ", a source of effects "
                    + KingCrimsonEraseOutsideRules.isEffectSource(pig, eraser));
            rule(broken, !KingCrimsonEraseOutsideRules.isEffectSource(eraser, eraser)
                    && !KingCrimsonEraseOutsideRules.isEffectSource(stand, eraser),
                    "the eraser or his Stand is a source of the outside world's effects");
            // A tick lets 48 particles through and no more.
            KingCrimsonEraseOutsideRules.Budget budget = new KingCrimsonEraseOutsideRules.Budget();
            int taken = 0;
            while (taken < 1000 && budget.take()) {
                taken++;
            }
            boolean spent = !budget.hasLeft();
            budget.refill();
            rule(broken, taken == 48 && spent && budget.take(), "a tick's allowance let " + taken
                    + " particles through (spent after them: " + spent + "), not 48, or did not refill");
            helper.assertTrue(broken.isEmpty(), "The eraser's sight of the outside: " + String.join("; ", broken));
        });
        fixture.succeed();
    }

    private static void rule(List<String> broken, boolean holds, String broke) {
        if (!holds) {
            broken.add(broke);
        }
    }

    private static String name(Entity entity) {
        return EntityType.getKey(entity.getType()).getPath();
    }
}
