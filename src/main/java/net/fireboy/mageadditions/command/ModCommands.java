package net.fireboy.mageadditions.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.registry.ModItems;
import net.fireboy.mageadditions.registry.ModSpells;
import net.fireboy.mageadditions.server.domain.DomainConfig;
import net.fireboy.mageadditions.spell.CounterspellHandler;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ModCommands {
    private ModCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("mageadditions")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    CastTimeOverrides.ReloadResult result = CastTimeOverrides.reload();
                                    DomainConfig.reload();

                                    if (result.success()) {
                                        context.getSource().sendSuccess(
                                                () -> Component.literal(
                                                        "Mage Additions reloaded: "
                                                                + result.loadedRules()
                                                                + " active cast-time rule(s), "
                                                                + result.skippedRules()
                                                                + " skipped. Counterspell: "
                                                                + CounterspellHandler.describe()
                                                                + "."
                                                ),
                                                true
                                        );
                                        return 1;
                                    }

                                    context.getSource().sendFailure(
                                            Component.literal(
                                                    "Mage Additions reload failed. Previous rules are still active; check latest.log."
                                            )
                                    );
                                    return 0;
                                })
                        )
                        .then(Commands.literal("domainrelic")
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayer();
                                            if (player == null) {
                                                context.getSource().sendFailure(
                                                        Component.literal("This command must be run by a player.")
                                                );
                                                return 0;
                                            }

                                            int spellLevel = IntegerArgumentType.getInteger(context, "level");
                                            ItemStack stack = new ItemStack(ModItems.DOMAIN_RELIC.get());

                                            ISpellContainerMutable container =
                                                    ISpellContainer.create(1, true, false).mutableCopy();
                                            container.addSpell(ModSpells.DOMAIN.get(), spellLevel, true);
                                            ISpellContainer.set(stack, container.toImmutable());

                                            if (!player.addItem(stack)) {
                                                player.drop(stack, false);
                                            }

                                            context.getSource().sendSuccess(
                                                    () -> Component.literal(
                                                            "Given Domain Relic with Domain level " + spellLevel + "."
                                                    ),
                                                    false
                                            );
                                            return 1;
                                        })
                                )
                        )
        );
    }
}
