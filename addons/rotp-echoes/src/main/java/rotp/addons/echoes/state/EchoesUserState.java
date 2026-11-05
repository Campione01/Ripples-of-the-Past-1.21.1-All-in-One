package rotp.addons.echoes.state;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class EchoesUserState
        implements INBTSerializable<CompoundTag> {
    public enum ActiveSkill {
        NONE,
        FREEZE_TARGET,
        HAND_FREEZE,
        FREEZE_SELF,
        GRAVITY_INFUSE,
        HAND_FREEZE_SELF
    }

    // master FreezeSoundHelper: one freeze voice per 30 seconds
    public static final long FREEZE_VOICE_COOLDOWN_TICKS = 600L;

    private static final int SERIALIZATION_VERSION = 1;

    private ActiveSkill activeSkill = ActiveSkill.NONE;
    @Nullable
    private UUID frozenTarget;
    private long skillSoundCooldownUntil;
    private long autoActionCooldownUntil;
    private long lastCommandTime = Long.MIN_VALUE;
    private long lastActiveTalk = Long.MIN_VALUE;
    private int ignoreCount;
    private long act0Since = Long.MIN_VALUE;
    private int act0FailCount;
    private boolean act0AwakeningDisabled;
    // R772 F11, ACT2 (master EchoesOnomatopoeiaSelection and EchoesOnomatopoeiaNeedleState): the chosen word is
    // kept; whether the tail is out on a target and a player's Youhuo immunity are not, as in the master
    @Nullable
    private EchoesOnomatopoeia onomatopoeia;
    private boolean tailOut;
    private long youhuoImmuneUntil = Long.MIN_VALUE;
    // master EchoesOnomatopoeiaNeedleState.isEntityForm, kept with the player: a word put on a block becomes a thing
    private boolean entityForm;
    // R772 F11, ACT1 (master EchoesSoundSelection, SoundNameNbtHandler): the chosen sound, the favourites and the
    // player's own names for sounds are kept; the self sound (master SoundAttachmentHandler.SELF_SOUNDS) is not
    @Nullable
    private ResourceLocation sound;
    private final Set<ResourceLocation> soundFavorites = new LinkedHashSet<>();
    private final Map<ResourceLocation, String> soundNames = new LinkedHashMap<>();
    private boolean selfSound;

    public ActiveSkill activeSkill() {
        return activeSkill;
    }

    @Nullable
    public UUID frozenTarget() {
        return frozenTarget;
    }

    public void activateTargetFreeze(UUID target) {
        activeSkill = ActiveSkill.FREEZE_TARGET;
        frozenTarget = target;
    }

    public void activateHandFreeze(UUID target) {
        activeSkill = ActiveSkill.HAND_FREEZE;
        frozenTarget = target;
    }

    public void activateSelfFreeze() {
        activeSkill = ActiveSkill.FREEZE_SELF;
        frozenTarget = null;
    }

    public void activateSelfHandFreeze() {
        activeSkill = ActiveSkill.HAND_FREEZE_SELF;
        frozenTarget = null;
    }

    public void activateGravityInfuse() {
        activeSkill = ActiveSkill.GRAVITY_INFUSE;
        frozenTarget = null;
    }

    public void clearActiveSkill() {
        activeSkill = ActiveSkill.NONE;
        frozenTarget = null;
    }

    public boolean skillSoundReady(long gameTime) {
        return gameTime >= skillSoundCooldownUntil;
    }

    public void resetSkillSoundCooldown(long gameTime) {
        skillSoundCooldownUntil = gameTime + FREEZE_VOICE_COOLDOWN_TICKS;
    }

    public boolean autoActionReady(long gameTime) {
        return gameTime >= autoActionCooldownUntil;
    }

    public void resetAutoActionCooldown(long gameTime) {
        autoActionCooldownUntil = gameTime + 100L;
    }

    public long lastCommandTime() {
        return lastCommandTime;
    }

    public void setLastCommandTime(long gameTime) {
        lastCommandTime = gameTime;
    }

    public long lastActiveTalk() {
        return lastActiveTalk;
    }

    public void setLastActiveTalk(long gameTime) {
        lastActiveTalk = gameTime;
    }

    public long act0Since() {
        return act0Since;
    }

    public void setAct0Since(long gameTime) {
        act0Since = gameTime;
    }

    public int act0FailCount() {
        return act0FailCount;
    }

    public void setAct0FailCount(int value) {
        act0FailCount = Math.max(0, value);
    }

    public boolean act0AwakeningDisabled() {
        return act0AwakeningDisabled;
    }

    public void setAct0AwakeningDisabled(boolean disabled) {
        act0AwakeningDisabled = disabled;
    }

    public int ignoreCount() {
        return ignoreCount;
    }

    public void setIgnoreCount(int value) {
        ignoreCount = Math.max(0, Math.min(3, value));
    }

    @Nullable
    public EchoesOnomatopoeia onomatopoeia() {
        return onomatopoeia;
    }

    public void setOnomatopoeia(@Nullable EchoesOnomatopoeia word) {
        onomatopoeia = word;
    }

    public boolean tailOut() {
        return tailOut;
    }

    public void setTailOut(boolean out) {
        tailOut = out;
    }

    public boolean youhuoImmune(long gameTime) {
        return gameTime < youhuoImmuneUntil;
    }

    public void setYouhuoImmuneUntil(long gameTime) {
        youhuoImmuneUntil = gameTime;
    }

    public boolean entityForm() {
        return entityForm;
    }

    @Nullable
    public ResourceLocation sound() {
        return sound;
    }

    public void setSound(@Nullable ResourceLocation sound) {
        this.sound = sound;
    }

    public Set<ResourceLocation> soundFavorites() {
        return soundFavorites;
    }

    public Map<ResourceLocation, String> soundNames() {
        return soundNames;
    }

    /** The choices the master kept by player id, so that they outlive a death. */
    public void copyChoicesFrom(EchoesUserState other) {
        onomatopoeia = other.onomatopoeia;
        entityForm = other.entityForm;
        sound = other.sound;
        soundFavorites.clear();
        soundFavorites.addAll(other.soundFavorites);
        soundNames.clear();
        soundNames.putAll(other.soundNames);
    }

    public boolean selfSound() {
        return selfSound;
    }

    public void setSelfSound(boolean selfSound) {
        this.selfSound = selfSound;
    }

    public void setEntityForm(boolean entityForm) {
        this.entityForm = entityForm;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SERIALIZATION_VERSION);
        tag.putString("ActiveSkill", activeSkill.name());
        if (frozenTarget != null) {
            tag.putUUID("FrozenTarget", frozenTarget);
        }
        tag.putLong("SkillSoundCooldownUntil", skillSoundCooldownUntil);
        tag.putLong("AutoActionCooldownUntil", autoActionCooldownUntil);
        tag.putLong("LastCommandTime", lastCommandTime);
        tag.putLong("LastActiveTalk", lastActiveTalk);
        tag.putInt("IgnoreCount", ignoreCount);
        tag.putLong("Act0Since", act0Since);
        tag.putInt("Act0FailCount", act0FailCount);
        tag.putBoolean("Act0AwakeningDisabled", act0AwakeningDisabled);
        if (onomatopoeia != null) {
            tag.putString("Onomatopoeia", onomatopoeia.id());
        }
        tag.putBoolean("OnomatopoeiaEntityForm", entityForm);
        if (sound != null) {
            tag.putString("Sound", sound.toString());
        }
        ListTag favorites = new ListTag();
        for (ResourceLocation favorite : soundFavorites) {
            favorites.add(StringTag.valueOf(favorite.toString()));
        }
        tag.put("SoundFavorites", favorites);
        CompoundTag names = new CompoundTag();
        soundNames.forEach((id, name) -> names.putString(id.toString(), name));
        tag.put("SoundNames", names);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clearActiveSkill();
        skillSoundCooldownUntil = 0L;
        autoActionCooldownUntil = 0L;
        lastCommandTime = Long.MIN_VALUE;
        lastActiveTalk = Long.MIN_VALUE;
        ignoreCount = 0;
        act0Since = Long.MIN_VALUE;
        act0FailCount = 0;
        act0AwakeningDisabled = false;
        onomatopoeia = null;
        tailOut = false;
        entityForm = false;
        sound = null;
        soundFavorites.clear();
        soundNames.clear();
        selfSound = false;
        if (tag.getInt("Version") != SERIALIZATION_VERSION) {
            return;
        }
        try {
            activeSkill = ActiveSkill.valueOf(tag.getString("ActiveSkill"));
        } catch (IllegalArgumentException ignored) {
            activeSkill = ActiveSkill.NONE;
        }
        if ((activeSkill == ActiveSkill.FREEZE_TARGET
                        || activeSkill == ActiveSkill.HAND_FREEZE)
                && tag.hasUUID("FrozenTarget")) {
            frozenTarget = tag.getUUID("FrozenTarget");
        } else if (activeSkill == ActiveSkill.FREEZE_TARGET
                || activeSkill == ActiveSkill.HAND_FREEZE) {
            activeSkill = ActiveSkill.NONE;
        }
        skillSoundCooldownUntil =
                Math.max(0L, tag.getLong("SkillSoundCooldownUntil"));
        autoActionCooldownUntil =
                Math.max(0L, tag.getLong("AutoActionCooldownUntil"));
        lastCommandTime = tag.getLong("LastCommandTime");
        lastActiveTalk = tag.getLong("LastActiveTalk");
        setIgnoreCount(tag.getInt("IgnoreCount"));
        if (tag.contains("Act0Since")) {
            act0Since = tag.getLong("Act0Since");
        }
        setAct0FailCount(tag.getInt("Act0FailCount"));
        act0AwakeningDisabled = tag.getBoolean("Act0AwakeningDisabled");
        onomatopoeia = EchoesOnomatopoeia.byId(tag.getString("Onomatopoeia"));
        entityForm = tag.getBoolean("OnomatopoeiaEntityForm");
        sound = tag.contains("Sound") ? ResourceLocation.tryParse(tag.getString("Sound")) : null;
        for (Tag favorite : tag.getList("SoundFavorites", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(favorite.getAsString());
            if (id != null) {
                soundFavorites.add(id);
            }
        }
        CompoundTag names = tag.getCompound("SoundNames");
        for (String key : names.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && !names.getString(key).isEmpty()) {
                soundNames.put(id, names.getString(key));
            }
        }
    }
}
