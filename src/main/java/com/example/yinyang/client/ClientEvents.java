package com.example.yinyang.client;

import com.example.yinyang.YinYangMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = YinYangMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(YinYangMod.GOD_OF_YINYANG.get(), GodOfYinyangRenderer::new);
        event.registerEntityRenderer(YinYangMod.YOGIRI.get(), YogiriTakatouRenderer::new);
    }
}
