package com.breakerch.yogiri.client.render;

import com.breakerch.yogiri.YogiriTheEnd;
import com.breakerch.yogiri.entity.TheEndEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class TheEndRenderer extends MobRenderer<TheEndEntity, HumanoidModel<TheEndEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(YogiriTheEnd.MODID, "textures/entity/the_end.png");

    public TheEndRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(TheEndEntity entity) {
        return TEXTURE;
    }
}
