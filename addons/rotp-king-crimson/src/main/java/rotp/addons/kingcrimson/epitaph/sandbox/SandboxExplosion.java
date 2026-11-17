package rotp.addons.kingcrimson.epitaph.sandbox;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;

/**
 * An explosion inside a room. Its rays and its blast are vanilla's own, reading the blocks as the room has them and
 * reaching the phantoms alone, whose hurt is the room's. The blocks it takes are not destroyed: they become air in the
 * overlay of the room, and the explosion is recorded as one op with that list, to be set off for real, once, with
 * exactly those blocks. Nothing drops and nothing burns in the room.
 */
public final class SandboxExplosion {
    private SandboxExplosion() {}

    public static Explosion explode(SandboxRoom room, Level level, @Nullable Entity source,
            @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator calculator, double x, double y,
            double z, float radius, boolean fire, Level.ExplosionInteraction interaction, ParticleOptions smallParticles,
            ParticleOptions largeParticles, Holder<SoundEvent> sound) {
        GameRules rules = level.getGameRules();
        Explosion.BlockInteraction blocks = switch (interaction) {
            case NONE -> Explosion.BlockInteraction.KEEP;
            case BLOCK -> decay(rules, GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY);
            // The rule itself: the grief event is for what is real.
            case MOB -> rules.getBoolean(GameRules.RULE_MOBGRIEFING) ? decay(rules, GameRules.RULE_MOB_EXPLOSION_DROP_DECAY)
                    : Explosion.BlockInteraction.KEEP;
            case TNT -> decay(rules, GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
            case TRIGGER -> Explosion.BlockInteraction.TRIGGER_BLOCK;
        };
        Explosion explosion = new Explosion(level, source, damageSource, calculator, x, y, z, radius, fire, blocks,
                smallParticles, largeParticles, sound);
        Map<BlockPos, BlockState> taken = new LinkedHashMap<>();
        // Before the hits it deals, so that it is set off before they land.
        room.record(new RecordedOp.Explode(EpitaphSandbox.actorKey(), ActorKey.of(source), damageSource, calculator, x, y, z,
                radius, fire, interaction, smallParticles, largeParticles, sound, taken));
        // Its crater is not drawn for the one who foresees; its flash is.
        room.show(new ClientboundLevelParticlesPacket(radius >= 2.0F && blocks != Explosion.BlockInteraction.KEEP
                ? largeParticles : smallParticles, true, x, y, z, 1.0F, 0.0F, 0.0F, 1.0F, 0));
        room.beginExplosion();
        try {
            explosion.explode();
        }
        finally {
            room.endExplosion();
        }
        if (!explosion.interactsWithBlocks()) {
            explosion.clearToBlow();
            return explosion;
        }
        for (BlockPos pos : explosion.getToBlow()) {
            BlockState old = level.getBlockState(pos);
            if (old.isAir()) {
                continue;
            }
            taken.put(pos.immutable(), old);
            if (blocks != Explosion.BlockInteraction.TRIGGER_BLOCK) {
                room.overlay().put(pos, Blocks.AIR.defaultBlockState());
            }
        }
        return explosion;
    }

    /** What a muted tick is handed where it asked for an explosion: one that is not set off. The blast is an op of the record. */
    public static Explosion dud(Level level, @Nullable Entity source, @Nullable DamageSource damageSource,
            @Nullable ExplosionDamageCalculator calculator, double x, double y, double z, float radius, boolean fire,
            ParticleOptions smallParticles, ParticleOptions largeParticles, Holder<SoundEvent> sound) {
        return new Explosion(level, source, damageSource, calculator, x, y, z, radius, fire, Explosion.BlockInteraction.KEEP,
                smallParticles, largeParticles, sound);
    }

    private static Explosion.BlockInteraction decay(GameRules rules, GameRules.Key<GameRules.BooleanValue> rule) {
        return rules.getBoolean(rule) ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
    }
}
