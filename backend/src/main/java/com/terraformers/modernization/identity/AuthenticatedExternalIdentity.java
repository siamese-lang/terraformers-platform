package com.terraformers.modernization.identity;

public record AuthenticatedExternalIdentity(
        String provider,
        String subject,
        String email,
        String explicitDisplayName,
        String fallbackDisplayName
) {
}
