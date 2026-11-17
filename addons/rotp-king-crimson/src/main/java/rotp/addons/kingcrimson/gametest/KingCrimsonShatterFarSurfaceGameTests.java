package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.Cell;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.Outcome;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.RingRead;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.RingSpec;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.RingStats;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarSurface;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarSurface.NearFootprint;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarSurface.SpriteAverage;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonShatterFarSurfaceGameTests {
    private KingCrimsonShatterFarSurfaceGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_far")
    public static void farSurfaceClipsExactNearColumns(GameTestHelper helper) {
        helper.assertTrue(KingCrimsonShatterFarSurface.knownGroundTop(64.0F, 64) == Float.POSITIVE_INFINITY
                        && KingCrimsonShatterFarSurface.knownGroundTop(64.0F, 63) == 64.0F
                        && KingCrimsonShatterFarSurface.knownGroundTop(64.5F, 64) == 64.5F,
                "Only a block above the sampled ground, not its own full block or slab, makes the near height unknown");
        Cell cell = new Cell(-2, -2, 12.5F, 7, 2);
        NearFootprint near = new NearFootprint(-2, -2, 0, 0,
                (x, z) -> x != -2 ? Float.NaN : z == -2 ? 11.0F : Float.POSITIVE_INFINITY);
        var surface = new KingCrimsonShatterFarSurface(List.of(read(new RingSpec(0, 16, 1), cell)), near, () -> false);
        List<Face> faces = collect(surface, cell, 2);
        List<Face> tops = faces.stream().filter(face -> face.direction == Direction.UP).toList();
        helper.assertTrue(tops.equals(List.of(new Face(cell, Direction.UP, -1, -2, 0, -1, 12.5F),
                        new Face(cell, Direction.UP, -1, -1, 0, 0, 12.5F))),
                "Far tops cover only the unowned columns, even when a near column's height is unknown");
        helper.assertTrue(faces.stream().filter(face -> face.direction != Direction.UP).toList()
                        .equals(List.of(new Face(cell, Direction.WEST, -1, -2, -1, -1, 11.0F))),
                "A clipped interior has a wall only against the known lower near column");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_far")
    public static void farSurfaceMatchesMixedLodWallsToTheirOwner(GameTestHelper helper) {
        Cell coarse = new Cell(-2, 6, 10.5F, 17, 4);
        Cell low = new Cell(0, 6, 8.25F, 31, 6);
        Cell same = new Cell(0, 7, 10.5F, 59, 6);
        var surface = new KingCrimsonShatterFarSurface(List.of(read(new RingSpec(0, 16, 1), coarse),
                read(new RingSpec(16, 33, 0), low, same)), NearFootprint.EMPTY, () -> false);
        helper.assertTrue(collect(surface, coarse, 2).equals(List.of(
                        new Face(coarse, Direction.UP, -2, 6, 0, 8, 10.5F),
                        new Face(coarse, Direction.EAST, 0, 6, 0, 7, 8.25F))),
                "A coarse wall spans exactly its lower fine neighbor and keeps the higher cell's own face material");
        helper.assertTrue(collect(surface, low, 1).equals(List.of(new Face(low, Direction.UP, 0, 6, 1, 7, 8.25F))),
                "A lower cell cannot draw a wall into the higher cell, and unknown neighbors create no walls");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_far")
    public static void farMaterialUsesAlphaTintAndOwnMapFallback(GameTestHelper helper) {
        SpriteAverage average = new SpriteAverage();
        average.add(0x0000FF00);
        helper.assertTrue(average.rgb() == -1, "A fully transparent face has no usable sprite average");
        average.add(0xFF0000FF);
        average.add(0x80FF0000);
        helper.assertTrue(average.rgb() == 0xA90055, "ABGR samples are weighted by alpha, with no weight for transparent pixels");
        helper.assertTrue(KingCrimsonShatterFarSurface.faceColor(average.rgb(), 0x8080FF, 0x204060, 0.5F) == 0xFF2A002A,
                "Supported faces use their sprite, tint and directional shade instead of the fallback");
        helper.assertTrue(KingCrimsonShatterFarSurface.faceColor(-1, 0, 0x204060, 0.5F) == 0xFF102030,
                "An unsupported face uses the owner's map color without a missing sprite tint");
        SpriteAverage grass = new SpriteAverage();
        grass.addLayered(0xFF0000FF, 0xFFFFFF, 0x00808080, 0x40FF00);
        grass.addLayered(0xFFFF0000, 0xFFFFFF, 0xFF808080, 0x40FF00);
        helper.assertTrue(grass.rgb() == 0x8F4000,
                "Transparent overlay texels keep the matching base texels; opaque ones replace them with biome-tinted color");
        helper.succeed();
    }

    private static RingRead read(RingSpec spec, Cell... cells) {
        return new RingRead(spec, List.of(cells), new RingStats(0, 0, 0, 0, cells.length, cells.length, 0),
                Outcome.COMPLETE, null);
    }

    private static List<Face> collect(KingCrimsonShatterFarSurface surface, Cell cell, int size) {
        var faces = new ArrayList<Face>();
        surface.emit(cell, size, (own, direction, x0, z0, x1, z1, bottom) ->
                faces.add(new Face(own, direction, x0, z0, x1, z1, bottom)));
        return faces;
    }

    private record Face(Cell cell, Direction direction, int x0, int z0, int x1, int z1, float bottom) {}
}
