package com.breakerch.yogiri;

import com.breakerch.yogiri.registry.ModEntities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(YogiriTheEnd.MODID)
public class YogiriTheEnd {
    public static final String MODID = "yogiri_the_end";

    public YogiriTheEnd() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITY_TYPES.register(bus);
    }
}
