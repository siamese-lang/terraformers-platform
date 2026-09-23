package com.terraformers.modernization.storage;

public class ObjectStorageException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,
        UNAVAILABLE,
        UPSTREAM_FAILURE
    }

    private final Reason reason;

    public ObjectStorageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
