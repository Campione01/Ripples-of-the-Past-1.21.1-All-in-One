package rotp.addons.echoes.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.power.ModPlayerPowers;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.block.EchoesSoundMarkBlock;
import rotp.addons.echoes.block.EchoesSoundMarkBlockEntity;
import rotp.addons.echoes.init.EchoesBlocks;
import rotp.addons.echoes.init.EchoesMobEffects;
import rotp.addons.echoes.network.EchoesSoundPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * R772 F11: ACT1's sounds (the master's SoundSelectAction, SoundSelfAction, SoundMarkBlockAction, SoundGiveAction and
 * their cancels, SoundAttachmentHandler.tickSelfSounds, SoundMarkBlockHandler, SoundMarkEffectHandler,
 * SoundGiveEffectHandler, SoundHauntHandler and EchoesImmunityUtil). ACT1 picks one of the game's sounds; it can play
 * it to its user over and over (self), put it on up to three block faces (whoever stands in the mark's cell, or mines
 * the block it is on, hears it and is stunned and hurt) or give it to a living target for 30 s (stunned, hurt, a mob
 * forgets its target and staggers). The numbers are the master's EchoesAct1Config defaults.
 */
public final class EchoesSoundService {
    public static final int MAX_MARKS = 3;
    private static final int GIVE_DURATION_TICKS = 600;
    private static final int STUN_DURATION = 100;
    private static final int STUN_AMPLIFIER = 3;
    private static final float DAMAGE_PER_SECOND = 2.0F;
    private static final int HAUNT_DURATION = 60;
    private static final int HAUNT_AMPLIFIER = 2;
    private static final int SELF_SOUND_INTERVAL = 40;
    private static final double NEAR_PLAYER_RANGE = 3.0D;
    public static final int MAX_NAME_LENGTH = 64;

    // master SoundGiveEffectHandler.ENTRIES, by caster
    private static final Map<UUID, List<Give>> GIVES = new HashMap<>();
    // master SoundMarkEffectHandler.MINING and PLAYING
    private static final Map<UUID, BlockPos> MINING = new HashMap<>();
    private static final Map<UUID, ResourceLocation> MARK_LOOPS = new HashMap<>();

    private static final class Give {
        final UUID target;
        final ResourceLocation sound;
        int remainingTicks = GIVE_DURATION_TICKS;
        boolean soundStarted;

        Give(UUID target, ResourceLocation sound) {
            this.target = target;
            this.sound = sound;
        }
    }

    private EchoesSoundService() {}

    // the chosen sound, favourites and names (master SoundSelectPacket, SoundFavoritesPacket, SoundNameOverridePacket)

    public static void select(ServerPlayer player, @Nullable ResourceLocation sound) {
        if (sound != null && !BuiltInRegistries.SOUND_EVENT.containsKey(sound)) {
            return;
        }
        EchoesUserState state = RotpEchoes.userState(player);
        state.setSound(sound);
        if (sound == null && state.selfSound()) {
            stopSelf(player);
        }
        sync(player);
    }

    public static void setFavorite(ServerPlayer player, ResourceLocation sound, boolean favorite) {
        Set<ResourceLocation> favorites = RotpEchoes.userState(player).soundFavorites();
        if (favorite) {
            favorites.add(sound);
        }
        else {
            favorites.remove(sound);
        }
        sync(player);
    }

