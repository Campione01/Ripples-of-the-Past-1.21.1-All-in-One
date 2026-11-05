package rotp.addons.nextalbum.state;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.nextalbum.item.CharacterSuitArmorItem;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem.Suit;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Next Album 0.1.13 PesciSuitUtil "Inherited Will": a full Pesci suit
 * wearer within 16 blocks of a player downed in Resolve gains Resolve as a
 * soul's onlooker does every tick (a Beach Boy user says "Big brother..."
 * on the first look), and while that player stays downed the wearer's
 * Resolve decays at half speed.
 */
public final class PesciInheritedWill {
    public static final String NBT_WATCHED = "RotpNextAlbumPesciInheritedWill";
    public static final String NBT_SOUND = "RotpNextAlbumPesciBrotherSaid";
    public static final double LOOK_RANGE = 16.0D;
    static final ResourceLocation BEACH_BOY =
            ResourceLocation.fromNamespaceAndPath("rotp_beach_boy", "beach_boy");
    static final ResourceLocation PESCI_BROTHER =
            ResourceLocation.fromNamespaceAndPath("rotp_beach_boy", "pesci_brother");
    // ResolveCounter.RESOLVE_DECAY, which the core keeps protected.
    static final float CORE_RESOLVE_DECAY = 2.0F;

    private PesciInheritedWill() {}

    public static void tick(ServerPlayer user) {
        CompoundTag data = user.getPersistentData();
        if (!CharacterSuitArmorItem.hasFullSet(user, Suit.PESCI)) {
            if (data.hasUUID(NBT_WATCHED)) {
                clearWatch(data);
            }
            return;
        }
        StandPower power = StandPower.get(user);
        Player downed = findNearbyDownedPlayer(user);
        if (downed == null) {
            if (data.hasUUID(NBT_WATCHED)
                    && !(watched(user) instanceof Player watched
                            && ResolveDowned.isDowned(watched))) {
                clearWatch(data);
            }
        } else if (power != null && power.hasPower()) {
            boolean firstLook = !data.hasUUID(NBT_WATCHED)
                    || !data.getUUID(NBT_WATCHED).equals(downed.getUUID());
            data.putUUID(NBT_WATCHED, downed.getUUID());
            power.getResolveCounter().soulAddResolveLook(power);
            if (firstLook && isBeachBoyUser(power) && !data.getBoolean(NBT_SOUND)) {
                data.putBoolean(NBT_SOUND, true);
                SoundEvent line = BuiltInRegistries.SOUND_EVENT.get(PESCI_BROTHER);
                if (line != null) {
                    JojoModUtil.sayVoiceLine(user, line);
                }
            }
        }
        halveResolveDecay(user, power);
    }

    /** 0.1.13 ResolveCounterPesciDecayMixin, without a hook: half the decay the core took this tick is given back. */
    private static void halveResolveDecay(ServerPlayer user, @Nullable StandPower power) {
        if (power == null || !power.hasPower() || !power.usesResolve()
                || !shouldHalveResolveDecay(user)
                || user.hasEffect(ModStatusEffects.RESOLVE)) {
            return;
        }
        ResolveCounter counter = power.getResolveCounter();
        float value = counter.getResolveValue();
        if (counter.noResolveDecayTicks > 0 || value <= 0.0F
                || value >= counter.getMaxResolveValue(power)) {
            return;
        }
        counter.setResolveValue(power, value + CORE_RESOLVE_DECAY * 0.5F, -1);
    }

    public static boolean shouldHalveResolveDecay(ServerPlayer user) {
        return CharacterSuitArmorItem.hasFullSet(user, Suit.PESCI)
                && watched(user) instanceof Player watched
                && ResolveDowned.isDowned(watched);
    }

    @Nullable
    private static Entity watched(ServerPlayer user) {
        CompoundTag data = user.getPersistentData();
        if (!data.hasUUID(NBT_WATCHED)) {
            return null;
        }
        UUID id = data.getUUID(NBT_WATCHED);
        return user.level() instanceof ServerLevel level ? level.getEntity(id) : null;
    }

    private static boolean isBeachBoyUser(StandPower power) {
        return power.getPowerType() != null
                && BEACH_BOY.equals(power.getPowerType().getId());
    }

    private static void clearWatch(CompoundTag data) {
        data.remove(NBT_WATCHED);
        data.remove(NBT_SOUND);
    }

    @Nullable
    private static Player findNearbyDownedPlayer(ServerPlayer observer) {
        List<Player> players = observer.level().getEntitiesOfClass(Player.class,
                observer.getBoundingBox().inflate(LOOK_RANGE),
                target -> target != observer && target.isAlive()
                        && ResolveDowned.isDowned(target));
        Player best = null;
        double bestDist = LOOK_RANGE * LOOK_RANGE;
        for (Player target : players) {
            double dist = observer.distanceToSqr(target);
            if (dist <= bestDist) {
                bestDist = dist;
                best = target;
            }
        }
        return best;
    }
}
