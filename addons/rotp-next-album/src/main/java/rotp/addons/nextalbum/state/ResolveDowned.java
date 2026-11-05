package rotp.addons.nextalbum.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.nextalbum.NextAlbumAddon;
import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.config.NextAlbumCommonConfig;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem.Suit;
import rotp.addons.nextalbum.network.ResolveDownedPayload;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Next Album 0.1.13 ResolveDownedUtil: a player killed while able to enter
 * Resolve's delayed death is downed instead (half health, lying, Stand
 * fading) for 30 s per Resolve level (+60 s with the full Prosciutto suit),
 * revived by being healed to full and dying when the time runs out or a
 * give-up hold (sneak + jump, 3 s) ends it. By default only The Grateful
 * Dead's user can be downed and keeps only its aging aura; with
 * unyieldingForSoulUsers any Stand user whose soul would rise can, keeps
 * the skills (Sex Pistols excepted) and loses time with the stamina spent.
 *
 * <p>The state lives in the player's persistent data, as in 0.1.13, so that
 * the other Next Album modules can read it by key; clients get a view of
 * it from {@link ResolveDownedPayload}.</p>
 */
public final class ResolveDowned {
    public static final String NBT_KEY = "RotpNextAlbumResolveDowned";
    public static final String DOWNED = "Downed";
    public static final int DOWNED_POSE_TICKS = 18;
    public static final int COOLDOWN_TICKS = 6000;
    public static final int GIVE_UP_HOLD_TICKS = 60;
    public static final int AURA_ACHIEVE_TICKS = 1200;
    public static final int SECONDS_PER_RESOLVE_LEVEL = 30;
    public static final int PROSCIUTTO_BONUS_TICKS = 1200;
    public static final int DEFAULT_DURATION = 600;
    public static final float STAMINA_FOR_FULL_BAR = 100.0F;
    public static final float DOWNED_HEALTH_RATIO = 0.5F;
    public static final float STAND_ALPHA_START = 0.72F;
    public static final float STAND_ALPHA_END = 0.28F;
    public static final String AGING_AURA_ABILITY = "the_grateful_dead_aging_aura";
    static final ResourceLocation GRATEFUL_DEAD =
            NextAlbumIds.id("the_grateful_dead");
    static final ResourceLocation AGING_AURA_EFFECT =
            NextAlbumIds.id("the_grateful_dead_aging_aura");

    private static final String TICKS_LEFT = "TicksLeft";
    private static final String DURATION = "Duration";
    private static final String GIVE_UP_TICKS = "GiveUpTicks";
    private static final String GIVE_UP_HELD = "GiveUpHeld";
    private static final String AURA_TICKS = "AuraTicks";
    private static final String COOLDOWN = "Cooldown";
    private static final String FREE_SKILL_MODE = "FreeSkillMode";
    private static final String LOCK_SKILLS = "LockSkills";
    private static final String USED_SKILL = "UsedSkill";
    private static final String LAST_STAMINA = "LastStamina";

    /** What a client knows of a downed player, by entity id. */
    public record View(int ticksLeft, int duration, int giveUpTicks, boolean lockSkills) {
        public float progress() {
            return duration > 0 ? 1.0F - (float) ticksLeft / duration : 0.0F;
        }
    }

    private static final Map<Integer, View> CLIENT_VIEWS = new ConcurrentHashMap<>();

    private ResolveDowned() {}

    // ---- both sides

