package rotp.core.impl.stands.goldexperience;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import rotp.core.client.ui.screen_jojomenu.GoldExperienceChooseLifeformLayout;
import rotp.core.impl.stands.goldexperience.client.GELifeformModelParts;
import rotp.core.impl.stands.goldexperience.client.GoldExperienceLifeformText;

import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.monster.Zombie;

public final class GoldExperienceLifeformOrderingSmokeTest {
    private GoldExperienceLifeformOrderingSmokeTest() {}

    public static void main(String[] args) {
        verifyPriorityAndTieBreaker();
        verifyKeysAreSnapshottedOnce();
        verifyPickerLayout();
        verifyEntityIconResources();
        verifyGrowthAnimationFindsVanillaModelParts();
        verifyLifeformMenuText();
    }

    // the lifeform menu's numbers: an age in game ticks, and the arrow's turn from straight ahead (right is positive)
    private static void verifyLifeformMenuText() {
        check(GoldExperienceLifeformText.age(0).equals("0:00") && GoldExperienceLifeformText.age(20 * 65).equals("1:05")
                && GoldExperienceLifeformText.age(GECreatedLifeformEffect.COUNTED_TICKS).equals("7:00")
                && GoldExperienceLifeformText.age(20L * (3600 + 62)).equals("1:01:02"),
                "lifeform menu age format changed: " + GoldExperienceLifeformText.age(20 * 65));
        check(GECreatedLifeformEffect.COUNTED_TICKS == 7 * 60 * 20, "a lifeform must stop counting after 7 minutes");
        // facing south (yaw 0): a lifeform to the south is ahead, one to the west is on the right, east on the left
        check(Math.abs(GoldExperienceLifeformText.relativeYaw(0, 0, 0.0F, 0, 10)) < 0.01F,
                "a lifeform straight ahead must not turn the arrow");
        check(Math.abs(GoldExperienceLifeformText.relativeYaw(0, 0, 0.0F, -10, 0) - 90.0F) < 0.01F,
                "a lifeform on the right must turn the arrow right");
        check(Math.abs(GoldExperienceLifeformText.relativeYaw(0, 0, 0.0F, 10, 0) + 90.0F) < 0.01F,
                "a lifeform on the left must turn the arrow left");
        check(Math.abs(Math.abs(GoldExperienceLifeformText.relativeYaw(0, 0, 90.0F, 10, 0)) - 180.0F) < 0.01F,
                "a lifeform behind must turn the arrow round");
        check(GoldExperienceLifeformText.coordinates(new net.minecraft.world.phys.Vec3(-0.5, 64.9, 12.2)).equals("-1, 64, 12"),
                "lifeform coordinates must be the block it stands in");
    }

