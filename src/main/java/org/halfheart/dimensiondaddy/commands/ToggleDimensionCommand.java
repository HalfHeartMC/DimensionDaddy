package org.halfheart.dimensiondaddy.commands;

import org.halfheart.dimensiondaddy.DimensionDaddy;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public class ToggleDimensionCommand {

    private static final Permission OP_LEVEL = new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("enableend")
                        .requires(src -> src.permissions().hasPermission(OP_LEVEL))
                        .executes(context -> executeEnd(context, true))
        );

        dispatcher.register(
                Commands.literal("disableend")
                        .requires(src -> src.permissions().hasPermission(OP_LEVEL))
                        .executes(context -> executeEnd(context, false))
        );

        dispatcher.register(
                Commands.literal("endstatus")
                        .executes(ToggleDimensionCommand::executeEndStatus)
        );

        dispatcher.register(
                Commands.literal("enablenether")
                        .requires(src -> src.permissions().hasPermission(OP_LEVEL))
                        .executes(context -> executeNether(context, true))
        );

        dispatcher.register(
                Commands.literal("disablenether")
                        .requires(src -> src.permissions().hasPermission(OP_LEVEL))
                        .executes(context -> executeNether(context, false))
        );

        dispatcher.register(
                Commands.literal("netherstatus")
                        .executes(ToggleDimensionCommand::executeNetherStatus)
        );
    }

    private static int executeEnd(CommandContext<CommandSourceStack> context, boolean enabled) {
        DimensionDaddy.setEndEnabled(enabled);
        String status = enabled ? "enabled" : "disabled";
        context.getSource().sendSuccess(
                () -> Component.literal("§aThe End dimension is now " + status),
                true
        );
        return 1;
    }

    private static int executeEndStatus(CommandContext<CommandSourceStack> context) {
        boolean enabled = DimensionDaddy.isEndEnabled();
        String status = enabled ? "§aenabled" : "§cdisabled";
        context.getSource().sendSuccess(
                () -> Component.literal("The End dimension is currently " + status),
                false
        );
        return 1;
    }

    private static int executeNether(CommandContext<CommandSourceStack> context, boolean enabled) {
        DimensionDaddy.setNetherEnabled(enabled);
        String status = enabled ? "enabled" : "disabled";
        context.getSource().sendSuccess(
                () -> Component.literal("§aThe Nether dimension is now " + status),
                true
        );
        return 1;
    }

    private static int executeNetherStatus(CommandContext<CommandSourceStack> context) {
        boolean enabled = DimensionDaddy.isNetherEnabled();
        String status = enabled ? "§aenabled" : "§cdisabled";
        context.getSource().sendSuccess(
                () -> Component.literal("The Nether dimension is currently " + status),
                false
        );
        return 1;
    }
}