package org.halfheart.dimensiondaddy.commands;

import org.halfheart.dimensiondaddy.DimensionDaddy;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class ToggleDimensionCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("enableend").requires(source -> source.getEntity() == null)
                        .executes(context -> executeEnd(context, true))
        );

        dispatcher.register(
                CommandManager.literal("disableend").requires(source -> source.getEntity() == null)
                        .executes(context -> executeEnd(context, false))
        );

        dispatcher.register(
                CommandManager.literal("endstatus")
                        .executes(ToggleDimensionCommand::executeEndStatus)
        );

        dispatcher.register(
                CommandManager.literal("enablenether").requires(source -> source.getEntity() == null)
                        .executes(context -> executeNether(context, true))
        );

        dispatcher.register(
                CommandManager.literal("disablenether").requires(source -> source.getEntity() == null)
                        .executes(context -> executeNether(context, false))
        );

        dispatcher.register(
                CommandManager.literal("netherstatus")
                        .executes(ToggleDimensionCommand::executeNetherStatus)
        );
    }

    private static int executeEnd(CommandContext<ServerCommandSource> context, boolean enabled) {
        DimensionDaddy.setEndEnabled(enabled);
        String status = enabled ? "enabled" : "disabled";
        context.getSource().sendFeedback(
                () -> Text.literal("§aThe End dimension is now " + status),
                true
        );
        return 1;
    }

    private static int executeEndStatus(CommandContext<ServerCommandSource> context) {
        boolean enabled = DimensionDaddy.isEndEnabled();
        String status = enabled ? "§aenabled" : "§cdisabled";
        context.getSource().sendFeedback(
                () -> Text.literal("The End dimension is currently " + status),
                false
        );
        return 1;
    }

    private static int executeNether(CommandContext<ServerCommandSource> context, boolean enabled) {
        DimensionDaddy.setNetherEnabled(enabled);
        String status = enabled ? "enabled" : "disabled";
        context.getSource().sendFeedback(
                () -> Text.literal("§aThe Nether dimension is now " + status),
                true
        );
        return 1;
    }

    private static int executeNetherStatus(CommandContext<ServerCommandSource> context) {
        boolean enabled = DimensionDaddy.isNetherEnabled();
        String status = enabled ? "§aenabled" : "§cdisabled";
        context.getSource().sendFeedback(
                () -> Text.literal("The Nether dimension is currently " + status),
                false
        );
        return 1;
    }
}