package rotp.addons.spin.world;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.entity.GyroTeacherEntity;
import rotp.addons.spin.power.SpinBiomes;

public final class GyroSpawns {
    private GyroSpawns() {}

    public static boolean canSpawn(EntityType<GyroTeacherEntity> type, ServerLevelAccessor level, MobSpawnType reason,
            BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), type)
                && level.getRawBrightness(pos, 0) >= SpinConfig.GYRO_MIN_LIGHT.get()
                && level.getEntitiesOfClass(GyroTeacherEntity.class,
                        new AABB(pos).inflate(SpinConfig.GYRO_UNIQUE_RADIUS.get())).isEmpty();
    }

    public static void tickVillages(ServerLevel level) {
        if (!SpinConfig.GYRO_VILLAGE_SPAWN.get()
                || !GyroSpawnRules.isCheckTick(level.getGameTime(), SpinConfig.GYRO_VILLAGE_CHECK_INTERVAL.get())) return;
        for (var player : level.players()) {
            if (!player.isSpectator()) visit(level, player.blockPosition());
        }
    }

    private static void visit(ServerLevel level, BlockPos pos) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var starts = level.structureManager().startsForStructure(new ChunkPos(pos), structure ->
                registry.getResourceKey(structure).map(key -> registry.getHolderOrThrow(key).is(StructureTags.VILLAGE)).orElse(false));
        for (StructureStart start : starts) {
            if (!start.isValid()) continue;
            var box = start.getBoundingBox();
            if (pos.getX() < box.minX() || pos.getX() > box.maxX()
                    || pos.getZ() < box.minZ() || pos.getZ() > box.maxZ()) continue;
            var center = start.getChunkPos().getWorldPosition();
            double radius = SpinConfig.GYRO_VILLAGE_RADIUS.get();
            if (!GyroSpawnRules.categoryListed(SpinBiomes.category(level, center), SpinConfig.GYRO_VILLAGE_BIOME_CATEGORIES.get())
                    || !level.getEntitiesOfClass(GyroTeacherEntity.class, new AABB(center.getX() - radius, box.minY() - radius,
                            center.getZ() - radius, center.getX() + radius, box.maxY() + radius, center.getZ() + radius)).isEmpty()
                    || !GyroSpawnRules.rolled(level.random.nextDouble(), SpinConfig.GYRO_VILLAGE_SPAWN_CHANCE.get())) return;
            int spread = SpinConfig.GYRO_VILLAGE_SPAWN_SPREAD.get();
            for (int i = 0; i < SpinConfig.GYRO_VILLAGE_SPAWN_ATTEMPTS.get(); i++) {
                BlockPos column = center.offset(GyroSpawnRules.spreadOffset(level.random.nextDouble(), spread),
                        0, GyroSpawnRules.spreadOffset(level.random.nextDouble(), spread));
                if (!level.hasChunkAt(column)) continue;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                var type = SpinAddon.GYRO_TEACHER.get();
                if (!SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, top, type)) continue;
                GyroTeacherEntity gyro = type.create(level);
                if (gyro == null) return;
                gyro.moveTo(top.getX() + 0.5D, top.getY(), top.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
                if (!level.noCollision(gyro)) continue;
                gyro.finalizeSpawn(level, level.getCurrentDifficultyAt(top), MobSpawnType.STRUCTURE, null);
                gyro.setPersistenceRequired();
                level.addFreshEntityWithPassengers(gyro);
                return;
            }
            return;
        }
    }

    public static void spawnHorse(ServerLevelAccessor level, GyroTeacherEntity gyro, MobSpawnType reason) {
        if (!SpinConfig.GYRO_SPAWN_WITH_HORSE.get() || reason != MobSpawnType.NATURAL
                && reason != MobSpawnType.CHUNK_GENERATION && reason != MobSpawnType.STRUCTURE) return;
        Horse horse = EntityType.HORSE.create(level.getLevel());
        if (horse == null) return;
        double offset = GyroSpawnRules.sameChunkOffset(gyro.getX(), SpinConfig.GYRO_HORSE_OFFSET.get());
        horse.moveTo(gyro.getX() + offset, gyro.getY(), gyro.getZ(), gyro.getYRot(), 0.0F);
        horse.finalizeSpawn(level, level.getCurrentDifficultyAt(gyro.blockPosition()), reason, null);
        horse.equipSaddle(new ItemStack(Items.SADDLE), SoundSource.NEUTRAL);
        horse.setLeashedTo(gyro, false);
        horse.setCustomName(Component.translatable("entity.rotp_spin.gyro_teacher.horse"));
        horse.setPersistenceRequired();
        level.addFreshEntityWithPassengers(horse);
    }

    public record Natural() implements BiomeModifier {
        public static final MapCodec<Natural> CODEC = MapCodec.unit(Natural::new);

        @Override
        public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase != Phase.ADD || !SpinConfig.GYRO_NATURAL_SPAWN.get()) return;
            if (GyroSpawnRules.categoryListed(SpinBiomes.category(biome), SpinConfig.GYRO_BIOME_CATEGORIES.get())) {
                builder.getMobSpawnSettings().addSpawn(MobCategory.CREATURE,
                        new MobSpawnSettings.SpawnerData(SpinAddon.GYRO_TEACHER.get(), SpinConfig.GYRO_SPAWN_WEIGHT.get(), 1, 1));
            }
        }

        @Override
        public MapCodec<? extends BiomeModifier> codec() {
            return CODEC;
        }
    }
}
