package rotp.addons.d4c.travel;

import java.lang.reflect.Field;
import java.util.Arrays;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * Whether two chunk sections hold the same block states, cheaply, without ever calling two different sections
 * equal. EQUAL is only answered from the containers' own palettes and packed data: the same entries in the same
 * order with the same packed longs, or, when the entries are the same set in another order, every index mapped
 * to the same entry. Anything else is UNSURE and the caller compares block by block.
 */
final class D4CSectionCompare {
    enum Result { EQUAL, UNSURE }

    // a palette larger than this is rare and costs more to map than to scan
    private static final int MAPPED_PALETTE_LIMIT = 256;
    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private static final Field DATA;
    @Nullable
    private static final Field STORAGE;
    @Nullable
    private static final Field PALETTE;

    static {
        Field data = null;
        Field storage = null;
        Field palette = null;
        try {
            data = PalettedContainer.class.getDeclaredField("data");
            data.setAccessible(true);
            Class<?> record = data.getType();
            storage = record.getDeclaredField("storage");
            storage.setAccessible(true);
            palette = record.getDeclaredField("palette");
            palette.setAccessible(true);
        }
        catch (ReflectiveOperationException | RuntimeException unreadable) {
            LOGGER.warn("D4C parallel copy compares every block: the section data cannot be read", unreadable);
            data = null;
        }
        DATA = data;
        STORAGE = data != null ? storage : null;
        PALETTE = data != null ? palette : null;
    }

    private D4CSectionCompare() {}

    static boolean available() {
        return DATA != null;
    }

    static Result compare(LevelChunkSection source, LevelChunkSection target) {
        if (source.hasOnlyAir() && target.hasOnlyAir()) {
            return Result.EQUAL;
        }
        if (DATA == null) {
            return Result.UNSURE;
        }
        try {
            Object sourceData = DATA.get(source.getStates());
            Object targetData = DATA.get(target.getStates());
            return compare(
                    (Palette<?>) PALETTE.get(sourceData), (BitStorage) STORAGE.get(sourceData),
                    (Palette<?>) PALETTE.get(targetData), (BitStorage) STORAGE.get(targetData));
        }
        catch (IllegalAccessException | RuntimeException unreadable) {
            return Result.UNSURE;
        }
    }

    // the containers are read as they are; the copy runs on the server thread, the only writer of loaded chunks
    static Result compare(Palette<?> sourcePalette, BitStorage sourceStorage,
            Palette<?> targetPalette, BitStorage targetStorage) {
        if (sourceStorage.getSize() != targetStorage.getSize()) {
            return Result.UNSURE;
        }
        boolean sourceGlobal = sourcePalette instanceof GlobalPalette;
        boolean targetGlobal = targetPalette instanceof GlobalPalette;
        if (sourceGlobal || targetGlobal) {
            // a global palette's index is the state's id: equal packed data is equal states
            return sourceGlobal && targetGlobal
                    && sourceStorage.getBits() == targetStorage.getBits()
                    && Arrays.equals(sourceStorage.getRaw(), targetStorage.getRaw())
                    ? Result.EQUAL
                    : Result.UNSURE;
        }
        int size = sourcePalette.getSize();
        if (size != targetPalette.getSize()) {
            return Result.UNSURE;
        }
        boolean sameOrder = sourceStorage.getBits() == targetStorage.getBits();
        for (int index = 0; sameOrder && index < size; index++) {
            sameOrder = sourcePalette.valueFor(index) == targetPalette.valueFor(index);
        }
        if (sameOrder) {
            return Arrays.equals(sourceStorage.getRaw(), targetStorage.getRaw())
                    ? Result.EQUAL
                    : Result.UNSURE;
        }
        if (size > MAPPED_PALETTE_LIMIT) {
            return Result.UNSURE;
        }
        int[] map = new int[size];
        for (int index = 0; index < size; index++) {
            Object state = sourcePalette.valueFor(index);
            map[index] = -1;
            for (int other = 0; other < size; other++) {
                if (targetPalette.valueFor(other) == state) {
                    map[index] = other;
                    break;
                }
            }
            if (map[index] < 0) {
                return Result.UNSURE;
            }
        }
        for (int cell = 0; cell < sourceStorage.getSize(); cell++) {
            int entry = sourceStorage.get(cell);
            if (entry >= size || map[entry] != targetStorage.get(cell)) {
                return Result.UNSURE;
            }
        }
        return Result.EQUAL;
    }
}