    public static boolean isDowned(@Nullable LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.level().isClientSide()) {
            return CLIENT_VIEWS.containsKey(entity.getId());
        }
        return tag(entity).getBoolean(DOWNED);
    }

    public static float progress(@Nullable LivingEntity entity) {
        if (entity == null) {
            return 0.0F;
        }
        if (entity.level().isClientSide()) {
            View view = CLIENT_VIEWS.get(entity.getId());
            return view != null ? view.progress() : 0.0F;
        }
        CompoundTag tag = tag(entity);
        if (!tag.getBoolean(DOWNED)) {
            return 0.0F;
        }
        return 1.0F - (float) tag.getInt(TICKS_LEFT) / duration(tag);
    }

    public static boolean locksSkills(@Nullable LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.level().isClientSide()) {
            View view = CLIENT_VIEWS.get(entity.getId());
            return view != null && view.lockSkills();
        }
        CompoundTag tag = tag(entity);
        return tag.getBoolean(DOWNED) && tag.getBoolean(LOCK_SKILLS);
    }

    /** 0.1.13 ActionDownedMixin, as an ability refusal: a locked downed user keeps only the aging aura. */
    @Nullable
    public static ConditionCheck refuse(Ability ability, LivingEntity user) {
        if (!isDowned(user) || !locksSkills(user)) {
            return null;
        }
        if (!isSexPistolsUser(user)
                && AGING_AURA_ABILITY.equals(ability.abilityId.nameInMoveset())) {
            return null;
        }
        return ConditionCheck.createNegative(Component.translatable(
                "jojo.message.action_condition.resolve_downed"));
    }

    /** 0.1.13 getStandVisualAlpha: a downed user's Stand fades from 0.72 to 0.28. */
    public static float standAlpha(@Nullable LivingEntity user) {
        if (!isDowned(user)) {
            return 1.0F;
        }
        return Mth.lerp(Mth.clamp(progress(user), 0.0F, 1.0F),
                STAND_ALPHA_START, STAND_ALPHA_END);
    }

    public static boolean isSexPistolsUser(@Nullable LivingEntity entity) {
        ResourceLocation stand = standId(entity);
        return stand != null && "sex_pistols".equals(stand.getPath());
    }

    public static boolean isGratefulDeadUser(@Nullable LivingEntity entity) {
        return GRATEFUL_DEAD.equals(standId(entity));
    }

    @Nullable
    static ResourceLocation standId(@Nullable LivingEntity entity) {
        StandPower power = entity != null ? StandPower.get(entity) : null;
        return power != null && power.hasPower() && power.getPowerType() != null
                ? power.getPowerType().getId()
                : null;
    }

    public static boolean isAgingAuraActive(@Nullable LivingEntity entity) {
        StandPower power = entity != null ? StandPower.get(entity) : null;
        if (power == null || !power.hasPower()) {
            return false;
        }
        return power.userStandEffects.getEffects().stream().anyMatch(effect ->
                AGING_AURA_EFFECT.equals(effect.effectType.registryKey));
    }

    public static boolean isActuallyDead(@Nullable LivingEntity entity) {
        return entity == null || entity.isRemoved() || entity.isDeadOrDying();
    }

    public static void setClientView(int entityId, @Nullable View view) {
        if (view == null) {
            CLIENT_VIEWS.remove(entityId);
        } else {
            CLIENT_VIEWS.put(entityId, view);
        }
    }

    @Nullable
    public static View clientView(int entityId) {
        return CLIENT_VIEWS.get(entityId);
    }

    public static Iterable<Integer> clientViewIds() {
        return CLIENT_VIEWS.keySet();
    }

    public static void clearClientViews() {
        CLIENT_VIEWS.clear();
    }

    // ---- server

    public static int computeDuration(ServerPlayer player, @Nullable StandPower power) {
        int level = power != null ? power.getResolveLevel() : 0;
        if (level <= 0) {
            return 0;
        }
        int duration = level * 20 * SECONDS_PER_RESOLVE_LEVEL;
        if (CharacterSuitArmorItem.hasFullSet(player, Suit.PROSCIUTTO)) {
            duration += PROSCIUTTO_BONUS_TICKS;
        }
        return duration;
    }

    public static boolean canStart(@Nullable ServerPlayer player) {
        if (player == null || player.isCreative() || player.isSpectator()
                || isDowned(player) || tag(player).getInt(COOLDOWN) > 0
                || !player.hasEffect(ModStatusEffects.RESOLVE)) {
            return false;
        }
        StandPower power = StandPower.get(player);
        if (power == null || !power.hasPower() || power.getResolveLevel() <= 0) {
            return false;
        }
        // The port's Stands start with every skill, so the aura is always learnt.
        return NextAlbumCommonConfig.unyieldingForSoulUsers()
                ? power.willSoulSpawn()
                : isGratefulDeadUser(player);
    }

    public static boolean tryStart(ServerPlayer player) {
        if (!canStart(player)) {
            return false;
        }
        StandPower power = StandPower.get(player);
        int duration = computeDuration(player, power);
        if (duration <= 0) {
            return false;
        }
        boolean freeSkillMode = NextAlbumCommonConfig.unyieldingForSoulUsers();
        CompoundTag tag = tag(player);
        tag.putBoolean(DOWNED, true);
        tag.putInt(TICKS_LEFT, duration);
        tag.putInt(DURATION, duration);
        tag.putInt(GIVE_UP_TICKS, 0);
        tag.putBoolean(GIVE_UP_HELD, false);
        tag.putInt(AURA_TICKS, 0);
        tag.putInt(COOLDOWN, COOLDOWN_TICKS);
        tag.putBoolean(FREE_SKILL_MODE, freeSkillMode);
        tag.putBoolean(LOCK_SKILLS, !freeSkillMode || isSexPistolsUser(player));
        tag.putBoolean(USED_SKILL, false);
        tag.remove(LAST_STAMINA);
        save(player, tag);
        player.setHealth(Math.max(1.0F, player.getMaxHealth() * DOWNED_HEALTH_RATIO));
        player.deathTime = DOWNED_POSE_TICKS;
        player.hurtTime = 0;
        player.invulnerableTime = 10;
        broadcastDeathMessage(player);
        sync(player);
        if (isGratefulDeadUser(player)) {
            JojoModUtil.sayVoiceLine(player, NextAlbumAddon.PROSCIUTTO_DEAD);
        }
        return true;
    }

    public static void tick(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(NBT_KEY)) {
            return;
        }
        CompoundTag tag = data.getCompound(NBT_KEY);
        if (!tag.getBoolean(DOWNED)) {
            int cooldown = tag.getInt(COOLDOWN);
            if (cooldown > 0) {
                tag.putInt(COOLDOWN, cooldown - 1);
                save(player, tag);
            }
            return;
        }
        player.deathTime = DOWNED_POSE_TICKS;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
        player.zza = 0.0F;
        player.xxa = 0.0F;
        player.setJumping(false);
        if (isAgingAuraActive(player)) {
            tag.putInt(AURA_TICKS, tag.getInt(AURA_TICKS) + 1);
        }
        save(player, tag);
        if (trackSpentStamina(player, tag)) {
            return;
        }
        if (tag.getBoolean(GIVE_UP_HELD)) {
            int held = tag.getInt(GIVE_UP_TICKS) + 1;
            tag.putInt(GIVE_UP_TICKS, held);
            save(player, tag);
            if (held >= GIVE_UP_HOLD_TICKS) {
                finishAndDie(player);
                return;
            }
            if (held == 1 || held % 10 == 0) {
                sync(player);
            }
        }
        int ticksLeft = tag.getInt(TICKS_LEFT) - 1;
        tag.putInt(TICKS_LEFT, ticksLeft);
        save(player, tag);
        if (player.getHealth() >= player.getMaxHealth() - 0.01F) {
            revive(player);
        } else if (ticksLeft <= 0) {
            finishAndDie(player);
        } else if (player.tickCount % 10 == 0) {
            sync(player);
        }
    }

    // 0.1.13 StandPowerDownedStaminaMixin read every consumeStamina; the
    // core has no hook there, so the stamina the user lost beyond its
    // regeneration since the last tick is what it spent.
    private static boolean trackSpentStamina(ServerPlayer player, CompoundTag tag) {
        if (!tag.getBoolean(FREE_SKILL_MODE) || tag.getBoolean(LOCK_SKILLS)) {
            return false;
        }
        StandPower power = StandPower.get(player);
        if (power == null || !power.hasPower() || !power.usesStamina()) {
            return false;
        }
        float stamina = power.getStamina();
        boolean known = tag.contains(LAST_STAMINA);
        float expected = Math.min(power.getMaxStamina(),
                tag.getFloat(LAST_STAMINA) + power.getStaminaTickGain());
        tag.putFloat(LAST_STAMINA, stamina);
        save(player, tag);
        float spent = expected - stamina;
        return known && spent > 0.01F && onStaminaSpent(player, tag, spent);
    }

    /** @return whether the spending ended the downed state */
    private static boolean onStaminaSpent(ServerPlayer player, CompoundTag tag, float amount) {
        tag.putBoolean(USED_SKILL, true);
        int duration = duration(tag);
        int drain = Math.max(1, Math.round(amount * duration / STAMINA_FOR_FULL_BAR));
        int ticksLeft = Math.max(0, tag.getInt(TICKS_LEFT) - drain);
        tag.putInt(TICKS_LEFT, ticksLeft);
        save(player, tag);
        if (ticksLeft <= 0) {
            finishAndDie(player);
            return true;
        }
        if (player.tickCount % 2 == 0) {
            sync(player);
        }
        return false;
    }

    public static void holdGiveUp(ServerPlayer player, boolean holding) {
        if (!isDowned(player)) {
            return;
        }
        CompoundTag tag = tag(player);
        tag.putBoolean(GIVE_UP_HELD, holding);
        if (!holding) {
            tag.putInt(GIVE_UP_TICKS, 0);
        }
        save(player, tag);
        if (!holding) {
            sync(player);
        }
    }

    public static void revive(ServerPlayer player) {
        CompoundTag tag = tag(player);
        tag.putBoolean(DOWNED, false);
        tag.putInt(TICKS_LEFT, 0);
        tag.putInt(GIVE_UP_TICKS, 0);
        tag.putBoolean(GIVE_UP_HELD, false);
        tag.putInt(AURA_TICKS, 0);
        tag.putBoolean(USED_SKILL, false);
        tag.putBoolean(LOCK_SKILLS, false);
        tag.putBoolean(FREE_SKILL_MODE, false);
        tag.remove(LAST_STAMINA);
        save(player, tag);
        player.deathTime = 0;
        sync(player);
    }

    public static void onTrueDeath(ServerPlayer player) {
        boolean glorious = qualifiesForGloriousDeath(player);
        boolean lastWords = qualifiesForLastWords(player);
        clearDowned(player, true);
        award(player, glorious, lastWords);
        syncToAll(player);
    }

    public static void copyOnClone(Player original, Player clone, boolean wasDeath) {
        CompoundTag tag = tag(original).copy();
        tag.putBoolean(DOWNED, false);
        tag.putInt(TICKS_LEFT, 0);
        tag.putInt(GIVE_UP_TICKS, 0);
        tag.putBoolean(GIVE_UP_HELD, false);
        tag.putInt(AURA_TICKS, 0);
        tag.putBoolean(USED_SKILL, false);
        tag.putBoolean(LOCK_SKILLS, false);
        tag.putBoolean(FREE_SKILL_MODE, false);
        tag.remove(LAST_STAMINA);
        if (wasDeath) {
            tag.putInt(COOLDOWN, 0);
        }
        save(clone, tag);
        if (clone instanceof ServerPlayer player) {
            syncToAll(player);
        }
    }

    /**
     * 0.1.13 CrazyDiamondHealDownedMixin: the core's heal skips a body
     * lying with deathTime above 15, so a downed user (or its Stand) is
     * healed here as a living target is, and revived once at full health.
     *
     * @return the health restored, 0 if the target is not a downed user
     */
    public static float restoreByCrazyDiamond(LivingEntity target, double healingSpeed) {
        LivingEntity toHeal = StandUtil.getStandUser(target);
        if (!(toHeal instanceof ServerPlayer player) || !isDowned(player)) {
            return 0.0F;
        }
        float missing = player.getMaxHealth() - player.getHealth();
        float healed = Math.min(Math.max(missing, 0.0F), 0.5F * (float) healingSpeed);
        if (healed > 0.0F) {
            player.setHealth(player.getHealth() + healed);
        }
        if (player.getHealth() >= player.getMaxHealth() - 0.01F) {
            revive(player);
        }
        return healed;
    }

    private static void finishAndDie(ServerPlayer player) {
        boolean glorious = qualifiesForGloriousDeath(player);
        boolean lastWords = qualifiesForLastWords(player);
        clearDowned(player, false);
        syncToAll(player);
        award(player, glorious, lastWords);
        player.hurt(player.damageSources().generic(), Float.MAX_VALUE);
    }

    private static boolean qualifiesForGloriousDeath(ServerPlayer player) {
        CompoundTag tag = tag(player);
        return tag.getBoolean(DOWNED) && tag.getInt(AURA_TICKS) >= AURA_ACHIEVE_TICKS;
    }

    private static boolean qualifiesForLastWords(ServerPlayer player) {
        CompoundTag tag = tag(player);
        return tag.getBoolean(DOWNED) && tag.getBoolean(FREE_SKILL_MODE)
                && tag.getBoolean(USED_SKILL);
    }

    private static void clearDowned(ServerPlayer player, boolean resetCooldown) {
        CompoundTag tag = tag(player);
        tag.putBoolean(DOWNED, false);
        tag.putInt(TICKS_LEFT, 0);
        tag.putInt(GIVE_UP_TICKS, 0);
        tag.putBoolean(GIVE_UP_HELD, false);
        tag.putBoolean(LOCK_SKILLS, false);
        tag.putBoolean(FREE_SKILL_MODE, false);
        tag.remove(LAST_STAMINA);
        if (resetCooldown) {
            tag.putInt(COOLDOWN, 0);
        }
        save(player, tag);
        player.deathTime = 0;
    }

    private static void award(ServerPlayer player, boolean glorious, boolean lastWords) {
        if (glorious) {
            award(player, "glorious_death");
        }
        if (lastWords) {
            award(player, "last_words");
        }
    }

    private static void award(ServerPlayer player, String name) {
        AdvancementHolder advancement = player.server.getAdvancements()
                .get(NextAlbumIds.id(name));
        if (advancement != null) {
            player.getAdvancements().award(advancement, name);
        }
    }

    // ServerPlayer.die's broadcast, without the death screen.
    private static void broadcastDeathMessage(ServerPlayer player) {
        if (!player.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) {
            return;
        }
        Component message = player.getCombatTracker().getDeathMessage();
        PlayerList players = player.server.getPlayerList();
        Team team = player.getTeam();
        if (team == null || team.getDeathMessageVisibility() == Team.Visibility.ALWAYS) {
            players.broadcastSystemMessage(message, false);
        } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
            players.broadcastSystemToTeam(player, message);
        } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
            players.broadcastSystemToAllExceptTeam(player, message);
        }
    }

    public static ResolveDownedPayload payload(ServerPlayer player) {
        CompoundTag tag = tag(player);
        return new ResolveDownedPayload(player.getId(), tag.getBoolean(DOWNED),
                tag.getInt(TICKS_LEFT), duration(tag), tag.getInt(GIVE_UP_TICKS),
                tag.getBoolean(LOCK_SKILLS));
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload(player));
    }

    public static void syncToAll(ServerPlayer player) {
        PacketDistributor.sendToAllPlayers(payload(player));
    }

    public static void sendTo(ServerPlayer receiver, ServerPlayer subject) {
        if (isDowned(subject)) {
            PacketDistributor.sendToPlayer(receiver, payload(subject));
        }
    }

    private static int duration(CompoundTag tag) {
        int duration = tag.getInt(DURATION);
        return duration > 0 ? duration : DEFAULT_DURATION;
    }

    private static CompoundTag tag(LivingEntity entity) {
        return entity.getPersistentData().getCompound(NBT_KEY);
    }

    private static void save(LivingEntity entity, CompoundTag tag) {
        entity.getPersistentData().put(NBT_KEY, tag);
    }
}
