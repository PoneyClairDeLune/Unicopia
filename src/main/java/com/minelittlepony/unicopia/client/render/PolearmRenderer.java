package com.minelittlepony.unicopia.client.render;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.DynamicItemRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ClampedModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.model.*;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.registry.Registries;

public class PolearmRenderer implements DynamicItemRenderer, ClampedModelPredicateProvider {
    private static final PolearmRenderer INSTANCE = new PolearmRenderer();
    private static final Identifier THROWING = Identifier.ofVanilla("throwing");

    private final ModelPart model = getTexturedModelData().createModel();

    public static void register(Item...items) {
        for (Item item : items) {
            BuiltinItemRendererRegistry.INSTANCE.register(item, INSTANCE);
            ModelPredicateProviderRegistry.register(item, THROWING, INSTANCE);
        }
        ModelLoadingPlugin.register(context -> {
            for (Item item : items) {
                context.addModels(getModelId(item));
            }
        });
    }

    static Identifier getModelId(ItemConvertible item) {
        Identifier id = Registries.ITEM.getId(item.asItem());
        return id.withPath(p -> "item/" + p + "_in_inventory");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        int y = -9;

        ModelPartData pole = root.addChild("pole", ModelPartBuilder.create().uv(0, 6).cuboid(-0.5f, y, -0.5f, 1, 25, 1), ModelTransform.NONE);
        pole.addChild("base", ModelPartBuilder.create().uv(4, 0).cuboid(-1.5f, y - 2, -0.5f, 3, 2, 1), ModelTransform.NONE);
        pole.addChild("head", ModelPartBuilder.create().uv(0, 0).cuboid(-0.5f, y - 6, -0.5f, 1, 4, 1), ModelTransform.NONE);
        return TexturedModelData.of(data, 32, 32);
    }

    @Override
    public float unclampedCall(ItemStack stack, ClientWorld world, LivingEntity entity, int seed) {
        return entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1 : 0;
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {

        if (mode == ModelTransformationMode.GUI || mode == ModelTransformationMode.GROUND || mode == ModelTransformationMode.FIXED) {
            // render as normal sprite
            ItemRenderer renderer = MinecraftClient.getInstance().getItemRenderer();

            BakedModel model = renderer.getModels().getModelManager().getModel(getModelId(stack.getItem()));
            matrices.pop();
            matrices.push();
            renderer.renderItem(stack, mode, false, matrices, vertexConsumers, light, overlay, model);
            matrices.pop();
            matrices.push();
        } else {
            matrices.push();
            if (mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND) {
                int swap = mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND ? -1 : 1;
                matrices.scale(1.5F, -1.5F, -1.5F);
                float offsetX = swap * 0.05F;
                matrices.translate(offsetX, 0, 0.05F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-30 * swap), offsetX, 0.5F, offsetX);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-30 * swap), offsetX, 0.5F, offsetX);
            } else {
                matrices.scale(1, -1, -1);
            }
            Identifier id = Registries.ITEM.getId(stack.getItem());
            Identifier texture = id.withPath(p -> "textures/entity/polearm/" + p + ".png");
            model.render(matrices, ItemRenderer.getDirectItemGlintConsumer(vertexConsumers, RenderLayer.getEntitySolid(texture), false, stack.hasGlint()), light, overlay, Colors.WHITE);
            matrices.pop();
        }
    }
}
