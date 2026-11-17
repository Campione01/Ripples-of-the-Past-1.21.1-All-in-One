package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.core.config.editor.AddonConfigKeys;
import rotp.core.config.editor.CommonConfigEditor;
import rotp.core.config.editor.ConfigEntry;

/**
 * King Crimson's settings on the core's settings screen: the screen is built from CommonConfigEditor.entries(), so
 * every value of the module's file has its row there, under a name and a tooltip of the language file, and the
 * settings this round added are among them. The three switches 1.16 stored and never applied stay in the file and
 * off the screen.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonConfigCatalogGameTests {
    private static final String PREFIX = KingCrimsonAddon.MOD_ID + "/";
    private static final String LABEL = KingCrimsonAddon.MOD_ID + ".configuration.";

    private KingCrimsonConfigCatalogGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20, batch = "kc_config_catalog")
    public static void everySettingOfKingCrimsonHasItsNamedRowOnTheSettingsScreen(GameTestHelper helper) {
        Map<String, ConfigEntry> rows = new LinkedHashMap<>();
        for (ConfigEntry entry : CommonConfigEditor.entries()) {
            if (entry.key().startsWith(PREFIX)) {
                rows.put(entry.key(), entry);
            }
        }
        List<ModConfigSpec.ConfigValue<?>> values = new ArrayList<>();
        collect(KingCrimsonConfig.SPEC.getValues(), values);
        Language language = Language.getInstance();
        List<String> broken = new ArrayList<>();
        Set<String> sections = new LinkedHashSet<>();
        for (ModConfigSpec.ConfigValue<?> value : values) {
            String key = AddonConfigKeys.key(KingCrimsonAddon.MOD_ID, value.getPath());
            String name = value.getPath().getLast();
            ConfigEntry row = rows.get(key);
            if (row == null) {
                broken.add(name + " has no row");
                continue;
            }
            sections.add(AddonConfigKeys.groupPath(key));
            if (row.side() != ConfigEntry.Side.SERVER || !(LABEL + name).equals(row.translationKey())) {
                broken.add(name + " is a row of the " + row.side() + " side named by " + row.translationKey());
            }
            if (!AddonConfigKeys.isHidden(key) && !(language.has(LABEL + name) && language.has(LABEL + name + ".tooltip"))) {
                broken.add(name + " has no name or no tooltip in the language file");
            }
        }
        if (rows.size() != values.size()) {
            broken.add("the screen has " + rows.size() + " rows for the file's " + values.size() + " values");
        }
        for (String section : sections) {
            if (!(language.has(LABEL + section) && language.has(LABEL + section + ".tooltip"))) {
                broken.add("the section '" + section + "' has no name or no tooltip in the language file");
            }
        }
        // What this round added, each by its name in the file; what it replaced is gone.
        for (String name : List.of("timeEraseMaxSeconds", "timeEraseShiftPlayers", "timeEraseShiftPerMove",
                "timeEraseAmnesiaSeconds", "timeEraseAmnesiaExempt", "reflexErase", "reflexEraseThreshold",
                "reflexEraseStamina", "reflexEraseSeconds", "reflexEraseCooldownSeconds", "lastChanceEraseHealthShare",
                "lastChanceEraseSeconds", "lastChanceEraseCooldownSeconds", "bloodThrowHealthCost", "bloodThrowStamina",
                "bloodThrowCooldownSeconds", "timeEraseStepMaxDistance")) {
            if (rows.keySet().stream().noneMatch(key -> key.endsWith("." + name))) {
                broken.add("no row is named " + name);
            }
        }
        if (rows.keySet().stream().anyMatch(key -> key.endsWith(".timeEraseDuration"))) {
            broken.add("timeEraseDuration, which timeEraseMaxSeconds replaced, still has a row");
        }
        for (String name : List.of("timeEraseDynamicCooldown", "timeEraseDynamicCooldownMultiplier",
                "timeEraseDynamicMinimumCooldown")) {
            String key = PREFIX + "King Crimson Time Erase Config." + name;
            if (!rows.containsKey(key) || !AddonConfigKeys.isHidden(key)) {
                broken.add(name + ", which does nothing, is in the file: " + rows.containsKey(key)
                        + ", kept off the screen: " + AddonConfigKeys.isHidden(key));
            }
        }
        helper.assertTrue(broken.isEmpty(), "King Crimson's settings on the screen: " + String.join("; ", broken));
        helper.succeed();
    }

    private static void collect(UnmodifiableConfig config, List<ModConfigSpec.ConfigValue<?>> values) {
        for (Object child : config.valueMap().values()) {
            if (child instanceof UnmodifiableConfig section) {
                collect(section, values);
            } else if (child instanceof ModConfigSpec.ConfigValue<?> value) {
                values.add(value);
            }
        }
    }
}
