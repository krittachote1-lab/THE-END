package com.example.yinyang;

import com.example.yinyang.entity.GodOfYinyangEntity;
import com.example.yinyang.entity.YogiriTakatouEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import software.bernie.geckolib.GeckoLib;

@Mod(YinYangMod.MODID)
public class YinYangMod {
    public static final String MODID = "yinyang";

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<EntityType<GodOfYinyangEntity>> GOD_OF_YINYANG =
            ENTITY_TYPES.register("god_of_yinyang", () -> EntityType.Builder
                    .of(GodOfYinyangEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .fireImmune()
                    .build(MODID + ":god_of_yinyang"));

    public static final RegistryObject<Item> GOD_OF_YINYANG_SPAWN_EGG =
            ITEMS.register("god_of_yinyang_spawn_egg", () -> new ForgeSpawnEggItem(
                    GOD_OF_YINYANG, 0x000000, 0xFFFFFF, new Item.Properties()));

    public static final RegistryObject<EntityType<YogiriTakatouEntity>> YOGIRI =
            ENTITY_TYPES.register("yogiri_takatou", () -> EntityType.Builder
                    .of(YogiriTakatouEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .fireImmune()
                    .build(MODID + ":yogiri_takatou"));

    public static final RegistryObject<Item> YOGIRI_SPAWN_EGG =
            ITEMS.register("yogiri_takatou_spawn_egg", () -> new ForgeSpawnEggItem(
                    YOGIRI, 0x1A0A24, 0xB00020, new Item.Properties()));

    public YinYangMod() {
        GeckoLib.initialize();

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITY_TYPES.register(bus);
        ITEMS.register(bus);
        bus.addListener(this::onEntityAttributes);
    }

    private void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(GOD_OF_YINYANG.get(), GodOfYinyangEntity.createAttributes().build());
        event.put(YOGIRI.get(), YogiriTakatouEntity.createAttributes().build());
    }
}
