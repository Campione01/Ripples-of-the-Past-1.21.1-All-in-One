package rotp.addons.nutkingcall.block;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.nutkingcall.NutKingCallAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** 1.16 NutKingCallNailTileEntity: a nail lasts only while a Nut King Call user is within 10 blocks. */
public final class NutKingCallNailBlockEntity extends BlockEntity {
    public static final double CHECK_DISTANCE = 10.0D;
    public static final int CHECK_INTERVAL_TICKS = 20;

    public NutKingCallNailBlockEntity(BlockPos pos, BlockState state) {
        super(NutKingCallAddon.NAIL_BLOCK_ENTITY.get(), pos, state);
    }

    void serverTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide() || level.getGameTime() % CHECK_INTERVAL_TICKS != 0L) {
            return;
        }
        if (getBlockState().getBlock() instanceof NutKingCallNailBlock && !hasNutKingCallUserNearby(level)) {
            level.removeBlock(worldPosition, false);
        }
    }

    private boolean hasNutKingCallUserNearby(Level level) {
        AABB area = new AABB(worldPosition).inflate(CHECK_DISTANCE);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            if (isNutKingCallUser(player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNutKingCallUser(Player player) {
        StandPower power = StandPower.get(player);
        StandType type = power != null ? power.getPowerType() : null;
        return type != null && type.getId() != null && type.getId().toString().contains("nut_king_call");
    }
}
