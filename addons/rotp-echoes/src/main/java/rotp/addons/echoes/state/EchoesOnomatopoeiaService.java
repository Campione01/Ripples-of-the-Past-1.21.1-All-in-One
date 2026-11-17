package rotp.addons.echoes.state;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandDamageContext;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.block.EchoesOnomatopoeiaMarkBlock;
import rotp.addons.echoes.block.EchoesOnomatopoeiaMarkBlockEntity;
import rotp.addons.echoes.entity.EchoesOnomatopoeiaWordEntity;
import rotp.addons.echoes.entity.EchoesTailNeedleEntity;
import rotp.addons.echoes.init.EchoesAttachments;
import rotp.addons.echoes.init.EchoesBlocks;
import rotp.addons.echoes.init.EchoesEntities;
import rotp.addons.echoes.init.EchoesMobEffects;
import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.network.EchoesOnomatopoeiaPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * R772 F11: ACT2's onomatopoeia (the master's OnomatopoeiaAttachHandler, OnomatopoeiaEffect and the ACT2
 * attach/self/cancel actions). ACT2 picks a word; its tail carries it to a living target within 5 blocks of the user's
 * aim, else to the block face there (any word but Youhuo), or the user puts it on himself; on a living target a word
 * acts once when it lands (Hong, Don, Fuwa, Weng, Pika), every 10 ticks after that (the rest), or by its own rule
 * (Youhuo draws everything to the marked one, the marked mob follows its owner; Tantan bounces it every 2 s); on a
 * block it acts every 10 ticks on whatever is in its cell, as a mark on the face or, in the entity form, as a thing on
 * the block. The tail stays out until the marks are taken back. Numbers are the master's EchoesAct2Config defaults.
 */
public final class EchoesOnomatopoeiaService {
    public static final double ATTACH_RANGE = 5.0D;
    private static final int TRIGGER_INTERVAL_TICKS = 10;
    private static final int EFFECT_DURATION = 100;
    private static final float DAMAGE_PER_TRIGGER = 0.25F;
    private static final double YOUHUO_RANGE = 8.0D;
    private static final int YOUHUO_IMMUNITY_TICKS = 600;
    private static final long TANTAN_INTERVAL_TICKS = 40L;

    private EchoesOnomatopoeiaService() {}

    public static void select(ServerPlayer player, @Nullable EchoesOnomatopoeia word) {
        RotpEchoes.userState(player).setOnomatopoeia(word);
    }

