package org.halfheart.dimensiondaddy;

import org.halfheart.dimensiondaddy.commands.ToggleDimensionCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.halfheart.dimensiondaddy.statemanagement.DimensionStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DimensionDaddy implements ModInitializer {
    public static final String MOD_ID = "dimensiondaddy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean endEnabled = true;
    private static boolean netherEnabled = true;

    @Override
    public void onInitialize() {
        LOGGER.info("DimensionDaddy mod initialized!");
        DimensionStateManager.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ToggleDimensionCommand.register(dispatcher);
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
}