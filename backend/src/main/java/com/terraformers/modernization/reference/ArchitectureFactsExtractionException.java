package com.terraformers.modernization.reference;

import java.util.Objects;

/**
 * Provider-neutral failure semantics for the architecture-fact extraction stage.
 *
 * <p>The detail string is intentionally assembled only from bounded, sanitized fields so it can
 * be written to machine-readable evaluation output without leaking source images, prompts, raw
 * provider responses, or exception messages.</p>
 */
public final class ArchitectureFactsExtractionException extends RuntimeException {

    public enum Reason {
        PROVIDER_RUNTIME,
        PROVIDER_CONTENT_BLOCKED,
        PROVIDER_TIMEOUT,
        PROVIDER_RATE_LIMITED,
        PROVIDER_ERROR,
        RESPONSE_TRUNCATED,
        EMPTY_RESPONSE,
        INVALID_RESPONSE,
        EMPTY_FACTS
    }

    private final Reason reason;
    private final String providerStatus;
    private final String providerErrorType;
    private final Boolean transientFailure;

    private ArchitectureFactsExtractionException(
            Reason reason,
            String providerStatus,
            String providerErrorType,
            Boolean transientFailure,
            Throwable cause
    ) {
        super(reason.name(), cause);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.providerStatus = safeStatus(providerStatus);
        this.providerErrorType = safeErrorType(providerErrorType);
        this.transientFailure = transientFailure;
    }

    public static ArchitectureFactsExtractionException providerRuntime(
            String providerStatus,
            String providerErrorType,
            Boolean transientFailure,
            Throwable cause
    ) {
        return new ArchitectureFactsExtractionException(
                Reason.PROVIDER_RUNTIME,
                providerStatus,
                providerErrorType,
                transientFailure,
                cause
        );
    }

    public static ArchitectureFactsExtractionException provider(
            Reason reason, String providerStatus, String providerErrorType, Throwable cause) {
        if (reason != Reason.PROVIDER_CONTENT_BLOCKED && reason != Reason.PROVIDER_TIMEOUT
                && reason != Reason.PROVIDER_RATE_LIMITED && reason != Reason.PROVIDER_ERROR) {
            throw new IllegalArgumentException("reason is not a classified provider failure");
        }
        return new ArchitectureFactsExtractionException(reason, providerStatus, providerErrorType, null, cause);
    }

    public static ArchitectureFactsExtractionException response(Reason reason, Throwable cause) {
        if (reason == Reason.PROVIDER_RUNTIME || reason == Reason.PROVIDER_CONTENT_BLOCKED
                || reason == Reason.PROVIDER_TIMEOUT || reason == Reason.PROVIDER_RATE_LIMITED
                || reason == Reason.PROVIDER_ERROR) {
            throw new IllegalArgumentException("provider runtime failures require providerRuntime");
        }
        return new ArchitectureFactsExtractionException(reason, "", "", null, cause);
    }

    public Reason reason() {
        return reason;
    }

    public String evaluationDetail() {
        StringBuilder detail = new StringBuilder("reason=").append(reason.name());
        append(detail, "providerStatus", providerStatus);
        append(detail, "providerErrorType", providerErrorType);
        if (transientFailure != null) {
            append(detail, "transient", transientFailure.toString());
        }
        return detail.toString();
    }

    private static void append(StringBuilder detail, String key, String value) {
        if (!value.isBlank()) {
            detail.append(';').append(key).append('=').append(value);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private static String safeStatus(String value) {
        String normalized = normalize(value);
        return normalized.matches("[1-5][0-9]{2}") ? normalized : "";
    }

    private static String safeErrorType(String value) {
        String normalized = normalize(value);
        return normalized.matches("[A-Za-z0-9_.-]{1,80}") ? normalized : "";
    }
}
