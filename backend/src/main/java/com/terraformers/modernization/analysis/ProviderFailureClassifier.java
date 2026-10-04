package com.terraformers.modernization.analysis;

import java.lang.reflect.Method;
import java.util.List;

/** Bounded transport classification shared by provider adapters. */
public final class ProviderFailureClassifier {
    private ProviderFailureClassifier() {}

    public static boolean isRateLimited(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (statusCode(current) == 429 || current.getClass().getSimpleName().toLowerCase().contains("throttl")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTimeout(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String name = current.getClass().getSimpleName().toLowerCase();
            if (name.contains("timeout") || name.contains("timedout")) return true;
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
