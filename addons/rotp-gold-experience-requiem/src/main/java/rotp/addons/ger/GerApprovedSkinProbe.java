package rotp.addons.ger;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import rotp.core.client.entityrender.parsemodel.ParseModEntityModel;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel.ModelFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.addons.ger.client.GerStandModelValidator;
import net.minecraft.client.model.geom.ModelPart;

// Run by hand (verifyGerApprovedSkin); it is not part of check. Uses the same mapped
// Minecraft runtime as the existing model bake probe.
//
// The selectable GER skins since 2026-11-14:
//   ger_stand  the default, the anime build of the new model (rig 2), with the clips and icons
//   ger_manga  the manga build of the same model (rig 2)
//   ger_pack   Day_GeE's "Model Optimization" pack on the core rig (the pack rig), default
//              from 2026-11-05 to 2026-11-14
// The four skins before them (ger_original, ger_classic, ger_custom, ger_refined) were
// removed; their backup is in SOURCE_AND_LICENSE.md.
public final class GerApprovedSkinProbe {
    private static final String SKIN_ROOT = "assets/geraddon/stand_skins/";
    private static final String DEFAULT_ROOT = SKIN_ROOT + "ger_stand/";
    private static final String MANGA_ROOT = SKIN_ROOT + "ger_manga/";
    private static final String PACK_ROOT = SKIN_ROOT + "ger_pack/";
    private static final String MODEL = "assets/geraddon/geo/ger_stand.geo.json";
    private static final String TEXTURE = "assets/geraddon/textures/entity/stand/ger_stand.png";
    private static final String ANIMATION = "assets/geraddon/animations/ger_stand.animation.json";
    private static final String LANG = "assets/geraddon/lang/";
    private static final List<String> SKIN_LANGUAGES = List.of("en_us", "ru_ru", "zh_cn", "zh_tw");
    private static int assertions;

    private GerApprovedSkinProbe() {}

