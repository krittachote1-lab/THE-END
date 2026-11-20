package com.breakerch.yogiri.client;

import com.breakerch.yogiri.YogiriTheEnd;
import com.breakerch.yogiri.client.render.TheEndRenderer;
import com.breakerch.yogiri.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = YogiriTheEnd.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.YOGIRI.get(), TheEndRenderer::new);
    }
}
