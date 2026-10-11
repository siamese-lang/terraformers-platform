package com.terraformers.modernization.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terraformers.modernization.identity.UserEntity;
import com.terraformers.modernization.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class BillableAnalysisPolicyTest {
    @Test
    void firebaseRequiresVerifiedServerEntitlementAndRateLimitsDuplicate() {
        JwtRuntimeProperties mode = new JwtRuntimeProperties();
        mode.setProvider("firebase");
        FirebaseJwtRuntimeProperties firebase = new FirebaseJwtRuntimeProperties();
        UserEntity user = new UserEntity();
        user.setExternalIdentity("firebase", "uid-1");
        user.setStatus(UserStatus.ACTIVE);
        Jwt verified = token("uid-1", true);
        assertThatThrownBy(() -> new BillableAnalysisPolicy(mode, firebase).requireAllowed(verified, user))
                .isInstanceOf(SecurityException.class);
        firebase.setBillableUids("uid-1");
        BillableAnalysisPolicy policy = new BillableAnalysisPolicy(mode, firebase);
        assertThatThrownBy(() -> policy.requireAllowed(token("uid-1", false), user))
                .isInstanceOf(SecurityException.class);
        policy.requireAllowed(verified, user);
        assertThatThrownBy(() -> policy.requireAllowed(verified, user))
                .isInstanceOf(SecurityException.class);
        user.setStatus(UserStatus.DISABLED);
        assertThatThrownBy(() -> new BillableAnalysisPolicy(mode, firebase).requireAllowed(verified, user))
                .isInstanceOf(SecurityException.class);
    }

    private Jwt token(String uid, boolean verified) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .subject(uid).claim("email_verified", verified).build();
    }
}
