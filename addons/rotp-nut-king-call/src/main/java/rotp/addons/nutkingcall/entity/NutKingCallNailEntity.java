package rotp.addons.nutkingcall.entity;

import rotp.addons.nutkingcall.NutKingCallAddon;
import rotp.addons.nutkingcall.block.NutKingCallNailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class NutKingCallNailEntity extends NutKingCallProjectile {
    public static final float BASE_DAMAGE = 5.0F;

    public NutKingCallNailEntity(EntityType<? extends NutKingCallNailEntity> type, Level level) {
        super(type, level);
    }

    public NutKingCallNailEntity(LivingEntity shooter, Level level) {
        super(NutKingCallAddon.NAIL_ENTITY.get(), shooter, level);
    }

    @Override
    protected float getBaseDamage() {
        return BASE_DAMAGE;
    }

    @Override
    protected void placeOnBlock(Level level, BlockPos hitPos, Direction face) {
        NutKingCallNailBlock.placeNail(level, hitPos, face);
    }

    @Override
    protected void attachTo(Level level, LivingEntity target) {
        NutKingCallAttachedNailEntity.attachNail(level, target);
    }
}
