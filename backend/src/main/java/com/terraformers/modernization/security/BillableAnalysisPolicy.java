package com.terraformers.modernization.security;

import com.terraformers.modernization.identity.UserEntity;
import com.terraformers.modernization.identity.UserStatus;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Server-controlled Firebase entitlement; default-deny before any paid job is created. */
@Component
public class BillableAnalysisPolicy {
    private static final long MIN_INTERVAL_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(1);
    private final JwtRuntimeProperties jwtProperties;
    private final Set<String> allowedUids;
    private final ConcurrentHashMap<String, Long> lastSubmission = new ConcurrentHashMap<>();

    public BillableAnalysisPolicy(JwtRuntimeProperties jwtProperties, FirebaseJwtRuntimeProperties firebase) {
        this.jwtProperties = jwtProperties;
        this.allowedUids = Arrays.stream(firebase.getBillableUids().split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public void requireAllowed(Jwt jwt, UserEntity user) {
        if (!jwtProperties.isFirebase()) {
            return; // Explicit historical Cognito/test mode; Firebase cannot use it.
        }
        if (user == null || user.getStatus() != UserStatus.ACTIVE
                || !"firebase".equals(user.getExternalIdentityProvider())
                || !user.getExternalIdentitySubject().equals(jwt.getSubject())
                || !Boolean.TRUE.equals(jwt.getClaim("email_verified"))
                || !allowedUids.contains(jwt.getSubject())) {
            throw new SecurityException("billable analysis is not enabled for this verified account");
        }
        long now = System.nanoTime();
        boolean[] admitted = {false};
        lastSubmission.compute(jwt.getSubject(), (uid, previous) -> {
            if (previous == null || now - previous >= MIN_INTERVAL_NANOS) {
                admitted[0] = true;
                return now;
            }
            return previous;
        });
        if (!admitted[0]) {
            throw new SecurityException("billable analysis submission is rate limited");
        }
    }
}
