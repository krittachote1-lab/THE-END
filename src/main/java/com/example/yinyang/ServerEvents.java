package com.example.yinyang;

import com.example.yinyang.entity.GodOfYinyangEntity;
import com.example.yinyang.entity.YogiriTakatouEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = YinYangMod.MODID)
public class ServerEvents {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel server) {
            GodOfYinyangEntity.watchdog(server);
            YogiriTakatouEntity.watchdog(server);
        }
    }
}