    public static void main(String[] args) throws Exception {
        check(args.length == 2, "Expected [moduleDir, jarPath].");
        Path resources = Path.of(args[0]).toAbsolutePath().normalize()
                .resolve("src/main/resources");
        Path jarPath = Path.of(args[1]).toAbsolutePath().normalize();

        for (String root : List.of(DEFAULT_ROOT, MANGA_ROOT, PACK_ROOT)) {
            check("geraddon:ger_stand".equals(json(resources.resolve(root + "skin.json"))
                            .get("stand_type").getAsString()),
                    "A GER skin is not bound to the geraddon:ger_stand Stand type: " + root);
        }
        check(json(resources.resolve(PACK_ROOT + "skin.json")).getAsJsonArray("authors")
                        .toString().contains("Day_GeE"),
                "ger_pack lost its credit to Day_GeE.");
        try (var skins = Files.list(resources.resolve(SKIN_ROOT))) {
            check(skins.map(path -> path.getFileName().toString()).sorted().toList()
                            .equals(List.of("ger_manga", "ger_pack", "ger_stand")),
                    "The set of selectable GER skins changed.");
        }

        JsonObject defaultModel = json(resources.resolve(DEFAULT_ROOT + MODEL));
        JsonObject mangaModel = json(resources.resolve(MANGA_ROOT + MODEL));
        JsonObject packModel = json(resources.resolve(PACK_ROOT + MODEL));
        validateInventory(defaultModel, GerStandModelValidator.DEFAULT_SKIN_BONE_COUNT,
                GerStandModelValidator.DEFAULT_SKIN_CUBE_COUNT, 256, "ger_stand");
        validateInventory(mangaModel, GerStandModelValidator.DEFAULT_SKIN_BONE_COUNT,
                72, 256, "ger_manga");
        validateInventory(packModel, GerStandModelValidator.DEFAULT_SKIN_BONE_COUNT,
                75, 128, "ger_pack");
        allCubesDrawn(bake(defaultModel), "ger_stand");
        allCubesDrawn(bake(mangaModel), "ger_manga");
        allCubesDrawn(bake(packModel), "ger_pack");
        validateTextureSize(resources.resolve(DEFAULT_ROOT + TEXTURE), 512, 1024);
        validateTextureSize(resources.resolve(MANGA_ROOT + TEXTURE), 512, 1024);
        validateTextureSize(resources.resolve(PACK_ROOT + TEXTURE), 512, 512);
        List<String> names = new ArrayList<>();
        names.addAll(validateNames(resources, MANGA_ROOT, "ger_manga"));
        names.addAll(validateNames(resources, PACK_ROOT, "ger_pack"));
        validateRejections(defaultModel, packModel);

        List<String> compared = new ArrayList<>(List.of(
                DEFAULT_ROOT + "skin.json", DEFAULT_ROOT + MODEL, DEFAULT_ROOT + TEXTURE,
                DEFAULT_ROOT + ANIMATION,
                MANGA_ROOT + "skin.json", MANGA_ROOT + MODEL, MANGA_ROOT + TEXTURE,
                PACK_ROOT + "skin.json", PACK_ROOT + MODEL, PACK_ROOT + TEXTURE,
                PACK_ROOT + ANIMATION));
        compared.addAll(names);
        try (ZipFile jar = new ZipFile(jarPath.toFile())) {
            for (String resource : compared) {
                check(Arrays.equals(Files.readAllBytes(resources.resolve(resource)),
                                jarBytes(jar, resource)),
                        "Packaged resource differs from source: " + resource);
            }
            check(jar.stream().filter(entry -> !entry.isDirectory())
                            .noneMatch(entry -> entry.getName().startsWith(SKIN_ROOT + "ger_original/")
                            || entry.getName().startsWith(SKIN_ROOT + "ger_classic/")
                            || entry.getName().startsWith(SKIN_ROOT + "ger_custom/")
                            || entry.getName().startsWith(SKIN_ROOT + "ger_refined/")),
                    "A removed GER skin is still packaged.");
            JsonObject packaged = JsonParser.parseString(new String(
                    jarBytes(jar, DEFAULT_ROOT + MODEL), StandardCharsets.UTF_8)).getAsJsonObject();
            allCubesDrawn(bake(packaged), "packaged ger_stand");
        }
        System.out.println("GER skin probe passed: assertions=" + assertions
                + ", skins=ger_stand,ger_manga,ger_pack"
                + ", matchedJarResources=" + compared.size());
    }

    private static void validateInventory(JsonObject model, int expectedBones, int expectedCubes,
            int atlasHeight, String skin) {
        JsonObject geometry = model.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        check(description.get("texture_width").getAsInt() == 128
                        && description.get("texture_height").getAsInt() == atlasHeight,
                "Skin " + skin + " must declare a 128x" + atlasHeight + " UV atlas.");
        JsonArray bones = geometry.getAsJsonArray("bones");
        int cubes = 0;
        for (JsonElement bone : bones) {
            JsonArray boneCubes = bone.getAsJsonObject().getAsJsonArray("cubes");
            cubes += boneCubes != null ? boneCubes.size() : 0;
        }
        check(bones.size() == expectedBones && cubes == expectedCubes,
                "Skin " + skin + " has " + bones.size() + " bones and " + cubes + " cubes.");
    }

    private static void allCubesDrawn(ModelPart root, String skin) {
        List<String> undrawn = GerStandModelValidator.bonesWithUndrawnCubes(root);
        check(undrawn.isEmpty(), "Cubes of " + skin + " that the core never draws: " + undrawn);
    }

