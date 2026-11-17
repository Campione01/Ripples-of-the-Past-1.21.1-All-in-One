package rotp.addons.kingcrimson.epitaph.human;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The body of a human phantom: a player to the creatures that hunt it, moved by real player physics, never in the
 * world and on no connection. A fake player by its class, so that other mods leave it alone, with what a fake player
 * switches off for a body that must be hit put back: it can be hurt as the player it stands for could.
 */
public final class EpitaphPlayerPhantom extends FakePlayer {
    private final String scoreboardName;
    @Nullable
    private PhantomBrain mind;
    @Nullable
    private Entity mount;
    private boolean steers;
    /** How fast what he rides is falling: its rider moves it, and keeps this for it. */
    double mountFall;

    EpitaphPlayerPhantom(ServerLevel level, GameProfile profile, ServerPlayer real) {
        super(level, profile);
        this.scoreboardName = real.getScoreboardName();
    }

    /** What steers this body, null for one that only stands where its player stood. */
    @Nullable
    public PhantomBrain mind() {
        return mind;
    }

    void mind(PhantomBrain steering) {
        mind = steering;
    }

    /** He sits on this copy of what his player rides; it is steered by him when the real one answers to its rider. */
    public void seat(Entity ridden, boolean steered) {
        mount = ridden;
        steers = steered;
    }

    /** What he rode at the press, for as long as he sits on it; null on foot. */
    @Nullable
    public Entity mount() {
        return mount;
    }

    public boolean steers() {
        return steers;
    }

    void unseat() {
        mount = null;
        steers = false;
    }

    // The constructor of a player searches the spawn area for a place to stand; a phantom is put where its player is.
    @Override
    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
        return pos;
    }

    // Team rules are read under the name of the player it stands for; the scoreboard is never written.
    @Override
    public String getScoreboardName() {
        return scoreboardName != null ? scoreboardName : super.getScoreboardName();
    }

    /**
     * The rule of a player, written out, since a fake player answers true to everything: what holds for any entity,
     * then the question NeoForge puts to the mods (a creature in the room is asked the same way, by its own method),
     * then the rules of the game.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        boolean asAnEntity = isRemoved()
                || isInvulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.isCreativePlayer()
                || source.is(DamageTypeTags.IS_FIRE) && fireImmune()
                || source.is(DamageTypeTags.IS_FALL) && getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE);
        if (CommonHooks.isEntityInvulnerableTo(this, source, asAnEntity)) {
            return true;
        }
        GameRules rules = level().getGameRules();
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return !rules.getBoolean(GameRules.RULE_DROWNING_DAMAGE);
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return !rules.getBoolean(GameRules.RULE_FALL_DAMAGE);
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return !rules.getBoolean(GameRules.RULE_FIRE_DAMAGE);
        }
        return source.is(DamageTypeTags.IS_FREEZING) && !rules.getBoolean(GameRules.RULE_FREEZE_DAMAGE);
    }

    @Override
    public boolean canHarmPlayer(Player other) {
        if (!server.isPvpAllowed()) {
            return false;
        }
        Team mine = getTeam();
        return mine == null || !mine.isAlliedTo(other.getTeam()) || mine.isAllowFriendlyFire();
    }
}
