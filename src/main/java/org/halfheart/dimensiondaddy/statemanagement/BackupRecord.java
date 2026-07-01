package org.halfheart.dimensiondaddy.statemanagement;

import java.time.LocalDateTime;
import java.util.List;

public record BackupRecord(String id, String type, String reason, boolean endEnabled, boolean netherEnabled, boolean overworldEnabled, List<String> worldDataDimensions, LocalDateTime timestamp) {

    public boolean hasWorldData(String dimensionId) {
        return worldDataDimensions.contains(dimensionId);
    }
}
