package rotp.addons.spin;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import rotp.addons.spin.entity.GyroTeacherEntity;
import rotp.addons.spin.world.GyroSpawns;
import rotp.core.init.ModItems;

public final class SpinModEvents {
    private SpinModEvents() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SpinAddon.GYRO_TEACHER.get(), GyroTeacherEntity.createAttributes().build());
    }

    public static void spawnPlacement(RegisterSpawnPlacementsEvent event) {
        event.register(SpinAddon.GYRO_TEACHER.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, GyroSpawns::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModItems.MAIN_TAB.getKey()) {
            SpinAddon.ITEMS.getEntries().forEach(item -> event.accept(item.get()));
        }
    }
}
