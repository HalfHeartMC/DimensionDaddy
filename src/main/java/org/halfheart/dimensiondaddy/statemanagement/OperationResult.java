package org.halfheart.dimensiondaddy.statemanagement;

public record OperationResult(boolean success, String message) {

    public static OperationResult success(String message) {
        return new OperationResult(true, message);
    }

    public static OperationResult failure(String message) {
        return new OperationResult(false, message);
    }
}
