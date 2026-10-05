package bat.grapnel.mod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

@Mod(Grapnel.MODID)
public class Grapnel {
    
    public static final String MODID = "grapnel";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Deferred Registers
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, MODID);

    // Block Registers
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);
    
    // Item Registers
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));
    
    public static final DeferredItem<GrapnelGunItem> GRAPNEL_GUN = ITEMS.register("grapnel_gun", () -> new GrapnelGunItem(new Item.Properties().stacksTo(1)));

    // Particle Registers
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAPNEL_MARKER = PARTICLES.register("grapnel_marker", () -> new SimpleParticleType(false));

    // Creative Tabs
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.grapnel"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get());
                output.accept(GRAPNEL_GUN.get());
            }).build());

    public Grapnel(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        PARTICLES.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Safe physical client check before interacting with the GrapnelClient class
        if (net.neoforged.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
            GrapnelClient.init(modEventBus, modContainer);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());
        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    public void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
            GRAPNEL_GUN.get(),
            ResourceLocation.fromNamespaceAndPath(MODID, "pulling"),
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

    @EventBusSubscriber(modid = Grapnel.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(GRAPNEL_MARKER.get(), GrapnelMarkerParticle.Provider::new);
        }
    }

    @EventBusSubscriber(modid = Grapnel.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        private static final ResourceLocation MARKER_TEXTURE = ResourceLocation.fromNamespaceAndPath(Grapnel.MODID, "textures/particle/grapnel_marker.png");

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            if (GrapnelClientTracker.renderTargetPos == null) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof GrapnelGunItem || mc.player.getOffhandItem().getItem() instanceof GrapnelGunItem)) {
                return;
            }

            Vec3 target = GrapnelClientTracker.renderTargetPos;
            Vec3 cameraPos = event.getCamera().getPosition();
            
            Vector3f lookDir = mc.gameRenderer.getMainCamera().getLookVector();
            Vec3 fixedTarget = target.add(new Vec3(lookDir.x(), lookDir.y(), lookDir.z()).scale(-0.2D));

            double renderX = fixedTarget.x - cameraPos.x;
            double renderY = fixedTarget.y - cameraPos.y;
            double renderZ = fixedTarget.z - cameraPos.z;

            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.translate(renderX, renderY, renderZ);
            poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, MARKER_TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            float size = 0.5F; 
            Matrix4f matrix = poseStack.last().pose();

            bufferBuilder.addVertex(matrix, -size, -size, 0).setUv(0, 1).setColor(255, 255, 255, 255);
            bufferBuilder.addVertex(matrix, size, -size, 0).setUv(1, 1).setColor(255, 255, 255, 255);
            bufferBuilder.addVertex(matrix, size, size, 0).setUv(1, 0).setColor(255, 255, 255, 255);
            bufferBuilder.addVertex(matrix, -size, size, 0).setUv(0, 0).setColor(255, 255, 255, 255);

            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest(); 
            
            poseStack.popPose();
        }
    }
}
