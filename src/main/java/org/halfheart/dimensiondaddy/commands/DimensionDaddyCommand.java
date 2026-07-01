package org.halfheart.dimensiondaddy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.halfheart.dimensiondaddy.DimensionDaddy;
import org.halfheart.dimensiondaddy.statemanagement.BackupRecord;
import org.halfheart.dimensiondaddy.statemanagement.DimensionStateManager;
import org.halfheart.dimensiondaddy.statemanagement.OperationResult;
import org.halfheart.dimensiondaddy.statemanagement.WorldDataManager;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DimensionDaddyCommand {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("DimensionDaddy")
                        .then(Commands.literal("enable")
                                .requires(src -> src.permissions().hasPermission(DimensionDaddyPermissions.OPERATOR))
                                .then(Commands.argument("dimension", StringArgumentType.word())
                                        .suggests(DimensionDaddyCommand::suggestDimensions)
                                        .executes(context -> executeEnableDisable(context, true))))
                        .then(Commands.literal("disable")
                                .requires(src -> src.permissions().hasPermission(DimensionDaddyPermissions.OPERATOR))
                                .then(Commands.argument("dimension", StringArgumentType.word())
                                        .suggests(DimensionDaddyCommand::suggestDimensions)
                                        .executes(context -> executeEnableDisable(context, false))))
                        .then(Commands.literal("status")
                                .executes(context -> executeStatus(context, "all"))
                                .then(Commands.argument("dimension", StringArgumentType.word())
                                        .suggests(DimensionDaddyCommand::suggestDimensions)
                                        .executes(context -> executeStatus(context, StringArgumentType.getString(context, "dimension")))))
                        .then(Commands.literal("backup")
                                .requires(src -> src.permissions().hasPermission(DimensionDaddyPermissions.OPERATOR))
                                .executes(context -> executeBackup(context, "all"))
                                .then(Commands.argument("dimension", StringArgumentType.word())
                                        .suggests(DimensionDaddyCommand::suggestDimensions)
                                        .executes(context -> executeBackup(context, StringArgumentType.getString(context, "dimension")))))
                        .then(Commands.literal("restore")
                                .requires(src -> src.permissions().hasPermission(DimensionDaddyPermissions.OPERATOR))
                                .then(Commands.argument("backupId", StringArgumentType.word())
                                        .suggests(DimensionDaddyCommand::suggestBackupIds)
                                        .executes(context -> executeRestore(context, StringArgumentType.getString(context, "backupId"), "all"))
                                        .then(Commands.argument("dimension", StringArgumentType.word())
                                                .suggests(DimensionDaddyCommand::suggestDimensions)
                                                .executes(context -> executeRestore(context, StringArgumentType.getString(context, "backupId"), StringArgumentType.getString(context, "dimension"))))))
                        .then(Commands.literal("reset")
                                .requires(src -> src.permissions().hasPermission(DimensionDaddyPermissions.OPERATOR))
                                .executes(context -> executeReset(context, "all"))
                                .then(Commands.argument("dimension", StringArgumentType.word())
                                        .suggests(DimensionDaddyCommand::suggestDimensions)
                                        .executes(context -> executeReset(context, StringArgumentType.getString(context, "dimension")))))
                        .then(Commands.literal("list")
                                .executes(DimensionDaddyCommand::executeList))
        );
    }

    private static List<String> resolveDimensions(CommandContext<CommandSourceStack> context, String input) {
        String normalized = input.toLowerCase();
        return switch (normalized) {
            case "overworld" -> List.of(WorldDataManager.OVERWORLD);
            case "nether" -> List.of(WorldDataManager.NETHER);
            case "end" -> List.of(WorldDataManager.END);
            case "all" -> List.of(WorldDataManager.OVERWORLD, WorldDataManager.NETHER, WorldDataManager.END);
            default -> null;
        };
    }

    private static boolean invalidDimension(CommandContext<CommandSourceStack> context, String input) {
        if (resolveDimensions(context, input) == null) {
            context.getSource().sendFailure(Component.literal("§cUnknown dimension '" + input + "'. Use overworld, nether, end, or all."));
            return true;
        }
        return false;
    }

    private static int executeEnableDisable(CommandContext<CommandSourceStack> context, boolean enabled) {
        String input = StringArgumentType.getString(context, "dimension");
        if (invalidDimension(context, input)) {
            return 0;
        }
        List<String> dimensions = resolveDimensions(context, input);
        for (String dimensionId : dimensions) {
            switch (dimensionId) {
                case WorldDataManager.OVERWORLD -> DimensionDaddy.setOverworldEnabled(enabled);
                case WorldDataManager.NETHER -> DimensionDaddy.setNetherEnabled(enabled);
                case WorldDataManager.END -> DimensionDaddy.setEndEnabled(enabled);
            }
        }
        String status = enabled ? "enabled" : "disabled";
        context.getSource().sendSuccess(
                () -> Component.literal("§a" + capitalizeJoin(dimensions) + " now " + status),
                true
        );
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context, String input) {
        if (invalidDimension(context, input)) {
            return 0;
        }
        List<String> dimensions = resolveDimensions(context, input);
        for (String dimensionId : dimensions) {
            boolean enabled = switch (dimensionId) {
                case WorldDataManager.OVERWORLD -> DimensionDaddy.isOverworldEnabled();
                case WorldDataManager.NETHER -> DimensionDaddy.isNetherEnabled();
                case WorldDataManager.END -> DimensionDaddy.isEndEnabled();
                default -> true;
            };
            String label = capitalize(dimensionId);
            String status = enabled ? "§aenabled" : "§cdisabled";
            context.getSource().sendSuccess(
                    () -> Component.literal("The " + label + " dimension is currently " + status),
                    false
            );
        }
        return 1;
    }

    private static int executeBackup(CommandContext<CommandSourceStack> context, String input) {
        if (invalidDimension(context, input)) {
            return 0;
        }
        List<String> dimensions = resolveDimensions(context, input);
        context.getSource().sendSuccess(
                () -> Component.literal("§eCreating backup of " + capitalizeJoin(dimensions) + " world data, this may take a moment for large worlds..."),
                true
        );
        OperationResult result = DimensionStateManager.backup(context.getSource().getServer(), dimensions);
        if (result.success()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("§aBackup created: " + result.message()),
                    true
            );
            return 1;
        }
        context.getSource().sendFailure(Component.literal("§c" + result.message()));
        return 0;
    }

    private static int executeRestore(CommandContext<CommandSourceStack> context, String backupId, String input) {
        if (invalidDimension(context, input)) {
            return 0;
        }
        List<String> dimensions = resolveDimensions(context, input);
        OperationResult result = DimensionStateManager.restore(context.getSource().getServer(), backupId, dimensions);
        if (!result.success()) {
            context.getSource().sendFailure(Component.literal("§c" + result.message()));
            return 0;
        }

        String[] parts = result.message().split("\\|");
        String resolvedId = parts[0];
        String worldData = parts.length > 2 ? parts[2].replace("worldData=", "") : "";
        String missing = parts.length > 3 ? parts[3].replace("missing=", "") : "";

        context.getSource().sendSuccess(
                () -> Component.literal("§aToggle state restored from backup '" + resolvedId + "'. A safety backup of the previous state was created automatically."),
                true
        );
        if (!worldData.isBlank()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("§eWorld data restore for [" + worldData + "] is scheduled. On a dedicated server, restart the server process to apply it. In singleplayer, save and quit to the title screen, then reload the world."),
                    true
            );
        }
        if (!missing.isBlank()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("§7No world data snapshot was found in this backup for [" + missing + "]; only the toggle state was restored for it."),
                    true
            );
        }
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> context, String input) {
        if (invalidDimension(context, input)) {
            return 0;
        }
        List<String> dimensions = resolveDimensions(context, input);
        OperationResult result = DimensionStateManager.reset(context.getSource().getServer(), dimensions);
        if (result.success()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("§aReset scheduled for [" + result.message() + "]. The toggle state is enabled now. The world itself will regenerate from the seed once the server process is restarted (dedicated server) or you save and quit to title and reload the world (singleplayer)."),
                    true
            );
            return 1;
        }
        context.getSource().sendFailure(Component.literal("§c" + result.message()));
        return 0;
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        List<BackupRecord> backups = DimensionStateManager.listBackups();

        boolean anyPending = false;
        for (String dimensionId : List.of(WorldDataManager.OVERWORLD, WorldDataManager.NETHER, WorldDataManager.END)) {
            if (WorldDataManager.hasPendingReset(dimensionId)) {
                anyPending = true;
                String label = capitalize(dimensionId);
                context.getSource().sendSuccess(
                        () -> Component.literal("§ePending: " + label + " is scheduled to reset to the seed's default state on next restart."),
                        false
                );
            }
        }
        for (var entry : WorldDataManager.readPendingRestores().entrySet()) {
            anyPending = true;
            String label = capitalize(entry.getKey());
            String backupId = entry.getValue();
            context.getSource().sendSuccess(
                    () -> Component.literal("§ePending: " + label + " is scheduled to restore world data from backup '" + backupId + "' on next restart."),
                    false
            );
        }
        if (anyPending) {
            context.getSource().sendSuccess(() -> Component.literal(""), false);
        }

        if (backups.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("§eNo dimension state backups have been created yet."),
                    false
            );
            return 1;
        }

        context.getSource().sendSuccess(
                () -> Component.literal("§6Dimension state backups (most recent first):"),
                false
        );
        for (BackupRecord record : backups) {
            String worldData = record.worldDataDimensions().isEmpty()
                    ? "toggle-only"
                    : "world data: " + String.join(",", record.worldDataDimensions());
            String line = "§7" + record.timestamp().format(DISPLAY_FORMAT)
                    + " §f[" + record.id() + "] §8(" + record.reason() + ", " + worldData + ")"
                    + " §fOverworld: " + (record.overworldEnabled() ? "§aenabled" : "§cdisabled")
                    + " §f| Nether: " + (record.netherEnabled() ? "§aenabled" : "§cdisabled")
                    + " §f| End: " + (record.endEnabled() ? "§aenabled" : "§cdisabled");
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestDimensions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        builder.suggest("overworld");
        builder.suggest("nether");
        builder.suggest("end");
        builder.suggest("all");
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestBackupIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        builder.suggest("latest");
        for (BackupRecord record : DimensionStateManager.listBackups()) {
            builder.suggest(record.id());
        }
        return builder.buildFuture();
    }

    private static String capitalize(String dimensionId) {
        return dimensionId.substring(0, 1).toUpperCase() + dimensionId.substring(1);
    }

    private static String capitalizeJoin(List<String> dimensions) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < dimensions.size(); i++) {
            if (i > 0) {
                builder.append(i == dimensions.size() - 1 ? " and " : ", ");
            }
            builder.append(capitalize(dimensions.get(i)));
        }
        return builder.toString();
    }
}