    // The growth animation moves the parts of the grown lifeform's model. A vanilla 1.21.1 model has no root handle
    // (only Stand and humanoid models are given one), so the parts must be found from the model itself.
    private static void verifyGrowthAnimationFindsVanillaModelParts() {
        ModelPart cowRoot = CowModel.createBodyLayer().bakeRoot();
        ModelPart chickenRoot = ChickenModel.createBodyLayer().bakeRoot();
        ModelPart villagerRoot = LayerDefinition.create(VillagerModel.createBodyModel(), 64, 64).bakeRoot();
        ModelPart zombieRoot = LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64).bakeRoot();
        verifyAllPartsFound("cow", new CowModel<>(cowRoot), cowRoot, 5);
        verifyAllPartsFound("chicken", new ChickenModel<>(chickenRoot), chickenRoot, 5);
        verifyAllPartsFound("villager", new VillagerModel<>(villagerRoot), villagerRoot, 5);
        verifyAllPartsFound("zombie", new ZombieModel<Zombie>(zombieRoot), zombieRoot, 5);
    }

    private static void verifyAllPartsFound(String name, EntityModel<?> model, ModelPart root, int atLeast) {
        List<ModelPart> found = GELifeformModelParts.collect(model);
        List<ModelPart> expected = root.getAllParts().filter(part -> part != root).toList();
        check(expected.size() >= atLeast, name + " fixture has only " + expected.size() + " parts");
        for (ModelPart part : expected) {
            check(found.stream().anyMatch(candidate -> candidate == part),
                    name + " model: the growth animation cannot reach a part (" + found.size() + " of " + expected.size()
                            + " found)");
        }
    }

    // The 1.16 core shipped these head icons for EntityTypeIcon (lifeform marker,
    // new-lifeform toast). The squid icon sits where the 1.21.1 squid texture
    // path maps to (entity/squid/squid.png) instead of 1.16's entity/squid.png.
    private static final List<String> ENTITY_ICONS = List.of(
            "bat", "bear/polarbear", "bee/bee", "blaze", "cat/black", "cat/ocelot",
            "chicken", "cow/brown_mooshroom", "cow/cow", "cow/red_mooshroom",
            "creeper/creeper", "dolphin", "enderdragon/dragon", "enderman/enderman",
            "endermite", "fish/cod", "fish/pufferfish", "fish/salmon", "fish/tropical_a",
            "fox/fox", "ghast/ghast", "guardian", "guardian_elder", "hoglin/hoglin",
            "hoglin/zoglin", "horse/donkey", "horse/horse_white", "horse/mule",
            "iron_golem/iron_golem", "llama/creamy", "panda/panda",
            "parrot/parrot_red_blue", "phantom", "pig/pig", "piglin/piglin",
            "piglin/piglin_brute", "piglin/zombified_piglin", "rabbit/brown",
            "sheep/sheep", "shulker/shulker", "silverfish", "skeleton/skeleton",
            "skeleton/stray", "skeleton/wither_skeleton", "slime/magmacube",
            "slime/slime", "spider/cave_spider", "spider/spider", "squid/squid",
            "strider/strider", "turtle/big_sea_turtle", "turtle/big_sea_turtle.stand",
            "wither/wither", "wolf/wolf", "zombie/drowned", "zombie/husk",
            "zombie/zombie", "unknown");

    private static void verifyEntityIconResources() {
        ClassLoader loader = GoldExperienceLifeformOrderingSmokeTest.class.getClassLoader();
        check(ENTITY_ICONS.size() == 58, "entity icon list drifted from the 57 icons plus unknown");
        for (String icon : ENTITY_ICONS) {
            check(loader.getResource("assets/minecraft/textures/entity_icon/" + icon + ".png") != null,
                    "missing GE entity icon: " + icon);
        }
        check(loader.getResource("assets/minecraft/textures/entity_icon/squid.png") == null,
                "squid icon must follow the 1.21.1 squid texture path");
    }

    // Choose Lifeform's paper window: every part inside the window and the screen and none over another, at any size
    private static void verifyPickerLayout() {
        int control = GoldExperienceChooseLifeformLayout.CONTROL;
        int gap = GoldExperienceChooseLifeformLayout.GAP;
        for (int width : new int[] {200, 320, 427, 640, 960}) {
            for (int height : new int[] {180, 240, 270, 360, 480}) {
                GoldExperienceChooseLifeformLayout layout = GoldExperienceChooseLifeformLayout.forScreen(width, height);
                String size = " at " + width + "x" + height;
                check(layout.x() >= 4 && layout.x() + layout.width() <= width - 4 && layout.y() >= 4
                        && layout.y() + layout.height() <= height - 4, "picker window outside the screen" + size);
                check(layout.width() >= GoldExperienceChooseLifeformLayout.MIN_WIDTH
                        && layout.width() <= GoldExperienceChooseLifeformLayout.MAX_WIDTH, "picker window width" + size);
                int titleBottom = (layout.summaryLine() ? layout.summaryY() : layout.titleY()) + 9;
                check(titleBottom <= layout.searchY(), "picker title overlaps the search" + size);
                check(layout.searchY() + control + gap <= layout.filterY(), "search overlaps the filters" + size);
                check(layout.filterY() + control < layout.listTop(), "filters overlap the choices" + size);
                check(layout.listBottom() - layout.listTop() >= 2 * GoldExperienceChooseLifeformLayout.LIST_ROW,
                        "fewer than two rows of choices" + size);
                check(layout.listBottom() < layout.footerTop() && layout.footerTop() < layout.buttonsY(),
                        "choices overlap the footer" + size);
                check(layout.lastButtonsY() + control <= layout.y() + layout.height() - 4, "footer buttons outside the window" + size);
                check(layout.twoFooterRows() == layout.width() < GoldExperienceChooseLifeformLayout.ONE_FOOTER_ROW_FROM,
                        "footer rows" + size);
                check(!layout.twoFooterRows() || layout.buttonsY() + control + gap <= layout.lastButtonsY(),
                        "footer rows overlap" + size);
                check(GoldExperienceChooseLifeformLayout.columns(layout.width() - 16) >= 3, "grid narrower than three tiles" + size);
            }
        }
        GoldExperienceChooseLifeformLayout normal = GoldExperienceChooseLifeformLayout.forScreen(640, 360);
        check(normal.width() == 340 && normal.height() == 300 && normal.summaryLine() && !normal.twoFooterRows()
                && GoldExperienceChooseLifeformLayout.columns(normal.width() - 16) == 6, "normal-size picker layout changed");
        GoldExperienceChooseLifeformLayout narrow = GoldExperienceChooseLifeformLayout.forScreen(200, 180);
        check(narrow.width() == 184 && narrow.twoFooterRows() && !narrow.summaryLine(),
                "a narrow, short picker must say the choice beside its title and give its footer two rows");
    }

    private static void verifyPriorityAndTieBreaker() {
        Candidate zebra = new Candidate("Zebra", "test:a");
        Candidate ant = new Candidate("Ant", "test:z");
        Candidate beeSecond = new Candidate("Bee", "test:b");
        Candidate beeFirst = new Candidate("Bee", "test:a");

        List<Candidate> sorted = GoldExperienceLifeforms.sortedByStableKeys(
                List.of(zebra, beeSecond, ant, beeFirst).stream(),
                Candidate::displayName,
                Candidate::id);

        check(sorted.equals(List.of(ant, beeFirst, beeSecond, zebra)),
                "display-name priority or canonical-ID tie-breaker changed");
    }

    private static void verifyKeysAreSnapshottedOnce() {
        List<Candidate> candidates = IntStream.range(0, 4_096)
                .mapToObj(index -> new Candidate(
                        String.format(Locale.ROOT, "Entity %03d",
                                index * 37 % 251),
                        String.format(Locale.ROOT, "test:entity_%05d", index)))
                .toList();
        Map<Candidate, Integer> displayNameReads = new IdentityHashMap<>();
        Map<Candidate, Integer> idReads = new IdentityHashMap<>();

        List<Candidate> sorted = GoldExperienceLifeforms.sortedByStableKeys(
                candidates.stream(),
                candidate -> changingKey(
                        candidate.displayName(), candidate, displayNameReads),
                candidate -> changingKey(candidate.id(), candidate, idReads));

        check(sorted.size() == candidates.size(),
                "stable-key sort lost an entity type");
        for (Candidate candidate : candidates) {
            check(displayNameReads.get(candidate) == 1,
                    "display name was read more than once for " + candidate.id());
            check(idReads.get(candidate) == 1,
                    "canonical ID was read more than once for " + candidate.id());
        }
        assertOrdered(sorted, Candidate::displayName, Candidate::id);

        List<String> expectedIds =
                sorted.stream().map(Candidate::id).toList();
        for (int seed = 0; seed < 64; seed++) {
            List<Candidate> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, new Random(seed));
            List<Candidate> rerun =
                    GoldExperienceLifeforms.sortedByStableKeys(
                            shuffled.stream(),
                            Candidate::displayName,
                            Candidate::id);
            check(rerun.stream().map(Candidate::id).toList()
                            .equals(expectedIds),
                    "entity ordering changed with input order for seed " + seed);
        }
    }

    private static <T> String changingKey(
            String stableValue, T value, Map<T, Integer> reads) {
        int read = reads.merge(value, 1, Integer::sum);
        return read == 1 ? stableValue : stableValue + "-changed-" + read;
    }

    private static <T> void assertOrdered(
            List<T> values,
            java.util.function.Function<T, String> priorityKey,
            java.util.function.Function<T, String> tieBreaker) {
        for (int index = 1; index < values.size(); index++) {
            T previous = values.get(index - 1);
            T current = values.get(index);
            int comparison = priorityKey.apply(previous)
                    .compareTo(priorityKey.apply(current));
            if (comparison == 0) {
                comparison = tieBreaker.apply(previous)
                        .compareTo(tieBreaker.apply(current));
            }
            check(comparison <= 0,
                    "entity ordering is not monotonic at index " + index);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Candidate(String displayName, String id) {}
}
