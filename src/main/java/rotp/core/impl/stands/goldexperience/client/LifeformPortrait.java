package rotp.core.impl.stands.goldexperience.client;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import org.joml.Quaternionf;

import rotp.core.core.JojoMod;
import rotp.core.util.mc.entitysubtype.EntitySubtype;
import rotp.core.util.mc.entitysubtype.EntityTypeToInstance;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/** A lifeform's portrait in the menu: the creature itself drawn small, as an inventory draws its player. */
final class LifeformPortrait {
    // the owner: a lifeform that cannot be reverted from where the user is shows grey
    private static final int GREY_WASH = 0xB88A8F99;
    private static final float FACING = 205.0F;
    private static final float CAMERA_PITCH = 10.0F;
    private static final Set<ResourceLocation> UNDRAWABLE = new HashSet<>();

    private LifeformPortrait() {}

    /**
     * @param live the lifeform as this client has it, if it does; else a creature of its kind is drawn
     */
    static void render(GuiGraphics graphics, @Nullable Entity live, @Nullable ResourceLocation typeId,
            int x, int y, int size, boolean grey, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        EntityType<?> type = typeId != null ? BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null) : null;
        Entity entity = live;
        if (entity == null && type != null && mc.level != null && !UNDRAWABLE.contains(typeId)) {
            entity = EntityTypeToInstance.getEntityInstance(EntitySubtype.base(type), mc.level);
        }
        graphics.enableScissor(x, y, x + size, y + size);
        boolean drawn = entity != null && draw(graphics, entity, x, y, size, live != null ? partialTick : 1.0F);
        graphics.disableScissor();
        if (!drawn) {
            drawEgg(graphics, type, x, y, size);
        }
        if (grey) {
            graphics.pose().pushPose();
            // over the creature, which the inventory drawing puts in front of the page
            graphics.pose().translate(0, 0, 300);
            graphics.fill(x, y, x + size, y + size, GREY_WASH);
            graphics.pose().popPose();
        }
    }

    private static boolean draw(GuiGraphics graphics, Entity entity, int x, int y, int size, float partialTick) {
        float height = entity.getBbHeight();
        float extent = Math.max(height, entity.getBbWidth() * 1.3F);
        if (extent <= 0.0F) {
            return false;
        }
        float scale = size * 0.8F / extent;
        Quaternionf camera = new Quaternionf().rotateX(CAMERA_PITCH * Mth.DEG_TO_RAD);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).mul(camera);

        float yRot = entity.getYRot();
        float yRotO = entity.yRotO;
        float xRot = entity.getXRot();
        float xRotO = entity.xRotO;
        LivingEntity living = entity instanceof LivingEntity livingEntity ? livingEntity : null;
        float body = living != null ? living.yBodyRot : 0.0F;
        float bodyO = living != null ? living.yBodyRotO : 0.0F;
        float head = living != null ? living.yHeadRot : 0.0F;
        float headO = living != null ? living.yHeadRotO : 0.0F;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        graphics.pose().pushPose();
        try {
            entity.setYRot(FACING);
            entity.yRotO = FACING;
            entity.setXRot(0.0F);
            entity.xRotO = 0.0F;
            if (living != null) {
                living.yBodyRot = FACING;
                living.yBodyRotO = FACING;
                living.yHeadRot = FACING;
                living.yHeadRotO = FACING;
            }
            graphics.pose().translate(x + size / 2.0F, y + size / 2.0F, 50.0F);
            graphics.pose().scale(scale, scale, -scale);
            graphics.pose().translate(0.0F, height / 2.0F, 0.0F);
            graphics.pose().mulPose(pose);
            Lighting.setupForEntityInInventory();
            dispatcher.overrideCameraOrientation(camera.conjugate(new Quaternionf()).rotateY((float) Math.PI));
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, partialTick, graphics.pose(),
                    graphics.bufferSource(), LightTexture.FULL_BRIGHT));
            graphics.flush();
            return true;
        }
        catch (RuntimeException e) {
            ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (UNDRAWABLE.add(typeId)) {
                JojoMod.getLogger().warn("Could not draw a {} in the Gold Experience lifeform menu; showing its egg.", typeId, e);
            }
            return false;
        }
        finally {
            dispatcher.setRenderShadow(true);
            graphics.pose().popPose();
            Lighting.setupFor3DItems();
            entity.setYRot(yRot);
            entity.yRotO = yRotO;
            entity.setXRot(xRot);
            entity.xRotO = xRotO;
            if (living != null) {
                living.yBodyRot = body;
                living.yBodyRotO = bodyO;
                living.yHeadRot = head;
                living.yHeadRotO = headO;
            }
        }
    }

    private static void drawEgg(GuiGraphics graphics, @Nullable EntityType<?> type, int x, int y, int size) {
        SpawnEggItem egg = SpawnEggItem.byId(type);
        int itemX = x + (size - 16) / 2;
        int itemY = y + (size - 16) / 2;
        if (egg != null) {
            graphics.renderItem(new ItemStack(egg), itemX, itemY);
        }
        else if (type != null) {
            EntityTypeIcon.renderIcon(type, graphics.pose(), itemX, itemY);
        }
    }
}
