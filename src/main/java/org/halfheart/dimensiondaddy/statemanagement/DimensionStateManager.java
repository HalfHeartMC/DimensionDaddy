package org.halfheart.dimensiondaddy.statemanagement;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.halfheart.dimensiondaddy.DimensionDaddy;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DimensionStateManager {

    public static final String INITIAL_BACKUP_ID = "initial-backup";

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("DimensionDaddy");

    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("dimensiondaddy.properties");

    private static final Path BACKUP_DIR = CONFIG_DIR.resolve("backups");

    private static final int MAX_BACKUPS = 20;

    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final Object LOCK = new Object();

    public static void init() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.createDirectories(BACKUP_DIR);
        } catch (IOException e) {
            DimensionDaddy.LOGGER.error("Failed to create DimensionDaddy config directories", e);
        }
        load();
    }

    public static Path backupsDir() {
        return BACKUP_DIR;
    }

    public static boolean isFirstRun() {
        return !Files.exists(BACKUP_DIR.resolve(INITIAL_BACKUP_ID).resolve("backup.properties"));
    }

    public static void createInitialBackup(MinecraftServer server) {
        synchronized (LOCK) {
            writeBackup(INITIAL_BACKUP_ID, "auto", "Initial Backup", List.of(WorldDataManager.OVERWORLD, WorldDataManager.NETHER, WorldDataManager.END), server);
            DimensionDaddy.LOGGER.info("DimensionDaddy created the Initial Backup");
        }
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
            DimensionDaddy.setOverworldEnabled(Boolean.parseBoolean(props.getProperty("overworldEnabled", "true")));
            DimensionDaddy.LOGGER.info("DimensionDaddy config loaded from " + CONFIG_PATH);
        } catch (IOException e) {
            DimensionDaddy.LOGGER.error("Failed to load DimensionDaddy config", e);
        }
    }

    public static void save() {
        synchronized (LOCK) {
            Properties props = new Properties();
            props.setProperty("endEnabled", String.valueOf(DimensionDaddy.isEndEnabled()));
            props.setProperty("netherEnabled", String.valueOf(DimensionDaddy.isNetherEnabled()));
            props.setProperty("overworldEnabled", String.valueOf(DimensionDaddy.isOverworldEnabled()));
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                props.store(writer, "DimensionDaddy Configuration");
            } catch (IOException e) {
                DimensionDaddy.LOGGER.error("Failed to save DimensionDaddy config", e);
            }
        }
    }

    public static OperationResult backup(MinecraftServer server, List<String> dimensions) {
        synchronized (LOCK) {
            try {
                String id = writeBackup(null, "manual", "Requested by command", dimensions, server);
                return OperationResult.success(id);
            } catch (RuntimeException e) {
                DimensionDaddy.LOGGER.error("Failed to create DimensionDaddy backup", e);
                return OperationResult.failure("Failed to create backup: " + e.getMessage());
            }
        }
    }

    public static OperationResult reset(MinecraftServer server, List<String> dimensions) {
        synchronized (LOCK) {
            for (String dimensionId : dimensions) {
                applyEnabled(dimensionId, true);
                WorldDataManager.schedulePendingReset(dimensionId);
            }
            DimensionDaddy.LOGGER.info("DimensionDaddy scheduled reset for " + dimensions);
            return OperationResult.success(String.join(", ", dimensions));
        }
    }

    public static OperationResult restore(MinecraftServer server, String backupId, List<String> dimensions) {
        synchronized (LOCK) {
            if (backupId == null || backupId.isBlank()) {
                return OperationResult.failure("A backup id is required.");
            }
            String resolvedId = backupId;
            if ("latest".equalsIgnoreCase(backupId)) {
                List<BackupRecord> backups = listBackups();
                if (backups.isEmpty()) {
                    return OperationResult.failure("No backups are available to restore.");
                }
                resolvedId = backups.get(0).id();
            } else if (!resolvedId.matches("[A-Za-z0-9_-]+")) {
                return OperationResult.failure("Invalid backup id format.");
            }

            Path backupPath = BACKUP_DIR.resolve(resolvedId).resolve("backup.properties");
            if (!Files.exists(backupPath)) {
                return OperationResult.failure("No backup found with id '" + resolvedId + "'.");
            }

            BackupRecord record = readBackupRecord(backupPath);
            if (record == null) {
                return OperationResult.failure("Backup '" + resolvedId + "' is corrupted and cannot be restored.");
            }

            List<String> appliedFlags = new ArrayList<>();
            List<String> scheduledWorldData = new ArrayList<>();
            List<String> missingWorldData = new ArrayList<>();

            for (String dimensionId : dimensions) {
                applyEnabled(dimensionId, flagForDimension(record, dimensionId));
                appliedFlags.add(dimensionId);
                if (record.hasWorldData(dimensionId)) {
                    WorldDataManager.schedulePendingRestore(dimensionId, resolvedId);
                    scheduledWorldData.add(dimensionId);
                } else {
                    missingWorldData.add(dimensionId);
                }
            }

            DimensionDaddy.LOGGER.info("DimensionDaddy restoring from backup " + resolvedId
                    + " flags=" + appliedFlags + " worldData=" + scheduledWorldData + " missing=" + missingWorldData);

            StringBuilder message = new StringBuilder(resolvedId);
            message.append("|flags=").append(String.join(",", appliedFlags));
            message.append("|worldData=").append(String.join(",", scheduledWorldData));
            message.append("|missing=").append(String.join(",", missingWorldData));
            return OperationResult.success(message.toString());
        }
    }

    private static boolean flagForDimension(BackupRecord record, String dimensionId) {
        return switch (dimensionId) {
            case WorldDataManager.OVERWORLD -> record.overworldEnabled();
            case WorldDataManager.NETHER -> record.netherEnabled();
            case WorldDataManager.END -> record.endEnabled();
            default -> true;
        };
    }

    private static void applyEnabled(String dimensionId, boolean enabled) {
        switch (dimensionId) {
            case WorldDataManager.OVERWORLD -> DimensionDaddy.setOverworldEnabled(enabled);
            case WorldDataManager.NETHER -> DimensionDaddy.setNetherEnabled(enabled);
            case WorldDataManager.END -> DimensionDaddy.setEndEnabled(enabled);
            default -> throw new IllegalArgumentException("Unknown dimension id: " + dimensionId);
        }
    }

    public static List<BackupRecord> listBackups() {
        synchronized (LOCK) {
            List<BackupRecord> records = new ArrayList<>();
            try {
                for (Path path : listBackupDirs()) {
                    Path metaPath = path.resolve("backup.properties");
                    if (!Files.exists(metaPath)) {
                        continue;
                    }
                    BackupRecord record = readBackupRecord(metaPath);
                    if (record != null) {
                        records.add(record);
                    }
                }
            } catch (IOException e) {
                DimensionDaddy.LOGGER.error("Failed to list DimensionDaddy backups", e);
            }
            return records;
        }
    }

    private static String writeBackup(String fixedId, String type, String reason, List<String> requestedDimensions, MinecraftServer server) {
        try {
            Files.createDirectories(BACKUP_DIR);
            String timestamp = LocalDateTime.now().format(ID_FORMAT);
            String id = fixedId != null ? fixedId : uniqueId(timestamp);
            Path backupDir = BACKUP_DIR.resolve(id);
            Files.createDirectories(backupDir);

            List<String> copiedDimensions = new ArrayList<>();
            if (server != null) {
                for (String dimensionId : requestedDimensions) {
                    try {
                        WorldDataManager.copyDimensionData(server, dimensionId, backupDir.resolve(dimensionId));
                        copiedDimensions.add(dimensionId);
                    } catch (IOException e) {
                        DimensionDaddy.LOGGER.error("Failed to copy world data for " + dimensionId + " while creating backup " + id, e);
                    }
                }
            }

            Properties props = new Properties();
            props.setProperty("type", type);
            props.setProperty("reason", reason);
            props.setProperty("timestamp", timestamp);
            props.setProperty("endEnabled", String.valueOf(DimensionDaddy.isEndEnabled()));
            props.setProperty("netherEnabled", String.valueOf(DimensionDaddy.isNetherEnabled()));
            props.setProperty("overworldEnabled", String.valueOf(DimensionDaddy.isOverworldEnabled()));
            props.setProperty("worldDataDimensions", String.join(",", copiedDimensions));

            try (Writer writer = Files.newBufferedWriter(backupDir.resolve("backup.properties"))) {
                props.store(writer, "DimensionDaddy State Backup");
            }

            DimensionDaddy.LOGGER.info("DimensionDaddy backup created: " + id + " (" + reason + ")");
            pruneOldBackups();
            return id;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static String uniqueId(String timestamp) throws IOException {
        String candidate = timestamp;
        int suffix = 1;
        while (Files.exists(BACKUP_DIR.resolve(candidate))) {
            candidate = timestamp + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static void pruneOldBackups() {
        try {
            List<Path> backups = listBackupDirs();
            if (backups.size() <= MAX_BACKUPS) {
                return;
            }
            for (Path path : backups.subList(MAX_BACKUPS, backups.size())) {
                if (path.getFileName().toString().equals(INITIAL_BACKUP_ID)) {
                    continue;
                }
                try {
                    deleteRecursive(path);
                    DimensionDaddy.LOGGER.info("Pruned old DimensionDaddy backup: " + path.getFileName());
                } catch (IOException e) {
                    DimensionDaddy.LOGGER.error("Failed to prune backup " + path.getFileName(), e);
                }
            }
        } catch (IOException e) {
            DimensionDaddy.LOGGER.error("Failed to prune DimensionDaddy backups", e);
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.comparing(Path::getNameCount).reversed())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            DimensionDaddy.LOGGER.error("Failed to delete " + p, e);
                        }
                    });
        }
    }

    private static List<Path> listBackupDirs() throws IOException {
        if (!Files.exists(BACKUP_DIR)) {
            return new ArrayList<>();
        }
        try (Stream<Path> stream = Files.list(BACKUP_DIR)) {
            return stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(DimensionStateManager::lastModified).reversed())
                    .collect(Collectors.toList());
        }
    }

    private static FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    private static BackupRecord readBackupRecord(Path metaPath) {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(metaPath)) {
            props.load(reader);
            String id = metaPath.getParent().getFileName().toString();
            String type = props.getProperty("type", "unknown");
            String reason = props.getProperty("reason", "");
            boolean endEnabled = Boolean.parseBoolean(props.getProperty("endEnabled", "true"));
            boolean netherEnabled = Boolean.parseBoolean(props.getProperty("netherEnabled", "true"));
            boolean overworldEnabled = Boolean.parseBoolean(props.getProperty("overworldEnabled", "true"));
            String worldDataRaw = props.getProperty("worldDataDimensions", "");
            List<String> worldDataDimensions = worldDataRaw.isBlank()
                    ? List.of()
                    : Arrays.stream(worldDataRaw.split(",")).filter(s -> !s.isBlank()).collect(Collectors.toList());

            LocalDateTime timestamp;
            try {
                timestamp = LocalDateTime.parse(props.getProperty("timestamp"), ID_FORMAT);
            } catch (Exception parseFailure) {
                timestamp = LocalDateTime.ofInstant(Files.getLastModifiedTime(metaPath).toInstant(), ZoneId.systemDefault());
            }

            return new BackupRecord(id, type, reason, endEnabled, netherEnabled, overworldEnabled, worldDataDimensions, timestamp);
        } catch (IOException e) {
            DimensionDaddy.LOGGER.warn("Skipping unreadable DimensionDaddy backup: " + metaPath.getParent().getFileName());
            return null;
        }
    }
}
