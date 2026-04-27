package net.silvertide.petsummon.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.silvertide.petsummon.PetSummon;
import net.silvertide.petsummon.attachment.BondRoster;
import net.silvertide.petsummon.attachment.Bonded;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, PetSummon.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BondRoster>> BOND_ROSTER =
            ATTACHMENTS.register("bond_roster",
                    () -> AttachmentType.builder(() -> BondRoster.EMPTY)
                            .serialize(BondRoster.CODEC)
                            .copyOnDeath()
                            .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Bonded>> BONDED =
            ATTACHMENTS.register("bonded",
                    () -> AttachmentType.builder(() -> Bonded.EMPTY)
                            .serialize(Bonded.CODEC)
                            .build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    private ModAttachments() {}
}
