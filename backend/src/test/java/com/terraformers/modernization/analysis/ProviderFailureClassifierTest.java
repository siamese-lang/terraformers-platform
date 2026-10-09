package com.terraformers.modernization.analysis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProviderFailureClassifierTest {
    @Test
    void diagnosticCauseTypesAreBoundedCycleSafeAndNeverContainMessages() {
        Throwable nested = new com.google.genai.errors.GenAiIOException("PRIVATE_REQUEST_RESPONSE",
                new java.io.InterruptedIOException("timeout"));
        assertEquals("GenAiIOException,InterruptedIOException", ProviderFailureClassifier.safeExceptionTypes(nested));
        Throwable deep = new IllegalStateException("PRIVATE_SECRET");
        for (int i = 0; i < 12; i++) deep = new RuntimeException("PRIVATE_SECRET", deep);
        assertEquals(8, ProviderFailureClassifier.safeExceptionTypes(deep).split(",").length);
        RuntimeException first = new RuntimeException("PRIVATE_SECRET");
        RuntimeException second = new RuntimeException("PRIVATE_SECRET", first);
        first.initCause(second);
        assertEquals("RuntimeException,RuntimeException", ProviderFailureClassifier.safeExceptionTypes(first));
    }

    @Test
    void recognizesExactSdkWholeCallTimeoutAndPreservesStandardTimeouts() {
        var timeout = new com.google.genai.errors.GenAiIOException("SDK I/O",
                new java.io.InterruptedIOException("timeout"));
        assertTrue(ProviderFailureClassifier.isTimeout(new RuntimeException(timeout)));
        assertTrue(ProviderFailureClassifier.isTimeout(new java.net.SocketTimeoutException("read")));
        assertTrue(ProviderFailureClassifier.isTimeout(new java.net.http.HttpTimeoutException("request")));
    }

    @Test
    void ordinaryInterruptedIoAndMessageOnlyFailuresAreNotTimeout() {
        assertFalse(ProviderFailureClassifier.isTimeout(new com.google.genai.errors.GenAiIOException(
                new java.io.InterruptedIOException("interrupted"))));
        assertFalse(ProviderFailureClassifier.isTimeout(new java.io.InterruptedIOException("timeout")));
        assertFalse(ProviderFailureClassifier.isTimeout(new RuntimeException("timeout")));
    }

    @Test
    void exceptionWithoutStatusAccessorDoesNotThrowAndIsNotRateLimited() {
        boolean rateLimited = assertDoesNotThrow(
                () -> ProviderFailureClassifier.isRateLimited(new RuntimeException("provider failed")));

        assertFalse(rateLimited);
    }

    @Test
    void explicitHttp429IsRateLimited() {
        assertTrue(ProviderFailureClassifier.isRateLimited(new HttpStatusException(429)));
    }

    @Test
    void explicitThrottlingSignalRemainsRateLimited() {
        assertTrue(ProviderFailureClassifier.isRateLimited(new ProviderThrottlingException()));
    }

    @Test
    void non429HttpStatusIsNotRateLimited() {
        assertFalse(ProviderFailureClassifier.isRateLimited(new HttpStatusException(503)));
    }

    @Test
    void preservesOnlyTypedNestedStatusWithoutInferringFromSensitiveMessage() {
        assertEquals(429, ProviderFailureClassifier.upstreamHttpStatus(
                new RuntimeException("private provider payload", new HttpStatusException(429))));
        assertEquals(503, ProviderFailureClassifier.upstreamHttpStatus(new HttpStatusException(503)));
        assertNull(ProviderFailureClassifier.upstreamHttpStatus(new RuntimeException("429 quota exhausted")));
        assertNull(ProviderFailureClassifier.upstreamHttpStatus(new ProviderThrottlingException()));
        assertNull(ProviderFailureClassifier.upstreamHttpStatus(new HttpStatusException(700)));
    }

    public static final class HttpStatusException extends RuntimeException {
        private final int statusCode;

        private HttpStatusException(int statusCode) {
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }

    public static final class ProviderThrottlingException extends RuntimeException {}
}
