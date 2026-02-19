package org.halfheart.dimensiondaddy.statemanagement;

import net.fabricmc.loader.api.FabricLoader;
import org.halfheart.dimensiondaddy.DimensionDaddy;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class DimensionStateManager {

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("DimensionDaddy")
            .resolve("dimensiondaddy.properties");

    public static void init() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
        } catch (IOException e) {
            DimensionDaddy.LOGGER.error("Failed to create DimensionDaddy config directory", e);
        }
        load();
    }

    private static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            props.load(reader);
            DimensionDaddy.setEndEnabled(Boolean.parseBoolean(props.getProperty("endEnabled", "true")));
            DimensionDaddy.setNetherEnabled(Boolean.parseBoolean(props.getProperty("netherEnabled", "true")));
            DimensionDaddy.LOGGER.info("DimensionDaddy config loaded from " + CONFIG_PATH);
        } catch (IOException e) {
            DimensionDaddy.LOGGER.error("Failed to load DimensionDaddy config", e);
        }
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("endEnabled", String.valueOf(DimensionDaddy.isEndEnabled()));
        props.setProperty("netherEnabled", String.valueOf(DimensionDaddy.isNetherEnabled()));
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            props.store(writer, "DimensionDaddy Configuration");
        } catch (IOException e) {
            DimensionDaddy.LOGGER.error("Failed to save DimensionDaddy config", e);
        }
    }
}