    public static void rename(ServerPlayer player, ResourceLocation sound, String name) {
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }
        if (trimmed.isEmpty()) {
            RotpEchoes.userState(player).soundNames().remove(sound);
        }
        else {
            RotpEchoes.userState(player).soundNames().put(sound, trimmed);
        }
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        EchoesSoundPayloads.sendSync(player, RotpEchoes.userState(player),
                EchoesSoundMarkBlockEntity.marksOf(player.getUUID()).size());
    }

    // the self sound (master SoundSelfAction.standPerform, SoundAttachmentHandler.tickSelfSounds)

    /** On if off (it needs a chosen sound), off if on. */
    public static boolean toggleSelf(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.selfSound()) {
            stopSelf(player);
            return true;
        }
        if (state.sound() == null) {
            return false;
        }
        state.setSelfSound(true);
        haunt(player);
        sync(player);
        return true;
    }

    public static void stopSelf(ServerPlayer player) {
        RotpEchoes.userState(player).setSelfSound(false);
        player.removeEffect(EchoesMobEffects.SOUND_HAUNT);
        sync(player);
    }

    // marks (master SoundMarkBlockAction.perform, SoundMarkBlockHandler)

    /**
     * The chosen sound in the cell in front of the given face; the user's own mark there is taken back instead. A
     * fourth mark takes the oldest away.
     */
    public static boolean markBlock(ServerPlayer player, BlockPos pos, Direction face) {
        if (takeBackMark(player, pos)) {
            return true;
        }
        ResourceLocation sound = RotpEchoes.userState(player).sound();
        if (sound == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        BlockPos markPos = pos.relative(face);
        BlockState there = level.getBlockState(markPos);
        BlockState mark = EchoesBlocks.SOUND_MARK.get().defaultBlockState()
                .setValue(EchoesSoundMarkBlock.FACING, face)
                .setValue(EchoesSoundMarkBlock.VARIANT, level.getRandom().nextInt(4));
        if (!there.canBeReplaced() || !mark.canSurvive(level, markPos)) {
            return false;
        }
        List<EchoesSoundMarkBlockEntity> own = EchoesSoundMarkBlockEntity.marksOf(player.getUUID());
        for (int i = 0; i <= own.size() - MAX_MARKS; i++) {
            takeOff(own.get(i));
        }
        level.setBlock(markPos, mark, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(markPos) instanceof EchoesSoundMarkBlockEntity markEntity)) {
            return false;
        }
        markEntity.set(player.getUUID(), sound, level.getGameTime());
        sync(player);
        return true;
    }

    /** master SoundClearMarkAction: the user's own mark at the given cell comes off. */
    public static boolean takeBackMark(ServerPlayer player, BlockPos pos) {
        EchoesSoundMarkBlockEntity mark = EchoesSoundMarkBlockEntity.at(player.level(), pos);
        if (mark == null || !player.getUUID().equals(mark.owner())) {
            return false;
        }
        takeOff(mark);
        sync(player);
        return true;
    }

    /** master SoundClearAllMarksAction. */
    public static void clearMarks(ServerPlayer player) {
        EchoesSoundMarkBlockEntity.marksOf(player.getUUID()).forEach(EchoesSoundService::takeOff);
        sync(player);
    }

    public static boolean isOwnMark(Player player, BlockPos pos) {
        EchoesSoundMarkBlockEntity mark = EchoesSoundMarkBlockEntity.at(player.level(), pos);
        return mark != null && player.getUUID().equals(mark.owner());
    }

    private static void takeOff(EchoesSoundMarkBlockEntity mark) {
        if (mark.getLevel() != null) {
            mark.getLevel().removeBlock(mark.getBlockPos(), false);
        }
    }

    // giving the sound (master SoundGiveAction, SoundGiveCancelAction, SoundGiveCancelAllAction)

    public static boolean give(ServerPlayer caster, LivingEntity target) {
        ResourceLocation sound = RotpEchoes.userState(caster).sound();
        if (sound == null || target == caster) {
            return false;
        }
        haunt(target);
        List<Give> gives = GIVES.computeIfAbsent(caster.getUUID(), id -> new ArrayList<>());
        for (Give give : gives) {
            if (give.target.equals(target.getUUID()) && give.sound.equals(sound)) {
                give.remainingTicks = GIVE_DURATION_TICKS;
                syncGives(caster);
                return true;
            }
        }
        gives.add(new Give(target.getUUID(), sound));
        syncGives(caster);
        return true;
    }

    public static boolean hasGiven(LivingEntity caster, LivingEntity target) {
        List<Give> gives = GIVES.get(caster.getUUID());
        return gives != null && gives.stream().anyMatch(give -> give.target.equals(target.getUUID()));
    }

    /** master hasAnyEffectiveEffect: a target in the caster's level that the sound acts on. */
    public static boolean hasAnyGive(ServerPlayer caster) {
        List<Give> gives = GIVES.get(caster.getUUID());
        if (gives == null) {
            return false;
        }
        for (Give give : gives) {
            if (caster.serverLevel().getEntity(give.target) instanceof LivingEntity living && !isImmune(living)) {
                return true;
            }
        }
        return false;
    }

    public static void cancelGive(ServerPlayer caster, LivingEntity target) {
        List<Give> gives = GIVES.get(caster.getUUID());
        if (gives != null && gives.removeIf(give -> give.target.equals(target.getUUID()))) {
            endGive(target);
            if (gives.isEmpty()) {
                GIVES.remove(caster.getUUID());
            }
            syncGives(caster);
        }
    }

    public static void cancelAllGives(ServerPlayer caster) {
        List<Give> gives = GIVES.remove(caster.getUUID());
        if (gives == null) {
            return;
        }
        for (Give give : gives) {
            if (caster.serverLevel().getEntity(give.target) instanceof LivingEntity living) {
                endGive(living);
            }
        }
        syncGives(caster);
    }

    private static void endGive(LivingEntity target) {
        target.removeEffect(MobEffects.CONFUSION);
        if (target instanceof ServerPlayer player) {
            EchoesSoundPayloads.sendLoop(player, null);
        }
    }

    private static void syncGives(ServerPlayer caster) {
        List<UUID> targets = new ArrayList<>();
        List<Give> gives = GIVES.get(caster.getUUID());
        if (gives != null) {
            for (Give give : gives) {
                Entity entity = caster.serverLevel().getEntity(give.target);
                if (!targets.contains(give.target)
                        && !(entity instanceof LivingEntity living && isImmune(living))) {
                    targets.add(give.target);
                }
            }
        }
        EchoesSoundPayloads.sendGiveTargets(caster, targets);
    }

    // master SoundMarkBlockAction's mining: a player who starts to break a block a mark is on hears it

    public static void startMining(ServerPlayer player, BlockPos pos) {
        for (Direction face : Direction.values()) {
            BlockPos markPos = pos.relative(face);
            if (EchoesSoundMarkBlockEntity.at(player.level(), markPos) != null
                    && player.level().getBlockState(markPos).getValue(EchoesSoundMarkBlock.FACING) == face) {
                MINING.put(player.getUUID(), markPos);
                return;
            }
        }
        MINING.remove(player.getUUID());
    }

    public static void stopMining(ServerPlayer player) {
        MINING.remove(player.getUUID());
    }

    public static void forget(ServerPlayer player) {
        cancelAllGives(player);
        MINING.remove(player.getUUID());
        MARK_LOOPS.remove(player.getUUID());
    }

    // the ticks (master SoundMarkEffectHandler.tickWorld, SoundAttachmentHandler.tickSelfSounds,
    // SoundGiveEffectHandler.onServerTick)

    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Set<LivingEntity> done = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                tickPlayerOnMark(player, level);
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(NEAR_PLAYER_RANGE),
                        entity -> entity.isAlive() && !(entity instanceof ServerPlayer))) {
                    if (done.add(other)) {
                        tickOtherOnMark(other, level);
                    }
                }
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickSelfSound(player);
        }
        tickGives(server);
    }

    private static void tickPlayerOnMark(ServerPlayer player, ServerLevel level) {
        UUID id = player.getUUID();
        EchoesSoundMarkBlockEntity mark = EchoesSoundMarkBlockEntity.at(level, player.blockPosition());
        BlockPos mining = MINING.get(id);
        if (mark == null && mining != null) {
            mark = EchoesSoundMarkBlockEntity.at(level, mining);
            if (mark == null) {
                MINING.remove(id);
            }
        }
        if (mark != null) {
            boolean immune = isImmune(player);
            if (!immune) {
                stun(player);
            }
            haunt(player);
            if (!mark.sound().equals(MARK_LOOPS.get(id))) {
                MARK_LOOPS.put(id, mark.sound());
                EchoesSoundPayloads.sendLoop(player, mark.sound());
            }
            if (!immune) {
                hurt(player);
            }
        }
        else if (MARK_LOOPS.remove(id) != null) {
            EchoesSoundPayloads.sendLoop(player, null);
        }
    }

    private static void tickOtherOnMark(LivingEntity entity, ServerLevel level) {
        if (EchoesSoundMarkBlockEntity.at(level, entity.blockPosition()) == null) {
            return;
        }
        haunt(entity);
        if (!isImmune(entity)) {
            stun(entity);
            hurt(entity);
            if (entity instanceof Mob mob) {
                stagger(mob, level);
            }
        }
    }

    private static void tickSelfSound(ServerPlayer player) {
        if (!player.hasData(rotp.addons.echoes.init.EchoesAttachments.USER_STATE)) {
            return;
        }
        EchoesUserState state = RotpEchoes.userState(player);
        if (!state.selfSound() || !player.isAlive()) {
            return;
        }
        if (state.sound() == null) {
            stopSelf(player);
            return;
        }
        haunt(player);
        if (player.level().getGameTime() % SELF_SOUND_INTERVAL == 0L) {
            EchoesSoundPayloads.sendPlay(player, state.sound(), 1.0F);
        }
    }

    private static void tickGives(MinecraftServer server) {
        Iterator<Map.Entry<UUID, List<Give>>> casters = GIVES.entrySet().iterator();
        while (casters.hasNext()) {
            Map.Entry<UUID, List<Give>> entry = casters.next();
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
            ServerLevel level = caster != null ? caster.serverLevel() : server.overworld();
            boolean changed = false;
            Iterator<Give> gives = entry.getValue().iterator();
            while (gives.hasNext()) {
                Give give = gives.next();
                Entity entity = level.getEntity(give.target);
                if (--give.remainingTicks <= 0) {
                    ServerPlayer targetPlayer = server.getPlayerList().getPlayer(give.target);
                    if (targetPlayer != null) {
                        EchoesSoundPayloads.sendLoop(targetPlayer, null);
                    }
                    gives.remove();
                    changed = true;
                    continue;
                }
                if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                    gives.remove();
                    changed = true;
                    continue;
                }
                boolean immune = isImmune(target);
                if (!immune) {
                    stun(target);
                    hurt(target);
                }
                if (target instanceof ServerPlayer targetPlayer) {
                    if (!give.soundStarted) {
                        EchoesSoundPayloads.sendLoop(targetPlayer, give.sound);
                        give.soundStarted = true;
                    }
                }
                else if (target instanceof Mob mob && !immune) {
                    stagger(mob, level);
                }
            }
            if (entry.getValue().isEmpty()) {
                casters.remove();
            }
            if (changed && caster != null) {
                syncGives(caster);
            }
        }
    }

    private static void stun(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, STUN_DURATION, STUN_AMPLIFIER, false, false));
    }

    // master SoundHauntHandler: the haunt is drawn on the entity for Stand users while the sound is on it
    private static void haunt(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(EchoesMobEffects.SOUND_HAUNT, HAUNT_DURATION, HAUNT_AMPLIFIER,
                false, false, false));
    }

    // master applyDamage: 2 a second past armour; a Creative or Spectator player is not hurt here
    private static void hurt(LivingEntity entity) {
        if (entity.tickCount % 20 != 0
                || entity instanceof Player player && (player.getAbilities().invulnerable || player.isSpectator())) {
            return;
        }
        float health = entity.getHealth() - DAMAGE_PER_SECOND;
        if (health <= 0.0F) {
            entity.hurt(entity.damageSources().magic(), Float.MAX_VALUE);
        }
        else {
            entity.setHealth(health);
        }
    }

    private static void stagger(Mob mob, ServerLevel level) {
        if (mob.getTarget() != null) {
            mob.setTarget(null);
        }
        mob.getNavigation().stop();
        if (level.getGameTime() % 5L == 0L && level.getRandom().nextFloat() < 0.5F) {
            mob.setDeltaMovement(new Vec3((level.getRandom().nextDouble() - 0.5D) * 0.6D,
                    mob.getDeltaMovement().y, (level.getRandom().nextDouble() - 0.5D) * 0.6D));
            mob.hurtMarked = true;
        }
    }

    /**
     * master EchoesImmunityUtil with EchoesConfig's defaults: Star Platinum, The World and Crazy Diamond users and Hamon,
     * vampire, Pillar Man and zombie users are immune to the sound's harm.
     */
    public static boolean isImmune(LivingEntity entity) {
        StandPower stand = StandPower.get(entity);
        if (stand != null && stand.hasPower() && (stand.getPowerType() == ModStands.STAR_PLATINUM.get()
                || stand.getPowerType() == ModStands.THE_WORLD.get()
                || stand.getPowerType() == ModStands.CRAZY_DIAMOND.get())) {
            return true;
        }
        PlayerPower power = PlayerPower.get(entity);
        return power != null && power.hasPower() && (power.getPowerType() == ModPlayerPowers.HAMON.get()
                || power.getPowerType() == ModPlayerPowers.VAMPIRISM.get()
                || power.getPowerType() == ModPlayerPowers.PILLAR_MAN.get()
                || power.getPowerType() == ModPlayerPowers.ZOMBIE.get());
    }
}
