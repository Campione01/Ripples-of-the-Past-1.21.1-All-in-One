package rotp.addons.kingcrimson.gametest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.state.KingCrimsonReflexErase;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * What came with core b6f65f6e and with the end of the old Epitaph: the two passive erases carry the passive mark of
 * the core and are described in every language the module ships, and a save of before R772.1, which holds the state of
 * the old Epitaph on its King Crimson users, loads and loses that entry.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonCarriedOverGameTests {
    private static final List<String> LANGUAGES = List.of("en_us", "zh_cn", "ru_ru", "uk_ua");
    // What a save of before R772.1 holds; the id is the one thing of the old Epitaph that is left.
    private static final String OLD_STATE = "rotp_kingcrimson:epitaph_state";

    private KingCrimsonCarriedOverGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_passive_mark")
    public static void thePassiveErasesAreMarkedPassiveAndDescribedInEveryLanguage(GameTestHelper helper) {
        KingCrimsonEraseFixture fixture = new KingCrimsonEraseFixture(helper);
        fixture.floor(4, 4);
        ServerPlayer player = fixture.player("kc-passive-mark", 2.5D, 2.0D, 2.5D);
        StandPower power = fixture.kingCrimson(player, 4);
        List<String> broken = new ArrayList<>();
        fixture.guarded(() -> {
            for (String name : List.of(KingCrimsonReflexErase.REFLEX, KingCrimsonReflexErase.LAST_CHANCE)) {
                Ability passive = power.getAbility(name);
                helper.assertTrue(passive != null, "Setup: no ability " + name);
                if (!passive.isPassive()) {
                    broken.add(name + " is not marked passive");
                }
                if (passive.addToControlSchemeEditing()) {
                    broken.add(name + " is offered on the controls screen");
                }
                String key = passive.getTranslationKey(".desc");
                for (String language : LANGUAGES) {
                    JsonObject texts = language(language);
                    if (!texts.has(key) || texts.get(key).getAsString().isBlank()) {
                        broken.add(language + " has no text for " + key);
                    }
                }
            }
            Ability keyed = power.getAbility("kingcrimson_epitaph");
            if (keyed == null || keyed.isPassive() || !keyed.addToControlSchemeEditing()) {
                broken.add("Epitaph, which a key uses, is marked passive or is not offered on the controls screen");
            }
            helper.assertTrue(broken.isEmpty(), "The passive erases: " + String.join("; ", broken));
        });
        fixture.succeed();
    }

    private static JsonObject language(String name) {
        String path = "/assets/rotp_kingcrimson/lang/" + name + ".json";
        try (var in = KingCrimsonAddon.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException failure) {
            throw new IllegalStateException(path, failure);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_old_save")
    public static void aSaveWithTheStateOfTheOldEpitaphLoadsAndLosesIt(GameTestHelper helper) {
        KingCrimsonEraseFixture fixture = new KingCrimsonEraseFixture(helper);
        fixture.floor(6, 6);
        ServerPlayer saved = fixture.player("kc-old-save", 2.5D, 2.0D, 2.5D);
        fixture.kingCrimson(saved, 4);
        ServerPlayer loaded = fixture.player("kc-old-load", 4.5D, 2.0D, 4.5D);
        fixture.guarded(() -> {
            CompoundTag save = saved.saveWithoutId(new CompoundTag());
            // What the old Epitaph wrote for a user whose vision was open.
            CompoundTag old = new CompoundTag();
            old.putInt("Version", 1);
            old.putInt("Stage", 1);
            old.putInt("Prediction", 1);
            old.putLong("ExpiresAt", helper.getLevel().getGameTime() + 40L);
            CompoundTag attachments = save.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                    ? save.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY) : new CompoundTag();
            attachments.put(OLD_STATE, old);
            save.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
            // The other player takes the save, and keeps his own identity in the level.
            save.remove("UUID");
            loaded.load(save);
            List<String> broken = new ArrayList<>();
            if (!loaded.hasData(KingCrimsonAddon.RETIRED_EPITAPH_STATE)) {
                broken.add("the entry " + OLD_STATE + " of the save was not read by its stand-in (NeoForge writes an error"
                        + " line for an entry nobody reads)");
            }
            if (!KingCrimsonAddon.isKingCrimsonUser(loaded)) {
                broken.add("the player of the save is no King Crimson user after the load");
            }
            CompoundTag again = loaded.saveWithoutId(new CompoundTag());
            if (again.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).contains(OLD_STATE)) {
                broken.add("the entry " + OLD_STATE + " is written again at the next save");
            }
            if (again.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).isEmpty()) {
                broken.add("Setup: the next save holds no attachment at all, so it shows nothing");
            }
            helper.assertTrue(broken.isEmpty(), "A save of before R772.1: " + String.join("; ", broken));
        });
        fixture.succeed();
    }
}
