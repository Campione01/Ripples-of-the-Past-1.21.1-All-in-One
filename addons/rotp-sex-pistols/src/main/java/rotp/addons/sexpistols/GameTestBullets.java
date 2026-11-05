package rotp.addons.sexpistols;

import java.util.UUID;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;

/**
 * Discards the revolver bullets of a fixture's shooter when its test ends, however it ends (passed, failed, timed out or
 * rerun): a shot that missed would otherwise fly on through the neighbouring test plots.
 */
final class GameTestBullets {
    private GameTestBullets() {}

    static void discardAtEnd(GameTestHelper helper, ServerPlayer shooter) {
        helper.testInfo.addListener(new Sweep(helper.getLevel(), shooter.getUUID()));
    }

    private record Sweep(ServerLevel level, UUID shooter) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            RevolverBulletEntity.discardOwnedBy(level, shooter);
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            RevolverBulletEntity.discardOwnedBy(level, shooter);
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            RevolverBulletEntity.discardOwnedBy(level, shooter);
        }
    }
}
