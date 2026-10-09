package com.terraformers.modernization.analysis;

import java.lang.reflect.Method;
import java.io.InterruptedIOException;
import com.google.genai.errors.GenAiIOException;
import java.util.List;

/** Bounded transport classification shared by provider adapters. */
public final class ProviderFailureClassifier {
    private ProviderFailureClassifier() {}

    /** Numeric transport evidence only; never exception messages or provider response bodies. */
    public static Integer upstreamHttpStatus(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            Integer status = statusCode(current);
            if (status != null) return status;
        }
        return null;
    }

    public static boolean isRateLimited(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            Integer status = statusCode(current);
            if (Integer.valueOf(429).equals(status)
                    || current.getClass().getSimpleName().toLowerCase().contains("throttl")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTimeout(Throwable failure) {
        boolean genAiIo = false;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof GenAiIOException) genAiIo = true;
            String name = current.getClass().getSimpleName().toLowerCase();
            if (name.contains("timeout") || name.contains("timedout")) return true;
            // OkHttp whole-call expiry in the audited SDK; ordinary interrupted I/O is not timeout.
            if (genAiIo && current instanceof InterruptedIOException
                    && "timeout".equals(current.getMessage())) return true;
        }
        return false;
    }

    public static Integer statusCode(Throwable failure) {
        for (String name : List.of("statusCode", "getStatusCode", "code")) {
            try {
                Method method = failure.getClass().getMethod(name);
                Object value = method.invoke(failure);
                if (value instanceof Number number && number.intValue() >= 100 && number.intValue() <= 599) {
                    return number.intValue();
                }
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // This provider exception does not expose a numeric HTTP status through this accessor.
            }
        }
        return null;
    }
}
