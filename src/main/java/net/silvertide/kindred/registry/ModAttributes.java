package net.silvertide.kindred.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.silvertide.kindred.Kindred;

@EventBusSubscriber(modid = Kindred.MODID)
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, Kindred.MODID);

    public static final DeferredHolder<Attribute, Attribute> MAX_COMPANION_BONDS =
            ATTRIBUTES.register("max_companion_bonds",
                    () -> (Attribute) new RangedAttribute("attribute.kindred.max_companion_bonds", 10.0D, 0.0D, 64.0D)
                            .setSyncable(true));

    @SubscribeEvent
    public static void onModifyEntityAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, MAX_COMPANION_BONDS);
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
    }

    private ModAttributes() {}
}
