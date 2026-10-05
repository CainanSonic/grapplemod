package bat.grapnel.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = Grapnel.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class GrapnelClient {
    
    // We pass the event buses directly from the main class constructor to isolate client initialization safely
    public static void init(IEventBus modEventBus, ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        Grapnel.LOGGER.info("HELLO FROM CLIENT SETUP");
        Grapnel.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        // Register pulling model properties cleanly on the client thread thread-safely
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
            Grapnel.GRAPNEL_GUN.get(),
            ResourceLocation.fromNamespaceAndPath(Grapnel.MODID, "pulling"),
            (stack, level, entity, seed) -> {
                CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
                if (customData != null) {
                    CompoundTag tag = customData.copyTag();
                    return tag.getBoolean("IsPulling") ? 1.0F : 0.0F;
                }
                return 0.0F;
            }
        ));
    }
}