    /**
     * master EchoesOnomatopoeiaAttachAction.standPerform: the first living target in the user's aim, else the face of
     * the block he looks at (not for Youhuo).
     */
    public static boolean attachInAim(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        EchoesOnomatopoeia word = state.onomatopoeia();
        if (word == null || state.tailOut()) {
            return false;
        }
        StandPower power = StandPower.get(player);
        StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(ATTACH_RANGE));
        int variant = player.getRandom().nextInt(EchoesOnomatopoeia.VARIANTS);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, start, end,
                player.getBoundingBox().inflate(ATTACH_RANGE),
                entity -> entity != player && entity != stand && entity.isAlive() && entity instanceof LivingEntity,
                0.0F);
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            attach(target, player.getUUID(), word, variant);
            state.setTailOut(true);
            return true;
        }
        if (word == EchoesOnomatopoeia.YOUHUO) {
            return false;
        }
        BlockHitResult block = player.level().clip(
                new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.BLOCK || !markBlock(player.serverLevel(), player.getUUID(),
                block.getBlockPos(), block.getDirection(), word, variant, state.entityForm())) {
            return false;
        }
        state.setTailOut(true);
        return true;
    }

    /**
     * master OnomatopoeiaAttachHandler.attachToBlock and the entity form: the word on the given face of a block, as a
     * mark in the cell in front of it (which must be free and the face sturdy), or as a thing at the block's centre.
     */
    public static boolean markBlock(ServerLevel level, UUID owner, BlockPos pos, Direction face,
            EchoesOnomatopoeia word, int variant, boolean entityForm) {
        if (word == EchoesOnomatopoeia.YOUHUO) {
            return false;
        }
        if (entityForm) {
            EchoesOnomatopoeiaWordEntity thing =
                    new EchoesOnomatopoeiaWordEntity(EchoesEntities.ONOMATOPOEIA_BLOCK.get(), level);
            thing.set(owner, word, variant);
            thing.moveTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.0F, 0.0F);
            return level.addFreshEntity(thing);
        }
        BlockPos markPos = pos.relative(face);
        BlockState there = level.getBlockState(markPos);
        BlockState mark = EchoesBlocks.ONOMATOPOEIA_MARK.get().defaultBlockState()
                .setValue(EchoesOnomatopoeiaMarkBlock.FACING, face)
                .setValue(EchoesOnomatopoeiaMarkBlock.VARIANT, Math.floorMod(variant, EchoesOnomatopoeia.VARIANTS));
        if (!(there.canBeReplaced() || there.is(EchoesBlocks.ONOMATOPOEIA_MARK.get()))
                || !mark.canSurvive(level, markPos)) {
            return false;
        }
        if (there != mark) {
            level.setBlock(markPos, mark, Block.UPDATE_ALL);
        }
        if (!(level.getBlockEntity(markPos) instanceof EchoesOnomatopoeiaMarkBlockEntity markEntity)) {
            return false;
        }
        markEntity.set(owner, word);
        return true;
    }

    /** master EchoesTailNeedleAction.standPerform: the tail flies from the user's eyes along his aim. */
    public static boolean shootNeedle(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        EchoesOnomatopoeia word = state.onomatopoeia();
        if (word == null || state.tailOut()) {
            return false;
        }
        EchoesTailNeedleEntity needle = new EchoesTailNeedleEntity(EchoesEntities.TAIL_NEEDLE.get(), player.level());
        needle.setOwner(player);
        needle.setWord(word, player.getRandom().nextInt(EchoesOnomatopoeia.VARIANTS));
        needle.setEntityForm(state.entityForm());
        Vec3 eyes = player.getEyePosition();
        needle.moveTo(eyes.x, eyes.y, eyes.z, player.getYRot(), player.getXRot());
        needle.setDeltaMovement(player.getLookAngle().scale(EchoesTailNeedleEntity.SPEED));
        player.level().addFreshEntity(needle);
        state.setTailOut(true);
        return true;
    }

    /** The needle's hit (master OnomatopoeiaTailNeedleEntity.onHitEntity): the tail stays on the target. */
    public static void attachFromNeedle(LivingEntity target, ServerPlayer owner, EchoesOnomatopoeia word, int variant) {
        attach(target, owner.getUUID(), word, variant);
    }

    /**
     * master EchoesTailNeedleRecallAction: each word the user's tail left on another, on a block or as a thing comes
     * off and flies back as a needle, a needle in flight turns back, the word on himself comes off; with nothing out
     * the tail is simply in.
     */
    public static void recall(ServerPlayer player) {
        boolean flying = false;
        List<LivingEntity> marked = new ArrayList<>();
        List<EchoesOnomatopoeiaWordEntity> things = new ArrayList<>();
        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (entity instanceof EchoesTailNeedleEntity needle && needle.getOwner() == player) {
                needle.startReturning();
                flying = true;
            }
            else if (entity instanceof EchoesOnomatopoeiaWordEntity thing && player.getUUID().equals(thing.owner())) {
                things.add(thing);
            }
            else if (entity != player && entity instanceof LivingEntity living && isMarkedBy(living, player.getUUID())) {
                marked.add(living);
            }
        }
        for (LivingEntity living : marked) {
            EchoesOnomatopoeiaMark mark = markOf(living);
            if (!flying && mark != null && mark.word() != null) {
                flying = returnNeedle(player, mark.word(), mark.variant(),
                        new Vec3(living.getX(), living.getY() + living.getBbHeight() * 0.5D, living.getZ()));
            }
            removeMark(living);
        }
        for (EchoesOnomatopoeiaWordEntity thing : things) {
            if (!flying && thing.word() != null) {
                flying = returnNeedle(player, thing.word(), thing.variant(), thing.position());
            }
            thing.discard();
        }
        for (EchoesOnomatopoeiaMarkBlockEntity mark : EchoesOnomatopoeiaMarkBlockEntity.marksOf(player.getUUID())) {
            if (!flying && mark.word() != null && mark.getLevel() == player.level()) {
                flying = returnNeedle(player, mark.word(), mark.variant(), Vec3.atCenterOf(mark.getBlockPos()));
            }
            mark.takeOff();
        }
        // the master's recall took every attachment of the owner, the one on himself too
        takeBackSelfMark(player);
        if (!flying) {
            RotpEchoes.userState(player).setTailOut(false);
        }
    }

    private static boolean returnNeedle(ServerPlayer player, EchoesOnomatopoeia word, int variant, Vec3 from) {
        EchoesTailNeedleEntity needle = new EchoesTailNeedleEntity(EchoesEntities.TAIL_NEEDLE.get(), player.level());
        needle.setOwner(player);
        needle.setWord(word, variant);
        needle.moveTo(from.x, from.y, from.z);
        needle.startReturning();
        return player.level().addFreshEntity(needle);
    }

    /**
     * master EchoesOnomatopoeiaSelfAction / attachToSelf: the word on the user. It counts as the tail fired, as the
     * master's setFired did: the tail tip is hidden, needle and attach wait for it, and it is refused with the tail out.
     */
    public static boolean attachToSelf(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        EchoesOnomatopoeia word = state.onomatopoeia();
        if (word == null || state.tailOut()) {
            return false;
        }
        attach(player, player.getUUID(), word, player.getRandom().nextInt(EchoesOnomatopoeia.VARIANTS));
        state.setTailOut(true);
        return true;
    }

    /**
     * master EchoesOnomatopoeiaAttachCancelAction: the user's words come off others, blocks and himself, the tail is
     * back.
     */
    public static void takeBackMarks(ServerPlayer player) {
        List<LivingEntity> marked = new ArrayList<>();
        List<Entity> gone = new ArrayList<>();
        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (entity instanceof EchoesTailNeedleEntity needle && needle.getOwner() == player) {
                gone.add(needle);
            }
            else if (entity instanceof EchoesOnomatopoeiaWordEntity thing && player.getUUID().equals(thing.owner())) {
                gone.add(thing);
            }
            else if (entity != player && entity instanceof LivingEntity living && isMarkedBy(living, player.getUUID())) {
                marked.add(living);
            }
        }
        gone.forEach(Entity::discard);
        marked.forEach(EchoesOnomatopoeiaService::removeMark);
        EchoesOnomatopoeiaMarkBlockEntity.marksOf(player.getUUID()).forEach(EchoesOnomatopoeiaMarkBlockEntity::takeOff);
        takeBackSelfMark(player);
        RotpEchoes.userState(player).setTailOut(false);
    }

    public static void takeBackSelfMark(ServerPlayer player) {
        if (isMarkedBy(player, player.getUUID())) {
            removeMark(player);
        }
    }

    public static boolean hasSelfMark(Player player) {
        return isMarkedBy(player, player.getUUID());
    }

    private static boolean isMarkedBy(LivingEntity entity, UUID owner) {
        return entity.hasData(EchoesAttachments.ONOMATOPOEIA_MARK)
                && owner.equals(entity.getData(EchoesAttachments.ONOMATOPOEIA_MARK).owner());
    }

    @Nullable
    public static EchoesOnomatopoeiaMark markOf(LivingEntity entity) {
        return entity.hasData(EchoesAttachments.ONOMATOPOEIA_MARK)
                ? entity.getData(EchoesAttachments.ONOMATOPOEIA_MARK)
                : null;
    }

    private static void attach(LivingEntity target, UUID owner, EchoesOnomatopoeia word, int variant) {
        EchoesOnomatopoeiaMark mark = new EchoesOnomatopoeiaMark();
        mark.set(owner, word, variant);
        target.setData(EchoesAttachments.ONOMATOPOEIA_MARK, mark);
        EchoesOnomatopoeiaPayloads.syncMark(target);
    }

    /**
     * The word comes off and every client watching is told, also when the server has none: a client draws by entity id,
     * and a respawned player keeps the id of the one who died. The state it gave ends too (master
     * OnomatopoeiaAttachedEntity.remove).
     */
    public static void removeMark(LivingEntity target) {
        EchoesOnomatopoeiaMark mark = markOf(target);
        if (mark != null && mark.word() != null) {
            removeWord(mark.word(), target);
        }
        // the self word held the tail, which nothing else can then hold (master EchoesOnomatopoeiaSelfCancelAction)
        if (mark != null && target instanceof ServerPlayer player && player.getUUID().equals(mark.owner())
                && player.hasData(EchoesAttachments.USER_STATE)) {
            RotpEchoes.userState(player).setTailOut(false);
        }
        target.removeData(EchoesAttachments.ONOMATOPOEIA_MARK);
        EchoesOnomatopoeiaPayloads.syncMark(target);
    }

    /** master OnomatopoeiaAttachHandler.tickEntityAttachments, for one marked living entity on the server. */
    public static void tickMarked(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level) || !target.hasData(EchoesAttachments.ONOMATOPOEIA_MARK)) {
            return;
        }
        EchoesOnomatopoeiaMark mark = target.getData(EchoesAttachments.ONOMATOPOEIA_MARK);
        EchoesOnomatopoeia word = mark.word();
        if (word == null || !target.isAlive()) {
            return;
        }
        if (ownerLostEchoes(level, mark)) {
            removeMark(target);
            return;
        }
        if (!ownerHasAct2(level, mark)) {
            return;
        }
        if (word == EchoesOnomatopoeia.YOUHUO) {
            tickYouhuo(level, mark, target);
        }
        else if (word == EchoesOnomatopoeia.TANTAN) {
            if (level.getGameTime() >= mark.nextTantan()) {
                mark.setNextTantan(level.getGameTime() + TANTAN_INTERVAL_TICKS);
                Vec3 motion = target.getDeltaMovement();
                target.setDeltaMovement(motion.x, 0.8D, motion.z);
                target.hurtMarked = true;
                target.fallDistance = 0.0F;
            }
        }
        else if (word.actsOnce()) {
            if (!mark.triggered()) {
                mark.markTriggered();
                applyBy(mark.owner(), word, target);
            }
        }
        else if (level.getGameTime() % TRIGGER_INTERVAL_TICKS == 0L) {
            applyBy(mark.owner(), word, target);
        }
    }

    // master tickYouhuo: the marked mob drops its fight and follows its owner; mobs within 8 blocks come to it, and a
    // player other than the owner is drawn to it unless he walks away from it, which keeps him free for 30 s
    private static void tickYouhuo(ServerLevel level, EchoesOnomatopoeiaMark mark, LivingEntity attached) {
        ServerPlayer owner = mark.owner() != null ? level.getServer().getPlayerList().getPlayer(mark.owner()) : null;
        if (attached instanceof Mob mob) {
            stopMobAi(mob);
            if (owner != null && owner.isAlive() && mob.distanceTo(owner) <= 32.0D) {
                mob.getNavigation().moveTo(owner, 1.0D);
                mob.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            }
        }
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                attached.getBoundingBox().inflate(YOUHUO_RANGE), entity -> entity != attached && entity.isAlive())) {
            if (other instanceof Mob mob) {
                stopMobAi(mob);
                mob.getNavigation().moveTo(attached, 1.0D);
                mob.getLookControl().setLookAt(attached, 30.0F, 30.0F);
            }
            else if (other instanceof ServerPlayer player && !player.getUUID().equals(mark.owner())) {
                EchoesUserState state = RotpEchoes.userState(player);
                if (state.youhuoImmune(level.getGameTime())) {
                    continue;
                }
                Vec3 dir = attached.position().subtract(player.position());
                if (dir.lengthSqr() < 0.01D) {
                    continue;
                }
                dir = dir.normalize();
                Vec3 input = new Vec3(player.xxa, 0.0D, player.zza);
                if (input.lengthSqr() > 0.1D && input.normalize().dot(dir) < -0.5D) {
                    state.setYouhuoImmuneUntil(level.getGameTime() + YOUHUO_IMMUNITY_TICKS);
                    continue;
                }
                player.setDeltaMovement(dir.x * 0.15D, player.getDeltaMovement().y, dir.z * 0.15D);
                player.hurtMarked = true;
            }
        }
    }

    // The master asked for ACT2 out; the owner's rule of 2026-11-05 is over it: a word goes on through a recall.
    private static boolean ownerHasAct2(ServerLevel level, EchoesOnomatopoeiaMark mark) {
        ServerPlayer owner = mark.owner() != null ? level.getServer().getPlayerList().getPlayer(mark.owner()) : null;
        StandPower power = owner != null && owner.level() == level ? StandPower.get(owner) : null;
        return power != null && power.getPowerType() == EchoesStands.ECHOES_ACT2.get();
    }

    // Another ACT only puts the word to sleep; with no Echoes left, or no user, nothing can wake it or take it off.
    private static boolean ownerLostEchoes(ServerLevel level, EchoesOnomatopoeiaMark mark) {
        ServerPlayer owner = mark.owner() != null ? level.getServer().getPlayerList().getPlayer(mark.owner()) : null;
        StandPower power = owner != null ? StandPower.get(owner) : null;
        return power == null || EchoesForms.actOf(power.getPowerType()) < 0;
    }

    /** A word on an entity ends with that entity's death, or a player's logout or change of level, whoever put it there. */
    public static void endWordOn(LivingEntity entity) {
        if (entity.hasData(EchoesAttachments.ONOMATOPOEIA_MARK)) {
            removeMark(entity);
        }
    }

    /** A word on another entity ends with its user's death, logout or change of level; a relog must not bring it back. */
    public static void endWordsOnOthers(ServerPlayer player) {
        List<LivingEntity> marked = new ArrayList<>();
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity != player && entity instanceof LivingEntity living && isMarkedBy(living, player.getUUID())) {
                    marked.add(living);
                }
            }
        }
        marked.forEach(EchoesOnomatopoeiaService::removeMark);
    }

    private static void stopMobAi(Mob mob) {
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.getNavigation().stop();
        mob.setAggressive(false);
        if (mob.isUsingItem()) {
            mob.stopUsingItem();
        }
        mob.setSprinting(false);
        if (mob.isPassenger()) {
            mob.stopRiding();
        }
    }

    /** master EchoesBlockEventHandler.onLivingTick, once a second: Jian in the entity's cell or under it pricks it. */
    public static void tickBlockMarks(LivingEntity entity) {
        BlockPos pos = entity.blockPosition();
        for (BlockPos cell : new BlockPos[] {pos, pos.below()}) {
            if (EchoesOnomatopoeiaMarkBlockEntity.wordAt(entity.level(), cell) == EchoesOnomatopoeia.JIAN) {
                try (var running = StandDamageContext.enter(RotpEchoes.powerOf(entity.level(),
                        EchoesOnomatopoeiaMarkBlockEntity.ownerAt(entity.level(), cell)))) {
                    entity.hurt(entity.damageSources().cactus(), 0.5F);
                }
                return;
            }
        }
    }

    /**
     * master EchoesBlockEventHandler.onBlockBreak: Yingbangbang makes the block it is put on unbreakable (the master
     * looked for the mark in the broken cell and the one under it, where a mark on that block never is).
     */
    public static boolean isHardened(BlockGetter level, BlockPos pos) {
        for (Direction face : Direction.values()) {
            BlockPos markPos = pos.relative(face);
            if (EchoesOnomatopoeiaMarkBlockEntity.wordAt(level, markPos) == EchoesOnomatopoeia.YINGBANGBANG
                    && level.getBlockState(markPos).getValue(EchoesOnomatopoeiaMarkBlock.FACING) == face) {
                return true;
            }
        }
        return false;
    }

    /** A word applied by its owner's Stand: its damage takes the Stand's damage share, whichever tick delivers it. */
    public static void applyBy(@Nullable UUID owner, EchoesOnomatopoeia word, LivingEntity target) {
        try (var running = StandDamageContext.enter(RotpEchoes.powerOf(target.level(), owner))) {
            apply(word, target);
        }
    }

    /** master OnomatopoeiaEffect.apply. */
    public static void apply(EchoesOnomatopoeia word, LivingEntity target) {
        switch (word) {
            case ZIZI -> electric(target, 0);
            case PIKA -> electric(target, 1);
            case RERE -> effect(target, EchoesMobEffects.HOT, EFFECT_DURATION, 0);
            case BING -> effect(target, EchoesMobEffects.COLD, EFFECT_DURATION, 0);
            case HONG -> {
                hurt(target);
                Vec3 back = backwards(target).scale(1.5D);
                target.setDeltaMovement(back.x, 0.8D, back.z);
                target.hurtMarked = true;
                target.fallDistance = 0.0F;
            }
            case KACHA -> {
                hurt(target);
                effect(target, MobEffects.MOVEMENT_SLOWDOWN, 40, 0);
            }
            case JIAN -> hurt(target);
            case WENG, FUWA -> {
                Vec3 motion = target.getDeltaMovement();
                target.setDeltaMovement(motion.x, motion.y + Math.sin(target.level().getGameTime() * 0.3D) * 0.08D,
                        motion.z);
                target.hurtMarked = true;
            }
            case YUNHUHU -> effect(target, MobEffects.CONFUSION, EFFECT_DURATION, 1);
            case PAPA, HUANGHUANG -> effect(target, EchoesMobEffects.PANIC, EFFECT_DURATION, 0);
            case MENGMENG -> effect(target, MobEffects.MOVEMENT_SLOWDOWN, 20, 4);
            case YANGYANG -> effect(target, EchoesMobEffects.ITCHY, EFFECT_DURATION, 0);
            case GOGOGO -> effect(target, EchoesMobEffects.PRESSURE, EFFECT_DURATION, 0);
            case DON -> {
                hurt(target);
                Vec3 back = backwards(target).scale(1.8D);
                Vec3 motion = target.getDeltaMovement();
                target.setDeltaMovement(motion.x + back.x, motion.y + 0.4D, motion.z + back.z);
                target.hurtMarked = true;
            }
            case NIANHUHU -> effect(target, MobEffects.MOVEMENT_SLOWDOWN, 20, 2);
            case HUALIU -> effect(target, EchoesMobEffects.SLIPPERY, EFFECT_DURATION, 0);
            case SHILU -> {
                effect(target, EchoesMobEffects.WET, EFFECT_DURATION, 0);
                if (target.isOnFire()) {
                    target.clearFire();
                }
            }
            case RUANMIANMIAN -> effect(target, MobEffects.SLOW_FALLING, EFFECT_DURATION, 0);
            case CHENDIANDIAN -> effect(target, EchoesMobEffects.SINK, EFFECT_DURATION, 0);
            case LENGBINGBING -> effect(target, EchoesMobEffects.FROZEN, EFFECT_DURATION, 0);
            case NUANNUAN -> effect(target, MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, 0);
            case SHANSHAN -> effect(target, EchoesMobEffects.BRIGHT, EFFECT_DURATION, 0);
            // the master's Tantan acts in its own tick; Yingbangbang and Youhuo have no effect of their own
            case TANTAN, YINGBANGBANG, YOUHUO -> {}
        }
    }

    /**
     * master OnomatopoeiaEffect.remove, for an entity that leaves a block mark: the state the word gives ends (Mengmeng
     * ends its slowness; the master's table took away nausea there, which Mengmeng does not give).
     */
    public static void removeWord(EchoesOnomatopoeia word, LivingEntity target) {
        switch (word) {
            case ZIZI, PIKA -> target.removeEffect(EchoesMobEffects.ELECTRIC);
            case RERE -> target.removeEffect(EchoesMobEffects.HOT);
            case BING -> target.removeEffect(EchoesMobEffects.COLD);
            case LENGBINGBING -> target.removeEffect(EchoesMobEffects.FROZEN);
            case YUNHUHU -> target.removeEffect(MobEffects.CONFUSION);
            case PAPA, HUANGHUANG -> target.removeEffect(EchoesMobEffects.PANIC);
            case YANGYANG -> target.removeEffect(EchoesMobEffects.ITCHY);
            case GOGOGO -> target.removeEffect(EchoesMobEffects.PRESSURE);
            case MENGMENG, NIANHUHU, KACHA -> target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            case HUALIU -> target.removeEffect(EchoesMobEffects.SLIPPERY);
            case SHILU -> target.removeEffect(EchoesMobEffects.WET);
            case CHENDIANDIAN -> target.removeEffect(EchoesMobEffects.SINK);
            case SHANSHAN -> target.removeEffect(EchoesMobEffects.BRIGHT);
            default -> {}
        }
    }

    private static void electric(LivingEntity target, int amplifier) {
        hurt(target);
        effect(target, EchoesMobEffects.ELECTRIC, EFFECT_DURATION, amplifier);
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(0.0D, Math.min(motion.y, 0.0D), 0.0D);
    }

    private static void hurt(LivingEntity target) {
        target.hurt(target.damageSources().magic(), DAMAGE_PER_TRIGGER);
    }

    // the master's states left a creative player alone (StatusEffect.isApplicable)
    private static void effect(LivingEntity target, Holder<MobEffect> effect, int duration, int amplifier) {
        if (target instanceof Player player && player.getAbilities().instabuild) {
            return;
        }
        target.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false));
    }

    private static Vec3 backwards(LivingEntity target) {
        Vec3 look = target.getViewVector(1.0F);
        Vec3 back = new Vec3(-look.x, 0.0D, -look.z);
        return back.lengthSqr() < 0.01D ? new Vec3(0.0D, 0.0D, 1.0D) : back.normalize();
    }
}
