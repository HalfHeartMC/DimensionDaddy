package org.halfheart.dimensiondaddy;

import org.halfheart.dimensiondaddy.commands.DimensionDaddyCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import org.halfheart.dimensiondaddy.statemanagement.DimensionStateManager;
import org.halfheart.dimensiondaddy.statemanagement.WorldDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class DimensionDaddy implements ModInitializer {
    public static final String MOD_ID = "dimensiondaddy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean endEnabled = true;
    private static boolean netherEnabled = true;
    private static boolean overworldEnabled = true;

    private static final Map<String, String> pendingReport = new LinkedHashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("DimensionDaddy mod initialized!");
        DimensionStateManager.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            DimensionDaddyCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            WorldDataManager.processPendingOperations(server, DimensionStateManager.backupsDir());
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (DimensionStateManager.isFirstRun()) {
                try {
                    DimensionStateManager.createInitialBackup(server);
                } catch (Exception e) {
                    LOGGER.error("Failed to create the DimensionDaddy Initial Backup", e);
                }
            }

            Map<String, String> report = WorldDataManager.readAndClearReport();
            for (Map.Entry<String, String> entry : report.entrySet()) {
                String dimensionId = entry.getKey();
                String label = dimensionId.substring(0, 1).toUpperCase() + dimensionId.substring(1);
                String line = "[DimensionDaddy] " + label + ": " + entry.getValue();
                LOGGER.info(line);
            }
            pendingReport.putAll(report);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (pendingReport.isEmpty()) {
                return;
            }
            if (server.getPlayerList().getPlayers().isEmpty()) {
                return;
            }
            for (Map.Entry<String, String> entry : pendingReport.entrySet()) {
                String dimensionId = entry.getKey();
                String label = dimensionId.substring(0, 1).toUpperCase() + dimensionId.substring(1);
                String line = "[DimensionDaddy] " + label + ": " + entry.getValue();
                server.getPlayerList().broadcastSystemMessage(Component.literal(line), false);
            }
            pendingReport.clear();
        });
    }

    public static boolean isEndEnabled() {
        return endEnabled;
    }

    public static void setEndEnabled(boolean enabled) {
        endEnabled = enabled;
        LOGGER.info("End dimension is now " + (enabled ? "enabled" : "disabled"));
        DimensionStateManager.save();
    }

    public static boolean isNetherEnabled() {
        return netherEnabled;
    }

    public static void setNetherEnabled(boolean enabled) {
        netherEnabled = enabled;
        LOGGER.info("Nether dimension is now " + (enabled ? "enabled" : "disabled"));
        DimensionStateManager.save();
    }

    public static boolean isOverworldEnabled() {
        return overworldEnabled;
    }

    public static void setOverworldEnabled(boolean enabled) {
        overworldEnabled = enabled;
        LOGGER.info("Overworld dimension is now " + (enabled ? "enabled" : "disabled"));
        DimensionStateManager.save();
    }
}
