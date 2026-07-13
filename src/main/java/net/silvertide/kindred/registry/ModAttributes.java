package net.silvertide.kindred.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.silvertide.kindred.Kindred;

@Mod.EventBusSubscriber(modid = Kindred.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Kindred.MODID);

    public static final RegistryObject<Attribute> MAX_COMPANION_BONDS =
            ATTRIBUTES.register("max_companion_bonds",
                    () -> new RangedAttribute("attribute.kindred.max_companion_bonds", 10.0D, 0.0D, 64.0D)
                            .setSyncable(true));

    @SubscribeEvent
    public static void onModifyEntityAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, MAX_COMPANION_BONDS.get());
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
    }

    private ModAttributes() {}
}
