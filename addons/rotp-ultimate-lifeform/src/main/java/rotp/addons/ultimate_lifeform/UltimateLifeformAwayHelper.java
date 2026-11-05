package rotp.addons.ultimate_lifeform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/**
 * A test's helper whose template stands AWAY blocks west of its place in the grid (and LANE more per lane), beyond the
 * grid and the scale-hit scenes 256 blocks west of it, so that no chunk of the framework or of another test reaches it.
 * There the template's corner lies on block 8 of a chunk in x and z: every block of the 8-block template is in one
 * chunk and whatever a scene puts 8 or more blocks out is in the next ones, the layout of a grid place whose origin
 * lies on block 8. Only tickets the scene holds itself make those chunks tick entities and their entities found.
 */
final class UltimateLifeformAwayHelper extends GameTestHelper {
    static final int AWAY = 512;
    // far enough that the chunks one lane's scene loads or holds never reach another lane's, whatever their columns
    static final int LANE = 256;
    private final BlockPos shift;

    UltimateLifeformAwayHelper(GameTestHelper helper, int lane) {
        super(helper.testInfo);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int x = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX() - AWAY - LANE * lane)) + 8;
        int z = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 8;
        shift = new BlockPos(x - origin.getX(), 0, z - origin.getZ());
    }

    @Override
    public BlockPos absolutePos(BlockPos pos) {
        return super.absolutePos(pos).offset(shift);
    }

    @Override
    public Vec3 absoluteVec(Vec3 pos) {
        return super.absoluteVec(pos).add(shift.getX(), shift.getY(), shift.getZ());
    }

    @Override
    public BlockPos relativePos(BlockPos pos) {
        return super.relativePos(pos.subtract(shift));
    }

    @Override
    public Vec3 relativeVec(Vec3 pos) {
        return super.relativeVec(pos.subtract(shift.getX(), shift.getY(), shift.getZ()));
    }
}