    private static void validateTextureSize(Path path, int width, int height) throws Exception {
        BufferedImage image = ImageIO.read(path.toFile());
        check(image != null && image.getWidth() == width && image.getHeight() == height,
                "Skin texture must be a readable " + width + "x" + height + " PNG: " + path);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    assertions++;
                    return;
                }
            }
        }
        throw new IllegalStateException("Skin texture is entirely transparent: " + path);
    }

    /** The skin's name, in the languages of the module, as the other add-ons' named skins carry theirs. */
    private static List<String> validateNames(Path resources, String root, String skin) throws Exception {
        List<String> files = new ArrayList<>();
        for (String language : SKIN_LANGUAGES) {
            String file = root + LANG + language + ".json";
            JsonObject lang = json(resources.resolve(file));
            String stand = lang.get("stand.geraddon.ger_stand").getAsString();
            check(!stand.isBlank() && stand.equals(lang.get("entity.geraddon.ger_stand").getAsString()),
                    skin + " has no name in " + language);
            files.add(file);
        }
        return files;
    }

    private static void validateRejections(JsonObject defaultModel, JsonObject packModel) {
        // rig 2 (the default) against pieces of the pack rig and the reverse
        ModelPart root = bake(defaultModel);
        part(root, "right_leg_xrot").x = -2;
        expectRejected(root, "rig 2 with one legacy leg");
        root = bake(defaultModel);
        part(root, "left_leg_bend").y = 6;
        expectRejected(root, "rig 2 with the pack's knee");
        root = bake(defaultModel);
        part(root, "left_item").z = -2;
        expectRejected(root, "rig 2 with the pack's item anchor");
        root = bake(defaultModel);
        part(root, "torso_bend").y = -3;
        expectRejected(root, "rig 2 with a moved torso_bend");
        root = bake(defaultModel);
        part(root, "body").y = 12;
        part(root, "left_leg_xrot").x = 1.9F;
        expectRejected(root, "rig 2 hips on the pack's body offset");
        root = bake(packModel);
        part(root, "left_item").z = -1.5F;
        expectRejected(root, "pack rig with rig 2's item anchor");
        root = bake(packModel);
        part(root, "left_leg_bend").y = 7;
        expectRejected(root, "pack rig with rig 2's knee");
        root = bake(packModel);
        part(root, "right_leg_xrot").x = -2;
        expectRejected(root, "pack body with one approved leg");
        root = bake(packModel);
        part(root, "left_leg_xrot").x = 2;
        expectRejected(root, "approved rig with one pack leg");
        root = bake(packModel);
        part(root, "torso_bend").y = -3;
        expectRejected(root, "pack rig with a moved torso_bend");
        root = bake(packModel);
        part(root, "body").y = 11;
        expectRejected(root, "unknown body offset");
        // the same faults on either rig
        for (JsonObject model : List.of(defaultModel, packModel)) {
            root = bake(model);
            part(root, "head_rot").yRot = 0.1F;
            expectRejected(root, "head rest rotation drift");
            root = bake(model);
            part(root, "head").children.remove("head_rot");
            expectRejected(root, "missing required bone");
            root = bake(model);
            ModelPart bend = part(root, "left_arm").children.remove("left_arm_bend");
            part(root, "body").children.put("left_arm_bend", bend);
            expectRejected(root, "wrong direct parent");
            expectRejected(withoutGeometry(bake(model)), "empty geometry");
        }
    }

    private static ModelPart withoutGeometry(ModelPart original) {
        Map<String, ModelPart> children = new HashMap<>();
        original.children.forEach((name, child) -> children.put(name, withoutGeometry(child)));
        ModelPart empty = new ModelPart(List.of(), children);
        empty.loadPose(original.getInitialPose());
        return empty;
    }

    private static void expectRejected(ModelPart invalid, String reason) {
        try {
            GerStandModelValidator.validate(invalid);
        }
        catch (IllegalStateException expected) {
            assertions++;
            return;
        }
        throw new IllegalStateException("Validator accepted " + reason + ".");
    }

    private static ModelPart part(ModelPart root, String name) {
        ModelPart direct = root.children.get(name);
        if (direct != null) return direct;
        for (ModelPart child : root.children.values()) {
            ModelPart found = part(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static ModelPart bake(JsonObject model) {
        ModelPart root = GerStandModelValidator.bakeAndValidate(
                ParseModEntityModel.parse(model, ModelFormat.GECKO));
        assertions++;
        return root;
    }

    private static JsonObject json(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static byte[] jarBytes(ZipFile jar, String path) throws Exception {
        check(jar.stream().filter(entry -> path.equals(entry.getName())).count() == 1,
                "Missing or duplicate JAR resource: " + path);
        ZipEntry entry = jar.getEntry(path);
        try (InputStream stream = jar.getInputStream(entry)) {
            return stream.readAllBytes();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        assertions++;
    }
}
