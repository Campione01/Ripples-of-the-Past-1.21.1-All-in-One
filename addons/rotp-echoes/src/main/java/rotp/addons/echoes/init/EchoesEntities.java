package rotp.addons.echoes.init;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.entity.EchoesOnomatopoeiaWordEntity;
import rotp.addons.echoes.entity.EchoesTailNeedleEntity;
import rotp.addons.echoes.stand.EchoesStandEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EchoesEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RotpEchoes.MOD_ID);

    // R772.1 E12: every act is 0.65 x 1.95, the scale of the author's 1.21.1 skins
    public static final DeferredHolder<
            EntityType<?>, EntityType<EchoesStandEntity>> ACT0 =
            ENTITY_TYPES.register(
                    "echoes_act0",
                    key -> EntityType.Builder.<EchoesStandEntity>of(
                                    EchoesStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.65F, 1.95F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<EchoesStandEntity>> ACT1 =
            ENTITY_TYPES.register(
                    "echoes_act1",
                    key -> EntityType.Builder.<EchoesStandEntity>of(
                                    EchoesStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.65F, 1.95F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<EchoesStandEntity>> ACT2 =
            ENTITY_TYPES.register(
                    "echoes_act2",
                    key -> EntityType.Builder.<EchoesStandEntity>of(
                                    EchoesStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.65F, 1.95F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<EchoesStandEntity>> ACT3 =
            ENTITY_TYPES.register(
                    "echoes_act3",
                    key -> EntityType.Builder.<EchoesStandEntity>of(
                                    EchoesStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.65F, 1.95F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    // R772 F11: ACT2's tail shot with a word (master InitEntities TAIL_NEEDLE)
    public static final DeferredHolder<
            EntityType<?>, EntityType<EchoesTailNeedleEntity>> TAIL_NEEDLE =
            ENTITY_TYPES.register(
                    "tail_needle",
                    key -> EntityType.Builder.<EchoesTailNeedleEntity>of(
                                    EchoesTailNeedleEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build(key.toString()));

    // R772 F11: ACT2's word made a thing on a block (master InitEntities ONOMATOPOEIA_BLOCK, 1 x 1, saved)
    public static final DeferredHolder<
            EntityType<?>, EntityType<EchoesOnomatopoeiaWordEntity>> ONOMATOPOEIA_BLOCK =
            ENTITY_TYPES.register(
                    "onomatopoeia_block",
                    key -> EntityType.Builder.<EchoesOnomatopoeiaWordEntity>of(
                                    EchoesOnomatopoeiaWordEntity::new,
                                    MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build(key.toString()));

    private EchoesEntities() {}
}
