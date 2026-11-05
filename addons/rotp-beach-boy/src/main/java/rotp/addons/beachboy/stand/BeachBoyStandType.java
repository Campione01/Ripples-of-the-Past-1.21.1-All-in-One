package rotp.addons.beachboy.stand;

import com.google.gson.JsonElement;

import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** 1.16 InitStands STAND_BEACH_BOY: an EntityStandType with disableStandLeap(); the rod and hook go with the Stand. */
public final class BeachBoyStandType extends EntityStandType {
    public BeachBoyStandType(StandStats stats, MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType, ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        standLeapEnabled = false;
    }

    @Override
    public void forceUnsummon(LivingEntity user, StandPower standPower) {
        clearRodAndHook(user);
        super.forceUnsummon(user, standPower);
    }

    @Override
    public void unsummon(LivingEntity user, StandPower standPower) {
        super.unsummon(user, standPower);
        if (!standPower.isSummoned()) {
            clearRodAndHook(user);
        }
    }

    private static void clearRodAndHook(LivingEntity user) {
        if (user == null || user.level().isClientSide()) {
            return;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
        if (hook != null) {
            hook.discard();
        }
        if (user instanceof Player player) {
            BeachBoyStandUtil.removeAllRods(player);
        }
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        standLeapEnabled = false;
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        standLeapEnabled = false;
    }

    @Override
    public boolean canLeap() {
        return false;
    }
}
