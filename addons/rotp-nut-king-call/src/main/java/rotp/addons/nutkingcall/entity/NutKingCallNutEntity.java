package rotp.addons.nutkingcall.entity;

import rotp.addons.nutkingcall.NutKingCallAddon;
import rotp.addons.nutkingcall.block.NutKingCallNailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class NutKingCallNutEntity extends NutKingCallProjectile {
    public static final float BASE_DAMAGE = 2.0F;

    public NutKingCallNutEntity(EntityType<? extends NutKingCallNutEntity> type, Level level) {
        super(type, level);
    }

    public NutKingCallNutEntity(LivingEntity shooter, Level level) {
        super(NutKingCallAddon.NUT_ENTITY.get(), shooter, level);
    }

    @Override
    protected float getBaseDamage() {
        return BASE_DAMAGE;
    }

    @Override
    protected void placeOnBlock(Level level, BlockPos hitPos, Direction face) {
        NutKingCallNailBlock.placeNut(level, hitPos, face);
    }

    @Override
    protected void attachTo(Level level, LivingEntity target) {
        NutKingCallAttachedNailEntity.attachNut(level, target);
    }
}
