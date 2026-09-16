package net.fireboy.mageadditions.command;

import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.spell.CounterspellHandler;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
        );
    }
}
