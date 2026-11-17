package rotp.addons.kingcrimson.epitaph.human;

import java.util.List;

import javax.annotation.Nullable;

import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * Which abilities a phantom may press (EPITAPH-ARCH 2.4): those of the table, and nothing else, whoever presses; but not
 * a pair of Stand type and ability the audit before delivery excluded (ruling R13).
 */
public final class PhantomAbilityAllowList {
    private static List<String> table = KingCrimsonEpitaphForesightSpec.PHANTOM_ABILITIES;
    private static List<String> excluded = KingCrimsonEpitaphForesightSpec.PHANTOM_ABILITY_EXCLUSIONS;

    private PhantomAbilityAllowList() {}

    /** Puts another table in the place of the one in force and hands that one back: for a test, or a setting to come. */
    public static List<String> replace(List<String> names) {
        List<String> before = table;
        table = List.copyOf(names);
        return before;
    }

    /** Puts other excluded pairs in the place of those in force and hands those back: for a test, or the audit. */
    public static List<String> replaceExclusions(List<String> pairs) {
        List<String> before = excluded;
        excluded = List.copyOf(pairs);
        return before;
    }

    /** Whether a phantom whose power this is may press the ability: it is on the table, and the pair is not excluded. */
    public static boolean allows(@Nullable Ability ability, @Nullable StandPower power) {
        String slot = slotOf(ability);
        return slot != null && !isExcluded(ability, power, slot);
    }

    /** Whether the ability is on the table and excluded for the Stand type of this power: for the audit, which lists it. */
    public static boolean excludes(@Nullable Ability ability, @Nullable StandPower power) {
        String slot = slotOf(ability);
        return slot != null && isExcluded(ability, power, slot);
    }

    private static boolean isExcluded(Ability ability, @Nullable StandPower power, String slot) {
        List<String> pairs = excluded;
        if (pairs.isEmpty() || power == null || power.getPowerType() == null) {
            return false;
        }
        String type = power.getPowerType().getId() + "/";
        return pairs.contains(type + ability.name()) || pairs.contains(type + slot);
    }

    // The name of the table an ability goes by, null when it is not on it.
    @Nullable
    private static String slotOf(@Nullable Ability ability) {
        if (ability == null) {
            return null;
        }
        List<String> names = table;
        String name = ability.name();
        if (names.contains(name)) {
            return name;
        }
        // A later blow of a combo goes by the name of its first, as its one key does.
        int end = name.length();
        while (end > 0 && Character.isDigit(name.charAt(end - 1))) {
            end--;
        }
        return end < name.length() && ability.isSubAbility && names.contains(name.substring(0, end)) ? name.substring(0, end) : null;
    }
}
