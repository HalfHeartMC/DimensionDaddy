package org.halfheart.dimensiondaddy.statemanagement;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.halfheart.dimensiondaddy.DimensionDaddy;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WorldDataManager {

    public static final String OVERWORLD = "overworld";
    public static final String NETHER = "nether";
    public static final String END = "end";

    private static final String[] DIMENSION_SUBFOLDERS = {"region", "entities", "poi", "data"};

    private static final int MAX_FILE_RETRIES = 5;

    private static final long RETRY_DELAY_MILLIS = 150;

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("DimensionDaddy");

    private static final Path PENDING_RESETS_PATH = CONFIG_DIR.resolve("pending-resets.properties");

    private static final Path PENDING_RESTORES_PATH = CONFIG_DIR.resolve("pending-restores.properties");

    private static final Path REPORT_PATH = CONFIG_DIR.resolve("last-operation-report.properties");

    private static final Object LOCK = new Object();

    private record FileOpResult(int succeeded, int failed) {
        static final FileOpResult EMPTY = new FileOpResult(0, 0);

        FileOpResult plus(FileOpResult other) {
            return new FileOpResult(succeeded + other.succeeded, failed + other.failed);
        }
    }

    public static Path dimensionRootPath(MinecraftServer server, String dimensionId) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path dimensionsRoot = root.resolve("dimensions").resolve("minecraft");
        return switch (dimensionId) {
            case OVERWORLD -> dimensionsRoot.resolve("overworld");
            case NETHER -> dimensionsRoot.resolve("the_nether");
            case END -> dimensionsRoot.resolve("the_end");
            default -> throw new IllegalArgumentException("Unknown dimension id: " + dimensionId);
        };
    }

    public static void flushDimension(MinecraftServer server, String dimensionId) {
        var key = switch (dimensionId) {
            case OVERWORLD -> Level.OVERWORLD;
            case NETHER -> Level.NETHER;
            case END -> Level.END;
            default -> throw new IllegalArgumentException("Unknown dimension id: " + dimensionId);
        };
        ServerLevel level = server.getLevel(key);
        if (level != null) {
            level.save(null, true, false);
        }
    }

    public static void copyDimensionData(MinecraftServer server, String dimensionId, Path destinationDir) throws IOException {
        flushDimension(server, dimensionId);
        Path sourceRoot = dimensionRootPath(server, dimensionId);
        Files.createDirectories(destinationDir);
        for (String subfolder : DIMENSION_SUBFOLDERS) {
            Path sourceSub = sourceRoot.resolve(subfolder);
            if (!Files.exists(sourceSub)) {
                continue;
            }
            Path destSub = destinationDir.resolve(subfolder);
            copyDirectoryRecursive(sourceSub, destSub);
        }
    }

    public static void schedulePendingReset(String dimensionId) {
        synchronized (LOCK) {
            Properties props = loadProperties(PENDING_RESETS_PATH);
            props.setProperty(dimensionId, "true");
            saveProperties(PENDING_RESETS_PATH, props, "DimensionDaddy Pending Resets");
        }
    }

    public static void schedulePendingRestore(String dimensionId, String backupId) {
        synchronized (LOCK) {
            Properties props = loadProperties(PENDING_RESTORES_PATH);
            props.setProperty(dimensionId, backupId);
            saveProperties(PENDING_RESTORES_PATH, props, "DimensionDaddy Pending Restores");
        }
    }

    public static void processPendingOperations(MinecraftServer server, Path backupsDir) {
        synchronized (LOCK) {
            Properties report = loadProperties(REPORT_PATH);
            processPendingResets(server, report);
            processPendingRestores(server, backupsDir, report);
            saveProperties(REPORT_PATH, report, "DimensionDaddy Last Operation Report");
        }
    }

    public static Map<String, String> readAndClearReport() {
        synchronized (LOCK) {
            Properties props = loadProperties(REPORT_PATH);
            Map<String, String> result = new LinkedHashMap<>();
            for (String key : props.stringPropertyNames()) {
                result.put(key, props.getProperty(key));
            }
            deleteQuietly(REPORT_PATH);
            return result;
        }
    }

    private static void processPendingResets(MinecraftServer server, Properties report) {
        Properties props = loadProperties(PENDING_RESETS_PATH);
        if (props.isEmpty()) {
            return;
        }
        for (String dimensionId : props.stringPropertyNames()) {
            DimensionDaddy.LOGGER.info("DimensionDaddy processing scheduled reset for " + dimensionId);
            try {
                Path root = dimensionRootPath(server, dimensionId);
                FileOpResult total = FileOpResult.EMPTY;
                for (String subfolder : DIMENSION_SUBFOLDERS) {
                    total = total.plus(deleteDirectoryRecursive(root.resolve(subfolder)));
                }
                String message = "Reset applied, deleted " + total.succeeded() + " file(s)"
                        + (total.failed() > 0 ? ", " + total.failed() + " file(s) could not be deleted" : "");
                report.setProperty(dimensionId, message);
                DimensionDaddy.LOGGER.info("DimensionDaddy reset result for " + dimensionId + ": " + message);
            } catch (Exception e) {
                String message = "Reset failed: " + e.getMessage();
                report.setProperty(dimensionId, message);
                DimensionDaddy.LOGGER.error("DimensionDaddy failed to apply scheduled reset for " + dimensionId, e);
            }
        }
        deleteQuietly(PENDING_RESETS_PATH);
    }

    private static void processPendingRestores(MinecraftServer server, Path backupsDir, Properties report) {
        Properties props = loadProperties(PENDING_RESTORES_PATH);
        if (props.isEmpty()) {
            return;
        }
        for (String dimensionId : props.stringPropertyNames()) {
            String backupId = props.getProperty(dimensionId);
            DimensionDaddy.LOGGER.info("DimensionDaddy processing scheduled restore for " + dimensionId + " from backup " + backupId);
            try {
                Path root = dimensionRootPath(server, dimensionId);
                FileOpResult deleteTotal = FileOpResult.EMPTY;
                for (String subfolder : DIMENSION_SUBFOLDERS) {
                    deleteTotal = deleteTotal.plus(deleteDirectoryRecursive(root.resolve(subfolder)));
                }

                Path backupDimDir = backupsDir.resolve(backupId).resolve(dimensionId);
                if (!Files.exists(backupDimDir)) {
                    String message = "Restore failed: backup directory not found at " + backupDimDir;
                    report.setProperty(dimensionId, message);
                    DimensionDaddy.LOGGER.error("DimensionDaddy " + message);
                    continue;
                }

                FileOpResult copyTotal = FileOpResult.EMPTY;
                for (String subfolder : DIMENSION_SUBFOLDERS) {
                    Path source = backupDimDir.resolve(subfolder);
                    if (Files.exists(source)) {
                        copyTotal = copyTotal.plus(copyDirectoryRecursive(source, root.resolve(subfolder)));
                    }
                }

                String message = "Restore from '" + backupId + "' applied, deleted " + deleteTotal.succeeded()
                        + " file(s), copied " + copyTotal.succeeded() + " file(s)"
                        + (deleteTotal.failed() + copyTotal.failed() > 0
                                ? ", " + (deleteTotal.failed() + copyTotal.failed()) + " file(s) had errors"
                                : "");
                report.setProperty(dimensionId, message);
                DimensionDaddy.LOGGER.info("DimensionDaddy restore result for " + dimensionId + ": " + message);
            } catch (Exception e) {
                String message = "Restore failed: " + e.getMessage();
                report.setProperty(dimensionId, message);
                DimensionDaddy.LOGGER.error("DimensionDaddy failed to apply scheduled restore for " + dimensionId, e);
            }
        }
        deleteQuietly(PENDING_RESTORES_PATH);
    }

    public static Map<String, String> readPendingRestores() {
        Properties props = loadProperties(PENDING_RESTORES_PATH);
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            result.put(key, props.getProperty(key));
        }
        return result;
    }

    public static boolean hasPendingReset(String dimensionId) {
        return loadProperties(PENDING_RESETS_PATH).containsKey(dimensionId);
    }

    private static FileOpResult copyDirectoryRecursive(Path source, Path destination) throws IOException {
        Files.createDirectories(destination);
        int succeeded = 0;
        int failed = 0;
        try (Stream<Path> stream = Files.walk(source)) {
            var files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path path : files) {
                Path relative = source.relativize(path);
                Path target = destination.resolve(relative.toString());
                boolean ok = false;
                IOException lastError = null;
                for (int attempt = 1; attempt <= MAX_FILE_RETRIES && !ok; attempt++) {
                    try {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                        ok = true;
                    } catch (IOException e) {
                        lastError = e;
                        sleepBeforeRetry();
                    }
                }
                if (ok) {
                    succeeded++;
                } else {
                    failed++;
                    DimensionDaddy.LOGGER.error("Failed to copy " + path + " to " + target, lastError);
                }
            }
        }
        return new FileOpResult(succeeded, failed);
    }

    private static FileOpResult deleteDirectoryRecursive(Path target) throws IOException {
        if (!Files.exists(target)) {
            return FileOpResult.EMPTY;
        }
        int succeeded = 0;
        int failed = 0;
        try (Stream<Path> stream = Files.walk(target)) {
            var paths = stream
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .collect(Collectors.toList());
            for (Path path : paths) {
                boolean ok = false;
                IOException lastError = null;
                for (int attempt = 1; attempt <= MAX_FILE_RETRIES && !ok; attempt++) {
                    try {
                        Files.delete(path);
                        ok = true;
                    } catch (IOException e) {
                        lastError = e;
                        sleepBeforeRetry();
                    }
                }
                if (ok) {
                    succeeded++;
                } else {
                    failed++;
                    DimensionDaddy.LOGGER.error("Failed to delete " + path, lastError);
                }
            }
        }
        return new FileOpResult(succeeded, failed);
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Properties loadProperties(Path path) {
        Properties props = new Properties();
        if (!Files.exists(path)) {
            return props;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            props.load(reader);
        } catch (IOException e) {
            DimensionDaddy.LOGGER.error("Failed to read " + path, e);
        }
        return props;
    }

    private static void saveProperties(Path path, Properties props, String comment) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                props.store(writer, comment);
            }
        } catch (IOException e) {
            DimensionDaddy.LOGGER.error("Failed to write " + path, e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            DimensionDaddy.LOGGER.error("Failed to delete " + path, e);
        }
    }
}
