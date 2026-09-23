package com.terraformers.modernization.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class ExternalIdentityMigrationTest {

    private static final String MIGRATION =
            "/db/migration/V20260923_005__neutralize_external_user_identity.sql";

    @Test
    void backfillsLegacyCognitoIdentityWithoutChangingInternalUserIdAndEnforcesScopedUniqueness()
            throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:external-identity-migration;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        ); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE users (
                        user_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        cognito_sub VARCHAR(128) NOT NULL,
                        email VARCHAR(320),
                        CONSTRAINT uk_users_cognito_sub UNIQUE (cognito_sub)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO users (user_id, cognito_sub, email)
                    VALUES (42, 'legacy-subject', 'legacy@example.com')
                    """);

            applyMigration(statement);

            try (ResultSet result = statement.executeQuery("""
                    SELECT user_id, cognito_sub, external_identity_provider, external_identity_subject
                    FROM users WHERE user_id = 42
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong("user_id")).isEqualTo(42L);
                assertThat(result.getString("cognito_sub")).isEqualTo("legacy-subject");
                assertThat(result.getString("external_identity_provider")).isEqualTo("cognito");
                assertThat(result.getString("external_identity_subject")).isEqualTo("legacy-subject");
            }

            statement.executeUpdate("""
                    INSERT INTO users (
                        cognito_sub, email, external_identity_provider, external_identity_subject
                    ) VALUES (NULL, 'other@example.com', 'other-provider', 'legacy-subject')
                    """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO users (
                        cognito_sub, email, external_identity_provider, external_identity_subject
                    ) VALUES (NULL, 'duplicate@example.com', 'cognito', 'legacy-subject')
                    """))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void applyMigration(Statement statement) throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as("migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String command : sql.split(";")) {
            if (!command.isBlank()) {
                statement.execute(command);
            }
        }
    }
}
