package com.example.yinyang.client;

import com.example.yinyang.YinYangMod;
import com.example.yinyang.entity.YogiriTakatouEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class YogiriTakatouRenderer extends GeoEntityRenderer<YogiriTakatouEntity> {

    public YogiriTakatouRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                new ResourceLocation(YinYangMod.MODID, "yogiri_takatou")));
    }
}
