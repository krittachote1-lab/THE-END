package com.example.yinyang.client;

import com.example.yinyang.YinYangMod;
import com.example.yinyang.entity.GodOfYinyangEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GodOfYinyangRenderer extends GeoEntityRenderer<GodOfYinyangEntity> {

    public GodOfYinyangRenderer(EntityRendererProvider.Context context) {
        // Looks up:
        // assets/yinyang/geo/god_of_yinyang.geo.json
        // assets/yinyang/animations/god_of_yinyang.animation.json
        // assets/yinyang/textures/entity/god_of_yinyang.png
        super(context, new DefaultedEntityGeoModel<>(
                new ResourceLocation(YinYangMod.MODID, "god_of_yinyang")));
    }
}
