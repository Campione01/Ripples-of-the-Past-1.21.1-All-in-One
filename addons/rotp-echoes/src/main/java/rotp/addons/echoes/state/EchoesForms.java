package rotp.addons.echoes.state;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.echoes.init.EchoesStands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Echoes' ACTs as the forms of one Stand (R772 E1, after the author's latest RotP-EchoesACT-master). The port keeps
 * one Stand type per ACT; this class makes them behave as the master's single Echoes:
 * <ul>
 * <li>an ACT is on offer once the Resolve level reaches its number (EchoesChangeActScreen: min(resolve, 3)), all
 * three in Creative, and the ACT in use always;</li>
 * <li>the Resolve level is the Stand's, not the form's: a switch carries it to the new form;</li>
 * <li>a summoned Stand changes form where it stands (owner 2026-11-03), instead of the master's unsummon and summon
 * from the user.</li>
 * </ul>
 */
public final class EchoesForms {
    public static final int HIGHEST_ACT = 3;

    private EchoesForms() {}

    /** 0 for the egg, 1-3 for the ACTs, -1 for any other Stand. */
    public static int actOf(@Nullable StandType type) {
        if (type == null) {
            return -1;
        }
        if (type == EchoesStands.ECHOES_ACT0.get()) {
            return 0;
        }
        if (type == EchoesStands.ECHOES_ACT1.get()) {
            return 1;
        }
        if (type == EchoesStands.ECHOES_ACT2.get()) {
            return 2;
        }
        if (type == EchoesStands.ECHOES_ACT3.get()) {
            return 3;
        }
        return -1;
    }

    public static EntityStandType typeOf(int act) {
        return switch (act) {
            case 0 -> EchoesStands.ECHOES_ACT0.get();
            case 1 -> EchoesStands.ECHOES_ACT1.get();
            case 2 -> EchoesStands.ECHOES_ACT2.get();
            case 3 -> EchoesStands.ECHOES_ACT3.get();
            default -> throw new IllegalArgumentException("no Echoes ACT " + act);
        };
    }

    /** The highest ACT the user may pick, 0 when the user has no hatched Echoes. */
    public static int highestAct(@Nullable StandPower power) {
        int current = power != null ? actOf(power.getPowerType()) : -1;
        if (current < 1) {
            return 0;
        }
        if (power.isUserCreative()) {
            return HIGHEST_ACT;
        }
        return Math.max(current, Math.min(power.getHighestResolveLevel(), HIGHEST_ACT));
    }

    public static boolean canSwitchTo(@Nullable StandPower power, int act) {
        int current = power != null ? actOf(power.getPowerType()) : -1;
        return current >= 1 && act >= 1 && act != current && act <= highestAct(power);
    }

    /** The selector's switch: only to an ACT on offer. */
    public static boolean select(ServerPlayer player, int act) {
        StandPower power = StandPower.get(player);
        if (!canSwitchTo(power, act)) {
            return false;
        }
        changeForm(player, power, act);
        return true;
    }

    /** Puts the user's Echoes in the given form; a summoned Stand changes where it stands. */
    public static void changeForm(ServerPlayer player, StandPower power, int act) {
        int oldAct = actOf(power.getPowerType());
        EntityStandType target = typeOf(act);
        if (oldAct == act) {
            return;
        }
        StandEntity old = power.getSummonedStandEntity();
        boolean inPlace = old != null && old.isAlive() && !old.isArmsOnlyMode();
        double x = inPlace ? old.getX() : 0.0D;
        double y = inPlace ? old.getY() : 0.0D;
        double z = inPlace ? old.getZ() : 0.0D;
        float yRot = inPlace ? old.getYRot() : 0.0F;
        float xRot = inPlace ? old.getXRot() : 0.0F;
        int resolveLevel = power.getResolveLevel();
        int highest = power.getHighestResolveLevel();
        if (oldAct == HIGHEST_ACT) {
            // the gravity of ACT3 ends with it (master EchoesCapability.cancelSelfStates, ACT3-only freeze ticking)
            EchoesGravityService.cleanupUser(player, false);
        }

        power.setStand(target);
        if (power.getHighestResolveLevel() < highest) {
            power.setResolveLevel(highest);
        }
        if (power.getResolveLevel() < resolveLevel) {
            power.setResolveLevel(resolveLevel);
        }

        if (inPlace) {
            target.summon(player, power, stand -> {
                stand.moveTo(x, y, z, yRot, xRot);
                stand.yBodyRot = yRot;
                stand.yBodyRotO = yRot;
                stand.setYHeadRot(yRot);
            }, true);
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.END_ROD, x, y + 0.6D, z, 24, 0.3D, 0.5D, 0.3D, 0.05D);
            }
        }
    }
}
