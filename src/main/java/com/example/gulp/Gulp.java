package com.example.gulp;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(Gulp.ID)
public class Gulp {
    public static final String ID = "gulp";

    /** Title of the item storage screen. The client looks for it to add the Back button. */
    public static final String STORAGE_TITLE = "Stomach Storage";

    /** Butter (churned from Breast Milk in the breast). Cum and Breast Milk are normal milk buckets with another name. */
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final RegistryObject<Item> BUTTER = ITEMS.register("butter", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(3).saturationMod(0.5f).build())));

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ID);
    public static final RegistryObject<SoundEvent> SWALLOW = sound("swallow");
    public static final RegistryObject<SoundEvent> DIGEST = sound("digest");
    public static final RegistryObject<SoundEvent> RELEASE = sound("release");
    public static final RegistryObject<SoundEvent> DIGEST_BUTTON = sound("digest_button");
    public static final RegistryObject<SoundEvent> SCREEN_LOOP = sound("screen_loop");

    // Louder twins (+6 dB) of the effects above. Minecraft can't play a file louder than it was recorded,
    // so volume settings above 100% switch to these. See ClientSounds.
    public static final RegistryObject<SoundEvent> SWALLOW_LOUD = sound("swallow_loud");
    public static final RegistryObject<SoundEvent> RELEASE_LOUD = sound("release_loud");
    public static final RegistryObject<SoundEvent> DIGEST_LOUD = sound("digest_loud");
    public static final RegistryObject<SoundEvent> DIGEST_BUTTON_LOUD = sound("digest_button_loud");

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, name)));
    }

    public Gulp() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        SOUNDS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(this::creativeTabs);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, GulpServerConfig.SPEC);
        modBus.addListener(this::commonSetup);
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(BUTTER);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
        event.enqueueWork(FlamevoreBridge::register); // Flamevore digests give Gulp XP (does nothing without Flamevore)
    }
}
