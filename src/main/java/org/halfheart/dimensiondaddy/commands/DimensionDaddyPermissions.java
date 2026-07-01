package org.halfheart.dimensiondaddy.commands;

import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public final class DimensionDaddyPermissions {

    public static final Permission OPERATOR = new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS);

    private DimensionDaddyPermissions() {
    }
}
