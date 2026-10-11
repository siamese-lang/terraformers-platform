package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.security.FirebaseJwtRuntimeProperties;
import com.terraformers.modernization.security.JwtRuntimeProperties;
import com.terraformers.modernization.storage.StorageRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

class FirebaseRuntimeConfigurationTest {
    @Test
    void productionRejectsDisabledJwtAndWrongFirebaseIssuerOrJwks() {
        AnalysisRuntimeProperties analysis = mock(AnalysisRuntimeProperties.class);
        StorageRuntimeProperties storage = mock(StorageRuntimeProperties.class);
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(org.mockito.ArgumentMatchers.any(Profiles.class)))
                .thenAnswer(call -> ((Profiles) call.getArgument(0)).matches("prod"::equals));
        JwtRuntimeProperties jwt = new JwtRuntimeProperties();
        jwt.setProvider("firebase");
        FirebaseJwtRuntimeProperties firebase = new FirebaseJwtRuntimeProperties();
        firebase.setProjectId("terraformers-test");
        jwt.setIssuerUri("https://securetoken.google.com/terraformers-test");
        jwt.setJwkSetUri(FirebaseJwtRuntimeProperties.JWK_SET_URI);
        ProviderSelectionValidator validator = new ProviderSelectionValidator(
                analysis, jwt, storage, firebase, environment);
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
        jwt.setEnabled(true);
        validator.validate();
        jwt.setIssuerUri("https://securetoken.google.com/wrong");
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
        jwt.setIssuerUri("https://securetoken.google.com/terraformers-test");
        jwt.setJwkSetUri("http://127.0.0.1:8080/jwks.json");
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }
}
