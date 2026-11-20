package com.breakerch.yogiri.registry;

import com.breakerch.yogiri.YogiriTheEnd;
import com.breakerch.yogiri.entity.TheEndEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YogiriTheEnd.MODID);

    public static final RegistryObject<EntityType<TheEndEntity>> YOGIRI =
            ENTITY_TYPES.register("the_end", () -> EntityType.Builder
                    .of(TheEndEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("yogiri_the_end:the_end"));

    private ModEntities() {}

    @Mod.EventBusSubscriber(modid = YogiriTheEnd.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Attributes {
        @SubscribeEvent
        public static void onAttributeCreate(EntityAttributeCreationEvent event) {
            event.put(YOGIRI.get(), TheEndEntity.createAttributes().build());
        }
    }
}
